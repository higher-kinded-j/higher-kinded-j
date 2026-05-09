# Axes of Transformer Transformation

> *"There are not two paths; there are only choices about which path to take."*

A monad transformer carries two generic parameters that you may want to bend over time: the inner effect `F` and the outer carrier (the state, environment, or log type). On top of that, sometimes the right move is not to bend the transformer at all, but to compose effects at a boundary instead. Higher-Kinded-J gives you four distinct tools for these jobs, and the goal of this page is to put them on the same map so you can pick the right one without guessing.

~~~admonish info title="What You'll Learn"
- The four axes along which a transformer-shaped workflow can be transformed
- When to reach for `mapT`, `zoom` / `magnify`, plain monad combinators, or `EffectBoundary` / `@ComposeEffects`
- A worked example that combines two axes in a service-layer setting close to the Spring integration
~~~

---

## The four axes at a glance

| Axis | Tool | What it bends | When to use |
|------|------|---------------|-------------|
| Values | `map` / `flatMap` (and the comprehensions) | The result type `A` produced by a step | Default move. You are in the right effect; you just want to compute something with the value. |
| Inner effect `F` | `mapT` on every transformer | The underlying monad that wraps the result | You have a `StateT<S, F, A>` and need it to be a `StateT<S, G, A>` (for example, swapping `IO` for a virtual-thread task). |
| Outer carrier | `ForState.zoom` / `ReaderPath.magnify` | The state type `S` or environment type `R` | You have a focused computation operating on a sub-record and you want to lift it into the larger record without rewriting it. |
| Boundary composition | `@EffectAlgebra`, `@ComposeEffects`, `EffectBoundary` | The whole effect language at a single seam | You want to keep multiple effect families separate, then interpret them together at a single seam (typically the imperative shell of an application). |

The first three are *in-flight* transformations: you change one parameter of the transformer or path while leaving the rest of the workflow untouched. The fourth is a *boundary* transformation: the workflow stays in its own algebra, and the seam decides how to interpret it. Mixing these up is the most common source of confusion when picking a tool.

---

## Plain monad combinators (`map`, `flatMap`)

`map` and `flatMap` change the value an effectful step produces. They do not touch `F`, the carrier, or the algebra. Reach for them by default; only consider the other axes when the value-level move does not fit.

```java
ReaderPath<AppEnv, User> user = userRepo.loadUser(id);
ReaderPath<AppEnv, String> greeting = user.map(u -> "Hello, " + u.name());
```

`For.from(monad, ...)` and the `ForPath` family of comprehensions are the higher-level form of the same axis. They keep witness types at the comprehension boundary so the body reads as a sequence of named bindings.

---

## Bending the inner effect: `mapT`

Every monad transformer in the library exposes a `mapT` method that transforms the inner monad `F` into a different monad `G` via a natural transformation. The carrier (state, environment, log) is unchanged.

```java
// Transformer over IO, before:
StateT<AppState, IOKind.Witness, Result> ioWorkflow = ...;

// The same workflow, now over a virtual-thread task:
Natural<IOKind.Witness, VTaskKind.Witness> ioToVTask = ...;
StateT<AppState, VTaskKind.Witness, Result> vTaskWorkflow =
    ioWorkflow.mapT(ioToVTask);
```

Use `mapT` when the *outer* effect is what does not fit. If you are in a `ReaderT<F, R, A>` and need a `ReaderT<G, R, A>` because a downstream collaborator is in `G`, `mapT` is the right tool. The state, environment, or log carrier is untouched. See the per-transformer pages ([ReaderT](readert_transformer.md), [StateT](statet_transformer.md), [WriterT](writert_transformer.md)) for the exact `mapT` shapes.

---

## Bending the outer carrier: `zoom` and `magnify`

When the workflow is fine but the *carrier* needs to change, reach for `zoom` (state) or `magnify` (environment).

### `ForState.zoom` for state

`ForState.zoom` narrows a state-threaded comprehension to a sub-record. Inside the zoom block, all operations run against the sub-state; on `endZoom`, the outer state is reconstructed automatically.

```java
record Address(String street, String city, String zip) {}
record Customer(String name, Address address, int loyaltyPoints) {}

// The zoom accepts the FocusPath that @GenerateFocus already produces.
FocusPath<Customer, Address> addressPath = CustomerFocus.address();

Kind<IdKind.Witness, Customer> updated =
    ForState.withState(idMonad, Id.of(customer))
        .zoom(addressPath)
            .update(streetLens, "456 Oak Ave")
            .modify(zipLens, z -> z + "-5678")
        .endZoom()
        .modify(loyaltyLens, lp -> lp + 50)
        .yield();
```

`ForState.zoom` accepts:

- `Lens<S, T>` for fields that are always present.
- `FocusPath<S, T>` for the same case, using the path types that `@GenerateFocus` emits directly.
- `Iso<S, T>` for representation-change zoom (units, encodings, swapped tuples).
- `AffinePath<S, T>` on `FilterableSteps` only (i.e. when the surrounding monad is a `MonadZero`). When the affine target is absent, the comprehension short-circuits via `MonadZero.zero()`.

### `ReaderPath.magnify` for the environment

`ReaderPath.magnify` lifts a computation that reads a sub-environment into a larger environment, using a `Getter` or a `FocusPath`. The bare-`Function` form remains available as `local` for environment adaptations that are not naturally expressed as optics.

```java
record AppEnv(DbConfig db, AuthConfig auth, String tenant) {}

// Sub-service that only knows about DbConfig:
ReaderPath<DbConfig, User> loadUser = ...;

// Lift it into an AppEnv-shaped request via the FocusPath @GenerateFocus emits:
FocusPath<AppEnv, DbConfig> dbConfig = AppEnvFocus.db();
ReaderPath<AppEnv, User> appUser = loadUser.magnify(dbConfig);
```

The naming convention is deliberate: `magnify` is reserved for optic overloads, and `local` for the bare-`Function` overload. Both compile to the same underlying transformation; the names exist to signal intent at the call site.

~~~admonish note title="Why two names for one operation?"
The optic overloads (`magnify(Getter)`, `magnify(FocusPath)`) are the recommended form when you have a typed domain model and a generated path. The `local(Function)` form remains the escape hatch for ad hoc environment adaptations that are not optics, for example when transforming between two environment shapes that have no shared record structure.
~~~

---

## Boundary composition: `@EffectAlgebra`, `@ComposeEffects`, `EffectBoundary`

The first three axes change one parameter of a single transformer or path. The fourth axis is different in kind: it keeps multiple effect *languages* separate and only fuses them at a seam.

`@EffectAlgebra` declares a sealed interface as an effect language. `@ComposeEffects` combines several languages into a single Free monad program. `EffectBoundary` turns that program into something the imperative shell can run, by providing a natural transformation from the algebra to a concrete monad such as `IO`.

```java
@EffectAlgebra
sealed interface Console<A> {
  record Log<A>(String message, A next) implements Console<A> {}
  // ...
}

@EffectAlgebra
sealed interface Db<A> {
  record FindUser<A>(UserId id, Function<Optional<User>, A> next) implements Db<A> {}
  // ...
}

// Compose into one program; interpret at the boundary:
@ComposeEffects({ConsoleAlgebra.class, DbAlgebra.class})
record AppEffects() {}

// In the shell:
EffectBoundary boundary = EffectBoundary.of(consoleToIO, dbToIO);
User u = boundary.run(myProgram).unsafeRunSync();
```

Use boundary composition when:

- Several effect families need to coexist without making any one of them know about the others.
- You want to test the algebra without an effect runtime, then plug in a real interpreter at the seam.
- The set of effects in play is closed at the boundary but open inside the program.

`zoom` and `magnify` are not the right tool for this; they are about scoping a single carrier inside a single transformer. Boundary composition is about *separation of concerns at the seam*. They complement each other rather than competing.

---

## Worked example: `mapT` and `magnify` together

A common service-layer shape: a sub-service is written against a typed sub-environment and runs in `Reader`. The application bundles it into an `AppEnv` and runs it on a virtual-thread task.

The first axis we need is `magnify` (lift the sub-environment into the application environment). The second is `mapT` (run the resulting reader on a different inner effect at a chosen seam). Each axis stays focused on one concern.

```java
// 1. Sub-service: knows only DbConfig.
ReaderPath<DbConfig, User> loadUser = ...;

// 2. Lift it into the application environment via a generated FocusPath.
FocusPath<AppEnv, DbConfig> db = AppEnvFocus.db();
ReaderPath<AppEnv, User> appUser = loadUser.magnify(db);

// 3. Run it under the application's request context. Translating an outer
// reader-style ReaderPath result into a virtual-thread task happens at the
// seam, not inside the service code, by combining the path with a VTaskPath
// boundary at the request handler.
User result = appUser.run(currentRequestEnv);
```

The same example with `mapT` enters when you reach for the raw `ReaderT<F, R, A>` transformer instead of the path. The two axes stay independent: one is about *what environment the computation reads*, the other is about *what effect runs underneath*. The mistake to avoid is using `mapT` to fix an environment mismatch, or `magnify` to swap effects.

---

## Picking the right axis

The fastest way to pick is to ask which parameter is wrong:

- "The result is the wrong shape" → values: `map`, `flatMap`, comprehensions.
- "The inner effect is wrong" → `mapT`.
- "The outer carrier is wrong (state or environment shape)" → `zoom` or `magnify`.
- "These effects should not know about each other" → boundary composition with `@EffectAlgebra` / `@ComposeEffects` / `EffectBoundary`.

If you find yourself reaching for two axes at once, that is usually correct: the example above uses `magnify` for the carrier and `mapT` for the inner effect, and each retains its single responsibility. If you find yourself fighting one axis to do the work of another, that is the signal to step back and check which axis the problem actually lives on.

~~~admonish info title="Hands-On Learning"
Practice the optic-polymorphic forms in [Tutorial 05: Optic-Polymorphic Zoom and Magnify](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/expression/Tutorial05_ZoomAndMagnify.java) (6 exercises, ~12 minutes).
~~~

~~~admonish tip title="See Also"
- [ForState Comprehension](../functional/forstate_comprehension.md) - The home of `zoom`, including nested zoom and `endZoom`.
- [ReaderT Transformer](readert_transformer.md) - Where `mapT` lives for environment-reading transformers.
- [StateT Transformer](statet_transformer.md) - Where `mapT` lives for stateful transformers.
- [Effect Handlers](../effect/effect_handlers_intro.md) - The `@EffectAlgebra` / `@ComposeEffects` story in full.
- [Path or Transformer?](when_to_drop_to_transformers.md) - The first decision before any of these axes apply.
~~~

---

**Previous:** [Combining Capabilities](mtl_combining.md)
**Next:** [Common Compiler Errors](common_errors.md)
