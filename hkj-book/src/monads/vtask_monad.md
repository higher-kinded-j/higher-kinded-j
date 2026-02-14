# The VTask Effect:
## _Virtual Thread-Based Concurrency_

~~~admonish info title="What You'll Learn"
- How VTask enables lightweight concurrent programming with virtual threads
- Building lazy, composable concurrent computations
- Using `run()`, `runSafe()`, and `runAsync()` for task execution
- Error handling and recovery with functional patterns
- Parallel composition using the `Par` combinator utilities
~~~

~~~admonish warning title="Preview API Notice"
VTask uses Java's **structured concurrency APIs** (JEP 505/525), which are currently in **preview** status. These APIs are stable and production-ready, but the underlying Java APIs may see minor changes before final release, expected in a near-future Java version.

Higher-Kinded-J's `Scope`, `ScopeJoiner`, and `Resource` abstractions provide a buffer against such changes; your code uses HKJ's stable API whilst we handle any necessary adaptations to the underlying preview features.
~~~

> *"Sometimes abstraction and encapsulation are at odds with performance -- although not nearly as often as many developers believe -- but it is always a good practice first to make your code right, and then make it fast."*
> -- **Brian Goetz**, *Java Concurrency in Practice*

## The Abstraction Tax

For two decades, Java developers have wrestled with an uncomfortable trade-off in concurrent programming. The clean abstractions that make code maintainable, encapsulating complexity behind simple interfaces and composing small pieces into larger wholes, seemed fundamentally at odds with the realities of thread-based concurrency. Platform threads are expensive: each consumes a megabyte or more of stack memory, and the operating system imposes hard limits on how many can exist simultaneously. This scarcity forced developers to abandon straightforward designs in favour of complex thread pool management, callback pyramids, and reactive streams that obscured business logic beneath infrastructure concerns.

The result was a generation of concurrent code optimised for machines rather than humans. Developers learned to hoard threads jealously, to batch operations artificially, and to transform naturally sequential logic into convoluted state machines. The abstraction tax seemed unavoidable; you could have clean code or performant code, but not both.

> *"Virtual threads are not faster threads -- they are cheaper threads. This means that you can have a lot more of them, and that changes how you structure programs."*
> -- **Brian Goetz**, Java Language Architect at Oracle

## A New Economics of Concurrency

Java 21 introduced virtual threads through Project Loom, and Java 25 refined the model with structured concurrency. Virtual threads fundamentally alter the economics: managed by the JVM rather than the operating system, they consume mere kilobytes rather than megabytes. An application can spawn millions of virtual threads without exhausting resources. Suddenly, the "abstraction tax" evaporates. Developers can write code that is *both* right and fast, using straightforward, blocking-style code that the runtime multiplexes efficiently across a small pool of carrier threads.

But cheaper threads alone do not solve the compositional problem: how do we build complex concurrent programs from smaller, reusable pieces while maintaining testability and referential transparency? This is where `VTask` enters the picture. Rather than executing effects immediately, `VTask` represents computations as *descriptions*: recipes that can be transformed, composed, and combined before execution. This separation of description from execution is the key insight that enables functional programming's approach to effects: we reason about what our program will do, compose smaller computations into larger ones, and defer execution until the boundary of our pure core.

## Purpose

The `VTask<A>` type in Higher-Kinded-J represents a lazy computation that, when executed, runs on a Java virtual thread and produces a value of type `A`. It is the primary effect type for virtual thread-based concurrency, bridging functional programming patterns with Java's modern concurrency primitives.

```
┌─────────────────────────────────────────────────────────────────┐
│                         VTask<A>                                │
│                                                                 │
│   ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌─────────┐   │
│   │ describe │ -> │  compose │ -> │ transform│ -> │ execute │   │
│   │  effect  │    │  effects │    │  results │    │   on    │   │
│   │          │    │          │    │          │    │ virtual │   │
│   │          │    │          │    │          │    │ thread  │   │
│   └──────────┘    └──────────┘    └──────────┘    └─────────┘   │
│                                                                 │
│   Lazy                                     Deferred Execution   │
└─────────────────────────────────────────────────────────────────┘
```

**Key Characteristics:**

* **Laziness:** Effects are not executed upon creation. A `VTask` is a description of *what to do*, not an immediate action.
* **Virtual Threads:** Computations execute on virtual threads, enabling millions of concurrent tasks with minimal memory overhead.
* **Structured Concurrency:** Uses Java 25's `StructuredTaskScope` for proper cancellation and error propagation.
* **Composability:** Operations chain seamlessly using `map`, `flatMap`, and parallel combinators.
* **HKT Integration:** `VTask<A>` directly extends `VTaskKind<A>`, participating in Higher-Kinded-J's type class hierarchy.

## Core Architecture

```
                    ┌─────────────────┐
                    │   VTaskKind<A>  │  (HKT marker interface)
                    │   Kind<W, A>    │
                    └────────┬────────┘
                             │ extends
                             ▼
                    ┌─────────────────┐
                    │    VTask<A>     │  (functional interface)
                    │   execute(): A  │
                    └────────┬────────┘
                             │
           ┌─────────────────┼─────────────────┐
           │                 │                 │
           ▼                 ▼                 ▼
    ┌────────────┐    ┌────────────┐    ┌────────────┐
    │ VTaskMonad │    │VTaskFunctor│    │    Par     │
    │  flatMap   │    │    map     │    │  parallel  │
    │    of      │    │            │    │ combinators│
    │ raiseError │    │            │    │            │
    └────────────┘    └────────────┘    └────────────┘
```

The VTask ecosystem consists of:

1. **`VTask<A>`**: The core functional interface. An `execute()` method describes the computation; execution methods (`run()`, `runSafe()`, `runAsync()`) actually perform it.

2. **`VTaskKind<A>`**: The HKT marker interface enabling `VTask` to work with type classes like `Functor`, `Monad`, and `MonadError`.

3. **`VTaskKindHelper`**: Utility for conversions between `VTask<A>` and `Kind<VTaskKind.Witness, A>`.

4. **`VTaskMonad`**: Implements `MonadError<VTaskKind.Witness, Throwable>`, providing `map`, `flatMap`, `of`, `raiseError`, and `handleErrorWith`.

5. **`Par`**: Static utilities for parallel execution using `StructuredTaskScope`.

## How to Use `VTask<A>`

~~~admonish title="Creating Instances"

VTask provides several factory methods for creating computations:

```java
import org.higherkindedj.hkt.vtask.VTask;

// From a Callable - the primary way to capture effects
VTask<String> fetchData = VTask.of(() -> httpClient.get("https://api.example.com"));

// From a Supplier using delay
VTask<Integer> randomValue = VTask.delay(() -> new Random().nextInt(100));

// Immediate success (the "pure" operation)
VTask<String> pureValue = VTask.succeed("Hello, VTask!");

// Immediate failure
VTask<String> failed = VTask.fail(new RuntimeException("Something went wrong"));

// From a Runnable (returns Unit)
VTask<Unit> logAction = VTask.exec(() -> System.out.println("Logging..."));

// Marking blocking operations (documentation hint)
VTask<byte[]> readFile = VTask.blocking(() -> Files.readAllBytes(path));
```

**Important:** Creating a `VTask` does nothing. The computation is only executed when you call `run()`, `runSafe()`, or `runAsync()`.
~~~

~~~admonish title="Executing Tasks"

VTask offers three execution methods:

```java
VTask<Integer> computation = VTask.of(() -> 42);

// 1. run() - throws on failure
//    Checked exceptions are wrapped in VTaskExecutionException;
//    RuntimeException and Error are thrown directly.
try {
    Integer result = computation.run();
    System.out.println("Result: " + result);
} catch (VTaskExecutionException e) {
    // Checked exception wrapped — original available via e.getCause()
    System.err.println("Wrapped: " + e.getCause().getMessage());
} catch (RuntimeException e) {
    System.err.println("Failed: " + e.getMessage());
}

// 2. runSafe() - returns Try<A> for safe error handling
Try<Integer> tryResult = computation.runSafe();
tryResult.fold(
    value -> System.out.println("Success: " + value),
    error -> System.err.println("Failure: " + error.getMessage())
);

// 3. runAsync() - returns CompletableFuture<A> for async composition
CompletableFuture<Integer> future = computation.runAsync();
future.thenAccept(value -> System.out.println("Async result: " + value));
```

The `runSafe()` method is preferred for most use cases as it captures failures in a `Try`, maintaining functional error handling.
~~~

~~~admonish title="Transforming Values (map)"

Use `map` to transform the result without changing the effect structure:

```java
VTask<String> greeting = VTask.succeed("world");
VTask<String> message = greeting.map(name -> "Hello, " + name + "!");
// When run: "Hello, world!"

VTask<Integer> length = message.map(String::length);
// When run: 13
```

If the mapping function throws, the VTask fails with that exception.
~~~

~~~admonish title="Chaining Operations (flatMap)"

Use `flatMap` to sequence dependent computations:

```java
VTask<User> fetchUser = VTask.of(() -> userService.getById(userId));
VTask<Profile> fetchProfile = fetchUser.flatMap(user ->
    VTask.of(() -> profileService.getForUser(user)));
VTask<String> displayName = fetchProfile.flatMap(profile ->
    VTask.succeed(profile.getDisplayName()));

// All three operations execute in sequence when run
String name = displayName.run();
```

The `via` method is an alias for `flatMap`:

```java
VTask<String> result = fetchUser
    .via(user -> VTask.of(() -> profileService.getForUser(user)))
    .via(profile -> VTask.succeed(profile.getDisplayName()));
```
~~~

---

### Error Handling

~~~admonish title="recover and recoverWith"

Handle failures gracefully with recovery functions:

```java
VTask<Config> loadConfig = VTask.of(() -> configService.load());

// recover: transform failure to success value
VTask<Config> withDefault = loadConfig.recover(error -> Config.defaultConfig());

// recoverWith: transform failure to another VTask
VTask<Config> withFallback = loadConfig.recoverWith(error ->
    VTask.of(() -> loadFallbackConfig()));

// mapError: transform the exception type
VTask<Config> withBetterError = loadConfig.mapError(error ->
    new ConfigException("Failed to load configuration", error));
```
~~~

~~~admonish title="Using VTaskMonad for Generic Error Handling"

For HKT-compatible error handling:

```java
VTaskMonad monad = VTaskMonad.INSTANCE;

Kind<VTaskKind.Witness, String> taskKind = VTASK.widen(VTask.fail(new IOException("Network error")));

Kind<VTaskKind.Witness, String> recovered = monad.handleErrorWith(
    taskKind,
    error -> monad.of("Default value")
);

String result = VTASK.narrow(recovered).run(); // "Default value"
```
~~~

---

### Timeouts

~~~admonish title="Adding Timeouts"

Fail fast when operations take too long:

```java
VTask<Data> slowOperation = VTask.of(() -> {
    Thread.sleep(5000);
    return fetchData();
});

VTask<Data> withTimeout = slowOperation.timeout(Duration.ofSeconds(2));

// Option 1: Use runSafe() for functional error handling (preferred)
Try<Data> result = withTimeout.runSafe();
result.fold(
    data -> System.out.println("Got data: " + data),
    error -> System.err.println("Operation timed out: " + error.getMessage())
);

// Option 2: Use run() — TimeoutException is wrapped in VTaskExecutionException
try {
    withTimeout.run();
} catch (VTaskExecutionException e) {
    if (e.getCause() instanceof TimeoutException) {
        System.err.println("Operation timed out!");
    }
}
```
~~~

---

## Parallel Composition with Par

The `Par` utility class provides combinators for executing VTasks concurrently:

```
┌───────────────────────────────────────────────────────────┐
│                    Par Combinators                        │
│                                                           │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │   zip    │  │   map2   │  │   all    │  │   race   │   │
│  │  (A, B)  │  │ (A,B)->R │  │ [A]->A[] │  │ first A  │   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘   │
│                                                           │
│  All use StructuredTaskScope for proper lifecycle         │
└───────────────────────────────────────────────────────────┘
```

~~~admonish example title="Parallel Execution Examples"

```java
import org.higherkindedj.hkt.vtask.Par;

// zip: combine two tasks into a tuple
VTask<String> userTask = VTask.of(() -> fetchUser(id));
VTask<String> profileTask = VTask.of(() -> fetchProfile(id));

VTask<Par.Tuple2<String, String>> both = Par.zip(userTask, profileTask);
Par.Tuple2<String, String> result = both.run();
// Both execute in parallel!

// map2: combine two tasks with a function
VTask<UserProfile> combined = Par.map2(
    userTask,
    profileTask,
    (user, profile) -> new UserProfile(user, profile)
);

// all: execute a list of tasks in parallel
List<VTask<Integer>> tasks = List.of(
    VTask.of(() -> compute(1)),
    VTask.of(() -> compute(2)),
    VTask.of(() -> compute(3))
);
VTask<List<Integer>> allResults = Par.all(tasks);

// race: return first successful result
VTask<String> fastest = Par.race(List.of(
    VTask.of(() -> fetchFromServer1()),
    VTask.of(() -> fetchFromServer2()),
    VTask.of(() -> fetchFromServer3())
));

// traverse: apply function to list, execute results in parallel
List<Integer> ids = List.of(1, 2, 3, 4, 5);
VTask<List<User>> users = Par.traverse(ids, id -> VTask.of(() -> fetchUser(id)));
```
~~~

---

## Advanced Topics

For more complex concurrent patterns, VTask provides additional APIs documented in their own pages:

~~~admonish tip title="Structured Concurrency"
**[Scope and ScopeJoiner](vtask_scope.md)** - Fluent API for coordinating concurrent tasks with configurable joining strategies:
- `allSucceed` - Wait for all tasks to complete successfully
- `anySucceed` - Return first success, cancel others
- `firstComplete` - Return first result regardless of outcome
- `accumulating` - Collect all errors using `Validated`
~~~

~~~admonish tip title="Resource Management"
**[Resource](vtask_resource.md)** - Safe resource management using the bracket pattern:
- Guaranteed cleanup on success, failure, or cancellation
- Composable with `flatMap` and `and`
- Integrates seamlessly with Scope for concurrent resource management
~~~

---

## VTask vs IO

`VTask` and `IO` serve similar purposes but with different execution models:

| Aspect | VTask | IO |
|--------|-------|-----|
| **Thread Model** | Virtual threads | Caller's thread |
| **Parallelism** | Built-in via `Par`, `Scope` | Manual composition |
| **Structured Concurrency** | Yes, with `Scope` and `Resource` | No |
| **Async Support** | `runAsync()` returns `CompletableFuture` | No built-in async |
| **Error Type** | `Throwable` | `Throwable` |

Choose `VTask` when:
- You need lightweight concurrency at scale
- You want structured concurrency with proper cancellation
- Parallel execution with `Par` or `Scope` is valuable

Choose `IO` when:
- Single-threaded execution is sufficient
- You want explicit control over which thread runs the computation
- You're building a library that shouldn't impose thread choices

---

~~~admonish info title="Key Takeaways"
* **Laziness:** VTask describes computations without executing them; nothing runs until you call `run()`, `runSafe()`, or `runAsync()`
* **Virtual Threads:** Execution happens on lightweight JVM-managed threads, enabling millions of concurrent tasks
* **Composition:** Use `map` for transformations, `flatMap` for dependent chains, and `Par` combinators for parallel execution
* **Error Handling:** Prefer `runSafe()` to capture failures in `Try`; use `recover` and `recoverWith` for graceful fallbacks
* **Structured Concurrency:** Use `StructuredTaskScope` via Par combinators for proper task lifecycle management
~~~

~~~admonish info title="Hands-On Learning"
Practice VTask fundamentals in [Tutorial: VTask](../tutorials/concurrency/vtask_journey.md) (8 exercises, ~25 minutes).
~~~

~~~admonish tip title="See Also"
- [Structured Concurrency](vtask_scope.md) - Scope and ScopeJoiner for advanced task coordination
- [Resource Management](vtask_resource.md) - Bracket pattern for safe resource handling
- [IO](io_monad.md) - The platform thread-based effect type for single-threaded scenarios
- [Monad](../functional/monad.md) - Understanding the `flatMap` abstraction
- [MonadError](../functional/monad_error.md) - Error handling patterns with `raiseError` and `handleErrorWith`
~~~

~~~admonish abstract title="Further Reading"
- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444) - The official OpenJDK specification for virtual threads in Java 21+
- [Virtual Threads, Structured Concurrency, and Scoped Values](https://www.amazon.com/Virtual-Threads-Structured-Concurrency-Scoped/dp/B0D5TTFSJ1) - Ron Veen & David Vlijmincx (Apress, 2024) - Comprehensive guide to Project Loom features
~~~

---

**Previous:** [Validated](validated_monad.md)
**Next:** [Structured Concurrency](vtask_scope.md)
