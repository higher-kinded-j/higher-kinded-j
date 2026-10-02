<!-- description: Hands-on Higher-Kinded-J exercises: journeys of failing tests you complete in your IDE, from the Kind encoding through effects and optics to DTO mapping. -->

# Hands-On Learning

> _"You look at where you're going and where you are and it never makes sense, but then you look back at where you've been and a pattern seems to emerge."_
>
> – Robert M. Pirsig, *Zen and the Art of Motorcycle Maintenance*

---

Reading about functional programming is one thing. Writing it is another entirely. This chapter is the writing part: nineteen journeys, each a few test files we open in our IDE and complete by replacing `answerRequired()` with working code. Tests stay red until the solution is right, which makes feedback immediate and the loop tight.

We can read every other chapter without ever opening this one; the Effect Path API, Optics, and Monad Transformers chapters are designed for that. Most readers find, though, that the patterns "click" only after typing them out. That is what this chapter is for.

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
   Optics, Expression, Concurrency, Context, Effect Handlers, Resilience
   ┌───────────────────────────────────────────┐
   │  Lens, Prism, Traversal, Focus DSL        │   immutable updates
   │  ForState, ForPath.par                    │   workflow shape
   │  VTask, Scope, Resource                   │   structured concurrency
   │  Request, security and trace contexts     │   context as values
   │  Effect algebras and interpreters         │   programs as data
   │  Circuit Breaker, Saga, Retry, Bulkhead   │   failure handling
   └───────────────────────────────────────────┘
```

We can take the journeys bottom-up (Foundations → Effect API → applications), top-down (Effect API first, fill in Foundations only when we want the theory), or pick one specialism and dive in. The [Learning Paths](learning_paths.md) page suggests sequences for each goal.

---

~~~admonish info title="In This Chapter"
- **Interactive Tutorials**: How the exercise pattern works, what to expect from a red test, and how to use the solutions.
- **Core Types Journeys**: Three journeys building from `Kind<F, A>` through `MonadError` to advanced patterns like Coyoneda and Free Applicative.
- **Effect API Journey**: The recommended user-facing API for working with functional effects in Java.
- **Monad Transformers Journey**: When the Path API isn't enough: async with absence, stacking, and MTL capabilities.
- **Concurrency Journeys**: Two journeys on virtual threads and structured concurrency with `VTask`, `Scope`, and `Resource`.
- **Context Journey**: `ScopedValue`-backed contexts for request, security and trace data, without `ThreadLocal`.
- **Effect Handlers Journey**: Effects as algebras, programs as values, and one program run through several interpreters.
- **Optics Journeys**: Six journeys progressing from Lens basics through Traversals, the Free Monad DSL, and the Focus DSL to batching, coupled updates, and the generated DTO boundary.
- **Expression Journeys**: Two journeys on comprehension patterns: `ForState` for named state workflows and `ForPath.par` for parallel composition.
- **Resilience Patterns**: Circuit Breaker, Saga, Retry, and Bulkhead, applied to `VTask` and the Path API.
- **Capstone**: One pipeline across effects, optics, resilience and concurrency.
- **Learning Paths, Solutions Guide, Troubleshooting**: Curated sequences, how to use solutions effectively, and fixes for common stumbles.
~~~

---

## Nineteen Journeys {#journeys}

| Journey | Focus | Exercises |
|---------|-------|-----------|
| **[Core: Foundations](coretypes/foundations_journey.md)** | HKT simulation, Functor, Applicative, Monad | 37 |
| **[Core: Error Handling](coretypes/error_handling_journey.md)** | MonadError, concrete types, real-world patterns | 26 |
| **[Core: Advanced](coretypes/advanced_journey.md)** | Natural Transformations, Coyoneda, Free Applicative | 26 |
| **[Effect API](effect/effect_journey.md)** | Effect paths, ForPath, Effect Contexts | 17 |
| **[Monad Transformers](transformers/transformers_journey.md)** | When Path isn't enough, async + absence, stacking, MTL | 29 |
| **[Concurrency: VTask](concurrency/vtask_journey.md)** | Virtual threads, Par combinators, VTaskPath, VTaskContext | 28 |
| **[Concurrency: Scope & Resource](concurrency/scope_resource_journey.md)** | Structured concurrency, resource bracket, cleanup | 20 |
| **[Context](context/ch_intro.md)** | `ScopedValue` contexts for requests, security and tracing | 59 |
| **[Effect Handlers](effecthandlers/ch_intro.md)** | Effect algebras, programs as values, several interpreters | 19 |
| **[Optics: Lens & Prism](optics/lens_prism_journey.md)** | Lens basics, Prism, Affine | 30 |
| **[Optics: Traversals](optics/traversals_journey.md)** | Traversals, composition, practical applications | 28 |
| **[Optics: Fluent & Free](optics/fluent_free_journey.md)** | Fluent API, Free Monad DSL | 22 |
| **[Optics: Focus DSL](optics/focus_dsl_journey.md)** | Type-safe path navigation, container widening | 90 |
| **[Optics: Batching & Coupled Updates](optics/batching_journey.md)** | Request batching, plan guardrails, coupled lenses | 13 |
| **[Optics: Boundary Mapping](optics/boundary_mapping_journey.md)** | Multi-edit, ValidatedPrism, generated record mapping, boundary edge cases | 19 |
| **[Expression: ForState](expression/forstate_journey.md)** | Named record state, lens threading, zoom | 13 |
| **[Expression: ForPath Parallel](expression/forpath_parallel_journey.md)** | Applicative parallel composition for Path types | 9 |
| **[Resilience Patterns](resilience/resilience_journey.md)** | Circuit Breaker, Saga, Retry, Bulkhead, Path API resilience | 24 |
| **[Capstone: One Line, Six Layers Grows Up](capstone/capstone_journey.md)** | One pipeline across effects, optics, resilience and concurrency | 7 |

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
4. [Monad Transformers](transformers/transformers_journey.md) - When the Path API isn't enough
5. [Concurrency Journeys](concurrency/ch_intro.md) - VTask, Scope, Resource
6. [Context Journey](context/ch_intro.md) - Request, security and trace contexts as values
7. [Effect Handlers Journey](effecthandlers/ch_intro.md) - Effect algebras and their interpreters
8. [Optics Journeys](optics/ch_intro.md) - Lens through Focus DSL, batching, boundary mapping
9. [Expression Journeys](expression/ch_intro.md) - ForState and ForPath.par
10. [Resilience Patterns](resilience/resilience_journey.md) - Circuit Breaker, Saga, Retry, Bulkhead
11. [Capstone: One Line, Six Layers Grows Up](capstone/capstone_journey.md) - One pipeline across every layer
12. [Learning Paths](learning_paths.md) - Recommended journey sequences
13. [Solutions Guide](solutions_guide.md) - Reference implementations
14. [Troubleshooting](troubleshooting.md) - Common issues and solutions

---

**Next:** [Interactive Tutorials](tutorials_intro.md)
