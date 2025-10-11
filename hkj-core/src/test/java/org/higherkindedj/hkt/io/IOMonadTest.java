// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("IOMonad Complete Test Suite")
class IOMonadTest extends TypeClassTestBase<IOKind.Witness, Integer, String> {

  private IOMonad monad;

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
  protected Function<Integer, Kind<IOKind.Witness, String>> createValidFlatMapper() {
    return i -> IO_OP.widen(IO.delay(() -> "flat:" + i));
  }

  @Override
  protected Kind<IOKind.Witness, Function<Integer, String>> createValidFunctionKind() {
    return IO_OP.widen(IO.delay(() -> TestFunctions.INT_TO_STRING));
  }

  @Override
  protected BiFunction<Integer, Integer, String> createValidCombiningFunction() {
    return (a, b) -> "Result:" + a + "," + b;
  }

  @Override
  protected Integer createTestValue() {
    return 42;
  }

  @Override
  protected Function<Integer, Kind<IOKind.Witness, String>> createTestFunction() {
    return i -> IO_OP.widen(IO.delay(() -> "test:" + i));
  }

  @Override
  protected Function<String, Kind<IOKind.Witness, String>> createChainFunction() {
    return s -> IO_OP.widen(IO.delay(() -> s + "!"));
  }

  @Override
  protected BiPredicate<Kind<IOKind.Witness, ?>, Kind<IOKind.Witness, ?>> createEqualityChecker() {
    return (k1, k2) -> {
      Object v1 = IO_OP.narrow(k1).unsafeRunSync();
      Object v2 = IO_OP.narrow(k2).unsafeRunSync();
      return java.util.Objects.equals(v1, v2);
    };
  }

  @BeforeEach
  void setUpMonad() {
    monad = IOMonad.INSTANCE;
    validateMonadFixtures();
  }

  @Nested
  @DisplayName("Complete Monad Test Suite")
  class CompleteMonadTestSuite {
    @Test
    @DisplayName("Run complete Monad test pattern")
    void runCompleteMonadTestPattern() {
      // IO has lazy evaluation, so we skip default exception tests
      // and provide our own in ExceptionPropagationTests
      TypeClassTest.<IOKind.Witness>monad(IOMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(IOFunctor.class)
          .withApFrom(IOApplicative.class)
          .withFlatMapFrom(IOMonad.class)
          .selectTests()
          .skipExceptions()
          .test();
    }

    @Test
    @DisplayName("Validate test structure follows standards")
    void validateTestStructure() {
      TestPatternValidator.ValidationResult result =
          TestPatternValidator.validateAndReport(IOMonadTest.class);

      if (result.hasErrors()) {
        result.printReport();
        throw new AssertionError("Test structure validation failed");
      }
    }
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {
    @Test
    @DisplayName("of() creates pure IO")
    void ofCreatesPureIO() {
      Kind<IOKind.Witness, String> result = monad.of("pureValue");

      String value = IO_OP.unsafeRunSync(result);
      assertThat(value).isEqualTo("pureValue");
    }

    @Test
    @DisplayName("of() with null value")
    void ofWithNullValue() {
      Kind<IOKind.Witness, String> result = monad.of(null);

      String value = IO_OP.unsafeRunSync(result);
      assertThat(value).isNull();
    }

    @Test
    @DisplayName("map() applies function lazily")
    void mapAppliesFunctionLazily() {
      AtomicInteger executeCount = new AtomicInteger(0);

      IO<Integer> io =
          IO.delay(
              () -> {
                executeCount.incrementAndGet();
                return 10;
              });

      Kind<IOKind.Witness, Integer> kind = IO_OP.widen(io);
      Kind<IOKind.Witness, String> result = monad.map(i -> "Val:" + i, kind);

      assertThat(executeCount.get()).isZero();

      String value = IO_OP.unsafeRunSync(result);
      assertThat(value).isEqualTo("Val:10");
      assertThat(executeCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("flatMap() sequences effects correctly")
    void flatMapSequencesEffectsCorrectly() {
      StringBuilder log = new StringBuilder();

      IO<Integer> initialIO =
          IO.delay(
              () -> {
                log.append("Effect1;");
                return 5;
              });

      Function<Integer, Kind<IOKind.Witness, String>> f =
          i -> {
            IO<String> nextIO =
                IO.delay(
                    () -> {
                      log.append("Effect2(").append(i).append(");");
                      return "Val" + (i * 2);
                    });
            return IO_OP.widen(nextIO);
          };

      Kind<IOKind.Witness, Integer> kind = IO_OP.widen(initialIO);
      Kind<IOKind.Witness, String> result = monad.flatMap(f, kind);

      assertThat(log.toString()).isEmpty();

      String value = IO_OP.unsafeRunSync(result);
      assertThat(value).isEqualTo("Val10");
      assertThat(log.toString()).isEqualTo("Effect1;Effect2(5);");
    }

    @Test
    @DisplayName("ap() applies function to value")
    void apAppliesFunctionToValue() {
      Kind<IOKind.Witness, Function<Integer, String>> funcKind = monad.of(i -> "value:" + i);
      Kind<IOKind.Witness, Integer> valueKind = monad.of(42);

      Kind<IOKind.Witness, String> result = monad.ap(funcKind, valueKind);

      String value = IO_OP.unsafeRunSync(result);
      assertThat(value).isEqualTo("value:42");
    }

    @Test
    @DisplayName("map2() combines two IOs")
    void map2CombinesTwoIOs() {
      Kind<IOKind.Witness, Integer> io1 = monad.of(10);
      Kind<IOKind.Witness, String> io2 = monad.of("test");

      BiFunction<Integer, String, String> combiner = (i, s) -> s + ":" + i;
      Kind<IOKind.Witness, String> result = monad.map2(io1, io2, combiner);

      String value = IO_OP.unsafeRunSync(result);
      assertThat(value).isEqualTo("test:10");
    }

    @Test
    @DisplayName("map3() combines three IOs")
    void map3CombinesThreeIOs() {
      Kind<IOKind.Witness, Integer> io1 = monad.of(1);
      Kind<IOKind.Witness, String> io2 = monad.of("test");
      Kind<IOKind.Witness, Double> io3 = monad.of(3.14);

      Function3<Integer, String, Double, String> combiner =
          (i, s, d) -> String.format("%s:%d:%.2f", s, i, d);

      Kind<IOKind.Witness, String> result = monad.map3(io1, io2, io3, combiner);

      String value = IO_OP.unsafeRunSync(result);
      assertThat(value).isEqualTo("test:1:3.14");
    }

    @Test
    @DisplayName("map4() combines four IOs")
    void map4CombinesFourIOs() {
      Kind<IOKind.Witness, Integer> io1 = monad.of(1);
      Kind<IOKind.Witness, String> io2 = monad.of("test");
      Kind<IOKind.Witness, Double> io3 = monad.of(3.14);
      Kind<IOKind.Witness, Boolean> io4 = monad.of(true);

      Function4<Integer, String, Double, Boolean, String> combiner =
          (i, s, d, b) -> String.format("%s:%d:%.2f:%b", s, i, d, b);

      Kind<IOKind.Witness, String> result = monad.map4(io1, io2, io3, io4, combiner);

      String value = IO_OP.unsafeRunSync(result);
      assertThat(value).isEqualTo("test:1:3.14:true");
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {
    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<IOKind.Witness>monad(IOMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .testOperations();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<IOKind.Witness>monad(IOMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(IOFunctor.class)
          .withApFrom(IOApplicative.class)
          .withFlatMapFrom(IOMonad.class)
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      // Note: Default exception tests don't work with IO's lazy evaluation
      // See ExceptionPropagationTests nested class for IO-specific exception tests
      // This test intentionally empty - use ExceptionPropagationTests for verification
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<IOKind.Witness>monad(IOMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .testLaws();
    }
  }

  @Nested
  @DisplayName("Exception Propagation Tests")
  class ExceptionPropagationTests {
    @Test
    @DisplayName("map() propagates exception from original IO")
    void mapPropagatesExceptionFromOriginalIO() {
      RuntimeException exception = new RuntimeException("OriginalFail");
      IO<Integer> failingIO =
          IO.delay(
              () -> {
                throw exception;
              });

      Kind<IOKind.Witness, Integer> kind = IO_OP.widen(failingIO);
      Kind<IOKind.Witness, String> result = monad.map(i -> "Val:" + i, kind);

      assertThatThrownBy(() -> IO_OP.unsafeRunSync(result)).isSameAs(exception);
    }

    @Test
    @DisplayName("map() propagates exception from mapper function")
    void mapPropagatesExceptionFromMapper() {
      RuntimeException exception = new RuntimeException("MapperFail");
      Kind<IOKind.Witness, String> result =
          monad.map(
              i -> {
                throw exception;
              },
              validKind);

      assertThatThrownBy(() -> IO_OP.unsafeRunSync(result)).isSameAs(exception);
    }

    @Test
    @DisplayName("ap() propagates exception from function IO")
    void apPropagatesExceptionFromFunctionIO() {
      RuntimeException exception = new RuntimeException("FuncFail");
      IO<Function<Integer, String>> failingFunc =
          IO.delay(
              () -> {
                throw exception;
              });

      Kind<IOKind.Witness, Function<Integer, String>> funcKind = IO_OP.widen(failingFunc);
      Kind<IOKind.Witness, Integer> valueKind = monad.of(20);

      Kind<IOKind.Witness, String> result = monad.ap(funcKind, valueKind);

      assertThatThrownBy(() -> IO_OP.unsafeRunSync(result)).isSameAs(exception);
    }

    @Test
    @DisplayName("ap() propagates exception from value IO")
    void apPropagatesExceptionFromValueIO() {
      RuntimeException exception = new RuntimeException("ValFail");
      IO<Integer> failingValue =
          IO.delay(
              () -> {
                throw exception;
              });

      Kind<IOKind.Witness, Function<Integer, String>> funcKind = monad.of(i -> "F(" + i + ")");
      Kind<IOKind.Witness, Integer> valueKind = IO_OP.widen(failingValue);

      Kind<IOKind.Witness, String> result = monad.ap(funcKind, valueKind);

      assertThatThrownBy(() -> IO_OP.unsafeRunSync(result)).isSameAs(exception);
    }

    @Test
    @DisplayName("ap() propagates exception from function application")
    void apPropagatesExceptionFromFunctionApplication() {
      RuntimeException exception = new RuntimeException("ApplyFail");
      Kind<IOKind.Witness, Function<Integer, String>> funcKind =
          monad.of(
              i -> {
                throw exception;
              });
      Kind<IOKind.Witness, Integer> valueKind = monad.of(20);

      Kind<IOKind.Witness, String> result = monad.ap(funcKind, valueKind);

      assertThatThrownBy(() -> IO_OP.unsafeRunSync(result)).isSameAs(exception);
    }

    @Test
    @DisplayName("flatMap() propagates exception from initial IO")
    void flatMapPropagatesExceptionFromInitialIO() {
      RuntimeException exception = new RuntimeException("Fail1");
      IO<Integer> failingIO =
          IO.delay(
              () -> {
                throw exception;
              });

      Kind<IOKind.Witness, Integer> kind = IO_OP.widen(failingIO);
      Kind<IOKind.Witness, String> result = monad.flatMap(validFlatMapper, kind);

      assertThatThrownBy(() -> IO_OP.unsafeRunSync(result)).isSameAs(exception);
    }

    @Test
    @DisplayName("flatMap() propagates exception from function application")
    void flatMapPropagatesExceptionFromFunctionApplication() {
      RuntimeException exception = new RuntimeException("FuncApplyFail");
      Function<Integer, Kind<IOKind.Witness, String>> throwingFunc =
          i -> {
            throw exception;
          };

      Kind<IOKind.Witness, String> result = monad.flatMap(throwingFunc, validKind);

      assertThatThrownBy(() -> IO_OP.unsafeRunSync(result)).isSameAs(exception);
    }

    @Test
    @DisplayName("flatMap() propagates exception from resulting IO")
    void flatMapPropagatesExceptionFromResultingIO() {
      RuntimeException exception = new RuntimeException("Fail2");
      Function<Integer, Kind<IOKind.Witness, String>> failingMapper =
          i -> {
            IO<String> failingIO =
                IO.delay(
                    () -> {
                      throw exception;
                    });
            return IO_OP.widen(failingIO);
          };

      Kind<IOKind.Witness, String> result = monad.flatMap(failingMapper, validKind);

      assertThatThrownBy(() -> IO_OP.unsafeRunSync(result)).isSameAs(exception);
    }

    @Test
    @DisplayName("flatMap() validates null result from function")
    void flatMapValidatesNullResultFromFunction() {
      Function<Integer, Kind<IOKind.Witness, String>> nullReturningMapper = i -> null;

      Kind<IOKind.Witness, String> result = monad.flatMap(nullReturningMapper, validKind);

      assertThatThrownBy(() -> IO_OP.unsafeRunSync(result))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Function in flatMap returned null");
    }
  }

  @Nested
  @DisplayName("Monad Laws Tests")
  class MonadLawsTests {
    // Function a -> M b (Integer -> IOKind<String>)
    final Function<Integer, Kind<IOKind.Witness, String>> fLaw = i -> monad.of("v" + i);

    // Function b -> M c (String -> IOKind<String>)
    final Function<String, Kind<IOKind.Witness, String>> gLaw = s -> monad.of(s + "!");

    @Test
    @DisplayName("Left Identity: flatMap(of(a), f) == f(a)")
    void leftIdentity() {
      int value = 5;
      Kind<IOKind.Witness, Integer> ofValue = monad.of(value);

      Kind<IOKind.Witness, String> leftSide = monad.flatMap(fLaw, ofValue);
      Kind<IOKind.Witness, String> rightSide = fLaw.apply(value);

      String leftResult = IO_OP.unsafeRunSync(leftSide);
      String rightResult = IO_OP.unsafeRunSync(rightSide);

      assertThat(leftResult).isEqualTo(rightResult);
    }

    @Test
    @DisplayName("Right Identity: flatMap(m, of) == m")
    void rightIdentity() {
      Kind<IOKind.Witness, Integer> mValue = IO_OP.delay(() -> 10);

      Function<Integer, Kind<IOKind.Witness, Integer>> ofFunc = monad::of;

      Kind<IOKind.Witness, Integer> leftSide = monad.flatMap(ofFunc, mValue);

      Integer leftResult = IO_OP.unsafeRunSync(leftSide);
      Integer rightResult = IO_OP.unsafeRunSync(mValue);

      assertThat(leftResult).isEqualTo(rightResult);
    }

    @Test
    @DisplayName("Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
    void associativity() {
      Kind<IOKind.Witness, Integer> mValue = IO_OP.delay(() -> 10);

      // Left side: flatMap(flatMap(m, f), g)
      Kind<IOKind.Witness, String> innerLeft = monad.flatMap(fLaw, mValue);
      Kind<IOKind.Witness, String> leftSide = monad.flatMap(gLaw, innerLeft);
      String leftResult = IO_OP.unsafeRunSync(leftSide);

      // Right side: flatMap(m, a -> flatMap(f(a), g))
      Function<Integer, Kind<IOKind.Witness, String>> rightSideFunc =
          a -> monad.flatMap(gLaw, fLaw.apply(a));
      Kind<IOKind.Witness, String> rightSide = monad.flatMap(rightSideFunc, mValue);
      String rightResult = IO_OP.unsafeRunSync(rightSide);

      assertThat(leftResult).isEqualTo(rightResult);
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {
    @Test
    @DisplayName("Deep flatMap chaining")
    void deepFlatMapChaining() {
      Kind<IOKind.Witness, Integer> start = monad.of(1);

      Kind<IOKind.Witness, Integer> result = start;
      for (int i = 0; i < 10; i++) {
        final int increment = i;
        result = monad.flatMap(x -> monad.of(x + increment), result);
      }

      Integer value = IO_OP.unsafeRunSync(result);
      assertThat(value).isEqualTo(46); // 1 + 0 + 1 + 2 + ... + 9 = 46
    }

    @Test
    @DisplayName("flatMap with null values")
    void flatMapWithNullValues() {
      Kind<IOKind.Witness, Integer> nullKind = monad.of(null);
      Function<Integer, Kind<IOKind.Witness, String>> nullSafeMapper =
          i -> monad.of(i == null ? "null" : i.toString());

      Kind<IOKind.Witness, String> result = monad.flatMap(nullSafeMapper, nullKind);

      String value = IO_OP.unsafeRunSync(result);
      assertThat(value).isEqualTo("null");
    }

    @Test
    @DisplayName("Complex nested monad operations")
    void complexNestedMonadOperations() {
      Kind<IOKind.Witness, Integer> io1 = monad.of(10);
      Kind<IOKind.Witness, Integer> io2 = monad.of(20);

      Kind<IOKind.Witness, String> result =
          monad.flatMap(a -> monad.flatMap(b -> monad.of("Sum:" + (a + b)), io2), io1);

      String value = IO_OP.unsafeRunSync(result);
      assertThat(value).isEqualTo("Sum:30");
    }

    @Test
    @DisplayName("flatMap is repeatable")
    void flatMapIsRepeatable() {
      Kind<IOKind.Witness, String> result = monad.flatMap(validFlatMapper, validKind);

      String first = IO_OP.unsafeRunSync(result);
      String second = IO_OP.unsafeRunSync(result);

      assertThat(first).isEqualTo(second).isEqualTo("flat:42");
    }
  }

  @Nested
  @DisplayName("Performance Tests")
  class PerformanceTests {
    @Test
    @DisplayName("flatMap efficient with many operations")
    void flatMapEfficientWithManyOperations() {
      if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
        Kind<IOKind.Witness, Integer> start = monad.of(1);

        Kind<IOKind.Witness, Integer> result = start;
        for (int i = 0; i < 100; i++) {
          final int increment = i;
          result = monad.flatMap(x -> monad.of(x + increment), result);
        }

        int expectedSum = 1 + (99 * 100) / 2;
        Integer value = IO_OP.unsafeRunSync(result);
        assertThat(value).isEqualTo(expectedSum);
      }
    }

    @Test
    @DisplayName("Maintains lazy evaluation with long flatMap chains")
    void maintainsLazyEvaluationWithLongFlatMapChains() {
      AtomicInteger executeCount = new AtomicInteger(0);

      IO<Integer> io =
          IO.delay(
              () -> {
                executeCount.incrementAndGet();
                return 1;
              });

      Kind<IOKind.Witness, Integer> start = IO_OP.widen(io);
      Kind<IOKind.Witness, Integer> result = start;

      // Build long chain
      for (int i = 0; i < 50; i++) {
        final int increment = i;
        result = monad.flatMap(x -> monad.of(x + increment), result);
      }

      // Should not have executed yet
      assertThat(executeCount.get()).isZero();

      // Execute once
      Integer value = IO_OP.unsafeRunSync(result);
      assertThat(value).isEqualTo(1226); // 1 + 0 + 1 + ... + 49
      assertThat(executeCount.get()).isEqualTo(1);
    }
  }
}
