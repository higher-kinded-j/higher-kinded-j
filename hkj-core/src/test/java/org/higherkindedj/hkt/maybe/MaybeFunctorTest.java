// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.maybe.MaybeAssert.assertThatMaybe;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MaybeFunctor Complete Test Suite")
class MaybeFunctorTest extends MaybeTestBase {
  private MaybeFunctor functor;
  private Functor<MaybeKind.Witness> functorTyped;

  @BeforeEach
  void setUpFunctor() {
    functor = new MaybeFunctor();
    functorTyped = functor;
  }

  @Nested
  @DisplayName("Complete Functor Test Suite")
  class CompleteFunctorTestSuite {
    @Test
    @DisplayName("Run complete Functor test pattern")
    void runCompleteFunctorTestPattern() {
      TypeClassTest.<MaybeKind.Witness>functor(MaybeFunctor.class)
          .<Integer>instance(functorTyped)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .withSecondMapper(secondMapper)
          .withEqualityChecker(equalityChecker)
          .testAll();
    }

    @Test
    @DisplayName("Validate test structure follows standards")
    void validateTestStructure() {
      TestPatternValidator.ValidationResult result =
          TestPatternValidator.validateAndReport(MaybeFunctorTest.class);

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
    @DisplayName("map() on Just applies function")
    void mapOnJustAppliesFunction() {
      Kind<MaybeKind.Witness, String> result = functor.map(validMapper, validKind);

      assertThatMaybe(narrowToMaybe(result)).isJust().hasValue(String.valueOf(DEFAULT_JUST_VALUE));
    }

    @Test
    @DisplayName("map() on Nothing returns Nothing")
    void mapOnNothingReturnsNothing() {
      Kind<MaybeKind.Witness, Integer> nothingKind = nothingKind();

      Kind<MaybeKind.Witness, String> result = functor.map(validMapper, nothingKind);

      assertThatMaybe(narrowToMaybe(result)).isNothing();
    }

    @Test
    @DisplayName("map() with null-returning mapper returns Nothing")
    void mapWithNullReturningMapper() {
      Function<Integer, String> nullMapper = i -> null;

      Kind<MaybeKind.Witness, String> result = functor.map(nullMapper, validKind);

      assertThatMaybe(narrowToMaybe(result)).isNothing();
    }

    @Test
    @DisplayName("map() chains multiple transformations")
    void mapChainsMultipleTransformations() {
      Kind<MaybeKind.Witness, String> result =
          functor.map(validMapper.andThen(String::toUpperCase), validKind);

      assertThatMaybe(narrowToMaybe(result)).isJust().hasValue(String.valueOf(DEFAULT_JUST_VALUE));
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {
    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<MaybeKind.Witness>functor(MaybeFunctor.class)
          .<Integer>instance(functorTyped)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .testOperations();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<MaybeKind.Witness>functor(MaybeFunctor.class)
          .<Integer>instance(functorTyped)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      TypeClassTest.<MaybeKind.Witness>functor(MaybeFunctor.class)
          .<Integer>instance(functorTyped)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .testExceptions();
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<MaybeKind.Witness>functor(MaybeFunctor.class)
          .<Integer>instance(functorTyped)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .withSecondMapper(secondMapper)
          .withEqualityChecker(equalityChecker)
          .testLaws();
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {
    @Test
    @DisplayName("map() preserves Nothing through chains")
    void mapPreservesNothingThroughChains() {
      Kind<MaybeKind.Witness, Integer> nothing = nothingKind();

      Function<Integer, Integer> doubleFunc = i -> i * 2;
      Function<Integer, String> stringFunc = i -> "Value: " + i;

      Kind<MaybeKind.Witness, Integer> intermediate = functor.map(doubleFunc, nothing);
      Kind<MaybeKind.Witness, String> result = functor.map(stringFunc, intermediate);

      assertThatMaybe(narrowToMaybe(result)).isNothing();
    }

    @Test
    @DisplayName("map() with complex transformations")
    void mapWithComplexTransformations() {
      Function<Integer, String> complexMapper =
          i -> {
            if (i < 0) return "negative";
            if (i == 0) return "zero";
            return "positive:" + i;
          };

      Kind<MaybeKind.Witness, String> result = functor.map(complexMapper, validKind);
      assertThatMaybe(narrowToMaybe(result)).isJust().hasValue("positive:" + DEFAULT_JUST_VALUE);
    }

    @Test
    @DisplayName("map() with identity is idempotent")
    void mapWithIdentityIsIdempotent() {
      Function<Integer, Integer> identity = i -> i;

      Kind<MaybeKind.Witness, Integer> result = functor.map(identity, validKind);

      assertThat(narrowToMaybe(result)).isEqualTo(narrowToMaybe(validKind));
    }
  }
}
