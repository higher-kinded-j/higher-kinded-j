// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test suite for IOPath resource management and parallel execution features.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>Resource management: bracket, withResource, guarantee
 *   <li>Parallel execution: parZipWith, race
 *   <li>PathOps parallel utilities: parSequenceIO, parZip3, parZip4, raceIO
 * </ul>
 */
@DisplayName("IOPath Resource Management and Parallel Execution Tests")
class IOPathResourceAndParallelTest {

  /** One of the four methods that run a cleanup after a use, building a path from the two. */
  record CleanedUse(String name, BiFunction<Supplier<String>, Runnable, IOPath<String>> build) {
    @Override
    public String toString() {
      return name;
    }
  }

  static Stream<CleanedUse> cleanedUses() {
    return Stream.of(
        new CleanedUse(
            "bracket",
            (use, cleanup) -> IOPath.bracket(() -> "r", _ -> use.get(), _ -> cleanup.run())),
        new CleanedUse(
            "bracketIO",
            (use, cleanup) -> IOPath.bracketIO(() -> "r", _ -> Path.io(use), _ -> cleanup.run())),
        new CleanedUse("guarantee", (use, cleanup) -> Path.io(use).guarantee(cleanup)),
        new CleanedUse(
            "guaranteeIO", (use, cleanup) -> Path.io(use).guaranteeIO(Path.ioRunnable(cleanup))));
  }

  /** One of the two methods that close an AutoCloseable after a use. */
  record ClosingUse(
      String name, BiFunction<AutoCloseable, Supplier<String>, IOPath<String>> build) {
    @Override
    public String toString() {
      return name;
    }
  }

  static Stream<ClosingUse> closingUses() {
    return Stream.of(
        new ClosingUse(
            "withResource", (resource, use) -> IOPath.withResource(() -> resource, _ -> use.get())),
        new ClosingUse(
            "withResourceIO",
            (resource, use) -> IOPath.withResourceIO(() -> resource, _ -> Path.io(use))));
  }

  // ===== Resource Management Tests =====

  @Nested
  @DisplayName("bracket() Pattern")
  class BracketTests {

    @Test
    @DisplayName("bracket() acquires, uses, and releases resource")
    void bracketAcquiresUsesAndReleasesResource() {
      AtomicBoolean acquired = new AtomicBoolean(false);
      AtomicBoolean used = new AtomicBoolean(false);
      AtomicBoolean released = new AtomicBoolean(false);

      IOPath<String> path =
          IOPath.bracket(
              () -> {
                acquired.set(true);
                return "resource";
              },
              resource -> {
                used.set(true);
                return "used: " + resource;
              },
              _ -> released.set(true));

      // Nothing executed yet
      assertThat(acquired).isFalse();

      String result = path.unsafeRun();

      assertThat(result).isEqualTo("used: resource");
      assertThat(acquired).isTrue();
      assertThat(used).isTrue();
      assertThat(released).isTrue();
    }

    @Test
    @DisplayName("bracket() releases resource even when use throws")
    void bracketReleasesResourceEvenWhenUseThrows() {
      AtomicBoolean released = new AtomicBoolean(false);

      IOPath<String> path =
          IOPath.bracket(
              () -> "resource",
              _ -> {
                throw new RuntimeException("Use failed");
              },
              _ -> released.set(true));

      assertThatThrownBy(path::unsafeRun)
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Use failed");

      assertThat(released).isTrue();
    }

    @Test
    @DisplayName("bracket() validates null arguments")
    void bracketValidatesNullArguments() {
      assertThatNullPointerException()
          .isThrownBy(() -> IOPath.bracket(null, r -> r, _ -> {}))
          .withMessageContaining("acquire must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> IOPath.bracket(() -> "r", null, _ -> {}))
          .withMessageContaining("use must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> IOPath.bracket(() -> "r", r -> r, null))
          .withMessageContaining("release must not be null");
    }
  }

  @Nested
  @DisplayName("bracketIO() Pattern")
  class BracketIOTests {

    @Test
    @DisplayName("bracketIO() works when use returns IOPath")
    void bracketIOWorksWhenUseReturnsIOPath() {
      AtomicBoolean released = new AtomicBoolean(false);

      IOPath<String> path =
          IOPath.bracketIO(
              () -> "resource",
              resource -> Path.ioPure("used: " + resource),
              _ -> released.set(true));

      String result = path.unsafeRun();

      assertThat(result).isEqualTo("used: resource");
      assertThat(released).isTrue();
    }

    @Test
    @DisplayName("bracketIO() releases resource when inner IOPath fails")
    void bracketIOReleasesResourceWhenInnerIOPathFails() {
      AtomicBoolean released = new AtomicBoolean(false);

      IOPath<String> path =
          IOPath.bracketIO(
              () -> "resource",
              _ ->
                  Path.io(
                      () -> {
                        throw new RuntimeException("Inner failed");
                      }),
              _ -> released.set(true));

      assertThatThrownBy(path::unsafeRun)
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Inner failed");

      assertThat(released).isTrue();
    }
  }

  @Nested
  @DisplayName("withResource() for AutoCloseable")
  class WithResourceTests {

    @Test
    @DisplayName("withResource() closes AutoCloseable resource")
    void withResourceClosesAutoCloseableResource() {
      AtomicBoolean closed = new AtomicBoolean(false);

      IOPath<String> path =
          IOPath.withResource(
              () ->
                  new AutoCloseable() {
                    @Override
                    public void close() {
                      closed.set(true);
                    }

                    @Override
                    public String toString() {
                      return "TestResource";
                    }
                  },
              resource -> "used: " + resource);

      String result = path.unsafeRun();

      assertThat(result).isEqualTo("used: TestResource");
      assertThat(closed).isTrue();
    }

    @Test
    @DisplayName("withResource() works with real AutoCloseable")
    void withResourceWorksWithRealAutoCloseable() {
      byte[] data = "Hello, World!".getBytes();

      IOPath<String> path =
          IOPath.withResource(
              () -> new ByteArrayInputStream(data), in -> new String(in.readAllBytes()));

      String result = path.unsafeRun();

      assertThat(result).isEqualTo("Hello, World!");
    }

    @Test
    @DisplayName("withResource() closes resource even when use throws")
    void withResourceClosesResourceEvenWhenUseThrows() {
      AtomicBoolean closed = new AtomicBoolean(false);

      IOPath<String> path =
          IOPath.withResource(
              () -> (AutoCloseable) () -> closed.set(true),
              _ -> {
                throw new RuntimeException("Use failed");
              });

      assertThatThrownBy(path::unsafeRun).isInstanceOf(RuntimeException.class);

      assertThat(closed).isTrue();
    }
  }

  @Nested
  @DisplayName("withResourceIO() for AutoCloseable")
  class WithResourceIOTests {

    @Test
    @DisplayName("withResourceIO() works when use returns IOPath")
    void withResourceIOWorksWhenUseReturnsIOPath() {
      AtomicBoolean closed = new AtomicBoolean(false);

      IOPath<String> path =
          IOPath.withResourceIO(
              () -> (AutoCloseable) () -> closed.set(true), _ -> Path.ioPure("result from IO"));

      String result = path.unsafeRun();

      assertThat(result).isEqualTo("result from IO");
      assertThat(closed).isTrue();
    }
  }

  @Nested
  @DisplayName("guarantee() Method")
  class GuaranteeTests {

    @Test
    @DisplayName("guarantee() runs finaliser after successful computation")
    void guaranteeRunsFinalizerAfterSuccess() {
      AtomicBoolean finalized = new AtomicBoolean(false);

      IOPath<String> path = Path.ioPure("result").guarantee(() -> finalized.set(true));

      String result = path.unsafeRun();

      assertThat(result).isEqualTo("result");
      assertThat(finalized).isTrue();
    }

    @Test
    @DisplayName("guarantee() runs finaliser after failure")
    void guaranteeRunsFinalizerAfterFailure() {
      AtomicBoolean finalized = new AtomicBoolean(false);

      IOPath<String> path =
          Path.<String>io(
                  () -> {
                    throw new RuntimeException("Failed");
                  })
              .guarantee(() -> finalized.set(true));

      assertThatThrownBy(path::unsafeRun).isInstanceOf(RuntimeException.class);

      assertThat(finalized).isTrue();
    }

    @Test
    @DisplayName("guarantee() validates null finaliser")
    void guaranteeValidatesNullFinalizer() {
      IOPath<String> path = Path.ioPure("value");

      assertThatNullPointerException()
          .isThrownBy(() -> path.guarantee(null))
          .withMessageContaining("finalizer must not be null");
    }
  }

  @Nested
  @DisplayName("guaranteeIO() Method")
  class GuaranteeIOTests {

    @Test
    @DisplayName("guaranteeIO() runs IOPath finaliser")
    void guaranteeIORunsIOPathFinalizer() {
      AtomicBoolean finalized = new AtomicBoolean(false);
      IOPath<?> finalizer = Path.ioRunnable(() -> finalized.set(true));

      IOPath<String> path = Path.ioPure("result").guaranteeIO(finalizer);

      String result = path.unsafeRun();

      assertThat(result).isEqualTo("result");
      assertThat(finalized).isTrue();
    }
  }

  @Nested
  @DisplayName("A cleanup that throws")
  class CleanupFailureTests {

    private static final String CLEANED_USES =
        "org.higherkindedj.hkt.effect.IOPathResourceAndParallelTest#cleanedUses";

    @ParameterizedTest(name = "{0}")
    @MethodSource(CLEANED_USES)
    @DisplayName("a failing use is reported, with the cleanup's exception suppressed onto it")
    void failingUseKeepsItsException(CleanedUse method) {
      IllegalStateException useFailure = new IllegalStateException("use failed");
      IllegalArgumentException cleanupFailure = new IllegalArgumentException("cleanup failed");

      IOPath<String> path =
          method
              .build()
              .apply(
                  () -> {
                    throw useFailure;
                  },
                  () -> {
                    throw cleanupFailure;
                  });

      assertThatThrownBy(path::unsafeRun)
          .isSameAs(useFailure)
          .satisfies(e -> assertThat(e.getSuppressed()).containsExactly(cleanupFailure));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource(CLEANED_USES)
    @DisplayName("a successful use fails with the cleanup's exception")
    void successfulUseFailsWithCleanupException(CleanedUse method) {
      IllegalArgumentException cleanupFailure = new IllegalArgumentException("cleanup failed");

      IOPath<String> path =
          method
              .build()
              .apply(
                  () -> "used",
                  () -> {
                    throw cleanupFailure;
                  });

      assertThatThrownBy(path::unsafeRun)
          .isSameAs(cleanupFailure)
          .satisfies(e -> assertThat(e.getSuppressed()).isEmpty());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource(CLEANED_USES)
    @DisplayName(
        "one exception thrown by both the use and the cleanup is not suppressed onto itself")
    void sharedExceptionIsNotSuppressedOntoItself(CleanedUse method) {
      IllegalStateException shared = new IllegalStateException("shared");

      IOPath<String> path =
          method
              .build()
              .apply(
                  () -> {
                    throw shared;
                  },
                  () -> {
                    throw shared;
                  });

      assertThatThrownBy(path::unsafeRun)
          .isSameAs(shared)
          .satisfies(e -> assertThat(e.getSuppressed()).isEmpty());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource(CLEANED_USES)
    @DisplayName(
        "the cleanup after an interrupted use runs with the interrupt status cleared, then restores"
            + " it")
    void cleanupAfterInterruptedUseRunsUninterrupted(CleanedUse method) {
      AtomicBoolean interruptedDuringCleanup = new AtomicBoolean(true);

      IOPath<String> path =
          method
              .build()
              .apply(
                  () -> {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted");
                  },
                  () -> interruptedDuringCleanup.set(Thread.currentThread().isInterrupted()));

      try {
        assertThatThrownBy(path::unsafeRun).hasMessage("interrupted");
        assertThat(interruptedDuringCleanup).isFalse();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
      } finally {
        Thread.interrupted();
      }
    }
  }

  @Nested
  @DisplayName("A close() that throws")
  class CloseFailureTests {

    private static final String CLOSING_USES =
        "org.higherkindedj.hkt.effect.IOPathResourceAndParallelTest#closingUses";

    @ParameterizedTest(name = "{0}")
    @MethodSource(CLOSING_USES)
    @DisplayName("an unchecked exception from close() fails a successful use")
    void uncheckedCloseFailureFailsSuccessfulUse(ClosingUse method) {
      IllegalStateException closeFailure = new IllegalStateException("close failed");

      IOPath<String> path =
          method
              .build()
              .apply(
                  () -> {
                    throw closeFailure;
                  },
                  () -> "used");

      assertThatThrownBy(path::unsafeRun).isSameAs(closeFailure);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource(CLOSING_USES)
    @DisplayName("an IOException from close() fails a successful use as an UncheckedIOException")
    void ioExceptionFromCloseFailsAsUnchecked(ClosingUse method) {
      IOException closeFailure = new IOException("close failed");

      IOPath<String> path =
          method
              .build()
              .apply(
                  () -> {
                    throw closeFailure;
                  },
                  () -> "used");

      assertThatThrownBy(path::unsafeRun)
          .isExactlyInstanceOf(UncheckedIOException.class)
          .cause()
          .isSameAs(closeFailure);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource(CLOSING_USES)
    @DisplayName("another checked exception from close() fails a successful use as its cause")
    void checkedExceptionFromCloseFailsAsCause(ClosingUse method) {
      Exception closeFailure = new Exception("close failed");

      IOPath<String> path =
          method
              .build()
              .apply(
                  () -> {
                    throw closeFailure;
                  },
                  () -> "used");

      assertThatThrownBy(path::unsafeRun)
          .isExactlyInstanceOf(RuntimeException.class)
          .cause()
          .isSameAs(closeFailure);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource(CLOSING_USES)
    @DisplayName(
        "an InterruptedException from close() fails the use and restores the interrupt status")
    void interruptedCloseRestoresInterruptStatus(ClosingUse method) {
      InterruptedException closeFailure = new InterruptedException("close interrupted");

      IOPath<String> path =
          method
              .build()
              .apply(
                  () -> {
                    throw closeFailure;
                  },
                  () -> "used");

      try {
        assertThatThrownBy(path::unsafeRun)
            .isExactlyInstanceOf(RuntimeException.class)
            .cause()
            .isSameAs(closeFailure);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
      } finally {
        Thread.interrupted();
      }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource(CLOSING_USES)
    @DisplayName(
        "an InterruptedException from close() after a failing use is suppressed, and sets the"
            + " interrupt status")
    void interruptedCloseAfterFailingUseSetsInterruptStatus(ClosingUse method) {
      IllegalStateException useFailure = new IllegalStateException("use failed");
      InterruptedException closeFailure = new InterruptedException("close interrupted");

      IOPath<String> path =
          method
              .build()
              .apply(
                  () -> {
                    throw closeFailure;
                  },
                  () -> {
                    throw useFailure;
                  });

      try {
        assertThatThrownBy(path::unsafeRun)
            .isSameAs(useFailure)
            .satisfies(e -> assertThat(e.getSuppressed()).containsExactly(closeFailure));
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
      } finally {
        Thread.interrupted();
      }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource(CLOSING_USES)
    @DisplayName("a resource supplier that returns null fails before the use runs")
    void nullResourceFailsBeforeTheUse(ClosingUse method) {
      AtomicBoolean used = new AtomicBoolean(false);

      IOPath<String> path =
          method
              .build()
              .apply(
                  null,
                  () -> {
                    used.set(true);
                    return "used";
                  });

      assertThatNullPointerException()
          .isThrownBy(path::unsafeRun)
          .withMessage("resourceSupplier must not return null");
      assertThat(used).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource(CLOSING_USES)
    @DisplayName("close()'s exception, as thrown, is suppressed onto a failing use's")
    void closeFailureIsSuppressedOntoFailingUse(ClosingUse method) {
      IllegalStateException useFailure = new IllegalStateException("use failed");
      IOException closeFailure = new IOException("close failed");

      IOPath<String> path =
          method
              .build()
              .apply(
                  () -> {
                    throw closeFailure;
                  },
                  () -> {
                    throw useFailure;
                  });

      assertThatThrownBy(path::unsafeRun)
          .isSameAs(useFailure)
          .satisfies(e -> assertThat(e.getSuppressed()).containsExactly(closeFailure));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource(CLOSING_USES)
    @DisplayName("one exception thrown by both the use and close() is not suppressed onto itself")
    void sharedExceptionIsNotSuppressedOntoItself(ClosingUse method) {
      IllegalStateException shared = new IllegalStateException("shared");

      IOPath<String> path =
          method
              .build()
              .apply(
                  () -> {
                    throw shared;
                  },
                  () -> {
                    throw shared;
                  });

      assertThatThrownBy(path::unsafeRun)
          .isSameAs(shared)
          .satisfies(e -> assertThat(e.getSuppressed()).isEmpty());
    }
  }

  // ===== Parallel Execution Tests =====

  @Nested
  @DisplayName("parZipWith() Method")
  class ParZipWithTests {

    @Test
    @DisplayName("parZipWith() combines two IOPaths")
    void parZipWithCombinesTwoIOPaths() {
      IOPath<Integer> first = Path.ioPure(10);
      IOPath<Integer> second = Path.ioPure(20);

      IOPath<Integer> result = first.parZipWith(second, Integer::sum);

      assertThat(result.unsafeRun()).isEqualTo(30);
    }

    @Test
    @DisplayName("parZipWith() executes concurrently")
    void parZipWithExecutesConcurrently() {
      CountDownLatch firstStarted = new CountDownLatch(1);
      CountDownLatch secondStarted = new CountDownLatch(1);
      AtomicBoolean bothStartedBeforeEitherFinished = new AtomicBoolean(false);

      IOPath<String> first =
          Path.io(
              () -> {
                firstStarted.countDown();
                // Wait for second to start
                try {
                  if (secondStarted.await(1, TimeUnit.SECONDS)) {
                    bothStartedBeforeEitherFinished.set(true);
                  }
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                return "first";
              });

      IOPath<String> second =
          Path.io(
              () -> {
                secondStarted.countDown();
                // Wait for first to start
                try {
                  if (firstStarted.await(1, TimeUnit.SECONDS)) {
                    bothStartedBeforeEitherFinished.set(true);
                  }
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                return "second";
              });

      String result = first.parZipWith(second, (a, b) -> a + "+" + b).unsafeRun();

      assertThat(result).isEqualTo("first+second");
      assertThat(bothStartedBeforeEitherFinished).isTrue();
    }

    @Test
    @DisplayName("parZipWith() validates null arguments")
    void parZipWithValidatesNullArguments() {
      IOPath<Integer> path = Path.ioPure(10);

      assertThatNullPointerException()
          .isThrownBy(() -> path.parZipWith(null, Integer::sum))
          .withMessageContaining("other must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> path.parZipWith(Path.ioPure(20), null))
          .withMessageContaining("combiner must not be null");
    }

    @Test
    @DisplayName("parZipWith() propagates exception from first IOPath")
    void parZipWithPropagatesExceptionFromFirst() {
      IOPath<Integer> first =
          Path.io(
              () -> {
                throw new RuntimeException("First failed");
              });
      IOPath<Integer> second = Path.ioPure(20);

      IOPath<Integer> result = first.parZipWith(second, Integer::sum);

      assertThatThrownBy(result::unsafeRun)
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("First failed");
    }
  }

  @Nested
  @DisplayName("race() Method")
  class RaceTests {

    @Test
    @DisplayName("race() returns first completed result")
    void raceReturnsFirstCompletedResult() {
      IOPath<String> fast = Path.ioPure("fast");
      IOPath<String> slow =
          Path.io(
              () -> {
                try {
                  Thread.sleep(500);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                return "slow";
              });

      String result = fast.race(slow).unsafeRun();

      assertThat(result).isEqualTo("fast");
    }

    @Test
    @DisplayName("race() validates null argument")
    void raceValidatesNullArgument() {
      IOPath<String> path = Path.ioPure("value");

      assertThatNullPointerException()
          .isThrownBy(() -> path.race(null))
          .withMessageContaining("other must not be null");
    }

    @Test
    @DisplayName("race() handles thread interruption")
    void raceHandlesThreadInterruption() throws InterruptedException {
      CountDownLatch operationStarted = new CountDownLatch(1);
      CountDownLatch canComplete = new CountDownLatch(1);

      // Both operations wait indefinitely until canComplete is triggered
      IOPath<String> first =
          Path.io(
              () -> {
                operationStarted.countDown();
                try {
                  canComplete.await();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                return "first";
              });

      IOPath<String> second =
          Path.io(
              () -> {
                try {
                  canComplete.await();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                return "second";
              });

      IOPath<String> raceResult = first.race(second);

      Thread testThread = Thread.currentThread();

      // Schedule an interrupt after the operation starts
      Thread interrupter =
          new Thread(
              () -> {
                try {
                  operationStarted.await();
                  Thread.sleep(50); // Give time for race to start waiting
                  testThread.interrupt();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              });
      interrupter.start();

      assertThatThrownBy(raceResult::unsafeRun)
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("interrupted");

      // Clean up
      canComplete.countDown();
      interrupter.join(1000);
      Thread.interrupted(); // Clear interrupt status
    }
  }

  // ===== PathOps Parallel Tests =====

  @Nested
  @DisplayName("PathOps.parSequenceIO()")
  class ParSequenceIOTests {

    @Test
    @DisplayName("parSequenceIO() executes all IOPaths and collects results")
    void parSequenceIOExecutesAllAndCollectsResults() {
      List<IOPath<Integer>> paths = List.of(Path.ioPure(1), Path.ioPure(2), Path.ioPure(3));

      IOPath<List<Integer>> result = PathOps.parSequenceIO(paths);

      assertThat(result.unsafeRun()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("parSequenceIO() returns empty list for empty input")
    void parSequenceIOReturnsEmptyListForEmptyInput() {
      List<IOPath<Integer>> paths = List.of();

      IOPath<List<Integer>> result = PathOps.parSequenceIO(paths);

      assertThat(result.unsafeRun()).isEmpty();
    }

    @Test
    @DisplayName("parSequenceIO() executes concurrently")
    void parSequenceIOExecutesConcurrently() {
      AtomicInteger concurrentCount = new AtomicInteger(0);
      AtomicInteger maxConcurrent = new AtomicInteger(0);

      List<IOPath<Integer>> paths =
          List.of(
              createConcurrencyTrackingPath(1, concurrentCount, maxConcurrent),
              createConcurrencyTrackingPath(2, concurrentCount, maxConcurrent),
              createConcurrencyTrackingPath(3, concurrentCount, maxConcurrent));

      List<Integer> result = PathOps.parSequenceIO(paths).unsafeRun();

      assertThat(result).containsExactly(1, 2, 3);
      assertThat(maxConcurrent.get()).isGreaterThan(1);
    }

    private IOPath<Integer> createConcurrencyTrackingPath(
        int value, AtomicInteger concurrentCount, AtomicInteger maxConcurrent) {
      return Path.io(
          () -> {
            int current = concurrentCount.incrementAndGet();
            maxConcurrent.updateAndGet(max -> Math.max(max, current));
            try {
              Thread.sleep(50); // Give time for concurrent execution
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            concurrentCount.decrementAndGet();
            return value;
          });
    }

    @Test
    @DisplayName("parSequenceIO() propagates first exception")
    void parSequenceIOPropagatesFirstException() {
      List<IOPath<Integer>> paths =
          List.of(
              Path.ioPure(1),
              Path.io(
                  () -> {
                    throw new RuntimeException("Failed");
                  }),
              Path.ioPure(3));

      IOPath<List<Integer>> result = PathOps.parSequenceIO(paths);

      assertThatThrownBy(result::unsafeRun)
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Failed");
    }

    @Test
    @DisplayName("parSequenceIO() validates null argument")
    void parSequenceIOValidatesNullArgument() {
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.parSequenceIO(null))
          .withMessageContaining("paths must not be null");
    }
  }

  @Nested
  @DisplayName("PathOps.parZip3()")
  class ParZip3Tests {

    @Test
    @DisplayName("parZip3() combines three IOPaths")
    void parZip3CombinesThreeIOPaths() {
      IOPath<Integer> first = Path.ioPure(1);
      IOPath<Integer> second = Path.ioPure(2);
      IOPath<Integer> third = Path.ioPure(3);

      IOPath<Integer> result = PathOps.parZip3(first, second, third, (a, b, c) -> a + b + c);

      assertThat(result.unsafeRun()).isEqualTo(6);
    }

    @Test
    @DisplayName("parZip3() validates null arguments")
    void parZip3ValidatesNullArguments() {
      IOPath<Integer> path = Path.ioPure(1);

      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.parZip3(null, path, path, (a, _, _) -> a))
          .withMessageContaining("first must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.parZip3(path, null, path, (a, _, _) -> a))
          .withMessageContaining("second must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.parZip3(path, path, null, (a, _, _) -> a))
          .withMessageContaining("third must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.parZip3(path, path, path, null))
          .withMessageContaining("combiner must not be null");
    }

    @Test
    @DisplayName("parZip3() handles thread interruption")
    void parZip3HandlesThreadInterruption() throws InterruptedException {
      CountDownLatch operationStarted = new CountDownLatch(1);
      CountDownLatch canComplete = new CountDownLatch(1);

      IOPath<Integer> first =
          Path.io(
              () -> {
                operationStarted.countDown();
                try {
                  canComplete.await();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                return 1;
              });

      IOPath<Integer> second =
          Path.io(
              () -> {
                try {
                  canComplete.await();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                return 2;
              });

      IOPath<Integer> third =
          Path.io(
              () -> {
                try {
                  canComplete.await();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                return 3;
              });

      IOPath<Integer> result = PathOps.parZip3(first, second, third, (a, b, c) -> a + b + c);

      Thread testThread = Thread.currentThread();

      Thread interrupter =
          new Thread(
              () -> {
                try {
                  operationStarted.await();
                  Thread.sleep(50);
                  testThread.interrupt();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              });
      interrupter.start();

      assertThatThrownBy(result::unsafeRun)
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("interrupted");

      canComplete.countDown();
      interrupter.join(1000);
      Thread.interrupted();
    }
  }

  @Nested
  @DisplayName("PathOps.parZip4()")
  class ParZip4Tests {

    @Test
    @DisplayName("parZip4() combines four IOPaths")
    void parZip4CombinesFourIOPaths() {
      IOPath<Integer> first = Path.ioPure(1);
      IOPath<Integer> second = Path.ioPure(2);
      IOPath<Integer> third = Path.ioPure(3);
      IOPath<Integer> fourth = Path.ioPure(4);

      IOPath<Integer> result =
          PathOps.parZip4(first, second, third, fourth, (a, b, c, d) -> a + b + c + d);

      assertThat(result.unsafeRun()).isEqualTo(10);
    }

    @Test
    @DisplayName("parZip4() validates null arguments")
    void parZip4ValidatesNullArguments() {
      IOPath<Integer> path = Path.ioPure(1);

      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.parZip4(null, path, path, path, (a, _, _, _) -> a))
          .withMessageContaining("first must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.parZip4(path, path, path, path, null))
          .withMessageContaining("combiner must not be null");
    }

    @Test
    @DisplayName("parZip4() handles thread interruption")
    void parZip4HandlesThreadInterruption() throws InterruptedException {
      CountDownLatch operationStarted = new CountDownLatch(1);
      CountDownLatch canComplete = new CountDownLatch(1);

      IOPath<Integer> first =
          Path.io(
              () -> {
                operationStarted.countDown();
                try {
                  canComplete.await();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                return 1;
              });

      IOPath<Integer> second =
          Path.io(
              () -> {
                try {
                  canComplete.await();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                return 2;
              });

      IOPath<Integer> third =
          Path.io(
              () -> {
                try {
                  canComplete.await();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                return 3;
              });

      IOPath<Integer> fourth =
          Path.io(
              () -> {
                try {
                  canComplete.await();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                return 4;
              });

      IOPath<Integer> result =
          PathOps.parZip4(first, second, third, fourth, (a, b, c, d) -> a + b + c + d);

      Thread testThread = Thread.currentThread();

      Thread interrupter =
          new Thread(
              () -> {
                try {
                  operationStarted.await();
                  Thread.sleep(50);
                  testThread.interrupt();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              });
      interrupter.start();

      assertThatThrownBy(result::unsafeRun)
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("interrupted");

      canComplete.countDown();
      interrupter.join(1000);
      Thread.interrupted();
    }
  }

  @Nested
  @DisplayName("PathOps.parSequenceFuture()")
  class ParSequenceFutureTests {

    @Test
    @DisplayName("parSequenceFuture() executes all CompletableFuturePaths and collects results")
    void parSequenceFutureExecutesAllAndCollectsResults() {
      List<CompletableFuturePath<Integer>> paths =
          List.of(
              CompletableFuturePath.completed(1),
              CompletableFuturePath.completed(2),
              CompletableFuturePath.completed(3));

      CompletableFuturePath<List<Integer>> result = PathOps.parSequenceFuture(paths);

      assertThat(result.join()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("parSequenceFuture() returns empty list for empty input")
    void parSequenceFutureReturnsEmptyListForEmptyInput() {
      List<CompletableFuturePath<Integer>> paths = List.of();

      CompletableFuturePath<List<Integer>> result = PathOps.parSequenceFuture(paths);

      assertThat(result.join()).isEmpty();
    }

    @Test
    @DisplayName("parSequenceFuture() propagates exception")
    void parSequenceFuturePropagatesException() {
      List<CompletableFuturePath<Integer>> paths =
          List.of(
              CompletableFuturePath.completed(1),
              CompletableFuturePath.failed(new RuntimeException("Failed")),
              CompletableFuturePath.completed(3));

      CompletableFuturePath<List<Integer>> result = PathOps.parSequenceFuture(paths);

      assertThatThrownBy(result::join)
          .isInstanceOf(CompletionException.class)
          .hasCauseInstanceOf(RuntimeException.class);
    }
  }

  @Nested
  @DisplayName("PathOps.raceIO()")
  class RaceIOTests {

    @Test
    @DisplayName("raceIO() returns first successful result")
    void raceIOReturnsFirstSuccessfulResult() {
      IOPath<String> fast = Path.ioPure("fast");
      IOPath<String> slow =
          Path.io(
              () -> {
                try {
                  Thread.sleep(500);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                return "slow";
              });

      IOPath<String> result = PathOps.raceIO(List.of(slow, fast));

      assertThat(result.unsafeRun()).isEqualTo("fast");
    }

    @Test
    @DisplayName("raceIO() returns single element for single-element list")
    void raceIOReturnsSingleElement() {
      IOPath<String> only = Path.ioPure("only");

      IOPath<String> result = PathOps.raceIO(List.of(only));

      assertThat(result.unsafeRun()).isEqualTo("only");
    }

    @Test
    @DisplayName("raceIO() validates null argument")
    void raceIOValidatesNullArgument() {
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.raceIO((List<IOPath<Integer>>) null))
          .withMessageContaining("paths must not be null");
    }

    @Test
    @DisplayName("raceIO() throws for empty list")
    void raceIOThrowsForEmptyList() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> PathOps.raceIO(List.of()))
          .withMessageContaining("paths must not be empty");
    }

    @Test
    @DisplayName("raceIO() propagates failure when all fail")
    void raceIOPropagatesFailureWhenAllFail() {
      IOPath<String> fail1 =
          Path.io(
              () -> {
                throw new RuntimeException("Fail 1");
              });
      IOPath<String> fail2 =
          Path.io(
              () -> {
                throw new RuntimeException("Fail 2");
              });

      IOPath<String> result = PathOps.raceIO(List.of(fail1, fail2));

      assertThatThrownBy(result::unsafeRun).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("raceIO() handles thread interruption")
    void raceIOHandlesThreadInterruption() throws InterruptedException {
      CountDownLatch operationStarted = new CountDownLatch(1);
      CountDownLatch canComplete = new CountDownLatch(1);

      IOPath<String> first =
          Path.io(
              () -> {
                operationStarted.countDown();
                try {
                  canComplete.await();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                return "first";
              });

      IOPath<String> second =
          Path.io(
              () -> {
                try {
                  canComplete.await();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                return "second";
              });

      IOPath<String> result = PathOps.raceIO(List.of(first, second));

      Thread testThread = Thread.currentThread();

      Thread interrupter =
          new Thread(
              () -> {
                try {
                  operationStarted.await();
                  Thread.sleep(50);
                  testThread.interrupt();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              });
      interrupter.start();

      assertThatThrownBy(result::unsafeRun)
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("interrupted");

      canComplete.countDown();
      interrupter.join(1000);
      Thread.interrupted();
    }
  }
}
