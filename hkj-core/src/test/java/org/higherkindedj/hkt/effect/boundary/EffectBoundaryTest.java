// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.boundary;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.FreePath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.trymonad.Try;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link EffectBoundary}.
 *
 * <p>Uses a minimal in-test DSL to avoid depending on external modules.
 */
@DisplayName("EffectBoundary Tests")
class EffectBoundaryTest {

  // ===== Minimal test DSL =====

  /** A simple effect: store a string and return it uppercased. */
  sealed interface TestOp<A> {
    record Store<A>(String value, Function<String, A> k) implements TestOp<A> {}

    record Fail<A>(String message) implements TestOp<A> {}
  }

  /** Kind marker for TestOp. */
  interface TestOpKind<A> extends Kind<TestOpKind.Witness, A> {
    final class Witness implements WitnessArity<TypeArity.Unary> {
      private Witness() {}
    }
  }

  record TestOpHolder<A>(TestOp<A> op) implements TestOpKind<A> {}

  static <A> Kind<TestOpKind.Witness, A> widen(TestOp<A> op) {
    return new TestOpHolder<>(op);
  }

  @SuppressWarnings("unchecked")
  static <A> TestOp<A> narrow(Kind<TestOpKind.Witness, A> kind) {
    return ((TestOpHolder<A>) kind).op();
  }

  /** Functor for TestOp (required by Free.liftF). */
  static final Functor<TestOpKind.Witness> TEST_FUNCTOR =
      new Functor<>() {
        @Override
        public <A, B> Kind<TestOpKind.Witness, B> map(
            Function<? super A, ? extends B> f, Kind<TestOpKind.Witness, A> fa) {
          TestOp<A> op = narrow(fa);
          return switch (op) {
            case TestOp.Store<A> s -> widen(new TestOp.Store<>(s.value(), s.k().andThen(f)));
            case TestOp.Fail<A> fail -> widen(new TestOp.Fail<>(fail.message()));
          };
        }
      };

  /** Smart constructor: lift a Store operation into Free. */
  static Free<TestOpKind.Witness, String> store(String value) {
    return Free.liftF(widen(new TestOp.Store<>(value, Function.identity())), TEST_FUNCTOR);
  }

  /** Smart constructor: lift a Fail operation into Free. */
  static <A> Free<TestOpKind.Witness, A> fail(String message) {
    return Free.liftF(widen(new TestOp.Fail<A>(message)), TEST_FUNCTOR);
  }

  // ===== Recording interpreter (IO target) =====

  static class RecordingInterpreter implements Natural<TestOpKind.Witness, IOKind.Witness> {
    private final List<String> stored = new ArrayList<>();

    @Override
    public <A> Kind<IOKind.Witness, A> apply(Kind<TestOpKind.Witness, A> fa) {
      TestOp<A> op = narrow(fa);
      return switch (op) {
        case TestOp.Store<A> s -> {
          stored.add(s.value());
          @SuppressWarnings("unchecked")
          A result = (A) s.k().apply(s.value().toUpperCase());
          yield IO_OP.widen(IO.delay(() -> result));
        }
        case TestOp.Fail<A> f ->
            IO_OP.widen(
                IO.delay(
                    () -> {
                      throw new RuntimeException(f.message());
                    }));
      };
    }

    List<String> stored() {
      return stored;
    }
  }

  // ===== Tests =====

  private final RecordingInterpreter interpreter = new RecordingInterpreter();
  private final EffectBoundary<TestOpKind.Witness> boundary = EffectBoundary.of(interpreter);

  @Nested
  @DisplayName("of() - Factory")
  class FactoryTests {

    @Test
    @DisplayName("Should create boundary from interpreter")
    void shouldCreateBoundary() {
      assertThat(boundary).isNotNull();
      assertThat(boundary.interpreter()).isSameAs(interpreter);
    }

    @Test
    @DisplayName("Should reject null interpreter")
    void shouldRejectNullInterpreter() {
      assertThatNullPointerException().isThrownBy(() -> EffectBoundary.of(null));
    }
  }

  @Nested
  @DisplayName("run(Free) - Synchronous Execution")
  class RunFreeTests {

    @Test
    @DisplayName("Should execute pure program and return value")
    void shouldExecutePureProgram() {
      Free<TestOpKind.Witness, String> program = Free.pure("hello");
      assertThat(boundary.run(program)).isEqualTo("hello");
    }

    @Test
    @DisplayName("Should interpret effect operations")
    void shouldInterpretEffectOperations() {
      Free<TestOpKind.Witness, String> program = store("hello");
      String result = boundary.run(program);
      assertThat(result).isEqualTo("HELLO");
      assertThat(interpreter.stored()).containsExactly("hello");
    }

    @Test
    @DisplayName("Should chain multiple effects via flatMap")
    void shouldChainMultipleEffects() {
      Free<TestOpKind.Witness, String> program =
          store("hello").flatMap(h -> store("world").map(w -> h + " " + w));
      String result = boundary.run(program);
      assertThat(result).isEqualTo("HELLO WORLD");
      assertThat(interpreter.stored()).containsExactly("hello", "world");
    }

    @Test
    @DisplayName("Should propagate interpreter exceptions")
    void shouldPropagateExceptions() {
      Free<TestOpKind.Witness, String> program = fail("boom");
      assertThatRuntimeException().isThrownBy(() -> boundary.run(program)).withMessage("boom");
    }

    @Test
    @DisplayName("Should reject null program")
    void shouldRejectNullProgram() {
      assertThatNullPointerException()
          .isThrownBy(() -> boundary.run((Free<TestOpKind.Witness, String>) null));
    }
  }

  @Nested
  @DisplayName("run(FreePath) - FreePath Convenience")
  class RunFreePathTests {

    @Test
    @DisplayName("Should interpret FreePath programs")
    void shouldInterpretFreePath() {
      FreePath<TestOpKind.Witness, String> program = FreePath.of(store("test"), TEST_FUNCTOR);
      String result = boundary.run(program);
      assertThat(result).isEqualTo("TEST");
      assertThat(interpreter.stored()).containsExactly("test");
    }

    @Test
    @DisplayName("Should reject null FreePath program")
    void shouldRejectNullFreePath() {
      assertThatNullPointerException()
          .isThrownBy(() -> boundary.run((FreePath<TestOpKind.Witness, String>) null));
    }
  }

  @Nested
  @DisplayName("runSafe() - Exception Capture")
  class RunSafeTests {

    @Test
    @DisplayName("Should return Try.Success on success")
    void shouldReturnSuccessOnSuccess() {
      Free<TestOpKind.Witness, String> program = store("hello");
      Try<String> result = boundary.runSafe(program);
      assertThat(result.isSuccess()).isTrue();
      assertThat(result.orElse(null)).isEqualTo("HELLO");
    }

    @Test
    @DisplayName("Should return Try.Failure on exception")
    void shouldReturnFailureOnException() {
      Free<TestOpKind.Witness, String> program = fail("boom");
      Try<String> result = boundary.runSafe(program);
      assertThat(result.isFailure()).isTrue();
      assertThat(result).isInstanceOf(Try.Failure.class);
      Throwable cause = ((Try.Failure<String>) result).cause();
      assertThat(cause).isInstanceOf(RuntimeException.class);
      assertThat(cause.getMessage()).isEqualTo("boom");
    }

    @Test
    @DisplayName("Should reject null program")
    void shouldRejectNullProgram() {
      assertThatNullPointerException().isThrownBy(() -> boundary.runSafe(null));
    }
  }

  @Nested
  @DisplayName("runAsync() - Asynchronous Execution")
  class RunAsyncTests {

    @Test
    @DisplayName("Should execute program asynchronously")
    void shouldExecuteAsync() throws Exception {
      Free<TestOpKind.Witness, String> program = store("async");
      CompletableFuture<String> future = boundary.runAsync(program);
      assertThat(future.get()).isEqualTo("ASYNC");
    }

    @Test
    @DisplayName("Should complete exceptionally on failure")
    void shouldCompleteExceptionallyOnFailure() {
      Free<TestOpKind.Witness, String> program = fail("async-boom");
      CompletableFuture<String> future = boundary.runAsync(program);
      assertThatThrownBy(future::get)
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(RuntimeException.class)
          .hasRootCauseMessage("async-boom");
    }

    @Test
    @DisplayName("Should reject null program")
    void shouldRejectNullProgram() {
      assertThatNullPointerException().isThrownBy(() -> boundary.runAsync(null));
    }
  }

  @Nested
  @DisplayName("runIO() - Deferred Execution")
  class RunIOTests {

    @Test
    @DisplayName("Should return IOPath without executing immediately")
    void shouldDeferExecution() {
      var freshInterpreter = new RecordingInterpreter();
      var lazyBoundary = EffectBoundary.of(freshInterpreter);

      IOPath<String> ioPath = lazyBoundary.runIO(store("deferred"));

      // Not yet executed
      assertThat(freshInterpreter.stored()).isEmpty();

      // Execute now
      String result = ioPath.unsafeRun();
      assertThat(result).isEqualTo("DEFERRED");
      assertThat(freshInterpreter.stored()).containsExactly("deferred");
    }

    @Test
    @DisplayName("Should return IOPath from FreePath")
    void shouldDeferFreePathExecution() {
      var freshInterpreter = new RecordingInterpreter();
      var lazyBoundary = EffectBoundary.of(freshInterpreter);

      FreePath<TestOpKind.Witness, String> program =
          FreePath.of(store("fp-deferred"), TEST_FUNCTOR);
      IOPath<String> ioPath = lazyBoundary.runIO(program);

      assertThat(freshInterpreter.stored()).isEmpty();
      String result = ioPath.unsafeRun();
      assertThat(result).isEqualTo("FP-DEFERRED");
    }

    @Test
    @DisplayName("Should capture exceptions via runSafe on IOPath")
    void shouldCaptureExceptionsViaRunSafe() {
      IOPath<String> ioPath = boundary.runIO(fail("io-boom"));
      Try<String> result = ioPath.runSafe();
      assertThat(result.isFailure()).isTrue();
      assertThat(result).isInstanceOf(Try.Failure.class);
      assertThat(((Try.Failure<String>) result).cause().getMessage()).isEqualTo("io-boom");
    }

    @Test
    @DisplayName("Should reject null Free program")
    void shouldRejectNullFreeProgram() {
      assertThatNullPointerException()
          .isThrownBy(() -> boundary.runIO((Free<TestOpKind.Witness, String>) null));
    }

    @Test
    @DisplayName("Should reject null FreePath program")
    void shouldRejectNullFreePathProgram() {
      assertThatNullPointerException()
          .isThrownBy(() -> boundary.runIO((FreePath<TestOpKind.Witness, String>) null));
    }
  }

  @Nested
  @DisplayName("embed() - Lift IO into Free")
  class EmbedTests {

    @Test
    @DisplayName("Should embed IO action as pure Free value")
    void shouldEmbedIO() {
      Free<TestOpKind.Witness, String> embedded = boundary.embed(IO.delay(() -> "from-io"));
      String result = boundary.run(embedded);
      assertThat(result).isEqualTo("from-io");
    }

    @Test
    @DisplayName("Should reject null IO")
    void shouldRejectNullIO() {
      assertThatNullPointerException().isThrownBy(() -> boundary.embed(null));
    }
  }

  @Nested
  @DisplayName("embedPath() - Lift IO into FreePath")
  class EmbedPathTests {

    @Test
    @DisplayName("Should embed IO action as pure FreePath value")
    void shouldEmbedPath() {
      FreePath<TestOpKind.Witness, String> embedded =
          boundary.embedPath(IO.delay(() -> "from-io-path"), TEST_FUNCTOR);
      String result = boundary.run(embedded);
      assertThat(result).isEqualTo("from-io-path");
    }

    @Test
    @DisplayName("Should reject null IO")
    void shouldRejectNullIO() {
      assertThatNullPointerException().isThrownBy(() -> boundary.embedPath(null, TEST_FUNCTOR));
    }

    @Test
    @DisplayName("Should reject null functor")
    void shouldRejectNullFunctor() {
      assertThatNullPointerException()
          .isThrownBy(() -> boundary.embedPath(IO.delay(() -> "x"), null));
    }
  }
}
