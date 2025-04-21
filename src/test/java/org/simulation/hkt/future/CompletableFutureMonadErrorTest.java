package org.simulation.hkt.future;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.simulation.hkt.Kind;


import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy; // Correct import
import static org.simulation.hkt.future.CompletableFutureKindHelper.*;

class CompletableFutureMonadErrorTest {

    // Instance of the MonadError implementation for CompletableFuture
    private final CompletableFutureMonadError futureMonad = new CompletableFutureMonadError();

    // --- Helper Functions ---
    private <A> CompletableFuture<A> unwrapFuture(Kind<CompletableFutureKind<?>, A> kind) {
        return CompletableFutureKindHelper.unwrap(kind);
    }

    private <A> A joinFuture(Kind<CompletableFutureKind<?>, A> kind) {
        // Helper to block and get result, or throw CompletionException
        return CompletableFutureKindHelper.join(kind);
    }

    // Creates a future that completes successfully after a short delay
    private <A> Kind<CompletableFutureKind<?>, A> delayedSuccess(A value, long delayMillis) {
        CompletableFuture<A> future = CompletableFuture.supplyAsync(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
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
            }
            // Use exceptionally() or completeExceptionally() outside supplyAsync if needed
            // For simplicity here, we throw inside.
            if (error instanceof RuntimeException re) throw re;
            if (error instanceof Error e) throw e;
            throw new CompletionException(error); // Wrap checked exceptions
        });
        // Ensure the exception is propagated correctly
        return wrap(future.exceptionally(ex -> {
            // Rethrow wrapped exception if needed, otherwise complete exceptionally
            // Ensure the final exception matches the input 'error' type if possible
            if (ex instanceof CompletionException && ex.getCause() != null) {
                throw new CompletionException(ex.getCause());
            }
            throw new CompletionException(error);
        }));
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
    }

    @Nested
    @DisplayName("Applicative 'ap' tests")
    class ApTests {
        RuntimeException funcException = new RuntimeException("FuncFail");
        RuntimeException valueException = new RuntimeException("ValueFail");
        Kind<CompletableFutureKind<?>, Function<Integer, String>> funcKindSuccess = futureMonad.of(i -> "N" + i);
        Kind<CompletableFutureKind<?>, Function<Integer, String>> funcKindFailure = futureMonad.raiseError(funcException);
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
            // Typically, the first exception encountered propagates.
            Kind<CompletableFutureKind<?>, String> result = futureMonad.ap(funcKindFailure, valueKindFailure);
            // Check that joinFuture throws one of the unwrapped RuntimeExceptions
            assertThatThrownBy(() -> joinFuture(result))
                    .isInstanceOf(RuntimeException.class)
                    .isIn(funcException, valueException); // It will be one of them
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
        Function<Integer, Kind<CompletableFutureKind<?>, String>> fSuccess =
                i -> futureMonad.of("Flat" + i);
        Function<Integer, Kind<CompletableFutureKind<?>, String>> fFailure =
                i -> futureMonad.raiseError(innerException);

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
        Kind<CompletableFutureKind<?>, Integer> successKind = futureMonad.of(100);
        Kind<CompletableFutureKind<?>, Integer> failedKind = futureMonad.raiseError(testError);

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
        void handleErrorWith_shouldHandleFailure() {
            Function<Throwable, Kind<CompletableFutureKind<?>, Integer>> handler =
                    err -> {
                        assertThat(err).isSameAs(testError);
                        return futureMonad.of(0); // Recover with 0
                    };

            Kind<CompletableFutureKind<?>, Integer> result = futureMonad.handleErrorWith(failedKind, handler);
            assertThat(joinFuture(result)).isEqualTo(0);
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
            assertThat(result).isSameAs(successKind); // Returns original successful future
            assertThat(joinFuture(result)).isEqualTo(100);
        }

        @Test
        void handleError_shouldHandleFailureWithPureValue() {
            Function<Throwable, Integer> handler = err -> -99;
            Kind<CompletableFutureKind<?>, Integer> result = futureMonad.handleError(failedKind, handler);
            assertThat(joinFuture(result)).isEqualTo(-99);
        }

        @Test
        void handleError_shouldIgnoreSuccess() {
            Function<Throwable, Integer> handler = err -> -1;
            Kind<CompletableFutureKind<?>, Integer> result = futureMonad.handleError(successKind, handler);
            assertThat(result).isSameAs(successKind);
            assertThat(joinFuture(result)).isEqualTo(100);
        }

        @Test
        void recoverWith_shouldReplaceFailureWithFallbackKind() {
            Kind<CompletableFutureKind<?>, Integer> fallback = futureMonad.of(0);
            Kind<CompletableFutureKind<?>, Integer> result = futureMonad.recoverWith(failedKind, fallback);
            // Note: recoverWith creates a new future, doesn't return the exact fallback instance
            assertThat(joinFuture(result)).isEqualTo(0);
        }

        @Test
        void recoverWith_shouldIgnoreSuccess() {
            Kind<CompletableFutureKind<?>, Integer> fallback = futureMonad.of(0);
            Kind<CompletableFutureKind<?>, Integer> result = futureMonad.recoverWith(successKind, fallback);
            assertThat(result).isSameAs(successKind);
        }

        @Test
        void recover_shouldReplaceFailureWithOfValue() {
            Kind<CompletableFutureKind<?>, Integer> result = futureMonad.recover(failedKind, 0);
            assertThat(joinFuture(result)).isEqualTo(0);
        }

        @Test
        void recover_shouldIgnoreSuccess() {
            Kind<CompletableFutureKind<?>, Integer> result = futureMonad.recover(successKind, 0);
            assertThat(result).isSameAs(successKind);
        }
    }

    @Nested
    @DisplayName("unwrap robustness tests")
    class UnwrapRobustnessTests {
        // Dummy Kind implementation
        record DummyFutureKind<A>() implements Kind<CompletableFutureKind<?>, A> {}

        @Test
        void unwrap_shouldReturnFailedFutureForNullInput() {
            CompletableFuture<String> future = CompletableFutureKindHelper.unwrap(null);
            assertThat(future.isCompletedExceptionally()).isTrue();
            assertThatThrownBy(future::join)
                    .isInstanceOf(CompletionException.class) // join still throws CompletionException
                    .hasCauseInstanceOf(NullPointerException.class) // Check cause
                    .cause() // Further check on cause
                    .hasMessageContaining("Cannot unwrap null Kind");
        }

        @Test
        void unwrap_shouldReturnFailedFutureForUnknownKindType() {
            Kind<CompletableFutureKind<?>, Integer> unknownKind = new DummyFutureKind<>();
            CompletableFuture<Integer> future = CompletableFutureKindHelper.unwrap(unknownKind);
            assertThat(future.isCompletedExceptionally()).isTrue();
            assertThatThrownBy(future::join)
                    .isInstanceOf(CompletionException.class) // join still throws CompletionException
                    .hasCauseInstanceOf(IllegalArgumentException.class) // Check cause
                    .cause() // Further check on cause
                    .hasMessageContaining("Kind instance is not a CompletableFutureHolder");
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
            // Corrected typo: assertThatThrownBy
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

        Kind<CompletableFutureKind<?>, Integer> v = futureMonad.of(x);
        Kind<CompletableFutureKind<?>, Integer> vFail = futureMonad.raiseError(vException);
        Kind<CompletableFutureKind<?>, Function<Integer, String>> fKind = futureMonad.of(f);
        Kind<CompletableFutureKind<?>, Function<Integer, String>> fKindFail = futureMonad.raiseError(fException);
        Kind<CompletableFutureKind<?>, Function<String, String>> gKind = futureMonad.of(g);

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
            // Failure checks omitted for brevity but should follow similar patterns
        }
    }


    @Nested
    @DisplayName("Monad Laws")
    class MonadLaws {
        int value = 5;
        RuntimeException mException = new RuntimeException("MFail");
        Kind<CompletableFutureKind<?>, Integer> mValue = futureMonad.of(value);
        Kind<CompletableFutureKind<?>, Integer> mValueFail = futureMonad.raiseError(mException); // Line 451

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
            assertThatThrownBy(() -> joinFuture(leftSideFail)) // Line 475 (original failure)
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

            // Failure checks omitted for brevity
        }
    }
}
