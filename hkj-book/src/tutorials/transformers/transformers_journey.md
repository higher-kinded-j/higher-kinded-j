# Monad Transformers Journey

**Estimated Duration**: ~90 minutes (four sub-journeys) | **Exercises**: ~28

~~~admonish info title="When to Take This Journey"
The Effect Path API ([Tutorial 01: Effect Path Basics](../effect/effect_journey.md)) covers most workflows we will write in Java. Take this journey when we have hit one of the corners that Path types do not reach: integrating with code that returns a different outer monad, or writing library code that should work against any caller's effect stack.
~~~

~~~admonish tip title="Where This Fits in the Bigger Picture"
Monad transformers compose two monads into a single layer (e.g. `CompletableFuture<Either<L, R>>` → `EitherT`). The `.toEitherPath()` token in [One Line, Six Layers](../../hkts/one_line_six_layers.md) is a special case of the same idea expressed as a [natural transformation](../../functional/natural_transformation.md). When the Path types do not reach, we drop down here.
~~~

## What We'll Learn

The Monad Transformers Journey builds on the Effect Path API to cover the cases where we need to drop down to the underlying transformer machinery.

### Tutorial 01: When Path Isn't Enough (~30 min, 6 exercises)
- Bridging an existing `CompletableFuture<Either<L, R>>` into `EitherT`
- Lifting synchronous `Either` values into the same workflow
- Composing async-and-typed-error steps with `For` comprehensions
- Recovering from typed errors with `handleErrorWith`
- Collapsing back to ordinary Java at the boundary

### Tutorial 02: Async with Absence (~25 min, 5 exercises)
- The same shape applied to `CompletableFuture<Optional<T>>`
- Chaining async lookups with `For`
- Providing defaults when a lookup yields nothing
- A short tour of `MaybeT` for codebases that prefer `Maybe`

### Tutorial 03: Stacking Transformers (~15 min, 4 exercises)
- Two effects in one workflow: `EitherT` over `Optional`
- Why `For` keeps stacked code readable
- When stacking gets uncomfortable, what to reach for instead

### Tutorial 04: Polymorphic Capabilities (MTL) (~30-40 min, 14 exercises)
- `MonadReader`: read-only access to a shared environment
- `MonadState`: read-write state threading
- `MonadWriter`: append-only output accumulation
- Writing polymorphic functions that accept capability interfaces, not concrete types

## Why Transformers Second?

The Effect Path API is designed to be the primary interface. Reach for raw transformers only when:

1. You need to integrate with code that already returns a different outer monad
2. You are writing a library and want callers to plug in their own effect stack
3. You need an outer monad that no Path type wraps

If none of those apply, stay on the Path API. Your code will be shorter and clearer.

## Getting Started

The tutorials are located in `hkj-examples/src/test/java/org/higherkindedj/tutorial/transformers/`:

### Tutorial 01: When Path Isn't Enough

**File**: `Tutorial01_WhenPathIsNotEnough.java`

```java
// Bridge an existing Future<Either> into the transformer
var london = fetchWeather("London");
EitherT<CompletableFutureKind.Witness, WeatherError, WeatherReport> wrapped =
    EitherT.fromKind(london);

// Compose two steps with For
var workflow = For.from(eitherTMonad, EitherT.fromKind(fetchWeather("Berlin")))
    .yield(report -> new TravelAdvice(report.city(),
        report.temperature() < 10 ? "Pack a coat" : "Travel light"));
```

### Tutorial 02: Async with Absence

**File**: `Tutorial02_AsyncWithAbsence.java`

```java
// Chain two async lookups
var workflow = For.from(optionalTMonad, OptionalT.fromKind(fetchUser("alice")))
    .from(user -> OptionalT.fromKind(fetchProfile(user.id())))
    .yield((user, profile) -> profile);
```

### Tutorial 03: Stacking Transformers

**File**: `Tutorial03_StackingTransformers.java`

```java
// EitherT layered over Optional: a value that may be absent AND may have failed validation
var sum = For.from(eitherTOverOptional,
        EitherT.fromEither(optionalMonad, Either.<AppError, Integer>right(10)))
    .from(_ -> EitherT.fromEither(optionalMonad, Either.<AppError, Integer>right(32)))
    .yield((a, b) -> a + b);
```

### Tutorial 04: Polymorphic Capabilities (MTL)

**File**: `Tutorial04_PolymorphicCapabilities.java`

```java
// A function that declares "I need to read an AppConfig" and nothing else
static <F extends WitnessArity<TypeArity.Unary>> Kind<F, String> buildUrl(
    MonadReader<F, AppConfig> env) {
  return For.from(env, env.ask())
      .yield(c -> c.dbUrl() + "?retries=" + c.maxRetries());
}
```

## Prerequisites

Before starting this journey, you should:

1. Be comfortable with Java lambdas, generics, and `var`
2. Have completed the [Effect API Journey](../effect/effect_journey.md)

**Helpful background**: skim the [Monad Transformers Quickstart](../../transformers/quickstart.md) and [Stack Archetypes](../../transformers/archetypes.md) so you have the high-level picture before drilling into exercises.

## Running the Tutorials

```bash
# Run all transformer tutorials
./gradlew :hkj-examples:test --tests "*tutorial.transformers.*"

# Run a specific tutorial
./gradlew :hkj-examples:test --tests "*Tutorial01_WhenPathIsNotEnough*"
./gradlew :hkj-examples:test --tests "*Tutorial02_AsyncWithAbsence*"
./gradlew :hkj-examples:test --tests "*Tutorial03_StackingTransformers*"
./gradlew :hkj-examples:test --tests "*Tutorial04_PolymorphicCapabilities*"
```

## Further Resources

After completing this journey, explore:

- [Monad Transformers Quickstart](../../transformers/quickstart.md), three runnable examples in 150 lines
- [Stack Archetypes](../../transformers/archetypes.md), seven named patterns
- [Migration Cookbook](../../transformers/migration_cookbook.md), imperative and Path translations
- [MTL Capabilities](../../transformers/mtl_capabilities.md), the conceptual reference for Tutorial 04
- [EitherT](../../transformers/eithert_transformer.md), [OptionalT](../../transformers/optionalt_transformer.md), [MaybeT](../../transformers/maybet_transformer.md), [ReaderT](../../transformers/readert_transformer.md), [StateT](../../transformers/statet_transformer.md), [WriterT](../../transformers/writert_transformer.md), the per-transformer reference pages

## What's Next?

After completing the transformers journey:

- **Polymorphic library code**: study [MTL Combining Capabilities](../../transformers/mtl_combining.md) for multi-capability functions
- **Production patterns**: explore the order-workflow examples in `hkj-examples/src/main/java/org/higherkindedj/example/order/`

---

**Previous:** [Effect API](../effect/effect_journey.md)
**Next:** [Concurrency: VTask](../concurrency/vtask_journey.md)
