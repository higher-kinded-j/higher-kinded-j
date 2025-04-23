package org.simulation.hkt.optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.simulation.hkt.Kind;


import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.simulation.hkt.optional.OptionalKindHelper.*;

@DisplayName("OptionalKindHelper Tests")
class OptionalKindHelperTest {

  @Nested
  @DisplayName("wrap()")
  class WrapTests {

    @Test
    void wrap_shouldReturnHolderForPresentOptional() {
      Optional<String> present = Optional.of("value");
      Kind<OptionalKind<?>, String> kind = wrap(present);

      assertThat(kind).isInstanceOf(OptionalHolder.class);
      // Unwrap to verify
      assertThat(unwrap(kind)).isSameAs(present);
    }

    @Test
    void wrap_shouldReturnHolderForEmptyOptional() {
      Optional<String> empty = Optional.empty();
      Kind<OptionalKind<?>, String> kind = wrap(empty);

      assertThat(kind).isInstanceOf(OptionalHolder.class);
      // Unwrap to verify
      assertThat(unwrap(kind)).isSameAs(empty);
    }

    @Test
    void wrap_shouldThrowForNullInput() {
      // Optional.wrap itself doesn't accept null, unlike Optional.ofNullable
      // So wrap(null) is not a valid use case here.
      // If wrap were to accept null, it should likely return wrap(Optional.empty())
      // Let's assume wrap requires a non-null Optional.
      assertThatNullPointerException().isThrownBy(() -> wrap(null));
    }
  }

  @Nested
  @DisplayName("unwrap()")
  class UnwrapTests {

    // --- Success Cases (already implicitly tested by wrap tests) ---
    @Test
    void unwrap_shouldReturnOriginalPresentOptional() {
      Optional<Integer> original = Optional.of(123);
      Kind<OptionalKind<?>, Integer> kind = wrap(original);
      assertThat(unwrap(kind)).isSameAs(original);
    }

    @Test
    void unwrap_shouldReturnOriginalEmptyOptional() {
      Optional<Integer> original = Optional.empty();
      Kind<OptionalKind<?>, Integer> kind = wrap(original);
      assertThat(unwrap(kind)).isSameAs(original);
    }

    // --- Robustness / Failure Cases ---

    // Dummy Kind implementation that is not OptionalHolder
    record DummyOptionalKind<A>() implements Kind<OptionalKind<?>, A> {}

    @Test
    void unwrap_shouldReturnEmptyOptionalForNullInput() {
      Optional<String> result = unwrap(null);
      assertThat(result).isNotNull().isEmpty();
    }

    @Test
    void unwrap_shouldReturnEmptyOptionalForUnknownKindType() {
      Kind<OptionalKind<?>, Integer> unknownKind = new DummyOptionalKind<>();
      Optional<Integer> result = unwrap(unknownKind);
      assertThat(result).isNotNull().isEmpty();
    }

    @Test
    void unwrap_shouldReturnEmptyOptionalForHolderWithNullOptional() {
      // Test the specific case where the holder exists but its internal optional is null
      OptionalHolder<String> holderWithNull = new OptionalHolder<>(null);
      // Need to cast to satisfy the Kind type parameter in unwrap
      @SuppressWarnings("unchecked")
      Kind<OptionalKind<?>, String> kind = holderWithNull;

      Optional<String> result = unwrap(kind);
      assertThat(result).isNotNull().isEmpty();
    }
  }
}
