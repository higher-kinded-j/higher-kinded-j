# Hands-On Learning

> _"You look at where you're going and where you are and it never makes sense, but then you look back at where you've been and a pattern seems to emerge."_
>
> – Robert M. Pirsig, *Zen and the Art of Motorcycle Maintenance*

---

Reading about functional programming is one thing. Writing it is another entirely. This chapter is the writing part: thirteen short journeys, each a single test file we open in our IDE and complete by replacing `answerRequired()` with working code. Tests stay red until the solution is right, which makes feedback immediate and the loop tight.

We can read every other chapter in the book without ever opening this one (the Effect Path API, Optics, and Monad Transformers chapters are designed for that), but most readers find that the patterns "click" only after they have spent forty minutes typing them out. That is what this chapter is for.

~~~admonish tip title="Already Productive? Skip to the Capstone"
If we have already shipped something with the library, the **[One Line, Six Layers](../hkts/one_line_six_layers.md)** anchor in the Foundations chapter is the densest tour of the stack. Each journey here teaches one of the tokens in that single expression; **Tutorial00_OneLineSixLayers** ties them together as a setup check.
~~~

---

## How The Journeys Stack

```
       Effect API Journey               our usual entry point
   ┌───────────────────────────────────────────┐
   │  Path / EitherPath / MaybePath / TryPath  │   the API most code uses
   │  ForPath, recover, contexts               │
   └───────────────────────────────────────────┘
                         ▲
                         │  rests on
                         │
       Foundations Journeys (Core Types)
   ┌───────────────────────────────────────────┐
   │  Kind, Functor, Applicative, Monad        │   the abstractions
   │  MonadError, Natural Transformations      │
   └───────────────────────────────────────────┘
                         ▲
                         │  applied through
                         │
       Optics, Expression, Concurrency, Resilience
   ┌───────────────────────────────────────────┐
   │  Lens, Prism, Traversal, Focus DSL        │   immutable updates
   │  ForState, ForPath.par                    │   workflow shape
   │  VTask, Scope, Resource                   │   structured concurrency
   │  Circuit Breaker, Saga, Retry, Bulkhead   │   failure handling
   └───────────────────────────────────────────┘
```

We can take the journeys bottom-up (Foundations → Effect API → applications), top-down (Effect API first, fill in Foundations only when we want the theory), or pick one specialism and dive in. The [Learning Paths](learning_paths.md) page suggests sequences for each goal.

---

~~~admonish info title="In This Chapter"
- **Interactive Tutorials** – How the exercise pattern works, what to expect from a red test, and how to use the solutions.
- **Core Types Journeys** – Three journeys building from `Kind<F, A>` through `MonadError` to advanced patterns like Coyoneda and Free Applicative.
- **Effect API Journey** – The recommended user-facing API for working with functional effects in Java.
- **Concurrency Journeys** – Two journeys on virtual threads and structured concurrency with `VTask`, `Scope`, and `Resource`.
- **Optics Journeys** – Four journeys progressing from Lens basics through Traversals, the Free Monad DSL, and the Focus DSL.
- **Expression Journeys** – Two journeys on comprehension patterns: `ForState` for named state workflows and `ForPath.par` for parallel composition.
- **Resilience Patterns** – Circuit Breaker, Saga, Retry, and Bulkhead, applied to `VTask` and the Path API.
- **Learning Paths, Solutions Guide, Troubleshooting** – Curated sequences, how to use solutions effectively, and fixes for common stumbles.
~~~

---

## Thirteen Journeys

| Journey | Focus | Duration | Exercises |
|---------|-------|----------|-----------|
| **Core: Foundations** | HKT simulation, Functor, Applicative, Monad | ~40 min | 24 |
| **Core: Error Handling** | MonadError, concrete types, real-world patterns | ~30 min | 20 |
| **Core: Advanced** | Natural Transformations, Coyoneda, Free Applicative | ~40 min | 26 |
| **Effect API** | Effect paths, ForPath, Effect Contexts | ~65 min | 15 |
| **Concurrency: VTask** | Virtual threads, Par combinators, VTaskPath, VTaskContext | ~45 min | 16 |
| **Concurrency: Scope & Resource** | Structured concurrency, resource bracket, cleanup | ~30 min | 12 |
| **Optics: Lens & Prism** | Lens basics, Prism, Affine | ~40 min | 30 |
| **Optics: Traversals** | Traversals, composition, practical applications | ~40 min | 27 |
| **Optics: Fluent & Free** | Fluent API, Free Monad DSL | ~35 min | 22 |
| **Optics: Focus DSL** | Type-safe path navigation, container widening | ~35 min | 29 |
| **Expression: ForState** | Named record state, lens threading, zoom | ~25 min | 11 |
| **Expression: ForPath Parallel** | Applicative parallel composition for Path types | ~20 min | 9 |
| **Resilience Patterns** | Circuit Breaker, Saga, Retry, Bulkhead, Path API resilience | ~40 min | 22 |

Start wherever interests us most. The pattern Pirsig describes applies precisely here: midway through an exercise on Applicative composition, the relationship between `map` and `ap` may feel arbitrary; three tutorials later, building a validation pipeline, it clicks. Looking back, the earlier struggle makes sense. That *is* the learning process.

---

## The Learning Loop

```
    ┌─────────────────────────────────────────────────────────────┐
    │                                                             │
    │    READ  ──────►  WRITE  ──────►  RUN  ──────►  OBSERVE     │
    │      │              │              │               │        │
    │      ▼              ▼              ▼               ▼        │
    │   Exercise      Replace        Execute          Red or      │
    │   description   answerRequired()  test          Green?      │
    │                                                             │
    │                         │                                   │
    │                         ▼                                   │
    │              ┌──────────┴──────────┐                        │
    │              │                     │                        │
    │           GREEN                   RED                       │
    │         (Next exercise)     (Read error, iterate)           │
    │                                                             │
    └─────────────────────────────────────────────────────────────┘
```

The loop is simple. The understanding it produces is not. Expect moments of confusion; they are signs that learning is happening, not that something is wrong.

---

## Chapter Contents

1. [Interactive Tutorials](tutorials_intro.md) - How the exercise system works
2. [Core Types Journeys](coretypes/ch_intro.md) - `Kind` basics through advanced patterns
3. [Effect API](effect/effect_journey.md) - The recommended user-facing API
4. [Concurrency Journeys](concurrency/ch_intro.md) - VTask, Scope, Resource
5. [Optics Journeys](optics/ch_intro.md) - Lens, Prism, Traversal, Focus DSL
6. [Expression Journeys](expression/ch_intro.md) - ForState and ForPath.par
7. [Resilience Patterns](resilience/resilience_journey.md) - Circuit Breaker, Saga, Retry, Bulkhead
8. [Learning Paths](learning_paths.md) - Recommended journey sequences
9. [Solutions Guide](solutions_guide.md) - Reference implementations
10. [Troubleshooting](troubleshooting.md) - Common issues and solutions

---

**Next:** [Interactive Tutorials](tutorials_intro.md)
