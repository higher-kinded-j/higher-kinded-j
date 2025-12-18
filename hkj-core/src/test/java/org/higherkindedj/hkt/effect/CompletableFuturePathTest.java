// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for CompletableFuturePath.
 *
 * <p>Tests cover factory methods, async operations, error recovery, and conversions.
 */
@DisplayName("CompletableFuturePath<A> Complete Test Suite")
class CompletableFuturePathTest {

  private static final String TEST_VALUE = "test";
  private static final Integer TEST_INT = 42;

  @Nested
  @DisplayName("Factory Methods via Path")
  class FactoryMethodsTests {

    @Test
    @DisplayName("Path.futureCompleted() creates completed future")
    void futureCompletedCreatesCompletedFuture() {
      CompletableFuturePath<String> path = Path.futureCompleted(TEST_VALUE);

      assertThat(path.join()).isEqualTo(TEST_VALUE);
      assertThat(path.toCompletableFuture().isDone()).isTrue();
    }

    @Test
    @DisplayName("Path.futureFailed() creates failed future")
    void futureFailedCreatesFailedFuture() {
      Exception ex = new RuntimeException("test error");
      CompletableFuturePath<String> path = Path.futureFailed(ex);

      assertThat(path.toCompletableFuture().isCompletedExceptionally()).isTrue();
      assertThatThrownBy(path::join).isInstanceOf(CompletionException.class).hasCause(ex);
    }

    @Test
    @DisplayName("Path.future() wraps existing CompletableFuture")
    void futureWrapsExistingFuture() {
      CompletableFuture<String> cf = CompletableFuture.completedFuture(TEST_VALUE);
      CompletableFuturePath<String> path = Path.future(cf);

      assertThat(path.join()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("Path.futureAsync() creates path from async supplier")
    void pathFutureAsyncCreatesPath() {
      AtomicInteger counter = new AtomicInteger(0);
      CompletableFuturePath<Integer> path = Path.futureAsync(() -> counter.incrementAndGet());

      assertThat(path.join()).isEqualTo(1);
      assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Path.futureAsync() validates non-null supplier")
    void pathFutureAsyncValidatesNonNullSupplier() {
      assertThatNullPointerException()
          .isThrownBy(() -> Path.futureAsync(null))
          .withMessageContaining("supplier must not be null");
    }

    @Test
    @DisplayName("CompletableFuturePath.completed() creates completed path")
    void staticCompletedCreatesPath() {
      CompletableFuturePath<Integer> path = CompletableFuturePath.completed(TEST_INT);

      assertThat(path.join()).isEqualTo(TEST_INT);
    }

    @Test
    @DisplayName("CompletableFuturePath.failed() creates failed path")
    void staticFailedCreatesPath() {
      Exception ex = new IllegalStateException("failed");
      CompletableFuturePath<Integer> path = CompletableFuturePath.failed(ex);

      assertThatThrownBy(path::join).isInstanceOf(CompletionException.class).hasCause(ex);
    }

    @Test
    @DisplayName("supplyAsync() creates path from async supplier")
    void supplyAsyncCreatesPath() {
      AtomicInteger counter = new AtomicInteger(0);
      CompletableFuturePath<Integer> path =
          CompletableFuturePath.supplyAsync(() -> counter.incrementAndGet());

      assertThat(path.join()).isEqualTo(1);
    }

    @Test
    @DisplayName("supplyAsync() with executor runs on specified executor")
    void supplyAsyncWithExecutorRunsOnExecutor() {
      var executor = Executors.newSingleThreadExecutor();
      try {
        AtomicBoolean ranOnExecutor = new AtomicBoolean(false);
        CompletableFuturePath<String> path =
            CompletableFuturePath.supplyAsync(
                () -> {
                  ranOnExecutor.set(true);
                  return "done";
                },
                executor);

        assertThat(path.join()).isEqualTo("done");
        assertThat(ranOnExecutor).isTrue();
      } finally {
        executor.shutdown();
      }
    }
  }

  @Nested
  @DisplayName("Run and Terminal Methods")
  class RunAndTerminalMethodsTests {

    @Test
    @DisplayName("run() returns underlying CompletableFuture")
    void runReturnsUnderlyingFuture() {
      CompletableFuturePath<String> path = CompletableFuturePath.completed(TEST_VALUE);

      assertThat(path.run()).isInstanceOf(CompletableFuture.class);
      assertThat(path.run().join()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("toCompletableFuture() is alias for run()")
    void toCompletableFutureIsAliasForRun() {
      CompletableFuturePath<String> path = CompletableFuturePath.completed(TEST_VALUE);

      assertThat(path.toCompletableFuture()).isSameAs(path.run());
    }

    @Test
    @DisplayName("join() blocks and returns result")
    void joinBlocksAndReturnsResult() {
      CompletableFuturePath<String> path = CompletableFuturePath.completed(TEST_VALUE);

      assertThat(path.join()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("join() throws CompletionException for failed future")
    void joinThrowsForFailedFuture() {
      Exception ex = new RuntimeException("test");
      CompletableFuturePath<String> path = CompletableFuturePath.failed(ex);

      assertThatThrownBy(path::join).isInstanceOf(CompletionException.class).hasCause(ex);
    }

    @Test
    @DisplayName("join(Duration) returns result within timeout")
    void joinWithDurationReturnsResultWithinTimeout() throws TimeoutException {
      CompletableFuturePath<String> path = CompletableFuturePath.completed(TEST_VALUE);

      String result = path.join(Duration.ofSeconds(1));

      assertThat(result).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("join(Duration) throws TimeoutException when exceeded")
    void joinWithDurationThrowsTimeoutException() {
      CompletableFuture<String> neverCompletes = new CompletableFuture<>();
      CompletableFuturePath<String> path = CompletableFuturePath.fromFuture(neverCompletes);

      assertThatThrownBy(() -> path.join(Duration.ofMillis(50)))
          .isInstanceOf(TimeoutException.class);
    }

    @Test
    @DisplayName("join(Duration) throws CompletionException for failed future")
    void joinWithDurationThrowsCompletionException() {
      Exception ex = new RuntimeException("error");
      CompletableFuturePath<String> path = CompletableFuturePath.failed(ex);

      assertThatThrownBy(() -> path.join(Duration.ofSeconds(1)))
          .isInstanceOf(CompletionException.class)
          .hasCause(ex);
    }

    @Test
    @DisplayName("isDone() returns true for completed future")
    void isDoneReturnsTrueForCompleted() {
      CompletableFuturePath<String> path = CompletableFuturePath.completed(TEST_VALUE);

      assertThat(path.isDone()).isTrue();
    }

    @Test
    @DisplayName("isDone() returns false for pending future")
    void isDoneReturnsFalseForPending() {
      CompletableFuture<String> pending = new CompletableFuture<>();
      CompletableFuturePath<String> path = CompletableFuturePath.fromFuture(pending);

      assertThat(path.isDone()).isFalse();
    }

    @Test
    @DisplayName("isCompletedExceptionally() returns true for failed future")
    void isCompletedExceptionallyReturnsTrue() {
      CompletableFuturePath<String> path =
          CompletableFuturePath.failed(new RuntimeException("error"));

      assertThat(path.isCompletedExceptionally()).isTrue();
    }

    @Test
    @DisplayName("isCompletedExceptionally() returns false for successful future")
    void isCompletedExceptionallyReturnsFalse() {
      CompletableFuturePath<String> path = CompletableFuturePath.completed(TEST_VALUE);

      assertThat(path.isCompletedExceptionally()).isFalse();
    }
  }

  @Nested
  @DisplayName("Composable Operations (map, peek)")
  class ComposableOperationsTests {

    @Test
    @DisplayName("map() transforms value asynchronously")
    void mapTransformsValue() {
      CompletableFuturePath<String> path = CompletableFuturePath.completed("hello");

      CompletableFuturePath<Integer> result = path.map(String::length);

      assertThat(result.join()).isEqualTo(5);
    }

    @Test
    @DisplayName("map() validates null mapper")
    void mapValidatesNullMapper() {
      CompletableFuturePath<String> path = CompletableFuturePath.completed(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.map(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("map() propagates failure")
    void mapPropagatesFailure() {
      Exception ex = new RuntimeException("original");
      CompletableFuturePath<String> path = CompletableFuturePath.failed(ex);

      CompletableFuturePath<Integer> result = path.map(String::length);

      assertThatThrownBy(result::join).isInstanceOf(CompletionException.class).hasCause(ex);
    }

    @Test
    @DisplayName("map() chains correctly")
    void mapChainsCorrectly() {
      CompletableFuturePath<String> path = CompletableFuturePath.completed("hello");

      CompletableFuturePath<String> result =
          path.map(String::toUpperCase).map(s -> s + "!").map(s -> "[" + s + "]");

      assertThat(result.join()).isEqualTo("[HELLO!]");
    }

    @Test
    @DisplayName("peek() observes value without modifying")
    void peekObservesValueWithoutModifying() {
      CompletableFuturePath<String> path = CompletableFuturePath.completed(TEST_VALUE);
      AtomicBoolean called = new AtomicBoolean(false);

      CompletableFuturePath<String> result = path.peek(v -> called.set(true));

      assertThat(result.join()).isEqualTo(TEST_VALUE);
      assertThat(called).isTrue();
    }
  }

  @Nested
  @DisplayName("Chainable Operations (via, flatMap, then)")
  class ChainableOperationsTests {

    @Test
    @DisplayName("via() chains dependent computations")
    void viaChainsComputations() {
      CompletableFuturePath<String> path = CompletableFuturePath.completed("hello");

      CompletableFuturePath<Integer> result =
          path.via(s -> CompletableFuturePath.completed(s.length()));

      assertThat(result.join()).isEqualTo(5);
    }

    @Test
    @DisplayName("via() validates null mapper")
    void viaValidatesNullMapper() {
      CompletableFuturePath<String> path = CompletableFuturePath.completed(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.via(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("via() propagates failure from first path")
    void viaPropagatesFailureFromFirst() {
      Exception ex = new RuntimeException("first failed");
      CompletableFuturePath<String> path = CompletableFuturePath.failed(ex);

      CompletableFuturePath<Integer> result =
          path.via(s -> CompletableFuturePath.completed(s.length()));

      assertThatThrownBy(result::join).isInstanceOf(CompletionException.class).hasCause(ex);
    }

    @Test
    @DisplayName("flatMap() is alias for via")
    void flatMapIsAliasForVia() {
      CompletableFuturePath<String> path = CompletableFuturePath.completed("hello");

      CompletableFuturePath<Integer> viaResult =
          path.via(s -> CompletableFuturePath.completed(s.length()));
      @SuppressWarnings("unchecked")
      CompletableFuturePath<Integer> flatMapResult =
          (CompletableFuturePath<Integer>)
              path.flatMap(s -> CompletableFuturePath.completed(s.length()));

      assertThat(flatMapResult.join()).isEqualTo(viaResult.join());
    }

    @Test
    @DisplayName("then() sequences computations discarding value")
    void thenSequencesComputationsDiscardingValue() {
      CompletableFuturePath<String> path = CompletableFuturePath.completed("ignored");

      CompletableFuturePath<Integer> result = path.then(() -> CompletableFuturePath.completed(42));

      assertThat(result.join()).isEqualTo(42);
    }

    @Test
    @DisplayName("via() throws for incompatible path type")
    void viaThrowsForIncompatibleType() {
      CompletableFuturePath<String> path = CompletableFuturePath.completed("hello");

      CompletableFuturePath<Integer> result = path.via(s -> Path.id(s.length()));

      assertThatThrownBy(result::join)
          .isInstanceOf(CompletionException.class)
          .hasCauseInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("via mapper must return CompletableFuturePath");
    }

    @Test
    @DisplayName("then() throws for incompatible path type")
    void thenThrowsForIncompatibleType() {
      CompletableFuturePath<String> path = CompletableFuturePath.completed("hello");

      CompletableFuturePath<Integer> result = path.then(() -> Path.id(42));

      assertThatThrownBy(result::join)
          .isInstanceOf(CompletionException.class)
          .hasCauseInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("then supplier must return CompletableFuturePath");
    }
  }

  @Nested
  @DisplayName("Combinable Operations (zipWith)")
  class CombinableOperationsTests {

    @Test
    @DisplayName("zipWith() combines two values")
    void zipWithCombinesTwoValues() {
      CompletableFuturePath<String> first = CompletableFuturePath.completed("hello");
      CompletableFuturePath<Integer> second = CompletableFuturePath.completed(3);

      CompletableFuturePath<String> result = first.zipWith(second, (s, n) -> s.repeat(n));

      assertThat(result.join()).isEqualTo("hellohellohello");
    }

    @Test
    @DisplayName("zipWith() validates null parameters")
    void zipWithValidatesNullParameters() {
      CompletableFuturePath<String> path = CompletableFuturePath.completed(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(null, (a, b) -> a + b))
          .withMessageContaining("other must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(CompletableFuturePath.completed("x"), null))
          .withMessageContaining("combiner must not be null");
    }

    @Test
    @DisplayName("zipWith3() combines three values")
    void zipWith3CombinesThreeValues() {
      CompletableFuturePath<String> first = CompletableFuturePath.completed("hello");
      CompletableFuturePath<String> second = CompletableFuturePath.completed(" ");
      CompletableFuturePath<String> third = CompletableFuturePath.completed("world");

      CompletableFuturePath<String> result = first.zipWith3(second, third, (a, b, c) -> a + b + c);

      assertThat(result.join()).isEqualTo("hello world");
    }

    @Test
    @DisplayName("zipWith() throws for incompatible path type")
    void zipWithThrowsForIncompatibleType() {
      CompletableFuturePath<String> path = CompletableFuturePath.completed("hello");
      IdPath<Integer> idPath = Path.id(42);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.zipWith(idPath, (s, n) -> s + n))
          .withMessageContaining("Cannot zipWith non-CompletableFuturePath");
    }
  }

  @Nested
  @DisplayName("Recoverable Operations")
  class RecoverableOperationsTests {

    @Test
    @DisplayName("recover() provides fallback value on failure")
    void recoverProvidesFallback() {
      CompletableFuturePath<String> path =
          CompletableFuturePath.failed(new RuntimeException("error"));

      CompletableFuturePath<String> result = path.recover(ex -> "fallback");

      assertThat(result.join()).isEqualTo("fallback");
    }

    @Test
    @DisplayName("recover() returns original value on success")
    void recoverReturnsOriginalOnSuccess() {
      CompletableFuturePath<String> path = CompletableFuturePath.completed(TEST_VALUE);

      CompletableFuturePath<String> result = path.recover(ex -> "fallback");

      assertThat(result.join()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("recoverWith() provides fallback path on failure")
    void recoverWithProvidesFallbackPath() {
      CompletableFuturePath<String> path =
          CompletableFuturePath.failed(new RuntimeException("error"));

      CompletableFuturePath<String> result =
          path.recoverWith(ex -> CompletableFuturePath.completed("recovered"));

      assertThat(result.join()).isEqualTo("recovered");
    }

    @Test
    @DisplayName("orElse() provides alternative on failure")
    void orElserovidesAlternative() {
      CompletableFuturePath<String> path =
          CompletableFuturePath.failed(new RuntimeException("error"));

      CompletableFuturePath<String> result =
          path.orElse(() -> CompletableFuturePath.completed("alternative"));

      assertThat(result.join()).isEqualTo("alternative");
    }

    @Test
    @DisplayName("mapError() returns same instance (limitation of type system)")
    void mapErrorReturnsSameInstance() {
      CompletableFuturePath<String> path =
          CompletableFuturePath.failed(new RuntimeException("error"));

      var result = path.mapError(ex -> "mapped: " + ex.getMessage());

      // mapError returns the same instance for CompletableFuturePath
      assertThat(result).isSameAs(path);
    }
  }

  @Nested
  @DisplayName("Async-Specific Operations")
  class AsyncSpecificOperationsTests {

    @Test
    @DisplayName("withTimeout() adds timeout to computation")
    void withTimeoutAddsTimeout() {
      CompletableFuturePath<String> path = CompletableFuturePath.completed(TEST_VALUE);

      CompletableFuturePath<String> result = path.withTimeout(Duration.ofSeconds(1));

      assertThat(result.join()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("completeOnTimeout() provides default value on timeout")
    void completeOnTimeoutProvidesDefault() {
      CompletableFuturePath<String> path = CompletableFuturePath.completed(TEST_VALUE);

      CompletableFuturePath<String> result =
          path.completeOnTimeout("default", Duration.ofSeconds(1));

      assertThat(result.join()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("onExecutor() switches executor for subsequent operations")
    void onExecutorSwitchesExecutor() {
      var executor = Executors.newSingleThreadExecutor();
      try {
        CompletableFuturePath<String> path = CompletableFuturePath.completed(TEST_VALUE);

        CompletableFuturePath<String> result = path.onExecutor(executor);

        assertThat(result.join()).isEqualTo(TEST_VALUE);
      } finally {
        executor.shutdown();
      }
    }
  }

  @Nested
  @DisplayName("Conversion Methods")
  class ConversionMethodsTests {

    @Test
    @DisplayName("toIOPath() converts to IOPath (blocking)")
    void toIOPathConvertsCorrectly() {
      CompletableFuturePath<String> path = CompletableFuturePath.completed(TEST_VALUE);

      IOPath<String> result = path.toIOPath();

      assertThat(result.unsafeRun()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("toTryPath() converts success to Success")
    void toTryPathConvertsSuccessCorrectly() {
      CompletableFuturePath<String> path = CompletableFuturePath.completed(TEST_VALUE);

      TryPath<String> result = path.toTryPath();

      assertThat(result.run().isSuccess()).isTrue();
      assertThat(result.run().orElse(null)).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("toTryPath() converts failure to Failure")
    void toTryPathConvertsFailureCorrectly() {
      CompletableFuturePath<String> path =
          CompletableFuturePath.failed(new RuntimeException("error"));

      TryPath<String> result = path.toTryPath();

      assertThat(result.run().isFailure()).isTrue();
    }

    @Test
    @DisplayName("toEitherPath() converts success to Right")
    void toEitherPathConvertsSuccessToRight() {
      CompletableFuturePath<String> path = CompletableFuturePath.completed(TEST_VALUE);

      EitherPath<Exception, String> result = path.toEitherPath();

      assertThat(result.run().isRight()).isTrue();
      assertThat(result.run().getRight()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("toEitherPath() converts failure to Left")
    void toEitherPathConvertsFailureToLeft() {
      RuntimeException ex = new RuntimeException("error");
      CompletableFuturePath<String> path = CompletableFuturePath.failed(ex);

      EitherPath<Exception, String> result = path.toEitherPath();

      assertThat(result.run().isLeft()).isTrue();
    }

    @Test
    @DisplayName("toMaybePath() converts success to Just")
    void toMaybePathConvertsSuccessToJust() {
      CompletableFuturePath<String> path = CompletableFuturePath.completed(TEST_VALUE);

      MaybePath<String> result = path.toMaybePath();

      assertThat(result.run().isJust()).isTrue();
      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("toMaybePath() converts failure to Nothing")
    void toMaybePathConvertsFailureToNothing() {
      CompletableFuturePath<String> path =
          CompletableFuturePath.failed(new RuntimeException("error"));

      MaybePath<String> result = path.toMaybePath();

      assertThat(result.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("toMaybePath() converts null value to Nothing")
    void toMaybePathConvertsNullToNothing() {
      CompletableFuturePath<String> path = CompletableFuturePath.completed(null);

      MaybePath<String> result = path.toMaybePath();

      assertThat(result.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("toEitherPath() wraps Error in RuntimeException for Left")
    void toEitherPathWrapsErrorInRuntimeException() {
      CompletableFuture<String> future = new CompletableFuture<>();
      future.completeExceptionally(new AssertionError("test error"));
      CompletableFuturePath<String> path = CompletableFuturePath.fromFuture(future);

      EitherPath<Exception, String> result = path.toEitherPath();

      assertThat(result.run().isLeft()).isTrue();
      assertThat(result.run().getLeft()).isInstanceOf(RuntimeException.class);
    }
  }

  @Nested
  @DisplayName("Object Methods")
  class ObjectMethodsTests {

    @Test
    @DisplayName("toString() shows completion state")
    void toStringShowsCompletionState() {
      CompletableFuturePath<String> completed = CompletableFuturePath.completed(TEST_VALUE);
      CompletableFuturePath<String> failed =
          CompletableFuturePath.failed(new RuntimeException("error"));

      assertThat(completed.toString()).contains("CompletableFuturePath").contains(TEST_VALUE);
      assertThat(failed.toString()).contains("CompletableFuturePath").contains("failed");
    }

    @Test
    @DisplayName("toString() shows pending for incomplete future")
    void toStringShowsPending() {
      CompletableFuture<String> pending = new CompletableFuture<>();
      CompletableFuturePath<String> path = CompletableFuturePath.fromFuture(pending);

      assertThat(path.toString()).contains("pending");
    }

    @Test
    @DisplayName("equals() returns true for same instance")
    void equalsReturnsTrueForSameInstance() {
      CompletableFuturePath<String> path = CompletableFuturePath.completed(TEST_VALUE);

      assertThat(path.equals(path)).isTrue();
    }

    @Test
    @DisplayName("equals() returns false for different instances")
    void equalsReturnsFalseForDifferentInstances() {
      CompletableFuturePath<String> path1 = CompletableFuturePath.completed(TEST_VALUE);
      CompletableFuturePath<String> path2 = CompletableFuturePath.completed(TEST_VALUE);

      // Different CompletableFutures so not equal
      assertThat(path1.equals(path2)).isFalse();
    }

    @Test
    @DisplayName("equals() returns false for non-CompletableFuturePath")
    void equalsReturnsFalseForOtherTypes() {
      CompletableFuturePath<String> path = CompletableFuturePath.completed(TEST_VALUE);

      assertThat(path.equals("not a path")).isFalse();
      assertThat(path.equals(null)).isFalse();
    }

    @Test
    @DisplayName("hashCode() is consistent")
    void hashCodeIsConsistent() {
      CompletableFuturePath<String> path = CompletableFuturePath.completed(TEST_VALUE);

      assertThat(path.hashCode()).isEqualTo(path.hashCode());
    }
  }

  @Nested
  @DisplayName("Edge Cases Coverage")
  class EdgeCasesCoverageTests {

    @Test
    @DisplayName("recoverWith() throws when recovery returns non-CompletableFuturePath")
    void recoverWithThrowsForNonCompletableFuturePath() {
      CompletableFuturePath<String> failedPath =
          CompletableFuturePath.failed(new RuntimeException("test error"));

      // Return an EitherPath (which is a Recoverable) instead of CompletableFuturePath
      // EitherPath<Exception, String> implements Recoverable<Exception, String>
      assertThatThrownBy(() -> failedPath.recoverWith(ex -> Path.right("recovered")).join())
          .isInstanceOf(CompletionException.class)
          .hasCauseInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("recoverWith must return CompletableFuturePath");
    }

    @Test
    @DisplayName("unwrapException handles non-CompletionException directly")
    void unwrapExceptionHandlesNonCompletionException() {
      // Create a failed future directly with a RuntimeException (not wrapped in
      // CompletionException)
      CompletableFuture<String> future = new CompletableFuture<>();
      RuntimeException directException = new RuntimeException("direct error");
      future.completeExceptionally(directException);
      CompletableFuturePath<String> path = CompletableFuturePath.fromFuture(future);

      // Recover should receive the original exception
      String result = path.recover(ex -> "recovered: " + ex.getMessage()).join();

      assertThat(result).isEqualTo("recovered: direct error");
    }

    @Test
    @DisplayName("unwrapException unwraps CompletionException to get cause")
    void unwrapExceptionUnwrapsCompletionException() {
      // When an exception occurs in map/flatMap, it gets wrapped in CompletionException
      CompletableFuturePath<String> path =
          CompletableFuturePath.completed("hello")
              .map(
                  s -> {
                    throw new IllegalStateException("map error");
                  });

      // Recover should receive the unwrapped IllegalStateException, not CompletionException
      String result =
          path.recover(
                  ex -> {
                    assertThat(ex).isInstanceOf(IllegalStateException.class);
                    assertThat(ex.getMessage()).isEqualTo("map error");
                    return "recovered";
                  })
              .join();

      assertThat(result).isEqualTo("recovered");
    }

    @Test
    @DisplayName("unwrapException wraps Error in RuntimeException when Error is direct")
    void unwrapExceptionWrapsDirectError() {
      // Create a failed future with an Error directly (not wrapped in CompletionException)
      CompletableFuture<String> future = new CompletableFuture<>();
      AssertionError error = new AssertionError("direct error");
      future.completeExceptionally(error);
      CompletableFuturePath<String> path = CompletableFuturePath.fromFuture(future);

      // Recover should receive the Error wrapped in RuntimeException
      String result =
          path.recover(
                  ex -> {
                    assertThat(ex).isInstanceOf(RuntimeException.class);
                    assertThat(ex.getCause()).isSameAs(error);
                    return "recovered: " + ex.getCause().getMessage();
                  })
              .join();

      assertThat(result).isEqualTo("recovered: direct error");
    }

    @Test
    @DisplayName(
        "unwrapException wraps Error in RuntimeException when Error is in CompletionException")
    void unwrapExceptionWrapsErrorFromCompletionException() {
      // When an Error is thrown inside map(), it gets wrapped in CompletionException
      CompletableFuturePath<String> path =
          CompletableFuturePath.completed("hello")
              .map(
                  s -> {
                    throw new AssertionError("error in map");
                  });

      // Recover should receive the Error wrapped in RuntimeException
      String result =
          path.recover(
                  ex -> {
                    assertThat(ex).isInstanceOf(RuntimeException.class);
                    assertThat(ex.getCause()).isInstanceOf(AssertionError.class);
                    assertThat(ex.getCause().getMessage()).isEqualTo("error in map");
                    return "recovered";
                  })
              .join();

      assertThat(result).isEqualTo("recovered");
    }

    @Test
    @DisplayName("join with timeout handles ExecutionException")
    void joinWithTimeoutHandlesExecutionException() {
      CompletableFuturePath<String> failedPath =
          CompletableFuturePath.failed(new RuntimeException("test error"));

      assertThatThrownBy(() -> failedPath.join(Duration.ofSeconds(1)))
          .isInstanceOf(CompletionException.class)
          .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("join with timeout handles InterruptedException")
    void joinWithTimeoutHandlesInterruptedException() throws Exception {
      // Create a future that will take a long time to complete
      CompletableFuture<String> slowFuture = new CompletableFuture<>();
      CompletableFuturePath<String> path = CompletableFuturePath.fromFuture(slowFuture);

      // Interrupt the current thread while waiting
      Thread testThread = Thread.currentThread();
      Thread interrupter =
          new Thread(
              () -> {
                try {
                  Thread.sleep(50); // Give the join time to start waiting
                  testThread.interrupt();
                } catch (InterruptedException e) {
                  // Ignore
                }
              });

      interrupter.start();

      assertThatThrownBy(() -> path.join(Duration.ofSeconds(5)))
          .isInstanceOf(CompletionException.class)
          .hasCauseInstanceOf(InterruptedException.class);

      interrupter.join();
      // Clear the interrupted flag
      Thread.interrupted();
    }
  }
}
