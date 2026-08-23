package io.github.ahmasm.vending.machine.domain.machine;

import io.github.ahmasm.vending.machine.domain.cash.CashComposition;
import io.github.ahmasm.vending.machine.domain.cash.ChangeCalculator;
import io.github.ahmasm.vending.machine.domain.machine.event.MoneyAccepted;
import io.github.ahmasm.vending.machine.domain.machine.event.PurchaseCompleted;
import io.github.ahmasm.vending.machine.domain.machine.event.PurchaseSessionStarted;
import io.github.ahmasm.vending.machine.domain.machine.event.RefundCompleted;
import io.github.ahmasm.vending.machine.domain.machine.event.SessionExpired;
import io.github.ahmasm.vending.machine.domain.machine.event.VendingMachineEvent;
import io.github.ahmasm.vending.machine.domain.money.Denomination;
import io.github.ahmasm.vending.machine.domain.money.Money;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class VendingMachine {

    private static final ChangeCalculator CHANGE_CALCULATOR = new ChangeCalculator();

    private final MachineId id;
    private final Map<SlotCode, Slot> slots;
    private final List<VendingMachineEvent> events = new ArrayList<>();
    private CashComposition cashInventory;
    private PurchaseSession currentSession;

    public VendingMachine(MachineId id) {
        this(id, CashComposition.empty(), List.of());
    }

    public VendingMachine(
            MachineId id, CashComposition initialCash, List<Slot> initialSlots) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        cashInventory = Objects.requireNonNull(initialCash, "initialCash must not be null");
        Objects.requireNonNull(initialSlots, "initialSlots must not be null");

        slots = new LinkedHashMap<>();
        for (var initialSlot : initialSlots) {
            var slot = Objects.requireNonNull(initialSlot, "slot must not be null").copy();
            if (slots.putIfAbsent(slot.code(), slot) != null) {
                throw new IllegalArgumentException(
                        "Duplicate slot code " + slot.code().value());
            }
        }
    }

    public static VendingMachine restore(VendingMachineState state) {
        Objects.requireNonNull(state, "state must not be null");
        var restored = new VendingMachine(
                state.id(),
                state.cashInventory(),
                state.slots().stream()
                        .map(slot -> new Slot(slot.code(), slot.product(), slot.quantity()))
                        .toList());
        restored.currentSession = state.currentSession()
                .map(PurchaseSession::restore)
                .orElse(null);
        return restored;
    }

    public void startSession(SessionId sessionId, Instant startedAt) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        if (currentSession != null && currentSession.isActive()) {
            throw new ActiveSessionAlreadyExistsException(id, currentSession.id());
        }

        currentSession = PurchaseSession.start(sessionId, startedAt);
        events.add(new PurchaseSessionStarted(id, sessionId, startedAt));
    }

    public Money acceptMoney(SessionId sessionId, Denomination denomination, Instant acceptedAt) {
        Objects.requireNonNull(denomination, "denomination must not be null");
        Objects.requireNonNull(acceptedAt, "acceptedAt must not be null");
        var session = requireActiveSession(sessionId);
        var balance = session.acceptMoney(denomination, acceptedAt);
        events.add(new MoneyAccepted(id, sessionId, denomination, balance, acceptedAt));
        return balance;
    }

    public CashComposition refund(SessionId sessionId, Instant refundedAt) {
        Objects.requireNonNull(refundedAt, "refundedAt must not be null");
        var session = requireActiveSession(sessionId);
        var returnedCash = session.refund();
        events.add(new RefundCompleted(id, sessionId, returnedCash, refundedAt));
        return returnedCash;
    }

    public CashComposition expireSession(
            SessionId sessionId, Instant checkedAt, Duration inactivityTimeout) {
        Objects.requireNonNull(checkedAt, "checkedAt must not be null");
        requirePositive(inactivityTimeout);
        var session = requireActiveSession(sessionId);
        var expiresAt = session.expiresAt(inactivityTimeout);
        if (checkedAt.isBefore(expiresAt)) {
            throw new SessionNotExpiredException(expiresAt, checkedAt);
        }

        var returnedCash = session.expire();
        events.add(new SessionExpired(id, sessionId, returnedCash, checkedAt));
        return returnedCash;
    }

    public Purchase purchase(
            SessionId sessionId,
            SlotCode slotCode,
            TransactionId transactionId,
            Instant completedAt) {
        Objects.requireNonNull(slotCode, "slotCode must not be null");
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(completedAt, "completedAt must not be null");

        var session = requireActiveSession(sessionId);
        var slot = requireSlot(slotCode);
        if (slot.quantity() == 0) {
            throw new ProductOutOfStockException(slotCode);
        }

        var escrow = session.escrow();
        var balance = escrow.total();
        var price = slot.product().price();
        if (balance.amount() < price.amount()) {
            throw new InsufficientBalanceException(balance, price);
        }

        var changeDue = balance.subtract(price);
        var provisionalCash = cashInventory.add(escrow);
        var change = CHANGE_CALCULATOR
                .calculate(changeDue, provisionalCash)
                .orElseThrow(() -> new ChangeUnavailableException(changeDue));
        var settledCash = provisionalCash.subtract(change);
        var purchase = new Purchase(
                transactionId,
                id,
                sessionId,
                slotCode,
                slot.product(),
                balance,
                change,
                completedAt);

        slot.dispenseOne();
        cashInventory = settledCash;
        session.complete();
        events.add(new PurchaseCompleted(purchase));
        return purchase;
    }

    public int stockOf(SlotCode slotCode) {
        return requireSlot(slotCode).quantity();
    }

    public CashComposition availableCash() {
        return cashInventory;
    }

    public VendingMachineState snapshot() {
        return new VendingMachineState(
                id,
                cashInventory,
                slots.values().stream().map(Slot::state).toList(),
                Optional.ofNullable(currentSession).map(PurchaseSession::state));
    }

    public Optional<SessionId> activeSessionId() {
        if (currentSession == null || !currentSession.isActive()) {
            return Optional.empty();
        }
        return Optional.of(currentSession.id());
    }

    public List<VendingMachineEvent> releaseEvents() {
        var released = List.copyOf(events);
        events.clear();
        return released;
    }

    private PurchaseSession requireActiveSession(SessionId sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        if (currentSession == null
                || !currentSession.isActive()
                || !currentSession.id().equals(sessionId)) {
            throw new ActiveSessionNotFoundException(id, sessionId);
        }
        return currentSession;
    }

    private Slot requireSlot(SlotCode slotCode) {
        Objects.requireNonNull(slotCode, "slotCode must not be null");
        var slot = slots.get(slotCode);
        if (slot == null) {
            throw new SlotNotFoundException(slotCode);
        }
        return slot;
    }

    private static Duration requirePositive(Duration inactivityTimeout) {
        Objects.requireNonNull(inactivityTimeout, "inactivityTimeout must not be null");
        if (inactivityTimeout.isZero() || inactivityTimeout.isNegative()) {
            throw new IllegalArgumentException("inactivityTimeout must be positive");
        }
        return inactivityTimeout;
    }
}
