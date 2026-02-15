# Testing Guide for higher-kinded-j

This document provides comprehensive guidance on testing patterns and best practices used throughout the higher-kinded-j project.

## Table of Contents

1. [Testing Philosophy](#testing-philosophy)
2. [JUnit 6 Features](#junit-6-features)
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
- **Minimal Boilerplate**: Use JUnit 6's advanced features to reduce duplication
- **Fast Feedback**: Tests should run quickly; performance tests are in separate module

## JUnit 6 Features

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
void allTypeClassesSatisfyLaws(TypeclassInstance instance) {
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

## Effect Path API Testing Patterns

The Effect Path API provides fluent wrappers around effect types with a unified interface. This section documents the testing patterns specific to Path types.

### Currently Implemented Path Types

The following Path types are implemented and should have comprehensive test coverage:

**Phase 1 & 2 Paths:**
- **MaybePath** - Optional values (nullable/absent handling)
- **EitherPath** - Disjoint union (success/failure with typed errors)
- **TryPath** - Exception-safe computations
- **IOPath** - Lazy side-effecting computations
- **ValidationPath** - Error accumulation
- **IdPath** - Identity wrapper
- **OptionalPath** - Java Optional wrapper
- **GenericPath** - Escape hatch for custom monads

**Phase 3 Paths:**
- **ReaderPath** - Environment-dependent computations
- **WriterPath** - Computations with log accumulation
- **WithStatePath** - Stateful computations
- **LazyPath** - Memoized deferred computation
- **CompletableFuturePath** - Asynchronous computations
- **ListPath** - Collection with positional semantics
- **StreamPath** - Lazy stream computations
- **NonDetPath** - Non-deterministic computations with Cartesian product

### Three-Layer Testing Strategy for Effect Paths

Each effect path type should have three layers of tests:

1. **Unit Tests** (`*PathTest.java`): Comprehensive behavior testing
2. **Property Tests** (`*PathPropertyTest.java`): Functor and Monad laws via jQwik
3. **Laws Tests** (`*PathLawsTest.java`): Explicit law verification with DynamicTests

### Standard Unit Test Structure

All effect path unit tests follow a consistent `@Nested` class structure:

```java
@DisplayName("XxxPath<A> Complete Test Suite")
class XxxPathTest {

  @Nested
  @DisplayName("Factory Methods via Path")
  class FactoryMethodsTests {
    // Tests for Path.xxx() factory methods
  }

  @Nested
  @DisplayName("Run and Terminal Methods")
  class RunAndTerminalMethodsTests {
    // Tests for run(), get(), unsafeRun(), etc.
  }

  @Nested
  @DisplayName("Composable Operations (map, peek)")
  class ComposableOperationsTests {
    // Functor operations
  }

  @Nested
  @DisplayName("Chainable Operations (via, then)")
  class ChainableOperationsTests {
    // Monad operations
  }

  @Nested
  @DisplayName("Combinable Operations (zipWith)")
  class CombinableOperationsTests {
    // Applicative operations
  }

  @Nested
  @DisplayName("Recoverable Operations (recover, mapError)")  // if applicable
  class RecoverableOperationsTests {
    // MonadError operations - only for Recoverable paths
  }

  @Nested
  @DisplayName("Object Methods (equals, hashCode, toString)")
  class ObjectMethodsTests {
    // Standard object method tests
  }
}
```

### Path-Specific Factory Delegation Pattern

Tests verify that the `Path` factory delegates correctly:

```java
@Test
@DisplayName("Path.just() creates MaybePath with value")
void pathJustCreatesMaybePathWithValue() {
  MaybePath<String> path = Path.just(TEST_VALUE);
  assertThat(path.run().isJust()).isTrue();
}
```

### Environment/Context Testing Pattern

For paths that require context (ReaderPath, WriterPath, WithStatePath), use embedded records:

```java
// Test environment record
record Config(String host, int port, boolean debug) {}
private static final Config TEST_CONFIG = new Config("localhost", 8080, true);

@Test
@DisplayName("run() executes computation with environment")
void runExecutesWithEnvironment() {
  ReaderPath<Config, String> path = Path.asks(c -> c.host() + ":" + c.port());
  assertThat(path.run(TEST_CONFIG)).isEqualTo("localhost:8080");
}
```

### AtomicBoolean Side-Effect Verification Pattern

Consistently used to verify peek/consumer invocation:

```java
@Test
@DisplayName("peek() observes value without modifying")
void peekObservesValueWithoutModifying() {
  AtomicBoolean called = new AtomicBoolean(false);
  MaybePath<String> result = path.peek(v -> called.set(true));

  result.run(); // Execute the path
  assertThat(called).isTrue();
}
```

### Property Test Arbitrary Provider Pattern

Property tests should provide arbitraries for both paths and functions:

```java
@Provide
Arbitrary<MaybePath<Integer>> maybePaths() {
  return Arbitraries.integers()
      .between(-1000, 1000)
      .injectNull(0.15)  // For paths with empty/failure states
      .map(i -> i == null ? Path.nothing() : Path.just(i));
}

@Provide
Arbitrary<Function<Integer, MaybePath<String>>> intToMaybeStringFunctions() {
  return Arbitraries.of(
      i -> i % 2 == 0 ? Path.just("even:" + i) : Path.nothing(),
      i -> i > 0 ? Path.just("positive:" + i) : Path.nothing(),
      i -> Path.just("value:" + i),
      i -> i == 0 ? Path.nothing() : Path.just("" + i)
  );
}
```

### Property Test Standard Laws

Every path property test should verify:

```java
// Functor Laws
@Property
@Label("Functor Identity Law: path.map(id) == path")
void functorIdentityLaw(@ForAll("paths") XxxPath<Integer> path) {
  XxxPath<Integer> result = path.map(Function.identity());
  assertThat(/* semantic equality */);
}

@Property
@Label("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
void functorCompositionLaw(...) { /* ... */ }

// Monad Laws
@Property
@Label("Monad Left Identity Law: XxxPath.pure(a).via(f) == f(a)")
void leftIdentityLaw(...) { /* ... */ }

@Property
@Label("Monad Right Identity Law: path.via(XxxPath::pure) == path")
void rightIdentityLaw(...) { /* ... */ }

@Property
@Label("Monad Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
void associativityLaw(...) { /* ... */ }
```

### Laws Test DynamicTest Organization

Laws tests should use `@TestFactory` with nested classes:

```java
@Nested
@DisplayName("Functor Laws")
class FunctorLawsTests {
  @TestFactory
  @DisplayName("Functor Identity Law: path.map(id) == path")
  Stream<DynamicTest> functorIdentityLaw() {
    return Stream.of(
        DynamicTest.dynamicTest("Identity law holds for pure value", () -> { ... }),
        DynamicTest.dynamicTest("Identity law holds for computed value", () -> { ... })
    );
  }
}

@Nested
@DisplayName("Monad Laws")
class MonadLawsTests {
  // Similar structure for monad laws
}
```

### Collection Path Testing (ListPath vs NonDetPath)

ListPath and NonDetPath have different zipWith semantics that must be tested:

```java
// ListPath: positional zipWith (stops at shortest)
@Test
void zipWithUsesPositionalSemantics() {
  ListPath<Integer> a = ListPath.of(1, 2, 3);
  ListPath<String> b = ListPath.of("a", "b");
  ListPath<String> result = a.zipWith(b, (i, s) -> i + s);
  assertThat(result.toList()).containsExactly("1a", "2b"); // Only 2 elements
}

// NonDetPath: Cartesian product zipWith
@Test
void zipWithUsesCartesianProductSemantics() {
  NonDetPath<Integer> a = NonDetPath.of(1, 2);
  NonDetPath<String> b = NonDetPath.of("a", "b");
  NonDetPath<String> result = a.zipWith(b, (i, s) -> i + s);
  assertThat(result.toList()).containsExactly("1a", "1b", "2a", "2b"); // 4 elements
}
```

### Async Path Testing (CompletableFuturePath)

For async paths, use `.join()` for blocking assertions in tests:

```java
@Test
void mapTransformsCompletedValue() {
  CompletableFuturePath<Integer> path = CompletableFuturePath.pure(42);
  CompletableFuturePath<String> result = path.map(i -> "value:" + i);
  assertThat(result.run().join()).isEqualTo("value:42");
}
```

### Lazy Path Testing (LazyPath)

Test laziness preservation and memoization:

```java
@Test
void computationIsDeferred() {
  AtomicInteger counter = new AtomicInteger(0);
  LazyPath<Integer> path = LazyPath.defer(() -> counter.incrementAndGet());

  assertThat(counter.get()).isEqualTo(0); // Not yet evaluated
  path.run(); // Force evaluation
  assertThat(counter.get()).isEqualTo(1);
}

@Test
void resultIsMemoized() {
  AtomicInteger counter = new AtomicInteger(0);
  LazyPath<Integer> path = LazyPath.defer(() -> counter.incrementAndGet());

  path.run();
  path.run();
  assertThat(counter.get()).isEqualTo(1); // Only evaluated once
}
```

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

## Annotation Processor Testing

The `hkj-processor` module uses specialized testing patterns to verify that generated optics code is correct and stable.

### Processor Optic Law Verification

Generated lenses and prisms must satisfy their mathematical laws. The `GeneratedOpticLawsTest` verifies this using runtime compilation.

**Key Components:**

```java
// RuntimeCompilationHelper compiles test types at runtime
CompiledResult compiled = RuntimeCompilationHelper.compile(
    CUSTOMER_RECORD,  // The record to generate optics for
    PACKAGE_INFO      // @ImportOptics annotation
);

// Load and invoke generated lens
Object lens = compiled.invokeStatic("com.test.optics.CustomerLenses", "name");
Object customer = compiled.newInstance("com.test.Customer", "Alice", 30);

// Verify laws via reflection
Object gotten = compiled.invokeLensGet(lens, customer);
Object result = compiled.invokeLensSet(lens, gotten, customer);
assertThat(result).isEqualTo(customer); // Get-Put law
```

**Tested Laws:**

| Optic | Law | Formula |
|-------|-----|---------|
| Lens | Get-Put | `set(get(s), s) == s` |
| Lens | Put-Get | `get(set(a, s)) == a` |
| Lens | Put-Put | `set(b, set(a, s)) == set(b, s)` |
| Prism | Build-GetOptional | `getOptional(build(a)) == Some(a)` |
| Prism | GetOptional-Build | `getOptional(s).map(build) == s` (when matching) |

**Location:** `hkj-processor/src/test/java/org/higherkindedj/optics/processing/GeneratedOpticLawsTest.java`

### Processor Property-Based Testing

Property-based tests use jqwik to verify generated optics with random inputs.

**Lens Property Tests:**

```java
@Property(tries = 100)
@Label("GetPut Law for name lens: set(get(s), s) == s")
void nameLensGetPutLaw(
    @ForAll @StringLength(min = 1, max = 50) String name,
    @ForAll @IntRange(min = 0, max = 150) int age,
    @ForAll boolean active) throws ReflectiveOperationException {

  Object lens = compiled.invokeStatic(TEST_PACKAGE + ".PersonLenses", "name");
  Object person = compiled.newInstance("org.test.Person", name, age, active);

  Object gotten = compiled.invokeLensGet(lens, person);
  Object result = compiled.invokeLensSet(lens, gotten, person);

  assertThat(result).isEqualTo(person);
}
```

**Prism Property Tests:**

```java
@Property(tries = 100)
@Label("Build-GetOptional Law: getOptional(build(a)) == Some(a)")
void creditCardPrismBuildGetOptionalLaw(
    @ForAll @StringLength(min = 16, max = 19) String cardNumber,
    @ForAll @StringLength(min = 5, max = 7) String expiry) throws ReflectiveOperationException {

  Object prism = compiled.invokeStatic(TEST_PACKAGE + ".PaymentMethodPrisms", "creditCard");
  Object creditCard = compiled.newInstance("org.test.PaymentMethod$CreditCard", cardNumber, expiry);

  Object built = compiled.invokePrismBuild(prism, creditCard);
  Optional<Object> result = compiled.invokePrismGetOptional(prism, built);

  assertThat(result).isPresent();
  assertThat(result.get()).isEqualTo(creditCard);
}
```

**Locations:**
- `hkj-processor/src/test/java/org/higherkindedj/optics/processing/GeneratedLensPropertyTest.java`
- `hkj-processor/src/test/java/org/higherkindedj/optics/processing/GeneratedPrismPropertyTest.java`

### Golden File Testing

Golden file tests detect unintended changes in generated code by comparing against known-good outputs.

**Structure:**

```
hkj-processor/src/test/resources/golden/
├── CustomerLenses.java.golden
├── ShapePrisms.java.golden
├── StatusPrisms.java.golden
├── PairLenses.java.golden
└── NestedLenses.java.golden
```

**Test Pattern:**

```java
@ParameterizedTest(name = "{0}")
@MethodSource("goldenFileTestCases")
@DisplayName("Generated code matches golden file")
void generatedCodeMatchesGolden(GoldenTestCase testCase) throws IOException {
  Compilation compilation = compile(testCase.sources());
  assertThat(compilation.status()).isEqualTo(Compilation.Status.SUCCESS);

  String generated = getGeneratedSource(compilation, testCase.generatedClassName());
  String golden = readGoldenFile(testCase.goldenFileName());

  assertThat(normalizeForComparison(generated))
      .isEqualTo(normalizeForComparison(golden));
}
```

**Updating Golden Files:**

When intentional changes are made to code generation:

```bash
./gradlew :hkj-processor:updateGoldenFiles
```

**Location:** `hkj-processor/src/test/java/org/higherkindedj/optics/processing/GoldenFileTest.java`

### Mutation Testing

Mutation testing measures test quality by introducing small changes (mutants) to the code and verifying tests catch them.

**Configuration:** PIT is configured in `hkj-processor/build.gradle.kts` with two profiles:

#### Profiles

| Setting | `conservative` (default) | `full` |
|---------|--------------------------|--------|
| Threads | Half available CPUs | All available CPUs |
| Mutators | `DEFAULT` | `STRONGER` |
| Heap per fork | `512m` | `1g` |
| Mutation threshold | 70% | 70% |

The **conservative** profile is the default and is suitable for laptops, CI runners, or machines where you want to keep resources available for other work. The **full** profile uses all available CPU cores and the more comprehensive `STRONGER` mutator set for thorough analysis.

#### Running Mutation Tests

```bash
# Conservative (default) — safe for laptops and lower-spec machines
./gradlew :hkj-processor:pitest

# Full — all cores, stronger mutators
./gradlew :hkj-processor:pitest -Ppitest.profile=full
```

#### Fine-Tuning Individual Settings

Each profile setting can be overridden independently via project properties. These overrides take precedence over the profile:

```bash
# Use conservative profile but with 4 threads
./gradlew :hkj-processor:pitest -Ppitest.threads=4

# Use full profile but cap heap at 768m
./gradlew :hkj-processor:pitest -Ppitest.profile=full -Ppitest.heap=768m

# Use STRONGER mutators with only 2 threads (constrained machine)
./gradlew :hkj-processor:pitest -Ppitest.mutators=STRONGER -Ppitest.threads=2
```

| Property | Values | Description |
|----------|--------|-------------|
| `pitest.profile` | `conservative`, `full` | Selects the base profile |
| `pitest.threads` | Any positive integer | Overrides thread count |
| `pitest.mutators` | `DEFAULT`, `STRONGER`, `ALL` | Overrides mutator group |
| `pitest.heap` | e.g. `512m`, `1g` | Overrides per-fork JVM heap |

#### Tuning Guidance

**Choosing a profile:**
- Start with `conservative` (the default). It generates fewer mutants (`DEFAULT` mutators) and uses fewer threads, keeping your machine responsive.
- Switch to `full` when you want a comprehensive analysis before merging, or on a dedicated build machine with resources to spare.

**Thread count considerations:**
- Each PIT thread forks a separate JVM, so memory usage scales linearly with thread count. On an 8-core machine with 16GB RAM, `full` (8 threads) will consume roughly 8GB of heap for PIT alone.
- If you see `OutOfMemoryError` or heavy swapping, reduce threads: `-Ppitest.threads=2`.
- A good rule of thumb: allow ~1GB of total system RAM per PIT thread (heap + JVM overhead).

**Mutator groups:**
- `DEFAULT` — Standard mutators (conditionals, math, return values). Fast; catches the majority of test weaknesses.
- `STRONGER` — Adds additional mutators (e.g. remove conditionals, constructor calls). ~2-3x more mutants generated. Use for thorough analysis.
- `ALL` — Every available mutator. Generates the most mutants, takes the longest. Rarely needed; useful for one-off deep audits.

**Heap sizing:**
- `512m` is sufficient for most annotation processor mutations since the generated code is relatively small.
- Increase to `768m` or `1g` if you see `OutOfMemoryError` in PIT output, especially when using `STRONGER` or `ALL` mutators.

**Persisting preferences in `gradle.properties`:**

To avoid typing `-P` flags every time, add your preferred settings to `gradle.properties`:

```properties
# gradle.properties (local — do not commit to shared repo)
pitest.profile=full
pitest.threads=4
```

Or in `~/.gradle/gradle.properties` for machine-wide defaults.

**Reports:** `hkj-processor/build/reports/pitest/`

**Interpreting Results:**

| Metric | Description |
|--------|-------------|
| Mutation Score | Percentage of mutants killed by tests |
| Killed | Mutant was detected (test failed) |
| Survived | Mutant was NOT detected (test gap) |
| No Coverage | Mutant in code not covered by tests |

**Note:** Mutation testing is a local development tool, not a CI gate. Use it to identify weak tests and improve test quality.

### Edge Case Testing

The `EdgeCaseTest` class verifies the processor handles unusual but valid Java code:

**Categories:**
- **Nested Generics**: `List<Optional<Map<String, List<Integer>>>>`
- **Unusual Names**: Keyword suffixes (`class_`), single chars, uppercase
- **Empty Types**: Empty records, single-constant enums
- **Recursive Types**: Self-referential records, expression trees
- **Complex Hierarchies**: Mixed sealed subtypes, multi-level hierarchies

**Location:** `hkj-processor/src/test/java/org/higherkindedj/optics/processing/EdgeCaseTest.java`

### Error Message Testing

The `ErrorMessageTest` class verifies error messages are clear and actionable:

**Categories:**
- Unsupported type errors (plain classes, non-sealed interfaces)
- Mutable type errors (classes with setters)
- Missing type errors
- Annotation placement errors
- Wither detection errors

**Location:** `hkj-processor/src/test/java/org/higherkindedj/optics/processing/ErrorMessageTest.java`

### Incremental Compilation Testing

The `IncrementalCompilationTest` class verifies the processor handles source changes:

**Scenarios:**
- Field additions/removals reflected in generated code
- Type parameter changes propagated
- Sealed interface variant changes handled
- Enum constant changes handled

**Location:** `hkj-processor/src/test/java/org/higherkindedj/optics/processing/IncrementalCompilationTest.java`

### Running Processor Tests

```bash
# Run all processor tests
./gradlew :hkj-processor:test

# Run law verification tests
./gradlew :hkj-processor:test --tests "*OpticLawsTest"

# Run property-based tests
./gradlew :hkj-processor:test --tests "*PropertyTest"

# Run golden file tests
./gradlew :hkj-processor:test --tests "*GoldenFileTest"

# Run edge case tests
./gradlew :hkj-processor:test --tests "*EdgeCaseTest"

# Run mutation testing — conservative (default)
./gradlew :hkj-processor:pitest

# Run mutation testing — full (all cores, STRONGER mutators)
./gradlew :hkj-processor:pitest -Ppitest.profile=full
```

## Summary

The higher-kinded-j testing approach combines:

1. **JUnit 6 Features**: @ParameterizedTest, @TestFactory, @Nested for minimal boilerplate
2. **Property-Based Testing**: jQwik for comprehensive coverage with generated inputs
3. **Law Verification**: Systematic testing of typeclass and optics laws
4. **Clear Organization**: Nested classes and descriptive names for maintainability
5. **Separation of Concerns**: Functional tests in hkj-core, performance in hkj-benchmarks

This comprehensive strategy ensures correctness, maintains clarity, and provides fast feedback during development.

For more examples, explore the test files referenced throughout this guide.
