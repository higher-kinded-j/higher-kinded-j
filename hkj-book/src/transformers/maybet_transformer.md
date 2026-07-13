# The MaybeT Transformer:
## _Functional Optionality Across Monads_

> *"Some things are present, others not."*
>
> — Aristotle, *Categories*

`MaybeT` is the FP-native cousin of `OptionalT`. Same idea, same composition story, slightly different host type, and a slightly cleaner pairing with the rest of Higher-Kinded-J.

~~~admonish info title="What You'll Learn"
- How to combine `Maybe`'s optionality with other monadic effects
- Building workflows where operations might produce `Nothing` within async contexts
- Understanding the difference between `MaybeT` and `OptionalT`
- Using `For` comprehensions to keep witness types localised
- Using `just`, `nothing`, `fromMaybe`, `liftF`, and `fromKind` to construct `MaybeT` values
- When to use the [`MaybePath`](../effect/path_maybe.md) Path type instead of raw `MaybeT`
~~~

~~~admonish example title="See Example Code"
[MaybeTExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/maybe_t/MaybeTExample.java)
~~~

~~~admonish note title="Path First, Stack Later"
For most use cases, [`MaybePath<A>`](../effect/path_maybe.md) is the better starting point. It wraps `MaybeT` in a fluent API, hides the witness types, and removes the `Kind` widening calls.

Reach for raw `MaybeT` only when you need to combine absence with a specific outer monad (`CompletableFuture`, `IO`, `VTask`, custom) or when you are writing polymorphic library code that names `MonadError<F, Unit>`.
~~~

---

## The Problem: Nested Async Optionality

When an async lookup returns `Maybe` rather than `Optional`, you face the same nesting problem:

```java
CompletableFuture<Maybe<UserPreferences>> getPreferences(String userId) {
    return fetchUserAsync(userId).thenCompose(maybeUser ->
        maybeUser.fold(
            () -> CompletableFuture.completedFuture(Maybe.nothing()),
            user -> fetchPreferencesAsync(user.id()).thenCompose(maybePrefs ->
                maybePrefs.fold(
                    () -> CompletableFuture.completedFuture(Maybe.nothing()),
                    prefs -> CompletableFuture.completedFuture(Maybe.just(prefs))
                ))
        ));
}
```

Each step requires folding over the `Maybe`, providing a `Nothing` fallback wrapped in a completed future, and nesting deeper. The pattern is identical to the `Optional` case but uses `Maybe`'s API.

---

## The Solution

### With the Effect Path API

```java
MaybePath<UserPreferences> getPreferences(String userId) {
    return Path.maybe(fetchUserAsync(userId))
        .via(user -> Path.maybe(fetchPreferencesAsync(user.id())));
}
```

### With raw `MaybeT`

```java
var futureMonad = Instances.monadError(completableFuture());
var maybeTMonad = Instances.maybeT(futureMonad);

var prefs = For.from(maybeTMonad, MaybeT.fromKind(fetchUserAsync(userId)))
    .from(user -> MaybeT.fromKind(fetchPreferencesAsync(user.id())))
    .yield((user, prefs) -> prefs);
```

If `fetchUserAsync` returns `Nothing`, the preferences lookup is skipped entirely. No manual folding, no fallback wrapping.

---

## The Railway View

<pre class="hkj-railway-diagram">
    <span style="color:#4CAF50"><b>Just</b>     ═══●═══════════════●═══════════════════▶  UserPreferences</span>
    <span style="color:#4CAF50">          fetchUser       fetchPreferences</span>
    <span style="color:#4CAF50">          (flatMap)        (flatMap)</span>
               ╲                ╲
                ╲                ╲  Nothing: skip remaining steps
                 ╲                ╲
    <span style="color:#F44336"><b>Nothing</b>  ────●────────────────●──────────────────▶  Nothing</span>
    <span style="color:#F44336">         user absent     prefs absent</span>
                                    │
                               <span style="color:#4CAF50">handleErrorWith</span>    provide defaults
                                    │
    <span style="color:#4CAF50">                                ●═══▶  default UserPreferences</span>
</pre>

Each `flatMap` runs inside the outer monad `F`. If the inner `Maybe` is `Nothing`, subsequent steps are skipped. `handleErrorWith` can provide a fallback value when the chain yields nothing.

---

## How MaybeT Works

`MaybeT<F, A>` wraps a computation yielding `Kind<F, Maybe<A>>`. It represents an effectful computation in `F` that may produce `Just(value)` or `Nothing`.

<pre class="hkj-ascii-diagram">
    ┌──────────────────────────────────────────────────────────┐
    │  MaybeT&lt;CompletableFutureKind.Witness, Value&gt;            │
    │                                                          │
    │  ┌─── CompletableFuture ──────────────────────────────┐  │
    │  │                                                    │  │
    │  │  ┌─── Maybe ───────────────────────────────────┐   │  │
    │  │  │                                             │   │  │
    │  │  │   <span style="color:#F44336">Nothing</span>            │   <span style="color:#4CAF50">Just(value)</span>        │   │  │
    │  │  │                      │                      │   │  │
    │  │  └─────────────────────────────────────────────┘   │  │
    │  │                                                    │  │
    │  └────────────────────────────────────────────────────┘  │
    │                                                          │
    │  flatMap ──▶ sequences F, then routes on Maybe           │
    │  map ──────▶ transforms <span style="color:#4CAF50">Just(value)</span> only                 │
    │  raiseError(Unit) ──▶ creates <span style="color:#F44336">Nothing</span> in F               │
    │  handleErrorWith ──▶ recovers from <span style="color:#F44336">Nothing</span>               │
    └──────────────────────────────────────────────────────────┘
</pre>

* **`F`**: The witness type of the **outer monad** (e.g. `CompletableFutureKind.Witness`, `ListKind.Witness`).
* **`A`**: The type of the value potentially held by the inner `Maybe`.

```java
public record MaybeT<F, A>(@NonNull Kind<F, Maybe<A>> value) {
/* ... static factories ... */ }
```

---

## Setting Up MaybeTMonad

The `MaybeTMonad<F>` class implements `MonadError<MaybeTKind.Witness<F>, Unit>`. Like `OptionalTMonad`, the error type is `Unit`, signifying that `Nothing` carries no information beyond its occurrence.

```java
var futureMonad = Instances.monadError(completableFuture());
var maybeTMonad = Instances.maybeT(futureMonad);
```

~~~admonish note title="Working with Kind"
**Witness Type:** `MaybeTKind<F, A>` extends `Kind<MaybeTKind.Witness<F>, A>`. The outer monad `F` is fixed; `A` is the variable value type.

**KindHelper:** `MaybeTKindHelper` provides `MAYBE_T.widen` and `MAYBE_T.narrow` for safe conversion. With `For` comprehensions you rarely need them; they appear at the boundaries when interoperating with raw `flatMap` chains.

```java
Kind<MaybeTKind.Witness<F>, A> kind = MAYBE_T.widen(maybeT);
MaybeT<F, A> concrete                = MAYBE_T.narrow(kind);
```
~~~

---

## Key Operations

| Operation | Behaviour |
|-----------|-----------|
| `maybeTMonad.of(value)`                          | Lifts a nullable value as `F<Maybe.fromNullable(value)>` |
| `maybeTMonad.map(f, kind)`                       | Applies `A -> B` to the `Just` value; `null` propagates as `Nothing` |
| `maybeTMonad.flatMap(f, kind)`                   | Sequences operations; `Nothing` short-circuits the rest |
| `maybeTMonad.raiseError(Unit.INSTANCE)`          | Creates `F<Nothing>` |
| `maybeTMonad.handleErrorWith(kind, handler)`     | Recovers from `Nothing` by applying `handler` |

---

## Creating MaybeT Instances

```java
var optMonad = Instances.monadError(optional());

// 1. From a non-null value: F<Just(value)>
var mtJust = MaybeT.just(optMonad, "Hello");

// 2. Nothing state: F<Nothing>
var mtNothing = MaybeT.<OptionalKind.Witness, String>nothing(optMonad);

// 3. From a plain Maybe: F<Maybe(input)>
var mtFromMaybe = MaybeT.fromMaybe(optMonad, Maybe.just(123));

// 4. Lifting F<A> into MaybeT (using fromNullable)
Kind<OptionalKind.Witness, String> outerOptional = OPTIONAL.widen(Optional.of("World"));
var mtLiftF = MaybeT.liftF(optMonad, outerOptional);

// 5. Wrapping an existing F<Maybe<A>>
Kind<OptionalKind.Witness, Maybe<String>> nestedKind =
    OPTIONAL.widen(Optional.of(Maybe.just("Present")));
var mtFromKind = MaybeT.fromKind(nestedKind);
```

`mtJust.value()` returns the underlying `Kind<F, Maybe<A>>`, which you narrow back to the concrete outer monad form when you need the result.

---

## Real-World Example: Async Resource Fetching

~~~admonish example title="Asynchronous Optional Resource Fetching"

**The problem:** fetch a user asynchronously, and if found, fetch their preferences. Each step might return `Nothing`. Compose without manual `Maybe.fold` at every step.

**The solution:**

```java
var futureMonad = Instances.monadError(completableFuture());
var maybeTMonad = Instances.maybeT(futureMonad);

// Service stubs return Future<Maybe<T>>
Kind<CompletableFutureKind.Witness, Maybe<User>> fetchUserAsync(String userId) {
    return FUTURE.widen(CompletableFuture.supplyAsync(() ->
        "user123".equals(userId) ? Maybe.just(new User(userId, "Alice"))
                                 : Maybe.nothing()));
}

// Workflow: user -> preferences
var preferences = For.from(maybeTMonad, MaybeT.fromKind(fetchUserAsync(userId)))
    .from(user -> MaybeT.fromKind(fetchPreferencesAsync(user.id())))
    .yield((user, prefs) -> prefs);
```

**Why this works:** the `from` lambda only executes if the user was found (`Just`). If `fetchUserAsync` returns `Nothing`, the entire chain short-circuits to `Future<Nothing>`.
~~~

---

## Transforming the Outer Monad with `mapT`

Sometimes you need to change the *outer monad* of a `MaybeT` without touching the inner `Maybe`. Perhaps you have an `IO`-based pipeline but want to switch to a `Task` for structured concurrency, or you want to collapse two layers of optionality by moving from `Optional<Maybe<A>>` to `Id<Maybe<A>>`.

`mapT` applies a function to the wrapped `Kind<F, Maybe<A>>` and produces a new `MaybeT<G, A>`:

```
  MaybeT< F , A >  ── mapT(f) ──>  MaybeT< G , A >
       │                                  │
  ┌────┴────┐                        ┌────┴────┐
  │    F    │   f: F[...] -> G[...]  │    G    │
  │ ┌─────┐ │        ====>           │ ┌─────┐ │
  │ │Maybe│ │  inner Maybe sealed    │ │Maybe│ │
  │ │  A  │ │                        │ │  A  │ │
  │ └─────┘ │                        │ └─────┘ │
  └─────────┘                        └─────────┘
```

```java
MaybeT<OptionalKind.Witness, String> optMt = MaybeT.just(optMonad, "Hello");

var idMt = optMt.mapT(optKind -> {
  Optional<Maybe<String>> opt = OPTIONAL.narrow(optKind);
  return ID.widen(Id.of(opt.orElse(Maybe.nothing())));
});
```

~~~admonish note title="mapT vs map"
`map` transforms the *value* inside the `Maybe` (the `A` in `Just(A)`).
`mapT` transforms the *outer monad* wrapping the `Maybe`, the `F` in `F<Maybe<A>>`.
They operate at different levels of the transformer stack.
~~~

---

## MaybeT vs OptionalT: When to Use Which?

Both `MaybeT` and `OptionalT` combine optionality with other effects. The functionality is equivalent; the choice depends on your codebase:

| Aspect | MaybeT | OptionalT |
|--------|--------|-----------|
| Inner type | `Maybe<A>` | `java.util.Optional<A>` |
| Best for | Higher-Kinded-J ecosystem code | Integrating with existing Java code |
| FP-native | Yes (designed for composition) | Wraps Java's standard library |
| Serialisation | No warnings | Identity-sensitive operation warnings |
| Team familiarity | Requires learning `Maybe` | Uses familiar `Optional` API |

### Use **MaybeT** when:
- You're working within the Higher-Kinded-J ecosystem and want consistency with `Maybe`
- You want a type explicitly designed for functional composition
- You want to avoid Java's `Optional` and its quirks (serialisation warnings, identity-sensitive operations)

### Use **OptionalT** when:
- You're integrating with existing Java code that uses `java.util.Optional`
- Your team is more comfortable with standard Java types
- You're wrapping external libraries that return `Optional`

In practice, choose whichever matches your existing codebase. Both offer equivalent functionality through their `MonadError` instances.

---

~~~admonish warning title="Common Mistakes"
- **Confusing `Maybe.nothing()` with null:** `MaybeT.of(null)` will use `Maybe.fromNullable(null)`, which produces `Nothing`. Be explicit about intent; use `MaybeT.nothing(monad)` when you mean absence.
- **Using `MaybeT` when you need error information:** `Nothing` carries no reason for the absence. If you need to know *why* a value is missing, use [`EitherT`](eithert_transformer.md) with a descriptive error type instead.
- **Reaching for the transformer when `MaybePath` would do:** if your outer monad is one Path already wraps, `MaybePath` is shorter, has less ceremony, and reads more naturally.
~~~

---

~~~admonish tip title="See Also"
- [MaybePath](../effect/path_maybe.md) - The Path-API equivalent, recommended for most use cases
- [Stack Archetypes](archetypes.md) - The Lookup Stack archetype maps to `MaybeT`/`MaybePath`
- [Migration Cookbook](migration_cookbook.md) - Side-by-side translations
- [Monad Transformers](transformers.md) - General concept and choosing the right transformer
- [OptionalT](optionalt_transformer.md) - Equivalent functionality for `java.util.Optional`
- [EitherT](eithert_transformer.md) - When you need typed errors, not just absence
- [Maybe Monad](../monads/maybe_monad.md) - The underlying Maybe type
~~~

---

~~~admonish tip title="Further Reading"
- [Null Handling Patterns in Modern Java](https://www.baeldung.com/java-avoid-null-check) - Comprehensive guide to null safety (15 min read)
~~~

~~~admonish info title="Hands-On Learning"
The MaybeT exercise lives alongside the OptionalT exercises in [Tutorial 02: Async with Absence](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/transformers/Tutorial02_AsyncWithAbsence.java) (5 exercises, ~25 minutes).
~~~

---

**Previous:** [OptionalT](optionalt_transformer.md)
**Next:** [ReaderT](readert_transformer.md)
