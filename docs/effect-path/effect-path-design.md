# EffectPath API Design Document

> **Status**: Living Document (v3.0)
> **Last Updated**: 2025-12-14
> **Scope**: Phase 1-2 (Maybe, Either, Try, IO, Validation, Id, Optional, Generic) with extension architecture
> **Java Baseline**: Java 25 (RELEASE_25) - leverages pattern matching, unnamed variables, sequenced collections

## Overview

This document describes the design of the EffectPath API, a fluent composition layer for higher-kinded-j's effect types. The design draws inspiration from the FocusPath optics DSL to provide a unified mental model for both data navigation and effect composition.

## Motivation

### Current Pain Points

1. **Scattered Entry Points**: Users must know about multiple classes per type (`MaybeMonad.INSTANCE`, `MaybeKindHelper.MAYBE`, `Maybe.just()`)
2. **Verbose Type Signatures**: `Kind<MaybeKind.Witness, A>` is verbose compared to `Maybe<A>`
3. **Widen/Narrow Ceremony**: Converting between concrete types and `Kind<>` requires explicit calls
4. **Discoverability**: Hard to discover what operations are available for a given type

### Goals

1. **Unified Mental Model**: Use the same `via`/`then` vocabulary as FocusPath for effect composition
2. **Progressive Complexity**: Beginners use simple instance methods; advanced users access full HKT power
3. **Zero Ceremony for Common Cases**: Direct operations on types without wrappers
4. **Full Backward Compatibility**: Existing code continues to work unchanged
5. **Leverage Existing Types**: Build on Maybe, Either, Try, IO - no reinventing wheels
6. **Annotation-Driven Extensibility**: Support code generation from the start
7. **Plugin Architecture**: Allow third-party extensions without modifying core

## Design Principles

### Principle 1: Build on Existing Types

The EffectPath API does **not** introduce new effect types. Instead, it provides fluent wrappers around existing higher-kinded-j types:

| Existing Type | Path Wrapper | Purpose |
|---------------|--------------|---------|
| `Maybe<A>` | `MaybePath<A>` | Optional value composition |
| `Either<E, A>` | `EitherPath<E, A>` | Error-aware composition |
| `Try<A>` | `TryPath<A>` | Exception-handling composition |
| `IO<A>` | `IOPath<A>` | Side-effect composition |
| `Validated<E, A>` | `ValidationPath<E, A>` | Error-accumulating validation (Phase 2) |
| `Id<A>` | `IdPath<A>` | Identity wrapper, trivial monad (Phase 2) |
| `Optional<A>` | `OptionalPath<A>` | Java stdlib Optional bridge (Phase 2) |
| `Kind<F, A>` | `GenericPath<F, A>` | Custom monad escape hatch (Phase 2) |

### Principle 2: FocusPath-Style Composition

The Path wrappers use the same vocabulary as FocusPath:

```java
// FocusPath (data navigation)
FocusPath<Company, String> ceoName = companyFocus
    .via(Company::ceo)
    .via(Person::name);

// EffectPath (effect composition) - same vocabulary
MaybePath<String> userName = MaybePath.just(userId)
    .via(id -> userRepo.findById(id))
    .via(user -> user.profile())
    .map(Profile::displayName);
```

### Principle 3: Thin Wrappers

Path types are thin wrappers that delegate to underlying types:

```java
public final class MaybePath<A> {
    private final Maybe<A> value;  // Wraps existing type

    public <B> MaybePath<B> map(Function<A, B> f) {
        return new MaybePath<>(value.map(f));  // Delegates
    }

    public Maybe<A> run() {
        return value;  // Unwrap when done
    }
}
```

### Principle 4: Opt-In Complexity

Users can work at three levels:

1. **Direct Type Usage** (no paths): `maybe.map(f).flatMap(g)`
2. **Path Wrappers**: `MaybePath.of(maybe).via(f).via(g).run()`
3. **Generic HKT**: `EffectPath<F, A>` with explicit typeclass evidence

### Principle 5: Sealed but Extensible

Use sealed interfaces for exhaustive pattern matching while providing escape hatches:

```java
public sealed interface Chainable<A>
    permits MaybePath, EitherPath, TryPath, IOPath, ValidatedPath, GenericPath {
    // GenericPath permits custom extensions
}
```

### Principle 6: Annotation-Driven from Day One

Design for annotation processing from the start to avoid rewrites:

```java
@PathSource(witness = MaybeKind.Witness.class)
public sealed interface Maybe<A> { ... }
```

## Architecture

### Layer Structure

```
┌─────────────────────────────────────────────────────────────────────────┐
│ Layer 5: Annotation Processing & Code Generation                         │
│          @PathSource, @GeneratePathBridge, PathProcessor                 │
├─────────────────────────────────────────────────────────────────────────┤
│ Layer 4: Plugin/Discovery Architecture                                   │
│          PathRegistry, PathProvider SPI, ServiceLoader integration       │
├─────────────────────────────────────────────────────────────────────────┤
│ Layer 3: Generic Interfaces                                              │
│          EffectPath<F,A>, MonadicPath<F,A>, FalliblePath<F,E,A>         │
│          (For library authors and advanced generic programming)          │
├─────────────────────────────────────────────────────────────────────────┤
│ Layer 2: Concrete Path Types                                             │
│          MaybePath<A>, EitherPath<E, A>, TryPath<A>, IOPath<A>          │
│          (For intermediate users wanting composition)                    │
├─────────────────────────────────────────────────────────────────────────┤
│ Layer 1: Existing HKJ Types (UNCHANGED)                                  │
│          Maybe<A>, Either<E, A>, Try<A>, IO<A>                          │
│          (Foundation - full backward compatibility)                      │
└─────────────────────────────────────────────────────────────────────────┘
```

### Type Hierarchy (Sealed with Extension Point)

```
Composable<A>                        (base sealed interface)
    │
    ├── Combinable<A>                (adds zipWith)
    │       │
    │       └── Chainable<A>         (adds via, then, flatMap)
    │               │
    │               ├── Recoverable<E, A>    (adds recover, recoverWith)
    │               │       │
    │               │       ├── MaybePath<A>
    │               │       ├── EitherPath<E, A>
    │               │       ├── TryPath<A>
    │               │       └── ValidationPath<E, A>
    │               │
    │               ├── Effectful<A>         (adds run semantics)
    │               │       │
    │               │       └── IOPath<A>
    │               │
    │               ├── IdPath<A>            (identity monad, Phase 2)
    │               ├── OptionalPath<A>      (Optional bridge, Phase 2)
    │               └── GenericPath<F, A>    (custom monad escape hatch)
    │
    └── Accumulating<E, A>           (error accumulation, Phase 2)
            │
            └── ValidationPath<E, A> (implements both Chainable & Accumulating)
```

### Capability-Based Interface Design

```java
// Minimal composition capability
public sealed interface Composable<A>
    permits Chainable, GenericPath {

    <B> Composable<B> map(Function<? super A, ? extends B> f);
    Composable<A> peek(Consumer<? super A> observer);
}

// Monadic composition
public sealed interface Chainable<A> extends Composable<A>
    permits Recoverable, Effectful, GenericPath {

    <B> Chainable<B> via(Function<? super A, ? extends Chainable<B>> f);
    default <B> Chainable<B> then(Function<? super A, ? extends Chainable<B>> f) {
        return via(f);
    }
    default <B> Chainable<B> andThen(Chainable<B> next) {
        return via(_ -> next);
    }
}

// Error recovery capability
public sealed interface Recoverable<E, A> extends Chainable<A>
    permits MaybePath, EitherPath, TryPath, ValidatedPath {

    Recoverable<E, A> recover(Function<? super E, ? extends A> handler);
    Recoverable<E, A> recoverWith(Function<? super E, ? extends Recoverable<E, A>> handler);
    <E2> Recoverable<E2, A> mapError(Function<? super E, ? extends E2> f);
    Recoverable<E, A> orElse(Recoverable<E, A> fallback);
}

// Error accumulation (different from short-circuit)
public sealed interface Accumulating<E, A> extends Composable<A>
    permits ValidatedPath {

    <B, C> Accumulating<E, C> zipWith(Accumulating<E, B> other, BiFunction<A, B, C> f);
}
```

## Non-Functional Requirements

### Maintainability

1. **No God Classes**: Each Path type is a focused, single-responsibility class
2. **Small Interface Surface**: Capability interfaces have 3-5 methods each
3. **Delegation Pattern**: Paths delegate to underlying types, not reimplement
4. **Clear Package Boundaries**:
   - `org.higherkindedj.hkt.path` - concrete paths
   - `org.higherkindedj.hkt.path.spi` - plugin interfaces
   - `org.higherkindedj.hkt.path.annotation` - annotation types
   - `org.higherkindedj.hkt.path.processor` - annotation processor

### Testability

1. **Law Verification**: All paths must satisfy functor/monad laws
2. **Property-Based Testing**: jQwik tests for all operations
3. **Assertion Library**: Custom AssertJ assertions per path type

### Performance

1. **Zero-Cost Wrapper**: Path types are thin wrappers, minimal overhead
2. **No Reflection at Runtime**: Annotation processing is compile-time only
3. **Lazy Evaluation Preserved**: IOPath maintains IO's lazy semantics

### Extensibility

1. **Sealed + GenericPath**: Exhaustive matching + escape hatch
2. **SPI for Plugins**: `PathProvider` interface for third-party types
3. **Annotation Processor Extension**: Custom annotations can generate paths

## Class Size Guidelines

To avoid god classes, each class should follow these guidelines:

| Class Type | Max Lines | Max Methods | Rationale |
|------------|-----------|-------------|-----------|
| Path Type (e.g., MaybePath) | 400 | 30 | Single effect type, all related |
| Capability Interface | 100 | 8 | Focused capability |
| Factory Class | 200 | 20 | Static methods, may grow |
| SPI Interface | 50 | 5 | Contract only |
| Annotation | 30 | 5 | Metadata only |

### Decomposition Strategy

If a class exceeds guidelines:

1. **Extract Capability**: Move related methods to a new interface
2. **Extract Companion**: Move static/factory methods to companion class
3. **Extract Extension**: Use extension interfaces with default methods
4. **Extract Transformer**: Move complex transformations to utility class

## Sealed Type Growth Strategy

### Current Permits (Phase 2)

```java
public sealed interface Chainable<A> extends Combinable<A>
    permits Recoverable, Effectful, IdPath, OptionalPath, GenericPath {
    // MaybePath, EitherPath, TryPath, ValidationPath via Recoverable
    // IOPath via Effectful
}

public sealed interface Recoverable<E, A> extends Chainable<A>
    permits MaybePath, EitherPath, TryPath, ValidationPath {
}
```

### Types by Phase

1. **Phase 1 Types** ✅: MaybePath, EitherPath, TryPath, IOPath (core effects)
2. **Phase 2 Types** ✅: ValidationPath, IdPath, OptionalPath, GenericPath
3. **Phase 3 Types** (Future): Reader, Writer, State paths
4. **Plugin Types**: Via GenericPath + PathProvider SPI

### Versioning Strategy

When adding new permits:
1. New permits are **minor version bumps** (1.1 -> 1.2)
2. Existing code continues to work (compile-time exhaustiveness may warn)
3. Document migration path for switch expressions

### GenericPath Escape Hatch

```java
public final class GenericPath<F, A> implements Chainable<A> {
    private final Kind<F, A> value;
    private final Monad<F> monad;

    // Allows any monad to participate in Path composition
    // Used by plugins and advanced users
}
```

## Plugin/Discovery Architecture

### Service Provider Interface

```java
// In org.higherkindedj.hkt.path.spi
public interface PathProvider<F> {

    /** Witness type this provider handles */
    Class<?> witnessType();

    /** Create a path from a Kind */
    <A> Chainable<A> createPath(Kind<F, A> value);

    /** Monad instance for this type */
    Monad<F> monad();

    /** Optional: MonadError if applicable */
    default Optional<MonadError<F, ?>> monadError() {
        return Optional.empty();
    }
}
```

### Service Loader Registration

```java
// META-INF/services/org.higherkindedj.hkt.path.spi.PathProvider
org.higherkindedj.hkt.path.MaybePathProvider
org.higherkindedj.hkt.path.EitherPathProvider
com.example.custom.MyCustomPathProvider
```

### Path Registry

```java
public final class PathRegistry {
    private static final Map<Class<?>, PathProvider<?>> providers = new ConcurrentHashMap<>();

    static {
        ServiceLoader.load(PathProvider.class).forEach(p ->
            providers.put(p.witnessType(), p)
        );
    }

    @SuppressWarnings("unchecked")
    public static <F, A> Optional<Chainable<A>> createPath(Kind<F, A> value, Class<?> witness) {
        return Optional.ofNullable((PathProvider<F>) providers.get(witness))
            .map(p -> p.createPath(value));
    }
}
```

## Annotation-Driven Assembly

### Core Annotations

```java
// Mark a type as a Path source
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface PathSource {
    Class<?> witness();
    String pathClassName() default "";  // Generated name if empty
}

// Generate bridge methods
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface GeneratePathBridge {
    String factoryClassName() default "";
}

// Mark a method as path-composable
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface PathVia {
    String value() default "";  // Method name in generated path
}
```

### Annotation Processor Design

```java
@SupportedAnnotationTypes({
    "org.higherkindedj.hkt.path.annotation.PathSource",
    "org.higherkindedj.hkt.path.annotation.GeneratePathBridge"
})
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public class PathProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        // Process @PathSource - generate Path wrapper classes
        for (Element element : roundEnv.getElementsAnnotatedWith(PathSource.class)) {
            processPathSource((TypeElement) element);
        }

        // Process @GeneratePathBridge - generate bridge factory methods
        for (Element element : roundEnv.getElementsAnnotatedWith(GeneratePathBridge.class)) {
            processPathBridge((TypeElement) element);
        }

        return true;
    }
}
```

### Generated Code Example

```java
// Input: User-defined service
@GeneratePathBridge
public record UserService(
    @PathVia("findById") Function<Long, Maybe<User>> findUser,
    @PathVia("validate") Function<User, Either<ValidationError, User>> validateUser
) {}

// Generated: UserServicePaths.java
@Generated("org.higherkindedj.hkt.path.processor.PathProcessor")
public final class UserServicePaths {

    public static MaybePath<User> findById(UserService service, Long id) {
        return MaybePath.of(service.findUser().apply(id));
    }

    public static EitherPath<ValidationError, User> validate(UserService service, User user) {
        return EitherPath.of(service.validateUser().apply(user));
    }

    // Composed operations
    public static EitherPath<ValidationError, User> findAndValidate(
            UserService service, Long id) {
        return findById(service, id)
            .toEitherPath(ValidationError.notFound(id))
            .via(user -> validate(service, user).run());
    }
}
```

## Pain Point Mitigation Strategies

### Pain Point 1: Type Inference Challenges

**Problem**: Java struggles with nested generic inference.

**Mitigations**:

| Strategy | Example | When to Use |
|----------|---------|-------------|
| Typed factory | `Path.<ValidationError>right(user)` | When error type not inferrable |
| Builder pattern | `Path.either(ValidationError.class).right(user)` | Complex type construction |
| Witness parameter | `EitherPath.right(user, ValidationError.class)` | Explicit type passing |
| var keyword | `var path = Path.right(user)` | When full type verbose |

### Pain Point 2: Type Explosion

**Problem**: Many similar Path types could overwhelm users.

**Mitigations**:

1. **Progressive Disclosure**: Only Maybe/Either/Try in primary docs
2. **Single Entry Point**: `Path.` factory class
3. **IDE-Friendly**: Concrete types show relevant methods only
4. **Capability Grouping**: Interfaces group related types

### Pain Point 3: Sealed = Closed

**Problem**: Users can't add custom path types easily.

**Mitigations**:

1. **GenericPath Escape Hatch**: Any monad can participate
2. **PathProvider SPI**: Register custom providers
3. **Annotation Processor**: Generate paths for custom types

### Pain Point 4: Loss of Type Information

**Problem**: Converting between paths may lose error types.

**Mitigations**:

```java
// Explicit error handler required
public MaybePath<A> toMaybePath(Consumer<E> errorHandler);

// Witness access preserved
public interface Chainable<A> {
    default Class<?> witnessType() { ... }
}
```

### Pain Point 5: Debugging Difficulty

**Problem**: Long path chains hard to debug.

**Mitigations**:

```java
// traced() at any point
MaybePath.just(user)
    .traced((state) -> log.debug("After user: {}", state))
    .via(this::findProfile)
    .traced((state) -> log.debug("After profile: {}", state))

// toString() shows structure
"MaybePath(Just(User(id=123)))"
```

### Pain Point 6: Learning Curve

**Problem**: New abstractions require learning.

**Mitigations**:

1. **Consistent Vocabulary**: `via`/`then` everywhere
2. **Example-Rich Documentation**: Show before/after
3. **Migration Guide**: From raw types to paths
4. **IDE Templates**: Code snippets for common patterns

## Comparison with FocusPath

| Aspect | FocusPath | EffectPath |
|--------|-----------|------------|
| **Domain** | Data structure navigation | Effect composition |
| **Wraps** | Lens, Prism, Affine, Traversal | Maybe, Either, Try, IO |
| **`get`** | Extracts focused value | N/A (effects are lazy) |
| **`run`** | N/A | Extracts underlying effect |
| **`via`** | Compose with another optic | Compose with effectful function |
| **Hierarchy** | FocusPath -> AffinePath -> TraversalPath | Composable -> Chainable -> Recoverable |
| **Sealed** | Yes | Yes (with GenericPath escape) |

## Phase Plan

### Phase 1: Core Types (MVP) ✅ COMPLETE
- MaybePath, EitherPath, TryPath, IOPath
- Path factory class
- Basic capability interfaces (Composable, Combinable, Chainable, Recoverable, Effectful)
- Core test suite with 100% coverage
- Law verification tests (Functor/Monad laws)
- Book chapter documentation (7 chapters)
- Runnable examples (5 examples)

### Phase 2: Extended Types ✅ COMPLETE
- ValidationPath with error accumulation
- IdPath (identity monad)
- OptionalPath (Java Optional bridge)
- GenericPath (custom monad escape hatch)
- Accumulating interface (parallel to Combinable)
- PathOps utility class (sequence, traverse, firstSuccess)
- Cross-path conversion methods
- Property-based tests (jQwik)
- @GeneratePathBridge, @PathVia annotations
- PathProcessor for code generation
- Additional examples (AccumulatingValidation, PathOps, CrossPathConversions)

### Phase 3: Extensibility (Future)
- PathProvider SPI
- PathRegistry
- ServiceLoader integration
- Plugin documentation

### Phase 4: FocusPath Integration (Future)
- FocusPath integration bridges (deferred to assess Effect API usage patterns)
- FocusDSL interaction patterns

### Phase 5: Advanced Features (Future)
- Natural transformations
- Parallel composition
- Reader/Writer/State paths
- Performance optimization

## Summary

The EffectPath API provides:

1. **Unified vocabulary** (`via`/`then`) for data and effect composition
2. **Progressive complexity** from direct usage to generic programming
3. **Full backward compatibility** with existing higher-kinded-j code
4. **Clean types** that Java developers find approachable
5. **Synergy with optics** reinforcing a consistent mental model
6. **Extensibility** via sealed interfaces, SPI, and annotations
7. **Maintainability** through small, focused classes

The design prioritizes pragmatism over purity, recognizing that Java developers value clarity and IDE support over theoretical elegance.
