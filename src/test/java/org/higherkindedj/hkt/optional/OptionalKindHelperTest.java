package org.higherkindedj.hkt.optional;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
      assertThat(unwrap(kind)).isSameAs(present);
    }

    @Test
    void wrap_shouldReturnHolderForEmptyOptional() {
      Optional<String> empty = Optional.empty();
      Kind<OptionalKind<?>, String> kind = wrap(empty);

      assertThat(kind).isInstanceOf(OptionalHolder.class);
      assertThat(unwrap(kind)).isSameAs(empty);
    }

    @Test
    void wrap_shouldThrowForNullInput() {
      assertThatNullPointerException()
          .isThrownBy(() -> wrap(null))
          .withMessageContaining("Input Optional cannot be null"); // Check message from wrap
    }
  }

  @Nested
  @DisplayName("unwrap()")
  class UnwrapTests {

    // --- Success Cases ---
    @Test
    void unwrap_shouldReturnOriginalPresentOptional() {
      Optional<Integer> original = Optional.of(123);
      Kind<OptionalKind<?>, Integer> kind = wrap(original);
      assertThat(unwrap(kind)).isSameAs(original);
    }

    @Test
    void unwrap_shouldReturnOriginalEmptyOptional() {
      Optional<Float> original = Optional.empty();
      Kind<OptionalKind<?>, Float> kind = wrap(original);
      assertThat(unwrap(kind)).isSameAs(original);
    }

    // --- Failure Cases ---
    // Dummy Kind implementation that is not OptionalHolder
    record DummyOptionalKind<A>() implements Kind<OptionalKind<?>, A> {}

    @Test
    void unwrap_shouldThrowForNullInput() {
      assertThatThrownBy(() -> unwrap(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_NULL_MSG);
    }

    @Test
    void unwrap_shouldThrowForUnknownKindType() {
      Kind<OptionalKind<?>, Integer> unknownKind = new DummyOptionalKind<>();
      assertThatThrownBy(() -> unwrap(unknownKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_TYPE_MSG + DummyOptionalKind.class.getName());
    }

    @Test
    void unwrap_shouldThrowForHolderWithNullOptional() {
      OptionalHolder<String> holderWithNull = new OptionalHolder<>(null);
      @SuppressWarnings("unchecked") // Cast needed for test setup
      Kind<OptionalKind<?>, String> kind = holderWithNull;

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
      Constructor<OptionalKindHelper> constructor =
          OptionalKindHelper.class.getDeclaredConstructor();
      constructor.setAccessible(true);
      assertThatThrownBy(constructor::newInstance)
          .isInstanceOf(InvocationTargetException.class)
          .hasCauseInstanceOf(UnsupportedOperationException.class)
          .cause()
          .hasMessageContaining("This is a utility class and cannot be instantiated");
    }
  }
}
