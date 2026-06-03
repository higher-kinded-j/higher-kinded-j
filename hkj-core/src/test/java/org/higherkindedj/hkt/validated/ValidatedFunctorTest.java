// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("ValidatedFunctor")
class ValidatedFunctorTest extends ValidatedTestBase {

  private Functor<ValidatedKind.Witness<String>> functor;

  @BeforeEach
  void setUpFunctor() {
    functor = Instances.validated(Semigroups.first());
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.validated.ValidatedLawFixtures#kinds")
    void identity(String label, Kind<ValidatedKind.Witness<String>, Integer> fa) {
      FunctorLaws.assertIdentity(functor, fa, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.validated.ValidatedLawFixtures#kinds")
    void composition(String label, Kind<ValidatedKind.Witness<String>, Integer> fa) {
      FunctorLaws.assertComposition(functor, fa, validMapper, secondMapper, equalityChecker);
    }
  }

  @Test
  @DisplayName("Functor contract — operations, validations & exceptions (laws verified above)")
  void functorContract() {
    TypeClassContract.<ValidatedKind.Witness<String>>functor(ValidatedMonad.class)
        .<Integer>instance(functor)
        .<String>withKind(validKind)
        .withMapper(validMapper)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS, Category.EXCEPTIONS);
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("Map transforms Valid value")
    void mapTransformsValidValue() {
      Kind<ValidatedKind.Witness<String>, Integer> kind = validKind(DEFAULT_VALID_VALUE);

      Kind<ValidatedKind.Witness<String>, String> result = functor.map(validMapper, kind);
      assertThatValidated(result).isValid().hasValue("42").hasValueOfType(String.class);
    }

    @Test
    @DisplayName("Map preserves Invalid unchanged")
    void mapPreservesInvalidUnchanged() {
      Kind<ValidatedKind.Witness<String>, Integer> kind = invalidKind(DEFAULT_ERROR);

      Kind<ValidatedKind.Witness<String>, String> result = functor.map(validMapper, kind);
      assertThatValidated(result).isInvalid().hasError(DEFAULT_ERROR).hasErrorOfType(String.class);
    }

    @Test
    @DisplayName("Map chains correctly")
    void mapChainsCorrectly() {
      Kind<ValidatedKind.Witness<String>, Integer> kind = validKind(DEFAULT_VALID_VALUE);

      Kind<ValidatedKind.Witness<String>, Integer> step1 = functor.map(n -> n * 2, kind);
      Kind<ValidatedKind.Witness<String>, String> step2 = functor.map(validMapper, step1);
      assertThatValidated(step2).isValid().hasValue("84");
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
      assertThatThrownBy(() -> functor.map(_ -> null, kind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Function fn in map returned null");
    }

    @Test
    @DisplayName("Map preserves Invalid with different error types")
    void mapPreservesInvalidWithDifferentErrorTypes() {
      Kind<ValidatedKind.Witness<String>, Integer> kind = invalidKind("original-error");

      Kind<ValidatedKind.Witness<String>, String> result = functor.map(validMapper, kind);
      assertThatValidated(result).isInvalid().hasError("original-error");
    }
  }
}
