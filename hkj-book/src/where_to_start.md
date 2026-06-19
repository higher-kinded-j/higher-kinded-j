# Where to Start

> *"Would you tell me, please, which way I ought to go from here?"*
>
> *"That depends a good deal on where you want to get to," said the Cat.*
>
> -- Lewis Carroll, *Alice's Adventures in Wonderland*

Alice's reply was that she did not much care where, at which point the Cat
told her any road would do. This page is built on the opposite assumption:
that you do care, that you already have a problem in front of you, and that
what you need is the shortest route to the chapter that solves it.

~~~admonish info title="What You'll Learn"
- Which tool fits the problem you have, before you know the library's vocabulary
- How the four tool families combine when one is not enough
- Where to go next once you have picked your direction
~~~

---

## The one question

> *What are you actually trying to do?*

Pick the branch that matches. If two match, read both and combine them;
the [Combining tools](#combining-tools) section below covers the common
overlaps.

1. [I'm working with values that might fail or be absent](#1-values-that-might-fail-or-be-absent)
2. [I'm reading or updating data inside immutable records](#2-reading-or-updating-nested-data)
3. [I'm doing concurrent, async, or deferred I/O](#3-concurrent-async-or-deferred-io)
4. [I'm sequencing several effects together](#4-sequencing-several-effects)
5. [I'm writing library or polymorphic code that others will compose with](#5-library-or-polymorphic-code)

---

## 1. Values that might fail or be absent

You have a function that may return nothing, throw, or report a domain error.
You want chaining, short-circuiting, and a clear extraction point.

| Your situation | Reach for |
|---|---|
| Value may be missing; the reason does not matter | [`MaybePath`](effect/path_maybe.md) |
| Bridging to Java's `Optional` | [`OptionalPath`](effect/path_optional.md) |
| Failure carries a typed domain error you control | [`EitherPath`](effect/path_either.md) |
| Failure comes from code that throws exceptions | [`TryPath`](effect/path_try.md) |
| You want **all** validation errors, not just the first | [`ValidationPath`](effect/path_validation.md) |

If you are not sure between `EitherPath` and `TryPath`, ask: *would I want to
unit-test the error branch?* If yes, `EitherPath`. If the error is genuinely
exceptional (file system blew up), `TryPath` is honest about it.

Go to: [Effect Path Quickstart](effect/quickstart.md) ·
[Core Paths](effect/effect_path_overview.md) ·
[full Path decision tree](effect/path_types.md)

---

## 2. Reading or updating nested data

You have records (often deeply nested), sealed hierarchies, or collections,
and you want to read or update one field without rebuilding the surrounding
structure by hand.

| Your situation | Reach for |
|---|---|
| Required field on a record, get/set | [`Lens`](optics/lenses.md), via [Focus DSL](optics/focus_dsl.md) |
| Optional field, or a field that may be absent | [`Affine`](optics/affine.md) / [`AffinePath`](optics/affine.md) |
| One variant of a sealed type | [`Prism`](optics/prisms.md) |
| Every element of a collection | [`Traversal`](optics/traversals.md) |
| Two equivalent representations | [`Iso`](optics/iso.md) |
| The type isn't yours to annotate | [`@ImportOptics`](optics/importing_optics.md) or an [`OpticsSpec`](optics/optics_spec_interfaces.md) |

In practice almost everyone starts with the [Focus DSL](optics/focus_dsl.md)
and annotations like `@GenerateLenses`, `@GenerateFocus`, `@GeneratePrisms`;
hand-written optic composition is rarely needed.

Go to: [Optics Quickstart](optics/quickstart.md) ·
[full Optics decision trees](optics/decision_trees.md)

---

## 3. Concurrent, async, or deferred I/O

You are calling APIs, talking to a database, reading files, fanning out work
across threads, and you want a sane composition story.

| Your situation | Reach for |
|---|---|
| Defer a side effect; do not run it until you ask | [`IOPath`](effect/path_io.md) |
| Concurrent work, virtual threads, structured concurrency | [`VTaskPath`](effect/path_vtask.md) |
| Lazy pull-based streaming, possibly infinite | [`VStreamPath`](effect/path_vstream.md) |
| Existing `CompletableFuture<T>` returns you must integrate with | [`CompletableFuturePath`](monads/cf_monad.md) |
| Memoise a deferred computation | [`LazyPath`](monads/lazy_monad.md) |
| Deeply recursive algorithm that would blow the stack | [`TrampolinePath`](effect/path_trampoline.md) |

If you are starting a new service from scratch, default to
[`VTaskPath`](effect/path_vtask.md); it gives you virtual-thread
concurrency without forcing the rest of your code reactive.

Go to: [VTask Monad](monads/vtask_monad.md) ·
[Structured Concurrency](monads/vtask_scope.md)

---

## 4. Sequencing several effects

You have more than one of the above to do, in order. Two answers, depending
on how regular the pipeline is.

| Your situation | Reach for |
|---|---|
| A linear pipeline: do A, then B with A's result, then C | The Path's own `.flatMap` / `.map` chain |
| A pipeline that pulls from several independent sources | [`ForPath` comprehension](effect/forpath_comprehension.md) |
| Independent steps you want to run in parallel | [`ForPath` parallel](effect/forpath_par.md) |
| The same operation across a collection | [`ForPath` traverse](effect/forpath_traverse.md) |
| A DSL you want to interpret in multiple ways (prod, audit, dry-run) | [`FreePath`](effect/path_free.md) and [Effect Handlers](effect/effect_handlers_intro.md) |

`ForPath` is the answer most of the time once a pipeline has more than two
steps and one of them needs a name. It reads top-to-bottom and removes the
nesting that `flatMap` chains accumulate.

Go to: [For Comprehension](functional/for_comprehension.md) ·
[Composition Patterns](effect/composition.md)

---

## 5. Library or polymorphic code

You are not writing application code; you are writing a function that
**other teams will compose into their effect stack**. The Path API fixes the
outer effect at the call site, which is wrong for this case.

| Your situation | Reach for |
|---|---|
| You need to read configuration without fixing the caller's effect | [`MonadReader`](transformers/mtl_reader.md) |
| You need to read **and modify** state polymorphically | [`MonadState`](transformers/mtl_state.md) |
| You need to record an audit log polymorphically | [`MonadWriter`](transformers/mtl_writer.md) |
| You need to combine several of the above | [MTL Combining Capabilities](transformers/mtl_combining.md) |
| Bridging to a third-party type like `Mono<Either<E, A>>` | [`EitherT`](transformers/eithert_transformer.md) directly |

If you are not in one of these cases, you do not need the transformer
chapter. The full reasoning lives in
[Path or Transformer?](transformers/when_to_drop_to_transformers.md).

Go to: [MTL Capabilities](transformers/mtl_capabilities.md) ·
[Transformers Quickstart](transformers/quickstart.md)

---

## Combining tools

The four families overlap. Here are the five combinations that come up most
often in real code, each with the right entry point.

### Validation **and** nested update

You need to update a field inside a record, but the update can fail.

- **Tools:** Focus DSL (to navigate) + `EitherPath` or `ValidationPath` (to
  carry the error).
- **API:** Call `.focus(lens)` on the Path, or `lens.toEitherPath()` from
  Focus.
- **Read:** [Optics Integration](effect/focus_integration.md) ·
  [Capstone: Effects Meet Optics](effect/capstone_focus_effect.md) ·
  [Tutorial14_FocusEffectBridge](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial14_FocusEffectBridge.java)

### Async **and** typed errors

You need to call an async API that can return a domain error before the
result is materialised.

- **Tools:** `VTaskPath` for the async, `EitherPath` for the error, composed
  through `ForPath`. For polymorphic code or third-party `Mono<Either<...>>`
  shapes, drop to `EitherT<VTaskKind.Witness, E, A>`
  ([EitherT](transformers/eithert_transformer.md)).
- **Read:** [Composition Patterns](effect/composition.md) ·
  [Path or Transformer?](transformers/when_to_drop_to_transformers.md#signal-1-you-need-an-outer-monad-path-does-not-wrap)

### Async **and** resilience (retry, circuit breaker, bulkhead, saga)

You have async or deferred work and you need to make it survive partial
failure.

- **Tools:** [`VTaskPath`](effect/path_vtask.md) or [`IOPath`](effect/path_io.md) underneath the resilience combinators.
- **Read:** [Resilience Patterns](resilience/ch_intro.md) ·
  [Retry](resilience/retry.md) ·
  [Saga](resilience/saga.md)

### Pure logic **and** I/O at the edges

You want a functional core (pure, testable, no effects) and an imperative
shell that runs the effects.

- **Tools:** Plain data types in the core, [`IOPath`](effect/path_io.md) / [`VTaskPath`](effect/path_vtask.md) at the
  boundary, `.unsafeRun()` only at the entry point (`main`, controller,
  test).
- **Read:** [the hkj-arch skill](tooling/claude_code_skills.md), [Effect Boundary Integration](spring/effect_boundary_integration.md).

### I call other services over HTTP

You have a Spring service that calls another, and you want the callee's typed
error to survive the network hop instead of collapsing into a status code.

- **Tools:** [`@HkjHttpClient`](spring/declarative_http_clients.md) on a Spring
  `@HttpExchange` interface, returning [`EitherPath`](effect/path_either.md) or
  [`VTaskPath`](effect/path_vtask.md); base URL and timeouts via
  `spring.http.serviceclient.<group>.*`.
- **Read:** [Declarative HTTP Clients](spring/declarative_http_clients.md).

### A DSL **and** multiple interpreters

You want to describe a workflow once and run it differently in production,
tests, audits, or dry-runs.

- **Tools:** [`FreePath`](effect/path_free.md) for the description, [`@EffectAlgebra`](effect/effect_handlers_intro.md) for the
  vocabulary, interpreters for each run mode.
- **Read:** [Effect Handlers](effect/effect_handlers_intro.md) ·
  [Free Monad](monads/free_monad.md) · [the hkj-effects skill](tooling/claude_code_skills.md).

---

## Escape hatches

If none of the above fits, you are in one of these situations:

- **A custom monad with no Path.** Use [`GenericPath<F, A>`](effect/path_generic.md)
  given a `Monad<F>` instance.
- **You are extending the library itself with a new HKT.** Read
  [Extending](hkts/extending-simulation.md) for the witness arity contract.
- **You are stuck on a compiler error and the type doesn't make sense.**
  See [Common Compiler Errors](effect/compiler_errors.md) for effects,
  [Optics Compiler Errors](optics/compiler_errors.md) for optics, or
  [Transformer Errors](transformers/common_errors.md) for transformers.

---

## Quick reference

| If you're doing this... | Start here |
|---|---|
| Absent values | [`MaybePath`](effect/path_maybe.md) |
| Typed domain errors | [`EitherPath`](effect/path_either.md) |
| Wrapping throwing code | [`TryPath`](effect/path_try.md) |
| Accumulating validation errors | [`ValidationPath`](effect/path_validation.md) |
| Deferred side effect | [`IOPath`](effect/path_io.md) |
| Async on virtual threads | [`VTaskPath`](effect/path_vtask.md) |
| Lazy streaming | [`VStreamPath`](effect/path_vstream.md) |
| Reading or updating one nested field | [Focus DSL](optics/focus_dsl.md) |
| Sealed-type variant | [Prism](optics/prisms.md) |
| Every element of a collection | [Traversal](optics/traversals.md) |
| Linear pipeline | [`.flatMap`](effect/composition.md) on the Path |
| Pipeline pulling from several sources | [`ForPath`](effect/forpath_comprehension.md) |
| Validation + nested update | [Optics Integration](effect/focus_integration.md) |
| Async + typed errors | [Composition Patterns](effect/composition.md) |
| Resilience around async work | [Resilience Patterns](resilience/ch_intro.md) |
| Polymorphic library function | [MTL Capabilities](transformers/mtl_capabilities.md) |
| Third-party outer effect | [`EitherT`](transformers/eithert_transformer.md) |
| DSL with multiple interpreters | [`FreePath`](effect/path_free.md) + [Effect Handlers](effect/effect_handlers_intro.md) |

~~~admonish tip title="Still not sure?"
Start with the [Quickstart](quickstart.md). Most production workflows use
one or two Paths and a Focus DSL, and that combination is enough to ship.
Reach for transformers, MTL, or Free monads only when one of the signals
on this page actually fires.
~~~

---

**Previous:** [Cheat Sheet](cheatsheet.md)
**Next:** [Effect Path API](effect/ch_intro.md)
