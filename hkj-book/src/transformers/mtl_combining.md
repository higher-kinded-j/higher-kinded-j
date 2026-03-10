# Combining Capabilities

> *"The whole is greater than the sum of its parts."*
>
> -- Aristotle, *Metaphysics*

A function that can read configuration is useful. A function that can also accumulate an audit log is more useful. A function that does both while managing state is a production workflow. The power of MTL emerges when you combine capabilities, letting each function declare exactly the subset of effects it needs.

~~~admonish info title="What You'll Learn"
- How to write functions that require **multiple** MTL capabilities
- The concrete instances that bridge MTL interfaces to transformer stacks
- How MTL operations compose naturally with **ForState** workflows
- Practical patterns for multi-capability code
~~~

---

## Multi-Capability Functions

A function that needs several effects simply takes multiple MTL parameters:

```java
// A function that reads config AND accumulates audit output
<F extends WitnessArity<TypeArity.Unary>> Kind<F, String>
    auditedLookup(
        MonadReader<F, AppConfig> env,
        MonadWriter<F, List<String>> audit,
        String key) {
  return For.from(env, env.ask())
      .from(config -> audit.tell(List.of("Looked up " + key + " in " + config.dbUrl())))
      .yield((config, _) -> config.dbUrl() + "/" + key);
}
```

The function declares two capabilities: "I need to read an `AppConfig`" and "I need to write `List<String>` output". It says nothing about how those capabilities are assembled. The caller provides the instances.

### The Type Variable `F` Must Match

When a function takes multiple MTL parameters, they must all share the same type variable `F`. This is enforced by the compiler:

```java
// ✓ Both use the same F
<F extends WitnessArity<TypeArity.Unary>> Kind<F, String>
    workflow(MonadReader<F, Config> env, MonadState<F, Counter> state) { ... }

// ✗ Would not compile: different F types cannot be unified
//   MonadReader<F1, Config> and MonadState<F2, Counter> are incompatible
```

In practice, this means the caller must provide a single transformer stack (or a custom type) that implements all the required capabilities.

---

## Concrete Instances

Each MTL interface has a standard implementation that bridges to its corresponding transformer:

```
    ┌──────────────────────────────────────────────────────────────┐
    │  MTL Interface              Concrete Instance                │
    │  ──────────────────────     ─────────────────────────────    │
    │                                                              │
    │  MonadReader<F', R>     ←   ReaderTMonadReader<F, R>         │
    │                              where F' = ReaderTKind.Witness  │
    │                              extends ReaderTMonad<F, R>      │
    │                                                              │
    │  MonadState<F', S>      ←   StateTMonadState<S, F>           │
    │                              where F' = StateTKind.Witness   │
    │                              extends StateTMonad<S, F>       │
    │                                                              │
    │  MonadWriter<F', W>     ←   WriterTMonad<F, W>               │
    │                              where F' = WriterTKind.Witness  │
    │                              implements MonadWriter directly │
    └──────────────────────────────────────────────────────────────┘
```

Each instance extends (or implements) the existing monad class for its transformer, inheriting `of`, `map`, `flatMap`, and `ap`. The MTL-specific operations are added on top.

### Creating Instances

```java
Monad<IdKind.Witness> idMonad = IdMonad.instance();

// MonadReader instance backed by ReaderT over Id
ReaderTMonadReader<IdKind.Witness, AppConfig> readerInstance =
    new ReaderTMonadReader<>(idMonad);

// MonadState instance backed by StateT over Id
StateTMonadState<Counter, IdKind.Witness> stateInstance =
    new StateTMonadState<>(idMonad);

// MonadWriter instance backed by WriterT over Id
WriterTMonad<IdKind.Witness, List<String>> writerInstance =
    new WriterTMonad<>(idMonad, listMonoid);
```

For production use, substitute the `Id` monad with `CompletableFuture`, `VTask`, or any other outer monad to combine the MTL capability with async execution or error handling.

---

## Integration with ForState

The existing `from()` and `fromThen()` methods on `ForState` already compose naturally with MTL operations. There is no special API. MTL operations are just functions that return `Kind<F, A>`, which is exactly what `from()` accepts:

```java
<F extends WitnessArity<TypeArity.Unary>> Kind<F, AppConfig>
    readConfig(MonadReader<F, AppConfig> env) {
  return env.ask();
}

// In a ForState workflow:
ForState.withState(readerMonad, initialState)
    .from(s -> readConfig(readerInstance))   // MTL operation via from()
    .fromThen(s -> /* next step */, lens)
    .yield();
```

This means you can mix MTL operations freely within stateful workflows without any bridging code.

---

## Practical Patterns

### Pattern 1: Testable Service Layer

Write your service logic against MTL interfaces, then provide different instances for production and testing:

```java
// Service logic: stack-independent
<F extends WitnessArity<TypeArity.Unary>> Kind<F, UserProfile>
    getProfile(MonadReader<F, ServiceConfig> env, String userId) {
  return For.from(env, env.ask())
      .yield(config -> fetchFromApi(config.apiUrl(), userId));
}

// Production: ReaderT over CompletableFuture
ReaderTMonadReader<CompletableFutureKind.Witness, ServiceConfig> prodEnv =
    new ReaderTMonadReader<>(futureMonad);
var asyncResult = getProfile(prodEnv, "user-123");

// Test: ReaderT over Id (synchronous, no threads)
ReaderTMonadReader<IdKind.Witness, ServiceConfig> testEnv =
    new ReaderTMonadReader<>(idMonad);
var syncResult = getProfile(testEnv, "user-123");
```

### Pattern 2: Audited State Transitions

Combine `MonadState` and `MonadWriter` to track state changes with an audit trail:

```java
<F extends WitnessArity<TypeArity.Unary>> Kind<F, Unit>
    deposit(
        MonadState<F, Account> state,
        MonadWriter<F, List<String>> audit,
        BigDecimal amount) {
  return For.from(state, state.get())
      .from(account -> audit.tell(List.of("Deposited " + amount + " to " + account.id())))
      .from(t -> state.put(t._1().credit(amount)))
      .yield((_, _, _) -> Unit.INSTANCE);
}
```

### Pattern 3: Configuration-Aware Logging

Combine `MonadReader` and `MonadWriter` to control log verbosity based on configuration:

```java
record LogConfig(boolean verbose) {}

<F extends WitnessArity<TypeArity.Unary>> Kind<F, Unit>
    logIfVerbose(
        MonadReader<F, LogConfig> env,
        MonadWriter<F, List<String>> audit,
        String message) {
  return For.from(env, env.ask())
      .from(config -> config.verbose()
          ? audit.tell(List.of(message))
          : env.of(Unit.INSTANCE))
      .yield((_, unit) -> unit);
}
```

---

~~~admonish warning title="Common Mistakes"
- **Mixing instances from different stacks:** If a function takes `MonadReader<F, R>` and `MonadWriter<F, W>`, both must use the *same* `F`. Passing instances with different witness types will not compile. In practice this means you need a single stack that provides all the capabilities your function requires.
- **Over-abstracting:** Not every function needs MTL. If a function is only ever called from one place with one stack, using the concrete transformer type is clearer and has fewer type parameters to track.
- **Forgetting that MTL interfaces extend Monad:** `MonadReader<F, R>` *is* a `Monad<F>`. You can call `of`, `map`, and `flatMap` directly on any MTL instance. There is no need for a separate `Monad<F>` parameter.
~~~

---

~~~admonish tip title="See Also"
- [MTL Capabilities](mtl_capabilities.md) -- Overview and when to use MTL vs concrete transformers
- [MonadReader](mtl_reader.md) -- Read-only environment access
- [MonadState](mtl_state.md) -- Mutable state threading
- [MonadWriter](mtl_writer.md) -- Append-only output accumulation
- [MonadError](../functional/monad_error.md) -- Typed error handling
- [ForState Comprehension](../functional/forstate_comprehension.md) -- Stateful workflows with named fields
~~~

---

**Previous:** [MonadWriter](mtl_writer.md)
