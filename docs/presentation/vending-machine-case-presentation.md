---
marp: true
theme: uncover
class: invert
size: 16:9
paginate: true
footer: VENDING-MACHINE-CASE
---

<!-- _paginate: false -->
<!-- _footer: 26.08.2026 -->

# Production-minded
# Vending Machine Backend

Bir satın alma işlemini stok, nakit, para üstü ve tekrar denemeler karşısında nasıl birlikte doğru tuttuk?

**1 authoritative servis · 99 başarılı test · 0 gereksiz broker**

---

# Case’i şu soruya indirgedim

> Kullanıcı ürünü seçtiğinde makine ya **tam ve bir kez** satış yapmalı ya da hiçbir state’i yarım bırakmamalı.

- **Birlikte doğru:** session, escrow, stock, cash inventory ve purchase
- **Tekrar güvenli:** ağ retry’ı ikinci ürün veya ikinci kredi üretmemeli
- **Hata güvenli:** para üstü yoksa state değişmemeli

Tasarım ilkesi: önce invariant’ları koru; dağıtık bileşenleri yalnız gerçek ihtiyaç varsa ekle.

---

# Kullanıcının gördüğü purchase akışı

```text
Session başlat
  → doğrulanmış para yatır
    → slot seç (ör. A1)
      → ürün ve exact change sonucunu al
```

Başarı için:

1. Session aktif olmalı.
2. Slot mevcut ve stock pozitif olmalı.
3. Bakiye ürün fiyatını karşılamalı.
4. Mevcut denomination’larla exact change üretilebilmeli.

Başarıda stock azalır; escrow kapanır; kasa güncellenir; session tamamlanır; immutable purchase oluşur.

---

# Nihai mimari: sade katmanlar, tek deployable

```text
REST / Swagger
      ↓
Application  — use-case ve transaction orchestration
      ↓
Domain       — VendingMachine aggregate ve business kuralları
      ↕
Persistence  — JPA + JDBC + Flyway → PostgreSQL
```

- Domain; Spring, JPA, HTTP ve JSON annotation’larından bağımsızdır.
- Her katman için yapay interface üretilmez.
- Boundary yalnız gerçek bir dış bağımlılık veya değişim noktası varsa tanımlanır.

_Kaynak: `docs/system-design.md`_

---

# Aggregate sınırı

```text
VendingMachine
├── active PurchaseSession
│   └── escrow composition
├── Slots
│   └── product snapshot + stock
└── CashInventory
    └── denomination quantities
```

Bu state birlikte değişir çünkü aynı satış kararının parçalarıdır.

- Makine başına tek aktif session
- Negatif stock veya cash yok
- Refund escrow composition’ını aynen döndürür
- Başarısız purchase kısmi mutation yapmaz

> DDD burada çok sınıf üretmek değil, hangi state’in birlikte tutarlı kalacağını belirlemektir.

---

# Purchase: önce plan, sonra mutation

```java
var session = requireActiveSession(sessionId);
var slot = requireSlot(slotCode);
ensureStockAndBalance(slot, session);

var provisionalCash = cashInventory.add(session.escrow());
var change = changeCalculator.calculate(changeDue, provisionalCash)
    .orElseThrow(() -> new ChangeUnavailableException(changeDue));

// Bütün kontrollerden sonra success mutation
slot.dispenseOne();
cashInventory = provisionalCash.subtract(change);
session.complete();
events.add(new PurchaseCompleted(purchase));
```

Change planı bulunamazsa mutation bölgesine hiç girilmez.

_Kaynak: `domain/machine/VendingMachine.java`_

---

# Money ve exact change

**Money**

- En küçük birimde `long`; `double` / `float` yok
- Currency açıkça `UNIT`
- Equality, persistence ve hesap deterministik
- Overflow kontrollü

**CashComposition**

```text
10 × 1 + 5 × 1 = 15 UNIT
```

Refund yalnız “15” tutarını değil fiziksel denomination kompozisyonunu döndürür.

Exact-change araması inventory sınırlarına uyar, en az parçayı seçer ve eşitlikte yüksek denomination’ı tercih eder. Sınırlı inventory nedeniyle basit greedy yeterli değildir.

---

# Para doğrulama: HTTP client’a güvenme

```text
Client
  validatorReference gönderir
        ↓
Trusted CurrencyValidator boundary
        ↓
ACCEPTED + authoritative denomination
        ↓
Escrow
```

- Client `isAuthentic=true` veya authoritative denomination gönderemez.
- Production profili gerçek adapter yoksa **fail closed** davranır.
- Demo simulator; accepted, counterfeit, unreadable, unsupported ve offline sonuçlarını tekrarlanabilir üretir.
- Simulator production authenticity iddiasında bulunmaz.

---

# İki replay problemine iki ayrı kimlik

| Risk | Kimlik | Koruma |
|---|---|---|
| HTTP retry komutu tekrar çalıştırır | `Idempotency-Key` | Request hash + stable command result |
| Aynı fiziksel para farklı key ile sunulur | `validatorReference` | Makine bazında tek kullanımlık SHA-256 hash |

```text
command claim
  → validator-reference hash claim
    → aggregate mutation
      → stable result
        → COMMIT
```

Hepsi aynı transaction’dadır. Rollback reference’ı tüketmez; aynı reference’ın farklı key ile kullanımı `409 Conflict` olur.

_Kaynak: `TransactionalValidatedMoneyExecutor` · `V5__prevent_currency_acceptance_replay.sql`_

---

# Başarılı satışın transaction sınırı

```text
Idempotency key claim
  → aggregate’i OPTIMISTIC_FORCE_INCREMENT ile yükle
    → purchase()
      → machine state’i kaydet
        → PurchaseCompleted yayınla
          → immutable purchase kaydet
            → stable command result kaydet
              → COMMIT
```

- **Atomic:** machine, purchase ve command sonucu birlikte commit / rollback
- **Concurrent-safe:** root `@Version` child mutation’da da artar
- **Retry-safe:** aynı komut yeniden uygulanmaz, sonucu replay edilir

Aynı makine üzerinde yarışan iki mutation’dan biri lost update üretmek yerine güvenli biçimde conflict alır.

---

# Event-driven karar: domain fact var, broker yok

```java
events.add(new PurchaseCompleted(purchase));

machine.releaseEvents().forEach(eventPublisher::publishEvent);

@EventListener
public void handle(PurchaseCompleted event) {
    purchaseStore.save(event.purchase());
}
```

Aggregate yalnız tamamlanmış business fact’i üretir. Immutable purchase kaydını application handler üstlenir.

Semantics: **senkron, aynı thread, aynı transaction**. Durable veya replayable message iddiası yoktur.

Kafka eklenmedi çünkü case gerçek bir external consumer tanımlamıyor. Böyle bir consumer doğarsa ADR, transactional outbox ve at-least-once/idempotent consumption gerekir.

---

# Failure ve recovery davranışları

| Durum | Davranış |
|---|---|
| Yetersiz bakiye / stock yok | Reddet; stock, cash ve session değişmez |
| Exact change yok | Mutation yapma; escrow aktif kalır ve refund edilebilir |
| Validator offline | `503`; para kredilendirilmez |
| Optimistic conflict | `409`; lost update oluşmaz |
| İnaktif session | Scheduler aday bulur; aggregate transaction içinde yeniden doğrular |

Scheduler business kararını vermez. “Session gerçekten expire oldu mu?” kontrolü aggregate içinde tekrar yapılır.

Persisted activity sayesinde recovery restart sonrasında devam eder.

---

# REST yüzeyi

**Commands — `Idempotency-Key` zorunlu**

- `POST .../sessions`
- `POST .../money`
- `POST .../selection`
- `POST .../refund`

**Queries**

- `GET .../products`
- `GET .../sessions/{id}`
- `GET .../purchases/{id}`

OpenAPI: `/v3/api-docs` · Swagger UI: `/swagger-ui.html`

Hatalar stable code, `application/problem+json` ve correlation ID taşır. Beklenmeyen `5xx` detayları dışarı sızdırılmaz.

---

# Doğrulama

## `./mvnw clean verify` → BUILD SUCCESS

## 99 test · 0 failure

- Domain invariant’ları
- REST sözleşmesi ve hata cevapları
- Gerçek PostgreSQL transaction ve concurrency davranışı

Test sayısına değil, kritik risklere odaklandım.

---

# Sonuç: küçük fakat production-minded bir core

**Teslim edilen**

- DDD aggregate ve value objects
- Transaction-local domain events
- Güvenli para doğrulama sınırı
- Exact change ve composition-preserving refund
- Command idempotency ve currency anti-replay
- Optimistic concurrency ve restart recovery

**Bilinçli olarak eklenmeyen**

Kafka, outbox, ikinci servis, Saga, Kubernetes, Redis ve requirement’sız abstraction’lar.

> En önemli karar: stok, escrow ve cash’in birlikte doğru kalmasını teknoloji gösterisinden daha değerli görmek.

Repository: `github.com/ahmasm/vending-machine-case`
