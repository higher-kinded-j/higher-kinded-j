# Monad Transformers: Combining Effects

> *"You can't just bolt two things together and expect them to work."*
>
> – Richard Feynman

---

Every Java developer who has worked with `CompletableFuture` and `Either` separately knows they compose beautifully in isolation. `CompletableFuture` handles asynchronicity. `Either` handles typed errors. Each offers clean `map` and `flatMap` operations, each respects its own laws, and each is a pleasure to use alone.

Then you need both at once.

The result, `CompletableFuture<Either<DomainError, Result>>`, is technically correct in the same way that a car engine strapped to a bicycle is technically a vehicle. The types nest, the generics compile, and then you try to `flatMap` across the combined structure and discover that Java has no opinion about how these two contexts should interact. You write nested `thenApply` calls, peel back layers manually, and watch the indentation grow. The ergonomics deteriorate rapidly.

Monad transformers solve this by wrapping the nested structure in a new type that provides a single, unified monadic interface. `EitherT<CompletableFutureKind.Witness, DomainError, Result>` is still `CompletableFuture<Either<DomainError, Result>>` underneath, but it offers one `flatMap` that sequences both the async and error-handling layers together. The nesting is hidden; the composition is restored.

Higher-Kinded-J provides five transformers, each adding a specific capability to any outer monad:

---

## The Stacking Concept

<pre style="line-height:1.4;font-size:0.95em">
<span style="color:#F44336">    ┌─────────────────────────────────────────────────────────────┐
    │  WITHOUT TRANSFORMER                                        │
    │                                                             │
    │    CompletableFuture&lt;Either&lt;Error, Result&gt;&gt;                 │
    │                                                             │
    │    future.thenApply(either -&gt;                               │
    │        either.map(result -&gt;                                 │
    │            either2.map(r2 -&gt;                                │
    │                ...)))   // Nesting grows unboundedly        │
    └─────────────────────────────────────────────────────────────┘</span>

<span style="color:#4CAF50">    ┌─────────────────────────────────────────────────────────────┐
    │  WITH TRANSFORMER                                           │
    │                                                             │
    │    EitherT&lt;FutureWitness, Error, Result&gt;                    │
    │                                                             │
    │    eitherT                                                  │
    │        .flatMap(result -&gt; operation1(result))               │
    │        .flatMap(r1 -&gt; operation2(r1))                       │
    │        .map(r2 -&gt; finalTransform(r2))  // Flat!             │
    └─────────────────────────────────────────────────────────────┘</span>
</pre>

Same semantics. Vastly different ergonomics.

---

## Which Transformer Do I Need?

```
    ┌──────────────────────────────────────────────────────────┐
    │              WHICH TRANSFORMER DO I NEED?                │
    ├──────────────────────────────────────────────────────────┤
    │                                                          │
    │  "My operation might fail with a typed error"            │
    │    └──▶  EitherT                                         │
    │                                                          │
    │  "My operation might return nothing"                     │
    │    ├──▶  OptionalT  (java.util.Optional)                 │
    │    └──▶  MaybeT     (Higher-Kinded-J Maybe)              │
    │                                                          │
    │  "My operation needs shared configuration"               │
    │    └──▶  ReaderT                                         │
    │                                                          │
    │  "My operation needs to track changing state"            │
    │    └──▶  StateT                                          │
    │                                                          │
    └──────────────────────────────────────────────────────────┘
```

---

## Available Transformers

| Transformer | Inner Effect | Use Case |
|-------------|--------------|----------|
| `EitherT<F, E, A>` | Typed error (`Either<E, A>`) | Async operations that fail with domain errors |
| `MaybeT<F, A>` | Optional value (`Maybe<A>`) | Async operations that might return nothing |
| `OptionalT<F, A>` | Java Optional (`Optional<A>`) | Same as MaybeT, for java.util.Optional |
| `ReaderT<F, R, A>` | Environment (`Reader<R, A>`) | Dependency injection in effectful contexts |
| `StateT<S, F, A>` | State (`State<S, A>`) | Stateful computation within other effects |

---

~~~admonish info title="In This Chapter"
- **The Problem** – Monads don't compose naturally. A `CompletableFuture<Either<E, A>>` requires nested operations that become unwieldy. Transformers restore ergonomic composition.
- **EitherT** – Adds typed error handling to any monad. Wrap your async operations with `EitherT` to get a single `flatMap` that handles both async sequencing and error propagation.
- **OptionalT** – Lifts `java.util.Optional` into another monadic context. When your async operation might return nothing, OptionalT provides clean composition.
- **MaybeT** – The same capability as OptionalT but for the library's `Maybe` type. Choose based on whether you're using Optional or Maybe elsewhere.
- **ReaderT** – Threads environment dependencies through effectful computations. Combine dependency injection with async operations or error handling.
- **StateT** – Manages state within effectful computations. Track state changes across async boundaries or error-handling paths.
~~~

---

## Chapter Contents

1. [Monad Transformers](transformers.md) - Why monads stack poorly and what transformers solve
2. [EitherT](eithert_transformer.md) - Typed errors in any monadic context
3. [OptionalT](optionalt_transformer.md) - Java Optional lifting
4. [MaybeT](maybet_transformer.md) - Maybe lifting
5. [ReaderT](readert_transformer.md) - Environment threading
6. [StateT](statet_transformer.md) - State management in effectful computation

---

**Next:** [Monad Transformers](transformers.md)
