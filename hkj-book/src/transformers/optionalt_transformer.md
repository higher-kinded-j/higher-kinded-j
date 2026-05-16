# The OptionalT Transformer:
## _When Absence Meets Other Effects_

> *"The most beautiful experience we can have is the mysterious."*
>
> -- Albert Einstein

`OptionalT` lets a chain of asynchronous lookups disappear cleanly the moment a value is missing. The mystery of "did this resolve?" stays inside the type and never leaks into your control flow.

~~~admonish info title="What You'll Learn"
- How to integrate Java's `Optional` with other monadic contexts
- Building async workflows where each step might return empty results
- Using `For` comprehensions to compose multi-step lookups without manual fallback wiring
- Using `some`, `none`, `fromOptional`, `liftF`, and `fromKind` to construct `OptionalT` values
- When to use the [`OptionalPath`](../effect/path_optional.md) Path type instead of raw `OptionalT`
~~~

~~~admonish example title="See Example Code"
[OptionalTExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/optional_t/OptionalTExample.java)
~~~

~~~admonish note title="Path First, Stack Later"
For most use cases, [`OptionalPath<A>`](../effect/path_optional.md) (or [`MaybePath<A>`](../effect/path_maybe.md) for the FP-native equivalent) is the better starting point. It wraps `OptionalT` in a fluent API that hides the witness types and `Kind` widening.

Reach for raw `OptionalT` only when you need to combine optionality with a specific outer monad (`CompletableFuture`, `IO`, `VTask`, custom) or when you are writing polymorphic library code that names `MonadError<F, Unit>`.
~~~

---

## The Problem: Nested Async Lookups

Consider fetching a user, then their profile, then their preferences. Each step is async and might return empty:

```java
CompletableFuture<Optional<UserPreferences>> getPreferences(String userId) {
    return fetchUserAsync(userId).thenCompose(optUser ->
        optUser.map(user ->
            fetchProfileAsync(user.id()).thenCompose(optProfile ->
                optProfile.map(profile ->
                    fetchPrefsAsync(profile.userId())
                ).orElse(CompletableFuture.completedFuture(Optional.empty()))
            )
        ).orElse(CompletableFuture.completedFuture(Optional.empty()))
    );
}
```

Each step requires checking the `Optional`, providing a fallback `CompletableFuture.completedFuture(Optional.empty())` for the absent case, and nesting deeper. Three lookups; three layers of `map`/`orElse`. The fallback expression is identical every time.

---

## The Solution

### With the Effect Path API

```java
OptionalPath<UserPreferences> getPreferences(String userId) {
    return Path.optional(fetchUserAsync(userId))
        .via(user    -> Path.optional(fetchProfileAsync(user.id())))
        .via(profile -> Path.optional(fetchPrefsAsync(profile.userId())));
}
```

Use this whenever the outer monad is one Path already wraps.

### With raw `OptionalT`

When you need a specific outer monad, use `OptionalT` with a `For` comprehension:

```java
var futureMonad    = Instances.monadError(completableFuture());
var optionalTMonad = Instances.optionalT(futureMonad);

var prefsLookup = For.from(optionalTMonad, OptionalT.fromKind(fetchUserAsync(userId)))
    .from(user    -> OptionalT.fromKind(fetchProfileAsync(user.id())))
    .from(profile -> OptionalT.fromKind(fetchPrefsAsync(profile.userId())))
    .yield((user, profile, prefs) -> prefs);
```

If any step returns empty, subsequent steps are skipped. No manual `orElse` fallbacks, no repeated `Optional.empty()` wrapping.

---

## The Railway View

<pre class="hkj-railway-diagram">
    <span style="color:#4CAF50"><b>Present</b>  ═══●═══════════●═══════════●═══════════▶  UserPreferences</span>
    <span style="color:#4CAF50">          fetchUser   fetchProfile fetchPrefs</span>
    <span style="color:#4CAF50">          (flatMap)   (flatMap)    (flatMap)</span>
               ╲          ╲             ╲
                ╲          ╲             ╲  empty: skip remaining steps
                 ╲          ╲             ╲
    <span style="color:#F44336"><b>Empty</b>    ────●──────────●─────────────●──────────▶  Optional.empty()</span>
    <span style="color:#F44336">        user absent  profile absent  prefs absent</span>
                                          │
                                     <span style="color:#4CAF50">handleErrorWith</span>    provide defaults
                                          │
    <span style="color:#4CAF50">                                      ●═══▶  default UserPreferences</span>
</pre>

Each `flatMap` runs inside the outer monad `F`. If the inner `Optional` is empty, subsequent steps are skipped. `handleErrorWith` can provide a fallback value when the chain yields nothing.

---

## How OptionalT Works

`OptionalT<F, A>` wraps a computation yielding `Kind<F, Optional<A>>`. It represents an effectful computation in `F` that may or may not produce a value.

<pre class="hkj-ascii-diagram">
    ┌──────────────────────────────────────────────────────────┐
    │  OptionalT&lt;CompletableFutureKind.Witness, Value&gt;         │
    │                                                          │
    │  ┌─── CompletableFuture ──────────────────────────────┐  │
    │  │                                                    │  │
    │  │  ┌─── Optional ────────────────────────────────┐   │  │
    │  │  │                                             │   │  │
    │  │  │   <span style="color:#F44336">empty()</span>            │   <span style="color:#4CAF50">of(value)</span>          │   │  │
    │  │  │                      │                      │   │  │
    │  │  └─────────────────────────────────────────────┘   │  │
    │  │                                                    │  │
    │  └────────────────────────────────────────────────────┘  │
    │                                                          │
    │  flatMap ──▶ sequences F, then routes on Optional        │
    │  map ──────▶ transforms <span style="color:#4CAF50">present value</span> only               │
    │  raiseError(Unit) ──▶ creates <span style="color:#F44336">empty()</span> in F               │
    │  handleErrorWith ──▶ recovers from <span style="color:#F44336">empty</span>                 │
    └──────────────────────────────────────────────────────────┘
</pre>

* **`F`**: The witness type of the **outer monad** (e.g. `CompletableFutureKind.Witness`).
* **`A`**: The type of the value that might be present within the `Optional`.

```java
public record OptionalT<F, A>(@NonNull Kind<F, Optional<A>> value)
    implements OptionalTKind<F, A> { /* ... static factories ... */ }
```

---

## Setting Up OptionalTMonad

The `OptionalTMonad<F>` class implements `MonadError<OptionalTKind.Witness<F>, Unit>`. The error type is `Unit`, signifying that an "error" is the `Optional.empty()` state (absence carries no information beyond its occurrence).

```java
var futureMonad    = Instances.monadError(completableFuture());
var optionalTMonad = Instances.optionalT(futureMonad);
```

~~~admonish note title="Working with Kind"
**Witness Type:** `OptionalTKind<F, A>` extends `Kind<OptionalTKind.Witness<F>, A>`. The outer monad `F` is fixed; `A` is the variable value type.

**KindHelper:** `OptionalTKindHelper` provides `OPTIONAL_T.widen` and `OPTIONAL_T.narrow` for safe conversion between `OptionalT<F, A>` and its `Kind` representation. With `For` comprehensions you rarely need them; they appear at the boundaries when interoperating with raw `flatMap` chains.

```java
Kind<OptionalTKind.Witness<F>, A> kind = OPTIONAL_T.widen(optionalT);
OptionalT<F, A> concrete                = OPTIONAL_T.narrow(kind);
```
~~~

---

## Key Operations

| Operation | Behaviour |
|-----------|-----------|
| `optionalTMonad.of(value)`                          | Lifts a nullable value as `F<Optional.ofNullable(value)>` |
| `optionalTMonad.map(f, kind)`                       | Applies `A -> B` to the present value; `null` results become `empty` |
| `optionalTMonad.flatMap(f, kind)`                   | Sequences operations; empty short-circuits the rest |
| `optionalTMonad.raiseError(Unit.INSTANCE)`          | Creates `F<Optional.empty()>` |
| `optionalTMonad.handleErrorWith(kind, handler)`     | Recovers from empty by applying `handler` |

---

## Creating OptionalT Instances

```java
var futureMonad = Instances.monadError(completableFuture());

// 1. From an existing F<Optional<A>>
Kind<CompletableFutureKind.Witness, Optional<String>> fOptional =
    FUTURE.widen(CompletableFuture.completedFuture(Optional.of("Data")));
var ot1 = OptionalT.fromKind(fOptional);

// 2. From a present value: F<Optional.of(a)>
var ot2 = OptionalT.some(futureMonad, "Data");

// 3. Empty: F<Optional.empty()>
var ot3 = OptionalT.<CompletableFutureKind.Witness, String>none(futureMonad);

// 4. From a plain java.util.Optional: F<Optional<A>>
var ot4 = OptionalT.fromOptional(futureMonad, Optional.of(123));

// 5. Lifting F<A> into OptionalT (null -> empty, non-null -> present)
Kind<CompletableFutureKind.Witness, String> fValue =
    FUTURE.widen(CompletableFuture.completedFuture("Data"));
var ot5 = OptionalT.liftF(futureMonad, fValue);
```

`ot1.value()` returns the underlying `Kind<CompletableFutureKind.Witness, Optional<String>>`, which you narrow back to a concrete `CompletableFuture<Optional<String>>` when you need the result.

---

## Real-World Example: Async Multi-Step Data Retrieval

~~~admonish example title="Asynchronous Multi-Step Data Retrieval"

- [OptionalTExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/optional_t/OptionalTExample.java)

**The problem:** fetch a user, then their profile, then their preferences. Each step is async and might not find data. The chain should short-circuit on the first empty result.

**The solution:**

```java
var futureMonad    = Instances.monadError(completableFuture());
var optionalTMonad = Instances.optionalT(futureMonad);

// Service stubs return Future<Optional<T>>
Kind<CompletableFutureKind.Witness, Optional<User>> fetchUserAsync(String userId) {
    return FUTURE.widen(CompletableFuture.supplyAsync(() ->
        "user1".equals(userId) ? Optional.of(new User(userId, "Alice"))
                               : Optional.empty()));
}

// Workflow: user -> profile -> preferences
var workflow = For.from(optionalTMonad, OptionalT.fromKind(fetchUserAsync(userId)))
    .from(user    -> OptionalT.fromKind(fetchProfileAsync(user.id())))
    .from(profile -> OptionalT.fromKind(fetchPrefsAsync(profile.userId())))
    .yield((user, profile, prefs) -> prefs);
```

**Why this works:** each `from` only executes its lambda if the previous step produced a present value. If `fetchUserAsync` returns empty, neither `fetchProfileAsync` nor `fetchPrefsAsync` is called.
~~~

---

## Providing Defaults with Error Recovery

~~~admonish example title="Recovery with Default Values"

**The problem:** when the preference chain returns empty, you want to provide default preferences rather than propagating the absence.

**The solution:**

```java
Kind<OptionalTKind.Witness<CompletableFutureKind.Witness>, UserPreferences>
    getPrefsWithDefault(String userId) {
  var prefsAttempt = getFullUserPreferences(userId);

  return optionalTMonad.handleErrorWith(
      prefsAttempt,
      (Unit v) -> OptionalT.some(futureMonad, new UserPreferences(userId, "default-light")));
}
```

`OptionalT` already implements the `Kind` interface, so neither the source value nor the handler's result need an explicit `OPTIONAL_T.widen` call. The `handleErrorWith` handler receives `Unit` (since absence carries no information) and returns an `OptionalT` containing the default preferences.
~~~

---

## Transforming the Outer Monad with `mapT`

Sometimes you need to change the *outer monad* of an `OptionalT` without touching the inner `Optional`. Perhaps you have awaited an async result and want to continue in a synchronous context.

`mapT` applies a function to the wrapped `Kind<F, Optional<A>>` and produces a new `OptionalT<G, A>`:

```
  OptionalT< F , A >  ── mapT(f) ──>  OptionalT< G , A >
       │                                     │
  ┌────┴────┐                           ┌────┴────┐
  │    F    │   f: F[...] -> G[...]     │    G    │
  │ ┌─────┐ │         ====>             │ ┌─────┐ │
  │ │Opt  │ │  inner Optional sealed    │ │Opt  │ │
  │ │  A  │ │                           │ │  A  │ │
  │ └─────┘ │                           │ └─────┘ │
  └─────────┘                           └─────────┘
```

```java
OptionalT<CompletableFutureKind.Witness, String> asyncOt = ...;

var syncOt = asyncOt.mapT(futureKind -> {
  Optional<String> awaited = FUTURE.narrow(futureKind).join();
  return OPTIONAL.widen(Optional.of(awaited));
});
```

~~~admonish note title="mapT vs map"
`map` transforms the *value* inside the `Optional` (the `A` in `Optional.of(A)`).
`mapT` transforms the *outer monad* wrapping the `Optional`, the `F` in `F<Optional<A>>`.
They operate at different levels of the transformer stack.
~~~

---

~~~admonish warning title="Common Mistakes"
- **Null vs. empty confusion:** `OptionalT.liftF` treats a `null` value inside `F<A>` as `Optional.empty()`. If you want to explicitly signal absence, use `OptionalT.none` rather than relying on null propagation.
- **Unit as error type:** when using `handleErrorWith`, the handler function receives `Unit.INSTANCE`, not a descriptive error. If you need typed error information, consider [`EitherT`](eithert_transformer.md) instead.
- **Reaching for the transformer when `OptionalPath` would do:** if your outer monad is one Path already wraps, `OptionalPath` is shorter, has less ceremony, and reads more naturally.
~~~

---

~~~admonish tip title="See Also"
- [OptionalPath](../effect/path_optional.md) - The Path-API equivalent, recommended for most use cases
- [Stack Archetypes](archetypes.md) - The Lookup Stack archetype maps to `OptionalT`/`MaybePath`
- [Migration Cookbook](migration_cookbook.md) - Side-by-side translations
- [Monad Transformers](transformers.md) - General concept and choosing the right transformer
- [MaybeT](maybet_transformer.md) - Equivalent functionality for Higher-Kinded-J's Maybe type
- [EitherT](eithert_transformer.md) - When you need typed errors, not just absence
~~~

---

~~~admonish tip title="Further Reading"
- [Java Optional Best Practices](https://www.baeldung.com/java-optional) - Comprehensive Baeldung guide (20 min read)
- [Null References: The Billion Dollar Mistake](https://www.infoq.com/presentations/Null-References-The-Billion-Dollar-Mistake-Tony-Hoare/) - Tony Hoare's historic talk on why Optional matters (60 min watch)
~~~

~~~admonish info title="Hands-On Learning"
Practice async lookup chains in [Tutorial 02: Async with Absence](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/transformers/Tutorial02_AsyncWithAbsence.java) (5 exercises, ~25 minutes).
~~~

---

**Previous:** [EitherT](eithert_transformer.md)
**Next:** [MaybeT](maybet_transformer.md)
