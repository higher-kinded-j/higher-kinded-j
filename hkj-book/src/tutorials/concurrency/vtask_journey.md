# Concurrency: VTask Journey

~~~admonish info title="What You'll Learn"
- Creating and composing lazy concurrent computations with VTask
- Executing tasks on virtual threads with proper error handling
- Using Par combinators for parallel execution
- Understanding structured concurrency principles
~~~

**Duration**: ~25 minutes | **Tutorials**: 1 | **Exercises**: 8

**Requirements**: Java 25+ (virtual threads and structured concurrency)

## Journey Overview

This journey introduces `VTask`, Higher-Kinded-J's effect type for virtual thread-based concurrency. You'll learn to describe computations lazily, compose them functionally, and execute them on lightweight virtual threads.

```
VTask.of() → map/flatMap → Par.zip/map2 → run/runSafe
   ↓            ↓              ↓              ↓
 Describe    Transform     Parallelize     Execute
```

By the end, you'll understand how to build concurrent applications using functional composition rather than low-level thread management.

---

## Tutorial: VTask Fundamentals (~25 minutes)
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

## Running the Tutorial

### Using Gradle
```bash
# Run all VTask exercises
./gradlew :hkj-examples:tutorialTest --tests "*TutorialVTask*"

# Check your solutions pass
./gradlew :hkj-examples:test --tests "*TutorialVTask_Solution*"
```

~~~admonish note title="Test Configuration"
Tutorial exercises use the `tutorialTest` task. The regular `test` task runs
solution tests only, which must pass to verify the tutorial is correctly designed.
~~~

### Using Your IDE
Navigate to `hkj-examples/src/test/java/org/higherkindedj/tutorial/concurrency/TutorialVTask.java` and run the tests.

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

---

## Key Concepts Summary

| Concept | Description |
|---------|-------------|
| **Laziness** | VTask describes computation; nothing runs until `run()`/`runSafe()`/`runAsync()` |
| **Virtual Threads** | Execution happens on lightweight JVM-managed threads |
| **Structured Concurrency** | Par combinators ensure proper task lifecycle management |
| **Error Propagation** | Failures short-circuit chains; use `recover` to handle |

---

## What's Next?

After completing this journey:

1. **Read the VTask Documentation**: Deep dive into all VTask operations → [VTask Guide](../../monads/vtask_monad.md)
2. **Explore Effect Path API**: See how VTask fits with other effect types
3. **Build Real Applications**: Use Par combinators for API aggregation, batch processing

---

**Related**: [VTask Guide](../../monads/vtask_monad.md) | [Monad Guide](../../functional/monad.md) | [MonadError Guide](../../functional/monad_error.md)
