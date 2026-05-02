# Capstone: A Multi-Capability Workflow

> *"The chief end of man is to compose effects."*
>
> -- A modernised paraphrase of Westminster, with apologies

~~~admonish info title="What You'll Learn"
- How a real workflow combines typed errors, configuration, audit, and async execution
- A complete before/after comparison: imperative Java vs MTL vs Effect Path API
- Why the MTL capability style is the right answer for polymorphic library code
- When the Effect Path API equivalent reads more naturally for application code
~~~

---

## The Scenario

You are writing the order-processing layer of an e-commerce service. Every order goes through three steps:

1. **Validate** the order (sync, may fail with `InvalidOrder`)
2. **Reserve** stock against an inventory service (async, reads the inventory URL from config, may fail with `OutOfStock`)
3. **Charge** the customer through a payment gateway (async, reads the gateway URL and API key from config, may fail with `PaymentDeclined`)

Each step appends an audit entry to a running log so the operations team can reconstruct what happened to any order.

```java
record AppConfig(String inventoryUrl, String paymentUrl, String apiKey) {}

record AuditEntry(String step, String detail) {}

record Order(String id, String sku, int quantity, double amount) {}

record Receipt(String orderId, String confirmationCode) {}

sealed interface DomainError {
    record InvalidOrder(String reason) implements DomainError {}
    record OutOfStock(String sku) implements DomainError {}
    record PaymentDeclined(String reason) implements DomainError {}
}
```

The pipeline must:

- Read `AppConfig` for service URLs and the API key
- Append an `AuditEntry` at each step
- Short-circuit with the appropriate `DomainError` if any step fails
- Run asynchronously throughout

---

## The Imperative Approach

The straightforward Java solution threads three concerns through every signature:

```java
CompletableFuture<Either<DomainError, Receipt>> processOrder(
        Order order, AppConfig config, List<AuditEntry> log) {

    // 1. Validate
    if (order.quantity() <= 0) {
        log.add(new AuditEntry("validate", "rejected: quantity must be positive"));
        return CompletableFuture.completedFuture(
            Either.left(new DomainError.InvalidOrder("quantity must be positive")));
    }
    log.add(new AuditEntry("validate", "ok: " + order.id()));

    // 2. Reserve stock (async)
    return reserveAsync(config.inventoryUrl(), order.sku(), order.quantity())
        .thenCompose(reserveResult -> {
            if (reserveResult.isLeft()) {
                log.add(new AuditEntry("reserve", "failed: " + reserveResult.getLeft()));
                return CompletableFuture.completedFuture(Either.left(reserveResult.getLeft()));
            }
            log.add(new AuditEntry("reserve", "ok: sku=" + order.sku()));

            // 3. Charge (async)
            return chargeAsync(config.paymentUrl(), config.apiKey(), order.amount())
                .thenApply(chargeResult -> {
                    if (chargeResult.isLeft()) {
                        log.add(new AuditEntry("charge", "declined"));
                        return Either.<DomainError, Receipt>left(chargeResult.getLeft());
                    }
                    log.add(new AuditEntry("charge", "ok: " + chargeResult.getRight()));
                    return Either.<DomainError, Receipt>right(
                        new Receipt(order.id(), chargeResult.getRight()));
                });
        });
}
```

The business logic ("validate, then reserve, then charge") is buried under explicit `thenCompose`/`thenApply` plumbing. The audit log is a mutable parameter that every caller must remember to thread. The `AppConfig` is passed through every helper. Three concerns crash through every line.

---

## The MTL Approach

When the same logic must run against different concrete stacks (production async, synchronous tests, audit-only interpreter), write it once against capability interfaces:

```java
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.MonadReader;
import org.higherkindedj.hkt.MonadWriter;
import org.higherkindedj.hkt.expression.For;

<F extends WitnessArity<TypeArity.Unary>> Kind<F, Receipt> processOrder(
        Order order,
        MonadReader<F, AppConfig> env,
        MonadWriter<F, List<AuditEntry>> audit,
        MonadError<F, DomainError> errors) {

    return For.from(env, validate(order, errors, audit))
        .from(_ -> env.ask())
        .from((_, config) -> reserve(order, config.inventoryUrl(), errors, audit))
        .from((_, config, _) -> charge(order, config, errors, audit))
        .yield((_, _, _, code) -> new Receipt(order.id(), code));
}
```

The function declares exactly the capabilities it needs: read-only `AppConfig`, append-only `List<AuditEntry>`, typed error of `DomainError`. It says nothing about *how* those capabilities are assembled, only *that* they are available.

The helper steps follow the same shape:

```java
<F extends WitnessArity<TypeArity.Unary>> Kind<F, Unit> validate(
        Order order,
        MonadError<F, DomainError> errors,
        MonadWriter<F, List<AuditEntry>> audit) {
    if (order.quantity() <= 0) {
        return For.from(audit, audit.tell(List.of(new AuditEntry("validate", "rejected"))))
            .from(_ -> errors.raiseError(new DomainError.InvalidOrder("quantity must be positive")))
            .yield((_, e) -> e);
    }
    return audit.tell(List.of(new AuditEntry("validate", "ok: " + order.id())));
}
```

`validate` declares only the two capabilities it actually uses (`MonadError` and `MonadWriter`). It does not see, and cannot accidentally depend on, the `MonadReader` capability that other steps need.

### Running the polymorphic function

To execute `processOrder`, the caller assembles a concrete stack that provides all three capabilities. For tests, a synchronous stack over `Id` is enough:

```java
// Compose three transformers over Id: ReaderT outside, WriterT in the middle, EitherT inside.
// Build the concrete monad once at the boundary; pass it as the three capability views.
var stack = buildTestStack(idMonad, listMonoid);

Kind<TestStack.Witness, Receipt> program =
    processOrder(order, stack.reader(), stack.writer(), stack.errors());

TestStack.Result<Receipt> result = stack.run(program, prodConfig);
//   result.value()  -> Either<DomainError, Receipt>
//   result.audit()  -> List<AuditEntry>
```

The full mechanics of stacking three transformers live in [Combining Capabilities](mtl_combining.md). What matters here is that the *workflow definition* never named a stack: the same `processOrder` function runs against a production stack over `CompletableFuture`, a test stack over `Id`, an audit-only interpreter, or any future stack a caller invents.

---

## The Effect Path Approach

For workflows where you do not need stack polymorphism (most application code), the Effect Path API expresses the same pipeline more directly. Each capability has its own Path type, and a `ForPath` comprehension threads them together:

```java
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.WriterPath;

EitherPath<DomainError, Receipt> processOrder(Order order, AppConfig config) {
    return validate(order)
        .via(_ -> reserve(order, config.inventoryUrl()))
        .via(_ -> charge(order, config))
        .map(code -> new Receipt(order.id(), code));
}

EitherPath<DomainError, Unit> validate(Order order) {
    return order.quantity() <= 0
        ? Path.<DomainError, Unit>left(new DomainError.InvalidOrder("quantity must be positive"))
        : Path.right(Unit.INSTANCE);
}
```

`EitherPath` carries the typed errors. The audit log can be threaded with a separate `WriterPath` chain, or recorded out-of-band via a logging context. Configuration is passed as a plain parameter because it does not vary mid-workflow; if you need to compose it through `For`, swap in `ReaderPath<AppConfig, A>`.

The Path API trades the polymorphism of MTL for shorter, more concrete code. When you control the call site and know the outer monad, that is usually a better trade.

---

## What Happened

The railway diagram for the success-and-failure tracks of the polymorphic version:

<pre style="line-height:1.5;font-size:0.95em">
    <span style="color:#4CAF50"><b>Success</b> ═══●══════════════●══════════════●══════════●═══▶  Receipt</span>
    <span style="color:#4CAF50">         validate       reserve         charge       map</span>
    <span style="color:#4CAF50">         (errors)        (env+errors)    (env+errors)</span>
              │                │                │
              ▼ <i>tell</i>          ▼ <i>tell</i>          ▼ <i>tell</i>
    <span style="color:#FFB300"><b>Audit</b>   ──●──────────────●──────────────●─────────────────▶  List&lt;AuditEntry&gt;</span>
    <span style="color:#FFB300">         "validate ok"  "reserve ok"   "charge ok"</span>
              ╲                ╲                ╲
               ╲                ╲                ╲  Left: skip remaining steps
                ╲                ╲                ╲
    <span style="color:#F44336"><b>Failure</b> ──●────────────────●──────────────●────────────────▶  DomainError</span>
    <span style="color:#F44336">         InvalidOrder    OutOfStock     PaymentDeclined</span>
</pre>

The audit track and the value track advance together. The error track absorbs any `Left` from any step and short-circuits the rest. All three concerns coexist in one comprehension because the capabilities composed through MTL share the same monad witness `F`.

---

## Side-by-side

| Aspect | Imperative | MTL polymorphic | Effect Path |
|--------|-----------|------------------|-------------|
| Lines of plumbing | ~25 | ~10 | ~6 |
| Stack polymorphism | None | Full | None |
| Capability declarations | Implicit (parameters) | Explicit (interfaces) | Implicit (Path types) |
| Reusable across stacks | Rewrite | Yes | No |
| Best for | Small, throwaway code | Library code | Application code |

The MTL version pays for its polymorphism with explicit capability parameters and the cost of constructing a concrete stack at the call site. That is the right cost when callers will plug their own outer monad underneath your function: you cannot know in advance whether they want `CompletableFuture`, `IO`, `VTask`, or something else.

The Effect Path version pays nothing for that polymorphism it never uses, and reads more directly. When you control the runtime, that is the better trade.

---

## Key Takeaways

~~~admonish info title="Key Takeaways"
* **MTL is the polymorphism story.** When the same business logic must run against many concrete stacks, declare capabilities on the function signature and let the caller assemble a stack that satisfies them. The function never names a transformer.
* **Effect Paths are the directness story.** When you control the call site and know the outer monad, the Path API expresses the same workflow with less ceremony. Most application code lives here.
* **Multiple concerns ride together.** The audit track, the configuration track, and the error track all compose through the same `For` body because every step shares one monad witness. The body of the comprehension reads like ordinary sequential code.
* **The cost of polymorphism is the cost of stack assembly.** The MTL function is short. Constructing a concrete stack that implements `MonadReader<F, AppConfig>`, `MonadWriter<F, List<AuditEntry>>`, and `MonadError<F, DomainError>` over `CompletableFuture` is not. Reach for MTL when that cost buys you something the Path API cannot.
~~~

~~~admonish tip title="See Also"
- [MTL Capabilities](mtl_capabilities.md), the conceptual reference for capability-based effects
- [Combining Capabilities](mtl_combining.md), the full mechanics of multi-capability functions and concrete instances
- [Stack Archetypes](archetypes.md), seven named patterns covering most enterprise composition needs
- [Effect Path API Capstone](../effect/capstone_focus_effect.md), the equivalent worked example for the Path API
- [Migration Cookbook](migration_cookbook.md), side-by-side translations between the styles shown here
~~~

---

**Previous:** [Common Compiler Errors](common_errors.md)
**Next:** [Foundations](../hkts/foundations_intro.md)
