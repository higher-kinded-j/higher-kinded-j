// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.future;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.*;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;

import java.io.IOException;
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
    void widen_shouldReturnHolderForCompletedFuture() {
      CompletableFuture<String> future = CompletableFuture.completedFuture("done");
      Kind<CompletableFutureKind.Witness, String> kind = FUTURE.widen(future);

      assertThat(kind).isInstanceOf(CompletableFutureHolder.class);
      assertThat(FUTURE.narrow(kind)).isSameAs(future);
    }

    @Test
    void widen_shouldReturnHolderForFailedFuture() {
      RuntimeException ex = new RuntimeException("fail");
      CompletableFuture<String> future = CompletableFuture.failedFuture(ex);
      Kind<CompletableFutureKind.Witness, String> kind = FUTURE.widen(future);

      assertThat(kind).isInstanceOf(CompletableFutureHolder.class);
      assertThat(FUTURE.narrow(kind)).isSameAs(future);
    }

    @Test
    void widen_shouldThrowForNullInput() {
      assertThatNullPointerException()
          .isThrownBy(() -> FUTURE.widen(null))
          .withMessageContaining(
              "Input %s cannot be null for widen".formatted("CompletableFuture"));
    }
  }

  @Nested
  @DisplayName("unwrap()")
  class UnwrapTests {

    // --- Success Cases ---
    @Test
    void narrowshouldReturnOriginalCompletedFuture() {
      CompletableFuture<Integer> original = CompletableFuture.completedFuture(42);
      Kind<CompletableFutureKind.Witness, Integer> kind = FUTURE.widen(original);
      assertThat(FUTURE.narrow(kind)).isSameAs(original);
    }

    @Test
    void narrowShouldReturnOriginalFailedFuture() {
      IOException ex = new IOException("io fail");
      CompletableFuture<Integer> original = CompletableFuture.failedFuture(ex);
      Kind<CompletableFutureKind.Witness, Integer> kind = FUTURE.widen(original);
      assertThat(FUTURE.narrow(kind)).isSameAs(original);
    }

    // --- Failure Cases ---

    record DummyFutureKind<A>() implements Kind<CompletableFutureKind.Witness, A> {}

    @Test
    void narrowShouldThrowForNullInput() {
      assertThatThrownBy(() -> FUTURE.narrow(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Cannot narrow null Kind for %s".formatted("CompletableFuture"));
    }

    @Test
    void narrowShouldThrowForUnknownKindType() {
      Kind<CompletableFutureKind.Witness, Integer> unknownKind = new DummyFutureKind<>();
      assertThatThrownBy(() -> FUTURE.narrow(unknownKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Kind instance cannot be narrowed to CompletableFuture");
    }

    @Test
    void shouldThrowForHolderWithNullFuture() {
      assertThatThrownBy(() -> new CompletableFutureHolder<>(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Input CompletableFuture cannot be null for widen");
    }
  }

  @Nested
  @DisplayName("join()")
  class JoinTests {

    @Test
    void join_shouldReturnResultOnSuccess() {
      Kind<CompletableFutureKind.Witness, String> kind =
          FUTURE.widen(CompletableFuture.completedFuture("Success"));
      assertThat(FUTURE.join(kind)).isEqualTo("Success");
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
      Kind<CompletableFutureKind.Witness, String> kind = FUTURE.widen(delayedFuture);
      long startTime = System.nanoTime();
      String result = FUTURE.join(kind);
      long duration = System.nanoTime() - startTime;
      assertThat(result).isEqualTo("Delayed Result");
      assertThat(duration).isGreaterThan(TimeUnit.MILLISECONDS.toNanos(40));
    }

    @Test
    void join_shouldThrowRuntimeExceptionDirectly() {
      RuntimeException ex = new IllegalStateException("Fail State");
      Kind<CompletableFutureKind.Witness, String> kind =
          FUTURE.widen(CompletableFuture.failedFuture(ex));
      assertThatThrownBy(() -> FUTURE.join(kind))
          .isInstanceOf(IllegalStateException.class)
          .isSameAs(ex);
    }

    @Test
    void join_shouldThrowErrorDirectly() {
      Error err = new StackOverflowError("Fail Error");
      Kind<CompletableFutureKind.Witness, String> kind =
          FUTURE.widen(CompletableFuture.failedFuture(err));
      assertThatThrownBy(() -> FUTURE.join(kind))
          .isInstanceOf(StackOverflowError.class)
          .isSameAs(err);
    }

    @Test
    void join_shouldKeepCheckedExceptionWrappedInCompletionException() {
      IOException ex = new IOException("IO Fail");
      Kind<CompletableFutureKind.Witness, String> kind =
          FUTURE.widen(CompletableFuture.failedFuture(ex));
      assertThatThrownBy(() -> FUTURE.join(kind))
          .isInstanceOf(CompletionException.class)
          .hasCause(ex);
    }

    @Test
    void join_shouldThrowCancellationExceptionIfCancelled() {
      CompletableFuture<String> cancelledFuture = new CompletableFuture<>();
      cancelledFuture.cancel(true);
      Kind<CompletableFutureKind.Witness, String> kind = FUTURE.widen(cancelledFuture);
      assertThatThrownBy(() -> FUTURE.join(kind)).isInstanceOf(CancellationException.class);
    }

    @Test
    void join_shouldPropagateKindUnwrapExceptionFromFailedUnwrap() {
      assertThatThrownBy(() -> FUTURE.join(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Cannot narrow null Kind for %s".formatted("CompletableFuture"));
    }
  }
}
