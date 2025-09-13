// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.*;
import static org.higherkindedj.hkt.util.ErrorHandling.INVALID_KIND_TYPE_TEMPLATE;
import static org.higherkindedj.hkt.util.ErrorHandling.NULL_HOLDER_STATE_TEMPLATE;
import static org.higherkindedj.hkt.util.ErrorHandling.NULL_KIND_TEMPLATE;

import java.io.IOException;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TryKindHelper Tests")
class TryKindHelperTest {

  private final String successValue = "value";
  private final RuntimeException testException = new RuntimeException("Test failure");
  private final Try<String> successTry = Try.success(successValue);
  private final Try<String> failureTry = Try.failure(testException);

  @Nested
  @DisplayName("TRY.widen()")
  class WidenTests {
    @Test
    void widen_shouldReturnHolderForSuccess() {
      Kind<TryKind.Witness, String> kind = TRY.widen(successTry);
      assertThat(TRY.narrow(kind)).isSameAs(successTry);
    }

    @Test
    void widen_shouldReturnHolderForFailure() {
      Kind<TryKind.Witness, String> kind = TRY.widen(failureTry);
      assertThat(TRY.narrow(kind)).isSameAs(failureTry);
    }

    @Test
    void widen_shouldThrowForNullInput() {
      assertThatNullPointerException()
          .isThrownBy(() -> TRY.widen(null))
          .withMessageContaining("Input Try cannot be null");
    }
  }

  @Nested
  @DisplayName("narrow()")
  class narrowTests {
    // --- Success Cases ---
    @Test
    void narrow_shouldReturnOriginalSuccess() {
      Kind<TryKind.Witness, String> kind = TRY.widen(successTry);
      assertThat(TRY.narrow(kind)).isSameAs(successTry);
    }

    @Test
    void narrow_shouldReturnOriginalFailure() {
      Kind<TryKind.Witness, String> kind = TRY.widen(failureTry);
      assertThat(TRY.narrow(kind)).isSameAs(failureTry);
    }

    // --- Failure Cases ---

    // Dummy Kind implementation that is not TryHolder but claims to be Kind<TryKind.Witness, A>
    record DummyOtherKind<A>() implements Kind<TryKind.Witness, A> {}

    @Test
    void narrow_shouldThrowForNullInput() {
      assertThatThrownBy(() -> TRY.narrow(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(String.format(NULL_KIND_TEMPLATE, "Try"));
    }

    @Test
    void narrow_shouldThrowForUnknownKindType() {
      Kind<TryKind.Witness, Integer> unknownKind = new DummyOtherKind<>(); // Use DummyOtherKind
      assertThatThrownBy(() -> TRY.narrow(unknownKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(
              String.format(INVALID_KIND_TYPE_TEMPLATE, "Try", DummyOtherKind.class.getName()));
    }

    @Test
    void shouldThrowForHolderWithNullTry() {
      assertThatThrownBy(() -> new TryKindHelper.TryHolder<>(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining(NULL_HOLDER_STATE_TEMPLATE.formatted("TryHolder", "Try"));
    }
  }

  @Nested
  @DisplayName("Helper Factories")
  class HelperFactoriesTests {

    @Test
    void success_helperShouldWrapSuccess() {
      Kind<TryKind.Witness, String> kind = TRY.success(successValue);
      Try<String> tryResult = TRY.narrow(kind);
      assertThat(tryResult.isSuccess()).isTrue();
      assertThatCode(() -> assertThat(tryResult.get()).isEqualTo(successValue))
          .doesNotThrowAnyException();
    }

    @Test
    void failure_helperShouldWrapFailure() {
      IOException ioEx = new IOException("IO");
      Kind<TryKind.Witness, String> kind = TRY.failure(ioEx);
      Try<String> tryResult = TRY.narrow(kind);
      assertThat(tryResult.isFailure()).isTrue();
      assertThatThrownBy(tryResult::get).isSameAs(ioEx);
    }

    @Test
    void tryOf_helperShouldWrapSuccess() {
      Kind<TryKind.Witness, Integer> kind = TRY.tryOf(() -> 10 / 2);
      Try<Integer> tryResult = TRY.narrow(kind);
      assertThat(tryResult.isSuccess()).isTrue();
      assertThatCode(() -> assertThat(tryResult.get()).isEqualTo(5)).doesNotThrowAnyException();
    }

    @Test
    void tryOf_helperShouldWrapFailure() {
      ArithmeticException arithEx = new ArithmeticException("/ by zero");
      Kind<TryKind.Witness, Integer> kind =
          TRY.tryOf(
              () -> {
                if (true) throw arithEx;
                return 1;
              });
      Try<Integer> tryResult = TRY.narrow(kind);
      assertThat(tryResult.isFailure()).isTrue();
      assertThatThrownBy(tryResult::get).isSameAs(arithEx);
    }

    @Test
    void tryOf_helperShouldThrowNPEForNullSupplier() {
      assertThatNullPointerException()
          .isThrownBy(() -> TRY.tryOf(null))
          .withMessageContaining("Supplier cannot be null"); // Message from Try.of
    }
  }
}
