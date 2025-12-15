# Effect Path API - Phase 2 Implementation Plan

> **Status**: ✅ Phase 2 Complete (v1.1)
> **Last Updated**: 2025-12-14
> **Phase 1 Completion**: PR #277 merged
> **Java Baseline**: Java 25 (RELEASE_25)
> **Branch**: `claude/phase-2-planning-01KqSpsDjthXYoUB8UjBs6Dv`

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Phase 2 Scope Overview](#phase-2-scope-overview)
3. [Design Decisions (Confirmed)](#design-decisions-confirmed)
4. [Implementation Tasks](#implementation-tasks)
5. [Task Dependencies](#task-dependencies)
6. [File Structure](#file-structure)
7. [Testing Strategy](#testing-strategy)
8. [Documentation Requirements](#documentation-requirements)
9. [Success Criteria](#success-criteria)

---

## Executive Summary

Phase 2 builds upon the Phase 1 foundation (MaybePath, EitherPath, TryPath, IOPath) to add:

| Category | Components |
|----------|------------|
| **New Path Types** | ValidationPath, IdPath, OptionalPath, GenericPath |
| **New Capability** | Accumulating interface (parallel to Combinable) |
| **Annotations** | @GeneratePathBridge, @PathVia |
| **Cross-Path** | Comprehensive conversion methods |
| **Utilities** | PathOps sequence/traverse helpers |
| **Testing** | Property-based law verification with jqwik |

> **Note**: FocusPath integration has been deferred to Phase 4+ to allow Effect API feedback and consider FocusDSL interaction patterns. See [FocusPath Integration Design](./effect-path-focus-integration-design.md).

### Effort Estimate

| Task Group | Estimated Effort |
|------------|------------------|
| New Path Types | Medium |
| Accumulating Interface | Low |
| Annotation Processor | High |
| Cross-Path Conversions | Low |
| PathOps Utilities | Low |
| Property-Based Testing | Medium |
| Documentation & Examples | Medium |

---

## Phase 2 Scope Overview

### New Path Types

#### 1. ValidationPath<E, A>
**Purpose**: Error accumulation wrapper for validation scenarios

**Capabilities**:
- `Chainable<A>` - for sequencing (short-circuits on first error)
- `Accumulating<E, A>` - for error accumulation (collects all errors)

**Key Features**:
- Requires `Semigroup<E>` at construction
- `zipWithAccum` accumulates errors instead of short-circuiting
- `andAlso` combines validations
- Conversion to/from EitherPath

#### 2. IdPath<A>
**Purpose**: Identity wrapper (trivial monad)

**Capabilities**:
- `Chainable<A>`

**Key Features**:
- Simplest possible path type
- Always contains a value
- Useful for testing and generic programming

#### 3. OptionalPath<A>
**Purpose**: Java stdlib `Optional` bridge

**Capabilities**:
- `Chainable<A>` (delegates to MaybePath internally)

**Key Features**:
- Wraps `java.util.Optional<A>`
- Bidirectional conversion with MaybePath
- Familiar API for Java developers

#### 4. GenericPath<F, A>
**Purpose**: Escape hatch for custom monads

**Capabilities**:
- `Chainable<A>` (via provided Monad instance)

**Key Features**:
- Works with any `Monad<F>` instance
- Limited cross-type composition (same witness required)
- Conversion methods to concrete paths
- See [GenericPath Design Document](./effect-path-generic-path-design.md)

### New Capability Interface

#### Accumulating<E, A>
**Purpose**: Error accumulation operations for validation

```java
public interface Accumulating<E, A> extends Composable<A> {

    /**
     * Combines with another Accumulating, accumulating errors from both.
     */
    <B, C> Accumulating<E, C> zipWithAccum(
        Accumulating<E, B> other,
        BiFunction<? super A, ? super B, ? extends C> combiner);

    /**
     * Combines with another validation, accumulating errors.
     */
    <B, C, D> Accumulating<E, D> zipWith3Accum(
        Accumulating<E, B> second,
        Accumulating<E, C> third,
        Function3<? super A, ? super B, ? super C, ? extends D> combiner);

    /**
     * Runs both validations, combining errors, discarding the other value.
     */
    Accumulating<E, A> andAlso(Accumulating<E, ?> other);
}
```

### Annotations (Service Bridge Generation)

#### @GeneratePathBridge
**Purpose**: Generate bridge classes for services returning effect types

**Target**: Interfaces

**Generated**: `{InterfaceName}Paths` companion class

#### @PathVia
**Purpose**: Mark methods for inclusion in generated bridges

**Target**: Methods within @GeneratePathBridge interfaces

**Options**:
- `value()`: Custom method name
- `doc()`: Documentation string
- `composable()`: Include in composed operations

### Cross-Path Conversions

All path types should support conversion to other path types where semantically meaningful.

### PathOps Utilities

Utility class for common operations across Path types:
- `sequence`: Convert `List<Path<A>>` to `Path<List<A>>`
- `traverse`: Map and sequence in one operation
- `firstSuccess`: Try multiple paths, return first success
- `retry`: Retry an effectful path with configurable strategy

---

## Design Decisions (Confirmed)

### DD-1: ValidationPath Error Accumulation

**Decision**: ValidationPath stores `Semigroup<E>` at construction time

**Rationale**:
- Simplifies API (no semigroup parameter on every method)
- Consistent with functional programming libraries (Cats, ZIO)
- Enables `zipWithAccum` to work without additional parameters

**Example**:
```java
ValidationPath<List<Error>, User> validated = Path.validated(
    Validated.valid(user),
    Semigroups.list()
);
```

### DD-2: ValidationPath Dual Interfaces

**Decision**: ValidationPath implements both `Chainable` AND `Accumulating`

**Rationale**:
- `via`/`flatMap` short-circuit (needed for sequencing)
- `zipWithAccum`/`andAlso` accumulate (needed for validation)
- Users choose the appropriate method for their use case

### DD-3: OptionalPath Implementation

**Decision**: OptionalPath wraps `Optional<A>` directly (not MaybePath internally)

**Rationale**:
- Cleaner implementation
- Avoids double-wrapping overhead
- Direct conversion methods to/from Optional

### DD-4: GenericPath Phase 2 Scope

**Decision**: Include basic GenericPath without PathProvider SPI

**Rationale**:
- Enables custom monad usage immediately
- Defers complex SPI to Phase 3
- See [GenericPath Design](./effect-path-generic-path-design.md)

### DD-5: FocusPath Integration Scope

**Decision**: FocusPath integration deferred to Phase 4+

**Rationale**:
- Effect API needs real-world usage feedback before coupling with optics
- Need to consider FocusDSL interaction patterns
- Phase 2 scope is already substantial
- See [FocusPath Integration Design](./effect-path-focus-integration-design.md)

### DD-6: Annotation Processing Module

**Decision**: Annotations go in `hkj-annotations`, processor goes in `hkj-processor`

**Rationale**:
- Follows established patterns from optics annotation processing
- Clean module separation
- Annotations available at compile time without processor dependency
- Consistent with existing project structure

### DD-7: Accumulating Interface Hierarchy

**Decision**: Accumulating interface is parallel to Combinable (not extending it)

**Rationale**:
- Semantic clarity: `zipWith` (Combinable) short-circuits, `zipWithAccum` (Accumulating) accumulates
- Avoids confusion about which operation does what
- ValidationPath implements both, users choose the appropriate method
- Consistent with the separate capability responsibilities pattern

---

## Implementation Tasks

### Task Group 1: Capability Interfaces

#### T1.1: Accumulating Interface
**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/capability/Accumulating.java`

**Content**:
```java
package org.higherkindedj.hkt.effect.capability;

import java.util.function.BiFunction;
import org.higherkindedj.hkt.function.Function3;

/**
 * Capability interface for error-accumulating types.
 *
 * <p>Unlike {@link Chainable} which short-circuits on the first error,
 * {@code Accumulating} collects errors from multiple computations.
 * This is essential for validation scenarios where you want to report
 * all validation failures, not just the first one.
 *
 * <h2>Accumulating vs Chainable</h2>
 * <pre>{@code
 * // Chainable (short-circuits): reports only first error
 * Path.validated(invalid1).via(x -> invalid2)  // => Invalid(error1)
 *
 * // Accumulating (collects): reports both errors
 * Path.validated(invalid1).zipWithAccum(invalid2, combiner)
 *     // => Invalid([error1, error2])
 * }</pre>
 *
 * @param <E> the error type
 * @param <A> the value type
 */
public interface Accumulating<E, A> extends Composable<A> {

    /**
     * Combines two accumulating values, collecting errors from both.
     */
    <B, C> Accumulating<E, C> zipWithAccum(
        Accumulating<E, B> other,
        BiFunction<? super A, ? super B, ? extends C> combiner);

    /**
     * Combines three accumulating values, collecting errors from all.
     */
    <B, C, D> Accumulating<E, D> zipWith3Accum(
        Accumulating<E, B> second,
        Accumulating<E, C> third,
        Function3<? super A, ? super B, ? super C, ? extends D> combiner);

    /**
     * Runs both validations, accumulating errors, keeping this value if both valid.
     */
    Accumulating<E, A> andAlso(Accumulating<E, ?> other);

    /**
     * Runs both validations, accumulating errors, keeping other value if both valid.
     */
    <B> Accumulating<E, B> andThen(Accumulating<E, B> other);
}
```

**Tests**: `AccumulatingLawTest.java`

---

### Task Group 2: New Path Types

#### T2.1: ValidationPath
**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/ValidationPath.java`

**Dependencies**: T1.1

**Key Implementation Points**:
1. Store `Semigroup<E>` at construction
2. Implement `Chainable<A>` (via short-circuits)
3. Implement `Accumulating<E, A>` (zipWithAccum accumulates)
4. Implement `Recoverable<E, A>` for error handling
5. Add conversion methods

**Structure**:
```java
public final class ValidationPath<E, A>
    implements Chainable<A>, Accumulating<E, A>, Recoverable<E, A> {

    private final Validated<E, A> value;
    private final Semigroup<E> semigroup;

    ValidationPath(Validated<E, A> value, Semigroup<E> semigroup) {
        this.value = Objects.requireNonNull(value);
        this.semigroup = Objects.requireNonNull(semigroup);
    }

    // Terminal
    public Validated<E, A> run() { return value; }
    public Semigroup<E> semigroup() { return semigroup; }

    // Composable
    @Override public <B> ValidationPath<E, B> map(...) { ... }

    // Chainable (short-circuits)
    @Override public <B> ValidationPath<E, B> via(...) { ... }

    // Accumulating (collects errors)
    @Override public <B, C> ValidationPath<E, C> zipWithAccum(...) { ... }
    @Override public ValidationPath<E, A> andAlso(...) { ... }

    // Recoverable
    @Override public ValidationPath<E, A> recover(...) { ... }
    @Override public <E2> ValidationPath<E2, A> mapError(...) { ... }

    // Conversions
    public EitherPath<E, A> toEitherPath() { ... }
    public MaybePath<A> toMaybePath() { ... }
}
```

**Tests**: `ValidationPathTest.java`, `ValidationPathLawTest.java`

#### T2.2: IdPath
**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/IdPath.java`

**Key Implementation Points**:
1. Wraps `Id<A>` value
2. Implements `Chainable<A>` only (no error type)
3. Simplest possible path type

**Structure**:
```java
public final class IdPath<A> implements Chainable<A> {

    private final Id<A> value;

    IdPath(Id<A> value) {
        this.value = Objects.requireNonNull(value);
    }

    // Terminal
    public Id<A> run() { return value; }
    public A get() { return value.value(); }

    // Composable
    @Override public <B> IdPath<B> map(...) { ... }

    // Chainable
    @Override public <B> IdPath<B> via(...) { ... }

    // Conversions
    public MaybePath<A> toMaybePath() { ... }
    public <E> EitherPath<E, A> toEitherPath() { ... }
}
```

**Tests**: `IdPathTest.java`

#### T2.3: OptionalPath
**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/OptionalPath.java`

**Key Implementation Points**:
1. Wraps `java.util.Optional<A>`
2. Implements `Chainable<A>`
3. Bidirectional conversion with MaybePath
4. Similar API to MaybePath but with Optional semantics

**Structure**:
```java
public final class OptionalPath<A> implements Chainable<A> {

    private final Optional<A> value;

    OptionalPath(Optional<A> value) {
        this.value = Objects.requireNonNull(value);
    }

    // Terminal
    public Optional<A> run() { return value; }
    public A getOrElse(A defaultValue) { ... }

    // Composable
    @Override public <B> OptionalPath<B> map(...) { ... }

    // Chainable
    @Override public <B> OptionalPath<B> via(...) { ... }

    // Filtering
    public OptionalPath<A> filter(Predicate<? super A> predicate) { ... }

    // Conversions
    public MaybePath<A> toMaybePath() { ... }
    public <E> EitherPath<E, A> toEitherPath(E errorIfEmpty) { ... }
}
```

**Tests**: `OptionalPathTest.java`

#### T2.4: GenericPath
**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/GenericPath.java`

**Dependencies**: None (uses existing Kind/Monad)

**Key Implementation Points**:
1. Stores `Kind<F, A>` and `Monad<F>`
2. Implements `Chainable<A>`
3. Runtime witness type checking
4. Conversion methods with narrowing functions

See [GenericPath Design](./effect-path-generic-path-design.md) for full details.

**Tests**: `GenericPathTest.java`

---

### Task Group 3: Path Factory Updates

#### T3.1: Path Factory Methods
**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/Path.java`

**New Methods**:
```java
// ValidationPath factories
public static <E, A> ValidationPath<E, A> valid(A value, Semigroup<E> semigroup);
public static <E, A> ValidationPath<E, A> invalid(E error, Semigroup<E> semigroup);
public static <E, A> ValidationPath<E, A> validated(Validated<E, A> validated, Semigroup<E> semigroup);

// IdPath factories
public static <A> IdPath<A> id(A value);
public static <A> IdPath<A> idPath(Id<A> id);

// OptionalPath factories
public static <A> OptionalPath<A> optional(Optional<A> optional);
public static <A> OptionalPath<A> present(A value);
public static <A> OptionalPath<A> absent();

// GenericPath factories
public static <F, A> GenericPath<F, A> generic(Kind<F, A> value, Monad<F> monad);
public static <F, A> GenericPath<F, A> genericPure(A value, Monad<F> monad);
```

---

### Task Group 4: Cross-Path Conversions

#### T4.1: MaybePath Conversions (update existing)
**File**: Update `MaybePath.java`

**New Methods**:
```java
// To ValidationPath
public <E> ValidationPath<E, A> toValidationPath(E errorIfEmpty, Semigroup<E> semigroup);

// To OptionalPath
public OptionalPath<A> toOptionalPath();

// To IdPath (if present, otherwise throws)
public IdPath<A> toIdPath(Supplier<? extends RuntimeException> exceptionSupplier);
```

#### T4.2: EitherPath Conversions (update existing)
**File**: Update `EitherPath.java`

**New Methods**:
```java
// To ValidationPath
public ValidationPath<E, A> toValidationPath(Semigroup<E> semigroup);

// To OptionalPath (discards error)
public OptionalPath<A> toOptionalPath();
```

#### T4.3: TryPath Conversions (update existing)
**File**: Update `TryPath.java`

**New Methods**:
```java
// To EitherPath (with error transformation)
public <E> EitherPath<E, A> toEitherPath(Function<Throwable, E> errorMapper);

// To ValidationPath
public <E> ValidationPath<E, A> toValidationPath(
    Function<Throwable, E> errorMapper, Semigroup<E> semigroup);

// To OptionalPath
public OptionalPath<A> toOptionalPath();
```

#### T4.4: ValidationPath Conversions
**File**: `ValidationPath.java`

**Methods**:
```java
// To EitherPath
public EitherPath<E, A> toEitherPath();

// To MaybePath (discards error)
public MaybePath<A> toMaybePath();

// To TryPath
public TryPath<A> toTryPath(Function<E, Throwable> errorToException);

// To OptionalPath
public OptionalPath<A> toOptionalPath();
```

#### T4.5: OptionalPath Conversions
**File**: `OptionalPath.java`

**Methods**:
```java
// To MaybePath
public MaybePath<A> toMaybePath();

// To EitherPath
public <E> EitherPath<E, A> toEitherPath(E errorIfEmpty);

// To ValidationPath
public <E> ValidationPath<E, A> toValidationPath(E errorIfEmpty, Semigroup<E> semigroup);
```

---

### Task Group 5: PathOps Utilities

#### T5.1: PathOps Class
**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/effect/PathOps.java`

**Content**:
```java
package org.higherkindedj.hkt.effect;

import java.util.List;
import java.util.function.Function;

/**
 * Utility operations for working with Path types.
 *
 * <p>Provides common functional programming patterns like sequence and traverse
 * that operate across collections of Path values.
 */
public final class PathOps {

    private PathOps() {}

    // === MaybePath Operations ===

    /**
     * Converts a list of MaybePaths to a MaybePath of list.
     * Returns Nothing if any path is Nothing.
     */
    public static <A> MaybePath<List<A>> sequenceMaybe(List<MaybePath<A>> paths);

    /**
     * Maps a function over a list and sequences the results.
     */
    public static <A, B> MaybePath<List<B>> traverseMaybe(
        List<A> items, Function<A, MaybePath<B>> f);

    // === EitherPath Operations ===

    /**
     * Converts a list of EitherPaths to an EitherPath of list.
     * Returns first Left if any path is Left.
     */
    public static <E, A> EitherPath<E, List<A>> sequenceEither(List<EitherPath<E, A>> paths);

    /**
     * Maps a function over a list and sequences the results.
     */
    public static <E, A, B> EitherPath<E, List<B>> traverseEither(
        List<A> items, Function<A, EitherPath<E, B>> f);

    // === ValidationPath Operations ===

    /**
     * Converts a list of ValidationPaths to a ValidationPath of list.
     * Accumulates all errors if any paths are Invalid.
     */
    public static <E, A> ValidationPath<E, List<A>> sequenceValidated(
        List<ValidationPath<E, A>> paths, Semigroup<E> semigroup);

    /**
     * Maps a function over a list and sequences the results, accumulating errors.
     */
    public static <E, A, B> ValidationPath<E, List<B>> traverseValidated(
        List<A> items, Function<A, ValidationPath<E, B>> f, Semigroup<E> semigroup);

    // === TryPath Operations ===

    /**
     * Converts a list of TryPaths to a TryPath of list.
     * Returns first Failure if any path failed.
     */
    public static <A> TryPath<List<A>> sequenceTry(List<TryPath<A>> paths);

    /**
     * Returns the first successful path, or the last failure if all fail.
     */
    public static <A> TryPath<A> firstSuccess(List<TryPath<A>> paths);
}
```

**Tests**: `PathOpsTest.java`

---

### Task Group 6: Annotation Processing

#### T6.1: Annotation Definitions
**Module**: `hkj-annotations`
**Directory**: `hkj-annotations/src/main/java/org/higherkindedj/hkt/effect/annotation/`

**Files**:
- `GeneratePathBridge.java` - Interface-level annotation
- `PathVia.java` - Method-level annotation

**Note**: Follows established pattern from optics annotations.

#### T6.2: Annotation Processor
**Module**: `hkj-processor`
**Directory**: `hkj-processor/src/main/java/org/higherkindedj/hkt/effect/processor/`

**Files**:
- `PathProcessor.java` - Main annotation processor
- `PathBridgeGenerator.java` - Generates bridge classes
- `PathValidations.java` - Compile-time validations
- `PathCodeModel.java` - AST model for code generation

**Resources**:
- `hkj-processor/src/main/resources/META-INF/services/javax.annotation.processing.Processor`

**Note**: Follows established pattern from optics processor.

#### T6.3: Processor Tests
**Module**: `hkj-processor`
**Directory**: `hkj-processor/src/test/java/org/higherkindedj/hkt/effect/processor/`

**Dependencies**: google-compile-testing library

**Tests**:
- `PathProcessorTest.java` - Processor functionality tests
- `PathBridgeGeneratorTest.java` - Code generation tests

---

### Task Group 7: Sealed Interface Updates

#### T7.1: Update Capability Interface Permits
**Files**: All capability interfaces

**Changes**:
```java
// Chainable.java
public sealed interface Chainable<A> extends Combinable<A>
    permits Recoverable, Effectful, MaybePath, EitherPath, TryPath, IOPath,
            ValidationPath, IdPath, OptionalPath, GenericPath {
```

```java
// Recoverable.java
public sealed interface Recoverable<E, A> extends Chainable<A>
    permits MaybePath, EitherPath, TryPath, ValidationPath {
```

```java
// Effectful.java
public sealed interface Effectful<A> extends Chainable<A>
    permits IOPath {
```

### Task Group 8: Property-Based Testing

#### T8.1: jqwik Dependency
**File**: `hkj-core/pom.xml` (test dependency)

Add jqwik for property-based testing:
```xml
<dependency>
    <groupId>net.jqwik</groupId>
    <artifactId>jqwik</artifactId>
    <version>1.9.0</version>
    <scope>test</scope>
</dependency>
```

#### T8.2: Path Arbitrary Providers
**File**: `hkj-core/src/test/java/org/higherkindedj/hkt/effect/PathArbitraries.java`

Provides generators for property-based tests:
```java
public class PathArbitraries {
    public static <A> Arbitrary<MaybePath<A>> maybePaths(Arbitrary<A> values);
    public static <E, A> Arbitrary<EitherPath<E, A>> eitherPaths(
        Arbitrary<E> errors, Arbitrary<A> values);
    public static <E, A> Arbitrary<ValidationPath<E, A>> validatedPaths(
        Arbitrary<E> errors, Arbitrary<A> values, Semigroup<E> semigroup);
    // etc.
}
```

#### T8.3: Law Verification Tests
**Directory**: `hkj-core/src/test/java/org/higherkindedj/hkt/effect/laws/`

**Files**:
- `MaybePathLawsTest.java` - Functor, Monad laws
- `EitherPathLawsTest.java` - Functor, Monad, MonadError laws
- `ValidationPathLawsTest.java` - Functor, Applicative (accumulating), Monad laws
- `IdPathLawsTest.java` - Functor, Monad laws
- `OptionalPathLawsTest.java` - Functor, Monad laws

**Example**:
```java
class MaybePathLawsTest {

    @Property
    void functorIdentity(@ForAll("maybePaths") MaybePath<Integer> path) {
        assertThat(path.map(Function.identity())).isEqualTo(path);
    }

    @Property
    void functorComposition(
            @ForAll("maybePaths") MaybePath<Integer> path,
            @ForAll Function<Integer, String> f,
            @ForAll Function<String, Double> g) {
        assertThat(path.map(f).map(g)).isEqualTo(path.map(f.andThen(g)));
    }

    @Property
    void monadLeftIdentity(
            @ForAll Integer value,
            @ForAll Function<Integer, MaybePath<String>> f) {
        assertThat(Path.just(value).via(f)).isEqualTo(f.apply(value));
    }

    // ... more laws
}
```

---

## Task Dependencies

```
T1.1 (Accumulating)
  │
  └──► T2.1 (ValidationPath)
         │
         └──► T4.4 (ValidationPath Conversions)
         └──► T5.1 (PathOps - sequenceValidated)

T2.2 (IdPath) ───► T3.1 (Path Factory)
T2.3 (OptionalPath) ───► T3.1 (Path Factory)
T2.4 (GenericPath) ───► T3.1 (Path Factory)

T4.1-T4.5 (Conversions) - can be parallel

T5.1 (PathOps) - after T2.x

T6.1 (Annotations in hkj-annotations)
  │
  └──► T6.2 (Processor in hkj-processor)
         │
         └──► T6.3 (Processor Tests)

T7.1 (Sealed Updates) - after all new types

T8.1-T8.3 (Property Testing) - can run in parallel with other tasks
```

---

## File Structure

```
hkj-core/src/main/java/org/higherkindedj/hkt/effect/
├── Path.java                          # Factory (updated)
├── PathOps.java                       # NEW: sequence/traverse utilities
├── MaybePath.java                     # Updated: conversions
├── EitherPath.java                    # Updated: conversions
├── TryPath.java                       # Updated: conversions
├── IOPath.java                        # Existing
├── ValidationPath.java                 # NEW
├── IdPath.java                        # NEW
├── OptionalPath.java                  # NEW
├── GenericPath.java                   # NEW
└── capability/
    ├── Composable.java                # Existing
    ├── Combinable.java                # Existing
    ├── Chainable.java                 # Updated: permits
    ├── Recoverable.java               # Updated: permits
    ├── Effectful.java                 # Existing
    └── Accumulating.java              # NEW

hkj-annotations/src/main/java/org/higherkindedj/hkt/effect/annotation/
├── GeneratePathBridge.java            # NEW
└── PathVia.java                       # NEW

hkj-processor/src/main/java/org/higherkindedj/hkt/effect/processor/
├── PathProcessor.java                 # NEW
├── PathBridgeGenerator.java           # NEW
├── PathValidations.java               # NEW
└── PathCodeModel.java                 # NEW

hkj-processor/src/main/resources/META-INF/services/
└── javax.annotation.processing.Processor  # NEW (add PathProcessor entry)

hkj-processor/src/test/java/org/higherkindedj/hkt/effect/processor/
└── PathProcessorTest.java             # NEW
└── PathBridgeGeneratorTest.java       # NEW

hkj-core/src/test/java/org/higherkindedj/hkt/effect/
├── ValidationPathTest.java             # NEW
├── IdPathTest.java                    # NEW
├── OptionalPathTest.java              # NEW
├── GenericPathTest.java               # NEW
├── PathOpsTest.java                   # NEW
├── CrossPathConversionTest.java       # NEW
├── PathArbitraries.java               # NEW: jqwik providers
└── laws/
    ├── MaybePathLawsTest.java         # NEW: property-based laws
    ├── EitherPathLawsTest.java        # NEW
    ├── ValidationPathLawsTest.java     # NEW
    ├── IdPathLawsTest.java            # NEW
    └── OptionalPathLawsTest.java      # NEW

hkj-examples/src/main/java/org/higherkindedj/examples/effect/
├── ValidationExample.java             # NEW: ValidationPath usage
├── ServiceBridgeExample.java          # NEW: annotation usage
└── PathCompositionExample.java        # NEW: cross-path patterns
```

---

## Testing Strategy

### Unit Tests

| Component | Test Class | Coverage Target |
|-----------|------------|-----------------|
| ValidationPath | ValidationPathTest | 100% line/branch |
| IdPath | IdPathTest | 100% line/branch |
| OptionalPath | OptionalPathTest | 100% line/branch |
| GenericPath | GenericPathTest | 100% line/branch |
| PathOps | PathOpsTest | 100% line/branch |
| Conversions | CrossPathConversionTest | All combinations |

### Property-Based Law Tests (jqwik)

| Path Type | Laws to Verify | Test Class |
|-----------|----------------|------------|
| MaybePath | Functor, Monad | MaybePathLawsTest |
| EitherPath | Functor, Monad, MonadError | EitherPathLawsTest |
| ValidationPath | Functor, Applicative (accumulating), Monad | ValidationPathLawsTest |
| IdPath | Functor, Monad | IdPathLawsTest |
| OptionalPath | Functor, Monad | OptionalPathLawsTest |

**Law Properties Tested**:
- Functor identity: `fa.map(id) == fa`
- Functor composition: `fa.map(f).map(g) == fa.map(f.andThen(g))`
- Monad left identity: `pure(a).via(f) == f(a)`
- Monad right identity: `fa.via(pure) == fa`
- Monad associativity: `fa.via(f).via(g) == fa.via(a -> f(a).via(g))`
- Applicative (accumulating): `zipWithAccum` accumulates all errors

### Processor Tests

Using google-compile-testing:
- Valid annotation produces expected code
- Invalid annotations produce clear errors
- Generated code compiles and works correctly
- Edge cases: no methods, mixed annotations, inheritance

---

## Documentation Requirements

### Javadoc

All new public classes and methods need comprehensive Javadoc including:
- Class/method description
- @param for all parameters
- @return for return values
- Usage examples in class-level doc
- @see cross-references

### Design Documents

- [x] GenericPath Design Document
- [x] FocusPath Integration Design Document (deferred to Phase 4+)
- [ ] Update effect-path-design.md with Phase 2 additions
- [ ] Update effect-path-implementation.md

### Examples

Add to `hkj-examples`:
- ValidationPath validation example
- Cross-path conversion example
- Annotation processing example
- PathOps sequence/traverse example

---

## Success Criteria

### Functional

- [ ] All new path types compile and pass tests
- [ ] ValidationPath accumulates errors correctly with Semigroup
- [ ] GenericPath works with custom monads
- [ ] Cross-path conversions are complete
- [ ] PathOps sequence/traverse work correctly
- [ ] Annotation processor generates correct code

### Quality

- [ ] 100% line coverage for new code
- [ ] 100% branch coverage for new code
- [ ] All property-based law tests pass
- [ ] No breaking changes to Phase 1 API
- [ ] Comprehensive Javadoc

### Process

- [ ] All design decisions documented
- [ ] Implementation matches design documents
- [ ] PR review completed
- [ ] Documentation updated
- [ ] Examples in hkj-examples

---

## Implementation Order (Recommended)

### Stage 1: Core Types
1. T1.1 - Accumulating interface
2. T2.1 - ValidationPath (depends on T1.1)
3. T2.2, T2.3, T2.4 - IdPath, OptionalPath, GenericPath (parallel)

### Stage 2: Infrastructure
4. T3.1 - Path factory updates
5. T7.1 - Sealed interface updates

### Stage 3: Extensions
6. T4.1-T4.5 - Cross-path conversions (parallel)
7. T5.1 - PathOps utilities

### Stage 4: Annotations (can run parallel with Stage 3)
8. T6.1 - Annotation definitions (hkj-annotations)
9. T6.2 - Annotation processor (hkj-processor)
10. T6.3 - Processor tests

### Stage 5: Quality
11. T8.1-T8.3 - Property-based testing setup and law tests
12. Examples in hkj-examples
13. Documentation updates

---

## Checklist Summary

### New Types
- [x] Accumulating interface (parallel to Combinable)
- [x] ValidationPath
- [x] IdPath
- [x] OptionalPath
- [x] GenericPath
- [x] PathOps utility class

### Updates
- [x] Path factory methods
- [x] Sealed interface permits
- [x] MaybePath conversions
- [x] EitherPath conversions
- [x] TryPath conversions

### Annotations (hkj-annotations + hkj-processor)
- [x] @GeneratePathBridge annotation
- [x] @PathVia annotation
- [x] PathProcessor (includes bridge generation, validations, and code model)

### Testing
- [x] ValidationPath tests
- [x] IdPath tests
- [x] OptionalPath tests
- [x] GenericPath tests
- [x] PathOps tests
- [x] Cross-path conversion tests
- [x] Property-based law tests (jqwik) - All path types have *PropertyTest.java verifying Functor/Monad laws
- [x] Processor tests

### Documentation
- [x] Javadoc for all new public API
- [x] Updated design documents (v3.0 design, v2.2 implementation)
- [x] Examples in hkj-examples (AccumulatingValidationExample, PathOpsExample, CrossPathConversionsExample)
- [x] Updated hkj-book Effect chapter for Phase 2 types
