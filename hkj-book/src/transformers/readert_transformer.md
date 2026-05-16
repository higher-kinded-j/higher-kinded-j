# The ReaderT Transformer:
## _Threading Configuration Through Effects_

> *"No man is an island, entire of itself."*
>
> – John Donne, *Meditation XVII*

No computation is independent of its environment. ReaderT makes that dependency explicit and composable.

~~~admonish info title="What You'll Learn"
- How to combine dependency injection (`Reader`) with other effects like async operations
- Building configuration-dependent workflows that are also async or failable
- Using `For` comprehensions with `ask`, `reader`, and `lift` to keep witness types localised
- Creating testable microservice clients with injected configuration
- When to use the [`ReaderPath`](../effect/advanced_effects.md) Path type or the [`MonadReader`](mtl_reader.md) capability instead of raw `ReaderT`
~~~

~~~admonish example title="See Example Code"
- [ReaderTExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/reader_t/ReaderTExample.java)
- [ReaderTAsyncExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/reader_t/ReaderTAsyncExample.java)
- [ReaderTAsyncUnitExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/reader_t/ReaderTAsyncUnitExample.java)
~~~

~~~admonish note title="Path First, Stack Later"
For most use cases, [`ReaderPath<R, A>`](../effect/advanced_effects.md) is the better starting point when the environment is the only effect. When you need polymorphic, stack-independent code, the [`MonadReader<F, R>`](mtl_reader.md) capability is usually a better fit than the concrete `ReaderT`.

Reach for raw `ReaderT` only when you need to combine an environment with a specific outer monad that Path does not wrap, or when you are constructing your own MTL instance.
~~~

---

## The Problem: Configuration Everywhere

Consider a service that needs API keys and URLs for every operation:

```java
CompletableFuture<ServiceData> fetchData(AppConfig config, String itemId) {
    return CompletableFuture.supplyAsync(() ->
        callApi(config.apiKey(), config.serviceUrl(), itemId));
}

CompletableFuture<ProcessedData> processData(AppConfig config, ServiceData data) {
    return CompletableFuture.supplyAsync(() ->
        transform(data, config.apiKey()));
}

CompletableFuture<ProcessedData> workflow(AppConfig config) {
    return fetchData(config, "item123")
        .thenCompose(data -> processData(config, data));
}
```

The `config` parameter threads through every function signature, every call site, every test. It's noise that obscures the actual logic. Rename a config field, and you touch every function in the chain.

---

## The Solution

### With the Effect Path API (single effect)

If the environment is the only effect, `ReaderPath` is the simplest expression:

```java
ReaderPath<AppConfig, ProcessedData> workflow() {
    return Path.<AppConfig>ask()
        .via(config -> Path.right(callApi(config.apiKey(), "item123")))
        .via(data   -> Path.<AppConfig>ask()
            .map(config -> transform(data, config.apiKey())));
}
```

### With raw `ReaderT` (combined effect)

When the environment must combine with another monad (here `CompletableFuture`):

```java
var futureMonad  = Instances.monadError(completableFuture());
var readerTMonad = Instances.readerT(futureMonad);

ReaderT<CompletableFutureKind.Witness, AppConfig, ServiceData> fetchDataRT(String itemId) {
  return ReaderT.of(config ->
      FUTURE.widen(CompletableFuture.supplyAsync(() ->
          callApi(config.apiKey(), config.serviceUrl(), itemId))));
}

ReaderT<CompletableFutureKind.Witness, AppConfig, ProcessedData> processDataRT(ServiceData data) {
  return ReaderT.reader(futureMonad,
      config -> transform(data, config.apiKey()));
}

// Compose with For:
var workflowRT = For.from(readerTMonad, fetchDataRT("item123"))
    .from(data -> processDataRT(data))
    .yield((data, processed) -> processed);

// Provide the config once at the edge:
var result = FUTURE.join(READER_T.narrow(workflowRT).run().apply(prodConfig));
```

The `AppConfig` is threaded implicitly through `flatMap`. Each operation declares its dependency on `AppConfig` in its return type but never receives it as a parameter.

---

## The Railway View

<pre class="hkj-railway-diagram">
    <span style="color:#4CAF50"><b>Value</b>     ═══●═══════════●═══════════●═══▶  ProcessedData (in F)</span>
    <span style="color:#4CAF50">              fetchData    process       map</span>
    <span style="color:#4CAF50">              (flatMap)    (flatMap)</span>
                 ▲           ▲           ▲    <i>config read at each step</i>
    <span style="color:#2196F3"><b>AppConfig</b> ═══╧═══════════╧═══════════╧═══▶  read-only, never modified</span>
    <span style="color:#2196F3">              apiKey       serviceUrl    executor</span>

                                              │
                                         <i>run(prodConfig)</i>  provide the environment once at the edge
</pre>

The configuration sits on its own track and is never consumed; each `flatMap` step reads from it without changing it. `run().apply(config)` supplies the environment at the boundary, after which the value track collapses into the outer monad `F`.

---

## How ReaderT Works

`ReaderT<F, R, A>` wraps a function `R -> Kind<F, A>`. When you supply an environment of type `R`, you get back a monadic value `Kind<F, A>`.

```
    ┌──────────────────────────────────────────────────────────┐
    │  ReaderT<CompletableFutureKind.Witness, AppConfig, A>    │
    │                                                          │
    │        ┌── AppConfig ──┐                                 │
    │        │               │                                 │
    │        │  apiKey       │                                 │
    │        │  serviceUrl   │                                 │
    │        │  executor     │                                 │
    │        └───────┬───────┘                                 │
    │                │                                         │
    │                ▼                                         │
    │   ┌─── Function: R → Kind<F, A> ───────────────────┐     │
    │   │                                                │     │
    │   │  config → CompletableFuture<result>            │     │
    │   │                                                │     │
    │   └────────────────────────────────────────────────┘     │
    │                                                          │
    │  flatMap ──▶ threads same config to next operation       │
    │  map ──────▶ transforms result, config unchanged         │
    │  ask ──────▶ gives you the config itself                 │
    │  run ──────▶ provides config, returns F<A>               │
    └──────────────────────────────────────────────────────────┘
```

* **`F`**: The witness type of the **outer monad** (e.g. `CompletableFutureKind.Witness`).
* **`R`**: The type of the **read-only environment** (configuration, dependencies).
* **`A`**: The type of the value produced, within the outer monad `F`.
* **`run`**: The core function `R -> Kind<F, A>`.

```java
public record ReaderT<F, R, A>(@NonNull Function<R, Kind<F, A>> run)
    implements ReaderTKind<F, R, A> {
  // ... static factory methods ...
}
```

---

## Setting Up ReaderTMonad

The `ReaderTMonad<F, R>` class implements `Monad<ReaderTKind.Witness<F, R>>`, providing standard monadic operations. It requires a `Monad<F>` instance for the outer monad:

```java
record AppConfig(String apiKey) {}

var readerTOptionalMonad = Instances.readerT(Instances.monadError(optional()));
```

~~~admonish note title="Working with Kind"
**Witness Type:** `ReaderTKind<F, R, A>` extends `Kind<ReaderTKind.Witness<F, R>, A>`. The outer monad `F` and environment `R` are fixed; `A` is the variable value type.

**KindHelper:** `ReaderTKindHelper` provides `READER_T.widen` and `READER_T.narrow` for safe conversion. With `For` comprehensions you rarely need them; they appear at the boundaries when interoperating with raw `flatMap` chains.

```java
Kind<ReaderTKind.Witness<F, R>, A> kind = READER_T.widen(readerT);
ReaderT<F, R, A> concrete                = READER_T.narrow(kind);
```
~~~

---

## Key Operations

| Operation | Behaviour |
|-----------|-----------|
| `readerTMonad.of(value)`        | Lifts a pure value; environment is ignored. Returns `ReaderT(r -> outerMonad.of(value))` |
| `readerTMonad.map(f, kind)`     | Transforms the result value `A -> B` within the outer monad; environment unchanged |
| `readerTMonad.flatMap(f, kind)` | Sequences operations; threads the *same* environment to the next step |

The `MonadReader` capability adds `ask()`, `reader(f)`, and `local(f, ma)` on top.

---

## Creating ReaderT Instances

```java
var optMonad   = Instances.monadError(optional());
record Config(String setting) {}

// 1. Directly from R -> F<A> function
var rt1 = ReaderT.<OptionalKind.Witness, Config, String>of(
    cfg -> OPTIONAL.widen(Optional.of("Data based on " + cfg.setting())));

// 2. Lifting an existing F<A> (environment ignored)
Kind<OptionalKind.Witness, Integer> optionalValue = OPTIONAL.widen(Optional.of(123));
var rt2 = ReaderT.<OptionalKind.Witness, Config, Integer>lift(optMonad, optionalValue);

// 3. From R -> A function (result lifted into F)
var rt3 = ReaderT.<OptionalKind.Witness, Config, String>reader(
    optMonad, cfg -> "Hello from " + cfg.setting());

// 4. ask: provides the environment itself as the result
var rt4 = ReaderT.<OptionalKind.Witness, Config>ask(optMonad);
```

---

## Real-World Example: Configuration-Dependent Async Services

~~~admonish example title="Configuration-Dependent Asynchronous Service Calls"

- [ReaderTAsyncExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/reader_t/ReaderTAsyncExample.java)

**The problem:** async service operations that all need an `AppConfig` (API keys, URLs, executor). Compose them without passing config through every call.

**The solution:**

```java
record AppConfig(String apiKey, String serviceUrl, ExecutorService executor) {}
record ServiceData(String rawData) {}
record ProcessedData(String info) {}

var futureMonad  = Instances.monadError(completableFuture());
var readerTMonad = Instances.readerT(futureMonad);

ReaderT<CompletableFutureKind.Witness, AppConfig, ServiceData> fetchServiceDataRT(String itemId) {
  return ReaderT.of(config -> FUTURE.widen(
      CompletableFuture.supplyAsync(() ->
          new ServiceData("Raw data for " + itemId + " from " + config.serviceUrl()),
          config.executor())));
}

ReaderT<CompletableFutureKind.Witness, AppConfig, ProcessedData> processDataRT(ServiceData data) {
  return ReaderT.reader(futureMonad,
      config -> new ProcessedData("Processed: " + data.rawData().toUpperCase()));
}

// Compose with For:
var workflowRT = For.from(readerTMonad, fetchServiceDataRT("item123"))
    .from(data -> processDataRT(data))
    .yield((data, processed) -> processed);

// Run with different configs:
var prodConfig    = new AppConfig("prod_key", "https://api.prod.example.com", executor);
var stagingConfig = new AppConfig("staging_key", "https://api.staging.example.com", executor);

var prodResult    = FUTURE.join(READER_T.narrow(workflowRT).run().apply(prodConfig));
var stagingResult = FUTURE.join(READER_T.narrow(workflowRT).run().apply(stagingConfig));
```

**Why this works:** the `AppConfig` is threaded through both operations by `flatMap`. The same workflow runs against production and staging by changing the argument to `run().apply(...)`. The workflow definition is completely decoupled from the environment.
~~~

---

## Using `ask` to Access Configuration Mid-Workflow

~~~admonish example title="Accessing Configuration with `ask`"

**The problem:** within a composed workflow, read a specific config value without restructuring the computation.

**The solution:**

```java
var getConfigRT  = ReaderT.<CompletableFutureKind.Witness, AppConfig>ask(futureMonad);
var serviceUrlRT = readerTMonad.map(
    (AppConfig cfg) -> "Service URL: " + cfg.serviceUrl(),
    getConfigRT);

var stagingUrl = FUTURE.join(READER_T.narrow(serviceUrlRT).run().apply(stagingConfig));
// → "Service URL: https://api.staging.example.com"
```

`ReaderT.ask` returns the entire environment as the result, which you can then `map` over to extract specific fields.
~~~

---

## Fire-and-Forget Operations with Unit

~~~admonish example title="`ReaderT` for Actions Returning `Unit`"

- [ReaderTAsyncUnitExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/reader_t/ReaderTAsyncUnitExample.java)

**The problem:** some operations (logging, initialisation, sending metrics) depend on configuration but don't produce a meaningful return value.

**The solution:** use `Unit` as the value type:

```java
ReaderT<CompletableFutureKind.Witness, AppConfig, Unit> initialiseComponentRT() {
    return ReaderT.of(config -> FUTURE.widen(
        CompletableFuture.runAsync(() -> {
            System.out.println("Initialising with API Key: " + config.apiKey());
        }, config.executor()).thenApply(v -> Unit.INSTANCE)));
}

var result = FUTURE.join(initialiseComponentRT().run().apply(prodConfig));
// → () (Unit.INSTANCE, signifying successful completion)
```
~~~

---

## Transforming the Outer Monad with `mapT`

Sometimes you need to change the *outer monad* of a `ReaderT` without altering the environment-threading logic. Perhaps you want to switch from `Optional` to `Id` (collapsing optionality with a default), or apply a natural transformation to move between effect types.

Because `ReaderT` wraps a function rather than a value, `mapT` composes the transformation function after each result of `run`:

```
  env ──> run() ──> Kind<F, A> ──> f ──> Kind<G, A>
   │                                          │
   └──── combined into new ReaderT<G, R, A> ──┘
```

```java
ReaderT<OptionalKind.Witness, Config, String> optReader = ...;

var idReader = optReader.mapT(optKind -> {
  Optional<String> opt = OPTIONAL.narrow(optKind);
  return ID.widen(Id.of(opt.orElse("default")));
});
```

~~~admonish note title="mapT vs map"
`map` transforms the *value* produced by the reader (the `A` in `Kind<F, A>`).
`mapT` transforms the *outer monad* that wraps each result, the `F` in `R -> F<A>`.
The environment-threading is completely unaffected.
~~~

---

~~~admonish warning title="Common Mistakes"
- **Forgetting to run:** a `ReaderT` is a *description* of a computation, not the computation itself. It does nothing until you call `.run().apply(config)`. If your tests pass but nothing happens, check that you are running the `ReaderT`.
- **Mutating the environment:** `R` should be immutable. `ReaderT` passes the same `R` to every operation in a chain. Mutating it would break referential transparency and produce unpredictable results.
- **Using `ReaderT` when you need state changes:** if the configuration changes between steps, you need [`StateT`](statet_transformer.md), not `ReaderT`. The "Reader" in `ReaderT` means *read-only*.
- **Reaching for the transformer when `ReaderPath` would do:** if your only effect is the environment, `ReaderPath` is shorter and reads more naturally.
~~~

---

~~~admonish tip title="See Also"
- [ReaderPath / Advanced Effects](../effect/advanced_effects.md) - The Path-API equivalent
- [MonadReader](mtl_reader.md) - The MTL capability for stack-independent code
- [Stack Archetypes](archetypes.md) - The Context Stack archetype maps to `ReaderT`/`ReaderPath`
- [Migration Cookbook](migration_cookbook.md) - Side-by-side translations
- [Monad Transformers](transformers.md) - General concept and choosing the right transformer
- [StateT](statet_transformer.md) - When your environment needs to change between steps
- [EitherT](eithert_transformer.md) - When operations can fail with typed errors
~~~

---

~~~admonish tip title="Further Reading"
- [Reader Monad for Dependency Injection](https://medium.com/@johnmcclean/reader-monad-for-dependency-injection-in-java-9056d9501c75) - Practical examples without frameworks (12 min read)
- [Functional Dependency Injection](https://www.youtube.com/watch?v=ZasXwtTRkio) - Conference talk on Reader pattern (40 min watch)
- [ReaderT Design Pattern](https://academy.fpblock.com/blog/2017/06/readert-design-pattern/) - Michael Snoyman's production patterns (30 min read)
- [A Fresh Perspective on Monads](https://rockthejvm.com/articles/a-fresh-perspective-on-monads) - Rock the JVM on composing monadic effects (20 min read)
~~~

~~~admonish info title="Hands-On Learning"
The `MonadReader` capability that wraps `ReaderT` is exercised in [Tutorial 04: Polymorphic Capabilities (MTL)](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/transformers/Tutorial04_PolymorphicCapabilities.java) (14 exercises, ~30-40 minutes).
~~~

---

**Previous:** [MaybeT](maybet_transformer.md)
**Next:** [StateT](statet_transformer.md)
