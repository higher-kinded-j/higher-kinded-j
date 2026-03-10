# MonadReader: Environment Access

> *"The answer to the Great Question... of Life, the Universe and Everything... is... Forty-two."*
>
> -- Douglas Adams, *The Hitchhiker's Guide to the Galaxy*

The computer Deep Thought spent seven and a half million years computing an answer, but nobody remembered the question. With `MonadReader`, the question is always available: your environment is there whenever you `ask()` for it, and every function in the chain sees the same one.

~~~admonish info title="What You'll Learn"
- How `MonadReader` abstracts read-only environment access independently of any transformer stack
- Using `ask`, `reader`, and `local` to work with configuration in polymorphic code
- What the MonadReader laws mean in plain Java terms
- When environment access is the right capability to reach for
~~~

---

## What MonadReader Does

`MonadReader<F, R>` gives your computation access to a shared, read-only environment of type `R`. Think of it as a global configuration object that every step can read but nobody can change. The "read-only" part is important: if you need to *update* the environment between steps, you want [MonadState](mtl_state.md) instead.

The interface provides four operations:

```
    ┌──────────────────────────────────────────────────────────────┐
    │  MonadReader<F, R>  (extends Monad<F>)                       │
    │                                                              │
    │  ask()          →  Kind<F, R>                                │
    │                    "Give me the whole environment"           │
    │                                                              │
    │  reader(f)      →  Kind<F, A>     where f : R → A            │
    │                    "Extract one field from the environment"  │
    │                                                              │
    │  asks(f)        →  Kind<F, A>     (alias for reader)         │
    │                                                              │
    │  local(f, ma)   →  Kind<F, A>     where f : R → R            │
    │                    "Run ma with a temporarily modified       │
    │                     environment, then restore the original"  │
    └──────────────────────────────────────────────────────────────┘
```

### `ask`: Retrieve the Whole Environment

`ask()` is the simplest operation: it returns the entire environment as a value inside the monad. If your environment is an `AppConfig` record, `ask()` gives you the whole `AppConfig`.

```java
record AppConfig(String dbUrl, int maxRetries, boolean debugMode) {}

<F extends WitnessArity<TypeArity.Unary>> Kind<F, AppConfig>
    getConfig(MonadReader<F, AppConfig> env) {
  return env.ask();  // Returns the entire AppConfig
}
```

### `reader` / `asks`: Extract a Single Field

Usually you don't need the whole environment; you need one field from it. `reader(f)` applies a function to the environment and returns the result. It is equivalent to `map(f, ask())`, but reads more clearly:

```java
<F extends WitnessArity<TypeArity.Unary>> Kind<F, String>
    connectionString(MonadReader<F, AppConfig> env) {
  return env.reader(config ->
      config.dbUrl() + "?retries=" + config.maxRetries());
}
```

`asks(f)` is an alias for `reader(f)`. Both exist because Haskell uses both names, and different developers find one or the other more natural.

### `local`: Temporarily Modify the Environment

`local(f, ma)` runs a sub-computation `ma` with a modified environment. The modification is temporary: other computations in the chain still see the original environment.

This is useful when a sub-system needs slightly different configuration. For example, you might force debug mode on for a diagnostic sub-query while the rest of the pipeline runs in production mode:

```java
<F extends WitnessArity<TypeArity.Unary>> Kind<F, String>
    debugQuery(MonadReader<F, AppConfig> env) {
  // Run this specific sub-computation with debug mode forced on
  return env.local(
      config -> new AppConfig(config.dbUrl(), config.maxRetries(), true),
      env.reader(config -> "debug=" + config.debugMode()));
}
// Always returns "debug=true", regardless of the original config's debugMode
```

The original `AppConfig` is not affected. After `local` completes, subsequent operations see the unchanged environment.

```
    Environment flow with local():

    AppConfig(url, 3, false)          ← original
         │
         ├──▶ reader(...)             ← sees (url, 3, false)
         │
         ├──▶ local(forceDebug, ...)  ← temporarily (url, 3, true)
         │         │
         │         └──▶ reader(...)   ← sees (url, 3, true)
         │
         └──▶ reader(...)             ← sees (url, 3, false) again
```

---

## A Complete Example

Here is a polymorphic function that builds a database connection string from configuration, with an optional debug suffix:

```java
record AppConfig(String dbUrl, int maxRetries, boolean debugMode) {}

// Works with ANY monad that provides reader capability
<F extends WitnessArity<TypeArity.Unary>> Kind<F, String>
    buildConnectionString(MonadReader<F, AppConfig> env) {
  return For.from(env, env.ask())
      .yield(config -> {
        String base = config.dbUrl() + "?retries=" + config.maxRetries();
        return config.debugMode() ? base + "&debug=true" : base;
      });
}

// Use with ReaderT over Id (for testing)
ReaderTMonadReader<IdKind.Witness, AppConfig> testEnv =
    new ReaderTMonadReader<>(idMonad);
Kind<ReaderTKind.Witness<IdKind.Witness, AppConfig>, String> result =
    buildConnectionString(testEnv);

// Use with ReaderT over CompletableFuture (for production)
ReaderTMonadReader<CompletableFutureKind.Witness, AppConfig> prodEnv =
    new ReaderTMonadReader<>(futureMonad);
Kind<ReaderTKind.Witness<CompletableFutureKind.Witness, AppConfig>, String> asyncResult =
    buildConnectionString(prodEnv);
```

The function `buildConnectionString` was written once. It runs synchronously in tests (over `Id`) and asynchronously in production (over `CompletableFuture`) with no code changes.

---

## The MonadReader Laws

Every `MonadReader` implementation must satisfy four laws. These are not abstract formalities; they are guarantees about how the environment behaves. If an implementation violates them, code that depends on `MonadReader` will produce surprising results.

The laws use Haskell-style notation (`>>` means "sequence, discarding the first result"; `>>=` means `flatMap`). Here is what each law means in Java terms:

### Law 1: Ask Is Idempotent

```
    ask >> ask  ≡  ask
```

**In Java:** Reading the environment and throwing away the result, then reading it again, is the same as just reading it once. The environment does not change between reads.

```java
// These produce the same result:
For.from(env, env.ask())
    .from(_ -> env.ask())
    .yield((_, second) -> second)

env.ask()
```

This guarantees that the environment is stable. No matter how many times you `ask()`, you get the same value. There are no hidden side effects that alter the environment between calls.

### Law 2: Local-Ask Coherence

```
    local(f, ask())  ≡  map(f, ask())
```

**In Java:** Modifying the environment with `f` and then reading it is the same as reading the original environment and then applying `f` to the result.

```java
// These produce the same result:
env.local(config -> config.withDebug(true), env.ask())
env.map(config -> config.withDebug(true), env.ask())
```

This means `local` does exactly what it claims: it applies a function to the environment before the sub-computation sees it. There are no additional effects.

### Law 3: Local Composition

```
    local(f, local(g, ma))  ≡  local(g.compose(f), ma)
```

**In Java:** Nesting two `local` calls is the same as combining their functions into one `local`. The environment modifications compose.

```java
// These produce the same result:
env.local(addRetries, env.local(enableDebug, computation))
env.local(enableDebug.compose(addRetries), computation)
```

This means you can refactor nested `local` calls into a single one without changing behaviour. Environment modifications are just function composition.

### Law 4: Local Identity

```
    local(identity, ma)  ≡  ma
```

**In Java:** Modifying the environment with the identity function (a function that returns its input unchanged) has no effect.

```java
// These produce the same result:
env.local(config -> config, computation)
computation
```

This is the "do nothing" baseline. If `local` with identity changed behaviour, something would be fundamentally wrong with the implementation.

---

## The Concrete Instance: ReaderTMonadReader

`ReaderTMonadReader<F, R>` is the standard implementation of `MonadReader` for the `ReaderT` transformer. It extends `ReaderTMonad<F, R>` (inheriting `of`, `map`, `flatMap`, `ap`) and adds the environment-access operations:

```java
ReaderTMonadReader<IdKind.Witness, AppConfig> readerInstance =
    new ReaderTMonadReader<>(idMonad);

// Now use it as a MonadReader:
Kind<ReaderTKind.Witness<IdKind.Witness, AppConfig>, String> result =
    readerInstance.reader(AppConfig::dbUrl);
```

---

~~~admonish warning title="Common Mistakes"
- **Mutating the environment object:** `MonadReader` passes the same `R` to every operation. If `R` is mutable and you modify it, every subsequent `ask()` sees the mutation. Always use immutable records for your environment type.
- **Using MonadReader when you need state changes:** If the "configuration" changes between steps, you need [MonadState](mtl_state.md). The "Reader" in `MonadReader` means *read-only*.
- **Confusing `local` with persistent modification:** `local` is scoped. After the sub-computation finishes, the original environment is restored. It does not permanently alter anything.
~~~

---

~~~admonish tip title="See Also"
- [ReaderT](readert_transformer.md) -- The concrete transformer behind `MonadReader`
- [MonadState](mtl_state.md) -- When you need read-write state
- [Reader Monad](../monads/reader_monad.md) -- The non-transformer version
~~~

---

**Previous:** [MTL Capabilities](mtl_capabilities.md)
**Next:** [MonadState](mtl_state.md)
