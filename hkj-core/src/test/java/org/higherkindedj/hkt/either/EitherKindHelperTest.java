// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.either.EitherKindHelper.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherKindHelper Tests")
class EitherKindHelperTest {

  // Define a simple error type for testing
  record TestError(String code) {}

  @Nested
  @DisplayName("wrap()")
  class WrapTests {

    @Test
    void wrap_shouldReturnHolderForRight() {
      Either<TestError, Integer> right = Either.right(123);
      var kind = wrap(right);

      assertThat(kind).isInstanceOf(EitherHolder.class);
      assertThat(unwrap(kind)).isSameAs(right);
    }

    @Test
    void wrap_shouldReturnHolderForLeft() {
      TestError error = new TestError("E404");
      Either<TestError, Integer> left = Either.left(error);
      Kind<EitherKind.Witness<TestError>, Integer> kind = wrap(left);

      assertThat(kind).isInstanceOf(EitherHolder.class);
      assertThat(unwrap(kind)).isSameAs(left);
    }

    @Test
    void wrap_shouldHandleNullRightValue() {
      var rightNull = Either.right(null);
      var kind = wrap(rightNull);

      assertThat(kind).isInstanceOf(EitherHolder.class);
      assertThat(unwrap(kind)).isSameAs(rightNull);
      assertThat(unwrap(kind).getRight()).isNull();
    }

    @Test
    void wrap_shouldHandleNullLeftValue() {
      var leftNull = Either.left(null);
      var kind = wrap(leftNull);

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
      Kind<EitherKind.Witness<TestError>, String> kind = wrap(original);
      assertThat(unwrap(kind)).isSameAs(original);
    }

    @Test
    void unwrap_shouldReturnOriginalLeft() {
      Either<TestError, String> original = Either.left(new TestError("E1"));
      Kind<EitherKind.Witness<TestError>, String> kind = wrap(original);
      assertThat(unwrap(kind)).isSameAs(original);
    }

    // --- Failure Cases ---

    // Dummy Kind implementation that is not EitherHolder
    record DummyEitherKind<L, R>() implements Kind<EitherKind.Witness<L>, R> {}

    @Test
    void unwrap_shouldThrowForNullInput() {
      assertThatThrownBy(() -> unwrap(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_NULL_MSG);
    }

    @Test
    void unwrap_shouldThrowForUnknownKindType() {
      DummyEitherKind<String, Boolean> unknownKind = new DummyEitherKind<>();
      assertThatThrownBy(() -> unwrap(unknownKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_TYPE_MSG + DummyEitherKind.class.getName());
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
