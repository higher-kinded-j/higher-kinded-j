# Selective: Conditional Effects 🔀

~~~admonish info title="What You'll Learn"
- How Selective bridges the gap between Applicative and Monad
- Conditional effect execution without full monadic power
- Using `select`, `whenS`, and `ifS` for static branching
- Building robust workflows with compile-time visible alternatives
- Combining multiple alternatives with `orElse`
- Real-world examples of conditional effect execution
~~~

You've seen how `Applicative` lets you combine independent computations and how `Monad` lets you chain dependent computations. The **`Selective`** type class sits precisely between them, providing a powerful middle ground: **conditional effects with static structure**.

---

## What is it?

A **`Selective`** functor extends `Applicative` with the ability to conditionally apply effects based on the result of a previous computation. Unlike `Monad`, which allows arbitrary dynamic choice of effects, Selective provides a more restricted form of conditional execution where all possible branches must be provided upfront.

This static structure enables:
* **Static analysis**: All possible execution paths are visible at construction time
* **Optimisation**: Compilers and runtimes can analyse and potentially parallelise branches
* **Conditional effects**: Execute operations only when needed, without full monadic power
* **Type-safe branching**: All branches must produce the same result type

The interface for `Selective` in `hkj-api` extends `Applicative`:

```java
@NullMarked
public interface Selective<F> extends Applicative<F> {
  // Core operation
  <A, B> Kind<F, B> select(Kind<F, Choice<A, B>> fab, Kind<F, Function<A, B>> ff);

  // Derived operations
  default <A> Kind<F, Unit> whenS(Kind<F, Boolean> fcond, Kind<F, Unit> fa) { ... }
  default <A> Kind<F, A> ifS(Kind<F, Boolean> fcond, Kind<F, A> fthen, Kind<F, A> felse) { ... }
  default <A, B, C> Kind<F, C> branch(Kind<F, Choice<A, B>> fab,
                                       Kind<F, Function<A, C>> fl,
                                       Kind<F, Function<B, C>> fr) { ... }
  // ... and more
}
```

---

## The Core Operation: `select`

The fundamental operation is `select`, which takes a `Choice<A, B>` (similar to `Either`) and a function:

* If the choice is `Left(a)`, the function is applied to `a` to produce `B`
* If the choice is `Right(b)`, the function is ignored and `b` is returned

**Example: Conditional Validation**

```java
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.maybe.MaybeSelective;
import org.higherkindedj.hkt.maybe.Maybe;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

Selective<MaybeKind.Witness> selective = MaybeSelective.INSTANCE;

// A value that might need validation
Kind<MaybeKind.Witness, Choice<String, Integer>> input =
    MAYBE.widen(Maybe.just(Selective.left("42"))); // Left: needs parsing

// Parser function (only applied if Choice is Left)
Kind<MaybeKind.Witness, Function<String, Integer>> parser =
    MAYBE.widen(Maybe.just(s -> Integer.parseInt(s)));

Kind<MaybeKind.Witness, Integer> result = selective.select(input, parser);
// Result: Just(42)

// If input was already Right(42), parser would not be used
Kind<MaybeKind.Witness, Choice<String, Integer>> alreadyParsed =
    MAYBE.widen(Maybe.just(Selective.right(42)));

Kind<MaybeKind.Witness, Integer> result2 = selective.select(alreadyParsed, parser);
// Result: Just(42) - parser was not applied
```

---

## Conditional Effect Execution: `whenS`

The `whenS` operation is the primary way to conditionally execute effects. It takes an effectful boolean condition and an effect that returns `Unit`, executing the effect only if the condition is `true`.

**Example: Conditional Logging**

```java
import org.higherkindedj.hkt.io.IOSelective;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.Unit;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_KIND;

Selective<IOKind.Witness> selective = IOSelective.INSTANCE;

// Check if debug mode is enabled
Kind<IOKind.Witness, Boolean> debugEnabled =
    IO_KIND.widen(IO.delay(() -> Config.isDebugMode()));

// The logging effect (only executed if debug is enabled)
Kind<IOKind.Witness, Unit> logEffect =
    IO_KIND.widen(IO.fromRunnable(() -> log.debug("Debug information")));

// Conditional execution
Kind<IOKind.Witness, Unit> maybeLog = selective.whenS(debugEnabled, logEffect);

// Run the IO
IO.narrow(maybeLog).unsafeRunSync();
// Only logs if Config.isDebugMode() returns true
```

### `whenS_`: Discarding Results

When you have an effect that returns a value but you want to treat it as a `Unit`-returning operation, use `whenS_`:

```java
// Database write returns row count, but we don't care about the value
Kind<IOKind.Witness, Integer> writeEffect =
    IO_KIND.widen(IO.delay(() -> database.write(data)));

Kind<IOKind.Witness, Boolean> shouldPersist =
    IO_KIND.widen(IO.delay(() -> config.shouldPersist()));

// Discard the Integer result, treat as Unit
Kind<IOKind.Witness, Unit> maybeWrite = selective.whenS_(shouldPersist, writeEffect);
```

---

## Branching: `ifS`

The `ifS` operation provides if-then-else semantics for selective functors. Unlike monadic branching, both branches must be provided upfront.

**Example: Configuration-Based Behaviour**

```java
import org.higherkindedj.hkt.either.EitherSelective;
import org.higherkindedj.hkt.either.Either;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

Selective<EitherKind.Witness<String>> selective = EitherSelective.instance();

// Check environment
Kind<EitherKind.Witness<String>, Boolean> isProd =
    EITHER.widen(Either.right(System.getenv("ENV").equals("production")));

// Production configuration
Kind<EitherKind.Witness<String>, Config> prodConfig =
    EITHER.widen(Either.right(new Config("prod", 443, true)));

// Development configuration
Kind<EitherKind.Witness<String>, Config> devConfig =
    EITHER.widen(Either.right(new Config("dev", 8080, false)));

// Select configuration based on environment
Kind<EitherKind.Witness<String>, Config> config =
    selective.ifS(isProd, prodConfig, devConfig);

// Result: Either.right(Config("prod", 443, true)) if ENV=production
//         Either.right(Config("dev", 8080, false)) otherwise
```

---

## Trying Alternatives: `orElse`

The `orElse` operation tries multiple alternatives in sequence, returning the first successful result.

**Example: Fallback Configuration Sources**

```java
import java.util.List;

Selective<OptionalKind.Witness> selective = OptionalSelective.INSTANCE;

// Try multiple configuration sources
List<Kind<OptionalKind.Witness, Choice<String, Config>>> sources = List.of(
    // Try environment variables
    OPTIONAL.widen(tryEnvConfig()),
    // Try config file
    OPTIONAL.widen(tryFileConfig()),
    // Try default config
    OPTIONAL.widen(Optional.of(Selective.right(defaultConfig())))
);

Kind<OptionalKind.Witness, Choice<String, Config>> result =
    selective.orElse(sources);

// Returns the first successful Config, or the last error
```

---

## Selective vs Applicative vs Monad

Understanding the differences helps you choose the right abstraction:

| Feature | Applicative | Selective | Monad |
|---------|------------|-----------|-------|
| **Combine independent effects** | ✅ | ✅ | ✅ |
| **Conditional effects** | ❌ | ✅ | ✅ |
| **Dynamic effect choice** | ❌ | ❌ | ✅ |
| **Static structure** | ✅ | ✅ | ❌ |
| **Error accumulation** | ✅ (with Validated) | ✅ (with Validated) | ❌ |
| **Analyse all branches** | ✅ | ✅ | ❌ |

**When to use Selective:**
* You need conditional effects but want static analysis
* All branches should be known at construction time
* You want optimisation opportunities from visible alternatives
* You need something more powerful than Applicative but less than Monad

**Example: Static vs Dynamic Choice**

```java
// Selective: Both branches visible at construction
Kind<F, A> selectiveChoice = selective.ifS(
    condition,
    branchA,  // Known upfront
    branchB   // Known upfront
);

// Monad: Second computation depends on first result (dynamic)
Kind<M, B> monadicChoice = monad.flatMap(a -> {
    if (a > 10) return computeX(a);  // Not known until 'a' is available
    else return computeY(a);
}, source);
```

---

## Multi-Way Branching: `branch`

For more complex branching, `branch` handles both sides of a `Choice` with different handlers:

```java
Kind<F, Choice<ErrorA, ErrorB>> input = ...; // Could be either error type

Kind<F, Function<ErrorA, String>> handleA =
    selective.of(a -> "Error type A: " + a);

Kind<F, Function<ErrorB, String>> handleB =
    selective.of(b -> "Error type B: " + b);

Kind<F, String> result = selective.branch(input, handleA, handleB);
// Applies the appropriate handler based on which error type
```

## Chaining Conditional Functions: `apS`

For chaining multiple conditional functions, `apS` applies a list of functions sequentially to a value, propagating either the successful result or the first error. It's useful for building a pipeline of validation or transformation steps.

**Example: Multi-Step Validation**

```java
Kind<F, Choice<Error, Data>> initialData = ...;

List<Kind<F, Function<Data, Choice<Error, Data>>>> validationSteps = List.of(
    validateStep1,
    validateStep2,
    validateStep3
);

// Applies each validation step in order, short-circuiting on the first error.
Kind<F, Choice<Error, Data>> finalResult = selective.apS(initialData, validationSteps);
```

---

---

## Real-World Example: Feature Flags

**Scenario:** Execute analytics tracking only if the feature flag is enabled.

```java
import org.higherkindedj.hkt.io.IOSelective;
import org.higherkindedj.hkt.io.IO;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_KIND;

public class AnalyticsService {
    private final Selective<IOKind.Witness> selective = IOSelective.INSTANCE;

    public Kind<IOKind.Witness, Unit> trackEvent(String eventName, User user) {
        // Check feature flag (effectful)
        Kind<IOKind.Witness, Boolean> flagEnabled =
            IO_KIND.widen(IO.delay(() -> featureFlags.isEnabled("analytics")));

        // The tracking effect (potentially expensive)
        Kind<IOKind.Witness, Unit> trackingEffect =
            IO_KIND.widen(IO.fromRunnable(() -> {
                analytics.track(eventName, user.id(), user.properties());
                log.info("Tracked event: " + eventName);
            }));

        // Only track if flag is enabled
        return selective.whenS(flagEnabled, trackingEffect);
    }
}

// Usage
AnalyticsService analytics = new AnalyticsService();
Kind<IOKind.Witness, Unit> trackingOperation =
    analytics.trackEvent("user_signup", currentUser);

// Execute the IO
IO.narrow(trackingOperation).unsafeRunSync();
// Only sends analytics if feature flag is enabled
```

---

## Implementations

Higher-Kinded-J provides Selective instances for:

* **`Either<E, *>`** - `EitherSelective`
* **`Maybe`** - `MaybeSelective`
* **`Optional`** - `OptionalSelective`
* **`List`** - `ListSelective`
* **`IO`** - `IOSelective`
* **`Reader<R, *>`** - `ReaderSelective`
* **`Id`** - `IdSelective`
* **`Validated<E, *>`** - `ValidatedSelective`

---

## Key Takeaways

* **Selective sits between Applicative and Monad**, providing conditional effects with static structure
* **`select` is the core operation**, conditionally applying a function based on a `Choice`
* **`whenS` enables conditional effect execution**, perfect for feature flags and debug logging
* **`ifS` provides if-then-else semantics** with both branches visible upfront
* **All branches are known at construction time**, enabling static analysis and optimisation
* **Use Selective when you need conditional effects but want to avoid full monadic power**

---

**Previous:** [MonadZero](monad_zero.md)
**Next:** [Profunctor](profunctor.md)
