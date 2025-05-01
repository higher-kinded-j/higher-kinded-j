package org.higherkindedj.hkt.maybe;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MaybeKindHelper Tests")
class MaybeKindHelperTest {

  @Nested
  @DisplayName("wrap()")
  class WrapTests {
    @Test
    void wrap_shouldReturnHolderForJust() {
      Maybe<String> just = Maybe.just("value");
      Kind<MaybeKind<?>, String> kind = wrap(just);

      assertThat(kind).isInstanceOf(MaybeHolder.class);
      assertThat(unwrap(kind)).isSameAs(just);
    }

    @Test
    void wrap_shouldReturnHolderForNothing() {
      Maybe<Integer> nothingVal = Maybe.nothing(); // Use variable for clarity
      Kind<MaybeKind<?>, Integer> kind = wrap(nothingVal);

      assertThat(kind).isInstanceOf(MaybeHolder.class);
      assertThat(unwrap(kind)).isSameAs(nothingVal);
    }

    @Test
    void wrap_shouldThrowForNullInput() {
      assertThatNullPointerException()
          .isThrownBy(() -> wrap(null))
          .withMessageContaining("Input Maybe cannot be null"); // Check message from wrap
    }
  }

  @Nested
  @DisplayName("just()")
  class JustHelperTests {
    @Test
    void just_shouldWrapJustValue() {
      String value = "test";
      Kind<MaybeKind<?>, String> kind = just(value);

      assertThat(kind).isInstanceOf(MaybeHolder.class);
      Maybe<String> maybe = unwrap(kind);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo(value);
    }

    @Test
    void just_shouldThrowForNullInput() {
      assertThatNullPointerException()
          .isThrownBy(() -> just(null))
          .withMessageContaining("Value for Just cannot be null");
    }
  }

  @Nested
  @DisplayName("nothing()")
  class NothingHelperTests {
    @Test
    void nothing_shouldWrapNothingValue() {
      Kind<MaybeKind<?>, Integer> kind = nothing();

      assertThat(kind).isInstanceOf(MaybeHolder.class);
      Maybe<Integer> maybe = unwrap(kind);
      assertThat(maybe.isNothing()).isTrue();
      assertThat(maybe).isSameAs(Maybe.nothing());
    }
  }

  @Nested
  @DisplayName("unwrap()")
  class UnwrapTests {

    // --- Success Cases ---
    @Test
    void unwrap_shouldReturnOriginalJust() {
      Maybe<Integer> original = Maybe.just(123);
      Kind<MaybeKind<?>, Integer> kind = wrap(original);
      assertThat(unwrap(kind)).isSameAs(original);
    }

    @Test
    void unwrap_shouldReturnOriginalNothing() {
      Maybe<String> original = Maybe.nothing();
      Kind<MaybeKind<?>, String> kind = wrap(original);
      assertThat(unwrap(kind)).isSameAs(original);
    }

    // --- Failure Cases ---
    // Dummy Kind implementation that is not MaybeHolder
    record DummyMaybeKind<A>() implements Kind<MaybeKind<?>, A> {}

    @Test
    void unwrap_shouldThrowForNullInput() {
      assertThatThrownBy(() -> unwrap(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_NULL_MSG);
    }

    @Test
    void unwrap_shouldThrowForUnknownKindType() {
      Kind<MaybeKind<?>, Integer> unknownKind = new DummyMaybeKind<>();
      assertThatThrownBy(() -> unwrap(unknownKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_TYPE_MSG + DummyMaybeKind.class.getName());
    }

    @Test
    void unwrap_shouldThrowForHolderWithNullMaybe() {
      MaybeHolder<Double> holderWithNull = new MaybeHolder<>(null);
      @SuppressWarnings("unchecked") // Cast needed for test setup
      Kind<MaybeKind<?>, Double> kind = holderWithNull;

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
      Constructor<MaybeKindHelper> constructor = MaybeKindHelper.class.getDeclaredConstructor();
      constructor.setAccessible(true);
      assertThatThrownBy(constructor::newInstance)
          .isInstanceOf(InvocationTargetException.class)
          .hasCauseInstanceOf(UnsupportedOperationException.class)
          .cause()
          .hasMessageContaining("This is a utility class and cannot be instantiated");
    }
  }
}
