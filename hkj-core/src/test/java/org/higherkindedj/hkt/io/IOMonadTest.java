// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.io.IOAssert.assertThatIO;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("IOMonad Complete Test Suite")
class IOMonadTest extends IOTestBase {

  private IOMonad monad;

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

      assertThatIO(narrowToIO(result)).hasValue("pureValue");
    }

    @Test
    @DisplayName("of() with null value")
    void ofWithNullValue() {
      Kind<IOKind.Witness, String> result = monad.of(null);

      assertThatIO(narrowToIO(result)).hasValueNull();
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

      assertThatIO(narrowToIO(result)).hasValue("Val:10");
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

      assertThatIO(narrowToIO(result)).hasValue("Val10");
      assertThat(log.toString()).isEqualTo("Effect1;Effect2(5);");
    }

    @Test
    @DisplayName("ap() applies function to value")
    void apAppliesFunctionToValue() {
      Kind<IOKind.Witness, Function<Integer, String>> funcKind = monad.of(i -> "value:" + i);
      Kind<IOKind.Witness, Integer> valueKind = monad.of(DEFAULT_IO_VALUE);

      Kind<IOKind.Witness, String> result = monad.ap(funcKind, valueKind);

      assertThatIO(narrowToIO(result)).hasValue("value:" + DEFAULT_IO_VALUE);
    }

    @Test
    @DisplayName("map2() combines two IOs")
    void map2CombinesTwoIOs() {
      Kind<IOKind.Witness, Integer> io1 = monad.of(10);
      Kind<IOKind.Witness, String> io2 = monad.of("test");

      BiFunction<Integer, String, String> combiner = (i, s) -> s + ":" + i;
      Kind<IOKind.Witness, String> result = monad.map2(io1, io2, combiner);

      assertThatIO(narrowToIO(result)).hasValue("test:10");
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

      assertThatIO(narrowToIO(result)).hasValue("test:1:3.14");
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

      assertThatIO(narrowToIO(result)).hasValue("test:1:3.14:true");
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
      Kind<IOKind.Witness, Integer> failingKind = failingIO(exception);

      Kind<IOKind.Witness, String> result = monad.map(i -> "Val:" + i, failingKind);

      assertThatIO(narrowToIO(result))
          .throwsException(RuntimeException.class)
          .withMessage("OriginalFail");
    }

    @Test
    @DisplayName("map() propagates exception from mapper function")
    void mapPropagatesExceptionFromMapper() {
      RuntimeException exception = new RuntimeException("MapperFail");
      Kind<IOKind.Witness, String> result =
          monad.map(TestFunctions.throwingFunction(exception), validKind);

      assertThatIO(narrowToIO(result))
          .throwsException(RuntimeException.class)
          .withMessage("MapperFail");
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

      assertThatIO(narrowToIO(result))
          .throwsException(RuntimeException.class)
          .withMessage("FuncFail");
    }

    @Test
    @DisplayName("ap() propagates exception from value IO")
    void apPropagatesExceptionFromValueIO() {
      RuntimeException exception = new RuntimeException("ValFail");
      Kind<IOKind.Witness, Integer> failingValueKind = failingIO(exception);

      Kind<IOKind.Witness, Function<Integer, String>> funcKind = monad.of(i -> "F(" + i + ")");

      Kind<IOKind.Witness, String> result = monad.ap(funcKind, failingValueKind);

      assertThatIO(narrowToIO(result))
          .throwsException(RuntimeException.class)
          .withMessage("ValFail");
    }

    @Test
    @DisplayName("ap() propagates exception from function application")
    void apPropagatesExceptionFromFunctionApplication() {
      RuntimeException exception = new RuntimeException("ApplyFail");
      Kind<IOKind.Witness, Function<Integer, String>> funcKind =
          monad.of(TestFunctions.throwingFunction(exception));
      Kind<IOKind.Witness, Integer> valueKind = monad.of(20);

      Kind<IOKind.Witness, String> result = monad.ap(funcKind, valueKind);

      assertThatIO(narrowToIO(result))
          .throwsException(RuntimeException.class)
          .withMessage("ApplyFail");
    }

    @Test
    @DisplayName("flatMap() propagates exception from initial IO")
    void flatMapPropagatesExceptionFromInitialIO() {
      RuntimeException exception = new RuntimeException("Fail1");
      Kind<IOKind.Witness, Integer> failingKind = failingIO(exception);

      Kind<IOKind.Witness, String> result = monad.flatMap(validFlatMapper, failingKind);

      assertThatIO(narrowToIO(result)).throwsException(RuntimeException.class).withMessage("Fail1");
    }

    @Test
    @DisplayName("flatMap() propagates exception from function application")
    void flatMapPropagatesExceptionFromFunctionApplication() {
      RuntimeException exception = new RuntimeException("FuncApplyFail");
      Function<Integer, Kind<IOKind.Witness, String>> throwingFunc =
          TestFunctions.throwingFunction(exception);

      Kind<IOKind.Witness, String> result = monad.flatMap(throwingFunc, validKind);

      assertThatIO(narrowToIO(result))
          .throwsException(RuntimeException.class)
          .withMessage("FuncApplyFail");
    }

    @Test
    @DisplayName("flatMap() propagates exception from resulting IO")
    void flatMapPropagatesExceptionFromResultingIO() {
      RuntimeException exception = new RuntimeException("Fail2");
      Function<Integer, Kind<IOKind.Witness, String>> failingMapper = i -> failingIO(exception);

      Kind<IOKind.Witness, String> result = monad.flatMap(failingMapper, validKind);

      assertThatIO(narrowToIO(result)).throwsException(RuntimeException.class).withMessage("Fail2");
    }

    @Test
    @DisplayName("flatMap() validates null result from function")
    void flatMapValidatesNullResultFromFunction() {
      Function<Integer, Kind<IOKind.Witness, String>> nullReturningMapper = i -> null;

      Kind<IOKind.Witness, String> result = monad.flatMap(nullReturningMapper, validKind);

      assertThatIO(narrowToIO(result))
          .throwsException(KindUnwrapException.class)
          .withMessageContaining("flatMap returned null");
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

      assertThat(equalityChecker.test(leftSide, rightSide)).isTrue();
    }

    @Test
    @DisplayName("Right Identity: flatMap(m, of) == m")
    void rightIdentity() {
      Kind<IOKind.Witness, Integer> mValue = IO_OP.delay(() -> 10);

      Function<Integer, Kind<IOKind.Witness, Integer>> ofFunc = monad::of;

      Kind<IOKind.Witness, Integer> leftSide = monad.flatMap(ofFunc, mValue);

      assertThat(equalityChecker.test(leftSide, mValue)).isTrue();
    }

    @Test
    @DisplayName("Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
    void associativity() {
      Kind<IOKind.Witness, Integer> mValue = IO_OP.delay(() -> 10);

      // Left side: flatMap(flatMap(m, f), g)
      Kind<IOKind.Witness, String> innerLeft = monad.flatMap(fLaw, mValue);
      Kind<IOKind.Witness, String> leftSide = monad.flatMap(gLaw, innerLeft);

      // Right side: flatMap(m, a -> flatMap(f(a), g))
      Function<Integer, Kind<IOKind.Witness, String>> rightSideFunc =
          a -> monad.flatMap(gLaw, fLaw.apply(a));
      Kind<IOKind.Witness, String> rightSide = monad.flatMap(rightSideFunc, mValue);

      assertThat(equalityChecker.test(leftSide, rightSide)).isTrue();
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

      assertThatIO(narrowToIO(result)).hasValue(46); // 1 + 0 + 1 + 2 + ... + 9 = 46
    }

    @Test
    @DisplayName("flatMap with null values")
    void flatMapWithNullValues() {
      Kind<IOKind.Witness, Integer> nullKind = monad.of(null);
      Function<Integer, Kind<IOKind.Witness, String>> nullSafeMapper =
          i -> monad.of(i == null ? "null" : i.toString());

      Kind<IOKind.Witness, String> result = monad.flatMap(nullSafeMapper, nullKind);

      assertThatIO(narrowToIO(result)).hasValue("null");
    }

    @Test
    @DisplayName("Complex nested monad operations")
    void complexNestedMonadOperations() {
      Kind<IOKind.Witness, Integer> io1 = monad.of(10);
      Kind<IOKind.Witness, Integer> io2 = monad.of(20);

      Kind<IOKind.Witness, String> result =
          monad.flatMap(a -> monad.flatMap(b -> monad.of("Sum:" + (a + b)), io2), io1);

      assertThatIO(narrowToIO(result)).hasValue("Sum:30");
    }

    @Test
    @DisplayName("flatMap is repeatable")
    void flatMapIsRepeatable() {
      Kind<IOKind.Witness, String> result = monad.flatMap(validFlatMapper, validKind);

      assertThatIO(narrowToIO(result)).isRepeatable();
    }
  }


  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {
    @Test
    @DisplayName("flatMap() validates null function")
    void flatMapValidatesNullFunction() {
      assertThatThrownBy(() -> monad.flatMap(null, validKind))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Function f for IOMonad.flatMap cannot be null");
    }

    @Test
    @DisplayName("flatMap() validates null Kind")
    void flatMapValidatesNullKind() {
      assertThatThrownBy(() -> monad.flatMap(validFlatMapper, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Kind")
          .hasMessageContaining("flatMap");
    }
  }

  @Nested
  @DisplayName("IOAssert Integration Tests")
  class IOAssertIntegrationTests {
    @Test
    @DisplayName("IOAssert works with flatMapped IOs")
    void testIOAssertWithFlatMappedIOs() {
      Kind<IOKind.Witness, String> result = monad.flatMap(validFlatMapper, validKind);

      assertThatIO(narrowToIO(result))
          .hasValueNonNull()
          .hasValueSatisfying(v -> assertThat(v).isEqualTo("flat:" + DEFAULT_IO_VALUE));
    }

    @Test
    @DisplayName("IOAssert verifies laziness in flatMap operations")
    void testIOAssertVerifiesLazinessInFlatMap() {
      AtomicInteger outerCounter = new AtomicInteger(0);
      AtomicInteger innerCounter = new AtomicInteger(0);

      Kind<IOKind.Witness, Integer> outerKind =
          IO_OP.widen(
              IO.delay(
                  () -> {
                    outerCounter.incrementAndGet();
                    return 10;
                  }));

      Function<Integer, Kind<IOKind.Witness, String>> flatMapper =
          i ->
              IO_OP.widen(
                  IO.delay(
                      () -> {
                        innerCounter.incrementAndGet();
                        return "value:" + i;
                      }));

      Kind<IOKind.Witness, String> result = monad.flatMap(flatMapper, outerKind);

      // Should not have executed yet
      assertThat(outerCounter.get()).isZero();
      assertThat(innerCounter.get()).isZero();

      // Execute and verify
      assertThatIO(narrowToIO(result)).hasValue("value:10");
      assertThat(outerCounter.get()).isEqualTo(1);
      assertThat(innerCounter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("IOAssert detects exceptions in flatMap chains")
    void testIOAssertDetectsExceptionsInFlatMapChains() {
      RuntimeException exception = new RuntimeException("FlatMap failure");
      Function<Integer, Kind<IOKind.Witness, String>> failingMapper = i -> failingIO(exception);

      Kind<IOKind.Witness, String> result = monad.flatMap(failingMapper, validKind);

      assertThatIO(narrowToIO(result))
          .throwsException(RuntimeException.class)
          .withMessageContaining("FlatMap failure");
    }

    @Test
    @DisplayName("IOAssert verifies repeatability of monad operations")
    void testIOAssertVerifiesRepeatability() {
      Kind<IOKind.Witness, String> result = monad.flatMap(validFlatMapper, validKind);

      assertThatIO(narrowToIO(result)).isRepeatable();
    }
  }
}
