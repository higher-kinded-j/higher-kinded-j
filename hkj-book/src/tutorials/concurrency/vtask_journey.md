# Concurrency: VTask Journey

~~~admonish info title="What You'll Learn"
- Creating and composing lazy concurrent computations with VTask
- Executing tasks on virtual threads with proper error handling
- Using Par combinators for parallel execution
- Understanding structured concurrency principles
- Using the fluent VTaskPath API for effect-based workflows
- Working with VTaskContext for dependency injection
~~~

**Duration**: ~45 minutes | **Tutorials**: 2 | **Exercises**: 16

**Requirements**: Java 25+ (virtual threads and structured concurrency)

## Journey Overview

This journey introduces `VTask` and `VTaskPath`, Higher-Kinded-J's types for virtual thread-based concurrency. You'll learn to describe computations lazily, compose them functionally, and execute them on lightweight virtual threads.

```
┌─────────────────────────────────────────────────────────────────┐
│                    VTask Journey                                │
│                                                                 │
│  Tutorial 1: VTask Fundamentals                                 │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐      │
│  │ VTask.of │ → │ map/flat │ → │ Par.zip  │ → │ runSafe  │      │
│  │          │   │   Map    │   │  /map2   │   │          │      │
│  └──────────┘   └──────────┘   └──────────┘   └──────────┘      │
│                                                                 │
│  Tutorial 2: VTaskPath Effect API                               │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐      │
│  │Path.vtask│ → │ via/map  │ → │ timeout/ │ → │   run    │      │
│  │          │   │          │   │handleErr │   │          │      │
│  └──────────┘   └──────────┘   └──────────┘   └──────────┘      │
└─────────────────────────────────────────────────────────────────┘
```

By the end, you'll understand how to build concurrent applications using functional composition rather than low-level thread management.

---

## Tutorial 1: VTask Fundamentals (~25 minutes)
**File**: `TutorialVTask.java` | **Exercises**: 8

Master virtual thread-based concurrency with functional composition.

### Part 1: Creating VTasks

**What you'll learn**:
- Creating lazy computations with `VTask.of()` and `VTask.delay()`
- Immediate values with `VTask.succeed()` and `VTask.fail()`
- Understanding that VTask describes effects without executing them

**Key insight**: A `VTask` is a recipe, not a meal. Creating a `VTask` doesn't run anything; it just describes what *would* happen when you eventually call `run()`.

**Exercise 1**: Create a VTask that computes the length of a string
**Exercise 2**: Create a succeeding and a failing VTask

---

### Part 2: Transforming VTasks

**What you'll learn**:
- Using `map` to transform results
- Using `flatMap` to chain dependent computations
- Short-circuiting on errors automatically

**Key insight**: VTask follows the same patterns as other monads in Higher-Kinded-J. If you know `Either.flatMap`, you know `VTask.flatMap`.

**Exercise 3**: Transform a VTask result using map
**Exercise 4**: Chain dependent VTask computations with flatMap

---

### Part 3: Error Handling

**What you'll learn**:
- Using `runSafe()` to capture errors in `Try`
- Recovery with `recover` and `recoverWith`
- Transforming errors with `mapError`

**Key insight**: `runSafe()` is the preferred execution method; it captures failures in a `Try`, letting you handle errors functionally rather than with try-catch.

**Exercise 5**: Execute a VTask safely and handle the result
**Exercise 6**: Recover from a failed VTask with a default value

---

### Part 4: Parallel Composition

**What you'll learn**:
- Using `Par.zip` to run tasks in parallel
- Combining parallel results with `Par.map2`
- Racing tasks with `Par.race`
- Collecting results with `Par.all`

**Key insight**: Par combinators use `StructuredTaskScope` under the hood, ensuring proper cancellation if any task fails.

**Exercise 7**: Execute two tasks in parallel and combine results
**Exercise 8**: Race multiple tasks and get the first result

---

## Tutorial 2: VTaskPath Effect API (~20 minutes)
**File**: `TutorialVTaskPath.java` | **Exercises**: 8

Learn the fluent Effect Path API for VTask-based workflows.

### Part 1: Creating VTaskPaths

**What you'll learn**:
- Creating paths with `Path.vtask()`, `Path.vtaskPure()`, `Path.vtaskFail()`
- Understanding the relationship between VTask and VTaskPath
- Converting between VTask and VTaskPath

**Key insight**: VTaskPath wraps VTask to provide a fluent, composable API that integrates with the broader Effect Path ecosystem.

**Exercise 1**: Create a VTaskPath from a computation
**Exercise 2**: Create pure and failing VTaskPaths

---

### Part 2: Fluent Composition

**What you'll learn**:
- Chaining operations with `via()` (the Effect Path equivalent of flatMap)
- Transforming values with `map()`
- Debugging with `peek()`
- Sequencing with `then()`

**Key insight**: VTaskPath operations mirror VTask operations but use Effect Path naming conventions (`via` instead of `flatMap`) for consistency across all Path types.

**Exercise 3**: Chain VTaskPath operations with via
**Exercise 4**: Use peek for debugging a computation pipeline

---

### Part 3: Error Handling and Timeouts

**What you'll learn**:
- Safe execution with `runSafe()` returning `Try`
- Recovery patterns: `handleError()`, `handleErrorWith()`
- Timeout management with `timeout(Duration)`
- Building fallback chains

**Key insight**: Timeouts integrate naturally into the fluent API. A timeout that expires returns a failure that can be recovered from, just like any other error.

**Exercise 5**: Add a timeout to a slow operation
**Exercise 6**: Build a fallback chain with multiple recovery attempts

---

### Part 4: Real-World Patterns

**What you'll learn**:
- Service aggregation with parallel fetches
- Dashboard-style data loading
- Graceful degradation under failure

**Key insight**: Combining VTaskPath with Par combinators gives you the best of both worlds: fluent composition for individual paths and parallel execution for independent operations.

**Exercise 7**: Aggregate data from multiple services in parallel
**Exercise 8**: Build a resilient dashboard loader with timeouts and fallbacks

---

## Running the Tutorials

### Using Gradle
```bash
# Run all VTask exercises
./gradlew :hkj-examples:test --tests "*TutorialVTask*"

# Run VTaskPath exercises
./gradlew :hkj-examples:test --tests "*TutorialVTaskPath*"

# Check your solutions pass
./gradlew :hkj-examples:test --tests "*TutorialVTask*_Solution*"
./gradlew :hkj-examples:test --tests "*TutorialVTaskPath*_Solution*"
```

### Using Your IDE
Navigate to `hkj-examples/src/test/java/org/higherkindedj/tutorial/concurrency/` and run the tests.

---

## Common Pitfalls

### 1. Forgetting VTask is Lazy
**Problem**: Assuming a VTask runs when created.

```java
// WRONG: This doesn't print anything yet!
VTask<Unit> task = VTask.exec(() -> System.out.println("Hello"));

// RIGHT: You must run it
task.run(); // Now it prints
```

### 2. Using run() Instead of runSafe()
**Problem**: Using `run()` and letting exceptions propagate.

```java
// RISKY: Throws on failure
Integer result = VTask.of(() -> dangerousOperation()).run();

// BETTER: Captures failure in Try
Try<Integer> result = VTask.of(() -> dangerousOperation()).runSafe();
result.fold(
    value -> handleSuccess(value),
    error -> handleError(error)
);
```

### 3. Sequential When Parallel Would Work
**Problem**: Using `flatMap` for independent computations.

```java
// SEQUENTIAL: fetchB waits for fetchA to complete
VTask<Pair> result = fetchA.flatMap(a ->
    fetchB.map(b -> new Pair(a, b)));

// PARALLEL: Both execute simultaneously
VTask<Pair> result = Par.map2(fetchA, fetchB, Pair::new);
```

### 4. Ignoring Timeouts in Production
**Problem**: Letting slow operations block indefinitely.

```java
// RISKY: No timeout, could hang forever
VTaskPath<Data> path = Path.vtask(() -> fetchFromSlowService());

// BETTER: Add timeout with fallback
VTaskPath<Data> safe = Path.vtask(() -> fetchFromSlowService())
    .timeout(Duration.ofSeconds(5))
    .handleError(e -> Data.empty());
```

---

## Key Concepts Summary

| Concept | Description |
|---------|-------------|
| **Laziness** | VTask/VTaskPath describes computation; nothing runs until `run()`/`runSafe()`/`runAsync()` |
| **Virtual Threads** | Execution happens on lightweight JVM-managed threads |
| **Structured Concurrency** | Par combinators ensure proper task lifecycle management |
| **Error Propagation** | Failures short-circuit chains; use `recover` (VTask) or `handleError` (VTaskPath) |
| **Timeouts** | Use `timeout(Duration)` to prevent runaway operations |
| **Effect Path Integration** | VTaskPath integrates with other Path types and ForPath comprehensions |

---

## What's Next?

After completing this journey:

1. **Continue to Scope & Resource**: Learn structured concurrency and resource management → [Scope & Resource Journey](scope_resource_journey.md)
2. **Read the VTask Documentation**: Deep dive into all VTask operations → [VTask Guide](../../monads/vtask_monad.md)
3. **Explore VTaskPath Documentation**: See the full VTaskPath API → [VTaskPath Guide](../../effect/path_vtask.md)
4. **Learn Effect Contexts**: Use VTaskContext for dependency injection → [Effect Contexts](../../effect/effect_contexts.md)

---

**Related**: [VTask Guide](../../monads/vtask_monad.md) | [VTaskPath Guide](../../effect/path_vtask.md) | [Monad Guide](../../functional/monad.md) | [Effect Path Overview](../../effect/effect_path_overview.md)
