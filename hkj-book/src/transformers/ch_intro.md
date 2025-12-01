# Monad Transformers: Combining Effects

> *"Shall I project a world?"*
>
> — Thomas Pynchon, *The Crying of Lot 49*

---

A transformer projects one monad's world into another's. `EitherT` takes the world of typed errors and projects it into whatever outer monad you choose—`CompletableFuture`, `IO`, `List`. The result is a new monad that combines both effects: asynchronous computation *with* error handling, deferred execution *with* failure semantics.

The problem being solved is fundamental: monads don't compose naturally. You can have a `CompletableFuture<A>`. You can have an `Either<E, A>`. But a `CompletableFuture<Either<E, A>>`—whilst perfectly expressible in Java—becomes awkward to work with. Every operation requires nested `thenApply` and `map` calls, peeling back layers manually. The ergonomics deteriorate rapidly.

Transformers restore sanity. `EitherT<CompletableFutureKind.Witness, DomainError, Result>` presents a unified interface: a single `flatMap` sequences async operations that might fail; a single `map` transforms successful results; error handling works at the combined level. The nesting is still there—it must be—but the transformer hides it.

Higher-Kinded-J provides transformers for common combinations: `EitherT` for typed errors, `MaybeT` and `OptionalT` for optional values, `ReaderT` for dependency injection, and `StateT` for state threading. Each takes an outer monad and adds its specific capability.

---

## The Stacking Concept

```
    ┌─────────────────────────────────────────────────────────────┐
    │  WITHOUT TRANSFORMER                                        │
    │                                                             │
    │    CompletableFuture<Either<Error, Result>>                │
    │                                                             │
    │    future.thenApply(either ->                              │
    │        either.map(result ->                                 │
    │            either2.map(r2 ->                                │
    │                ...)))   // Nesting grows unboundedly        │
    └─────────────────────────────────────────────────────────────┘

    ┌─────────────────────────────────────────────────────────────┐
    │  WITH TRANSFORMER                                           │
    │                                                             │
    │    EitherT<FutureWitness, Error, Result>                   │
    │                                                             │
    │    eitherT                                                  │
    │        .flatMap(result -> operation1(result))              │
    │        .flatMap(r1 -> operation2(r1))                      │
    │        .map(r2 -> finalTransform(r2))  // Flat!            │
    └─────────────────────────────────────────────────────────────┘
```

Same semantics. Vastly different ergonomics.

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

## What You'll Learn

~~~admonish info title="In This Chapter"
- **The Problem** — Why monads stack poorly and what transformers solve
- **EitherT** — Typed errors in any monadic context
- **OptionalT** — Java Optional lifting
- **MaybeT** — Maybe lifting
- **ReaderT** — Environment threading
- **StateT** — State management in effectful computation
~~~

---

## Chapter Contents

