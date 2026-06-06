// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.VTaskAssert.assertThatVTask;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.vtask.VTaskKindHelper.VTASK;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("VTaskMonad Test Suite")
class VTaskMonadTest {

  private static final int TEST_VALUE = 42;
  private final MonadError<VTaskKind.Witness, Throwable> monad = Instances.monadError(vtask());

  // --- MonadError contract inputs (see monadErrorContract) ---
  private final Kind<VTaskKind.Witness, Integer> validKind = VTASK.widen(VTask.succeed(TEST_VALUE));
  private final Function<Integer, String> validMapper = i -> "v" + i;
  private final Function<Integer, Kind<VTaskKind.Witness, String>> validFlatMapper =
      i -> VTASK.widen(VTask.succeed("flat:" + i));
  private final Kind<VTaskKind.Witness, Function<Integer, String>> validFunctionKind =
      VTASK.widen(VTask.succeed(i -> "v" + i));
  private final Function<Throwable, Kind<VTaskKind.Witness, Integer>> validHandler =
      _ -> VTASK.widen(VTask.succeed(0));
  private final Kind<VTaskKind.Witness, Integer> validFallback = VTASK.widen(VTask.succeed(-1));

  /**
   * Operation smoke for {@code map}/{@code flatMap}/{@code ap}/{@code handleErrorWith}/{@code
   * recoverWith} on the MonadError instance. The Monad laws are verified parameterised in {@link
   * Laws} below, so this contract omits {@link Category#LAWS}.
   *
   * <p>Only {@link Category#OPERATIONS} is run:
   *
   * <ul>
   *   <li>{@link Category#EXCEPTIONS} is omitted because the generic contract asserts that {@code
   *       map}/{@code flatMap} <em>propagate</em> a thrown function exception, whereas a VTask is
   *       lazy and surfaces it only when run (exercised in the operation tests below).
   *   <li>{@link Category#VALIDATIONS} is omitted because, like {@code Try}, {@code VTaskMonad}
   *       inherits the default {@code recoverWith}, which does not eagerly reject a null fallback
   *       against a success. The standard null-argument validations are kept in {@link
   *       ValidationTests} below.
   * </ul>
   */
  @Test
  @DisplayName("MonadError contract — operations (laws verified in Laws below)")
  void monadErrorContract() {
    TypeClassContract.<VTaskKind.Witness, Throwable>monadError(VTaskMonad.class)
        .<Integer>instance(monad)
        .<String>withKind(validKind)
        .withMonadOperations(validMapper, validFlatMapper, validFunctionKind)
        .withErrorHandling(validHandler, validFallback)
        .verifyOnly(Category.OPERATIONS);
  }

  @Nested
  @DisplayName("flatMap() Operations")
  class FlatMapOperations {

    @Test
    @DisplayName("flatMap() sequences computations")
    void flatMapSequencesComputations() {
      Kind<VTaskKind.Witness, Integer> ma = VTASK.widen(VTask.succeed(TEST_VALUE));
      Function<Integer, Kind<VTaskKind.Witness, String>> f =
          i -> VTASK.widen(VTask.succeed("Value: " + i));

      Kind<VTaskKind.Witness, String> result = monad.flatMap(f, ma);

      VTask<String> vtask = VTASK.narrow(result);
      assertThat(vtask.run()).isEqualTo("Value: 42");
    }

    @Test
    @DisplayName("flatMap() chains multiple operations")
    void flatMapChainsMultipleOperations() {
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
          _ -> VTASK.widen(VTask.fail(exception));

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

      assertThatVTask(VTASK.narrow(result))
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
    void handleErrorWithRecoversFromError() {
      RuntimeException exception = new RuntimeException("Original error");
      Kind<VTaskKind.Witness, Integer> ma = VTASK.widen(VTask.fail(exception));
      Function<Throwable, Kind<VTaskKind.Witness, Integer>> handler =
          _ -> VTASK.widen(VTask.succeed(-1));

      Kind<VTaskKind.Witness, Integer> result = monad.handleErrorWith(ma, handler);

      VTask<Integer> vtask = VTASK.narrow(result);
      assertThat(vtask.run()).isEqualTo(-1);
    }

    @Test
    @DisplayName("handleErrorWith() passes through successful VTask")
    void handleErrorWithPassesThroughSuccessfulVTask() {
      Kind<VTaskKind.Witness, Integer> ma = VTASK.widen(VTask.succeed(TEST_VALUE));
      Function<Throwable, Kind<VTaskKind.Witness, Integer>> handler =
          _ -> VTASK.widen(VTask.succeed(-1));

      Kind<VTaskKind.Witness, Integer> result = monad.handleErrorWith(ma, handler);

      VTask<Integer> vtask = VTASK.narrow(result);
      assertThat(vtask.run()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("handleErrorWith() can inspect the error")
    void handleErrorWithCanInspectError() {
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

      assertThatVTask(VTASK.narrow(result))
          .fails()
          .withExceptionType(IllegalStateException.class)
          .withMessageContaining("Transformed: Original");
    }
  }

  @Nested
  @DisplayName("Validation Tests")
  @SuppressWarnings("DataFlowIssue") // null arguments are passed deliberately to verify rejection
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
          _ -> VTASK.widen(VTask.succeed(-1));

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
  @DisplayName("Laws")
  class Laws {

    private final BiPredicate<Kind<VTaskKind.Witness, ?>, Kind<VTaskKind.Witness, ?>> eq =
        (k1, k2) -> Objects.equals(VTASK.narrow(k1).run(), VTASK.narrow(k2).run());

    private final Function<Integer, Kind<VTaskKind.Witness, String>> f =
        i -> VTASK.widen(VTask.succeed("Value: " + i));
    private final Function<String, Kind<VTaskKind.Witness, Integer>> g =
        s -> VTASK.widen(VTask.succeed(s.length()));

    @ParameterizedTest(name = "left identity holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.vtask.VTaskLawFixtures#values")
    void leftIdentity(Integer value) {
      MonadLaws.assertLeftIdentity(monad, value, f, eq);
    }

    @ParameterizedTest(name = "right identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.vtask.VTaskLawFixtures#kinds")
    void rightIdentity(String label, Kind<VTaskKind.Witness, Integer> ma) {
      MonadLaws.assertRightIdentity(monad, ma, eq);
    }

    @ParameterizedTest(name = "associativity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.vtask.VTaskLawFixtures#kinds")
    void associativity(String label, Kind<VTaskKind.Witness, Integer> ma) {
      MonadLaws.assertAssociativity(monad, ma, f, g, eq);
    }
  }
}
