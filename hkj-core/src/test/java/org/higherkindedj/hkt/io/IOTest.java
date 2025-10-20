// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.coverage.TestCoverageReporter;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("IO Complete Test Suite")
class IOTest extends TypeClassTestBase<IOKind.Witness, Integer, String> {

  private IOMonad monad;
  private IOFunctor functor;

  @Override
  protected Kind<IOKind.Witness, Integer> createValidKind() {
    return IO_OP.widen(IO.delay(() -> 42));
  }

  @Override
  protected Kind<IOKind.Witness, Integer> createValidKind2() {
    return IO_OP.widen(IO.delay(() -> 24));
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return TestFunctions.INT_TO_STRING;
  }

  @Override
  protected BiPredicate<Kind<IOKind.Witness, ?>, Kind<IOKind.Witness, ?>> createEqualityChecker() {
    return (k1, k2) -> {
      // Execute both IOs and compare their results using Object.equals()
      // This works because we're comparing the actual values, not the types
      Object v1 = IO_OP.narrow(castKind(k1)).unsafeRunSync();
      Object v2 = IO_OP.narrow(castKind(k2)).unsafeRunSync();
      return v1.equals(v2);
    };
  }

  @SuppressWarnings("unchecked")
  private static <A> Kind<IOKind.Witness, A> castKind(Kind<IOKind.Witness, ?> kind) {
    return (Kind<IOKind.Witness, A>) kind;
  }

  @Override
  protected Function<Integer, Kind<IOKind.Witness, String>> createValidFlatMapper() {
    return i -> IO_OP.widen(IO.delay(() -> "flat:" + i));
  }

  @Override
  protected Kind<IOKind.Witness, Function<Integer, String>> createValidFunctionKind() {
    return IO_OP.widen(IO.delay(() -> TestFunctions.INT_TO_STRING));
  }

  @BeforeEach
  void setUpIO() {
    monad = IOMonad.INSTANCE;
    functor = new IOFunctor();
  }

  @Nested
  @DisplayName("Complete Test Suite")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete IO test pattern")
    void runCompleteIOTestPattern() {
      CoreTypeTest.<Integer>io(IO.class)
          .withIO(IO.delay(() -> 42))
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

      assertThat(executed.get()).as("IO computation should not execute on creation").isFalse();

      String result = lazyIO.unsafeRunSync();

      assertThat(executed.get())
          .as("IO computation should execute when unsafeRunSync is called")
          .isTrue();
      assertThat(result).isEqualTo("executed");
    }

    @Test
    @DisplayName("Test IO map transforms result")
    void testMapTransformsResult() {
      IO<Integer> io = IO.delay(() -> 42);
      IO<String> mapped = io.map(Object::toString);

      assertThat(mapped.unsafeRunSync()).isEqualTo("42");
    }

    @Test
    @DisplayName("Test IO flatMap chains computations")
    void testFlatMapChainsComputations() {
      IO<Integer> io = IO.delay(() -> 42);
      IO<String> flatMapped = io.flatMap(i -> IO.delay(() -> "Result: " + i));

      assertThat(flatMapped.unsafeRunSync()).isEqualTo("Result: 42");
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
                return 42;
              });

      IO<String> io2 =
          io1.flatMap(
              i ->
                  IO.delay(
                      () -> {
                        executed2.set(true);
                        return "Value: " + i;
                      }));

      assertThat(executed1.get()).isFalse();
      assertThat(executed2.get()).isFalse();

      String result = io2.unsafeRunSync();

      assertThat(executed1.get()).isTrue();
      assertThat(executed2.get()).isTrue();
      assertThat(result).isEqualTo("Value: 42");
    }

    @Test
    @DisplayName("Test IO can return null")
    void testIOCanReturnNull() {
      IO<String> nullIO = IO.delay(() -> null);
      assertThat(nullIO.unsafeRunSync()).isNull();
    }

    @Test
    @DisplayName("Test IO map propagates exceptions")
    void testMapPropagatesExceptions() {
      IO<Integer> io = IO.delay(() -> 42);
      RuntimeException testException = new RuntimeException("Test exception");

      IO<String> mapped =
          io.map(
              i -> {
                throw testException;
              });

      assertThatThrownBy(mapped::unsafeRunSync).isSameAs(testException);
    }

    @Test
    @DisplayName("Test IO flatMap propagates exceptions")
    void testFlatMapPropagatesExceptions() {
      IO<Integer> io = IO.delay(() -> 42);
      RuntimeException testException = new RuntimeException("Test exception");

      IO<String> flatMapped =
          io.flatMap(
              i -> {
                throw testException;
              });

      assertThatThrownBy(flatMapped::unsafeRunSync).isSameAs(testException);
    }

    @Test
    @DisplayName("Test IO flatMap validates non-null result")
    void testFlatMapValidatesNonNullResult() {
      IO<Integer> io = IO.delay(() -> 42);

      IO<String> flatMapped = io.flatMap(i -> null);

      assertThatThrownBy(flatMapped::unsafeRunSync)
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(
              "Function f in IO.flatMap returned null when IO expected, which is not allowed");
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
      assertThat(IO_OP.narrow(result).unsafeRunSync()).isEqualTo("42");
    }

    @Test
    @DisplayName("Test functor map composition")
    void testFunctorMapComposition() {
      Function<String, Integer> secondMapper = String::length;

      Kind<IOKind.Witness, String> mapped1 = functor.map(validMapper, validKind);
      Kind<IOKind.Witness, Integer> mapped2 = functor.map(secondMapper, mapped1);

      assertThat(IO_OP.narrow(mapped2).unsafeRunSync()).isEqualTo(2);
    }

    @Test
    @DisplayName("Test functor identity law")
    void testFunctorIdentityLaw() {
      Function<Integer, Integer> identity = i -> i;
      Kind<IOKind.Witness, Integer> mapped = functor.map(identity, validKind);

      assertThat(equalityChecker.test(mapped, validKind))
          .as("Functor identity law: map(id, fa) == fa")
          .isTrue();
    }

    @Test
    @DisplayName("Test functor composition law")
    void testFunctorCompositionLaw() {
      Function<String, Integer> g = String::length;

      // Left side: map(g ∘ f, fa)
      Function<Integer, Integer> composed = validMapper.andThen(g);
      Kind<IOKind.Witness, Integer> leftSide = functor.map(composed, validKind);

      // Right side: map(g, map(f, fa))
      Kind<IOKind.Witness, String> intermediate = functor.map(validMapper, validKind);
      Kind<IOKind.Witness, Integer> rightSide = functor.map(g, intermediate);

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
      assertThat(IO_OP.narrow(result).unsafeRunSync()).isEqualTo("flat:42");
    }

    @Test
    @DisplayName("Test monad left identity law")
    void testMonadLeftIdentityLaw() {
      Integer testValue = 42;
      Kind<IOKind.Witness, Integer> ofValue = monad.of(testValue);

      Kind<IOKind.Witness, String> leftSide = monad.flatMap(validFlatMapper, ofValue);
      Kind<IOKind.Witness, String> rightSide = validFlatMapper.apply(testValue);

      assertThat(equalityChecker.test(leftSide, rightSide))
          .as("Monad left identity law: flatMap(of(a), f) == f(a)")
          .isTrue();
    }

    @Test
    @DisplayName("Test monad right identity law")
    void testMonadRightIdentityLaw() {
      Function<Integer, Kind<IOKind.Witness, Integer>> ofFunc = monad::of;
      Kind<IOKind.Witness, Integer> leftSide = monad.flatMap(ofFunc, validKind);

      assertThat(equalityChecker.test(leftSide, validKind))
          .as("Monad right identity law: flatMap(m, of) == m")
          .isTrue();
    }

    @Test
    @DisplayName("Test monad associativity law")
    void testMonadAssociativityLaw() {
      Function<String, Kind<IOKind.Witness, String>> chainFunction =
          s -> IO_OP.widen(IO.delay(() -> s + "!"));

      // Left side: flatMap(flatMap(m, f), g)
      Kind<IOKind.Witness, String> innerFlatMap = monad.flatMap(validFlatMapper, validKind);
      Kind<IOKind.Witness, String> leftSide = monad.flatMap(chainFunction, innerFlatMap);

      // Right side: flatMap(m, x -> flatMap(f(x), g))
      Function<Integer, Kind<IOKind.Witness, String>> rightSideFunc =
          i -> monad.flatMap(chainFunction, validFlatMapper.apply(i));
      Kind<IOKind.Witness, String> rightSide = monad.flatMap(rightSideFunc, validKind);

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
      IO<Integer> io = IO.delay(() -> 42);

      assertThatThrownBy(() -> io.map(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Function f for IO.map cannot be null");
    }

    @Test
    @DisplayName("Test IO.flatMap validates null mapper")
    void testFlatMapValidatesNullMapper() {
      IO<Integer> io = IO.delay(() -> 42);

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
      IO<Integer> original = IO.delay(() -> 42);
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
          .isInstanceOf(org.higherkindedj.hkt.exception.KindUnwrapException.class)
          .hasMessageContaining("IO");
    }

    @Test
    @DisplayName("Test delay helper creates valid Kind")
    void testDelayHelperCreatesValidKind() {
      Kind<IOKind.Witness, String> kind = IO_OP.delay(() -> "test");

      assertThat(kind).isNotNull();
      assertThat(IO_OP.unsafeRunSync(kind)).isEqualTo("test");
    }

    @Test
    @DisplayName("Test unsafeRunSync helper executes IO")
    void testUnsafeRunSyncHelperExecutesIO() {
      Kind<IOKind.Witness, Integer> kind = IO_OP.widen(IO.delay(() -> 42));

      Integer result = IO_OP.unsafeRunSync(kind);

      assertThat(result).isEqualTo(42);
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCasesTests {

    @Test
    @DisplayName("Test nested IO execution")
    void testNestedIOExecution() {
      IO<IO<Integer>> nested = IO.delay(() -> IO.delay(() -> 42));
      IO<Integer> inner = nested.unsafeRunSync();
      Integer result = inner.unsafeRunSync();

      assertThat(result).isEqualTo(42);
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

      io.unsafeRunSync();
      assertThat(executed.get()).isTrue();

      // Reset and run again
      executed.set(false);
      io.unsafeRunSync();
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
                return 42;
              });

      IO<String> mapped = io.map(Object::toString).map(s -> s + "!");

      assertThat(executed.get()).isFalse();

      String result = mapped.unsafeRunSync();

      assertThat(executed.get()).isTrue();
      assertThat(result).isEqualTo("42!");
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
                return 42;
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

      String result = chained.unsafeRunSync();

      assertThat(executed1.get()).isTrue();
      assertThat(executed2.get()).isTrue();
      assertThat(result).isEqualTo("42!");
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
