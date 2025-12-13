# Patterns and Recipes

This chapter presents real-world patterns and recipes for using the Effect Path API effectively.

~~~admonish info title="What You'll Learn"
- Validation pipeline patterns for single fields and combined validations
- Service layer patterns for repositories and chained service calls
- IO effect patterns for resource management and composing effects
- Error handling strategies: enrichment, recovery with logging, circuit breakers
- Testing patterns for Path-returning methods
- Integration patterns with existing code
~~~

## Validation Pipelines

### Single Field Validation

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

```java
record User(String name, String email, int age) {}

EitherPath<String, User> validateUser(UserInput input) {
    EitherPath<String, String> name = validateName(input.name());
    EitherPath<String, String> email = validateEmail(input.email());
    EitherPath<String, Integer> age = validateAge(input.age());

    return name.zipWith3(email, age, User::new);
}
```

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

---

## Service Layer Patterns

### Repository Pattern

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

### Testing Path-returning Methods

```java
@Test
void shouldReturnUserWhenFound() {
    when(repository.findById("123")).thenReturn(Maybe.just(testUser));

    EitherPath<UserError, User> result = service.getUserById("123");

    assertThat(result.run().isRight()).isTrue();
    assertThat(result.run().getRight()).isEqualTo(testUser);
}

@Test
void shouldReturnErrorWhenNotFound() {
    when(repository.findById("123")).thenReturn(Maybe.nothing());

    EitherPath<UserError, User> result = service.getUserById("123");

    assertThat(result.run().isLeft()).isTrue();
    assertThat(result.run().getLeft()).isInstanceOf(UserError.NotFound.class);
}
```

### Testing Chains

```java
@Test
void shouldPropagateFirstError() {
    // Given validation that fails on name
    EitherPath<String, String> invalidName = Path.left("Name too short");
    EitherPath<String, String> validEmail = Path.right("test@example.com");

    // When combining
    EitherPath<String, User> result = invalidName.zipWith(validEmail, User::new);

    // Then first error is returned
    assertThat(result.run().getLeft()).isEqualTo("Name too short");
}
```

### Property-Based Testing

```java
@Property
void functorIdentityLaw(@ForAll @StringLength(min = 1, max = 100) String value) {
    MaybePath<String> path = Path.just(value);
    MaybePath<String> result = path.map(Function.identity());

    assertThat(result.run()).isEqualTo(path.run());
}

@Property
void monadLeftIdentity(
        @ForAll @IntRange(min = -100, max = 100) int value,
        @ForAll("intToMaybeStringFunctions") Function<Integer, MaybePath<String>> f) {

    MaybePath<String> leftSide = Path.just(value).via(f);
    MaybePath<String> rightSide = f.apply(value);

    assertThat(leftSide.run()).isEqualTo(rightSide.run());
}
```

---

## Integration with Existing Code

### Wrapping Exception-Throwing APIs

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

### Pitfall 1: Unnecessary Conversions

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

- **Create** paths with `Path.just()`, `Path.right()`, `Path.tryOf()`, `Path.io()`
- **Transform** with `map()`
- **Chain** with `via()` for dependent computations
- **Combine** with `zipWith()` for independent computations
- **Convert** between types with `toEitherPath()`, `toMaybePath()`, etc.
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
