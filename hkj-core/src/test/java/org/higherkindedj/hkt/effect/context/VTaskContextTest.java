// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.context;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.test.assertions.VTaskContextAssert.assertThatVTaskContext;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for VTaskContext.
 *
 * <p>Tests cover factory methods, transformation operations, error recovery, execution methods, and
 * conversion methods.
 */
@DisplayName("VTaskContext<A> Complete Test Suite")
class VTaskContextTest {

  private static final String TEST_VALUE = "test";
  private static final Integer TEST_INT = 42;

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethodsTests {

    @Test
    @DisplayName("of() creates VTaskContext from callable")
    void ofCreatesVTaskContextFromCallable() {
      VTaskContext<Integer> context = VTaskContext.of(() -> 10 + 32);

      assertThatVTaskContext(context).succeeds().hasValue(42);
    }

    @Test
    @DisplayName("of() is lazy - does not execute until run")
    void ofIsLazy() {
      AtomicBoolean executed = new AtomicBoolean(false);

      VTaskContext<Integer> context =
          VTaskContext.of(
              () -> {
                executed.set(true);
                return 42;
              });

      assertThat(executed).isFalse();
      context.runOrThrow();
      assertThat(executed).isTrue();
    }

    @Test
    @DisplayName("exec() creates VTaskContext from runnable")
    void execCreatesVTaskContextFromRunnable() {
      AtomicBoolean executed = new AtomicBoolean(false);

      VTaskContext<Unit> context = VTaskContext.exec(() -> executed.set(true));

      assertThat(executed).isFalse();
      context.runOrThrow();
      assertThat(executed).isTrue();
    }

    @Test
    @DisplayName("pure() creates VTaskContext with pure value")
    void pureCreatesVTaskContextWithPureValue() {
      VTaskContext<Integer> context = VTaskContext.pure(TEST_INT);

      assertThatVTaskContext(context).succeeds().hasValue(TEST_INT);
    }

    @Test
    @DisplayName("fail() creates failed VTaskContext")
    void failCreatesFailedVTaskContext() {
      RuntimeException error = new RuntimeException("test error");
      VTaskContext<String> context = VTaskContext.fail(error);

      assertThatVTaskContext(context)
          .fails()
          .withExceptionType(RuntimeException.class)
          .withExceptionMessage("test error");
    }

    @Test
    @DisplayName("fromPath() creates VTaskContext from VTaskPath")
    void fromPathCreatesVTaskContextFromVTaskPath() {
      VTaskPath<Integer> path = Path.vtaskPure(TEST_INT);
      VTaskContext<Integer> context = VTaskContext.fromPath(path);

      assertThatVTaskContext(context).succeeds().hasValue(TEST_INT);
    }

    @Test
    @DisplayName("fromVTask() creates VTaskContext from VTask")
    void fromVTaskCreatesVTaskContextFromVTask() {
      VTask<Integer> vtask = VTask.succeed(TEST_INT);
      VTaskContext<Integer> context = VTaskContext.fromVTask(vtask);

      assertThatVTaskContext(context).succeeds().hasValue(TEST_INT);
    }

    @Test
    @DisplayName("of() validates non-null callable")
    void ofValidatesNonNullCallable() {
      assertThatNullPointerException()
          .isThrownBy(() -> VTaskContext.of(null))
          .withMessageContaining("callable must not be null");
    }

    @Test
    @DisplayName("fail() validates non-null error")
    void failValidatesNonNullError() {
      assertThatNullPointerException()
          .isThrownBy(() -> VTaskContext.fail(null))
          .withMessageContaining("error must not be null");
    }
  }

  @Nested
  @DisplayName("Transformation Operations")
  class TransformationOperationsTests {

    @Test
    @DisplayName("map() transforms value lazily")
    void mapTransformsValueLazily() {
      AtomicBoolean executed = new AtomicBoolean(false);

      VTaskContext<Integer> context =
          VTaskContext.of(
                  () -> {
                    executed.set(true);
                    return 10;
                  })
              .map(i -> i * 2);

      assertThat(executed).isFalse();
      assertThatVTaskContext(context).succeeds().hasValue(20);
      assertThat(executed).isTrue();
    }

    @Test
    @DisplayName("map() validates null mapper")
    void mapValidatesNullMapper() {
      VTaskContext<String> context = VTaskContext.pure(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> context.map(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("via() chains dependent computations")
    void viaChainsComputations() {
      AtomicInteger counter = new AtomicInteger(0);

      VTaskContext<Integer> context =
          VTaskContext.of(
                  () -> {
                    counter.incrementAndGet();
                    return 10;
                  })
              .via(
                  i ->
                      VTaskContext.of(
                          () -> {
                            counter.incrementAndGet();
                            return i * 2;
                          }));

      assertThat(counter.get()).isEqualTo(0);
      assertThatVTaskContext(context).succeeds().hasValue(20);
      assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("via() validates null function")
    void viaValidatesNullFunction() {
      VTaskContext<String> context = VTaskContext.pure(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> context.via(null))
          .withMessageContaining("fn must not be null");
    }

    @Test
    @DisplayName("flatMap() is alias for via")
    void flatMapIsAliasForVia() {
      VTaskContext<String> context = VTaskContext.pure("hello");

      Integer viaResult = context.via(s -> VTaskContext.pure(s.length())).runOrThrow();
      Integer flatMapResult = context.flatMap(s -> VTaskContext.pure(s.length())).runOrThrow();

      assertThat(viaResult).isEqualTo(flatMapResult);
    }

    @Test
    @DisplayName("then() sequences computations discarding value")
    void thenSequencesComputations() {
      AtomicBoolean firstExecuted = new AtomicBoolean(false);

      VTaskContext<Integer> context =
          VTaskContext.of(
                  () -> {
                    firstExecuted.set(true);
                    return "hello";
                  })
              .then(() -> VTaskContext.pure(42));

      assertThatVTaskContext(context).succeeds().hasValue(42);
      assertThat(firstExecuted).isTrue();
    }
  }

  @Nested
  @DisplayName("Error Recovery Operations")
  class ErrorRecoveryOperationsTests {

    @Test
    @DisplayName("recover() provides fallback for exceptions")
    void recoverProvidesFallbackForExceptions() {
      VTaskContext<String> context =
          VTaskContext.<String>of(
                  () -> {
                    throw new RuntimeException("error");
                  })
              .recover(ex -> "recovered: " + ex.getMessage());

      assertThatVTaskContext(context).succeeds().hasValue("recovered: error");
    }

    @Test
    @DisplayName("recover() preserves successful value")
    void recoverPreservesSuccessfulValue() {
      VTaskContext<String> context = VTaskContext.pure(TEST_VALUE).recover(ex -> "recovered");

      assertThatVTaskContext(context).succeeds().hasValue(TEST_VALUE);
    }

    @Test
    @DisplayName("recoverWith() provides fallback context for exceptions")
    void recoverWithProvidesFallbackContext() {
      VTaskContext<String> context =
          VTaskContext.<String>of(
                  () -> {
                    throw new RuntimeException("error");
                  })
              .recoverWith(ex -> VTaskContext.pure("fallback"));

      assertThatVTaskContext(context).succeeds().hasValue("fallback");
    }

    @Test
    @DisplayName("recoverWith() preserves successful value")
    void recoverWithPreservesSuccessfulValue() {
      VTaskContext<String> context =
          VTaskContext.pure(TEST_VALUE).recoverWith(ex -> VTaskContext.pure("fallback"));

      assertThatVTaskContext(context).succeeds().hasValue(TEST_VALUE);
    }

    @Test
    @DisplayName("orElse() provides alternative on failure")
    void orElseProvidesAlternativeOnFailure() {
      VTaskContext<String> context =
          VTaskContext.<String>of(
                  () -> {
                    throw new RuntimeException("error");
                  })
              .orElse(() -> VTaskContext.pure("alternative"));

      assertThatVTaskContext(context).succeeds().hasValue("alternative");
    }
  }

  @Nested
  @DisplayName("Timeout Operations")
  class TimeoutOperationsTests {

    @Test
    @DisplayName("timeout() allows fast operations to complete")
    void timeoutAllowsFastOperationsToComplete() {
      VTaskContext<Integer> context = VTaskContext.pure(TEST_INT).timeout(Duration.ofSeconds(1));

      assertThatVTaskContext(context)
          .completesWithin(Duration.ofSeconds(1))
          .succeeds()
          .hasValue(TEST_INT);
    }

    @Test
    @DisplayName("timeout() validates null duration")
    void timeoutValidatesNullDuration() {
      VTaskContext<String> context = VTaskContext.pure(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> context.timeout(null))
          .withMessageContaining("duration must not be null");
    }
  }

  @Nested
  @DisplayName("Execution Methods")
  class ExecutionMethodsTests {

    @Test
    @DisplayName("run() returns Try with success")
    void runReturnsTryWithSuccess() {
      VTaskContext<Integer> context = VTaskContext.pure(TEST_INT);

      assertThatVTaskContext(context).succeeds().hasValue(TEST_INT);
    }

    @Test
    @DisplayName("run() returns Try with failure")
    void runReturnsTryWithFailure() {
      VTaskContext<Integer> context =
          VTaskContext.of(
              () -> {
                throw new RuntimeException("test error");
              });

      assertThatVTaskContext(context).fails().withExceptionType(RuntimeException.class);
    }

    @Test
    @DisplayName("runAsync() returns CompletableFuture")
    void runAsyncReturnsCompletableFuture() throws Exception {
      VTaskContext<Integer> context = VTaskContext.pure(TEST_INT);

      CompletableFuture<Integer> future = context.runAsync();

      assertThat(future.get()).isEqualTo(TEST_INT);
    }

    @Test
    @DisplayName("runOrThrow() returns value on success")
    void runOrThrowReturnsValueOnSuccess() {
      VTaskContext<Integer> context = VTaskContext.pure(TEST_INT);

      assertThatVTaskContext(context).succeeds().hasValue(TEST_INT);
    }

    @Test
    @DisplayName("runOrThrow() throws on failure")
    void runOrThrowThrowsOnFailure() {
      VTaskContext<Integer> context =
          VTaskContext.of(
              () -> {
                throw new RuntimeException("test error");
              });

      assertThatVTaskContext(context)
          .fails()
          .withExceptionType(RuntimeException.class)
          .withExceptionMessage("test error");
    }

    @Test
    @DisplayName("runOrElse() returns value on success")
    void runOrElseReturnsValueOnSuccess() {
      VTaskContext<Integer> context = VTaskContext.pure(TEST_INT);

      assertThatVTaskContext(context).succeeds().hasValue(TEST_INT);
    }

    @Test
    @DisplayName("runOrElse() returns default on failure")
    void runOrElseReturnsDefaultOnFailure() {
      VTaskContext<Integer> context =
          VTaskContext.of(
              () -> {
                throw new RuntimeException("test error");
              });

      // VTaskContextAssert verifies failure, but we also test the runOrElse method directly
      assertThatVTaskContext(context).fails();
      assertThat(context.runOrElse(99)).isEqualTo(99);
    }

    @Test
    @DisplayName("runOrElseGet() returns value on success")
    void runOrElseGetReturnsValueOnSuccess() {
      VTaskContext<Integer> context = VTaskContext.pure(TEST_INT);

      assertThatVTaskContext(context).succeeds().hasValue(TEST_INT);
    }

    @Test
    @DisplayName("runOrElseGet() applies handler on failure")
    void runOrElseGetAppliesHandlerOnFailure() {
      VTaskContext<Integer> context =
          VTaskContext.of(
              () -> {
                throw new RuntimeException("test error");
              });

      Integer result = context.runOrElseGet(ex -> -1);

      assertThat(result).isEqualTo(-1);
    }

    @Test
    @DisplayName("runOrElseGet() returns value on success")
    void runOrElseGetReturnsValueOnSuccessDirectCall() {
      VTaskContext<Integer> context = VTaskContext.pure(TEST_INT);

      Integer result = context.runOrElseGet(ex -> -1);

      assertThat(result).isEqualTo(TEST_INT);
    }
  }

  @Nested
  @DisplayName("Conversion Methods")
  class ConversionMethodsTests {

    @Test
    @DisplayName("toPath() returns underlying VTaskPath")
    void toPathReturnsUnderlyingVTaskPath() {
      VTaskContext<Integer> context = VTaskContext.pure(TEST_INT);

      VTaskPath<Integer> path = context.toPath();

      assertThat(path.unsafeRun()).isEqualTo(TEST_INT);
    }

    @Test
    @DisplayName("toVTask() returns underlying VTask")
    void toVTaskReturnsUnderlyingVTask() {
      VTaskContext<Integer> context = VTaskContext.pure(TEST_INT);

      VTask<Integer> vtask = context.toVTask();

      assertThat(vtask.run()).isEqualTo(TEST_INT);
    }
  }

  @Nested
  @DisplayName("Complex Chaining Patterns")
  class ComplexChainingPatternsTests {

    @Test
    @DisplayName("Deferred computation pattern")
    void deferredComputationPattern() {
      AtomicInteger counter = new AtomicInteger(0);

      VTaskContext<Integer> computation =
          VTaskContext.of(counter::incrementAndGet)
              .map(i -> i * 2)
              .via(i -> VTaskContext.of(() -> i + counter.incrementAndGet()));

      // Nothing has executed yet
      assertThat(counter.get()).isEqualTo(0);

      // First run
      Integer result1 = computation.runOrThrow();
      assertThat(result1).isEqualTo(4); // (1 * 2) + 2
    }

    @Test
    @DisplayName("Effect sequencing pattern")
    void effectSequencingPattern() {
      StringBuilder log = new StringBuilder();

      VTaskContext<String> pipeline =
          VTaskContext.exec(() -> log.append("start>"))
              .then(
                  () ->
                      VTaskContext.of(
                          () -> {
                            log.append("process>");
                            return "data";
                          }))
              .map(
                  s -> {
                    log.append("transform>");
                    return s.toUpperCase();
                  });

      assertThat(log).isEmpty();

      String result = pipeline.runOrThrow();

      assertThat(result).isEqualTo("DATA");
      assertThat(log.toString()).isEqualTo("start>process>transform>");
    }

    @Test
    @DisplayName("Error recovery with retries")
    void errorRecoveryWithRetries() {
      AtomicInteger attempts = new AtomicInteger(0);

      VTaskContext<String> unreliable =
          VTaskContext.of(
              () -> {
                int attempt = attempts.incrementAndGet();
                if (attempt < 3) {
                  throw new RuntimeException("Attempt " + attempt + " failed");
                }
                return "success on attempt " + attempt;
              });

      VTaskContext<String> withRetry =
          unreliable
              .recoverWith(ex -> unreliable)
              .recoverWith(ex -> unreliable)
              .recover(ex -> "all retries failed: " + ex.getMessage());

      assertThatVTaskContext(withRetry).succeeds().hasValue("success on attempt 3");
      assertThat(attempts.get()).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("Object Methods")
  class ObjectMethodsTests {

    @Test
    @DisplayName("toString() provides meaningful representation")
    void toStringProvidesMeaningfulRepresentation() {
      VTaskContext<Integer> context = VTaskContext.pure(TEST_INT);

      assertThat(context.toString()).contains("VTaskContext");
      assertThat(context.toString()).contains("deferred");
    }
  }
}
