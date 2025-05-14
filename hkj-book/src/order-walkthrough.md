# Order Workflow Example: Async Operations with Error Handling

This document provides a detailed walkthrough of the Order Processing example found in the `org.higherkindedj.example.order` package. This example is a practical demonstration of how to use the Higher-Kinded-J library to manage a common real-world scenario: **a workflow involving asynchronous operations that can also fail with specific, expected business errors.**

**Goal of this Example:**

* To show how to compose asynchronous steps (using `CompletableFuture`) with steps that might result in domain-specific errors (using `Either`).
* To introduce the **`EitherT` monad transformer** as a powerful tool to simplify working with nested structures like `CompletableFuture<Either<DomainError, Result>>`.
* To illustrate how to handle different kinds of errors:
  * **Domain Errors:** Expected business failures (e.g., invalid input, item out of stock) represented by `Either.Left`.
  * **System Errors:** Unexpected issues during async execution (e.g., network timeouts) handled by `CompletableFuture`.
  * **Synchronous Exceptions:** Using `Try` to capture exceptions from synchronous code and integrate them into the error handling flow.
* To demonstrate error recovery using `MonadError` capabilities.
* To show how dependencies (like logging) can be managed within the workflow steps.

**Prerequisites:**

Before diving in, it's helpful to have a basic understanding of:

* [Core Concepts](core-concepts.md) of Higher-Kinded-J (`Kind`, Type Classes).
* The specific types being used: [Supported Types](supported-types.md).
* The general [Usage Guide](usage-guide.md).

**Key Files:**

* [`Dependencies.java`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/example/order/workflow/Dependencies.java): Holds external dependencies (e.g., logger).
* [`OrderWorkflowRunner.java`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/example/order/workflow/OrderWorkflowRunner.java): Orchestrates the workflow using `EitherT`.
* [`OrderWorkflowSteps.java`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/example/order/workflow/OrderWorkflowSteps.java): Defines the individual workflow steps (sync/async), accepting `Dependencies`.
* [`WorkflowModels.java`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/example/order/model/WorkflowModels.java): Data records (`OrderData`, `ValidatedOrder`, etc.).
* [`DomainError.java`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/example/order/error/DomainError.java): Sealed interface defining specific business errors.

---

## The Problem: Combining Asynchronicity and Typed Errors

Imagine an online order process:

1. **Validate Order Data:** Check quantity, product ID, etc. (Can fail with `ValidationError`).
2. **Check Inventory:** Call an external inventory service (async). (Can fail with `StockError`).
3. **Process Payment:** Call a payment gateway (async). (Can fail with `PaymentError`).
4. **Create Shipment:** Call a shipping service (async). (Can fail with `ShippingError`).
5. **Notify Customer:** Send an email/SMS (async). (Might fail, but maybe not critically).

We face several challenges:

* **Asynchronicity:** Steps 2, 3, 4, 5 involve network calls and should use `CompletableFuture`.
* **Domain Errors:** Steps can fail for specific business reasons. We want to represent these failures with *types* (like `ValidationError`, `StockError`) rather than just generic exceptions or nulls. `Either<DomainError, SuccessValue>` is a good fit for this.
* **Composition:** How do we chain these steps together? Directly nesting `CompletableFuture<Either<DomainError, ...>>` leads to complex and hard-to-read code (often called "callback hell" or nested `thenCompose`/`thenApply` chains).
* **Short-Circuiting:** If validation fails (returns `Left(ValidationError)`), we shouldn't proceed to check inventory or process payment. The workflow should stop and return the validation error.
* **Dependencies & Logging:** Steps need access to external resources (like service clients, configuration, loggers). How do we manage this cleanly?

## The Solution: `EitherT` Monad Transformer + Dependency Injection

This example tackles these challenges using:

1. **`Either<DomainError, R>`**: To represent the result of steps that can fail with a specific business error (`DomainError`). `Left` holds the error, `Right` holds the success value `R`.
2. **`CompletableFuture<T>`**: To handle the asynchronous nature of external service calls. It also inherently handles system-level exceptions (network timeouts, service unavailability) by completing exceptionally with a `Throwable`.
3. **`EitherT<F_OUTER_WITNESS, L_ERROR, R_VALUE>`**: The key component! This *monad transformer* wraps a nested structure `Kind<F_OUTER_WITNESS, Either<L_ERROR, R_VALUE>>`. In our case:
   * `F_OUTER_WITNESS` (Outer Monad's Witness) = `CompletableFutureKind.Witness` (handling async and system errors `Throwable`).
   * `L_ERROR` (Left Type) = `DomainError` (handling business errors).
   * `R_VALUE` (Right Type) = The success value of a step.
     It provides `map`, `flatMap`, and `handleErrorWith` operations that work seamlessly across *both* the outer `CompletableFuture` context and the inner `Either` context.
4. **Dependency Injection:** A `Dependencies` record holds external collaborators (like a logger). This record is passed to `OrderWorkflowSteps`, making dependencies explicit and testable.
5. **Structured Logging:** Steps use the injected logger (`dependencies.log(...)`) for consistent logging without being tied to a specific implementation like `System.out`.

### Setting up `EitherTMonad`

In `OrderWorkflowRunner`, we get the necessary type class instances:

```java
// Get MonadError instance for CompletableFuture (handles Throwable)
// F_OUTER_WITNESS for CompletableFuture is CompletableFutureKind.Witness
MonadError<CompletableFutureKind.Witness, Throwable> futureMonad =
    new CompletableFutureMonadError();

// Create the EitherTMonad instance, providing the outer monad (futureMonad).
// This instance handles DomainError for the inner Either.
// The HKT witness for EitherT here is EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>
MonadError<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, DomainError> eitherTMonad =
    new EitherTMonad<>(futureMonad);
```

Now, `eitherTMonad` can be used to chain operations on `EitherT` values (which are `Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, A>`). Its `flatMap` method automatically handles:

* **Async Sequencing:** Delegated to `futureMonad.flatMap` (`thenCompose`).
* **Error Short-Circuiting:** If an inner `Either` becomes `Left(domainError)`, subsequent `flatMap` operations are skipped, propagating the `Left` within the `CompletableFuture`.

---

## Workflow Step-by-Step (`runOrderWorkflowEitherT`)

Let's trace the execution flow defined in `OrderWorkflowRunner.runOrderWorkflowEitherT`. The workflow uses `flatMap` on the `eitherTMonad` to sequence steps. The state (`WorkflowContext`) is carried implicitly within the `Right` side of the `EitherT`.

**Initial State:**

```java
WorkflowContext initialContext = WorkflowContext.start(orderData);
// Lift the initial context into EitherT: Represents Future<Right(initialContext)>
Kind<EitherTKind<CompletableFutureKind<?>, DomainError, ?>, WorkflowContext> initialET =
eitherTMonad.of(initialContext);
```

**Step 1: Validate Order (Synchronous, returns `Either`)**

```java
Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, WorkflowContext> validatedET =
    eitherTMonad.flatMap( // Start chaining
        ctx -> { // Lambda receives the context if the previous step was Right
          // 1. Call the synchronous step (logging happens inside)
          //    steps.validateOrder returns Kind<EitherKind.Witness<DomainError>, ValidatedOrder>
          Either<DomainError, ValidatedOrder> syncResultEither =
              EitherKindHelper.unwrap(steps.validateOrder(ctx.initialData()));

          // 2. Lift the sync Either result into the EitherT<Future, ...> context
          //    EitherT.fromEither wraps the Either in a completed Future: Future<Either<...>>
          //    The resulting type is EitherT<CompletableFutureKind.Witness, DomainError, ValidatedOrder>
          //    which is then wrapped as Kind<EitherTKind.Witness<...>, ValidatedOrder>
          Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, ValidatedOrder>
              validatedOrderET = EitherT.fromEither(futureMonad, syncResultEither);

          // 3. If validatedOrderET is Right(vo), map its value to update the context: ctx -> ctx.withValidatedOrder(vo)
          //    If validatedOrderET is Left(error), map is skipped, and Left propagates.
          //    The result is Kind<EitherTKind.Witness<...>, WorkflowContext>
          return eitherTMonad.map(ctx::withValidatedOrder, validatedOrderET);
        },
        initialET // Input to the first flatMap
    );
```

* **Purpose:** Validate basic order data.
* **Sync/Async:** Synchronous.
* **Return Type:**`Either<DomainError, ValidatedOrder>`.
* **HKT Integration:** The `Either` result is unwrapped from its `Kind` and then lifted into the `EitherT<CompletableFuture, ...>` context using `EitherT.fromEither`. This wraps the immediate `Either` in a *completed*`CompletableFuture`.
* **Error Handling:** If validation returns `Left`, the `flatMap` ensures `validatedET` becomes `Future<Left(validationError)>`, and subsequent `flatMap` calls will be skipped.

**Step 2: Check Inventory (Asynchronous)**

```java
Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, WorkflowContext> inventoryET =
    eitherTMonad.flatMap( // Chain from the previous step's result (validatedET)
        ctx -> { // Executed only if validatedET was Right(context)
          // 1. Call the asynchronous step (logging happens inside)
          //    Returns Kind<CompletableFutureKind.Witness, Either<DomainError, Void>>
          Kind<CompletableFutureKind.Witness, Either<DomainError, Void>> inventoryCheckFutureKind =
              steps.checkInventoryAsync(
                  ctx.validatedOrder().productId(), ctx.validatedOrder().quantity());

          // 2. Lift the async result directly into EitherT using fromKind
          //    This directly uses the Future<Either<...>> from the step.
          Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, Void> inventoryCheckET =
              EitherT.fromKind(inventoryCheckFutureKind);

          // 3. If inventoryCheckET resolves to Right(null), map to update context.
          //    If it resolves to Left(stockError), map is skipped, Left propagates.
          return eitherTMonad.map(ignored -> ctx.withInventoryChecked(), inventoryCheckET);
        },
        validatedET // Input is the result of the validation step
    );
```

* **Purpose:** Check if the product is in stock via an external service.
* **Sync/Async:** Asynchronous.
* **Return Type from `steps.checkInventoryAsync`:**`Kind<CompletableFutureKind.Witness, Either<DomainError, Void>>`.
* **HKT Integration:** The `Kind` returned by the async step (which represents `CompletableFuture<Either<...>>`) is directly wrapped into `EitherT` using `EitherT.fromKind`.
* **Error Handling:** If the `CompletableFuture` completes with `Left(stockError)`, this `Left` is propagated by `flatMap`. If the `CompletableFuture` itself fails (e.g., network error), the outer monad (`futureMonad`) handles it, resulting in a failed `CompletableFuture` wrapped in `inventoryET`.

**Step 3: Process Payment (Asynchronous)**

```java
Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, WorkflowContext> paymentET =
    eitherTMonad.flatMap( // Chain from inventory check
        ctx -> { // Executed only if inventory check was Right
          // 1. Call async payment step
          // Returns Kind<CompletableFutureKind.Witness, Either<DomainError, PaymentConfirmation>>
          Kind<CompletableFutureKind.Witness, Either<DomainError, PaymentConfirmation>>
              paymentFutureKind =
              steps.processPaymentAsync(
                  ctx.validatedOrder().paymentDetails(), ctx.validatedOrder().amount());
          // 2. Lift async result into EitherT
          Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, PaymentConfirmation>
              paymentConfirmET = EitherT.fromKind(paymentFutureKind);
          // 3. Map success to update context
          return eitherTMonad.map(ctx::withPaymentConfirmation, paymentConfirmET);
        },
        inventoryET // Input is result of inventory step
    );
```

* **Purpose:** Charge the customer via a payment gateway.
* **Sync/Async:** Asynchronous.
* **Return Type from `steps.processPaymentAsync`:**`Kind<CompletableFutureKind.Witness, Either<DomainError, PaymentConfirmation>>`.
* **HKT Integration:** Same as Step 2, using `EitherT.fromKind`.
* **Error Handling:** Propagates `Left(paymentError)` or underlying `CompletableFuture` failures.

**Step 4: Create Shipment (Asynchronous with Recovery)**

```java
Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, WorkflowContext> shipmentET =
    eitherTMonad.flatMap( // Chain from payment
        ctx -> { // Executed only if payment was Right
          // 1. Call async shipment step
          // Returns Kind<CompletableFutureKind.Witness, Either<DomainError, ShipmentInfo>>
          Kind<CompletableFutureKind.Witness, Either<DomainError, ShipmentInfo>>
              shipmentAttemptFutureKind =
              steps.createShipmentAsync(
                  ctx.validatedOrder().orderId(), ctx.validatedOrder().shippingAddress());
          // 2. Lift async result into EitherT
          Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, ShipmentInfo>
              shipmentAttemptET = EitherT.fromKind(shipmentAttemptFutureKind);

          // *** 3. Error Handling & Recovery ***
          // Use MonadError's handleErrorWith to inspect and potentially recover from DomainErrors
          Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, ShipmentInfo>
              recoveredShipmentET =
              eitherTMonad.handleErrorWith( // Operates on the EitherT value
                  shipmentAttemptET,
                  error -> { // Lambda receives the DomainError if shipmentAttemptET is Left
                    // Check if it's a specific, recoverable error
                    if (error instanceof DomainError.ShippingError se // Pattern matching
                        && "Temporary Glitch".equals(se.reason())) {
                      dependencies.log(
                          "WARN (EitherT): Recovering from temporary shipping glitch...");
                      // Return a *successful* EitherT using 'of'
                      return eitherTMonad.of(new WorkflowModels.ShipmentInfo("DEFAULT_SHIPPING_USED"));
                    } else {
                      // It's a different or non-recoverable error, re-raise it
                      return eitherTMonad.raiseError(error); // Returns a Left EitherT
                    }
                  });
          // 4. Map the potentially recovered result to update context
          return eitherTMonad.map(ctx::withShipmentInfo, recoveredShipmentET);
        },
        paymentET // Input is result of payment step
    );
```

* **Purpose:** Arrange shipment via a shipping service.
* **Sync/Async:** Asynchronous.
* **Return Type from `steps.createShipmentAsync`:**`Kind<CompletableFutureKind.Witness, Either<DomainError, ShipmentInfo>>`.
* **HKT Integration:** Uses `EitherT.fromKind`. Crucially, it then uses `eitherTMonad.handleErrorWith`.
* **Error Handling:** This step demonstrates recovery. If `createShipmentAsync` results in `Left(ShippingError("Temporary Glitch"))`, the `handleErrorWith` block catches this specific `DomainError`. Instead of propagating the error, it returns a *successful*`EitherT` (`eitherTMonad.of(...)`) containing default shipment info. If any other `DomainError` occurs, it's re-raised using `eitherTMonad.raiseError(...)`, propagating the `Left` state. System errors from the `CompletableFuture` are still handled implicitly.

**Step 5: Map to Final Result**

```java
Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, WorkflowModels.FinalResult> finalResultET =
    eitherTMonad.map( // Use map as we are just transforming the success value
        ctx -> { // Executed only if shipment step (or recovery) was Right
            dependencies.log("Mapping final context to FinalResult...");
            return new WorkflowModels.FinalResult( // Create the final success object
                ctx.validatedOrder().orderId(),
                ctx.paymentConfirmation().transactionId(),
                ctx.shipmentInfo().trackingId());
        },
        shipmentET // Input is result of shipment step
    );
```

* **Purpose:** Transform the final successful `WorkflowContext` into the desired `FinalResult`.
* **HKT Integration:** Uses `eitherTMonad.map` because we are only transforming the value within the `Right` case. If `shipmentET` was `Left`, this `map` is skipped.

**Step 6: Notify Customer (Optional Final Step with Recovery)**

```java
Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, WorkflowModels.FinalResult>
    finalResultWithNotificationET =
        eitherTMonad.flatMap( // Use flatMap because notifyCustomerAsync returns an F<Either<...>>
            finalResult -> { // Executed only if previous steps were Right
                // 1. Call async notification step
                // Returns Kind<CompletableFutureKind.Witness, Either<DomainError, Void>>
                Kind<CompletableFutureKind.Witness, Either<DomainError, Void>> notifyFutureKind =
                    steps.notifyCustomerAsync(
                        orderData.customerId(), "Order processed: " + finalResult.orderId());
                // 2. Lift async result into EitherT
                Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, Void> notifyET =
                    EitherT.fromKind(notifyFutureKind);

                // 3. Handle potential notification error, but *always recover*
                // Use handleError (simpler recovery to a pure value for the 'Right' side of Either)
                Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, Void> recoveredNotifyET =
                    eitherTMonad.handleError( // handleError returns Kind<F, A> where A is the value type
                        notifyET,
                        notifyError -> { // Receives DomainError if notifyET is Left
                            dependencies.log(
                                "WARN (EitherT): Notification failed but order succeeded: "
                                    + notifyError.message());
                            return null; // Recover by returning null (for Void in the Right path)
                        });

                // 4. Map the recovered notification result back to the original FinalResult
                // This ensures the overall workflow result remains FinalResult, even if notification fails.
                return eitherTMonad.map(ignoredVoid -> finalResult, recoveredNotifyET);
            },
            finalResultET // Input is the result from Step 5
        );
```

* **Purpose:** Send a notification (e.g., email) to the customer. Failure here shouldn't fail the whole order.
* **Sync/Async:** Asynchronous.
* **HKT Integration:** Uses `flatMap` to sequence the notification, `EitherT.fromKind` to lift the async result, and `handleError` for simple recovery.
* **Error Handling:** If `notifyCustomerAsync` results in a `Left`, `handleError` catches it, logs a warning, and recovers by returning `null` (representing `Void` for the success type `A` of the `Kind`). The final `map` ensures that the successful `FinalResult` from the previous step is preserved and returned, regardless of the notification outcome.

**Final Unwrapping:**

```java
// Cast the final Kind back to EitherT to access the value() method
EitherT<CompletableFutureKind.Witness, DomainError, WorkflowModels.FinalResult> finalET =
    (EitherT<CompletableFutureKind.Witness, DomainError, WorkflowModels.FinalResult>) finalResultWithNotificationET;
// Unwrap EitherT to get the underlying Kind<CompletableFutureKind.Witness, Either<DomainError, FinalResult>>
return finalET.value();
```

* The final result of the `flatMap` chain is still a `Kind` representing the `EitherT`.
* We cast it back to the concrete `EitherT` record type.
* We call `finalET.value()` to extract the underlying `Kind<CompletableFutureKind.Witness, Either<DomainError, FinalResult>>`, which is the method's return type. The caller can then `unwrap` this further to get the `CompletableFuture` and handle its eventual `Either` result (e.g., using `CompletableFutureKindHelper.join()` and checking `isLeft`/`isRight`).

---

## Alternative: Handling Exceptions with `Try` (`runOrderWorkflowEitherTWithTryValidation`)

This runner method shows how to handle a synchronous validation step (`validateOrderWithTry`) that might throw exceptions instead of returning `Either`.

```java
// Inside the first flatMap:
// steps.validateOrderWithTry returns Kind<TryKind.Witness, ValidatedOrder>
Kind<TryKind.Witness, WorkflowModels.ValidatedOrder> tryResultKind =
    steps.validateOrderWithTry(ctx.initialData());
Try<WorkflowModels.ValidatedOrder> tryResult = TryKindHelper.unwrap(tryResultKind); // Unwrap to Try

// Convert Try<ValidatedOrder> to Either<DomainError, ValidatedOrder>
Either<DomainError, WorkflowModels.ValidatedOrder> eitherResult =
    tryResult.fold(
        validatedOrder -> Either.right(validatedOrder), // Success(v) -> Right(v)
        throwable -> { // Failure(t) -> Left(DomainError)
          dependencies.log(
              "Converting Try.Failure to DomainError.ValidationError: "
                  + throwable.getMessage());
          return Either.left(new DomainError.ValidationError(throwable.getMessage()));
        });

// Lift the converted Either result into EitherT<CompletableFutureKind.Witness, ...>
Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, WorkflowModels.ValidatedOrder>
    validatedOrderET = EitherT.fromEither(futureMonad, eitherResult);
// ... rest of the map logic ...
```

*
* The step returns `Kind<TryKind.Witness, ValidatedOrder>`.
* We `unwrap` it to get the `Try<ValidatedOrder>`.
* We use `tryResult.fold` to convert the `Try` into an `Either<DomainError, ValidatedOrder>`. A `Try.Success` becomes `Either.Right`, and a `Try.Failure` is mapped to an appropriate `Either.Left(DomainError.ValidationError(...))`.
* The resulting `Either` is then lifted into `EitherT` using `EitherT.fromEither`, just like in the previous example.
* The rest of the workflow proceeds identically.

This demonstrates how to integrate potentially exception-throwing synchronous code into the `EitherT`-based workflow by explicitly converting `Try` failures into your defined `DomainError` types.

---

## Key Takeaways & How to Apply

This example illustrates several powerful patterns enabled by Higher-Kinded-J:

1. **`EitherT` for `Future<Either<Error, Value>>`**: This is the core pattern. Use `EitherT` whenever you need to sequence asynchronous operations (`CompletableFuture`) where each step can also fail with a specific, typed error (`Either`).
   * Instantiate `EitherTMonad<F_OUTER_WITNESS, L_ERROR>` with the `Monad<F_OUTER_WITNESS>` instance for your outer monad (e.g., `CompletableFutureMonadError`).
   * Use `eitherTMonad.flatMap` to chain steps.
   * Lift async results (`Kind<F_OUTER_WITNESS, Either<L, R>>`) using `EitherT.fromKind`.
   * Lift sync results (`Either<L, R>`) using `EitherT.fromEither`.
   * Lift pure values (`R`) using `eitherTMonad.of` or `EitherT.right`.
   * Lift errors (`L`) using `eitherTMonad.raiseError` or `EitherT.left`.
2. **Typed Domain Errors**: Use `Either` (often with a sealed interface like `DomainError` for the `Left` type) to represent expected business failures clearly.
3. **Error Recovery**: Use `eitherTMonad.handleErrorWith` (or `handleError`, `recoverWith`, `recover`) to inspect `DomainError`s and potentially recover, allowing the workflow to continue.
4. **Integrating `Try`**: If dealing with synchronous code that throws exceptions, wrap it using `TryKindHelper.tryOf`, `unwrap` the `Try`, and use `fold` to convert `Try.Failure` into an appropriate `Either.Left<DomainError>` before lifting into `EitherT`.
5. **Dependency Injection**: Pass necessary dependencies (loggers, clients, config) into your workflow steps via a dedicated context or DI framework, rather than having steps manage their own dependencies.
6. **Structured Logging**: Use an injected logger within steps to provide visibility without tying the steps to a specific logging implementation.
