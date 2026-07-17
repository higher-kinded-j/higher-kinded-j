# Glossary: Concurrency & Resilience

~~~admonish info title="What This Page Covers"
- Virtual-thread concurrency, structured scopes, and resilience.
- Part of the [Glossary](../glossary.md); see it for the other categories.
~~~

## Bracket Pattern

**Definition:** A resource management pattern that guarantees cleanup by structuring code as three phases: acquire, use, and release. The release phase executes regardless of whether the use phase succeeds, fails, or is cancelled. Also known as RAII (Resource Acquisition Is Initialization) in C++.

**Structure:**
```
┌─────────────────────────────────────────────────────────────┐
│                    Bracket Pattern                          │
│                                                             │
│  acquire ─────► use ─────► release                          │
│     │            │            ↑                             │
│     │            │            │ (always executed)           │
│     │            └── success ─┘                             │
│     │            └── failure ─┘                             │
│     │            └── cancel ──┘                             │
│     │                                                       │
│  (may fail)   (may fail)   (guaranteed to run)              │
└─────────────────────────────────────────────────────────────┘
```

**Example:**
```java
// Without bracket pattern: manual cleanup is error-prone
Connection conn = null;
try {
    conn = dataSource.getConnection();
    return processData(conn);
} finally {
    if (conn != null) {
        try { conn.close(); } catch (Exception e) { /* swallowed */ }
    }
}

// With bracket pattern: cleanup is guaranteed
Resource<Connection> connResource = Resource.fromAutoCloseable(
    () -> dataSource.getConnection()
);

VTask<Result> result = connResource.use(conn ->
    VTask.of(() -> processData(conn))
);
// Connection is ALWAYS closed, even on exception or cancellation
```

**Benefits:**
- Eliminates resource leaks
- Cleanup code is colocated with acquisition
- Exception-safe by construction
- Composable: multiple resources release in reverse order (LIFO)

**Related:** [Resource](#resource-vtask), [RAII Pattern](https://en.wikipedia.org/wiki/Resource_acquisition_is_initialization)

---

## Bulkhead

**Definition:** A resilience pattern that caps how many callers may use a shared resource at once, so one slow dependency cannot exhaust all available capacity and sink otherwise-healthy work (named for a ship's watertight compartments). Implemented as semaphore-based concurrency limiting with configurable permits, fairness, and timeout behaviour, and applied as `withBulkhead` in the resilience vocabulary.

**Related:** [Bulkhead](../resilience/bulkhead.md), [Resilience Combinators](#resilience-combinators), [Circuit Breaker](#circuit-breaker)

---

**Previous:** [More Functional Thinking](../reading.md)
**Next:** [Release History](../release-history.md)

---

## Circuit Breaker

**Definition:** A resilience pattern that remembers recent failures and stops calling a dependency that is clearly down, so a system does not waste effort on doomed requests whilst a service recovers. It is a state machine across three states: **closed** (calls flow), **open** (calls are rejected immediately once failures cross a threshold), and **half-open** (after a timeout, a single probe is allowed; success closes the breaker, failure re-opens it). Applied as `withCircuitBreaker` in the resilience vocabulary.

**Related:** [Circuit Breaker](../resilience/circuit_breaker.md), [Resilience Combinators](#resilience-combinators), [Bulkhead](#bulkhead)

---

## Resilience Combinators

**Definition:** A single `with*` vocabulary for wrapping a computation in a resilience policy: `withRetry`, `withTimeout`, `withCircuitBreaker`, and `withBulkhead`, available across the Path family. On the lazy carriers (`IOPath`, `VTaskPath`, [VResultPath](effect-paths.md#vresultpath)) they chain as instance methods; on the eager `EitherPath` the same combinators are static, taking the step as a `Supplier`, because resilience wraps a *computation* and an eager path has already run.

**Railway-aware:** on the typed-error carriers a business `Left` is a value, never retried and never counted as a circuit-breaker failure. Typed overloads opt selected transient errors into retry and land timeouts or rejections as `Left`s rather than thrown exceptions, so resilience never swallows a domain outcome.

**Related:** [Resilience Patterns](../resilience/ch_intro.md), [Retry](../resilience/retry.md), [Circuit Breaker](#circuit-breaker), [Bulkhead](#bulkhead), [Path](effect-paths.md#path), [VResultPath](effect-paths.md#vresultpath)

---

## Resource (VTask)

**Definition:** A type in Higher-Kinded-J that implements the bracket pattern for safe resource management in VTask computations. Resources encapsulate acquisition and release logic, guaranteeing cleanup even when computations fail or are cancelled.

**Factory Methods:**
| Method | Use Case |
|--------|----------|
| `fromAutoCloseable(supplier)` | Wrap Java `AutoCloseable` types |
| `make(acquire, release)` | Custom acquisition and release logic |
| `pure(value)` | Wrap a value with no cleanup needed |

**Composition Methods:**
| Method | Description |
|--------|-------------|
| `map(f)` | Transform the resource value |
| `flatMap(f)` | Chain dependent resources |
| `and(other)` | Combine two resources (releases in LIFO order) |
| `and(r2, r3)` | Combine three resources |
| `withFinalizer(action)` | Add cleanup that runs after release |

**Example:**
```java
import org.higherkindedj.hkt.vtask.Resource;

// Create from AutoCloseable
Resource<Connection> conn = Resource.fromAutoCloseable(
    () -> dataSource.getConnection()
);

// Use the resource
VTask<List<User>> users = conn.use(c ->
    VTask.of(() -> userDao.findAll(c))
);

// Compose multiple resources
Resource<PreparedStatement> stmt = conn.flatMap(c ->
    Resource.make(
        () -> c.prepareStatement(sql),
        PreparedStatement::close
    )
);

// Combine independent resources (LIFO release order)
Resource<Tuple2<Connection, FileChannel>> combined =
    conn.and(fileChannel);
// fileChannel released first, then conn

// Add finalizer for logging/metrics
Resource<Lock> lock = Resource.make(
    () -> { rwLock.lock(); return rwLock; },
    Lock::unlock
).withFinalizer(() -> metrics.recordLockRelease());
```

**Related:** [Bracket Pattern](#bracket-pattern), [Scope](#scope), [Resource Documentation](../monads/vtask_resource.md)

---

## Scope

**Definition:** A fluent builder for structured concurrent computations in Higher-Kinded-J. Scope wraps Java's `StructuredTaskScope` with functional result handling, providing factory methods for common joining strategies.

**Factory Methods:**
| Method | Behaviour |
|--------|-----------|
| `allSucceed()` | Wait for all tasks; fail on first failure |
| `anySucceed()` | Return first success; cancel others |
| `firstComplete()` | Return first result (success or failure) |
| `accumulating(mapper)` | Collect all errors using `Validated` |
| `withJoiner(joiner)` | Use custom `ScopeJoiner` |

**Example:**
```java
import org.higherkindedj.hkt.vtask.Scope;

// All-succeed: parallel fetches that must all complete
VTask<List<UserData>> userData = Scope.<UserData>allSucceed()
    .fork(VTask.of(() -> fetchPermissions(userId)))
    .fork(VTask.of(() -> fetchProfile(userId)))
    .timeout(Duration.ofSeconds(5))
    .join();

// Any-succeed: racing redundant requests
VTask<Package> download = Scope.<Package>anySucceed()
    .fork(VTask.of(() -> fetchFrom(mirror1)))
    .fork(VTask.of(() -> fetchFrom(mirror2)))
    .join();

// Safe result handling
VTask<Try<List<String>>> safe = Scope.<String>allSucceed()
    .fork(task1())
    .fork(task2())
    .joinSafe();  // Returns Try instead of throwing
```

**Related:** [ScopeJoiner](#scopejoiner), [Structured Concurrency](#structured-concurrency), [Scope Documentation](../monads/vtask_scope.md)

---

## ScopeJoiner

**Definition:** A functional wrapper around Java 25's `StructuredTaskScope.Joiner` interface. ScopeJoiner provides HKJ-friendly result accessors via `Either` and `Validated`, bridging Java's preview APIs with functional error handling.

**Available Joiners:**
| Joiner | Result Type | Behaviour |
|--------|-------------|-----------|
| `allSucceed()` | `List<T>` | Collect all successful results |
| `anySucceed()` | `T` | First successful result |
| `firstComplete()` | `T` | First result regardless of outcome |
| `accumulating(mapper)` | `Validated<List<E>, List<T>>` | Collect all errors and successes |

**Example:**
```java
import org.higherkindedj.hkt.vtask.ScopeJoiner;

// Create joiners directly
ScopeJoiner<String, List<String>> allSucceed = ScopeJoiner.allSucceed();
ScopeJoiner<String, Validated<List<Error>, List<String>>> accum =
    ScopeJoiner.accumulating(Error::from);

// Use with Scope
VTask<List<String>> result = Scope.withJoiner(allSucceed)
    .fork(task1())
    .fork(task2())
    .join();

// Access underlying Java 25 Joiner for interop
StructuredTaskScope.Joiner<String, List<String>> java25Joiner =
    allSucceed.joiner();

// Get result wrapped in Either
Either<Throwable, List<String>> eitherResult = allSucceed.resultEither();
```

**Related:** [Scope](#scope), [Validated](data-effects.md#validated), [ScopeJoiner Documentation](../monads/vtask_scope.md)

---

## Structured Concurrency

**Definition:** A programming paradigm where concurrent tasks are organised hierarchically, with child tasks bounded by the lifetime of their parent scope. When a scope completes (successfully or via cancellation), all its child tasks are guaranteed to have completed or been cancelled.

**Key Principles:**
1. **Hierarchy:** Tasks form a tree structure; children cannot outlive parents
2. **Cancellation propagation:** Cancelling a scope cancels all its subtasks
3. **Error propagation:** Failures in subtasks propagate to the parent scope
4. **Clean shutdown:** Resources are released in reverse order of acquisition

**Example:**
```java
// All three fetches are bounded by this scope
VTask<List<String>> results = Scope.<String>allSucceed()
    .fork(VTask.of(() -> fetchUser(id)))
    .fork(VTask.of(() -> fetchProfile(id)))
    .fork(VTask.of(() -> fetchPreferences(id)))
    .timeout(Duration.ofSeconds(5))
    .join();

// If any task fails or times out:
// - Other tasks are cancelled
// - Resources are cleaned up
// - Error propagates to caller
```

**Contrast with Unstructured Concurrency:**
```java
// Unstructured: tasks can outlive their creator
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
Future<A> futureA = executor.submit(taskA);
Future<B> futureB = executor.submit(taskB);
// If we return early, tasks may still be running!

// Structured: tasks are bounded by scope
Scope.<Result>allSucceed()
    .fork(taskA)
    .fork(taskB)
    .join();
// Guaranteed: both tasks complete before we continue
```

**Preview API Notice:** Structured concurrency APIs in Java (JEP 505/525) are currently in preview. The `Scope` and `ScopeJoiner` abstractions in Higher-Kinded-J provide a buffer against potential API changes whilst the feature stabilises, expected in a near-future Java release.

**Related:** [Scope](#scope), [ScopeJoiner](#scopejoiner), [Virtual Thread](#virtual-thread)

---

## Virtual Thread

**Definition:** A lightweight thread managed by the JVM rather than the operating system. Introduced in Java 21 (Project Loom), virtual threads consume only kilobytes of memory compared to megabytes for platform threads, enabling millions of concurrent tasks without exhausting system resources.

**Key Characteristics:**
- Managed by JVM, not OS
- Extremely lightweight (kilobytes vs megabytes)
- Blocking operations automatically yield the carrier thread
- No need for reactive/async programming patterns
- Write simple blocking code that scales

**Example:**
```java
// VTask executes on virtual threads
VTask<String> task = VTask.of(() -> {
    // This blocking call doesn't waste a platform thread
    return httpClient.get("https://api.example.com/data");
});

// Spawn millions of concurrent tasks
List<VTask<Result>> tasks = ids.stream()
    .map(id -> VTask.of(() -> processId(id)))
    .toList();
```

**Why It Matters:** Virtual threads eliminate the traditional trade-off between simple blocking code and scalable concurrency. You can write straightforward sequential code whilst the JVM handles efficient multiplexing across a small pool of carrier threads.

**Related:** [VTask](../monads/vtask_monad.md), [Structured Concurrency](#structured-concurrency)

