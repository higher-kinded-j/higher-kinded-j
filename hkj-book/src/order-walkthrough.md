# The Order Workflow Example

This example is a practical demonstration of how to use the Higher-Kinded-J library to manage a common real-world scenario.  

The scenario covers an Order workflow that involves asynchronous operations.  The Operations can fail with specific, expected business errors.

## Async Operations with Error Handling:

You can find the code for the Order Processing example in the `org.higherkindedj.example.order` package. 

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
* [`OrderWorkflowRunner.java`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/example/order/workflow/OrderWorkflowRunner.java): Orchestrates the workflow using `EitherT`. This is the main focus of the walkthrough.
* [`OrderWorkflowSteps.java`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/example/order/workflow/OrderWorkflowSteps.java): Defines the individual workflow steps (sync/async), accepting `Dependencies`.
* [`WorkflowModels.java`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/example/order/model/WorkflowModels.java): Data records (`OrderData`, `ValidatedOrder`, etc.).
* [`DomainError.java`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/example/order/error/DomainError.java): Sealed interface defining specific business errors.

---

## Order Processing Workflow

![mermaid-flow-transparent.svg](images/mermaid-flow-transparent.svg)

---

## The Problem: Combining Asynchronicity and Typed Errors

Imagine an online order process with the following stages:

1. **Validate Order Data:** Check quantity, product ID, etc. (Can fail with `ValidationError`). This is a synchronous operation.
2. **Check Inventory:** Call an external inventory service (async). (Can fail with `StockError`).
3. **Process Payment:** Call a payment gateway (async). (Can fail with `PaymentError`).
4. **Create Shipment:** Call a shipping service (async). (Can fail with `ShippingError`, some of which might be recoverable).
5. **Notify Customer:** Send an email/SMS (async). (Might fail, but should not critically fail the entire order).

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
5. **Structured Logging:** Steps use the injected logger (`dependencies.log(...)`) for consistent logging.


### Setting up `EitherTMonad`

In `OrderWorkflowRunner`, we get the necessary type class instances:

```java
// MonadError instance for CompletableFuture (handles Throwable)
// F_OUTER_WITNESS for CompletableFuture is CompletableFutureKind.Witness
private final @NonNull MonadError<CompletableFutureKind.Witness, Throwable> futureMonad =
    new CompletableFutureMonadError();

// EitherTMonad instance, providing the outer monad (futureMonad).
// This instance handles DomainError for the inner Either.
// The HKT witness for EitherT here is EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>
private final @NonNull
MonadError<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, DomainError>
    eitherTMonad = new EitherTMonad<>(this.futureMonad);
```

Now, `eitherTMonad` can be used to chain operations on `EitherT` values (which are `Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, A>`). Its `flatMap` method automatically handles:

* **Async Sequencing:** Delegated to `futureMonad.flatMap` (which translates to `CompletableFuture::thenCompose`).
* **Error Short-Circuiting:** If an inner `Either` becomes `Left(domainError)`, subsequent `flatMap` operations are skipped, propagating the `Left` within the `CompletableFuture`.

## Workflow Step-by-Step (`runOrderWorkflowEitherT`)

Let's trace the execution flow defined in `OrderWorkflowRunner.runOrderWorkflowEitherT`. The workflow uses `flatMap` on the `eitherTMonad` to sequence steps. The state (`WorkflowContext`) is carried implicitly within the `Right` side of the `EitherT`. Comments in the code (`// --- ... ---`) link to these markdown sections.

**Initial State:** (`// --- Initialisation ... ---`)

```java
var initialContext = WorkflowContext.start(orderData);
// Lift the initial context into EitherT: Represents Future<Right(initialContext)>
var initialET = eitherTMonad.of(initialContext);
```

* We start with `OrderData` and create an initial `WorkflowContext`.
* `eitherTMonad.of(initialContext)` lifts this context into an `EitherT` value. This represents a `CompletableFuture` that is already successfully completed with an `Either.Right(initialContext)`.We start with OrderData and create an initial WorkflowContext.
  eitherTMonad.of(initialContext) lifts this context into an EitherT value. This represents a CompletableFuture that is already successfully completed with an Either.Right(initialContext).
* We start with `OrderData` and create an initial `WorkflowContext`.
* `eitherTMonad.of(initialContext)` lifts this context into an `EitherT` value. This represents a `CompletableFuture` that is already successfully completed with an `Either.Right(initialContext)`.

**Step 1: Validate Order (Synchronous, returns `Either`)**
(`// --- Step 1: Validate Order ... ---`)

```java
var validatedET =
    eitherTMonad.flatMap( // Start chaining
        ctx -> { // Lambda receives the context if initialET was Right
          // 1. Call the synchronous step (logging happens inside)
          //    steps.validateOrder returns Kind<EitherKind.Witness<DomainError>, ValidatedOrder>
          var syncResultEitherKind = steps.validateOrder(ctx.initialData());
          var syncResultEither = EitherKindHelper.unwrap(syncResultEitherKind);

          // 2. Lift the sync Either result into the EitherT<Future, ...> context
          //    EitherT.fromEither wraps the Either in a completed Future: Future<Either<...>>
          var validatedOrderET = EitherT.fromEither(futureMonad, syncResultEither);

          // 3. If validatedOrderET is Right(vo), map its value to update the context: ctx -> ctx.withValidatedOrder(vo)
          //    If validatedOrderET is Left(error), map is skipped, and Left propagates.
          return eitherTMonad.map(ctx::withValidatedOrder, validatedOrderET);
        },
        initialET // Input to the first flatMap
    );
```

* **Purpose:** Validate basic order data.
* **Sync/Async:** Synchronous. `steps.validateOrder` returns `Kind<EitherKind.Witness<DomainError>, ValidatedOrder>`.
* **HKT Integration:** The `Either` result is unwrapped from its `Kind` and then lifted into the `EitherT<CompletableFuture, ...>` context using `EitherT.fromEither(futureMonad, syncResultEither)`. This wraps the immediate `Either` in a *completed*`CompletableFuture`.
* **Error Handling:** If `validateOrder` returns a `Left(ValidationError)`, `EitherT.fromEither` creates a `Future<Left(validationError)>`. The subsequent `eitherTMonad.map` to update the context is effectively skipped for the error case within `flatMap`'s logic. The `validatedET` will hold this `Future<Left(validationError)>`, and subsequent `flatMap` calls in the chain will also be skipped.

**Step 2: Check Inventory (Asynchronous)**
(`// --- Step 2: Check Inventory ... ---`)

```java
var inventoryET =
    eitherTMonad.flatMap( // Chain from the previous step's result (validatedET)
        ctx -> { // Executed only if validatedET was Right(context)
          // 1. Call the asynchronous step (logging happens inside)
          //    Returns Kind<CompletableFutureKind.Witness, Either<DomainError, Void>>
          var inventoryCheckFutureKind =
              steps.checkInventoryAsync(
                  ctx.validatedOrder().productId(), ctx.validatedOrder().quantity());

          // 2. Lift the async result directly into EitherT using fromKind
          //    This directly uses the Future<Either<...>> from the step.
          var inventoryCheckET = EitherT.fromKind(inventoryCheckFutureKind);

          // 3. If inventoryCheckET resolves to Right(null), map to update context.
          //    If it resolves to Left(stockError), map is skipped, Left propagates.
          return eitherTMonad.map(ignored -> ctx.withInventoryChecked(), inventoryCheckET);
        },
        validatedET // Input is the result of the validation step
    );
```

* **Purpose:** Check if the product is in stock via an external service.
* **Sync/Async:** Asynchronous. `steps.checkInventoryAsync` returns `Kind<CompletableFutureKind.Witness, Either<DomainError, Void>>`.
* **HKT Integration:** The `Kind` returned by the async step (which represents `CompletableFuture<Either<...>>`) is directly wrapped into `EitherT` using `EitherT.fromKind(inventoryCheckFutureKind)`.
* **Error Handling:** If the `CompletableFuture` from `checkInventoryAsync` completes with `Left(StockError)`, this `Left` is propagated by `flatMap`. If the `CompletableFuture` itself fails (e.g., network error), the outer monad (`futureMonad`) handles it, resulting in a failed `CompletableFuture` wrapped within `inventoryET`.

**Step 3: Process Payment (Asynchronous)**
(`// --- Step 3: Process Payment ... ---`)

```java
var paymentET =
    eitherTMonad.flatMap( // Chain from inventory check
        ctx -> { // Executed only if inventory check was Right
          // 1. Call async payment step
          // Returns Kind<CompletableFutureKind.Witness, Either<DomainError, PaymentConfirmation>>
          var paymentFutureKind =
              steps.processPaymentAsync(
                  ctx.validatedOrder().paymentDetails(), ctx.validatedOrder().amount());
          // 2. Lift async result into EitherT
          var paymentConfirmET = EitherT.fromKind(paymentFutureKind);
          // 3. Map success to update context
          return eitherTMonad.map(ctx::withPaymentConfirmation, paymentConfirmET);
        },
        inventoryET // Input is result of inventory step
    );
```

* **Purpose:** Charge the customer via a payment gateway.
* **Sync/Async:** Asynchronous. `steps.processPaymentAsync` returns `Kind<CompletableFutureKind.Witness, Either<DomainError, PaymentConfirmation>>`.
* **HKT Integration:** Same as Step 2, using `EitherT.fromKind`.
* **Error Handling:** Propagates `Left(PaymentError)` or underlying `CompletableFuture` failures.

**Step 4: Create Shipment (Asynchronous with Recovery)**
(`// --- Step 4: Create Shipment ... ---`)

```java
var shipmentET =
    eitherTMonad.flatMap( // Chain from payment
        ctx -> { // Executed only if payment was Right
          // 1. Call async shipment step
          var shipmentAttemptFutureKind =
              steps.createShipmentAsync(
                  ctx.validatedOrder().orderId(), ctx.validatedOrder().shippingAddress());
          // 2. Lift async result into EitherT
          var shipmentAttemptET = EitherT.fromKind(shipmentAttemptFutureKind);

          // *** 3. Error Handling & Recovery ***
          var recoveredShipmentET =
              eitherTMonad.handleErrorWith( // Operates on the EitherT value
                  shipmentAttemptET,
                  error -> { // Lambda receives the DomainError if shipmentAttemptET is Left
                    // Check if it's a specific, recoverable error (Java 17+ pattern matching)
                    if (error instanceof DomainError.ShippingError(String reason)
                        && "Temporary Glitch".equals(reason)) {
                      dependencies.log(
                          "WARN (EitherT): Recovering from temporary shipping glitch...");
                      // Return a *successful* EitherT using 'of'
                      return eitherTMonad.of(new ShipmentInfo("DEFAULT_SHIPPING_USED"));
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
* **HKT Integration:** Uses `EitherT.fromKind`. Crucially, it then uses `eitherTMonad.handleErrorWith`.
* **Error Handling & Recovery:** This step demonstrates recovery. If `createShipmentAsync` results in `Left(ShippingError("Temporary Glitch"))`, the `handleErrorWith` block catches this specific `DomainError`. Instead of propagating the error, it returns a *successful*`EitherT` (`eitherTMonad.of(...)`) containing default shipment info. The workflow can then proceed. If any other `DomainError` occurs, it's re-raised using `eitherTMonad.raiseError(...)`, propagating the `Left` state. System errors from the `CompletableFuture` are still handled implicitly by the outer `CompletableFuture` layer.

**Step 5: Map to Final Result**
(`// --- Step 5: Map to Final Result ... ---`)

```java
var finalResultET =
    eitherTMonad.map( // Use map as we are just transforming the success value
        ctx -> { // Executed only if shipment step (or recovery) was Right
          dependencies.log(
              "Mapping final context to FinalResult (EitherT) for Order: "
                  + ctx.validatedOrder().orderId());
          return new FinalResult( // Create the final success object
              ctx.validatedOrder().orderId(),
              ctx.paymentConfirmation().transactionId(),
              ctx.shipmentInfo().trackingId());
        },
        shipmentET // Input is result of shipment step
    );
```

* **Purpose:** Transform the final successful `WorkflowContext` into the desired `FinalResult` record.
* **HKT Integration:** Uses `eitherTMonad.map` because we are only transforming the value within the `Right` case of the `EitherT`. If `shipmentET` was `Left`, this `map` is skipped, and `finalResultET` will also be `Left`.

**Step 6: Attempt Notification (Optional Final Step with Recovery)**
(`// --- Step 6: Attempt Notification ... ---`)

```java
var finalResultWithNotificationET =
    eitherTMonad.flatMap( // Use flatMap because notifyCustomerAsync returns an F<Either<...>>
        finalResult -> { // Executed only if previous steps were Right, finalResult is FinalResult
          var notifyFutureKind =
              steps.notifyCustomerAsync(
                  orderData.customerId(), "Order processed: " + finalResult.orderId());
          var notifyET = EitherT.fromKind(notifyFutureKind);

          // Handle potential notification error, but *always recover*
          // and return the original finalResult
          return eitherTMonad.map(
              ignoredVoid -> finalResult, // Ensure the original FinalResult is the success value
              eitherTMonad.handleError( // handleError recovers the 'Right' side of Either
                  notifyET,
                  notifyError -> {
                    dependencies.log(
                        "WARN (EitherT): Notification failed but order succeeded: "
                            + notifyError.message());
                    return null; // Recover by returning null (for Void in the Right path of notifyET)
                  })
          );
        },
        finalResultET // Input is the result from Step 5
    );
```

* **Purpose:** Send a notification (e.g., email) to the customer. Failure here shouldn't fail the whole order.
* **Sync/Async:** Asynchronous.
* **HKT Integration:** Uses `flatMap` to sequence the notification, `EitherT.fromKind` to lift the async result, and `handleError` for simple recovery.
* **Error Handling:** If `notifyCustomerAsync` results in a `Left(NotificationError)`, `handleError` catches it, logs a warning, and recovers by effectively making the notification step result in a `Right(null)` (as `Void` is represented by `null`). The outer `map` ensures that the `FinalResult` from the previous step (`finalResultET`) is preserved and becomes the `Right` value of `finalResultWithNotificationET`, regardless of the notification's success or failure.

**Final Unwrapping:**
(`// --- Final Unwrapping ... ---`)

```java
// Unwrap EitherT to get the underlying Kind<CompletableFutureKind.Witness, Either<DomainError, FinalResult>>
var finalConcreteET =
    EitherTKindHelper.<CompletableFutureKind.Witness, DomainError, FinalResult>unwrap(
        finalResultWithNotificationET);
return finalConcreteET.value();
```

*
* The result of the `flatMap` chain, `finalResultWithNotificationET`, is still a `Kind` representing the `EitherT`.
* We use `EitherTKindHelper.unwrap` to safely cast it back to the concrete `EitherT` record type.
* We call `finalConcreteET.value()` to extract the underlying `Kind<CompletableFutureKind.Witness, Either<DomainError, FinalResult>>`.
* The `main` method in `OrderWorkflowRunner` then further unwraps this to a `CompletableFuture<Either<DomainError, FinalResult>>` using `CompletableFutureKindHelper.unwrap()` and calls `.join()` to get the final `Either` result for printing.

---

## Alternative: Handling Exceptions with `Try` (`runOrderWorkflowEitherTWithTryValidation`)

The `OrderWorkflowRunner` also includes `runOrderWorkflowEitherTWithTryValidation`. This method is very similar to `runOrderWorkflowEitherT`, but its first step (validation) calls `steps.validateOrderWithTry(ctx.initialData())`.

```java
// Inside the first flatMap of runOrderWorkflowEitherTWithTryValidation:
// --- Step 1: Validate Order (using Try) ---
var validatedET =
    eitherTMonad.flatMap(
        ctx -> {
          // Synchronous step returning Kind<TryKind.Witness, ValidatedOrder>
          var tryResultKind = steps.validateOrderWithTry(ctx.initialData());
          var tryResult = TryKindHelper.<ValidatedOrder>unwrap(tryResultKind);

          // Convert Try<ValidatedOrder> to Either<DomainError, ValidatedOrder> using the new method
          var eitherResult = tryResult.toEither(
              throwable -> {
                dependencies.log(
                    "Converting Try.Failure to DomainError.ValidationError: "
                        + throwable.getMessage());
                // The type L (DomainError) is inferred from this lambda's return type
                return (DomainError)new DomainError.ValidationError(throwable.getMessage());
              });

          var validatedOrderET_Concrete =
              EitherT.<CompletableFutureKind.Witness, DomainError, ValidatedOrder>fromEither(futureMonad, eitherResult);
          var validatedOrderET_Kind = EitherTKindHelper.wrap(validatedOrderET_Concrete);

          return eitherTMonad.map(
              ctx::withValidatedOrder,
              validatedOrderET_Kind
          );
        initialET);
// ... rest of the workflow proceeds identically to runOrderWorkflowEitherT ...
```

* The `steps.validateOrderWithTry` method is designed to throw exceptions on validation failure (e.g., `IllegalArgumentException`).
* `TryKindHelper.tryOf(...)` in `OrderWorkflowSteps` wraps this potentially exception-throwing code, returning a `Kind<TryKind.Witness, ValidatedOrder>`.
* In the runner, we `unwrap` this to get a `Try<ValidatedOrder>`.
* We use `tryResult.fold(...)` to convert the `Try` into an `Either<DomainError, ValidatedOrder>`:
  * A `Try.Success(validatedOrder)` becomes `Either.right(validatedOrder)`.
  * A `Try.Failure(throwable)` is mapped to an `Either.left(new DomainError.ValidationError(throwable.getMessage()))`.
* The resulting `Either` is then lifted into `EitherT` using `EitherT.fromEither`, and the rest of the workflow proceeds as before.

This demonstrates how to integrate synchronous code that might throw exceptions into the `EitherT`-based workflow by explicitly converting `Try.Failure`s into your defined `DomainError` types.

---

## Key Takeaways & How to Apply

This example illustrates several powerful patterns enabled by Higher-Kinded-J:

1. **`EitherT` for `Future<Either<Error, Value>>`**: This is the core pattern. Use `EitherT` whenever you need to sequence asynchronous operations (`CompletableFuture`) where each step can also fail with a specific, typed error (`Either`).
   * Instantiate `EitherTMonad<F_OUTER_WITNESS, L_ERROR>` with the `Monad<F_OUTER_WITNESS>` instance for your outer monad (e.g., `CompletableFutureMonadError`).
   * Use `eitherTMonad.flatMap` to chain steps.
   * Lift async results (`Kind<F_OUTER_WITNESS, Either<L, R>>`) into `EitherT` using `EitherT.fromKind`.
   * Lift sync results (`Either<L, R>`) into `EitherT` using `EitherT.fromEither`.
   * Lift pure values (`R`) into `EitherT` using `eitherTMonad.of` or `EitherT.right`.
   * Lift errors (`L`) into `EitherT` using `eitherTMonad.raiseError` or `EitherT.left`.
2. **Typed Domain Errors**: Use `Either` (often with a sealed interface like `DomainError` for the `Left` type) to represent expected business failures clearly. This improves type safety and makes error handling more explicit.
3. **Error Recovery**: Use `eitherTMonad.handleErrorWith` (for complex recovery returning another `EitherT`) or `handleError` (for simpler recovery to a pure value for the `Right` side) to inspect `DomainError`s and potentially recover, allowing the workflow to continue gracefully.
4. **Integrating `Try`**: If dealing with synchronous legacy code or libraries that throw exceptions, wrap calls using `TryKindHelper.tryOf`. Then, `unwrap` the `Try` and use `fold` to convert `Try.Failure` into an appropriate `Either.Left<DomainError>` before lifting into `EitherT`.
5. **Dependency Injection**: Pass necessary dependencies (loggers, service clients, configurations) into your workflow steps (e.g., via a constructor and a `Dependencies` record). This promotes loose coupling and testability.
6. **Structured Logging**: Use an injected logger within steps to provide visibility into the workflow's progress and state without tying the steps to a specific logging implementation (like `System.out`).
7. **`var` for Conciseness**: Utilize Java's `var` for local variable type inference where the type is clear from the right-hand side of an assignment. This can reduce verbosity, especially with complex generic types common in HKT.

## Further Considerations & Potential Enhancements

While this example covers the core concepts, a real-world application might involve more complexities. Here are some areas for further exploration:

1. **More Sophisticated Error Handling/Retries:**
   * **Retry Mechanisms:** For transient errors (like network hiccups or temporary service unavailability), you might implement retry logic. This could involve retrying a failed async step a certain number of times with exponential backoff. While `higher-kinded-j` itself doesn't provide specific retry utilities, you could integrate libraries like Resilience4j or implement custom retry logic within a `flatMap` or `handleErrorWith` block.
   * **Compensating Actions (Sagas):** If a step fails after previous steps have caused side effects (e.g., payment succeeds, but shipment fails irrevocably), you might need to trigger compensating actions (e.g., refund payment). This often leads to more complex Saga patterns.
2. **Configuration of Services:**
   * The `Dependencies` record currently only holds a logger. In a real application, it would also provide configured instances of service clients (e.g., `InventoryService`, `PaymentGatewayClient`, `ShippingServiceClient`). These clients would be interfaces, with concrete implementations (real or mock for testing) injected.
3. **Parallel Execution of Independent Steps:**
   * If some workflow steps are independent and can be executed concurrently, you could leverage `CompletableFuture.allOf` (to await all) or `CompletableFuture.thenCombine` (to combine results of two).
   * Integrating these with `EitherT` would require careful management of the `Either` results from parallel futures. For instance, if you run two `EitherT` operations in parallel, you'd get two `CompletableFuture<Either<DomainError, ResultX>>`. You would then need to combine these, deciding how to aggregate errors if multiple occur, or how to proceed if one fails and others succeed.
4. **Transactionality (Conceptual):**
   * For operations requiring atomicity (all succeed or all fail and roll back), traditional distributed transactions are complex. The Saga pattern mentioned above is a common alternative for managing distributed consistency.
   * Individual steps might interact with transactional resources (e.g., a database). The workflow itself would coordinate these, but doesn't typically manage a global transaction across disparate async services.
5. **More Detailed & Structured Logging:**
   * The current logging is simple string messages. For better observability, use a structured logging library (e.g., SLF4J with Logback/Log4j2) and log key-value pairs (e.g., `orderId`, `stepName`, `status`, `durationMs`, `errorType` if applicable). This makes logs easier to parse, query, and analyze.
   * Consider logging at the beginning and end of each significant step, including the outcome (success/failure and error details).
6. **Metrics & Monitoring:**
   * Instrument the workflow to emit metrics (e.g., using Micrometer). Track things like workflow execution time, step durations, success/failure counts for each step, and error rates. This is crucial for monitoring the health and performance of the system.
7. **Splitting into Smaller Examples:**
   * For developers completely new to HKT and `EitherT`, this example, while comprehensive, could be preceded by an even simpler one. A "Part 1" could focus on just two asynchronous steps composed with `EitherT` to illustrate the basic mechanics before introducing error recovery, `Try` integration, and more steps.

By considering these aspects, you can build more robust, resilient, and observable workflows using the foundational patterns demonstrated in this example.
