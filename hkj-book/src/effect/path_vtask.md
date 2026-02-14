# VTaskPath

`VTaskPath<A>` wraps `VTask<A>` for **virtual thread-based concurrency**.
It brings the lightweight threading of Project Loom to the Effect Path API,
letting you write simple blocking code that scales to millions of concurrent
operations.

> *"The perfect is the enemy of the good."*
>
> -- Voltaire, *Dictionnaire philosophique*

Java's reactive libraries pursued a kind of perfection: maximum throughput, minimal thread usage, non-blocking from end to end. The result was powerful but demanding; developers needed to master publishers, subscribers, backpressure, and schedulers before writing their first concurrent operation. The perfect abstraction for performance became the enemy of good code that anyone could read and maintain.

Virtual threads offer a different bargain. Rather than perfecting the abstraction, they changed the underlying economics. When threads cost almost nothing, simple blocking code performs well enough for most applications. VTaskPath embraces this pragmatism: it provides a fluent, composable API for concurrent operations without requiring you to abandon familiar patterns. Good code that works is better than perfect code that mystifies.

~~~admonish info title="What You'll Learn"
- Creating VTaskPath instances via the Path factory
- Composing concurrent operations with familiar `map` and `via` patterns
- Error handling and recovery strategies
- Timeout management for long-running operations
- Parallel execution with the `Par` utilities
- Advanced structured concurrency with `Scope` and `Resource`
- When to choose VTaskPath over IOPath
~~~

~~~admonish title="Hands On Practice"
[TutorialVTaskPath.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/concurrency/TutorialVTaskPath.java)
~~~

~~~admonish example title="See Example Code"
[VTaskPathExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/VTaskPathExample.java)
~~~

---

## Creation

```java
// From a computation (the primary pattern)
VTaskPath<String> fetchData = Path.vtask(() -> httpClient.get(url));

// Pure value (no computation)
VTaskPath<Integer> pure = Path.vtaskPure(42);

// Immediate failure
VTaskPath<String> failed = Path.vtaskFail(new IOException("Network error"));

// From a Runnable (returns Unit)
VTaskPath<Unit> logAction = Path.vtaskExec(() -> logger.info("Starting..."));

// From an existing VTask
VTaskPath<Config> config = Path.vtaskPath(existingVTask);
```

Unlike `IOPath`, which defers execution until you call `unsafeRun()`, a
`VTaskPath` also defers execution. Nothing happens until you explicitly
run the task. The difference is in *how* it runs: on a virtual thread,
not the caller's thread.

---

## Execution Model

```
┌─────────────────────────────────────────────────────────────────┐
│                      VTaskPath<A>                               │
│                                                                 │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌─────────────┐   │
│  │ describe │ → │  compose │ → │ transform│ → │   execute   │   │
│  │  effect  │   │  effects │   │  results │   │ on virtual  │   │
│  │          │   │          │   │          │   │   thread    │   │
│  └──────────┘   └──────────┘   └──────────┘   └─────────────┘   │
│                                                                 │
│  Nothing runs until you call run(), runSafe(), or runAsync()    │
└─────────────────────────────────────────────────────────────────┘
```

Virtual threads are managed by the JVM, not the operating system. Each
consumes only kilobytes of memory (versus megabytes for platform threads),
so you can spawn millions without exhausting resources.

---

## Core Operations

```java
VTaskPath<String> greeting = Path.vtaskPure("Hello");

// Transform with map
VTaskPath<Integer> length = greeting.map(String::length);

// Chain with via
VTaskPath<String> result = greeting
    .map(String::toUpperCase)
    .via(s -> Path.vtask(() -> enrichWithTimestamp(s)))
    .map(s -> s + "!");

// Sequence, discarding first result
VTaskPath<Unit> setup = Path.vtaskExec(() -> initResources());
VTaskPath<Data> withSetup = setup.then(() -> Path.vtask(() -> loadData()));

// Debug with peek
VTaskPath<Integer> debugged = Path.vtaskPure(42)
    .peek(v -> System.out.println("Before: " + v))
    .map(v -> v * 2)
    .peek(v -> System.out.println("After: " + v));
```

---

## Running Tasks

VTaskPath provides three execution methods:

```java
VTaskPath<Integer> task = Path.vtask(() -> compute());

// 1. unsafeRun() - Blocks, may throw
//    Checked exceptions are wrapped in VTaskExecutionException;
//    RuntimeException and Error are thrown directly.
try {
    Integer result = task.unsafeRun();
} catch (VTaskExecutionException e) {
    // Checked exception wrapped — original available via e.getCause()
    handleError(e.getCause());
} catch (RuntimeException e) {
    handleError(e);
}

// 2. runSafe() - Returns Try<A> for functional error handling
Try<Integer> tryResult = task.runSafe();
tryResult.fold(
    value -> System.out.println("Success: " + value),
    error -> System.err.println("Failure: " + error.getMessage())
);

// 3. runAsync() - Returns CompletableFuture<A> for async composition
CompletableFuture<Integer> future = task.runAsync();
future.thenAccept(value -> System.out.println("Async result: " + value));
```

~~~admonish note title="Execution Guidance"
Prefer `runSafe()` for most use cases. It captures failures in a `Try`,
maintaining functional error handling throughout your codebase. Use `run()`
at system boundaries where you need to interact with exception-based APIs.
~~~

---

## Error Handling

```java
VTaskPath<Config> loadConfig = Path.vtask(() -> configService.load());

// handleError: Transform failure to success value
VTaskPath<Config> withDefault = loadConfig
    .handleError(error -> Config.defaults());

// handleErrorWith: Transform failure to another VTaskPath
VTaskPath<Config> withFallback = loadConfig
    .handleErrorWith(error -> Path.vtask(() -> loadFallbackConfig()));
```

### Fallback Chains

Build resilient services with cascading fallbacks:

```java
VTaskPath<Config> resilientConfig =
    Path.vtask(() -> loadFromPrimarySource())
        .handleErrorWith(e -> Path.vtask(() -> loadFromSecondarySource()))
        .handleErrorWith(e -> Path.vtask(() -> loadFromCache()))
        .handleError(e -> Config.defaults());
```

---

## Timeouts

Prevent runaway operations with timeouts:

```java
VTaskPath<Data> slowOperation = Path.vtask(() -> {
    Thread.sleep(5000);
    return fetchData();
});

VTaskPath<Data> withTimeout = slowOperation.timeout(Duration.ofSeconds(2));

Try<Data> result = withTimeout.runSafe();
// result.isFailure() == true (TimeoutException)
```

---

## Parallel Execution with Par

The `Par` utility provides combinators for running VTasks concurrently:

```
┌───────────────────────────────────────────────────────────────┐
│                       Par Combinators                         │
│                                                               │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐    │
│  │   zip    │   │   map2   │   │   all    │   │   race   │    │
│  │  (A, B)  │   │ (A,B)→R  │   │ [A]→[A]  │   │ first A  │    │
│  └──────────┘   └──────────┘   └──────────┘   └──────────┘    │
│                                                               │
│  All use StructuredTaskScope for proper lifecycle management  │
└───────────────────────────────────────────────────────────────┘
```

```java
import org.higherkindedj.hkt.vtask.Par;

// zip: Combine two tasks into a tuple
VTask<String> userTask = VTask.of(() -> fetchUser(id));
VTask<String> profileTask = VTask.of(() -> fetchProfile(id));
VTask<Par.Tuple2<String, String>> both = Par.zip(userTask, profileTask);
// Both execute in parallel

// map2: Combine two tasks with a function
VTask<UserProfile> combined = Par.map2(
    userTask,
    profileTask,
    (user, profile) -> new UserProfile(user, profile)
);

// all: Execute a list of tasks in parallel
List<VTask<Integer>> tasks = List.of(
    VTask.of(() -> compute(1)),
    VTask.of(() -> compute(2)),
    VTask.of(() -> compute(3))
);
VTask<List<Integer>> allResults = Par.all(tasks);

// race: Return first successful result
VTask<String> fastest = Par.race(List.of(
    VTask.of(() -> fetchFromServerA()),
    VTask.of(() -> fetchFromServerB())
));
```

~~~admonish tip title="Structured Concurrency"
Par combinators use Java's `StructuredTaskScope` internally. If any task
fails, sibling tasks are cancelled automatically. This prevents resource
leaks and ensures clean shutdown semantics.
~~~

---

## Scope and Resource

For advanced structured concurrency patterns, use `Scope` and `Resource` directly with VTask:

```java
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.Resource;

// Scope: fluent builder for structured concurrency
VTask<List<String>> results = Scope.<String>allSucceed()
    .fork(VTask.of(() -> fetchUserData(id)))
    .fork(VTask.of(() -> fetchUserProfile(id)))
    .timeout(Duration.ofSeconds(5))
    .join();

// Error accumulation with Validated
VTask<Validated<List<Error>, List<String>>> validation =
    Scope.<String>accumulating(Error::from)
        .fork(validateField1())
        .fork(validateField2())
        .join();

// Resource: bracket pattern for safe resource management
Resource<Connection> conn = Resource.fromAutoCloseable(
    () -> dataSource.getConnection()
);

VTask<List<User>> users = conn.use(c ->
    VTask.of(() -> userDao.findAll(c))
);
```

~~~admonish tip title="See Also"
For comprehensive documentation on Scope, ScopeJoiner, and Resource, see:
- [Structured Concurrency](../monads/vtask_scope.md) - Scope and ScopeJoiner
- [Resource Management](../monads/vtask_resource.md) - Bracket pattern
~~~

---

## Converting Between Path Types

```java
// VTaskPath to TryPath (executes immediately)
VTaskPath<String> vtask = Path.vtask(() -> fetchData());
TryPath<String> tryResult = vtask.toTryPath();

// VTaskPath to IOPath (preserves laziness)
IOPath<String> io = vtask.toIOPath();

// From underlying VTask
VTask<String> underlying = vtask.run();
VTaskPath<String> backToPath = Path.vtaskPath(VTask.succeed("restored"));
```

---

## VTaskPath vs IOPath

| Aspect | VTaskPath | IOPath |
|--------|-----------|--------|
| **Thread Model** | Virtual threads | Caller's thread |
| **Parallelism** | Built-in via `Par`, `Scope` | Manual composition |
| **`zipWith` Behaviour** | Parallel (uses `Par.map2`) | Sequential |
| **Structured Concurrency** | Yes, with `Scope` and `Resource` | No |
| **Async Support** | `runAsync()` returns `CompletableFuture` | No built-in async |
| **Resource Usage** | Kilobytes per task | N/A (single-threaded) |

**Choose VTaskPath when:**
- You need lightweight concurrency at scale
- You want structured concurrency with proper cancellation
- Simple blocking code is preferable to reactive complexity

**Choose IOPath when:**
- Single-threaded execution is sufficient
- You want explicit control over which thread runs the computation
- You're building a library that shouldn't impose thread choices

---

## Real-World Example

```java
// Parallel service aggregation with timeout and fallback
VTaskPath<Dashboard> loadDashboard(UserId userId) {
    VTask<User> userTask = VTask.of(() -> userService.get(userId));
    VTask<List<Order>> ordersTask = VTask.of(() -> orderService.recent(userId));
    VTask<Analytics> analyticsTask = VTask.of(() -> analyticsService.get(userId));

    return Path.vtaskPath(
        Par.map3(
            userTask,
            ordersTask,
            analyticsTask,
            Dashboard::new
        )
    )
    .timeout(Duration.ofSeconds(5))
    .handleError(e -> Dashboard.empty());
}

// Usage
Try<Dashboard> dashboard = loadDashboard(userId).runSafe();
```

---

~~~admonish info title="Key Takeaways"
* **Virtual Threads:** VTaskPath executes on lightweight JVM-managed threads, enabling millions of concurrent tasks
* **Laziness:** Nothing runs until you call `run()`, `runSafe()`, or `runAsync()`
* **Composition:** Use `map` for transformations, `via` for dependent chains, and `Par` combinators for parallel execution
* **Scope:** Use `Scope` for flexible structured concurrency with `allSucceed`, `anySucceed`, `firstComplete`, or `accumulating` joiners
* **Resource:** Use `Resource` for bracket-pattern resource management with guaranteed cleanup
* **Error Handling:** Prefer `runSafe()` to capture failures in `Try`; use `handleError` and `handleErrorWith` for graceful fallbacks
* **Pragmatism:** Write simple blocking code that composes naturally; let virtual threads handle the scalability
~~~

~~~admonish info title="Hands-On Learning"
Practice VTaskPath composition in [TutorialVTaskPath.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/concurrency/TutorialVTaskPath.java) (8 exercises, ~20 minutes).
~~~

~~~admonish tip title="See Also"
- [VTask Monad](../monads/vtask_monad.md) - Underlying type with full API details
- [Structured Concurrency](../monads/vtask_scope.md) - Scope and ScopeJoiner for task coordination
- [Resource Management](../monads/vtask_resource.md) - Bracket pattern for safe resource handling
- [IOPath](path_io.md) - Platform thread-based effect path for single-threaded scenarios
- [Composition Patterns](composition.md) - More composition techniques applicable to all Path types
- [Patterns and Recipes](patterns.md) - Resilience and resource patterns
~~~

---

**Previous:** [FreeApPath](path_freeap.md)
**Next:** [Composition Patterns](composition.md)
