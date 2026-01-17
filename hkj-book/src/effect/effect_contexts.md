# Effect Contexts: Taming Transformer Power

> *"Evrything has a shape and so does the nite only you cant see the shape of nite nor you cant think it."*
>
> — Russell Hoban, *Riddley Walker*

Hoban's narrator speaks of invisible shapes—structures that exist whether or not we perceive them. Monad transformers are like this. `EitherT<IOKind.Witness, ApiError, User>` has a definite shape: it's a computation that defers execution, might fail with a typed error, and produces a user when successful. The shape is there. But the syntax makes it hard to see, hard to think.

Effect Contexts give that shape a face you can recognise.

They're a middle layer between the simple Path types you've already learned and the raw transformers lurking beneath. When `EitherPath` isn't quite enough—when you need error handling *and* deferred execution, or optional values *and* IO effects—Effect Contexts provide a user-friendly API that hides the transformer machinery while preserving its power.

~~~admonish info title="What You'll Learn"
- Why monad transformers are powerful but syntactically demanding
- The three-layer architecture: Paths, Effect Contexts, Raw Transformers
- How Effect Contexts wrap transformers with intuitive APIs
- The six Effect Context types and when to use each
- Escape hatches for when you need the raw transformer
~~~

~~~admonish example title="See Example Code"
- [EffectContextExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/EffectContextExample.java) - Demonstrates all five Effect Contexts
~~~

---

## The Problem: Transformer Syntax

Consider a typical API call. It might fail. It uses IO. You want typed errors. The raw transformer approach looks like this:

```java
// Raw transformer: correct but noisy
EitherTMonad<IOKind.Witness, ApiError> monad = new EitherTMonad<>(IOMonad.INSTANCE);

Kind<EitherTKind.Witness<IOKind.Witness, ApiError>, User> userKind =
    EitherT.fromKind(IO_OP.widen(IO.delay(() -> {
        try {
            return Either.right(userService.fetch(userId));
        } catch (Exception e) {
            return Either.left(new ApiError(e.getMessage()));
        }
    })));

Kind<EitherTKind.Witness<IOKind.Witness, ApiError>, Profile> profileKind =
    monad.flatMap(user ->
        EitherT.fromKind(IO_OP.widen(IO.delay(() -> {
            try {
                return Either.right(profileService.fetch(user.profileId()));
            } catch (Exception e) {
                return Either.left(new ApiError(e.getMessage()));
            }
        }))),
        userKind);
```

The business logic—fetch a user, then fetch their profile—is drowning in ceremony. You're manually constructing witnesses, wrapping IO, handling `Kind` types, threading monads. The *what* disappears into the *how*.

---

## The Solution: Effect Contexts

The same logic with `ErrorContext`:

```java
// Effect Context: same power, readable syntax
ErrorContext<IOKind.Witness, ApiError, Profile> profile = ErrorContext
    .<ApiError, User>io(
        () -> userService.fetch(userId),
        ApiError::fromException)
    .via(user -> ErrorContext.io(
        () -> profileService.fetch(user.profileId()),
        ApiError::fromException));

Either<ApiError, Profile> result = profile.runIO().unsafeRun();
```

The transformer is still there—`ErrorContext` wraps `EitherT`—but the API speaks in terms you recognise: `io()` for effectful computation, `via()` for chaining, `runIO()` to execute. The shape of the computation emerges from the noise.

---

## The Three-Layer Architecture

Higher-Kinded-J provides three ways to work with combined effects, each serving different needs:

```
┌─────────────────────────────────────────────────────────────────┐
│  LAYER 1: Effect Path API                                       │
│  ─────────────────────────                                      │
│                                                                 │
│  EitherPath, MaybePath, TryPath, IOPath, ValidationPath...      │
│                                                                 │
│  ✓ Simple, fluent API                                           │
│  ✓ Single effect per path                                       │
│  ✓ Best for: Most application code                              │
│                                                                 │
│  Limitation: Can't combine effects (e.g., IO + typed errors)    │
├─────────────────────────────────────────────────────────────────┤
│  LAYER 2: Effect Contexts                       ← NEW           │
│  ────────────────────────                                       │
│                                                                 │
│  ErrorContext, OptionalContext, JavaOptionalContext,            │
│  ConfigContext, MutableContext, VTaskContext                    │
│                                                                 │
│  ✓ User-friendly transformer wrappers                           │
│  ✓ Hides HKT complexity (no Kind<F, A> in your code)            │
│  ✓ Best for: Combined effects without ceremony                  │
│                                                                 │
│  Limitation: Fixed to common patterns                           │
├─────────────────────────────────────────────────────────────────┤
│  LAYER 3: Raw Transformers                                      │
│  ─────────────────────────                                      │
│                                                                 │
│  EitherT, MaybeT, OptionalT, ReaderT, StateT                    │
│                                                                 │
│  ✓ Full transformer power                                       │
│  ✓ Arbitrary effect stacking                                    │
│  ✓ Best for: Library authors, unusual combinations              │
│                                                                 │
│  Limitation: Requires HKT fluency                               │
└─────────────────────────────────────────────────────────────────┘
```

Most code lives in Layer 1. When you need combined effects, Layer 2 handles the common cases cleanly. Layer 3 waits for the rare occasions when nothing else suffices.

---

## Available Effect Contexts

Each Effect Context wraps a specific transformer, exposing its capabilities through a streamlined API:

| Context | Wraps | Primary Use Case |
|---------|-------|------------------|
| [`ErrorContext<F, E, A>`](effect_contexts_error.md) | `EitherT<F, E, A>` | IO with typed error handling |
| [`OptionalContext<F, A>`](effect_contexts_optional.md) | `MaybeT<F, A>` | IO with optional results (using `Maybe`) |
| [`JavaOptionalContext<F, A>`](effect_contexts_optional.md) | `OptionalT<F, A>` | IO with optional results (using `java.util.Optional`) |
| [`ConfigContext<F, R, A>`](effect_contexts_config.md) | `ReaderT<F, R, A>` | Dependency injection in effectful computation |
| [`MutableContext<F, S, A>`](effect_contexts_mutable.md) | `StateT<S, F, A>` | Stateful computation with IO |
| [`VTaskContext<A>`](path_vtask.md) | `VTaskPath<A>` | Virtual thread concurrency with simple API |

---

## Choosing the Right Context

```
                    What's your primary concern?
                              │
           ┌──────────────────┼──────────────────┐
           │                  │                  │
    Typed Errors       Optional Values     Environment/State
           │                  │                  │
           ▼                  │          ┌──────┴──────┐
     ErrorContext             │          │             │
                              │     ConfigContext  MutableContext
                    ┌─────────┴─────────┐
                    │                   │
             Using Maybe?         Using Optional?
                    │                   │
                    ▼                   ▼
            OptionalContext    JavaOptionalContext
```

**ErrorContext** when you need:
- Typed errors across IO operations
- Exception-catching with custom error types
- Error recovery and transformation

**OptionalContext / JavaOptionalContext** when you need:
- Optional values from IO operations
- Fallback chains for missing data
- The choice between them is simply which optional type you prefer

**ConfigContext** when you need:
- Dependency injection without frameworks
- Environment/configuration threading
- Local configuration overrides

**MutableContext** when you need:
- State threading through IO operations
- Accumulators, counters, or state machines
- Both the final value and final state

**VTaskContext** when you need:
- Virtual thread-based concurrency
- Simple blocking code that scales to many concurrent tasks
- Timeout and error recovery without transformer complexity

---

## Common Patterns

### The Error-Handling Pipeline

```java
ErrorContext<IOKind.Witness, ApiError, Order> orderPipeline =
    ErrorContext.<ApiError, User>io(
        () -> userService.fetch(userId),
        ApiError::fromException)
    .via(user -> ErrorContext.io(
        () -> cartService.getCart(user.id()),
        ApiError::fromException))
    .via(cart -> ErrorContext.io(
        () -> orderService.createOrder(cart),
        ApiError::fromException))
    .recover(error -> Order.failed(error.message()));
```

### The Optional Lookup Chain

```java
OptionalContext<IOKind.Witness, Config> config =
    OptionalContext.<Config>io(() -> cache.get("config"))
        .orElse(() -> OptionalContext.io(() -> database.loadConfig()))
        .orElse(() -> OptionalContext.some(Config.defaults()));
```

### Dependency Injection

```java
ConfigContext<IOKind.Witness, ServiceConfig, Report> report =
    ConfigContext.io(config ->
        reportService.generate(config.reportFormat()));

Report result = report.runWithSync(new ServiceConfig("PDF", 30));
```

### Stateful Computation

```java
MutableContext<IOKind.Witness, Counter, String> workflow =
    MutableContext.<Counter>get()
        .map(c -> "Started at: " + c.value())
        .flatMap(msg -> MutableContext.<Counter, Unit>modify(Counter::increment)
            .map(u -> msg));

StateTuple<Counter, String> result = workflow.runWith(new Counter(0)).unsafeRun();
// result.state().value() == 1
// result.value() == "Started at: 0"
```

### Virtual Thread Concurrency

```java
Try<Profile> result = VTaskContext
    .<User>of(() -> userService.fetch(userId))
    .via(user -> VTaskContext.of(() -> profileService.fetch(user.profileId())))
    .timeout(Duration.ofSeconds(5))
    .recover(err -> Profile.defaultProfile())
    .run();

// Or with fallback chains
Try<Config> config = VTaskContext
    .<Config>of(() -> loadFromPrimary())
    .recoverWith(err -> VTaskContext.of(() -> loadFromSecondary()))
    .recover(err -> Config.defaults())
    .run();
```

---

## Escape Hatches

Every Effect Context provides access to its underlying transformer via an escape hatch method. When you need capabilities beyond what the Context API exposes, you can drop to Layer 3:

```java
ErrorContext<IOKind.Witness, ApiError, User> ctx = ErrorContext.success(user);

// Escape to raw transformer
EitherT<IOKind.Witness, ApiError, User> transformer = ctx.toEitherT();

// Now you have full transformer capabilities
// ... perform advanced operations ...
```

| Context | Escape Hatch Method | Returns |
|---------|---------------------|---------|
| `ErrorContext` | `toEitherT()` | `EitherT<F, E, A>` |
| `OptionalContext` | `toMaybeT()` | `MaybeT<F, A>` |
| `JavaOptionalContext` | `toOptionalT()` | `OptionalT<F, A>` |
| `ConfigContext` | `toReaderT()` | `ReaderT<F, R, A>` |
| `MutableContext` | `toStateT()` | `StateT<S, F, A>` |
| `VTaskContext` | `toPath()` / `toVTask()` | `VTaskPath<A>` / `VTask<A>` |

Use escape hatches sparingly. They're for genuine edge cases, not everyday operations.

---

## Summary

| Layer | Types | Best For |
|-------|-------|----------|
| **Layer 1** | Path types (`EitherPath`, `IOPath`, etc.) | Single-effect scenarios |
| **Layer 2** | Effect Contexts | Combined effects with clean syntax |
| **Layer 3** | Raw transformers (`EitherT`, `StateT`, etc.) | Maximum flexibility |

Effect Contexts occupy the middle ground: more power than simple Paths, more clarity than raw transformers. They make the invisible shapes visible, the unthinkable thinkable.

~~~admonish tip title="See Also"
- [EitherT](../transformers/eithert_transformer.md) - The transformer behind ErrorContext
- [MaybeT](../transformers/maybet_transformer.md) - The transformer behind OptionalContext
- [ReaderT](../transformers/readert_transformer.md) - The transformer behind ConfigContext
- [StateT](../transformers/statet_transformer.md) - The transformer behind MutableContext
- [VTaskPath](path_vtask.md) - The Effect Path behind VTaskContext
~~~

---

**Previous:** [Advanced Effects](advanced_effects.md)
**Next:** [ErrorContext](effect_contexts_error.md)
