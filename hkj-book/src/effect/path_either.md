# EitherPath

`EitherPath<E, A>` wraps `Either<E, A>` for computations with typed errors.
The left side carries failure; the right side carries success. (Right is
right, as the mnemonic goes.)

~~~admonish info title="What You'll Learn"
- Creating EitherPath instances
- Core operations and error handling
- Bifunctor operations
- Extraction patterns
- Railway-aware resilience with the static step combinators (`withRetry`, `withTimeout`, `withCircuitBreaker`, `withBulkhead`)
- When to use (and when not to)
~~~

~~~admonish example title="See Example Code"
- [BasicPathExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/BasicPathExample.java) - Creating and transforming EitherPath values
- [ErrorHandlingExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ErrorHandlingExample.java) - Typed-error workflows with `recover` and `mapError`
- [EitherPathBimapExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/EitherPathBimapExample.java) - Bifunctor operations transforming both sides
- [ServiceLayerExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ServiceLayerExample.java) - Real-world service composition with EitherPath
~~~

---

## Creation

```java
// Success
EitherPath<Error, Integer> success = Path.right(42);

// Failure
EitherPath<Error, Integer> failure = Path.left(new ValidationError("invalid"));

// From existing Either
EitherPath<Error, User> user = Path.either(validateUser(input));
```

---

## Core Operations

```java
EitherPath<String, Integer> number = Path.right(42);

// Transform success
EitherPath<String, String> formatted = number.map(n -> "Value: " + n);

// Chain
EitherPath<String, Integer> doubled = number.via(n ->
    n > 0 ? Path.right(n * 2) : Path.left("Must be positive"));

// Combine independent values
EitherPath<String, String> name = Path.right("Alice");
EitherPath<String, Integer> age = Path.right(25);
EitherPath<String, Person> person = name.zipWith(age, Person::new);
```

---

## Error Handling

```java
EitherPath<String, Config> config = Path.either(loadConfig())
    // Provide fallback value
    .recover(error -> Config.defaults())

    // Transform error type
    .mapError(e -> new ConfigError(e))

    // Recover with another computation
    .recoverWith(error -> Path.either(loadBackupConfig()))

    // Provide alternative path
    .orElse(() -> Path.right(Config.defaults()));
```

---

## Bifunctor Operations

Transform both the error and the success values simultaneously with `bimap`:

```java
EitherPath<String, Integer> original = Path.right(42);

EitherPath<Integer, String> transformed = original.bimap(
    String::length,       // Transform error
    n -> "Value: " + n    // Transform success
);
```

`bimap(errorFn, successFn)` is equivalent to `.mapError(errorFn).map(successFn)`
but expressed in one call. Only the mapper for the side that is present is
invoked: a `Right` leaves the error mapper untouched, and a `Left` leaves the
success mapper untouched.

Use the single-sided variants when only one side needs changing:

```java
// Transform only the error
EitherPath<DomainError, User> mapped = path.mapError(ApiError::toDomain);

// Transform only the success
EitherPath<Error, String> named = path.map(User::name);
```

---

## Extraction

```java
EitherPath<String, Integer> path = Path.right(42);
Either<String, Integer> either = path.run();

// Pattern match
String result = either.fold(
    error -> "Error: " + error,
    value -> "Value: " + value
);

// Direct access (throws if wrong side)
if (either.isRight()) {
    Integer value = either.getRight();
}
```

---

## Resilience (Step Combinators)

`EitherPath` is an *eager* carrier — by the time an instance exists, the computation has already run, so an instance-chained retry would have nothing left to protect. Resilience wraps a **computation**, so on `EitherPath` the `with*` vocabulary is static, taking the step as a `Supplier` — the same combinators the lazy paths chain, applied at the point where the computation still exists:

```java
// Railway-aware retry: thrown exceptions retry per the policy; a Left retries
// only when the predicate selects it. A business Left ("card declined") is a
// value — returned immediately, never retried. Typed exhaustion returns the
// last Left, staying on the typed channel.
EitherPath<OrderError, Reservation> reserved = EitherPath.withRetry(
    () -> reserveInventory(order),
    error -> error instanceof OrderError.SystemError,
    policy);

// Typed time budget: the timeout arrives as Left(onTimeout.get()), not a
// thrown TimeoutException. The losing computation is not interrupted.
EitherPath<OrderError, Receipt> charged = EitherPath.withTimeout(
    () -> chargePayment(order),
    Duration.ofSeconds(10),
    () -> OrderError.SystemError.timeout("payment"));

// Circuit breaker and bulkhead, with rejections on the typed channel. A Left
// never trips the breaker — only thrown exceptions count as failures.
EitherPath<OrderError, Status> status = EitherPath.withCircuitBreaker(
    () -> fetchStatus(orderId), breaker, open -> OrderError.unavailable());

EitherPath<OrderError, Result> queried = EitherPath.withBulkhead(
    () -> runQuery(sql), bulkhead, full -> OrderError.busy());
```

In a pipeline these sit naturally inside `via`:

```java
pipeline.via(order ->
    EitherPath.withRetry(() -> reserveInventory(order), isTransient, policy));
```

`withRetry(step, policy)` (without the predicate) retries thrown exceptions only — the pure railway default. Do not wrap non-idempotent steps (a payment) in retry: the whole supplier is re-invoked. See [Resilience Patterns](../resilience/ch_intro.md) for the full treatment, including the per-carrier availability table.

---

## When to Use

`EitherPath` is right when:
- Errors carry meaningful, typed information
- Different errors need different handling
- You're building validation pipelines (with short-circuit semantics)
- You want to transform errors as they propagate

`EitherPath` is wrong when:
- You need to collect *all* errors → use [ValidationPath](path_validation.md)
- Absence isn't really an error → use [MaybePath](path_maybe.md)

~~~admonish tip title="See Also"
- [Either Monad](../monads/either_monad.md) - Underlying type for EitherPath
- [ValidationPath](path_validation.md) - For accumulating errors
- [VResultPath](path_vresult.md) - The composition of this path with the async half: `VTask<Either<E, A>>` as one railway
- [Resilience Patterns](../resilience/ch_intro.md) - Retry, timeout, circuit breaker, and bulkhead across the Path family
~~~

---

**Previous:** [MaybePath](path_maybe.md)
**Next:** [TryPath](path_try.md)
