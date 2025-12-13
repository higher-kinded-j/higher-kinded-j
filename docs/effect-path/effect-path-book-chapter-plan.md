# Effect Path API - Book Chapter Plan

> **Status**: Confirmed Selections
> **Last Updated**: 2025-01-15

## Confirmed Documentation Choices

### Opening Quote: Option E (Map/Territory Theme) ✓

> *"A map is not the territory it represents, but, if correct, it has a similar structure to the territory, which accounts for its usefulness."*
>
> – Alfred Korzybski, *Science and Sanity*

**Rationale**: Path types "map" effect operations to a fluent structure. The wrapper isn't the underlying monad, but it preserves the structure—and that's why it's useful. Philosophical depth matching the Pynchon quotes elsewhere in the book.

---

### Chapter Introduction: Draft A (Problem-Solution Focus) ✓

```markdown
# Effect Path API: Fluent Effect Composition

> *"A map is not the territory it represents, but, if correct, it has a similar
> structure to the territory, which accounts for its usefulness."*
>
> – Alfred Korzybski, *Science and Sanity*

---

Service layers return effects. A repository method yields `Maybe<User>`. A validation
function produces `Either<Error, Order>`. A file operation returns `Try<Contents>`.
Each effect type has its own API. Each requires its own handling pattern. String a few
together and the code becomes a nested mess of maps, flatMaps, and explicit unwrapping.

The Effect Path API provides a way through.

Rather than working with raw effect types, you wrap them in Path types: `MaybePath`,
`EitherPath`, `TryPath`, `IOPath`. These thin wrappers expose a unified vocabulary—
the same `via`, `map`, and `recover` operations regardless of the underlying effect.
Chain them together, convert between them, extract results at the end. The underlying
complexity remains (it must), but the Path contains it.

The vocabulary deliberately mirrors the Focus DSL from the optics chapters. Where
FocusPath navigates through *data structures*, EffectPath navigates through *effect
types*. Both use `via` for composition. Both provide fluent, chainable operations.
If you've used optics, the patterns will feel familiar. If you haven't, the consistency
will help when you do.

---
```

---

## Book Chapter Placement

### Recommended: New "Effects" Section

```markdown
# SUMMARY.md structure

# Type Classes
- [Introduction](functional/ch_intro.md)
  - [Functor](functional/functor.md)
  - [Applicative](functional/applicative.md)
  - [Monad](functional/monad.md)
  - ...

# Monads in Practice
- [Introduction](monads/ch_intro.md)
  - [Maybe](monads/maybe_monad.md)
  - [Either](monads/either_monad.md)
  - [Try](monads/try_monad.md)
  - [IO](monads/io_monad.md)
  - ...

# Effect Path API  ← NEW SECTION
- [Introduction](effects/ch_intro.md)
  - [Effect Path Overview](effects/effect_path_overview.md)
  - [Capability Interfaces](effects/capabilities.md)
  - [Path Types](effects/path_types.md)
  - [Service Bridges](effects/service_bridges.md)  ← Phase 2
  - [Custom Effects](effects/custom_effects.md)    ← Phase 3
  - [Patterns and Recipes](effects/patterns.md)

# Optics I: Fundamentals
- [Introduction](optics/ch1_intro.md)
  ...
```

### Rationale for Placement

1. **After "Monads in Practice"**: Users understand Maybe, Either, Try, IO
2. **Before "Optics"**: Effect Path is simpler; Optics is more advanced
3. **Parallel to Focus DSL**: Both are "fluent APIs over complex machinery"

---

## Capability Hierarchy Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      EFFECT PATH CAPABILITY HIERARCHY                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│                           ┌────────────────┐                                │
│                           │   Composable   │   map(), peek()                │
│                           │    (Functor)   │                                │
│                           └───────┬────────┘                                │
│                                   │                                         │
│                           ┌───────┴────────┐                                │
│                           │   Combinable   │   zipWith(), map2()            │
│                           │ (Applicative)  │                                │
│                           └───────┬────────┘                                │
│                                   │                                         │
│                           ┌───────┴────────┐                                │
│                           │   Chainable    │   via(), flatMap(), then()     │
│                           │    (Monad)     │                                │
│                           └───────┬────────┘                                │
│                                   │                                         │
│               ┌───────────────────┼───────────────────┐                     │
│               │                                       │                     │
│       ┌───────┴────────┐                     ┌────────┴───────┐             │
│       │   Recoverable  │                     │    Effectful   │             │
│       │ (MonadError)   │                     │      (IO)      │             │
│       └───────┬────────┘                     └────────┬───────┘             │
│               │                                       │                     │
│    ┌──────────┼──────────┬──────────┐                │                     │
│    │          │          │          │                │                     │
│ ┌──┴───┐ ┌────┴────┐ ┌───┴──┐      │           ┌────┴────┐               │
│ │Maybe │ │ Either  │ │ Try  │      │           │   IO    │               │
│ │ Path │ │  Path   │ │ Path │      │           │  Path   │               │
│ └──────┘ └─────────┘ └──────┘      │           └─────────┘               │
│                                     │                                       │
│                              ┌──────┴──────┐                                │
│                              │  Validated  │   (Phase 2)                    │
│                              │    Path     │                                │
│                              └─────────────┘                                │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Path API Processing Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          EFFECT PATH PROCESSING FLOW                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   ┌──────────────────────┐                                                  │
│   │    Service Method    │                                                  │
│   │  Maybe<User> findById│                                                  │
│   └──────────┬───────────┘                                                  │
│              │                                                              │
│              ▼                                                              │
│   ┌──────────────────────┐                                                  │
│   │    Path.maybe()      │    Wrap in MaybePath                            │
│   │   Entry Point        │                                                  │
│   └──────────┬───────────┘                                                  │
│              │                                                              │
│              ▼                                                              │
│   ┌──────────────────────┐                                                  │
│   │       .map()         │    Transform value                              │
│   │  user -> user.name   │    (Functor operation)                          │
│   └──────────┬───────────┘                                                  │
│              │                                                              │
│              ▼                                                              │
│   ┌──────────────────────┐                                                  │
│   │       .via()         │    Chain dependent computation                  │
│   │  name -> lookup(name)│    (Monad operation)                            │
│   └──────────┬───────────┘                                                  │
│              │                                                              │
│              ▼                                                              │
│   ┌──────────────────────┐                                                  │
│   │    .toEitherPath()   │    Convert to different effect                  │
│   │   Error.notFound()   │    (Cross-path conversion)                      │
│   └──────────┬───────────┘                                                  │
│              │                                                              │
│              ▼                                                              │
│   ┌──────────────────────┐                                                  │
│   │     .recover()       │    Handle errors                                │
│   │   err -> default     │    (MonadError operation)                       │
│   └──────────┬───────────┘                                                  │
│              │                                                              │
│              ▼                                                              │
│   ┌──────────────────────┐                                                  │
│   │       .run()         │    Extract underlying type                      │
│   │  → Either<E, A>      │    (Terminal operation)                         │
│   └──────────────────────┘                                                  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## "What You'll Learn" Section

```markdown
~~~admonish info title="What You'll Learn"
- **Path Types** – Fluent wrappers (`MaybePath`, `EitherPath`, `TryPath`, `IOPath`)
  that provide unified composition over higher-kinded-j's effect types. Each wrapper
  delegates to its underlying type while exposing a consistent API.

- **Capability Interfaces** – The hierarchy that powers composition: `Composable`
  (mapping), `Combinable` (independent combination), `Chainable` (sequencing),
  `Recoverable` (error handling), and `Effectful` (deferred execution).

- **The `via` Pattern** – Chain dependent computations using the same vocabulary as
  optics. Where FocusPath's `via` navigates data, EffectPath's `via` navigates
  effects.

- **Type Conversion** – Convert freely between path types: `MaybePath` to `EitherPath`
  (providing an error), `EitherPath` to `TryPath` (wrapping the error), and more.

- **Error Recovery** – Handle failures gracefully with `recover`, `orElse`, and
  `recoverWith`. Transform error types with `mapError`.

- **Service Integration** – (Phase 2) Use `@GeneratePathBridge` to automatically
  generate Path-returning bridges for your service interfaces.

- **Custom Effects** – (Phase 3) Use `@PathSource` to add Path support for your
  own effect types.
~~~
```

---

## Proposed Chapter Structure

```markdown
# Effect Path API

1. **Introduction** (ch_intro.md)
   - Opening quote (Korzybski)
   - The problem: verbose effect composition
   - The solution: unified Path wrappers
   - Relationship to Focus DSL

2. **Effect Path Overview** (effect_path_overview.md)
   - Creating paths with `Path.maybe()`, `Path.either()`, etc.
   - Basic transformations with `map`
   - Chaining with `via`
   - Terminal operations with `run`, `getOrElse`

3. **Capability Interfaces** (capabilities.md)
   - Composable: functor operations
   - Combinable: applicative operations
   - Chainable: monad operations
   - Recoverable: error handling
   - Effectful: deferred execution

4. **Path Types** (path_types.md)
   - MaybePath: optional value paths
   - EitherPath: error-aware paths
   - TryPath: exception-handling paths
   - IOPath: side-effect paths

5. **Composition Patterns** (composition.md)
   - Sequential composition with `via`
   - Independent combination with `zipWith`
   - Error recovery patterns
   - Debugging with `traced` and `peek`

6. **Type Conversions** (conversions.md)
   - Maybe ↔ Either ↔ Try
   - Lifting to IO
   - Extracting with terminal operations

7. **Service Bridges** (service_bridges.md) — *Phase 2*
   - @GeneratePathBridge annotation
   - @PathVia for method marking
   - Generated code patterns

8. **Custom Effects** (custom_effects.md) — *Phase 3*
   - @PathSource for library authors
   - Extending the Path hierarchy

9. **Patterns and Recipes** (patterns.md)
   - Validation pipelines
   - Service layer composition
   - Repository patterns
   - Error handling strategies
```

---

## Summary of Confirmed Decisions

| Item | Selection | Status |
|------|-----------|--------|
| **Opening Quote** | Option E: Korzybski (Map/Territory) | ✓ Confirmed |
| **Introduction Draft** | Draft A: Problem-Solution Focus | ✓ Confirmed |
| **Book Placement** | New "Effect Path API" section after Monads, before Optics | ✓ Confirmed |
| **Phase 2 includes** | `@GeneratePathBridge` + `@PathVia` | ✓ Confirmed |
| **Phase 3 includes** | `@PathSource` + `@PathConfig` | ✓ Confirmed |
| **Branch coverage** | 100% for all components | ✓ Confirmed |
