# ErrorContext: Typed Errors in Effectful Code

> *"You wont never get the whole thing its too big in the nite of your head ther aint room."*
>
> -- Russell Hoban, *Riddley Walker*

API calls fail. Database queries timeout. Files go missing. The "whole thing" of what can go wrong in effectful code is indeed too big to hold in your head at once. But you can give those failures *names*: typed errors that the compiler understands and your code can reason about. `ErrorContext` captures both the deferred nature of IO and the typed-error semantics of `Either` in a single, composable abstraction.

~~~admonish info title="What You'll Learn"
- Creating ErrorContexts that catch exceptions and map them to typed errors
- Chaining dependent computations with `via()` and `flatMap()`
- Recovering from errors with `recover()`, `recoverWith()`, and `orElse()`
- Transforming error types with `mapError()`
- Executing contexts with `runIO()`, `runIOOrThrow()`, and `runIOOrElse()`
~~~

---

## The Problem

Traditional Java error handling fragments across styles:

```java
// Style 1: Checked exceptions
public User fetchUser(String id) throws UserNotFoundException { ... }

// Style 2: Unchecked exceptions
public Order createOrder(Cart cart) { /* might throw RuntimeException */ }

// Style 3: Optional returns
public Optional<Profile> getProfile(User user) { ... }

// Style 4: Custom result types
public Result<ValidationError, Order> validateOrder(OrderRequest req) { ... }
```

Composing across these styles requires constant translation. Each boundary demands explicit handling, cluttering your code with conversion logic.

---

## The Solution

`ErrorContext` unifies error handling for IO-based operations. Exceptions become typed errors. Optionality can be converted to errors. All computations compose through the same operations:

```java
ErrorContext<IOKind.Witness, OrderError, Order> orderPipeline =
    ErrorContext.<OrderError, User>io(
        () -> userService.fetch(userId),           // May throw
        OrderError::fromException)
    .via(user -> ErrorContext.io(
        () -> orderService.validate(user, request),
        OrderError::fromException))
    .via(validated -> ErrorContext.io(
        () -> orderService.create(validated),
        OrderError::fromException))
    .recover(error -> Order.placeholder(error.message()));
```

One unified API. One error type. The compiler tracks what can fail.

---

## Creating ErrorContexts

### From Throwing Computations

The most common factory method catches exceptions and maps them to your error type:

```java
ErrorContext<IOKind.Witness, ApiError, User> user = ErrorContext.io(
    () -> httpClient.get("/users/" + id),   // May throw
    ApiError::fromException                   // Throwable → ApiError
);
```

The computation is *deferred*; nothing executes until you call `runIO()`. Exceptions thrown during execution are caught and converted to the error type.

### From Either-Returning Computations

When your code already returns `Either`:

```java
ErrorContext<IOKind.Witness, ValidationError, Order> validated = ErrorContext.ioEither(
    () -> validator.validate(request)   // Returns Either<ValidationError, Order>
);
```

### Pure Values

For successful or failed values you already have:

```java
// Known success
ErrorContext<IOKind.Witness, String, Integer> success = ErrorContext.success(42);

// Known failure
ErrorContext<IOKind.Witness, String, Integer> failure = ErrorContext.failure("Not found");

// From an existing Either
Either<ApiError, User> either = lookupUser(id);
ErrorContext<IOKind.Witness, ApiError, User> ctx = ErrorContext.fromEither(either);
```

---

## Transforming Values

### map: Transform the Success Value

`map` transforms the value inside a successful context:

```java
ErrorContext<IOKind.Witness, String, String> greeting = ErrorContext.success("world");

ErrorContext<IOKind.Witness, String, String> result = greeting.map(s -> "Hello, " + s);
// Success("Hello, world")

ErrorContext<IOKind.Witness, String, String> failed = ErrorContext.failure("oops");
ErrorContext<IOKind.Witness, String, String> stillFailed = failed.map(s -> "Hello, " + s);
// Failure("oops") -- map doesn't run on failures
```

The function only executes if the context is successful. Failures pass through unchanged.

---

## Chaining Computations

### via: Chain Dependent Operations

`via` is the workhorse for sequencing operations where each step depends on the previous:

```java
ErrorContext<IOKind.Witness, DbError, Invoice> invoice =
    ErrorContext.<DbError, Customer>io(
        () -> customerRepo.find(customerId),
        DbError::fromException)
    .via(customer -> ErrorContext.io(
        () -> orderRepo.findByCustomer(customer.id()),
        DbError::fromException))
    .via(orders -> ErrorContext.io(
        () -> invoiceService.generate(orders),
        DbError::fromException));
```

Each step receives the previous result. If any step fails, subsequent steps are skipped; the failure propagates to the end.

### flatMap: Type-Preserving Chain

`flatMap` is equivalent to `via` but with a more explicit type signature:

```java
// Using flatMap instead of via
ErrorContext<IOKind.Witness, ApiError, Profile> profile =
    fetchUser(userId)
        .flatMap(user -> fetchProfile(user.profileId()))
        .flatMap(prof -> enrichProfile(prof));
```

Choose based on readability. Both short-circuit on failure.

### then: Sequence Without Using the Value

When you need to sequence but don't care about the previous value:

```java
ErrorContext<IOKind.Witness, String, Unit> workflow =
    ErrorContext.<String, Unit>success(Unit.INSTANCE)
        .then(() -> logAction("Starting"))
        .then(() -> performAction())
        .then(() -> logAction("Completed"));
```

---

## Recovering from Errors

### recover: Provide a Fallback Value

`recover` catches errors and produces a fallback value:

```java
ErrorContext<IOKind.Witness, String, Config> config =
    ErrorContext.<String, Config>io(
        () -> loadConfigFromServer(),
        Throwable::getMessage)
    .recover(error -> {
        log.warn("Using defaults: {}", error);
        return Config.defaults();
    });
```

If the original computation fails, the recovery function runs. If it succeeds, recovery is never invoked.

### recoverWith: Provide a Fallback Computation

When recovery itself might fail:

```java
ErrorContext<IOKind.Witness, ApiError, Data> data =
    ErrorContext.<ApiError, Data>io(
        () -> primaryService.fetch(),
        ApiError::fromException)
    .recoverWith(error -> ErrorContext.io(
        () -> backupService.fetch(),
        ApiError::fromException));
```

The fallback is another `ErrorContext`. This enables fallback chains that can themselves fail.

### orElse: Alternative Without Inspecting Error

When you don't need the error details:

```java
ErrorContext<IOKind.Witness, String, User> user =
    fetchFromCache(userId)
        .orElse(() -> fetchFromDatabase(userId))
        .orElse(() -> ErrorContext.success(User.anonymous()));
```

---

## Transforming Errors

### mapError: Change the Error Type

`mapError` transforms the error without affecting success values:

```java
ErrorContext<IOKind.Witness, String, User> lowLevel = ErrorContext.failure("connection refused");

ErrorContext<IOKind.Witness, ApiError, User> highLevel =
    lowLevel.mapError(msg -> new ApiError(500, msg));
```

This is essential when composing code from different modules that use different error types.

---

## Execution

### runIO: Get an IOPath

`runIO()` extracts an `IOPath<Either<E, A>>` for deferred execution:

```java
ErrorContext<IOKind.Witness, ApiError, User> ctx = ...;

// Get the IOPath (nothing runs yet)
IOPath<Either<ApiError, User>> ioPath = ctx.runIO();

// Execute when ready
Either<ApiError, User> result = ioPath.unsafeRun();
```

### runIOOrThrow: Success or Exception

For cases where failure should throw:

```java
try {
    User user = userContext.runIOOrThrow();
    // Use the user
} catch (RuntimeException e) {
    // Error was wrapped: e.getMessage() contains error.toString()
}
```

### runIOOrElse: Success or Default

For cases where failure should use a default:

```java
User user = userContext.runIOOrElse(User.guest());
```

### runIOOrElseGet: Success or Computed Default

When the default depends on the error:

```java
User user = userContext.runIOOrElseGet(error -> {
    log.error("Failed with: {}", error);
    return User.guest();
});
```

---

## Real-World Patterns

### API Client with Error Handling

```java
public class UserClient {
    public ErrorContext<IOKind.Witness, ApiError, User> fetchUser(String id) {
        return ErrorContext.<ApiError, User>io(
            () -> {
                HttpResponse response = httpClient.get("/users/" + id);
                if (response.status() == 404) {
                    throw new NotFoundException("User not found: " + id);
                }
                return parseUser(response.body());
            },
            ApiError::fromException);
    }

    public ErrorContext<IOKind.Witness, ApiError, Profile> fetchProfile(User user) {
        return ErrorContext.<ApiError, Profile>io(
            () -> {
                HttpResponse response = httpClient.get("/profiles/" + user.profileId());
                return parseProfile(response.body());
            },
            ApiError::fromException);
    }
}

// Usage
ErrorContext<IOKind.Witness, ApiError, Profile> profile =
    client.fetchUser(userId)
        .via(client::fetchProfile)
        .recover(error -> Profile.empty());
```

### Multi-Step Transaction

```java
public ErrorContext<IOKind.Witness, OrderError, Order> processOrder(OrderRequest request) {
    return validateRequest(request)
        .via(this::checkInventory)
        .via(this::reserveItems)
        .via(this::chargePayment)
        .via(this::createOrder)
        .recoverWith(error -> {
            // Compensating action
            return rollbackReservations()
                .then(() -> ErrorContext.failure(error));
        });
}
```

### Layered Error Types

```java
// Low-level: database errors
ErrorContext<IOKind.Witness, DbError, User> dbUser =
    ErrorContext.io(() -> db.query("SELECT ..."), DbError::fromSql);

// Mid-level: repository errors
ErrorContext<IOKind.Witness, RepoError, User> repoUser =
    dbUser.mapError(RepoError::fromDbError);

// High-level: API errors
ErrorContext<IOKind.Witness, ApiError, User> apiUser =
    repoUser.mapError(ApiError::fromRepoError);
```

---

## Escape Hatch

When you need the raw transformer:

```java
ErrorContext<IOKind.Witness, String, Integer> ctx = ErrorContext.success(42);

EitherT<IOKind.Witness, String, Integer> transformer = ctx.toEitherT();

// Now you have full EitherT capabilities
```

---

## Summary

| Operation | Purpose | Short-circuits on error? |
|-----------|---------|--------------------------|
| `io(supplier, errorMapper)` | Create from throwing computation | N/A |
| `success(value)` | Create successful context | N/A |
| `failure(error)` | Create failed context | N/A |
| `map(f)` | Transform success value | Yes |
| `via(f)` | Chain dependent computation | Yes |
| `recover(f)` | Provide fallback value | No |
| `recoverWith(f)` | Provide fallback computation | No |
| `mapError(f)` | Transform error type | No |
| `runIO()` | Extract IOPath for execution | N/A |

`ErrorContext` brings order to effectful error handling. Exceptions become data. Failures propagate predictably. Recovery is explicit. The shapes in the night become visible.

~~~admonish tip title="See Also"
- [EitherT Transformer](../transformers/eithert_transformer.md) - The underlying transformer
- [Either Monad](../monads/either_monad.md) - The Either type
- [MonadError](../functional/monad_error.md) - The type class powering recovery
~~~

---

**Previous:** [Effect Contexts](effect_contexts.md)
**Next:** [Optional Contexts](effect_contexts_optional.md)
