# Patterns and Recipes

> *"When the going gets weird, the weird turn professional."*
>
> — Hunter S. Thompson, *Fear and Loathing on the Campaign Trail '72*

Every codebase eventually gets weird. Edge cases multiply. Requirements
contradict. Legacy systems refuse to behave. The patterns in this chapter
are for those moments: tested approaches from production code where the
weird was met professionally.

These aren't academic exercises. They're recipes that survived contact with
reality.

~~~admonish info title="What You'll Learn"
- Validation pipeline patterns for single fields and combined validations
- Service layer patterns for repositories and chained service calls
- IO effect patterns for resource management and composing effects
- Error handling strategies: enrichment, recovery with logging, circuit breakers
- Testing patterns for Path-returning methods
- Integration patterns with existing code
~~~

---

## Validation Pipelines

User input arrives untrustworthy. Every field might be missing, malformed,
or actively hostile. Traditional validation scatters null checks and
conditionals throughout your code. Path types let you build validation as
composable pipelines where each rule is small, testable, and reusable.

### Single Field Validation

Each field gets its own validation function returning a Path:

<!-- verify -->
```java
EitherPath<String, String> validateEmail(String email) {
    if (email == null || email.isBlank()) {
        return Path.left("Email is required");
    }
    if (!email.contains("@")) {
        return Path.left("Email must contain @");
    }
    if (!email.contains(".")) {
        return Path.left("Email must contain a domain");
    }
    return Path.right(email.toLowerCase().trim());
}
```

Or with modern pattern matching:

<!-- verify -->
```java
EitherPath<String, String> validateEmail(String email) {
    return switch (email) {
        case null -> Path.left("Email is required");
        case String e when e.isBlank() -> Path.left("Email is required");
        case String e when !e.contains("@") -> Path.left("Email must contain @");
        case String e when !e.contains(".") -> Path.left("Email must have a domain");
        case String e -> Path.right(e.toLowerCase().trim());
    };
}
```

The validated value may be transformed (lowercase, trimmed); the Path
carries the clean version forward.

### Combining Validations (Fail-Fast)

When all fields must be valid to proceed:

<!-- verify -->
```java
record User(String name, String email, int age) {}

EitherPath<String, User> validateUser(UserInput input) {
    return validateName(input.name())
        .zipWith3(
            validateEmail(input.email()),
            validateAge(input.age()),
            User::new
        );
}
```

First failure stops processing. For user-facing forms, this is often too
abrupt; see the next pattern.

### Combining Validations (Accumulating)

When users deserve to see all problems at once:

<!-- verify -->
```java
ValidationPath<List<String>, User> validateUserAccumulating(UserInput input) {
    return validateNameV(input.name())
        .zipWith3Accum(
            validateEmailV(input.email()),
            validateAgeV(input.age()),
            User::new
        );
}

// Usage
validateUserAccumulating(input).run().fold(
    errors -> showAllErrors(errors),  // ["Name too short", "Invalid email"]
    user -> proceed(user)
);
```

The difference is respect for the user's time.

### Nested Validation

Complex objects with nested structures:

<!-- verify -->
```java
record Registration(User user, Address address, List<Preference> prefs) {}

EitherPath<String, Registration> validateRegistration(RegistrationInput input) {
    EitherPath<String, User> user = validateUser(input.user());

    EitherPath<String, Address> address = validateStreet(input.street())
        .zipWith3(
            validateCity(input.city()),
            validatePostcode(input.postcode()),
            Address::new
        );

    EitherPath<String, List<Preference>> prefs =
        validatePreferences(input.preferences());

    return user.zipWith3(address, prefs, Registration::new);
}
```

Each sub-validation is independent; they combine at the end.

---

## Service Layer Patterns

Services orchestrate multiple operations: fetching data, applying business
rules, calling external systems. Each step might fail differently. Path types
make the orchestration explicit.

### Repository Pattern

Repositories return `Maybe` (absence is expected). Services convert to
`Either` (absence becomes an error in context):

<!-- verify -->
```java
public class UserRepository {
    private final JdbcTemplate jdbcTemplate = new JdbcTemplate();

    public Maybe<User> findById(String id) {
        return Maybe.fromOptional(
            jdbcTemplate.queryForOptional("SELECT...", id)
        );
    }

    public Maybe<User> findByEmail(String email) {
        return Maybe.fromOptional(
            jdbcTemplate.queryForOptional("SELECT...", email)
        );
    }
}

```

<!-- verify -->
```java
public class UserService {
    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    public EitherPath<UserError, User> getById(String id) {
        return Path.maybe(repository.findById(id))
            .toEitherPath(new UserError.NotFound(id));
    }

    public EitherPath<UserError, User> getByEmail(String email) {
        return Path.maybe(repository.findByEmail(email))
            .toEitherPath(new UserError.NotFound(email));
    }
}
```

The conversion happens at the layer boundary. Repository callers get `Maybe`;
service callers get `Either` with meaningful errors.

### Chained Service Calls

When each step depends on previous results, use `ForPath` to keep all
intermediate values in scope via the accumulated tuple:

<!-- verify -->
```java
public class OrderService {
    private final UserService users;
    private final InventoryService inventory;
    private final PaymentService payments;

    public OrderService(UserService users, InventoryService inventory,
            PaymentService payments) {
        this.users = users;
        this.inventory = inventory;
        this.payments = payments;
    }

    public EitherPath<OrderError, Order> placeOrder(
            String userId, List<Item> items) {
        return ForPath.from(users.getById(userId)
                .mapError(OrderError::fromUserError))
            .from(user -> inventory.reserve(items)
                .mapError(OrderError::fromInventoryError))
            .from(t -> payments.charge(t._1(), t._2().total())  // user, reservation
                .mapError(OrderError::fromPaymentError))
            .yield((user, reservation, payment) ->
                createOrder(user, items, payment));
    }

    private Order createOrder(User user, List<Item> items, Payment payment) {
        return new Order(payment.reference());
    }
}
```

Each `mapError` translates the sub-service error into the order domain.
The `yield` destructures all three values by name, and the `Order` is
created only if all steps succeed.

### Service with Fallbacks

When multiple sources can satisfy a request:

<!-- verify -->
```java
public class ConfigService {
    private static final Logger log = new Logger();

    public EitherPath<ConfigError, Config> loadConfig() {
        return Path.either(loadFromFile())
            .recoverWith(e -> {
                log.warn("File config unavailable: {}", e.getMessage());
                return Path.either(loadFromEnvironment());
            })
            .recoverWith(e -> {
                log.warn("Env config unavailable: {}", e.getMessage());
                return Path.right(Config.defaults());
            });
    }

    private Either<ConfigError, Config> loadFromFile() {
        return Either.right(new Config("file"));
    }

    private Either<ConfigError, Config> loadFromEnvironment() {
        return Either.right(new Config("env"));
    }
}
```

The logs record what was tried; the caller gets a working config or a clear
failure.

---

## IO Effect Patterns

### Resource Management

Acquire, use, release, regardless of success or failure:

<!-- verify -->
```java
// The parameter is a File, not a java.nio.file.Path: that name is taken here by the
// effect Path this very snippet calls.
IOPath<ProcessResult> process(File file) {
    return Path.io(() -> openReader(file))
        .via(reader -> Path.io(() -> processContent(reader)))
        .guarantee(() -> {
            // Cleanup runs regardless of outcome
            log.debug("Processing complete: {}", file);
        });
}
```

For true resource safety with acquisition and release:

<!-- verify -->
```java
public IOPath<Result> withConnection(Function<Connection, Result> action) {
    return Path.io(() -> dataSource.getConnection())
        .via(conn -> Path.io(() -> action.apply(conn))
            .guarantee(() -> {
                try { conn.close(); }
                catch (SQLException e) { log.warn("Close failed", e); }
            }));
}
```

### Composing Effect Pipelines

Build complex pipelines that execute later:

<!-- verify -->
```java
IOPath<Report> generateReport(ReportRequest request) {
    return Path.ioRunnable(() -> log.info("Starting report: {}", request.id()))
        .then(() -> Path.io(() -> fetchData(request)))
        .via(data -> Path.io(() -> transform(data)))
        .via(transformed -> Path.io(() -> aggregate(transformed)))
        .via(aggregated -> Path.io(() -> format(aggregated)))
        .peek(report -> log.info("Report ready: {} rows", report.rowCount()));
}

// Nothing happens until:
Report report = pipeline.generateReport(reportRequest).unsafeRun();
```

### Expressing Parallel Intent

While `IOPath` doesn't parallelise automatically, `zipWith` expresses
independence:

<!-- verify -->
```java
IOPath<CombinedData> fetchAll() {
    IOPath<UserData> users = Path.io(() -> fetchUsers());
    IOPath<ProductData> products = Path.io(() -> fetchProducts());
    IOPath<OrderData> orders = Path.io(() -> fetchOrders());

    return users.zipWith3(products, orders, CombinedData::new);
}
```

A more sophisticated runtime could parallelise these. For now, they execute
in sequence, but the structure is clear.

---

## Error Handling Strategies

### Error Enrichment

Add context as errors propagate through layers:

<!-- verify -->
```java
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

// Usage
EitherPath<DetailedError, User> detailed = withContext(
    userService.getUser(id),
    "getUser",
    Map.of("userId", id, "requestId", requestId)
);
```

When the error surfaces, you know what was happening and with what parameters.

### Recovery with Logging

Log the failure, provide a fallback:

<!-- verify -->
```java
public <A> EitherPath<Error, A> withRecoveryLogging(
        EitherPath<Error, A> path,
        A fallback,
        String operation) {
    return path.recover(error -> {
        log.warn("Operation '{}' failed: {}. Using fallback.",
            operation, error);
        return fallback;
    });
}
```

### Circuit Breaker

Stop calling a failing service:

<!-- verify -->
```java
public class CircuitBreaker<E, A> {
    private static final Logger log = new Logger();

    private final AtomicInteger failures = new AtomicInteger(0);
    private final int threshold;
    private final Supplier<EitherPath<E, A>> fallback;

    public CircuitBreaker(int threshold, Supplier<EitherPath<E, A>> fallback) {
        this.threshold = threshold;
        this.fallback = fallback;
    }

    public EitherPath<E, A> execute(Supplier<EitherPath<E, A>> operation) {
        if (failures.get() >= threshold) {
            log.debug("Circuit open, using fallback");
            return fallback.get();
        }

        return operation.get()
            .peek(success -> failures.set(0))
            .recoverWith(error -> {
                int count = failures.incrementAndGet();
                log.warn("Failure {} of {}", count, threshold);
                return Path.left(error);
            });
    }
}
```

A proper implementation would include timeouts and half-open states. This
shows the pattern.

---

## Resilience: Retry with Backoff

> *"The protocol specified exponential backoff: wait one second, try again;
> wait two seconds, try again; wait four seconds..."*
>
> — Neal Stephenson, *Cryptonomicon*

Transient failures (network blips, brief overloads) often succeed on retry.
But retrying immediately can worsen the problem. The `RetryPolicy` API
provides configurable backoff strategies.

### Creating Retry Policies

<!-- verify -->
```java
// Fixed delay: 100ms between each of 3 attempts
RetryPolicy fixed = RetryPolicy.fixed(3, Duration.ofMillis(100));

// Exponential backoff: 1s, 2s, 4s, 8s...
RetryPolicy exponential = RetryPolicy.exponentialBackoff(5, Duration.ofSeconds(1));

// With jitter to prevent thundering herd
RetryPolicy jittered = RetryPolicy.exponentialBackoffWithJitter(5, Duration.ofSeconds(1));
```

### Applying Retry to Paths

<!-- verify -->
```java
IOPath<Response> resilient = Path.io(() -> httpClient.get(url))
    .withRetry(RetryPolicy.exponentialBackoff(3, Duration.ofSeconds(1)));

// Convenience method for simple cases
IOPath<Response> simple = Path.io(() -> httpClient.get(url))
    .retry(3, Duration.ofSeconds(1));  // Exponential backoff from that delay
```

### Configuring Retry Behaviour

Policies are immutable but configurable:

<!-- verify -->
```java
RetryPolicy policy = RetryPolicy.exponentialBackoff(5, Duration.ofMillis(100))
    .withMaxDelay(Duration.ofSeconds(30))   // Cap maximum wait
    .retryOn(IOException.class);             // Only retry I/O errors
```

### Selective Retry

Not all errors should trigger retry:

<!-- verify -->
```java
RetryPolicy selective = RetryPolicy.fixed(3, Duration.ofMillis(100))
    .retryIf(ex ->
        ex instanceof IOException ||
        ex instanceof TimeoutException ||
        (ex instanceof HttpException http && http.statusCode() >= 500));
```

### Combining Retry with Fallback

<!-- verify -->
```java
IOPath<Data> robust = fetchFromPrimary()
    .withRetry(RetryPolicy.exponentialBackoff(3, Duration.ofSeconds(1)))
    .handleErrorWith(e -> {
        log.warn("Primary exhausted, trying backup");
        return fetchFromBackup()
            .withRetry(RetryPolicy.fixed(2, Duration.ofMillis(100)));
    })
    .handleError(e -> {
        log.error("All sources failed", e);
        return Data.empty();
    });
```

### Handling Exhausted Retries

When all attempts fail, `RetryExhaustedException` is thrown:

<!-- verify -->
```java
Data runOrFallBack() {
    try {
        resilient.unsafeRun();
        return new Data("ok");
    } catch (RetryExhaustedException e) {
        log.error("All retries failed: {}", e.getMessage());
        Throwable lastFailure = e.getCause();
        return fallbackValue;
    }
}
```

### Retry Pattern Quick Reference

| Strategy | Use Case |
|----------|----------|
| `fixed(n, delay)` | Known recovery time |
| `exponentialBackoff(n, initial)` | Unknown recovery time |
| `exponentialBackoffWithJitter(n, initial)` | Multiple clients retrying |
| `.retryOn(ExceptionType.class)` | Selective retry |
| `.withMaxDelay(duration)` | Cap exponential growth |

~~~admonish tip title="See Also"
See [Advanced Effect Topics](advanced_topics.md) for comprehensive coverage of
resilience patterns including resource management with `bracket` and `guarantee`.
~~~

---

## Testing Patterns

Path-returning methods are straightforward to test. The explicit success/failure
encoding means you can verify both paths without exception handling gymnastics.

### Testing Success and Failure

<!-- verify -->
```java
@Test
void shouldReturnUserWhenFound() {
    when(repository.findById("123")).thenReturn(Maybe.just(testUser));

    EitherPath<UserError, User> result = service.getUser("123");

    assertThat(result.run().isRight()).isTrue();
    assertThat(result.run().getRight()).isEqualTo(testUser);
}

@Test
void shouldReturnErrorWhenNotFound() {
    when(repository.findById("123")).thenReturn(Maybe.nothing());

    EitherPath<UserError, User> result = service.getUser("123");

    assertThat(result.run().isLeft()).isTrue();
    assertThat(result.run().getLeft()).isInstanceOf(UserError.NotFound.class);
}
```

### Testing Error Propagation

Verify that errors from nested operations surface correctly:

<!-- verify -->
```java
@Test
void shouldPropagatePaymentError() {
    when(userService.getUser(any())).thenReturn(Path.right(testUser));
    when(inventory.check(any())).thenReturn(Path.right(availability));
    when(payments.charge(any(), any()))
        .thenReturn(Path.left(new PaymentError("Declined")));

    EitherPath<OrderError, Order> result = orderService.placeOrder(request);

    assertThat(result.run().isLeft()).isTrue();
    assertThat(result.run().getLeft())
        .isInstanceOf(OrderError.PaymentFailed.class);

    // Order creation should never be called
    verify(orderRepository, never()).save(any());
}
```

### Property-Based Testing

Use jqwik or similar to verify laws across many inputs:

<!-- verify -->
```java
@Property
void functorIdentityLaw(@ForAll @StringLength(min = 1, max = 100) String value) {
    MaybePath<String> path = Path.just(value);
    MaybePath<String> mapped = path.map(Function.identity());

    assertThat(mapped.run()).isEqualTo(path.run());
}

@Property
void recoverAlwaysSucceeds(
        @ForAll String errorMessage,
        @ForAll String fallback) {
    EitherPath<String, String> failed = Path.left(errorMessage);
    EitherPath<String, String> recovered = failed.recover(e -> fallback);

    assertThat(recovered.run().isRight()).isTrue();
    assertThat(recovered.run().getRight()).isEqualTo(fallback);
}
```

### Testing Deferred Effects

Verify that `IOPath` defers execution:

<!-- verify -->
```java
@Test
void shouldDeferExecution() {
    AtomicInteger callCount = new AtomicInteger(0);
    IOPath<Integer> io = Path.io(() -> callCount.incrementAndGet());

    assertThat(callCount.get()).isEqualTo(0);  // Not yet

    int result = io.unsafeRun();

    assertThat(callCount.get()).isEqualTo(1);  // Now
    assertThat(result).isEqualTo(1);
}

@Test
void shouldCaptureExceptionInRunSafe() {
    IOPath<String> failing = Path.io(() -> {
        throw new RuntimeException("test error");
    });

    Try<String> result = failing.runSafe();

    assertThat(result.isFailure()).isTrue();
    assertThat(result.foldFailureFirst(Throwable::getMessage, value -> value))
        .isEqualTo("test error");
}
```

---

## Integration with Existing Code

Real projects have legacy code that throws exceptions, returns `Optional`,
or uses patterns that predate functional error handling.

### Wrapping Exception-Throwing APIs

<!-- verify -->
```java
public class LegacyWrapper {
    private final LegacyService legacy;

    public LegacyWrapper(LegacyService legacy) {
        this.legacy = legacy;
    }

    public TryPath<Data> fetchData(String id) {
        return Path.tryOf(() -> legacy.fetchData(id));
    }

    public EitherPath<ServiceError, Data> fetchDataSafe(String id) {
        return Path.tryOf(() -> legacy.fetchData(id))
            .toEitherPath(ex -> new ServiceError("Fetch failed", ex));
    }
}
```

### Wrapping Optional-Returning APIs

<!-- verify -->
```java
public class ModernWrapper {
    private final ModernService modern;

    public ModernWrapper(ModernService modern) {
        this.modern = modern;
    }

    public MaybePath<User> findUser(String id) {
        return Path.maybe(Maybe.fromOptional(modern.findUser(id)));
    }
}
```

### Exposing to Non-Path Consumers

When callers expect traditional patterns:

<!-- verify -->
```java
public class ServiceAdapter {
    private final PathBasedService service;

    public ServiceAdapter(PathBasedService service) {
        this.service = service;
    }

    // For consumers expecting Optional
    public Optional<User> findUser(String id) {
        return service.findUser(id).run().toOptional();
    }

    // For consumers expecting exceptions
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

### Pitfall 1: Excessive Conversion

<!-- verify -->
```java
// Wasteful
Path.maybe(findUser(id))
    .toEitherPath(error)
    .toMaybePath()
    .toEitherPath(error);

// Clean
Path.maybe(findUser(id))
    .toEitherPath(error);
```

### Pitfall 2: Side Effects in Pure Operations

<!-- verify -->
```java
// Wrong
path.map(user -> {
    auditLog.record(user);  // Side effect in map!
    return user;
});

// Right
path.peek(user -> auditLog.record(user));
```

### Pitfall 3: Ignoring the Result

<!-- verify -->
```java
// Bug: result discarded, nothing happens
void processOrder(OrderRequest request) {
    validateAndProcess(request);  // Returns EitherPath, ignored
}

// Fixed
Either<OrderError, Order> processOrderAndKeepIt(OrderRequest request) {
    return validateAndProcess(request).run();  // Actually execute
}
```

### Pitfall 4: Treating All Errors the Same

<!-- verify -->
```java
// Loses information
EitherPath<String, User> flattened =
    path.mapError(e -> "An error occurred");

// Preserves structure
EitherPath<DomainError, User> preserved =
    path.mapError(e -> new DomainError(e.code(), e.message(), e));
```

---

## Quick Reference

| Pattern | When to Use | Key Methods |
|---------|-------------|-------------|
| Single validation | Validate one field | `Path.right/left` |
| Combined validation | Multiple independent fields | `zipWith`, `zipWithAccum` |
| Record assembly | N validated fields, every error located | [`fields()` / `accumulate()`](../monads/validated_assembly.md) |
| Repository wrapping | Maybe → Either at boundary | `toEitherPath` |
| Service chaining | Sequential dependent calls | `via`, `mapError` |
| Fallback chain | Multiple sources | `recoverWith` |
| Resource management | Acquire/use/release | `bracket`, `withResource` |
| Cleanup guarantee | Ensure finalizer runs | `guarantee` |
| Effect pipeline | Deferred composition | `via`, `then`, `unsafeRun` |
| Error enrichment | Add context | `mapError` |
| Circuit breaker | Protect failing service | `recover`, `recoverWith` |
| Retry with backoff | Transient failures | `withRetry`, `RetryPolicy` |
| Selective retry | Specific exception types | `.retryOn()`, `.retryIf()` |

---

## Summary

The patterns in this chapter share a common theme: making the implicit explicit.
Error handling becomes visible in the types. Composition becomes visible in
the pipeline. Dependencies become visible in the choice of `via` vs `zipWith`.

When the going gets weird (and it will), these patterns are your professional
toolkit. They won't make the weirdness go away, but they'll help you handle
it with composure.

Continue to [Advanced Effects](advanced_effects.md) for Reader, State, and
Writer patterns.

~~~admonish tip title="See Also"
- [Validated Monad](../monads/validated_monad.md) - Accumulating validation errors
- [MonadError](../functional/monad_error.md) - The type class behind error recovery
- [IO Monad](../monads/io_monad.md) - Deferred effect execution
~~~

---

**Previous:** [Effect Handler Reference](effect_handlers.md)
**Next:** [Resilience Patterns](../resilience/ch_intro.md)
