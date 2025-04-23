package org.simulation.hkt.list;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.simulation.hkt.Kind;


import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.simulation.hkt.list.ListKindHelper.*;

@DisplayName("ListKindHelper Tests")
class ListKindHelperTest {

  @Nested
  @DisplayName("wrap()")
  class WrapTests {

    @Test
    void wrap_shouldReturnHolderForNonEmptyList() {
      List<Integer> list = Arrays.asList(1, 2, 3);
      Kind<ListKind<?>, Integer> kind = wrap(list);

      assertThat(kind).isInstanceOf(ListHolder.class);
      // Unwrap to verify
      assertThat(unwrap(kind)).isSameAs(list);
    }

    @Test
    void wrap_shouldReturnHolderForEmptyList() {
      List<String> list = Collections.emptyList();
      Kind<ListKind<?>, String> kind = wrap(list);

      assertThat(kind).isInstanceOf(ListHolder.class);
      // Unwrap to verify
      assertThat(unwrap(kind)).isSameAs(list);
    }

    @Test
    void wrap_shouldThrowForNullInput() {
      // Assuming wrap requires a non-null List input
      assertThatNullPointerException().isThrownBy(() -> wrap(null));
    }
  }

  @Nested
  @DisplayName("unwrap()")
  class UnwrapTests {

    // --- Success Cases (already implicitly tested by wrap tests) ---
    @Test
    void unwrap_shouldReturnOriginalNonEmptyList() {
      List<String> original = List.of("a", "b");
      Kind<ListKind<?>, String> kind = wrap(original);
      assertThat(unwrap(kind)).isSameAs(original);
    }

    @Test
    void unwrap_shouldReturnOriginalEmptyList() {
      List<Integer> original = Collections.emptyList();
      Kind<ListKind<?>, Integer> kind = wrap(original);
      assertThat(unwrap(kind)).isSameAs(original);
    }

    // --- Robustness / Failure Cases ---

    // Dummy Kind implementation that is not ListHolder
    record DummyListKind<A>() implements Kind<ListKind<?>, A> {}

    @Test
    void unwrap_shouldReturnEmptyListForNullInput() {
      List<String> result = unwrap(null);
      assertThat(result).isNotNull().isEmpty();
    }

    @Test
    void unwrap_shouldReturnEmptyListForUnknownKindType() {
      Kind<ListKind<?>, Integer> unknownKind = new DummyListKind<>();
      List<Integer> result = unwrap(unknownKind);
      assertThat(result).isNotNull().isEmpty();
    }

    @Test
    void unwrap_shouldReturnEmptyListForHolderWithNullList() {
      // Test the specific case where the holder exists but its internal list is null
      ListHolder<Double> holderWithNull = new ListHolder<>(null);
      // Need to cast to satisfy the Kind type parameter in unwrap
      @SuppressWarnings("unchecked")
      Kind<ListKind<?>, Double> kind = holderWithNull;

      List<Double> result = unwrap(kind);
      assertThat(result).isNotNull().isEmpty();
    }
  }
}
