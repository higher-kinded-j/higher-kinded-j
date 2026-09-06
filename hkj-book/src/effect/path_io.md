# IOPath

`IOPath<A>` wraps `IO<A>` for **deferred** side-effectful computations.
Unlike other Path types, nothing happens until you explicitly run it.

> *"Buy the ticket, take the ride... and if it occasionally gets a little
> heavier than what you had in mind, well... maybe chalk it up to forced
> consciousness expansion."*
>
> — Hunter S. Thompson, *Fear and Loathing in Las Vegas*

Thompson's advice applies here. When you call `unsafeRun()`, you've bought
the ticket. The effects will happen. There's no going back. Until that moment,
an `IOPath` is just a description: a plan you haven't committed to yet.

~~~admonish info title="What You'll Learn"
- Creating IOPath instances
- Deferred execution model
- Error handling patterns
- Resource management (bracket, withResource)
- Parallel execution
- When to use (and when not to)
~~~

---

## Creation

<!-- verify -->
```java
// Pure value (no effects)
IOPath<Integer> pure = Path.ioPure(42);

// Deferred effect. `Path.io` takes a `Supplier`, which cannot throw a checked
// exception, so a throwing call is wrapped where it is made.
IOPath<String> readFile = Path.io(() -> {
    try {
        return Files.readString(Paths.get("data.txt"));
    } catch (IOException e) {
        throw new UncheckedIOException(e);
    }
});

// From existing IO
IOPath<Connection> conn = Path.ioPath(databaseIO);
```

~~~admonish tip title="A computation that throws"
`Path.vtask` takes a `Callable`, so a throwing computation goes in as it stands:
`Path.vtask(() -> Files.readString(path))`. See [VTaskPath](path_vtask.md).
~~~

---

## Core Operations (All Deferred)

<!-- verify -->
```java
IOPath<String> content = Path.io(() -> fetchFromApi(url));

// Transform (deferred)
IOPath<Data> data = content.map(this::parse);

// Chain (deferred)
IOPath<Result> result = content.via(c -> Path.io(() -> process(c)));

// Combine (deferred)
IOPath<String> header = Path.io(() -> readHeader());
IOPath<String> body = Path.io(() -> readBody());
IOPath<String> combined = header.zipWith(body, (h, b) -> h + "\n" + b);

// Sequence (discarding first result)
IOPath<Unit> setup = Path.ioRunnable(() -> log("Starting..."));
IOPath<Data> withSetup = setup.then(() -> Path.io(() -> loadData()));
```

---

## Execution: Buying the Ticket

<!-- verify -->
```java
IOPath<String> io = Path.io(() -> fetchData());

// Execute (may throw)
String value = io.unsafeRun();

// Execute safely (captures exceptions)
Try<String> captured = io.runSafe();

// Convert to TryPath (executes immediately)
TryPath<String> tryPath = io.toTryPath();
```

The naming is deliberate. `unsafeRun` warns you: referential transparency
ends here. Side effects are about to happen. Call it at the boundaries of
your system (in your `main` method, your HTTP handler, your message consumer),
not scattered throughout your business logic.

---

## Error Handling

<!-- verify -->
```java
IOPath<Config> config = Path.io(() -> loadConfig())
    // Handle any exception
    .handleError(ex -> Config.defaults())

    // Handle with another effect
    .handleErrorWith(ex -> Path.io(() -> loadBackupConfig()))

    // Ensure cleanup runs regardless of outcome
    .guarantee(() -> releaseResources());
```

---

## Resource Management

### bracket

The `bracket` pattern ensures resources are properly released:

<!-- verify -->
```java
IOPath<Report> report = IOPath.bracket(
    () -> pool.borrow(),           // acquire
    conn -> conn.query(sql),       // use
    conn -> pool.release(conn)     // release (always runs)
);
```

### withResource

For `AutoCloseable` resources:

<!-- verify -->
```java
IOPath<String> content = IOPath.withResource(
    () -> new Scanner(source),
    scanner -> scanner.useDelimiter("\\A").next()
);
// scanner.close() is called automatically
```

All three arguments to `bracket` are plain `Supplier`, `Function` and `Consumer`, so a
resource whose acquisition, use or release throws a checked exception needs the same wrap
the creation section shows. `bracketIO` and `withResourceIO` take a use function that
returns an `IOPath`, for when the body is itself an effect.

---

## Parallel Execution

<!-- verify -->
```java
IOPath<String> fetchA = Path.io(() -> callServiceA());
IOPath<String> fetchB = Path.io(() -> callServiceB());

// Run in parallel, combine results
IOPath<String> combined = fetchA.parZipWith(fetchB, (a, b) -> a + b);

// Race: first to complete wins
IOPath<String> fastest = fetchA.race(fetchB);

// Run many in parallel
List<IOPath<String>> ios = List.of(io1, io2, io3);
IOPath<List<String>> all = PathOps.parSequenceIO(ios);
```

---

## Resilience

The full `with*` vocabulary chains directly on the path (retry, time budget, circuit breaker, and bulkhead), all lazy until `unsafeRun()`:

<!-- verify -->
```java
IOPath<String> resilient = Path.io(() -> callFlakyService())
    .retry(5, Duration.ofMillis(100))                        // exponential backoff convenience
    .withRetry(RetryPolicy.fixed(3, Duration.ofMillis(50)))  // policy-driven retry
    .withTimeout(Duration.ofSeconds(2))                      // bound the elapsed time
    .withCircuitBreaker(serviceBreaker)                      // shared breaker per dependency
    .withBulkhead(serviceBulkhead);                          // cap concurrent callers
```

`IOPath` has no typed error channel, so failures surface on the failure channel: `withTimeout` fails with a `CompletionException` wrapping the `TimeoutException` (the same surfacing as `CompletableFuturePath.withTimeout`), an open circuit throws `CircuitOpenException`, and a full bulkhead throws `BulkheadFullException`. The timed-out computation is not interrupted; it keeps running unobserved. To land a timeout or rejection as a typed `Left` instead, use the static `EitherPath` combinators (`EitherPath.withTimeout(step, duration, onTimeout)` and friends).

See [Resilience Patterns](../resilience/ch_intro.md) for the per-carrier availability table, and [Patterns and Recipes](patterns.md) for more recipes.

---

## Lazy Evaluation in Action

<!-- verify -->
```java
IOPath<String> effect = Path.io(() -> {
    System.out.println("Side effect!");  // Not printed yet
    return "result";
});

// Still nothing
IOPath<Integer> transformed = effect.map(String::length);

// NOW it runs
Integer length = transformed.unsafeRun();  // Prints "Side effect!"
```

---

## When to Use

`IOPath` is right when:
- You're performing side effects (file I/O, network, database)
- You want lazy evaluation: describe now, execute later
- You want referential transparency throughout your core logic
- You need to compose complex effect pipelines before committing

`IOPath` is wrong when:
- You want immediate execution → use [TryPath](path_try.md)
- There are no side effects → use [EitherPath](path_either.md) or [MaybePath](path_maybe.md)

~~~admonish example title="Benchmarks"
IOPath has dedicated JMH benchmarks measuring wrapper overhead on top of raw IO. Key expectations:

- **IOPath vs raw IO:** 5-15% overhead (wrapper allocation plus delegation cost)
- Wrapper overhead > 30% is a warning sign suggesting unnecessary allocation
- Comparison benchmarks against VTaskPath are available for choosing between effect types

```bash
./gradlew :hkj-benchmarks:jmh --includes=".*IOPathBenchmark.*"
./gradlew :hkj-benchmarks:jmh --includes=".*VTaskPathVsIOPathBenchmark.*"
```
See [Benchmarks & Performance](../benchmarks.md) for full details, comparison benchmarks, and how to interpret results.
~~~

~~~admonish tip title="See Also"
- [IO Monad](../monads/io_monad.md) - Underlying type for IOPath
- [VTaskPath](path_vtask.md) - Virtual thread-based alternative for concurrent workloads
- [Composition Patterns](composition.md) - More composition techniques
- [Patterns and Recipes](patterns.md) - Resilience and resource patterns
- [Advanced Topics](advanced_topics.md) - Deep dive on IOPath features
~~~

---

**Previous:** [EitherOrBothPath](path_either_or_both.md)
**Next:** [VTaskPath](path_vtask.md)
