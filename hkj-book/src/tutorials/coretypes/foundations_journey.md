# Core Types: Foundations Journey

~~~admonish info title="What You'll Learn"
- How Higher-Kinded Types work in Java through the `Kind<F, A>` wrapper
- The progression from Functor (map) to Applicative (combine) to Monad (chain)
- Building the mental model for functional programming in Java
~~~

**Duration**: ~38 minutes | **Tutorials**: 4 | **Exercises**: 24

## Journey Overview

This journey builds the foundation for everything else in Higher-Kinded-J. You'll learn how Java can simulate Higher-Kinded Types and master the three core abstractions: Functor, Applicative, and Monad.

```
Kind (container) → Functor (map) → Applicative (combine) → Monad (chain)
```

By the end, you'll understand why these abstractions exist and how they help you write cleaner, more composable code.

---

## Tutorial 01: Kind Basics (~8 minutes)
**File**: `Tutorial01_KindBasics.java` | **Exercises**: 4

Demystify the `Kind<F, A>` wrapper that makes Higher-Kinded Types possible in Java.

**What you'll learn**:
- Why `Kind<F, A>` exists and what it represents
- How to widen concrete types like `Either<L, R>` into `Kind<F, A>`
- How to narrow `Kind<F, A>` back to concrete types
- What "witness types" are and how they work

**Key insight**: `Kind<F, A>` is just a type-level trick. The actual data never changes; we're just changing how the type system sees it.

**Links to documentation**: [HKT Introduction](../../hkts/hkt_introduction.md) | [Core Concepts](../../hkts/core-concepts.md)

[Hands On Practice](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial01_KindBasics.java)

---

## Tutorial 02: Functor Mapping (~8 minutes)
**File**: `Tutorial02_FunctorMapping.java` | **Exercises**: 6

Learn to transform values inside containers uniformly, regardless of the container type.

**What you'll learn**:
- The `map` operation and how it preserves structure
- Using Functor to write code that works for `List`, `Optional`, `Either`, etc.
- Method references with Functor
- Functor composition (chaining multiple maps)

**Key insight**: Functor lets you focus on the transformation logic without worrying about the container's details.

**Real-world application**: Transform API responses, map over asynchronous results, convert validation errors.

**Links to documentation**: [Functor Guide](../../functional/functor.md)

[Hands On Practice](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial02_FunctorMapping.java)

---

## Tutorial 03: Applicative Combining (~10 minutes)
**File**: `Tutorial03_ApplicativeCombining.java` | **Exercises**: 7

Learn to combine multiple independent computations, perfect for validating forms where each field is checked separately.

**What you'll learn**:
- Lifting plain values into context with `of` (pure)
- Combining 2, 3, 4, or 5 independent values with `map2`, `map3`, `map4`, `map5`
- The difference between `Either` (fail-fast) and `Validated` (accumulate errors)
- When to use Applicative vs Monad

**Key insight**: Use Applicative when your computations don't depend on each other's results. It's more powerful than Functor but less powerful (and more efficient) than Monad.

**Real-world application**: Form validation, combining multiple API calls in parallel, aggregating independent data sources.

**Links to documentation**: [Applicative Guide](../../functional/applicative.md)

[Hands On Practice](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial03_ApplicativeCombining.java)

---

## Tutorial 04: Monad Chaining (~12 minutes)
**File**: `Tutorial04_MonadChaining.java` | **Exercises**: 7

Learn to chain computations where each step depends on the result of the previous one.

**What you'll learn**:
- The `flatMap` operation for dependent computations
- How Monad automatically handles context (no manual unwrapping!)
- Chaining database lookups, API calls, and business logic
- Short-circuiting on errors automatically

**Key insight**: Monad is like a programmable semicolon. It handles the "what happens next" logic so you can focus on your business logic.

**Real-world application**: Multi-step workflows, database query chains, API request pipelines, business rule processing.

**Links to documentation**: [Monad Guide](../../functional/monad.md) | [Order Workflow Example](../../hkts/order-walkthrough.md)

[Hands On Practice](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial04_MonadChaining.java)

---

## Running the Tutorials

### Option 1: Run This Journey's Tests
```bash
./gradlew :hkj-examples:test --tests "*Tutorial01_KindBasics*"
./gradlew :hkj-examples:test --tests "*Tutorial02_FunctorMapping*"
./gradlew :hkj-examples:test --tests "*Tutorial03_ApplicativeCombining*"
./gradlew :hkj-examples:test --tests "*Tutorial04_MonadChaining*"
```

### Option 2: Use Your IDE
Right-click on any tutorial file and select "Run".

---

## Common Pitfalls

### 1. Skipping the Widen/Narrow Steps
**Problem**: Trying to pass `Either<L, R>` directly to a method expecting `Kind<F, A>`.

**Solution**: Always widen before passing to generic code:
```java
Either<String, Integer> either = Either.right(42);
Kind<EitherKind.Witness<String>, Integer> kind = EITHER.widen(either);
```

### 2. Confusing map2 with flatMap
**Problem**: Using `flatMap` when `map2` would suffice (or vice versa).

**Solution**: Ask: "Does this step depend on the previous result?"
- **No** → Use Applicative (`map2`, `map3`, etc.)
- **Yes** → Use Monad (`flatMap`)

---

## What's Next?

After completing this journey:

1. **Continue to Error Handling**: Learn MonadError and when to use which concrete type
2. **Jump to Effect API**: Start using the user-friendly Effect Path API (recommended)
3. **Explore Optics**: See how these concepts power the optics library

---

**Next Journey**: [Core Types: Error Handling](error_handling_journey.md)
