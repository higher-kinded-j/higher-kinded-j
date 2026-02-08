# The EitherT Transformer:
## _Typed Errors in Any Context_

> *"It is not down on any map; true places never are."*
>
> – Herman Melville, *Moby-Dick*

The Either inside a Future exists in a place Java's type system cannot natively map to. EitherT creates the map.

~~~admonish info title="What You'll Learn"
- How to combine async operations (CompletableFuture) with typed error handling (Either)
- Building workflows that can fail with specific domain errors while remaining async
- Using `fromKind`, `fromEither`, and `liftF` to construct EitherT values
- Real-world order processing with validation, inventory checks, and payment processing
- Why EitherT eliminates "callback hell" in complex async workflows
~~~

~~~ admonish example title="See Example Code:"
[EitherTExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/either_t/EitherTExample.java)
~~~

---

## The Problem: Nested Async Errors

Consider a typical order processing flow. Each step is asynchronous and can fail with a domain error:

```java
// Without EitherT: manual nesting
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

## The Solution: EitherT

```java
// With EitherT: flat composition
Kind<W, Receipt> processOrder(OrderData data) {
    return For.from(eitherTMonad, EitherT.fromKind(validateOrder(data)))
        .from(validated -> EitherT.fromKind(checkInventory(validated)))
        .from(inventory -> EitherT.fromKind(processPayment(inventory)))
        .from(payment -> EitherT.fromKind(createReceipt(payment)))
        .yield((v, i, p, r) -> r);
}
```

Same four steps. No manual error propagation. If any step returns `Left`, subsequent steps are skipped automatically. The error type `DomainError` flows through the entire chain.

### The Railway View

<pre style="line-height:1.5;font-size:0.95em">
    <span style="color:#4CAF50"><b>Right</b>  ═══●══════════●══════════●══════════●═══▶  Receipt</span>
    <span style="color:#4CAF50">       validate   inventory   payment   receipt</span>
    <span style="color:#4CAF50">       (flatMap)  (flatMap)   (flatMap)  (map)</span>
                ╲            ╲            ╲
                 ╲            ╲            ╲  Left: skip remaining steps
                  ╲            ╲            ╲
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

<pre style="line-height:1.4;font-size:0.95em">
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
    │  flatMap ──▶ sequences F, then routes on Either         │
    │  map ──────▶ transforms <span style="color:#4CAF50">Right(value)</span> only               │
    │  raiseError ──▶ creates <span style="color:#F44336">Left(error)</span> in F                │
    │  handleErrorWith ──▶ recovers from <span style="color:#F44336">Left</span>                 │
    └──────────────────────────────────────────────────────────┘
</pre>

![eithert_transformer.svg](../images/puml/eithert_transformer.svg)

* **`F`**: The witness type of the **outer monad** (e.g., `CompletableFutureKind.Witness`). This monad handles the primary effect.
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
// F = CompletableFutureKind.Witness, L = DomainError
MonadError<CompletableFutureKind.Witness, Throwable> futureMonad = CompletableFutureMonad.INSTANCE;

MonadError<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, DomainError> eitherTMonad =
    new EitherTMonad<>(futureMonad);
```

~~~admonish note title="Type Witness and Helpers"
**Witness Type:** `EitherTKind<F, L, R>` extends `Kind<EitherTKind.Witness<F, L>, R>`. The types `F` and `L` are fixed for a given context; `R` is the variable value type.

**KindHelper:** `EitherTKindHelper` provides `EITHER_T.widen` and `EITHER_T.narrow` for safe conversion between the concrete `EitherT<F, L, R>` and its `Kind` representation.

```java
// Widen: concrete → Kind
Kind<EitherTKind.Witness<F, L>, R> kind = EITHER_T.widen(eitherT);

// Narrow: Kind → concrete
EitherT<F, L, R> concrete = EITHER_T.narrow(kind);
```
~~~

---

## Key Operations

~~~admonish info title="Key Operations with _EitherTMonad_:"
* **`eitherTMonad.of(value)`:** Lifts a pure value `A` into the `EitherT` context. Result: `F<Right(A)>`.
* **`eitherTMonad.map(f, eitherTKind)`:** Applies function `A -> B` to the `Right` value inside the nested structure. If `Left`, the error propagates unchanged. Result: `F<Either<L, B>>`.
* **`eitherTMonad.flatMap(f, eitherTKind)`:** The core sequencing operation. Takes `A -> Kind<EitherTKind.Witness<F, L>, B>`:
  * If `Left(l)`, propagates `F<Left(l)>` (subsequent steps skipped)
  * If `Right(a)`, applies `f(a)` to get the next `EitherT<F, L, B>`
* **`eitherTMonad.raiseError(errorL)`:** Creates an `EitherT` representing a failure. Result: `F<Left(L)>`.
* **`eitherTMonad.handleErrorWith(eitherTKind, handler)`:** Handles a failure `L` from the inner `Either`. If `Right(a)`, propagates unchanged. If `Left(l)`, applies `handler(l)` to attempt recovery.
~~~

---

## Creating EitherT Instances

~~~admonish title="Creating _EitherT_ Instances"
`EitherT` provides several factory methods for different starting points:

```java
Monad<OptionalKind.Witness> optMonad = OptionalMonad.INSTANCE;

// 1. From a pure Right value: F<Right(value)>
EitherT<OptionalKind.Witness, String, String> etRight =
    EitherT.right(optMonad, "OK");

// 2. From a pure Left value: F<Left(error)>
EitherT<OptionalKind.Witness, String, Integer> etLeft =
    EitherT.left(optMonad, "FAILED");

// 3. From an existing Either: F<Either(input)>
Either<String, String> plainEither = Either.left("FAILED");
EitherT<OptionalKind.Witness, String, String> etFromEither =
    EitherT.fromEither(optMonad, plainEither);

// 4. Lifting an outer monad value F<R> → F<Right(R)>
Kind<OptionalKind.Witness, Integer> outerOptional =
    OPTIONAL.widen(Optional.of(123));
EitherT<OptionalKind.Witness, String, Integer> etLiftF =
    EitherT.liftF(optMonad, outerOptional);

// 5. Wrapping an existing nested Kind F<Either<L, R>>
Kind<OptionalKind.Witness, Either<String, String>> nestedKind =
    OPTIONAL.widen(Optional.of(Either.right("OK")));
EitherT<OptionalKind.Witness, String, String> etFromKind =
    EitherT.fromKind(nestedKind);

// Accessing the wrapped value:
Kind<OptionalKind.Witness, Either<String, String>> wrappedValue = etRight.value();
Optional<Either<String, String>> unwrappedOptional = OPTIONAL.narrow(wrappedValue);
// → Optional.of(Either.right("OK"))
```
~~~

---

## Real-World Example: Async Workflow with Error Handling

~~~admonish Example title="Async Workflow with Error Handling"

- [EitherTExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/either_t/EitherTExample.java)

**The problem:** You need to validate input, process it asynchronously, and handle domain-specific errors at each step, all without nested `thenCompose`/`fold` chains.

**The solution:**

```java
public class EitherTExample {

  record DomainError(String message) {}
  record ValidatedData(String data) {}
  record ProcessedData(String data) {}

  MonadError<CompletableFutureKind.Witness, Throwable> futureMonad =
      CompletableFutureMonad.INSTANCE;
  MonadError<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
      DomainError> eitherTMonad = new EitherTMonad<>(futureMonad);

  // Sync validation returning Either
  Kind<EitherKind.Witness<DomainError>, ValidatedData> validateSync(String input) {
    if (input.isEmpty()) {
      return EITHER.widen(Either.left(new DomainError("Input empty")));
    }
    return EITHER.widen(Either.right(new ValidatedData("Validated:" + input)));
  }

  // Async processing returning Future<Either>
  Kind<CompletableFutureKind.Witness, Either<DomainError, ProcessedData>>
      processAsync(ValidatedData vd) {
    CompletableFuture<Either<DomainError, ProcessedData>> future =
        CompletableFuture.supplyAsync(() -> {
          if (vd.data().contains("fail")) {
            return Either.left(new DomainError("Processing failed"));
          }
          return Either.right(new ProcessedData("Processed:" + vd.data()));
        });
    return FUTURE.widen(future);
  }

  Kind<CompletableFutureKind.Witness, Either<DomainError, ProcessedData>>
      runWorkflow(String input) {
    // Lift initial value into EitherT
    var initialET = eitherTMonad.of(input);

    // Step 1: Validate (sync Either → EitherT)
    var validatedET = eitherTMonad.flatMap(
        in -> EitherT.fromEither(futureMonad, EITHER.narrow(validateSync(in))),
        initialET);

    // Step 2: Process (async Future<Either> → EitherT)
    var processedET = eitherTMonad.flatMap(
        vd -> EitherT.fromKind(processAsync(vd)),
        validatedET);

    // Unwrap to get Future<Either>
    return EITHER_T.narrow(processedET).value();
  }
}
```

**Why this works:** Each step produces an `EitherT` value. The `flatMap` handles both the `CompletableFuture` sequencing and the `Either` error propagation. If validation returns `Left`, processing is skipped entirely. No manual error checking at any point.
~~~

---

## Advanced Example: Error Recovery

~~~admonish Example title="Using _EitherTMonad_ for Sequencing and Error Handling"

- [OrderWorkflowRunner.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/workflow/OrderWorkflowRunner.java)

**The problem:** A shipping step might fail with a temporary error, and you want to recover by using a default shipping option rather than failing the entire workflow.

**The solution:**

```java
// Attempt shipment
Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
    ShipmentInfo> shipmentAttemptET =
    EitherT.fromKind(steps.createShipmentAsync(orderId, address));

// Recover from specific errors
Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>,
    ShipmentInfo> recoveredShipmentET =
    eitherTMonad.handleErrorWith(
        shipmentAttemptET,
        error -> {
            if (error instanceof DomainError.ShippingError se
                    && "Temporary Glitch".equals(se.reason())) {
                // Recoverable: use default shipping
                return eitherTMonad.of(new ShipmentInfo("DEFAULT_SHIPPING_USED"));
            } else {
                // Non-recoverable: re-raise
                return eitherTMonad.raiseError(error);
            }
        });
```

The `handleErrorWith` only fires when the inner `Either` is `Left`. The outer `CompletableFuture` context is preserved throughout.
~~~

---

~~~admonish warning title="Common Mistakes"
- **Mixing up `fromEither` and `fromKind`:** Use `fromEither` when you have a plain `Either<L, R>` (e.g., from a synchronous validation). Use `fromKind` when you have `Kind<F, Either<L, R>>` (e.g., from an async operation that already returns `Future<Either>`).
- **Forgetting `.value()`:** The final result of an `EitherT` chain is still an `EitherT`. Call `.value()` to extract the underlying `Kind<F, Either<L, R>>` when you need to interact with the outer monad directly.
~~~

---

~~~admonish note title="Why Higher-Kinded Types Matter Here"
Without HKT simulation, you would need a separate transformer for each outer monad: `EitherTOptional`, `EitherTFuture`, `EitherTIO`, and so on. Higher-Kinded-J's `Kind<F, A>` interface means there is **one** `EitherT` and **one** `EitherTMonad` that works generically for any outer monad `F` for which a `Monad<F>` instance exists. You provide the outer monad at construction time; the transformer does the rest.
~~~

---

~~~admonish tip title="See Also"
- [Monad Transformers](transformers.md) - General concept and choosing the right transformer
- [OptionalT](optionalt_transformer.md) - When your inner effect is absence rather than typed errors
- [Either Monad](../monads/either_monad.md) - The underlying Either type
- [Order Processing Walkthrough](../hkts/order-walkthrough.md) - Complete EitherT example with CompletableFuture
~~~

---

~~~admonish tip title="Further Reading"
- [Railway Oriented Programming](https://dev.tube/video/fYo3LN9Vf_M) - Scott Wlaschin's classic talk on functional error handling (60 min watch)
~~~


**Previous:** [Monad Transformers](transformers.md)
**Next:** [OptionalT](optionalt_transformer.md)
