package org.simulation.hkt.either;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.simulation.hkt.Kind;
import org.simulation.hkt.future.CompletableFutureKindHelper;


import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.simulation.hkt.either.EitherKindHelper.*;

@DisplayName("EitherKindHelper Tests")
class EitherKindHelperTest {

  // Define a simple error type for testing
  record TestError(String code) {}
  private static final String INVALID_KIND_ERROR = "Invalid Kind state (null or unexpected type)";

  @Nested
  @DisplayName("wrap()")
  class WrapTests {

    @Test
    void wrap_shouldReturnHolderForRight() {
      Either<TestError, Integer> right = Either.right(123);
      Kind<EitherKind<TestError, ?>, Integer> kind = wrap(right);

      assertThat(kind).isInstanceOf(EitherHolder.class);
      // Unwrap to verify
      assertThat(unwrap(kind)).isSameAs(right);
    }

    @Test
    void wrap_shouldReturnHolderForLeft() {
      TestError error = new TestError("E404");
      Either<TestError, Integer> left = Either.left(error);
      Kind<EitherKind<TestError, ?>, Integer> kind = wrap(left);

      assertThat(kind).isInstanceOf(EitherHolder.class);
      // Unwrap to verify
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

    // --- Success Cases (already implicitly tested by wrap tests) ---
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

    // --- Robustness / Failure Cases ---

    // Dummy Kind implementation that is not EitherHolder
    record DummyEitherKind<L, R>() implements Kind<EitherKind<L, ?>, R> {}

    @Test
    void unwrap_shouldReturnLeftErrorForNullInput() {
      // We need to specify the expected Left type for the assertion
      // Note: The helper returns Left(String), so L must be String or Object here.
      Either<String, Integer> result = unwrap(null);
      assertThat(result).isNotNull();
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft()).isEqualTo(INVALID_KIND_ERROR);
    }

    @Test
    void unwrap_shouldReturnLeftErrorForUnknownKindType() {
      // Specify L as String, R as Boolean for this test instance
      Kind<EitherKind<String, ?>, Boolean> unknownKind = new DummyEitherKind<>();
      Either<String, Boolean> result = unwrap(unknownKind);
      assertThat(result).isNotNull();
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft()).isEqualTo(INVALID_KIND_ERROR);
    }

    @Test
    void unwrap_shouldReturnLeftErrorForHolderWithNullEither() {
      // Test the specific case where the holder exists but its internal either is null
      EitherHolder<String, Integer> holderWithNull = new EitherHolder<>(null);
      // Need to cast to satisfy the Kind type parameter in unwrap
      @SuppressWarnings("unchecked")
      Kind<EitherKind<String, ?>, Integer> kind = holderWithNull;

      Either<String, Integer> result = unwrap(kind);
      assertThat(result).isNotNull();
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft()).isEqualTo(INVALID_KIND_ERROR);
    }
  }


  @Nested
  @DisplayName("Private Constructor")
  class PrivateConstructorTest {

    @Test
    @DisplayName("should throw UnsupportedOperationException when invoked via reflection")
    void constructor_shouldThrowException() throws NoSuchMethodException {
      // Get the private constructor
      Constructor<EitherKindHelper> constructor = EitherKindHelper.class.getDeclaredConstructor();

      // Make it accessible
      constructor.setAccessible(true);

      // Assert that invoking the constructor throws the expected exception
      // InvocationTargetException wraps the actual exception thrown by the constructor
      assertThatThrownBy(constructor::newInstance)
          .isInstanceOf(InvocationTargetException.class)
          .hasCauseInstanceOf(UnsupportedOperationException.class)
          .cause() // Get the wrapped UnsupportedOperationException
          .hasMessageContaining("This is a utility class and cannot be instantiated");
    }
  }
}
