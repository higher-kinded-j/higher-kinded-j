// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Cleanup: reporting a failure as try-with-resources does")
class CleanupTest {

  @Nested
  @DisplayName("guarantee()")
  class GuaranteeTests {

    @Test
    @DisplayName("returns the work's result after running the cleanup once")
    void returnsResultAfterCleanup() {
      List<String> events = new ArrayList<>();

      String result =
          Cleanup.guarantee(
              () -> {
                events.add("work");
                return "done";
              },
              () -> events.add("cleanup"));

      assertThat(result).isEqualTo("done");
      assertThat(events).containsExactly("work", "cleanup");
    }

    @Test
    @DisplayName("throws the cleanup's exception after successful work")
    void throwsCleanupFailureAfterSuccess() {
      IllegalStateException cleanupFailure = new IllegalStateException("cleanup failed");

      assertThatThrownBy(
              () ->
                  Cleanup.guarantee(
                      () -> "done",
                      () -> {
                        throw cleanupFailure;
                      }))
          .isSameAs(cleanupFailure)
          .satisfies(e -> assertThat(e.getSuppressed()).isEmpty());
    }

    @Test
    @DisplayName(
        "rethrows the work's failure, even an Error, with the cleanup's suppressed onto it")
    void rethrowsWorkFailureWithCleanupSuppressed() {
      AssertionError workFailure = new AssertionError("work failed");
      IllegalStateException cleanupFailure = new IllegalStateException("cleanup failed");

      assertThatThrownBy(
              () ->
                  Cleanup.guarantee(
                      () -> {
                        throw workFailure;
                      },
                      () -> {
                        throw cleanupFailure;
                      }))
          .isSameAs(workFailure)
          .satisfies(e -> assertThat(e.getSuppressed()).containsExactly(cleanupFailure));
    }

    @Test
    @DisplayName("runs the cleanup after failed work that the cleanup does not fail")
    void runsCleanupAfterFailedWork() {
      AtomicBoolean cleaned = new AtomicBoolean(false);
      IllegalStateException workFailure = new IllegalStateException("work failed");

      assertThatThrownBy(
              () ->
                  Cleanup.guarantee(
                      () -> {
                        throw workFailure;
                      },
                      () -> cleaned.set(true)))
          .isSameAs(workFailure)
          .satisfies(e -> assertThat(e.getSuppressed()).isEmpty());
      assertThat(cleaned).isTrue();
    }
  }

  @Nested
  @DisplayName("afterFailure()")
  class AfterFailureTests {

    @Test
    @DisplayName("suppresses a checked exception from the cleanup, as thrown, onto the failure")
    void suppressesCheckedCleanupFailure() {
      IllegalStateException failure = new IllegalStateException("failed");
      IOException cleanupFailure = new IOException("close failed");

      Cleanup.afterFailure(
          failure,
          () -> {
            throw cleanupFailure;
          });

      assertThat(failure.getSuppressed()).containsExactly(cleanupFailure);
    }

    @Test
    @DisplayName("runs the cleanup with the interrupt status cleared, then restores it")
    void clearsThenRestoresInterruptStatus() {
      AtomicBoolean interruptedDuringCleanup = new AtomicBoolean(true);
      Thread.currentThread().interrupt();

      try {
        Cleanup.afterFailure(
            new IllegalStateException("failed"),
            () -> interruptedDuringCleanup.set(Thread.currentThread().isInterrupted()));

        assertThat(interruptedDuringCleanup).isFalse();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
      } finally {
        Thread.interrupted();
      }
    }

    @Test
    @DisplayName("leaves a clear interrupt status clear")
    void leavesClearInterruptStatusClear() {
      Cleanup.afterFailure(new IllegalStateException("failed"), () -> {});

      assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }
  }

  @Nested
  @DisplayName("keep()")
  class KeepTests {

    @Test
    @DisplayName("keeps the later failure when there was no first")
    void keepsLaterWithoutFirst() {
      IllegalStateException later = new IllegalStateException("later");

      assertThat(Cleanup.keep(null, later)).isSameAs(later);
      assertThat(later.getSuppressed()).isEmpty();
    }

    @Test
    @DisplayName("keeps the first failure, with the later one suppressed onto it")
    void keepsFirstWithLaterSuppressed() {
      IllegalStateException first = new IllegalStateException("first");
      IllegalStateException later = new IllegalStateException("later");

      assertThat(Cleanup.keep(first, later)).isSameAs(first);
      assertThat(first.getSuppressed()).containsExactly(later);
    }
  }

  @Nested
  @DisplayName("suppress()")
  class SuppressTests {

    @Test
    @DisplayName("does not suppress a failure onto itself")
    void doesNotSuppressOntoItself() {
      IllegalStateException shared = new IllegalStateException("shared");

      Cleanup.suppress(shared, shared);

      assertThat(shared.getSuppressed()).isEmpty();
    }

    @Test
    @DisplayName("does not suppress a failure onto one it is a cause of")
    void doesNotSuppressACause() {
      IOException cause = new IOException("cause");
      RuntimeException wrapper = new RuntimeException(new IllegalStateException("middle", cause));

      Cleanup.suppress(wrapper, cause);

      assertThat(wrapper.getSuppressed()).isEmpty();
    }

    @Test
    @DisplayName("suppresses an unrelated failure onto one whose cause chain is cyclic")
    void suppressesOntoCyclicCauseChain() {
      IllegalStateException first = new IllegalStateException("first");
      IllegalStateException second = new IllegalStateException("second");
      first.initCause(second);
      second.initCause(first);
      IllegalStateException later = new IllegalStateException("later");

      Cleanup.suppress(first, later);

      assertThat(first.getSuppressed()).containsExactly(later);
    }
  }
}
