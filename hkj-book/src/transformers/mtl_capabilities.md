# MTL Capabilities:
## _Writing Code That Doesn't Care How Effects Are Assembled_

> *"I am the sum total of everything that went before me, of all I have been seen done, of everything done-to-me."*
>
> -- Salman Rushdie, *Midnight's Children*

Rushdie's narrator insists that identity does not depend on the specifics of its construction. The same principle applies to effectful code. A function that reads configuration should not care whether the environment arrives through `ReaderT<CompletableFuture, ...>`, `ReaderT<IO, ...>`, or some custom stack. It should declare *what it needs* ("I need to read an environment") and let the caller decide *how* that capability is provided.

That is what MTL-style capability interfaces do. They separate the declaration of an effect from its implementation, so your business logic composes against abstract capabilities rather than concrete transformer stacks.

~~~ admonish example title="See Example Code:"
[MTLCapabilitiesExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/expression/MTLCapabilitiesExample.java)
~~~

~~~admonish info title="What You'll Learn"
- Why coding against concrete transformer stacks creates **coupling** that makes refactoring painful
- How `MonadReader`, `MonadState`, and `MonadWriter` declare capabilities without fixing the stack
- Writing **polymorphic** functions that work with any stack providing the required capabilities
- When to use MTL interfaces and when concrete transformers are the simpler choice
~~~

---

## The Problem: Locked to a Stack

Consider a function that reads a URL from configuration and fetches a user profile:

```java
// Locked to ReaderT over CompletableFuture — cannot be reused with IO, Id, or VTask
ReaderT<CompletableFutureKind.Witness, AppConfig, UserProfile>
    fetchProfile(String userId) {
  return ReaderT.of(config ->
      FUTURE.widen(CompletableFuture.supplyAsync(() ->
          callApi(config.apiUrl(), userId))));
}
```

This works, but it welds the function to `CompletableFuture`. Want to test it with `Id`? Rewrite it. Want to use it in a `VTask` pipeline? Rewrite it again. The business logic ("read the API URL from config, call the service") is identical each time; only the plumbing changes.

The same problem arises with `StateT` and `WriterT`. Any function that names a specific transformer stack becomes locked to that stack, even when the business logic is entirely independent of the choice.

## The Solution: Declare Capabilities, Not Stacks

```java
// Works with ANY monad that provides environment access
<F extends WitnessArity<TypeArity.Unary>> Kind<F, UserProfile>
    fetchProfile(MonadReader<F, AppConfig> env, String userId) {
  return For.from(env, env.ask())
      .yield(config -> callApi(config.apiUrl(), userId));
}
```

The function says: *"Give me something that can read an `AppConfig`, and I will produce a `UserProfile` inside whatever monad you choose."* The caller provides a `ReaderTMonadReader`, a custom test instance, or any future implementation that satisfies `MonadReader`. The function never changes.

```
    ┌──────────────────────────────────────────────────────────────┐
    │  CONCRETE STACK (before MTL)                                 │
    │                                                              │
    │  fetchProfile : ReaderT<Future, AppConfig, UserProfile>      │
    │                                                              │
    │  Locked to Future. Must rewrite for Id, IO, VTask.           │
    └──────────────────────────────────────────────────────────────┘

                              ▼  refactor  ▼

    ┌──────────────────────────────────────────────────────────────┐
    │  MTL CAPABILITY (after)                                      │
    │                                                              │
    │  fetchProfile : MonadReader<F, AppConfig> → Kind<F, Profile> │
    │                                                              │
    │  Works with ReaderT<Future>, ReaderT<IO>, ReaderT<Id>, ...   │
    │  Business logic unchanged. Only the instance varies.         │
    └──────────────────────────────────────────────────────────────┘
```

This is the same idea behind Java interfaces like `List` vs `ArrayList`. You program to the capability, not the implementation. MTL applies that principle to monadic effects.

---

## The Capability Family

Higher-Kinded-J provides three MTL-style interfaces, each extending `Monad<F>`. Together with the existing `MonadError`, they form a family of four capability abstractions:

```
    ┌────────────────────────────────────────────────────────────┐
    │                        Monad<F>                            │
    │                     (of, map, flatMap)                     │
    │                                                            │
    │         ┌──────────────┬──────────────┬──────────────┐     │
    │         │              │              │              │     │
    │         ▼              ▼              ▼              ▼     │
    │   MonadReader     MonadState     MonadWriter    MonadError │
    │   <F, R>          <F, S>         <F, W>         <F, E>     │
    │                                                            │
    │   ask()           get()          tell(w)       raiseError()│
    │   local(f, ma)    put(s)         listen(ma)    handleError │
    │   reader(f)       modify(f)      pass(ma)                  │
    │   asks(f)         gets(f)        listens(f,ma)             │
    │                   inspect(f)     censor(f, ma)             │
    └────────────────────────────────────────────────────────────┘
```

Each interface declares a single, orthogonal capability. A function can require one, two, or all of them by accepting multiple parameters. This is the MTL pattern: **capabilities as type class constraints**.

| Capability | What It Provides | Analogy |
|------------|-----------------|---------|
| **MonadReader** | Read-only access to a shared environment | A global `AppConfig` everyone can read but nobody can change |
| **MonadState** | Read-write access to threaded state | A mutable variable that passes through each step |
| **MonadWriter** | Append-only output accumulation | A log file that each step writes to but never reads |
| **MonadError** | Typed error raising and recovery | A `try-catch` block with domain-specific error types |

Because each interface extends `Monad<F>`, every MTL instance is also a full monad. You get `of`, `map`, `flatMap`, and `ap` for free alongside the capability-specific operations. No need for a separate monad parameter.

---

## When to Use MTL vs Concrete Transformers

| Scenario | Recommendation |
|----------|---------------|
| Single module, one transformer stack | **Concrete transformer.** Simpler. Less indirection. |
| Library code consumed by multiple callers | **MTL.** Callers choose their own stack. |
| Polymorphic function used across different stacks | **MTL.** Write once, interpret many ways. |
| Testing with simplified stacks | **MTL.** Swap `ReaderT<CompletableFuture>` for `ReaderT<Id>` in tests. |
| Learning or prototyping | **Concrete transformer.** Easier to follow the types. |

~~~admonish note title="MTL Is Not Always Better"
MTL adds a layer of abstraction. If your entire application uses a single transformer stack and will never change it, the indirection of MTL buys you nothing. Use it when the abstraction pays for itself: in libraries, in polymorphic utility functions, and in code that genuinely needs to run against multiple stack configurations.
~~~

---

~~~admonish info title="In This Section"
- **[MonadReader](mtl_reader.md)** -- Read-only environment access. Inject configuration, database URLs, API keys, and other dependencies without threading parameters through every function.

- **[MonadState](mtl_state.md)** -- Mutable state management. Read and update state as a computation progresses, with automatic state threading through `flatMap` chains.

- **[MonadWriter](mtl_writer.md)** -- Append-only output accumulation. Build audit trails, diagnostic logs, and computation summaries that travel with the result.

- **[Combining Capabilities](mtl_combining.md)** -- Using multiple MTL interfaces together. Concrete instances, ForState integration, and practical patterns for multi-capability functions.
~~~

---

## Section Contents

1. [MonadReader](mtl_reader.md) -- Environment access and scoped modification
2. [MonadState](mtl_state.md) -- State threading and mutation
3. [MonadWriter](mtl_writer.md) -- Output accumulation and log inspection
4. [Combining Capabilities](mtl_combining.md) -- Multi-capability functions and concrete instances

---

**Previous:** [WriterT](writert_transformer.md)
**Next:** [MonadReader](mtl_reader.md)
