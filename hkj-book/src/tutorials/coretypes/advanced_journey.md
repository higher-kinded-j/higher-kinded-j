# Core Types: Advanced Patterns Journey

~~~admonish info title="What You'll Learn"
- Natural Transformations for polymorphic type conversion
- Coyoneda for free functors and map fusion optimisation
- Free Applicative for modelling independent, parallelisable computations
- Static analysis of programs before execution
~~~

**Duration**: ~38 minutes | **Tutorials**: 4 | **Exercises**: 26

**Prerequisites**: [Core Types: Error Handling Journey](error_handling_journey.md)

## Journey Overview

This journey covers advanced functional programming patterns. These aren't everyday tools, but when you need them, they're invaluable. You'll learn how to transform between type constructors, optimise mapping operations, and model parallel computations.

```
Natural Transformations → Coyoneda (map fusion) → Free Applicative (parallel)
```

---

## Tutorial 08: Natural Transformations (~8 minutes)
**File**: `Tutorial08_NaturalTransformation.java` | **Exercises**: 5

Learn to transform between type constructors while preserving structure.

**What you'll learn**:
- What natural transformations are and why they matter
- The `Natural<F, G>` interface for polymorphic transformations
- Composing transformations with `andThen` and `compose`
- Using transformations to interpret Free structures
- The identity transformation and its uses

**Key insight**: Natural transformations let you change the "container" without touching the contents. They're the morphisms between functors.

**Real-world application**: Interpreting DSLs, converting between effect types, abstracting over different backends.

**Links to documentation**: [Natural Transformation Guide](../../functional/natural_transformation.md)

[Hands On Practice](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial08_NaturalTransformation.java)

---

## Tutorial 09: Coyoneda (~8 minutes)
**File**: `Tutorial09_Coyoneda.java` | **Exercises**: 5

Learn how Coyoneda gives you a free Functor and enables map fusion.

**What you'll learn**:
- Lifting values into Coyoneda with `lift`
- Mapping without a Functor instance (deferred execution)
- Map fusion: chaining maps accumulates into one traversal
- Lowering back to execute accumulated transformations
- When and why to use Coyoneda

**Key insight**: Coyoneda stores the value and accumulated function separately, executing only when you lower. Multiple maps become a single traversal.

**Example**:
```java
// Without Coyoneda: 3 separate traversals
list.map(f).map(g).map(h);

// With Coyoneda: functions compose, single traversal on lower
Coyoneda.lift(list)
    .map(f)
    .map(g)
    .map(h)
    .lower(listFunctor);  // One traversal with f.andThen(g).andThen(h)
```

**Real-world application**: Optimising repeated transformations, working with types that lack Functor instances, building efficient pipelines.

**Links to documentation**: [Coyoneda Guide](../../monads/coyoneda.md)

[Hands On Practice](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial09_Coyoneda.java)

---

## Tutorial 10: Free Applicative (~10 minutes)
**File**: `Tutorial10_FreeApplicative.java` | **Exercises**: 6

Learn to model independent computations that can potentially run in parallel.

**What you'll learn**:
- Creating pure values with `FreeAp.pure`
- Lifting instructions with `FreeAp.lift`
- Combining independent computations with `map2`
- Interpreting programs with `foldMap`
- The key difference from Free Monad: independence vs dependence

**Key insight**: With Free Applicative, neither computation in `map2` depends on the other's result. This structural independence enables parallel execution.

**Comparison**:
```java
// Free Monad (sequential - B depends on A)
freeA.flatMap(a -> computeB(a))

// Free Applicative (parallel - independent)
FreeAp.map2(freeA, freeB, (a, b) -> combine(a, b))
```

**Real-world application**: Parallel data fetching, form validation, batch API calls, request deduplication.

**Links to documentation**: [Free Applicative Guide](../../monads/free_applicative.md)

[Hands On Practice](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial10_FreeApplicative.java)

---

## Tutorial 11: Static Analysis (~12 minutes)
**File**: `Tutorial11_StaticAnalysis.java` | **Exercises**: 10

Learn to analyse Free Applicative programs before execution.

**What you'll learn**:
- Counting operations with `FreeApAnalyzer.countOperations`
- Collecting all operations with `FreeApAnalyzer.collectOperations`
- Checking for dangerous operations before execution
- Grouping operations by type for batching
- Custom analysis with `Const` functor and `Monoid`
- Under/Over semantics with `SelectiveAnalyzer`
- Effect bounds and partitioning

**Key insight**: Because Free Applicative captures program structure as data, you can inspect it before running. This enables permission checking, cost estimation, and optimisation.

**Example**:
```java
// Build a program
FreeAp<DbOp, Dashboard> program = buildDashboard(userId);

// Analyse before running
int opCount = FreeApAnalyzer.countOperations(program);
boolean hasDeletions = FreeApAnalyzer.containsOperation(
    program,
    op -> DbOp.Delete.class.isInstance(DbOpHelper.DB_OP.narrow(op))
);

if (hasDeletions && !userHasPermission("delete")) {
    throw new SecurityException("Delete operations not permitted");
}
```

**Real-world application**: Permission checking, audit logging, cost estimation, query batching, security validation.

**Links to documentation**: [Choosing Abstraction Levels](../../functional/abstraction_levels.md)

[Hands On Practice](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial11_StaticAnalysis.java)

---

## Running the Tutorials

```bash
./gradlew :hkj-examples:test --tests "*Tutorial08_NaturalTransformation*"
./gradlew :hkj-examples:test --tests "*Tutorial09_Coyoneda*"
./gradlew :hkj-examples:test --tests "*Tutorial10_FreeApplicative*"
./gradlew :hkj-examples:test --tests "*Tutorial11_StaticAnalysis*"
```

---

## Key Concepts Summary

### Natural Transformation
A polymorphic function between type constructors: `Natural<F, G>` transforms `Kind<F, A>` to `Kind<G, A>` for any type `A`. Used to interpret Free structures.

### Coyoneda
The "free functor" that gives any type a Functor instance. Stores a value and an accumulated function, enabling map fusion where multiple maps become a single traversal.

### Free Applicative
Captures independent computations that can potentially run in parallel. Unlike Free Monad's `flatMap`, `map2` combines values without creating dependencies.

### Static Analysis
`FreeApAnalyzer` and `SelectiveAnalyzer` provide utilities for inspecting Free Applicative programs before execution. Count operations, check for dangerous effects, group operations for batching, and compute effect bounds.

---

## Common Pitfalls

### 1. Using Free Monad When Free Applicative Suffices
**Problem**: Using `flatMap` everywhere, missing parallelisation opportunities.

**Solution**: Ask: "Does this step need the previous result?" If no, use `map2`.

### 2. Forgetting to Lower Coyoneda
**Problem**: Building up Coyoneda but never executing with `lower()`.

**Solution**: Coyoneda is lazy. Call `lower(functor)` to execute.

### 3. Type Parameter Confusion with Natural
**Problem**: Getting lost in `Natural<F, G>` type parameters.

**Solution**: Think of it as: "I can turn any `F<A>` into a `G<A>`".

---

## What's Next?

Congratulations! You've completed the Core Types track. You now understand:
- The HKT simulation in Java
- Functor, Applicative, and Monad
- Error handling with MonadError
- Advanced patterns for optimisation and parallelism

**Recommended next steps**:

1. **Effect API Journey**: Learn the user-friendly path-based API (recommended)
2. **Optics: Lens & Prism**: Apply functional patterns to data manipulation
3. **Monad Transformers**: Stack effects with `EitherT`, `ReaderT`, etc.
4. **Study Real Examples**: See [Order Workflow](../../hkts/order-walkthrough.md)

---

**Previous**: [Core Types: Error Handling](error_handling_journey.md)
**Next**: [Effect API Journey](../effect/effect_journey.md)
