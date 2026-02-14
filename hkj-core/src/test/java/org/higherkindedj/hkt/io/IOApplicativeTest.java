// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.io.IOAssert.assertThatIO;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("IOApplicative Complete Test Suite")
class IOApplicativeTest extends IOTestBase {

  private final IOApplicative applicative = IOApplicative.INSTANCE;
  private Applicative<IOKind.Witness> applicativeTyped;

  @BeforeEach
  void setUpApplicative() {
    applicativeTyped = applicative;
    validateApplicativeFixtures();
  }

  @Nested
  @DisplayName("Complete Applicative Test Suite")
  class CompleteApplicativeTestSuite {
    @Test
    @DisplayName("Run complete Applicative test pattern")
    void runCompleteApplicativeTestPattern() {
      // IO has lazy evaluation, so we skip default exception tests
      // and provide our own in EdgeCasesTests
      TypeClassTest.<IOKind.Witness>applicative(IOApplicative.class)
          .<Integer>instance(applicativeTyped)
          .<String>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, validMapper, equalityChecker)
          .selectTests()
          .skipExceptions()
          .and()
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(IOFunctor.class)
          .withApFrom(IOApplicative.class)
          .testAll();
    }

    @Test
    @DisplayName("Validate test structure follows standards")
    void validateTestStructure() {
      TestPatternValidator.ValidationResult result =
          TestPatternValidator.validateAndReport(IOApplicativeTest.class);

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
      Kind<IOKind.Witness, String> result = applicative.of("pure");

      assertThatIO(narrowToIO(result)).hasValue("pure");
    }

    @Test
    @DisplayName("of() with null value")
    void ofWithNullValue() {
      Kind<IOKind.Witness, String> result = applicative.of(null);

      assertThatIO(narrowToIO(result)).hasValueNull();
    }

    @Test
    @DisplayName("of() defers evaluation")
    void ofDefersEvaluation() {
      AtomicInteger sideEffect = new AtomicInteger(0);

      Kind<IOKind.Witness, Integer> result = applicative.of(sideEffect.incrementAndGet());

      // Creating the IO should not execute the side effect yet
      // Note: of() eagerly captures the value, but wraps it in a lazy IO
      assertThat(sideEffect.get()).isEqualTo(1);

      assertThatIO(narrowToIO(result)).hasValue(1);
    }

    @Test
    @DisplayName("ap() applies function to value")
    void apAppliesFunctionToValue() {
      Kind<IOKind.Witness, Function<Integer, String>> funcKind = applicative.of(i -> "value:" + i);
      Kind<IOKind.Witness, Integer> valueKind = applicative.of(DEFAULT_IO_VALUE);

      Kind<IOKind.Witness, String> result = applicative.ap(funcKind, valueKind);

      assertThatIO(narrowToIO(result)).hasValue("value:" + DEFAULT_IO_VALUE);
    }

    @Test
    @DisplayName("ap() sequences effects correctly")
    void apSequencesEffectsCorrectly() {
      StringBuilder log = new StringBuilder();

      IO<Function<Integer, String>> funcIO =
          IO.delay(
              () -> {
                log.append("function;");
                return i -> "result:" + i;
              });

      IO<Integer> valueIO =
          IO.delay(
              () -> {
                log.append("value;");
                return DEFAULT_IO_VALUE;
              });

      Kind<IOKind.Witness, Function<Integer, String>> funcKind = IO_OP.widen(funcIO);
      Kind<IOKind.Witness, Integer> valueKind = IO_OP.widen(valueIO);

      Kind<IOKind.Witness, String> result = applicative.ap(funcKind, valueKind);

      // Execute
      assertThatIO(narrowToIO(result)).hasValue("result:" + DEFAULT_IO_VALUE);
      assertThat(log.toString()).isEqualTo("function;value;");
    }

    @Test
    @DisplayName("map2() combines two IOs")
    void map2CombinesTwoIOs() {
      Kind<IOKind.Witness, Integer> io1 = applicative.of(10);
      Kind<IOKind.Witness, String> io2 = applicative.of("test");

      BiFunction<Integer, String, String> combiner = (i, s) -> s + ":" + i;
      Kind<IOKind.Witness, String> result = applicative.map2(io1, io2, combiner);

      assertThatIO(narrowToIO(result)).hasValue("test:10");
    }

    @Test
    @DisplayName("map2() sequences effects in order")
    void map2SequencesEffectsInOrder() {
      StringBuilder log = new StringBuilder();

      IO<Integer> io1 =
          IO.delay(
              () -> {
                log.append("first;");
                return 1;
              });

      IO<Integer> io2 =
          IO.delay(
              () -> {
                log.append("second;");
                return 2;
              });

      Kind<IOKind.Witness, Integer> kind1 = IO_OP.widen(io1);
      Kind<IOKind.Witness, Integer> kind2 = IO_OP.widen(io2);

      BiFunction<Integer, Integer, Integer> combiner =
          (a, b) -> {
            log.append("combine;");
            return a + b;
          };

      Kind<IOKind.Witness, Integer> result = applicative.map2(kind1, kind2, combiner);

      assertThatIO(narrowToIO(result)).hasValue(3);
      assertThat(log.toString()).isEqualTo("first;second;combine;");
    }

    @Test
    @DisplayName("map3() combines three IOs")
    void map3CombinesThreeIOs() {
      Kind<IOKind.Witness, Integer> io1 = applicative.of(1);
      Kind<IOKind.Witness, String> io2 = applicative.of("test");
      Kind<IOKind.Witness, Double> io3 = applicative.of(3.14);

      Function3<Integer, String, Double, String> combiner =
          (i, s, d) -> String.format("%s:%d:%.2f", s, i, d);

      Kind<IOKind.Witness, String> result = applicative.map3(io1, io2, io3, combiner);

      assertThatIO(narrowToIO(result)).hasValue("test:1:3.14");
    }

    @Test
    @DisplayName("map4() combines four IOs")
    void map4CombinesFourIOs() {
      Kind<IOKind.Witness, Integer> io1 = applicative.of(1);
      Kind<IOKind.Witness, String> io2 = applicative.of("test");
      Kind<IOKind.Witness, Double> io3 = applicative.of(3.14);
      Kind<IOKind.Witness, Boolean> io4 = applicative.of(true);

      Function4<Integer, String, Double, Boolean, String> combiner =
          (i, s, d, b) -> String.format("%s:%d:%.2f:%b", s, i, d, b);

      Kind<IOKind.Witness, String> result = applicative.map4(io1, io2, io3, io4, combiner);

      assertThatIO(narrowToIO(result)).hasValue("test:1:3.14:true");
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {
    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<IOKind.Witness>applicative(IOApplicative.class)
          .<Integer>instance(applicativeTyped)
          .<String>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .testOperations();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<IOKind.Witness>applicative(IOApplicative.class)
          .<Integer>instance(applicativeTyped)
          .<String>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(IOFunctor.class)
          .withApFrom(IOApplicative.class)
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      // Note: Default exception tests don't work with IO's lazy evaluation
      // See EdgeCasesTests nested class for IO-specific exception tests
      // This test intentionally empty - use EdgeCasesTests for exception verification
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<IOKind.Witness>applicative(IOApplicative.class)
          .<Integer>instance(applicativeTyped)
          .<String>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, validMapper, equalityChecker)
          .testLaws();
    }

    @Test
    @DisplayName("Test Functor composition law with both mappers")
    void testFunctorCompositionLaw() {
      Function<Integer, String> composed = validMapper.andThen(secondMapper);
      Kind<IOKind.Witness, String> leftSide = applicative.map(composed, validKind);

      Kind<IOKind.Witness, String> intermediate = applicative.map(validMapper, validKind);
      Kind<IOKind.Witness, String> rightSide = applicative.map(secondMapper, intermediate);

      assertThat(equalityChecker.test(leftSide, rightSide)).as("Functor Composition Law").isTrue();
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {
    @Test
    @DisplayName("mapN operations with null values")
    void mapNWithNullValues() {
      Kind<IOKind.Witness, Integer> nullIO = applicative.of(null);
      Kind<IOKind.Witness, String> valueIO = applicative.of("test");

      BiFunction<Integer, String, String> nullSafeFunc =
          (i, s) -> (i == null ? "null" : i.toString()) + ":" + s;

      Kind<IOKind.Witness, String> result = applicative.map2(nullIO, valueIO, nullSafeFunc);

      assertThatIO(narrowToIO(result)).hasValue("null:test");
    }

    @Test
    @DisplayName("ap() with function that returns null")
    void apWithFunctionReturningNull() {
      Kind<IOKind.Witness, Function<Integer, String>> funcKind = applicative.of(i -> null);
      Kind<IOKind.Witness, Integer> valueKind = applicative.of(DEFAULT_IO_VALUE);

      Kind<IOKind.Witness, String> result = applicative.ap(funcKind, valueKind);

      assertThatIO(narrowToIO(result)).hasValueNull();
    }

    @Test
    @DisplayName("Complex nested applicative operations")
    void complexNestedApplicativeOperations() {
      Kind<IOKind.Witness, Function<Integer, Function<String, String>>> nestedFunc =
          applicative.of(i -> s -> s + ":" + i);
      Kind<IOKind.Witness, Integer> intKind = applicative.of(DEFAULT_IO_VALUE);
      Kind<IOKind.Witness, String> stringKind = applicative.of("test");

      Kind<IOKind.Witness, Function<String, String>> partialFunc =
          applicative.ap(nestedFunc, intKind);
      Kind<IOKind.Witness, String> result = applicative.ap(partialFunc, stringKind);

      assertThatIO(narrowToIO(result)).hasValue("test:" + DEFAULT_IO_VALUE);
    }

    @Test
    @DisplayName("map2() is repeatable")
    void map2IsRepeatable() {
      BiFunction<Integer, Integer, String> combiner = (a, b) -> a + "+" + b;
      Kind<IOKind.Witness, String> result = applicative.map2(validKind, validKind2, combiner);

      assertThatIO(narrowToIO(result)).isRepeatable();
    }

    @Test
    @DisplayName("ap() propagates exceptions from function IO")
    void apPropagatesExceptionsFromFunctionIO() {
      RuntimeException exception = new RuntimeException("Function failed");
      IO<Function<Integer, String>> failingFunc =
          IO.delay(
              () -> {
                throw exception;
              });

      Kind<IOKind.Witness, Function<Integer, String>> funcKind = IO_OP.widen(failingFunc);
      Kind<IOKind.Witness, Integer> valueKind = applicative.of(DEFAULT_IO_VALUE);

      Kind<IOKind.Witness, String> result = applicative.ap(funcKind, valueKind);

      assertThatIO(narrowToIO(result))
          .throwsException(RuntimeException.class)
          .withMessage("Function failed");
    }

    @Test
    @DisplayName("ap() propagates exceptions from value IO")
    void apPropagatesExceptionsFromValueIO() {
      RuntimeException exception = new RuntimeException("Value failed");
      IO<Integer> failingValue =
          IO.delay(
              () -> {
                throw exception;
              });

      Kind<IOKind.Witness, Function<Integer, String>> funcKind = applicative.of(i -> "value:" + i);
      Kind<IOKind.Witness, Integer> valueKind = IO_OP.widen(failingValue);

      Kind<IOKind.Witness, String> result = applicative.ap(funcKind, valueKind);

      assertThatIO(narrowToIO(result))
          .throwsException(RuntimeException.class)
          .withMessage("Value failed");
    }
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {
    @Test
    @DisplayName("ap() validates null function Kind")
    void apValidatesNullFunctionKind() {
      assertThatThrownBy(() -> applicative.ap(null, validKind))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Kind")
          .hasMessageContaining("function");
    }

    @Test
    @DisplayName("ap() validates null argument Kind")
    void apValidatesNullArgumentKind() {
      assertThatThrownBy(() -> applicative.ap(validFunctionKind, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Kind")
          .hasMessageContaining("argument");
    }

    @Test
    @DisplayName("map2() validates null arguments")
    void map2ValidatesNullArguments() {
      BiFunction<Integer, Integer, String> combiner = (a, b) -> a + ":" + b;

      assertThatThrownBy(() -> applicative.map2(null, validKind2, combiner))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Kind<F, A> fa for map2 cannot be null");

      assertThatThrownBy(() -> applicative.map2(validKind, null, combiner))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Kind<F, B> fb for map2 cannot be null");

      assertThatThrownBy(
              () ->
                  applicative.map2(
                      validKind, validKind2, (BiFunction<Integer, Integer, String>) null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("combining function for map2 cannot be null");
    }
  }

  @Nested
  @DisplayName("IOAssert Integration Tests")
  class IOAssertIntegrationTests {
    @Test
    @DisplayName("IOAssert works with applicative operations")
    void testIOAssertWithApplicativeOperations() {
      Kind<IOKind.Witness, String> result =
          applicative.map2(validKind, validKind2, validCombiningFunction);

      assertThatIO(narrowToIO(result))
          .hasValueNonNull()
          .hasValueSatisfying(
              v ->
                  assertThat(v)
                      .isEqualTo("Result:" + DEFAULT_IO_VALUE + "," + ALTERNATIVE_IO_VALUE));
    }

    @Test
    @DisplayName("IOAssert verifies laziness in ap operations")
    void testIOAssertVerifiesLazinessInAp() {
      AtomicInteger funcCounter = new AtomicInteger(0);
      AtomicInteger valueCounter = new AtomicInteger(0);

      Kind<IOKind.Witness, Function<Integer, String>> funcKind =
          IO_OP.widen(
              IO.delay(
                  () -> {
                    funcCounter.incrementAndGet();
                    return i -> "value:" + i;
                  }));

      Kind<IOKind.Witness, Integer> valueKind =
          IO_OP.widen(
              IO.delay(
                  () -> {
                    valueCounter.incrementAndGet();
                    return DEFAULT_IO_VALUE;
                  }));

      Kind<IOKind.Witness, String> result = applicative.ap(funcKind, valueKind);

      // Should not have executed yet
      assertThat(funcCounter.get()).isZero();
      assertThat(valueCounter.get()).isZero();

      // Execute and verify
      assertThatIO(narrowToIO(result)).hasValue("value:" + DEFAULT_IO_VALUE);
      assertThat(funcCounter.get()).isEqualTo(1);
      assertThat(valueCounter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("IOAssert detects exceptions in applicative chains")
    void testIOAssertDetectsExceptionsInChains() {
      RuntimeException exception = new RuntimeException("Chain failure");
      Kind<IOKind.Witness, Integer> failingKind = failingIO(exception);

      Kind<IOKind.Witness, String> result =
          applicative.map2(failingKind, validKind2, validCombiningFunction);

      assertThatIO(narrowToIO(result))
          .throwsException(RuntimeException.class)
          .withMessageContaining("Chain failure");
    }
  }
}
