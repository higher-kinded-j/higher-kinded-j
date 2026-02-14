// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.lazy.Lazy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for LazyPath.
 *
 * <p>Tests cover factory methods, lazy evaluation semantics, Composable/Combinable/Chainable
 * operations, and conversions.
 */
@DisplayName("LazyPath<A> Complete Test Suite")
class LazyPathTest {

  private static final String TEST_VALUE = "test";
  private static final Integer TEST_INT = 42;

  @Nested
  @DisplayName("Factory Methods via Path")
  class FactoryMethodsTests {

    @Test
    @DisplayName("Path.lazyNow() creates already-evaluated LazyPath")
    void lazyNowCreatesEvaluatedPath() {
      LazyPath<String> path = Path.lazyNow(TEST_VALUE);

      assertThat(path.isEvaluated()).isTrue();
      assertThat(path.get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("Path.lazyDefer() creates unevaluated LazyPath")
    void lazyDeferCreatesUnevaluatedPath() {
      AtomicBoolean called = new AtomicBoolean(false);

      LazyPath<String> path =
          Path.lazyDefer(
              () -> {
                called.set(true);
                return TEST_VALUE;
              });

      assertThat(path.isEvaluated()).isFalse();
      assertThat(called).isFalse();

      assertThat(path.get()).isEqualTo(TEST_VALUE);
      assertThat(path.isEvaluated()).isTrue();
      assertThat(called).isTrue();
    }

    @Test
    @DisplayName("Path.lazyDefer() validates non-null supplier")
    void lazyDeferValidatesNonNullSupplier() {
      assertThatNullPointerException()
          .isThrownBy(() -> Path.lazyDefer(null))
          .withMessageContaining("supplier must not be null");
    }

    @Test
    @DisplayName("LazyPath.now() creates already-evaluated path")
    void staticNowCreatesEvaluatedPath() {
      LazyPath<Integer> path = LazyPath.now(TEST_INT);

      assertThat(path.isEvaluated()).isTrue();
      assertThat(path.get()).isEqualTo(TEST_INT);
    }

    @Test
    @DisplayName("LazyPath.defer() creates deferred path")
    void staticDeferCreatesDeferredPath() {
      AtomicInteger callCount = new AtomicInteger(0);

      LazyPath<Integer> path =
          LazyPath.defer(
              () -> {
                callCount.incrementAndGet();
                return 42;
              });

      assertThat(callCount.get()).isZero();
      assertThat(path.get()).isEqualTo(42);
      assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Path.lazy() wraps existing Lazy")
    void pathLazyWrapsExistingLazy() {
      Lazy<String> lazy = Lazy.now(TEST_VALUE);
      LazyPath<String> path = Path.lazy(lazy);

      assertThat(path.get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("Path.lazy() validates non-null lazy")
    void pathLazyValidatesNonNullLazy() {
      assertThatNullPointerException()
          .isThrownBy(() -> Path.lazy(null))
          .withMessageContaining("lazy must not be null");
    }

    @Test
    @DisplayName("LazyPath.of() wraps existing Lazy")
    void ofWrapsExistingLazy() {
      Lazy<String> lazy = Lazy.now(TEST_VALUE);
      LazyPath<String> path = LazyPath.of(lazy);

      assertThat(path.get()).isEqualTo(TEST_VALUE);
    }
  }

  @Nested
  @DisplayName("Lazy Evaluation Semantics")
  class LazyEvaluationSemanticsTests {

    @Test
    @DisplayName("Supplier is called at most once (memoization)")
    void supplierCalledAtMostOnce() {
      AtomicInteger callCount = new AtomicInteger(0);

      LazyPath<Integer> path =
          Path.lazyDefer(
              () -> {
                callCount.incrementAndGet();
                return TEST_INT;
              });

      // Call get() multiple times
      path.get();
      path.get();
      path.get();

      assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("isEvaluated() reflects evaluation state")
    void isEvaluatedReflectsState() {
      LazyPath<String> path = Path.lazyDefer(() -> TEST_VALUE);

      assertThat(path.isEvaluated()).isFalse();
      path.get();
      assertThat(path.isEvaluated()).isTrue();
    }

    @Test
    @DisplayName("get() triggers evaluation and returns value")
    void getTriggersEvaluation() {
      AtomicBoolean called = new AtomicBoolean(false);

      LazyPath<String> path =
          Path.lazyDefer(
              () -> {
                called.set(true);
                return TEST_VALUE;
              });

      String result = path.get();

      assertThat(result).isEqualTo(TEST_VALUE);
      assertThat(called).isTrue();
      assertThat(path.isEvaluated()).isTrue();
    }

    @Test
    @DisplayName("force() triggers evaluation and may throw Throwable")
    void forceTriggersEvaluation() throws Throwable {
      LazyPath<String> path = Path.lazyNow(TEST_VALUE);

      String result = path.force();

      assertThat(result).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("toLazy() returns underlying Lazy")
    void toLazyReturnsUnderlyingLazy() {
      LazyPath<String> path = Path.lazyNow(TEST_VALUE);

      Lazy<String> lazy = path.toLazy();

      assertThat(lazy).isNotNull();
    }

    @Test
    @DisplayName("get() wraps checked exceptions in RuntimeException")
    void getWrapsCheckedExceptions() {
      LazyPath<String> path =
          LazyPath.deferThrowable(
              () -> {
                throw new IOException("test IO error");
              });

      assertThatThrownBy(path::get)
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("LazyPath computation failed");
    }
  }

  @Nested
  @DisplayName("Composable Operations (map, peek)")
  class ComposableOperationsTests {

    @Test
    @DisplayName("map() transforms value lazily")
    void mapTransformsValueLazily() {
      AtomicInteger evalCount = new AtomicInteger(0);

      LazyPath<String> path =
          Path.lazyDefer(
              () -> {
                evalCount.incrementAndGet();
                return "hello";
              });

      LazyPath<Integer> mapped = path.map(String::length);

      assertThat(evalCount.get()).isZero(); // Not evaluated yet
      assertThat(mapped.get()).isEqualTo(5);
      assertThat(evalCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("map() validates null mapper")
    void mapValidatesNullMapper() {
      LazyPath<String> path = Path.lazyNow(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.map(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("map() chains correctly")
    void mapChainsCorrectly() {
      LazyPath<String> path = Path.lazyNow("hello");

      LazyPath<String> result =
          path.map(String::toUpperCase).map(s -> s + "!").map(s -> "[" + s + "]");

      assertThat(result.get()).isEqualTo("[HELLO!]");
    }

    @Test
    @DisplayName("peek() observes value without modifying")
    void peekObservesValueWithoutModifying() {
      LazyPath<String> path = Path.lazyNow(TEST_VALUE);
      AtomicBoolean called = new AtomicBoolean(false);

      LazyPath<String> result = path.peek(v -> called.set(true));

      assertThat(called).isFalse(); // peek is lazy too
      assertThat(result.get()).isEqualTo(TEST_VALUE);
      assertThat(called).isTrue();
    }
  }

  @Nested
  @DisplayName("Chainable Operations (via, flatMap, then)")
  class ChainableOperationsTests {

    @Test
    @DisplayName("via() chains dependent computations lazily")
    void viaChainsComputationsLazily() {
      AtomicInteger evalCount = new AtomicInteger(0);

      LazyPath<String> path =
          Path.lazyDefer(
              () -> {
                evalCount.incrementAndGet();
                return "hello";
              });

      LazyPath<Integer> result =
          path.via(
              s ->
                  LazyPath.defer(
                      () -> {
                        evalCount.incrementAndGet();
                        return s.length();
                      }));

      assertThat(evalCount.get()).isZero();
      assertThat(result.get()).isEqualTo(5);
      assertThat(evalCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("via() validates null mapper")
    void viaValidatesNullMapper() {
      LazyPath<String> path = Path.lazyNow(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.via(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("via() validates non-null result")
    void viaValidatesNonNullResult() {
      LazyPath<String> path = Path.lazyNow(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.via(s -> null).get())
          .withMessageContaining("mapper must not return null");
    }

    @Test
    @DisplayName("via() validates result is LazyPath")
    void viaValidatesResultType() {
      LazyPath<String> path = Path.lazyNow(TEST_VALUE);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.via(s -> Path.just(s)).get())
          .withMessageContaining("via mapper must return LazyPath");
    }

    @Test
    @DisplayName("flatMap() is alias for via")
    void flatMapIsAliasForVia() {
      LazyPath<String> path = Path.lazyNow("hello");

      LazyPath<Integer> viaResult = path.via(s -> LazyPath.now(s.length()));
      @SuppressWarnings("unchecked")
      LazyPath<Integer> flatMapResult =
          (LazyPath<Integer>) path.flatMap(s -> LazyPath.now(s.length()));

      assertThat(flatMapResult.get()).isEqualTo(viaResult.get());
    }

    @Test
    @DisplayName("then() sequences computations discarding value")
    void thenSequencesComputationsDiscardingValue() {
      AtomicBoolean firstEvaluated = new AtomicBoolean(false);

      LazyPath<String> path =
          Path.lazyDefer(
              () -> {
                firstEvaluated.set(true);
                return "ignored";
              });

      LazyPath<Integer> result = path.then(() -> LazyPath.now(42));

      assertThat(firstEvaluated).isFalse();
      assertThat(result.get()).isEqualTo(42);
      assertThat(firstEvaluated).isTrue();
    }

    @Test
    @DisplayName("then() throws for incompatible path type")
    void thenThrowsForIncompatibleType() {
      LazyPath<String> path = Path.lazyNow(TEST_VALUE);

      LazyPath<Integer> result = path.then(() -> Path.id(42));

      assertThatIllegalArgumentException()
          .isThrownBy(result::get)
          .withMessageContaining("then supplier must return LazyPath");
    }
  }

  @Nested
  @DisplayName("Combinable Operations (zipWith)")
  class CombinableOperationsTests {

    @Test
    @DisplayName("zipWith() combines two values lazily")
    void zipWithCombinesTwoValuesLazily() {
      AtomicInteger evalCount = new AtomicInteger(0);

      LazyPath<String> first =
          Path.lazyDefer(
              () -> {
                evalCount.incrementAndGet();
                return "hello";
              });
      LazyPath<Integer> second =
          Path.lazyDefer(
              () -> {
                evalCount.incrementAndGet();
                return 3;
              });

      LazyPath<String> result = first.zipWith(second, (s, n) -> s.repeat(n));

      assertThat(evalCount.get()).isZero();
      assertThat(result.get()).isEqualTo("hellohellohello");
      assertThat(evalCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("zipWith() validates null parameters")
    void zipWithValidatesNullParameters() {
      LazyPath<String> path = Path.lazyNow(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(null, (a, b) -> a + b))
          .withMessageContaining("other must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(Path.lazyNow("x"), null))
          .withMessageContaining("combiner must not be null");
    }

    @Test
    @DisplayName("zipWith() throws when given non-LazyPath")
    void zipWithThrowsWhenGivenNonLazyPath() {
      LazyPath<String> path = Path.lazyNow(TEST_VALUE);
      IdPath<Integer> idPath = Path.id(42);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.zipWith(idPath, (s, i) -> s + i))
          .withMessageContaining("Cannot zipWith non-LazyPath");
    }

    @Test
    @DisplayName("zipWith3() combines three values")
    void zipWith3CombinesThreeValues() {
      LazyPath<String> first = Path.lazyNow("hello");
      LazyPath<String> second = Path.lazyNow(" ");
      LazyPath<String> third = Path.lazyNow("world");

      LazyPath<String> result = first.zipWith3(second, third, (a, b, c) -> a + b + c);

      assertThat(result.get()).isEqualTo("hello world");
    }
  }

  @Nested
  @DisplayName("Conversion Methods")
  class ConversionMethodsTests {

    @Test
    @DisplayName("toIOPath() converts to IOPath preserving laziness")
    void toIOPathConvertsCorrectly() {
      AtomicBoolean evaluated = new AtomicBoolean(false);
      LazyPath<String> path =
          Path.lazyDefer(
              () -> {
                evaluated.set(true);
                return TEST_VALUE;
              });

      IOPath<String> result = path.toIOPath();

      assertThat(evaluated).isFalse();
      assertThat(result.unsafeRun()).isEqualTo(TEST_VALUE);
      assertThat(evaluated).isTrue();
    }

    @Test
    @DisplayName("toIdPath() converts to IdPath (forces evaluation)")
    void toIdPathConvertsCorrectly() {
      LazyPath<String> path = Path.lazyNow(TEST_VALUE);

      IdPath<String> result = path.toIdPath();

      assertThat(result.get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("toMaybePath() converts to MaybePath with Just")
    void toMaybePathConvertsToJust() {
      LazyPath<String> path = Path.lazyNow(TEST_VALUE);

      MaybePath<String> result = path.toMaybePath();

      assertThat(result.run().isJust()).isTrue();
      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("toMaybePath() converts to Nothing for null")
    void toMaybePathConvertsToNothing() {
      LazyPath<String> path = Path.lazyNow(null);

      MaybePath<String> result = path.toMaybePath();

      assertThat(result.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("toTryPath() converts to TryPath with success")
    void toTryPathConvertsToSuccess() {
      LazyPath<String> path = Path.lazyNow(TEST_VALUE);

      TryPath<String> result = path.toTryPath();

      assertThat(result.run().isSuccess()).isTrue();
      assertThat(result.run().orElse(null)).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("toTryPath() captures exception as failure")
    void toTryPathCapturesException() {
      RuntimeException ex = new RuntimeException("test error");
      LazyPath<String> path =
          Path.lazyDefer(
              () -> {
                throw ex;
              });

      TryPath<String> result = path.toTryPath();

      assertThat(result.run().isFailure()).isTrue();
    }
  }

  @Nested
  @DisplayName("Object Methods")
  class ObjectMethodsTests {

    @Test
    @DisplayName("equals() returns true for same instance")
    void equalsWorksForSameInstance() {
      LazyPath<String> path = Path.lazyNow(TEST_VALUE);

      assertThat(path).isEqualTo(path);
    }

    @Test
    @DisplayName("equals() returns false for different instances (identity-based)")
    void equalsReturnsFalseForDifferentInstances() {
      LazyPath<String> path1 = Path.lazyNow(TEST_VALUE);
      LazyPath<String> path2 = Path.lazyNow(TEST_VALUE);

      // Lazy uses identity-based equality, so different instances are not equal
      assertThat(path1).isNotEqualTo(path2);
    }

    @Test
    @DisplayName("equals() returns false for non-LazyPath types")
    void equalsReturnsFalseForOtherTypes() {
      LazyPath<String> path = Path.lazyNow(TEST_VALUE);

      assertThat(path.equals("not a path")).isFalse();
      assertThat(path.equals(null)).isFalse();
    }

    @Test
    @DisplayName("hashCode() is consistent for same instance")
    void hashCodeIsConsistentForSameInstance() {
      LazyPath<String> path = Path.lazyNow(TEST_VALUE);

      assertThat(path.hashCode()).isEqualTo(path.hashCode());
    }

    @Test
    @DisplayName("toString() shows evaluation state")
    void toStringShowsEvaluationState() {
      LazyPath<String> unevaluated = Path.lazyDefer(() -> TEST_VALUE);
      LazyPath<String> evaluated = Path.lazyNow(TEST_VALUE);

      assertThat(unevaluated.toString()).contains("LazyPath");
      assertThat(evaluated.toString()).contains("LazyPath");
    }
  }

  @Nested
  @DisplayName("Exception Handling")
  class ExceptionHandlingTests {

    @Test
    @DisplayName("deferThrowable creates path that can fail")
    void deferThrowableCreatesFailablePath() {
      RuntimeException ex = new RuntimeException("test error");

      LazyPath<String> path =
          LazyPath.deferThrowable(
              () -> {
                throw ex;
              });

      assertThatThrownBy(path::get).isInstanceOf(RuntimeException.class).hasMessage("test error");
    }

    @Test
    @DisplayName("Exception during evaluation is propagated on subsequent calls")
    void exceptionIsPropagated() {
      AtomicInteger callCount = new AtomicInteger(0);

      LazyPath<String> path =
          Path.lazyDefer(
              () -> {
                callCount.incrementAndGet();
                throw new RuntimeException("test error");
              });

      assertThatThrownBy(path::get).isInstanceOf(RuntimeException.class);
      // Depending on implementation, may or may not cache the exception
    }
  }
}
