// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ValidatedMonad Complete Test Suite")
class ValidatedMonadTest extends TypeClassTestBase<ValidatedKind.Witness<String>, Integer, String> {

  private ValidatedMonad<String> monad;
  private Semigroup<String> stringSemigroup;

  @Override
  protected Kind<ValidatedKind.Witness<String>, Integer> createValidKind() {
    return VALIDATED.widen(Validated.valid(42));
  }

  @Override
  protected Kind<ValidatedKind.Witness<String>, Integer> createValidKind2() {
    return VALIDATED.widen(Validated.valid(24));
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return Object::toString;
  }

  @Override
  protected Function<Integer, Kind<ValidatedKind.Witness<String>, String>> createValidFlatMapper() {
    return n -> VALIDATED.widen(Validated.valid("Value: " + n));
  }

  @Override
  protected Kind<ValidatedKind.Witness<String>, Function<Integer, String>>
      createValidFunctionKind() {
    Function<Integer, String> fn = n -> "Result: " + n;
    return VALIDATED.widen(Validated.valid(fn));
  }

  @Override
  protected BiFunction<Integer, Integer, String> createValidCombiningFunction() {
    return (a, b) -> a + " and " + b;
  }

  @Override
  protected Integer createTestValue() {
    return 100;
  }

  @Override
  protected Function<Integer, Kind<ValidatedKind.Witness<String>, String>> createTestFunction() {
    return n -> VALIDATED.widen(Validated.valid("Test: " + n));
  }

  @Override
  protected Function<String, Kind<ValidatedKind.Witness<String>, String>> createChainFunction() {
    return s -> VALIDATED.widen(Validated.valid(s + " [chained]"));
  }

  @Override
  protected BiPredicate<
          Kind<ValidatedKind.Witness<String>, ?>, Kind<ValidatedKind.Witness<String>, ?>>
      createEqualityChecker() {
    return (k1, k2) -> {
      Validated<String, ?> v1 = VALIDATED.narrow(k1);
      Validated<String, ?> v2 = VALIDATED.narrow(k2);
      return v1.equals(v2);
    };
  }

  @BeforeEach
  void setUpMonad() {
    stringSemigroup = (a, b) -> a + ", " + b;
    monad = ValidatedMonad.instance(stringSemigroup);
  }

  @Nested
  @DisplayName("Complete Test Suite")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete Monad test pattern")
    void runCompleteMonadTestPattern() {
      TypeClassTest.<ValidatedKind.Witness<String>>monad(ValidatedMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .testAll();
    }
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("FlatMap chains Valid computations")
    void flatMapChainsValidComputations() {
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.widen(Validated.valid(42));
      Function<Integer, Kind<ValidatedKind.Witness<String>, String>> fn =
          n -> VALIDATED.widen(Validated.valid("Value: " + n));

      Kind<ValidatedKind.Witness<String>, String> result = monad.flatMap(fn, kind);

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.isValid()).isTrue();
      assertThat(validated.get()).isEqualTo("Value: 42");
    }

    @Test
    @DisplayName("FlatMap propagates Invalid from source")
    void flatMapPropagatesInvalidFromSource() {
      Kind<ValidatedKind.Witness<String>, Integer> kind =
          VALIDATED.widen(Validated.invalid("source-error"));
      Function<Integer, Kind<ValidatedKind.Witness<String>, String>> fn =
          n -> VALIDATED.widen(Validated.valid("Value: " + n));

      Kind<ValidatedKind.Witness<String>, String> result = monad.flatMap(fn, kind);

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("source-error");
    }

    @Test
    @DisplayName("FlatMap uses result from function on Valid")
    void flatMapUsesResultFromFunctionOnValid() {
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.widen(Validated.valid(42));
      Function<Integer, Kind<ValidatedKind.Witness<String>, String>> fn =
          n -> VALIDATED.widen(Validated.invalid("function-error"));

      Kind<ValidatedKind.Witness<String>, String> result = monad.flatMap(fn, kind);

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("function-error");
    }

    @Test
    @DisplayName("FlatMap chains multiple operations")
    void flatMapChainsMultipleOperations() {
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.widen(Validated.valid(10));

      Kind<ValidatedKind.Witness<String>, Integer> step1 =
          monad.flatMap(n -> VALIDATED.widen(Validated.valid(n * 2)), kind);
      Kind<ValidatedKind.Witness<String>, Integer> step2 =
          monad.flatMap(n -> VALIDATED.widen(Validated.valid(n + 5)), step1);
      Kind<ValidatedKind.Witness<String>, String> step3 =
          monad.flatMap(n -> VALIDATED.widen(Validated.valid("Result: " + n)), step2);

      Validated<String, String> result = VALIDATED.narrow(step3);
      assertThat(result.isValid()).isTrue();
      assertThat(result.get()).isEqualTo("Result: 25");
    }
  }

  @Nested
  @DisplayName("Individual Component Tests")
  class IndividualComponentTests {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<ValidatedKind.Witness<String>>monad(ValidatedMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .testOperations();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<ValidatedKind.Witness<String>>monad(ValidatedMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      TypeClassTest.<ValidatedKind.Witness<String>>monad(ValidatedMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .testExceptions();
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<ValidatedKind.Witness<String>>monad(ValidatedMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .testLaws();
    }
  }

  @Nested
  @DisplayName("Validation Configuration Tests")
  class ValidationConfigurationTests {

    @Test
    @DisplayName("Test with inheritance-based validation")
    void testWithInheritanceBasedValidation() {
      TypeClassTest.<ValidatedKind.Witness<String>>monad(ValidatedMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(ValidatedMonad.class)
          .withApFrom(ValidatedMonad.class)
          .withFlatMapFrom(ValidatedMonad.class)
          .testValidations();
    }
  }

  @Nested
  @DisplayName("Law Verification Tests")
  class LawVerificationTests {

    @Test
    @DisplayName("Left identity law holds")
    void leftIdentityLawHolds() {
      Integer value = 42;
      Function<Integer, Kind<ValidatedKind.Witness<String>, String>> fn =
          n -> VALIDATED.widen(Validated.valid("Value: " + n));

      Kind<ValidatedKind.Witness<String>, Integer> ofValue = monad.of(value);
      Kind<ValidatedKind.Witness<String>, String> leftSide = monad.flatMap(fn, ofValue);
      Kind<ValidatedKind.Witness<String>, String> rightSide = fn.apply(value);

      assertThat(equalityChecker.test(leftSide, rightSide)).isTrue();
    }

    @Test
    @DisplayName("Right identity law holds")
    void rightIdentityLawHolds() {
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.widen(Validated.valid(42));
      Function<Integer, Kind<ValidatedKind.Witness<String>, Integer>> ofFunc = monad::of;

      Kind<ValidatedKind.Witness<String>, Integer> result = monad.flatMap(ofFunc, kind);

      assertThat(equalityChecker.test(result, kind)).isTrue();
    }

    @Test
    @DisplayName("Associativity law holds")
    void associativityLawHolds() {
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.widen(Validated.valid(10));
      Function<Integer, Kind<ValidatedKind.Witness<String>, String>> f =
          n -> VALIDATED.widen(Validated.valid("Step1: " + n));
      Function<String, Kind<ValidatedKind.Witness<String>, String>> g =
          s -> VALIDATED.widen(Validated.valid(s + " Step2"));

      // Left side: flatMap(flatMap(m, f), g)
      Kind<ValidatedKind.Witness<String>, String> innerFlatMap = monad.flatMap(f, kind);
      Kind<ValidatedKind.Witness<String>, String> leftSide = monad.flatMap(g, innerFlatMap);

      // Right side: flatMap(m, a -> flatMap(f(a), g))
      Function<Integer, Kind<ValidatedKind.Witness<String>, String>> composed =
          n -> monad.flatMap(g, f.apply(n));
      Kind<ValidatedKind.Witness<String>, String> rightSide = monad.flatMap(composed, kind);

      assertThat(equalityChecker.test(leftSide, rightSide)).isTrue();
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("FlatMap with function returning null Kind fails appropriately")
    void flatMapWithFunctionReturningNullKindFailsAppropriately() {
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.widen(Validated.valid(42));
      Function<Integer, Kind<ValidatedKind.Witness<String>, String>> nullReturningFn = n -> null;

      org.junit.jupiter.api.Assertions.assertThrows(
          KindUnwrapException.class, () -> monad.flatMap(nullReturningFn, kind));
    }

    @Test
    @DisplayName("Ap still accumulates errors despite flatMap fail-fast behaviour")
    void apStillAccumulatesErrorsDespiteFlatMapFailFastBehaviour() {
      Kind<ValidatedKind.Witness<String>, Function<Integer, String>> fnKind =
          VALIDATED.widen(Validated.invalid("error1"));
      Kind<ValidatedKind.Witness<String>, Integer> valueKind =
          VALIDATED.widen(Validated.invalid("error2"));

      Kind<ValidatedKind.Witness<String>, String> result = monad.ap(fnKind, valueKind);

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("error1, error2");
    }
  }
}
