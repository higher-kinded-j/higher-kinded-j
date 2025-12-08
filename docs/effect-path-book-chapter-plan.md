
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

## Summary of Recommendations

| Item | Recommendation |
|------|----------------|
| **Phase 2 includes** | `@GeneratePathBridge` + `@PathVia` |
| **Phase 3 includes** | `@PathSource` + `@PathConfig` |
| **Branch coverage** | 100% for all components |
| **Book placement** | New "Effect Path API" section after Monads, before Optics |
| **Chapter review items** | Intro, graphics, diagrams, quotes - to be reviewed before commit |

---

## ✅ Confirmed Decisions

### Annotation Phasing: CONFIRMED

| Phase | Components | Status |
|-------|------------|--------|
| **Phase 1** | Core Path types (MaybePath, EitherPath, TryPath, IOPath) | Ready for implementation |
| **Phase 2** | `@GeneratePathBridge` + `@PathVia` for service bridges | **CONFIRMED** |
| **Phase 3** | `@PathSource` + `@PathConfig` for custom effect types | **CONFIRMED** |

**Rationale Summary:**

1. **Phase 2 for `@GeneratePathBridge`** (not Phase 1 or Phase 3):
    - Phase 1 needs stable Path APIs before generating code that depends on them
    - Users can provide feedback on Path API before locking in generated patterns
    - Immediate productivity boost once Phase 1 is complete
    - Works directly with core types—no custom effect types needed
    - Eliminates tedious manual wrapping: `Path.maybe(service.findById(id))`

2. **Phase 3 for `@PathSource`** (not earlier):
    - Advanced feature for library authors, not typical users
    - Requires understanding of HKT witness types
    - Lower frequency of use; most users work with core types
    - Phase 2 feedback will inform @PathSource design

---

## Documentation Items for Review

The following items have been drafted for your review before implementation begins:

### 1. Opening Quotes (Choose One)

Following the book's convention of literary quotes that illuminate the chapter's theme, here are options:

---

**Option A: The Journey/Path Theme** (Recommended)

> *"The only people for me are the mad ones, the ones who are mad to live, mad to talk, mad to be saved, desirous of everything at the same time, the ones who never yawn or say a commonplace thing, but burn, burn, burn like fabulous yellow roman candles exploding like spiders across the stars."*
>
> – Jack Kerouac, *On the Road*

*Rationale:* "On the Road" literally embodies the "path" metaphor. The quote captures the enthusiasm of chaining effects together—each operation burns into the next, building toward something explosive. Perfect for an API named "Path."

---

**Option B: The Simplicity Theme**

> *"Simplicity is the ultimate sophistication."*
>
> – Leonardo da Vinci (attributed)

*Rationale:* The Effect Path API hides HKT complexity behind a simple, fluent interface. This quote captures the design philosophy directly.

---

**Option C: The Flow/River Theme**

> *"No man ever steps in the same river twice, for it's not the same river and he's not the same man."*
>
> – Heraclitus

*Rationale:* Effects transform values as they flow through the path. Each `via` step produces something new. The river metaphor aligns with immutable functional composition.

---

**Option D: The Tools/Craft Theme**

> *"Give me six hours to chop down a tree and I will spend the first four sharpening the axe."*
>
> – Abraham Lincoln (attributed)

*Rationale:* The Path API is about having the right tools. Spending time to create fluent composition yields cleaner, more maintainable code.

---

**Option E: The Map/Territory Theme** (Pynchon Style)

> *"A map is not the territory it represents, but, if correct, it has a similar structure to the territory, which accounts for its usefulness."*
>
> – Alfred Korzybski, *Science and Sanity*

*Rationale:* Path types "map" effect operations to a fluent structure. The wrapper isn't the underlying monad, but it preserves the structure—and that's why it's useful. Philosophical depth matching the Pynchon quotes elsewhere.

---

**Option F: The Composition Theme**

> *"In the beginner's mind there are many possibilities, but in the expert's mind there are few."*
>
> – Shunryu Suzuki, *Zen Mind, Beginner's Mind*

*Rationale:* For beginners, effect handling seems complex with many approaches. The Path API provides the expert's focused solution: one clear way to compose effects.

---

### 2. Chapter Introduction Drafts (Choose One)

---

**Draft A: Problem-Solution Focus** (Recommended)

```markdown
# Effect Path API: Fluent Effect Composition

> *[Selected quote here]*
>
> – [Author], *[Work]*

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

**Draft B: Comparison/Contrast Focus**

```markdown
# Effect Path API: Fluent Effect Composition

> *[Selected quote here]*
>
> – [Author], *[Work]*

---

Consider two ways to compose a service call with validation:

**Without Path API:**
```java
Maybe<User> user = userService.findById(id);
if (user.isNothing()) {
    return Either.left(Error.notFound());
}
Either<ValidationError, User> validated = validate(user.get());
if (validated.isLeft()) {
    return validated.mapLeft(Error::fromValidation);
}
return Either.right(validated.get());
```

**With Path API:**
```java
return Path.maybe(userService.findById(id))
    .toEitherPath(Error.notFound())
    .via(user -> Path.either(validate(user)))
    .mapError(Error::fromValidation)
    .run();
```

Same logic. Vastly different ergonomics.

The Effect Path API wraps higher-kinded-j's effect types—`Maybe`, `Either`, `Try`,
`IO`—in fluent Path wrappers that enable chainable composition. Each wrapper
implements capability interfaces (`Composable`, `Chainable`, `Recoverable`) that
provide consistent operations across all effect types.

The design parallels the Focus DSL for optics. Where FocusPath composes navigation
through data structures, EffectPath composes operations across effect boundaries.
The vocabulary is intentionally shared: both use `via` for composition, both provide
fluent APIs, both hide complexity behind clean interfaces.

---
```

---

**Draft C: Conceptual/Philosophical Focus**

```markdown
# Effect Path API: Fluent Effect Composition

> *[Selected quote here]*
>
> – [Author], *[Work]*

---

A path is a journey through transformations.

In the optics chapters, FocusPath traces a journey through data: from an outer record,
through nested fields, to the value you seek. In this chapter, EffectPath traces a
different journey: through computational effects, from an initial value wrapped in
`Maybe` or `Either`, through transformations and validations, to the final result.

Both are paths. Both compose. Both hide complexity behind a fluent interface.

The effect types themselves—`Maybe`, `Either`, `Try`, `IO`—remain unchanged. The
Path wrappers are thin layers that expose a unified API: `map` to transform values,
`via` to chain dependent operations, `recover` to handle errors. The underlying
monadic machinery (functors, applicatives, monads) still powers everything. But you
need not think about it. You simply navigate the path.

This is the pattern: complexity contained, simplicity exposed. The same philosophy
drives the Focus DSL for optics. The same philosophy drives the Effect Path API.

---
```

---

### 3. Capability Hierarchy Diagram

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

### 4. Path API Processing Flow Diagram

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

### 5. Annotation Processing Flow Diagram (Phase 2)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        ANNOTATION PROCESSING FLOW                            │
│                            (Phase 2 Feature)                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   SOURCE CODE                  PROCESSOR                    GENERATED       │
│   ───────────                  ─────────                    ─────────       │
│                                                                             │
│   @GeneratePathBridge                                                       │
│   interface UserService {                                                   │
│       @PathVia                       ┌─────────────────┐                    │
│       Maybe<User> findById(Long);────▶  PathProcessor  │                    │
│       @PathVia                       │                 │                    │
│       Either<Err,User> save(User);───▶  • Validates    │                    │
│   }                                  │    annotations  │                    │
│                                      │  • Extracts     │                    │
│                                      │    method info  │                    │
│                                      │  • Generates    │                    │
│                                      │    bridge code  │                    │
│                                      └────────┬────────┘                    │
│                                               │                             │
│                                               ▼                             │
│                                      ┌─────────────────┐                    │
│                                      │ UserServicePaths │                   │
│                                      │ ─────────────────│                   │
│                                      │ static MaybePath │                   │
│                                      │   findById(...)  │                   │
│                                      │ static EitherPath│                   │
│                                      │   save(...)      │                   │
│                                      └─────────────────┘                    │
│                                                                             │
│   USAGE:                                                                    │
│   ───────                                                                   │
│                                                                             │
│   UserServicePaths.findById(service, 42L)                                   │
│       .map(User::name)                                                      │
│       .via(name -> UserServicePaths.findByName(service, name))             │
│       .run();                                                               │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### 6. "What You'll Learn" Section

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

### 7. Proposed Chapter Structure

```markdown
# Effect Path API

1. **Introduction** (ch_intro.md)
   - Opening quote
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