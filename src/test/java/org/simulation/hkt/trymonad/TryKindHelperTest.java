package org.simulation.hkt.trymonad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.simulation.hkt.Kind;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;
import static org.simulation.hkt.trymonad.TryKindHelper.*;

@DisplayName("TryKindHelper Tests")
class TryKindHelperTest {

  private final String successValue = "value";
  private final RuntimeException testException = new RuntimeException("Test failure");
  private final Try<String> successTry = Try.success(successValue);
  private final Try<String> failureTry = Try.failure(testException);

  @Nested
  @DisplayName("wrap()")
  class WrapTests {
    @Test
    void wrap_shouldReturnHolderForSuccess() {
      Kind<TryKind<?>, String> kind = wrap(successTry);
      assertThat(kind).isInstanceOf(TryHolder.class);
      assertThat(unwrap(kind)).isSameAs(successTry);
    }

    @Test
    void wrap_shouldReturnHolderForFailure() {
      Kind<TryKind<?>, String> kind = wrap(failureTry);
      assertThat(kind).isInstanceOf(TryHolder.class);
      assertThat(unwrap(kind)).isSameAs(failureTry);
    }

    @Test
    void wrap_shouldThrowForNullInput() {
      assertThatNullPointerException()
          .isThrownBy(() -> wrap(null))
          .withMessageContaining("Input Try cannot be null");
    }
  }

  @Nested
  @DisplayName("unwrap()")
  class UnwrapTests {
    // --- Success Cases (implicitly tested by wrap tests) ---
    @Test
    void unwrap_shouldReturnOriginalSuccess() {
      Kind<TryKind<?>, String> kind = wrap(successTry);
      assertThat(unwrap(kind)).isSameAs(successTry);
    }

    @Test
    void unwrap_shouldReturnOriginalFailure() {
      Kind<TryKind<?>, String> kind = wrap(failureTry);
      assertThat(unwrap(kind)).isSameAs(failureTry);
    }

    // --- Robustness / Failure Cases ---

    // Dummy Kind implementation that is not TryHolder
    record DummyTryKind<A>() implements Kind<TryKind<?>, A> {}

    @Test
    void unwrap_shouldReturnFailureForNullInput() {
      Try<String> result = unwrap(null);
      assertThat(result.isFailure()).isTrue();
      assertThatThrownBy(result::get)
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Cannot unwrap null Kind");
    }

    @Test
    void unwrap_shouldReturnFailureForUnknownKindType() {
      Kind<TryKind<?>, Integer> unknownKind = new DummyTryKind<>();
      Try<Integer> result = unwrap(unknownKind);
      assertThat(result.isFailure()).isTrue();
      assertThatThrownBy(result::get)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Kind instance is not a TryHolder");
    }

    @Test
    void unwrap_shouldReturnFailureForHolderWithNullTry() {
      // Test the specific case where the holder exists but its internal Try is null
      TryHolder<Double> holderWithNull = new TryHolder<>(null);
      // Need to cast to satisfy the Kind type parameter in unwrap
      @SuppressWarnings("unchecked")
      Kind<TryKind<?>, Double> kind = (Kind<TryKind<?>, Double>) holderWithNull;

      Try<Double> result = unwrap(kind);
      assertThat(result.isFailure()).isTrue();
      assertThatThrownBy(result::get)
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("TryHolder contained null Try instance");
    }
  }

  @Nested
  @DisplayName("Helper Factories")
  class HelperFactoriesTests {

    @Test
    void success_helperShouldWrapSuccess() {
      Kind<TryKind<?>, String> kind = TryKindHelper.success(successValue);
      Try<String> tryResult = unwrap(kind);
      assertThat(tryResult.isSuccess()).isTrue();
      assertThatCode(() -> assertThat(tryResult.get()).isEqualTo(successValue)).doesNotThrowAnyException();
    }

    @Test
    void failure_helperShouldWrapFailure() {
      IOException ioEx = new IOException("IO");
      Kind<TryKind<?>, String> kind = TryKindHelper.failure(ioEx);
      Try<String> tryResult = unwrap(kind);
      assertThat(tryResult.isFailure()).isTrue();
      assertThatThrownBy(tryResult::get).isSameAs(ioEx);
    }

    @Test
    void tryOf_helperShouldWrapSuccess() {
      Kind<TryKind<?>, Integer> kind = TryKindHelper.tryOf(() -> 10 / 2);
      Try<Integer> tryResult = unwrap(kind);
      assertThat(tryResult.isSuccess()).isTrue();
      assertThatCode(() -> assertThat(tryResult.get()).isEqualTo(5)).doesNotThrowAnyException();
    }

    @Test
    void tryOf_helperShouldWrapFailure() {
      ArithmeticException arithEx = new ArithmeticException("/ by zero");
      Kind<TryKind<?>, Integer> kind = TryKindHelper.tryOf(() -> {
        // Simulate the exception being thrown
        if (true) throw arithEx;
        return 1; // Unreachable
      });
      Try<Integer> tryResult = unwrap(kind);
      assertThat(tryResult.isFailure()).isTrue();
      // Check that the cause is the original exception instance
      assertThatThrownBy(tryResult::get).isSameAs(arithEx);
    }

    @Test
    void tryOf_helperShouldThrowNPEForNullSupplier() {
      assertThatNullPointerException()
          .isThrownBy(() -> TryKindHelper.tryOf(null))
          .withMessageContaining("Supplier cannot be null");
    }
  }
}