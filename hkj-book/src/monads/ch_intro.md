# Foundations: Core Types

> *"Everything was beautiful and nothing hurt."*
>
> – Kurt Vonnegut, *Slaughterhouse-Five*

---

~~~admonish tip title="Prefer Effect Paths for Everyday Use"
While this chapter documents the underlying core types, **most applications should use the [Effect Path API](../effect/ch_intro.md)** for cleaner composition and unified error handling. Effect Paths like `EitherPath`, `MaybePath`, and `TryPath` wrap these types and provide:

- A consistent API across all effect types (`map`, `via`, `recover`)
- Seamless integration with the [Focus DSL](../optics/focus_dsl.md)
- Railway-oriented programming for flat, readable pipelines

Use this chapter as a **reference** for understanding the foundations that power the Effect Path API.
~~~

---

Within a monadic context, certain complexities simply vanish. Null checks disappear inside `Maybe`. Error propagation becomes implicit within `Either`. Asynchronous callbacks flatten into sequential steps with `CompletableFuture`. The mess remains (it must), but the monad contains it, and within that containment, everything is beautiful and nothing hurts.

This chapter surveys the types that Higher-Kinded-J supports: seventeen distinct monads, each representing a different computational context. Some wrap standard Java types (`Optional`, `List`, `CompletableFuture`). Others are library-defined (`Maybe`, `Either`, `IO`, `Validated`). Still others handle advanced concerns like state management (`State`, `Reader`, `Writer`) or stack-safe recursion (`Trampoline`, `Free`).

Each type has its purpose. `Id` does nothing, which makes it useful as a baseline. `Maybe` and `Optional` handle absence. `Either` and `Try` handle failure with information. `IO` defers side effects. `Lazy` memoises computations. `Reader` threads configuration. `State` manages mutable state purely. `Writer` accumulates logs. `Validated` gathers all errors rather than stopping at the first.

The art lies in choosing the right context for the problem at hand. Sometimes you need fail-fast semantics; sometimes you need error accumulation. Sometimes laziness helps; sometimes it hinders. This chapter provides the vocabulary. Experience provides the judgement.

~~~admonish title="Hands On Practice"
[Tutorial07_RealWorld.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial07_RealWorld.java)
~~~

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
- **Identity** – The trivial monad that simply wraps a value unchanged. Useful as a baseline for testing and as a placeholder in generic code.
- **Maybe and Optional** – Two approaches to representing absent values. Maybe is the library's own implementation; Optional wraps Java's `java.util.Optional`.
- **Either** – Represents computations that can fail with typed error information. Unlike exceptions, the error type is explicit in the signature.
- **Try** – Captures exceptions as values, converting thrown errors into a failed Try. Bridges imperative exception handling with functional composition.
- **Validated** – Like Either, but accumulates all errors rather than stopping at the first. Essential for form validation and batch processing.
- **List and Stream** – Model non-deterministic computation where operations can produce multiple results. Stream adds laziness for infinite or large sequences.
- **CompletableFuture** – Wraps Java's CompletableFuture for asynchronous computation, enabling monadic composition of async operations.
- **IO** – Defers side effects until explicitly run, maintaining referential transparency. Effects are described, not executed, until interpretation.
- **Lazy** – Delays computation until the value is needed, then memoises the result. Useful for expensive computations that may never be required.
- **Reader** – Threads a read-only environment through computations. A functional approach to dependency injection.
- **State** – Threads mutable state through a computation while maintaining purity. Each step receives state and produces new state alongside its result.
- **Writer** – Accumulates output (typically logs) alongside computation results. The output type must be a Monoid for combining.
- **Trampoline** – Enables stack-safe recursion by converting recursive calls into a heap-allocated data structure.
- **Free** – Represents programs as data structures that can be interpreted in multiple ways. The foundation for building embedded DSLs.
- **Const** – Ignores its second type parameter, carrying only the first. Useful for accumulating values during traversals.
~~~

---

## Chapter Contents

1. [Supported Types](supported-types.md) - Overview of all seventeen monadic types
2. [CompletableFuture](cf_monad.md) - Asynchronous computation
3. [Either](either_monad.md) - Typed, informative failure
4. [Identity](identity.md) - The simplest monad, doing nothing
5. [IO](io_monad.md) - Deferred side effects
6. [Lazy](lazy_monad.md) - Memoised computation
7. [List](list_monad.md) - Multiple values (non-determinism)
8. [Maybe](maybe_monad.md) - Handling absence
9. [Optional](optional_monad.md) - Java's Optional as a monad
10. [Reader](reader_monad.md) - Environment access
11. [State](state_monad.md) - Pure state threading
12. [Stream](stream_monad.md) - Lazy sequences
13. [Trampoline](trampoline_monad.md) - Stack-safe recursion
14. [Free](free_monad.md) - Programs as data
15. [Free Applicative](free_applicative.md) - Static analysis of programs
16. [Coyoneda](coyoneda.md) - Free functor for any type
17. [Try](try_monad.md) - Exception capture
18. [Validated](validated_monad.md) - Error accumulation
19. [Writer](writer_monad.md) - Output accumulation
20. [Const](const_type.md) - Phantom-typed constants

~~~admonish info title="Hands-On Learning"
Practice real-world monad patterns in [Tutorial 07: Real World](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial07_RealWorld.java) (6 exercises, ~12 minutes).
~~~

---

**Next:** [Supported Types](supported-types.md)
