// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.either.EitherKindHelper.*;
import static org.higherkindedj.hkt.util.ErrorHandling.INVALID_KIND_TYPE_TEMPLATE;
import static org.higherkindedj.hkt.util.ErrorHandling.NULL_KIND_TEMPLATE;

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
  @DisplayName("EITHER.widen()")
  class WrapTests {

    @Test
    void widen_shouldReturnHolderForRight() {
      Either<TestError, Integer> right = Either.right(123);
      var kind = EITHER.widen(right);

      assertThat(kind).isInstanceOf(EitherHolder.class);
      assertThat(EITHER.narrow(kind)).isSameAs(right);
    }

    @Test
    void widen_shouldReturnHolderForLeft() {
      TestError error = new TestError("E404");
      Either<TestError, Integer> left = Either.left(error);
      Kind<EitherKind.Witness<TestError>, Integer> kind = EITHER.widen(left);

      assertThat(kind).isInstanceOf(EitherHolder.class);
      assertThat(EITHER.narrow(kind)).isSameAs(left);
    }

    @Test
    void widen_shouldHandleNullRightValue() {
      var rightNull = Either.right(null);
      var kind = EITHER.widen(rightNull);

      assertThat(kind).isInstanceOf(EitherHolder.class);
      assertThat(EITHER.narrow(kind)).isSameAs(rightNull);
      assertThat(EITHER.narrow(kind).getRight()).isNull();
    }

    @Test
    void widen_shouldHandleNullLeftValue() {
      var leftNull = Either.left(null);
      var kind = EITHER.widen(leftNull);

      assertThat(kind).isInstanceOf(EitherHolder.class);
      assertThat(EITHER.narrow(kind)).isSameAs(leftNull);
      assertThat(EITHER.narrow(kind).getLeft()).isNull();
    }
  }

  @Nested
  @DisplayName("EITHER.narrow()")
  class UnwrapTests {

    // --- Success Cases ---
    @Test
    void narrow_shouldReturnOriginalRight() {
      Either<TestError, String> original = Either.right("Success");
      Kind<EitherKind.Witness<TestError>, String> kind = EITHER.widen(original);
      assertThat(EITHER.narrow(kind)).isSameAs(original);
    }

    @Test
    void narrow_shouldReturnOriginalLeft() {
      Either<TestError, String> original = Either.left(new TestError("E1"));
      Kind<EitherKind.Witness<TestError>, String> kind = EITHER.widen(original);
      assertThat(EITHER.narrow(kind)).isSameAs(original);
    }

    // --- Failure Cases ---

    // Dummy Kind implementation that is not EitherHolder
    record DummyEitherKind<L, R>() implements Kind<EitherKind.Witness<L>, R> {}

    @Test
    void narrow_shouldThrowForNullInput() {
      assertThatThrownBy(() -> EITHER.narrow(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(NULL_KIND_TEMPLATE.formatted(Either.class.getSimpleName()));
    }

    @Test
    void narrow_shouldThrowForUnknownKindType() {
      DummyEitherKind<String, Boolean> unknownKind = new DummyEitherKind<>();
      assertThatThrownBy(() -> EITHER.narrow(unknownKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(
              INVALID_KIND_TYPE_TEMPLATE.formatted("Either", DummyEitherKind.class.getName()));
    }
  }
}
