# Phase 1c Implementation Plan: Raise Arity Ceiling to 12

## Overview

Extend for-comprehension support from arity 8 to arity 12. The generator
infrastructure from Phase 1 already handles arbitrary arities; the work is
expanding the lookup tables in the three generators, hand-writing Function9-12
(JPMS constraint), updating sealed permits and factory methods, and adding
tests for the new arities.

No changes to generator algorithms or processor orchestration are required.

## Prerequisites (all satisfied)

- Phase 1 complete: generators produce correct output at arities 2-8
- Phase 1b complete: `minArity = 2` in package-info.java, hand-written arities
  2-5 replaced by generated equivalents
- All existing tests pass

## Scope Summary

| Component | Current Max | New Max | Change Type |
|-----------|-------------|---------|-------------|
| Tuple records | 8 | 12 | Generator table expansion |
| Function interfaces | 8 | 12 | Hand-written (JPMS) |
| MonadicSteps | 8 | 12 | Generator table expansion |
| FilterableSteps | 8 | 12 | Generator table expansion |
| 9 ForPath step families | 8 | 12 | Generator table expansion |
| Tuple.of() factories | 8 | 12 | Hand-written |

---

## Implementation Tasks

### Task 1: Extend Generator Lookup Tables

All three generators use arrays that are currently sized for 8 elements. Each
must be extended to 12.

#### 1a. TupleGenerator.java

Extend all four arrays from 8 to 12 entries:

```java
// Current (8 entries):
private static final String[] TYPE_PARAMS = {"A", "B", "C", "D", "E", "F", "G", "H"};
private static final String[] ORDINALS = {
    "First", "Second", "Third", "Fourth", "Fifth", "Sixth", "Seventh", "Eighth"};
private static final String[] ORDINAL_LOWER = {
    "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth"};
private static final String[] MAP_OPS = {
    "MAP_FIRST", "MAP_SECOND", "MAP_THIRD", "MAP_FOURTH",
    "MAP_FIFTH", "MAP_SIXTH", "MAP_SEVENTH", "MAP_EIGHTH"};

// New (12 entries):
private static final String[] TYPE_PARAMS =
    {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L"};
private static final String[] ORDINALS = {
    "First", "Second", "Third", "Fourth", "Fifth", "Sixth",
    "Seventh", "Eighth", "Ninth", "Tenth", "Eleventh", "Twelfth"};
private static final String[] ORDINAL_LOWER = {
    "first", "second", "third", "fourth", "fifth", "sixth",
    "seventh", "eighth", "ninth", "tenth", "eleventh", "twelfth"};
private static final String[] MAP_OPS = {
    "MAP_FIRST", "MAP_SECOND", "MAP_THIRD", "MAP_FOURTH",
    "MAP_FIFTH", "MAP_SIXTH", "MAP_SEVENTH", "MAP_EIGHTH",
    "MAP_NINTH", "MAP_TENTH", "MAP_ELEVENTH", "MAP_TWELFTH"};
```

#### 1b. ForStepGenerator.java

Extend the TYPE_PARAMS array from 8 to 12 entries:

```java
// Current:
private static final String[] TYPE_PARAMS = {"A", "B", "C", "D", "E", "F", "G", "H"};

// New:
private static final String[] TYPE_PARAMS =
    {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L"};
```

#### 1c. ForPathStepGenerator.java

Extend three arrays from 8 to 12 entries:

```java
// Current:
private static final String[] TYPE_PARAMS = {"A", "B", "C", "D", "E", "F", "G", "H"};
private static final String[] EITHER_VALUE_PARAMS = {"A", "B", "C", "D", "F", "G", "H", "I"};
private static final String[] GENERIC_VALUE_PARAMS = {"A", "B", "C", "D", "E", "G", "H", "I"};

// New:
private static final String[] TYPE_PARAMS =
    {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L"};
private static final String[] EITHER_VALUE_PARAMS =
    {"A", "B", "C", "D", "F", "G", "H", "I", "J", "K", "L", "M"};
private static final String[] GENERIC_VALUE_PARAMS =
    {"A", "B", "C", "D", "E", "G", "H", "I", "J", "K", "L", "M"};
```

Note: `EITHER_VALUE_PARAMS` skips "E" (collision with the Either error type
parameter); `GENERIC_VALUE_PARAMS` skips "F" (collision with the Generic
witness type parameter). Both extend to "M" as the 12th value parameter.

---

### Task 2: Hand-Write Function9, Function10, Function11, Function12

**Module**: `hkj-api`
**Package**: `org.higherkindedj.hkt.function`

Four new files following the exact pattern of Function8:

```java
@FunctionalInterface
public interface Function9<T1, T2, T3, T4, T5, T6, T7, T8, T9, R> {
    @Nullable R apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6, T7 t7, T8 t8, T9 t9);
}
```

Repeat for Function10, Function11, Function12.

**Why hand-written**: JPMS prevents generating into `hkj-api` from `hkj-processor`
(which runs during `hkj-core` compilation). The `org.higherkindedj.hkt.function`
package is exported by `hkj-api`'s `module-info.java`. These 4 files are trivial
(~45 lines each including Javadoc).

**Each file includes**:
- Copyright header
- `@FunctionalInterface` annotation
- Javadoc with `@param` for each type parameter and `@see` to adjacent arities
- Single `apply()` method with `@Nullable R` return

---

### Task 3: Update `package-info.java` Trigger

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/expression/package-info.java`

```java
// Before:
@GenerateForComprehensions(minArity = 2, maxArity = 8)

// After:
@GenerateForComprehensions(minArity = 2, maxArity = 12)
```

This single change causes the processor to generate Tuple9-12, MonadicSteps9-12,
FilterableSteps9-12, and all 9 ForPath step families at arities 9-12.

---

### Task 4: Update Sealed Permits in Tuple.java

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/tuple/Tuple.java`

```java
// Before:
public sealed interface Tuple permits Tuple2, Tuple3, Tuple4, Tuple5, Tuple6, Tuple7, Tuple8 {

// After:
public sealed interface Tuple
    permits Tuple2, Tuple3, Tuple4, Tuple5, Tuple6, Tuple7, Tuple8,
            Tuple9, Tuple10, Tuple11, Tuple12 {
```

Also add `Tuple.of()` factory overloads for arities 9-12, following the
existing pattern with Javadoc. Type parameters use I, J, K, L for positions
9-12.

```java
static <A, B, C, D, E, F, G, H, I> Tuple9<A, B, C, D, E, F, G, H, I> of(
    A a, B b, C c, D d, E e, F f, G g, H h, I i) {
  return new Tuple9<>(a, b, c, d, e, f, g, h, i);
}
// ... and so on for Tuple10, Tuple11, Tuple12
```

Update the class-level Javadoc `@see` references to include Tuple9-12.

---

### Task 5: Update Sealed Permits in For.java

**File**: `hkj-core/src/main/java/org/higherkindedj/hkt/expression/For.java`

```java
// Before:
public sealed interface Steps<M extends WitnessArity<TypeArity.Unary>>
    permits MonadicSteps1,
        MonadicSteps2, MonadicSteps3, MonadicSteps4, MonadicSteps5,
        MonadicSteps6, MonadicSteps7, MonadicSteps8,
        FilterableSteps1,
        FilterableSteps2, FilterableSteps3, FilterableSteps4, FilterableSteps5,
        FilterableSteps6, FilterableSteps7, FilterableSteps8 {}

// After:
public sealed interface Steps<M extends WitnessArity<TypeArity.Unary>>
    permits MonadicSteps1,
        MonadicSteps2, MonadicSteps3, MonadicSteps4, MonadicSteps5,
        MonadicSteps6, MonadicSteps7, MonadicSteps8,
        MonadicSteps9, MonadicSteps10, MonadicSteps11, MonadicSteps12,
        FilterableSteps1,
        FilterableSteps2, FilterableSteps3, FilterableSteps4, FilterableSteps5,
        FilterableSteps6, FilterableSteps7, FilterableSteps8,
        FilterableSteps9, FilterableSteps10, FilterableSteps11, FilterableSteps12 {}
```

---

### Task 6: Golden File Tests

**Module**: `hkj-processor` (test resources)

Add golden file placeholders for representative arities:

| Golden File | Generator |
|-------------|-----------|
| `Tuple9.java.golden` | TupleGenerator |
| `Tuple12.java.golden` | TupleGenerator |
| `MonadicSteps9.java.golden` | ForStepGenerator |
| `MonadicSteps12.java.golden` | ForStepGenerator |
| `FilterableSteps9.java.golden` | ForStepGenerator |
| `FilterableSteps12.java.golden` | ForStepGenerator |
| `MaybePathSteps9.java.golden` | ForPathStepGenerator (filterable) |
| `EitherPathSteps9.java.golden` | ForPathStepGenerator (extra type param) |
| `GenericPathSteps9.java.golden` | ForPathStepGenerator (generic) |

Update existing golden file test class to include the new arities. Run with
`-DupdateGolden=true` to populate the placeholder files.

Update `ForComprehensionGeneratorTest` to verify generation at arity 12
(existing `maxArity12Works` test or add new one).

---

### Task 7: Runtime Tests for Arities 9-12

**Module**: `hkj-core` (test source set)

#### 7a. Tuple Runtime Tests

Create test files for representative arities:
- `Tuple9Test.java` - constructor, accessors, factory method, map operations
- `Tuple12Test.java` - constructor, accessors, factory method, map operations
- Update `TupleMapIdentityPropertyTest.java` with jqwik property tests for
  Tuple9-12 map identity law

#### 7b. For Comprehension Runtime Tests

Extend `ForTest.java` with:
- `arity9_yield()` through `arity12_yield()` tests with Id monad
- `arity9_yieldTuple()` through `arity12_yieldTuple()` tests
- Mixed `from()`/`let()` chains at arities 9-12
- `when()` filtering at arities 9-12 with List monad

#### 7c. ForPath Runtime Tests

Extend `ForPathTest.java` with arity 9+ tests for all 9 path types:
- MaybePath, OptionalPath (filterable with `when()`)
- EitherPath, TryPath, IOPath, VTaskPath, IdPath (non-filterable)
- NonDetPath (filterable, Cartesian product)
- GenericPath (generic monad parameter)

#### 7d. ForOptic Runtime Tests

Extend `ForOpticTest.java` with:
- `focus()` tests at arities 8-11 (stepping into 9-12)
- `match()` tests at arities 8-11 for filterable steps

---

### Task 8: Build and Verify

1. `./gradlew clean build` - verify compilation succeeds
2. `./gradlew test` - verify all tests pass
3. `./gradlew spotlessCheck` - verify formatting
4. `./gradlew :hkj-processor:test --tests "*ForComprehensionGoldenFileTest*" -DupdateGolden=true` - populate golden files
5. `./gradlew :hkj-processor:test` - verify golden file comparisons
6. Verify a 12-step `For.from(...).from(...) ... .yield(...)` chain compiles and runs

---

## Implementation Order

```
Task 1: Extend generator lookup tables  ──┐
Task 2: Hand-write Function9-12  ─────────┤
                                          ├──→ Task 3: Update package-info.java maxArity = 12
                                          │         │
                                          │    Task 4: Sealed permits + factories in Tuple.java
                                          │    Task 5: Sealed permits in For.java
                                          │         │
                                          │         ├──→ Task 8: Build and verify
                                          │         │
                                          │    Task 6: Golden file tests
                                          │    Task 7: Runtime tests
                                          │         │
                                          └─────────┴──→ Final verification
```

**Recommended execution order**:

1. **Tasks 1 + 2 (parallel)**: Generator tables + Function9-12 (no dependencies)
2. **Tasks 3 + 4 + 5 (sequential)**: Trigger + sealed permits (must compile together)
3. **Task 8 (checkpoint)**: Build and verify everything compiles
4. **Tasks 6 + 7 (parallel)**: Golden files + runtime tests
5. **Final verification**: Full test suite

---

## What Changes vs. What Stays the Same

| Component | Changes | No Change |
|-----------|---------|-----------|
| **TupleGenerator.java** | 4 arrays extended by 4 entries each | Algorithm, method generation logic |
| **ForStepGenerator.java** | 1 array extended by 4 entries | Step class template, from/let/yield patterns |
| **ForPathStepGenerator.java** | 3 arrays extended by 4 entries each | Descriptor table, path type definitions |
| **ForComprehensionProcessor.java** | Nothing | Orchestration logic unchanged |
| **Tuple.java** | Permits + 4 factory overloads | Existing factories, sealed interface design |
| **For.java** | Permits clause expanded | Steps1 classes, factory methods |
| **ForPath.java** | Nothing | Steps1 classes, factory methods |
| **package-info.java** | `maxArity = 8` becomes `maxArity = 12` | Package, annotation import |

---

## Files Created (Hand-Written)

| File | Module | Lines | Purpose |
|------|--------|-------|---------|
| `Function9.java` | hkj-api | ~45 | 9-argument function interface |
| `Function10.java` | hkj-api | ~45 | 10-argument function interface |
| `Function11.java` | hkj-api | ~45 | 11-argument function interface |
| `Function12.java` | hkj-api | ~45 | 12-argument function interface |
| `Tuple9Test.java` | hkj-core test | ~80 | Runtime tests for Tuple9 |
| `Tuple12Test.java` | hkj-core test | ~80 | Runtime tests for Tuple12 |

## Files Modified (Hand-Written)

| File | Module | Change |
|------|--------|--------|
| `TupleGenerator.java` | hkj-processor | Extend 4 arrays (+4 entries each) |
| `ForStepGenerator.java` | hkj-processor | Extend 1 array (+4 entries) |
| `ForPathStepGenerator.java` | hkj-processor | Extend 3 arrays (+4 entries each) |
| `Tuple.java` | hkj-core | Sealed permits + 4 factory overloads |
| `For.java` | hkj-core | Sealed permits clause |
| `package-info.java` | hkj-core | `maxArity = 12` |
| `ForTest.java` | hkj-core test | Arity 9-12 test cases |
| `ForPathTest.java` | hkj-core test | Arity 9-12 per path type |
| `ForOpticTest.java` | hkj-core test | Arity 9-12 focus/match tests |
| `TupleMapIdentityPropertyTest.java` | hkj-core test | Property tests for Tuple9-12 |

## Files Generated (by Processor)

| Generated File | Count | Generator |
|----------------|-------|-----------|
| `Tuple9..12` records | 4 | TupleGenerator |
| `MonadicSteps9..12` | 4 | ForStepGenerator |
| `FilterableSteps9..12` | 4 | ForStepGenerator |
| `*PathSteps9..12` (9 path types x 4 arities) | 36 | ForPathStepGenerator |
| **Total generated classes** | **48** | |

---

## Estimated Scope

| Component | Lines | Type |
|-----------|-------|------|
| Function9-12 | ~180 | Hand-written (hkj-api) |
| Generator table extensions | ~30 | Hand-written (hkj-processor) |
| Tuple.java sealed permits + factories | ~90 | Hand-written (hkj-core) |
| For.java sealed permits | ~10 | Hand-written (hkj-core) |
| package-info.java | ~1 | Hand-written (hkj-core) |
| Golden file tests + placeholders | ~50 | Hand-written (hkj-processor test) |
| Runtime tests (ForTest, ForPathTest, etc.) | ~600 | Hand-written (hkj-core test) |
| Tuple9Test, Tuple12Test | ~160 | Hand-written (hkj-core test) |
| **Total hand-written** | **~1120** | |
| Generated Tuple9-12 | ~600 | Generated |
| Generated MonadicSteps9-12 | ~500 | Generated |
| Generated FilterableSteps9-12 | ~650 | Generated |
| Generated PathSteps (36 classes) | ~3000 | Generated |
| **Total generated** | **~4750** | |

Ratio: approximately **4:1 generated-to-hand-written**.

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Type parameter name collision at higher arities | Low | High | Collision-avoiding arrays already handle Either (E) and Generic (F); new params I-L have no known conflicts |
| Java compiler type inference degrades at arity 12 | Low | Medium | Test with explicit type annotations; Java handles up to 255 type params |
| Generated code size increases compilation time | Low | Low | 48 additional generated classes are small; annotation processing is fast |
| Sealed permits list becomes long | Low | Low | Cosmetic concern only; readability addressed with line grouping |
| Golden file comparison drift | Low | Medium | Run `-DupdateGolden=true` after generator changes; review diffs |

---

## Success Criteria

1. `./gradlew clean build` succeeds
2. `./gradlew test` passes all existing and new tests
3. A 12-step for-comprehension chain compiles and yields the correct result:
   ```java
   For.from(idMonad, Id.of(1))
      .from(t -> Id.of(t._1() + 1))
      // ... 10 more from() calls ...
      .yield((a, b, c, d, e, f, g, h, i, j, k, l) -> a + b + c + d + e + f + g + h + i + j + k + l)
   ```
4. ForPath works at arity 12 for all 9 path types
5. `when()` filtering works at arities 9-12 for filterable types
6. Changing `maxArity` from 12 to 16 requires only table expansions and Function13-16
7. `./gradlew spotlessCheck` passes
8. Golden file tests pass for representative arities (9 and 12)

---

## Future: Raising to 16 or Beyond

Following this same pattern, raising from 12 to 16 would require:
1. Extend generator arrays by 4 more entries (M, N, O, P)
2. Add Function13-16 in hkj-api
3. Update sealed permits and factories in Tuple.java
4. Update sealed permits in For.java
5. Change `maxArity = 16` in package-info.java
6. Add golden file placeholders and runtime tests

The generator validation already enforces `maxArity <= 26` (matching the
alphabet), so the maximum theoretical arity is 26.
