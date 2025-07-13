// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.*;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MaybeKindHelper Tests")
class MaybeKindHelperTest {

  @Nested
  @DisplayName("MAYBE.widen()")
  class WrapTests {
    @Test
    void widen_shouldReturnHolderForJust() {
      Maybe<String> just = Maybe.just("value");
      Kind<MaybeKind.Witness, String> kind = MAYBE.widen(just);

      assertThat(kind).isInstanceOf(MaybeHolder.class);
      assertThat(MAYBE.narrow(kind)).isSameAs(just);
    }

    @Test
    void widen_shouldReturnHolderForNothing() {
      Maybe<Integer> nothingVal = Maybe.nothing(); // Use variable for clarity
      Kind<MaybeKind.Witness, Integer> kind = MAYBE.widen(nothingVal);

      assertThat(kind).isInstanceOf(MaybeHolder.class);
      assertThat(MAYBE.narrow(kind)).isSameAs(nothingVal);
    }

    @Test
    void widen_shouldThrowForNullInput() {
      assertThatNullPointerException()
          .isThrownBy(() -> MAYBE.widen(null))
          .withMessageContaining("Input Maybe cannot be null");
    }
  }

  @Nested
  @DisplayName("just()")
  class JustHelperTests {
    @Test
    void just_shouldWrapJustValue() {
      String value = "test";
      Kind<MaybeKind.Witness, String> kind = MAYBE.just(value);

      assertThat(kind).isInstanceOf(MaybeHolder.class);
      Maybe<String> maybe = MAYBE.narrow(kind);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo(value);
    }

    @Test
    void just_shouldThrowForNullInput() {
      assertThatNullPointerException()
          .isThrownBy(() -> MAYBE.just(null))
          .withMessageContaining("Value for Just cannot be null");
    }
  }

  @Nested
  @DisplayName("nothing()")
  class NothingHelperTests {
    @Test
    void nothing_shouldWrapNothingValue() {
      Kind<MaybeKind.Witness, Integer> kind = MAYBE.nothing();

      assertThat(kind).isInstanceOf(MaybeHolder.class);
      Maybe<Integer> maybe = MAYBE.narrow(kind);
      assertThat(maybe.isNothing()).isTrue();
      assertThat(maybe).isSameAs(Maybe.nothing());
    }
  }

  @Nested
  @DisplayName("MAYBE.narrow()")
  class UnwrapTests {

    // --- Success Cases ---
    @Test
    void narrow_shouldReturnOriginalJust() {
      Maybe<Integer> original = Maybe.just(123);
      Kind<MaybeKind.Witness, Integer> kind = MAYBE.widen(original);
      assertThat(MAYBE.narrow(kind)).isSameAs(original);
    }

    @Test
    void narrow_shouldReturnOriginalNothing() {
      Maybe<String> original = Maybe.nothing();
      Kind<MaybeKind.Witness, String> kind = MAYBE.widen(original);
      assertThat(MAYBE.narrow(kind)).isSameAs(original);
    }

    // --- Failure Cases ---
    // Dummy Kind implementation that is not MaybeHolder
    record DummyMaybeKind<A>() implements Kind<MaybeKind.Witness, A> {}

    @Test
    void narrow_shouldThrowForNullInput() {
      assertThatThrownBy(() -> MAYBE.narrow(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_NULL_MSG);
    }

    @Test
    void narrow_shouldThrowForUnknownKindType() {
      Kind<MaybeKind.Witness, Integer> unknownKind = new DummyMaybeKind<>();
      assertThatThrownBy(() -> MAYBE.narrow(unknownKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_TYPE_MSG + DummyMaybeKind.class.getName());
    }

    @Test
    void narrow_shouldThrowForHolderWithNullMaybe() {

      Kind<MaybeKind.Witness, Double> kind = new MaybeHolder<>(null);

      assertThatThrownBy(() -> MAYBE.narrow(kind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_HOLDER_STATE_MSG);
    }
  }
}
