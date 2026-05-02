# Path or Transformer? When to Drop Down

Most workflows you write in Higher-Kinded-J live happily on the [Effect Path API](../effect/ch_intro.md). `EitherPath`, `MaybePath`, `OptionalPath`, `ReaderPath`, `WithStatePath`, and `WriterPath` cover the same ground as their transformer counterparts and add a fluent, Java-friendly facade that hides witness types and `Kind` widening. This page exists for the moments when a Path no longer fits and you need the raw transformer.

~~~admonish info title="What You'll Learn"
- The three signals that mean you have outgrown the Path API
- Why the chapter ahead is optional for most readers
- Where to go next if a Path type already covers your case
~~~

---

## The default answer is "use the Path"

If you are starting a new workflow, reach for a Path type first. The mapping is straightforward:

| Path type | Replaces transformer |
|-----------|----------------------|
| `EitherPath<E, A>` | `EitherT<F, E, A>` |
| `MaybePath<A>` / `OptionalPath<A>` | `MaybeT<F, A>` / `OptionalT<F, A>` |
| `ReaderPath<R, A>` | `ReaderT<F, R, A>` |
| `WithStatePath<S, A>` | `StateT<S, F, A>` |
| `WriterPath<W, A>` | `WriterT<F, W, A>` |

Path types compose with [`ForPath` comprehensions](../effect/forpath_comprehension.md), integrate with [optics](../effect/focus_integration.md), and surface errors at compile time through the [HKJ Gradle plugin](../tooling/compile_checks.md). They are the recommended interface.

The chapter ahead is for the cases where the Path API genuinely cannot help. There are three of them.

---

## Signal 1: You need an outer monad Path does not wrap

The Path API is opinionated about its outer monad. Each Path type is built on top of a specific underlying effect (`Either`, `Optional`, `CompletableFuture` via `CompletableFuturePath`, and so on), and you cannot swap that out.

If you need to combine an inner effect (such as typed errors) with a *different* outer monad, you have to drop down to the transformer. Common cases:

- A third-party library returns `Mono<Either<E, A>>`, `IO<Either<E, A>>`, or some other library-specific shape that wraps an `Either`. `EitherPath` cannot reach inside that container; `EitherT<F, E, A>` can, given a `Monad<F>` for the outer effect.
- You are integrating with a custom IO or task type that has a `Monad` instance but no Path counterpart.
- You need an effect stack like `EitherT<VTaskKind.Witness, E, A>` for virtual-thread-backed work that returns typed errors before the result is awaited.

When the *outer* effect is what does not fit, the transformer is the right tool.

---

## Signal 2: You are writing polymorphic library code

Path types fix both the inner and outer effect at the call site. That is exactly what you want for application code, but it is the wrong shape for a library that wants to accept any caller's stack.

If you are publishing a function that other teams will compose with their own effect choices, MTL capabilities are usually a better fit than a concrete Path. A function that declares "I need to read an `AppConfig`" with the signature

```java
<F extends WitnessArity<TypeArity.Unary>> Kind<F, ConnectionString>
    buildConnectionString(MonadReader<F, AppConfig> env) { ... }
```

works against any stack the caller assembles, including stacks that wrap effects you have never heard of. The same function written against `ReaderPath<AppConfig, ConnectionString>` would force every caller into the Path's outer effect.

The same reasoning applies to `MonadState` over `WithStatePath` and `MonadWriter` over `WriterPath`. See [MTL Capabilities](mtl_capabilities.md) for the full story.

---

## Signal 3: You are integrating with existing `Kind<F, ...>` code

The Path API hides `Kind<F, A>` from its users. That is a feature for application code; it is a problem when you are working with code that already exposes raw `Kind<F, ...>` values, for example:

- Existing transformer code in the same codebase you cannot refactor today
- Library code (including parts of Higher-Kinded-J itself) that returns `Kind` shapes
- Bridging across modules where one side has adopted Paths and the other has not

In those cases the lift from `Kind<F, A>` into a Path and back is mostly noise. Working in the transformer layer directly, with `EitherT.fromKind(...)` to bridge in and `.value()` to bridge back out, is shorter.

---

## Decision summary

```
    ┌──────────────────────────────────────────────────────────┐
    │              PATH OR TRANSFORMER?                        │
    ├──────────────────────────────────────────────────────────┤
    │                                                          │
    │  Are you writing application code with a standard        │
    │  effect (CompletableFuture, IO, sync)?                   │
    │    └──▶  Use the Path API                                │
    │                                                          │
    │  Do you need a custom or third-party outer monad?        │
    │    └──▶  Drop to the transformer                         │
    │                                                          │
    │  Are you publishing a function for other teams           │
    │  to compose with their own stack?                        │
    │    └──▶  Use an MTL capability                           │
    │                                                          │
    │  Are you integrating with existing Kind<F, ...> code?    │
    │    └──▶  Drop to the transformer                         │
    │                                                          │
    └──────────────────────────────────────────────────────────┘
```

If none of those signals apply, the rest of this chapter is reference material rather than required reading.

---

## Where to go from here

If a Path type covers your case, leave this chapter and go to:

- [Effect Path API Quickstart](../effect/quickstart.md), three runnable examples in 150 lines
- [Path Types Overview](../effect/path_types.md), the reference for every Path
- [ForPath Comprehension](../effect/forpath_comprehension.md), composition syntax for Paths

If you do need a transformer:

- [Quickstart](quickstart.md), three runnable transformer examples
- [Stack Archetypes](archetypes.md), seven named patterns
- [Migration Cookbook](migration_cookbook.md), Path-to-transformer translations
- [MTL Capabilities](mtl_capabilities.md) for polymorphic library code

~~~admonish tip title="See Also"
- [Effect Path API Introduction](../effect/ch_intro.md), the recommended starting point
- [Transformers at a Glance](transformers_at_a_glance.md), reference card for every transformer
- [Common Compiler Errors](common_errors.md), what to do when the type checker disagrees with you
~~~

---

**Previous:** [Monad Transformers Introduction](ch_intro.md)
**Next:** [Quickstart](quickstart.md)
