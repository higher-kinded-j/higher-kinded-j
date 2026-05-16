# The EitherT Transformer:
## _Typed Errors in Any Context_

> *"It is not down on any map; true places never are."*
>
> – Herman Melville, *Moby-Dick*

The Either inside a Future exists in a place Java's type system cannot natively map to. EitherT creates the map.

~~~admonish info title="What You'll Learn"
- How to combine async operations (`CompletableFuture`) with typed error handling (`Either`)
- Building workflows that can fail with specific domain errors while remaining async
- Using `For` comprehensions to keep witness types localised and the body readable
- Real-world order processing with validation, inventory checks, and payment processing
- When to use the [`EitherPath`](../effect/path_either.md) Path type instead of raw `EitherT`
~~~

~~~admonish example title="See Example Code"
[EitherTExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/either_t/EitherTExample.java)
~~~

~~~admonish note title="Path First, Stack Later"
For most use cases, [`EitherPath<E, A>`](../effect/path_either.md) is the better starting point. It wraps `EitherT` in a fluent API, hides the witness types, and removes the `Kind` widening calls.

Reach for raw `EitherT` only when you need to combine typed errors with a specific outer monad (`CompletableFuture`, `IO`, `VTask`, custom) or when you are writing polymorphic library code that names `MonadError<F, E>`. The [Migration Cookbook](migration_cookbook.md) shows the side-by-side translation.
~~~

---

## The Problem: Nested Async Errors

Consider a typical order processing flow. Each step is asynchronous and can fail with a domain error:

```java
CompletableFuture<Either<DomainError, Receipt>> processOrder(OrderData data) {
    return validateOrder(data).thenCompose(eitherValidated ->
        eitherValidated.fold(
            error -> CompletableFuture.completedFuture(Either.left(error)),
            validated -> checkInventory(validated).thenCompose(eitherInventory ->
                eitherInventory.fold(
                    error -> CompletableFuture.completedFuture(Either.left(error)),
                    inventory -> processPayment(inventory).thenCompose(eitherPayment ->
                        eitherPayment.fold(
                            error -> CompletableFuture.completedFuture(Either.left(error)),
                            payment -> createReceipt(payment)
                        ))
                ))
        ));
}
```

Four steps, four levels of nesting, identical error-propagation boilerplate at every level. The actual business logic is buried inside the structure. Add error recovery for a specific step and this becomes nearly unreadable.

---

## The Solution

### With the Effect Path API

The simplest fix is to switch to `EitherPath`. It composes the same way as a transformer but with no witness types in your code:

```java
EitherPath<DomainError, Receipt> processOrder(OrderData data) {
    return Path.either(validateOrder(data))
        .via(validated -> Path.either(checkInventory(validated)))
        .via(inventory -> Path.either(processPayment(inventory)))
        .via(payment   -> Path.either(createReceipt(payment)));
}
```

Use this whenever the outer monad is one Path already wraps.

### With raw `EitherT`

When you need a specific outer monad (here `CompletableFuture`), use `EitherT` with a `For` comprehension:

```java
var futureMonad  = Instances.monadError(completableFuture());
var eitherTMonad = Instances.eitherT(futureMonad);

var workflow = For.from(eitherTMonad, EitherT.fromKind(validateOrder(data)))
    .from(validated -> EitherT.fromKind(checkInventory(validated)))
    .from(inventory -> EitherT.fromKind(processPayment(inventory)))
    .from(payment   -> EitherT.fromKind(createReceipt(payment)))
    .yield((v, i, p, r) -> r);
```

Same four steps. No manual error propagation. If any step returns `Left`, subsequent steps are skipped automatically. The witness type appears once, on the `eitherTMonad` declaration.

---

## The Railway View

<pre class="hkj-railway-diagram">
    <span style="color:#4CAF50"><b>Right</b>  ═══●══════════●══════════●══════════●═══▶  Receipt</span>
    <span style="color:#4CAF50">       validate   inventory   payment   receipt</span>
    <span style="color:#4CAF50">       (flatMap)  (flatMap)   (flatMap)  (map)</span>
              ╲         ╲             ╲
               ╲         ╲             ╲  Left: skip remaining steps
                ╲         ╲             ╲
    <span style="color:#F44336"><b>Left</b>   ─────●─────────●─────────────●──────────▶  DomainError</span>
    <span style="color:#F44336">       InvalidOrder  OutOfStock  PaymentFailed</span>
                                        │
                                   <span style="color:#4CAF50">handleErrorWith</span>    optional recovery
                                        │
    <span style="color:#4CAF50">                                    ●═══▶  recovered Right</span>
</pre>

Each `flatMap` runs inside the outer monad `F` (e.g. `CompletableFuture`). If the inner `Either` is `Left`, subsequent steps are skipped and the error propagates along the lower track. `handleErrorWith` can switch back to the success track for recoverable errors.

---

## How EitherT Works

`EitherT<F, L, R>` wraps a value of type `Kind<F, Either<L, R>>`. It represents a computation within the context `F` that will eventually yield an `Either<L, R>`.

<pre class="hkj-ascii-diagram">
    ┌──────────────────────────────────────────────────────────┐
    │  EitherT&lt;CompletableFutureKind.Witness, Error, Value&gt;    │
    │                                                          │
    │  ┌─── CompletableFuture ──────────────────────────────┐  │
    │  │                                                    │  │
    │  │  ┌─── Either ──────────────────────────────────┐   │  │
    │  │  │                                             │   │  │
    │  │  │   <span style="color:#F44336">Left(error)</span>       │     <span style="color:#4CAF50">Right(value)</span>      │   │  │
    │  │  │                     │                       │   │  │
    │  │  └─────────────────────────────────────────────┘   │  │
    │  │                                                    │  │
    │  └────────────────────────────────────────────────────┘  │
    │                                                          │
    │  flatMap ──▶ sequences F, then routes on Either          │
    │  map ──────▶ transforms <span style="color:#4CAF50">Right(value)</span> only                │
    │  raiseError ──▶ creates <span style="color:#F44336">Left(error)</span> in F                 │
    │  handleErrorWith ──▶ recovers from <span style="color:#F44336">Left</span>                  │
    └──────────────────────────────────────────────────────────┘
</pre>

* **`F`**: The witness type of the **outer monad** (e.g. `CompletableFutureKind.Witness`). This monad handles the primary effect.
* **`L`**: The **Left type** of the inner `Either`, typically the error type.
* **`R`**: The **Right type** of the inner `Either`, typically the success value.

```java
public record EitherT<F, L, R>(@NonNull Kind<F, Either<L, R>> value) {
  /* ... static factories ... */ }
```

---

## Setting Up EitherTMonad

The `EitherTMonad<F, L>` class implements `MonadError<EitherTKind.Witness<F, L>, L>`, providing the standard monadic operations for the combined structure. It requires a `Monad<F>` instance for the outer monad:

```java
var futureMonad  = Instances.monadError(completableFuture());
var eitherTMonad = Instances.eitherT(futureMonad);
```

~~~admonish note title="Working with Kind"
**Witness Type:** `EitherTKind<F, L, R>` extends `Kind<EitherTKind.Witness<F, L>, R>`. The types `F` and `L` are fixed for a given context; `R` is the variable value type.

**KindHelper:** `EitherTKindHelper` provides `EITHER_T.widen` and `EITHER_T.narrow` for safe conversion between the concrete `EitherT<F, L, R>` and its `Kind` representation. You rarely need them when using `For` comprehensions; they appear at the boundaries when interoperating with raw `flatMap` chains or other `Kind`-returning code.

```java
Kind<EitherTKind.Witness<F, L>, R> kind = EITHER_T.widen(eitherT);
EitherT<F, L, R> concrete                = EITHER_T.narrow(kind);
```
~~~

---

## Key Operations

| Operation | Behaviour |
|-----------|-----------|
| `eitherTMonad.of(value)`                          | Lifts a pure value into the `EitherT` context as `F<Right(value)>` |
| `eitherTMonad.map(f, kind)`                       | Applies `A -> B` to the `Right`; `Left` propagates unchanged |
| `eitherTMonad.flatMap(f, kind)`                   | Sequences operations; `Left` short-circuits the rest |
| `eitherTMonad.raiseError(error)`                  | Creates `F<Left(error)>` |
| `eitherTMonad.handleErrorWith(kind, handler)`     | Recovers from a `Left` by applying `handler` |

---

## Creating EitherT Instances

`EitherT` provides several factory methods for different starting points:

```java
var optMonad = Instances.monadError(optional());

// 1. From a pure Right value: F<Right(value)>
var etRight = EitherT.<OptionalKind.Witness, String, String>right(optMonad, "OK");

// 2. From a pure Left value: F<Left(error)>
var etLeft  = EitherT.<OptionalKind.Witness, String, Integer>left(optMonad, "FAILED");

// 3. From an existing Either: F<input>
Either<String, String> plainEither = Either.left("FAILED");
var etFromEither = EitherT.fromEither(optMonad, plainEither);

// 4. Lifting an outer-monad value F<R> into F<Right(R)>
Kind<OptionalKind.Witness, Integer> outerOptional = OPTIONAL.widen(Optional.of(123));
var etLiftF = EitherT.<OptionalKind.Witness, String, Integer>liftF(optMonad, outerOptional);

// 5. Wrapping an existing nested Kind F<Either<L, R>>
Kind<OptionalKind.Witness, Either<String, String>> nestedKind =
    OPTIONAL.widen(Optional.of(Either.right("OK")));
var etFromKind = EitherT.fromKind(nestedKind);
```

`etRight.value()` returns the underlying `Kind<F, Either<L, R>>`, which you narrow back to the outer monad's concrete form when you need the result.

---

## Real-World Example: Async Workflow with Error Handling

~~~admonish example title="Async Workflow with Error Handling"

- [EitherTExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/either_t/EitherTExample.java)

**The problem:** validate input, process it asynchronously, and handle domain-specific errors at each step without nested `thenCompose`/`fold` chains.

**The solution:**

```java
record DomainError(String message) {}
record ValidatedData(String data) {}
record ProcessedData(String data) {}

var futureMonad  = Instances.monadError(completableFuture());
var eitherTMonad = Instances.eitherT(futureMonad);

// Sync validation returning Either
Either<DomainError, ValidatedData> validateSync(String input) {
  return input.isEmpty()
      ? Either.left(new DomainError("Input empty"))
      : Either.right(new ValidatedData("Validated:" + input));
}

// Async processing returning Future<Either>
Kind<CompletableFutureKind.Witness, Either<DomainError, ProcessedData>>
    processAsync(ValidatedData vd) {
  var future = CompletableFuture.supplyAsync(() ->
      vd.data().contains("fail")
          ? Either.<DomainError, ProcessedData>left(new DomainError("Processing failed"))
          : Either.<DomainError, ProcessedData>right(new ProcessedData("Processed:" + vd.data())));
  return FUTURE.widen(future);
}

// Compose with For: validate (sync Either) then process (async Future<Either>)
var workflow = For.from(eitherTMonad, EitherT.fromEither(futureMonad, validateSync(input)))
    .from(validated -> EitherT.fromKind(processAsync(validated)))
    .yield((validated, processed) -> processed);
```

**Why this works:** the `For` comprehension threads each step through `eitherTMonad.flatMap`. The first step lifts a synchronous `Either` into the transformer; the second wraps an existing `Future<Either>`. If validation returns `Left`, processing is skipped and the error propagates through the future.
~~~

---

## Advanced Example: Error Recovery

~~~admonish example title="Error Recovery"

- [OrderWorkflowRunner.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/workflow/OrderWorkflowRunner.java)

**The problem:** a shipping step might fail with a temporary error, and you want to recover by using a default shipping option rather than failing the entire workflow.

**The solution:**

```java
var shipmentAttempt = EitherT.fromKind(steps.createShipmentAsync(orderId, address));

var recoveredShipment = eitherTMonad.handleErrorWith(
    shipmentAttempt,
    error -> error instanceof DomainError.ShippingError se && "Temporary Glitch".equals(se.reason())
        ? eitherTMonad.of(new ShipmentInfo("DEFAULT_SHIPPING_USED"))
        : eitherTMonad.raiseError(error));
```

`EitherT` already implements the `Kind` interface, so it can be passed straight to `handleErrorWith` without an explicit widen. The handler only fires when the inner `Either` is `Left`, and the outer `CompletableFuture` context is preserved throughout.
~~~

---

## Transforming the Outer Monad with `mapT`

Sometimes you need to change the *outer monad* of an `EitherT` without touching the inner `Either` at all. Imagine you have built a pipeline over `CompletableFuture` but now want to continue in a synchronous `Optional` context, or you want to apply a cross-cutting concern (logging, retry) at the monad level.

`mapT` does exactly this. It applies a function to the wrapped `Kind<F, Either<L, R>>` and produces a new `EitherT<G, L, R>`:

```
  EitherT< F , L, R >  ── mapT(f) ──>  EitherT< G , L, R >
       │                                      │
  ┌────┴────┐                            ┌────┴────┐
  │    F    │     f: F[...] -> G[...]    │    G    │
  │ ┌─────┐ │         ====>              │ ┌─────┐ │
  │ │ E   │ │   inner Either untouched   │ │ E   │ │
  │ │ L|R │ │                            │ │ L|R │ │
  │ └─────┘ │                            │ └─────┘ │
  └─────────┘                            └─────────┘
```

```java
EitherT<CompletableFutureKind.Witness, Error, Data> futureET = ...;

var optionalET = futureET.mapT(futureKind -> {
  Either<Error, Data> awaited = FUTURE.join(futureKind);
  return OPTIONAL.widen(Optional.of(awaited));
});
```

~~~admonish note title="mapT vs map"
`map` transforms the *value* inside the `Either` (the `R` in `Right(R)`).
`mapT` transforms the *outer monad* wrapping the `Either`, the `F` in `F<Either<L, R>>`.
They operate at different levels of the transformer stack.
~~~

---

~~~admonish warning title="Common Mistakes"
- **Mixing up `fromEither` and `fromKind`:** use `fromEither` when you have a plain `Either<L, R>` (e.g. from a synchronous validation). Use `fromKind` when you have `Kind<F, Either<L, R>>` (e.g. from an async operation that already returns `Future<Either>`).
- **Forgetting `.value()`:** the final result of an `EitherT` chain is still an `EitherT`. Call `.value()` to extract the underlying `Kind<F, Either<L, R>>` when you need to interact with the outer monad directly.
- **Reaching for the transformer when `EitherPath` would do:** if your outer monad is one Path already wraps, `EitherPath` is shorter, has less ceremony, and reads more naturally. The transformer is the right choice when the outer monad is fixed by an external constraint.
~~~

---

~~~admonish note title="Why Higher-Kinded Types Matter Here"
Without HKT simulation, you would need a separate transformer for each outer monad: `EitherTOptional`, `EitherTFuture`, `EitherTIO`, and so on. Higher-Kinded-J's `Kind<F, A>` interface means there is **one** `EitherT` and **one** `EitherTMonad` that works generically for any outer monad `F` for which a `Monad<F>` instance exists. You provide the outer monad at construction time; the transformer does the rest.
~~~

---

~~~admonish tip title="See Also"
- [EitherPath](../effect/path_either.md) - The Path-API equivalent, recommended for most use cases
- [Stack Archetypes](archetypes.md) - The Service Stack archetype maps directly to `EitherT`/`EitherPath`
- [Migration Cookbook](migration_cookbook.md) - Side-by-side translations
- [Monad Transformers](transformers.md) - General concept and choosing the right transformer
- [OptionalT](optionalt_transformer.md) - When your inner effect is absence rather than typed errors
- [Either Monad](../monads/either_monad.md) - The underlying Either type
- [Order Processing Walkthrough](../hkts/order-walkthrough.md) - Complete EitherT example with CompletableFuture
~~~

---

~~~admonish tip title="Further Reading"
- [Railway Oriented Programming](https://dev.tube/video/fYo3LN9Vf_M) - Scott Wlaschin's classic talk on functional error handling (60 min watch)
~~~

~~~admonish info title="Hands-On Learning"
Practice composing async-and-typed-error workflows in [Tutorial 01: When Path Isn't Enough](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/transformers/Tutorial01_WhenPathIsNotEnough.java) (6 exercises, ~25 minutes).
~~~

---

**Previous:** [Monad Transformers](transformers.md)
**Next:** [OptionalT](optionalt_transformer.md)
