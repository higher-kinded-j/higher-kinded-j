package org.simulation.hkt.future;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.simulation.hkt.Kind;
import org.simulation.hkt.trymonad.TryKindHelper;


import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CancellationException; // Added
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit; // Added

import static org.assertj.core.api.Assertions.*;
import static org.simulation.hkt.future.CompletableFutureKindHelper.*;

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
      // Unwrap to verify
      assertThat(unwrap(kind)).isSameAs(future);
    }

    @Test
    void wrap_shouldReturnHolderForFailedFuture() {
      RuntimeException ex = new RuntimeException("fail");
      CompletableFuture<String> future = CompletableFuture.failedFuture(ex);
      Kind<CompletableFutureKind<?>, String> kind = wrap(future);

      assertThat(kind).isInstanceOf(CompletableFutureHolder.class);
      // Unwrap to verify
      assertThat(unwrap(kind)).isSameAs(future);
    }

    @Test
    void wrap_shouldThrowForNullInput() {
      // Assuming wrap requires a non-null CompletableFuture
      assertThatNullPointerException().isThrownBy(() -> wrap(null))
          .withMessageContaining("Input CompletableFuture cannot be null"); // Check message from explicit check
    }
  }

  @Nested
  @DisplayName("unwrap()")
  class UnwrapTests {

    // --- Success Cases (already implicitly tested by wrap tests) ---
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

    // --- Robustness / Failure Cases ---

    // Dummy Kind implementation that is not CompletableFutureHolder
    record DummyFutureKind<A>() implements Kind<CompletableFutureKind<?>, A> {}

    @Test
    void unwrap_shouldReturnFailedFutureForNullInput() {
      CompletableFuture<String> future = unwrap(null);
      assertThat(future.isCompletedExceptionally()).isTrue();
      assertThatThrownBy(future::get) // Use get() to check exception type easily
          .isInstanceOf(ExecutionException.class)
          .cause()
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Cannot unwrap null Kind");
    }

    @Test
    void unwrap_shouldReturnFailedFutureForUnknownKindType() {
      Kind<CompletableFutureKind<?>, Integer> unknownKind = new DummyFutureKind<>();
      CompletableFuture<Integer> future = unwrap(unknownKind);
      assertThat(future.isCompletedExceptionally()).isTrue();
      assertThatThrownBy(future::get)
          .isInstanceOf(ExecutionException.class)
          .cause()
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Kind instance is not a CompletableFutureHolder");
    }

    @Test
    void unwrap_shouldReturnInternalFutureWhenHolderHasNullFuture() {
      // Test the specific case where the holder exists but its internal future is null
      CompletableFutureHolder<Boolean> holderWithNull = new CompletableFutureHolder<>(null);
      // Need to cast to satisfy the Kind type parameter in unwrap
      @SuppressWarnings("unchecked")
      Kind<CompletableFutureKind<?>, Boolean> kind = holderWithNull;

      // The current unwrap implementation will return the null future directly
      CompletableFuture<Boolean> future = unwrap(kind);
      assertThat(future).isNull();
      // Note: If unwrap were modified to return a *failed* future in this case,
      // the assertion would change.
    }
  }

  @Nested
  @DisplayName("join()")
  class JoinTests {

    @Test
    void join_shouldReturnResultOnSuccess() {
      Kind<CompletableFutureKind<?>, String> kind = wrap(CompletableFuture.completedFuture("Success"));
      assertThat(join(kind)).isEqualTo("Success");
    }

    @Test
    void join_shouldBlockAndWaitForCompletion() { // Added Test
      CompletableFuture<String> delayedFuture = CompletableFuture.supplyAsync(() -> {
        try {
          TimeUnit.MILLISECONDS.sleep(50); // Simulate work
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
      // Check that it actually took some time (more than, say, 40ms)
      assertThat(duration).isGreaterThan(TimeUnit.MILLISECONDS.toNanos(40));
    }


    @Test
    void join_shouldThrowRuntimeExceptionDirectly() {
      RuntimeException ex = new IllegalStateException("Fail State");
      Kind<CompletableFutureKind<?>, String> kind = wrap(CompletableFuture.failedFuture(ex));

      assertThatThrownBy(() -> join(kind))
          .isInstanceOf(IllegalStateException.class) // Should be the original RuntimeException
          .isSameAs(ex);
    }

    @Test
    void join_shouldThrowErrorDirectly() {
      Error err = new StackOverflowError("Fail Error");
      Kind<CompletableFutureKind<?>, String> kind = wrap(CompletableFuture.failedFuture(err));

      assertThatThrownBy(() -> join(kind))
          .isInstanceOf(StackOverflowError.class) // Should be the original Error
          .isSameAs(err);
    }

    @Test
    void join_shouldKeepCheckedExceptionWrappedInCompletionException() {
      // Correction: The join helper ONLY rethrows RuntimeException and Error directly.
      // Checked exceptions remain wrapped.
      IOException ex = new IOException("IO Fail");
      Kind<CompletableFutureKind<?>, String> kind = wrap(CompletableFuture.failedFuture(ex));

      assertThatThrownBy(() -> join(kind))
          .isInstanceOf(CompletionException.class) // Should be the original CompletionException
          .hasCause(ex); // Check that the cause is the original checked exception
    }

    @Test
    void join_shouldThrowCancellationExceptionIfCancelled() { // Added Test
      CompletableFuture<String> cancelledFuture = new CompletableFuture<>();
      cancelledFuture.cancel(true); // Cancel the future

      Kind<CompletableFutureKind<?>, String> kind = wrap(cancelledFuture);

      // CompletableFuture.join throws CancellationException if cancelled
      // The helper's catch block doesn't treat this specially, so it propagates
      assertThatThrownBy(() -> join(kind))
          .isInstanceOf(CancellationException.class);
    }


    @Test
    void join_shouldPropagateUnderlyingExceptionFromFailedUnwrap() {
      // Test join when unwrap itself fails (e.g., null input)
      // join() calls unwrap(null), gets failedFuture(NPE), calls join() on it,
      // catches CompletionException(NPE), then rethrows the NPE cause.
      assertThatThrownBy(() -> join(null))
          .isInstanceOf(NullPointerException.class) // Expect the rethrown cause
          .hasMessageContaining("Cannot unwrap null Kind");

      Kind<CompletableFutureKind<?>, Integer> unknownKind = new UnwrapTests.DummyFutureKind<>();
      // join() calls unwrap(unknownKind), gets failedFuture(IAE), calls join() on it,
      // catches CompletionException(IAE), then rethrows the IAE cause.
      assertThatThrownBy(() -> join(unknownKind))
          .isInstanceOf(IllegalArgumentException.class) // Expect the rethrown cause
          .hasMessageContaining("Kind instance is not a CompletableFutureHolder");
    }

    @Test
    void join_shouldThrowNPEIfUnwrapReturnsNull() {
      // Test the case where unwrap returns null (e.g., holder with null future)
      CompletableFutureHolder<Boolean> holderWithNull = new CompletableFutureHolder<>(null);
      @SuppressWarnings("unchecked")
      Kind<CompletableFutureKind<?>, Boolean> kind = holderWithNull;

      // join will call unwrap(kind), get null, then call null.join() -> NPE
      assertThatThrownBy(() -> join(kind))
          .isInstanceOf(NullPointerException.class);
      // Note: No specific message check as it comes from calling join() on null
    }
  }

  @Nested
  @DisplayName("Private Constructor")
  class PrivateConstructorTest {

    @Test
    @DisplayName("should throw UnsupportedOperationException when invoked via reflection")
    void constructor_shouldThrowException() throws NoSuchMethodException {
      // Get the private constructor
      Constructor<CompletableFutureKindHelper> constructor = CompletableFutureKindHelper.class.getDeclaredConstructor();

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
