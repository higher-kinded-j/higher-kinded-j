// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.reader_t.ReaderTKindHelper.READER_T;
import static org.higherkindedj.hkt.util.ErrorHandling.NULL_KIND_TEMPLATE;
import static org.higherkindedj.hkt.util.ErrorHandling.NULL_WIDEN_INPUT_TEMPLATE;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ReaderTKindHelper Tests (F=OptionalKind.Witness, R=String)")
class ReaderTKindHelperTest {

  private static final String TYPE_NAME = "ReaderT";

  private Monad<OptionalKind.Witness> outerMonad;

  @BeforeEach
  void setUp() {
    outerMonad = OptionalMonad.INSTANCE;
  }

  private <A> ReaderT<OptionalKind.Witness, String, A> createReaderT(A value) {
    return ReaderT.reader(outerMonad, env -> value);
  }

  @Nested
  @DisplayName("Widen Tests")
  class WidenTests {

    @Test
    @DisplayName("widen should convert non-null ReaderT to ReaderTKind")
    void widen_nonNullReaderT_shouldReturnReaderTKind() {
      ReaderT<OptionalKind.Witness, String, Integer> concreteReaderT = createReaderT(123);
      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> wrapped =
          READER_T.widen(concreteReaderT);

      assertThat(wrapped).isNotNull().isInstanceOf(ReaderTKind.class);
      assertThat(READER_T.narrow(wrapped)).isSameAs(concreteReaderT);
    }

    @Test
    @DisplayName("widen should preserve ReaderT function")
    void widen_shouldPreserveFunction() {
      ReaderT<OptionalKind.Witness, String, Integer> concreteReaderT = createReaderT(456);
      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> wrapped =
          READER_T.widen(concreteReaderT);

      ReaderT<OptionalKind.Witness, String, Integer> narrowed = READER_T.narrow(wrapped);
      assertThat(narrowed.run()).isSameAs(concreteReaderT.run());
    }

    @Test
    @DisplayName("widen should throw NullPointerException when given null")
    void widen_nullReaderT_shouldThrowNullPointerException() {
      assertThatThrownBy(() -> READER_T.widen(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage(NULL_WIDEN_INPUT_TEMPLATE.formatted(TYPE_NAME));
    }
  }

  @Nested
  @DisplayName("Narrow Tests")
  class NarrowTests {

    @Test
    @DisplayName("narrow should unwrap valid ReaderTKind to original ReaderT instance")
    void narrow_validKind_shouldReturnReaderT() {
      ReaderT<OptionalKind.Witness, String, Integer> originalReaderT = createReaderT(789);
      var wrappedKind = READER_T.widen(originalReaderT);

      ReaderT<OptionalKind.Witness, String, Integer> unwrappedReaderT =
          READER_T.narrow(wrappedKind);

      assertThat(unwrappedReaderT).isSameAs(originalReaderT);
    }

    @Test
    @DisplayName("narrow should preserve ReaderT function")
    void narrow_shouldPreserveFunction() {
      ReaderT<OptionalKind.Witness, String, Integer> originalReaderT = createReaderT(999);
      var wrappedKind = READER_T.widen(originalReaderT);

      ReaderT<OptionalKind.Witness, String, Integer> unwrappedReaderT =
          READER_T.narrow(wrappedKind);

      assertThat(unwrappedReaderT.run()).isSameAs(originalReaderT.run());
    }

    @Test
    @DisplayName("narrow should throw KindUnwrapException when given null")
    void narrow_nullKind_shouldThrowKindUnwrapException() {
      assertThatThrownBy(() -> READER_T.narrow(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage(NULL_KIND_TEMPLATE.formatted(TYPE_NAME));
    }

    @Test
    @DisplayName("narrow should throw KindUnwrapException when given incorrect Kind type")
    void narrow_incorrectKindType_shouldThrowKindUnwrapException() {
      OtherKind<OptionalKind.Witness, String, Integer> incorrectKind = new OtherKind<>();

      @SuppressWarnings({"unchecked", "rawtypes"})
      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> kindToTest =
          (Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer>) (Kind) incorrectKind;

      assertThatThrownBy(() -> READER_T.narrow(kindToTest))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage("Kind instance cannot be narrowed to " + ReaderT.class.getSimpleName());
    }
  }

  @Nested
  @DisplayName("Round-Trip Tests")
  class RoundTripTests {

    @Test
    @DisplayName("widen then narrow should preserve identity")
    void roundTrip_shouldPreserveIdentity() {
      ReaderT<OptionalKind.Witness, String, Integer> original = createReaderT(111);

      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> widened =
          READER_T.widen(original);
      ReaderT<OptionalKind.Witness, String, Integer> narrowed = READER_T.narrow(widened);

      assertThat(narrowed).isSameAs(original);
    }

    @Test
    @DisplayName("widen then narrow should preserve function reference")
    void roundTrip_shouldPreserveFunctionReference() {
      ReaderT<OptionalKind.Witness, String, Integer> original = createReaderT(222);

      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> widened =
          READER_T.widen(original);
      ReaderT<OptionalKind.Witness, String, Integer> narrowed = READER_T.narrow(widened);

      assertThat(narrowed.run()).isSameAs(original.run());
    }

    @Test
    @DisplayName("multiple round-trips should preserve identity")
    void multipleRoundTrips_shouldPreserveIdentity() {
      ReaderT<OptionalKind.Witness, String, Integer> original = createReaderT(333);

      ReaderT<OptionalKind.Witness, String, Integer> current = original;
      for (int i = 0; i < 3; i++) {
        Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> widened =
            READER_T.widen(current);
        current = READER_T.narrow(widened);
      }

      assertThat(current).isSameAs(original);
    }
  }

  // Dummy Kind for testing invalid type unwrap
  private static class OtherKind<F_Witness, R, A> implements Kind<OtherKind<F_Witness, R, ?>, A> {}
}
