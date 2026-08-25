# Accepted Assumptions

This is the concise source of truth for product assumptions. Contradicting changes must update this file and [`system-design.md`](system-design.md), then be proven by tests.

## Boundary

- The system manages multiple machines; each machine has at most one active customer session.
- `vending-machine-service` is the single deployable and owns sessions, slots, stock, cash, purchases, command results, and product availability in PostgreSQL.
- Domain events are dispatched synchronously in-process; only events with a current secondary consequence have subscribers. No external broker or invented downstream bounded context is part of the case delivery.
- Vendor-specific validator/dispenser protocols and dispense-failure recovery are outside the first delivery; the trusted validator boundary and fail-closed behavior are implemented.

## Domain rules

- Currency is `UNIT`; amounts are immutable `long` minor units, never `double` or `float`.
- Supported denominations are `5`, `10`, `20`, and `50`; quantities are bounded integers.
- A `CurrencyValidator` boundary interface resolves a trusted validator reference before mutation. Only an accepted result supplies the authoritative denomination to the aggregate.
- The deterministic validator simulator is enabled only by the explicit `demo` profile. Non-demo runtime fails closed until an authenticated hardware integration replaces it.
- Accepted money stays in session escrow. Refund returns the same denomination composition.
- `VendingMachine` is the consistency boundary for session, stock, escrow, and cash mutations.
- Sessions are `ACTIVE`, `COMPLETED`, `REFUNDED`, or `EXPIRED`; terminal states reject mutation.
- Recovery reads an injected `Clock`, then passes the two-minute policy and check time explicitly to the aggregate; expiry refunds escrow.
- Purchase needs balance, stock, and exact change from machine cash plus escrow.
- Change minimizes piece count; ties prefer higher denominations. It is planned before mutation.
- Any failed purchase leaves stock, cash, escrow, and session unchanged.

## Consistency and delivery

- Domain classes contain no Spring, JPA, HTTP, Kafka, or JSON annotations.
- PostgreSQL/Flyway and database constraints complement domain invariants.
- Root `@Version` plus mutation-time `OPTIMISTIC_FORCE_INCREMENT` detects child-only conflicts.
- A successful command commits machine state, purchase when applicable, and processed result in one local transaction.
- State-changing HTTP calls require a machine-scoped `Idempotency-Key`; same request replays, different payload conflicts.
- Successful state changes may emit framework-independent domain events. Application handlers run synchronously inside the originating transaction when the event has a current business consequence.
- `PurchaseCompleted` is handled in-process to persist the immutable purchase. Handler failure rolls back machine state and the processed command with the same transaction.
- Current application events are not durably retained or replayable; events without a subscriber are discarded after dispatch while their authoritative aggregate outcome remains persisted.
- Kafka, transactional outbox and an audit projection are reconsidered only when a real external consumer and delivery requirement are identified.

## API and scope

- REST covers product availability, session, money, selection, refund, and authoritative purchase lookup.
- Errors use `application/problem+json`; clients never provide authoritative denomination, authenticity, price, stock, balance, or change.
- Secrets come from the environment; sensitive hardware data is neither logged nor emitted.
- Core behavior, persistence, API, recovery, the supplied ten-product sample data, and a repeatable demonstration are the delivery focus.
- Saga, Kubernetes, gateway, Redis, Debezium, frontend, and speculative microservices are out of scope.

Reconsider the design only when measured contention favors pessimistic locking, real hardware separates recyclable cash, CDC is operationally justified, session policies outgrow simple guarded transitions, or independent payment/reservation contexts require compensation.
