# Core Types Tutorial Track

**Duration**: ~86 minutes | **Tutorials**: 10 | **Exercises**: 60

## What You'll Master

By the end of this track, you'll confidently use Higher-Kinded Types to:
- Transform values across different computational contexts (Functor)
- Combine independent operations for parallel validation (Applicative)
- Chain dependent computations with automatic error handling (Monad)
- Choose the right type for error handling, optionality, and accumulation
- Transform between type constructors with Natural Transformations
- Optimise mapping operations with Coyoneda (map fusion)
- Model independent, parallelisable computations with Free Applicative

This track teaches you the theoretical foundation that powers the entire Higher-Kinded-J library, including the optics system. You'll learn to write generic code that works uniformly across `List`, `Optional`, `Either`, `CompletableFuture`, and custom types.

## The Progression

Each tutorial introduces a new abstraction that builds on the previous one:

```
Kind (container) → Functor (map) → Applicative (combine) → Monad (chain)
                                                              ↓
                          Coyoneda ← Natural Transformation ← Real World
                              ↓
                      Free Applicative
```

### Tutorial 01: Kind Basics (~8 minutes)
**File**: `Tutorial01_KindBasics.java` | **Exercises**: 4

Demystify the `Kind<F, A>` wrapper that makes Higher-Kinded Types possible in Java.

**What you'll learn**:
- Why `Kind<F, A>` exists and what it represents
- How to widen concrete types like `Either<L, R>` into `Kind<F, A>`
- How to narrow `Kind<F, A>` back to concrete types
- What "witness types" are and how they work

**Key insight**: `Kind<F, A>` is just a type-level trick. The actual data never changes; we're just changing how the type system sees it.

**Links to documentation**: [HKT Introduction](../hkts/hkt_introduction.md) | [Core Concepts](../hkts/core-concepts.md)

---

### Tutorial 02: Functor Mapping (~8 minutes)
**File**: `Tutorial02_FunctorMapping.java` | **Exercises**: 6

Learn to transform values inside containers uniformly, regardless of the container type.

**What you'll learn**:
- The `map` operation and how it preserves structure
- Using Functor to write code that works for `List`, `Optional`, `Either`, etc.
- Method references with Functor
- Functor composition (chaining multiple maps)

**Key insight**: Functor lets you focus on the transformation logic without worrying about the container's details.

**Real-world application**: Transform API responses, map over asynchronous results, convert validation errors.

**Links to documentation**: [Functor Guide](../functional/functor.md)

---

### Tutorial 03: Applicative Combining (~10 minutes)
**File**: `Tutorial03_ApplicativeCombining.java` | **Exercises**: 7

Learn to combine multiple independent computations, perfect for validating forms where each field is checked separately.

**What you'll learn**:
- Lifting plain values into context with `of` (pure)
- Combining 2, 3, 4, or 5 independent values with `map2`, `map3`, `map4`, `map5`
- The difference between `Either` (fail-fast) and `Validated` (accumulate errors)
- When to use Applicative vs Monad

**Key insight**: Use Applicative when your computations don't depend on each other's results. It's more powerful than Functor but less powerful (and more efficient) than Monad.

**Real-world application**: Form validation, combining multiple API calls in parallel, aggregating independent data sources.

**Links to documentation**: [Applicative Guide](../functional/applicative.md)

---

### Tutorial 04: Monad Chaining (~12 minutes)
**File**: `Tutorial04_MonadChaining.java` | **Exercises**: 7

Learn to chain computations where each step depends on the result of the previous one.

**What you'll learn**:
- The `flatMap` operation for dependent computations
- How Monad automatically handles context (no manual unwrapping!)
- Chaining database lookups, API calls, and business logic
- Short-circuiting on errors automatically

**Key insight**: Monad is like a programmable semicolon. It handles the "what happens next" logic so you can focus on your business logic.

**Real-world application**: Multi-step workflows, database query chains, API request pipelines, business rule processing.

**Links to documentation**: [Monad Guide](../functional/monad.md) | [Order Workflow Example](../hkts/order-walkthrough.md)

---

### Tutorial 05: MonadError Handling (~8 minutes)
**File**: `Tutorial05_MonadErrorHandling.java` | **Exercises**: 7

Learn to make failures explicit and recoverable using MonadError.

**What you'll learn**:
- Creating errors explicitly with `raiseError`
- Recovering from errors with `handleErrorWith` and `recover`
- Using `Try` to wrap exception-throwing code safely
- When to use `Either`, `Try`, or `Validated`

**Key insight**: MonadError makes error handling a first-class part of your type signatures. No more surprise exceptions!

**Real-world application**: API error handling, database failure recovery, validation with custom error types.

**Links to documentation**: [MonadError Guide](../functional/monad_error.md) | [Either Monad](../monads/either_monad.md)

---

### Tutorial 06: Concrete Types (~10 minutes)
**File**: `Tutorial06_ConcreteTypes.java` | **Exercises**: 7

Learn when to use each concrete type that implements the typeclasses you've learned.

**What you'll learn**:
- `Either<L, R>` for explicit, fail-fast error handling
- `Maybe<A>` for optional values without error details
- `List<A>` for working with multiple values
- `Validated<E, A>` for accumulating all errors
- How to convert between these types

**Key insight**: Each type makes different trade-offs. `Either` fails fast, `Validated` accumulates errors, `Maybe` discards error details.

**Decision tree**:
- Need error message? → `Either` or `Validated`
- Want to collect ALL errors? → `Validated`
- Just optional value? → `Maybe`
- Multiple values? → `List`

**Links to documentation**: [Either](../monads/either_monad.md) | [Maybe](../monads/maybe_monad.md) | [Validated](../monads/validated_monad.md)

---

### Tutorial 07: Real World (~12 minutes)
**File**: `Tutorial07_RealWorld.java` | **Exercises**: 6

Bring everything together by building realistic workflows that combine multiple patterns.

**What you'll learn**:
- Building validation pipelines with Applicative
- Processing data streams with Functor and Monad
- Using `Reader` monad for dependency injection
- Combining effects: validation + transformation + error handling

**Key insight**: Real applications rarely use just one pattern. They compose Functor, Applicative, and Monad to build robust systems.

**Real-world scenarios**:
- User registration with validation, database checks, and email sending
- Data import pipeline with parsing, validation, and transformation
- Configuration-driven workflow using Reader monad

**Links to documentation**: [Reader Monad](../monads/reader_monad.md) | [For Comprehensions](../functional/for_comprehension.md)

---

### Tutorial 08: Natural Transformations (~8 minutes)
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

**Links to documentation**: [Natural Transformation Guide](../functional/natural_transformation.md)

---

### Tutorial 09: Coyoneda (~8 minutes)
**File**: `Tutorial09_Coyoneda.java` | **Exercises**: 5

Learn how Coyoneda gives you a free Functor and enables map fusion.

**What you'll learn**:
- Lifting values into Coyoneda with `lift`
- Mapping without a Functor instance (deferred execution)
- Map fusion: chaining maps accumulates into one traversal
- Lowering back to execute accumulated transformations
- When and why to use Coyoneda

**Key insight**: Coyoneda stores the value and accumulated function separately, executing only when you lower. Multiple maps become a single traversal.

**Real-world application**: Optimising repeated transformations, working with types that lack Functor instances, building efficient pipelines.

**Links to documentation**: [Coyoneda Guide](../monads/coyoneda.md)

---

### Tutorial 10: Free Applicative (~10 minutes)
**File**: `Tutorial10_FreeApplicative.java` | **Exercises**: 6

Learn to model independent computations that can potentially run in parallel.

**What you'll learn**:
- Creating pure values with `FreeAp.pure`
- Lifting instructions with `FreeAp.lift`
- Combining independent computations with `map2`
- Interpreting programs with `foldMap`
- The key difference from Free Monad: independence vs dependence

**Key insight**: With Free Applicative, neither computation in `map2` depends on the other's result. This structural independence enables parallel execution.

**Real-world application**: Parallel data fetching, form validation, batch API calls, request deduplication.

**Links to documentation**: [Free Applicative Guide](../monads/free_applicative.md)

---

## Learning Map

```
Tutorial 01: Kind Basics
    ↓
    └─ Understand Kind<F, A> wrapper
       ↓
Tutorial 02: Functor
    ↓
    └─ Transform values with map()
       ↓
Tutorial 03: Applicative
    ↓
    └─ Combine independent values with map2/map3/map4
       ↓
Tutorial 04: Monad
    ↓
    └─ Chain dependent computations with flatMap()
       ↓
Tutorial 05: MonadError
    ↓
    └─ Handle failures explicitly
       ↓
Tutorial 06: Concrete Types
    ↓
    └─ Choose the right type for the job
       ↓
Tutorial 07: Real World
    ↓
    └─ Combine everything into production patterns
       ↓
Tutorial 08: Natural Transformations
    ↓
    └─ Transform between type constructors
       ↓
Tutorial 09: Coyoneda
    ↓
    └─ Free functor and map fusion
       ↓
Tutorial 10: Free Applicative
    ↓
    └─ Model independent, parallelisable computations
```

## Key Concepts Summary

### Kind<F, A>
A type wrapper that simulates Higher-Kinded Types in Java. Think of it as a temporary disguise for types like `Either<L, R>` so they can be treated generically.

### Functor
A typeclass for types that can be mapped over. Provides `map(Function<A, B> f, Kind<F, A> kind)`.

### Applicative
A typeclass for combining independent values in context. Provides `map2`, `map3`, `map4`, `map5` for combining 2-5 values.

### Monad
A typeclass for chaining dependent computations. Provides `flatMap` for sequences where each step depends on the previous result.

### MonadError
A typeclass for explicit error handling. Provides `raiseError` and `handleErrorWith` for working with failures.

### Natural Transformation
A polymorphic function between type constructors: `Natural<F, G>` transforms `Kind<F, A>` to `Kind<G, A>` for any type `A`. Used to interpret Free structures.

### Coyoneda
The "free functor" that gives any type a Functor instance. Stores a value and an accumulated function, enabling map fusion where multiple maps become a single traversal.

### Free Applicative
Captures independent computations that can potentially run in parallel. Unlike Free Monad's `flatMap`, `map2` combines values without creating dependencies.

## Running the Tutorials

### Option 1: Run All Tests
```bash
./gradlew :hkj-examples:test --tests "*coretypes*"
```

### Option 2: Run Individual Tutorials
```bash
./gradlew :hkj-examples:test --tests "*Tutorial01_KindBasics*"
./gradlew :hkj-examples:test --tests "*Tutorial02_FunctorMapping*"
# ... etc
```

### Option 3: Use Your IDE
Right-click on any tutorial file → "Run 'Tutorial01_KindBasics'"

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

### 3. Forgetting to Handle Errors
**Problem**: Assuming `Either.right()` everywhere without planning for `Either.left()`.

**Solution**: Always think about both paths. Tests check both success and failure cases!

### 4. Type Inference Struggles
**Problem**: Java can't infer the type parameters and gives cryptic errors.

**Solution**: Add explicit type witnesses:
```java
EitherMonad<String> monad = EitherMonad.instance();
```

## What's Next?

After completing this track:

1. **Apply the Patterns**: Try using `Either`, `Validated`, or `Reader` in your own projects
2. **Explore the Optics Track**: See how these concepts power the optics library
3. **Read the Monad Transformers Guide**: Learn to stack multiple effects (e.g., `EitherT<CompletableFuture, Error, A>`)
4. **Study Real Examples**: The [Order Workflow](../hkts/order-walkthrough.md) shows these patterns in production

## Getting Help

- **Stuck on an exercise?** Check the [Solutions Guide](solutions_guide.md)
- **Conceptual confusion?** Re-read the linked documentation
- **Still stuck?** Ask in [GitHub Discussions](https://github.com/higher-kinded-j/higher-kinded-j/discussions)

---

Ready to start? Open `Tutorial01_KindBasics.java` and let's demystify Higher-Kinded Types!
