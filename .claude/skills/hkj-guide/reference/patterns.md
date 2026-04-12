# Effect Path Patterns Reference

Practical patterns for Higher-Kinded-J Effect Paths in production code.

---

## 1. Validation Pipelines

### Single Field Validation

Each field gets its own function returning `EitherPath`. The success track carries the cleaned value.

```java
private EitherPath<String, String> validateEmail(String email) {
    return switch (email) {
        case null -> Path.left("Email is required");
        case String e when e.isBlank() -> Path.left("Email is required");
        case String e when !e.contains("@") -> Path.left("Email must contain @");
        case String e -> Path.right(e.toLowerCase().trim());
    };
}
```

### Combining Validations: Fail-Fast vs Accumulating

```java
// Fail-fast: first error short-circuits (use for API calls, sequential deps)
EitherPath<String, User> validateUser(UserInput in) {
    return validateName(in.name())
        .zipWith3(validateEmail(in.email()), validateAge(in.age()), User::new);
}

// Accumulating: collects ALL errors (use for user-facing forms)
ValidationPath<List<String>, User> validateUser(UserInput in) {
    return validateNameV(in.name())
        .zipWith3Accum(validateEmailV(in.email()), validateAgeV(in.age()), User::new);
}
// Extract: validateUser(in).run().fold(errors -> showAll(errors), user -> proceed(user));
```

Nest by validating sub-objects independently, then combining with `zipWith3` at the top level.

| Strategy | Method | Error Behavior | Best For |
|----------|--------|----------------|----------|
| Fail-fast | `zipWith` / `zipWith3` | Stops at first | API calls, sequential deps |
| Accumulating | `zipWithAccum` / `zipWith3Accum` | Collects all | User-facing forms |

---

## 2. Service Layer Patterns

### Repository Pattern (Maybe to Either at Boundary)

Repositories return `Maybe` (absence is normal). Services convert to `Either` (absence is an error).

```java
// Repository
public Maybe<User> findById(String id) {
    return Maybe.fromOptional(jdbcTemplate.queryForOptional("SELECT...", id));
}

// Service -- converts at the boundary
public EitherPath<UserError, User> getById(String id) {
    return Path.maybe(repository.findById(id))
        .toEitherPath(() -> new UserError.NotFound(id));
}
```

### Chained Service Calls with ForPath

Use `ForPath` when each step depends on previous results. Use `.mapError()` to unify error types.

```java
public EitherPath<OrderError, Order> placeOrder(String userId, List<Item> items) {
    return ForPath.from(users.getById(userId).mapError(OrderError::fromUserError))
        .from(user -> inventory.reserve(items).mapError(OrderError::fromInventoryError))
        .from(t -> payments.charge(t._1(), t._2().total()).mapError(OrderError::fromPaymentError))
        .yield((user, reservation, payment) -> createOrder(user, items, payment));
}
```

### Service with Fallback Chain

```java
public EitherPath<ConfigError, Config> loadConfig() {
    return Path.either(loadFromFile())
        .recoverWith(e -> { log.warn("File unavailable: {}", e); return Path.either(loadFromEnv()); })
        .recoverWith(e -> { log.warn("Env unavailable: {}", e); return Path.right(Config.defaults()); });
}
```

---

## 3. IO Effect Patterns

### Resource Management

Acquire, use, release -- cleanup runs via `ensuring` regardless of outcome.

```java
public IOPath<Result> withConnection(Function<Connection, Result> action) {
    return Path.io(() -> dataSource.getConnection())
        .via(conn -> Path.io(() -> action.apply(conn))
            .ensuring(() -> { try { conn.close(); } catch (SQLException e) { log.warn("Close failed", e); } }));
}
```

### Composing Deferred Pipelines

Nothing executes until the terminal call. Use `then` to sequence, `via` to chain dependent steps.

```java
public IOPath<Report> generateReport(ReportRequest req) {
    return Path.io(() -> fetchData(req))
        .via(data -> Path.io(() -> transform(data)))
        .via(transformed -> Path.io(() -> format(transformed)))
        .peek(report -> log.info("Ready: {} rows", report.rowCount()));
}
Report r = pipeline.generateReport(req).unsafeRun(); // executes here
```

### Parallel Intent

`zipWith` on IOPath declares independence (currently sequential, structured for future parallelism):
`users.zipWith3(products, orders, CombinedData::new)`

| Method | Returns | Behavior |
|--------|---------|----------|
| `.unsafeRun()` | `A` | Executes, throws on failure |
| `.runSafe()` | `Try<A>` | Executes, captures exceptions in `Try` |

---

## 4. Error Handling Strategies

### Error Enrichment with mapError

```java
public <A> EitherPath<DetailedError, A> withContext(
        EitherPath<Error, A> path, String op, Map<String, Object> ctx) {
    return path.mapError(e -> new DetailedError(e, op, ctx, Instant.now()));
}
```

### Recovery with Logging

```java
path.recover(error -> { log.warn("Failed: {}. Using fallback.", error); return fallback; });
```

### Circuit Breaker

Track failures in an `AtomicInteger`; when threshold is exceeded, skip the call and return `fallback.get()`. On success, reset counter with `.peek(ok -> failures.set(0))`. On failure, increment with `.recoverWith(err -> { failures.incrementAndGet(); return Path.left(err); })`.

### Retry with Backoff

```java
// Policies
RetryPolicy.fixed(3, Duration.ofMillis(100));
RetryPolicy.exponentialBackoff(5, Duration.ofSeconds(1));
RetryPolicy.exponentialBackoffWithJitter(5, Duration.ofSeconds(1));

// Apply
IOPath<Response> resilient = IOPath.delay(() -> httpClient.get(url))
    .withRetry(RetryPolicy.exponentialBackoff(3, Duration.ofSeconds(1)));

// Configure
RetryPolicy policy = RetryPolicy.exponentialBackoff(5, Duration.ofMillis(100))
    .withMaxDelay(Duration.ofSeconds(30))
    .retryOn(IOException.class);        // or .retryIf(ex -> ex instanceof IOException)

// Retry + fallback
IOPath<Data> robust = fetchFromPrimary()
    .withRetry(RetryPolicy.exponentialBackoff(3, Duration.ofSeconds(1)))
    .handleErrorWith(e -> fetchFromBackup().withRetry(RetryPolicy.fixed(2, Duration.ofMillis(100))))
    .recover(e -> Data.empty());
```

| Strategy | Factory | Use Case |
|----------|---------|----------|
| Fixed delay | `RetryPolicy.fixed(n, delay)` | Known recovery time |
| Exponential | `RetryPolicy.exponentialBackoff(n, initial)` | Unknown recovery time |
| Jittered | `RetryPolicy.exponentialBackoffWithJitter(n, initial)` | Multiple clients retrying |
| Cap delay | `.withMaxDelay(duration)` | Prevent unbounded waits |
| Filter | `.retryOn(Class)` / `.retryIf(pred)` | Selective retry |

### Error Handling Summary

| Pattern | Key Method | When To Use |
|---------|------------|-------------|
| Enrichment | `mapError` | Add context at layer boundaries |
| Recovery | `recover` | Provide a default value |
| Fallback chain | `recoverWith` | Try alternative sources |
| Circuit breaker | `recover` + `recoverWith` | Protect a failing service |
| Retry | `withRetry` + `RetryPolicy` | Transient failures |

---

## 5. Testing Patterns

### Testing Success and Failure Paths

```java
@Test void shouldReturnUserWhenFound() {
    when(repository.findById("123")).thenReturn(Maybe.just(testUser));
    EitherPath<UserError, User> result = service.getUser("123");
    assertThat(result.run().isRight()).isTrue();
    assertThat(result.run().getRight()).isEqualTo(testUser);
}

@Test void shouldReturnErrorWhenNotFound() {
    when(repository.findById("123")).thenReturn(Maybe.nothing());
    assertThat(service.getUser("123").run().isLeft()).isTrue();
    assertThat(service.getUser("123").run().getLeft()).isInstanceOf(UserError.NotFound.class);
}
```

### Testing Error Propagation

Mock earlier steps to succeed, mock the failing step to return `Path.left(...)`, then assert the result is left with the expected error type. Use `verify(repo, never()).save(any())` to confirm short-circuiting.

### Testing Deferred Effects (IOPath)

```java
@Test void shouldDeferExecution() {
    AtomicInteger count = new AtomicInteger(0);
    IOPath<Integer> io = Path.io(() -> count.incrementAndGet());
    assertThat(count.get()).isEqualTo(0);  // not yet
    io.unsafeRun();
    assertThat(count.get()).isEqualTo(1);  // now
}

@Test void shouldCaptureExceptionInRunSafe() {
    IOPath<String> failing = Path.io(() -> { throw new RuntimeException("boom"); });
    Try<String> result = failing.runSafe();
    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).isEqualTo("boom");
}
```

### Property-Based Testing (jqwik)

Use `@Property` to verify functor/monad laws. Example: `path.map(Function.identity()).run()` equals `path.run()` (identity law). `Path.left(err).recover(e -> fb).run().isRight()` is always true (recovery law).

---

## 6. Integration Patterns

### Wrapping Exception-Throwing APIs

```java
// As TryPath
public TryPath<Data> fetchData(String id) {
    return Path.tryOf(() -> legacy.fetchData(id));
}
// As typed EitherPath
public EitherPath<ServiceError, Data> fetchDataSafe(String id) {
    return Path.tryOf(() -> legacy.fetchData(id))
        .toEitherPath(ex -> new ServiceError("Fetch failed", ex));
}
```

### Wrapping Optional-Returning APIs

```java
public MaybePath<User> findUser(String id) {
    return Path.maybe(Maybe.fromOptional(modern.findUser(id)));
}
```

### Exposing to Non-Path Consumers

```java
public class ServiceAdapter {
    private final PathBasedService service;

    public Optional<User> findUser(String id) {           // Optional consumer
        return service.findUser(id).run().toOptional();
    }

    public User getUser(String id) throws UserNotFoundException {  // Exception consumer
        Either<UserError, User> result = service.getUser(id).run();
        if (result.isLeft()) throw new UserNotFoundException(result.getLeft().message());
        return result.getRight();
    }
}
```

---

## Common Pitfalls

| Pitfall | Wrong | Right |
|---------|-------|-------|
| Excessive conversion | `.toEitherPath().toMaybePath().toEitherPath()` | Convert once at the boundary |
| Side effects in `map` | `.map(u -> { audit.record(u); return u; })` | `.peek(u -> audit.record(u))` |
| Ignoring the result | `validateAndProcess(req);` (Path discarded) | `validateAndProcess(req).run();` |
| Flattening errors | `.mapError(e -> "An error occurred")` | `.mapError(e -> new DomainError(e.code(), e.msg(), e))` |

---

## Quick Reference

| Pattern | Key Methods |
|---------|-------------|
| Single validation | `Path.right` / `Path.left` |
| Combined validation (fail-fast) | `zipWith` / `zipWith3` |
| Combined validation (accumulating) | `zipWithAccum` / `zipWith3Accum` |
| Repository wrapping | `Path.maybe(...)` + `toEitherPath` |
| Service chaining | `ForPath.from(...)`, `via`, `mapError` |
| Fallback chain | `recoverWith` |
| Resource management | `via` + `ensuring` |
| Deferred pipeline | `via`, `then`, `unsafeRun` |
| Error enrichment | `mapError` |
| Retry | `withRetry` + `RetryPolicy` |
| Legacy wrapping | `Path.tryOf`, `toEitherPath` |
| Optional bridging | `Maybe.fromOptional` |
