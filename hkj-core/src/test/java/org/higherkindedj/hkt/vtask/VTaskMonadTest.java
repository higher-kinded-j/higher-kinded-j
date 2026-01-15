// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.vtask.VTaskAssert.assertThatVTask;
import static org.higherkindedj.hkt.vtask.VTaskKindHelper.VTASK;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("VTaskMonad Test Suite")
class VTaskMonadTest {

  private static final int TEST_VALUE = 42;
  private static final String TEST_STRING = "hello";
  private final VTaskMonad monad = VTaskMonad.INSTANCE;

  @Nested
  @DisplayName("flatMap() Operations")
  class FlatMapOperations {

    @Test
    @DisplayName("flatMap() sequences computations")
    void flatMapSequencesComputations() throws Throwable {
      Kind<VTaskKind.Witness, Integer> ma = VTASK.widen(VTask.succeed(TEST_VALUE));
      Function<Integer, Kind<VTaskKind.Witness, String>> f =
          i -> VTASK.widen(VTask.succeed("Value: " + i));

      Kind<VTaskKind.Witness, String> result = monad.flatMap(f, ma);

      VTask<String> vtask = VTASK.narrow(result);
      assertThat(vtask.run()).isEqualTo("Value: 42");
    }

    @Test
    @DisplayName("flatMap() chains multiple operations")
    void flatMapChainsMultipleOperations() throws Throwable {
      Kind<VTaskKind.Witness, Integer> ma = VTASK.widen(VTask.succeed(TEST_VALUE));

      Kind<VTaskKind.Witness, String> result =
          monad.flatMap(
              i ->
                  monad.flatMap(
                      s -> VTASK.widen(VTask.succeed(s + "!")),
                      VTASK.widen(VTask.succeed("Value: " + i))),
              ma);

      VTask<String> vtask = VTASK.narrow(result);
      assertThat(vtask.run()).isEqualTo("Value: 42!");
    }

    @Test
    @DisplayName("flatMap() propagates exception from source")
    void flatMapPropagatesExceptionFromSource() {
      RuntimeException exception = new RuntimeException("Source failed");
      Kind<VTaskKind.Witness, Integer> ma = VTASK.widen(VTask.fail(exception));
      Function<Integer, Kind<VTaskKind.Witness, String>> f =
          i -> VTASK.widen(VTask.succeed("Value: " + i));

      Kind<VTaskKind.Witness, String> result = monad.flatMap(f, ma);

      assertThatVTask(VTASK.narrow(result))
          .fails()
          .withExceptionType(RuntimeException.class)
          .withMessage("Source failed");
    }

    @Test
    @DisplayName("flatMap() propagates exception from function result")
    void flatMapPropagatesExceptionFromFunctionResult() {
      Kind<VTaskKind.Witness, Integer> ma = VTASK.widen(VTask.succeed(TEST_VALUE));
      RuntimeException exception = new RuntimeException("Function result failed");
      Function<Integer, Kind<VTaskKind.Witness, String>> f =
          i -> VTASK.widen(VTask.fail(exception));

      Kind<VTaskKind.Witness, String> result = monad.flatMap(f, ma);

      assertThatVTask(VTASK.narrow(result))
          .fails()
          .withExceptionType(RuntimeException.class)
          .withMessage("Function result failed");
    }
  }

  @Nested
  @DisplayName("MonadError Operations")
  class MonadErrorOperations {

    @Test
    @DisplayName("raiseError() creates failing VTask")
    void raiseErrorCreatesFailingVTask() {
      RuntimeException exception = new RuntimeException("Test error");
      Kind<VTaskKind.Witness, Integer> result = monad.raiseError(exception);

      assertThatVTask(VTASK.<Integer>narrow(result))
          .fails()
          .withExceptionType(RuntimeException.class)
          .withMessage("Test error");
    }

    @Test
    @DisplayName("raiseError() with null throws NullPointerException")
    void raiseErrorWithNullThrowsNPE() {
      assertThatThrownBy(() -> monad.raiseError(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("handleErrorWith() recovers from error")
    void handleErrorWithRecoversFromError() throws Throwable {
      RuntimeException exception = new RuntimeException("Original error");
      Kind<VTaskKind.Witness, Integer> ma = VTASK.widen(VTask.fail(exception));
      Function<Throwable, Kind<VTaskKind.Witness, Integer>> handler =
          e -> VTASK.widen(VTask.succeed(-1));

      Kind<VTaskKind.Witness, Integer> result = monad.handleErrorWith(ma, handler);

      VTask<Integer> vtask = VTASK.narrow(result);
      assertThat(vtask.run()).isEqualTo(-1);
    }

    @Test
    @DisplayName("handleErrorWith() passes through successful VTask")
    void handleErrorWithPassesThroughSuccessfulVTask() throws Throwable {
      Kind<VTaskKind.Witness, Integer> ma = VTASK.widen(VTask.succeed(TEST_VALUE));
      Function<Throwable, Kind<VTaskKind.Witness, Integer>> handler =
          e -> VTASK.widen(VTask.succeed(-1));

      Kind<VTaskKind.Witness, Integer> result = monad.handleErrorWith(ma, handler);

      VTask<Integer> vtask = VTASK.narrow(result);
      assertThat(vtask.run()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("handleErrorWith() can inspect the error")
    void handleErrorWithCanInspectError() throws Throwable {
      RuntimeException exception = new RuntimeException("Specific error");
      Kind<VTaskKind.Witness, String> ma = VTASK.widen(VTask.fail(exception));
      Function<Throwable, Kind<VTaskKind.Witness, String>> handler =
          e -> VTASK.widen(VTask.succeed("Recovered from: " + e.getMessage()));

      Kind<VTaskKind.Witness, String> result = monad.handleErrorWith(ma, handler);

      VTask<String> vtask = VTASK.narrow(result);
      assertThat(vtask.run()).isEqualTo("Recovered from: Specific error");
    }

    @Test
    @DisplayName("handleErrorWith() can re-throw different error")
    void handleErrorWithCanReThrowDifferentError() {
      RuntimeException originalException = new RuntimeException("Original");
      Kind<VTaskKind.Witness, Integer> ma = VTASK.widen(VTask.fail(originalException));
      Function<Throwable, Kind<VTaskKind.Witness, Integer>> handler =
          e -> VTASK.widen(VTask.fail(new IllegalStateException("Transformed: " + e.getMessage())));

      Kind<VTaskKind.Witness, Integer> result = monad.handleErrorWith(ma, handler);

      assertThatVTask(VTASK.<Integer>narrow(result))
          .fails()
          .withExceptionType(IllegalStateException.class)
          .withMessageContaining("Transformed: Original");
    }
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {

    @Test
    @DisplayName("flatMap() validates null function")
    void flatMapValidatesNullFunction() {
      Kind<VTaskKind.Witness, Integer> ma = VTASK.widen(VTask.succeed(TEST_VALUE));

      assertThatThrownBy(() -> monad.flatMap(null, ma)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("flatMap() validates null Kind")
    void flatMapValidatesNullKind() {
      Function<Integer, Kind<VTaskKind.Witness, String>> f =
          i -> VTASK.widen(VTask.succeed("Value: " + i));

      assertThatThrownBy(() -> monad.flatMap(f, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("handleErrorWith() validates null Kind")
    void handleErrorWithValidatesNullKind() {
      Function<Throwable, Kind<VTaskKind.Witness, Integer>> handler =
          e -> VTASK.widen(VTask.succeed(-1));

      assertThatThrownBy(() -> monad.handleErrorWith(null, handler))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("handleErrorWith() validates null handler")
    void handleErrorWithValidatesNullHandler() {
      Kind<VTaskKind.Witness, Integer> ma = VTASK.widen(VTask.succeed(TEST_VALUE));

      assertThatThrownBy(() -> monad.handleErrorWith(ma, null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLaws {

    @Test
    @DisplayName("Left identity: flatMap(f, of(a)) == f(a)")
    void leftIdentityLaw() throws Throwable {
      Function<Integer, Kind<VTaskKind.Witness, String>> f =
          i -> VTASK.widen(VTask.succeed("Value: " + i));

      Kind<VTaskKind.Witness, String> flatMapResult = monad.flatMap(f, monad.of(TEST_VALUE));
      Kind<VTaskKind.Witness, String> directResult = f.apply(TEST_VALUE);

      assertThat(VTASK.narrow(flatMapResult).run()).isEqualTo(VTASK.narrow(directResult).run());
    }

    @Test
    @DisplayName("Right identity: flatMap(of, ma) == ma")
    void rightIdentityLaw() throws Throwable {
      VTask<Integer> original = VTask.succeed(TEST_VALUE);
      Kind<VTaskKind.Witness, Integer> ma = VTASK.widen(original);
      Function<Integer, Kind<VTaskKind.Witness, Integer>> ofFunc = monad::of;

      Kind<VTaskKind.Witness, Integer> result = monad.flatMap(ofFunc, ma);

      assertThat(VTASK.narrow(result).run()).isEqualTo(original.run());
    }

    @Test
    @DisplayName("Associativity: flatMap(g, flatMap(f, ma)) == flatMap(x -> flatMap(g, f(x)), ma)")
    void associativityLaw() throws Throwable {
      Kind<VTaskKind.Witness, Integer> ma = VTASK.widen(VTask.succeed(TEST_VALUE));
      Function<Integer, Kind<VTaskKind.Witness, String>> f =
          i -> VTASK.widen(VTask.succeed("Value: " + i));
      Function<String, Kind<VTaskKind.Witness, Integer>> g =
          s -> VTASK.widen(VTask.succeed(s.length()));

      // Left side: flatMap(g, flatMap(f, ma))
      Kind<VTaskKind.Witness, Integer> leftSide = monad.flatMap(g, monad.flatMap(f, ma));

      // Right side: flatMap(x -> flatMap(g, f(x)), ma)
      Kind<VTaskKind.Witness, Integer> rightSide =
          monad.flatMap(x -> monad.flatMap(g, f.apply(x)), ma);

      assertThat(VTASK.narrow(leftSide).run()).isEqualTo(VTASK.narrow(rightSide).run());
    }
  }
}
