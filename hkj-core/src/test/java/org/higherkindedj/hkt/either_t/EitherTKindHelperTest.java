// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.either_t.EitherTKindHelper.EITHER_T;
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

  private <L, R> EitherT<OptionalKind.Witness, L, R> createEitherT(Either<L, R> either) {
    return EitherT.fromEither(outerMonad, either);
  }

  @Nested
  @DisplayName("Widen Tests")
  class WidenTests {

    @Test
    @DisplayName("widen should convert non-null EitherT (Right) to EitherTKind")
    void widen_nonNullEitherTRight_shouldReturnEitherTKind() {
      EitherT<OptionalKind.Witness, String, Integer> concreteEitherT =
          createEitherT(Either.right(123));
      Kind<EitherTKind.Witness<OptionalKind.Witness, String>, Integer> wrapped =
          EITHER_T.widen(concreteEitherT);

      assertThat(wrapped).isNotNull().isInstanceOf(EitherTKind.class);
      assertThat(EITHER_T.narrow(wrapped)).isSameAs(concreteEitherT);
    }

    @Test
    @DisplayName("widen should convert non-null EitherT (Left) to EitherTKind")
    void widen_nonNullEitherTLeft_shouldReturnEitherTKind() {
      EitherT<OptionalKind.Witness, String, Integer> concreteEitherT =
          createEitherT(Either.left("error"));
      Kind<EitherTKind.Witness<OptionalKind.Witness, String>, Integer> wrapped =
          EITHER_T.widen(concreteEitherT);

      assertThat(wrapped).isNotNull().isInstanceOf(EitherTKind.class);
      assertThat(EITHER_T.narrow(wrapped)).isSameAs(concreteEitherT);
    }

    @Test
    @DisplayName("widen should throw NullPointerException when given null")
    void widen_nullEitherT_shouldThrowNullPointerException() {
      assertThatThrownBy(() -> EITHER_T.widen(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage(NULL_WIDEN_INPUT_TEMPLATE.formatted(TYPE_NAME));
    }
  }

  @Nested
  @DisplayName("Narrow Tests")
  class NarrowTests {

    @Test
    @DisplayName("narrow should unwrap valid EitherTKind (Right) to original EitherT instance")
    void narrow_validKindRight_shouldReturnEitherT() {
      EitherT<OptionalKind.Witness, String, Integer> originalEitherT =
          createEitherT(Either.right(456));
      var wrappedKind = EITHER_T.widen(originalEitherT);

      EitherT<OptionalKind.Witness, String, Integer> unwrappedEitherT =
          EITHER_T.narrow(wrappedKind);

      assertThat(unwrappedEitherT).isSameAs(originalEitherT);
    }

    @Test
    @DisplayName("narrow should unwrap valid EitherTKind (Left) to original EitherT instance")
    void narrow_validKindLeft_shouldReturnEitherT() {
      EitherT<OptionalKind.Witness, String, Integer> originalEitherT =
          createEitherT(Either.left("error"));
      var wrappedKind = EITHER_T.widen(originalEitherT);

      EitherT<OptionalKind.Witness, String, Integer> unwrappedEitherT =
          EITHER_T.narrow(wrappedKind);

      assertThat(unwrappedEitherT).isSameAs(originalEitherT);
    }

    @Test
    @DisplayName("narrow should throw KindUnwrapException when given null")
    void narrow_nullKind_shouldThrowKindUnwrapException() {
      assertThatThrownBy(() -> EITHER_T.narrow(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage(NULL_KIND_TEMPLATE.formatted(TYPE_NAME));
    }

    @Test
    @DisplayName("narrow should throw KindUnwrapException when given incorrect Kind type")
    void narrow_incorrectKindType_shouldThrowKindUnwrapException() {
      OtherKind<OptionalKind.Witness, String, Integer> incorrectKind = new OtherKind<>();

      @SuppressWarnings({"unchecked", "rawtypes"})
      Kind<EitherTKind.Witness<OptionalKind.Witness, String>, Integer> kindToTest =
          (Kind<EitherTKind.Witness<OptionalKind.Witness, String>, Integer>) (Kind) incorrectKind;

      assertThatThrownBy(() -> EITHER_T.narrow(kindToTest))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage("Kind instance cannot be narrowed to EitherT");
    }
  }

  @Nested
  @DisplayName("Round-Trip Tests")
  class RoundTripTests {

    @Test
    @DisplayName("widen then narrow should preserve identity for Right")
    void roundTrip_right_shouldPreserveIdentity() {
      EitherT<OptionalKind.Witness, String, Integer> original = createEitherT(Either.right(789));

      Kind<EitherTKind.Witness<OptionalKind.Witness, String>, Integer> widened =
          EITHER_T.widen(original);
      EitherT<OptionalKind.Witness, String, Integer> narrowed = EITHER_T.narrow(widened);

      assertThat(narrowed).isSameAs(original);
    }

    @Test
    @DisplayName("widen then narrow should preserve identity for Left")
    void roundTrip_left_shouldPreserveIdentity() {
      EitherT<OptionalKind.Witness, String, Integer> original =
          createEitherT(Either.left("failure"));

      Kind<EitherTKind.Witness<OptionalKind.Witness, String>, Integer> widened =
          EITHER_T.widen(original);
      EitherT<OptionalKind.Witness, String, Integer> narrowed = EITHER_T.narrow(widened);

      assertThat(narrowed).isSameAs(original);
    }

    @Test
    @DisplayName("multiple round-trips should preserve identity")
    void multipleRoundTrips_shouldPreserveIdentity() {
      EitherT<OptionalKind.Witness, String, Integer> original = createEitherT(Either.right(999));

      EitherT<OptionalKind.Witness, String, Integer> current = original;
      for (int i = 0; i < 3; i++) {
        Kind<EitherTKind.Witness<OptionalKind.Witness, String>, Integer> widened =
            EITHER_T.widen(current);
        current = EITHER_T.narrow(widened);
      }

      assertThat(current).isSameAs(original);
    }
  }

  // Dummy Kind for testing invalid type unwrap
  private static class OtherKind<F_Witness, L, R> implements Kind<OtherKind<F_Witness, L, ?>, R> {}
}
