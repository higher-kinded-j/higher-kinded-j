# Payment Processing: _Same Program, Four Interpretations_

A comprehensive example demonstrating algebraic effect handlers with the Free monad.

---

~~~admonish info title="What You'll Learn"
- How to define effect algebras with sealed interfaces and `@EffectAlgebra`
- How to compose multiple effects using EitherF and Inject
- How to write programs with `Free.flatMap` chains including error recovery
- How to interpret the same program for production, testing, quoting, and auditing
- Why this approach offers guarantees that dependency injection cannot
~~~

~~~admonish example title="Run It Now"
```bash
./gradlew :hkj-examples:run \
  -PmainClass=org.higherkindedj.example.payment.PaymentProcessingExample
```
Runs all four interpretation strategies (production, testing, quote, high-risk decline) on
the same payment program and prints the results.

[View source on GitHub](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/payment/PaymentProcessingExample.java)
~~~

---

## The Problem

A payment system interacts with four external systems:

1. **Payment Gateway**: authorises and charges cards (Stripe, Adyen)
2. **Fraud Detection**: assesses transaction risk (ML model)
3. **Accounting Ledger**: records financial transactions (database)
4. **Notifications**: sends receipts and alerts (email, push)

```
┌──────────────────────────────────────────────────────────────────────┐
│                      PAYMENT PROCESSING FLOW                         │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│   Fraud Check ──▶ Balance Check ──▶ Decision                         │
│       │                │               │                             │
│       ▼                ▼               ▼                             │
│   RiskScore          Money        High risk? ──▶ Alert + Decline     │
│                                   Low funds? ──▶ Decline             │
│                                   OK? ────────────────┐              │
│                                                       ▼              │
│                                                    Charge            │
│                                                       │              │
│                                                       ▼              │
│                                               Record Ledger          │
│                                                       │              │
│                                                       ▼              │
│                                               Send Receipt           │
│                                              (recovered on           │
│                                                failure)              │
│                                                       │              │
│                                                       ▼              │
│                                                    Result            │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

The business needs to run the **same logic** in different modes:

| Mode | Target Monad | Purpose |
|---|---|---|
| Production | `IO` | Real services, deferred execution |
| Testing | `Id` | Pure, synchronous, no mocks |
| Quote | `Id` | Fee estimation without charging |
| High Risk | `Id` | Demonstrates risk-based decline |

Traditional dependency injection can swap implementations, but it cannot:

- **Inspect** the program before execution (count external calls, estimate cost)
- **Guarantee** that all operations are handled (exhaustive interpreter checking)
- **Compose** interpreters structurally (audit logging as a decorator)

Effect handlers provide all three.

---

## Defining the Effects

Each effect algebra is a sealed interface annotated with `@EffectAlgebra`. Operations use
continuation-passing style (CPS): a `Function` parameter maps the natural result type to `A`,
giving the compiler enough information to infer types at every call site.

```java
@EffectAlgebra
public sealed interface PaymentGatewayOp<A>
    permits PaymentGatewayOp.Authorise,
            PaymentGatewayOp.Charge,
            PaymentGatewayOp.Refund {

  <B> PaymentGatewayOp<B> mapK(Function<? super A, ? extends B> f);

  record Authorise<A>(Money amount, PaymentMethod method,
      Function<AuthorisationToken, A> k) implements PaymentGatewayOp<A> {
    @Override
    public <B> PaymentGatewayOp<B> mapK(Function<? super A, ? extends B> f) {
      return new Authorise<>(amount, method, k.andThen(f));
    }
  }

  record Charge<A>(Money amount, PaymentMethod method,
      Function<ChargeResult, A> k) implements PaymentGatewayOp<A> {
    @Override
    public <B> PaymentGatewayOp<B> mapK(Function<? super A, ? extends B> f) {
      return new Charge<>(amount, method, k.andThen(f));
    }
  }
  // Refund follows the same pattern
}
```

The `mapK` method enables the generated Functor to delegate mapping rather than using unsafe
cast-through. The `Function.identity()` continuation means "give me the natural result type
directly."

~~~admonish tip title="What Gets Generated"
The `@EffectAlgebra` processor generates five classes per annotated interface:

`PaymentGatewayOpKind`
: Kind marker + Witness

`PaymentGatewayOpKindHelper`
: widen/narrow conversions

`PaymentGatewayOpFunctor`
: Functor instance (delegates to `mapK`)

`PaymentGatewayOpOps`
: Smart constructors + `Bound` inner class

`PaymentGatewayOpInterpreter`
: Abstract interpreter skeleton
~~~

The four effect algebras are composed via `@ComposeEffects`. The composition uses right-nested
`EitherF` to combine the algebras into a single effect type:

```
┌──────────────────────────────────────────────────────────────────────┐
│                     EFFECT COMPOSITION (EitherF)                     │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│   EitherF< PaymentGatewayOp,                                         │
│            EitherF< FraudCheckOp,                                    │
│                     EitherF< LedgerOp,                               │
│                              NotificationOp >>>                      │
│                                                                      │
│   Interpreters.combine(gateway, fraud, ledger, notification)         │
│       │                                                              │
│       ▼                                                              │
│   Left?  ──▶ gatewayInterpreter.apply()                              │
│   Right? ──▶ Left?  ──▶ fraudInterpreter.apply()                     │
│              Right? ──▶ Left?  ──▶ ledgerInterpreter.apply()         │
│                         Right? ──▶ notificationInterpreter.apply()   │
│                                                                      │
│   PaymentEffectsWiring provides:                                     │
│     injectGateway()      = Left                                      │
│     injectFraud()        = Right > Left                              │
│     injectLedger()       = Right > Right > Left                      │
│     injectNotification() = Right > Right > Right                     │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

`PaymentEffectsWiring` provides inject instances, a composed functor, and a `BoundSet`:

```java
@ComposeEffects
public record PaymentEffects(
    Class<PaymentGatewayOp<?>> gateway,
    Class<FraudCheckOp<?>> fraud,
    Class<LedgerOp<?>> ledger,
    Class<NotificationOp<?>> notification) {}
```

---

## Writing the Program

The `PaymentService` uses constructor-injected `Bound` instances, exactly like Spring bean
injection:

```java
public final class PaymentService<G extends WitnessArity<TypeArity.Unary>> {

  private final PaymentGatewayOpOps.Bound<G> gateway;
  private final FraudCheckOpOps.Bound<G> fraud;
  private final LedgerOpOps.Bound<G> ledger;
  private final NotificationOpOps.Bound<G> notification;

  public Free<G, PaymentResult> processPayment(
      Customer customer, Money amount, PaymentMethod method) {
    // Pure program description, no side effects here
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

The `Function.identity()` continuation tells the generated smart constructor to return the
natural result type directly (`RiskScore`, `Money`, etc.). The program is **data**: it describes
what to do without executing anything.

---

## Production Interpretation

Production interpreters target the `IO` monad and perform real (simulated) side effects.
Each handler applies the operation's continuation `op.k()` to the computed result:

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

Interpreters are combined and the program is interpreted:

```java
var interpreter = Interpreters.combine(
    new ProductionGatewayInterpreter(),
    new ProductionFraudInterpreter(),
    new ProductionLedgerInterpreter(),
    new ProductionNotificationInterpreter());

IO<PaymentResult> io = IOKindHelper.IO_OP.narrow(
    PaymentEffectsWiring.interpret(program, interpreter, IOMonad.INSTANCE));
PaymentResult result = io.unsafeRunSync();
```

---

## Testing Without Mocks

Test interpreters target the `Id` monad for pure, synchronous execution without mock frameworks:

```java
@Test
void processPayment_highRisk_declines() {
    var gateway = new RecordingGatewayInterpreter();
    var fraud = new FixedRiskInterpreter(RiskScore.of(95));
    var ledger = new InMemoryLedgerInterpreter();
    var notification = new CapturingNotificationInterpreter();

    var interpreter = Interpreters.combine(gateway, fraud, ledger, notification);

    PaymentResult result = IdKindHelper.ID.<PaymentResult>narrow(
        PaymentEffectsWiring.interpret(program, interpreter, IdMonad.instance()))
        .value();

    assertThat(result.isDeclined()).isTrue();
    assertThat(gateway.calls()).isEmpty();
    assertThat(notification.alerts()).hasSize(1);
}
```

No mocks. No reflection. No side effects. Pure functional testing.

---

## Quote Mode: Fee Estimation

The `QuoteGatewayInterpreter` calculates processing fees without contacting any payment gateway:

```java
public final class QuoteGatewayInterpreter
    extends PaymentGatewayOpInterpreter<IdKind.Witness> {

  @Override
  protected <A> Kind<IdKind.Witness, A> handleCharge(PaymentGatewayOp.Charge<A> op) {
    BigDecimal fee = op.amount().amount().multiply(FEE_RATE).add(FIXED_FEE);
    Money totalWithFee = new Money(op.amount().amount().add(fee), op.amount().currency());
    return new Id<>(op.k().apply(
        ChargeResult.success(new TransactionId("quote-txn"), totalWithFee)));
  }
}
```

The **same program** now estimates costs instead of charging. No code change required.

---

## Error Recovery Patterns

Recovery is built into the program using `handleError`:

```java
// Send receipt (non-critical, recovered on failure)
Free<G, Unit> safeReceipt = receipt.handleError(
    Throwable.class, _ -> Free.pure(Unit.INSTANCE));
```

The recovery strategy is part of the program description. Whether errors actually occur
depends on the interpreter: a test interpreter that never fails will never trigger recovery,
while a production interpreter with real network calls will.

---

## Inspecting Programs Before Execution

```java
ProgramAnalysis analysis = ProgramAnalyser.analyse(program);

System.out.println(analysis.suspendCount() + " instructions");
System.out.println(analysis.recoveryPoints() + " error recovery points");
System.out.println(analysis.parallelScopes() + " parallel scopes");
```

Because programs are data, they can be traversed and analysed before any side effect occurs.

---

## Comparison with Dependency Injection

```
┌─────────────────────────┐     ┌─────────────────────────┐
│    DEPENDENCY INJECTION │     │    EFFECT HANDLERS      │
├─────────────────────────┤     ├─────────────────────────┤
│                         │     │                         │
│  PaymentService         │     │  processPayment()       │
│    .charge(amount)      │     │    = Free program       │
│    .sendReceipt(...)    │     │    (pure data)          │
│                         │     │         │               │
│  Calls real services    │     │         ▼               │
│  immediately.           │     │  ┌──────────────┐       │
│  Cannot inspect.        │     │  │  foldMap with│       │
│  Cannot replay.         │     │  │  interpreter │       │
│                         │     │  └──────┬───────┘       │
│                         │     │    ┌────┼────┐          │
│                         │     │    ▼    ▼    ▼          │
│                         │     │   IO   Id  Writer       │
│                         │     │  prod  test audit       │
│                         │     │                         │
└─────────────────────────┘     └─────────────────────────┘
```

| Capability | Spring DI | Free Effects |
|---|---|---|
| Swap implementations | Yes | Yes |
| Test without mocks | No (needs Mockito) | Yes |
| Inspect before execution | No | Yes |
| Exhaustive handler checking | No | Yes (`@Handles`) |
| Compositional decoration | Limited (AOP) | Yes (interpreter wrapping) |
| Multiple interpretation modes | Manual wiring | Built-in |

---

Previous: [Portfolio Risk Analysis](examples_portfolio_risk.md)
