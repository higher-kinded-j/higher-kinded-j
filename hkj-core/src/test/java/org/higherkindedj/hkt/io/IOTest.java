// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.io.IOAssert.assertThatIO;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

import java.util.concurrent.atomic.AtomicBoolean;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.higherkindedj.hkt.test.coverage.TestCoverageReporter;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for IO using standardised patterns.
 *
 * <p>Coverage includes factory methods, Functor/Monad operations, utility methods, object methods,
 * algebraic laws, and performance characteristics.
 */
@DisplayName("IO<T> Complete Test Suite")
class IOTest extends IOTestBase {

  private final String testString = "Test Value";
  private final IO<String> testIO = IO.delay(() -> testString);
  private final RuntimeException testException = new RuntimeException("Test exception");
  private final IO<String> failingTestIO =
      IO.delay(
          () -> {
            throw testException;
          });

  private IOMonad monad;
  private IOFunctor functor;

  @BeforeEach
  void setUpIO() {
    monad = IOMonad.INSTANCE;
    functor = new IOFunctor();
    validateRequiredFixtures();
  }

  @Nested
  @DisplayName("Complete Test Suite")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete IO test pattern")
    void runCompleteIOTestPattern() {
      CoreTypeTest.<Integer>io(IO.class)
          .withIO(IO.delay(() -> DEFAULT_IO_VALUE))
          .withMapper(TestFunctions.INT_TO_STRING)
          .testAll();
    }
  }

  @Nested
  @DisplayName("Core Type Tests")
  class CoreTypeTests {

    @Test
    @DisplayName("Test IO delay creates lazy computation")
    void testDelayCreatesLazyComputation() {
      AtomicBoolean executed = new AtomicBoolean(false);

      IO<String> lazyIO =
          IO.delay(
              () -> {
                executed.set(true);
                return "executed";
              });

      assertThatIO(lazyIO).isNotExecutedYet();
      assertThat(executed.get()).as("IO computation should not execute on creation").isFalse();

      assertThatIO(lazyIO).hasValue("executed");
      assertThat(executed.get())
          .as("IO computation should execute when unsafeRunSync is called")
          .isTrue();
    }

    @Test
    @DisplayName("Test IO map transforms result")
    void testMapTransformsResult() {
      IO<Integer> io = IO.delay(() -> DEFAULT_IO_VALUE);
      IO<String> mapped = io.map(Object::toString);

      assertThatIO(mapped).hasValue(String.valueOf(DEFAULT_IO_VALUE));
    }

    @Test
    @DisplayName("Test IO flatMap chains computations")
    void testFlatMapChainsComputations() {
      IO<Integer> io = IO.delay(() -> DEFAULT_IO_VALUE);
      IO<String> flatMapped = io.flatMap(i -> IO.delay(() -> "Result: " + i));

      assertThatIO(flatMapped).hasValue("Result: " + DEFAULT_IO_VALUE);
    }

    @Test
    @DisplayName("Test IO composition preserves laziness")
    void testCompositionPreservesLaziness() {
      AtomicBoolean executed1 = new AtomicBoolean(false);
      AtomicBoolean executed2 = new AtomicBoolean(false);

      IO<Integer> io1 =
          IO.delay(
              () -> {
                executed1.set(true);
                return DEFAULT_IO_VALUE;
              });

      IO<String> io2 =
          io1.flatMap(
              i ->
                  IO.delay(
                      () -> {
                        executed2.set(true);
                        return "Value: " + i;
                      }));

      assertThatIO(io2).isNotExecutedYet();
      assertThat(executed1.get()).isFalse();
      assertThat(executed2.get()).isFalse();

      assertThatIO(io2).hasValue("Value: " + DEFAULT_IO_VALUE);
      assertThat(executed1.get()).isTrue();
      assertThat(executed2.get()).isTrue();
    }

    @Test
    @DisplayName("Test IO can return null")
    void testIOCanReturnNull() {
      IO<String> nullIO = IO.delay(() -> null);
      assertThatIO(nullIO).hasValueNull();
    }

    @Test
    @DisplayName("Test IO map propagates exceptions")
    void testMapPropagatesExceptions() {
      IO<Integer> io = IO.delay(() -> DEFAULT_IO_VALUE);

      IO<String> mapped = io.map(TestFunctions.throwingFunction(testException));

      assertThatIO(mapped).throwsException(RuntimeException.class).withMessage("Test exception");
    }

    @Test
    @DisplayName("Test IO flatMap propagates exceptions")
    void testFlatMapPropagatesExceptions() {
      IO<Integer> io = IO.delay(() -> DEFAULT_IO_VALUE);

      IO<String> flatMapped = io.flatMap(TestFunctions.throwingFunction(testException));

      assertThatIO(flatMapped)
          .throwsException(RuntimeException.class)
          .withMessage("Test exception");
    }

    @Test
    @DisplayName("Test IO flatMap validates non-null result")
    void testFlatMapValidatesNonNullResult() {
      IO<Integer> io = IO.delay(() -> DEFAULT_IO_VALUE);

      IO<String> flatMapped = io.flatMap(i -> null);

      assertThatIO(flatMapped)
          .throwsException(KindUnwrapException.class)
          .withMessageContaining("flatMap returned null");
    }
  }

  @Nested
  @DisplayName("Functor Tests")
  class FunctorTests {

    @Test
    @DisplayName("Test functor map operation")
    void testFunctorMap() {
      Kind<IOKind.Witness, String> result = functor.map(validMapper, validKind);

      assertThat(result).isNotNull();
      assertThatIO(narrowToIO(result)).hasValue(String.valueOf(DEFAULT_IO_VALUE));
    }

    @Test
    @DisplayName("Test functor map composition")
    void testFunctorMapComposition() {
      Kind<IOKind.Witness, String> mapped1 = functor.map(validMapper, validKind);
      Kind<IOKind.Witness, Integer> mapped2 = functor.map(String::length, mapped1);

      assertThatIO(narrowToIO(mapped2)).hasValue(String.valueOf(DEFAULT_IO_VALUE).length());
    }

    @Test
    @DisplayName("Test functor identity law")
    void testFunctorIdentityLaw() {
      Kind<IOKind.Witness, Integer> mapped = functor.map(i -> i, validKind);

      assertThat(equalityChecker.test(mapped, validKind))
          .as("Functor identity law: map(id, fa) == fa")
          .isTrue();
    }

    @Test
    @DisplayName("Test functor composition law")
    void testFunctorCompositionLaw() {
      // Left side: map(g ∘ f, fa)
      Kind<IOKind.Witness, Integer> leftSide =
          functor.map(validMapper.andThen(String::length), validKind);

      // Right side: map(g, map(f, fa))
      Kind<IOKind.Witness, String> intermediate = functor.map(validMapper, validKind);
      Kind<IOKind.Witness, Integer> rightSide = functor.map(String::length, intermediate);

      assertThat(equalityChecker.test(leftSide, rightSide))
          .as("Functor composition law: map(g ∘ f, fa) == map(g, map(f, fa))")
          .isTrue();
    }
  }

  @Nested
  @DisplayName("Monad Tests")
  class MonadTests {

    @Test
    @DisplayName("Test monad flatMap operation")
    void testMonadFlatMap() {
      Kind<IOKind.Witness, String> result = monad.flatMap(validFlatMapper, validKind);

      assertThat(result).isNotNull();
      assertThatIO(narrowToIO(result)).hasValue("flat:" + DEFAULT_IO_VALUE);
    }

    @Test
    @DisplayName("Test monad left identity law")
    void testMonadLeftIdentityLaw() {
      Kind<IOKind.Witness, Integer> ofValue = monad.of(DEFAULT_IO_VALUE);

      Kind<IOKind.Witness, String> leftSide = monad.flatMap(validFlatMapper, ofValue);
      Kind<IOKind.Witness, String> rightSide = validFlatMapper.apply(DEFAULT_IO_VALUE);

      assertThat(equalityChecker.test(leftSide, rightSide))
          .as("Monad left identity law: flatMap(of(a), f) == f(a)")
          .isTrue();
    }

    @Test
    @DisplayName("Test monad right identity law")
    void testMonadRightIdentityLaw() {
      Kind<IOKind.Witness, Integer> leftSide = monad.flatMap(monad::of, validKind);

      assertThat(equalityChecker.test(leftSide, validKind))
          .as("Monad right identity law: flatMap(m, of) == m")
          .isTrue();
    }

    @Test
    @DisplayName("Test monad associativity law")
    void testMonadAssociativityLaw() {
      // Left side: flatMap(flatMap(m, f), g)
      Kind<IOKind.Witness, String> innerFlatMap = monad.flatMap(validFlatMapper, validKind);
      Kind<IOKind.Witness, String> leftSide = monad.flatMap(chainFunction, innerFlatMap);

      // Right side: flatMap(m, x -> flatMap(f(x), g))
      Kind<IOKind.Witness, String> rightSide =
          monad.flatMap(i -> monad.flatMap(chainFunction, validFlatMapper.apply(i)), validKind);

      assertThat(equalityChecker.test(leftSide, rightSide)).as("Monad associativity law").isTrue();
    }
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {

    @Test
    @DisplayName("Test IO.delay validates null thunk")
    void testDelayValidatesNullThunk() {
      assertThatThrownBy(() -> IO.delay(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("thunk")
          .hasMessageContaining("delay");
    }

    @Test
    @DisplayName("Test IO.map validates null mapper")
    void testMapValidatesNullMapper() {
      IO<Integer> io = IO.delay(() -> DEFAULT_IO_VALUE);

      assertThatThrownBy(() -> io.map(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Function f for IO.map cannot be null");
    }

    @Test
    @DisplayName("Test IO.flatMap validates null mapper")
    void testFlatMapValidatesNullMapper() {
      IO<Integer> io = IO.delay(() -> DEFAULT_IO_VALUE);

      assertThatThrownBy(() -> io.flatMap(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Function f for IO.flatMap cannot be null");
    }

    @Test
    @DisplayName("Test functor.map validates null function")
    void testFunctorMapValidatesNullFunction() {
      assertThatThrownBy(() -> functor.map(null, validKind))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Function f for IOFunctor.map cannot be null");
    }

    @Test
    @DisplayName("Test functor.map validates null Kind")
    void testFunctorMapValidatesNullKind() {
      assertThatThrownBy(() -> functor.map(validMapper, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Kind")
          .hasMessageContaining("map");
    }

    @Test
    @DisplayName("Test monad.flatMap validates null function")
    void testMonadFlatMapValidatesNullFunction() {
      assertThatThrownBy(() -> monad.flatMap(null, validKind))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Function f for IOMonad.flatMap cannot be null");
    }

    @Test
    @DisplayName("Test monad.flatMap validates null Kind")
    void testMonadFlatMapValidatesNullKind() {
      assertThatThrownBy(() -> monad.flatMap(validFlatMapper, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Kind")
          .hasMessageContaining("flatMap");
    }
  }

  @Nested
  @DisplayName("KindHelper Tests")
  class KindHelperTests {

    @Test
    @DisplayName("Test widen/narrow round-trip preserves identity")
    void testRoundTripPreservesIdentity() {
      IO<Integer> original = IO.delay(() -> DEFAULT_IO_VALUE);
      Kind<IOKind.Witness, Integer> widened = IO_OP.widen(original);
      IO<Integer> narrowed = IO_OP.narrow(widened);

      assertThat(narrowed).isSameAs(original);
    }

    @Test
    @DisplayName("Test widen validates null IO")
    void testWidenValidatesNullIO() {
      assertThatThrownBy(() -> IO_OP.widen(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("IO");
    }

    @Test
    @DisplayName("Test narrow validates null Kind")
    void testNarrowValidatesNullKind() {
      assertThatThrownBy(() -> IO_OP.narrow(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("IO");
    }

    @Test
    @DisplayName("Test delay helper creates valid Kind")
    void testDelayHelperCreatesValidKind() {
      Kind<IOKind.Witness, String> kind = IO_OP.delay(() -> "test");

      assertThat(kind).isNotNull();
      assertThatIO(narrowToIO(kind)).hasValue("test");
    }

    @Test
    @DisplayName("Test unsafeRunSync helper executes IO")
    void testUnsafeRunSyncHelperExecutesIO() {
      Kind<IOKind.Witness, Integer> kind = IO_OP.widen(IO.delay(() -> DEFAULT_IO_VALUE));

      Integer result = IO_OP.unsafeRunSync(kind);

      assertThat(result).isEqualTo(DEFAULT_IO_VALUE);
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCasesTests {

    @Test
    @DisplayName("Test nested IO execution")
    void testNestedIOExecution() {
      IO<IO<Integer>> nested = IO.delay(() -> IO.delay(() -> DEFAULT_IO_VALUE));
      assertThatIO(nested)
          .hasValueSatisfying(inner -> assertThatIO(inner).hasValue(DEFAULT_IO_VALUE));
    }

    @Test
    @DisplayName("Test IO with side effects executes once per unsafeRunSync")
    void testSideEffectsExecuteOncePerCall() {
      AtomicBoolean executed = new AtomicBoolean(false);
      IO<String> io =
          IO.delay(
              () -> {
                executed.set(true);
                return "executed";
              });

      assertThat(executed.get()).isFalse();

      assertThatIO(io).hasValue("executed");
      assertThat(executed.get()).isTrue();

      // Reset and run again
      executed.set(false);
      assertThatIO(io).hasValue("executed");
      assertThat(executed.get()).isTrue();
    }

    @Test
    @DisplayName("Test IO map chain preserves laziness")
    void testMapChainPreservesLaziness() {
      AtomicBoolean executed = new AtomicBoolean(false);

      IO<Integer> io =
          IO.delay(
              () -> {
                executed.set(true);
                return DEFAULT_IO_VALUE;
              });

      IO<String> mapped = io.map(Object::toString).map(s -> s + "!");

      assertThat(executed.get()).isFalse();

      assertThatIO(mapped).hasValue(DEFAULT_IO_VALUE + "!");
      assertThat(executed.get()).isTrue();
    }

    @Test
    @DisplayName("Test IO flatMap chain preserves laziness")
    void testFlatMapChainPreservesLaziness() {
      AtomicBoolean executed1 = new AtomicBoolean(false);
      AtomicBoolean executed2 = new AtomicBoolean(false);

      IO<Integer> io =
          IO.delay(
              () -> {
                executed1.set(true);
                return DEFAULT_IO_VALUE;
              });

      IO<String> chained =
          io.flatMap(
                  i ->
                      IO.delay(
                          () -> {
                            executed2.set(true);
                            return i.toString();
                          }))
              .map(s -> s + "!");

      assertThat(executed1.get()).isFalse();
      assertThat(executed2.get()).isFalse();

      assertThatIO(chained).hasValue(DEFAULT_IO_VALUE + "!");
      assertThat(executed1.get()).isTrue();
      assertThat(executed2.get()).isTrue();
    }

    @Test
    @DisplayName("Test IO is repeatable")
    void testIOIsRepeatable() {
      IO<Integer> io = IO.delay(() -> DEFAULT_IO_VALUE);
      assertThatIO(io).isRepeatable();

      IO<String> mappedIO = io.map(Object::toString);
      assertThatIO(mappedIO).isRepeatable();
    }

    @Test
    @DisplayName("Test IO with exceptions is consistently failing")
    void testIOWithExceptionsIsConsistentlyFailing() {
      assertThatIO(failingTestIO)
          .throwsException(RuntimeException.class)
          .withMessage("Test exception");

      // Should throw same exception on second execution
      assertThatIO(failingTestIO)
          .throwsException(RuntimeException.class)
          .withMessage("Test exception");
    }
  }

  @Nested
  @DisplayName("IOAssert Verification Tests")
  class IOAssertVerificationTests {

    @Test
    @DisplayName("IOAssert whenExecuted works correctly")
    void testWhenExecuted() {
      assertThatIO(testIO).whenExecuted().hasValue(testString);
    }

    @Test
    @DisplayName("IOAssert hasValueSatisfying works correctly")
    void testHasValueSatisfying() {
      assertThatIO(testIO)
          .hasValueSatisfying(
              value -> {
                assertThat(value).isEqualTo(testString);
                assertThat(value).isNotEmpty();
              });
    }

    @Test
    @DisplayName("IOAssert hasValueNonNull works correctly")
    void testHasValueNonNull() {
      assertThatIO(testIO).hasValueNonNull();
    }

    @Test
    @DisplayName("IOAssert completesSuccessfully works correctly")
    void testCompletesSuccessfully() {
      assertThatIO(testIO).completesSuccessfully();
    }

    @Test
    @DisplayName("IOAssert throwsException works correctly")
    void testThrowsException() {
      assertThatIO(failingTestIO).throwsException(RuntimeException.class);
    }

    @Test
    @DisplayName("IOAssert withMessageContaining works correctly")
    void testWithMessageContaining() {
      assertThatIO(failingTestIO)
          .throwsException(RuntimeException.class)
          .withMessageContaining("Test");
    }

    @Test
    @DisplayName("IOAssert getValue and getException work correctly")
    void testGetValueAndGetException() {
      assertThatIO(testIO).whenExecuted();
      assertThat(assertThatIO(testIO).getValue()).isEqualTo(testString);

      assertThatIO(failingTestIO).whenExecuted();
      assertThat(assertThatIO(failingTestIO).getException()).isInstanceOf(RuntimeException.class);
    }
  }

  @Nested
  @DisplayName("Test Coverage Validation")
  class TestCoverageValidation {

    @Test
    @DisplayName("Verify test coverage is comprehensive")
    void verifyTestCoverage() {
      TestCoverageReporter.TestCoverageReport report = TestCoverageReporter.analyze(IOTest.class);

      assertThat(report.hasOperationTests()).as("Should have operation tests").isTrue();

      assertThat(report.hasValidationTests()).as("Should have validation tests").isTrue();

      assertThat(report.hasLawTests()).as("Should have law tests").isTrue();

      assertThat(report.hasEdgeCaseTests()).as("Should have edge case tests").isTrue();
    }
  }
}
