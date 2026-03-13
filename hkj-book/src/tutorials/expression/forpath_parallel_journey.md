# Expression: ForPath Parallel Composition Journey

~~~admonish info title="What You'll Learn"
- How `ForPath.par()` combines independent computations using applicative semantics
- Short-circuit behaviour when one computation fails (MaybePath, EitherPath)
- True parallel execution with VTaskPath on virtual threads
- Chaining sequential operations after parallel composition
- The difference between applicative (`par`) and monadic (`from`) composition
~~~

**Duration**: ~20 minutes | **Tutorials**: 1 | **Exercises**: 9

## Journey Overview

When building workflows with for-comprehensions, some computations are genuinely independent: fetching a user profile and fetching application configuration, for example, have no dependency on each other. Using `.from()` chains these sequentially via `flatMap`, even though parallelism is safe. `ForPath.par()` lets you express this independence directly.

```
ForPath.par(pathA, pathB) → Steps2 → .let() / .from() / .yield()
ForPath.par(pathA, pathB, pathC) → Steps3 → .let() / .from() / .yield()
```

For `VTaskPath`, `par()` spawns virtual threads via `StructuredTaskScope`, achieving true concurrency. For other Path types, execution is sequential but the code documents the dependency structure of your workflow.

~~~admonish tip title="Read First"
Before starting this tutorial, read the [ForPath Comprehension](../../effect/forpath_comprehension.md) chapter, particularly the [ForPath Parallel Composition](../../effect/forpath_par.md) section.
~~~

---

## Tutorial 02: ForPath Parallel Composition (~20 minutes)
**File**: `Tutorial02_ForPathParallel.java` | **Exercises**: 9

Master parallel composition across multiple Path types, from simple value combination to true concurrent execution with virtual threads.

**What you'll learn**:
- Basic `par(2)` and `par(3)` with MaybePath
- Short-circuit behaviour when a computation fails
- EitherPath error propagation with `par()`
- IdPath composition with `Id.of()` wrapping
- IOPath lazy composition with `par()`
- VTaskPath true parallelism with timing verification
- Chaining `.let()` after `par()`
- Comparing `par()` (applicative) vs `from()` (monadic) semantics

**Key insight**: `par()` expresses *independence*, not necessarily *concurrency*. The same code works across all Path types, but only VTaskPath delivers true parallel execution. This separation of intent from implementation lets you write correct, clear code regardless of the underlying execution model.

**Links to documentation**: [ForPath Comprehension](../../effect/forpath_comprehension.md)

[Hands On Practice](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/expression/Tutorial02_ForPathParallel.java)

---

### Exercise Progression

| Exercise | Concept | Difficulty |
|----------|---------|------------|
| 1 | Basic `par(2)` with MaybePath | Beginner |
| 2 | Short-circuit on Nothing | Beginner |
| 3 | `par(3)` with three values | Beginner |
| 4 | EitherPath `par(2)` | Intermediate |
| 5 | IdPath `par(3)` with `Id.of()` | Intermediate |
| 6 | IOPath `par(2)` | Intermediate |
| 7 | VTaskPath true parallelism | Advanced |
| 8 | Chaining `.let()` after `par()` | Intermediate |
| 9 | `par()` vs `from()` comparison | Advanced |

---

**Previous:** [Expression: ForState](forstate_journey.md)
**Next:** [Resilience Patterns](../resilience/resilience_journey.md)
