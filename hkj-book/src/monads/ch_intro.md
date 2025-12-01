# Monads in Practice

> *"Everything was beautiful and nothing hurt."*
>
> — Kurt Vonnegut, *Slaughterhouse-Five*

---

Within a monadic context, certain complexities simply vanish. Null checks disappear inside `Maybe`. Error propagation becomes implicit within `Either`. Asynchronous callbacks flatten into sequential steps with `CompletableFuture`. The mess remains—it must—but the monad contains it, and within that containment, everything is beautiful and nothing hurts.

This chapter surveys the types that Higher-Kinded-J supports: seventeen distinct monads, each representing a different computational context. Some wrap standard Java types (`Optional`, `List`, `CompletableFuture`). Others are library-defined (`Maybe`, `Either`, `IO`, `Validated`). Still others handle advanced concerns like state management (`State`, `Reader`, `Writer`) or stack-safe recursion (`Trampoline`, `Free`).

Each type has its purpose. `Id` does nothing—which makes it useful as a baseline. `Maybe` and `Optional` handle absence. `Either` and `Try` handle failure with information. `IO` defers side effects. `Lazy` memoises computations. `Reader` threads configuration. `State` manages mutable state purely. `Writer` accumulates logs. `Validated` gathers all errors rather than stopping at the first.

The art lies in choosing the right context for the problem at hand. Sometimes you need fail-fast semantics; sometimes you need error accumulation. Sometimes laziness helps; sometimes it hinders. This chapter provides the vocabulary. Experience provides the judgement.

---

## The Landscape

```
    ┌─────────────────────────────────────────────────────────────┐
    │  ABSENCE                                                    │
    │    Maybe         Optional         (value or nothing)        │
    └─────────────────────────────────────────────────────────────┘

    ┌─────────────────────────────────────────────────────────────┐
    │  FAILURE                                                    │
    │    Either        Try         Validated   (error handling)   │
    └─────────────────────────────────────────────────────────────┘

    ┌─────────────────────────────────────────────────────────────┐
    │  EFFECTS                                                    │
    │    IO       CompletableFuture    (deferred/async execution) │
    └─────────────────────────────────────────────────────────────┘

    ┌─────────────────────────────────────────────────────────────┐
    │  COLLECTIONS                                                │
    │    List          Stream           (multiple values)         │
    └─────────────────────────────────────────────────────────────┘

    ┌─────────────────────────────────────────────────────────────┐
    │  ENVIRONMENT                                                │
    │    Reader       State       Writer   (contextual computation)│
    └─────────────────────────────────────────────────────────────┘

    ┌─────────────────────────────────────────────────────────────┐
    │  CONTROL                                                    │
    │    Lazy      Trampoline      Free    (evaluation strategy)  │
    └─────────────────────────────────────────────────────────────┘
```

---

## Choosing the Right Type

A rough decision guide:

| Need | Type | Why |
|------|------|-----|
| Value might be absent | `Maybe` or `Optional` | Explicit absence handling |
| Operation might fail | `Either<E, A>` | Typed error with information |
| Might throw exceptions | `Try<A>` | Captures Throwable |
| Accumulate all errors | `Validated<E, A>` | Fail-slow validation |
| Defer side effects | `IO<A>` | Referential transparency |
| Run asynchronously | `CompletableFuture<A>` | Non-blocking execution |
| Multiple results | `List<A>` or `Stream<A>` | Non-determinism |
| Read configuration | `Reader<R, A>` | Dependency injection |
| Track state changes | `State<S, A>` | Pure state management |
| Log operations | `Writer<W, A>` | Accumulate output |
| Deep recursion | `Trampoline<A>` | Stack safety |
| Build DSLs | `Free<F, A>` | Separate description from execution |

---

## What You'll Learn

~~~admonish info title="In This Chapter"
- **Identity** — The simplest monad, doing nothing
- **Maybe and Optional** — Handling absence
- **Either** — Typed, informative failure
- **Try** — Exception capture
- **Validated** — Error accumulation
- **List and Stream** — Multiple values
- **CompletableFuture** — Asynchronous computation
- **IO** — Deferred side effects
- **Lazy** — Memoised computation
- **Reader** — Environment access
- **State** — Pure state threading
- **Writer** — Output accumulation
- **Trampoline** — Stack-safe recursion
- **Free** — Programs as data
- **Const** — Phantom-typed constants
~~~

---

## Chapter Contents

