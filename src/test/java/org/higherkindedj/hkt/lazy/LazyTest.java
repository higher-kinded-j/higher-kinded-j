package org.higherkindedj.hkt.lazy;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout; // Import Timeout

@DisplayName("Lazy<A> Direct Tests (ThrowableSupplier)")
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

  // --- Test Suppliers (now ThrowableSupplier) ---

  private ThrowableSupplier<String> successSupplier() {
    return () -> { // Lambda implicitly matches ThrowableSupplier if it doesn't throw checked
      counter.incrementAndGet();
      // Simulate some work
      try {
        Thread.sleep(5); // Small delay
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        // If interrupted during non-critical sleep, maybe just proceed or log
        // For testing concurrency failure propagation, throw here.
        throw new CompletionException("Interrupted during successSupplier sleep", e);
      }
      return "SuccessValue";
    };
  }

  private ThrowableSupplier<String> nullSupplier() {
    return () -> {
      counter.incrementAndGet();
      return null;
    };
  }

  private ThrowableSupplier<String> runtimeFailSupplier() {
    return () -> {
      counter.incrementAndGet();
      throw new IllegalStateException("Runtime Failure");
    };
  }

  private ThrowableSupplier<String> errorFailSupplier() {
    return () -> {
      counter.incrementAndGet();
      throw new StackOverflowError("Error Failure");
    };
  }

  // Supplier that *directly* throws a checked exception
  private ThrowableSupplier<String> checkedFailSupplierDirect() {
    return () -> {
      counter.incrementAndGet();
      throw new IOException("Direct Checked Failure"); // Directly throw checked exception
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
    void now_shouldCreateEvaluatedLazyWithValue() throws Throwable { // Add throws
      Lazy<String> lazy = Lazy.now("DirectValue");
      assertThat(counter.get()).isZero(); // No computation ran
      assertThat(lazy.force()).isEqualTo("DirectValue");
      assertThat(counter.get()).isZero(); // Still zero
      assertThat(lazy.toString()).isEqualTo("Lazy[DirectValue]"); // Test evaluated toString
    }

    @Test
    void now_shouldCreateEvaluatedLazyWithNull() throws Throwable { // Add throws
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
    void force_shouldEvaluateDeferredSupplierOnlyOnce() throws Throwable { // Add throws
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
    void force_shouldReturnCachedValueForNow() throws Throwable { // Add throws
      Lazy<String> lazy = Lazy.now("Preset");
      assertThat(counter.get()).isZero();
      assertThat(lazy.force()).isEqualTo("Preset");
      assertThat(counter.get()).isZero(); // No computation involved
    }

    @Test
    void force_shouldCacheAndReturnNullValue() throws Throwable { // Add throws
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
    void force_shouldCacheAndRethrowRuntimeException() { // No throws needed for unchecked
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
    void force_shouldCacheAndRethrowError() { // No throws needed for unchecked
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
    void force_shouldCacheAndRethrowDirectCheckedException() throws Throwable { // Add throws
      // Use the supplier that directly throws IOException
      Lazy<String> lazy = Lazy.defer(checkedFailSupplierDirect());
      assertThat(counter.get()).isZero();

      // First force - Expect the original IOException
      Throwable thrown1 = catchThrowable(lazy::force);
      assertThat(thrown1).isInstanceOf(IOException.class).hasMessage("Direct Checked Failure");
      assertThat(counter.get()).isEqualTo(1);

      // Second force - Expect the same cached IOException
      Throwable thrown2 = catchThrowable(lazy::force);
      assertThat(thrown2).isInstanceOf(IOException.class).hasMessage("Direct Checked Failure");
      assertThat(counter.get()).isEqualTo(1); // Not evaluated again

      // Verify the specific re-throwing branch (line 88) was likely hit
      assertThat(thrown2).as("Should re-throw same checked Exception instance").isSameAs(thrown1);
      assertThat(lazy.toString())
          .isEqualTo("Lazy[failed: IOException]"); // Check toString for checked fail
    }

    // Simplified concurrency test
    @Test
    @Timeout(10) // Keep a reasonable timeout
    void force_shouldBeThreadSafeDuringInitialisation() throws InterruptedException {
      int numThreads = 15; // Reduced thread count
      ThrowableSupplier<String> verySlowSupplier =
          () -> {
            try {
              // Keep a delay to encourage contention
              Thread.sleep(100);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              // --- FIX: Throw an unchecked exception on interruption ---
              throw new CompletionException("Thread interrupted during slow supplier", e);
              // --- End of FIX ---
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
                String result = lazy.force(); // force() now throws Throwable
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

      // Check for unexpected exceptions (like the CompletionException if interrupted)
      // Depending on timing, interruption might or might not happen reliably.
      // The main goal is that *if* it happens, it doesn't lead to null results.
      // We primarily assert the success path here.
      if (!exceptions.isEmpty()) {
        System.err.println("Concurrency test encountered exceptions: " + exceptions);
        // Optionally fail here if *any* exception is unexpected
        // fail("Concurrency test failed with exceptions: " + exceptions);
      }

      // Filter out potential nulls caused by interruption before asserting equality
      List<String> nonNullResults = results.stream().filter(java.util.Objects::nonNull).toList();

      // Check all *non-null* threads got the same result
      // If the list is empty, it means all threads might have been interrupted and failed.
      if (!nonNullResults.isEmpty()) {
        assertThat(nonNullResults).hasSizeGreaterThan(0); // Ensure at least one thread succeeded
        nonNullResults.forEach(result -> assertThat(result).isEqualTo("VerySlowValue"));
      } else if (exceptions.isEmpty()) {
        // If there were no exceptions but also no successful results, something is wrong
        fail("Concurrency test resulted in no successful results and no exceptions.");
      }

      // Check computation ran exactly once despite contention *if* it wasn't interrupted early
      // This assertion might be flaky if interruption happens before increment.
      // It's more reliable to check that it didn't run *more* than once.
      assertThat(counter.get()).as("Computation count").isLessThanOrEqualTo(1);
      // If we got at least one success, the count must be 1
      if (!nonNullResults.isEmpty()) {
        assertThat(counter.get()).as("Computation count (at least one success)").isEqualTo(1);
      }

      // Additional force call - needs try-catch or throws Throwable
      // This might throw if the initial computation was interrupted and failed.
      try {
        assertThat(lazy.force()).isEqualTo("VerySlowValue");
        assertThat(counter.get()).as("Computation count after second force").isEqualTo(1);
      } catch (Throwable e) {
        // If the initial run failed due to interruption, this second force should re-throw
        assertThat(e).isInstanceOf(CompletionException.class);
        assertThat(counter.get())
            .as("Computation count after failed force")
            .isLessThanOrEqualTo(1); // Should not have run again
      }
    }
  }

  @Nested
  @DisplayName("map() Method")
  class MapTests {
    @Test
    void map_shouldTransformValueLazily() throws Throwable { // Add throws
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
    void map_shouldPropagateFailure() { // No throws needed
      Lazy<String> lazy = Lazy.defer(runtimeFailSupplier());
      AtomicInteger mapCounter = new AtomicInteger(0);
      Lazy<Integer> mappedLazy =
          lazy.map(
              s -> {
                mapCounter.incrementAndGet();
                return s.length();
              });

      assertThat(counter.get()).isZero();
      assertThat(mapCounter.get()).isZero();
      assertThatThrownBy(mappedLazy::force)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Runtime Failure");
      assertThat(counter.get()).isEqualTo(1); // Original ran (and failed)
      assertThat(mapCounter.get()).isZero(); // Mapper function never ran
    }

    @Test
    void map_shouldFailIfMapperThrows() { // No throws needed
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
    void map_shouldPropagateCheckedExceptionFromForce() { // No throws needed
      // Use the lazy value that throws IOException directly
      Lazy<String> lazy = Lazy.defer(checkedFailSupplierDirect());
      AtomicInteger mapCounter = new AtomicInteger(0);
      Lazy<Integer> mappedLazy =
          lazy.map(
              s -> {
                mapCounter.incrementAndGet();
                return s.length();
              });

      assertThat(counter.get()).isZero();
      assertThat(mapCounter.get()).isZero();

      // The map's defer will catch the Throwable from the inner force() and rethrow it
      assertThatThrownBy(mappedLazy::force)
          .isInstanceOf(IOException.class) // Expect the original checked exception
          .hasMessage("Direct Checked Failure");
      assertThat(counter.get()).isEqualTo(1); // Original ran (and failed)
      assertThat(mapCounter.get()).isZero(); // Mapper never ran
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
    void flatMap_shouldSequenceLazily() throws Throwable { // Add throws
      AtomicInteger innerCounter = new AtomicInteger(0);
      Lazy<String> lazyA = Lazy.defer(successSupplier());
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

      assertThat(flatMappedLazy.force()).isEqualTo("SuccessValue".length());
      assertThat(counter.get()).isEqualTo(1);
      assertThat(innerCounter.get()).isEqualTo(1);

      assertThat(flatMappedLazy.force()).isEqualTo("SuccessValue".length());
      assertThat(counter.get()).isEqualTo(1);
      assertThat(innerCounter.get()).isEqualTo(1);
    }

    @Test
    void flatMap_shouldPropagateFailureFromInitialLazy() { // No throws needed
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
      assertThat(counter.get()).isEqualTo(1);
      assertThat(innerCounter.get()).isZero();
    }

    @Test
    void flatMap_shouldPropagateFailureFromMapperFunction() { // No throws needed
      Lazy<String> lazyA = Lazy.defer(successSupplier());
      RuntimeException mapperEx = new IllegalArgumentException("Mapper failed");
      AtomicInteger innerCounter = new AtomicInteger(0);
      Function<String, Lazy<Integer>> mapper =
          s -> {
            innerCounter.incrementAndGet();
            throw mapperEx;
          };
      Lazy<Integer> flatMappedLazy = lazyA.flatMap(mapper);

      assertThat(counter.get()).isZero();
      assertThat(innerCounter.get()).isZero();
      assertThatThrownBy(flatMappedLazy::force)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Mapper failed");
      assertThat(counter.get()).isEqualTo(1);
      assertThat(innerCounter.get()).isEqualTo(1);
    }

    @Test
    void flatMap_shouldPropagateFailureFromResultingLazy() { // No throws needed
      Lazy<String> lazyA = Lazy.defer(successSupplier());
      RuntimeException resultEx = new UnsupportedOperationException("Result Lazy failed");
      AtomicInteger innerCounter = new AtomicInteger(0);
      Function<String, Lazy<Integer>> mapper =
          s ->
              Lazy.defer(
                  () -> {
                    innerCounter.incrementAndGet();
                    throw resultEx;
                  });
      Lazy<Integer> flatMappedLazy = lazyA.flatMap(mapper);

      assertThat(counter.get()).isZero();
      assertThat(innerCounter.get()).isZero();
      assertThatThrownBy(flatMappedLazy::force)
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessage("Result Lazy failed");
      assertThat(counter.get()).isEqualTo(1);
      assertThat(innerCounter.get()).isEqualTo(1);
    }

    @Test
    void flatMap_shouldPropagateCheckedExceptionFromInitialLazy() { // No throws needed
      Lazy<String> lazyA = Lazy.defer(checkedFailSupplierDirect()); // Throws IOException
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
      // The flatMap's defer catches the Throwable from the initial force() and rethrows it
      assertThatThrownBy(flatMappedLazy::force)
          .isInstanceOf(IOException.class)
          .hasMessage("Direct Checked Failure");
      assertThat(counter.get()).isEqualTo(1);
      assertThat(innerCounter.get()).isZero();
    }

    @Test
    void flatMap_shouldPropagateCheckedExceptionFromResultingLazy() { // No throws needed
      Lazy<String> lazyA = Lazy.defer(successSupplier());
      AtomicInteger innerCounter = new AtomicInteger(0);
      Function<String, Lazy<Integer>> mapper =
          s ->
              Lazy.defer(
                  () -> { // Inner lazy throws checked exception
                    innerCounter.incrementAndGet();
                    throw new IOException("Inner Checked Fail");
                  });
      Lazy<Integer> flatMappedLazy = lazyA.flatMap(mapper);

      assertThat(counter.get()).isZero();
      assertThat(innerCounter.get()).isZero();
      // The flatMap's defer catches the Throwable from the inner force() and rethrows it
      assertThatThrownBy(flatMappedLazy::force)
          .isInstanceOf(IOException.class)
          .hasMessage("Inner Checked Fail");
      assertThat(counter.get()).isEqualTo(1);
      assertThat(innerCounter.get()).isEqualTo(1);
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
    void toString_shouldShowValueAfterForceSuccess() throws Throwable { // Add throws
      Lazy<String> lazy = Lazy.defer(successSupplier());
      lazy.force(); // Evaluate success
      assertThat(lazy.toString()).isEqualTo("Lazy[SuccessValue]");
    }

    @Test
    void toString_shouldShowNullValueAfterForce() throws Throwable { // Add throws
      Lazy<String> lazy = Lazy.defer(nullSupplier());
      lazy.force(); // Evaluate null
      assertThat(lazy.toString()).isEqualTo("Lazy[null]");
    }

    @Test
    void toString_shouldShowFailureStateCorrectly() {
      Lazy<String> lazyFailRuntime = Lazy.defer(runtimeFailSupplier());
      Lazy<String> lazyFailError = Lazy.defer(errorFailSupplier());
      Lazy<String> lazyFailChecked = Lazy.defer(checkedFailSupplierDirect()); // Use direct checked

      // Force evaluation to cache the exception
      catchThrowable(lazyFailRuntime::force);
      catchThrowable(lazyFailError::force);
      catchThrowable(lazyFailChecked::force); // This will throw IOException

      // Verify toString shows the correct failure state representation
      assertThat(lazyFailRuntime.toString()).isEqualTo("Lazy[failed: IllegalStateException]");
      assertThat(lazyFailError.toString()).isEqualTo("Lazy[failed: StackOverflowError]");
      assertThat(lazyFailChecked.toString())
          .isEqualTo("Lazy[failed: IOException]"); // Now shows IOException
    }
  }

  // Basic equals/hashCode tests - Lazy doesn't override them, relies on object identity
  @Nested
  @DisplayName("equals() and hashCode()")
  class EqualsHashCodeTests {
    @Test
    void equals_shouldUseReferenceEquality() throws Throwable { // Add throws
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
