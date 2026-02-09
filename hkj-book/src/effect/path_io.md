# IOPath

`IOPath<A>` wraps `IO<A>` for **deferred** side-effectful computations.
Unlike other Path types, nothing happens until you explicitly run it.

> *"Buy the ticket, take the ride... and if it occasionally gets a little
> heavier than what you had in mind, well... maybe chalk it up to forced
> consciousness expansion."*
>
> -- Hunter S. Thompson, *Fear and Loathing in Las Vegas*

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

```java
// Pure value (no effects)
IOPath<Integer> pure = Path.ioPure(42);

// Deferred effect
IOPath<String> readFile = Path.io(() -> Files.readString(Paths.get("data.txt")));

// From existing IO
IOPath<Connection> conn = Path.ioPath(databaseIO);
```

---

## Core Operations (All Deferred)

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

```java
IOPath<String> io = Path.io(() -> fetchData());

// Execute (may throw)
String result = io.unsafeRun();

// Execute safely (captures exceptions)
Try<String> result = io.runSafe();

// Convert to TryPath (executes immediately)
TryPath<String> tryPath = io.toTryPath();
```

The naming is deliberate. `unsafeRun` warns you: referential transparency
ends here. Side effects are about to happen. Call it at the boundaries of
your system (in your `main` method, your HTTP handler, your message consumer),
not scattered throughout your business logic.

---

## Error Handling

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

```java
IOPath<String> content = IOPath.bracket(
    () -> Files.newInputStream(path),      // acquire
    in -> new String(in.readAllBytes()),   // use
    in -> in.close()                       // release (always runs)
);
```

### withResource

For `AutoCloseable` resources:

```java
IOPath<String> content = IOPath.withResource(
    () -> Files.newBufferedReader(path),
    reader -> reader.lines().collect(Collectors.joining("\n"))
);
// reader.close() is called automatically
```

---

## Parallel Execution

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

## Retry with Resilience

```java
IOPath<String> resilient = Path.io(() -> callFlakyService())
    .retry(5, Duration.ofMillis(100))  // exponential backoff
    .withRetry(RetryPolicy.fixed(3, Duration.ofMillis(50)));
```

See [Patterns and Recipes](patterns.md) for more resilience patterns.

---

## Lazy Evaluation in Action

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

~~~admonish tip title="See Also"
- [IO Monad](../monads/io_monad.md) - Underlying type for IOPath
- [VTaskPath](path_vtask.md) - Virtual thread-based alternative for concurrent workloads
- [Composition Patterns](composition.md) - More composition techniques
- [Patterns and Recipes](patterns.md) - Resilience and resource patterns
- [Advanced Topics](advanced_topics.md) - Deep dive on IOPath features
~~~

---

**Previous:** [TryPath](path_try.md)
**Next:** [ValidationPath](path_validation.md)
