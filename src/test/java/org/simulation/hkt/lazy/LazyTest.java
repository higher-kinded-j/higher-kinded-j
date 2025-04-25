package org.simulation.hkt.lazy;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@DisplayName("Lazy<A> Direct Tests")
class LazyTest {

  private AtomicInteger counter;
  private ExecutorService executor; // For concurrency tests

  @BeforeEach
  void setUp() {
    counter = new AtomicInteger(0);
    // Use a fixed thread pool for more predictable concurrency tests
    // Adjust pool size if needed, but 10 is usually reasonable for testing
    executor = Executors.newFixedThreadPool(10);
  }

  @AfterEach
  void tearDown() {
    // Shut down the executor after each test
    if (executor != null) {
      executor.shutdownNow();
      try {
        // Wait a bit for termination
        if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
          System.err.println("Executor did not terminate in time.");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  // --- Test Suppliers ---

  private Supplier<String> successSupplier() {
    return () -> {
      counter.incrementAndGet();
      // Simulate some work
      try {
        Thread.sleep(5); // Small delay
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return "SuccessValue";
    };
  }

  private Supplier<String> nullSupplier() {
    return () -> {
      counter.incrementAndGet();
      return null;
    };
  }

  private Supplier<String> runtimeFailSupplier() {
    return () -> {
      counter.incrementAndGet();
      throw new IllegalStateException("Runtime Failure");
    };
  }

  private Supplier<String> errorFailSupplier() {
    return () -> {
      counter.incrementAndGet();
      throw new StackOverflowError("Error Failure");
    };
  }

  // Helper for checked exceptions (wrapped in RuntimeException for Supplier compatibility)
  private Supplier<String> checkedFailSupplier() {
    return () -> {
      counter.incrementAndGet();
      try {
        throw new IOException("Checked Failure");
      } catch (IOException e) {
        // Re-throw wrapped as Lazy.force catches Throwable
        throw new RuntimeException(e);
      }
    };
  }

  // --- Test Classes ---

  @Nested
  @DisplayName("Factory Methods (defer, now)")
  class FactoryTests {
    @Test
    void defer_shouldNotEvaluateSupplierImmediately() {
      Lazy<String> lazy = Lazy.defer(successSupplier());
      assertThat(counter.get()).isZero();
      // Also test toString for unevaluated state here
      assertThat(lazy.toString()).isEqualTo("Lazy[unevaluated...]");
    }

    @Test
    void defer_shouldThrowNPEForNullSupplier() {
      // This test covers line 47 in Lazy.java (constructor check)
      assertThatNullPointerException()
          .isThrownBy(() -> Lazy.defer(null))
          .withMessageContaining("computation");
    }

    @Test
    void now_shouldCreateEvaluatedLazyWithValue() {
      Lazy<String> lazy = Lazy.now("DirectValue");
      assertThat(counter.get()).isZero(); // No computation ran
      assertThat(lazy.force()).isEqualTo("DirectValue");
      assertThat(counter.get()).isZero(); // Still zero
      assertThat(lazy.toString()).isEqualTo("Lazy[DirectValue]"); // Test evaluated toString
    }

    @Test
    void now_shouldCreateEvaluatedLazyWithNull() {
      Lazy<String> lazy = Lazy.now(null);
      assertThat(counter.get()).isZero();
      assertThat(lazy.force()).isNull();
      assertThat(counter.get()).isZero();
      assertThat(lazy.toString()).isEqualTo("Lazy[null]");
    }
  }

  @Nested
  @DisplayName("force() Method")
  class ForceTests {
    @Test
    void force_shouldEvaluateDeferredSupplierOnlyOnce() {
      Lazy<String> lazy = Lazy.defer(successSupplier());
      assertThat(counter.get()).isZero();

      // First force
      assertThat(lazy.force()).isEqualTo("SuccessValue");
      assertThat(counter.get()).isEqualTo(1);

      // Second force
      assertThat(lazy.force()).isEqualTo("SuccessValue");
      assertThat(counter.get()).isEqualTo(1); // Should not increment again
    }

    @Test
    void force_shouldReturnCachedValueForNow() {
      Lazy<String> lazy = Lazy.now("Preset");
      assertThat(counter.get()).isZero();
      assertThat(lazy.force()).isEqualTo("Preset");
      assertThat(counter.get()).isZero(); // No computation involved
    }

    @Test
    void force_shouldCacheAndReturnNullValue() {
      Lazy<String> lazy = Lazy.defer(nullSupplier());
      assertThat(counter.get()).isZero();

      // First force
      assertThat(lazy.force()).isNull();
      assertThat(counter.get()).isEqualTo(1);

      // Second force
      assertThat(lazy.force()).isNull();
      assertThat(counter.get()).isEqualTo(1); // Memoized null
    }

    @Test
    void force_shouldCacheAndRethrowRuntimeException() {
      Lazy<String> lazy = Lazy.defer(runtimeFailSupplier());
      assertThat(counter.get()).isZero();

      // First force - expect exception
      Throwable thrown1 = catchThrowable(lazy::force);
      assertThat(thrown1).isInstanceOf(IllegalStateException.class).hasMessage("Runtime Failure");
      assertThat(counter.get()).isEqualTo(1); // Evaluated once

      // Second force - expect same cached exception
      Throwable thrown2 = catchThrowable(lazy::force);
      assertThat(thrown2).isInstanceOf(IllegalStateException.class).hasMessage("Runtime Failure");
      assertThat(counter.get()).isEqualTo(1); // Not evaluated again

      // Verify the specific re-throwing branch (line 84) was likely hit
      assertThat(thrown2).isSameAs(thrown1);
    }

    @Test
    void force_shouldCacheAndRethrowError() {
      Lazy<String> lazy = Lazy.defer(errorFailSupplier());
      assertThat(counter.get()).isZero();

      // First force
      Throwable thrown1 = catchThrowable(lazy::force);
      assertThat(thrown1).isInstanceOf(StackOverflowError.class).hasMessage("Error Failure");
      assertThat(counter.get()).isEqualTo(1);

      // Second force
      Throwable thrown2 = catchThrowable(lazy::force);
      assertThat(thrown2).isInstanceOf(StackOverflowError.class).hasMessage("Error Failure");
      assertThat(counter.get()).isEqualTo(1); // Not evaluated again

      // Verify the specific re-throwing branch (line 86) was likely hit
      assertThat(thrown2).isSameAs(thrown1);
    }

    @Test
    void force_shouldCacheAndRethrowWrappedCheckedException() {
      // This tests the scenario where the supplier wraps a checked exception
      // The exception caught by force() will be the RuntimeException wrapper.
      Lazy<String> lazy = Lazy.defer(checkedFailSupplier());
      assertThat(counter.get()).isZero();

      // First force - Expect the wrapper RuntimeException
      Throwable thrown1 = catchThrowable(lazy::force);
      assertThat(thrown1)
          .isInstanceOf(RuntimeException.class) // The wrapper
          .hasCauseInstanceOf(IOException.class) // The original checked exception
          .cause()
          .hasMessage("Checked Failure");
      assertThat(counter.get()).isEqualTo(1);

      // Second force - Expect the same cached wrapper exception
      Throwable thrown2 = catchThrowable(lazy::force);
      assertThat(thrown2)
          .isInstanceOf(RuntimeException.class)
          .hasCauseInstanceOf(IOException.class)
          .cause()
          .hasMessage("Checked Failure");
      assertThat(counter.get()).isEqualTo(1); // Not evaluated again

      // Check the cached exception directly if possible (though internal state)
      // This implicitly tests line 84 (re-throwing RuntimeException)
      assertThat(thrown2).isSameAs(thrown1); // Should re-throw the exact same exception instance

      // Note: Testing line 88 (re-throwing non-Runtime/non-Error) is difficult
      // because Supplier.get() doesn't allow throwing checked exceptions directly.
    }

    // Simplified concurrency test
    @Test
    @Timeout(10) // Keep a reasonable timeout
    void force_shouldBeThreadSafeDuringInitialization() throws InterruptedException {
      int numThreads = 15; // Reduced thread count
      Supplier<String> verySlowSupplier =
          () -> {
            try {
              // Keep a delay to encourage contention
              Thread.sleep(100);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            counter.incrementAndGet();
            return "VerySlowValue";
          };
      Lazy<String> lazy = Lazy.defer(verySlowSupplier);
      counter.set(0);
      List<String> results = Collections.synchronizedList(new ArrayList<>());
      List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());
      CountDownLatch finishLatch = new CountDownLatch(numThreads); // Only use finish latch

      for (int i = 0; i < numThreads; i++) {
        executor.submit(
            () -> {
              try {
                // No explicit start synchronization, just submit and let them run
                String result = lazy.force();
                results.add(result);
              } catch (Throwable e) {
                exceptions.add(e);
              } finally {
                finishLatch.countDown(); // Signal thread finished
              }
            });
      }

      // Wait for all threads to finish - Keep a generous timeout
      assertThat(finishLatch.await(10, TimeUnit.SECONDS))
          .as("Threads did not finish in time")
          .isTrue();

      // Check for unexpected exceptions
      assertThat(exceptions).isEmpty();
      // Check all threads got the same result
      assertThat(results).hasSize(numThreads);
      results.forEach(result -> assertThat(result).isEqualTo("VerySlowValue"));
      // Check computation ran exactly once despite contention
      assertThat(counter.get()).as("Computation count").isEqualTo(1);

      // Additional force call to ensure memoization holds after concurrency
      assertThat(lazy.force()).isEqualTo("VerySlowValue");
      assertThat(counter.get()).as("Computation count after second force").isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("map() Method")
  class MapTests {
    @Test
    void map_shouldTransformValueLazily() {
      Lazy<String> lazy = Lazy.defer(successSupplier());
      AtomicInteger mapCounter = new AtomicInteger(0); // Counter for mapper function
      Function<String, Integer> mapper =
          s -> {
            mapCounter.incrementAndGet();
            return s.length();
          };
      Lazy<Integer> mappedLazy = lazy.map(mapper);

      assertThat(counter.get()).isZero(); // Original not evaluated yet
      assertThat(mapCounter.get()).isZero(); // Mapper not evaluated yet

      // Force the mapped lazy
      assertThat(mappedLazy.force()).isEqualTo("SuccessValue".length());
      assertThat(counter.get()).isEqualTo(1); // Original computation ran
      assertThat(mapCounter.get()).isEqualTo(1); // Mapper ran

      // Force again
      assertThat(mappedLazy.force()).isEqualTo("SuccessValue".length());
      assertThat(counter.get()).isEqualTo(1); // Original still memoized
      assertThat(mapCounter.get()).isEqualTo(1); // Mapper result memoized by outer Lazy
    }

    @Test
    void map_shouldPropagateFailure() {
      Lazy<String> lazy = Lazy.defer(runtimeFailSupplier());
      AtomicInteger mapCounter = new AtomicInteger(0);
      Lazy<Integer> mappedLazy = lazy.map(s -> { mapCounter.incrementAndGet(); return s.length(); });

      assertThat(counter.get()).isZero();
      assertThat(mapCounter.get()).isZero();
      assertThatThrownBy(mappedLazy::force)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Runtime Failure");
      assertThat(counter.get()).isEqualTo(1); // Original ran (and failed)
      assertThat(mapCounter.get()).isZero(); // Mapper function never ran
    }

    @Test
    void map_shouldFailIfMapperThrows() {
      Lazy<String> lazy = Lazy.defer(successSupplier());
      RuntimeException mapperEx = new IllegalArgumentException("Mapper failed");
      AtomicInteger mapCounter = new AtomicInteger(0);
      Lazy<Integer> mappedLazy =
          lazy.map(
              s -> {
                mapCounter.incrementAndGet();
                throw mapperEx;
              });

      assertThat(counter.get()).isZero();
      assertThat(mapCounter.get()).isZero();
      assertThatThrownBy(mappedLazy::force)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Mapper failed");
      assertThat(counter.get()).isEqualTo(1); // Original ran
      assertThat(mapCounter.get()).isEqualTo(1); // Mapper ran (and failed)
    }

    @Test
    void map_shouldThrowNPEForNullMapper() {
      Lazy<String> lazy = Lazy.defer(successSupplier());
      assertThatNullPointerException()
          .isThrownBy(() -> lazy.map(null))
          .withMessageContaining("mapper function");
    }
  }

  @Nested
  @DisplayName("flatMap() Method")
  class FlatMapTests {
    @Test
    void flatMap_shouldSequenceLazily() {
      AtomicInteger innerCounter = new AtomicInteger(0);
      Lazy<String> lazyA = Lazy.defer(successSupplier()); // -> "SuccessValue"
      Function<String, Lazy<Integer>> mapper =
          s ->
              Lazy.defer(
                  () -> { // Lazy length
                    innerCounter.incrementAndGet();
                    return s.length();
                  });

      Lazy<Integer> flatMappedLazy = lazyA.flatMap(mapper);

      assertThat(counter.get()).isZero(); // Nothing evaluated yet
      assertThat(innerCounter.get()).isZero();

      // Force the result
      assertThat(flatMappedLazy.force()).isEqualTo("SuccessValue".length());
      assertThat(counter.get()).isEqualTo(1); // Original computation ran
      assertThat(innerCounter.get()).isEqualTo(1); // Inner computation ran

      // Force again
      assertThat(flatMappedLazy.force()).isEqualTo("SuccessValue".length());
      assertThat(counter.get()).isEqualTo(1); // Original memoized
      assertThat(innerCounter.get()).isEqualTo(1); // Inner memoized by the outer lazy result
    }

    @Test
    void flatMap_shouldPropagateFailureFromInitialLazy() {
      Lazy<String> lazyA = Lazy.defer(runtimeFailSupplier());
      AtomicInteger innerCounter = new AtomicInteger(0);
      Function<String, Lazy<Integer>> mapper =
          s ->
              Lazy.defer(
                  () -> {
                    innerCounter.incrementAndGet();
                    return s.length();
                  });

      Lazy<Integer> flatMappedLazy = lazyA.flatMap(mapper);

      assertThat(counter.get()).isZero();
      assertThat(innerCounter.get()).isZero();
      assertThatThrownBy(flatMappedLazy::force)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Runtime Failure");
      assertThat(counter.get()).isEqualTo(1); // Original ran (and failed)
      assertThat(innerCounter.get()).isZero(); // Inner computation never started
    }

    @Test
    void flatMap_shouldPropagateFailureFromMapperFunction() {
      Lazy<String> lazyA = Lazy.defer(successSupplier());
      RuntimeException mapperEx = new IllegalArgumentException("Mapper failed");
      AtomicInteger innerCounter = new AtomicInteger(0);
      Function<String, Lazy<Integer>> mapper =
          s -> {
            innerCounter.incrementAndGet(); // Track if mapper starts
            throw mapperEx;
          };

      Lazy<Integer> flatMappedLazy = lazyA.flatMap(mapper);

      assertThat(counter.get()).isZero();
      assertThat(innerCounter.get()).isZero();
      assertThatThrownBy(flatMappedLazy::force)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Mapper failed");
      assertThat(counter.get()).isEqualTo(1); // Original ran
      assertThat(innerCounter.get()).isEqualTo(1); // Mapper started (and failed)
    }

    @Test
    void flatMap_shouldPropagateFailureFromResultingLazy() {
      Lazy<String> lazyA = Lazy.defer(successSupplier());
      RuntimeException resultEx = new UnsupportedOperationException("Result Lazy failed");
      AtomicInteger innerCounter = new AtomicInteger(0);
      Function<String, Lazy<Integer>> mapper =
          s ->
              Lazy.defer(
                  () -> {
                    innerCounter.incrementAndGet();
                    throw resultEx;
                  }); // Lazy returned by mapper fails

      Lazy<Integer> flatMappedLazy = lazyA.flatMap(mapper);

      assertThat(counter.get()).isZero();
      assertThat(innerCounter.get()).isZero();
      assertThatThrownBy(flatMappedLazy::force)
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessage("Result Lazy failed");
      assertThat(counter.get()).isEqualTo(1); // Original ran
      assertThat(innerCounter.get()).isEqualTo(1); // Inner ran (and failed)
    }

    @Test
    void flatMap_shouldThrowNPEForNullMapper() {
      Lazy<String> lazy = Lazy.defer(successSupplier());
      assertThatNullPointerException()
          .isThrownBy(() -> lazy.flatMap(null))
          .withMessageContaining("flatMap mapper function");
    }

    @Test
    void flatMap_shouldThrowNPEIfMapperReturnsNull() {
      Lazy<String> lazy = Lazy.defer(successSupplier());
      Function<String, Lazy<Integer>> nullReturningMapper = s -> null;
      Lazy<Integer> flatMappedLazy = lazy.flatMap(nullReturningMapper);

      assertThatNullPointerException()
          .isThrownBy(flatMappedLazy::force) // Exception happens when forcing
          .withMessageContaining("flatMap function returned null Lazy");
    }
  }

  @Nested
  @DisplayName("toString() Method")
  class ToStringTests {
    @Test
    void toString_shouldNotForceEvaluationForDeferred() {
      Lazy<String> lazy = Lazy.defer(successSupplier());
      assertThat(lazy.toString()).isEqualTo("Lazy[unevaluated...]");
      assertThat(counter.get()).isZero();
    }

    @Test
    void toString_shouldShowValueForNow() {
      Lazy<String> lazy = Lazy.now("Ready");
      assertThat(lazy.toString()).isEqualTo("Lazy[Ready]");
    }

    @Test
    void toString_shouldShowValueAfterForceSuccess() {
      Lazy<String> lazy = Lazy.defer(successSupplier());
      lazy.force(); // Evaluate success
      assertThat(lazy.toString()).isEqualTo("Lazy[SuccessValue]");
    }

    @Test
    void toString_shouldShowNullValueAfterForce() {
      Lazy<String> lazy = Lazy.defer(nullSupplier());
      lazy.force(); // Evaluate null
      assertThat(lazy.toString()).isEqualTo("Lazy[null]");
    }

    @Test
    void toString_shouldShowFailureStateCorrectly() {
      Lazy<String> lazyFailRuntime = Lazy.defer(runtimeFailSupplier());
      Lazy<String> lazyFailError = Lazy.defer(errorFailSupplier());
      Lazy<String> lazyFailChecked = Lazy.defer(checkedFailSupplier());

      // Force evaluation to cache the exception
      catchThrowable(lazyFailRuntime::force);
      catchThrowable(lazyFailError::force);
      catchThrowable(lazyFailChecked::force);

      // Verify toString shows the correct failure state
      assertThat(lazyFailRuntime.toString()).isEqualTo("Lazy[failed: IllegalStateException]");
      assertThat(lazyFailError.toString()).isEqualTo("Lazy[failed: StackOverflowError]");
      // The checked exception was wrapped in RuntimeException by the supplier
      assertThat(lazyFailChecked.toString()).isEqualTo("Lazy[failed: RuntimeException]");
    }
  }

  // Basic equals/hashCode tests - Lazy doesn't override them, relies on object identity
  @Nested
  @DisplayName("equals() and hashCode()")
  class EqualsHashCodeTests {
    @Test
    void equals_shouldUseReferenceEquality() {
      Lazy<String> lazy1a = Lazy.defer(() -> "a");
      Lazy<String> lazy1b = Lazy.defer(() -> "a"); // Different instance, same logic
      Lazy<String> lazy2 = Lazy.now("a");
      Lazy<String> lazy1aRef = lazy1a;

      assertThat(lazy1a).isEqualTo(lazy1aRef);
      assertThat(lazy1a).isNotEqualTo(lazy1b);
      assertThat(lazy1a).isNotEqualTo(lazy2);
      assertThat(lazy1a).isNotEqualTo(null);
      assertThat(lazy1a).isNotEqualTo("a");

      // Force evaluation - should still be reference equality
      lazy1a.force();
      lazy1b.force();
      assertThat(lazy1a).isNotEqualTo(lazy1b);
    }

    @Test
    void hashCode_shouldUseReferenceHashCode() {
      Lazy<String> lazy1a = Lazy.defer(() -> "a");
      Lazy<String> lazy1b = Lazy.defer(() -> "a");
      Lazy<String> lazy1aRef = lazy1a;

      assertThat(lazy1a.hashCode()).isEqualTo(lazy1aRef.hashCode());
      // Hashcodes of lazy1a and lazy1b are not guaranteed to be different or same
    }
  }
}
