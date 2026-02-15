// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.validated.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ValidatedFunctor Complete Test Suite")
class ValidatedFunctorTest extends ValidatedTestBase {

  private Functor<ValidatedKind.Witness<String>> functor;
  private Semigroup<String> stringSemigroup;

  @BeforeEach
  void setUpFunctor() {
    // Use standard Semigroups.first() as a simple semigroup
    // Note: For Functor operations, the semigroup isn't used, but we need one for construction
    stringSemigroup = Semigroups.first();
    functor = ValidatedMonad.instance(stringSemigroup);
  }

  @Nested
  @DisplayName("Complete Test Suite")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete Functor test pattern")
    void runCompleteFunctorTestPattern() {
      TypeClassTest.<ValidatedKind.Witness<String>>functor(ValidatedMonad.class)
          .<Integer>instance(functor)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .withSecondMapper(secondMapper)
          .withEqualityChecker(equalityChecker)
          .testAll();
    }
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("Map transforms Valid value")
    void mapTransformsValidValue() {
      Validated<String, Integer> valid = Validated.valid(DEFAULT_VALID_VALUE);
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.widen(valid);

      Kind<ValidatedKind.Witness<String>, String> result = functor.map(validMapper, kind);

      Validated<String, String> narrowed = narrowToValidated(result);
      assertThatValidated(narrowed).isValid().hasValue("42").hasValueOfType(String.class);
    }

    @Test
    @DisplayName("Map preserves Invalid unchanged")
    void mapPreservesInvalidUnchanged() {
      Validated<String, Integer> invalid = Validated.invalid(DEFAULT_ERROR);
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.widen(invalid);

      Kind<ValidatedKind.Witness<String>, String> result = functor.map(validMapper, kind);

      Validated<String, String> narrowed = narrowToValidated(result);
      assertThatValidated(narrowed)
          .isInvalid()
          .hasError(DEFAULT_ERROR)
          .hasErrorOfType(String.class);
    }

    @Test
    @DisplayName("Map chains correctly")
    void mapChainsCorrectly() {
      Kind<ValidatedKind.Witness<String>, Integer> kind = validKind(DEFAULT_VALID_VALUE);

      Kind<ValidatedKind.Witness<String>, Integer> step1 = functor.map(n -> n * 2, kind);
      Kind<ValidatedKind.Witness<String>, String> step2 = functor.map(validMapper, step1);

      Validated<String, String> result = narrowToValidated(step2);
      assertThatValidated(result).isValid().hasValue("84");
    }
  }

  @Nested
  @DisplayName("Individual Component Tests")
  class IndividualComponentTests {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<ValidatedKind.Witness<String>>functor(ValidatedMonad.class)
          .<Integer>instance(functor)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .testOperations();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<ValidatedKind.Witness<String>>functor(ValidatedMonad.class)
          .<Integer>instance(functor)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      TypeClassTest.<ValidatedKind.Witness<String>>functor(ValidatedMonad.class)
          .<Integer>instance(functor)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .testExceptions();
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<ValidatedKind.Witness<String>>functor(ValidatedMonad.class)
          .<Integer>instance(functor)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .withSecondMapper(secondMapper)
          .withEqualityChecker(equalityChecker)
          .testLaws();
    }
  }

  @Nested
  @DisplayName("Validation Configuration Tests")
  class ValidationConfigurationTests {

    @Test
    @DisplayName("Test with standard validation")
    void testWithStandardValidation() {
      TypeClassTest.<ValidatedKind.Witness<String>>functor(ValidatedMonad.class)
          .<Integer>instance(functor)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .testValidations();
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Map with null-returning function on Valid throws exception")
    void mapWithNullReturningFunctionOnValidThrowsException() {
      Kind<ValidatedKind.Witness<String>, Integer> kind = validKind(DEFAULT_VALID_VALUE);

      // Valid.map validates that the function doesn't return null
      assertThatThrownBy(() -> functor.map(n -> null, kind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Function fn in Valid.map returned null");
    }

    @Test
    @DisplayName("Map preserves Invalid with different error types")
    void mapPreservesInvalidWithDifferentErrorTypes() {
      Validated<String, Integer> invalid = Validated.invalid("original-error");
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.widen(invalid);

      Kind<ValidatedKind.Witness<String>, String> result = functor.map(validMapper, kind);

      Validated<String, String> narrowed = narrowToValidated(result);
      assertThatValidated(narrowed).isInvalid().hasError("original-error");
    }
  }
}
