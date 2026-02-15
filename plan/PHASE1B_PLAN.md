# Phase 1b Implementation Plan: Consolidate Hand-Written Arities 2-5

## Overview

Replace hand-written Tuple2-5, MonadicSteps2-5, FilterableSteps2-5, and all ForPath
PathSteps2-5 inner classes with generated equivalents. The generators already support
this; the main work is reconciling structural differences, updating the annotation
trigger, and removing ~2900 lines of hand-written code.

## Prerequisites (verified)

- Phase 1 complete: all tests pass at arities 6-8
- `minArity` annotation element already exists (default 6)
- Generators (TupleGenerator, ForStepGenerator, ForPathStepGenerator) already accept minArity

## Steps

### 1. Add `bimap()` generation to TupleGenerator for arity 2

Hand-written Tuple2 has `bimap(f1, f2)` instead of `map(f1, f2)`. Tuple2Bifunctor
delegates to `tuple.bimap(f, g)`. The generator currently only generates `map()`.

**Change**: In `TupleGenerator.java`, add a `bimap()` method for arity 2. This is
semantically identical to `map()` but named `bimap` with parameter names `firstMapper`
and `secondMapper`, using `BIMAP` operation constant for validation.

### 2. Verify generated output matches hand-written structure

Before deleting anything, generate arities 2-5 and diff against hand-written code.

**Change**: Set `minArity = 2` in `package-info.java` temporarily (alongside keeping
hand-written files) and inspect the generated output. Fix any template deviations.

**Critical differences to verify**:
- Tuple2: has `bimap()` (not `map()`), plus `mapFirst()`, `mapSecond()`
- Tuple3-5: have `map()` plus individual `mapN()` methods
- Validation patterns: both use `Validation.function().requireMapper()`
- `@GenerateLenses` annotation present on all
- `@Generated` annotation added to generated (acceptable difference)

### 3. Update `package-info.java` to `minArity = 2`

```java
@GenerateForComprehensions(minArity = 2, maxArity = 8)
package org.higherkindedj.hkt.expression;
```

### 4. Delete hand-written Tuple2-5 source files

Delete these files (they become generated):
- `hkj-core/src/main/java/org/higherkindedj/hkt/tuple/Tuple2.java`
- `hkj-core/src/main/java/org/higherkindedj/hkt/tuple/Tuple3.java`
- `hkj-core/src/main/java/org/higherkindedj/hkt/tuple/Tuple4.java`
- `hkj-core/src/main/java/org/higherkindedj/hkt/tuple/Tuple5.java`

**Keep**: `Tuple.java` (sealed interface + factory methods), `Tuple2Bifunctor.java`,
`Tuple2KindHelper.java`, all test files.

### 5. Remove MonadicSteps2-5 and FilterableSteps2-5 inner classes from For.java

**Keep in For.java**:
- `Steps<M>` sealed interface (permits unchanged - already lists MonadicSteps2-8, FilterableSteps2-8)
- `MonadicSteps1<M, A>` inner class
- `FilterableSteps1<M, A>` inner class
- Factory methods: `from(Monad, Kind)` and `from(MonadZero, Kind)`

**Remove**: The 8 inner classes MonadicSteps2-5 and FilterableSteps2-5.

**Update MonadicSteps1**: Its `from()`, `let()`, `focus()` methods currently return
`new MonadicSteps2<>(monad, newComputation)` using a private constructor. The generated
MonadicSteps2 will have a package-private constructor, accessible from the same package.

**Update FilterableSteps1**: Same pattern - return `new FilterableSteps2<>(monad, newComputation)`.

### 6. Remove PathSteps2-5 inner classes from ForPath.java

**Keep in ForPath.java**:
- All 9 `*PathSteps1` inner classes (MaybePathSteps1, OptionalPathSteps1, etc.)
- All 9 `ForPath.from(...)` factory methods

**Remove**: All PathSteps2-5 inner classes (~22 classes total).

**Update *PathSteps1 classes**: Their `from()`, `let()`, `focus()`, `when()`, `match()`
methods return `new *PathSteps2<>(...)`. The generated PathSteps2 classes will have
package-private constructors, accessible from the same package.

### 7. Update ForPathStepGenerator `currentMaxArity` for all path types

The generator uses `currentMaxArity` per path type to skip arities that already exist
as hand-written code. For Phase 1b, set `currentMaxArity = 1` for all path types
(only Steps1 remains hand-written).

### 8. Build, fix compilation issues, run tests

- Build the project: `./gradlew clean build`
- Fix any compilation errors from the transition
- Run full test suite: `./gradlew test`
- Verify all existing tests pass without modification

### 9. Update golden file tests

Add golden file tests for representative generated arities (e.g., arity 2 and arity 5)
to verify the generated output is stable.

### 10. Commit and push

## Risk Assessment

| Risk | Mitigation |
|------|-----------|
| Constructor access: private → package-private | Same package, so accessible |
| Sealed permits: inner class → top-level | Java resolves in same package |
| Tuple2.bimap() missing | Step 1 adds it to generator |
| Test code references inner class types | Tests use fluent API, not class names directly |
| Generated Javadoc differs from hand-written | Acceptable; generated Javadoc is adequate |

## Estimated Reduction

- ~510 lines from Tuple2-5 records
- ~830 lines from MonadicSteps2-5 + FilterableSteps2-5 in For.java
- ~1600 lines from ForPath PathSteps2-5 inner classes
- **Total: ~2940 lines of hand-written boilerplate eliminated**
