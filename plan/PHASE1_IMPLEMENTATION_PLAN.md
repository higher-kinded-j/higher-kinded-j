# Phase 1 Implementation Plan: Lift the For-Comprehension Arity Ceiling

## Overview

Phase 1 removes the hard cap of 5 bindings in for-comprehensions by code-generating
the repetitive step classes, tuple types, and function types up to a configurable maximum
arity. Today, 10 hand-written inner classes in `For.java`, 31 in `ForPath.java`, 4 tuple
records, and 3 function interfaces limit every comprehension to at most 5 chained
operations. This plan extends that to arity 8 via generation while preserving full
backward compatibility with the hand-written API.

## Goals

1. Generate `Tuple6`, `Tuple7`, `Tuple8` records with full method parity to Tuple2-5
2. Generate `Function6`, `Function7`, `Function8` functional interfaces
3. Generate `MonadicSteps6..8` and `FilterableSteps6..8` for `For`
4. Generate `*PathSteps` at arities 4-5 (or higher) for ForPath types currently capped at 3
5. Extend ForPath VTaskPath and MaybePath families (already at 5) to 8
6. Add comprehensive tests following TESTING-GUIDE.md patterns
7. Add golden file tests for generated code following existing processor test patterns
8. Maintain full backward compatibility; existing hand-written code remains untouched

## Non-Goals

- Removing the hand-written code for arities 1-5 (they remain the source of truth)
- Changing the tuple-based accumulation strategy (Phase 2 addresses named bindings via ForState)
- Adding new comprehension features (parallel, traverse, MTL; those are later phases)
- Generating ForState, ForTraversal, or ForIndexed extensions (they are not arity-limited)

---

## Scope of What Exists Today

### Tuple Types (`hkj-core/.../tuple/`)

| Type | Location | Components | Methods |
|------|----------|------------|---------|
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

### For.java Step Classes (10 inner classes)

- `MonadicSteps1..5`: hold `Kind<M, A>` / `Kind<M, Tuple2..5<...>>`
- `FilterableSteps1..5`: mirror of above supporting `when(Predicate)` and `match(Prism)`
- Each step has: `from()`, `let()`, `focus()`, `yield()` (plus `when()`, `match()` for filterable)
- Focus signature: `Lens<A,B>` at step 1; `Function<TupleN, X>` at step N >= 2

### ForPath.java Step Families (31 inner classes)

| Path Type | Arities | Filterable | Notes |
|-----------|---------|------------|-------|
| MaybePath | 1-5 | Yes | Full arity coverage |
| VTaskPath | 1-5 | No | Full arity coverage |
| OptionalPath | 1-3 | Yes | Needs extension to 5+ |
| EitherPath | 1-3 | No | Needs extension to 5+ |
| TryPath | 1-3 | No | Needs extension to 5+ |
| IOPath | 1-3 | No | Needs extension to 5+ |
| IdPath | 1-3 | No | Needs extension to 5+ |
| NonDetPath | 1-3 | Yes | Needs extension to 5+ |
| GenericPath | 1-3 | No | Needs extension to 5+ |

### Test Coverage

| Test File | Lines | Arities Tested | Pattern |
|-----------|-------|----------------|---------|
| `ForTest.java` | 519 | 1-5 | `@Nested` per monad, `@Test` per scenario |
| `ForOpticTest.java` | 957 | 1-5 | `@Nested` per optic type per monad |
| `ForPathTest.java` | 1752 | 1-5 (varies) | `@Nested` per path type |
| `ForStateTest.java` | 412 | N/A (unlimited) | `@Nested` per scenario |
| `ForTraversalTest.java` | 322 | N/A | `@Nested` per operation |
| `ForIndexedTest.java` | 623 | N/A | `@Nested` per operation |

---

## Implementation Tasks

### Task 1: Generate Tuple6, Tuple7, Tuple8

**Module**: `hkj-core`
**Package**: `org.higherkindedj.hkt.tuple`

**What to create** (3 new files):

For each arity N in {6, 7, 8}, create a record:

```java
@GenerateLenses
public record TupleN<A, B, C, D, E, F, ...>(A _1, B _2, C _3, D _4, E _5, F _6, ...)
    implements Tuple {
    // mapFirst(), mapSecond(), ..., mapNth()
    // map(f1, f2, ..., fN) applying each function to corresponding component
}
```

**Conventions to follow**:
- Same Javadoc style as Tuple5 (British English, no emojis, no em dashes)
- `@GenerateLenses` annotation so optics are auto-generated
- JSpecify `@Nullable` annotations matching existing tuples
- Component accessors named `_1()` through `_N()`
- Each `mapXxx` returns a new TupleN with one component transformed
- `map(f1..fN)` applies all functions simultaneously

**Modify**:
- `Tuple.java` sealed interface: add `permits Tuple6, Tuple7, Tuple8`
- Add `Tuple.of(...)` factory overloads for arities 6, 7, 8

**Tests to create**:
- `Tuple6Test.java`, `Tuple7Test.java`, `Tuple8Test.java`
- Follow existing Tuple5 test pattern: test accessors, map methods, equals/hashCode/toString
- Property tests with jqwik for law-like properties (e.g. map identity)

### Task 2: Generate Function6, Function7, Function8

**Module**: `hkj-api`
**Package**: `org.higherkindedj.hkt.function`

**What to create** (3 new files):

```java
@FunctionalInterface
public interface FunctionN<T1, T2, ..., TN, R> {
    @Nullable R apply(T1 t1, T2 t2, ..., TN tN);
}
```

**Conventions**:
- Same Javadoc pattern as Function5
- `@FunctionalInterface` annotation
- JSpecify `@Nullable` on return type
- Located in `hkj-api` (not `hkj-core`) matching existing Function3-5

### Task 3: Extend For.java with MonadicSteps6..8 and FilterableSteps6..8

**Module**: `hkj-core`
**Package**: `org.higherkindedj.hkt.expression`

**What to create** (6 new inner classes inside For.java):

For each arity N in {6, 7, 8}:

`MonadicStepsN<M, A, B, C, D, E, F, ...>`:
- Field: `Kind<M, TupleN<A, B, C, D, E, F, ...>>`
- `from(Function<TupleN<...>, Kind<M, X>>)` → `MonadicSteps(N+1)` (except at max arity)
- `let(Function<TupleN<...>, X>)` → `MonadicSteps(N+1)` (except at max arity)
- `focus(Function<TupleN<...>, X>)` → `MonadicSteps(N+1)` (except at max arity)
- `yield(FunctionN<A, B, C, D, E, F, ..., R>)` → `Kind<M, R>`
- `yield(Function<TupleN<...>, R>)` → `Kind<M, R>` (tuple variant)

`FilterableStepsN<M, A, B, C, D, E, F, ...>`:
- Same as MonadicStepsN plus:
- `when(Predicate<TupleN<...>>)` → same FilterableStepsN (filter in place)
- `match(Prism<X, Y>)` at arity 1; `match(Function<TupleN, Optional<X>>)` at arity >= 2

**Modify**:
- `Steps<M>` sealed interface: add `permits MonadicSteps6..8, FilterableSteps6..8`
- `MonadicSteps5.from()` / `.let()` / `.focus()` → return `MonadicSteps6` (currently terminal)
- `FilterableSteps5.from()` / `.let()` / `.focus()` / `.match()` → return `FilterableSteps6`

**Key implementation details**:
- Each `from()` does: `monad.flatMap(tuple -> monad.map(x -> Tuple.of(tuple._1(), ..., x), next.apply(tuple)), this.computation)`
- Each `let()` does: `monad.map(tuple -> Tuple.of(tuple._1(), ..., f.apply(tuple)), this.computation)`
- Each `focus()` does: `monad.map(tuple -> Tuple.of(tuple._1(), ..., extractor.apply(tuple)), this.computation)`
- Each `when()` does: `monad instanceof MonadZero mz ? (pred.test(tuple) ? ... : mz.zero()) : ...`
- `yield(FunctionN)` does: `monad.map(tuple -> f.apply(tuple._1(), ..., tuple._N()), computation)`

**Backward compatibility**: MonadicSteps5 and FilterableSteps5 currently have no `from()`/`let()`/`focus()` methods; adding them is purely additive.

### Task 4: Extend ForPath Step Families to Higher Arities

**Module**: `hkj-core`
**Package**: `org.higherkindedj.hkt.expression`

**What to create** (new inner classes inside ForPath.java):

Extend path types currently capped at 3 to arity 5 first (matching MaybePath/VTaskPath), then all to 8:

| Path Type | Current Max | Target Max | New Classes |
|-----------|------------|------------|-------------|
| OptionalPath | 3 | 8 | Steps 4-8 (5 classes) |
| EitherPath | 3 | 8 | Steps 4-8 (5 classes) |
| TryPath | 3 | 8 | Steps 4-8 (5 classes) |
| IOPath | 3 | 8 | Steps 4-8 (5 classes) |
| IdPath | 3 | 8 | Steps 4-8 (5 classes) |
| NonDetPath | 3 | 8 | Steps 4-8 (5 classes) |
| GenericPath | 3 | 8 | Steps 4-8 (5 classes) |
| MaybePath | 5 | 8 | Steps 6-8 (3 classes) |
| VTaskPath | 5 | 8 | Steps 6-8 (3 classes) |

**Total new inner classes**: 5 x 7 + 3 x 2 = 41 classes

Each class follows the same pattern as the existing ones:
- Wraps `Kind<WitnessType, TupleN<...>>` internally
- Exposes `from()`, `let()`, `focus()`, `yield()` returning the concrete Path type
- Filterable variants (MaybePath, OptionalPath, NonDetPath) also expose `when()`
- `yield()` narrows from `Kind` back to the concrete Path type using the appropriate helper

**Why this is the largest task**: Each class has roughly 60-80 lines of boilerplate following a strict pattern. At 41 classes, this is ~2800-3200 lines. This is the primary candidate for future generation via the annotation processor.

### Task 5: Tests for Extended For Arities (6-8)

**Module**: `hkj-core` (test source set)
**Package**: `org.higherkindedj.hkt.expression`

**What to create/modify**:

**ForTest.java** additions:
- `@Nested` class `ForIdExtendedArityTest`: Tests for MonadicSteps6, 7, 8 with Id monad
  - Test 6-step chain with `from()` and `let()` mixed
  - Test 7-step chain
  - Test 8-step chain
  - Test tuple-variant yield at each arity
- `@Nested` class `ForListExtendedArityTest`: Tests for FilterableSteps6, 7, 8 with List monad
  - Test filtering at arity 6, 7, 8
  - Test cartesian products with 6+ generators
  - Test when() at higher arities

**ForOpticTest.java** additions:
- Tests for `focus()` at arities 5-7 (stepping into 6, 7, 8)
- Tests for `match()` at arities 5-7 for filterable steps

**ForPathTest.java** additions:
- Extend each `@Nested` path test class with tests at arities 4-8
- Priority: EitherPath, TryPath, IOPath (most commonly used in real workflows)

**Testing patterns** (per TESTING-GUIDE.md):
- `@Nested` + `@DisplayName` for organisation
- AssertJ for assertions
- Test both success and failure/empty paths
- Test null validation on new methods
- Test tuple variant and spread variant of yield

### Task 6: Golden File Tests for Generated Arities

**Module**: `hkj-processor` (test source set)
**Location**: `hkj-processor/src/test/resources/golden/`

**If generation is added to the processor** (see Task 7), create golden files for:
- `Tuple6.java.golden`, `Tuple7.java.golden`, `Tuple8.java.golden`
- Verify generated lenses for TupleN records

**For the initial hand-crafted implementation**: No golden files needed since the code is hand-written in hkj-core, not processor-generated. Golden file tests apply only when we automate generation via the annotation processor.

### Task 7: Annotation Processor Generator (Future Automation)

**Module**: `hkj-processor`

This task designs but does **not implement** the generator. It creates the specification that would allow future automation:

**Create**: `plan/FOR_COMPREHENSION_GENERATOR_SPEC.md`

Document the generation rules so a future `ForComprehensionGenerator` can produce:
- TupleN records from a template
- FunctionN interfaces from a template
- MonadicStepsN / FilterableStepsN from a template
- *PathStepsN from a template per path type
- The rules for how signatures change across arities
- The sealed interface permits clauses that need updating

This specification becomes the blueprint for automating Phase 1 output, eliminating
future hand-writing when the target arity is raised from 8 to 12 or beyond.

---

## Detailed File Inventory

### New Files to Create

| # | File | Module | Description |
|---|------|--------|-------------|
| 1 | `Tuple6.java` | hkj-core | 6-arity tuple record |
| 2 | `Tuple7.java` | hkj-core | 7-arity tuple record |
| 3 | `Tuple8.java` | hkj-core | 8-arity tuple record |
| 4 | `Function6.java` | hkj-api | 6-arity functional interface |
| 5 | `Function7.java` | hkj-api | 7-arity functional interface |
| 6 | `Function8.java` | hkj-api | 8-arity functional interface |
| 7 | `Tuple6Test.java` | hkj-core (test) | Tuple6 unit tests |
| 8 | `Tuple7Test.java` | hkj-core (test) | Tuple7 unit tests |
| 9 | `Tuple8Test.java` | hkj-core (test) | Tuple8 unit tests |

### Existing Files to Modify

| # | File | Changes |
|---|------|---------|
| 1 | `Tuple.java` | Add `permits Tuple6, Tuple7, Tuple8`; add `of()` overloads |
| 2 | `For.java` | Add 6 new inner classes (MonadicSteps6-8, FilterableSteps6-8); update sealed permits; add from/let/focus to Steps5 classes |
| 3 | `ForPath.java` | Add ~41 new inner classes for extended path step families |
| 4 | `ForTest.java` | Add `@Nested` test classes for arities 6-8 |
| 5 | `ForOpticTest.java` | Add focus/match tests at arities 5-8 |
| 6 | `ForPathTest.java` | Extend per-path nested tests to higher arities |

---

## Implementation Order

The tasks have dependencies that dictate ordering:

```
Task 1: Tuple6-8          ──┐
Task 2: Function6-8       ──┤
                            ├──→ Task 3: For.java Steps6-8
                            │         │
                            │         ├──→ Task 4: ForPath.java extended steps
                            │         │
                            │         └──→ Task 5: Tests for For arities 6-8
                            │                  │
                            │                  └──→ Task 6: Golden file tests
                            │
                            └──→ Task 7: Generator spec (can start any time)
```

**Recommended execution order**:

1. **Tasks 1 + 2 (parallel)**: Tuple6-8 and Function6-8 have no mutual dependency
2. **Task 3**: Requires Tuple6-8 and Function6-8
3. **Task 4**: Requires Task 3 (For steps define the pattern ForPath follows)
4. **Task 5**: Requires Tasks 3 and 4
5. **Task 6**: Requires Task 5
6. **Task 7**: Independent; can be written at any point

---

## Conventions and Standards Checklist

### Code Standards (per project patterns)

- [ ] Java 25 with `--enable-preview` features (sealed interfaces, pattern matching, records)
- [ ] British English in all Javadoc (behaviour, colour, optimisation)
- [ ] No emojis in code or documentation
- [ ] No em dashes in documentation; use commas or semicolons
- [ ] JSpecify `@Nullable` annotations where applicable
- [ ] `Objects.requireNonNull()` with descriptive messages for all public method parameters
- [ ] `final` classes for non-extensible types
- [ ] Package-private constructors for step classes (created only by builder methods)
- [ ] Google Java Format via Spotless (`./gradlew spotlessApply`)

### Testing Standards (per TESTING-GUIDE.md)

- [ ] `@DisplayName` on every test class and method
- [ ] `@Nested` classes for logical grouping
- [ ] AssertJ for all assertions (`assertThat`)
- [ ] Test both success and failure/empty paths
- [ ] Null validation tests for all public methods
- [ ] Both tuple-variant and spread-variant yield tested at each arity
- [ ] Property-based tests with jqwik for Tuple map identity law
- [ ] No performance tests in core module (benchmarks go in hkj-benchmarks)

### Performance Standards (per PERFORMANCE-TESTING-GUIDE.md)

- [ ] No JMH benchmarks required for this phase (pure API extension)
- [ ] If composition overhead is a concern, add benchmark in hkj-benchmarks comparing
      arity-5 vs arity-8 chain performance
- [ ] Benchmark should use `@BenchmarkMode(Mode.Throughput)` with standard JMH config

### Documentation Standards (per STYLE-GUIDE.md)

- [ ] Javadoc on all public types and methods
- [ ] Code examples in Javadoc for new Tuple types
- [ ] `@see` references to related types (e.g. Tuple6 references Tuple5 and Tuple7)
- [ ] `@since` tag if project uses versioning

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| ForPath.java becomes unwieldy (3000+ lines) | High | Medium | Future task: split into per-path-type files or generate |
| For.java sealed permits clause hits readability limit | Medium | Low | Well-commented sections; future generation |
| Type inference degrades at higher arities | Low | High | Test with explicit type annotations; verify IDE support |
| Compile time increases with more inner classes | Low | Low | Measure before/after; inner classes are simple |
| Breaking change to sealed interface | None | High | Purely additive; existing permits are untouched |

---

## Success Criteria

1. All existing tests pass unchanged (backward compatibility)
2. A for-comprehension can chain 8 `from()` calls and yield with all 8 values
3. `ForPath.from(Path.right(x)).from(...).from(...)...` works at arity 8 for all 9 path types
4. `when()` filtering works at arities 6-8 for filterable path types
5. `focus()` and `match()` work at arities 6-7 (stepping into 7, 8)
6. All new code passes `./gradlew spotlessCheck`
7. All new code has test coverage verified by `./gradlew :hkj-core:test :hkj-core:jacocoTestReport`
8. No regressions in existing test suites

---

## Estimated Scope

| Task | New Lines (approx) | Complexity |
|------|-------------------|------------|
| Tuple6-8 | ~450 | Low (mechanical) |
| Function6-8 | ~90 | Low (trivial) |
| For.java extensions | ~600 | Medium (follows pattern) |
| ForPath.java extensions | ~3200 | Medium (repetitive) |
| Tests | ~1500 | Medium (comprehensive) |
| Generator spec | ~200 | Low (documentation) |
| **Total** | **~6040** | |

The majority of the work (ForPath extensions) is highly repetitive, following the
exact pattern established by the existing MaybePath and VTaskPath step classes. This
repetitiveness is itself the strongest argument for automating generation in the future.
