// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.*;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CompletableFutureMonadErrorTest {

  private final CompletableFutureMonadError futureMonad = new CompletableFutureMonadError();

  // --- Helper Functions ---
  private <A> CompletableFuture<A> unwrapFuture(Kind<CompletableFutureKind.Witness, A> kind) {
    return FUTURE.narrow(kind);
  }

  private <A> A joinFuture(Kind<CompletableFutureKind.Witness, A> kind) {
    return FUTURE.join(kind);
  }

  private <A> Kind<CompletableFutureKind.Witness, A> delayedSuccess(A value, long delayMillis) {
    CompletableFuture<A> future =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                TimeUnit.MILLISECONDS.sleep(delayMillis);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
              }
              return value;
            });
    return FUTURE.widen(future);
  }

  private <A> Kind<CompletableFutureKind.Witness, A> delayedFailure(
      Throwable error, long delayMillis) {
    CompletableFuture<A> future =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                TimeUnit.MILLISECONDS.sleep(delayMillis);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
              }
              if (error instanceof RuntimeException re) throw re;
              if (error instanceof Error e) throw e;
              throw new CompletionException(error);
            });
    return FUTURE.widen(future);
  }

  @Nested
  @DisplayName("Applicative 'of' tests")
  class OfTests {
    @Test
    void of_shouldCreateCompletedFuture() {
      Kind<CompletableFutureKind.Witness, String> kind = futureMonad.of("done");
      assertThat(joinFuture(kind)).isEqualTo("done");
      assertThat(unwrapFuture(kind).isDone()).isTrue();
      assertThat(unwrapFuture(kind).isCompletedExceptionally()).isFalse();
    }
  }

  @Nested
  @DisplayName("Functor 'map' tests")
  class MapTests {
    @Test
    void map_shouldApplyFunctionToSuccessfulFuture() {
      Kind<CompletableFutureKind.Witness, Integer> input = futureMonad.of(10);
      Kind<CompletableFutureKind.Witness, String> result = futureMonad.map(i -> "v" + i, input);
      assertThat(joinFuture(result)).isEqualTo("v10");
    }

    @Test
    void map_shouldPropagateFailure() {
      RuntimeException testException = new RuntimeException("MapFail");
      Kind<CompletableFutureKind.Witness, Integer> input = futureMonad.raiseError(testException);
      Kind<CompletableFutureKind.Witness, String> result = futureMonad.map(i -> "v" + i, input);

      assertThatThrownBy(() -> joinFuture(result))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(testException);
    }

    @Test
    void map_shouldFailIfFunctionThrows() {
      RuntimeException mapEx = new RuntimeException("Map function failed");
      Kind<CompletableFutureKind.Witness, Integer> input = futureMonad.of(10);
      Kind<CompletableFutureKind.Witness, String> result =
          futureMonad.map(
              i -> {
                throw mapEx;
              },
              input);

      assertThatThrownBy(() -> joinFuture(result))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(mapEx);
    }
  }

  @Nested
  @DisplayName("Applicative 'ap' tests")
  class ApTests {
    RuntimeException funcException = new RuntimeException("FuncFail");
    RuntimeException valueException = new RuntimeException("ValueFail");
    RuntimeException applyException = new RuntimeException("ApplyFail");

    Kind<CompletableFutureKind.Witness, Function<Integer, String>> funcKindSuccess =
        futureMonad.of(i -> "N" + i);
    Kind<CompletableFutureKind.Witness, Function<Integer, String>> funcKindFailure =
        futureMonad.raiseError(funcException);
    Kind<CompletableFutureKind.Witness, Function<Integer, String>> funcKindThrows =
        futureMonad.of(
            i -> {
              throw applyException;
            });

    Kind<CompletableFutureKind.Witness, Integer> valueKindSuccess = futureMonad.of(20);
    Kind<CompletableFutureKind.Witness, Integer> valueKindFailure =
        futureMonad.raiseError(valueException);

    @Test
    void ap_shouldApplySuccessFuncToSuccessValue() {
      Kind<CompletableFutureKind.Witness, String> result =
          futureMonad.ap(funcKindSuccess, valueKindSuccess);
      assertThat(joinFuture(result)).isEqualTo("N20");
    }

    @Test
    void ap_shouldReturnFailureIfFuncFails() {
      Kind<CompletableFutureKind.Witness, String> result =
          futureMonad.ap(funcKindFailure, valueKindSuccess);
      assertThatThrownBy(() -> joinFuture(result))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(funcException);
    }

    @Test
    void ap_shouldReturnFailureIfValueFails() {
      Kind<CompletableFutureKind.Witness, String> result =
          futureMonad.ap(funcKindSuccess, valueKindFailure);
      assertThatThrownBy(() -> joinFuture(result))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(valueException);
    }

    @Test
    void ap_shouldReturnFailureIfBothFail() {
      Kind<CompletableFutureKind.Witness, String> result =
          futureMonad.ap(funcKindFailure, valueKindFailure);
      assertThatThrownBy(() -> joinFuture(result))
          .isInstanceOf(RuntimeException.class)
          .isIn(funcException, valueException);
    }

    @Test
    void ap_shouldReturnFailureIfFunctionApplicationThrows() {
      Kind<CompletableFutureKind.Witness, String> result =
          futureMonad.ap(funcKindThrows, valueKindSuccess);
      assertThatThrownBy(() -> joinFuture(result))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(applyException);
    }

    @Test
    void ap_shouldWorkWithDelayedValues() {
      Kind<CompletableFutureKind.Witness, Function<Integer, String>> delayedFunc =
          delayedSuccess(i -> "Delayed" + i, 50);
      Kind<CompletableFutureKind.Witness, Integer> delayedValue = delayedSuccess(30, 30);
      Kind<CompletableFutureKind.Witness, String> result =
          futureMonad.ap(delayedFunc, delayedValue);
      assertThat(joinFuture(result)).isEqualTo("Delayed30");
    }
  }

  @Nested
  @DisplayName("Monad 'flatMap' tests")
  class FlatMapTests {
    RuntimeException innerException = new RuntimeException("FlatMapInnerFail");
    RuntimeException outerException = new RuntimeException("FlatMapOuterFail");
    RuntimeException funcApplyException = new RuntimeException("FuncApplyFail");

    Function<Integer, Kind<CompletableFutureKind.Witness, String>> fSuccess =
        i -> futureMonad.of("Flat" + i);
    Function<Integer, Kind<CompletableFutureKind.Witness, String>> fFailure =
        i -> futureMonad.raiseError(innerException);
    Function<Integer, Kind<CompletableFutureKind.Witness, String>> fThrows =
        i -> {
          throw funcApplyException;
        };

    Kind<CompletableFutureKind.Witness, Integer> inputSuccess = futureMonad.of(5);
    Kind<CompletableFutureKind.Witness, Integer> inputFailure =
        futureMonad.raiseError(outerException);

    @Test
    void flatMap_shouldApplySuccessFuncToSuccessInput() {
      Kind<CompletableFutureKind.Witness, String> result =
          futureMonad.flatMap(fSuccess, inputSuccess);
      assertThat(joinFuture(result)).isEqualTo("Flat5");
    }

    @Test
    void flatMap_shouldReturnFailureIfFuncReturnsFailure() {
      Kind<CompletableFutureKind.Witness, String> result =
          futureMonad.flatMap(fFailure, inputSuccess);
      assertThatThrownBy(() -> joinFuture(result))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(innerException);
    }

    @Test
    void flatMap_shouldReturnFailureIfInputFails() {
      Kind<CompletableFutureKind.Witness, String> result =
          futureMonad.flatMap(fSuccess, inputFailure);
      assertThatThrownBy(() -> joinFuture(result))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(outerException);
    }

    @Test
    void flatMap_shouldReturnFailureIfFunctionApplicationThrows() {
      Kind<CompletableFutureKind.Witness, String> result =
          futureMonad.flatMap(fThrows, inputSuccess);
      assertThatThrownBy(() -> joinFuture(result))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(funcApplyException);
    }

    @Test
    void flatMap_shouldWorkWithDelayedValues() {
      Kind<CompletableFutureKind.Witness, Integer> delayedInput = delayedSuccess(15, 20);
      Function<Integer, Kind<CompletableFutureKind.Witness, String>> delayedFunc =
          i -> delayedSuccess("Chain" + i, 30);

      Kind<CompletableFutureKind.Witness, String> result =
          futureMonad.flatMap(delayedFunc, delayedInput);
      assertThat(joinFuture(result)).isEqualTo("Chain15");
    }
  }

  @Nested
  @DisplayName("MonadError tests")
  class MonadErrorTests {
    RuntimeException testError = new RuntimeException("TestError");
    Error testErrorSubclass = new StackOverflowError("Test StackOverflow");
    IOException checkedException = new IOException("Checked IO Error");
    RuntimeException handlerError = new RuntimeException("HandlerError");
    RuntimeException handlerApplyError = new RuntimeException("HandlerApplyError");

    Kind<CompletableFutureKind.Witness, Integer> successKind = futureMonad.of(100);
    Kind<CompletableFutureKind.Witness, Integer> failedKind = futureMonad.raiseError(testError);
    Kind<CompletableFutureKind.Witness, Integer> failedKindWithError =
        futureMonad.raiseError(testErrorSubclass);
    Kind<CompletableFutureKind.Witness, Integer> failedKindChecked =
        futureMonad.raiseError(checkedException);

    @Test
    void raiseError_shouldCreateFailedFuture() {
      Kind<CompletableFutureKind.Witness, String> kind = futureMonad.raiseError(testError);
      CompletableFuture<String> future = unwrapFuture(kind);

      assertThat(future.isDone()).isTrue();
      assertThat(future.isCompletedExceptionally()).isTrue();
      assertThatThrownBy(() -> joinFuture(kind))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(testError);
    }

    @Test
    void raiseError_shouldCreateFailedFutureWithErrorSubclass() {
      Kind<CompletableFutureKind.Witness, String> kind = futureMonad.raiseError(testErrorSubclass);
      CompletableFuture<String> future = unwrapFuture(kind);
      assertThat(future.isCompletedExceptionally()).isTrue();
      assertThatThrownBy(() -> joinFuture(kind))
          .isInstanceOf(StackOverflowError.class)
          .isSameAs(testErrorSubclass);
    }

    @Test
    void raiseError_shouldCreateFailedFutureWithCheckedException() {
      Kind<CompletableFutureKind.Witness, String> kind = futureMonad.raiseError(checkedException);
      CompletableFuture<String> future = unwrapFuture(kind);
      assertThat(future.isCompletedExceptionally()).isTrue();
      assertThatThrownBy(() -> joinFuture(kind))
          .isInstanceOf(CompletionException.class)
          .hasCause(checkedException);
    }

    @Test
    void handleErrorWith_shouldHandleFailureWithSuccessHandler() {
      AtomicReference<Throwable> caught = new AtomicReference<>();
      Function<Throwable, Kind<CompletableFutureKind.Witness, Integer>> handler =
          err -> {
            caught.set(err);
            return futureMonad.of(0);
          };

      Kind<CompletableFutureKind.Witness, Integer> result =
          futureMonad.handleErrorWith(failedKind, handler);
      assertThat(joinFuture(result)).isEqualTo(0);
      assertThat(caught.get()).isSameAs(testError);
    }

    @Test
    void handleErrorWith_shouldHandleFailureWithFailedHandler() {
      AtomicReference<Throwable> caught = new AtomicReference<>();
      Function<Throwable, Kind<CompletableFutureKind.Witness, Integer>> handler =
          err -> {
            caught.set(err);
            return futureMonad.raiseError(handlerError);
          };

      Kind<CompletableFutureKind.Witness, Integer> result =
          futureMonad.handleErrorWith(failedKind, handler);
      assertThatThrownBy(() -> joinFuture(result))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(handlerError);
      assertThat(caught.get()).isSameAs(testError);
    }

    @Test
    void handleErrorWith_shouldHandleFailureWhenHandlerThrows() {
      AtomicReference<Throwable> caught = new AtomicReference<>();
      Function<Throwable, Kind<CompletableFutureKind.Witness, Integer>> handler =
          err -> {
            caught.set(err);
            throw handlerApplyError;
          };

      Kind<CompletableFutureKind.Witness, Integer> result =
          futureMonad.handleErrorWith(failedKind, handler);
      assertThatThrownBy(() -> joinFuture(result))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(handlerApplyError);
      assertThat(caught.get()).isSameAs(testError);
    }

    @Test
    void handleErrorWith_shouldFailWhenHandlerIsNull() {
      Kind<CompletableFutureKind.Witness, Integer> result =
          futureMonad.handleErrorWith(failedKind, null);
      assertThatThrownBy(() -> joinFuture(result)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void handleErrorWith_shouldHandleErrorSubclass() {
      AtomicReference<Throwable> caughtError = new AtomicReference<>();
      Function<Throwable, Kind<CompletableFutureKind.Witness, Integer>> handler =
          err -> {
            caughtError.set(err);
            return futureMonad.of(50);
          };

      Kind<CompletableFutureKind.Witness, Integer> result =
          futureMonad.handleErrorWith(failedKindWithError, handler);
      assertThat(joinFuture(result)).isEqualTo(50);
      assertThat(caughtError.get()).isSameAs(testErrorSubclass);
    }

    @Test
    void handleErrorWith_shouldHandleCheckedException() {
      AtomicReference<Throwable> caughtError = new AtomicReference<>();
      Function<Throwable, Kind<CompletableFutureKind.Witness, Integer>> handler =
          err -> {
            caughtError.set(err);
            return futureMonad.of(75);
          };

      Kind<CompletableFutureKind.Witness, Integer> result =
          futureMonad.handleErrorWith(failedKindChecked, handler);
      assertThat(joinFuture(result)).isEqualTo(75);
      assertThat(caughtError.get()).isSameAs(checkedException);
    }

    @Test
    void handleErrorWith_shouldHandleCompletionExceptionWithCause() {
      IOException ioCause = new IOException("Underlying IO failure");
      Kind<CompletableFutureKind.Witness, Integer> failedKindWithCause =
          FUTURE.widen(CompletableFuture.failedFuture(new CompletionException(ioCause)));
      AtomicReference<Throwable> caughtError = new AtomicReference<>();

      Function<Throwable, Kind<CompletableFutureKind.Witness, Integer>> handler =
          err -> {
            caughtError.set(err);
            return futureMonad.of(77);
          };

      Kind<CompletableFutureKind.Witness, Integer> result =
          futureMonad.handleErrorWith(failedKindWithCause, handler);
      assertThat(joinFuture(result)).isEqualTo(77);
      assertThat(caughtError.get()).isSameAs(ioCause);
    }

    @Test
    void handleErrorWith_shouldHandleCompletionExceptionWithCauseDirectly() {
      CompletableFuture<Integer> internalFailure = new CompletableFuture<>();
      internalFailure.completeExceptionally(testError);

      Kind<CompletableFutureKind.Witness, Integer> failedKindWithCause =
          FUTURE.widen(internalFailure);
      AtomicReference<Throwable> caughtError = new AtomicReference<>();

      Function<Throwable, Kind<CompletableFutureKind.Witness, Integer>> handler =
          err -> {
            caughtError.set(err);
            return futureMonad.of(80);
          };

      Kind<CompletableFutureKind.Witness, Integer> result =
          futureMonad.handleErrorWith(failedKindWithCause, handler);
      assertThat(joinFuture(result)).isEqualTo(80);
      assertThat(caughtError.get()).isSameAs(testError);
    }

    @Test
    void handleErrorWith_shouldHandleCompletionExceptionWithoutCause() {
      CompletionException completionExNoCause =
          new CompletionException("Failed Future No Cause", null);
      Kind<CompletableFutureKind.Witness, Integer> failedKindNoCause =
          futureMonad.raiseError(completionExNoCause);
      AtomicReference<Throwable> caughtError = new AtomicReference<>();

      Function<Throwable, Kind<CompletableFutureKind.Witness, Integer>> handler =
          err -> {
            caughtError.set(err);
            return futureMonad.of(88);
          };

      Kind<CompletableFutureKind.Witness, Integer> result =
          futureMonad.handleErrorWith(failedKindNoCause, handler);
      assertThat(joinFuture(result)).isEqualTo(88);
      assertThat(caughtError.get()).isSameAs(completionExNoCause);
    }

    @Test
    void handleErrorWith_shouldHandleCompletionExceptionWithoutCauseDirectly() {
      CompletionException completionEx = new CompletionException("No cause here", null);
      CompletableFuture<Integer> internalFailure = new CompletableFuture<>();
      internalFailure.completeExceptionally(completionEx);

      Kind<CompletableFutureKind.Witness, Integer> failedKindNoCause =
          FUTURE.widen(internalFailure);
      AtomicReference<Throwable> caughtError = new AtomicReference<>();

      Function<Throwable, Kind<CompletableFutureKind.Witness, Integer>> handler =
          err -> {
            caughtError.set(err);
            return futureMonad.of(90);
          };
      Kind<CompletableFutureKind.Witness, Integer> result =
          futureMonad.handleErrorWith(failedKindNoCause, handler);
      assertThat(joinFuture(result)).isEqualTo(90);
      assertThat(caughtError.get()).isSameAs(completionEx);
    }

    @Test
    void handleErrorWith_shouldHandleFailureWithDelayedRecovery() {
      Function<Throwable, Kind<CompletableFutureKind.Witness, Integer>> handler =
          err -> delayedSuccess(0, 30);

      Kind<CompletableFutureKind.Witness, Integer> result =
          futureMonad.handleErrorWith(failedKind, handler);
      assertThat(joinFuture(result)).isEqualTo(0);
    }

    @Test
    void handleErrorWith_shouldIgnoreSuccess() {
      Function<Throwable, Kind<CompletableFutureKind.Witness, Integer>> handler =
          err -> futureMonad.of(-1);

      Kind<CompletableFutureKind.Witness, Integer> result =
          futureMonad.handleErrorWith(successKind, handler);
      assertThat(joinFuture(result)).isEqualTo(100);
    }

    @Test
    void handleErrorWith_shouldIgnoreAlreadyCompletedSuccessFuture() {
      CompletableFuture<Integer> alreadyDone = CompletableFuture.completedFuture(200);
      Kind<CompletableFutureKind.Witness, Integer> alreadyDoneKind = FUTURE.widen(alreadyDone);

      AtomicBoolean handlerCalled = new AtomicBoolean(false);
      Function<Throwable, Kind<CompletableFutureKind.Witness, Integer>> handler =
          err -> {
            handlerCalled.set(true);
            return futureMonad.of(-1);
          };

      Kind<CompletableFutureKind.Witness, Integer> result =
          futureMonad.handleErrorWith(alreadyDoneKind, handler);
      assertThat(handlerCalled).isFalse();
      assertThat(joinFuture(result)).isEqualTo(200);
      assertThat(unwrapFuture(result)).isSameAs(alreadyDone);
    }

    @Test
    void handleErrorWith_shouldReturnOriginalKindIfAlreadyCompletedSuccessfully() {
      CompletableFuture<Integer> alreadyDone = CompletableFuture.completedFuture(200);
      Kind<CompletableFutureKind.Witness, Integer> alreadyDoneKind = FUTURE.widen(alreadyDone);

      AtomicBoolean handlerCalled = new AtomicBoolean(false);
      Function<Throwable, Kind<CompletableFutureKind.Witness, Integer>> handler =
          err -> {
            handlerCalled.set(true);
            return futureMonad.of(-1);
          };

      Kind<CompletableFutureKind.Witness, Integer> result =
          futureMonad.handleErrorWith(alreadyDoneKind, handler);

      assertThat(handlerCalled).isFalse();
      assertThat(joinFuture(result)).isEqualTo(200);
      assertThat(result).isSameAs(alreadyDoneKind);
    }

    @Test
    @DisplayName(
        "handleErrorWith should ignore already completed success future (optimization path)")
    void handleErrorWith_shouldReturnOriginalKindForAlreadyCompletedSuccess() {
      String successValue = "Already Done";
      CompletableFuture<String> alreadyDoneFuture = CompletableFuture.completedFuture(successValue);
      Kind<CompletableFutureKind.Witness, String> alreadyDoneKind = FUTURE.widen(alreadyDoneFuture);

      AtomicBoolean handlerCalled = new AtomicBoolean(false);
      Function<Throwable, Kind<CompletableFutureKind.Witness, String>> handler =
          err -> {
            handlerCalled.set(true);
            return futureMonad.of("Recovered?");
          };

      Kind<CompletableFutureKind.Witness, String> resultKind =
          futureMonad.handleErrorWith(alreadyDoneKind, handler);

      assertThat(handlerCalled).isFalse();
      assertThat(resultKind).isSameAs(alreadyDoneKind);
      assertThat(joinFuture(resultKind)).isEqualTo(successValue);
    }

    @Test
    void handleErrorWith_shouldHandleDirectRuntimeException() {
      RuntimeException directError = new IllegalStateException("Direct Failure");
      Kind<CompletableFutureKind.Witness, Integer> failedKindDirect =
          futureMonad.raiseError(directError);
      AtomicReference<Throwable> caughtError = new AtomicReference<>();

      Function<Throwable, Kind<CompletableFutureKind.Witness, Integer>> handler =
          err -> {
            caughtError.set(err);
            return futureMonad.of(99);
          };

      Kind<CompletableFutureKind.Witness, Integer> result =
          futureMonad.handleErrorWith(failedKindDirect, handler);

      assertThat(joinFuture(result)).isEqualTo(99);
      assertThat(caughtError.get()).isSameAs(directError);
    }

    @Test
    void handleErrorWith_shouldPropagateExceptionFromHandler() {
      RuntimeException handlerException = new RuntimeException("Handler itself failed!");
      Kind<CompletableFutureKind.Witness, Integer> failedKind = futureMonad.raiseError(testError);

      Function<Throwable, Kind<CompletableFutureKind.Witness, Integer>> handler =
          err -> {
            throw handlerException;
          };

      Kind<CompletableFutureKind.Witness, Integer> result =
          futureMonad.handleErrorWith(failedKind, handler);
      assertThatThrownBy(() -> joinFuture(result))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(handlerException);
    }

    @Test
    void handleError_shouldHandleFailureWithPureValue() {
      Function<Throwable, Integer> handler = err -> -99;
      Kind<CompletableFutureKind.Witness, Integer> result =
          futureMonad.handleError(failedKind, handler);
      assertThat(joinFuture(result)).isEqualTo(-99);
    }

    @Test
    void handleError_shouldHandleFailureWhenPureHandlerThrows() {
      Function<Throwable, Integer> handler =
          err -> {
            throw handlerApplyError;
          };
      Kind<CompletableFutureKind.Witness, Integer> result =
          futureMonad.handleError(failedKind, handler);
      assertThatThrownBy(() -> joinFuture(result))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(handlerApplyError);
    }

    @Test
    void handleError_shouldIgnoreSuccess() {
      Function<Throwable, Integer> handler = err -> -1;
      Kind<CompletableFutureKind.Witness, Integer> result =
          futureMonad.handleError(successKind, handler);
      assertThat(joinFuture(result)).isEqualTo(100);
    }

    @Test
    void recoverWith_shouldReplaceFailureWithFallbackKind() {
      Kind<CompletableFutureKind.Witness, Integer> fallback = futureMonad.of(0);
      Kind<CompletableFutureKind.Witness, Integer> result =
          futureMonad.recoverWith(failedKind, fallback);
      assertThat(joinFuture(result)).isEqualTo(0);
    }

    @Test
    void recoverWith_shouldReplaceFailureWithFailedFallbackKind() {
      Kind<CompletableFutureKind.Witness, Integer> fallback = futureMonad.raiseError(handlerError);
      Kind<CompletableFutureKind.Witness, Integer> result =
          futureMonad.recoverWith(failedKind, fallback);
      assertThatThrownBy(() -> joinFuture(result))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(handlerError);
    }

    @Test
    void recoverWith_shouldIgnoreSuccess() {
      Kind<CompletableFutureKind.Witness, Integer> fallback = futureMonad.of(0);
      Kind<CompletableFutureKind.Witness, Integer> result =
          futureMonad.recoverWith(successKind, fallback);
      assertThat(joinFuture(result)).isEqualTo(100);
    }

    @Test
    void recover_shouldReplaceFailureWithOfValue() {
      Kind<CompletableFutureKind.Witness, Integer> result = futureMonad.recover(failedKind, 0);
      assertThat(joinFuture(result)).isEqualTo(0);
    }

    @Test
    void recover_shouldIgnoreSuccess() {
      Kind<CompletableFutureKind.Witness, Integer> result = futureMonad.recover(successKind, 0);
      assertThat(joinFuture(result)).isEqualTo(100);
    }

    @Test
    void handleErrorWith_shouldHandleSpecificExceptionType() {
      IOException ioError = new IOException("IO Error");
      Kind<CompletableFutureKind.Witness, String> failedKindIO = futureMonad.raiseError(ioError);

      Function<Throwable, Kind<CompletableFutureKind.Witness, String>> handler =
          err -> {
            if (err instanceof IOException) {
              return futureMonad.of("Handled IOException");
            } else {
              return futureMonad.raiseError(err);
            }
          };

      Kind<CompletableFutureKind.Witness, String> result =
          futureMonad.handleErrorWith(failedKindIO, handler);
      assertThat(joinFuture(result)).isEqualTo("Handled IOException");

      Kind<CompletableFutureKind.Witness, String> failedKindRuntime =
          futureMonad.raiseError(testError);
      Kind<CompletableFutureKind.Witness, String> resultRuntime =
          futureMonad.handleErrorWith(failedKindRuntime, handler);
      assertThatThrownBy(() -> joinFuture(resultRuntime))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(testError);
    }

    @Test
    @DisplayName("handleErrorWith should handle future that fails later")
    void handleErrorWith_shouldHandleDelayedFailure() {
      RuntimeException delayedException = new RuntimeException("DelayedFail");
      Kind<CompletableFutureKind.Witness, Integer> delayedFailedKind =
          delayedFailure(delayedException, 50);

      assertThat(unwrapFuture(delayedFailedKind).isDone()).isFalse();

      AtomicBoolean handlerCalled = new AtomicBoolean(false);
      Function<Throwable, Kind<CompletableFutureKind.Witness, Integer>> handler =
          err -> {
            handlerCalled.set(true);
            assertThat(err).isSameAs(delayedException);
            return futureMonad.of(99);
          };

      Kind<CompletableFutureKind.Witness, Integer> result =
          futureMonad.handleErrorWith(delayedFailedKind, handler);

      assertThat(joinFuture(result)).isEqualTo(99);
      assertThat(handlerCalled).isTrue();
    }
  }

  @Nested
  @DisplayName("Functor Laws")
  class FunctorLaws {
    RuntimeException failException = new RuntimeException("Fail");
    Kind<CompletableFutureKind.Witness, Integer> fa = futureMonad.of(10);
    Kind<CompletableFutureKind.Witness, Integer> faFail = futureMonad.raiseError(failException);

    @Test
    @DisplayName("1. Identity: map(id, fa) == fa")
    void identity() {
      assertThat(joinFuture(futureMonad.map(Function.identity(), fa))).isEqualTo(joinFuture(fa));
      assertThatThrownBy(() -> joinFuture(futureMonad.map(Function.identity(), faFail)))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(failException);
      assertThatThrownBy(() -> joinFuture(faFail))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(failException);
    }

    @Test
    @DisplayName("2. Composition: map(g.compose(f), fa) == map(g, map(f, fa))")
    void composition() {
      Function<Integer, String> f_func = i -> "v" + i;
      Function<String, String> g_func = s -> s + "!";

      Kind<CompletableFutureKind.Witness, String> leftSide =
          futureMonad.map(g_func.compose(f_func), fa);
      Kind<CompletableFutureKind.Witness, String> rightSide =
          futureMonad.map(g_func, futureMonad.map(f_func, fa));
      assertThat(joinFuture(leftSide)).isEqualTo(joinFuture(rightSide));

      Kind<CompletableFutureKind.Witness, String> leftSideFail =
          futureMonad.map(g_func.compose(f_func), faFail);
      Kind<CompletableFutureKind.Witness, String> rightSideFail =
          futureMonad.map(g_func, futureMonad.map(f_func, faFail));
      assertThatThrownBy(() -> joinFuture(leftSideFail))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(failException);
      assertThatThrownBy(() -> joinFuture(rightSideFail))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(failException);
    }
  }

  // Applicative and Monad Laws also need their Kind types updated to .Witness
  // This will be similar to the changes in FunctorLaws and the other test sections.
  // For brevity, I'll show one example from Applicative and one from Monad.

  @Nested
  @DisplayName("Applicative Laws")
  class ApplicativeLaws {
    int x = 5;
    Function<Integer, String> f_func = i -> "f" + i;

    RuntimeException vException = new RuntimeException("VFail");
    RuntimeException fException = new RuntimeException("FFail");

    Kind<CompletableFutureKind.Witness, Integer> v = futureMonad.of(x);
    Kind<CompletableFutureKind.Witness, Integer> vFail = futureMonad.raiseError(vException);
    Kind<CompletableFutureKind.Witness, Function<Integer, String>> fKind = futureMonad.of(f_func);
    Kind<CompletableFutureKind.Witness, Function<Integer, String>> fKindFail =
        futureMonad.raiseError(fException);

    // ... (gKind, gKindFail if used in full law tests)

    @Test
    @DisplayName("1. Identity: ap(of(id), v) == v")
    void identity() {
      Kind<CompletableFutureKind.Witness, Function<Integer, Integer>> idFuncKind =
          futureMonad.of(Function.identity());
      assertThat(joinFuture(futureMonad.ap(idFuncKind, v))).isEqualTo(joinFuture(v));
      assertThatThrownBy(() -> joinFuture(futureMonad.ap(idFuncKind, vFail)))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(vException);
    }

    @Test
    @DisplayName("2. Homomorphism: ap(of(f), of(x)) == of(f(x))")
    void homomorphism() {
      Kind<CompletableFutureKind.Witness, Function<Integer, String>> apFunc =
          futureMonad.of(f_func);
      Kind<CompletableFutureKind.Witness, Integer> apVal = futureMonad.of(x);
      Kind<CompletableFutureKind.Witness, String> leftSide = futureMonad.ap(apFunc, apVal);
      Kind<CompletableFutureKind.Witness, String> rightSide = futureMonad.of(f_func.apply(x));
      assertThat(joinFuture(leftSide)).isEqualTo(joinFuture(rightSide));
    }

    @Test
    @DisplayName("3. Interchange: ap(fKind, of(y)) == ap(of(f -> f(y)), fKind)")
    void interchange() {
      int y = 10;
      Kind<CompletableFutureKind.Witness, String> leftSide =
          futureMonad.ap(fKind, futureMonad.of(y));

      Function<Function<Integer, String>, String> evalWithY = fn -> fn.apply(y);
      Kind<CompletableFutureKind.Witness, Function<Function<Integer, String>, String>> evalKind =
          futureMonad.of(evalWithY);
      Kind<CompletableFutureKind.Witness, String> rightSide = futureMonad.ap(evalKind, fKind);

      assertThat(joinFuture(leftSide)).isEqualTo(joinFuture(rightSide));

      Kind<CompletableFutureKind.Witness, String> leftSideFail =
          futureMonad.ap(fKindFail, futureMonad.of(y));
      Kind<CompletableFutureKind.Witness, String> rightSideFail =
          futureMonad.ap(evalKind, fKindFail);
      assertThatThrownBy(() -> joinFuture(leftSideFail))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(fException);
      assertThatThrownBy(() -> joinFuture(rightSideFail))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(fException);
    }

    @Test
    @DisplayName("4. Composition: ap(ap(map(compose, gKind), fKind), v) == ap(gKind, ap(fKind, v))")
    void composition() {
      Function<String, String> g_func = s -> s + "g";
      Kind<CompletableFutureKind.Witness, Function<String, String>> gKind =
          futureMonad.of(g_func); // MODIFIED
      RuntimeException gException = new RuntimeException("GFail");
      Kind<CompletableFutureKind.Witness, Function<String, String>> gKindFail =
          futureMonad.raiseError(gException); // MODIFIED

      Function<
              Function<String, String>,
              Function<Function<Integer, String>, Function<Integer, String>>>
          composeMap = gg -> ff -> gg.compose(ff);

      Kind<
              CompletableFutureKind.Witness,
              Function<Function<Integer, String>, Function<Integer, String>>>
          mappedCompose = futureMonad.map(composeMap, gKind);
      Kind<CompletableFutureKind.Witness, Function<Integer, String>> ap1 =
          futureMonad.ap(mappedCompose, fKind);
      Kind<CompletableFutureKind.Witness, String> leftSide = futureMonad.ap(ap1, v);

      Kind<CompletableFutureKind.Witness, String> innerAp = futureMonad.ap(fKind, v);
      Kind<CompletableFutureKind.Witness, String> rightSide = futureMonad.ap(gKind, innerAp);

      assertThat(joinFuture(leftSide)).isEqualTo(joinFuture(rightSide));

      // Failure cases
      Kind<CompletableFutureKind.Witness, String> leftSideFailG =
          futureMonad.ap(futureMonad.ap(futureMonad.map(composeMap, gKindFail), fKind), v);
      Kind<CompletableFutureKind.Witness, String> rightSideFailG =
          futureMonad.ap(gKindFail, futureMonad.ap(fKind, v));
      assertThatThrownBy(() -> joinFuture(leftSideFailG))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(gException);
      assertThatThrownBy(() -> joinFuture(rightSideFailG))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(gException);

      Kind<CompletableFutureKind.Witness, String> leftSideFailF =
          futureMonad.ap(futureMonad.ap(futureMonad.map(composeMap, gKind), fKindFail), v);
      Kind<CompletableFutureKind.Witness, String> rightSideFailF =
          futureMonad.ap(gKind, futureMonad.ap(fKindFail, v));
      assertThatThrownBy(() -> joinFuture(leftSideFailF))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(fException);
      assertThatThrownBy(() -> joinFuture(rightSideFailF))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(fException);

      Kind<CompletableFutureKind.Witness, String> leftSideFailV =
          futureMonad.ap(futureMonad.ap(futureMonad.map(composeMap, gKind), fKind), vFail);
      Kind<CompletableFutureKind.Witness, String> rightSideFailV =
          futureMonad.ap(gKind, futureMonad.ap(fKind, vFail));
      assertThatThrownBy(() -> joinFuture(leftSideFailV))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(vException);
      assertThatThrownBy(() -> joinFuture(rightSideFailV))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(vException);
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLaws {
    int value = 5;
    RuntimeException mException = new RuntimeException("MFail");
    Kind<CompletableFutureKind.Witness, Integer> mValue = futureMonad.of(value);
    Kind<CompletableFutureKind.Witness, Integer> mValueFail = futureMonad.raiseError(mException);

    Function<Integer, Kind<CompletableFutureKind.Witness, String>> f_law =
        i -> futureMonad.of("v" + i);
    Function<String, Kind<CompletableFutureKind.Witness, String>> g_law =
        s -> futureMonad.of(s + "!");

    @Test
    @DisplayName("1. Left Identity: flatMap(of(a), f) == f(a)")
    void leftIdentity() {
      Kind<CompletableFutureKind.Witness, Integer> ofValue = futureMonad.of(value);
      Kind<CompletableFutureKind.Witness, String> leftSide = futureMonad.flatMap(f_law, ofValue);
      Kind<CompletableFutureKind.Witness, String> rightSide = f_law.apply(value);
      assertThat(joinFuture(leftSide)).isEqualTo(joinFuture(rightSide));
    }

    @Test
    @DisplayName("2. Right Identity: flatMap(m, of) == m")
    void rightIdentity() {
      Function<Integer, Kind<CompletableFutureKind.Witness, Integer>> ofFunc =
          i -> futureMonad.of(i);
      Kind<CompletableFutureKind.Witness, Integer> leftSide = futureMonad.flatMap(ofFunc, mValue);
      assertThat(joinFuture(leftSide)).isEqualTo(joinFuture(mValue));

      Kind<CompletableFutureKind.Witness, Integer> leftSideFail =
          futureMonad.flatMap(ofFunc, mValueFail);
      assertThatThrownBy(() -> joinFuture(leftSideFail))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(mException);
      assertThatThrownBy(() -> joinFuture(mValueFail))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(mException);
    }

    @Test
    @DisplayName("3. Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
    void associativity() {
      Kind<CompletableFutureKind.Witness, String> innerFlatMap = futureMonad.flatMap(f_law, mValue);
      Kind<CompletableFutureKind.Witness, String> leftSide =
          futureMonad.flatMap(g_law, innerFlatMap);

      Function<Integer, Kind<CompletableFutureKind.Witness, String>> rightSideFunc =
          a -> futureMonad.flatMap(g_law, f_law.apply(a));
      Kind<CompletableFutureKind.Witness, String> rightSide =
          futureMonad.flatMap(rightSideFunc, mValue);
      assertThat(joinFuture(leftSide)).isEqualTo(joinFuture(rightSide));

      Kind<CompletableFutureKind.Witness, String> innerFlatMapFail =
          futureMonad.flatMap(f_law, mValueFail);
      Kind<CompletableFutureKind.Witness, String> leftSideFail =
          futureMonad.flatMap(g_law, innerFlatMapFail);
      Kind<CompletableFutureKind.Witness, String> rightSideFail =
          futureMonad.flatMap(rightSideFunc, mValueFail);
      assertThatThrownBy(() -> joinFuture(leftSideFail))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(mException);
      assertThatThrownBy(() -> joinFuture(rightSideFail))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(mException);
    }
  }

  @Nested
  @DisplayName("mapN tests")
  class MapNTests {
    Kind<CompletableFutureKind.Witness, Integer> fut1 = futureMonad.of(10);
    Kind<CompletableFutureKind.Witness, String> fut2 = futureMonad.of("hello");
    Kind<CompletableFutureKind.Witness, Double> fut3 = futureMonad.of(1.5);
    Kind<CompletableFutureKind.Witness, Boolean> fut4 = futureMonad.of(true);

    RuntimeException testException_mapN = new RuntimeException("MapN Fail"); // Renamed
    Kind<CompletableFutureKind.Witness, Integer> failFut =
        futureMonad.raiseError(testException_mapN);

    @Test
    void map2_bothSuccess() {
      BiFunction<Integer, String, String> f2_func = (i, s) -> s + i; // Renamed
      Kind<CompletableFutureKind.Witness, String> result = futureMonad.map2(fut1, fut2, f2_func);
      assertThat(joinFuture(result)).isEqualTo("hello10");
    }

    @Test
    void map2_firstFails() {
      BiFunction<Integer, String, String> f2_func = (i, s) -> s + i;
      Kind<CompletableFutureKind.Witness, String> result = futureMonad.map2(failFut, fut2, f2_func);
      assertThatThrownBy(() -> joinFuture(result))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(testException_mapN);
    }

    @Test
    void map2_secondFails() {
      BiFunction<Integer, String, String> f2_func = (i, s) -> s + i;
      Kind<CompletableFutureKind.Witness, String> result =
          futureMonad.map2(fut1, futureMonad.raiseError(testException_mapN), f2_func);
      assertThatThrownBy(() -> joinFuture(result))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(testException_mapN);
    }

    @Test
    void map2_functionThrows() {
      BiFunction<Integer, String, String> f2_func =
          (i, s) -> {
            throw testException_mapN;
          };
      Kind<CompletableFutureKind.Witness, String> result = futureMonad.map2(fut1, fut2, f2_func);
      assertThatThrownBy(() -> joinFuture(result))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(testException_mapN);
    }

    @Test
    void map3_allSuccess() {
      Function3<Integer, String, Double, String> f3_func = // Renamed
          (i, s, d) -> String.format("I:%d S:%s D:%.1f", i, s, d);
      Kind<CompletableFutureKind.Witness, String> result =
          futureMonad.map3(fut1, fut2, fut3, f3_func);
      assertThat(joinFuture(result)).isEqualTo("I:10 S:hello D:1.5");
    }

    @Test
    void map3_middleFails() {
      Function3<Integer, String, Double, String> f3_func = (i, s, d) -> "Should not execute";
      Kind<CompletableFutureKind.Witness, String> result =
          futureMonad.map3(fut1, futureMonad.raiseError(testException_mapN), fut3, f3_func);
      assertThatThrownBy(() -> joinFuture(result))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(testException_mapN);
    }

    @Test
    void map3_functionThrows() {
      Function3<Integer, String, Double, String> f3_func =
          (i, s, d) -> {
            throw testException_mapN;
          };
      Kind<CompletableFutureKind.Witness, String> result =
          futureMonad.map3(fut1, fut2, fut3, f3_func);
      assertThatThrownBy(() -> joinFuture(result))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(testException_mapN);
    }

    @Test
    void map4_allSuccess() {
      Function4<Integer, String, Double, Boolean, String> f4_func = // Renamed
          (i, s, d, b) -> String.format("I:%d S:%s D:%.1f B:%b", i, s, d, b);
      Kind<CompletableFutureKind.Witness, String> result =
          futureMonad.map4(fut1, fut2, fut3, fut4, f4_func);
      assertThat(joinFuture(result)).isEqualTo("I:10 S:hello D:1.5 B:true");
    }

    @Test
    void map4_lastFails() {
      Function4<Integer, String, Double, Boolean, String> f4_func =
          (i, s, d, b) -> "Should not execute";
      Kind<CompletableFutureKind.Witness, String> result =
          futureMonad.map4(fut1, fut2, fut3, futureMonad.raiseError(testException_mapN), f4_func);
      assertThatThrownBy(() -> joinFuture(result))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(testException_mapN);
    }

    @Test
    void map4_functionThrows() {
      Function4<Integer, String, Double, Boolean, String> f4_func =
          (i, s, d, b) -> {
            throw testException_mapN;
          };
      Kind<CompletableFutureKind.Witness, String> result =
          futureMonad.map4(fut1, fut2, fut3, fut4, f4_func);
      assertThatThrownBy(() -> joinFuture(result))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(testException_mapN);
    }

    @Test
    void mapN_withDelays() {
      Kind<CompletableFutureKind.Witness, Integer> dFut1 = delayedSuccess(5, 30);
      Kind<CompletableFutureKind.Witness, String> dFut2 = delayedSuccess("world", 20);
      BiFunction<Integer, String, String> f2_func = (i, s) -> s + i; // Renamed
      Kind<CompletableFutureKind.Witness, String> result = futureMonad.map2(dFut1, dFut2, f2_func);
      assertThat(joinFuture(result)).isEqualTo("world5");
    }
  }
}
