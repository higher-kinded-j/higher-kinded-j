# Order Example Walkthrough

This page provides a detailed walkthrough of the Order Processing example found in the `org.simulation.hkt.example.order` package. This example serves as a practical demonstration of using the HKT simulation, particularly combining `CompletableFuture` for asynchronous operations with `Either` for domain error handling, facilitated by the `EitherT` monad transformer.

**Key Files:**

* [OrderWorkflowRunner.java](../src/main/java/org/simulation/example/order/workflow/OrderWorkflowRunner.java): Orchestrates the workflow steps.
* [OrderWorkflowSteps.java](../src/main/java/org/simulation/example/order/workflow/OrderWorkflowSteps.java): Defines the individual (sync/async) steps.
* [WorkflowModels.java](../src/main/java/org/simulation/example/order/model/WorkflowModels.java): Contains the data records used (e.g., `OrderData`, `ValidatedOrder`, `WorkflowContext`).
* [DomainError.java](../src/main/java/org/simulation/example/order/error/DomainError.java): Defines the sealed interface for specific business errors.

## The Challenge: Async + Domain Errors

Modern applications often involve sequences of operations where:

1. Some steps are asynchronous (e.g., calling external services like payment gateways, inventory checks, shipping APIs).
2. Steps can fail due to specific, expected business reasons (e.g., invalid input, item out of stock, payment declined).
3. We want to stop processing subsequent steps if an earlier step fails with a business error.
4. We also need to handle unexpected system-level errors (e.g., network timeouts, service unavailability).

Representing this naively often leads to nested structures like `CompletableFuture<Optional<Result>>` or, more appropriately for typed errors, `CompletableFuture<Either<DomainError, Result>>`. Chaining operations on such nested types becomes cumbersome (the "callback hell" or nested `thenCompose`/`thenApply` problem).

## The Solution: `EitherT` Monad Transformer

The `EitherT<F, L, R>` monad transformer is designed precisely for this scenario. It wraps a value of type `Kind<F, Either<L, R>>`.

In our example:

* `F` (Outer Monad) = `CompletableFutureKind<?>`: Handles the asynchronicity and system errors (`Throwable`). We use `CompletableFutureMonadError`.
* `L` (Left Type) = `DomainError`: Represents specific business rule violations (validation, stock, payment, shipping errors).
* `R` (Right Type) = The successful result type of a step (e.g., `ValidatedOrder`, `PaymentConfirmation`, `Void`).

We create an `EitherTMonad` instance, providing it with the `Monad` instance for the outer context (`CompletableFutureMonadError`):

**Java**

```
// In OrderWorkflowRunner
MonadError<CompletableFutureKind<?>, Throwable> futureMonad = new CompletableFutureMonadError();
MonadError<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, DomainError> eitherTMonad
    = new EitherTMonad<>(futureMonad);
```

This `eitherTMonad` now allows us to use `map`, `flatMap`, `handleErrorWith` etc., directly on the `EitherT` structure. These operations automatically handle both:

1. **Asynchronous Sequencing:** Delegated to the outer `futureMonad`'s `map`/`flatMap`.
2. **Either Short-Circuiting:** The logic within `EitherTMonad` ensures that if an inner `Either` becomes `Left(domainError)`, subsequent `flatMap` operations are skipped, propagating the `Left` value within the `CompletableFuture`.

## Workflow Breakdown (`runOrderWorkflowEitherT`)

The `runOrderWorkflowEitherT` method in `OrderWorkflowRunner` shows the flow:

1. **Initialization:**
   * An initial `WorkflowContext` is created.
   * It's lifted into the `EitherT` context using `eitherTMonad.of()`, resulting in `Kind<EitherTKind<...>, WorkflowContext>` which represents `CompletableFuture<Right(initialContext)>`.
2. **Step 1: Validate Order (Sync - Either):**
   * Uses `eitherTMonad.flatMap`. The lambda receives the `WorkflowContext`.
   * Calls the *synchronous*`steps.validateOrder()`, which returns `Kind<EitherKind<DomainError, ?>, ValidatedOrder>`.
   * This `Either` result is unwrapped and then lifted *back* into the `EitherT` context using `EitherT.fromEither(futureMonad, syncResultEither)`. This wraps the immediate `Either` result inside a completed `CompletableFuture`.
   * If the lifted `EitherT` represents success (`Right(validatedOrder)`), `eitherTMonad.map` is used to update the context (`ctx.withValidatedOrder(validatedOrder)`).
   * If validation failed (`Left(validationError)`), the `flatMap` propagates the `Left` state within the `CompletableFuture`.
3. **Step 2: Check Inventory (Async):**
   * Uses `eitherTMonad.flatMap` again. If the previous step resulted in `Left`, this lambda is skipped.
   * Calls the *asynchronous*`steps.checkInventoryAsync()`, which returns `Kind<CompletableFutureKind<?>, Either<DomainError, Void>>`.
   * This `Kind` is directly wrapped into `EitherT` using `EitherT.fromKind()`.
   * If the inventory check succeeds (`Right(Void)`), `eitherTMonad.map` updates the context (`ctx.withInventoryChecked()`).
   * If it fails (`Left(stockError)`), the `flatMap` propagates the `Left`.
4. **Step 3: Process Payment (Async):**
   * Similar to Step 2, using `flatMap` and `EitherT.fromKind` to integrate the async result `Kind<CompletableFutureKind<?>, Either<DomainError, PaymentConfirmation>>`.
   * Uses `map` to update the context with `PaymentConfirmation`.
5. **Step 4: Create Shipment (Async with Recovery):**
   * Similar structure using `flatMap` and `EitherT.fromKind`.
   * Crucially, it uses `eitherTMonad.handleErrorWith` on the result of the shipment step (`shipmentAttemptET`).
   * **Pattern matching (`switch`)** is used inside the handler to check if the `DomainError` is a recoverable `ShippingError`.
     * If recoverable, it returns `eitherTMonad.of(new ShipmentInfo("DEFAULT_SHIPPING_USED"))`, effectively replacing the `Left(shippingError)` with a `Right(defaultShipmentInfo)` inside the `CompletableFuture`.
     * If not recoverable, it re-raises the error using `eitherTMonad.raiseError(error)`, propagating the original `Left`.
   * `map` updates the context with the (potentially recovered) `ShipmentInfo`.
6. **Step 5: Final Mapping:**
   * Uses `eitherTMonad.map` to transform the final successful `WorkflowContext` into a `FinalResult`. This lambda only executes if all preceding steps succeeded (or were recovered).
7. **Step 6: Notification (Optional Side-Effect):**
   * Uses `flatMap` again. If the main workflow succeeded, it attempts notification.
   * Uses `handleError` to catch potential `DomainError` during notification (e.g., customer unreachable) but recovers immediately by returning `null` (for `Void`), allowing the main `FinalResult` to proceed even if notification fails. This demonstrates recovering from errors in non-critical final steps.
8. **Unwrapping:**
   * Finally, `finalET.value()` unwraps the `EitherT` back to the underlying `Kind<CompletableFutureKind<?>, Either<DomainError, FinalResult>>`, which is the method's return type. The caller can then `unwrap` this to get the `CompletableFuture` and handle its final `Either` result.

## Validation with `Try` (`runOrderWorkflowEitherTWithTryValidation`)

This alternative runner method demonstrates handling a synchronous step (`validateOrderWithTry`) that might throw exceptions instead of returning `Either`.

* Inside the first `flatMap`, `steps.validateOrderWithTry()` returns `Kind<TryKind<?>, ValidatedOrder>`.
* This `Try` is unwrapped and then explicitly converted to an `Either<DomainError, ValidatedOrder>` using `tryResult.fold(...)`. A `Try.Failure` is mapped to `Either.Left(new ValidationError(...))`.
* The rest of the workflow proceeds identically, as the result is lifted back into `EitherT` using `EitherT.fromEither`.

This shows how to integrate exception-throwing synchronous code into the `EitherT`-based workflow by converting the `Try` result to `Either`.

## Benefits Demonstrated

* **Unified Error Channel:**`EitherT` provides a single monadic structure to handle both asynchronous operations (`CompletableFuture`) and domain errors (`Either`).
* **Composability:**`flatMap` allows sequencing steps naturally, regardless of whether they are sync or async, or return `Either` or `Try` (after conversion).
* **Automatic Short-Circuiting:**`flatMap` automatically propagates `Left` values, stopping subsequent computations.
* **Explicit Error Handling:**`MonadError` (`handleErrorWith`) provides a structured way to inspect and potentially recover from specific `DomainError`s, enhanced with pattern matching.
* **Separation of Concerns:** Asynchronicity is handled by `CompletableFuture` (the outer monad `F`), while domain errors are handled by `Either` (the inner structure), managed together by `EitherT`.
