# The Order Workflow Example

This example is a practical demonstration of how to use the Higher-Kinded-J library to manage a common real-world scenario.

The scenario covers an Order workflow that involves asynchronous operations.  The Operations can fail with specific, expected business errors.

## Async Operations with Error Handling:

You can find the code for the Order Processing example in the [`org.higherkindedj.example.order`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-examples/src/main/java/org/higherkindedj/example/order/workflow) package.

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

* [Core Concepts](core-concepts.md) of Higher-Kinded-J (`Kind`and Type Classes).
* The specific types being used: [Supported Types](../monads/supported-types.md).
* The general [Usage Guide](usage-guide.md).

**Key Files:**

* [`Dependencies.java`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/workflow/Dependencies.java): Holds external dependencies (e.g., logger).
* [`OrderWorkflowRunner.java`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/workflow/OrderWorkflowRunner.java): Orchestrates the workflow, initialising and running different workflow versions (Workflow1 and Workflow2).
* [`OrderWorkflowSteps.java`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/workflow/OrderWorkflowSteps.java): Defines the individual workflow steps (sync/async), accepting `Dependencies`.
* [`Workflow1.java`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/workflow/Workflow1.java): Implements the order processing workflow using `EitherT` over `CompletableFuture`, with the initial validation step using an `Either`.
* [`Workflow2.java`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/workflow/Workflow2.java): Implements a similar workflow to `Workflow1`, but the initial validation step uses a `Try` that is then converted to an `Either`.
* [`WorkflowModels.java`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/model/WorkflowModels.java): Data records (`OrderData`, `ValidatedOrder`, etc.).
* [`DomainError.java`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/error/DomainError.java): Sealed interface defining specific business errors.

---

## Order Processing Workflow

![mermaid-flow-transparent.svg](../images/mermaid-flow-transparent.svg)

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
    CompletableFutureMonad.INSTANCE;

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

## Workflow Step-by-Step (`Workflow1.java`)

- [Workflow1.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/workflow/Workflow1.java)

Let's trace the execution flow defined in `Workflow1`. The workflow uses a `For` comprehension to sequentially chain the steps. steps. The state (`WorkflowContext`) is carried implicitly within the `Right` side of the `EitherT`.

The `OrderWorkflowRunner` initialises and calls `Workflow1` (or `Workflow2`). The core logic for composing the steps resides within these classes.


We start with `OrderData` and create an initial `WorkflowContext`.

Next, `eitherTMonad.of(initialContext)` lifts this context into an `EitherT` value, representing a `CompletableFuture` that is already successfully completed with an `Either.Right(initialContext)`.



```java
// From Workflow1.run()

var initialContext = WorkflowModels.WorkflowContext.start(orderData);

// The For-comprehension expresses the workflow sequentially.
// Each 'from' step represents a monadic bind (flatMap).
var workflow = For.from(eitherTMonad, eitherTMonad.of(initialContext))
    // Step 1: Validation. The lambda receives the initial context.
    .from(ctx1 -> {
      var validatedOrderET = EitherT.fromEither(futureMonad, EITHER.narrow(steps.validateOrder(ctx1.initialData())));
      return eitherTMonad.map(ctx1::withValidatedOrder, validatedOrderET);
    })
    // Step 2: Inventory. The lambda receives a tuple of (initial context, context after validation).
    .from(t -> {
      var ctx = t._2(); // Get the context from the previous step
      var inventoryCheckET = EitherT.fromKind(steps.checkInventoryAsync(ctx.validatedOrder().productId(), ctx.validatedOrder().quantity()));
      return eitherTMonad.map(ignored -> ctx.withInventoryChecked(), inventoryCheckET);
    })
    // Step 3: Payment. The lambda receives a tuple of all previous results. The latest context is the last element.
    .from(t -> {
      var ctx = t._3(); // Get the context from the previous step
      var paymentConfirmET = EitherT.fromKind(steps.processPaymentAsync(ctx.validatedOrder().paymentDetails(), ctx.validatedOrder().amount()));
      return eitherTMonad.map(ctx::withPaymentConfirmation, paymentConfirmET);
    })
    // Step 4: Shipment (with error handling).
    .from(t -> {
        var ctx = t._4(); // Get the context from the previous step
        var shipmentAttemptET = EitherT.fromKind(steps.createShipmentAsync(ctx.validatedOrder().orderId(), ctx.validatedOrder().shippingAddress()));
        var recoveredShipmentET = eitherTMonad.handleErrorWith(shipmentAttemptET, error -> {
            if (error instanceof DomainError.ShippingError(var reason) && "Temporary Glitch".equals(reason)) {
                dependencies.log("WARN: Recovering from temporary shipping glitch for order " + ctx.validatedOrder().orderId());
                return eitherTMonad.of(new WorkflowModels.ShipmentInfo("DEFAULT_SHIPPING_USED"));
            }
            return eitherTMonad.raiseError(error);
        });
        return eitherTMonad.map(ctx::withShipmentInfo, recoveredShipmentET);
    })
    // Step 5 & 6 are combined in the yield for a cleaner result.
    .yield(t -> {
      var finalContext = t._5(); // The context after the last 'from'
      var finalResult = new WorkflowModels.FinalResult(
          finalContext.validatedOrder().orderId(),
          finalContext.paymentConfirmation().transactionId(),
          finalContext.shipmentInfo().trackingId()
      );

      // Attempt notification, but recover from failure, returning the original FinalResult.
      var notifyET = EitherT.fromKind(steps.notifyCustomerAsync(finalContext.initialData().customerId(), "Order processed: " + finalResult.orderId()));
      var recoveredNotifyET = eitherTMonad.handleError(notifyET, notifyError -> {
        dependencies.log("WARN: Notification failed for order " + finalResult.orderId() + ": " + notifyError.message());
        return Unit.INSTANCE;
      });

      // Map the result of the notification back to the FinalResult we want to return.
      return eitherTMonad.map(ignored -> finalResult, recoveredNotifyET);
    });

// The yield returns a Kind<M, Kind<M, R>>, so we must flatten it one last time.
var flattenedFinalResultET = eitherTMonad.flatMap(x -> x, workflow);

var finalConcreteET = EITHER_T.narrow(flattenedFinalResultET);
return finalConcreteET.value();
```

There is a lot going on in the `For` comprehension so lets try and unpick it.

#### Breakdown of the `For` Comprehension:

1. **`For.from(eitherTMonad, eitherTMonad.of(initialContext))`**: The comprehension is initiated with a starting value. We lift the initial `WorkflowContext` into our `EitherT` monad, representing a successful, asynchronous starting point: `Future<Right(initialContext)>`.
2. **`.from(ctx1 -> ...)` (Validation)**:
   * **Purpose:** Validates the basic order data.
   * **Sync/Async:** Synchronous. `steps.validateOrder` returns `Kind<EitherKind.Witness<DomainError>, ValidatedOrder>`.
   * **HKT Integration:** The `Either` result is lifted into the `EitherT<CompletableFuture, ...>` context using `EitherT.fromEither(...)`. This wraps the immediate `Either` result in a *completed*`CompletableFuture`.
   * **Error Handling:** If validation fails, `validateOrder` returns a `Left(ValidationError)`. This becomes a `Future<Left(ValidationError)>`, and the `For` comprehension automatically short-circuits, skipping all subsequent steps.
3. **`.from(t -> ...)` (Inventory Check)**:
   * **Purpose:** Asynchronously checks if the product is in stock.
   * **Sync/Async:** Asynchronous. `steps.checkInventoryAsync` returns `Kind<CompletableFutureKind.Witness, Either<DomainError, Unit>>`.
   * **HKT Integration:** The `Kind` returned by the async step is directly wrapped into `EitherT` using `EitherT.fromKind(...)`.
   * **Error Handling:** Propagates `Left(StockError)` or underlying `CompletableFuture` failures.
4. **`.from(t -> ...)` (Payment)**:
   * **Purpose:** Asynchronously processes the payment.
   * **Sync/Async:** Asynchronous.
   * **HKT Integration & Error Handling:** Works just like the inventory check, propagating `Left(PaymentError)` or `CompletableFuture` failures.
5. **`.from(t -> ...)` (Shipment with Recovery)**:
   * **Purpose:** Asynchronously creates a shipment.
   * **HKT Integration:** Uses `EitherT.fromKind` and `eitherTMonad.handleErrorWith`.
   * **Error Handling & Recovery:** If `createShipmentAsync` returns a `Left(ShippingError("Temporary Glitch"))`, the `handleErrorWith` block catches it and returns a *successful*`EitherT` with default shipment info, allowing the workflow to proceed. All other errors are propagated.
6. **`.yield(t -> ...)` (Final Result and Notification)**:
   * **Purpose:** The final block of the `For` comprehension. It takes the accumulated results from all previous steps (in a tuple `t`) and produces the final result of the entire chain.
   * **Logic:**
     1. It constructs the `FinalResult` from the successful `WorkflowContext`.
     2. It attempts the final, non-critical notification step (`notifyCustomerAsync`).
     3. Crucially, it uses `handleError` on the notification result. If notification fails, it logs a warning but recovers to a `Right(Unit.INSTANCE)`, ensuring the overall workflow remains successful.
     4. It then maps the result of the recovered notification step back to the `FinalResult`, which becomes the final value of the entire comprehension.
7. **Final `flatMap` and Unwrapping**:
   * The `yield` block itself can return a monadic value. To get the final, single-layer result, we do one last `flatMap` over the `For` comprehension's result.
   * Finally, `EITHER_T.narrow(...)` and `.value()` are used to extract the underlying `Kind<CompletableFutureKind.Witness, Either<...>>` from the `EitherT` record. The `main` method in `OrderWorkflowRunner` then uses `FUTURE.narrow()` and `.join()` to get the final `Either` result for printing.


---


## Alternative: Handling Exceptions with `Try` (`Workflow2.java`)

The `OrderWorkflowRunner` also initialises and can run `Workflow2`. This workflow is identical to Workflow1 except for the first step. It demonstrates how to integrate synchronous code that might throw exceptions.

- [Workflow2.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/workflow/Workflow2.java)

```java
// From Workflow2.run(), inside the first .from(...)
.from(ctx1 -> {
  var tryResult = TRY.narrow(steps.validateOrderWithTry(ctx1.initialData()));
  var eitherResult = tryResult.toEither(
      throwable -> (DomainError) new DomainError.ValidationError(throwable.getMessage()));
  var validatedOrderET = EitherT.fromEither(futureMonad, eitherResult);
  // ... map context ...
})
```

* The `steps.validateOrderWithTry` method is designed to throw exceptions on validation failure (e.g., `IllegalArgumentException`).
* `TRY.tryOf(...)` in `OrderWorkflowSteps` wraps this potentially exception-throwing code, returning a `Kind<TryKind.Witness, ValidatedOrder>`.
* In `Workflow2`, we `narrow` this to a concrete `Try<ValidatedOrder>`.
* We use `tryResult.toEither(...)` to convert the `Try` into an `Either<DomainError, ValidatedOrder>`:
    * A `Try.Success(validatedOrder)` becomes `Either.right(validatedOrder)`.
    * A `Try.Failure(throwable)` is mapped to an `Either.left(new DomainError.ValidationError(throwable.getMessage()))`.
* The resulting `Either` is then lifted into `EitherT` using `EitherT.fromEither`, and the rest of the workflow proceeds as before.

This demonstrates a practical pattern for integrating synchronous, exception-throwing code into the `EitherT`-based workflow by explicitly converting failures into your defined `DomainError` types.

---

~~~admonish important  title="Key Points:"


This example illustrates several powerful patterns enabled by Higher-Kinded-J:

1.  **`EitherT` for `Future<Either<Error, Value>>`**: This is the core pattern. Use `EitherT` whenever you need to sequence asynchronous operations (`CompletableFuture`) where each step can also fail with a specific, typed error (`Either`).
    * Instantiate `EitherTMonad<F_OUTER_WITNESS, L_ERROR>` with the `Monad<F_OUTER_WITNESS>` instance for your outer monad (e.g., `CompletableFutureMonad`).
    * Use `eitherTMonad.flatMap` or a `For` comprehension to chain steps.
    * Lift async results (`Kind<F_OUTER_WITNESS, Either<L, R>>`) into `EitherT` using `EitherT.fromKind`.
    * Lift sync results (`Either<L, R>`) into `EitherT` using `EitherT.fromEither`.
    * Lift pure values (`R`) into `EitherT` using `eitherTMonad.of` or `EitherT.right`.
    * Lift errors (`L`) into `EitherT` using `eitherTMonad.raiseError` or `EitherT.left`.
2.  **Typed Domain Errors**: Use `Either` (often with a sealed interface like `DomainError` for the `Left` type) to represent expected business failures clearly. This improves type safety and makes error handling more explicit.
3.  **Error Recovery**: Use `eitherTMonad.handleErrorWith` (for complex recovery returning another `EitherT`) or `handleError` (for simpler recovery to a pure value for the `Right` side) to inspect `DomainError`s and potentially recover, allowing the workflow to continue gracefully.
4.  **Integrating `Try`**: If dealing with synchronous legacy code or libraries that throw exceptions, wrap calls using `TRY.tryOf`. Then, `narrow` the `Try` and use `toEither` (or `fold`) to convert `Try.Failure` into an appropriate `Either.Left<DomainError>` before lifting into `EitherT`.
5.  **Dependency Injection**: Pass necessary dependencies (loggers, service clients, configurations) into your workflow steps (e.g., via a constructor and a `Dependencies` record). This promotes loose coupling and testability.
6.  **Structured Logging**: Use an injected logger within steps to provide visibility into the workflow's progress and state without tying the steps to a specific logging implementation (like `System.out`).
7.  **`var` for Conciseness**: Utilise Java's `var` for local variable type inference where the type is clear from the right-hand side of an assignment. This can reduce verbosity, especially with complex generic types common in HKT.
~~~

---

~~~admonish success title="Further Considerations & Potential Enhancements"

While this example covers a the core concepts, a real-world application might involve more complexities. Here are some areas to consider for further refinement:

1. **More Sophisticated Error Handling/Retries:**
   * **Retry Mechanisms:** For transient errors (like network hiccups or temporary service unavailability), you might implement retry logic. This could involve retrying a failed async step a certain number of times with exponential backoff. While `higher-kinded-j` itself doesn't provide specific retry utilities, you could integrate libraries like Resilience4j or implement custom retry logic within a `flatMap` or `handleErrorWith` block.
   * **Compensating Actions (Sagas):** If a step fails after previous steps have caused side effects (e.g., payment succeeds, but shipment fails irrevocably), you might need to trigger compensating actions (e.g., refund payment). This often leads to more complex Saga patterns.
2. **Configuration of Services:**
   * The `Dependencies` record currently only holds a logger. In a real application, it would also provide configured instances of service clients (e.g., `InventoryService`, `PaymentGatewayClient`, `ShippingServiceClient`). These clients would be interfaces, with concrete implementations (real or mock for testing) injected.
3. **Parallel Execution of Independent Steps:**
   * If some workflow steps are independent and can be executed concurrently, you could leverage `CompletableFuture.allOf` (to await all) or `CompletableFuture.thenCombine` (to combine results of two).
   * Integrating these with `EitherT` would require careful management of the `Either` results from parallel futures. For instance, if you run two `EitherT` operations in parallel, you'd get two `CompletableFuture<Either<DomainError, ResultX>>`. You would then need to combine these, deciding how to aggregate errors if multiple occur, or how to proceed if one fails and others succeed.
4. **Transactionality:**
   * For operations requiring atomicity (all succeed or all fail and roll back), traditional distributed transactions are complex. The Saga pattern mentioned above is a common alternative for managing distributed consistency.
   * Individual steps might interact with transactional resources (e.g., a database). The workflow itself would coordinate these, but doesn't typically manage a global transaction across disparate async services.
5. **More Detailed & Structured Logging:**
   * The current logging is simple string messages. For better observability, use a structured logging library (e.g., SLF4J with Logback/Log4j2) and log key-value pairs (e.g., `orderId`, `stepName`, `status`, `durationMs`, `errorType` if applicable). This makes logs easier to parse, query, and analyse.
   * Consider logging at the beginning and end of each significant step, including the outcome (success/failure and error details).
6. **Metrics & Monitoring:**
   * Instrument the workflow to emit metrics (e.g., using Micrometer). Track things like workflow execution time, step durations, success/failure counts for each step, and error rates. This is crucial for monitoring the health and performance of the system.

~~~

Higher-Kinded-J can help build more robust, resilient, and observable workflows using these foundational patterns from this example.
