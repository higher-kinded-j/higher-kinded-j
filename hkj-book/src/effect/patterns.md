# Patterns and Recipes

Every application faces the same challenges: validating input, orchestrating services, handling errors gracefully, and testing complex workflows. This chapter shows how the Effect Path API addresses these challenges with reusable patterns.

The patterns here are not academic exercises. They come from real codebases where tangled error handling was replaced with clear, composable pipelines. Each pattern solves a specific problem that you will recognise from your own projects.

~~~admonish info title="What You'll Learn"
- Validation pipeline patterns for single fields and combined validations
- Service layer patterns for repositories and chained service calls
- IO effect patterns for resource management and composing effects
- Error handling strategies: enrichment, recovery with logging, circuit breakers
- Testing patterns for Path-returning methods
- Integration patterns with existing code
~~~

## Validation Pipelines

User input cannot be trusted. Every field might be missing, malformed, or malicious. Traditional validation scatters null checks and conditionals throughout your code. The Path API lets you build validation as composable pipelines where each rule is a small, testable function.

### Single Field Validation

**The pattern:** Each field gets its own validation function that returns a Path. Success carries the validated value (possibly transformed); failure carries an error message.

```java
private EitherPath<String, String> validateEmail(String email) {
    return switch (email) {
        case null -> Path.left("Email is required");
        case String e when e.isBlank() -> Path.left("Email is required");
        case String e when !e.contains("@") -> Path.left("Email must contain @");
        case String e when !e.contains(".") -> Path.left("Email must contain a domain");
        case String e -> Path.right(e.toLowerCase().trim());
    };
}
```

### Combining Validations

**The pattern:** When multiple fields must all be valid to construct an object, use `zipWith` to combine them. The first error stops processing (fail-fast behaviour).

```java
record User(String name, String email, int age) {}

EitherPath<String, User> validateUser(UserInput input) {
    EitherPath<String, String> name = validateName(input.name());
    EitherPath<String, String> email = validateEmail(input.email());
    EitherPath<String, Integer> age = validateAge(input.age());

    return name.zipWith3(email, age, User::new);
}
```

**Why this works:** `zipWith3` only calls `User::new` if all three validations succeed. If any fails, the error propagates immediately.

### Nested Validation

```java
record Registration(User user, Address address) {}

EitherPath<String, Registration> validateRegistration(RegistrationInput input) {
    // Validate user
    EitherPath<String, User> userValidation = validateUser(input.user());

    // Validate address
    EitherPath<String, Address> addressValidation =
        validateStreet(input.street())
            .zipWith3(
                validateCity(input.city()),
                validateZipCode(input.zipCode()),
                Address::new);

    // Combine
    return userValidation.zipWith(addressValidation, Registration::new);
}
```

~~~admonish tip title="Accumulating All Errors"
For user-facing forms where you want to show *all* validation errors at once, use `ValidationPath` with `zipWithAccum` instead of `EitherPath` with `zipWith`. See [ValidationPath](path_types.md#validationpath) for details.
~~~

---

## Service Layer Patterns

Service layers orchestrate multiple operations: fetching data, applying business rules, calling external services. Each step might fail, and each failure needs different handling. The Path API makes this orchestration explicit and composable.

### Repository Pattern

**The problem:** Repositories return optional values (user might not exist), but services need to turn "not found" into meaningful errors.

```java
public class UserRepository {
    // Returns Maybe - absence is expected
    public Maybe<User> findById(String id) {
        return Maybe.fromOptional(jdbcTemplate.queryForOptional(...));
    }

    public Maybe<User> findByEmail(String email) {
        return Maybe.fromOptional(jdbcTemplate.queryForOptional(...));
    }
}

public class UserService {
    private final UserRepository repository;

    // Returns EitherPath - errors are meaningful
    public EitherPath<UserError, User> getUserById(String id) {
        return Path.maybe(repository.findById(id))
            .toEitherPath(() -> new UserError.NotFound(id));
    }

    public EitherPath<UserError, User> getUserByEmail(String email) {
        return Path.maybe(repository.findByEmail(email))
            .toEitherPath(() -> new UserError.NotFound(email));
    }
}
```

### Chained Service Calls

```java
public class OrderService {
    private final UserService users;
    private final InventoryService inventory;
    private final PaymentService payments;

    public EitherPath<OrderError, Order> placeOrder(String userId, List<Item> items) {
        return users.getUserById(userId)
            .mapError(OrderError::fromUserError)
            .via(user -> inventory.reserve(items)
                .mapError(OrderError::fromInventoryError))
            .via(reservation -> payments.charge(user, reservation.total())
                .mapError(OrderError::fromPaymentError))
            .via(payment -> createOrder(user, items, payment));
    }
}
```

### Service with Fallbacks

```java
public class ConfigService {
    public EitherPath<ConfigError, Config> loadConfig() {
        return Path.either(loadFromFile())
            .recoverWith(e -> {
                log.warn("File config failed: {}", e.getMessage());
                return Path.either(loadFromEnvironment());
            })
            .recoverWith(e -> {
                log.warn("Env config failed: {}", e.getMessage());
                return Path.right(Config.defaults());
            });
    }
}
```

---

## IO Effect Patterns

### Resource Management

```java
public class FileProcessor {
    public IOPath<ProcessResult> processFile(Path path) {
        return Path.io(() -> {
                var reader = new BufferedReader(new FileReader(path.toFile()));
                return reader;
            })
            .via(reader -> Path.io(() -> processContent(reader)))
            .ensuring(() -> {
                // Cleanup runs regardless of success/failure
                log.debug("Processing complete for: {}", path);
            });
    }
}
```

### Composing Effects

```java
public class DataPipeline {
    public IOPath<Report> generateReport(ReportRequest request) {
        return Path.io(() -> log.info("Starting report generation"))
            .then(() -> Path.io(() -> fetchData(request)))
            .via(data -> Path.io(() -> transformData(data)))
            .via(transformed -> Path.io(() -> aggregateResults(transformed)))
            .via(aggregated -> Path.io(() -> formatReport(aggregated)))
            .peek(report -> log.info("Report generated: {} rows", report.rowCount()));
    }
}
```

### Parallel IO (Conceptual)

```java
// While IOPath itself doesn't parallelize, you can use zipWith
// to express independent computations:
IOPath<CombinedData> fetchAll() {
    IOPath<UserData> users = Path.io(() -> fetchUsers());
    IOPath<ProductData> products = Path.io(() -> fetchProducts());
    IOPath<OrderData> orders = Path.io(() -> fetchOrders());

    return users.zipWith3(products, orders, CombinedData::new);
}
```

---

## Error Handling Strategies

### Error Enrichment

```java
public class EnrichedErrorHandler {
    public <A> EitherPath<DetailedError, A> withContext(
            EitherPath<Error, A> path,
            String operation,
            Map<String, Object> context) {

        return path.mapError(error -> new DetailedError(
            error,
            operation,
            context,
            Instant.now()
        ));
    }
}

// Usage
return withContext(
    userService.getUser(id),
    "getUser",
    Map.of("userId", id, "requestId", requestId)
);
```

### Error Recovery with Logging

```java
public <A> EitherPath<Error, A> withRecoveryLogging(
        EitherPath<Error, A> path,
        A fallback,
        String operation) {

    return path.recover(error -> {
        log.warn("Operation {} failed: {}. Using fallback.", operation, error);
        return fallback;
    });
}
```

### Circuit Breaker Pattern

```java
public class CircuitBreaker<E, A> {
    private final AtomicInteger failures = new AtomicInteger(0);
    private final int threshold;
    private final Supplier<EitherPath<E, A>> fallback;

    public EitherPath<E, A> execute(Supplier<EitherPath<E, A>> operation) {
        if (failures.get() >= threshold) {
            return fallback.get();
        }

        return operation.get()
            .peek(success -> failures.set(0))
            .recoverWith(error -> {
                failures.incrementAndGet();
                return Path.left(error);
            });
    }
}
```

---

## Testing Patterns

Path-returning methods are inherently testable. The explicit success/failure encoding means you can verify both happy paths and error cases without exception handling in your tests. The lawful behaviour of Path types also enables property-based testing that catches edge cases you might not think to test manually.

### Testing Success and Failure Paths

**The pattern:** Call `.run()` to extract the underlying type, then assert on its state. Test both the success case and relevant failure cases.

```java
@Test
void shouldReturnUserWhenFound() {
    // Given
    when(repository.findById("123")).thenReturn(Maybe.just(testUser));

    // When
    EitherPath<UserError, User> result = service.getUserById("123");

    // Then
    assertThat(result.run().isRight()).isTrue();
    assertThat(result.run().getRight()).isEqualTo(testUser);
}

@Test
void shouldReturnErrorWhenNotFound() {
    // Given
    when(repository.findById("123")).thenReturn(Maybe.nothing());

    // When
    EitherPath<UserError, User> result = service.getUserById("123");

    // Then
    assertThat(result.run().isLeft()).isTrue();
    assertThat(result.run().getLeft()).isInstanceOf(UserError.NotFound.class);
}
```

### Testing Error Propagation

**The problem:** When chaining multiple operations, you need to verify that errors from any step propagate correctly.

**The pattern:** Create inputs that fail at specific steps and verify the error emerges unchanged.

```java
@Test
void shouldPropagateFirstError() {
    // Given validation that fails on name
    EitherPath<String, String> invalidName = Path.left("Name too short");
    EitherPath<String, String> validEmail = Path.right("test@example.com");

    // When combining
    EitherPath<String, User> result = invalidName.zipWith(validEmail, User::new);

    // Then first error is returned (not swallowed or transformed)
    assertThat(result.run().getLeft()).isEqualTo("Name too short");
}

@Test
void shouldPropagateErrorThroughChain() {
    // Given a chain where the second step fails
    EitherPath<String, Integer> result =
        Path.right("hello")
            .via(s -> Path.left("Error in step 2"))
            .via(x -> Path.right(42));  // Never reached

    // Then the error from step 2 propagates
    assertThat(result.run().getLeft()).isEqualTo("Error in step 2");
}
```

### Testing with Mocked Dependencies

**The pattern:** Mock repository and service dependencies to return specific Path values, then verify the orchestration logic.

```java
@Test
void shouldCombineUserAndOrderData() {
    // Given
    when(userService.getUser(userId)).thenReturn(Path.right(testUser));
    when(orderService.getOrders(testUser)).thenReturn(Path.right(testOrders));

    // When
    EitherPath<Error, UserWithOrders> result =
        compositeService.getUserWithOrders(userId);

    // Then
    assertThat(result.run().isRight()).isTrue();
    UserWithOrders data = result.run().getRight();
    assertThat(data.user()).isEqualTo(testUser);
    assertThat(data.orders()).isEqualTo(testOrders);
}

@Test
void shouldFailIfUserServiceFails() {
    // Given
    when(userService.getUser(userId))
        .thenReturn(Path.left(new Error.UserNotFound(userId)));

    // When
    EitherPath<Error, UserWithOrders> result =
        compositeService.getUserWithOrders(userId);

    // Then - order service should never be called
    verify(orderService, never()).getOrders(any());
    assertThat(result.run().isLeft()).isTrue();
}
```

### Property-Based Testing

**The pattern:** Use property-based testing (with jqwik or similar) to verify that Path types obey their laws across many random inputs. This catches edge cases that example-based tests miss.

```java
@Property
void functorIdentityLaw(@ForAll @StringLength(min = 1, max = 100) String value) {
    // Law: path.map(identity) == path
    MaybePath<String> path = Path.just(value);
    MaybePath<String> result = path.map(Function.identity());

    assertThat(result.run()).isEqualTo(path.run());
}

@Property
void monadLeftIdentity(
        @ForAll @IntRange(min = -100, max = 100) int value,
        @ForAll("intToMaybeStringFunctions") Function<Integer, MaybePath<String>> f) {

    // Law: Path.just(a).via(f) == f(a)
    MaybePath<String> leftSide = Path.just(value).via(f);
    MaybePath<String> rightSide = f.apply(value);

    assertThat(leftSide.run()).isEqualTo(rightSide.run());
}

@Property
void recoverAlwaysSucceeds(@ForAll String errorMessage, @ForAll String fallback) {
    // Property: recover always produces a success
    EitherPath<String, String> failed = Path.left(errorMessage);
    EitherPath<String, String> recovered = failed.recover(e -> fallback);

    assertThat(recovered.run().isRight()).isTrue();
    assertThat(recovered.run().getRight()).isEqualTo(fallback);
}
```

### Testing IOPath Effects

**The problem:** IOPath defers execution until `run()` is called. You need to verify both that the effect is properly deferred and that it executes correctly.

```java
@Test
void shouldDeferExecution() {
    AtomicInteger callCount = new AtomicInteger(0);
    IOPath<Integer> io = Path.io(() -> callCount.incrementAndGet());

    // Effect not yet executed
    assertThat(callCount.get()).isEqualTo(0);

    // Execute
    int result = io.unsafeRun();

    // Now executed exactly once
    assertThat(callCount.get()).isEqualTo(1);
    assertThat(result).isEqualTo(1);
}

@Test
void shouldCaptureExceptionInRunSafe() {
    IOPath<String> failing = Path.io(() -> {
        throw new RuntimeException("test error");
    });

    Try<String> result = failing.runSafe();

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).isEqualTo("test error");
}
```

---

## Integration with Existing Code

Real projects do not start with a blank slate. You have existing code that throws exceptions, returns `Optional`, or uses other patterns. The Path API provides bridges to work with this code without rewriting everything.

### Wrapping Exception-Throwing APIs

**The problem:** Legacy code throws exceptions, but you want to use Path composition.

**The solution:** Use `Path.tryOf` to capture exceptions as `TryPath` failures.

```java
public class LegacyWrapper {
    private final LegacyService legacy;

    public TryPath<Data> fetchData(String id) {
        return Path.tryOf(() -> legacy.fetchData(id));  // May throw
    }

    public EitherPath<ServiceError, Data> fetchDataSafe(String id) {
        return Path.tryOf(() -> legacy.fetchData(id))
            .toEitherPath(ex -> new ServiceError("Fetch failed", ex));
    }
}
```

### Wrapping Optional-returning APIs

```java
public class OptionalWrapper {
    private final ModernService modern;

    public MaybePath<User> findUser(String id) {
        Optional<User> result = modern.findUser(id);
        return Path.maybe(Maybe.fromOptional(result));
    }
}
```

### Exposing to Non-Path Consumers

```java
public class ServiceAdapter {
    private final PathBasedService service;

    // For consumers that expect Optional
    public Optional<User> findUser(String id) {
        return service.findUser(id).run().toOptional();
    }

    // For consumers that expect exceptions
    public User getUser(String id) throws UserNotFoundException {
        Either<UserError, User> result = service.getUser(id).run();
        if (result.isLeft()) {
            throw new UserNotFoundException(result.getLeft().message());
        }
        return result.getRight();
    }
}
```

---

## Common Pitfalls

The Path API is straightforward, but a few patterns can trip up newcomers. These pitfalls come from treating Paths like regular values when they are actually descriptions of computations.

### Pitfall 1: Unnecessary Conversions

**The issue:** Converting between path types repeatedly wastes effort and obscures intent.

```java
// Bad: Converting back and forth
Path.maybe(findUser(id))
    .toEitherPath(() -> error)
    .toMaybePath()  // Why?
    .toEitherPath(() -> error);  // Wasteful

// Good: Convert once
Path.maybe(findUser(id))
    .toEitherPath(() -> error);
```

### Pitfall 2: Forgetting to Run

**The issue:** Paths are lazy descriptions. Without calling `.run()`, nothing actually happens.

```java
// Bug: path is never executed
void processUser(String id) {
    Path.maybe(findUser(id))
        .map(this::processUser);  // Nothing happens!
}

// Correct: extract the result
void processUser(String id) {
    Path.maybe(findUser(id))
        .map(this::processUser)
        .run();  // Now it executes
}
```

### Pitfall 3: Side Effects in map

**The issue:** The `map` function should be pure (no side effects). Side effects in `map` break referential transparency and can lead to surprising behaviour.

```java
// Bad: Side effect in map
path.map(user -> {
    database.save(user);  // Side effect!
    return user;
});

// Good: Use peek for side effects
path.peek(user -> database.save(user));

// Or use IOPath for deferred effects
Path.io(() -> database.save(user));
```

---

## Quick Reference

| Pattern | Use Case | Example |
|---------|----------|---------|
| Validation | Combine validations | `name.zipWith(email, User::new)` |
| Service chain | Dependent calls | `path.via(x -> nextService(x))` |
| Fallback | Default on error | `path.recover(e -> default)` |
| Error transform | Change error type | `path.mapError(ApiError::new)` |
| Type conversion | Change path type | `path.toEitherPath(err)` |
| Debug | Add logging | `path.peek(x -> log(x))` |
| Resource | Cleanup | `path.ensuring(() -> cleanup())` |

---

## Summary

The Effect Path API provides a consistent vocabulary for working with effectful computations:

- **Create** paths with `Path.just()`, `Path.right()`, `Path.tryOf()`, `Path.io()`, `Path.valid()`
- **Transform** with `map()`
- **Chain** with `via()` for dependent computations
- **Combine** with `zipWith()` for independent computations (fail-fast)
- **Accumulate** with `zipWithAccum()` for validation (collect all errors)
- **Convert** between types with `toEitherPath()`, `toValidationPath()`, etc.
- **Handle errors** with `recover()`, `recoverWith()`, `mapError()`
- **Extract** with `run()`, `getOrElse()`, `unsafeRun()`

The patterns in this chapter demonstrate how these operations compose to solve real-world problems while keeping code clear and maintainable.

~~~admonish tip title="See Also"
- [Validated Monad](../monads/validated_monad.md) - Accumulating validation errors
- [MonadError](../functional/monad_error.md) - The type class behind error recovery
- [IO Monad](../monads/io_monad.md) - Deferred effect execution
~~~

---

**Previous:** [Type Conversions](conversions.md)
