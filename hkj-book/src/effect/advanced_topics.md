# Advanced Effect Topics

> *"The Cryptonomicon was like an idea that had been exploded into a thousand
> fragments and scattered across the world... You had to pick up all the
> pieces and fit them together just right for any of it to make sense."*
>
> -- Neal Stephenson, *Cryptonomicon*

Stephenson's observation about scattered fragments applies equally to
advanced effect patterns. Stack safety, resource management, parallel
execution, resilience: these are the fragments that, assembled correctly,
transform fragile code into production-ready systems. Each piece makes
sense alone; together they unlock capabilities that would otherwise require
external frameworks or unsafe compromises.

This chapter explores advanced capabilities that build on the core Path types:
stack-safe recursion, DSL building, resource management, parallelism, and
resilience. These aren't academic exercises. They're the patterns that emerge
when simple composition meets real-world demands.

~~~admonish info title="What You'll Learn"
- `TrampolinePath` for stack-safe recursive computations that never overflow
- `FreePath` and `FreeApPath` for building interpretable domain-specific languages
- Resource management with `bracket`, `withResource`, and `guarantee`
- Parallel execution with `parZipWith`, `parSequence`, and `race`
- Resilience patterns with `RetryPolicy` and configurable backoff strategies
~~~

~~~admonish example title="See Example Code"
- [TrampolinePathExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/TrampolinePathExample.java) - Stack-safe recursion patterns
- [FreePathExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/FreePathExample.java) - DSL building and interpretation
- [ResourceManagementExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ResourceManagementExample.java) - Safe resource handling
- [ParallelExecutionExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ParallelExecutionExample.java) - Concurrent operations
- [ResilienceExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ResilienceExample.java) - Retry and backoff patterns
~~~

---

## Stack-Safe Recursion with TrampolinePath

> *"Randy had learned that the science of secrecy was really about recursion.
> Even after you'd encrypted something, you had to worry about whether the
> encrypted thing was encrypted safely."*
>
> -- Neal Stephenson, *Cryptonomicon*

Recursion is elegant, until it overflows your stack. Java's call stack is
finite, typically 512KB to 1MB depending on configuration. A recursive
algorithm that works beautifully for small inputs becomes a `StackOverflowError`
waiting to happen once the depth exceeds a few thousand calls.

`TrampolinePath` eliminates this limitation through *trampolining*: converting
recursive calls into a loop that consumes constant stack space regardless of
depth. The recursive structure of your algorithm remains; the stack overflow
doesn't.

### The Problem

Consider calculating factorial recursively:

```java
// This will overflow around n = 10,000
long factorial(long n) {
    if (n <= 1) return 1;
    return n * factorial(n - 1);  // Stack frame per call
}
```

Each recursive call adds a stack frame. For large `n`, you exhaust the stack
before reaching the base case.

### The Solution

`TrampolinePath` separates describing the recursion from executing it:

```java
TrampolinePath<BigInteger> factorial(BigInteger n, BigInteger acc) {
    if (n.compareTo(BigInteger.ONE) <= 0) {
        return TrampolinePath.done(acc);    // Base case: return immediately
    }
    return TrampolinePath.defer(() ->        // Recursive case: describe next step
        factorial(n.subtract(BigInteger.ONE), n.multiply(acc)));
}

// Safe for any input size
BigInteger result = factorial(BigInteger.valueOf(100000), BigInteger.ONE).run();
```

`TrampolinePath.done(value)` signals completion. `TrampolinePath.defer(supplier)`
describes the next step without immediately executing it. When you call `run()`,
the trampoline bounces through the deferred steps in a loop, never growing the
call stack.

### How It Works

The trampoline maintains a simple loop:

1. Check if the current step is `done` → return the value
2. If it's `defer` → evaluate the supplier to get the next step
3. Repeat

This converts stack depth into iteration count. Memory usage stays constant
regardless of recursion depth.

### Mutual Recursion

Trampolining handles mutual recursion (functions that call each other) with the
same elegance:

```java
TrampolinePath<Boolean> isEven(int n) {
    if (n == 0) return TrampolinePath.done(true);
    return TrampolinePath.defer(() -> isOdd(n - 1));
}

TrampolinePath<Boolean> isOdd(int n) {
    if (n == 0) return TrampolinePath.done(false);
    return TrampolinePath.defer(() -> isEven(n - 1));
}

// Works for any depth
boolean result = isEven(1_000_000).run();  // true
```

Without trampolining, `isEven(1_000_000)` would overflow after about 10,000
calls. With trampolining, it completes in milliseconds.

### Fibonacci with Accumulator

The classic fibonacci benefits from trampolining when using accumulator style:

```java
TrampolinePath<BigInteger> fibonacci(int n) {
    return fibHelper(n, BigInteger.ZERO, BigInteger.ONE);
}

TrampolinePath<BigInteger> fibHelper(int n, BigInteger a, BigInteger b) {
    if (n <= 0) {
        return TrampolinePath.done(a);
    }
    return TrampolinePath.defer(() -> fibHelper(n - 1, b, a.add(b)));
}

// fib(10000) completes without stack overflow
BigInteger result = fibonacci(10000).run();
```

### When to Use TrampolinePath

| Use Case | TrampolinePath? |
|----------|-----------------|
| Deeply recursive algorithms (factorial, fibonacci) | Yes |
| Tree traversal of arbitrary depth | Yes |
| Mutual recursion patterns | Yes |
| Interpreter/evaluator implementations | Yes |
| Shallow recursion (guaranteed < 1000 depth) | Optional |
| Non-recursive code | No |

### Conversion and Integration

`TrampolinePath` integrates with other Path types:

```java
TrampolinePath<Integer> computation = /* ... */;

// Convert to IOPath for deferred execution
IOPath<Integer> io = computation.toIOPath();

// Convert to LazyPath for memoised evaluation
LazyPath<Integer> lazy = computation.toLazyPath();
```

---

## Building DSLs with Free Structures

> *"The key to the whole thing was that Turing had taken the seemingly useless
> ability to write numbers down and combined it with a few simple rules. From
> this, everything else followed."*
>
> -- Neal Stephenson, *Cryptonomicon*

Sometimes you want to describe a computation without immediately executing it.
You might want to:

- Test the same program against mock and real interpreters
- Optimise the program before running it
- Log or serialise what the program *would* do
- Run the same description in different contexts

Free structures (`FreePath` and `FreeApPath`) let you build domain-specific
languages (DSLs) where the program is *data* that can be inspected, transformed,
and interpreted later.

### FreePath: The Free Monad

`FreePath<F, A>` represents a computation built from a functor `F` that can be
interpreted into any monad. It supports the full `Chainable` interface: you can
use `via` to sequence operations where later steps depend on earlier results.

#### Defining a DSL

First, define your DSL operations as a sealed interface:

```java
// A simple Console DSL
sealed interface ConsoleOp<A> {
    record PrintLine<A>(String message, A next) implements ConsoleOp<A> {}
    record ReadLine<A>(Function<String, A> cont) implements ConsoleOp<A> {}
}
```

#### Building Programs

Lift operations into `FreePath` and compose them:

```java
FreePath<ConsoleOp.Witness, Unit> print(String msg) {
    return FreePath.liftF(new PrintLine<>(msg, Unit.INSTANCE), consoleFunctor);
}

FreePath<ConsoleOp.Witness, String> readLine() {
    return FreePath.liftF(new ReadLine<>(s -> s), consoleFunctor);
}

// Build a program
FreePath<ConsoleOp.Witness, String> greet =
    print("What's your name?")
        .then(() -> readLine())
        .via(name -> print("Hello, " + name).map(_ -> name));
```

The program `greet` doesn't *do* anything yet. It's a data structure describing
what should happen.

#### Interpretation

Interpret the program by providing a natural transformation to a target monad:

```java
// Real interpreter: performs actual I/O
NaturalTransformation<ConsoleOp.Witness, IOKind.Witness> realInterpreter =
    new NaturalTransformation<>() {
        @Override
        public <A> Kind<IOKind.Witness, A> apply(Kind<ConsoleOp.Witness, A> fa) {
            ConsoleOp<A> op = ConsoleOp.narrow(fa);
            return switch (op) {
                case PrintLine(var msg, var next) ->
                    IO.delay(() -> { System.out.println(msg); return next; });
                case ReadLine(var cont) ->
                    IO.delay(() -> cont.apply(scanner.nextLine()));
            };
        }
    };

// Execute with real I/O
GenericPath<IOKind.Witness, String> result = greet.foldMap(realInterpreter, ioMonad);
String name = result.run().unsafeRun();
```

#### Testing with Mock Interpreters

The same program can be tested without real I/O:

```java
// Test interpreter with canned responses
NaturalTransformation<ConsoleOp.Witness, StateKind.Witness> testInterpreter =
    /* returns canned values, records outputs */;

// Same program, different execution
GenericPath<StateKind.Witness, String> testResult =
    greet.foldMap(testInterpreter, stateMonad);
```

### FreeApPath: The Free Applicative

`FreeApPath<F, A>` is more restricted: it implements `Combinable` but **not**
`Chainable`. You can use `zipWith` to combine independent computations, but
you cannot use `via` to make one computation depend on another's result.

Why would you want less power? Because the restriction enables *static analysis*.
Since no step depends on previous results, you can inspect the entire structure
before running anything.

#### Validation Example

```java
// Validation operations
sealed interface ValidationOp<A> {
    record ValidateField<A>(String field, Predicate<String> check, A onSuccess)
        implements ValidationOp<A> {}
}

// Build validation program
FreeApPath<ValidationOp.Witness, User> validateUser =
    FreeApPath.liftF(new ValidateField<>("name", s -> !s.isBlank(), "name"), valFunctor)
        .zipWith3(
            FreeApPath.liftF(new ValidateField<>("email", s -> s.contains("@"), "email"), valFunctor),
            FreeApPath.liftF(new ValidateField<>("age", s -> Integer.parseInt(s) > 0, "age"), valFunctor),
            (name, email, age) -> new User(name, email, Integer.parseInt(age))
        );
```

All three validations are independent; they can run in parallel, and all errors
can be collected rather than stopping at the first failure.

### FreePath vs FreeApPath

| Feature | FreePath | FreeApPath |
|---------|----------|------------|
| Sequencing (`via`) | Yes | No |
| Combining (`zipWith`) | Yes | Yes |
| Static analysis | No (depends on runtime values) | Yes (structure fixed) |
| Parallel-friendly | No | Yes |
| Use case | Sequential DSLs | Validation, queries, parallel DSLs |

### When to Use Free Structures

| Scenario | Recommendation |
|----------|----------------|
| Testing with mock interpreters | FreePath or FreeApPath |
| Multiple interpretation strategies | FreePath or FreeApPath |
| Sequential operations with dependencies | FreePath |
| Independent operations, all errors wanted | FreeApPath |
| Static analysis or optimisation | FreeApPath |
| Simple, one-off computation | Use concrete types directly |

---

## Resource Management

> *"He had learned to think of the system as a living thing, with resources
> that had to be carefully husbanded."*
>
> -- Lionel Davidson, *Kolymsky Heights*

Davidson's protagonist survived the Siberian wilderness by meticulous resource
management. Software faces analogous challenges: database connections, file
handles, network sockets. Acquire them, use them, release them, and make
absolutely certain the release happens even when something goes wrong.

### The bracket Pattern

`bracket` is the fundamental resource pattern: acquire, use, release. The release
*always* runs, regardless of whether the use succeeds or fails:

```java
IOPath<String> readFile = IOPath.bracket(
    () -> new FileInputStream("data.txt"),      // acquire
    stream -> new String(stream.readAllBytes()), // use
    stream -> stream.close()                     // release (always runs)
);
```

If `readAllBytes()` throws, the stream still closes. The exception propagates
after cleanup completes.

#### bracketIO for Effectful Use

When the use phase itself returns an `IOPath`:

```java
IOPath<List<String>> processFile = IOPath.bracketIO(
    () -> Files.newBufferedReader(path),
    reader -> IOPath.delay(() -> reader.lines().toList()),
    reader -> { try { reader.close(); } catch (Exception e) { /* log */ } }
);
```

### withResource for AutoCloseable

When your resource implements `AutoCloseable`, `withResource` provides a
cleaner syntax:

```java
IOPath<List<String>> lines = IOPath.withResource(
    () -> Files.newBufferedReader(path),
    reader -> reader.lines().toList()
);
```

The reader is automatically closed after use, with proper exception handling.

#### withResourceIO Variant

```java
IOPath<Config> config = IOPath.withResourceIO(
    () -> new FileInputStream("config.json"),
    stream -> IOPath.delay(() -> parseConfig(stream))
);
```

### guarantee for Cleanup Actions

Sometimes you don't need acquire/release semantics; you just need to ensure
something runs after a computation completes:

```java
IOPath<Result> computation = fetchData()
    .guarantee(() -> log.info("Fetch completed"));
```

The guarantee runs whether `fetchData()` succeeds or fails.

#### guaranteeIO for Effectful Cleanup

```java
IOPath<Result> withCleanup = process()
    .guaranteeIO(() -> IOPath.delay(() -> {
        cleanup();
        return Unit.INSTANCE;
    }));
```

### Nested Resources

Resources often nest. `bracket` composes cleanly:

```java
IOPath<Result> nested = IOPath.bracketIO(
    () -> acquireConnection(),
    connection -> IOPath.bracketIO(
        () -> connection.prepareStatement(sql),
        statement -> IOPath.bracket(
            () -> statement.executeQuery(),
            resultSet -> processResults(resultSet),
            resultSet -> resultSet.close()
        ),
        statement -> statement.close()
    ),
    connection -> connection.close()
);
```

Resources are released in reverse order: result set, then statement, then
connection. If any step fails, all acquired resources are still released.

### Resource Pattern Summary

| Pattern | Use Case |
|---------|----------|
| `bracket` | Acquire/use/release with custom cleanup |
| `bracketIO` | Same, but use returns IOPath |
| `withResource` | AutoCloseable resources |
| `withResourceIO` | AutoCloseable with IOPath use |
| `guarantee` | Ensure cleanup runs after computation |
| `guaranteeIO` | Effectful cleanup |

---

## Parallel Execution

> *"In cryptography, you sometimes have to try many keys simultaneously.
> The first one that works wins."*
>
> -- Neal Stephenson, *Cryptonomicon*

Some computations are independent. Fetching user data and fetching preferences
don't depend on each other; why wait for one to finish before starting the other?

### parZipWith: Binary Parallelism

`parZipWith` runs two `IOPath` computations concurrently and combines their
results:

```java
IOPath<String> fetchUser = IOPath.delay(() -> {
    Thread.sleep(100);
    return "User123";
});

IOPath<String> fetchPrefs = IOPath.delay(() -> {
    Thread.sleep(100);
    return "DarkMode";
});

// Sequential: ~200ms (100ms + 100ms)
IOPath<String> sequential = fetchUser.zipWith(fetchPrefs, (u, p) -> u + "/" + p);

// Parallel: ~100ms (max of both)
IOPath<String> parallel = fetchUser.parZipWith(fetchPrefs, (u, p) -> u + "/" + p);
```

The operations are the same; the execution strategy differs.

### parZip3 and parZip4

For three or four independent computations, use `PathOps` utilities:

```java
IOPath<Dashboard> dashboard = PathOps.parZip3(
    fetchMetrics(),
    fetchAlerts(),
    fetchUsers(),
    Dashboard::new
);

IOPath<Report> report = PathOps.parZip4(
    fetchSales(),
    fetchInventory(),
    fetchCustomers(),
    fetchTrends(),
    Report::new
);
```

All operations start immediately and run concurrently. The result is available
when all complete.

### parSequenceIO: List Parallelism

When you have a dynamic number of independent operations:

```java
List<IOPath<User>> fetches = userIds.stream()
    .map(id -> IOPath.delay(() -> userService.fetch(id)))
    .toList();

// Sequential: N * fetchTime
IOPath<List<User>> sequential = PathOps.sequenceIO(fetches);

// Parallel: ~1 * fetchTime (with enough threads)
IOPath<List<User>> parallel = PathOps.parSequenceIO(fetches);
```

### parSequenceFuture for CompletableFuturePath

The same pattern works with `CompletableFuturePath`:

```java
List<CompletableFuturePath<Data>> futures = /* ... */;
CompletableFuturePath<List<Data>> all = PathOps.parSequenceFuture(futures);
```

### race: First to Finish

Sometimes you want whichever completes first:

```java
IOPath<Config> primary = IOPath.delay(() -> fetchFromPrimary());
IOPath<Config> backup = IOPath.delay(() -> fetchFromBackup());

// Returns whichever config arrives first
IOPath<Config> fastest = primary.race(backup);
```

~~~admonish warning title="Race Caveats"
The losing computation is *not* cancelled (Java limitation). It continues
running in the background. Use `race` for idempotent operations where
completing the loser is acceptable.
~~~

### raceIO for Multiple Competitors

```java
List<IOPath<Response>> sources = List.of(
    fetchFromRegionA(),
    fetchFromRegionB(),
    fetchFromRegionC()
);

IOPath<Response> fastest = PathOps.raceIO(sources);
```

### Sequential vs Parallel: The Decision

| Scenario | Use |
|----------|-----|
| B needs A's result | `via` (sequential) |
| A and B independent, need both | `parZipWith` |
| 3-4 independent operations | `parZip3`, `parZip4` |
| List of independent operations | `parSequenceIO` |
| Want fastest of alternatives | `race`, `raceIO` |

The wrong choice doesn't break correctness, just performance. When in doubt,
prefer sequential; parallelise when profiling shows it matters.

---

## Resilience Patterns

> *"The protocol specified exponential backoff: wait one second, try again;
> wait two seconds, try again; wait four seconds..."*
>
> -- Neal Stephenson, *Cryptonomicon*

Networks fail. Services timeout. Databases hiccup. Resilient code doesn't
assume success; it plans for failure and recovers gracefully.

### RetryPolicy

`RetryPolicy` encapsulates retry strategy: how many attempts, how long between
them, which failures to retry:

```java
// Fixed delay: 100ms between each of 3 attempts
RetryPolicy fixed = RetryPolicy.fixed(3, Duration.ofMillis(100));

// Exponential backoff: 1s, 2s, 4s, 8s...
RetryPolicy exponential = RetryPolicy.exponentialBackoff(5, Duration.ofSeconds(1));

// With jitter to prevent thundering herd
RetryPolicy jittered = RetryPolicy.exponentialBackoffWithJitter(5, Duration.ofSeconds(1));
```

### Understanding Backoff Strategies

| Strategy | Delays | Use Case |
|----------|--------|----------|
| Fixed | 100ms, 100ms, 100ms | Known recovery time |
| Exponential | 1s, 2s, 4s, 8s | Unknown recovery time |
| Exponential + Jitter | ~1s, ~2s, ~4s (randomised) | Multiple clients retrying |

Jitter adds randomisation to prevent the "thundering herd" problem where many
clients retry at exactly the same moment, overwhelming a recovering service.

### Configuring Retry Behaviour

Policies are immutable but configurable through builder-style methods:

```java
RetryPolicy policy = RetryPolicy.exponentialBackoff(5, Duration.ofMillis(100))
    .withMaxDelay(Duration.ofSeconds(30))   // Cap maximum wait
    .retryOn(IOException.class);             // Only retry I/O errors
```

#### Custom Retry Predicates

```java
RetryPolicy selective = RetryPolicy.fixed(3, Duration.ofMillis(100))
    .retryIf(ex ->
        ex instanceof IOException ||
        ex instanceof TimeoutException ||
        (ex instanceof HttpException http && http.statusCode() >= 500));
```

### Using withRetry

`IOPath` and `CompletableFuturePath` integrate directly with retry policies:

```java
IOPath<Response> resilient = IOPath.delay(() -> httpClient.get(url))
    .withRetry(RetryPolicy.exponentialBackoff(3, Duration.ofSeconds(1)));
```

#### Convenience Method

For simple cases with default exponential backoff:

```java
IOPath<Response> simple = IOPath.delay(() -> httpClient.get(url))
    .retry(3);  // 3 attempts with default backoff
```

### Handling Exhausted Retries

When all attempts fail, `RetryExhaustedException` is thrown with the last
failure as its cause:

```java
try {
    resilient.unsafeRun();
} catch (RetryExhaustedException e) {
    log.error("All {} retries failed", e.getMessage());
    Throwable lastFailure = e.getCause();
    return fallbackValue;
}
```

### Combining Resilience Patterns

Retry composes with other Path operations:

```java
IOPath<Data> robust = IOPath.delay(() -> primarySource.fetch())
    .withRetry(RetryPolicy.exponentialBackoff(3, Duration.ofSeconds(1)))
    .handleErrorWith(e -> {
        log.warn("Primary exhausted, trying backup", e);
        return IOPath.delay(() -> backupSource.fetch())
            .withRetry(RetryPolicy.fixed(2, Duration.ofMillis(100)));
    })
    .recover(e -> {
        log.error("All sources failed", e);
        return Data.empty();
    });
```

### Retry with Resource Management

```java
IOPath<Result> resilientWithResource = IOPath.withResourceIO(
    () -> acquireConnection(),
    conn -> IOPath.delay(() -> conn.execute(query))
        .withRetry(RetryPolicy.fixed(3, Duration.ofMillis(50)))
);
```

The connection is acquired once; the query is retried within that connection.

### Retry Pattern Quick Reference

| Pattern | Code |
|---------|------|
| Fixed delay | `RetryPolicy.fixed(3, Duration.ofMillis(100))` |
| Exponential backoff | `RetryPolicy.exponentialBackoff(5, Duration.ofSeconds(1))` |
| With jitter | `RetryPolicy.exponentialBackoffWithJitter(5, Duration.ofSeconds(1))` |
| Cap max delay | `.withMaxDelay(Duration.ofSeconds(30))` |
| Retry specific errors | `.retryOn(IOException.class)` |
| Custom predicate | `.retryIf(ex -> ...)` |
| Apply to IOPath | `path.withRetry(policy)` |
| Simple retry | `path.retry(3)` |

---

## Summary

| Capability | Type/Method | Use Case |
|------------|-------------|----------|
| Stack-safe recursion | `TrampolinePath` | Deep recursion, mutual recursion |
| Monadic DSLs | `FreePath` | Interpretable sequential programs |
| Applicative DSLs | `FreeApPath` | Static analysis, parallel-friendly programs |
| Resource safety | `bracket`, `withResource` | Files, connections, cleanup |
| Cleanup guarantee | `guarantee` | Ensure finalizers run |
| Parallel binary | `parZipWith` | Two independent computations |
| Parallel n-ary | `parZip3`, `parZip4` | 3-4 independent computations |
| Parallel list | `parSequenceIO` | Dynamic number of computations |
| First-to-finish | `race`, `raceIO` | Redundant sources, timeouts |
| Retry with backoff | `RetryPolicy`, `withRetry` | Transient failures |

These patterns are the fragments Stephenson described: individually useful,
collectively transformative. Combined with the core Path types from earlier
chapters, they provide a comprehensive toolkit for building robust, composable
systems that handle the full spectrum of real-world complexity.

~~~admonish tip title="See Also"
- [Trampoline Monad](../monads/trampoline_monad.md) - Underlying type for TrampolinePath
- [Free Monad](../monads/free_monad.md) - Underlying type for FreePath
- [Free Applicative](../monads/free_applicative.md) - Underlying type for FreeApPath
- [IO Monad](../monads/io_monad.md) - Resource and parallel operations
~~~

---

**Previous:** [MutableContext](effect_contexts_mutable.md)
**Next:** [Production Readiness](production_readiness.md)
