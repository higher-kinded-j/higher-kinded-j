package org.simulation.hkt.either;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.simulation.hkt.Kind;
import org.simulation.hkt.exception.KindUnwrapException;


import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.simulation.hkt.either.EitherKindHelper.*;

@DisplayName("EitherKindHelper Tests")
class EitherKindHelperTest {

  // Error Messages from EitherKindHelper (copy or import if made public)
  private static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for Either";
  private static final String INVALID_KIND_TYPE_MSG = "Kind instance is not an EitherHolder: ";
  private static final String INVALID_HOLDER_STATE_MSG = "EitherHolder contained null Either instance";

  // Define a simple error type for testing
  record TestError(String code) {}

  @Nested
  @DisplayName("wrap()")
  class WrapTests {

    @Test
    void wrap_shouldReturnHolderForRight() {
      Either<TestError, Integer> right = Either.right(123);
      Kind<EitherKind<TestError, ?>, Integer> kind = wrap(right);

      assertThat(kind).isInstanceOf(EitherHolder.class);
      assertThat(unwrap(kind)).isSameAs(right);
    }

    @Test
    void wrap_shouldReturnHolderForLeft() {
      TestError error = new TestError("E404");
      Either<TestError, Integer> left = Either.left(error);
      Kind<EitherKind<TestError, ?>, Integer> kind = wrap(left);

      assertThat(kind).isInstanceOf(EitherHolder.class);
      assertThat(unwrap(kind)).isSameAs(left);
    }

    @Test
    void wrap_shouldHandleNullRightValue() {
      Either<String, Integer> rightNull = Either.right(null);
      Kind<EitherKind<String, ?>, Integer> kind = wrap(rightNull);

      assertThat(kind).isInstanceOf(EitherHolder.class);
      assertThat(unwrap(kind)).isSameAs(rightNull);
      assertThat(unwrap(kind).getRight()).isNull();
    }

    @Test
    void wrap_shouldHandleNullLeftValue() {
      Either<String, Integer> leftNull = Either.left(null);
      Kind<EitherKind<String, ?>, Integer> kind = wrap(leftNull);

      assertThat(kind).isInstanceOf(EitherHolder.class);
      assertThat(unwrap(kind)).isSameAs(leftNull);
      assertThat(unwrap(kind).getLeft()).isNull();
    }
  }

  @Nested
  @DisplayName("unwrap()")
  class UnwrapTests {

    // --- Success Cases ---
    @Test
    void unwrap_shouldReturnOriginalRight() {
      Either<TestError, String> original = Either.right("Success");
      Kind<EitherKind<TestError, ?>, String> kind = wrap(original);
      assertThat(unwrap(kind)).isSameAs(original);
    }

    @Test
    void unwrap_shouldReturnOriginalLeft() {
      Either<TestError, String> original = Either.left(new TestError("E1"));
      Kind<EitherKind<TestError, ?>, String> kind = wrap(original);
      assertThat(unwrap(kind)).isSameAs(original);
    }

    // --- Failure Cases ---

    // Dummy Kind implementation that is not EitherHolder
    record DummyEitherKind<L, R>() implements Kind<EitherKind<L, ?>, R> {}

    @Test
    void unwrap_shouldThrowForNullInput() {
      assertThatThrownBy(() -> unwrap(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_NULL_MSG);
    }

    @Test
    void unwrap_shouldThrowForUnknownKindType() {
      Kind<EitherKind<String, ?>, Boolean> unknownKind = new DummyEitherKind<>();
      assertThatThrownBy(() -> unwrap(unknownKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_TYPE_MSG + DummyEitherKind.class.getName());
    }

    @Test
    void unwrap_shouldThrowForHolderWithNullEither() {
      EitherHolder<String, Integer> holderWithNull = new EitherHolder<>(null);
      @SuppressWarnings("unchecked") // Cast needed for test setup
      Kind<EitherKind<String, ?>, Integer> kind = holderWithNull;

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
      Constructor<EitherKindHelper> constructor = EitherKindHelper.class.getDeclaredConstructor();
      constructor.setAccessible(true);
      assertThatThrownBy(constructor::newInstance)
          .isInstanceOf(InvocationTargetException.class)
          .hasCauseInstanceOf(UnsupportedOperationException.class)
          .cause()
          .hasMessageContaining("This is a utility class and cannot be instantiated");
    }
  }
}