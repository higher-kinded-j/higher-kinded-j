package org.higherkindedj.hkt.future;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.*;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CompletableFutureKindHelper Tests")
class CompletableFutureKindHelperTest {

  @Nested
  @DisplayName("wrap()")
  class WrapTests {

    @Test
    void wrap_shouldReturnHolderForCompletedFuture() {
      CompletableFuture<String> future = CompletableFuture.completedFuture("done");
      Kind<CompletableFutureKind<?>, String> kind = wrap(future);

      assertThat(kind).isInstanceOf(CompletableFutureHolder.class);
      assertThat(unwrap(kind)).isSameAs(future);
    }

    @Test
    void wrap_shouldReturnHolderForFailedFuture() {
      RuntimeException ex = new RuntimeException("fail");
      CompletableFuture<String> future = CompletableFuture.failedFuture(ex);
      Kind<CompletableFutureKind<?>, String> kind = wrap(future);

      assertThat(kind).isInstanceOf(CompletableFutureHolder.class);
      assertThat(unwrap(kind)).isSameAs(future);
    }

    @Test
    void wrap_shouldThrowForNullInput() {
      assertThatNullPointerException()
          .isThrownBy(() -> wrap(null))
          .withMessageContaining("Input CompletableFuture cannot be null");
    }
  }

  @Nested
  @DisplayName("unwrap()")
  class UnwrapTests {

    // --- Success Cases ---
    @Test
    void unwrap_shouldReturnOriginalCompletedFuture() {
      CompletableFuture<Integer> original = CompletableFuture.completedFuture(42);
      Kind<CompletableFutureKind<?>, Integer> kind = wrap(original);
      assertThat(unwrap(kind)).isSameAs(original);
    }

    @Test
    void unwrap_shouldReturnOriginalFailedFuture() {
      IOException ex = new IOException("io fail");
      CompletableFuture<Integer> original = CompletableFuture.failedFuture(ex);
      Kind<CompletableFutureKind<?>, Integer> kind = wrap(original);
      assertThat(unwrap(kind)).isSameAs(original);
    }

    // --- Failure Cases ---

    // Dummy Kind implementation that is not CompletableFutureHolder
    record DummyFutureKind<A>() implements Kind<CompletableFutureKind<?>, A> {}

    @Test
    void unwrap_shouldThrowForNullInput() {
      assertThatThrownBy(() -> unwrap(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_NULL_MSG);
    }

    @Test
    void unwrap_shouldThrowForUnknownKindType() {
      Kind<CompletableFutureKind<?>, Integer> unknownKind = new DummyFutureKind<>();
      assertThatThrownBy(() -> unwrap(unknownKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_TYPE_MSG + DummyFutureKind.class.getName());
    }

    @Test
    void unwrap_shouldThrowForHolderWithNullFuture() { // Updated test name
      CompletableFutureHolder<Boolean> holderWithNull = new CompletableFutureHolder<>(null);
      @SuppressWarnings("unchecked") // Cast needed for test setup
      Kind<CompletableFutureKind<?>, Boolean> kind = holderWithNull;

      assertThatThrownBy(() -> unwrap(kind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_HOLDER_STATE_MSG);
    }
  }

  @Nested
  @DisplayName("join()")
  class JoinTests {

    @Test
    void join_shouldReturnResultOnSuccess() {
      Kind<CompletableFutureKind<?>, String> kind =
          wrap(CompletableFuture.completedFuture("Success"));
      assertThat(join(kind)).isEqualTo("Success");
    }

    @Test
    void join_shouldBlockAndWaitForCompletion() {
      CompletableFuture<String> delayedFuture =
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  TimeUnit.MILLISECONDS.sleep(50);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  throw new CompletionException(e);
                }
                return "Delayed Result";
              });
      Kind<CompletableFutureKind<?>, String> kind = wrap(delayedFuture);
      long startTime = System.nanoTime();
      String result = join(kind);
      long duration = System.nanoTime() - startTime;
      assertThat(result).isEqualTo("Delayed Result");
      assertThat(duration).isGreaterThan(TimeUnit.MILLISECONDS.toNanos(40));
    }

    @Test
    void join_shouldThrowRuntimeExceptionDirectly() {
      RuntimeException ex = new IllegalStateException("Fail State");
      Kind<CompletableFutureKind<?>, String> kind = wrap(CompletableFuture.failedFuture(ex));
      assertThatThrownBy(() -> join(kind)).isInstanceOf(IllegalStateException.class).isSameAs(ex);
    }

    @Test
    void join_shouldThrowErrorDirectly() {
      Error err = new StackOverflowError("Fail Error");
      Kind<CompletableFutureKind<?>, String> kind = wrap(CompletableFuture.failedFuture(err));
      assertThatThrownBy(() -> join(kind)).isInstanceOf(StackOverflowError.class).isSameAs(err);
    }

    @Test
    void join_shouldKeepCheckedExceptionWrappedInCompletionException() {
      IOException ex = new IOException("IO Fail");
      Kind<CompletableFutureKind<?>, String> kind = wrap(CompletableFuture.failedFuture(ex));
      assertThatThrownBy(() -> join(kind)).isInstanceOf(CompletionException.class).hasCause(ex);
    }

    @Test
    void join_shouldThrowCancellationExceptionIfCancelled() {
      CompletableFuture<String> cancelledFuture = new CompletableFuture<>();
      cancelledFuture.cancel(true);
      Kind<CompletableFutureKind<?>, String> kind = wrap(cancelledFuture);
      assertThatThrownBy(() -> join(kind)).isInstanceOf(CancellationException.class);
    }

    @Test
    void join_shouldPropagateKindUnwrapExceptionFromFailedUnwrap() {
      // Test join when unwrap itself fails (e.g., null input)
      assertThatThrownBy(() -> join(null))
          .isInstanceOf(KindUnwrapException.class) // Expect the exception from unwrap
          .hasMessageContaining(INVALID_KIND_NULL_MSG);

      Kind<CompletableFutureKind<?>, Integer> unknownKind = new UnwrapTests.DummyFutureKind<>();
      assertThatThrownBy(() -> join(unknownKind))
          .isInstanceOf(KindUnwrapException.class) // Expect the exception from unwrap
          .hasMessageContaining(INVALID_KIND_TYPE_MSG);

      CompletableFutureHolder<Boolean> holderWithNull = new CompletableFutureHolder<>(null);
      @SuppressWarnings("unchecked") // Cast needed for test setup
      Kind<CompletableFutureKind<?>, Boolean> kindWithNullHolder = holderWithNull;
      assertThatThrownBy(() -> join(kindWithNullHolder))
          .isInstanceOf(KindUnwrapException.class) // Expect the exception from unwrap
          .hasMessageContaining(INVALID_HOLDER_STATE_MSG);
    }
  }

  @Nested
  @DisplayName("Private Constructor")
  class PrivateConstructorTest {

    @Test
    @DisplayName("should throw UnsupportedOperationException when invoked via reflection")
    void constructor_shouldThrowException() throws NoSuchMethodException {
      Constructor<CompletableFutureKindHelper> constructor =
          CompletableFutureKindHelper.class.getDeclaredConstructor();
      constructor.setAccessible(true);
      assertThatThrownBy(constructor::newInstance)
          .isInstanceOf(InvocationTargetException.class)
          .hasCauseInstanceOf(UnsupportedOperationException.class)
          .cause()
          .hasMessageContaining("This is a utility class and cannot be instantiated");
    }
  }
}
