# Phase 1 Implementation Plan: Lift the For-Comprehension Arity Ceiling

## Overview

Phase 1 removes the hard cap of 5 bindings in for-comprehensions. Rather than hand-writing
~6000 lines of mechanically repetitive boilerplate, we extend the existing `hkj-processor`
annotation processor to generate Tuple, For step, and ForPath step classes from templates.
The generator logic lives alongside the existing Lens, Prism, and Traversal processors;
it is triggered by a new `@GenerateForComprehensions` annotation on a `package-info.java`
in `hkj-core`. The only hand-written artefacts are `Function6-8` in `hkj-api` (~90 lines
total), sealed permits updates, and bridge methods on existing Step5 classes.

Today the hand-written code limits comprehensions to 5 chained operations across 10 inner
classes in `For.java`, 31 in `ForPath.java`, 4 tuple records, and 3 function interfaces.
This plan extends coverage to arity 8 (configurable higher) while reducing long-term
maintenance burden to near zero for the generated artefacts.

## Goals

1. Add a `ForComprehensionProcessor` to the existing `hkj-processor` module
2. Generate `Tuple6..8`, `MonadicSteps6..8`, `FilterableSteps6..8`, and
   all 9 ForPath step families up to arity 8 via annotation processing during `hkj-core` compilation
3. Hand-write `Function6-8` in `hkj-api` (trivially small; JPMS prevents cross-module generation)
4. Add golden file tests in `hkj-processor` following the existing GoldenFileTest pattern
5. Add comprehensive runtime tests in `hkj-core` following TESTING-GUIDE.md patterns
6. Maintain full backward compatibility; arities 1-5 remain hand-written as the reference implementation

## Non-Goals

- Creating a new module (hkj-generator or similar)
- Replacing the hand-written arities 1-5 (they remain the source of truth and reference)
- Changing the tuple-based accumulation strategy (Phase 2 addresses named bindings)
- Adding new comprehension features (parallel, traverse, MTL; those are later phases)
- Generating ForState, ForTraversal, or ForIndexed extensions (they are not arity-limited)

---

## Architecture Decision: Use hkj-processor

### Options Considered

| Approach | Pros | Cons |
|----------|------|------|
| **A. Extend hkj-processor** | Existing infrastructure, JavaPoet, AutoService, golden file tests, SPI, already wired into hkj-core | Cannot generate into hkj-api (JPMS split package) |
| **B. Dedicated generator module** | Clean separation | Duplicates tooling; new module overhead |
| **C. Gradle JavaExec task** | Simple to wire | Generator code has no natural home; not testable like a processor |

**Decision: Option A** (extend `hkj-processor`).

Rationale:
- `hkj-processor` already uses JavaPoet (Palantir 0.7.0), `@AutoService`, golden file tests,
  property-based tests, and the full annotation processor lifecycle.
- `hkj-core/build.gradle.kts` already declares `annotationProcessor(project(":hkj-processor"))`.
- The `ImportOpticsProcessor` already handles `PACKAGE`-level annotations via `package-info.java`,
  proving this trigger mechanism works.
- The only artefact that cannot be generated into `hkj-core` is `Function6-8`, which lives
  in `hkj-api` due to JPMS constraints (`org.higherkindedj.hkt.function` is exported by
  `hkj-api`). These are 3 trivial `@FunctionalInterface` files (~30 lines each) that are
  cheaper to hand-write than to build cross-module generation infrastructure for.

### Trigger Mechanism

A new annotation `@GenerateForComprehensions` in `hkj-annotations`, placed on a
`package-info.java` in `hkj-core`:

```java
// hkj-annotations: new annotation
@Target(ElementType.PACKAGE)
@Retention(RetentionPolicy.CLASS)
public @interface GenerateForComprehensions {
    int maxArity() default 8;
}

// hkj-core: trigger
@GenerateForComprehensions(maxArity = 8)
package org.higherkindedj.hkt.expression;

import org.higherkindedj.optics.annotations.GenerateForComprehensions;
```

The processor reads `maxArity` from the annotation and generates all artefacts into
`hkj-core`'s generated sources directory (managed automatically by `processingEnv.getFiler()`).

---

## Scope of What Exists Today

### Tuple Types (`hkj-core/.../tuple/`)

| Type | Structure | Components | Methods |
|------|-----------|------------|---------|
| `Tuple` | Sealed interface | Factory | `of(...)` overloads for arities 2-5 |
| `Tuple2<A,B>` | Record | `_1`, `_2` | `bimap`, `mapFirst`, `mapSecond` |
| `Tuple3<A,B,C>` | Record | `_1`..`_3` | `map(f1,f2,f3)`, `mapFirst`..`mapThird` |
| `Tuple4<A,B,C,D>` | Record | `_1`..`_4` | `map(f1..f4)`, `mapFirst`..`mapFourth` |
| `Tuple5<A,B,C,D,E>` | Record | `_1`..`_5` | `map(f1..f5)`, `mapFirst`..`mapFifth` |

All are `@GenerateLenses`-annotated records implementing the sealed `Tuple` interface.

### Function Types (`hkj-api/.../function/`)

| Type | Parameters | Return |
|------|-----------|--------|
| `Function3<T1,T2,T3,R>` | 3 | `@Nullable R` |
| `Function4<T1,T2,T3,T4,R>` | 4 | `@Nullable R` |
| `Function5<T1,T2,T3,T4,T5,R>` | 5 | `@Nullable R` |

Java's `Function<T,R>` and `BiFunction<T,U,R>` cover arities 1-2.
Used by `Applicative.java`, `Monad.java`, and `Lens.java` in hkj-api.

### For.java (10 hand-written inner classes)

- `MonadicSteps1..5`: hold `Kind<M, A>` / `Kind<M, Tuple2..5<...>>`
- `FilterableSteps1..5`: mirror of above supporting `when(Predicate)` and `match(Prism)`
- Each step: `from()`, `let()`, `focus()`, `yield()` (plus `when()`, `match()` for filterable)
- Focus signature: `Lens<A,B>` at step 1; `Function<TupleN, X>` at step N >= 2
- Steps5 is currently terminal (no `from()`/`let()`/`focus()`)

### ForPath.java (31 hand-written inner classes)

| Path Type | Current Max Arity | Filterable |
|-----------|-------------------|------------|
| MaybePath | 5 | Yes |
| VTaskPath | 5 | No |
| OptionalPath | 3 | Yes |
| EitherPath | 3 | No |
| TryPath | 3 | No |
| IOPath | 3 | No |
| IdPath | 3 | No |
| NonDetPath | 3 | Yes |
| GenericPath | 3 | No |

---

## Implementation Tasks

### Task 1: Add `@GenerateForComprehensions` Annotation

**Module**: `hkj-annotations`
**Package**: `org.higherkindedj.optics.annotations`

```java
@Target(ElementType.PACKAGE)
@Retention(RetentionPolicy.CLASS)
public @interface GenerateForComprehensions {
    /** Maximum arity for generated comprehension support. Default 8. */
    int maxArity() default 8;
}
```

### Task 2: Hand-Write Function6, Function7, Function8

**Module**: `hkj-api`
**Package**: `org.higherkindedj.hkt.function`

Three trivial files (~30 lines each) following the exact pattern of Function5:

```java
@FunctionalInterface
public interface Function6<T1, T2, T3, T4, T5, T6, R> {
    @Nullable R apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6);
}
```

**Why hand-written**: JPMS prevents generating into `hkj-api` from `hkj-processor` (which
runs during `hkj-core` compilation). The `org.higherkindedj.hkt.function` package is
exported by `hkj-api`'s `module-info.java`. These 3 files are the only hand-written
boilerplate in the entire plan outside of bridge methods.

### Task 3: Create `ForComprehensionProcessor` in hkj-processor

**Module**: `hkj-processor`
**Package**: `org.higherkindedj.optics.processing`

```java
@AutoService(Processor.class)
@SupportedAnnotationTypes("org.higherkindedj.optics.annotations.GenerateForComprehensions")
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public class ForComprehensionProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(GenerateForComprehensions.class)) {
            if (element.getKind() != ElementKind.PACKAGE) continue;

            GenerateForComprehensions config =
                element.getAnnotation(GenerateForComprehensions.class);
            int maxArity = config.maxArity();

            generateTuples(6, maxArity);
            generateMonadicSteps(6, maxArity);
            generateFilterableSteps(6, maxArity);
            generatePathSteps(6, maxArity);
        }
        return true;
    }
}
```

**Register in `module-info.java`**:
```java
provides javax.annotation.processing.Processor with
    // ... existing processors ...
    org.higherkindedj.optics.processing.ForComprehensionProcessor;
```

**Internal generator classes** (within hkj-processor, not public):

| Class | Responsibility |
|-------|---------------|
| `TupleGenerator` | Generates `Tuple6..N` records using JavaPoet |
| `ForStepGenerator` | Generates `MonadicSteps6..N` and `FilterableSteps6..N` |
| `ForPathStepGenerator` | Generates `*PathSteps` for all 9 path types |

These are package-private helper classes used by `ForComprehensionProcessor`, not
separate processors. They follow the same JavaPoet patterns as the existing
`ExternalLensGenerator`, `PrismCodeGenerator`, etc.

### Task 4: TupleGenerator

**Generates**: `Tuple6.java` through `Tuple{maxArity}.java` into `org.higherkindedj.hkt.tuple`

**Template rules** (derived from hand-written Tuple2-5):

For each arity N from 6 to maxArity, generates a record:
- `@GenerateLenses` annotation (triggers lens generation in the same compilation round)
- Implements `Tuple` sealed interface
- Components: `@Nullable A _1` through `_N`
- `map(f1..fN)` applying each function to the corresponding component
- `mapFirst()` through `mapNth()` for per-component transformation
- Type parameter names: A, B, C, D, E, F, G, H
- Javadoc: British English, no emojis, no em dashes

**Note on sealed permits**: The `Tuple` sealed interface in hand-written code must be
updated to permit the generated types. The generator emits a warning message via
`processingEnv.getMessager()` listing the required permits if they are missing.

### Task 5: ForStepGenerator

**Generates**: `MonadicSteps6..N` and `FilterableSteps6..N` as top-level classes
in `org.higherkindedj.hkt.expression`

Generated as top-level classes (not inner classes of `For.java`) to keep the hand-written
file stable. They implement `For.Steps<M>` and follow the exact pattern of the hand-written
steps.

**Template rules for MonadicStepsN** (derived from MonadicSteps2-5):
- `implements For.Steps<M>`
- Package-private constructor: `MonadicStepsN(Monad<M> monad, Kind<M, TupleN<...>> computation)`
- `from()` / `let()` / `focus()` returning `MonadicSteps(N+1)` (omitted at maxArity)
- `yield(FunctionN<A, B, ..., R>)` returning `Kind<M, R>` (spread variant)
- `yield(Function<TupleN<A, B, ...>, R>)` returning `Kind<M, R>` (tuple variant)
- `Objects.requireNonNull()` on all parameters

**Template rules for FilterableStepsN** (adds to above):
- `when(Predicate<TupleN<...>>)` returning same FilterableStepsN
- `match(Function<TupleN<...>, Optional<X>>)` returning `FilterableSteps(N+1)` (omitted at maxArity)

**Key implementation patterns**:
- `from()`: `monad.flatMap(t -> monad.map(x -> Tuple.of(t._1(), ..., x), next.apply(t)), computation)`
- `let()`: `monad.map(t -> Tuple.of(t._1(), ..., f.apply(t)), computation)`
- `when()`: `monad.flatMap(t -> pred.test(t) ? monad.of(t) : ((MonadZero<M>) monad).zero(), computation)`
- `yield(spread)`: `monad.map(t -> f.apply(t._1(), ..., t._N()), computation)`

### Task 6: ForPathStepGenerator

**Generates**: Per-path-type step classes for arities beyond what is hand-written.

Uses a built-in descriptor table:

| Path Type | Witness | Extra Type Params | Filterable | Current Max | Monad Instance |
|-----------|---------|-------------------|------------|-------------|----------------|
| MaybePath | `MaybeKind.Witness` | none | Yes | 5 | `MaybeMonad.INSTANCE` |
| OptionalPath | `OptionalKind.Witness` | none | Yes | 3 | `OptionalMonad.INSTANCE` |
| EitherPath | `EitherKind.Witness<E>` | `E` | No | 3 | `EitherMonad.instance()` |
| TryPath | `TryKind.Witness` | none | No | 3 | `TryMonad.INSTANCE` |
| IOPath | `IOKind.Witness` | none | No | 3 | `IOMonad.INSTANCE` |
| VTaskPath | `VTaskKind.Witness` | none | No | 5 | `VTaskMonad.INSTANCE` |
| IdPath | `IdKind.Witness` | none | No | 3 | `IdMonad.INSTANCE` |
| NonDetPath | `ListKind.Witness` | none | Yes | 3 | `ListMonad.INSTANCE` |
| GenericPath | `F` | `F` | No | 3 | (passed in) |

For each path type, for each arity from `currentMaxArity + 1` to `maxArity`, generates
a step class as a top-level file (e.g. `EitherPathSteps4.java`).

Each class:
- Wraps `Kind<WitnessType, TupleN<...>>` internally
- Exposes `from()`, `let()`, `focus()`, `yield()` returning the concrete Path type
- Filterable variants also expose `when()`
- `yield()` narrows from `Kind` to the concrete Path type using the appropriate KindHelper

### Task 7: Minimal Hand-Written Changes (Sealed Permits + Bridge Methods)

These are the only hand-written changes to existing source files:

**`Tuple.java`**: Update sealed `permits` and add `of()` factory overloads:
```java
public sealed interface Tuple
    permits Tuple2, Tuple3, Tuple4, Tuple5,
            Tuple6, Tuple7, Tuple8 {  // <-- add generated types

    static <A, B, C, D, E, F> Tuple6<A, B, C, D, E, F> of(...) { ... }
    static <A, B, C, D, E, F, G> Tuple7<A, B, C, D, E, F, G> of(...) { ... }
    static <A, B, C, D, E, F, G, H> Tuple8<A, B, C, D, E, F, G, H> of(...) { ... }
}
```

**`For.java`**: Update `Steps<M>` sealed permits and add bridge methods on Steps5:
```java
public sealed interface Steps<M>
    permits MonadicSteps1, ..., MonadicSteps5,
            MonadicSteps6, MonadicSteps7, MonadicSteps8,
            FilterableSteps1, ..., FilterableSteps5,
            FilterableSteps6, FilterableSteps7, FilterableSteps8 {}

// MonadicSteps5: add from(), let(), focus() returning MonadicSteps6
// FilterableSteps5: add from(), let(), focus(), match() returning FilterableSteps6
```

**`ForPath.java`**: Add bridge methods on the highest existing step class for each path type.
For example, `EitherPathSteps3.from()` returns the generated `EitherPathSteps4`.

**`hkj-core/module-info.java`**: Already exports `org.higherkindedj.hkt.expression` and
`org.higherkindedj.hkt.tuple`, so no changes needed (generated classes go into existing
exported packages).

**`hkj-processor/module-info.java`**: Add `ForComprehensionProcessor` to `provides` clause.

**`package-info.java`**: Create trigger file in `hkj-core`:
```java
@GenerateForComprehensions(maxArity = 8)
package org.higherkindedj.hkt.expression;
```

### Task 8: Golden File Tests

**Module**: `hkj-processor` (test source set)

Following the existing `GoldenFileTest.java` pattern with `RuntimeCompilationHelper`:

```java
@ParameterizedTest(name = "Generated {0} matches golden file")
@MethodSource("goldenTestCases")
@DisplayName("Generated for-comprehension code matches golden file")
void generatedCodeMatchesGolden(String className, String goldenPath) {
    Compilation compilation = compile(PACKAGE_INFO_WITH_ANNOTATION);
    assertThat(compilation.status()).isEqualTo(Compilation.Status.SUCCESS);
    String generated = getGeneratedSource(compilation, className);
    String golden = readGoldenFile(goldenPath);
    assertThat(normalise(generated)).isEqualTo(normalise(golden));
}
```

**Golden files** (`hkj-processor/src/test/resources/golden/`):

| Golden File | Generator |
|-------------|-----------|
| `Tuple6.java.golden` | TupleGenerator |
| `Tuple7.java.golden` | TupleGenerator |
| `Tuple8.java.golden` | TupleGenerator |
| `MonadicSteps6.java.golden` | ForStepGenerator |
| `MonadicSteps7.java.golden` | ForStepGenerator |
| `MonadicSteps8.java.golden` | ForStepGenerator |
| `FilterableSteps6.java.golden` | ForStepGenerator |
| `FilterableSteps7.java.golden` | ForStepGenerator |
| `FilterableSteps8.java.golden` | ForStepGenerator |
| `EitherPathSteps4.java.golden` | ForPathStepGenerator (representative) |
| `MaybePathSteps6.java.golden` | ForPathStepGenerator (representative) |
| `VTaskPathSteps6.java.golden` | ForPathStepGenerator (representative) |

**Updating**: `./gradlew :hkj-processor:test --tests "*updateGoldenFiles*" -DupdateGolden=true`

### Task 9: Runtime Tests for Generated Arities

**Module**: `hkj-core` (test source set)

**ForTest.java extensions**:
- `@Nested` class `ExtendedArityMonadicTest`: Tests MonadicSteps 6, 7, 8 with Id monad
  - 6-step, 7-step, 8-step chains mixing `from()` and `let()`
  - Tuple-variant and spread-variant `yield()` at each arity
- `@Nested` class `ExtendedArityFilterableTest`: Tests FilterableSteps 6, 7, 8 with List monad
  - `when()` filtering at arities 6-8
  - Cartesian products with 6+ generators
- Null validation tests for all new public methods

**ForOpticTest.java extensions**:
- `focus()` at arities 5-7 (stepping into 6, 7, 8)
- `match()` at arities 5-7 for filterable steps

**ForPathTest.java extensions**:
- Tests for each path type at their newly available arities
- Priority: EitherPath, TryPath, IOPath, VTaskPath, MaybePath
- Both success and failure/empty paths tested

**Testing patterns** (per TESTING-GUIDE.md):
- `@DisplayName` on every test class and method
- `@Nested` classes for logical grouping
- AssertJ for all assertions
- Test both success and failure/empty paths
- Null validation tests for all public methods
- Both tuple-variant and spread-variant yield tested
- `@ParameterizedTest` with `@MethodSource` where arity can be parameterised
- Property-based tests with jqwik for Tuple map identity law

---

## Implementation Order

```
Task 1: @GenerateForComprehensions annotation  ──┐
Task 2: Function6-8 in hkj-api (hand-written)  ──┤
                                                  │
                                                  ├──→ Task 3: ForComprehensionProcessor skeleton
                                                  │         │
                                                  │    Task 4: TupleGenerator  ────────┐
                                                  │    Task 5: ForStepGenerator  ──────┤
                                                  │    Task 6: ForPathStepGenerator  ──┤
                                                  │                                    │
                                                  │                                    ├─→ Task 8: Golden file tests
                                                  │                                    │
                                                  └─→ Task 7: Sealed permits + bridges ┤
                                                                                       │
                                                                                       └─→ Task 9: Runtime tests
```

**Recommended execution order**:

1. **Tasks 1 + 2 (parallel)**: Annotation + Function6-8 (no dependencies between them)
2. **Task 3**: Processor skeleton with empty generate methods
3. **Tasks 4, 5, 6 (sequential)**: Generator implementations (each builds on prior patterns)
4. **Task 8**: Golden file tests (validate generator output)
5. **Task 7**: Bridge methods + sealed permits in hand-written code
6. **Task 9**: Runtime integration tests

---

## What is Generated vs. What is Hand-Written

| Artefact | Arities 1-5 | Arities 6-8 | Maintenance |
|----------|-------------|-------------|-------------|
| `Tuple` records | Hand-written | **Generated** by hkj-processor | Processor template |
| `Function` interfaces | Hand-written (hkj-api) | Hand-written (hkj-api) | Manual (3 trivial files) |
| `MonadicSteps` | Hand-written (For.java inner) | **Generated** (top-level) | Processor template |
| `FilterableSteps` | Hand-written (For.java inner) | **Generated** (top-level) | Processor template |
| `*PathSteps` | Hand-written (ForPath.java inner) | **Generated** (top-level) | Processor template |
| `Tuple.of()` overloads | Hand-written | Hand-written (3 lines) | Manual |
| Sealed `permits` clauses | Hand-written | Hand-written (list update) | Manual |
| Bridge methods (Steps5 -> Steps6) | N/A | Hand-written | Manual |
| Golden files | Existing in hkj-processor | New golden files | `./gradlew ... -DupdateGolden=true` |
| Runtime tests | Hand-written | Hand-written | Manual |

**Raising arity from 8 to 12 in the future requires**:
1. Change `@GenerateForComprehensions(maxArity = 12)` in package-info.java
2. Add Function9-12 in hkj-api (4 trivial files)
3. Update sealed permits in Tuple.java and For.java
4. Update Steps8 bridge methods to point to Steps9
5. Update golden files
6. Add runtime tests for arities 9-12

No changes to the processor or generator logic.

---

## Conventions and Standards Checklist

### Generated Code Standards

- [ ] Every generated file includes `@Generated("hkj-processor")` annotation
- [ ] Generated Javadoc follows STYLE-GUIDE.md: British English, no emojis, no em dashes
- [ ] Generated code passes `./gradlew spotlessCheck` (Google Java Format)
- [ ] JSpecify `@Nullable` annotations match hand-written equivalents
- [ ] `Objects.requireNonNull()` with descriptive messages on all public method parameters
- [ ] Package-private constructors for step classes

### Processor Code Standards

- [ ] Java 25 with `--enable-preview`
- [ ] `@AutoService(Processor.class)` registration
- [ ] Added to `module-info.java` `provides` clause
- [ ] Tested with golden file comparisons (following GoldenFileTest pattern)
- [ ] Tested with RuntimeCompilationHelper (following GeneratedOpticLawsTest pattern)
- [ ] Deterministic output (no timestamps, no random ordering)
- [ ] Error messages via `processingEnv.getMessager()` for misconfiguration

### Testing Standards (per TESTING-GUIDE.md)

- [ ] Golden file tests in `hkj-processor` module
- [ ] Runtime tests in `hkj-core` test source set
- [ ] `@DisplayName` on every test class and method
- [ ] `@Nested` classes for logical grouping
- [ ] AssertJ for all assertions
- [ ] Test both success and failure/empty paths
- [ ] Null validation tests for all public methods
- [ ] Both tuple-variant and spread-variant yield tested at each arity
- [ ] Property-based tests with jqwik for Tuple map identity law

### Performance Standards (per PERFORMANCE-TESTING-GUIDE.md)

- [ ] No JMH benchmarks required for this phase
- [ ] Optional: benchmark in `hkj-benchmarks` comparing arity-5 vs arity-8 chain overhead
- [ ] Annotation processing should not noticeably increase compilation time

### Documentation Standards (per STYLE-GUIDE.md)

- [ ] Generated Javadoc on all public types and methods
- [ ] `@see` references to related types (e.g. Tuple6 references Tuple5 and Tuple7)
- [ ] Update main README to mention extended arity support

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Generated code drifts from hand-written patterns | Medium | High | Golden file tests; review generated vs hand-written side-by-side |
| Generated top-level steps break sealed permits | Low | High | Processor emits warning via Messager listing required permits |
| Annotation processing round ordering with @GenerateLenses on generated Tuples | Medium | Medium | Test that lens generation works on processor-generated Tuples |
| Type inference degrades at higher arities | Low | High | Test with explicit type annotations in runtime tests |
| PIT mutation testing threshold affected by new processor code | Low | Low | Adjust threshold or exclude generator classes from mutation |

---

## Success Criteria

1. `./gradlew :hkj-processor:test` passes all golden file comparisons
2. `./gradlew :hkj-core:test` passes all existing tests (backward compatibility)
3. `./gradlew :hkj-core:test` passes new runtime tests for arities 6-8
4. A for-comprehension can chain 8 `from()` calls and yield with all 8 values
5. `ForPath.from(Path.right(x)).from(...) ... .yield(...)` works at arity 8 for all 9 path types
6. `when()` filtering works at arities 6-8 for filterable path types
7. `focus()` and `match()` work at arities 6-7 (stepping into 7, 8)
8. All code passes `./gradlew spotlessCheck`
9. Changing `maxArity` from 8 to 12 requires zero changes to processor code

---

## Estimated Scope

| Component | Lines | Type | Notes |
|-----------|-------|------|-------|
| `@GenerateForComprehensions` annotation | ~15 | Hand-written | hkj-annotations |
| `Function6-8` | ~90 | Hand-written | hkj-api (JPMS constraint) |
| `ForComprehensionProcessor` | ~80 | Hand-written | hkj-processor (orchestrator) |
| `TupleGenerator` | ~150 | Hand-written | hkj-processor (JavaPoet template) |
| `ForStepGenerator` | ~200 | Hand-written | hkj-processor (JavaPoet template) |
| `ForPathStepGenerator` | ~250 | Hand-written | hkj-processor (JavaPoet template, descriptor table) |
| Golden file tests | ~100 | Hand-written | hkj-processor test |
| Sealed permits + bridges | ~80 | Hand-written | hkj-core (Tuple.java, For.java, ForPath.java) |
| Runtime tests | ~1200 | Hand-written | hkj-core test |
| `package-info.java` trigger | ~5 | Hand-written | hkj-core |
| **Total hand-written** | **~2170** | | |
| Generated Tuple6-8 | ~450 | Generated | hkj-core (by processor) |
| Generated MonadicSteps6-8 | ~400 | Generated | hkj-core (by processor) |
| Generated FilterableSteps6-8 | ~500 | Generated | hkj-core (by processor) |
| Generated PathSteps (41 classes) | ~3200 | Generated | hkj-core (by processor) |
| Golden file fixtures | ~4000 | Generated then committed | hkj-processor test resources |
| **Total generated** | **~8550** | | |

The ratio is roughly **4:1 generated-to-hand-written**. All generated output is covered
by golden file tests, and the generator logic lives in the same module as the existing
Lens, Prism, and Traversal processors.

---

## Phase 1b: Consolidation (Post-Completion Opportunity)

Once the generator is proven at arities 6-8, the same templates can replace the
hand-written arities 2-5, reducing long-term maintenance to near zero. This is a
separate follow-on task, not part of the initial Phase 1 delivery.

### Approach

The `ForComprehensionProcessor` accepts a `minArity` parameter (defaulting to 6 in
Phase 1). Lowering it to 2 causes the processor to generate Tuple2-8, MonadicSteps2-8,
FilterableSteps2-8, and all ForPath step classes from arity 2 upward.

```java
@GenerateForComprehensions(minArity = 2, maxArity = 8)
package org.higherkindedj.hkt.expression;
```

### What Can Be Replaced

| Artefact | Current | After Consolidation |
|----------|---------|---------------------|
| `Tuple2..5` records | Hand-written (4 files) | Generated |
| `MonadicSteps2..5` | Hand-written inner classes in `For.java` | Generated top-level classes |
| `FilterableSteps2..5` | Hand-written inner classes in `For.java` | Generated top-level classes |
| All 31 ForPath inner classes | Hand-written in `ForPath.java` | Generated top-level classes |
| `Function3..5` | Hand-written in hkj-api | Remain hand-written (JPMS) |

### What Stays Hand-Written

| Artefact | Reason |
|----------|--------|
| `MonadicSteps1` / `FilterableSteps1` | Structurally unique entry points: hold `Kind<M, A>` not a Tuple; accept `Kind` directly, not a `Function<TupleN, Kind>` |
| `For.java` factory methods | `from()`, `withMonad()`, etc. are the public API entry points |
| `ForPath.java` factory methods | `from(Path.just(...))` entry points and path-type dispatch |
| `ForState`, `ForTraversal`, `ForIndexed` | Not arity-limited; no step class hierarchy |
| `Function3..5` in hkj-api | JPMS prevents cross-module generation |
| `Tuple` sealed interface | Factory `of()` overloads and permits clause |

### Prerequisites

1. Phase 1 is complete and all tests pass at arities 6-8
2. Generated output matches hand-written output character-for-character (verified by diff)
3. Golden file tests cover representative arities (at least arity 3 and arity 5)
4. The `minArity` annotation element is added to `@GenerateForComprehensions`

### Migration Steps

1. Add `int minArity() default 6;` to `@GenerateForComprehensions`
2. Update the processor to accept `minArity` and generate from that value
3. Generate Tuple2-5 and diff against hand-written versions; fix any template deviations
4. Generate MonadicSteps2-5 and FilterableSteps2-5; diff against hand-written inner classes
5. Remove hand-written inner classes from `For.java` (keep Steps1 and factory methods)
6. Remove hand-written inner classes from `ForPath.java` (keep factory methods)
7. Remove hand-written `Tuple2..5` records (keep `Tuple` sealed interface)
8. Update sealed permits to reference only generated types
9. Update golden files for the newly generated arities
10. Verify all existing tests pass without modification

### Estimated Reduction

- ~1800 hand-written lines removed from `For.java` (8 inner classes become generated)
- ~2400 hand-written lines removed from `ForPath.java` (31 inner classes become generated)
- ~400 hand-written lines removed from `Tuple2..5` (4 records become generated)
- Total: ~4600 lines of hand-written boilerplate eliminated
- Remaining hand-written: Steps1 classes (~300 lines), factory methods (~200 lines),
  Function3-5 (~90 lines), Tuple sealed interface (~100 lines)

---

## Documentation, Examples, and Tutorials Update Checklist

After Phase 1 completion, the following documentation and example artefacts need
updating to reflect the extended arity support.

### hkj-book (User-Facing Documentation)

| File | Section | Update Required |
|------|---------|-----------------|
| `hkj-book/src/functional/for_comprehension.md` | Overview | Note that comprehensions support up to 8 bindings (previously 5) |
| `hkj-book/src/functional/for_comprehension.md` | Examples | Add a 6+ binding example demonstrating the extended range |
| `hkj-book/src/effect/forpath_comprehension.md` | Overview | Note extended arity support across all 9 path types |
| `hkj-book/src/effect/forpath_comprehension.md` | Path type table | Update max arity column for each path type |
| `hkj-book/src/effect/forpath_comprehension.md` | VTaskPath example | Consider extending the 4-binding example to 6+ |
| `hkj-book/src/functional/functional_api.md` | MonadZero reference | Mention FilterableSteps support up to arity 8 |
| `hkj-book/src/release-history.md` | New release entry | Document arity extension, new Tuple types, generated code infrastructure |
| `hkj-book/src/glossary.md` | Tuple entry | Add references to Tuple6-8 if a Tuple glossary entry exists |
| `hkj-book/src/SUMMARY.md` | No change | Chapter structure unchanged (for-comprehension and ForPath pages already listed) |

### hkj-book Tutorials

| File | Update Required |
|------|-----------------|
| `hkj-book/src/tutorials/effect/effect_journey.md` | ForPath exercise could mention extended arities |
| `hkj-book/src/tutorials/coretypes/error_handling_journey.md` | Links to for-comprehension docs (no content change needed) |
| `hkj-book/src/tutorials/concurrency/vtask_journey.md` | Note VTaskPath now supports up to 8 bindings |

### hkj-examples Module

| File | Update Required |
|------|-----------------|
| `hkj-examples/.../expression/ForComprehensionExample.java` | Add example demonstrating 6+ binding comprehension |
| `hkj-examples/.../effect/ForPathExample.java` | Add example demonstrating extended ForPath arities |
| `hkj-examples/.../effect/ForPathExample.java` | Show at least one path type (e.g. EitherPath) at arity 6+ |

### Source Code Javadoc

| File | Update Required |
|------|-----------------|
| `hkj-api/.../function/package-info.java` | Add references to Function6, Function7, Function8 |
| `hkj-core/.../expression/For.java` | Update class-level Javadoc to mention arity 8 support |
| `hkj-core/.../expression/ForPath.java` | Update class-level Javadoc to mention extended arities |
| `hkj-core/.../tuple/Tuple.java` | Update sealed interface Javadoc to reference Tuple6-8 |

### Migration and Release Documentation

| File | Update Required |
|------|-----------------|
| New `MIGRATION-0.x.0.md` | Document new types (Tuple6-8, Function6-8), generated step classes, sealed permits changes |
| `README.md` (root) | Mention extended for-comprehension arity if the README references comprehension features |

### What Does NOT Need Updating

- `ForState.java` / `ForTraversal.java` / `ForIndexed.java` documentation: these are not arity-limited
- Optics documentation (lenses, prisms, traversals): unaffected by arity extension
- Spring Boot integration guides: no for-comprehension content
- HKT core concepts documentation: arity extension does not change the encoding
- `STYLE-GUIDE.md` / `TESTING-GUIDE.md` / `PERFORMANCE-TESTING-GUIDE.md`: these are process guides, not feature docs
