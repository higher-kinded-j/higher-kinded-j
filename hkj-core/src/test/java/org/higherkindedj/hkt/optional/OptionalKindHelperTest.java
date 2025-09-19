// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.*;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.util.ErrorHandling.*;

import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("OptionalKindHelper Tests")
class OptionalKindHelperTest {

  @Nested
  @DisplayName("OPTIONAL.widen()")
  class WrapTests {

    @Test
    void widen_shouldReturnHolderForPresentOptional() {
      Optional<String> present = Optional.of("value");
      Kind<OptionalKind.Witness, String> kind = OPTIONAL.widen(present);

      assertThat(kind).isInstanceOf(OptionalHolder.class);
      assertThat(OPTIONAL.narrow(kind)).isSameAs(present);
    }

    @Test
    void widen_shouldReturnHolderForEmptyOptional() {
      Optional<String> empty = Optional.empty();
      Kind<OptionalKind.Witness, String> kind = OPTIONAL.widen(empty);

      assertThat(kind).isInstanceOf(OptionalHolder.class);
      assertThat(OPTIONAL.narrow(kind)).isSameAs(empty);
    }

    @Test
    void widen_shouldThrowForNullInput() {
      assertThatNullPointerException()
          .isThrownBy(() -> OPTIONAL.widen(null))
          .withMessageContaining("Input Optional cannot be null");
    }
  }

  @Nested
  @DisplayName("OPTIONAL.narrow()")
  class UnwrapTests {

    // --- Success Cases ---
    @Test
    void narrow_shouldReturnOriginalPresentOptional() {
      Optional<Integer> original = Optional.of(123);
      Kind<OptionalKind.Witness, Integer> kind = OPTIONAL.widen(original);
      assertThat(OPTIONAL.narrow(kind)).isSameAs(original);
    }

    @Test
    void narrow_shouldReturnOriginalEmptyOptional() {
      Optional<Float> original = Optional.empty();
      Kind<OptionalKind.Witness, Float> kind = OPTIONAL.widen(original);
      assertThat(OPTIONAL.narrow(kind)).isSameAs(original);
    }

    // --- Failure Cases ---
    record DummyOptionalKind<A>() implements Kind<OptionalKind.Witness, A> {}

    @Test
    void narrow_shouldThrowForNullInput() {
      assertThatThrownBy(() -> OPTIONAL.narrow(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(NULL_KIND_TEMPLATE.formatted(Optional.class.getSimpleName()));
    }

    @Test
    void narrow_shouldThrowForUnknownKindType() {
      Kind<OptionalKind.Witness, Integer> unknownKind = new DummyOptionalKind<>();
      assertThatThrownBy(() -> OPTIONAL.narrow(unknownKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(
              INVALID_KIND_TYPE_TEMPLATE.formatted(
                  Optional.class.getSimpleName(), DummyOptionalKind.class.getName()));
    }

    @Test
    void narrow_shouldThrowForHolderWithNullOptional() {
      assertThatNullPointerException()
          .isThrownBy(() -> new OptionalHolder<>(null))
          .isInstanceOf(NullPointerException.class)
          .withMessageContaining(
              NULL_HOLDER_STATE_TEMPLATE.formatted("OptionalHolder", "Optional"));
    }
  }
}
