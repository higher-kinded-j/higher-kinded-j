// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.maybe_t.MaybeTKindHelper.MAYBE_T;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MaybeTKindHelper Tests")
//  [F=OptionalKind.Witness]
class MaybeTKindHelperTest {

  private static final String TYPE_NAME = "MaybeT";

  private Monad<OptionalKind.Witness> outerMonad;

  @BeforeEach
  void setUp() {
    outerMonad = OptionalMonad.INSTANCE;
  }

  private <A> MaybeT<OptionalKind.Witness, A> createMaybeT(Maybe<A> maybe) {
    return MaybeT.fromMaybe(outerMonad, maybe);
  }

  @Nested
  @DisplayName("Widen Tests")
  class WidenTests {

    @Test
    @DisplayName("widen should convert non-null MaybeT (Just) to MaybeTKind")
    void widen_nonNullMaybeTJust_shouldReturnMaybeTKind() {
      MaybeT<OptionalKind.Witness, Integer> concreteMaybeT = createMaybeT(Maybe.just(123));
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> wrapped =
          MAYBE_T.widen(concreteMaybeT);

      assertThat(wrapped).isNotNull().isInstanceOf(MaybeTKind.class);
      assertThat(MAYBE_T.narrow(wrapped)).isSameAs(concreteMaybeT);
    }

    @Test
    @DisplayName("widen should convert non-null MaybeT (Nothing) to MaybeTKind")
    void widen_nonNullMaybeTNothing_shouldReturnMaybeTKind() {
      MaybeT<OptionalKind.Witness, Integer> concreteMaybeT = createMaybeT(Maybe.nothing());
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> wrapped =
          MAYBE_T.widen(concreteMaybeT);

      assertThat(wrapped).isNotNull().isInstanceOf(MaybeTKind.class);
      assertThat(MAYBE_T.narrow(wrapped)).isSameAs(concreteMaybeT);
    }

    @Test
    @DisplayName("widen should convert non-null MaybeT (Just(null)) to MaybeTKind")
    void widen_nonNullMaybeTJustNull_shouldReturnMaybeTKind() {
      // Use fromNullable instead of just for null values
      MaybeT<OptionalKind.Witness, String> concreteMaybeT = createMaybeT(Maybe.fromNullable(null));
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> wrapped =
          MAYBE_T.widen(concreteMaybeT);

      assertThat(wrapped).isNotNull().isInstanceOf(MaybeTKind.class);
      assertThat(MAYBE_T.narrow(wrapped)).isSameAs(concreteMaybeT);
    }

    @Test
    @DisplayName("widen should throw NullPointerException when given null")
    void widen_nullMaybeT_shouldThrowNullPointerException() {
      assertThatThrownBy(() -> MAYBE_T.widen(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("Input %s cannot be null for widen".formatted(TYPE_NAME));
    }
  }

  @Nested
  @DisplayName("Narrow Tests")
  class NarrowTests {

    @Test
    @DisplayName("narrow should unwrap valid MaybeTKind (Just) to original MaybeT instance")
    void narrow_validKindJust_shouldReturnMaybeT() {
      MaybeT<OptionalKind.Witness, Integer> originalMaybeT = createMaybeT(Maybe.just(456));
      var wrappedKind = MAYBE_T.widen(originalMaybeT);

      MaybeT<OptionalKind.Witness, Integer> unwrappedMaybeT = MAYBE_T.narrow(wrappedKind);

      assertThat(unwrappedMaybeT).isSameAs(originalMaybeT);
    }

    @Test
    @DisplayName("narrow should unwrap valid MaybeTKind (Nothing) to original MaybeT instance")
    void narrow_validKindNothing_shouldReturnMaybeT() {
      MaybeT<OptionalKind.Witness, Integer> originalMaybeT = createMaybeT(Maybe.nothing());
      var wrappedKind = MAYBE_T.widen(originalMaybeT);

      MaybeT<OptionalKind.Witness, Integer> unwrappedMaybeT = MAYBE_T.narrow(wrappedKind);

      assertThat(unwrappedMaybeT).isSameAs(originalMaybeT);
    }

    @Test
    @DisplayName("narrow should throw KindUnwrapException when given null")
    void narrow_nullKind_shouldThrowKindUnwrapException() {
      assertThatThrownBy(() -> MAYBE_T.narrow(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage("Cannot narrow null Kind for %s".formatted(TYPE_NAME));
    }

    @Test
    @DisplayName("narrow should throw KindUnwrapException when given incorrect Kind type")
    void narrow_incorrectKindType_shouldThrowKindUnwrapException() {
      OtherKind<OptionalKind.Witness, Integer> incorrectKind = new OtherKind<>();

      @SuppressWarnings({"unchecked", "rawtypes"})
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> kindToTest =
          (Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer>) (Kind) incorrectKind;

      assertThatThrownBy(() -> MAYBE_T.narrow(kindToTest))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage(
              "Kind instance cannot be narrowed to %s (received: %s)"
                  .formatted(MaybeT.class.getSimpleName(), OtherKind.class.getSimpleName()));
    }
  }

  @Nested
  @DisplayName("Round-Trip Tests")
  class RoundTripTests {

    @Test
    @DisplayName("widen then narrow should preserve identity for Just")
    void roundTrip_just_shouldPreserveIdentity() {
      MaybeT<OptionalKind.Witness, Integer> original = createMaybeT(Maybe.just(789));

      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> widened = MAYBE_T.widen(original);
      MaybeT<OptionalKind.Witness, Integer> narrowed = MAYBE_T.narrow(widened);

      assertThat(narrowed).isSameAs(original);
    }

    @Test
    @DisplayName("widen then narrow should preserve identity for Nothing")
    void roundTrip_nothing_shouldPreserveIdentity() {
      MaybeT<OptionalKind.Witness, Integer> original = createMaybeT(Maybe.nothing());

      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> widened = MAYBE_T.widen(original);
      MaybeT<OptionalKind.Witness, Integer> narrowed = MAYBE_T.narrow(widened);

      assertThat(narrowed).isSameAs(original);
    }

    @Test
    @DisplayName("widen then narrow should preserve identity for Just(null)")
    void roundTrip_justNull_shouldPreserveIdentity() {
      // Use fromNullable instead of just for null values
      MaybeT<OptionalKind.Witness, String> original = createMaybeT(Maybe.fromNullable(null));

      Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> widened = MAYBE_T.widen(original);
      MaybeT<OptionalKind.Witness, String> narrowed = MAYBE_T.narrow(widened);

      assertThat(narrowed).isSameAs(original);
    }

    @Test
    @DisplayName("multiple round-trips should preserve identity")
    void multipleRoundTrips_shouldPreserveIdentity() {
      MaybeT<OptionalKind.Witness, Integer> original = createMaybeT(Maybe.just(999));

      MaybeT<OptionalKind.Witness, Integer> current = original;
      for (int i = 0; i < 3; i++) {
        Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> widened = MAYBE_T.widen(current);
        current = MAYBE_T.narrow(widened);
      }

      assertThat(current).isSameAs(original);
    }
  }

  // Dummy Kind for testing invalid type unwrap
  private interface OtherWitness extends WitnessArity<TypeArity.Unary> {}

  private static class OtherKind<F_Witness, A> implements Kind<OtherWitness, A> {}
}
