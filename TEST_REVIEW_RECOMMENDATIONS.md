# HKJ-Core Test Review and Recommendations

## Executive Summary

This document reviews the test infrastructure in `hkj-core` and identifies opportunities to improve test quality through better use of base classes and custom assertions while maintaining test coverage. The goal is to make tests leaner with more reusable patterns and reduced duplication.

## Current State Assessment

### Well-Structured Tests (9 types)

The following types have excellent test infrastructure:

**Core Types:**
- `Either`, `Maybe`, `Lazy`, `Validated`, `Reader`, `Writer`, `IO`, `Try`, `State`

**Characteristics:**
- ✅ Custom assertions (e.g., `EitherAssert`, `MaybeAssert`)
- ✅ Dedicated test base classes (e.g., `EitherTestBase`, `MaybeTestBase`)
- ✅ Use fluent `TypeClassTest` API
- ✅ Well-organized nested test classes
- ✅ Comprehensive coverage of operations, laws, edge cases, and validations

**Example Pattern:**
```java
@DisplayName("MaybeMonad Complete Test Suite")
class MaybeMonadTest extends MaybeTestBase {
  @Test
  void runCompleteMonadTestPattern() {
    TypeClassTest.<MaybeKind.Witness>monad(MaybeMonad.class)
        .<Integer>instance(monad)
        .<String>withKind(validKind)
        .withMonadOperations(...)
        .testAll();
  }
}
```

### Tests Needing Improvement (3+ types)

**Core Types:** `List`, `Optional`, `Id`

**Issues:**
- ❌ No custom assertions
- ❌ No test base classes
- ❌ Don't use fluent `TypeClassTest` API
- ❌ Verbose manual test setup
- ❌ Duplicate law test implementations

**Example of Verbosity:**
```java
// Current approach in ListMonadTest (verbose)
@Test
void leftIdentity() {
  Kind<ListKind.Witness, Integer> ofValue = listMonad.of(value);
  Kind<ListKind.Witness, String> leftSide = listMonad.flatMap(f, ofValue);
  Kind<ListKind.Witness, String> rightSide = f.apply(value);
  assertThat(LIST.narrow(leftSide)).isEqualTo(LIST.narrow(rightSide));
}

// vs. Well-structured approach with TypeClassTest API
@Test
void runCompleteMonadTestPattern() {
  TypeClassTest.<ListKind.Witness>monad(ListMonad.class)
      .<Integer>instance(listMonad)
      .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
      .testAll();  // Includes all law tests automatically
}
```

### Transformer Tests (6+ types)

**Types:** `EitherT`, `MaybeT`, `OptionalT`, `ReaderT`, `StateT`, `WriterT`

**Strengths:**
- ✅ Extend `TypeClassTestBase`
- ✅ Comprehensive test coverage

**Areas for Improvement:**
- ⚠️ No custom assertions
- ⚠️ Significant boilerplate for unwrapping/wrapping
- ⚠️ Repetitive helper methods across transformer tests

**Example Boilerplate:**
```java
// Repeated in every transformer test
private <A> Optional<Either<TestError, A>> unwrapKindToOptionalEither(
    Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, A> kind) {
  if (kind == null) return Optional.empty();
  var eitherT = EITHER_T.narrow(kind);
  Kind<OptionalKind.Witness, Either<TestError, A>> outerKind = eitherT.value();
  return OPTIONAL.narrow(outerKind);
}

private <R> Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, R> rightT(R value) {
  return EITHER_T.widen(EitherT.right(outerMonad, value));
}

private <R> Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, R> leftT(String errorCode) {
  return EITHER_T.widen(EitherT.left(outerMonad, new TestError(errorCode)));
}
```

## Key Areas of Duplication and Verbosity

### 1. Repeated Unwrapping Logic in Assertions

**Location:** List, Optional, Id tests
**Impact:** High verbosity, reduced readability

**Current:**
```java
assertThat(LIST.narrow(result)).containsExactly("v1", "v2");
assertThat(OPTIONAL.narrow(result)).isPresent().contains("test");
```

**Improved with Custom Assertions:**
```java
assertThatList(result).containsExactly("v1", "v2");
assertThatOptional(result).isPresent().contains("test");
```

### 2. Transformer Test Boilerplate

**Location:** All transformer tests (EitherT, MaybeT, etc.)
**Impact:** ~50-80 lines of repeated code per transformer test class

**Issues:**
- Each transformer has similar unwrapping methods
- Similar helper methods for creating test instances
- Could be abstracted into a base class

### 3. Manual Law Test Implementations

**Location:** List, Optional tests
**Impact:** ~100-150 lines of law tests that could be handled by TypeClassTest API

**Current:**
```java
@Nested
@DisplayName("Monad Laws")
class MonadLaws {
  @Test
  @DisplayName("1. Left Identity: flatMap(of(a), f) == f(a)")
  void leftIdentity() {
    Kind<ListKind.Witness, Integer> ofValue = listMonad.of(value);
    Kind<ListKind.Witness, String> leftSide = listMonad.flatMap(f, ofValue);
    Kind<ListKind.Witness, String> rightSide = f.apply(value);
    assertThat(LIST.narrow(leftSide)).isEqualTo(LIST.narrow(rightSide));
  }
  // ... more law tests
}
```

**Improved:**
```java
@Test
void testMonadLaws() {
  TypeClassTest.<ListKind.Witness>monad(ListMonad.class)
      .<Integer>instance(listMonad)
      .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
      .testLaws();  // All laws tested automatically
}
```

### 4. Inconsistent Error Type Definitions

**Location:** Various test base classes
**Impact:** Duplication of error enum/record definitions

**Example:** `EitherTestBase` defines `TestErrorType` enum, but similar patterns are duplicated elsewhere.

## Recommendations

### High Priority (Biggest Impact on Verbosity Reduction)

#### 1. Add Custom Assertions for Missing Types

**Types:** List, Optional, Id

**Benefits:**
- Reduces test verbosity by ~30%
- Improves readability
- Better error messages
- Consistent with existing patterns

**Implementation:**
```java
// ListAssert.java
public class ListAssert<T> extends AbstractAssert<ListAssert<T>, Kind<ListKind.Witness, T>> {
  public static <T> ListAssert<T> assertThatList(Kind<ListKind.Witness, T> actual) {
    return new ListAssert<>(actual);
  }

  public ListAssert<T> isEmpty() {
    isNotNull();
    if (!LIST.narrow(actual).isEmpty()) {
      failWithMessage("Expected List to be empty but had %d elements",
                      LIST.narrow(actual).size());
    }
    return this;
  }

  @SafeVarargs
  public final ListAssert<T> containsExactly(T... expected) {
    isNotNull();
    assertThat(LIST.narrow(actual)).containsExactly(expected);
    return this;
  }

  public ListAssert<T> hasSize(int expected) {
    isNotNull();
    int actualSize = LIST.narrow(actual).size();
    if (actualSize != expected) {
      failWithMessage("Expected List to have size <%d> but had <%d>",
                      expected, actualSize);
    }
    return this;
  }
}

// OptionalAssert.java
public class OptionalAssert<T> extends AbstractAssert<OptionalAssert<T>, Kind<OptionalKind.Witness, T>> {
  public static <T> OptionalAssert<T> assertThatOptional(Kind<OptionalKind.Witness, T> actual) {
    return new OptionalAssert<>(actual);
  }

  public OptionalAssert<T> isPresent() {
    isNotNull();
    if (OPTIONAL.narrow(actual).isEmpty()) {
      failWithMessage("Expected Optional to be present but was empty");
    }
    return this;
  }

  public OptionalAssert<T> isEmpty() {
    isNotNull();
    if (OPTIONAL.narrow(actual).isPresent()) {
      failWithMessage("Expected Optional to be empty but contained <%s>",
                      OPTIONAL.narrow(actual).get());
    }
    return this;
  }

  public OptionalAssert<T> contains(T expected) {
    isPresent();
    T actual = OPTIONAL.narrow(this.actual).get();
    if (!Objects.equals(actual, expected)) {
      failWithMessage("Expected Optional to contain <%s> but contained <%s>",
                      expected, actual);
    }
    return this;
  }
}
```

#### 2. Add Test Base Classes for Missing Types

**Types:** List, Optional, Id

**Benefits:**
- Eliminates fixture duplication
- Standardizes test structure
- Reduces setup code by ~50-70 lines per test class

**Implementation:**
```java
// ListTestBase.java
abstract class ListTestBase extends TypeClassTestBase<ListKind.Witness, Integer, String> {

  protected static final Integer DEFAULT_VALUE = 42;
  protected static final Integer ALTERNATIVE_VALUE = 24;

  @Override
  protected Kind<ListKind.Witness, Integer> createValidKind() {
    return LIST.widen(List.of(DEFAULT_VALUE, ALTERNATIVE_VALUE));
  }

  @Override
  protected Kind<ListKind.Witness, Integer> createValidKind2() {
    return LIST.widen(List.of(100, 200));
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return TestFunctions.INT_TO_STRING;
  }

  @Override
  protected BiPredicate<Kind<ListKind.Witness, ?>, Kind<ListKind.Witness, ?>> createEqualityChecker() {
    return (k1, k2) -> LIST.narrow(k1).equals(LIST.narrow(k2));
  }

  // Helper methods
  protected <A> Kind<ListKind.Witness, A> listOf(A... elements) {
    return LIST.widen(List.of(elements));
  }

  protected <A> Kind<ListKind.Witness, A> emptyList() {
    return LIST.widen(List.of());
  }

  protected <A> List<A> narrowToList(Kind<ListKind.Witness, A> kind) {
    return LIST.narrow(kind);
  }
}
```

#### 3. Migrate List, Optional, Id to Fluent TypeClassTest API

**Before (ListMonadTest - 386 lines):**
```java
class ListMonadTest {
  private final Monad<ListKind.Witness> listMonad = ListMonad.INSTANCE;

  @Nested class MonadLaws {
    @Test void leftIdentity() { /* ... */ }
    @Test void rightIdentity() { /* ... */ }
    @Test void associativity() { /* ... */ }
  }

  @Nested class FunctorLaws {
    @Test void identity() { /* ... */ }
    @Test void composition() { /* ... */ }
  }

  @Nested class ApplicativeLaws {
    @Test void identity() { /* ... */ }
    @Test void homomorphism() { /* ... */ }
    @Test void interchange() { /* ... */ }
  }
  // ... many more manual tests
}
```

**After (Estimated ~150-200 lines):**
```java
class ListMonadTest extends ListTestBase {
  private Monad<ListKind.Witness> listMonad;

  @BeforeEach
  void setUp() {
    listMonad = ListMonad.INSTANCE;
    validateMonadFixtures();
  }

  @Nested
  @DisplayName("Complete Monad Test Suite")
  class CompleteMonadTestSuite {
    @Test
    void runCompleteMonadTestPattern() {
      TypeClassTest.<ListKind.Witness>monad(ListMonad.class)
          .<Integer>instance(listMonad)
          .<String>withKind(validKind)
          .withMonadOperations(validKind2, validMapper, validFlatMapper,
                               validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .testAll();  // Runs all laws automatically
    }
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {
    @Test
    void mapAppliesFunctionToAllElements() {
      var result = listMonad.map(validMapper, validKind);
      assertThatList(result).containsExactly("42", "24");
    }

    @Test
    void flatMapFlattensNestedLists() {
      var result = listMonad.flatMap(i -> listOf(i, i * 2), validKind);
      assertThatList(result).containsExactly(42, 84, 24, 48);
    }
  }
}
```

**Estimated Savings:** ~50% reduction in test code, all laws still tested

#### 4. Create Transformer Test Base Pattern

**Benefits:**
- Eliminates 50-80 lines of boilerplate per transformer test
- Provides consistent transformer testing patterns
- Makes transformer tests as clean as core type tests

**Implementation:**
```java
// TransformerTestBase.java
public abstract class TransformerTestBase<
    Outer,
    Transformer extends TransformerKind<Outer>,
    A,
    B> extends TypeClassTestBase<Transformer, A, B> {

  protected Monad<Outer> outerMonad;

  /**
   * Unwraps a transformer Kind to its outer monad representation.
   * Subclasses implement this to handle their specific transformer type.
   */
  protected abstract <T> Kind<Outer, ?> unwrapTransformer(Kind<Transformer, T> kind);

  /**
   * Creates a transformer Kind wrapping a successful value.
   */
  protected abstract <T> Kind<Transformer, T> wrapSuccess(T value);

  /**
   * Creates a transformer Kind wrapping a failure/empty value.
   */
  protected abstract <T> Kind<Transformer, T> wrapFailure();

  /**
   * Creates a transformer Kind with an empty outer monad.
   */
  protected abstract <T> Kind<Transformer, T> wrapEmpty();

  // Common helper for transformers that wrap Option-like inner types
  protected <T> boolean isSuccess(Kind<Transformer, T> kind) {
    var unwrapped = unwrapTransformer(kind);
    return unwrapped != null && !isOuterEmpty(unwrapped);
  }

  protected abstract <T> boolean isOuterEmpty(Kind<Outer, T> outerKind);
}

// Usage in EitherTMonadTest
class EitherTMonadTest extends TransformerTestBase<
    OptionalKind.Witness,
    EitherTKind.Witness<OptionalKind.Witness, TestError>,
    Integer,
    String> {

  @Override
  protected <T> Kind<OptionalKind.Witness, Either<TestError, T>> unwrapTransformer(
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, T> kind) {
    return EITHER_T.narrow(kind).value();
  }

  @Override
  protected <T> Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, T> wrapSuccess(T value) {
    return EITHER_T.widen(EitherT.right(outerMonad, value));
  }

  @Override
  protected <T> Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, T> wrapFailure() {
    return EITHER_T.widen(EitherT.left(outerMonad, new TestError("TEST_ERROR")));
  }

  // ... rest of implementation much shorter
}
```

### Medium Priority (Code Quality and Maintainability)

#### 5. Standardize Error Types Across Tests

**Create shared test utilities:**

```java
// org.higherkindedj.hkt.test.data.TestErrors.java
public final class TestErrors {

  /**
   * Standard error codes for testing error-handling type classes.
   */
  public enum ErrorCode {
    DEFAULT("TEST_ERROR"),
    ERROR_1("E1"),
    ERROR_2("E2"),
    VALIDATION("VALIDATION_ERROR"),
    RESOURCE_UNAVAILABLE("RESOURCE_UNAVAILABLE");

    private final String message;

    ErrorCode(String message) {
      this.message = message;
    }

    public String message() {
      return message;
    }
  }

  /**
   * Standard error record for complex error scenarios.
   */
  public record ComplexError(String code, int severity, String message) {
    public static ComplexError low(String code, String message) {
      return new ComplexError(code, 1, message);
    }

    public static ComplexError high(String code, String message) {
      return new ComplexError(code, 10, message);
    }
  }
}
```

#### 6. Enhance TypeClassTestBase with More Utilities

**Add commonly-used helpers:**

```java
public abstract class TypeClassTestBase<F, A, B> {
  // ... existing code ...

  /**
   * Creates a test value wrapped in the type constructor.
   * Default implementation - override if your type has a different "of" method.
   */
  protected Kind<F, A> wrapValue(A value) {
    return createValidKind(); // Override for specific behavior
  }

  /**
   * Standard test value for string-based tests.
   */
  protected static final String TEST_STRING = "test";

  /**
   * Standard test value for integer-based tests.
   */
  protected static final Integer TEST_INT = 42;

  /**
   * Validates that two Kinds are equal using the equality checker.
   */
  protected void assertKindsEqual(Kind<F, ?> expected, Kind<F, ?> actual) {
    if (!equalityChecker.test(expected, actual)) {
      throw new AssertionError(String.format(
          "Expected kinds to be equal but they were different. Expected: %s, Actual: %s",
          expected, actual));
    }
  }
}
```

#### 7. Add Transformer Custom Assertions (Optional)

**For complex transformers, consider:**

```java
// EitherTAssert.java
public class EitherTAssert<Outer, L, R>
    extends AbstractAssert<EitherTAssert<Outer, L, R>,
                          Kind<EitherTKind.Witness<Outer, L>, R>> {

  private final OuterMonadHelper<Outer> outerHelper;

  public EitherTAssert<Outer, L, R> isRight() {
    var eitherT = EITHER_T.narrow(actual);
    var outerEither = outerHelper.narrow(eitherT.value());
    // Assert it's not empty and contains a Right
    return this;
  }

  public EitherTAssert<Outer, L, R> hasRight(R expected) {
    isRight();
    // Extract and compare value
    return this;
  }
}
```

### Low Priority (Nice to Have)

#### 8. Documentation and Best Practices Guide

**Create test writing guide:**
- How to create a new type's test suite
- When to use custom assertions vs. standard assertions
- Pattern for using TypeClassTest API
- Examples of complete test suites

#### 9. Standardize Performance Tests

**Current state:** Some tests have performance tests, others don't

**Recommendation:**
```java
public abstract class TypeClassTestBase<F, A, B> {
  // ... existing code ...

  protected boolean shouldRunPerformanceTests() {
    return Boolean.parseBoolean(System.getProperty("test.performance", "false"));
  }

  protected void runPerformanceTest(String name, Runnable test, long maxDurationNanos) {
    if (!shouldRunPerformanceTests()) {
      return;
    }

    long start = System.nanoTime();
    test.run();
    long duration = System.nanoTime() - start;

    assertThat(duration)
        .as("%s performance", name)
        .isLessThan(maxDurationNanos);
  }
}
```

## Implementation Priority and Effort Estimates

| Priority | Task | Types Affected | Estimated Effort | Impact |
|----------|------|----------------|------------------|--------|
| **High** | Add ListAssert | List | 2-3 hours | High |
| **High** | Add OptionalAssert | Optional | 2-3 hours | High |
| **High** | Add IdAssert | Id | 2 hours | Medium |
| **High** | Add ListTestBase | List | 2 hours | High |
| **High** | Add OptionalTestBase | Optional | 2 hours | High |
| **High** | Add IdTestBase | Id | 1-2 hours | Medium |
| **High** | Migrate ListMonadTest | List | 3-4 hours | High |
| **High** | Migrate OptionalMonadTest | Optional | 3-4 hours | High |
| **High** | Migrate IdMonadTest | Id | 2-3 hours | Medium |
| **High** | Create TransformerTestBase | All transformers | 4-6 hours | High |
| **High** | Refactor 1-2 transformer tests | EitherT, MaybeT | 4-6 hours | Medium-High |
| **Medium** | Standardize error types | All tests | 3-4 hours | Medium |
| **Medium** | Enhance TypeClassTestBase | All tests | 2-3 hours | Low-Medium |

**Total High Priority Effort:** ~30-40 hours
**Total Estimated Verbosity Reduction:** 30-50% across affected tests
**Test Coverage Impact:** None (all coverage maintained or improved)

## Expected Outcomes

After implementing these recommendations:

1. **Reduced Code Duplication:**
   - Transformer tests: ~50-80 lines of boilerplate eliminated per test
   - Core type tests: ~100-150 lines of manual law tests replaced by fluent API
   - Overall: ~30-40% reduction in test code volume

2. **Improved Readability:**
   - Custom assertions make test intent clearer
   - Consistent patterns across all test types
   - Less visual noise from repeated unwrapping/wrapping

3. **Better Maintainability:**
   - Changes to test patterns propagate automatically through base classes
   - New types can follow established patterns more easily
   - Reduced risk of inconsistency between test suites

4. **Maintained or Improved Coverage:**
   - All existing tests preserved
   - Law tests become more comprehensive through TypeClassTest API
   - Validation tests become more standardized

## Examples of Before/After

### Example 1: List Test Verbosity

**Before (Current ListMonadTest):**
```java
@Test
void flatMap_shouldApplyFunctionAndFlattenResults() {
  Kind<ListKind.Witness, Integer> input = LIST.widen(Arrays.asList(1, 2));
  Kind<ListKind.Witness, String> result = listMonad.flatMap(duplicateAndStringify, input);
  assertThat(LIST.narrow(result)).containsExactly("v1", "v1", "v2", "v2");
}
```

**After:**
```java
@Test
void flatMapAppliesFunctionAndFlattensResults() {
  var result = listMonad.flatMap(duplicateAndStringify, listOf(1, 2));
  assertThatList(result).containsExactly("v1", "v1", "v2", "v2");
}
```

**Savings:** ~40% fewer characters, clearer intent

### Example 2: Transformer Test Boilerplate

**Before (EitherTMonadTest - setup code):**
```java
private <A> Optional<Either<TestError, A>> unwrapKindToOptionalEither(
    Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, A> kind) {
  if (kind == null) return Optional.empty();
  var eitherT = EITHER_T.narrow(kind);
  Kind<OptionalKind.Witness, Either<TestError, A>> outerKind = eitherT.value();
  return OPTIONAL.narrow(outerKind);
}

private <R> Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, R> rightT(R value) {
  return EITHER_T.widen(EitherT.right(outerMonad, value));
}

private <R> Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, R> leftT(String errorCode) {
  return EITHER_T.widen(EitherT.left(outerMonad, new TestError(errorCode)));
}

private <R> Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, R> emptyT() {
  Kind<OptionalKind.Witness, Either<TestError, R>> emptyOuter = OPTIONAL.widen(Optional.empty());
  return EITHER_T.widen(EitherT.fromKind(emptyOuter));
}
```

**After (with TransformerTestBase):**
```java
// All helper methods inherited from TransformerTestBase
// Only need to implement abstract methods:

@Override
protected <T> Kind<OptionalKind.Witness, Either<TestError, T>> unwrapTransformer(
    Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, T> kind) {
  return EITHER_T.narrow(kind).value();
}

@Override
protected <T> Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, T> wrapSuccess(T value) {
  return EITHER_T.widen(EitherT.right(outerMonad, value));
}
```

**Savings:** ~60-70 lines of boilerplate eliminated

### Example 3: Law Tests

**Before (Manual implementation in ListMonadTest):**
```java
@Nested
@DisplayName("Monad Laws")
class MonadLaws {
  int value = 5;
  Kind<ListKind.Witness, Integer> mValue = LIST.widen(Arrays.asList(value, value + 1));

  @Test
  @DisplayName("1. Left Identity: flatMap(of(a), f) == f(a)")
  void leftIdentity() {
    Kind<ListKind.Witness, Integer> ofValue = listMonad.of(value);
    Kind<ListKind.Witness, String> leftSide = listMonad.flatMap(f, ofValue);
    Kind<ListKind.Witness, String> rightSide = f.apply(value);
    assertThat(LIST.narrow(leftSide)).isEqualTo(LIST.narrow(rightSide));
  }

  @Test
  @DisplayName("2. Right Identity: flatMap(m, of) == m")
  void rightIdentity() {
    Function<Integer, Kind<ListKind.Witness, Integer>> ofFunc = i -> listMonad.of(i);
    Kind<ListKind.Witness, Integer> leftSide = listMonad.flatMap(ofFunc, mValue);
    assertThat(LIST.narrow(leftSide)).isEqualTo(LIST.narrow(mValue));
  }

  @Test
  @DisplayName("3. Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
  void associativity() {
    Kind<ListKind.Witness, String> innerFlatMap = listMonad.flatMap(f, mValue);
    Kind<ListKind.Witness, String> leftSide = listMonad.flatMap(g, innerFlatMap);

    Function<Integer, Kind<ListKind.Witness, String>> rightSideFunc =
        a -> listMonad.flatMap(g, f.apply(a));
    Kind<ListKind.Witness, String> rightSide = listMonad.flatMap(rightSideFunc, mValue);

    assertThat(LIST.narrow(leftSide)).isEqualTo(LIST.narrow(rightSide));
  }
}
```

**After (using TypeClassTest API):**
```java
@Nested
@DisplayName("Monad Laws")
class MonadLaws {
  @Test
  void testAllMonadLaws() {
    TypeClassTest.<ListKind.Witness>monad(ListMonad.class)
        .<Integer>instance(listMonad)
        .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
        .testLaws();  // Tests all three laws automatically
  }
}
```

**Savings:** ~50 lines reduced to ~8 lines, same coverage

## Conclusion

The hkj-core test suite has a strong foundation with excellent patterns established in the core types (Either, Maybe, etc.). By extending these patterns to the remaining types and standardizing transformer tests, we can achieve:

- **30-50% reduction in test code verbosity**
- **Improved consistency across all test suites**
- **Easier maintenance and evolution of test patterns**
- **No reduction in test coverage** (maintained or improved)

The highest-impact improvements are:
1. Adding custom assertions for List, Optional, and Id
2. Adding test base classes for these types
3. Migrating to the fluent TypeClassTest API
4. Creating a TransformerTestBase pattern

These changes follow existing established patterns and will make the test suite significantly more maintainable while keeping the same comprehensive coverage.
