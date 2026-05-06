# Core Types: Foundations Journey

~~~admonish info title="What We'll Learn"
- How Higher-Kinded Types work in Java through the `Kind<F, A>` wrapper
- The progression from Functor (map) to Applicative (combine) to Monad (chain)
- Building the mental model for functional programming in Java
- The Pain → Promise framing: which Java pain points each abstraction replaces
~~~

**Duration**: ~50 minutes (with the new anchor and diagnostic exercises) | **Tutorials**: 5 | **Exercises**: 32

~~~admonish tip title="Where This Fits in the Bigger Picture"
This journey is the hands-on counterpart to the [Foundations chapter](../../hkts/foundations_intro.md). Each tutorial exercises one layer of the [One Line, Six Layers](../../hkts/one_line_six_layers.md) anchor:

| Tutorial | Anchor token | Foundations reference |
|----------|--------------|------------------------|
| **00 One Line, Six Layers** | All six tokens, end-to-end | [One Line, Six Layers](../../hkts/one_line_six_layers.md) |
| 01 Kind basics | (the type-level encoding under everything) | [Lifting the Hood](../../hkts/lifting_the_hood.md) |
| 02 Functor mapping | `.map(...)` / `.modify(...)` | [Functor](../../functional/functor.md) |
| 03 Applicative combining | independent `zipWith` / `map2` | [Applicative](../../functional/applicative.md) |
| 04 Monad chaining | `.flatMap(repo::save)` | [Monad](../../functional/monad.md) |

When a *Things People Get Wrong* panel in the Foundations chapter references an idea, the diagnostic exercise that drills it lives in the matching tutorial here.
~~~

## Journey Overview

This journey builds the foundation for everything else in Higher-Kinded-J. We learn how Java can simulate Higher-Kinded Types and master the three core abstractions: Functor, Applicative, and Monad.

```
Tutorial 00 (the whole stack as one expression)
        │
        ▼
Kind (container) → Functor (map) → Applicative (combine) → Monad (chain)
   T01                T02              T03                  T04
```

By the end, we understand why these abstractions exist and how they help us write cleaner, more composable code — starting from a single expression that uses all of them, then unpacking each layer in turn.

~~~admonish note title="One-page Cheatsheet"
A single-page reference to every abstraction in this journey, with imperative-Java vs Higher-Kinded-J side-by-side, is at [Foundations Cheatsheet](foundations_cheatsheet.md). Useful when revising or when applying these patterns in our own code.
~~~

---

## Tutorial 00: One Line, Six Layers (~10 minutes)
**File**: `Tutorial00_OneLineSixLayers.java` | **Exercises**: 8 (7 graded + 1 diagnostic, plus a no-code setup check)

The chapter anchor and the setup check. We type out one line of working Higher-Kinded-J that touches every layer of the library, then unpack each token in a separate exercise.

**What we'll learn**:
- Confirm the build is wired correctly (annotation processing, Java 25, the Effect Path API on the classpath)
- See the same six tokens — Effect type, natural transformation, optic, functor, monad, dispatch — that the rest of the chapter teaches in detail
- Set the Pain → Promise framing for everything that follows

**Key insight**: A single expression — `find(id).toEitherPath(...).map(item -> lens.modify(...)).via(repo::save).run()` — already uses every layer. Once we can read that expression, the rest of the chapter is unpacking the parts.

[Hands On Practice](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial00_OneLineSixLayers.java)

---

## Tutorial 01: Kind Basics (~10 minutes)
**File**: `Tutorial01_KindBasics.java` | **Exercises**: 5 (4 graded + 1 diagnostic)

Demystify the `Kind<F, A>` wrapper that makes Higher-Kinded Types possible in Java.

**What we'll learn**:
- Why `Kind<F, A>` exists and what it represents
- How to widen concrete types like `Either<L, R>` into `Kind<F, A>`
- How to narrow `Kind<F, A>` back to concrete types
- What "witness types" are and how they work — and how the witness catches cross-helper narrow attempts at compile time

**Key insight**: `Kind<F, A>` is just a type-level trick. The actual data never changes; we are just changing how the type system sees it.

**Java idiom anchor**: same shape as `Stream<Order>`, `CompletableFuture<Response>`, `Optional<User>` — except `F` is now a type parameter we can pass around.

**Links to documentation**: [HKT Introduction](../../hkts/hkt_introduction.md) | [Core Concepts](../../hkts/core-concepts.md) | [Lifting the Hood](../../hkts/lifting_the_hood.md)

[Hands On Practice](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial01_KindBasics.java)

---

## Tutorial 02: Functor Mapping (~10 minutes)
**File**: `Tutorial02_FunctorMapping.java` | **Exercises**: 7 (6 graded + 1 diagnostic)

Learn to transform values inside containers uniformly, regardless of the container type.

**What we'll learn**:
- The `map` operation and how it preserves structure
- Using Functor to write code that works for `List`, `Optional`, `Either`, etc.
- Method references with Functor
- Functor composition (chaining multiple maps)
- The `map` vs `flatMap` decision (the diagnostic exercise produces the nested-Either symptom on purpose)

**Key insight**: Functor lets us focus on the transformation logic without worrying about the container's details.

**Java idiom anchor**: Functor is the abstraction behind `Stream.map`, `Optional.map`, and `CompletableFuture.thenApply` — each is a Functor instance in disguise.

**Links to documentation**: [Functor Guide](../../functional/functor.md) (includes the *Things People Get Wrong* panel that the diagnostic exercise is drawn from)

[Hands On Practice](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial02_FunctorMapping.java)

---

## Tutorial 03: Applicative Combining (~10 minutes)
**File**: `Tutorial03_ApplicativeCombining.java` | **Exercises**: 8 (7 graded + 1 diagnostic)

Learn to combine multiple independent computations, perfect for validating forms where each field is checked separately.

**What we'll learn**:
- Lifting plain values into context with `of` (pure)
- Combining 2, 3, 4, or 5 independent values with `map2`, `map3`, `map4`, `map5` via the typeclass instance
- The difference between `Either` (fail-fast) and `Validated` (accumulate errors via a `Semigroup`)
- When to use Applicative vs Monad — the diagnostic exercise makes the Validated case visible

**Key insight**: Use Applicative when the computations don't depend on each other's results. It is more powerful than Functor and (when failure semantics matter) more honest than Monad about what is independent.

**Java idiom anchor**: `CompletableFuture.allOf` is the Applicative for futures. Bean Validation's `Set<ConstraintViolation>` is the same idea as Validated, except now the accumulation is part of the type.

**Links to documentation**: [Applicative Guide](../../functional/applicative.md)

[Hands On Practice](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial03_ApplicativeCombining.java)

---

## Tutorial 04: Monad Chaining (~10 minutes)
**File**: `Tutorial04_MonadChaining.java` | **Exercises**: 8 (7 graded + 1 diagnostic)

Learn to chain computations where each step depends on the result of the previous one.

**What we'll learn**:
- The `flatMap` operation for dependent computations
- How Monad automatically handles context (no manual unwrapping)
- Chaining database lookups, API calls, and business logic
- Short-circuiting on errors automatically
- When *not* to use `flatMap` — the diagnostic exercise covers the "all inputs are independent" case

**Key insight**: Monad is like a programmable semicolon. It handles the "what happens next" logic so we can focus on the business logic.

**Java idiom anchor**: `Optional.flatMap` and `CompletableFuture.thenCompose` are Monad instances. Roll-your-own `Result<T>` types are usually trying to recreate `Either + flatMap`.

**Links to documentation**: [Monad Guide](../../functional/monad.md) | [Order Workflow Example](../../hkts/order-walkthrough.md)

[Hands On Practice](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial04_MonadChaining.java)

---

## How Each Exercise Is Structured

Tutorials in this journey use a consistent shape established by the Tutorial 01 pilot:

- **Pain → Promise** header at the top of every file: imperative Java first, then the Higher-Kinded-J equivalent.
- **Java idiom anchor** mapping the abstraction to APIs developers already know.
- **Tiered hints** on every exercise — Nudge (the concept), Strategy (the method), Spoiler (the literal answer). Read top-to-bottom; stop as soon as we have what we need.
- **Diagnostic exercise** at the end of each tutorial, drawn from the matching *Things People Get Wrong* panel in the Foundations chapter.
- **Teaching solutions** in `solutions/coretypes/`: every exercise has commentary on *why* the idiomatic choice is idiomatic, one alternative shape with the trade-off, and one common wrong attempt with its symptom.

---

## Running the Tutorials

```bash
./gradlew :hkj-examples:tutorialTest --tests "*Tutorial00_OneLineSixLayers*"
./gradlew :hkj-examples:tutorialTest --tests "*Tutorial01_KindBasics*"
./gradlew :hkj-examples:tutorialTest --tests "*Tutorial02_FunctorMapping*"
./gradlew :hkj-examples:tutorialTest --tests "*Tutorial03_ApplicativeCombining*"
./gradlew :hkj-examples:tutorialTest --tests "*Tutorial04_MonadChaining*"
```

Or run them all at once and watch the progress bar:

```bash
./gradlew :hkj-examples:tutorialProgress
```

In an IDE, right-click on any tutorial file and select "Run".

---

## Common Pitfalls

### 1. Skipping the Widen/Narrow Steps
**Problem**: Trying to pass `Either<L, R>` directly to a method expecting `Kind<F, A>`.

**Solution**: Always widen before passing to generic code.

```java
Either<String, Integer> either = Either.right(42);
Kind<EitherKind.Witness<String>, Integer> kind = EITHER.widen(either);
```

### 2. Looking for `map2` on the Concrete Type
**Problem**: Calling `value1.map2(value2, ...)` on `Either`. There is no such instance method.

**Solution**: Combinators across multiple inputs live on the `Applicative` typeclass instance. Get the instance, widen the inputs, call the combinator, narrow the result.

```java
EitherMonad<String> app = EitherMonad.instance();
Either<String, Integer> sum =
    EITHER.narrow(app.map2(EITHER.widen(value1), EITHER.widen(value2), Integer::sum));
```

### 3. Confusing `map` with `flatMap`
**Problem**: Using `map` with a function that returns a wrapped value, producing `F<F<B>>`.

**Solution**: Ask "what does the function return?". If a plain value, use `map`. If a wrapped value, use `flatMap`.

### 4. Using `flatMap` When the Steps Are Independent
**Problem**: Forcing a sequential mental model on inputs that do not depend on each other; on `Validated` this loses the error-accumulation behaviour.

**Solution**: Reach for `mapN` (Applicative) when the inputs do not depend on each other.

---

## What's Next?

After completing this journey:

1. **Continue to Error Handling** — `MonadError` and when to use which concrete type
2. **Jump to Effect API** — start using the user-friendly Effect Path API (recommended)
3. **Explore Optics** — see how these concepts power the optics library
4. **Read [Lifting the Hood](../../hkts/lifting_the_hood.md)** — a token-by-token trace of one `flatMap` call through `widen`, dispatch, and `narrow`, with the JIT cost called out

---

**Previous:** [Interactive Tutorials](../tutorials_intro.md)
**Next:** [Core Types: Error Handling](error_handling_journey.md)
