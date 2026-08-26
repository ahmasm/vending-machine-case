# Vending Machine Case

ASELSAN interview case implementation built with Java 25, Spring Boot 3.5.16, Maven, and PostgreSQL. The code applies pragmatic layered DDD around one authoritative `VendingMachine` aggregate and one deployable service.

## Case coverage

The service supports session start, validated money insertion, product selection, change, refund, product/session/purchase queries, inactivity recovery, and the ten products supplied by the case. Money is stored as `long` minor units; accepted denominations use bounded integer quantities.

The service has a command-driven core with synchronous, transaction-local domain-event notifications. `PurchaseCompleted` has the only current subscriber and persists the immutable purchase in the same PostgreSQL transaction as stock, cash, session, and idempotency state. These events are not durable or replayable messages; the case defines no external consumer, so Kafka, an outbox, an audit service, and microservice decomposition are intentionally outside the current scope.

## REST API

- `GET /api/v1/machines/{machineId}/products`
- `POST /api/v1/machines/{machineId}/sessions`
- `GET /api/v1/machines/{machineId}/sessions/{sessionId}`
- `POST /api/v1/machines/{machineId}/sessions/{sessionId}/money`
- `POST /api/v1/machines/{machineId}/sessions/{sessionId}/selection`
- `POST /api/v1/machines/{machineId}/sessions/{sessionId}/refund`
- `GET /api/v1/machines/{machineId}/purchases/{transactionId}`

Every command requires an `Idempotency-Key` header. Responses carry `X-Correlation-Id`; errors use `application/problem+json`. OpenAPI JSON is at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`.

The money endpoint accepts a trusted validator reference, not a client assertion such as `isAuthentic` or an authoritative denomination. The validator result supplies the denomination that enters escrow:

```json
{"validatorReference": "SIM-VALID-10-DEMO-001"}
```

## Build

Prerequisites are JDK 25 and Docker. Docker is used by PostgreSQL Testcontainers during integration tests; a global Maven installation is not needed.

```shell
./mvnw clean verify
```

The checked-in wrapper uses Maven 3.9.16.

## Run the demo

```shell
cp .env.example .env
# Replace the example password in .env.
docker compose up --build -d
docker compose ps
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/machines/VM-001/products
```

Docker Compose enables the isolated `db/demo` Flyway location. It creates `VM-001`, change inventory, stock quantity 10, and these exact case products:

| Slot | Product | Price |
|---|---|---:|
| A1 | Water | 25 |
| A2 | Coke | 35 |
| A3 | Soda | 45 |
| A4 | Snickers | 50 |
| A5 | Chips | 40 |
| B1 | Candy Bar | 30 |
| B2 | Energy Drink | 60 |
| B3 | Juice Box | 55 |
| B4 | Protein Bar | 45 |
| B5 | Gum | 20 |

Docker Compose also enables the explicit `demo` profile. Its deterministic currency-validator references are:

| Reference | Result |
|---|---|
| `SIM-VALID-5-<unique-token>`, `SIM-VALID-10-<unique-token>`, `SIM-VALID-20-<unique-token>`, `SIM-VALID-50-<unique-token>` | Accepted with the corresponding authoritative denomination |
| `SIM-COUNTERFEIT` | Rejected as counterfeit |
| `SIM-UNREADABLE` | Rejected as unreadable |
| `SIM-UNSUPPORTED` | Rejected as unsupported denomination |
| `SIM-OFFLINE` | Validator unavailable |

Each full accepted reference represents one physical insertion and is single-use per machine; use a new token for every demo insertion. The unsuffixed forms remain valid but can likewise be consumed only once. Unknown references are rejected. Outside the `demo` profile the bundled validator fails closed with `503 CURRENCY_VALIDATION_UNAVAILABLE`; a real deployment must replace it with an authenticated hardware integration. Use Swagger UI for the complete purchase flow. Stop the stack with `docker compose down`; add `-v` only when you intentionally want to delete local database data.

## Design notes

The implementation keeps the domain free of Spring, JPA, HTTP, and JSON annotations. PostgreSQL transactions, command idempotency, `@Version`, and `OPTIMISTIC_FORCE_INCREMENT` protect the aggregate when child rows change. The inactivity scheduler revalidates each candidate inside the mutation transaction before expiring and refunding a session.

Accepted assumptions and design decisions are documented in [`docs/assumptions.md`](docs/assumptions.md) and [`docs/system-design.md`](docs/system-design.md).
