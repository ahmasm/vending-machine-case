package io.github.ahmasm.vending.machine.domain.machine;

import static io.github.ahmasm.vending.machine.domain.money.Currency.UNIT;
import static io.github.ahmasm.vending.machine.domain.money.Denomination.FIFTY;
import static io.github.ahmasm.vending.machine.domain.money.Denomination.FIVE;
import static io.github.ahmasm.vending.machine.domain.money.Denomination.TEN;
import static io.github.ahmasm.vending.machine.domain.money.Denomination.TWENTY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ahmasm.vending.machine.domain.cash.CashComposition;
import io.github.ahmasm.vending.machine.domain.machine.event.PurchaseCompleted;
import io.github.ahmasm.vending.machine.domain.money.Denomination;
import io.github.ahmasm.vending.machine.domain.money.Money;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class VendingMachinePurchaseTest {

    private static final MachineId MACHINE_ID = new MachineId("VM-001");
    private static final SessionId SESSION_ID = new SessionId("SES-001");
    private static final SlotCode SLOT_CODE = new SlotCode("A1");
    private static final ProductId PRODUCT_ID = new ProductId("COLA");
    private static final TransactionId TRANSACTION_ID = new TransactionId("TRX-001");
    private static final Instant STARTED_AT = Instant.parse("2026-08-23T10:00:00Z");
    private static final Instant PURCHASED_AT = STARTED_AT.plusSeconds(30);

    @Test
    void purchasingWithExactPaymentCompletesSessionAndMovesEscrowIntoCash() {
        var initialCash = CashComposition.of(Map.of(TEN, 1));
        var product = productPricedAt(20);
        var machine = machine(product, 2, initialCash);
        startSessionAndAccept(machine, TWENTY);

        var purchase = machine.purchase(SESSION_ID, SLOT_CODE, TRANSACTION_ID, PURCHASED_AT);

        assertEquals(
                new Purchase(
                        TRANSACTION_ID,
                        MACHINE_ID,
                        SESSION_ID,
                        SLOT_CODE,
                        product,
                        unit(20),
                        CashComposition.empty(),
                        PURCHASED_AT),
                purchase);
        assertEquals(1, machine.stockOf(SLOT_CODE));
        assertEquals(CashComposition.of(Map.of(TEN, 1, TWENTY, 1)), machine.availableCash());
        assertEquals(Optional.empty(), machine.activeSessionId());
        assertEquals(List.of(new PurchaseCompleted(purchase)), machine.releaseEvents());
        assertThrows(
                ActiveSessionNotFoundException.class,
                () -> machine.refund(SESSION_ID, PURCHASED_AT.plusSeconds(1)));
        assertTrue(machine.releaseEvents().isEmpty());
    }

    @Test
    void purchasingWithExcessBalanceDispensesMinimumExactChange() {
        var product = productPricedAt(35);
        var machine = machine(product, 1, CashComposition.of(Map.of(TEN, 1, FIVE, 1)));
        startSessionAndAccept(machine, FIFTY);

        var purchase = machine.purchase(SESSION_ID, SLOT_CODE, TRANSACTION_ID, PURCHASED_AT);

        assertEquals(CashComposition.of(Map.of(TEN, 1, FIVE, 1)), purchase.change());
        assertEquals(CashComposition.of(Map.of(FIFTY, 1)), machine.availableCash());
        assertEquals(0, machine.stockOf(SLOT_CODE));
    }

    @Test
    void purchasingWithInsufficientBalancePreservesStockCashAndEscrow() {
        var initialCash = CashComposition.of(Map.of(FIVE, 2));
        var machine = machine(productPricedAt(20), 1, initialCash);
        startSessionAndAccept(machine, TEN);

        var exception = assertThrows(
                InsufficientBalanceException.class,
                () -> machine.purchase(SESSION_ID, SLOT_CODE, TRANSACTION_ID, PURCHASED_AT));

        assertEquals(unit(10), exception.balance());
        assertEquals(unit(20), exception.price());
        assertEquals(1, machine.stockOf(SLOT_CODE));
        assertEquals(initialCash, machine.availableCash());
        assertEquals(Optional.of(SESSION_ID), machine.activeSessionId());
        assertTrue(machine.releaseEvents().isEmpty());
        assertEquals(CashComposition.of(Map.of(TEN, 1)), machine.refund(SESSION_ID, PURCHASED_AT));
    }

    @Test
    void purchasingOutOfStockPreservesCashAndEscrow() {
        var initialCash = CashComposition.of(Map.of(TEN, 2));
        var machine = machine(productPricedAt(20), 0, initialCash);
        startSessionAndAccept(machine, TWENTY);

        assertThrows(
                ProductOutOfStockException.class,
                () -> machine.purchase(SESSION_ID, SLOT_CODE, TRANSACTION_ID, PURCHASED_AT));

        assertEquals(0, machine.stockOf(SLOT_CODE));
        assertEquals(initialCash, machine.availableCash());
        assertEquals(Optional.of(SESSION_ID), machine.activeSessionId());
        assertTrue(machine.releaseEvents().isEmpty());
        assertEquals(CashComposition.of(Map.of(TWENTY, 1)), machine.refund(SESSION_ID, PURCHASED_AT));
    }

    @Test
    void purchasingWithoutAvailableExactChangePreservesStockCashAndEscrow() {
        var machine = machine(productPricedAt(35), 1, CashComposition.empty());
        startSessionAndAccept(machine, FIFTY);

        var exception = assertThrows(
                ChangeUnavailableException.class,
                () -> machine.purchase(SESSION_ID, SLOT_CODE, TRANSACTION_ID, PURCHASED_AT));

        assertEquals(unit(15), exception.changeDue());
        assertEquals(1, machine.stockOf(SLOT_CODE));
        assertEquals(CashComposition.empty(), machine.availableCash());
        assertEquals(Optional.of(SESSION_ID), machine.activeSessionId());
        assertTrue(machine.releaseEvents().isEmpty());
        assertEquals(CashComposition.of(Map.of(FIFTY, 1)), machine.refund(SESSION_ID, PURCHASED_AT));
    }

    private static VendingMachine machine(
            ProductSnapshot product, int stock, CashComposition initialCash) {
        return new VendingMachine(
                MACHINE_ID,
                initialCash,
                List.of(new Slot(SLOT_CODE, product, stock)));
    }

    private static ProductSnapshot productPricedAt(long amount) {
        return new ProductSnapshot(PRODUCT_ID, "Cola", unit(amount));
    }

    private static void startSessionAndAccept(
            VendingMachine machine, Denomination denomination) {
        machine.startSession(SESSION_ID, STARTED_AT);
        machine.acceptMoney(SESSION_ID, denomination, STARTED_AT.plusSeconds(10));
        machine.releaseEvents();
    }

    private static Money unit(long amount) {
        return Money.of(amount, UNIT);
    }
}
