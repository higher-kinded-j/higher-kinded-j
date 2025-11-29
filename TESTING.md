# Testing Guide for higher-kinded-j

This document provides comprehensive guidance on testing patterns and best practices used throughout the higher-kinded-j project.

## Table of Contents

1. [Testing Philosophy](#testing-philosophy)
2. [JUnit 5 Features](#junit-5-features)
3. [Property-Based Testing](#property-based-testing)
4. [Typeclass Law Testing](#typeclass-law-testing)
5. [Optics Law Testing](#optics-law-testing)
6. [Testing Patterns by Concept](#testing-patterns-by-concept)
7. [Best Practices](#best-practices)
8. [Interface Default Method Coverage](#interface-default-method-coverage)
9. [Running Tests](#running-tests)

## Testing Philosophy

The higher-kinded-j project employs a multi-layered testing strategy:

1. **Unit Tests**: Verify individual component behavior
2. **Property-Based Tests**: Generate hundreds of test cases automatically
3. **Law Tests**: Ensure typeclass and optics implementations satisfy mathematical laws
4. **Integration Tests**: Verify components work together correctly
5. **Benchmarks**: Performance testing in separate `hkj-benchmarks` module

### Key Principles

- **Comprehensive Coverage**: Every public API should have tests
- **Clear Intent**: Test names should describe what is being tested
- **Minimal Boilerplate**: Use JUnit 5's advanced features to reduce duplication
- **Fast Feedback**: Tests should run quickly; performance tests are in separate module

## JUnit 5 Features

### @ParameterizedTest

Use parameterized tests to verify behavior across multiple inputs without duplication.

**Example with @MethodSource:**

```java
@ParameterizedTest(name = "{0}")
@MethodSource("allMonoids")
@DisplayName("Empty is left identity")
void emptyIsLeftIdentity(MonoidTestData<?> data) {
    Object empty = data.monoid().empty();
    Object value = data.sampleValue();
    Object result = data.monoid().combine(empty, value);
    assertThat(result).isEqualTo(value);
}

private static Stream<MonoidTestData<?>> allMonoids() {
    return Stream.of(
        MonoidTestData.of("String", StringMonoid.INSTANCE, "test", "hello"),
        MonoidTestData.of("Integer (addition)", IntegerAdditionMonoid.INSTANCE, 5, 10),
        // ... more monoids
    );
}
```

**Example with @EnumSource:**

```java
@ParameterizedTest
@EnumSource(TypeclassInstance.class)
@DisplayName("All typeclasses satisfy laws")
void allTypeclassesSatisfyLaws(TypeclassInstance instance) {
    instance.verifyLaws();
}
```

**Benefits:**
- Single test method tests all implementations
- Clear test output showing which implementation failed
- Easy to add new test cases

**Real examples:**
- `MonoidLawsParameterizedTest.java` - Tests all Monoid implementations
- `ApplicativeLawsParameterizedTest.java` - Tests all Applicative implementations

### @TestFactory

Generate tests dynamically at runtime for flexible, comprehensive testing.

**Example:**

```java
@TestFactory
@DisplayName("Identity Law: map(id) = id")
Stream<DynamicTest> identityLaw() {
    return allFunctors()
        .map(data -> DynamicTest.dynamicTest(
            data.name() + " satisfies identity law",
            () -> testIdentityLaw(data)));
}

private <F> void testIdentityLaw(FunctorTestData<F> data) {
    Functor<F> functor = data.functor();
    Kind<F, Integer> testValue = data.testValue();

    Function<Integer, Integer> identity = x -> x;
    Kind<F, Integer> result = functor.map(identity, testValue);

    // Verify identity holds
    assertEqualKind(result, testValue, data.extractor());
}
```

**Benefits:**
- Tests generated based on available implementations
- Adding new implementation automatically adds test coverage
- Each test runs independently with proper isolation

**Real examples:**
- `FunctorLawsTestFactory.java` - 15 dynamic tests across 5 Functor implementations
- `MonadLawsTestFactory.java` - 20 dynamic tests across 5 Monad implementations
- `MonadErrorLawsTestFactory.java` - 25 dynamic tests across 5 MonadError implementations
- `AlternativeLawsTestFactory.java` - 15 dynamic tests across 3 Alternative implementations
- `LensLawsTestFactory.java` - 36 dynamic tests across 9 Lens implementations
- `PrismLawsTestFactory.java` - 55 dynamic tests across 11 Prism implementations

### @Nested Classes

Organize related tests into logical groups for better structure and readability.

**Example:**

```java
@DisplayName("Maybe Monad Tests")
class MaybeMonadTest {

    @Nested
    @DisplayName("Core Operations")
    class CoreOperations {
        @Test
        @DisplayName("flatMap should apply and flatten")
        void flatMapAppliesAndFlattens() { /* ... */ }
    }

    @Nested
    @DisplayName("Law Verification")
    class LawVerification {
        @Test
        @DisplayName("Left identity: flatMap(of(a), f) = f(a)")
        void leftIdentityLaw() { /* ... */ }
    }
}
```

**Benefits:**
- Clear test organization in IDE and reports
- Shared setup via `@BeforeEach` at appropriate level
- Better test discovery

## Property-Based Testing

Property-based testing with **jQwik** generates hundreds of test cases automatically, uncovering edge cases that manual tests might miss.

### Basic Property Test

```java
@Property
@Label("Functor Identity Law: map(id) = id")
void functorIdentityLaw(@ForAll("eitherInts") Either<String, Integer> either) {
    Kind<EitherKind.Witness<String>, Integer> kindEither = EITHER.widen(either);
    Function<Integer, Integer> identity = x -> x;

    Kind<EitherKind.Witness<String>, Integer> result = functor.map(identity, kindEither);

    assertThat(EITHER.narrow(result)).isEqualTo(either);
}

@Provide
Arbitrary<Either<String, Integer>> eitherInts() {
    return Arbitraries.oneOf(
        Arbitraries.integers().between(-100, 100).map(Either::right),
        Arbitraries.strings().alpha().ofLength(5).map(Either::left)
    );
}
```

### Advanced: Testing with Functions

Generate arbitrary functions for testing higher-order operations:

```java
@Property
@Label("Monad Left Identity Law: flatMap(of(a), f) = f(a)")
void leftIdentityLaw(
    @ForAll @IntRange(min = -50, max = 50) int value,
    @ForAll("intToEitherStringFunctions") Function<Integer, Either<String, String>> f) {

    Kind<EitherKind.Witness<String>, Integer> ofValue = monad.of(value);
    Kind<EitherKind.Witness<String>, String> leftSide =
        monad.flatMap(i -> EITHER.widen(f.apply(i)), ofValue);
    Either<String, String> rightSide = f.apply(value);

    assertThat(EITHER.narrow(leftSide)).isEqualTo(rightSide);
}

@Provide
Arbitrary<Function<Integer, Either<String, String>>> intToEitherStringFunctions() {
    return Arbitraries.oneOf(
        // Function that always returns Right
        Arbitraries.just(i -> Either.right("value:" + i)),
        // Function that returns Left for negative
        Arbitraries.just(i -> i < 0 ? Either.left("negative") : Either.right("positive")),
        // Function that returns Left for even
        Arbitraries.just(i -> i % 2 == 0 ? Either.left("even") : Either.right("odd"))
    );
}
```

### Testing Error Accumulation

Demonstrate differences between error handling strategies:

```java
@Property
@Label("ap accumulates errors when both function and value are Invalid")
void apAccumulatesErrors() {
    List<String> functionErrors = List.of("error: bad function");
    List<String> valueErrors = List.of("error: bad value");

    Validated<List<String>, Function<Integer, String>> invalidFunction =
        Validated.invalid(functionErrors);
    Validated<List<String>, Integer> invalidValue =
        Validated.invalid(valueErrors);

    Kind<ValidatedKind.Witness<List<String>>, Function<Integer, String>> kindFunction =
        VALIDATED.widen(invalidFunction);
    Kind<ValidatedKind.Witness<List<String>>, Integer> kindValue =
        VALIDATED.widen(invalidValue);

    Kind<ValidatedKind.Witness<List<String>>, String> result =
        applicative.ap(kindFunction, kindValue);

    Validated<List<String>, String> narrowed = VALIDATED.narrow(result);
    assertThat(narrowed.isInvalid()).isTrue();

    // Both error lists should be combined
    List<String> expectedErrors = new ArrayList<>(functionErrors);
    expectedErrors.addAll(valueErrors);
    assertThat(narrowed.getError()).isEqualTo(expectedErrors);
}
```

### Handling Equality for Effects

When testing monads with effects (like Try), use semantic equality:

```java
private <T> void assertTryEquals(Try<T> actual, Try<T> expected) throws Throwable {
    assertThat(actual.isSuccess()).isEqualTo(expected.isSuccess());
    assertThat(actual.isFailure()).isEqualTo(expected.isFailure());

    if (expected.isSuccess()) {
        assertThat(actual.get()).isEqualTo(expected.get());
    } else {
        // Compare exception type and message, not object identity
        Throwable expectedCause = ((Try.Failure<T>) expected).cause();
        Throwable actualCause = ((Try.Failure<T>) actual).cause();
        assertThat(actualCause.getClass()).isEqualTo(expectedCause.getClass());
        assertThat(actualCause.getMessage()).isEqualTo(expectedCause.getMessage());
    }
}
```

**Real examples:**
- `EitherFunctorPropertyTest.java` - Property tests for Either Functor
- `TryMonadPropertyTest.java` - Monad laws with exception handling
- `ValidatedApplicativePropertyTest.java` - Error accumulation testing

## Typeclass Law Testing

Typeclasses must satisfy mathematical laws. We test these laws systematically.

### Functor Laws

A Functor must satisfy:
1. **Identity**: `map(id) = id`
2. **Composition**: `map(g ∘ f) = map(g) ∘ map(f)`

**Implementation pattern:**

```java
record FunctorTestData<F>(
    String name,
    Functor<F> functor,
    Kind<F, Integer> testValue,
    Function<Kind<F, Integer>, Integer> extractor) {}

@TestFactory
@DisplayName("Identity Law: map(id, fa) = fa")
Stream<DynamicTest> identityLaw() {
    return allFunctors()
        .map(data -> DynamicTest.dynamicTest(
            data.name() + " satisfies identity law",
            () -> testIdentityLaw(data)));
}

private <F> void testIdentityLaw(FunctorTestData<F> data) {
    // Test implementation
}
```

### Monad Laws

A Monad must satisfy:
1. **Left Identity**: `flatMap(of(a), f) = f(a)`
2. **Right Identity**: `flatMap(m, of) = m`
3. **Associativity**: `flatMap(flatMap(m, f), g) = flatMap(m, x -> flatMap(f(x), g))`

**Handling type-specific equality:**

```java
@FunctionalInterface
interface EqualityChecker<M> {
    <A> boolean areEqual(Kind<M, A> a, Kind<M, A> b);
}

// For Try, use semantic equality
MonadTestData.of("Try", TryMonad.INSTANCE, TRY.widen(Try.success(42)),
    new EqualityChecker<TryKind.Witness>() {
        @Override
        public <A> boolean areEqual(Kind<TryKind.Witness, A> a, Kind<TryKind.Witness, A> b) {
            Try<A> tryA = TRY.narrow(a);
            Try<A> tryB = TRY.narrow(b);
            if (tryA.isSuccess() != tryB.isSuccess()) return false;
            if (tryA.isSuccess()) {
                try { return java.util.Objects.equals(tryA.get(), tryB.get()); }
                catch (Throwable e) { return false; }
            } else {
                // Both are failures, compare them semantically
                Throwable causeA = ((Try.Failure<A>) tryA).cause();
                Throwable causeB = ((Try.Failure<A>) tryB).cause();
                return causeA.getClass().equals(causeB.getClass())
                    && java.util.Objects.equals(causeA.getMessage(), causeB.getMessage());
            }
        }
    })
```

### Applicative Laws

An Applicative must satisfy:
1. **Identity**: `ap(pure(id), v) = v`
2. **Composition**: `ap(ap(ap(pure(compose), u), v), w) = ap(u, ap(v, w))`
3. **Homomorphism**: `ap(pure(f), pure(x)) = pure(f(x))`
4. **Interchange**: `ap(u, pure(y)) = ap(pure(f -> f(y)), u)`

**Real examples:**
- `FunctorLawsTestFactory.java`
- `MonadLawsTestFactory.java`
- `ApplicativeLawsParameterizedTest.java`

### MonadError Laws

A MonadError must satisfy:
1. **Left Zero**: `flatMap(raiseError(e), f) = raiseError(e)` - Error short-circuits flatMap
2. **Recovery**: `handleErrorWith(raiseError(e), f) = f(e)` - Handler recovers from error
3. **Success Passthrough**: `handleErrorWith(of(a), f) = of(a)` - Success values pass through unchanged

**Implementation pattern:**

```java
record MonadErrorTestData<F, E>(
    String name,
    MonadError<F, E> monadError,
    Kind<F, Integer> successValue,
    E errorValue,
    EqualityChecker<F> equalityChecker) {}

@TestFactory
@DisplayName("Left Zero Law: flatMap(raiseError(e), f) = raiseError(e)")
Stream<DynamicTest> leftZeroLaw() {
    return allMonadErrors()
        .map(data -> DynamicTest.dynamicTest(
            data.name() + " satisfies left zero law",
            () -> {
                MonadError<F, E> me = data.monadError();
                E error = data.errorValue();
                Function<Integer, Kind<F, String>> f = i -> me.of("result:" + i);

                Kind<F, String> leftSide = me.flatMap(f, me.raiseError(error));
                Kind<F, String> rightSide = me.raiseError(error);

                assertThat(data.equalityChecker().areEqual(leftSide, rightSide)).isTrue();
            }));
}
```

**Tested implementations:**
- Maybe (error type: Unit)
- Optional (error type: Unit)
- Either (error type: E)
- Try (error type: Throwable)
- Validated (error type: E)

**Real example:**
- `MonadErrorLawsTestFactory.java` - 25 dynamic tests across 5 MonadError implementations

### Alternative Laws

An Alternative must satisfy:
1. **Left Identity**: `orElse(empty(), () -> fa) = fa` - Empty is left identity
2. **Right Identity**: `orElse(fa, () -> empty()) = fa` - Empty is right identity
3. **Associativity**: `orElse(fa, () -> orElse(fb, () -> fc)) = orElse(orElse(fa, () -> fb), () -> fc)`

**Note**: Alternative has two common semantics:
- **Choice semantics** (Maybe, Optional): First non-empty value wins
- **Concatenation semantics** (List, Stream): Both values are combined

**Implementation pattern:**

```java
record AlternativeTestData<F>(
    String name,
    Alternative<F> alternative,
    Kind<F, Integer> testValue,
    Kind<F, Integer> testValue2,
    EqualityChecker<F> equalityChecker) {}

@TestFactory
@DisplayName("Left Identity Law: orElse(empty(), () -> fa) = fa")
Stream<DynamicTest> leftIdentityLaw() {
    return allAlternatives()
        .map(data -> DynamicTest.dynamicTest(
            data.name() + " satisfies left identity law",
            () -> {
                Alternative<F> alt = data.alternative();
                Kind<F, Integer> fa = data.testValue();

                Kind<F, Integer> leftSide = alt.orElse(alt.empty(), () -> fa);
                Kind<F, Integer> rightSide = fa;

                assertThat(data.equalityChecker().areEqual(leftSide, rightSide)).isTrue();
            }));
}
```

**Tested implementations:**
- Maybe (choice semantics)
- Optional (choice semantics)
- List (concatenation semantics)

Note: Stream is excluded because Java Streams can only be consumed once.

**Real example:**
- `AlternativeLawsTestFactory.java` - 15 dynamic tests across 3 Alternative implementations

## Optics Law Testing

Optics (Lens, Prism, Traversal) must satisfy specific laws based on their semantics.

### Lens Laws

A Lens must satisfy:
1. **Get-Put**: `set(get(s), s) = s` - Setting what you get doesn't change anything
2. **Put-Get**: `get(set(a, s)) = a` - Getting what you set returns what you set
3. **Put-Put**: `set(b, set(a, s)) = set(b, s)` - Second set wins

**Implementation:**

```java
record LensTestData<S, A>(
    String name,
    Lens<S, A> lens,
    S testValue,
    A newValue,
    A alternateValue) {}

@TestFactory
@DisplayName("Get-Put Law: set(get(s), s) = s")
Stream<DynamicTest> getPutLaw() {
    return allLenses()
        .map(data -> DynamicTest.dynamicTest(
            data.name() + " satisfies get-put law",
            () -> {
                A currentValue = data.lens().get(data.testValue());
                S result = data.lens().set(currentValue, data.testValue());
                assertThat(result).isEqualTo(data.testValue());
            }));
}
```

**Testing composed lenses:**

```java
// Compose: User -> Address -> Street -> String
Lens<User, String> userStreetNameLens =
    userAddressLens.andThen(addressStreetLens).andThen(streetNameLens);

LensTestData.of(
    "User.streetName (composed)",
    userStreetNameLens,
    createTestUser(),
    "Sunset Blvd",
    "Hollywood Ave")
```

### Prism Laws

A Prism must satisfy:
1. **Review Law**: `getOptional(build(a)) = Some(a)` - Building then extracting returns original

**Implementation:**

```java
record PrismTestData<S, A>(
    String name,
    Prism<S, A> prism,
    S matchingValue,
    S nonMatchingValue,
    A testValue,
    A alternateValue) {}

@TestFactory
@DisplayName("Review Law: getOptional(build(a)) = Some(a)")
Stream<DynamicTest> reviewLaw() {
    return allPrisms()
        .map(data -> DynamicTest.dynamicTest(
            data.name() + " satisfies review law",
            () -> {
                A value = data.testValue();
                S built = data.prism().build(value);
                Optional<A> extracted = data.prism().getOptional(built);
                assertThat(extracted).isPresent().contains(value);
            }));
}
```

**Testing matching behavior:**

```java
@TestFactory
@DisplayName("modify only affects matching values")
Stream<DynamicTest> modifyOnlyAffectsMatching() {
    return stringPrisms()
        .map(data -> DynamicTest.dynamicTest(
            data.name() + " modify only affects matching",
            () -> {
                // Modify matching value - should transform
                S modifiedMatching = data.prism().modify(String::toUpperCase, data.matchingValue());
                assertThat(data.prism().getOptional(modifiedMatching)).isPresent();

                // Modify non-matching value - should be unchanged
                S modifiedNonMatching = data.prism().modify(String::toUpperCase, data.nonMatchingValue());
                assertThat(modifiedNonMatching).isSameAs(data.nonMatchingValue());
            }));
}
```

**Real examples:**
- `LensLawsTestFactory.java` - 36 tests across 9 lens implementations
- `PrismLawsTestFactory.java` - 55 tests across 11 prism implementations

## Testing Patterns by Concept

### Either: Error Handling

```java
@ParameterizedTest
@MethodSource("transformationScenarios")
@DisplayName("map transforms Right value, leaves Left unchanged")
void mapTransformsRight(Either<String, Integer> either, Function<Integer, String> f) {
    Either<String, String> result = either.map(f);

    if (either.isRight()) {
        assertThat(result.isRight()).isTrue();
        assertThat(result.getRight()).isEqualTo(f.apply(either.getRight()));
    } else {
        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isEqualTo(either.getLeft());
    }
}
```

### Try: Exception as Values

```java
@Test
@DisplayName("success() wraps value, get() returns it")
void successWrapsValue() throws Throwable {
    Try<String> success = Try.success("result");
    assertThat(success.isSuccess()).isTrue();
    assertThat(success.get()).isEqualTo("result");
}

@Test
@DisplayName("failure() wraps exception, get() throws it")
void failureWrapsException() {
    Exception cause = new RuntimeException("error");
    Try<String> failure = Try.failure(cause);

    assertThat(failure.isFailure()).isTrue();
    assertThatThrownBy(() -> failure.get())
        .isSameAs(cause);
}
```

### Validated: Error Accumulation

```java
@Test
@DisplayName("Combining two Invalid values accumulates errors")
void combiningInvalidAccumulatesErrors() {
    Semigroup<List<String>> listSemigroup = (a, b) -> {
        List<String> combined = new ArrayList<>(a);
        combined.addAll(b);
        return combined;
    };

    Validated<List<String>, Integer> v1 = Validated.invalid(List.of("error1"));
    Validated<List<String>, Integer> v2 = Validated.invalid(List.of("error2"));

    // Using map2 from the Applicative instance is clearer
    ValidatedApplicative<List<String>> applicative =
        ValidatedApplicative.instance(listSemigroup);

    Validated<List<String>, Integer> result = VALIDATED.narrow(
        applicative.map2(VALIDATED.widen(v1), VALIDATED.widen(v2), (x, y) -> x + y)
    );

    assertThat(result.isInvalid()).isTrue();
    assertThat(result.getError()).containsExactly("error1", "error2");
}
```

### Maybe: Optional Values

```java
@ParameterizedTest
@MethodSource("maybeValues")
@DisplayName("orElse provides fallback for Nothing")
void orElseProvidesFallback(Maybe<String> maybe, String fallback, String expected) {
    assertThat(maybe.orElse(fallback)).isEqualTo(expected);
}

static Stream<Arguments> maybeValues() {
    return Stream.of(
        Arguments.of(Maybe.just("value"), "fallback", "value"),
        Arguments.of(Maybe.nothing(), "fallback", "fallback")
    );
}
```

## Best Practices

### 1. Use Descriptive Test Names

```java
// Good
@Test
@DisplayName("flatMap applies function and flattens nested structure")
void flatMapAppliesAndFlattens() { /* ... */ }

// Bad
@Test
void testFlatMap() { /* ... */ }
```

### 2. Test Both Success and Failure Paths

```java
@Nested
@DisplayName("Either.map()")
class EitherMapTests {
    @Test
    @DisplayName("transforms Right value")
    void transformsRightValue() { /* ... */ }

    @Test
    @DisplayName("preserves Left value")
    void preservesLeftValue() { /* ... */ }
}
```

### 3. Avoid Generic Wildcards in Filters

```java
// Bad - causes compilation errors
allLenses()
    .filter(data -> data.lens().get(data.testValue()) instanceof String)

// Good - create typed helper method
private static Stream<LensTestData<?, String>> stringLenses() {
    return Stream.of(
        LensTestData.of("Street.name", streetNameLens, ...),
        LensTestData.of("User.name", userNameLens, ...)
    );
}
```

### 4. Use AssertJ for Fluent Assertions

```java
// Good - fluent and readable
assertThat(maybe)
    .isInstanceOf(Maybe.Just.class)
    .extracting(m -> ((Maybe.Just<String>) m).value())
    .isEqualTo("expected");

// Less readable
assertTrue(maybe instanceof Maybe.Just);
assertEquals("expected", ((Maybe.Just<String>) maybe).value());
```

### 5. Separate Performance Tests

Performance tests belong in the `hkj-benchmarks` module using JMH:

```java
// In hkj-benchmarks/src/jmh/java/...
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class EitherBenchmark {

    @Benchmark
    public Either<String, Integer> mapRight() {
        return right.map(x -> x * 2);
    }
}
```

### 6. Test Law Violations

Ensure tests actually fail when laws are violated:

```java
// Create an intentionally broken Functor
Functor<TestKind.Witness> brokenFunctor = new Functor<>() {
    @Override
    public <A, B> Kind<TestKind.Witness, B> map(Function<A, B> f, Kind<TestKind.Witness, A> fa) {
        // Broken: always returns a fixed value
        return widen(new TestValue<>(null));
    }
};

// Verify the test catches the violation
assertThatThrownBy(() -> testIdentityLaw(brokenFunctor, testData))
    .isInstanceOf(AssertionError.class);
```

### 7. Document Edge Cases

```java
@Test
@DisplayName("map catches exceptions and converts Success to Failure")
void mapCatchesExceptions() {
    Try<Integer> success = Try.success(null);
    // Function throws NPE on null
    Function<Integer, String> throwingFunction = i -> i.toString();

    Try<String> result = success.map(throwingFunction);

    assertThat(result.isFailure()).isTrue();
    assertThat(((Try.Failure<String>) result).cause())
        .isInstanceOf(NullPointerException.class);
}
```

## Interface Default Method Coverage

The `hkj-api` module defines interfaces with default method implementations (e.g., `Monad.flatMap2()`, `Applicative.map2()`, `Lens.modify()`). These default methods are tested through their implementing classes in `hkj-core`.

### Cross-Module JaCoCo Coverage

JaCoCo is configured in `hkj-core` to measure coverage of both:
- **hkj-core classes**: The implementing classes
- **hkj-api classes**: The interface default methods

This ensures that default method implementations are properly tested through concrete implementations.

### Detecting Unreachable Default Methods

Some default methods may be overridden by **all** implementing classes for performance reasons. These methods are unreachable through the default implementation and should be excluded from coverage metrics.

The `DefaultMethodCoverageRules` ArchUnit test helps identify such methods:

```bash
./gradlew :hkj-core:test --tests "org.higherkindedj.architecture.DefaultMethodCoverageRules"
```

This test will print a report of any default methods that are overridden by all implementations:

```
=== Unreachable Default Methods Report ===
The following default methods are overridden by ALL implementations.
Consider adding them to JaCoCo exclusions in hkj-core/build.gradle.kts:

Interface: Applicative
  - map(Function,Kind)
===========================================
```

### Adding JaCoCo Exclusions

When the ArchUnit test identifies unreachable default methods, add them to the JaCoCo exclusion list in `hkj-core/build.gradle.kts`:

```kotlin
// hkj-api classes (interface default methods we want to cover)
apiProject.sourceSets.main.get().output.classesDirs.map { dir ->
    fileTree(dir).apply {
        exclude(
            // Exclude default methods that are overridden by ALL implementations
            // (and thus unreachable through hkj-core tests)
            // Example: "**/Applicative.class"
        )
    }
}
```

### Workflow

1. **Run tests with coverage**: `./gradlew :hkj-core:test :hkj-core:jacocoTestReport`
2. **Check coverage report**: `hkj-core/build/reports/jacoco/test/html/index.html`
3. **Review hkj-api coverage**: Navigate to `org.higherkindedj.hkt` packages
4. **If coverage is unexpectedly low**: Run `DefaultMethodCoverageRules` test to check for unreachable methods
5. **Add exclusions**: Update `build.gradle.kts` with any identified unreachable methods

### Best Practices for Default Methods

1. **Prefer delegation over override**: Only override default methods when there's a clear performance benefit
2. **Test default methods explicitly**: Even if a method has a default implementation, write tests that exercise it through at least one implementing class
3. **Document overrides**: When overriding a default method, add a comment explaining why (e.g., performance optimization)

## Running Tests

### Run All Tests

```bash
./gradlew test
```

### Run Tests for Specific Module

```bash
./gradlew :hkj-core:test
```

### Run Specific Test Class

```bash
./gradlew :hkj-core:test --tests "org.higherkindedj.hkt.either.EitherTest"
```

### Run Tests with Coverage

```bash
./gradlew test jacocoTestReport
```

Coverage reports: `build/reports/jacoco/test/html/index.html`

### Run Benchmarks

```bash
./gradlew :hkj-benchmarks:jmh
```

### Run with Different Test Filters

```bash
# Run only property tests
./gradlew test --tests "*PropertyTest"

# Run only law tests
./gradlew test --tests "*LawsTestFactory"

# Run only parameterized tests
./gradlew test --tests "*ParameterizedTest"
```

## Summary

The higher-kinded-j testing approach combines:

1. **JUnit 5 Features**: @ParameterizedTest, @TestFactory, @Nested for minimal boilerplate
2. **Property-Based Testing**: jQwik for comprehensive coverage with generated inputs
3. **Law Verification**: Systematic testing of typeclass and optics laws
4. **Clear Organization**: Nested classes and descriptive names for maintainability
5. **Separation of Concerns**: Functional tests in hkj-core, performance in hkj-benchmarks

This comprehensive strategy ensures correctness, maintains clarity, and provides fast feedback during development.

For more examples, explore the test files referenced throughout this guide.
