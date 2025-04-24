package org.simulation.hkt.trymonad;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.simulation.hkt.Kind;
import org.simulation.hkt.exception.KindUnwrapException;
import org.simulation.hkt.function.Function3;
import org.simulation.hkt.function.Function4;


import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.*;


@DisplayName("TryMonadError Tests")
class TryMonadErrorTest {

  private final TryMonadError tryMonad = new TryMonadError();

  // --- Helper Functions ---
  private <A> Try<A> unwrapTry(Kind<TryKind<?>, A> kind) {
    return TryKindHelper.unwrap(kind);
  }

  // Gets value or throws if Failure
  private <A> A getOrThrow(Kind<TryKind<?>, A> kind) throws Throwable {
    return unwrapTry(kind).get();
  }

  // Creates a Success Kind
  private <A> Kind<TryKind<?>, A> successKind(A value) {
    return TryKindHelper.success(value);
  }

  // Creates a Failure Kind
  private <A> Kind<TryKind<?>, A> failureKind(Throwable t) {
    return TryKindHelper.failure(t);
  }

  // Test Exceptions
  private final RuntimeException testRuntimeException = new RuntimeException("Test Runtime Failure");
  private final IOException testIOException = new IOException("Test IO Failure");
  private final Error testError = new StackOverflowError("Test Stack Overflow");


  // --- Basic Functionality Tests ---

  @Nested
  @DisplayName("Applicative 'of' tests")
  class OfTests {
    @Test
    void of_shouldCreateSuccessKind() throws Throwable {
      Kind<TryKind<?>, String> kind = tryMonad.of("done");
      assertThat(unwrapTry(kind).isSuccess()).isTrue();
      assertThat(getOrThrow(kind)).isEqualTo("done");
    }

    @Test
    void of_shouldCreateSuccessKindWithNull() throws Throwable {
      Kind<TryKind<?>, String> kind = tryMonad.of(null);
      assertThat(unwrapTry(kind).isSuccess()).isTrue();
      assertThat(getOrThrow(kind)).isNull();
    }
  }

  @Nested
  @DisplayName("Functor 'map' tests")
  class MapTests {
    @Test
    void map_shouldApplyFunctionToSuccessKind() throws Throwable {
      Kind<TryKind<?>, Integer> input = successKind(10);
      Kind<TryKind<?>, String> result = tryMonad.map(i -> "v" + i, input);
      assertThat(unwrapTry(result).isSuccess()).isTrue();
      assertThat(getOrThrow(result)).isEqualTo("v10");
    }

    @Test
    void map_shouldPropagateFailureKind() {
      Kind<TryKind<?>, Integer> input = failureKind(testRuntimeException);
      Kind<TryKind<?>, String> result = tryMonad.map(i -> "v" + i, input); // Function shouldn't run

      assertThat(unwrapTry(result).isFailure()).isTrue();
      assertThatThrownBy(() -> getOrThrow(result)).isSameAs(testRuntimeException);
    }

    @Test
    void map_shouldTurnIntoFailureIfFunctionThrows() {
      RuntimeException mapEx = new RuntimeException("Map function failed");
      Kind<TryKind<?>, Integer> input = successKind(10);
      Kind<TryKind<?>, String> result = tryMonad.map(i -> { throw mapEx; }, input);

      assertThat(unwrapTry(result).isFailure()).isTrue();
      assertThatThrownBy(() -> getOrThrow(result)).isSameAs(mapEx);
    }
  }

  @Nested
  @DisplayName("Applicative 'ap' tests")
  class ApTests {
    RuntimeException funcException = new RuntimeException("FuncFail");
    RuntimeException valueException = new RuntimeException("ValueFail");
    RuntimeException applyException = new RuntimeException("ApplyFail");

    Kind<TryKind<?>, Function<Integer, String>> funcKindSuccess = tryMonad.of(i -> "N" + i);
    Kind<TryKind<?>, Function<Integer, String>> funcKindFailure = failureKind(funcException);
    Kind<TryKind<?>, Function<Integer, String>> funcKindThrows = tryMonad.of(i -> { throw applyException; });

    Kind<TryKind<?>, Integer> valueKindSuccess = tryMonad.of(20);
    Kind<TryKind<?>, Integer> valueKindFailure = failureKind(valueException);

    @Test
    void ap_shouldApplySuccessFuncToSuccessValue() throws Throwable {
      Kind<TryKind<?>, String> result = tryMonad.ap(funcKindSuccess, valueKindSuccess);
      assertThat(unwrapTry(result).isSuccess()).isTrue();
      assertThat(getOrThrow(result)).isEqualTo("N20");
    }

    @Test
    void ap_shouldReturnFailureIfFuncFails() {
      Kind<TryKind<?>, String> result = tryMonad.ap(funcKindFailure, valueKindSuccess);
      assertThat(unwrapTry(result).isFailure()).isTrue();
      assertThatThrownBy(() -> getOrThrow(result)).isSameAs(funcException);
    }

    @Test
    void ap_shouldReturnFailureIfValueFails() {
      Kind<TryKind<?>, String> result = tryMonad.ap(funcKindSuccess, valueKindFailure);
      assertThat(unwrapTry(result).isFailure()).isTrue();
      assertThatThrownBy(() -> getOrThrow(result)).isSameAs(valueException);
    }

    @Test
    void ap_shouldReturnFailureIfBothFail() {
      // Failure from 'ff' should propagate first in this implementation
      Kind<TryKind<?>, String> result = tryMonad.ap(funcKindFailure, valueKindFailure);
      assertThat(unwrapTry(result).isFailure()).isTrue();
      assertThatThrownBy(() -> getOrThrow(result)).isSameAs(funcException);
    }

    @Test
    void ap_shouldReturnFailureIfFunctionApplicationThrows() {
      Kind<TryKind<?>, String> result = tryMonad.ap(funcKindThrows, valueKindSuccess);
      assertThat(unwrapTry(result).isFailure()).isTrue();
      assertThatThrownBy(() -> getOrThrow(result)).isSameAs(applyException);
    }
  }

  @Nested
  @DisplayName("Monad 'flatMap' tests")
  class FlatMapTests {
    RuntimeException innerException = new RuntimeException("FlatMapInnerFail");
    RuntimeException outerException = new RuntimeException("FlatMapOuterFail");
    RuntimeException funcApplyException = new RuntimeException("FuncApplyFail");

    Function<Integer, Kind<TryKind<?>, String>> fSuccess =
        i -> successKind("Flat" + i);
    Function<Integer, Kind<TryKind<?>, String>> fFailure =
        i -> failureKind(innerException);
    Function<Integer, Kind<TryKind<?>, String>> fThrows =
        i -> { throw funcApplyException; };

    Kind<TryKind<?>, Integer> inputSuccess = successKind(5);
    Kind<TryKind<?>, Integer> inputFailure = failureKind(outerException);

    @Test
    void flatMap_shouldApplySuccessFuncToSuccessInput() throws Throwable {
      Kind<TryKind<?>, String> result = tryMonad.flatMap(fSuccess, inputSuccess);
      assertThat(unwrapTry(result).isSuccess()).isTrue();
      assertThat(getOrThrow(result)).isEqualTo("Flat5");
    }

    @Test
    void flatMap_shouldReturnFailureIfFuncReturnsFailure() {
      Kind<TryKind<?>, String> result = tryMonad.flatMap(fFailure, inputSuccess);
      assertThat(unwrapTry(result).isFailure()).isTrue();
      assertThatThrownBy(() -> getOrThrow(result)).isSameAs(innerException);
    }

    @Test
    void flatMap_shouldReturnFailureIfInputFails() {
      Kind<TryKind<?>, String> result = tryMonad.flatMap(fSuccess, inputFailure); // fSuccess should not run
      assertThat(unwrapTry(result).isFailure()).isTrue();
      assertThatThrownBy(() -> getOrThrow(result)).isSameAs(outerException);
    }

    @Test
    void flatMap_shouldReturnFailureIfFunctionApplicationThrows() {
      Kind<TryKind<?>, String> result = tryMonad.flatMap(fThrows, inputSuccess);
      assertThat(unwrapTry(result).isFailure()).isTrue();
      assertThatThrownBy(() -> getOrThrow(result)).isSameAs(funcApplyException);
    }

    @Test
    void flatMap_shouldHandleChaining() throws Throwable {
      Kind<TryKind<?>, Integer> initial = successKind(10);
      Function<Integer, Kind<TryKind<?>, Double>> f1 = i -> successKind(i * 2.0); // 10 -> 20.0
      Function<Double, Kind<TryKind<?>, String>> f2 = d -> successKind("Final: " + d); // 20.0 -> "Final: 20.0"

      Kind<TryKind<?>, String> result = tryMonad.flatMap(
          i -> tryMonad.flatMap(f2, f1.apply(i)), // flatMap(d -> f2(d), f1(10))
          initial
      );

      assertThat(unwrapTry(result).isSuccess()).isTrue();
      assertThat(getOrThrow(result)).isEqualTo("Final: 20.0");
    }

    @Test
    void flatMap_shouldHandleChainingWithFailure() {
      Kind<TryKind<?>, Integer> initial = successKind(10);
      Function<Integer, Kind<TryKind<?>, Double>> f1Fails = i -> failureKind(testIOException);
      Function<Double, Kind<TryKind<?>, String>> f2 = d -> successKind("Final: " + d); // Should not run

      Kind<TryKind<?>, String> result = tryMonad.flatMap(
          i -> tryMonad.flatMap(f2, f1Fails.apply(i)),
          initial
      );

      assertThat(unwrapTry(result).isFailure()).isTrue();
      assertThatThrownBy(() -> getOrThrow(result)).isSameAs(testIOException);
    }
  }

  @Nested
  @DisplayName("MonadError tests")
  class MonadErrorTests {
    RuntimeException handlerError = new RuntimeException("HandlerError");
    RuntimeException handlerApplyError = new RuntimeException("HandlerApplyError");

    Kind<TryKind<?>, Integer> successK = successKind(100);
    Kind<TryKind<?>, Integer> failedKRuntime = failureKind(testRuntimeException);
    Kind<TryKind<?>, Integer> failedKIO = failureKind(testIOException);
    Kind<TryKind<?>, Integer> failedKError = failureKind(testError);

    @Test
    void raiseError_shouldCreateFailedKind() {
      Kind<TryKind<?>, String> kind = tryMonad.raiseError(testRuntimeException);
      assertThat(unwrapTry(kind).isFailure()).isTrue();
      assertThatThrownBy(() -> getOrThrow(kind)).isSameAs(testRuntimeException);

      Kind<TryKind<?>, String> kindIO = tryMonad.raiseError(testIOException);
      assertThat(unwrapTry(kindIO).isFailure()).isTrue();
      assertThatThrownBy(() -> getOrThrow(kindIO)).isSameAs(testIOException);

      Kind<TryKind<?>, String> kindErr = tryMonad.raiseError(testError);
      assertThat(unwrapTry(kindErr).isFailure()).isTrue();
      assertThatThrownBy(() -> getOrThrow(kindErr)).isSameAs(testError);
    }

    @Test
    void handleErrorWith_shouldHandleFailureWithSuccessHandler() throws Throwable {
      AtomicReference<Throwable> caught = new AtomicReference<>();
      Function<Throwable, Kind<TryKind<?>, Integer>> handler =
          err -> {
            caught.set(err);
            return successKind(0); // Recover with 0
          };

      Kind<TryKind<?>, Integer> result = tryMonad.handleErrorWith(failedKRuntime, handler);
      assertThat(unwrapTry(result).isSuccess()).isTrue();
      assertThat(getOrThrow(result)).isEqualTo(0);
      assertThat(caught.get()).isSameAs(testRuntimeException);
    }

    @Test
    void handleErrorWith_shouldHandleFailureWithFailedHandler() {
      AtomicReference<Throwable> caught = new AtomicReference<>();
      Function<Throwable, Kind<TryKind<?>, Integer>> handler =
          err -> {
            caught.set(err);
            return failureKind(handlerError); // Recover with new error
          };

      Kind<TryKind<?>, Integer> result = tryMonad.handleErrorWith(failedKIO, handler);
      assertThat(unwrapTry(result).isFailure()).isTrue();
      assertThatThrownBy(() -> getOrThrow(result)).isSameAs(handlerError);
      assertThat(caught.get()).isSameAs(testIOException);
    }

    @Test
    void handleErrorWith_shouldHandleFailureWhenHandlerThrows() {
      AtomicReference<Throwable> caught = new AtomicReference<>();
      Function<Throwable, Kind<TryKind<?>, Integer>> handler =
          err -> {
            caught.set(err);
            throw handlerApplyError; // Handler throws
          };

      Kind<TryKind<?>, Integer> result = tryMonad.handleErrorWith(failedKError, handler);
      assertThat(unwrapTry(result).isFailure()).isTrue();
      assertThatThrownBy(() -> getOrThrow(result)).isSameAs(handlerApplyError);
      assertThat(caught.get()).isSameAs(testError);
    }

    @Test
    @DisplayName("handleErrorWith should return Failure(KindUnwrapException) when handler returns null Kind")
    void handleErrorWith_shouldReturnFailureWhenHandlerReturnsNullKind() {
      // Handler function explicitly returns null
      Function<Throwable, Kind<TryKind<?>, Integer>> handler = err -> null;

      Kind<TryKind<?>, Integer> resultKind = tryMonad.handleErrorWith(failedKRuntime, handler); // failedKind is defined elsewhere in the test class

      // Unwrap the result to get the Try
      Try<Integer> resultTry = unwrapTry(resultKind); // unwrapTry is a helper in the test class

      // Assert that the result is a Failure
      assertThat(resultTry.isFailure()).isTrue();

      // Assert that the cause of the Failure is the KindUnwrapException from TryKindHelper.unwrap(null)
      assertThatThrownBy(resultTry::get) // This throws the cause of the Failure
          .isInstanceOf(KindUnwrapException.class) // Check the cause type
          .hasMessageContaining("Cannot unwrap null Kind for Try"); // Check the specific message
    }


    @Test
    void handleErrorWith_shouldIgnoreSuccess() throws Throwable {
      Function<Throwable, Kind<TryKind<?>, Integer>> handler =
          err -> successKind(-1); // Should not be called

      Kind<TryKind<?>, Integer> result = tryMonad.handleErrorWith(successK, handler);
      // Check for value equality, not instance identity
      assertThat(unwrapTry(result)).isEqualTo(unwrapTry(successK));
      assertThat(getOrThrow(result)).isEqualTo(100);
    }

    @Test
    void handleError_shouldHandleFailureWithPureValue() throws Throwable {
      Function<Throwable, Integer> handler = err -> -99;
      Kind<TryKind<?>, Integer> result = tryMonad.handleError(failedKRuntime, handler);
      assertThat(unwrapTry(result).isSuccess()).isTrue();
      assertThat(getOrThrow(result)).isEqualTo(-99);
    }

    @Test
    void handleError_shouldHandleFailureWhenPureHandlerThrows() {
      Function<Throwable, Integer> handler = err -> { throw handlerApplyError; };
      Kind<TryKind<?>, Integer> result = tryMonad.handleError(failedKIO, handler);
      assertThat(unwrapTry(result).isFailure()).isTrue();
      assertThatThrownBy(() -> getOrThrow(result)).isSameAs(handlerApplyError);
    }

    @Test
    void handleError_shouldIgnoreSuccess() throws Throwable {
      Function<Throwable, Integer> handler = err -> -1;
      Kind<TryKind<?>, Integer> result = tryMonad.handleError(successK, handler);
      // Check for value equality, not instance identity
      assertThat(unwrapTry(result)).isEqualTo(unwrapTry(successK));
      assertThat(getOrThrow(result)).isEqualTo(100);
    }

    @Test
    void recoverWith_shouldReplaceFailureWithFallbackKind() throws Throwable {
      Kind<TryKind<?>, Integer> fallback = successKind(0);
      Kind<TryKind<?>, Integer> result = tryMonad.recoverWith(failedKRuntime, fallback);
      assertThat(unwrapTry(result).isSuccess()).isTrue();
      assertThat(getOrThrow(result)).isEqualTo(0);
    }

    @Test
    void recoverWith_shouldReplaceFailureWithFailedFallbackKind() {
      Kind<TryKind<?>, Integer> fallback = failureKind(handlerError);
      Kind<TryKind<?>, Integer> result = tryMonad.recoverWith(failedKIO, fallback);
      assertThat(unwrapTry(result).isFailure()).isTrue();
      assertThatThrownBy(() -> getOrThrow(result)).isSameAs(handlerError);
    }

    @Test
    void recoverWith_shouldIgnoreSuccess() throws Throwable {
      Kind<TryKind<?>, Integer> fallback = successKind(0);
      Kind<TryKind<?>, Integer> result = tryMonad.recoverWith(successK, fallback);
      // Check for value equality, not instance identity
      assertThat(unwrapTry(result)).isEqualTo(unwrapTry(successK));
      assertThat(getOrThrow(result)).isEqualTo(100);
    }

    @Test
    void recover_shouldReplaceFailureWithOfValue() throws Throwable {
      Kind<TryKind<?>, Integer> result = tryMonad.recover(failedKRuntime, 0);
      assertThat(unwrapTry(result).isSuccess()).isTrue();
      assertThat(getOrThrow(result)).isEqualTo(0);
    }

    @Test
    void recover_shouldIgnoreSuccess() throws Throwable {
      Kind<TryKind<?>, Integer> result = tryMonad.recover(successK, 0);
      // Check for value equality, not instance identity
      assertThat(unwrapTry(result)).isEqualTo(unwrapTry(successK));
      assertThat(getOrThrow(result)).isEqualTo(100);
    }

    @Test
    void handleErrorWith_shouldHandleSpecificExceptionType() throws Throwable {
      Function<Throwable, Kind<TryKind<?>, Integer>> handler = err -> {
        if (err instanceof IOException) {
          return successKind(55);
        } else {
          return failureKind(err); // Re-raise other errors
        }
      };

      // Test handling the specific type
      Kind<TryKind<?>, Integer> resultIO = tryMonad.handleErrorWith(failedKIO, handler);
      assertThat(unwrapTry(resultIO).isSuccess()).isTrue();
      assertThat(getOrThrow(resultIO)).isEqualTo(55);

      // Test re-raising a different type
      Kind<TryKind<?>, Integer> resultRuntime = tryMonad.handleErrorWith(failedKRuntime, handler);
      assertThat(unwrapTry(resultRuntime).isFailure()).isTrue();
      assertThatThrownBy(() -> getOrThrow(resultRuntime)).isSameAs(testRuntimeException);
    }
  }


  // --- Law Tests ---
  // Note: Comparing Try instances directly is fine if content matches.
  // We use unwrapTry for consistency and clarity.

  @Nested
  @DisplayName("Functor Laws")
  class FunctorLaws {
    Kind<TryKind<?>, Integer> faSuccess = successKind(10);
    Kind<TryKind<?>, Integer> faFailure = failureKind(testRuntimeException);

    @Test
    @DisplayName("1. Identity: map(id, fa) == fa")
    void identity() {
      assertThat(unwrapTry(tryMonad.map(Function.identity(), faSuccess)))
          .isEqualTo(unwrapTry(faSuccess));

      assertThat(unwrapTry(tryMonad.map(Function.identity(), faFailure)))
          .isEqualTo(unwrapTry(faFailure));
    }

    @Test
    @DisplayName("2. Composition: map(g.compose(f), fa) == map(g, map(f, fa))")
    void composition() {
      Function<Integer, String> f = i -> "v" + i;
      Function<String, String> g = s -> s + "!";
      Function<Integer, String> gComposeF = g.compose(f);

      // Success case
      Kind<TryKind<?>, String> leftSideSuccess = tryMonad.map(gComposeF, faSuccess);
      Kind<TryKind<?>, String> rightSideSuccess = tryMonad.map(g, tryMonad.map(f, faSuccess));
      assertThat(unwrapTry(leftSideSuccess)).isEqualTo(unwrapTry(rightSideSuccess));

      // Failure case
      Kind<TryKind<?>, String> leftSideFailure = tryMonad.map(gComposeF, faFailure);
      Kind<TryKind<?>, String> rightSideFailure = tryMonad.map(g, tryMonad.map(f, faFailure));
      assertThat(unwrapTry(leftSideFailure)).isEqualTo(unwrapTry(rightSideFailure)); // Both should be Failure(testRuntimeException)
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

    Kind<TryKind<?>, Integer> v = successKind(x);
    Kind<TryKind<?>, Integer> vFail = failureKind(vException);
    Kind<TryKind<?>, Function<Integer, String>> fKind = successKind(f);
    Kind<TryKind<?>, Function<Integer, String>> fKindFail = failureKind(fException);
    Kind<TryKind<?>, Function<String, String>> gKind = successKind(g);
    Kind<TryKind<?>, Function<String, String>> gKindFail = failureKind(gException);

    @Test
    @DisplayName("1. Identity: ap(of(id), v) == v")
    void identity() {
      Kind<TryKind<?>, Function<Integer, Integer>> idFuncKind = tryMonad.of(Function.identity());
      assertThat(unwrapTry(tryMonad.ap(idFuncKind, v))).isEqualTo(unwrapTry(v));
      assertThat(unwrapTry(tryMonad.ap(idFuncKind, vFail))).isEqualTo(unwrapTry(vFail));
    }

    @Test
    @DisplayName("2. Homomorphism: ap(of(f), of(x)) == of(f(x))")
    void homomorphism() {
      Kind<TryKind<?>, Function<Integer, String>> apFunc = tryMonad.of(f);
      Kind<TryKind<?>, Integer> apVal = tryMonad.of(x);
      Kind<TryKind<?>, String> leftSide = tryMonad.ap(apFunc, apVal);
      Kind<TryKind<?>, String> rightSide = tryMonad.of(f.apply(x));
      assertThat(unwrapTry(leftSide)).isEqualTo(unwrapTry(rightSide));
    }

    @Test
    @DisplayName("3. Interchange: ap(fKind, of(y)) == ap(of(f -> f(y)), fKind)")
    void interchange() {
      int y = 10;
      Kind<TryKind<?>, String> leftSide = tryMonad.ap(fKind, tryMonad.of(y));

      Function<Function<Integer, String>, String> evalWithY = fn -> fn.apply(y);
      Kind<TryKind<?>, Function<Function<Integer, String>, String>> evalKind = tryMonad.of(evalWithY);
      Kind<TryKind<?>, String> rightSide = tryMonad.ap(evalKind, fKind);

      assertThat(unwrapTry(leftSide)).isEqualTo(unwrapTry(rightSide));

      // Check failure propagation
      Kind<TryKind<?>, String> leftSideFail = tryMonad.ap(fKindFail, tryMonad.of(y));
      Kind<TryKind<?>, String> rightSideFail = tryMonad.ap(evalKind, fKindFail);
      assertThat(unwrapTry(leftSideFail)).isEqualTo(unwrapTry(rightSideFail));
    }

    @Test
    @DisplayName("4. Composition: ap(ap(map(compose, gKind), fKind), v) == ap(gKind, ap(fKind, v))")
    void composition() {
      // Using the standard definition: compose = f -> g -> f.compose(g) is tricky with currying in Java Kind
      // Let's use the equivalent structure: ap(u, ap(v, w)) == ap(ap(map(compose, u), v), w)
      // Let u = gKind, v = fKind, w = v
      Function<Function<String, String>, Function<Function<Integer, String>, Function<Integer, String>>> composeMap =
          gg -> ff -> gg.compose(ff);

      // Left side: ap(ap(map(composeMap, gKind), fKind), v)
      Kind<TryKind<?>, Function<Function<Integer, String>, Function<Integer, String>>> mappedCompose =
          tryMonad.map(composeMap, gKind);
      Kind<TryKind<?>, Function<Integer, String>> ap1 =
          tryMonad.ap(mappedCompose, fKind);
      Kind<TryKind<?>, String> leftSide = tryMonad.ap(ap1, v);

      // Right side: ap(gKind, ap(fKind, v))
      Kind<TryKind<?>, String> innerAp = tryMonad.ap(fKind, v);
      Kind<TryKind<?>, String> rightSide = tryMonad.ap(gKind, innerAp);

      assertThat(unwrapTry(leftSide)).isEqualTo(unwrapTry(rightSide));

      // Check failure propagation (simplified check, assumes first failure propagates)
      Kind<TryKind<?>, String> leftSideFailG = tryMonad.ap(tryMonad.ap(tryMonad.map(composeMap, gKindFail), fKind), v);
      Kind<TryKind<?>, String> rightSideFailG = tryMonad.ap(gKindFail, tryMonad.ap(fKind, v));
      assertThat(unwrapTry(leftSideFailG)).isEqualTo(unwrapTry(rightSideFailG)); // Should be Failure(gException)

      Kind<TryKind<?>, String> leftSideFailF = tryMonad.ap(tryMonad.ap(tryMonad.map(composeMap, gKind), fKindFail), v);
      Kind<TryKind<?>, String> rightSideFailF = tryMonad.ap(gKind, tryMonad.ap(fKindFail, v));
      assertThat(unwrapTry(leftSideFailF)).isEqualTo(unwrapTry(rightSideFailF)); // Should be Failure(fException)

      Kind<TryKind<?>, String> leftSideFailV = tryMonad.ap(tryMonad.ap(tryMonad.map(composeMap, gKind), fKind), vFail);
      Kind<TryKind<?>, String> rightSideFailV = tryMonad.ap(gKind, tryMonad.ap(fKind, vFail));
      assertThat(unwrapTry(leftSideFailV)).isEqualTo(unwrapTry(rightSideFailV)); // Should be Failure(vException)
    }
  }


  @Nested
  @DisplayName("Monad Laws")
  class MonadLaws {
    int value = 5;
    RuntimeException mException = new RuntimeException("MFail");
    Kind<TryKind<?>, Integer> mValue = successKind(value);
    Kind<TryKind<?>, Integer> mValueFail = failureKind(mException);

    Function<Integer, Kind<TryKind<?>, String>> f = i -> successKind("v" + i);
    Function<String, Kind<TryKind<?>, String>> g = s -> successKind(s + "!");
    Function<Integer, Kind<TryKind<?>, String>> fFails = i -> failureKind(new IOException("f failed"));


    @Test
    @DisplayName("1. Left Identity: flatMap(of(a), f) == f(a)")
    void leftIdentity() {
      Kind<TryKind<?>, Integer> ofValue = tryMonad.of(value);
      Kind<TryKind<?>, String> leftSide = tryMonad.flatMap(f, ofValue);
      Kind<TryKind<?>, String> rightSide = f.apply(value);
      assertThat(unwrapTry(leftSide)).isEqualTo(unwrapTry(rightSide));
    }

    @Test
    @DisplayName("2. Right Identity: flatMap(m, of) == m")
    void rightIdentity() {
      Function<Integer, Kind<TryKind<?>, Integer>> ofFunc = i -> tryMonad.of(i);
      Kind<TryKind<?>, Integer> leftSide = tryMonad.flatMap(ofFunc, mValue);
      assertThat(unwrapTry(leftSide)).isEqualTo(unwrapTry(mValue));

      // Check failure
      Kind<TryKind<?>, Integer> leftSideFail = tryMonad.flatMap(ofFunc, mValueFail);
      assertThat(unwrapTry(leftSideFail)).isEqualTo(unwrapTry(mValueFail));
    }


    @Test
    @DisplayName("3. Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
    void associativity() {
      // Success case
      Kind<TryKind<?>, String> innerFlatMap = tryMonad.flatMap(f, mValue);
      Kind<TryKind<?>, String> leftSide = tryMonad.flatMap(g, innerFlatMap);

      Function<Integer, Kind<TryKind<?>, String>> rightSideFunc =
          a -> tryMonad.flatMap(g, f.apply(a));
      Kind<TryKind<?>, String> rightSide = tryMonad.flatMap(rightSideFunc, mValue);

      assertThat(unwrapTry(leftSide)).isEqualTo(unwrapTry(rightSide));

      // Failure case 1: initial m fails
      Kind<TryKind<?>, String> innerFlatMapFailM = tryMonad.flatMap(f, mValueFail);
      Kind<TryKind<?>, String> leftSideFailM = tryMonad.flatMap(g, innerFlatMapFailM);
      Kind<TryKind<?>, String> rightSideFailM = tryMonad.flatMap(rightSideFunc, mValueFail);
      assertThat(unwrapTry(leftSideFailM)).isEqualTo(unwrapTry(rightSideFailM)); // Failure(mException)

      // Failure case 2: function f fails
      Kind<TryKind<?>, String> innerFlatMapFailF = tryMonad.flatMap(fFails, mValue); // Failure(IOException)
      Kind<TryKind<?>, String> leftSideFailF = tryMonad.flatMap(g, innerFlatMapFailF); // Failure(IOException)

      Function<Integer, Kind<TryKind<?>, String>> rightSideFuncFailF =
          a -> tryMonad.flatMap(g, fFails.apply(a)); // applies fFails, gets Failure, flatMap propagates
      Kind<TryKind<?>, String> rightSideFailF = tryMonad.flatMap(rightSideFuncFailF, mValue); // Failure(IOException)

      // Assert that both sides result in Failure with the same exception type and message
      // because fFails creates a new exception instance each time.
      Try<String> leftTry = unwrapTry(leftSideFailF);
      Try<String> rightTry = unwrapTry(rightSideFailF);
      assertThat(leftTry.isFailure()).isTrue();
      assertThat(rightTry.isFailure()).isTrue();
      // Compare exception type and message, not instance equality
      assertThat(leftTry).asInstanceOf(InstanceOfAssertFactories.type(Try.Failure.class))
          .extracting(Try.Failure::cause)
          .satisfies(cause -> {
            assertThat(cause).isInstanceOf(IOException.class);
            assertThat(cause.getMessage()).isEqualTo("f failed");
          });
      assertThat(rightTry).asInstanceOf(InstanceOfAssertFactories.type(Try.Failure.class))
          .extracting(Try.Failure::cause)
          .satisfies(cause -> {
            assertThat(cause).isInstanceOf(IOException.class);
            assertThat(cause.getMessage()).isEqualTo("f failed");
          });
    }
  }

  // --- mapN Tests --- (Using default implementations from Applicative)
  @Nested
  @DisplayName("mapN tests")
  class MapNTests {

    Kind<TryKind<?>, Integer> try1 = successKind(10);
    Kind<TryKind<?>, String> try2 = successKind("hello");
    Kind<TryKind<?>, Double> try3 = successKind(1.5);
    Kind<TryKind<?>, Boolean> try4 = successKind(true);

    RuntimeException testException = new RuntimeException("MapN Fail");
    Kind<TryKind<?>, Integer> failTry = failureKind(testException);

    @Test
    void map2_bothSuccess() throws Throwable {
      Function<Integer, Function<String, String>> f2 = i -> s -> i + s; // Using curried version for default map2
      Kind<TryKind<?>, String> result = tryMonad.map2(try1, try2, f2);
      assertThat(unwrapTry(result).isSuccess()).isTrue();
      assertThat(getOrThrow(result)).isEqualTo("10hello");
    }

    @Test
    void map2_firstFails() {
      Function<Integer, Function<String, String>> f2 = i -> s -> i + s;
      Kind<TryKind<?>, String> result = tryMonad.map2(failTry, try2, f2);
      assertThat(unwrapTry(result).isFailure()).isTrue();
      assertThatThrownBy(() -> getOrThrow(result)).isSameAs(testException);
    }

    @Test
    void map2_secondFails() {
      Function<Integer, Function<String, String>> f2 = i -> s -> i + s;
      Kind<TryKind<?>, String> result = tryMonad.map2(try1, failureKind(testException), f2);
      assertThat(unwrapTry(result).isFailure()).isTrue();
      assertThatThrownBy(() -> getOrThrow(result)).isSameAs(testException);
    }

    @Test
    void map3_allSuccess() throws Throwable {
      Function3<Integer, String, Double, String> f3 =
          (i, s, d) -> String.format("I:%d S:%s D:%.1f", i, s, d);
      Kind<TryKind<?>, String> result = tryMonad.map3(try1, try2, try3, f3);
      assertThat(unwrapTry(result).isSuccess()).isTrue();
      assertThat(getOrThrow(result)).isEqualTo("I:10 S:hello D:1.5");
    }

    @Test
    void map3_middleFails() {
      Function3<Integer, String, Double, String> f3 = (i, s, d) -> "Should not execute";
      Kind<TryKind<?>, String> result = tryMonad.map3(try1, failureKind(testException), try3, f3);
      assertThat(unwrapTry(result).isFailure()).isTrue();
      assertThatThrownBy(() -> getOrThrow(result)).isSameAs(testException);
    }

    @Test
    void map4_allSuccess() throws Throwable {
      Function4<Integer, String, Double, Boolean, String> f4 =
          (i, s, d, b) -> String.format("I:%d S:%s D:%.1f B:%b", i, s, d, b);
      Kind<TryKind<?>, String> result = tryMonad.map4(try1, try2, try3, try4, f4);
      assertThat(unwrapTry(result).isSuccess()).isTrue();
      assertThat(getOrThrow(result)).isEqualTo("I:10 S:hello D:1.5 B:true");
    }

    @Test
    void map4_lastFails() {
      Function4<Integer, String, Double, Boolean, String> f4 = (i, s, d, b) -> "Should not execute";
      Kind<TryKind<?>, String> result = tryMonad.map4(try1, try2, try3, failureKind(testException), f4);
      assertThat(unwrapTry(result).isFailure()).isTrue();
      assertThatThrownBy(() -> getOrThrow(result)).isSameAs(testException);
    }
  }
}
