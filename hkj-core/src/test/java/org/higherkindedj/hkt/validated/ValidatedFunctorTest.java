// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ValidatedFunctor Complete Test Suite")
class ValidatedFunctorTest
    extends TypeClassTestBase<ValidatedKind.Witness<String>, Integer, String> {

  private Functor<ValidatedKind.Witness<String>> functor;
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
      Validated<String, Integer> valid = Validated.valid(42);
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.widen(valid);

      Kind<ValidatedKind.Witness<String>, String> result = functor.map(Object::toString, kind);

      Validated<String, String> narrowed = VALIDATED.narrow(result);
      assertThat(narrowed.isValid()).isTrue();
      assertThat(narrowed.get()).isEqualTo("42");
    }

    @Test
    @DisplayName("Map preserves Invalid unchanged")
    void mapPreservesInvalidUnchanged() {
      Validated<String, Integer> invalid = Validated.invalid("error");
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.widen(invalid);

      Kind<ValidatedKind.Witness<String>, String> result = functor.map(Object::toString, kind);

      Validated<String, String> narrowed = VALIDATED.narrow(result);
      assertThat(narrowed.isInvalid()).isTrue();
      assertThat(narrowed.getError()).isEqualTo("error");
    }

    @Test
    @DisplayName("Map chains correctly")
    void mapChainsCorrectly() {
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.widen(Validated.valid(42));

      Kind<ValidatedKind.Witness<String>, Integer> step1 = functor.map(n -> n * 2, kind);
      Kind<ValidatedKind.Witness<String>, String> step2 = functor.map(Object::toString, step1);

      Validated<String, String> result = VALIDATED.narrow(step2);
      assertThat(result.isValid()).isTrue();
      assertThat(result.get()).isEqualTo("84");
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
      Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.widen(Validated.valid(42));

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

      Kind<ValidatedKind.Witness<String>, String> result = functor.map(Object::toString, kind);

      Validated<String, String> narrowed = VALIDATED.narrow(result);
      assertThat(narrowed.isInvalid()).isTrue();
      assertThat(narrowed.getError()).isEqualTo("original-error");
    }
  }
}
