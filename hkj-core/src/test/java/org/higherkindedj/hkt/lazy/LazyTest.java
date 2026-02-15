// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.lazy;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.lazy.LazyAssert.assertThatLazy;
import static org.higherkindedj.hkt.lazy.LazyKindHelper.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Lazy<A> Complete Test Suite")
class LazyTest extends LazyTestBase {

  @Nested
  @DisplayName("Complete Lazy Test Suite")
  class CompleteLazyTestSuite {

    @Test
    @DisplayName("Run complete Lazy core type tests using base fixtures")
    void runCompleteLazyCoreTypeTestsUsingBaseFixtures() {
      // Create proper deferred and now instances for core type tests
      // Note: validKind from base is Lazy.now(), so we need to create deferred explicitly
      Lazy<Integer> deferredLazy = Lazy.defer(() -> DEFAULT_LAZY_VALUE);
      Lazy<Integer> nowLazy = Lazy.now(DEFAULT_LAZY_VALUE);

      CoreTypeTest.<Integer>lazy(Lazy.class)
          .withDeferred(deferredLazy)
          .withNow(nowLazy)
          .withMappers(validMapper)
          .testAll();
    }

    @Test
    @DisplayName("Run complete Lazy core type tests with custom instances")
    void runCompleteLazyCoreTypeTestsWithCustomInstances() {
      COUNTER.set(0);
      Lazy<String> deferred =
          Lazy.defer(
              () -> {
                COUNTER.incrementAndGet();
                Thread.sleep(5);
                return "SuccessValue";
              });
      Lazy<String> now = Lazy.now("PrecomputedValue");

      CoreTypeTest.<String>lazy(Lazy.class)
          .withDeferred(deferred)
          .withNow(now)
          .withMappers(String::length)
          .testAll();
    }

    @Test
    @DisplayName("Run complete Lazy KindHelper tests")
    void runCompleteLazyKindHelperTests() {
      Lazy<String> instance = Lazy.now("TestValue");

      CoreTypeTest.lazyKindHelper(instance).test();
    }
  }

  @Nested
  @DisplayName("Factory Methods (defer, now)")
  class FactoryTests {

    @Test
    @DisplayName("defer should not evaluate supplier immediately")
    void deferShouldNotEvaluateSupplierImmediately() {
      COUNTER.set(0);
      Lazy<String> lazy = Lazy.defer(successSupplier());

      assertThat(COUNTER.get()).isZero();
      assertThatLazy(lazy).isNotEvaluated();
    }

    @Test
    @DisplayName("defer should throw NPE for null supplier")
    void deferShouldThrowNPEForNullSupplier() {
      assertThatNullPointerException()
          .isThrownBy(() -> Lazy.defer(null))
          .withMessageContaining("computation");
    }

    @Test
    @DisplayName("now should create evaluated Lazy with value")
    void nowShouldCreateEvaluatedLazyWithValue() throws Throwable {
      COUNTER.set(0);
      Lazy<String> lazy = Lazy.now("DirectValue");

      assertThat(COUNTER.get()).isZero();
      assertThatLazy(lazy).isEvaluated().hasValue("DirectValue");
      assertThat(COUNTER.get()).isZero();
    }

    @Test
    @DisplayName("now should create evaluated Lazy with null")
    void nowShouldCreateEvaluatedLazyWithNull() throws Throwable {
      COUNTER.set(0);
      Lazy<String> lazy = Lazy.now(null);

      assertThat(COUNTER.get()).isZero();
      assertThatLazy(lazy).hasValue(null);
      assertThat(COUNTER.get()).isZero();
      assertThat(lazy.toString()).isEqualTo("Lazy[null]");
    }
  }

  @Nested
  @DisplayName("force() Method")
  class ForceTests {

    @Test
    @DisplayName("force should evaluate deferred supplier only once")
    void forceShouldEvaluateDeferredSupplierOnlyOnce() throws Throwable {
      COUNTER.set(0);
      Lazy<String> lazy = Lazy.defer(successSupplier());

      assertThat(COUNTER.get()).isZero();

      // First force
      assertThatLazy(lazy).whenForcedHasValue("SuccessValue");
      assertThat(COUNTER.get()).isEqualTo(1);

      // Second force - should use cached value
      assertThatLazy(lazy).whenForcedHasValue("SuccessValue");
      assertThat(COUNTER.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("force should return cached value for now")
    void forceShouldReturnCachedValueForNow() throws Throwable {
      COUNTER.set(0);
      Lazy<String> lazy = Lazy.now("Preset");

      assertThatLazy(lazy).whenForcedHasValue("Preset");
      assertThat(COUNTER.get()).isZero();
    }

    @Test
    @DisplayName("force should cache and return null value")
    void forceShouldCacheAndReturnNullValue() throws Throwable {
      COUNTER.set(0);
      Lazy<String> lazy = Lazy.defer(nullSupplier());

      // First force
      assertThat(lazy.force()).isNull();
      assertThat(COUNTER.get()).isEqualTo(1);

      // Second force - memoised null
      assertThat(lazy.force()).isNull();
      assertThat(COUNTER.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("force should cache and rethrow runtime exception")
    void forceShouldCacheAndRethrowRuntimeException() {
      COUNTER.set(0);
      Lazy<String> lazy = Lazy.defer(runtimeFailSupplier());

      // First force
      assertThatLazy(lazy).whenForcedThrows(IllegalStateException.class);
      assertThat(COUNTER.get()).isEqualTo(1);

      // Second force - cached exception
      Throwable thrown2 = catchThrowable(lazy::force);
      assertThat(thrown2).isInstanceOf(IllegalStateException.class).hasMessage("Runtime Failure");
      assertThat(COUNTER.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("force should cache and rethrow checked exception")
    void forceShouldCacheAndRethrowCheckedException() {
      COUNTER.set(0);
      Lazy<String> lazy = Lazy.defer(checkedFailSupplier());

      // First force
      assertThatLazy(lazy).whenForcedThrows(IOException.class);
      assertThat(COUNTER.get()).isEqualTo(1);

      // Second force - cached exception
      Throwable thrown2 = catchThrowable(lazy::force);
      assertThat(thrown2).isInstanceOf(IOException.class).hasMessage("Checked Failure");
      assertThat(COUNTER.get()).isEqualTo(1);
      assertThatLazy(lazy).hasFailed();
    }
  }

  // ============================================================================
  // Map Operations
  // ============================================================================

  @Nested
  @DisplayName("map() Method")
  class MapTests {

    @Test
    @DisplayName("map should transform value lazily")
    void mapShouldTransformValueLazily() throws Throwable {
      COUNTER.set(0);
      AtomicInteger mapCounter = new AtomicInteger(0);

      Lazy<String> lazy = Lazy.defer(successSupplier());
      Lazy<Integer> mapped =
          lazy.map(
              s -> {
                mapCounter.incrementAndGet();
                return s.length();
              });

      assertThat(COUNTER.get()).isZero();
      assertThat(mapCounter.get()).isZero();

      // Force the mapped lazy
      assertThatLazy(mapped).whenForcedHasValue("SuccessValue".length());
      assertThat(COUNTER.get()).isEqualTo(1);
      assertThat(mapCounter.get()).isEqualTo(1);

      // Force again - both should be memoised
      assertThatLazy(mapped).whenForcedHasValue("SuccessValue".length());
      assertThat(COUNTER.get()).isEqualTo(1);
      assertThat(mapCounter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("map should propagate failure from original Lazy")
    void mapShouldPropagateFailureFromOriginalLazy() {
      COUNTER.set(0);
      AtomicInteger mapCounter = new AtomicInteger(0);

      Lazy<String> lazy = Lazy.defer(runtimeFailSupplier());
      Lazy<Integer> mapped =
          lazy.map(
              s -> {
                mapCounter.incrementAndGet();
                return s.length();
              });

      assertThat(COUNTER.get()).isZero();
      assertThat(mapCounter.get()).isZero();

      assertThatLazy(mapped).whenForcedThrows(IllegalStateException.class);

      assertThat(COUNTER.get()).isEqualTo(1);
      assertThat(mapCounter.get()).isZero(); // Mapper never ran
    }

    @Test
    @DisplayName("map should fail if mapper throws")
    void mapShouldFailIfMapperThrows() {
      COUNTER.set(0);
      RuntimeException mapperEx = new IllegalArgumentException("Mapper failed");

      Lazy<String> lazy = Lazy.defer(successSupplier());
      Lazy<Integer> mapped =
          lazy.map(
              s -> {
                throw mapperEx;
              });

      assertThatLazy(mapped).whenForcedThrows(IllegalArgumentException.class);
      assertThat(COUNTER.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("flatMap should throw exception if mapper returns null")
    void flatMapShouldThrowExceptionIfMapperReturnsNull() {
      Lazy<String> lazy = Lazy.defer(successSupplier());
      Lazy<Integer> flatMapped = lazy.flatMap(s -> null);

      assertThatThrownBy(flatMapped::force)
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(
              "Function f in Lazy.flatMap returned null when Lazy expected, which is not allowed");
    }
  }

  // ============================================================================
  // FlatMap Operations
  // ============================================================================

  @Nested
  @DisplayName("flatMap() Method")
  class FlatMapTests {

    @Test
    @DisplayName("flatMap should sequence lazily")
    void flatMapShouldSequenceLazily() throws Throwable {
      COUNTER.set(0);
      AtomicInteger innerCounter = new AtomicInteger(0);

      Lazy<String> lazyA = Lazy.defer(successSupplier());
      Lazy<Integer> flatMapped =
          lazyA.flatMap(
              s ->
                  Lazy.defer(
                      () -> {
                        innerCounter.incrementAndGet();
                        return s.length();
                      }));

      assertThat(COUNTER.get()).isZero();
      assertThat(innerCounter.get()).isZero();

      assertThatLazy(flatMapped).whenForcedHasValue("SuccessValue".length());
      assertThat(COUNTER.get()).isEqualTo(1);
      assertThat(innerCounter.get()).isEqualTo(1);

      // Second force - both memoised
      assertThatLazy(flatMapped).whenForcedHasValue("SuccessValue".length());
      assertThat(COUNTER.get()).isEqualTo(1);
      assertThat(innerCounter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("flatMap should propagate failure from initial Lazy")
    void flatMapShouldPropagateFailureFromInitialLazy() {
      COUNTER.set(0);
      AtomicInteger innerCounter = new AtomicInteger(0);

      Lazy<String> lazyA = Lazy.defer(runtimeFailSupplier());
      Lazy<Integer> flatMapped =
          lazyA.flatMap(
              s ->
                  Lazy.defer(
                      () -> {
                        innerCounter.incrementAndGet();
                        return s.length();
                      }));

      assertThatLazy(flatMapped).whenForcedThrows(IllegalStateException.class);

      assertThat(COUNTER.get()).isEqualTo(1);
      assertThat(innerCounter.get()).isZero();
    }

    @Test
    @DisplayName("flatMap should propagate failure from mapper function")
    void flatMapShouldPropagateFailureFromMapperFunction() {
      COUNTER.set(0);
      RuntimeException mapperEx = new IllegalArgumentException("Mapper failed");

      Lazy<String> lazyA = Lazy.defer(successSupplier());
      Lazy<Integer> flatMapped =
          lazyA.flatMap(
              s -> {
                throw mapperEx;
              });

      assertThatLazy(flatMapped).whenForcedThrows(IllegalArgumentException.class);
      assertThat(COUNTER.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("flatMap should propagate failure from resulting Lazy")
    void flatMapShouldPropagateFailureFromResultingLazy() {
      COUNTER.set(0);
      RuntimeException resultEx = new UnsupportedOperationException("Result Lazy failed");

      Lazy<String> lazyA = Lazy.defer(successSupplier());
      Lazy<Integer> flatMapped =
          lazyA.flatMap(
              s ->
                  Lazy.defer(
                      () -> {
                        throw resultEx;
                      }));

      assertThatLazy(flatMapped).whenForcedThrows(UnsupportedOperationException.class);
      assertThat(COUNTER.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("flatMap should throw NPE for null mapper")
    void flatMapShouldThrowNPEForNullMapper() {
      Lazy<String> lazy = Lazy.defer(successSupplier());

      assertThatNullPointerException()
          .isThrownBy(() -> lazy.flatMap(null))
          .withMessageContaining("Function f for Lazy.flatMap cannot be null");
    }

    @Test
    @DisplayName("flatMap should throw exception if mapper returns null")
    void flatMapShouldThrowExceptionIfMapperReturnsNull() {
      Lazy<String> lazy = Lazy.defer(successSupplier());
      Lazy<Integer> flatMapped = lazy.flatMap(s -> null);

      assertThatThrownBy(flatMapped::force)
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(
              "Function f in Lazy.flatMap returned null when Lazy expected, which is not allowed");
    }
  }

  @Nested
  @DisplayName("Memoisation Semantics")
  class MemoizationTests {

    @Test
    @DisplayName("Should memoise successful computation")
    void shouldMemoiseSuccessfulComputation() throws Throwable {
      AtomicInteger counter = new AtomicInteger(0);
      Lazy<String> lazy =
          Lazy.defer(
              () -> {
                counter.incrementAndGet();
                return "value";
              });

      lazy.force();
      lazy.force();
      lazy.force();

      assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should memoise null value")
    void shouldMemoiseNullValue() throws Throwable {
      AtomicInteger counter = new AtomicInteger(0);
      Lazy<String> lazy =
          Lazy.defer(
              () -> {
                counter.incrementAndGet();
                return null;
              });

      lazy.force();
      lazy.force();

      assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should memoise exception")
    void shouldMemoiseException() {
      AtomicInteger counter = new AtomicInteger(0);
      Lazy<String> lazy =
          Lazy.defer(
              () -> {
                counter.incrementAndGet();
                throw new RuntimeException("Failed");
              });

      catchThrowable(lazy::force);
      catchThrowable(lazy::force);

      assertThat(counter.get()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("toString should not force evaluation")
    void toStringShouldNotForceEvaluation() {
      COUNTER.set(0);
      Lazy<String> lazy = Lazy.defer(successSupplier());

      assertThat(lazy.toString()).isEqualTo("Lazy[unevaluated...]");
      assertThat(COUNTER.get()).isZero();
    }

    @Test
    @DisplayName("toString should show value after force")
    void toStringShouldShowValueAfterForce() throws Throwable {
      Lazy<String> lazy = Lazy.defer(() -> "Ready");
      lazy.force();

      assertThat(lazy.toString()).isEqualTo("Lazy[Ready]");
    }

    @Test
    @DisplayName("toString should show failure state")
    void toStringShouldShowFailureState() {
      Lazy<String> lazy =
          Lazy.defer(
              () -> {
                throw new IllegalStateException("Test");
              });

      catchThrowable(lazy::force);

      assertThat(lazy.toString()).isEqualTo("Lazy[failed: IllegalStateException]");
    }

    @Test
    @DisplayName("Lazy should use reference equality")
    void lazyShouldUseReferenceEquality() {
      Lazy<String> lazy1 = Lazy.defer(() -> "a");
      Lazy<String> lazy2 = Lazy.defer(() -> "a");
      Lazy<String> lazy1Ref = lazy1;

      assertThat(lazy1).isEqualTo(lazy1Ref);
      assertThat(lazy1).isNotEqualTo(lazy2);
      assertThat(lazy1).isNotEqualTo(null);
      assertThat(lazy1).isNotEqualTo("a");
    }

    @Test
    @DisplayName("hashCode should use reference hashCode")
    void hashCodeShouldUseReferenceHashCode() {
      Lazy<String> lazy1 = Lazy.defer(() -> "a");
      Lazy<String> lazy1Ref = lazy1;

      assertThat(lazy1.hashCode()).isEqualTo(lazy1Ref.hashCode());
    }
  }

  @Nested
  @DisplayName("Thread Safety and Concurrent Evaluation")
  class ThreadSafetyTests {

    @Test
    @DisplayName("Concurrent force() calls should evaluate only once")
    void concurrentForceCallsShouldEvaluateOnlyOnce() throws Exception {
      AtomicInteger counter = new AtomicInteger(0);
      Lazy<String> lazy =
          Lazy.defer(
              () -> {
                counter.incrementAndGet();
                Thread.sleep(50); // Delay to increase chance of concurrent access
                return "ConcurrentValue";
              });

      // Create multiple threads that will all try to force the Lazy simultaneously
      int threadCount = 10;
      Thread[] threads = new Thread[threadCount];
      String[] results = new String[threadCount];
      Throwable[] exceptions = new Throwable[threadCount];

      for (int i = 0; i < threadCount; i++) {
        final int index = i;
        threads[i] =
            new Thread(
                () -> {
                  try {
                    results[index] = lazy.force();
                  } catch (Throwable t) {
                    exceptions[index] = t;
                  }
                });
      }

      // Start all threads
      for (Thread thread : threads) {
        thread.start();
      }

      // Wait for all threads to complete
      for (Thread thread : threads) {
        thread.join();
      }

      // Verify that the computation was only executed once
      assertThat(counter.get()).isEqualTo(1);

      // Verify that all threads got the same result
      for (int i = 0; i < threadCount; i++) {
        assertThat(exceptions[i]).isNull();
        assertThat(results[i]).isEqualTo("ConcurrentValue");
      }
    }

    @Test
    @DisplayName("Concurrent force() calls should cache exception")
    void concurrentForceCallsShouldCacheException() throws Exception {
      AtomicInteger counter = new AtomicInteger(0);
      RuntimeException testException = new IllegalStateException("Concurrent Failure");
      Lazy<String> lazy =
          Lazy.defer(
              () -> {
                counter.incrementAndGet();
                Thread.sleep(50); // Delay to increase chance of concurrent access
                throw testException;
              });

      // Create multiple threads
      int threadCount = 10;
      Thread[] threads = new Thread[threadCount];
      Throwable[] exceptions = new Throwable[threadCount];

      for (int i = 0; i < threadCount; i++) {
        final int index = i;
        threads[i] =
            new Thread(
                () -> {
                  try {
                    lazy.force();
                  } catch (Throwable t) {
                    exceptions[index] = t;
                  }
                });
      }

      // Start all threads
      for (Thread thread : threads) {
        thread.start();
      }

      // Wait for all threads to complete
      for (Thread thread : threads) {
        thread.join();
      }

      // Verify that the computation was only executed once
      assertThat(counter.get()).isEqualTo(1);

      // Verify that all threads got the same exception
      for (int i = 0; i < threadCount; i++) {
        assertThat(exceptions[i]).isInstanceOf(IllegalStateException.class).isSameAs(testException);
      }
    }

    @Test
    @DisplayName("ExecutorService concurrent force() calls should evaluate only once")
    void executorServiceConcurrentForceCallsShouldEvaluateOnlyOnce() throws Exception {
      AtomicInteger count = new AtomicInteger(0);
      Lazy<Integer> lazy =
          Lazy.defer(
              () -> {
                Thread.sleep(10); // Small delay to increase contention
                return count.incrementAndGet();
              });

      // 10 threads all force() simultaneously
      ExecutorService executor = Executors.newFixedThreadPool(10);
      try {
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
          futures.add(
              executor.submit(
                  () -> {
                    try {
                      return lazy.force();
                    } catch (Throwable t) {
                      throw new RuntimeException(t);
                    }
                  }));
        }

        // All threads should get the same value
        Set<Integer> results =
            futures.stream()
                .map(
                    f -> {
                      try {
                        return f.get();
                      } catch (Exception e) {
                        throw new RuntimeException(e);
                      }
                    })
                .collect(Collectors.toSet());

        assertThat(results).hasSize(1); // All got same value
        assertThat(count.get()).isEqualTo(1); // Supplier called once!
      } finally {
        executor.shutdown();
      }
    }
  }

  @Nested
  @DisplayName("Individual Component Tests")
  class IndividualComponents {

    @Test
    @DisplayName("Test factory methods only")
    void testFactoryMethodsOnly() {
      CoreTypeTest.lazy(Lazy.class)
          .withDeferred(Lazy.defer(() -> "test"))
          .withNow(Lazy.now("test"))
          .withoutMappers()
          .onlyFactoryMethods()
          .testAll();
    }

    @Test
    @DisplayName("Test force operation only")
    void testForceOnly() {
      CoreTypeTest.lazy(Lazy.class)
          .withDeferred(Lazy.defer(() -> "test"))
          .withNow(Lazy.now("test"))
          .withoutMappers()
          .onlyForce()
          .testAll();
    }

    @Test
    @DisplayName("Test memoisation only")
    void testMemoizationOnly() {
      CoreTypeTest.lazy(Lazy.class)
          .withDeferred(Lazy.defer(() -> "test"))
          .withNow(Lazy.now("test"))
          .withoutMappers()
          .onlyMemoisation()
          .testAll();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      CoreTypeTest.lazy(Lazy.class)
          .withDeferred(Lazy.defer(() -> "test"))
          .withNow(Lazy.now("test"))
          .withMappers(Object::toString)
          .onlyValidations()
          .testAll();
    }
  }
}
