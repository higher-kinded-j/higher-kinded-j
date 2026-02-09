# Concurrency: Scope & Resource Journey

~~~admonish info title="What You'll Learn"
- Using `Scope` for structured concurrent computations with different joining strategies
- Choosing between `allSucceed`, `anySucceed`, `firstComplete`, and `accumulating` joiners
- Safe resource management with the `Resource` bracket pattern
- Composing multiple resources with guaranteed cleanup
- Integrating Scope and Resource for concurrent resource-aware operations
~~~

**Duration**: ~30 minutes | **Tutorials**: 2 | **Exercises**: 12

**Prerequisites**: Complete the [VTask Journey](vtask_journey.md) first

## Journey Overview

This journey builds on VTask fundamentals to introduce structured concurrency with `Scope` and safe resource management with `Resource`. You'll learn to coordinate multiple concurrent tasks with different completion strategies and ensure resources are always properly released.

```
┌─────────────────────────────────────────────────────────────────┐
│                Scope & Resource Journey                         │
│                                                                 │
│  Tutorial 1: Structured Concurrency with Scope                  │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐      │
│  │  Scope   │ → │  fork    │ → │ joiner   │ → │  join    │      │
│  │ factory  │   │  tasks   │   │ strategy │   │ results  │      │
│  └──────────┘   └──────────┘   └──────────┘   └──────────┘      │
│                                                                 │
│  Tutorial 2: Resource Management                                │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐      │
│  │ acquire  │ → │   use    │ → │ compose  │ → │ release  │      │
│  │ resource │   │ resource │   │ resources│   │ (always) │      │
│  └──────────┘   └──────────┘   └──────────┘   └──────────┘      │
└─────────────────────────────────────────────────────────────────┘
```

By the end, you'll understand how to build robust concurrent applications with proper task coordination and guaranteed resource cleanup.

---

## Tutorial 1: Structured Concurrency with Scope (~15 minutes)
**File**: `TutorialScope.java` | **Exercises**: 6

Master task coordination with different joining strategies.

### Part 1: Creating Scopes

**What you'll learn**:
- Creating scopes with `Scope.allSucceed()`, `anySucceed()`, `firstComplete()`
- Forking tasks into a scope
- Joining to collect results

**Key insight**: `Scope` is a fluent builder for structured concurrency. Each factory method creates a scope with a specific joining strategy that determines how task results are collected.

```java
// All tasks must succeed - returns List<String>
VTask<List<String>> all = Scope.<String>allSucceed()
    .fork(VTask.of(() -> "result1"))
    .fork(VTask.of(() -> "result2"))
    .join();

// First success wins - returns String
VTask<String> any = Scope.<String>anySucceed()
    .fork(VTask.of(() -> fetchFromServerA()))
    .fork(VTask.of(() -> fetchFromServerB()))
    .join();
```

**Exercise 1**: Create a scope that waits for all tasks to succeed
**Exercise 2**: Create a scope that returns the first successful result

---

### Part 2: Choosing Joiners

**What you'll learn**:
- When to use each joiner strategy
- Understanding cancellation behaviour
- Using `firstComplete` for racing strategies

**Key insight**: The joiner strategy determines both the result type and the cancellation behaviour. `allSucceed` fails fast on any failure; `anySucceed` cancels siblings when one succeeds.

| Joiner | Behaviour | Result Type | Use Case |
|--------|----------|-------------|----------|
| `allSucceed` | Wait for all; fail on first failure | `List<T>` | Parallel fetches that all must complete |
| `anySucceed` | Return first success; cancel others | `T` | Racing redundant requests |
| `firstComplete` | Return first result (success or failure) | `T` | Fast-path with fallback |

**Exercise 3**: Use `firstComplete` to race a fast but risky operation against a slow but safe one
**Exercise 4**: Add a timeout to a scope operation

---

### Part 3: Error Accumulation

**What you'll learn**:
- Using `accumulating` for validation scenarios
- Working with `Validated` results
- Collecting all errors instead of failing fast

**Key insight**: Unlike other joiners that fail fast, `accumulating` runs all tasks to completion and collects all failures. This is perfect for form validation where you want to report all errors at once.

```java
VTask<Validated<List<String>, List<String>>> validation =
    Scope.<String, String>accumulating(Throwable::getMessage)
        .fork(validateUsername())
        .fork(validateEmail())
        .fork(validatePassword())
        .join();

validation.run().fold(
    errors -> showAllErrors(errors),    // List of all error messages
    values -> proceedWithValues(values) // List of all success values
);
```

**Exercise 5**: Create a validation scope that accumulates all errors
**Exercise 6**: Handle the Validated result with fold

---

## Tutorial 2: Resource Management (~15 minutes)
**File**: `TutorialResource.java` | **Exercises**: 6

Master the bracket pattern for safe resource handling.

### Part 1: Creating Resources

**What you'll learn**:
- Creating resources from `AutoCloseable` with `Resource.fromAutoCloseable()`
- Explicit acquire/release with `Resource.make()`
- Pure values with `Resource.pure()`

**Key insight**: `Resource` wraps acquire-use-release into a single abstraction. The resource is acquired lazily when you call `use()`, and release is guaranteed even if the use function throws.

```java
// From AutoCloseable (most common)
Resource<Connection> conn = Resource.fromAutoCloseable(
    () -> dataSource.getConnection()
);

// Explicit acquire and release
Resource<Lock> lock = Resource.make(
    () -> { theLock.lock(); return theLock; },
    Lock::unlock
);

// Pure value (no cleanup needed)
Resource<Config> config = Resource.pure(loadedConfig);
```

**Exercise 1**: Create a Resource from an AutoCloseable
**Exercise 2**: Create a Resource with explicit acquire and release functions

---

### Part 2: Using Resources

**What you'll learn**:
- Running computations with `use()`
- Exception safety guarantees
- Safe result handling with `joinSafe()`

**Key insight**: The `use()` method takes a function from the resource to a `VTask`. No matter what happens in that function - success, failure, or cancellation - the resource is released.

```java
Resource<Connection> conn = Resource.fromAutoCloseable(
    () -> dataSource.getConnection()
);

VTask<List<User>> users = conn.use(c ->
    VTask.of(() -> userDao.findAll(c))
);

// Connection is acquired when run() is called
// Connection is released when the VTask completes (success or failure)
Try<List<User>> result = users.runSafe();
```

**Exercise 3**: Use a resource to execute a computation
**Exercise 4**: Handle a resource computation that might fail

---

### Part 3: Composing Resources

**What you'll learn**:
- Chaining dependent resources with `flatMap`
- Combining independent resources with `and`
- Understanding LIFO release order

**Key insight**: When you compose resources, they are acquired in order and released in reverse order (LIFO). This ensures dependent resources are released before the resources they depend on.

```java
Resource<Connection> conn = Resource.fromAutoCloseable(getConnection);
Resource<Statement> stmt = conn.flatMap(c ->
    Resource.fromAutoCloseable(() -> c.createStatement())
);

// Combine independent resources
Resource<Tuple2<FileReader, FileWriter>> both =
    readerResource.and(writerResource);

// Release order: writer first, then reader
```

**Exercise 5**: Chain dependent resources with flatMap
**Exercise 6**: Combine independent resources and use them together

---

## Running the Tutorials

### Using Gradle
```bash
# Run Scope exercises
./gradlew :hkj-examples:test --tests "*TutorialScope*"

# Run Resource exercises
./gradlew :hkj-examples:test --tests "*TutorialResource*"

# Check your solutions
./gradlew :hkj-examples:test --tests "*TutorialScope*_Solution*"
./gradlew :hkj-examples:test --tests "*TutorialResource*_Solution*"
```

### Using Your IDE
Navigate to `hkj-examples/src/test/java/org/higherkindedj/tutorial/concurrency/` and run the tests.

---

## Common Pitfalls

### 1. Forgetting Scope is Lazy
**Problem**: Assuming forking tasks starts execution immediately.

```java
// WRONG: Nothing is running yet
Scope.<String>allSucceed()
    .fork(VTask.of(() -> expensiveOperation()))
    .fork(VTask.of(() -> anotherOperation()));
// Missing .join() - tasks never execute!

// RIGHT: Call join() to execute
VTask<List<String>> results = Scope.<String>allSucceed()
    .fork(VTask.of(() -> expensiveOperation()))
    .fork(VTask.of(() -> anotherOperation()))
    .join();
results.run(); // Now they execute
```

### 2. Using Wrong Joiner for Validation
**Problem**: Using `allSucceed` when you want all errors.

```java
// WRONG: Fails fast on first error
VTask<List<String>> validation = Scope.<String>allSucceed()
    .fork(validateField1()) // If this fails...
    .fork(validateField2()) // ...this might not even run
    .join();

// RIGHT: Use accumulating for validation
VTask<Validated<List<Error>, List<String>>> validation =
    Scope.<Error, String>accumulating(Error::from)
        .fork(validateField1())
        .fork(validateField2())
        .join();
// All fields validated, all errors collected
```

### 3. Not Using Resource for Cleanup
**Problem**: Manual try-finally for resource management.

```java
// RISKY: Complex error handling, easy to get wrong
Connection conn = null;
try {
    conn = dataSource.getConnection();
    return doWork(conn);
} finally {
    if (conn != null) {
        try { conn.close(); } catch (Exception e) { /* log */ }
    }
}

// BETTER: Resource handles all the complexity
Resource.fromAutoCloseable(() -> dataSource.getConnection())
    .use(conn -> VTask.of(() -> doWork(conn)))
    .run();
```

### 4. Ignoring LIFO Release Order
**Problem**: Assuming resources release in acquisition order.

```java
// Resources acquired: A, then B, then C
Resource<Tuple3<A, B, C>> combined = ra.and(rb, rc);

// Resources released: C, then B, then A (reverse order!)
// This is correct - dependent resources release first
```

---

## Key Concepts Summary

| Concept | Description |
|---------|-------------|
| **Scope** | Fluent builder for structured concurrent computations |
| **Joiner Strategy** | Determines how task results are collected and when cancellation occurs |
| **allSucceed** | Wait for all tasks; fail fast on any failure |
| **anySucceed** | Return first success; cancel remaining tasks |
| **accumulating** | Run all tasks; collect all errors in `Validated` |
| **Resource** | Bracket pattern: acquire-use-release with guaranteed cleanup |
| **LIFO Release** | Composed resources release in reverse acquisition order |

---

## What's Next?

After completing this journey:

1. **Read the Documentation**: Deep dive into Scope and Resource → [Structured Concurrency](../../monads/vtask_scope.md), [Resource Management](../../monads/vtask_resource.md)
2. **Explore Examples**: See real-world patterns → [ScopeExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ScopeExample.java), [VTaskResourceExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/VTaskResourceExample.java)
3. **Combine with VTaskPath**: Use Scope and Resource with the fluent Effect Path API
4. **Build Real Applications**: Transaction management, connection pooling, parallel validation

---

**Related**: [VTask Journey](vtask_journey.md) | [Structured Concurrency](../../monads/vtask_scope.md) | [Resource Management](../../monads/vtask_resource.md) | [VTaskPath](../../effect/path_vtask.md)
