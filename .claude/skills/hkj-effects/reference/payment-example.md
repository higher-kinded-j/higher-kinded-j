# Payment Processing Example Reference

Walkthrough of the payment processing example: same program, four interpretations.

Source: `hkj-examples/src/main/java/org/higherkindedj/example/payment/`

Run: `./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.payment.PaymentProcessingExample`

---

## Architecture Overview

```
Fraud Check --> Balance Check --> Decision
    |               |               |
    v               v               v
 RiskScore        Money        High risk? --> Alert + Decline
                               Low funds? --> Decline
                               OK? --> Charge --> Record Ledger --> Send Receipt --> Result
```

Four external systems, four effect algebras, one program, four interpreters:

| Mode | Target Monad | Purpose |
|------|-------------|---------|
| Production | `IO` | Real services, deferred execution |
| Testing | `Id` | Pure, synchronous, no mocks |
| Quote | `Id` | Fee estimation without charging |
| High Risk | `Id` | Demonstrates risk-based decline |

---

## The Four Effect Algebras

Each algebra: sealed interface + `@EffectAlgebra` + CPS records with `mapK`.

### PaymentGatewayOp

```java
@EffectAlgebra
public sealed interface PaymentGatewayOp<A>
    permits PaymentGatewayOp.Authorise, PaymentGatewayOp.Charge, PaymentGatewayOp.Refund {

  <B> PaymentGatewayOp<B> mapK(Function<? super A, ? extends B> f);

  record Authorise<A>(Money amount, PaymentMethod method,
      Function<AuthorisationToken, A> k) implements PaymentGatewayOp<A> {
    @Override
    public <B> PaymentGatewayOp<B> mapK(Function<? super A, ? extends B> f) {
      return new Authorise<>(amount, method, k.andThen(f));
    }
  }

  record Charge<A>(Money amount, PaymentMethod method,
      Function<ChargeResult, A> k) implements PaymentGatewayOp<A> { /* same pattern */ }

  record Refund<A>(TransactionId transactionId, Money amount,
      Function<ChargeResult, A> k) implements PaymentGatewayOp<A> { /* same pattern */ }
}
```

### FraudCheckOp

```java
@EffectAlgebra
public sealed interface FraudCheckOp<A> permits FraudCheckOp.CheckTransaction {
  <B> FraudCheckOp<B> mapK(Function<? super A, ? extends B> f);

  record CheckTransaction<A>(Money amount, Customer customer,
      Function<RiskScore, A> k) implements FraudCheckOp<A> { /* mapK pattern */ }
}
```

### LedgerOp

```java
@EffectAlgebra
public sealed interface LedgerOp<A> permits LedgerOp.RecordEntry, LedgerOp.GetBalance {
  <B> LedgerOp<B> mapK(Function<? super A, ? extends B> f);

  record RecordEntry<A>(LedgerEntry entry, Function<LedgerEntry, A> k) implements LedgerOp<A> {}
  record GetBalance<A>(CustomerId accountId, Function<Money, A> k) implements LedgerOp<A> {}
}
```

### NotificationOp

```java
@EffectAlgebra
public sealed interface NotificationOp<A>
    permits NotificationOp.SendReceipt, NotificationOp.AlertFraudTeam {
  <B> NotificationOp<B> mapK(Function<? super A, ? extends B> f);

  record SendReceipt<A>(Customer customer, ChargeResult chargeResult,
      Function<Unit, A> k) implements NotificationOp<A> {}
  record AlertFraudTeam<A>(Customer customer, RiskScore riskScore,
      Function<Unit, A> k) implements NotificationOp<A> {}
}
```

### CPS Pattern Summary

| Algebra | Operation | Natural Result | Continuation |
|---------|-----------|---------------|-------------|
| PaymentGatewayOp | Authorise | `AuthorisationToken` | `Function<AuthorisationToken, A>` |
| PaymentGatewayOp | Charge | `ChargeResult` | `Function<ChargeResult, A>` |
| PaymentGatewayOp | Refund | `ChargeResult` | `Function<ChargeResult, A>` |
| FraudCheckOp | CheckTransaction | `RiskScore` | `Function<RiskScore, A>` |
| LedgerOp | RecordEntry | `LedgerEntry` | `Function<LedgerEntry, A>` |
| LedgerOp | GetBalance | `Money` | `Function<Money, A>` |
| NotificationOp | SendReceipt | `Unit` | `Function<Unit, A>` |
| NotificationOp | AlertFraudTeam | `Unit` | `Function<Unit, A>` |

Use `Function.identity()` as continuation to get the natural result type directly.

---

## Generated Classes Per Algebra

`@EffectAlgebra` generates five classes per annotated interface:

| Generated Class | Purpose |
|----------------|---------|
| `PaymentGatewayOpKind` | HKT marker + Witness |
| `PaymentGatewayOpKindHelper` | widen/narrow conversions |
| `PaymentGatewayOpFunctor` | Functor instance (delegates to `mapK`) |
| `PaymentGatewayOpOps` | Smart constructors + `Bound` inner class |
| `PaymentGatewayOpInterpreter` | Abstract interpreter skeleton |

---

## @ComposeEffects Wiring

```java
@ComposeEffects
public record PaymentEffects(
    Class<PaymentGatewayOp<?>> gateway,
    Class<FraudCheckOp<?>> fraud,
    Class<LedgerOp<?>> ledger,
    Class<NotificationOp<?>> notification) {}
```

Produces `PaymentEffectsWiring` with right-nested EitherF composition:

```
EitherF< PaymentGatewayOp,
         EitherF< FraudCheckOp,
                  EitherF< LedgerOp,
                           NotificationOp >>>
```

Inject routing:

| Effect | Inject Path | Wiring Method |
|--------|------------|---------------|
| PaymentGatewayOp | Left | `injectGateway()` |
| FraudCheckOp | Right > Left | `injectFraud()` |
| LedgerOp | Right > Right > Left | `injectLedger()` |
| NotificationOp | Right > Right > Right | `injectNotification()` |

Key `PaymentEffectsWiring` API:

```java
PaymentEffectsWiring.functor()    // Composed EitherFFunctor
PaymentEffectsWiring.boundSet()   // BoundSet with all four Bound instances
PaymentEffectsWiring.interpret(program, interpreter, monad)  // foldMap convenience
```

---

## The PaymentService Program

```java
public final class PaymentService<G extends WitnessArity<TypeArity.Unary>> {

  private final PaymentGatewayOpOps.Bound<G> gateway;
  private final FraudCheckOpOps.Bound<G> fraud;
  private final LedgerOpOps.Bound<G> ledger;
  private final NotificationOpOps.Bound<G> notification;

  // Factory using PaymentEffectsWiring
  public static PaymentService<...> create() {
    var bounds = PaymentEffectsWiring.boundSet();
    return new PaymentService<>(bounds.gateway(), bounds.fraud(),
        bounds.ledger(), bounds.notification(), PaymentEffectsWiring.functor());
  }

  public Free<G, PaymentResult> processPayment(
      Customer customer, Money amount, PaymentMethod method) {

    Free<G, RiskScore> checkRisk =
        fraud.checkTransaction(amount, customer, Function.identity());

    return checkRisk.flatMap(risk -> {
      Free<G, Money> getBalance =
          ledger.getBalance(customer.accountId(), Function.identity());
      return getBalance.flatMap(balance -> {
        if (risk.exceeds(RISK_THRESHOLD))
          return alertAndDecline(customer, risk);
        if (balance.lessThan(amount))
          return Free.pure(PaymentResult.declined("Insufficient funds"));
        return chargeAndRecord(customer, amount, method, risk);
      });
    });
  }
}
```

Key patterns:
- `Function.identity()` continuation = get natural result type
- `Free.pure(value)` = lift pure value into program
- `.flatMap(result -> ...)` = sequence dependent operations
- `.handleError(Throwable.class, _ -> Free.pure(fallback))` = error recovery
- `.map(_ -> result)` = transform result without new effects

---

## Interpretation Mode 1: Production (IO)

```java
var interpreter = Interpreters.combine(
    new ProductionGatewayInterpreter(),   // extends PaymentGatewayOpInterpreter<IOKind.Witness>
    new ProductionFraudInterpreter(),
    new ProductionLedgerInterpreter(),
    new ProductionNotificationInterpreter());

IO<PaymentResult> io = IOKindHelper.IO_OP.narrow(
    PaymentEffectsWiring.interpret(program, interpreter, IOMonad.INSTANCE));
PaymentResult result = io.unsafeRunSync();
```

Production interpreter pattern -- wrap in `IO.delay`:

```java
public final class ProductionGatewayInterpreter
    extends PaymentGatewayOpInterpreter<IOKind.Witness> {
  @Override
  protected <A> Kind<IOKind.Witness, A> handleCharge(PaymentGatewayOp.Charge<A> op) {
    return IOKindHelper.IO_OP.widen(
        IO.delay(() -> op.k().apply(
            ChargeResult.success(TransactionId.generate(), op.amount()))));
  }
}
```

---

## Interpretation Mode 2: Testing (Id, No Mocks)

```java
var gateway = new RecordingGatewayInterpreter();     // records calls, returns deterministic results
var fraud = new FixedRiskInterpreter(RiskScore.of(15));  // fixed score
var ledger = new InMemoryLedgerInterpreter();        // in-memory state
ledger.setBalance(new CustomerId("acc-001"), Money.gbp("5000.00"));
var notification = new CapturingNotificationInterpreter();  // captures receipts/alerts

var interpreter = Interpreters.combine(gateway, fraud, ledger, notification);
Id<PaymentResult> id = IdKindHelper.ID.narrow(
    PaymentEffectsWiring.interpret(program, interpreter, IdMonad.instance()));
PaymentResult result = id.value();

// Assert on captured data
assertThat(gateway.calls()).containsExactly("charge:GBP 49.99");
assertThat(notification.receipts()).hasSize(1);
```

Test interpreter pattern -- return `new Id<>(...)`:

```java
public final class RecordingGatewayInterpreter
    extends PaymentGatewayOpInterpreter<IdKind.Witness> {
  private final List<String> calls = new ArrayList<>();
  public List<String> calls() { return Collections.unmodifiableList(calls); }

  @Override
  protected <A> Kind<IdKind.Witness, A> handleCharge(PaymentGatewayOp.Charge<A> op) {
    calls.add("charge:" + op.amount());
    return new Id<>(op.k().apply(
        ChargeResult.success(new TransactionId("test-txn-001"), op.amount())));
  }
}
```

---

## Interpretation Mode 3: Quote (Fee Estimation)

```java
var gateway = new QuoteGatewayInterpreter();  // calculates fees, no real charge
// ... same fraud/ledger/notification as test mode

if (result instanceof PaymentResult.Approved approved) {
  System.out.println("Estimated total: " + approved.chargeResult().amount());
}
```

Quote interpreter pattern -- compute derived values:

```java
public final class QuoteGatewayInterpreter
    extends PaymentGatewayOpInterpreter<IdKind.Witness> {
  private static final BigDecimal FEE_RATE = new BigDecimal("0.029");
  private static final BigDecimal FIXED_FEE = new BigDecimal("0.30");

  @Override
  protected <A> Kind<IdKind.Witness, A> handleCharge(PaymentGatewayOp.Charge<A> op) {
    BigDecimal fee = op.amount().amount().multiply(FEE_RATE).add(FIXED_FEE);
    Money totalWithFee = new Money(op.amount().amount().add(fee), op.amount().currency());
    return new Id<>(op.k().apply(
        ChargeResult.success(new TransactionId("quote-txn"), totalWithFee)));
  }
}
```

---

## Interpretation Mode 4: High-Risk Decline

```java
var fraud = new FixedRiskInterpreter(RiskScore.of(95));  // above RISK_THRESHOLD (70)
// gateway, ledger, notification same as test mode

// Result: Declined("High risk: ...")
// gateway.calls() is empty -- charge never reached
// notification.alerts() has 1 entry -- fraud team alerted
```

Same program, different interpreter configuration. No code changes to PaymentService.

---

## Error Recovery Pattern

```java
// In chargeAndRecord(): receipt sending is non-critical
Free<G, Unit> receipt = notification.sendReceipt(customer, chargeResult, Function.identity());
Free<G, Unit> safeReceipt = receipt.handleError(
    Throwable.class, _ -> Free.pure(Unit.INSTANCE));
```

- Recovery is part of the program description
- Test interpreters (Id) never trigger recovery (Id is not MonadError)
- Production interpreters (IO) trigger recovery on real failures

---

## PaymentResult Model

```java
public sealed interface PaymentResult
    permits PaymentResult.Approved, PaymentResult.Declined, PaymentResult.Failed {
  record Approved(ChargeResult chargeResult, LedgerEntry ledgerEntry, RiskScore riskScore) {}
  record Declined(String reason) {}
  record Failed(ChargeResult chargeResult) {}
}
```

---

## Effect Handlers vs Dependency Injection

| Capability | Spring DI | Free Effects |
|-----------|-----------|-------------|
| Swap implementations | Yes | Yes |
| Test without mocks | No (needs Mockito) | Yes |
| Inspect before execution | No | Yes |
| Exhaustive handler checking | No | Yes (`sealed` + `@Handles`) |
| Compositional decoration | Limited (AOP) | Yes (interpreter wrapping) |
| Multiple interpretation modes | Manual wiring | Built-in |
