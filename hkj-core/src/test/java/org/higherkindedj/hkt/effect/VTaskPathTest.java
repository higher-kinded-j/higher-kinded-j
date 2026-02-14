// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.test.assertions.VTaskPathAssert.assertThatVTaskPath;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for VTaskPath.
 *
 * <p>Tests cover factory methods, Composable/Combinable/Chainable/Effectful operations, error
 * handling, timeout, and object methods.
 */
@DisplayName("VTaskPath<A> Complete Test Suite")
class VTaskPathTest {

  private static final String TEST_VALUE = "test";
  private static final Integer TEST_INT = 42;

  @Nested
  @DisplayName("Factory Methods via Path")
  class FactoryMethodsTests {

    @Test
    @DisplayName("Path.vtask() creates VTaskPath from callable")
    void pathVtaskCreatesVTaskPathFromCallable() {
      VTaskPath<Integer> path = Path.vtask(() -> 10 + 32);

      assertThatVTaskPath(path).succeeds().hasValue(42);
    }

    @Test
    @DisplayName("Path.vtask() is lazy - does not execute until run")
    void pathVtaskIsLazy() {
      AtomicBoolean executed = new AtomicBoolean(false);

      VTaskPath<Integer> path =
          Path.vtask(
              () -> {
                executed.set(true);
                return 42;
              });

      assertThat(executed).isFalse();
      path.unsafeRun();
      assertThat(executed).isTrue();
    }

    @Test
    @DisplayName("Path.vtaskPure() creates VTaskPath with pure value")
    void pathVtaskPureCreatesVTaskPathWithPureValue() {
      VTaskPath<Integer> path = Path.vtaskPure(TEST_INT);

      assertThatVTaskPath(path).succeeds().hasValue(TEST_INT);
    }

    @Test
    @DisplayName("Path.vtaskExec() creates VTaskPath from Runnable")
    void pathVtaskExecCreatesVTaskPathFromRunnable() {
      AtomicBoolean executed = new AtomicBoolean(false);

      VTaskPath<Unit> path = Path.vtaskExec(() -> executed.set(true));

      assertThat(executed).isFalse();
      path.unsafeRun();
      assertThat(executed).isTrue();
    }

    @Test
    @DisplayName("Path.vtaskFail() creates failed VTaskPath")
    void pathVtaskFailCreatesFailedVTaskPath() {
      RuntimeException error = new RuntimeException("test error");
      VTaskPath<String> path = Path.vtaskFail(error);

      assertThatVTaskPath(path)
          .fails()
          .withExceptionType(RuntimeException.class)
          .withExceptionMessage("test error");
    }

    @Test
    @DisplayName("Path.vtaskPath() creates VTaskPath from VTask")
    void pathVtaskPathCreatesVTaskPathFromVTask() {
      VTask<Integer> vtask = VTask.succeed(TEST_INT);

      VTaskPath<Integer> path = Path.vtaskPath(vtask);

      assertThatVTaskPath(path).succeeds().hasValue(TEST_INT);
    }

    @Test
    @DisplayName("Path.vtask() validates non-null callable")
    void pathVtaskValidatesNonNullCallable() {
      assertThatNullPointerException()
          .isThrownBy(() -> Path.vtask(null))
          .withMessageContaining("callable must not be null");
    }
  }

  @Nested
  @DisplayName("Run Methods")
  class RunMethodsTests {

    @Test
    @DisplayName("run() returns underlying VTask")
    void runReturnsUnderlyingVTask() {
      VTaskPath<Integer> path = Path.vtaskPure(TEST_INT);

      VTask<Integer> vtask = path.run();

      assertThat(vtask).isInstanceOf(VTask.class);
    }

    @Test
    @DisplayName("unsafeRun() executes and returns result")
    void unsafeRunExecutesAndReturnsResult() {
      VTaskPath<Integer> path = Path.vtask(() -> 10 + 32);

      assertThatVTaskPath(path).succeeds().hasValue(42);
    }

    @Test
    @DisplayName("unsafeRun() propagates runtime exceptions")
    void unsafeRunPropagatesRuntimeExceptions() {
      VTaskPath<Integer> path =
          Path.vtask(
              () -> {
                throw new RuntimeException("test error");
              });

      assertThatVTaskPath(path)
          .fails()
          .withExceptionType(RuntimeException.class)
          .withExceptionMessage("test error");
    }

    @Test
    @DisplayName("unsafeRun() wraps checked exceptions in RuntimeException")
    void unsafeRunWrapsCheckedExceptions() {
      VTaskPath<Integer> path =
          Path.vtask(
              () -> {
                throw new Exception("checked error");
              });

      assertThatVTaskPath(path).fails().withExceptionType(RuntimeException.class);
    }

    @Test
    @DisplayName("runSafe() returns Try with success")
    void runSafeReturnsTryWithSuccess() {
      VTaskPath<Integer> path = Path.vtaskPure(TEST_INT);

      assertThatVTaskPath(path).succeeds().hasValue(TEST_INT);
    }

    @Test
    @DisplayName("runSafe() returns Try with failure")
    void runSafeReturnsTryWithFailure() {
      VTaskPath<Integer> path =
          Path.vtask(
              () -> {
                throw new RuntimeException("test error");
              });

      assertThatVTaskPath(path).fails().withExceptionType(RuntimeException.class);
    }

    @Test
    @DisplayName("runAsync() returns CompletableFuture")
    void runAsyncReturnsCompletableFuture() throws Exception {
      VTaskPath<Integer> path = Path.vtaskPure(TEST_INT);

      CompletableFuture<Integer> future = path.runAsync();

      assertThat(future.get()).isEqualTo(TEST_INT);
    }

    @Test
    @DisplayName("runAsync() executes asynchronously")
    void runAsyncExecutesAsynchronously() throws Exception {
      AtomicBoolean executed = new AtomicBoolean(false);

      VTaskPath<Integer> path =
          Path.vtask(
              () -> {
                Thread.sleep(10);
                executed.set(true);
                return 42;
              });

      CompletableFuture<Integer> future = path.runAsync();

      // May or may not have executed yet
      Integer result = future.get();
      assertThat(result).isEqualTo(42);
      assertThat(executed).isTrue();
    }
  }

  @Nested
  @DisplayName("Composable Operations (map, peek)")
  class ComposableOperationsTests {

    @Test
    @DisplayName("map() transforms value lazily")
    void mapTransformsValueLazily() {
      AtomicBoolean executed = new AtomicBoolean(false);

      VTaskPath<Integer> path =
          Path.vtask(
                  () -> {
                    executed.set(true);
                    return 10;
                  })
              .map(i -> i * 2);

      assertThat(executed).isFalse();
      assertThat(path.unsafeRun()).isEqualTo(20);
      assertThat(executed).isTrue();
    }

    @Test
    @DisplayName("map() validates null mapper")
    void mapValidatesNullMapper() {
      VTaskPath<String> path = Path.vtaskPure(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.map(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("map() chains correctly")
    void mapChainsCorrectly() {
      VTaskPath<String> path = Path.vtaskPure("hello");

      VTaskPath<String> result =
          path.map(String::toUpperCase).map(s -> s + "!").map(s -> s.repeat(2));

      assertThatVTaskPath(result).succeeds().hasValue("HELLO!HELLO!");
    }

    @Test
    @DisplayName("peek() observes value without modifying")
    void peekObservesValueWithoutModifying() {
      AtomicBoolean called = new AtomicBoolean(false);

      VTaskPath<String> path = Path.vtaskPure(TEST_VALUE).peek(v -> called.set(true));

      assertThat(called).isFalse();
      assertThat(path.unsafeRun()).isEqualTo(TEST_VALUE);
      assertThat(called).isTrue();
    }

    @Test
    @DisplayName("peek() is also lazy")
    void peekIsAlsoLazy() {
      AtomicBoolean peeked = new AtomicBoolean(false);

      VTaskPath<String> path = Path.vtaskPure(TEST_VALUE).peek(v -> peeked.set(true));

      assertThat(peeked).isFalse();
      path.unsafeRun();
      assertThat(peeked).isTrue();
    }

    @Test
    @DisplayName("asUnit() discards result")
    void asUnitDiscardsResult() {
      AtomicBoolean executed = new AtomicBoolean(false);

      VTaskPath<Unit> path =
          Path.vtask(
                  () -> {
                    executed.set(true);
                    return TEST_INT;
                  })
              .asUnit();

      assertThat(executed).isFalse();
      Unit result = path.unsafeRun();
      assertThat(result).isEqualTo(Unit.INSTANCE);
      assertThat(executed).isTrue();
    }
  }

  @Nested
  @DisplayName("Chainable Operations (via, flatMap, then)")
  class ChainableOperationsTests {

    @Test
    @DisplayName("via() chains dependent computations lazily")
    void viaChainsComputationsLazily() {
      AtomicInteger counter = new AtomicInteger(0);

      VTaskPath<Integer> path =
          Path.vtask(
                  () -> {
                    counter.incrementAndGet();
                    return 10;
                  })
              .via(
                  i ->
                      Path.vtask(
                          () -> {
                            counter.incrementAndGet();
                            return i * 2;
                          }));

      assertThat(counter.get()).isEqualTo(0);
      assertThat(path.unsafeRun()).isEqualTo(20);
      assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("via() validates null mapper")
    void viaValidatesNullMapper() {
      VTaskPath<String> path = Path.vtaskPure(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.via(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("via() validates result is VTaskPath")
    void viaValidatesResultType() {
      VTaskPath<String> path = Path.vtaskPure(TEST_VALUE);

      VTaskPath<Integer> result = path.via(s -> Path.just(s.length()));

      assertThatIllegalArgumentException()
          .isThrownBy(result::unsafeRun)
          .withMessageContaining("via mapper must return VTaskPath");
    }

    @Test
    @DisplayName("flatMap() is alias for via")
    void flatMapIsAliasForVia() {
      VTaskPath<String> path = Path.vtaskPure("hello");

      Integer viaResult = path.via(s -> Path.vtaskPure(s.length())).unsafeRun();
      Integer flatMapResult = path.flatMap(s -> Path.vtaskPure(s.length())).unsafeRun();

      assertThat(viaResult).isEqualTo(flatMapResult);
    }

    @Test
    @DisplayName("then() sequences computations discarding value")
    void thenSequencesComputationsDiscardingValue() {
      AtomicBoolean firstExecuted = new AtomicBoolean(false);

      VTaskPath<Integer> path =
          Path.vtaskPure("hello").peek(v -> firstExecuted.set(true)).then(() -> Path.vtaskPure(42));

      assertThatVTaskPath(path).succeeds().hasValue(42);
      assertThat(firstExecuted).isTrue();
    }

    @Test
    @DisplayName("then() maintains sequencing order")
    void thenMaintainsSequencingOrder() {
      StringBuilder order = new StringBuilder();

      VTaskPath<String> path =
          Path.vtask(
                  () -> {
                    order.append("1");
                    return "first";
                  })
              .then(
                  () ->
                      Path.vtask(
                          () -> {
                            order.append("2");
                            return "second";
                          }))
              .then(
                  () ->
                      Path.vtask(
                          () -> {
                            order.append("3");
                            return "third";
                          }));

      assertThat(order).isEmpty();
      assertThat(path.unsafeRun()).isEqualTo("third");
      assertThat(order.toString()).isEqualTo("123");
    }
  }

  @Nested
  @DisplayName("Combinable Operations (zipWith)")
  class CombinableOperationsTests {

    @Test
    @DisplayName("zipWith() combines two VTaskPaths")
    void zipWithCombinesTwoVTaskPaths() {
      VTaskPath<String> first = Path.vtaskPure("hello");
      VTaskPath<Integer> second = Path.vtaskPure(3);

      VTaskPath<String> result = first.zipWith(second, (s, n) -> s.repeat(n));

      assertThatVTaskPath(result).succeeds().hasValue("hellohellohello");
    }

    @Test
    @DisplayName("zipWith() executes both VTasks")
    void zipWithExecutesBothVTasks() {
      AtomicBoolean firstExecuted = new AtomicBoolean(false);
      AtomicBoolean secondExecuted = new AtomicBoolean(false);

      VTaskPath<String> first =
          Path.vtask(
              () -> {
                firstExecuted.set(true);
                return "hello";
              });
      VTaskPath<Integer> second =
          Path.vtask(
              () -> {
                secondExecuted.set(true);
                return 3;
              });

      VTaskPath<String> result = first.zipWith(second, (s, n) -> s.repeat(n));

      assertThat(firstExecuted).isFalse();
      assertThat(secondExecuted).isFalse();

      result.unsafeRun();

      assertThat(firstExecuted).isTrue();
      assertThat(secondExecuted).isTrue();
    }

    @Test
    @DisplayName("zipWith() validates null parameters")
    void zipWithValidatesNullParameters() {
      VTaskPath<String> path = Path.vtaskPure(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(null, (a, b) -> a + b))
          .withMessageContaining("other must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(Path.vtaskPure("x"), null))
          .withMessageContaining("combiner must not be null");
    }

    @Test
    @DisplayName("zipWith3() combines three VTaskPaths")
    void zipWith3CombinesThreeVTaskPaths() {
      VTaskPath<String> first = Path.vtaskPure("hello");
      VTaskPath<String> second = Path.vtaskPure(" ");
      VTaskPath<String> third = Path.vtaskPure("world");

      VTaskPath<String> result = first.zipWith3(second, third, (a, b, c) -> a + b + c);

      assertThatVTaskPath(result).succeeds().hasValue("hello world");
    }

    @Test
    @DisplayName("zipWith() throws when given non-VTaskPath")
    void zipWithThrowsWhenGivenNonVTaskPath() {
      VTaskPath<String> path = Path.vtaskPure(TEST_VALUE);
      MaybePath<Integer> maybePath = Path.just(TEST_INT);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.zipWith(maybePath, (s, i) -> s + i))
          .withMessageContaining("Cannot zipWith non-VTaskPath");
    }
  }

  @Nested
  @DisplayName("Error Handling Operations")
  class ErrorHandlingOperationsTests {

    @Test
    @DisplayName("handleError() provides fallback for exceptions")
    void handleErrorProvidesFallbackForExceptions() {
      VTaskPath<String> path =
          Path.<String>vtask(
                  () -> {
                    throw new RuntimeException("error");
                  })
              .handleError(ex -> "recovered: " + ex.getMessage());

      assertThatVTaskPath(path).succeeds().hasValue("recovered: error");
    }

    @Test
    @DisplayName("handleError() preserves successful value")
    void handleErrorPreservesSuccessfulValue() {
      VTaskPath<String> path = Path.vtaskPure(TEST_VALUE).handleError(ex -> "recovered");

      assertThatVTaskPath(path).succeeds().hasValue(TEST_VALUE);
    }

    @Test
    @DisplayName("handleErrorWith() provides fallback VTaskPath for exceptions")
    void handleErrorWithProvidesFallbackVTaskPathForExceptions() {
      VTaskPath<String> path =
          Path.<String>vtask(
                  () -> {
                    throw new RuntimeException("error");
                  })
              .handleErrorWith(ex -> Path.vtaskPure("fallback"));

      assertThatVTaskPath(path).succeeds().hasValue("fallback");
    }

    @Test
    @DisplayName("handleErrorWith() preserves successful value")
    void handleErrorWithPreservesSuccessfulValue() {
      VTaskPath<String> path =
          Path.vtaskPure(TEST_VALUE).handleErrorWith(ex -> Path.vtaskPure("fallback"));

      assertThatVTaskPath(path).succeeds().hasValue(TEST_VALUE);
    }
  }

  @Nested
  @DisplayName("Timeout Operations")
  class TimeoutOperationsTests {

    @Test
    @DisplayName("timeout() allows fast operations to complete")
    void timeoutAllowsFastOperationsToComplete() {
      VTaskPath<Integer> path = Path.vtaskPure(TEST_INT).timeout(Duration.ofSeconds(1));

      assertThatVTaskPath(path)
          .completesWithin(Duration.ofSeconds(1))
          .succeeds()
          .hasValue(TEST_INT);
    }

    @Test
    @DisplayName("timeout() validates null duration")
    void timeoutValidatesNullDuration() {
      VTaskPath<String> path = Path.vtaskPure(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.timeout(null))
          .withMessageContaining("duration must not be null");
    }
  }

  @Nested
  @DisplayName("Utility Operations")
  class UtilityOperationsTests {

    @Test
    @DisplayName("toTryPath() converts to TryPath")
    void toTryPathConvertsToTryPath() {
      VTaskPath<Integer> successPath = Path.vtaskPure(TEST_INT);
      VTaskPath<Integer> failurePath =
          Path.vtask(
              () -> {
                throw new RuntimeException("error");
              });

      // Use VTaskPathAssert to verify the original paths
      assertThatVTaskPath(successPath).succeeds().hasValue(TEST_INT);
      assertThatVTaskPath(failurePath).fails().withExceptionType(RuntimeException.class);

      // Verify conversion to TryPath works
      TryPath<Integer> successResult = successPath.toTryPath();
      TryPath<Integer> failureResult = failurePath.toTryPath();

      assertThat(successResult.run().isSuccess()).isTrue();
      assertThat(successResult.getOrElse(null)).isEqualTo(TEST_INT);
      assertThat(failureResult.run().isFailure()).isTrue();
    }

    @Test
    @DisplayName("toIOPath() converts successful VTaskPath to IOPath")
    void toIOPathConvertsSuccessfulVTaskPathToIOPath() {
      VTaskPath<Integer> vtaskPath = Path.vtaskPure(TEST_INT);

      IOPath<Integer> ioPath = vtaskPath.toIOPath();

      assertThat(ioPath).isNotNull();
      assertThat(ioPath.unsafeRun()).isEqualTo(TEST_INT);
    }

    @Test
    @DisplayName("toIOPath() converts failed VTaskPath to IOPath with exception")
    void toIOPathConvertsFailedVTaskPathToIOPath() {
      RuntimeException error = new RuntimeException("vtask error");
      VTaskPath<Integer> vtaskPath = Path.vtaskFail(error);

      IOPath<Integer> ioPath = vtaskPath.toIOPath();

      assertThat(ioPath).isNotNull();
      assertThatThrownBy(ioPath::unsafeRun)
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("vtask error");
    }

    @Test
    @DisplayName("toIOPath() is lazy - does not execute VTask immediately")
    void toIOPathIsLazy() {
      AtomicBoolean executed = new AtomicBoolean(false);

      VTaskPath<Integer> vtaskPath =
          Path.vtask(
              () -> {
                executed.set(true);
                return TEST_INT;
              });

      IOPath<Integer> ioPath = vtaskPath.toIOPath();

      // VTask should not have executed yet
      assertThat(executed).isFalse();

      // Execute the IOPath
      Integer result = ioPath.unsafeRun();

      // Now it should have executed
      assertThat(executed).isTrue();
      assertThat(result).isEqualTo(TEST_INT);
    }

    @Test
    @DisplayName("toIOPath() can be composed with IOPath operations")
    void toIOPathCanBeComposedWithIOPathOperations() {
      VTaskPath<String> vtaskPath = Path.vtaskPure("hello");

      IOPath<String> result = vtaskPath.toIOPath().map(String::toUpperCase).map(s -> s + "!");

      assertThat(result.unsafeRun()).isEqualTo("HELLO!");
    }

    @Test
    @DisplayName("toIOPath() allows using IOPath-specific features like guarantee")
    void toIOPathAllowsUsingIOPathSpecificFeatures() {
      AtomicBoolean finalizerCalled = new AtomicBoolean(false);
      VTaskPath<String> vtaskPath = Path.vtaskPure("test");

      IOPath<String> withGuarantee =
          vtaskPath.toIOPath().guarantee(() -> finalizerCalled.set(true));

      assertThat(finalizerCalled).isFalse();
      String result = withGuarantee.unsafeRun();
      assertThat(result).isEqualTo("test");
      assertThat(finalizerCalled).isTrue();
    }

    @Test
    @DisplayName("toIOPath() allows using IOPath bracket pattern")
    void toIOPathAllowsUsingIOPathBracketPattern() {
      AtomicBoolean resourceReleased = new AtomicBoolean(false);

      VTaskPath<String> vtaskPath = Path.vtaskPure("resource-data");

      IOPath<String> withBracket =
          vtaskPath.toIOPath().peek(data -> {}).guarantee(() -> resourceReleased.set(true));

      String result = withBracket.unsafeRun();

      assertThat(result).isEqualTo("resource-data");
      assertThat(resourceReleased).isTrue();
    }

    @Test
    @DisplayName("toIOPath() propagates exceptions through IOPath error handling")
    void toIOPathPropagatesExceptionsThroughIOPathErrorHandling() {
      VTaskPath<String> vtaskPath =
          Path.vtask(
              () -> {
                throw new RuntimeException("original error");
              });

      IOPath<String> recovered =
          vtaskPath.toIOPath().handleError(ex -> "recovered: " + ex.getMessage());

      assertThat(recovered.unsafeRun()).isEqualTo("recovered: original error");
    }

    @Test
    @DisplayName("toIOPath() can be run multiple times (not memoized)")
    void toIOPathCanBeRunMultipleTimes() {
      AtomicInteger counter = new AtomicInteger(0);

      VTaskPath<Integer> vtaskPath = Path.vtask(counter::incrementAndGet);

      IOPath<Integer> ioPath = vtaskPath.toIOPath();

      // Run three times
      assertThat(ioPath.unsafeRun()).isEqualTo(1);
      assertThat(ioPath.unsafeRun()).isEqualTo(2);
      assertThat(ioPath.unsafeRun()).isEqualTo(3);
    }

    @Test
    @DisplayName("toIOPath() works with complex chained VTaskPath")
    void toIOPathWorksWithComplexChainedVTaskPath() {
      VTaskPath<String> complex =
          Path.vtaskPure("hello")
              .map(String::toUpperCase)
              .via(s -> Path.vtaskPure(s + " WORLD"))
              .map(s -> s + "!");

      IOPath<String> ioPath = complex.toIOPath();

      assertThat(ioPath.unsafeRun()).isEqualTo("HELLO WORLD!");
    }

    @Test
    @DisplayName("toIOPath() preserves runSafe behavior via asTry")
    void toIOPathPreservesRunSafeBehaviorViaTry() {
      VTaskPath<Integer> failingPath =
          Path.vtask(
              () -> {
                throw new RuntimeException("expected error");
              });

      IOPath<Integer> ioPath = failingPath.toIOPath();

      // Use asTry to safely capture the error
      var tryResult = ioPath.asTry().unsafeRun();

      assertThat(tryResult.isFailure()).isTrue();
      assertThat(((Try.Failure<Integer>) tryResult).cause()).isInstanceOf(RuntimeException.class);
    }
  }

  @Nested
  @DisplayName("Object Methods")
  class ObjectMethodsTests {

    @Test
    @DisplayName("equals() is reference-based for VTask")
    void equalsIsReferenceBasedForVTask() {
      VTaskPath<Integer> path1 = Path.vtaskPure(TEST_INT);
      VTaskPath<Integer> path2 = Path.vtaskPure(TEST_INT);

      // VTask equality is reference-based since it represents a computation
      assertThat(path1).isEqualTo(path1);
      assertThat(path1).isNotEqualTo(path2);
    }

    @Test
    @DisplayName("hashCode() is identity-based")
    void hashCodeIsIdentityBased() {
      VTaskPath<Integer> path = Path.vtaskPure(TEST_INT);

      assertThat(path.hashCode()).isEqualTo(System.identityHashCode(path));
    }

    @Test
    @DisplayName("toString() provides meaningful representation")
    void toStringProvidesMeaningfulRepresentation() {
      VTaskPath<Integer> path = Path.vtaskPure(TEST_INT);

      assertThat(path.toString()).contains("VTaskPath");
      assertThat(path.toString()).contains("deferred");
    }
  }

  @Nested
  @DisplayName("Complex Chaining Patterns")
  class ComplexChainingPatternsTests {

    @Test
    @DisplayName("Deferred computation pattern")
    void deferredComputationPattern() {
      AtomicInteger counter = new AtomicInteger(0);

      VTaskPath<Integer> computation =
          Path.vtask(counter::incrementAndGet)
              .map(i -> i * 2)
              .via(i -> Path.vtask(() -> i + counter.incrementAndGet()));

      // Nothing has executed yet
      assertThat(counter.get()).isEqualTo(0);

      // First run
      Integer result1 = computation.unsafeRun();
      assertThat(result1).isEqualTo(4); // (1 * 2) + 2

      // Second run - executes again (VTask is not memoized)
      Integer result2 = computation.unsafeRun();
      assertThat(result2).isEqualTo(10); // (3 * 2) + 4
    }

    @Test
    @DisplayName("Effect sequencing pattern")
    void effectSequencingPattern() {
      StringBuilder log = new StringBuilder();

      VTaskPath<String> pipeline =
          Path.vtaskExec(() -> log.append("start>"))
              .then(
                  () ->
                      Path.vtask(
                          () -> {
                            log.append("process>");
                            return "data";
                          }))
              .map(
                  s -> {
                    log.append("transform>");
                    return s.toUpperCase();
                  })
              .peek(s -> log.append("result:").append(s));

      assertThat(log).isEmpty();

      String result = pipeline.unsafeRun();

      assertThat(result).isEqualTo("DATA");
      assertThat(log.toString()).isEqualTo("start>process>transform>result:DATA");
    }

    @Test
    @DisplayName("Combining multiple VTask sources")
    void combiningMultipleVTaskSources() {
      VTaskPath<String> firstName = Path.vtaskPure("John");
      VTaskPath<String> lastName = Path.vtaskPure("Doe");
      VTaskPath<Integer> age = Path.vtaskPure(30);

      VTaskPath<String> fullRecord =
          firstName.zipWith3(
              lastName, age, (first, last, a) -> String.format("%s %s, age %d", first, last, a));

      assertThatVTaskPath(fullRecord).succeeds().hasValue("John Doe, age 30");
    }

    @Test
    @DisplayName("Error recovery with retries")
    void errorRecoveryWithRetries() {
      AtomicInteger attempts = new AtomicInteger(0);

      VTaskPath<String> unreliable =
          Path.vtask(
              () -> {
                int attempt = attempts.incrementAndGet();
                if (attempt < 3) {
                  throw new RuntimeException("Attempt " + attempt + " failed");
                }
                return "success on attempt " + attempt;
              });

      VTaskPath<String> withRetry =
          unreliable
              .handleErrorWith(ex -> unreliable)
              .handleErrorWith(ex -> unreliable)
              .handleError(ex -> "all retries failed: " + ex.getMessage());

      assertThatVTaskPath(withRetry).succeeds().hasValue("success on attempt 3");
      assertThat(attempts.get()).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("DefaultVTaskPath Edge Cases")
  class DefaultVTaskPathEdgeCasesTests {

    // Test data for focus operations
    record Person(String name, String email) {}

    static final Lens<Person, String> nameLens =
        Lens.of(Person::name, (p, n) -> new Person(n, p.email()));

    static final FocusPath<Person, String> nameFocus = FocusPath.of(nameLens);

    static final AffinePath<Person, String> emailAffine =
        AffinePath.of(
            Affine.of(p -> Optional.ofNullable(p.email()), (p, e) -> new Person(p.name(), e)));

    @Test
    @DisplayName("zipWith() throws IllegalArgumentException for non-VTaskPath")
    void zipWithThrowsForNonVTaskPath() {
      VTaskPath<String> path = Path.vtaskPure("hello");

      // Use IdPath which is a Chainable but not a VTaskPath
      Chainable<Integer> nonVTaskPath = Path.id(42);

      assertThatThrownBy(() -> path.zipWith(nonVTaskPath, (s, i) -> s + i))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Cannot zipWith non-VTaskPath");
    }

    @Test
    @DisplayName("via() throws IllegalArgumentException for non-VTaskPath result")
    void viaThrowsForNonVTaskPathResult() {
      VTaskPath<String> path = Path.vtaskPure("hello");

      // Create a mapper that returns a non-VTaskPath Chainable (IdPath)
      VTaskPath<Integer> result = path.via(s -> Path.id(42));

      assertThatThrownBy(result::unsafeRun)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("via mapper must return VTaskPath");
    }

    @Test
    @DisplayName("via() throws NullPointerException for null result")
    void viaThrowsForNullResult() {
      VTaskPath<String> path = Path.vtaskPure("hello");

      VTaskPath<Integer> result = path.via(s -> null);

      assertThatThrownBy(result::unsafeRun)
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("mapper must not return null");
    }

    @Test
    @DisplayName("then() throws IllegalArgumentException for non-VTaskPath result")
    void thenThrowsForNonVTaskPathResult() {
      VTaskPath<String> path = Path.vtaskPure("hello");

      // Create a supplier that returns a non-VTaskPath Chainable (IdPath)
      VTaskPath<Integer> result = path.then(() -> Path.id(42));

      assertThatThrownBy(result::unsafeRun)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("then supplier must return VTaskPath");
    }

    @Test
    @DisplayName("then() throws NullPointerException for null result")
    void thenThrowsForNullResult() {
      VTaskPath<String> path = Path.vtaskPure("hello");

      VTaskPath<Integer> result = path.then(() -> null);

      assertThatThrownBy(result::unsafeRun)
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("supplier must not return null");
    }

    @Test
    @DisplayName("handleErrorWith() throws NullPointerException for null recovery result")
    void handleErrorWithThrowsForNullRecoveryResult() {
      VTaskPath<String> path =
          Path.vtask(
              () -> {
                throw new RuntimeException("error");
              });

      VTaskPath<String> result = path.handleErrorWith(ex -> null);

      assertThatThrownBy(result::unsafeRun)
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("recovery must not return null");
    }

    @Test
    @DisplayName("focus() with FocusPath extracts nested value")
    void focusWithFocusPathExtractsNestedValue() {
      Person person = new Person("John", "john@example.com");
      VTaskPath<Person> path = Path.vtaskPure(person);

      VTaskPath<String> result = path.focus(nameFocus);

      assertThatVTaskPath(result).succeeds().hasValue("John");
    }

    @Test
    @DisplayName("focus() with AffinePath extracts present value")
    void focusWithAffinePathExtractsPresentValue() {
      Person person = new Person("John", "john@example.com");
      VTaskPath<Person> path = Path.vtaskPure(person);

      VTaskPath<String> result =
          path.focus(emailAffine, () -> new RuntimeException("Email not found"));

      assertThatVTaskPath(result).succeeds().hasValue("john@example.com");
    }

    @Test
    @DisplayName("focus() with AffinePath throws when value is absent")
    void focusWithAffinePathThrowsWhenAbsent() {
      Person person = new Person("John", null);
      VTaskPath<Person> path = Path.vtaskPure(person);

      VTaskPath<String> result =
          path.focus(emailAffine, () -> new IllegalStateException("Email not found"));

      assertThatVTaskPath(result)
          .fails()
          .withExceptionType(IllegalStateException.class)
          .withExceptionMessage("Email not found");
    }

    @Test
    @DisplayName("focus() validates null FocusPath")
    void focusValidatesNullFocusPath() {
      VTaskPath<Person> path = Path.vtaskPure(new Person("John", null));

      assertThatNullPointerException()
          .isThrownBy(() -> path.focus((FocusPath<Person, String>) null))
          .withMessageContaining("path must not be null");
    }

    @Test
    @DisplayName("focus() validates null AffinePath")
    void focusValidatesNullAffinePath() {
      VTaskPath<Person> path = Path.vtaskPure(new Person("John", null));

      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  path.focus((AffinePath<Person, String>) null, () -> new RuntimeException("err")))
          .withMessageContaining("path must not be null");
    }

    @Test
    @DisplayName("focus() validates null exception supplier")
    void focusValidatesNullExceptionSupplier() {
      VTaskPath<Person> path = Path.vtaskPure(new Person("John", null));

      assertThatNullPointerException()
          .isThrownBy(() -> path.focus(emailAffine, null))
          .withMessageContaining("exceptionIfAbsent must not be null");
    }

    @Test
    @DisplayName("unsafeRun() propagates Error directly")
    void unsafeRunPropagatesErrorDirectly() {
      VTaskPath<Integer> path =
          Path.vtask(
              () -> {
                throw new OutOfMemoryError("test error");
              });

      assertThatThrownBy(path::unsafeRun).isInstanceOf(OutOfMemoryError.class);
    }
  }
}
