package org.simulation.hkt.list;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.simulation.hkt.Kind;
import org.simulation.hkt.exception.KindUnwrapException;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
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
      assertThatNullPointerException().isThrownBy(() -> wrap(null))
          .withMessageContaining("Input list cannot be null"); // Check message from wrap
    }
  }

  @Nested
  @DisplayName("unwrap()")
  class UnwrapTests {

    // --- Success Cases ---
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

    // --- Failure Cases ---

    // Dummy Kind implementation that is not ListHolder
    record DummyListKind<A>() implements Kind<ListKind<?>, A> {}

    @Test
    void unwrap_shouldThrowForNullInput() {
      assertThatThrownBy(() -> unwrap(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_NULL_MSG);
    }

    @Test
    void unwrap_shouldThrowForUnknownKindType() {
      Kind<ListKind<?>, Integer> unknownKind = new DummyListKind<>();
      assertThatThrownBy(() -> unwrap(unknownKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_TYPE_MSG + DummyListKind.class.getName());
    }

    @Test
    void unwrap_shouldThrowForHolderWithNullList() {
      ListHolder<Double> holderWithNull = new ListHolder<>(null);
      @SuppressWarnings("unchecked") // Cast needed for test setup
      Kind<ListKind<?>, Double> kind = holderWithNull;

      assertThatThrownBy(() -> unwrap(kind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_HOLDER_STATE_MSG);
    }
  }

  @Nested
  @DisplayName("Private Constructor")
  class PrivateConstructorTest {

    @Test
    @DisplayName("should throw UnsupportedOperationException when invoked via reflection")
    void constructor_shouldThrowException() throws NoSuchMethodException {
      Constructor<ListKindHelper> constructor = ListKindHelper.class.getDeclaredConstructor();
      constructor.setAccessible(true);
      assertThatThrownBy(constructor::newInstance)
          .isInstanceOf(InvocationTargetException.class)
          .hasCauseInstanceOf(UnsupportedOperationException.class)
          .cause()
          .hasMessageContaining("This is a utility class and cannot be instantiated");
    }
  }
}