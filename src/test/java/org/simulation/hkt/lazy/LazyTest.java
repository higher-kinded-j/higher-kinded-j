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
import org.junit.jupiter.api.Timeout; // Import Timeout

/**
 * Contains direct unit tests for the {@link Lazy} class. These tests verify the core functionality
 * of lazy evaluation, memoization (caching of results and exceptions), factory methods, and basic
 * operations like map and flatMap directly on the Lazy object, independent of the HKT simulation
 * layer (Kind, Monad instance).
 */
@DisplayName("Lazy<A> Direct Tests")
class LazyTest {

  // Counter to track how many times a lazy computation's supplier is executed.
  private AtomicInteger counter;
  // Executor service for running concurrency tests.
  private ExecutorService executor;

  /** Initializes resources before each test method. */
  @BeforeEach
  void setUp() {
    counter = new AtomicInteger(0);
    // Use a fixed thread pool for more predictable concurrency tests
    executor = Executors.newFixedThreadPool(10);
  }

  /** Cleans up resources after each test method. */
  @AfterEach
  void tearDown() {
    // Shut down the executor after each test to release resources
    if (executor != null) {
      executor.shutdownNow(); // Attempt immediate shutdown
      try {
        // Wait a short time for termination
        if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
          System.err.println("Executor did not terminate in time.");
        }
      } catch (InterruptedException e) {
        // Preserve interrupt status
        Thread.currentThread().interrupt();
      }
    }
  }

  // --- Test Suppliers ---
  // These suppliers are used to create Lazy instances for testing different scenarios.
  // Each supplier increments the shared 'counter' when executed.

  /** Supplier that simulates a successful computation. */
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

  /** Supplier that simulates a successful computation returning null. */
  private Supplier<String> nullSupplier() {
    return () -> {
      counter.incrementAndGet();
      return null;
    };
  }

  /** Supplier that simulates a computation failing with a RuntimeException. */
  private Supplier<String> runtimeFailSupplier() {
    return () -> {
      counter.incrementAndGet();
      throw new IllegalStateException("Runtime Failure");
    };
  }

  /** Supplier that simulates a computation failing with an Error. */
  private Supplier<String> errorFailSupplier() {
    return () -> {
      counter.incrementAndGet();
      throw new StackOverflowError("Error Failure");
    };
  }

  /**
   * Supplier that simulates a computation failing with a checked exception. Note: Checked
   * exceptions must be wrapped in a RuntimeException to be thrown by a Supplier. Lazy.force() will
   * catch the wrapper and re-throw it.
   */
  private Supplier<String> checkedFailSupplier() {
    return () -> {
      counter.incrementAndGet();
      try {
        throw new IOException("Checked Failure");
      } catch (IOException e) {
        // Re-throw wrapped as Lazy.force catches Throwable and handles re-throwing
        throw new RuntimeException(e);
      }
    };
  }

  // --- Test Classes ---

  /** Tests for the static factory methods {@link Lazy#defer(Supplier)} and {@link Lazy#now(Object)}. */
  @Nested
  @DisplayName("Factory Methods (defer, now)")
  class FactoryTests {
    /** Verifies that Lazy.defer does not execute the supplier immediately. */
    @Test
    void defer_shouldNotEvaluateSupplierImmediately() {
      Lazy<String> lazy = Lazy.defer(successSupplier());
      // Check that the counter wasn't incremented upon creation
      assertThat(counter.get()).isZero();
      // Check the initial toString representation
      assertThat(lazy.toString()).isEqualTo("Lazy[unevaluated...]");
    }

    /** Verifies that Lazy.defer throws NullPointerException if the supplier is null. */
    @Test
    void defer_shouldThrowNPEForNullSupplier() {
      // This test covers the null check in the Lazy constructor (line 47)
      assertThatNullPointerException()
          .isThrownBy(() -> Lazy.defer(null))
          .withMessageContaining("computation");
    }

    /** Verifies that Lazy.now creates an already evaluated Lazy instance with the given value. */
    @Test
    void now_shouldCreateEvaluatedLazyWithValue() {
      Lazy<String> lazy = Lazy.now("DirectValue");
      assertThat(counter.get()).isZero(); // No computation should have run
      // force() should return the value immediately without computation
      assertThat(lazy.force()).isEqualTo("DirectValue");
      assertThat(counter.get()).isZero(); // Counter should remain zero
      // Check the toString representation for an evaluated Lazy
      assertThat(lazy.toString()).isEqualTo("Lazy[DirectValue]");
    }

    /** Verifies that Lazy.now correctly handles a null value. */
    @Test
    void now_shouldCreateEvaluatedLazyWithNull() {
      Lazy<String> lazy = Lazy.now(null);
      assertThat(counter.get()).isZero();
      assertThat(lazy.force()).isNull(); // force() should return null
      assertThat(counter.get()).isZero();
      assertThat(lazy.toString()).isEqualTo("Lazy[null]"); // toString should show null
    }
  }

  /** Tests for the {@link Lazy#force()} method, covering evaluation, memoization, and exception handling. */
  @Nested
  @DisplayName("force() Method")
  class ForceTests {
    /** Verifies that force() evaluates the supplier only on the first call and caches the result. */
    @Test
    void force_shouldEvaluateDeferredSupplierOnlyOnce() {
      Lazy<String> lazy = Lazy.defer(successSupplier());
      assertThat(counter.get()).isZero(); // Not evaluated initially

      // First force: evaluates the supplier
      assertThat(lazy.force()).isEqualTo("SuccessValue");
      assertThat(counter.get()).isEqualTo(1); // Counter incremented

      // Second force: returns the cached value
      assertThat(lazy.force()).isEqualTo("SuccessValue");
      assertThat(counter.get()).isEqualTo(1); // Counter NOT incremented again (memoization)
    }

    /** Verifies that force() on a Lazy created with now() returns the value without evaluation. */
    @Test
    void force_shouldReturnCachedValueForNow() {
      Lazy<String> lazy = Lazy.now("Preset");
      assertThat(counter.get()).isZero();
      assertThat(lazy.force()).isEqualTo("Preset");
      assertThat(counter.get()).isZero(); // No computation involved
    }

    /** Verifies that force() correctly caches and returns a null result. */
    @Test
    void force_shouldCacheAndReturnNullValue() {
      Lazy<String> lazy = Lazy.defer(nullSupplier());
      assertThat(counter.get()).isZero();

      // First force: evaluates to null
      assertThat(lazy.force()).isNull();
      assertThat(counter.get()).isEqualTo(1);

      // Second force: returns cached null
      assertThat(lazy.force()).isNull();
      assertThat(counter.get()).isEqualTo(1); // Not evaluated again
    }

    /** Verifies that force() caches and re-throws RuntimeExceptions from the supplier. */
    @Test
    void force_shouldCacheAndRethrowRuntimeException() {
      Lazy<String> lazy = Lazy.defer(runtimeFailSupplier());
      assertThat(counter.get()).isZero();

      // First force: expect exception
      Throwable thrown1 = catchThrowable(lazy::force);
      assertThat(thrown1).isInstanceOf(IllegalStateException.class).hasMessage("Runtime Failure");
      assertThat(counter.get()).isEqualTo(1); // Evaluated once

      // Second force: expect same cached exception
      Throwable thrown2 = catchThrowable(lazy::force);
      assertThat(thrown2).isInstanceOf(IllegalStateException.class).hasMessage("Runtime Failure");
      assertThat(counter.get()).isEqualTo(1); // Not evaluated again

      // Verify the specific re-throwing branch (line 84) was likely hit by checking instance equality
      assertThat(thrown2).isSameAs(thrown1);
    }

    /** Verifies that force() caches and re-throws Errors from the supplier. */
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

    /**
     * Verifies that force() caches and re-throws wrapped checked exceptions. Note: The supplier wraps
     * the checked exception in a RuntimeException. force() catches this wrapper.
     */
    @Test
    void force_shouldCacheAndRethrowWrappedCheckedException() {
      Lazy<String> lazy = Lazy.defer(checkedFailSupplier());
      assertThat(counter.get()).isZero();

      // First force - Expect the wrapper RuntimeException
      Throwable thrown1 = catchThrowable(lazy::force);
      assertThat(thrown1)
          .isInstanceOf(RuntimeException.class) // The wrapper caught by force()
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

      // Verify the specific re-throwing branch (line 84) was likely hit
      assertThat(thrown2).isSameAs(thrown1); // Should re-throw the exact same exception instance

      // Note: Testing line 88 (re-throwing non-Runtime/non-Error) directly is difficult
      // because Supplier.get() doesn't allow throwing checked exceptions directly.
      // This test ensures the RuntimeException wrapping a checked exception is handled.
    }

    /**
     * Basic test for thread safety during the initial evaluation of force(). Uses multiple threads
     * calling force() concurrently on the same Lazy instance. Verifies that the underlying supplier
     * is executed exactly once and all threads receive the same result.
     */
    @Test
    @Timeout(10) // Add timeout to prevent potential deadlocks or excessive waits
    void force_shouldBeThreadSafeDuringInitialization() throws InterruptedException {
      int numThreads = 15; // Number of concurrent threads
      Supplier<String> verySlowSupplier =
          () -> {
            try {
              // Delay to increase the chance of threads contending for the lock
              Thread.sleep(100);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            counter.incrementAndGet(); // Track actual executions
            return "VerySlowValue";
          };
      Lazy<String> lazy = Lazy.defer(verySlowSupplier);
      counter.set(0);
      // Use synchronized lists to collect results/exceptions from threads
      List<String> results = Collections.synchronizedList(new ArrayList<>());
      List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());
      // Use a latch to wait for all threads to complete
      CountDownLatch finishLatch = new CountDownLatch(numThreads);

      // Submit tasks to the executor pool
      for (int i = 0; i < numThreads; i++) {
        executor.submit(
            () -> {
              try {
                // Each thread calls force() on the *same* lazy instance
                String result = lazy.force();
                results.add(result);
              } catch (Throwable e) {
                exceptions.add(e); // Record any exceptions
              } finally {
                finishLatch.countDown(); // Signal this thread is done
              }
            });
      }

      // Wait for all threads to finish within the timeout
      assertThat(finishLatch.await(10, TimeUnit.SECONDS))
          .as("Threads did not finish in time")
          .isTrue();

      // Assertions after all threads complete:
      assertThat(exceptions).as("Unexpected exceptions during concurrent force()").isEmpty();
      assertThat(results).as("Number of results collected").hasSize(numThreads);
      // Check that all threads received the same correct result
      results.forEach(result -> assertThat(result).isEqualTo("VerySlowValue"));
      // CRITICAL: Check that the supplier was executed exactly once
      assertThat(counter.get()).as("Computation count").isEqualTo(1);

      // Perform an additional force call after concurrency to ensure memoization still holds
      assertThat(lazy.force()).isEqualTo("VerySlowValue");
      assertThat(counter.get()).as("Computation count after second force").isEqualTo(1);
    }
  }

  /** Tests for the {@link Lazy#map(Function)} method. */
  @Nested
  @DisplayName("map() Method")
  class MapTests {
    /** Verifies that map applies the function lazily and the result is memoized. */
    @Test
    void map_shouldTransformValueLazily() {
      Lazy<String> lazy = Lazy.defer(successSupplier());
      AtomicInteger mapCounter = new AtomicInteger(0); // Counter for the mapping function
      Function<String, Integer> mapper =
          s -> {
            mapCounter.incrementAndGet();
            return s.length();
          };
      Lazy<Integer> mappedLazy = lazy.map(mapper);

      // Verify nothing has been evaluated yet
      assertThat(counter.get()).isZero();
      assertThat(mapCounter.get()).isZero();

      // Force the mapped lazy value
      assertThat(mappedLazy.force()).isEqualTo("SuccessValue".length());
      // Verify original supplier and mapper ran once
      assertThat(counter.get()).isEqualTo(1);
      assertThat(mapCounter.get()).isEqualTo(1);

      // Force again to check memoization
      assertThat(mappedLazy.force()).isEqualTo("SuccessValue".length());
      // Verify counters did not increment again
      assertThat(counter.get()).isEqualTo(1);
      assertThat(mapCounter.get()).isEqualTo(1);
    }

    /** Verifies that map propagates failures from the original Lazy instance. */
    @Test
    void map_shouldPropagateFailure() {
      Lazy<String> lazy = Lazy.defer(runtimeFailSupplier()); // This Lazy will fail
      AtomicInteger mapCounter = new AtomicInteger(0);
      Lazy<Integer> mappedLazy = lazy.map(s -> { mapCounter.incrementAndGet(); return s.length(); });

      assertThat(counter.get()).isZero();
      assertThat(mapCounter.get()).isZero();
      // Forcing the mapped Lazy should trigger the original failure
      assertThatThrownBy(mappedLazy::force)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Runtime Failure");
      // Verify original supplier ran (and failed), but mapper did not
      assertThat(counter.get()).isEqualTo(1);
      assertThat(mapCounter.get()).isZero();
    }

    /** Verifies that map correctly handles exceptions thrown by the mapping function itself. */
    @Test
    void map_shouldFailIfMapperThrows() {
      Lazy<String> lazy = Lazy.defer(successSupplier()); // Original Lazy succeeds
      RuntimeException mapperEx = new IllegalArgumentException("Mapper failed");
      AtomicInteger mapCounter = new AtomicInteger(0);
      Lazy<Integer> mappedLazy =
          lazy.map(
              s -> {
                mapCounter.incrementAndGet();
                throw mapperEx; // Mapper function throws
              });

      assertThat(counter.get()).isZero();
      assertThat(mapCounter.get()).isZero();
      // Forcing should trigger original evaluation, then the mapper failure
      assertThatThrownBy(mappedLazy::force)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Mapper failed");
      // Verify original supplier ran, and mapper ran (and failed)
      assertThat(counter.get()).isEqualTo(1);
      assertThat(mapCounter.get()).isEqualTo(1);
    }

    /** Verifies that map throws NullPointerException if the mapping function is null. */
    @Test
    void map_shouldThrowNPEForNullMapper() {
      Lazy<String> lazy = Lazy.defer(successSupplier());
      assertThatNullPointerException()
          .isThrownBy(() -> lazy.map(null))
          .withMessageContaining("mapper function");
    }
  }

  /** Tests for the {@link Lazy#flatMap(Function)} method. */
  @Nested
  @DisplayName("flatMap() Method")
  class FlatMapTests {
    /** Verifies that flatMap sequences lazy computations correctly and memoizes the final result. */
    @Test
    void flatMap_shouldSequenceLazily() {
      AtomicInteger innerCounter = new AtomicInteger(0);
      Lazy<String> lazyA = Lazy.defer(successSupplier()); // Lazy -> "SuccessValue"
      // Function that takes String, returns Lazy<Integer>
      Function<String, Lazy<Integer>> mapper =
          s ->
              Lazy.defer(
                  () -> { // Creates a new lazy computation for length
                    innerCounter.incrementAndGet();
                    return s.length();
                  });

      Lazy<Integer> flatMappedLazy = lazyA.flatMap(mapper);

      // Verify nothing evaluated yet
      assertThat(counter.get()).isZero();
      assertThat(innerCounter.get()).isZero();

      // Force the final result
      assertThat(flatMappedLazy.force()).isEqualTo("SuccessValue".length());
      // Verify original supplier ran, and the inner supplier (from mapper) ran
      assertThat(counter.get()).isEqualTo(1);
      assertThat(innerCounter.get()).isEqualTo(1);

      // Force again to check memoization
      assertThat(flatMappedLazy.force()).isEqualTo("SuccessValue".length());
      // Verify counters did not increment again
      assertThat(counter.get()).isEqualTo(1);
      assertThat(innerCounter.get()).isEqualTo(1);
    }

    /** Verifies that flatMap propagates failures from the initial Lazy instance. */
    @Test
    void flatMap_shouldPropagateFailureFromInitialLazy() {
      Lazy<String> lazyA = Lazy.defer(runtimeFailSupplier()); // This one fails
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
      // Forcing should trigger the initial failure
      assertThatThrownBy(flatMappedLazy::force)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Runtime Failure");
      // Verify original supplier ran (and failed), inner one did not
      assertThat(counter.get()).isEqualTo(1);
      assertThat(innerCounter.get()).isZero();
    }

    /** Verifies that flatMap propagates failures from the mapping function itself. */
    @Test
    void flatMap_shouldPropagateFailureFromMapperFunction() {
      Lazy<String> lazyA = Lazy.defer(successSupplier()); // This one succeeds
      RuntimeException mapperEx = new IllegalArgumentException("Mapper failed");
      AtomicInteger innerCounter = new AtomicInteger(0);
      Function<String, Lazy<Integer>> mapper =
          s -> {
            innerCounter.incrementAndGet(); // Track if mapper starts
            throw mapperEx; // Mapper function throws
          };

      Lazy<Integer> flatMappedLazy = lazyA.flatMap(mapper);

      assertThat(counter.get()).isZero();
      assertThat(innerCounter.get()).isZero();
      // Forcing should trigger original success, then mapper failure
      assertThatThrownBy(flatMappedLazy::force)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Mapper failed");
      // Verify original supplier ran, and mapper function started (and failed)
      assertThat(counter.get()).isEqualTo(1);
      assertThat(innerCounter.get()).isEqualTo(1);
    }

    /** Verifies that flatMap propagates failures from the Lazy instance returned by the mapper. */
    @Test
    void flatMap_shouldPropagateFailureFromResultingLazy() {
      Lazy<String> lazyA = Lazy.defer(successSupplier()); // This one succeeds
      RuntimeException resultEx = new UnsupportedOperationException("Result Lazy failed");
      AtomicInteger innerCounter = new AtomicInteger(0);
      Function<String, Lazy<Integer>> mapper =
          s ->
              Lazy.defer(
                  () -> { // This inner Lazy fails when forced
                    innerCounter.incrementAndGet();
                    throw resultEx;
                  });

      Lazy<Integer> flatMappedLazy = lazyA.flatMap(mapper);

      assertThat(counter.get()).isZero();
      assertThat(innerCounter.get()).isZero();
      // Forcing should trigger original success, then inner failure
      assertThatThrownBy(flatMappedLazy::force)
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessage("Result Lazy failed");
      // Verify original supplier ran, and inner supplier ran (and failed)
      assertThat(counter.get()).isEqualTo(1);
      assertThat(innerCounter.get()).isEqualTo(1);
    }

    /** Verifies flatMap throws NullPointerException if the mapping function is null. */
    @Test
    void flatMap_shouldThrowNPEForNullMapper() {
      Lazy<String> lazy = Lazy.defer(successSupplier());
      assertThatNullPointerException()
          .isThrownBy(() -> lazy.flatMap(null))
          .withMessageContaining("flatMap mapper function");
    }

    /** Verifies flatMap throws NullPointerException if the mapping function returns null. */
    @Test
    void flatMap_shouldThrowNPEIfMapperReturnsNull() {
      Lazy<String> lazy = Lazy.defer(successSupplier());
      Function<String, Lazy<Integer>> nullReturningMapper = s -> null;
      Lazy<Integer> flatMappedLazy = lazy.flatMap(nullReturningMapper);

      // The exception occurs when the flatMappedLazy is forced
      assertThatNullPointerException()
          .isThrownBy(flatMappedLazy::force)
          .withMessageContaining("flatMap function returned null Lazy");
    }
  }

  /** Tests for the {@link Lazy#toString()} method. */
  @Nested
  @DisplayName("toString() Method")
  class ToStringTests {
    /** Verifies toString() on an unevaluated Lazy does not trigger evaluation. */
    @Test
    void toString_shouldNotForceEvaluationForDeferred() {
      Lazy<String> lazy = Lazy.defer(successSupplier());
      assertThat(lazy.toString()).isEqualTo("Lazy[unevaluated...]");
      assertThat(counter.get()).isZero(); // Counter should remain zero
    }

    /** Verifies toString() on a Lazy created with now() shows the value. */
    @Test
    void toString_shouldShowValueForNow() {
      Lazy<String> lazy = Lazy.now("Ready");
      assertThat(lazy.toString()).isEqualTo("Lazy[Ready]");
    }

    /** Verifies toString() shows the computed value after a successful force(). */
    @Test
    void toString_shouldShowValueAfterForceSuccess() {
      Lazy<String> lazy = Lazy.defer(successSupplier());
      lazy.force(); // Evaluate successfully
      assertThat(lazy.toString()).isEqualTo("Lazy[SuccessValue]");
    }

    /** Verifies toString() shows "null" after forcing a Lazy that computes null. */
    @Test
    void toString_shouldShowNullValueAfterForce() {
      Lazy<String> lazy = Lazy.defer(nullSupplier());
      lazy.force(); // Evaluate to null
      assertThat(lazy.toString()).isEqualTo("Lazy[null]");
    }

    /** Verifies toString() shows the correct failure state after force() catches an exception. */
    @Test
    void toString_shouldShowFailureStateCorrectly() {
      Lazy<String> lazyFailRuntime = Lazy.defer(runtimeFailSupplier());
      Lazy<String> lazyFailError = Lazy.defer(errorFailSupplier());
      Lazy<String> lazyFailChecked = Lazy.defer(checkedFailSupplier());

      // Force evaluation to cache the exception
      catchThrowable(lazyFailRuntime::force);
      catchThrowable(lazyFailError::force);
      catchThrowable(lazyFailChecked::force);

      // Verify toString shows the correct failure state representation
      assertThat(lazyFailRuntime.toString()).isEqualTo("Lazy[failed: IllegalStateException]");
      assertThat(lazyFailError.toString()).isEqualTo("Lazy[failed: StackOverflowError]");
      // The checked exception was wrapped in RuntimeException by the supplier
      assertThat(lazyFailChecked.toString()).isEqualTo("Lazy[failed: RuntimeException]");
    }
  }

  /** Tests for equals() and hashCode() which rely on default object identity. */
  @Nested
  @DisplayName("equals() and hashCode()")
  class EqualsHashCodeTests {
    /** Verifies that equals() uses reference equality, not value equality. */
    @Test
    void equals_shouldUseReferenceEquality() {
      Lazy<String> lazy1a = Lazy.defer(() -> "a");
      Lazy<String> lazy1b = Lazy.defer(() -> "a"); // Different instance, same logic
      Lazy<String> lazy2 = Lazy.now("a");
      Lazy<String> lazy1aRef = lazy1a; // Same instance

      assertThat(lazy1a).isEqualTo(lazy1aRef); // Equal to self
      assertThat(lazy1a).isNotEqualTo(lazy1b); // Not equal to different instance
      assertThat(lazy1a).isNotEqualTo(lazy2); // Not equal to 'now' instance
      assertThat(lazy1a).isNotEqualTo(null); // Not equal to null
      assertThat(lazy1a).isNotEqualTo("a"); // Not equal to different type

      // Force evaluation - should still be reference equality
      lazy1a.force();
      lazy1b.force();
      assertThat(lazy1a).isNotEqualTo(lazy1b);
    }

    /** Verifies that hashCode() uses the default object hash code. */
    @Test
    void hashCode_shouldUseReferenceHashCode() {
      Lazy<String> lazy1a = Lazy.defer(() -> "a");
      Lazy<String> lazy1b = Lazy.defer(() -> "a");
      Lazy<String> lazy1aRef = lazy1a;

      assertThat(lazy1a.hashCode()).isEqualTo(lazy1aRef.hashCode());
      // Hashcodes of lazy1a and lazy1b are not guaranteed to be different or same
      // No assertion here about lazy1a.hashCode() vs lazy1b.hashCode()
    }
  }
}
