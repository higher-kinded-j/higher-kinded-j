# Phase 1 Implementation Plan: Lift the For-Comprehension Arity Ceiling

## Overview

Phase 1 removes the hard cap of 5 bindings in for-comprehensions. Rather than hand-writing
~6000 lines of mechanically repetitive boilerplate, we build a **source generator** that
produces the Tuple, Function, For step, and ForPath step classes from templates. The generator
itself becomes the maintained artefact; the output is regenerable on demand.

Today the hand-written code limits comprehensions to 5 chained operations across 10 inner
classes in `For.java`, 31 in `ForPath.java`, 4 tuple records, and 3 function interfaces.
This plan extends coverage to arity 8 (configurable higher) while reducing long-term
maintenance burden to near zero for the generated artefacts.

## Goals

1. Build a JavaPoet-based source generator as a new Gradle module (`hkj-generator`)
2. Generate `Tuple6..8`, `Function6..8`, `MonadicSteps6..8`, `FilterableSteps6..8`, and
   all 9 ForPath step families up to arity 8 from a single configurable run
3. Wire generation into the Gradle build so generated sources compile alongside hand-written code
4. Add golden file tests for all generated output
5. Add comprehensive runtime tests following TESTING-GUIDE.md patterns
6. Maintain full backward compatibility; arities 1-5 remain hand-written as the reference implementation

## Non-Goals

- Replacing the hand-written arities 1-5 (they remain the source of truth and reference pattern)
- Changing the tuple-based accumulation strategy (Phase 2 addresses named bindings)
- Adding new comprehension features (parallel, traverse, MTL; those are later phases)
- Generating ForState, ForTraversal, or ForIndexed extensions (they are not arity-limited)

---

## Architecture Decision: Why a Separate Generator Module

### Options Considered

| Approach | Pros | Cons |
|----------|------|------|
| **A. Annotation processor** | Existing infrastructure; auto-discovered | Requires annotated source as trigger; wrong fit for "generate from nothing" |
| **B. Gradle JavaExec task** | Simple to wire | Generator code mixed into buildSrc or root; no standalone testability |
| **C. Dedicated generator module** | Testable in isolation; reusable; clean separation | New module in settings.gradle.kts |

**Decision: Option C** (`hkj-generator` module).

Rationale:
- The generator produces source files for two target modules (`hkj-api` for Functions, `hkj-core`
  for Tuples/Steps). An annotation processor cannot produce code in a *different* module from
  the one it processes.
- A dedicated module uses the same JavaPoet (Palantir 0.7.0) already used by `hkj-processor`.
- It can be tested independently with golden file comparisons, exactly matching the existing
  `hkj-processor` test pattern.
- The generation is invoked via a Gradle task that runs before `compileJava` in the target modules.
- The configurable maximum arity lives in one place; raising it from 8 to 12 or 16 is a
  single property change.

### Build Integration

```
hkj-generator/
  src/main/java/.../generator/
    TupleGenerator.java          -- generates TupleN records
    FunctionGenerator.java       -- generates FunctionN interfaces
    ForStepGenerator.java        -- generates MonadicStepsN, FilterableStepsN
    ForPathStepGenerator.java    -- generates per-path-type step classes
    GeneratorMain.java           -- CLI entry point
    GeneratorConfig.java         -- arity range, output dirs, package names
  src/test/java/.../generator/
    TupleGeneratorTest.java      -- golden file comparison
    FunctionGeneratorTest.java
    ForStepGeneratorTest.java
    ForPathStepGeneratorTest.java
  src/test/resources/golden/
    Tuple6.java.golden
    Tuple7.java.golden
    Tuple8.java.golden
    Function6.java.golden
    ...
  build.gradle.kts               -- depends on javapoet, junit, assertj
```

**Gradle wiring** (root `build.gradle.kts` or per-module):

```kotlin
// In hkj-core/build.gradle.kts
val generateForComprehensionSources by tasks.registering(JavaExec::class) {
    group = "build"
    description = "Generate Tuple, Function, For step, and ForPath step classes"
    mainClass.set("org.higherkindedj.generator.GeneratorMain")
    classpath = project(":hkj-generator").sourceSets.main.get().runtimeClasspath
    args(
        "--min-arity", "6",
        "--max-arity", "8",
        "--tuple-output", layout.buildDirectory.dir("generated/sources/forComprehension/main/java").get().asFile.path,
        "--function-output", project(":hkj-api").layout.buildDirectory.dir("generated/sources/forComprehension/main/java").get().asFile.path,
        "--for-output", layout.buildDirectory.dir("generated/sources/forComprehension/main/java").get().asFile.path,
        "--forpath-output", layout.buildDirectory.dir("generated/sources/forComprehension/main/java").get().asFile.path
    )
}

sourceSets.main.get().java.srcDir(
    layout.buildDirectory.dir("generated/sources/forComprehension/main/java")
)

tasks.named("compileJava") {
    dependsOn(generateForComprehensionSources)
}
```

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

### Task 1: Create the `hkj-generator` Module

**New module**: `hkj-generator`

**Dependencies**: Palantir JavaPoet 0.7.0, JUnit 6, AssertJ, Google Java Format (for output formatting)

**Configuration class** (`GeneratorConfig`):
```java
public record GeneratorConfig(
    int minArity,       // 6 (start after hand-written)
    int maxArity,       // 8 (configurable)
    Path tupleOutputDir,
    Path functionOutputDir,
    Path forOutputDir,
    Path forPathOutputDir,
    String tuplePackage,    // org.higherkindedj.hkt.tuple
    String functionPackage, // org.higherkindedj.hkt.function
    String forPackage,      // org.higherkindedj.hkt.expression
    String forPathPackage   // org.higherkindedj.hkt.expression
) {}
```

**Entry point** (`GeneratorMain`): Parses CLI args, creates config, runs all generators.

**`settings.gradle.kts` change**: Add `include("hkj-generator")`.

### Task 2: TupleGenerator

**Generates**: `Tuple6.java`, `Tuple7.java`, `Tuple8.java` (or up to maxArity)

**Template rules** (derived from hand-written Tuple2-5):

For each arity N, generate a record:

```java
package org.higherkindedj.hkt.tuple;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.annotations.GenerateLenses;
import org.jspecify.annotations.Nullable;

/**
 * An immutable tuple of N elements.
 *
 * @param <A> ... through <{LAST}> type parameters
 * @param _1 ... through _N component descriptions
 */
@GenerateLenses
public record TupleN<A, B, C, D, E, F, ...>(
    @Nullable A _1,
    @Nullable B _2,
    ... through _N
) implements Tuple {

    /** Applies a function to each component simultaneously. */
    public <A2, B2, ...> TupleN<A2, B2, ...> map(
        Function<? super A, ? extends A2> f1,
        Function<? super B, ? extends B2> f2,
        ... through fN
    ) {
        return new TupleN<>(f1.apply(_1), f2.apply(_2), ...);
    }

    /** Transforms the first component. */
    public <A2> TupleN<A2, B, C, ...> mapFirst(Function<? super A, ? extends A2> f) {
        return new TupleN<>(f.apply(_1), _2, _3, ...);
    }

    // mapSecond, mapThird, ... mapNth for each component
}
```

**Type parameter naming convention**: A, B, C, D, E, F, G, H (matching existing Tuple2-5 pattern).

**Also generates** additions to `Tuple.java`:
- New `of()` factory overloads (generated as a patch file or separate helper class)
- Note: The sealed `permits` clause must be updated in the hand-written `Tuple.java`;
  the generator produces a comment indicating what must be added.

### Task 3: FunctionGenerator

**Generates**: `Function6.java`, `Function7.java`, `Function8.java`

**Template rules** (derived from hand-written Function3-5):

```java
package org.higherkindedj.hkt.function;

import org.jspecify.annotations.Nullable;

/**
 * Represents a function that accepts N arguments and produces a result.
 *
 * @param <T1> ... through <TN> argument types
 * @param <R> the type of the result
 */
@FunctionalInterface
public interface FunctionN<T1, T2, ..., TN, R> {
    /**
     * Applies this function to the given arguments.
     *
     * @param t1 ... through tN argument descriptions
     * @return the function result
     */
    @Nullable R apply(T1 t1, T2 t2, ..., TN tN);
}
```

**Type parameter naming**: T1..TN, R (matching existing Function3-5).

### Task 4: ForStepGenerator

**Generates**: `MonadicSteps6..8` and `FilterableSteps6..8` as standalone Java files.

These are generated as **top-level classes** in separate files rather than inner classes of
`For.java`. The hand-written `For.java` is modified minimally:
- Update `Steps<M>` sealed interface permits to include the generated classes
- Add `from()`/`let()`/`focus()` to `MonadicSteps5` and `FilterableSteps5` returning Steps6

**Template rules for MonadicStepsN** (derived from MonadicSteps2-5):

```java
package org.higherkindedj.hkt.expression;

// imports...

/**
 * Step N in a non-filterable for-comprehension, holding N accumulated results.
 *
 * @param <M> The witness type of the Monad.
 * @param <A> through <{N}> the accumulated value types.
 */
public final class MonadicStepsN<M extends WitnessArity<TypeArity.Unary>, A, B, C, ...>
    implements For.Steps<M> {

    private final Monad<M> monad;
    private final Kind<M, TupleN<A, B, C, ...>> computation;

    MonadicStepsN(Monad<M> monad, Kind<M, TupleN<A, B, C, ...>> computation) {
        this.monad = monad;
        this.computation = computation;
    }

    // from() -> MonadicSteps(N+1)  [omitted at maxArity]
    // let()  -> MonadicSteps(N+1)  [omitted at maxArity]
    // focus() -> MonadicSteps(N+1) [omitted at maxArity]

    // yield(FunctionN<A, B, ..., R>) -> Kind<M, R>
    // yield(Function<TupleN<A, B, ...>, R>) -> Kind<M, R>
}
```

**Template rules for FilterableStepsN** add:
- `when(Predicate<TupleN<...>>)` -> same FilterableStepsN
- `match(Function<TupleN<...>, Optional<X>>)` -> FilterableSteps(N+1) [omitted at maxArity]

**Key implementation patterns** (from existing steps):
- `from()`: `monad.flatMap(t -> monad.map(x -> Tuple.of(t._1(), ..., t._N(), x), next.apply(t)), computation)`
- `let()`: `monad.map(t -> Tuple.of(t._1(), ..., t._N(), f.apply(t)), computation)`
- `focus()`: `monad.map(t -> Tuple.of(t._1(), ..., t._N(), extractor.apply(t)), computation)`
- `when()`: `monad.flatMap(t -> pred.test(t) ? monad.of(t) : ((MonadZero<M>) monad).zero(), computation)`
- `yield(spread)`: `monad.map(t -> f.apply(t._1(), t._2(), ..., t._N()), computation)`
- `yield(tuple)`: `monad.map(f, computation)`

### Task 5: ForPathStepGenerator

**Generates**: Per-path-type step classes for arities beyond what is hand-written.

This is the largest generator. For each path type descriptor:

```java
record PathTypeDescriptor(
    String pathTypeName,         // e.g. "EitherPath"
    String witnessType,          // e.g. "EitherKind.Witness<E>"
    String kindHelperField,      // e.g. "EITHER"
    String kindHelperClass,      // e.g. "EitherKindHelper"
    boolean filterable,          // whether it supports when()
    List<String> extraTypeParams, // e.g. ["E"] for EitherPath<E, A>
    int currentMaxArity,         // e.g. 3 for EitherPath
    String narrowMethod,         // e.g. "narrow" or custom
    String widenMethod           // e.g. "widen" or custom
) {}
```

The generator has a built-in table of all 9 path types:

| Path Type | Witness | Extra Type Params | Filterable | Current Max |
|-----------|---------|-------------------|------------|-------------|
| MaybePath | `MaybeKind.Witness` | none | Yes | 5 |
| OptionalPath | `OptionalKind.Witness` | none | Yes | 3 |
| EitherPath | `EitherKind.Witness<E>` | `E` | No | 3 |
| TryPath | `TryKind.Witness` | none | No | 3 |
| IOPath | `IOKind.Witness` | none | No | 3 |
| VTaskPath | `VTaskKind.Witness` | none | No | 5 |
| IdPath | `IdKind.Witness` | none | No | 3 |
| NonDetPath | `ListKind.Witness` | none | Yes | 3 |
| GenericPath | `F` | `F` | No | 3 |

For each path type, for each arity from `currentMaxArity + 1` to `maxArity`, generate a
step class following the exact pattern of the existing hand-written classes.

**Generated as**: Separate top-level files (e.g. `EitherPathSteps4.java` through
`EitherPathSteps8.java`) to avoid bloating ForPath.java.

The hand-written `ForPath.java` is modified minimally to import and delegate to the
generated classes where the existing step classes would have returned them.

### Task 6: Golden File Tests for All Generated Output

**Module**: `hkj-generator` (test source set)

For each generated file, create a corresponding `.golden` file containing the expected output.
Tests run the generator and compare output character-by-character (after normalisation).

**Test pattern** (following `hkj-processor/GoldenFileTest.java`):

```java
@ParameterizedTest(name = "Generated {0} matches golden file")
@MethodSource("goldenTestCases")
@DisplayName("Generated code matches golden file")
void generatedCodeMatchesGolden(String fileName, String goldenPath) {
    String generated = runGenerator(fileName);
    String golden = readGolden(goldenPath);
    assertThat(normalise(generated)).isEqualTo(normalise(golden));
}
```

**Golden files to create**:

| Golden File | Generator |
|-------------|-----------|
| `Tuple6.java.golden` | TupleGenerator |
| `Tuple7.java.golden` | TupleGenerator |
| `Tuple8.java.golden` | TupleGenerator |
| `Function6.java.golden` | FunctionGenerator |
| `Function7.java.golden` | FunctionGenerator |
| `Function8.java.golden` | FunctionGenerator |
| `MonadicSteps6.java.golden` | ForStepGenerator |
| `MonadicSteps7.java.golden` | ForStepGenerator |
| `MonadicSteps8.java.golden` | ForStepGenerator |
| `FilterableSteps6.java.golden` | ForStepGenerator |
| `FilterableSteps7.java.golden` | ForStepGenerator |
| `FilterableSteps8.java.golden` | ForStepGenerator |
| `EitherPathSteps4.java.golden` | ForPathStepGenerator |
| `MaybePathSteps6.java.golden` | ForPathStepGenerator |
| `VTaskPathSteps6.java.golden` | ForPathStepGenerator |
| ... (representative sample per path type) | |

**Updating golden files**: `./gradlew :hkj-generator:test --tests "*updateGoldenFiles*" -DupdateGolden=true`

### Task 7: Minimal Hand-Written Changes (Sealed Permits + Bridge Methods)

These are the only hand-written changes required in the existing source:

**`Tuple.java`**: Update sealed `permits` clause and add `of()` factory overloads:
```java
public sealed interface Tuple
    permits Tuple2, Tuple3, Tuple4, Tuple5,
            Tuple6, Tuple7, Tuple8 {  // <-- add generated types

    // Add of() overloads for arities 6, 7, 8
    static <A, B, C, D, E, F> Tuple6<A, B, C, D, E, F> of(A a, B b, C c, D d, E e, F f) { ... }
    static <A, B, C, D, E, F, G> Tuple7<A, B, C, D, E, F, G> of(A a, B b, C c, D d, E e, F f, G g) { ... }
    static <A, B, C, D, E, F, G, H> Tuple8<A, B, C, D, E, F, G, H> of(A a, B b, C c, D d, E e, F f, G g, H h) { ... }
}
```

**`For.java`**: Update `Steps<M>` sealed interface and add bridge methods on Steps5:
```java
// In Steps<M>: add permits for generated step classes
public sealed interface Steps<M extends WitnessArity<TypeArity.Unary>>
    permits MonadicSteps1, ..., MonadicSteps5,
            MonadicSteps6, MonadicSteps7, MonadicSteps8,      // <-- generated
            FilterableSteps1, ..., FilterableSteps5,
            FilterableSteps6, FilterableSteps7, FilterableSteps8 {} // <-- generated

// In MonadicSteps5: add from(), let(), focus() returning MonadicSteps6
// In FilterableSteps5: add from(), let(), focus(), match() returning FilterableSteps6
```

**`ForPath.java`**: Add bridge methods on the highest existing step class for each path type
so it returns the generated Steps class at the next arity. For example, `EitherPathSteps3.from()`
would return the generated `EitherPathSteps4`.

### Task 8: Runtime Tests for Generated Arities

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
- Priority order: EitherPath, TryPath, IOPath, VTaskPath, MaybePath
- Both success and failure/empty paths tested

**Testing patterns** (per TESTING-GUIDE.md):
- `@DisplayName` on every test class and method
- `@Nested` classes for logical grouping
- AssertJ for all assertions
- Test both success and failure/empty paths
- Null validation tests for all public methods
- Both tuple-variant and spread-variant yield tested
- `@ParameterizedTest` with `@MethodSource` where arity can be parameterised

---

## Implementation Order

```
Task 1: hkj-generator module skeleton ──┐
                                         │
Task 2: TupleGenerator      ──────────┐ │
Task 3: FunctionGenerator   ──────────┤ │
                                      ├─┴──→ Task 4: ForStepGenerator
                                      │            │
                                      │            ├──→ Task 5: ForPathStepGenerator
                                      │            │
                                      │            └──→ Task 6: Golden file tests
                                      │
                                      └─────→ Task 7: Hand-written bridge changes
                                                   │
                                                   └──→ Task 8: Runtime tests
```

**Recommended execution order**:

1. **Task 1**: Module skeleton, build.gradle.kts, GeneratorConfig, GeneratorMain
2. **Tasks 2 + 3 (parallel)**: TupleGenerator and FunctionGenerator
3. **Task 4**: ForStepGenerator (requires Tuple and Function types to exist)
4. **Task 5**: ForPathStepGenerator (requires ForStepGenerator patterns)
5. **Task 6**: Golden file tests for all generators
6. **Task 7**: Minimal hand-written bridge changes (sealed permits, factory overloads, bridge methods)
7. **Task 8**: Runtime integration tests

---

## What is Generated vs. What is Hand-Written

| Artefact | Arities 1-5 | Arities 6-8 | Maintenance |
|----------|-------------|-------------|-------------|
| `Tuple` records | Hand-written | **Generated** | Generator template |
| `Function` interfaces | Hand-written (3-5), stdlib (1-2) | **Generated** | Generator template |
| `MonadicSteps` | Hand-written (inner classes in For.java) | **Generated** (top-level) | Generator template |
| `FilterableSteps` | Hand-written (inner classes in For.java) | **Generated** (top-level) | Generator template |
| `*PathSteps` | Hand-written (inner classes in ForPath.java) | **Generated** (top-level) | Generator template |
| `Tuple.of()` overloads | Hand-written | Hand-written (3 lines) | Manual |
| Sealed `permits` clauses | Hand-written | Hand-written (list update) | Manual |
| Bridge methods (Steps5 -> Steps6) | N/A (new) | Hand-written | Manual |
| Golden files | N/A | **Generated** then committed | `./gradlew :hkj-generator:test -DupdateGolden=true` |
| Runtime tests | Hand-written | Hand-written | Manual |

**Key insight**: Raising from arity 8 to arity 12 in the future requires:
1. Change `maxArity` property from 8 to 12
2. Run generator
3. Update sealed permits (add 4 more types)
4. Update Steps8 bridge methods to point to Steps9
5. Commit golden files
6. Add runtime tests for arities 9-12

No new generator code is needed. This is the maintenance burden reduction.

---

## Conventions and Standards Checklist

### Generated Code Standards

- [ ] Every generated file starts with `// Generated by hkj-generator. Do not edit.`
- [ ] Generated Javadoc follows STYLE-GUIDE.md: British English, no emojis, no em dashes
- [ ] Generated code passes `./gradlew spotlessCheck` (Google Java Format)
- [ ] JSpecify `@Nullable` annotations match hand-written equivalents
- [ ] `Objects.requireNonNull()` with descriptive messages on all public method parameters
- [ ] Package-private constructors for step classes

### Generator Code Standards

- [ ] Java 25 with `--enable-preview`
- [ ] Unit tested with golden file comparisons
- [ ] Configurable arity range via `GeneratorConfig`
- [ ] Deterministic output (no timestamps, no random ordering)

### Testing Standards (per TESTING-GUIDE.md)

- [ ] Golden file tests in `hkj-generator` module
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
- [ ] Generation itself should complete in under 5 seconds

### Documentation Standards (per STYLE-GUIDE.md)

- [ ] Generated Javadoc on all public types and methods
- [ ] `@see` references to related types (e.g. Tuple6 references Tuple5 and Tuple7)
- [ ] README in `hkj-generator/` explaining how to run and configure the generator
- [ ] Update main README to mention extended arity support

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Generated code drifts from hand-written patterns | Medium | High | Golden file tests; review generated vs hand-written side by side |
| Generated top-level steps break sealed permits | Low | High | Generator emits comments listing required permits changes |
| JavaPoet output formatting differs from Spotless | Medium | Low | Run `spotlessApply` after generation; or format in generator |
| Module dependency cycle (generator needs types that depend on generation) | Low | High | Generator depends on nothing from hkj-core; it generates source text only |
| Type inference degrades at higher arities | Low | High | Test with explicit type annotations in runtime tests |
| Build ordering: generated sources must exist before compilation | Medium | Medium | Gradle task dependency `compileJava.dependsOn(generateSources)` |

---

## Success Criteria

1. `./gradlew :hkj-generator:test` passes all golden file comparisons
2. `./gradlew :hkj-core:test` passes all existing tests (backward compatibility)
3. `./gradlew :hkj-core:test` passes new runtime tests for arities 6-8
4. A for-comprehension can chain 8 `from()` calls and yield with all 8 values
5. `ForPath.from(Path.right(x)).from(...) ... .yield(...)` works at arity 8 for all 9 path types
6. `when()` filtering works at arities 6-8 for filterable path types
7. `focus()` and `match()` work at arities 6-7 (stepping into 7, 8)
8. All code passes `./gradlew spotlessCheck`
9. Raising maxArity from 8 to 12 requires zero changes to generator code

---

## Estimated Scope

| Component | Hand-Written Lines | Generated Lines | Notes |
|-----------|-------------------|----------------|-------|
| hkj-generator module | ~800 | 0 | Generator code + tests |
| Golden files | 0 | ~4000 | Committed test fixtures |
| Tuple6-8 | 0 | ~450 | Generated records |
| Function6-8 | 0 | ~90 | Generated interfaces |
| MonadicSteps6-8 | 0 | ~400 | Generated top-level classes |
| FilterableSteps6-8 | 0 | ~500 | Generated top-level classes |
| ForPath steps (41 classes) | 0 | ~3200 | Generated top-level classes |
| Sealed permits + bridges | ~50 | 0 | Minimal hand-written changes |
| Runtime tests | ~1200 | 0 | Hand-written tests |
| **Total hand-written** | **~2050** | | |
| **Total generated** | | **~8640** | |

The ratio is roughly **4:1 generated-to-hand-written**. Every line of generated code is
covered by a golden file test, ensuring it stays correct as the generator evolves.
