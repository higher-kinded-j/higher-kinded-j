package org.simulation.hkt.trymonad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.simulation.hkt.Kind;
import org.simulation.hkt.exception.KindUnwrapException;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

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
    // --- Success Cases ---
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

    // --- Failure Cases ---

    // Dummy Kind implementation that is not TryHolder
    record DummyTryKind<A>() implements Kind<TryKind<?>, A> {}

    @Test
    void unwrap_shouldThrowForNullInput() {
      assertThatThrownBy(() -> unwrap(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_NULL_MSG);
    }

    @Test
    void unwrap_shouldThrowForUnknownKindType() {
      Kind<TryKind<?>, Integer> unknownKind = new DummyTryKind<>();
      assertThatThrownBy(() -> unwrap(unknownKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_TYPE_MSG + DummyTryKind.class.getName());
    }

    @Test
    void unwrap_shouldThrowForHolderWithNullTry() {
      TryHolder<Double> holderWithNull = new TryHolder<>(null);
      @SuppressWarnings("unchecked") // Cast needed for test setup
      Kind<TryKind<?>, Double> kind = holderWithNull;

      assertThatThrownBy(() -> unwrap(kind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_HOLDER_STATE_MSG);
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
        if (true) throw arithEx; // Simulate throwing
        return 1;
      });
      Try<Integer> tryResult = unwrap(kind);
      assertThat(tryResult.isFailure()).isTrue();
      assertThatThrownBy(tryResult::get).isSameAs(arithEx);
    }

    @Test
    void tryOf_helperShouldThrowNPEForNullSupplier() {
      assertThatNullPointerException()
          .isThrownBy(() -> TryKindHelper.tryOf(null))
          .withMessageContaining("Supplier cannot be null");
    }
  }

  @Nested
  @DisplayName("Private Constructor")
  class PrivateConstructorTest {

    @Test
    @DisplayName("should throw UnsupportedOperationException when invoked via reflection")
    void constructor_shouldThrowException() throws NoSuchMethodException {
      Constructor<TryKindHelper> constructor = TryKindHelper.class.getDeclaredConstructor();
      constructor.setAccessible(true);
      assertThatThrownBy(constructor::newInstance)
          .isInstanceOf(InvocationTargetException.class)
          .hasCauseInstanceOf(UnsupportedOperationException.class)
          .cause()
          .hasMessageContaining("This is a utility class and cannot be instantiated");
    }
  }
}