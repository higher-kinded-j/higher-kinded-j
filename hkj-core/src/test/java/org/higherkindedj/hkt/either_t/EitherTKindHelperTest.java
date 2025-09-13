// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.either_t.EitherTKindHelper.EITHER_T;
import static org.higherkindedj.hkt.util.ErrorHandling.INVALID_KIND_TYPE_TEMPLATE;
import static org.higherkindedj.hkt.util.ErrorHandling.NULL_KIND_TEMPLATE;
import static org.higherkindedj.hkt.util.ErrorHandling.NULL_WIDEN_INPUT_TEMPLATE;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherTKindHelper Tests (F=OptionalKind.Witness)")
class EitherTKindHelperTest {

  private static final String TYPE_NAME = "EitherT";

  private Monad<OptionalKind.Witness> outerMonad;

  @BeforeEach
  void setUp() {
    outerMonad = OptionalMonad.INSTANCE;
  }

  // Helper to create a concrete EitherT with OptionalKind.Witness as F
  private <L, R> EitherT<OptionalKind.Witness, L, R> createEitherT(Either<L, R> either) {
    return EitherT.fromEither(outerMonad, either);
  }

  @Nested
  @DisplayName("wrap() tests")
  class WrapTests {
    @Test
    @DisplayName("should wrap a non-null EitherT (Right) into an EitherTKind")
    void widen_nonNullEitherTRight_shouldReturnEitherTKind() {
      EitherT<OptionalKind.Witness, String, Integer> concreteEitherT =
          createEitherT(Either.right(123));
      Kind<EitherTKind.Witness<OptionalKind.Witness, String>, Integer> wrapped =
          EITHER_T.widen(concreteEitherT);

      assertThat(wrapped).isNotNull().isInstanceOf(EitherTKind.class);
      assertThat(EITHER_T.narrow(wrapped)).isSameAs(concreteEitherT);
    }

    @Test
    @DisplayName("should throw NullPointerException when wrapping null")
    void widen_nullEitherT_shouldThrowNullPointerException() {
      assertThatThrownBy(() -> EITHER_T.widen(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage(NULL_WIDEN_INPUT_TEMPLATE.formatted(TYPE_NAME));
    }
  }

  @Nested
  @DisplayName("unwrap() tests")
  class UnwrapTests {
    @Test
    @DisplayName("should unwrap a valid EitherTKind (Right) to the original EitherT instance")
    void narrow_validKindRight_shouldReturnEitherT() {
      EitherT<OptionalKind.Witness, String, Integer> originalEitherT =
          createEitherT(Either.right(456));
      var wrappedKind = EITHER_T.widen(originalEitherT);

      EitherT<OptionalKind.Witness, String, Integer> unwrappedEitherT =
          EITHER_T.narrow(wrappedKind);

      assertThat(unwrappedEitherT).isSameAs(originalEitherT);
    }

    @Test
    @DisplayName("should throw KindUnwrapException when unwrapping null")
    void narrow_nullKind_shouldThrowKindUnwrapException() {
      assertThatThrownBy(() -> EITHER_T.narrow(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage(NULL_KIND_TEMPLATE.formatted(TYPE_NAME));
    }

    // Dummy Kind for testing invalid type unwrap
    private static class OtherKind<F_Witness, L, R>
        implements Kind<OtherKind<F_Witness, L, ?>, R> {}

    @Test
    @DisplayName("should throw KindUnwrapException when unwrapping an incorrect Kind type")
    void narrow_incorrectKindType_shouldThrowKindUnwrapException() {
      OtherKind<OptionalKind.Witness, String, Integer> incorrectKind = new OtherKind<>();

      @SuppressWarnings({"unchecked", "rawtypes"})
      Kind<EitherTKind.Witness<OptionalKind.Witness, String>, Integer> kindToTest =
          (Kind<EitherTKind.Witness<OptionalKind.Witness, String>, Integer>) (Kind) incorrectKind;

      assertThatThrownBy(() -> EITHER_T.narrow(kindToTest))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage(
              INVALID_KIND_TYPE_TEMPLATE.formatted(TYPE_NAME, incorrectKind.getClass().getName()));
    }
  }
}
