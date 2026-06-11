// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.*;
import static org.higherkindedj.hkt.instances.Witnesses.*;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.ApplicativeLaws;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CompletableFutureMonadTest {

  private final MonadError<CompletableFutureKind.Witness, Throwable> futureMonad =
      Instances.monadError(completableFuture());

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

  @SuppressWarnings("SameParameterValue") // delayMillis kept for symmetry with delayedSuccess
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

  // --- MonadError contract inputs (see monadErrorContract) ---
  private final Kind<CompletableFutureKind.Witness, Integer> validKind = futureMonad.of(42);
  private final Function<Integer, String> validMapper = i -> "v" + i;
  private final Function<Integer, Kind<CompletableFutureKind.Witness, String>> validFlatMapper =
      i -> futureMonad.of("flat:" + i);
  private final Kind<CompletableFutureKind.Witness, Function<Integer, String>> validFunctionKind =
      futureMonad.of(i -> "v" + i);
  private final Function<Throwable, Kind<CompletableFutureKind.Witness, Integer>> validHandler =
      _ -> futureMonad.of(0);
  private final Kind<CompletableFutureKind.Witness, Integer> validFallback = futureMonad.of(-1);

  /**
   * Operation smoke and null-argument validation for {@code map}/{@code flatMap}/{@code ap}/{@code
   * handleErrorWith}/{@code recoverWith} on the MonadError instance. The Functor/Applicative/Monad
   * laws are verified parameterised in the {@code *LawTests} blocks below, so this contract omits
   * {@link Category#LAWS}.
   *
   * <p>{@link Category#EXCEPTIONS} is omitted because the generic contract asserts that {@code
   * map}/{@code flatMap} <em>propagate</em> a thrown function exception, whereas a {@code
   * CompletableFuture} <em>captures</em> it as an exceptionally-completed future (exercised by the
   * {@code map_shouldFailIfFunctionThrows} / {@code ap}/{@code flatMap} failure tests below).
   *
   * <p>{@link Category#VALIDATIONS} <em>is</em> run: {@code CompletableFutureMonad} now overrides
   * {@code recoverWith} to reject a null fallback eagerly (regardless of {@code ma}'s state),
   * matching {@code Either}/{@code Maybe}. Per-method message assertions live in {@link
   * MonadErrorTests}.
   */
  @Test
  @DisplayName(
      "MonadError contract — operations & validations (laws in the *LawTests blocks below)")
  void monadErrorContract() {
    TypeClassContract.<CompletableFutureKind.Witness, Throwable>monadError(
            CompletableFutureMonad.class)
        .<Integer>instance(futureMonad)
        .<String>withKind(validKind)
        .withMonadOperations(validMapper, validFlatMapper, validFunctionKind)
        .withErrorHandling(validHandler, validFallback)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS);
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
              _ -> {
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
            _ -> {
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
        _ -> futureMonad.raiseError(innerException);
    Function<Integer, Kind<CompletableFutureKind.Witness, String>> fThrows =
        _ -> {
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
      AtomicReference<@Nullable Throwable> caught = new AtomicReference<>();
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
      AtomicReference<@Nullable Throwable> caught = new AtomicReference<>();
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
      AtomicReference<@Nullable Throwable> caught = new AtomicReference<>();
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
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void handleErrorWith_shouldFailWhenHandlerIsNull() {
      assertThatThrownBy(() -> futureMonad.handleErrorWith(failedKind, null))
          .isInstanceOf(NullPointerException.class)
          .message()
          .isEqualTo("handler for handleErrorWith cannot be null");
    }

    @Test
    void handleErrorWith_shouldHandleErrorSubclass() {
      AtomicReference<@Nullable Throwable> caughtError = new AtomicReference<>();
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
      AtomicReference<@Nullable Throwable> caughtError = new AtomicReference<>();
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
      AtomicReference<@Nullable Throwable> caughtError = new AtomicReference<>();

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
      AtomicReference<@Nullable Throwable> caughtError = new AtomicReference<>();

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
      AtomicReference<@Nullable Throwable> caughtError = new AtomicReference<>();

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
      AtomicReference<@Nullable Throwable> caughtError = new AtomicReference<>();

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
          _ -> delayedSuccess(0, 30);

      Kind<CompletableFutureKind.Witness, Integer> result =
          futureMonad.handleErrorWith(failedKind, handler);
      assertThat(joinFuture(result)).isEqualTo(0);
    }

    @Test
    void handleErrorWith_shouldIgnoreSuccess() {
      Function<Throwable, Kind<CompletableFutureKind.Witness, Integer>> handler =
          _ -> futureMonad.of(-1);

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
          _ -> {
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
          _ -> {
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
        "handleErrorWith should ignore already completed success future (Optimisation path)")
    void handleErrorWith_shouldReturnOriginalKindForAlreadyCompletedSuccess() {
      String successValue = "Already Done";
      CompletableFuture<String> alreadyDoneFuture = CompletableFuture.completedFuture(successValue);
      Kind<CompletableFutureKind.Witness, String> alreadyDoneKind = FUTURE.widen(alreadyDoneFuture);

      AtomicBoolean handlerCalled = new AtomicBoolean(false);
      Function<Throwable, Kind<CompletableFutureKind.Witness, String>> handler =
          _ -> {
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
      AtomicReference<@Nullable Throwable> caughtError = new AtomicReference<>();

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
          _ -> {
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
      Function<Throwable, Integer> handler = _ -> -99;
      Kind<CompletableFutureKind.Witness, Integer> result =
          futureMonad.handleError(failedKind, handler);
      assertThat(joinFuture(result)).isEqualTo(-99);
    }

    @Test
    void handleError_shouldHandleFailureWhenPureHandlerThrows() {
      Function<Throwable, Integer> handler =
          _ -> {
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
      Function<Throwable, Integer> handler = _ -> -1;
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
    @SuppressWarnings("DataFlowIssue") // null source exercises recoverWith's guard
    void recoverWith_shouldRejectNullSourceEagerly() {
      Kind<CompletableFutureKind.Witness, Integer> fallback = futureMonad.of(0);
      assertThatThrownBy(() -> futureMonad.recoverWith(null, fallback))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("recoverWith (source)");
    }

    @Test
    @SuppressWarnings("DataFlowIssue") // null fallback exercises recoverWith's guard
    void recoverWith_shouldRejectNullFallbackEagerlyAgainstSuccess() {
      assertThatThrownBy(() -> futureMonad.recoverWith(successKind, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("recoverWith (fallback)");
    }

    @Test
    @SuppressWarnings("DataFlowIssue") // null fallback exercises recoverWith's guard
    void recoverWith_shouldRejectNullFallbackEagerlyAgainstFailure() {
      assertThatThrownBy(() -> futureMonad.recoverWith(failedKind, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("recoverWith (fallback)");
    }

    @Test
    @SuppressWarnings("DataFlowIssue") // null source exercises recover's guard
    void recover_shouldRejectNullSourceNamingRecover() {
      assertThatThrownBy(() -> futureMonad.recover(null, 0))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("recover (source)");
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
  class FunctorLawTests {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.future.FutureLawFixtures#kinds")
    void identity(String label, Kind<CompletableFutureKind.Witness, Integer> fa) {
      FunctorLaws.assertIdentity(futureMonad, fa, FutureLawFixtures.EQ);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.future.FutureLawFixtures#kinds")
    void composition(String label, Kind<CompletableFutureKind.Witness, Integer> fa) {
      FunctorLaws.assertComposition(
          futureMonad, fa, i -> "v" + i, (String s) -> s + "!", FutureLawFixtures.EQ);
    }
  }

  @Nested
  @DisplayName("Applicative Laws")
  class ApplicativeLawTests {
    final Function<Integer, String> f_func = i -> "f" + i;
    final Kind<CompletableFutureKind.Witness, Function<Integer, String>> fKind =
        futureMonad.of(f_func);

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.future.FutureLawFixtures#kinds")
    void identity(String label, Kind<CompletableFutureKind.Witness, Integer> v) {
      ApplicativeLaws.assertIdentity(futureMonad, v, FutureLawFixtures.EQ);
    }

    @ParameterizedTest(name = "homomorphism holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.future.FutureLawFixtures#values")
    void homomorphism(Integer value) {
      ApplicativeLaws.assertHomomorphism(futureMonad, value, f_func, FutureLawFixtures.EQ);
    }

    @ParameterizedTest(name = "interchange holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.future.FutureLawFixtures#values")
    void interchange(Integer value) {
      ApplicativeLaws.assertInterchange(futureMonad, fKind, value, FutureLawFixtures.EQ);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.future.FutureLawFixtures#kinds")
    void composition(String label, Kind<CompletableFutureKind.Witness, Integer> v) {
      Kind<CompletableFutureKind.Witness, Function<String, String>> gKind =
          futureMonad.of(s -> s + "g");
      ApplicativeLaws.assertComposition(futureMonad, gKind, fKind, v, FutureLawFixtures.EQ);
    }
  }

  @Nested
  @DisplayName("Applicative failure propagation")
  class ApplicativeFailureTests {
    final RuntimeException vException = new RuntimeException("VFail");
    final RuntimeException fException = new RuntimeException("FFail");
    final Kind<CompletableFutureKind.Witness, Integer> v = futureMonad.of(5);
    final Kind<CompletableFutureKind.Witness, Integer> vFail = futureMonad.raiseError(vException);
    final Kind<CompletableFutureKind.Witness, Function<Integer, String>> fKind =
        futureMonad.of(i -> "f" + i);
    final Kind<CompletableFutureKind.Witness, Function<Integer, String>> fKindFail =
        futureMonad.raiseError(fException);

    @Test
    @DisplayName("ap(of(id), vFail) propagates the value failure")
    void identityPropagatesVFailure() {
      Kind<CompletableFutureKind.Witness, Function<Integer, Integer>> idFuncKind =
          futureMonad.of(Function.identity());
      assertThatThrownBy(() -> joinFuture(futureMonad.ap(idFuncKind, vFail)))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(vException);
    }

    @Test
    @DisplayName("ap propagates failure when function or value fails")
    void apPropagatesFailures() {
      assertThatThrownBy(() -> joinFuture(futureMonad.ap(fKindFail, v)))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(fException);
      assertThatThrownBy(() -> joinFuture(futureMonad.ap(fKind, vFail)))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(vException);
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLawTests {
    final Function<Integer, Kind<CompletableFutureKind.Witness, String>> f_law =
        i -> futureMonad.of("v" + i);
    final Function<String, Kind<CompletableFutureKind.Witness, String>> g_law =
        s -> futureMonad.of(s + "!");

    @ParameterizedTest(name = "left identity holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.future.FutureLawFixtures#values")
    void leftIdentity(Integer value) {
      MonadLaws.assertLeftIdentity(futureMonad, value, f_law, FutureLawFixtures.EQ);
    }

    @ParameterizedTest(name = "right identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.future.FutureLawFixtures#kinds")
    void rightIdentity(String label, Kind<CompletableFutureKind.Witness, Integer> mValue) {
      MonadLaws.assertRightIdentity(futureMonad, mValue, FutureLawFixtures.EQ);
    }

    @ParameterizedTest(name = "associativity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.future.FutureLawFixtures#kinds")
    void associativity(String label, Kind<CompletableFutureKind.Witness, Integer> mValue) {
      MonadLaws.assertAssociativity(futureMonad, mValue, f_law, g_law, FutureLawFixtures.EQ);
    }
  }

  @Nested
  @DisplayName("Monad failure propagation")
  class MonadFailureTests {
    final RuntimeException mException = new RuntimeException("MFail");
    final Kind<CompletableFutureKind.Witness, Integer> mValueFail =
        futureMonad.raiseError(mException);
    final Function<Integer, Kind<CompletableFutureKind.Witness, String>> f_law =
        i -> futureMonad.of("v" + i);
    final Function<String, Kind<CompletableFutureKind.Witness, String>> g_law =
        s -> futureMonad.of(s + "!");

    @Test
    @DisplayName("flatMap propagates the source failure")
    void flatMapPropagatesFailure() {
      // The source future is failed, so the kleisli is never invoked; f_law just supplies one.
      assertThatThrownBy(() -> joinFuture(futureMonad.flatMap(f_law, mValueFail)))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(mException);
    }

    @Test
    @DisplayName("Associative chain over a failure propagates the failure")
    void associativeChainOverFailure() {
      assertThatThrownBy(
              () -> joinFuture(futureMonad.flatMap(g_law, futureMonad.flatMap(f_law, mValueFail))))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(mException);
      assertThatThrownBy(
              () ->
                  joinFuture(
                      futureMonad.flatMap(
                          a -> futureMonad.flatMap(g_law, f_law.apply(a)), mValueFail)))
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
          (_, _) -> {
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
      Function3<Integer, String, Double, String> f3_func = (_, _, _) -> "Should not execute";
      Kind<CompletableFutureKind.Witness, String> result =
          futureMonad.map3(fut1, futureMonad.raiseError(testException_mapN), fut3, f3_func);
      assertThatThrownBy(() -> joinFuture(result))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(testException_mapN);
    }

    @Test
    void map3_functionThrows() {
      Function3<Integer, String, Double, String> f3_func =
          (_, _, _) -> {
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
          (_, _, _, _) -> "Should not execute";
      Kind<CompletableFutureKind.Witness, String> result =
          futureMonad.map4(fut1, fut2, fut3, futureMonad.raiseError(testException_mapN), f4_func);
      assertThatThrownBy(() -> joinFuture(result))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(testException_mapN);
    }

    @Test
    void map4_functionThrows() {
      Function4<Integer, String, Double, Boolean, String> f4_func =
          (_, _, _, _) -> {
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

  @Nested
  @DisplayName("Additional Edge Case Tests")
  class EdgeCaseTests {

    @Test
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void flatMap_shouldThrowNullPointerExceptionForNullFunction() {
      Kind<CompletableFutureKind.Witness, Integer> input = futureMonad.of(1);
      assertThatThrownBy(() -> futureMonad.flatMap(null, input))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("f for flatMap cannot be null");
    }

    @Test
    void raiseError_shouldThrowNullPointerExceptionForNullError() {
      assertThatThrownBy(() -> futureMonad.raiseError(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("CompletableFutureMonad.raiseError error cannot be null");
    }

    @Test
    void handleErrorWith_shouldHandleAlreadyExceptionallyCompletedFuture() {
      CompletableFuture<Integer> alreadyFailed = new CompletableFuture<>();
      RuntimeException testException = new RuntimeException("Already Failed");
      alreadyFailed.completeExceptionally(testException);

      Kind<CompletableFutureKind.Witness, Integer> failedKind = FUTURE.widen(alreadyFailed);
      AtomicReference<@Nullable Throwable> caught = new AtomicReference<>();
      Function<Throwable, Kind<CompletableFutureKind.Witness, Integer>> handler =
          err -> {
            caught.set(err);
            return futureMonad.of(0);
          };

      Kind<CompletableFutureKind.Witness, Integer> result =
          futureMonad.handleErrorWith(failedKind, handler);

      assertThat(joinFuture(result)).isEqualTo(0);
      assertThat(caught.get()).isSameAs(testException);
    }
  }
}
