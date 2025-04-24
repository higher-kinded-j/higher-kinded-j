package org.simulation.hkt.future;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.simulation.hkt.Kind;
import org.simulation.hkt.function.Function3;
import org.simulation.hkt.function.Function4;


import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.simulation.hkt.future.CompletableFutureKindHelper.*;

class CompletableFutureMonadErrorTest {

    // Instance of the MonadError implementation for CompletableFuture
    private final CompletableFutureMonadError futureMonad = new CompletableFutureMonadError();

    // --- Helper Functions ---
    private <A> CompletableFuture<A> unwrapFuture(Kind<CompletableFutureKind<?>, A> kind) {
        return CompletableFutureKindHelper.unwrap(kind);
    }

    private <A> A joinFuture(Kind<CompletableFutureKind<?>, A> kind) {
        // Helper to block and get result, or throw CompletionException's cause
        return CompletableFutureKindHelper.join(kind);
    }

    // Creates a future that completes successfully after a short delay
    private <A> Kind<CompletableFutureKind<?>, A> delayedSuccess(A value, long delayMillis) {
        CompletableFuture<A> future = CompletableFuture.supplyAsync(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // Wrap InterruptedException as it's checked
                throw new CompletionException(e);
            }
            return value;
        });
        return wrap(future);
    }

    // Creates a future that fails after a short delay
    private <A> Kind<CompletableFutureKind<?>, A> delayedFailure(Throwable error, long delayMillis) {
        CompletableFuture<A> future = CompletableFuture.supplyAsync(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // Wrap InterruptedException as it's checked
                throw new CompletionException(e);
            }
            // Throw the original error to be caught by exceptionallyCompose/handle
            if (error instanceof RuntimeException re) throw re;
            if (error instanceof Error e) throw e;
            throw new CompletionException(error); // Wrap checked exceptions
        });
        return wrap(future);
    }


    // --- Basic Functionality Tests ---

    @Nested
    @DisplayName("Applicative 'of' tests")
    class OfTests {
        @Test
        void of_shouldCreateCompletedFuture() {
            Kind<CompletableFutureKind<?>, String> kind = futureMonad.of("done");
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
            Kind<CompletableFutureKind<?>, Integer> input = futureMonad.of(10);
            Kind<CompletableFutureKind<?>, String> result = futureMonad.map(i -> "v" + i, input);
            assertThat(joinFuture(result)).isEqualTo("v10");
        }

        @Test
        void map_shouldPropagateFailure() {
            RuntimeException testException = new RuntimeException("MapFail");
            Kind<CompletableFutureKind<?>, Integer> input = futureMonad.raiseError(testException);
            Kind<CompletableFutureKind<?>, String> result = futureMonad.map(i -> "v" + i, input); // Function shouldn't run

            // Check that joinFuture throws the unwrapped RuntimeException
            assertThatThrownBy(() -> joinFuture(result))
                .isInstanceOf(RuntimeException.class) // Check for the unwrapped exception type
                .isSameAs(testException); // Check it's the original exception instance
        }

        @Test
        void map_shouldFailIfFunctionThrows() {
            RuntimeException mapEx = new RuntimeException("Map function failed");
            Kind<CompletableFutureKind<?>, Integer> input = futureMonad.of(10);
            Kind<CompletableFutureKind<?>, String> result = futureMonad.map(i -> {
                throw mapEx;
            }, input);

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

        Kind<CompletableFutureKind<?>, Function<Integer, String>> funcKindSuccess = futureMonad.of(i -> "N" + i);
        Kind<CompletableFutureKind<?>, Function<Integer, String>> funcKindFailure = futureMonad.raiseError(funcException);
        Kind<CompletableFutureKind<?>, Function<Integer, String>> funcKindThrows = futureMonad.of(i -> { throw applyException; });

        Kind<CompletableFutureKind<?>, Integer> valueKindSuccess = futureMonad.of(20);
        Kind<CompletableFutureKind<?>, Integer> valueKindFailure = futureMonad.raiseError(valueException);

        @Test
        void ap_shouldApplySuccessFuncToSuccessValue() {
            Kind<CompletableFutureKind<?>, String> result = futureMonad.ap(funcKindSuccess, valueKindSuccess);
            assertThat(joinFuture(result)).isEqualTo("N20");
        }

        @Test
        void ap_shouldReturnFailureIfFuncFails() {
            Kind<CompletableFutureKind<?>, String> result = futureMonad.ap(funcKindFailure, valueKindSuccess);
            // Check that joinFuture throws the unwrapped RuntimeException
            assertThatThrownBy(() -> joinFuture(result))
                .isInstanceOf(RuntimeException.class)
                .isSameAs(funcException);
        }

        @Test
        void ap_shouldReturnFailureIfValueFails() {
            Kind<CompletableFutureKind<?>, String> result = futureMonad.ap(funcKindSuccess, valueKindFailure);
            // Check that joinFuture throws the unwrapped RuntimeException
            assertThatThrownBy(() -> joinFuture(result))
                .isInstanceOf(RuntimeException.class)
                .isSameAs(valueException);
        }

        @Test
        void ap_shouldReturnFailureIfBothFail() {
            // Behavior depends on which future fails first or how combine handles it.
            // CompletableFuture.thenCombine typically propagates the first exception.
            Kind<CompletableFutureKind<?>, String> result = futureMonad.ap(funcKindFailure, valueKindFailure);
            // Check that joinFuture throws one of the unwrapped RuntimeExceptions
            assertThatThrownBy(() -> joinFuture(result))
                .isInstanceOf(RuntimeException.class)
                .isIn(funcException, valueException); // It will be one of them
        }

        @Test
        void ap_shouldReturnFailureIfFunctionApplicationThrows() {
            Kind<CompletableFutureKind<?>, String> result = futureMonad.ap(funcKindThrows, valueKindSuccess);
            assertThatThrownBy(() -> joinFuture(result))
                .isInstanceOf(RuntimeException.class)
                .isSameAs(applyException);
        }

        @Test
        void ap_shouldWorkWithDelayedValues() {
            Kind<CompletableFutureKind<?>, Function<Integer, String>> delayedFunc = delayedSuccess(i -> "Delayed" + i, 50);
            Kind<CompletableFutureKind<?>, Integer> delayedValue = delayedSuccess(30, 30);
            Kind<CompletableFutureKind<?>, String> result = futureMonad.ap(delayedFunc, delayedValue);
            assertThat(joinFuture(result)).isEqualTo("Delayed30");
        }
    }

    @Nested
    @DisplayName("Monad 'flatMap' tests")
    class FlatMapTests {
        RuntimeException innerException = new RuntimeException("FlatMapInnerFail");
        RuntimeException outerException = new RuntimeException("FlatMapOuterFail");
        RuntimeException funcApplyException = new RuntimeException("FuncApplyFail");

        Function<Integer, Kind<CompletableFutureKind<?>, String>> fSuccess =
            i -> futureMonad.of("Flat" + i);
        Function<Integer, Kind<CompletableFutureKind<?>, String>> fFailure =
            i -> futureMonad.raiseError(innerException);
        Function<Integer, Kind<CompletableFutureKind<?>, String>> fThrows =
            i -> { throw funcApplyException; };


        Kind<CompletableFutureKind<?>, Integer> inputSuccess = futureMonad.of(5);
        Kind<CompletableFutureKind<?>, Integer> inputFailure = futureMonad.raiseError(outerException);

        @Test
        void flatMap_shouldApplySuccessFuncToSuccessInput() {
            Kind<CompletableFutureKind<?>, String> result = futureMonad.flatMap(fSuccess, inputSuccess);
            assertThat(joinFuture(result)).isEqualTo("Flat5");
        }

        @Test
        void flatMap_shouldReturnFailureIfFuncReturnsFailure() {
            Kind<CompletableFutureKind<?>, String> result = futureMonad.flatMap(fFailure, inputSuccess);
            // Check that joinFuture throws the unwrapped RuntimeException
            assertThatThrownBy(() -> joinFuture(result))
                .isInstanceOf(RuntimeException.class)
                .isSameAs(innerException);
        }

        @Test
        void flatMap_shouldReturnFailureIfInputFails() {
            Kind<CompletableFutureKind<?>, String> result = futureMonad.flatMap(fSuccess, inputFailure); // fSuccess should not run
            // Check that joinFuture throws the unwrapped RuntimeException
            assertThatThrownBy(() -> joinFuture(result))
                .isInstanceOf(RuntimeException.class)
                .isSameAs(outerException);
        }

        @Test
        void flatMap_shouldReturnFailureIfFunctionApplicationThrows() {
            Kind<CompletableFutureKind<?>, String> result = futureMonad.flatMap(fThrows, inputSuccess);
            assertThatThrownBy(() -> joinFuture(result))
                .isInstanceOf(RuntimeException.class)
                .isSameAs(funcApplyException);
        }

        @Test
        void flatMap_shouldWorkWithDelayedValues() {
            Kind<CompletableFutureKind<?>, Integer> delayedInput = delayedSuccess(15, 20);
            Function<Integer, Kind<CompletableFutureKind<?>, String>> delayedFunc =
                i -> delayedSuccess("Chain" + i, 30);

            Kind<CompletableFutureKind<?>, String> result = futureMonad.flatMap(delayedFunc, delayedInput);
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

        Kind<CompletableFutureKind<?>, Integer> successKind = futureMonad.of(100);
        Kind<CompletableFutureKind<?>, Integer> failedKind = futureMonad.raiseError(testError);
        Kind<CompletableFutureKind<?>, Integer> failedKindWithError = futureMonad.raiseError(testErrorSubclass);
        Kind<CompletableFutureKind<?>, Integer> failedKindChecked = futureMonad.raiseError(checkedException);

        @Test
        void raiseError_shouldCreateFailedFuture() {
            Kind<CompletableFutureKind<?>, String> kind = futureMonad.raiseError(testError);
            CompletableFuture<String> future = unwrapFuture(kind);

            assertThat(future.isDone()).isTrue(); // failedFuture completes immediately
            assertThat(future.isCompletedExceptionally()).isTrue();

            // Check that joinFuture throws the unwrapped RuntimeException
            assertThatThrownBy(() -> joinFuture(kind))
                .isInstanceOf(RuntimeException.class)
                .isSameAs(testError);
        }

        @Test
        void raiseError_shouldCreateFailedFutureWithErrorSubclass() {
            Kind<CompletableFutureKind<?>, String> kind = futureMonad.raiseError(testErrorSubclass);
            CompletableFuture<String> future = unwrapFuture(kind);
            assertThat(future.isCompletedExceptionally()).isTrue();
            assertThatThrownBy(() -> joinFuture(kind))
                .isInstanceOf(StackOverflowError.class)
                .isSameAs(testErrorSubclass);
        }

        @Test
        void raiseError_shouldCreateFailedFutureWithCheckedException() { // Added
            Kind<CompletableFutureKind<?>, String> kind = futureMonad.raiseError(checkedException);
            CompletableFuture<String> future = unwrapFuture(kind);
            assertThat(future.isCompletedExceptionally()).isTrue();
            // join will wrap checked exception in CompletionException
            assertThatThrownBy(() -> joinFuture(kind))
                .isInstanceOf(CompletionException.class)
                .hasCause(checkedException);
        }


        @Test
        void handleErrorWith_shouldHandleFailureWithSuccessHandler() {
            Function<Throwable, Kind<CompletableFutureKind<?>, Integer>> handler =
                err -> {
                    assertThat(err).isSameAs(testError);
                    return futureMonad.of(0); // Recover with 0
                };

            Kind<CompletableFutureKind<?>, Integer> result = futureMonad.handleErrorWith(failedKind, handler);
            assertThat(joinFuture(result)).isEqualTo(0);
        }

        @Test
        void handleErrorWith_shouldHandleFailureWithFailedHandler() {
            // Handler recovers with another failure
            Function<Throwable, Kind<CompletableFutureKind<?>, Integer>> handler =
                err -> {
                    assertThat(err).isSameAs(testError);
                    return futureMonad.raiseError(handlerError); // Recover with new error
                };

            Kind<CompletableFutureKind<?>, Integer> result = futureMonad.handleErrorWith(failedKind, handler);

            // Expect the error returned by the handler
            assertThatThrownBy(() -> joinFuture(result))
                .isInstanceOf(RuntimeException.class)
                .isSameAs(handlerError);
        }

        @Test
        void handleErrorWith_shouldHandleFailureWhenHandlerThrows() {
            // Handler function itself throws an exception
            Function<Throwable, Kind<CompletableFutureKind<?>, Integer>> handler =
                err -> {
                    assertThat(err).isSameAs(testError);
                    throw handlerApplyError; // Handler throws
                };

            Kind<CompletableFutureKind<?>, Integer> result = futureMonad.handleErrorWith(failedKind, handler);

            // Expect the exception thrown by the handler
            assertThatThrownBy(() -> joinFuture(result))
                .isInstanceOf(RuntimeException.class)
                .isSameAs(handlerApplyError);
        }

        @Test
        void handleErrorWith_shouldFailWhenHandlerIsNull() {
            // Call the method with a null handler
            Kind<CompletableFutureKind<?>, Integer> result = futureMonad.handleErrorWith(failedKind, null);

            // Assert that joining the resulting future throws NullPointerException
            // because the handler.apply(cause) inside exceptionallyCompose will fail.
            assertThatThrownBy(() -> joinFuture(result))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void handleErrorWith_shouldHandleErrorSubclass() {
            AtomicReference<Throwable> caughtError = new AtomicReference<>();
            Function<Throwable, Kind<CompletableFutureKind<?>, Integer>> handler =
                err -> {
                    caughtError.set(err);
                    return futureMonad.of(50); // Recover
                };

            Kind<CompletableFutureKind<?>, Integer> result = futureMonad.handleErrorWith(failedKindWithError, handler);
            assertThat(joinFuture(result)).isEqualTo(50);
            assertThat(caughtError.get()).isSameAs(testErrorSubclass);
        }

        @Test
        void handleErrorWith_shouldHandleCheckedException() { // Added
            AtomicReference<Throwable> caughtError = new AtomicReference<>();
            Function<Throwable, Kind<CompletableFutureKind<?>, Integer>> handler =
                err -> {
                    caughtError.set(err);
                    return futureMonad.of(75); // Recover
                };

            Kind<CompletableFutureKind<?>, Integer> result = futureMonad.handleErrorWith(failedKindChecked, handler);
            assertThat(joinFuture(result)).isEqualTo(75);
            // The handler receives the original checked exception because failedFuture doesn't wrap it
            assertThat(caughtError.get()).isSameAs(checkedException);
        }

        // Example Test for Branch 3a (likely already covered but good to be explicit):
        // Exception IS CompletionException WITH cause
        @Test
        void handleErrorWith_shouldHandleCompletionExceptionWithCause() {
            IOException ioCause = new IOException("Underlying IO failure");
            // Create a future that fails with CompletionException wrapping IOException
            Kind<CompletableFutureKind<?>, Integer> failedKindWithCause = wrap(CompletableFuture.failedFuture(new CompletionException(ioCause)));
            AtomicReference<Throwable> caughtError = new AtomicReference<>();

            Function<Throwable, Kind<CompletableFutureKind<?>, Integer>> handler =
                err -> {
                    caughtError.set(err); // Capture the error passed to the handler
                    return futureMonad.of(77); // Recover
                };

            Kind<CompletableFutureKind<?>, Integer> result = futureMonad.handleErrorWith(failedKindWithCause, handler);

            // Verify recovery happened
            assertThat(joinFuture(result)).isEqualTo(77);
            // Verify the handler received the UNWRAPPED cause (the IOException)
            assertThat(caughtError.get()).isSameAs(ioCause);
        }

        @Test
        void handleErrorWith_shouldHandleCompletionExceptionWithCauseDirectly() { // Added
            // Simulate a future that fails internally with a specific cause
            CompletableFuture<Integer> internalFailure = new CompletableFuture<>();
            internalFailure.completeExceptionally(testError); // Fail with original error

            Kind<CompletableFutureKind<?>, Integer> failedKindWithCause = wrap(internalFailure);
            AtomicReference<Throwable> caughtError = new AtomicReference<>();

            Function<Throwable, Kind<CompletableFutureKind<?>, Integer>> handler =
                err -> {
                    caughtError.set(err);
                    return futureMonad.of(80); // Recover
                };

            Kind<CompletableFutureKind<?>, Integer> result = futureMonad.handleErrorWith(failedKindWithCause, handler);
            assertThat(joinFuture(result)).isEqualTo(80);
            // Verify the handler received the *unwrapped* cause
            assertThat(caughtError.get()).isSameAs(testError);
        }
        // Test for Branch 3b: Exception IS CompletionException but WITHOUT cause
        @Test
        void handleErrorWith_shouldHandleCompletionExceptionWithoutCause() {
            CompletionException completionExNoCause = new CompletionException("Failed Future No Cause", null); // Explicit null cause
            Kind<CompletableFutureKind<?>, Integer> failedKindNoCause = futureMonad.raiseError(completionExNoCause);
            AtomicReference<Throwable> caughtError = new AtomicReference<>();

            Function<Throwable, Kind<CompletableFutureKind<?>, Integer>> handler =
                err -> {
                    caughtError.set(err); // Capture the error passed to the handler
                    return futureMonad.of(88); // Recover
                };

            Kind<CompletableFutureKind<?>, Integer> result = futureMonad.handleErrorWith(failedKindNoCause, handler);

            // Verify recovery happened
            assertThat(joinFuture(result)).isEqualTo(88);
            // Verify the handler received the original CompletionException (since cause was null)
            assertThat(caughtError.get()).isSameAs(completionExNoCause);
        }

        @Test
        void handleErrorWith_shouldHandleCompletionExceptionWithoutCauseDirectly() {
            // Simulate a future that fails with CompletionException directly (no cause)
            CompletionException completionEx = new CompletionException("No cause here", null);
            CompletableFuture<Integer> internalFailure = new CompletableFuture<>();
            internalFailure.completeExceptionally(completionEx);

            Kind<CompletableFutureKind<?>, Integer> failedKindNoCause = wrap(internalFailure);
            AtomicReference<Throwable> caughtError = new AtomicReference<>();

            Function<Throwable, Kind<CompletableFutureKind<?>, Integer>> handler =
                err -> {
                    caughtError.set(err);
                    return futureMonad.of(90); // Recover
                };

            Kind<CompletableFutureKind<?>, Integer> result = futureMonad.handleErrorWith(failedKindNoCause, handler);
            assertThat(joinFuture(result)).isEqualTo(90);
            // Verify the handler received the original CompletionException
            assertThat(caughtError.get()).isSameAs(completionEx);
        }


        @Test
        void handleErrorWith_shouldHandleFailureWithDelayedRecovery() {
            Function<Throwable, Kind<CompletableFutureKind<?>, Integer>> handler =
                err -> delayedSuccess(0, 30); // Recover with delayed 0

            Kind<CompletableFutureKind<?>, Integer> result = futureMonad.handleErrorWith(failedKind, handler);
            assertThat(joinFuture(result)).isEqualTo(0);
        }

        @Test
        void handleErrorWith_shouldIgnoreSuccess() {
            Function<Throwable, Kind<CompletableFutureKind<?>, Integer>> handler =
                err -> futureMonad.of(-1); // Should not be called

            Kind<CompletableFutureKind<?>, Integer> result = futureMonad.handleErrorWith(successKind, handler);
            // Check the result value instead of instance equality for async safety.
            assertThat(joinFuture(result)).isEqualTo(100);
        }

        @Test
        void handleErrorWith_shouldIgnoreAlreadyCompletedSuccessFuture() { // Added
            // Explicitly test the early exit condition
            CompletableFuture<Integer> alreadyDone = CompletableFuture.completedFuture(200);
            Kind<CompletableFutureKind<?>, Integer> alreadyDoneKind = wrap(alreadyDone);

            AtomicBoolean handlerCalled = new AtomicBoolean(false);
            Function<Throwable, Kind<CompletableFutureKind<?>, Integer>> handler =
                err -> {
                    handlerCalled.set(true);
                    return futureMonad.of(-1);
                };

            Kind<CompletableFutureKind<?>, Integer> result = futureMonad.handleErrorWith(alreadyDoneKind, handler);

            // Verify the handler was not called and the result is the original value
            assertThat(handlerCalled).isFalse();
            assertThat(joinFuture(result)).isEqualTo(200);
            // Optionally check if the same future instance is returned (though not strictly required by MonadError)
            assertThat(unwrapFuture(result)).isSameAs(alreadyDone);
        }

        // Test for Branch 1: Input future already successfully completed
        @Test
        void handleErrorWith_shouldReturnOriginalKindIfAlreadyCompletedSuccessfully() {
            CompletableFuture<Integer> alreadyDone = CompletableFuture.completedFuture(200);
            Kind<CompletableFutureKind<?>, Integer> alreadyDoneKind = wrap(alreadyDone);

            AtomicBoolean handlerCalled = new AtomicBoolean(false);
            Function<Throwable, Kind<CompletableFutureKind<?>, Integer>> handler =
                err -> {
                    handlerCalled.set(true); // Track if handler was erroneously called
                    return futureMonad.of(-1);
                };

            Kind<CompletableFutureKind<?>, Integer> result = futureMonad.handleErrorWith(alreadyDoneKind, handler);

            // Verify the handler was NOT called
            assertThat(handlerCalled).isFalse();
            // Verify the result is the original value
            assertThat(joinFuture(result)).isEqualTo(200);
            // Verify the original Kind instance is returned (due to the optimization)
            assertThat(result).isSameAs(alreadyDoneKind);
        }

        @Test
        @DisplayName("handleErrorWith should ignore already completed success future (optimization path)")
        void handleErrorWith_shouldReturnOriginalKindForAlreadyCompletedSuccess() {
            // 1. Create an already successfully completed future
            String successValue = "Already Done";
            CompletableFuture<String> alreadyDoneFuture = CompletableFuture.completedFuture(successValue);
            Kind<CompletableFutureKind<?>, String> alreadyDoneKind = wrap(alreadyDoneFuture);

            // 2. Define a handler that should NOT be called
            AtomicBoolean handlerCalled = new AtomicBoolean(false);
            Function<Throwable, Kind<CompletableFutureKind<?>, String>> handler =
                err -> {
                    handlerCalled.set(true); // Track if handler was erroneously called
                    return futureMonad.of("Recovered?"); // Should not happen
                };

            // 3. Call handleErrorWith
            Kind<CompletableFutureKind<?>, String> resultKind = futureMonad.handleErrorWith(alreadyDoneKind, handler);

            // 4. Assert handler was not called
            assertThat(handlerCalled).isFalse();

            // 5. Assert the returned Kind is the *same instance* as the input (optimization check)
            assertThat(resultKind).isSameAs(alreadyDoneKind);

            // 6. Assert the result value is correct
            assertThat(joinFuture(resultKind)).isEqualTo(successValue);
        }

        // Test for Branch 3b: Exception is NOT CompletionException with cause
        @Test
        void handleErrorWith_shouldHandleDirectRuntimeException() {
            RuntimeException directError = new IllegalStateException("Direct Failure");
            Kind<CompletableFutureKind<?>, Integer> failedKindDirect = futureMonad.raiseError(directError);
            AtomicReference<Throwable> caughtError = new AtomicReference<>();

            Function<Throwable, Kind<CompletableFutureKind<?>, Integer>> handler =
                err -> {
                    caughtError.set(err); // Capture the error passed to the handler
                    return futureMonad.of(99); // Recover
                };

            Kind<CompletableFutureKind<?>, Integer> result = futureMonad.handleErrorWith(failedKindDirect, handler);

            // Verify recovery happened
            assertThat(joinFuture(result)).isEqualTo(99);
            // Verify the handler received the ORIGINAL direct exception
            assertThat(caughtError.get()).isSameAs(directError);
        }


        // Optional: Test case where the handler itself throws
        @Test
        void handleErrorWith_shouldPropagateExceptionFromHandler() {
            RuntimeException handlerException = new RuntimeException("Handler itself failed!");
            Kind<CompletableFutureKind<?>, Integer> failedKind = futureMonad.raiseError(testError); // Initial failure

            Function<Throwable, Kind<CompletableFutureKind<?>, Integer>> handler =
                err -> {
                    // This handler throws an exception instead of returning a Kind
                    throw handlerException;
                };

            Kind<CompletableFutureKind<?>, Integer> result = futureMonad.handleErrorWith(failedKind, handler);

            // Expect the exception thrown by the handler to be the final failure cause
            assertThatThrownBy(() -> joinFuture(result))
                .isInstanceOf(RuntimeException.class) // It's a RuntimeException
                .isSameAs(handlerException);        // Verify it's the specific one from the handler
        }


        @Test
        void handleError_shouldHandleFailureWithPureValue() {
            Function<Throwable, Integer> handler = err -> -99;
            Kind<CompletableFutureKind<?>, Integer> result = futureMonad.handleError(failedKind, handler);
            assertThat(joinFuture(result)).isEqualTo(-99);
        }

        @Test
        void handleError_shouldHandleFailureWhenPureHandlerThrows() {
            Function<Throwable, Integer> handler = err -> {
                throw handlerApplyError;
            };
            Kind<CompletableFutureKind<?>, Integer> result = futureMonad.handleError(failedKind, handler);
            // handleErrorWith catches exceptions from the handler function
            assertThatThrownBy(() -> joinFuture(result))
                .isInstanceOf(RuntimeException.class)
                .isSameAs(handlerApplyError);
        }

        @Test
        void handleError_shouldIgnoreSuccess() {
            Function<Throwable, Integer> handler = err -> -1;
            Kind<CompletableFutureKind<?>, Integer> result = futureMonad.handleError(successKind, handler);
            assertThat(joinFuture(result)).isEqualTo(100);
        }

        @Test
        void recoverWith_shouldReplaceFailureWithFallbackKind() {
            Kind<CompletableFutureKind<?>, Integer> fallback = futureMonad.of(0);
            Kind<CompletableFutureKind<?>, Integer> result = futureMonad.recoverWith(failedKind, fallback);
            assertThat(joinFuture(result)).isEqualTo(0);
        }

        @Test
        void recoverWith_shouldReplaceFailureWithFailedFallbackKind() {
            Kind<CompletableFutureKind<?>, Integer> fallback = futureMonad.raiseError(handlerError);
            Kind<CompletableFutureKind<?>, Integer> result = futureMonad.recoverWith(failedKind, fallback);
            assertThatThrownBy(() -> joinFuture(result))
                .isInstanceOf(RuntimeException.class)
                .isSameAs(handlerError);
        }

        @Test
        void recoverWith_shouldIgnoreSuccess() {
            Kind<CompletableFutureKind<?>, Integer> fallback = futureMonad.of(0);
            Kind<CompletableFutureKind<?>, Integer> result = futureMonad.recoverWith(successKind, fallback);
            assertThat(joinFuture(result)).isEqualTo(100);
        }

        @Test
        void recover_shouldReplaceFailureWithOfValue() {
            Kind<CompletableFutureKind<?>, Integer> result = futureMonad.recover(failedKind, 0);
            assertThat(joinFuture(result)).isEqualTo(0);
        }

        @Test
        void recover_shouldIgnoreSuccess() {
            Kind<CompletableFutureKind<?>, Integer> result = futureMonad.recover(successKind, 0);
            assertThat(joinFuture(result)).isEqualTo(100);
        }

        @Test
        void handleErrorWith_shouldHandleSpecificExceptionType() {
            IOException ioError = new IOException("IO Error");
            Kind<CompletableFutureKind<?>, String> failedKindIO = futureMonad.raiseError(ioError);

            Function<Throwable, Kind<CompletableFutureKind<?>, String>> handler = err -> {
                if (err instanceof IOException) {
                    return futureMonad.of("Handled IOException");
                } else {
                    // Re-raise other errors
                    return futureMonad.raiseError(err);
                }
            };

            Kind<CompletableFutureKind<?>, String> result = futureMonad.handleErrorWith(failedKindIO, handler);
            assertThat(joinFuture(result)).isEqualTo("Handled IOException");

            // Test with a different error type that should be re-raised
            Kind<CompletableFutureKind<?>, String> failedKindRuntime = futureMonad.raiseError(testError);
            Kind<CompletableFutureKind<?>, String> resultRuntime = futureMonad.handleErrorWith(failedKindRuntime, handler);
            assertThatThrownBy(() -> joinFuture(resultRuntime))
                .isInstanceOf(RuntimeException.class)
                .isSameAs(testError);
        }



    }

    // --- Law Tests ---
    // Note: Comparing Futures directly is tricky. We compare results using join().

    @Nested
    @DisplayName("Functor Laws")
    class FunctorLaws {
        RuntimeException failException = new RuntimeException("Fail");
        Kind<CompletableFutureKind<?>, Integer> fa = futureMonad.of(10);
        Kind<CompletableFutureKind<?>, Integer> faFail = futureMonad.raiseError(failException);

        @Test
        @DisplayName("1. Identity: map(id, fa) == fa")
        void identity() {
            assertThat(joinFuture(futureMonad.map(Function.identity(), fa)))
                .isEqualTo(joinFuture(fa));

            // Check failure propagation consistency
            assertThatThrownBy(() -> joinFuture(futureMonad.map(Function.identity(), faFail)))
                .isInstanceOf(RuntimeException.class) // Expect unwrapped exception from joinFuture
                .isSameAs(failException);
            assertThatThrownBy(() -> joinFuture(faFail))
                .isInstanceOf(RuntimeException.class) // Expect unwrapped exception from joinFuture
                .isSameAs(failException);

        }

        @Test
        @DisplayName("2. Composition: map(g.compose(f), fa) == map(g, map(f, fa))")
        void composition() {
            Function<Integer, String> f = i -> "v" + i;
            Function<String, String> g = s -> s + "!";

            Kind<CompletableFutureKind<?>, String> leftSide = futureMonad.map(g.compose(f), fa);
            Kind<CompletableFutureKind<?>, String> rightSide = futureMonad.map(g, futureMonad.map(f, fa));

            assertThat(joinFuture(leftSide)).isEqualTo(joinFuture(rightSide));

            // Check failure propagation consistency
            Kind<CompletableFutureKind<?>, String> leftSideFail = futureMonad.map(g.compose(f), faFail);
            Kind<CompletableFutureKind<?>, String> rightSideFail = futureMonad.map(g, futureMonad.map(f, faFail));

            assertThatThrownBy(() -> joinFuture(leftSideFail))
                .isInstanceOf(RuntimeException.class) // Expect unwrapped exception from joinFuture
                .isSameAs(failException);
            assertThatThrownBy(() -> joinFuture(rightSideFail))
                .isInstanceOf(RuntimeException.class) // Expect unwrapped exception from joinFuture
                .isSameAs(failException);
        }
    }

    @Nested
    @DisplayName("Applicative Laws")
    class ApplicativeLaws {
        int x = 5;
        Function<Integer, String> f = i -> "f" + i;
        Function<String, String> g = s -> s + "g";
        RuntimeException vException = new RuntimeException("VFail");
        RuntimeException fException = new RuntimeException("FFail");
        RuntimeException gException = new RuntimeException("GFail");


        Kind<CompletableFutureKind<?>, Integer> v = futureMonad.of(x);
        Kind<CompletableFutureKind<?>, Integer> vFail = futureMonad.raiseError(vException);
        Kind<CompletableFutureKind<?>, Function<Integer, String>> fKind = futureMonad.of(f);
        Kind<CompletableFutureKind<?>, Function<Integer, String>> fKindFail = futureMonad.raiseError(fException);
        Kind<CompletableFutureKind<?>, Function<String, String>> gKind = futureMonad.of(g);
        Kind<CompletableFutureKind<?>, Function<String, String>> gKindFail = futureMonad.raiseError(gException);


        @Test
        @DisplayName("1. Identity: ap(of(id), v) == v")
        void identity() {
            Kind<CompletableFutureKind<?>, Function<Integer, Integer>> idFuncKind = futureMonad.of(Function.identity());
            assertThat(joinFuture(futureMonad.ap(idFuncKind, v))).isEqualTo(joinFuture(v));
            // Check failure
            assertThatThrownBy(() -> joinFuture(futureMonad.ap(idFuncKind, vFail)))
                .isInstanceOf(RuntimeException.class) // Expect unwrapped exception from joinFuture
                .isSameAs(vException);
        }

        @Test
        @DisplayName("2. Homomorphism: ap(of(f), of(x)) == of(f(x))")
        void homomorphism() {
            Kind<CompletableFutureKind<?>, Function<Integer, String>> apFunc = futureMonad.of(f);
            Kind<CompletableFutureKind<?>, Integer> apVal = futureMonad.of(x);
            Kind<CompletableFutureKind<?>, String> leftSide = futureMonad.ap(apFunc, apVal);
            Kind<CompletableFutureKind<?>, String> rightSide = futureMonad.of(f.apply(x));
            assertThat(joinFuture(leftSide)).isEqualTo(joinFuture(rightSide));
        }

        @Test
        @DisplayName("3. Interchange: ap(fKind, of(y)) == ap(of(f -> f(y)), fKind)")
        void interchange() {
            int y = 10;
            Kind<CompletableFutureKind<?>, String> leftSide = futureMonad.ap(fKind, futureMonad.of(y));

            Function<Function<Integer, String>, String> evalWithY = fn -> fn.apply(y);
            Kind<CompletableFutureKind<?>, Function<Function<Integer, String>, String>> evalKind = futureMonad.of(evalWithY);
            Kind<CompletableFutureKind<?>, String> rightSide = futureMonad.ap(evalKind, fKind);

            assertThat(joinFuture(leftSide)).isEqualTo(joinFuture(rightSide));

            // Check failure
            Kind<CompletableFutureKind<?>, String> leftSideFail = futureMonad.ap(fKindFail, futureMonad.of(y));
            Kind<CompletableFutureKind<?>, String> rightSideFail = futureMonad.ap(evalKind, fKindFail);
            assertThatThrownBy(() -> joinFuture(leftSideFail))
                .isInstanceOf(RuntimeException.class) // Expect unwrapped exception from joinFuture
                .isSameAs(fException);
            assertThatThrownBy(() -> joinFuture(rightSideFail))
                .isInstanceOf(RuntimeException.class) // Expect unwrapped exception from joinFuture
                .isSameAs(fException);
        }

        @Test
        @DisplayName("4. Composition: ap(ap(map(compose, gKind), fKind), v) == ap(gKind, ap(fKind, v))")
        void composition() {
            Function<Function<String, String>, Function<Function<Integer, String>, Function<Integer, String>>> composeMap =
                gg -> ff -> gg.compose(ff);

            Kind<CompletableFutureKind<?>, Function<Function<Integer, String>, Function<Integer, String>>> mappedCompose =
                futureMonad.map(composeMap, gKind);
            Kind<CompletableFutureKind<?>, Function<Integer, String>> ap1 =
                futureMonad.ap(mappedCompose, fKind);
            Kind<CompletableFutureKind<?>, String> leftSide = futureMonad.ap(ap1, v);

            Kind<CompletableFutureKind<?>, String> innerAp = futureMonad.ap(fKind, v);
            Kind<CompletableFutureKind<?>, String> rightSide = futureMonad.ap(gKind, innerAp);

            assertThat(joinFuture(leftSide)).isEqualTo(joinFuture(rightSide));

            // Check failure propagation (simplified check, assumes first failure propagates)
            Kind<CompletableFutureKind<?>, String> leftSideFailG = futureMonad.ap(futureMonad.ap(futureMonad.map(composeMap, gKindFail), fKind), v);
            Kind<CompletableFutureKind<?>, String> rightSideFailG = futureMonad.ap(gKindFail, futureMonad.ap(fKind, v));
            assertThatThrownBy(() -> joinFuture(leftSideFailG)).isInstanceOf(RuntimeException.class).isSameAs(gException);
            assertThatThrownBy(() -> joinFuture(rightSideFailG)).isInstanceOf(RuntimeException.class).isSameAs(gException);

            Kind<CompletableFutureKind<?>, String> leftSideFailF = futureMonad.ap(futureMonad.ap(futureMonad.map(composeMap, gKind), fKindFail), v);
            Kind<CompletableFutureKind<?>, String> rightSideFailF = futureMonad.ap(gKind, futureMonad.ap(fKindFail, v));
            assertThatThrownBy(() -> joinFuture(leftSideFailF)).isInstanceOf(RuntimeException.class).isSameAs(fException);
            assertThatThrownBy(() -> joinFuture(rightSideFailF)).isInstanceOf(RuntimeException.class).isSameAs(fException);

            Kind<CompletableFutureKind<?>, String> leftSideFailV = futureMonad.ap(futureMonad.ap(futureMonad.map(composeMap, gKind), fKind), vFail);
            Kind<CompletableFutureKind<?>, String> rightSideFailV = futureMonad.ap(gKind, futureMonad.ap(fKind, vFail));
            assertThatThrownBy(() -> joinFuture(leftSideFailV)).isInstanceOf(RuntimeException.class).isSameAs(vException);
            assertThatThrownBy(() -> joinFuture(rightSideFailV)).isInstanceOf(RuntimeException.class).isSameAs(vException);
        }
    }


    @Nested
    @DisplayName("Monad Laws")
    class MonadLaws {
        int value = 5;
        RuntimeException mException = new RuntimeException("MFail");
        Kind<CompletableFutureKind<?>, Integer> mValue = futureMonad.of(value);
        Kind<CompletableFutureKind<?>, Integer> mValueFail = futureMonad.raiseError(mException);

        Function<Integer, Kind<CompletableFutureKind<?>, String>> f = i -> futureMonad.of("v" + i);
        Function<String, Kind<CompletableFutureKind<?>, String>> g = s -> futureMonad.of(s + "!");

        @Test
        @DisplayName("1. Left Identity: flatMap(of(a), f) == f(a)")
        void leftIdentity() {
            Kind<CompletableFutureKind<?>, Integer> ofValue = futureMonad.of(value);
            Kind<CompletableFutureKind<?>, String> leftSide = futureMonad.flatMap(f, ofValue);
            Kind<CompletableFutureKind<?>, String> rightSide = f.apply(value);
            assertThat(joinFuture(leftSide)).isEqualTo(joinFuture(rightSide));
        }

        @Test
        @DisplayName("2. Right Identity: flatMap(m, of) == m")
        void rightIdentity() {
            Function<Integer, Kind<CompletableFutureKind<?>, Integer>> ofFunc = i -> futureMonad.of(i);
            Kind<CompletableFutureKind<?>, Integer> leftSide = futureMonad.flatMap(ofFunc, mValue);
            assertThat(joinFuture(leftSide)).isEqualTo(joinFuture(mValue));

            // Check failure
            Kind<CompletableFutureKind<?>, Integer> leftSideFail = futureMonad.flatMap(ofFunc, mValueFail);
            // Assert that joinFuture(leftSideFail) throws the specific RuntimeException
            assertThatThrownBy(() -> joinFuture(leftSideFail))
                .isInstanceOf(RuntimeException.class) // Expect the unwrapped exception due to joinFuture helper
                .isSameAs(mException); // Check it's the original exception instance

            // Also check the original failed future for consistency
            assertThatThrownBy(() -> joinFuture(mValueFail))
                .isInstanceOf(RuntimeException.class) // Expect the unwrapped exception due to joinFuture helper
                .isSameAs(mException); // Check it's the original exception instance
        }


        @Test
        @DisplayName("3. Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
        void associativity() {
            Kind<CompletableFutureKind<?>, String> innerFlatMap = futureMonad.flatMap(f, mValue);
            Kind<CompletableFutureKind<?>, String> leftSide = futureMonad.flatMap(g, innerFlatMap);

            Function<Integer, Kind<CompletableFutureKind<?>, String>> rightSideFunc =
                a -> futureMonad.flatMap(g, f.apply(a));
            Kind<CompletableFutureKind<?>, String> rightSide = futureMonad.flatMap(rightSideFunc, mValue);

            assertThat(joinFuture(leftSide)).isEqualTo(joinFuture(rightSide));

            // Check failure propagation (simplified check, assumes first failure propagates)
            Kind<CompletableFutureKind<?>, String> innerFlatMapFail = futureMonad.flatMap(f, mValueFail);
            Kind<CompletableFutureKind<?>, String> leftSideFail = futureMonad.flatMap(g, innerFlatMapFail);
            Kind<CompletableFutureKind<?>, String> rightSideFail = futureMonad.flatMap(rightSideFunc, mValueFail);

            assertThatThrownBy(() -> joinFuture(leftSideFail)).isInstanceOf(RuntimeException.class).isSameAs(mException);
            assertThatThrownBy(() -> joinFuture(rightSideFail)).isInstanceOf(RuntimeException.class).isSameAs(mException);
        }
    }

    // --- mapN Tests ---

    @Nested
    @DisplayName("mapN tests")
    class MapNTests {

        Kind<CompletableFutureKind<?>, Integer> fut1 = futureMonad.of(10);
        Kind<CompletableFutureKind<?>, String> fut2 = futureMonad.of("hello");
        Kind<CompletableFutureKind<?>, Double> fut3 = futureMonad.of(1.5);
        Kind<CompletableFutureKind<?>, Boolean> fut4 = futureMonad.of(true);

        RuntimeException testException = new RuntimeException("MapN Fail");
        Kind<CompletableFutureKind<?>, Integer> failFut = futureMonad.raiseError(testException);

        @Test
        void map2_bothSuccess() {
            BiFunction<Integer, String, String> f2 = (i, s) -> s + i;
            Kind<CompletableFutureKind<?>, String> result = futureMonad.map2(fut1, fut2, f2);
            assertThat(joinFuture(result)).isEqualTo("hello10");
        }

        @Test
        void map2_firstFails() {
            BiFunction<Integer, String, String> f2 = (i, s) -> s + i;
            Kind<CompletableFutureKind<?>, String> result = futureMonad.map2(failFut, fut2, f2);
            assertThatThrownBy(() -> joinFuture(result))
                .isInstanceOf(RuntimeException.class)
                .isSameAs(testException);
        }

        @Test
        void map2_secondFails() {
            BiFunction<Integer, String, String> f2 = (i, s) -> s + i;
            Kind<CompletableFutureKind<?>, String> result = futureMonad.map2(fut1, futureMonad.raiseError(testException), f2);
            assertThatThrownBy(() -> joinFuture(result))
                .isInstanceOf(RuntimeException.class)
                .isSameAs(testException);
        }

        @Test
        void map2_functionThrows() {
            BiFunction<Integer, String, String> f2 = (i, s) -> { throw testException; };
            Kind<CompletableFutureKind<?>, String> result = futureMonad.map2(fut1, fut2, f2);
            assertThatThrownBy(() -> joinFuture(result))
                .isInstanceOf(RuntimeException.class)
                .isSameAs(testException);
        }


        @Test
        void map3_allSuccess() {
            Function3<Integer, String, Double, String> f3 =
                (i, s, d) -> String.format("I:%d S:%s D:%.1f", i, s, d);
            Kind<CompletableFutureKind<?>, String> result = futureMonad.map3(fut1, fut2, fut3, f3);
            assertThat(joinFuture(result)).isEqualTo("I:10 S:hello D:1.5");
        }

        @Test
        void map3_middleFails() {
            Function3<Integer, String, Double, String> f3 = (i, s, d) -> "Should not execute";
            Kind<CompletableFutureKind<?>, String> result = futureMonad.map3(fut1, futureMonad.raiseError(testException), fut3, f3);
            assertThatThrownBy(() -> joinFuture(result))
                .isInstanceOf(RuntimeException.class)
                .isSameAs(testException);
        }

        @Test
        void map3_functionThrows() {
            Function3<Integer, String, Double, String> f3 = (i, s, d) -> { throw testException; };
            Kind<CompletableFutureKind<?>, String> result = futureMonad.map3(fut1, fut2, fut3, f3);
            assertThatThrownBy(() -> joinFuture(result))
                .isInstanceOf(RuntimeException.class)
                .isSameAs(testException);
        }

        @Test
        void map4_allSuccess() {
            Function4<Integer, String, Double, Boolean, String> f4 =
                (i, s, d, b) -> String.format("I:%d S:%s D:%.1f B:%b", i, s, d, b);
            Kind<CompletableFutureKind<?>, String> result = futureMonad.map4(fut1, fut2, fut3, fut4, f4);
            assertThat(joinFuture(result)).isEqualTo("I:10 S:hello D:1.5 B:true");
        }

        @Test
        void map4_lastFails() {
            Function4<Integer, String, Double, Boolean, String> f4 = (i, s, d, b) -> "Should not execute";
            Kind<CompletableFutureKind<?>, String> result = futureMonad.map4(fut1, fut2, fut3, futureMonad.raiseError(testException), f4);
            assertThatThrownBy(() -> joinFuture(result))
                .isInstanceOf(RuntimeException.class)
                .isSameAs(testException);
        }

        @Test
        void map4_functionThrows() {
            Function4<Integer, String, Double, Boolean, String> f4 = (i, s, d, b) -> { throw testException; };
            Kind<CompletableFutureKind<?>, String> result = futureMonad.map4(fut1, fut2, fut3, fut4, f4);
            assertThatThrownBy(() -> joinFuture(result))
                .isInstanceOf(RuntimeException.class)
                .isSameAs(testException);
        }

        @Test
        void mapN_withDelays() {
            Kind<CompletableFutureKind<?>, Integer> dFut1 = delayedSuccess(5, 30);
            Kind<CompletableFutureKind<?>, String> dFut2 = delayedSuccess("world", 20);
            BiFunction<Integer, String, String> f2 = (i, s) -> s + i;
            Kind<CompletableFutureKind<?>, String> result = futureMonad.map2(dFut1, dFut2, f2);
            assertThat(joinFuture(result)).isEqualTo("world5");
        }
    }

}
