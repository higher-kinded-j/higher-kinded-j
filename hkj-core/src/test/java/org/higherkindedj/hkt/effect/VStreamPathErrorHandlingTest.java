// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for VStreamPath error handling methods.
 *
 * <p>Tests cover recover, recoverWith, mapError, onError, mapTask, throttle, and metered.
 */
@DisplayName("VStreamPath Error Handling Tests")
class VStreamPathErrorHandlingTest {

  @Nested
  @DisplayName("recover()")
  class RecoverTests {

    @Test
    @DisplayName("passes through elements from a successful stream")
    void passesThroughSuccessfulElements() {
      VStreamPath<Integer> stream = Path.vstreamFromList(List.of(1, 2, 3));

      VStreamPath<Integer> recovered = stream.recover(ex -> -1);

      List<Integer> result = recovered.toList().unsafeRun();
      assertThat(result).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("replaces error with recovery value")
    void replacesErrorWithRecoveryValue() {
      VStreamPath<String> stream = Path.vstream(VStream.fail(new RuntimeException("stream error")));

      VStreamPath<String> recovered = stream.recover(ex -> "fallback: " + ex.getMessage());

      List<String> result = recovered.toList().unsafeRun();
      assertThat(result).containsExactly("fallback: stream error");
    }

    @Test
    @DisplayName("recovery applies to failing elements in mapTask")
    void recoveryAppliesWithMapTask() {
      VStreamPath<Integer> stream = Path.vstreamFromList(List.of(1, 2, 3));

      VStreamPath<Integer> failing =
          stream.mapTask(
              n -> {
                if (n == 2) {
                  return VTask.fail(new RuntimeException("fail on 2"));
                }
                return VTask.succeed(n * 10);
              });

      VStreamPath<Integer> recovered = failing.recover(ex -> -1);

      List<Integer> result = recovered.toList().unsafeRun();
      assertThat(result).containsExactly(10, -1, 30);
    }
  }

  @Nested
  @DisplayName("recoverWith()")
  class RecoverWithTests {

    @Test
    @DisplayName("passes through elements from a successful stream")
    void passesThroughSuccessfulElements() {
      VStreamPath<Integer> stream = Path.vstreamFromList(List.of(1, 2, 3));

      VStreamPath<Integer> recovered = stream.recoverWith(ex -> Path.vstreamFromList(List.of(99)));

      List<Integer> result = recovered.toList().unsafeRun();
      assertThat(result).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("switches to fallback stream on error")
    void switchesToFallbackStreamOnError() {
      VStreamPath<String> stream = Path.vstream(VStream.fail(new RuntimeException("stream error")));

      VStreamPath<String> recovered =
          stream.recoverWith(ex -> Path.vstreamFromList(List.of("fallback1", "fallback2")));

      List<String> result = recovered.toList().unsafeRun();
      assertThat(result).containsExactly("fallback1", "fallback2");
    }

    @Test
    @DisplayName("fallback stream can be empty")
    void fallbackStreamCanBeEmpty() {
      VStreamPath<String> stream = Path.vstream(VStream.fail(new RuntimeException("error")));

      VStreamPath<String> recovered = stream.recoverWith(ex -> Path.vstreamFromList(List.of()));

      List<String> result = recovered.toList().unsafeRun();
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("mapError()")
  class MapErrorTests {

    @Test
    @DisplayName("does not affect successful elements")
    void doesNotAffectSuccessfulElements() {
      VStreamPath<Integer> stream = Path.vstreamFromList(List.of(1, 2, 3));

      VStreamPath<Integer> mapped = stream.mapError(ex -> new IllegalStateException("mapped"));

      List<Integer> result = mapped.toList().unsafeRun();
      assertThat(result).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("transforms the error type")
    void transformsErrorType() {
      VStreamPath<String> stream = Path.vstream(VStream.fail(new RuntimeException("original")));

      VStreamPath<String> mapped =
          stream.mapError(ex -> new IllegalStateException("mapped: " + ex.getMessage()));

      assertThatThrownBy(() -> mapped.toList().unsafeRun())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("mapped: original");
    }

    @Test
    @DisplayName("mapError() chains with recover()")
    void chainsWithRecover() {
      VStreamPath<String> stream = Path.vstream(VStream.fail(new RuntimeException("original")));

      VStreamPath<String> result =
          stream
              .mapError(ex -> new IllegalStateException("mapped"))
              .recover(ex -> "recovered: " + ex.getClass().getSimpleName());

      List<String> elements = result.toList().unsafeRun();
      assertThat(elements).containsExactly("recovered: IllegalStateException");
    }
  }

  @Nested
  @DisplayName("onError()")
  class OnErrorTests {

    @Test
    @DisplayName("does not fire on successful stream")
    void doesNotFireOnSuccess() {
      AtomicBoolean errorObserved = new AtomicBoolean(false);

      VStreamPath<Integer> stream = Path.vstreamFromList(List.of(1, 2, 3));

      VStreamPath<Integer> observed = stream.onError(ex -> errorObserved.set(true));

      List<Integer> result = observed.toList().unsafeRun();
      assertThat(result).containsExactly(1, 2, 3);
      assertThat(errorObserved).isFalse();
    }

    @Test
    @DisplayName("observes error and re-raises it")
    void observesErrorAndReRaises() {
      AtomicReference<Throwable> observed = new AtomicReference<>();

      VStreamPath<String> stream =
          Path.vstream(VStream.fail(new RuntimeException("observed error")));

      VStreamPath<String> withObserver = stream.onError(observed::set);

      assertThatThrownBy(() -> withObserver.toList().unsafeRun())
          .isInstanceOf(RuntimeException.class);

      assertThat(observed.get()).isNotNull();
      assertThat(observed.get()).hasMessageContaining("observed error");
    }

    @Test
    @DisplayName("onError() side-effect runs before error propagates")
    void sideEffectRunsBeforeErrorPropagates() {
      AtomicBoolean sideEffectRan = new AtomicBoolean(false);

      VStreamPath<String> stream = Path.vstream(VStream.fail(new RuntimeException("error")));

      VStreamPath<String> withObserver = stream.onError(ex -> sideEffectRan.set(true));

      assertThatThrownBy(() -> withObserver.toList().unsafeRun())
          .isInstanceOf(RuntimeException.class);

      assertThat(sideEffectRan).isTrue();
    }
  }

  @Nested
  @DisplayName("mapTask()")
  class MapTaskTests {

    @Test
    @DisplayName("applies effectful function to each element")
    void appliesEffectfulFunction() {
      VStreamPath<Integer> stream = Path.vstreamFromList(List.of(1, 2, 3));

      VStreamPath<String> mapped = stream.mapTask(n -> VTask.succeed("item-" + n));

      List<String> result = mapped.toList().unsafeRun();
      assertThat(result).containsExactly("item-1", "item-2", "item-3");
    }

    @Test
    @DisplayName("propagates VTask failure as stream error")
    void propagatesVTaskFailure() {
      VStreamPath<Integer> stream = Path.vstreamFromList(List.of(1, 2, 3));

      VStreamPath<Integer> mapped =
          stream.mapTask(
              n -> {
                if (n == 2) {
                  return VTask.fail(new RuntimeException("fail on 2"));
                }
                return VTask.succeed(n * 10);
              });

      assertThatThrownBy(() -> mapped.toList().unsafeRun())
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("fail on 2");
    }

    @Test
    @DisplayName("mapTask() can be composed with recover()")
    void composesWithRecover() {
      VStreamPath<Integer> stream = Path.vstreamFromList(List.of(1, 2, 3));

      VStreamPath<Integer> result =
          stream
              .mapTask(
                  n -> {
                    if (n == 2) {
                      return VTask.fail(new RuntimeException("fail"));
                    }
                    return VTask.succeed(n * 10);
                  })
              .recover(ex -> -1);

      List<Integer> elements = result.toList().unsafeRun();
      assertThat(elements).containsExactly(10, -1, 30);
    }

    @Test
    @DisplayName("handles empty stream")
    void handlesEmptyStream() {
      VStreamPath<Integer> stream = Path.vstreamFromList(List.of());

      VStreamPath<String> mapped = stream.mapTask(n -> VTask.succeed("x"));

      List<String> result = mapped.toList().unsafeRun();
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("throttle()")
  class ThrottleTests {

    @Test
    @DisplayName("throttle allows elements through within the rate limit")
    void allowsElementsThroughWithinRateLimit() {
      VStreamPath<Integer> stream = Path.vstreamFromList(List.of(1, 2, 3));

      VStreamPath<Integer> throttled = stream.throttle(10, Duration.ofSeconds(1));

      List<Integer> result = throttled.toList().unsafeRun();
      assertThat(result).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("throttle with tight limit introduces delays")
    void tightLimitIntroducesDelays() {
      VStreamPath<Integer> stream = Path.vstreamFromList(List.of(1, 2, 3));

      // 1 element per 50ms window
      VStreamPath<Integer> throttled = stream.throttle(1, Duration.ofMillis(50));

      long start = System.nanoTime();
      List<Integer> result = throttled.toList().unsafeRun();
      long elapsed = (System.nanoTime() - start) / 1_000_000;

      assertThat(result).containsExactly(1, 2, 3);
      // With 3 elements and 1 per 50ms, should take at least 100ms (2 delays)
      assertThat(elapsed).isGreaterThanOrEqualTo(80);
    }

    @Test
    @DisplayName("throttle preserves element order")
    void preservesElementOrder() {
      VStreamPath<String> stream = Path.vstreamFromList(List.of("a", "b", "c", "d"));

      VStreamPath<String> throttled = stream.throttle(2, Duration.ofMillis(30));

      List<String> result = throttled.toList().unsafeRun();
      assertThat(result).containsExactly("a", "b", "c", "d");
    }
  }

  @Nested
  @DisplayName("metered()")
  class MeteredTests {

    @Test
    @DisplayName("metered adds delay between elements")
    void addsDelayBetweenElements() {
      VStreamPath<Integer> stream = Path.vstreamFromList(List.of(1, 2, 3));

      VStreamPath<Integer> metered = stream.metered(Duration.ofMillis(50));

      long start = System.nanoTime();
      List<Integer> result = metered.toList().unsafeRun();
      long elapsed = (System.nanoTime() - start) / 1_000_000;

      assertThat(result).containsExactly(1, 2, 3);
      // With 3 elements and 50ms interval, should take at least 100ms (2 intervals)
      assertThat(elapsed).isGreaterThanOrEqualTo(80);
    }

    @Test
    @DisplayName("metered preserves all elements")
    void preservesAllElements() {
      List<Integer> input = List.of(10, 20, 30, 40, 50);
      VStreamPath<Integer> stream = Path.vstreamFromList(input);

      VStreamPath<Integer> metered = stream.metered(Duration.ofMillis(10));

      List<Integer> result = metered.toList().unsafeRun();
      assertThat(result).containsExactly(10, 20, 30, 40, 50);
    }

    @Test
    @DisplayName("metered handles empty stream")
    void handlesEmptyStream() {
      VStreamPath<Integer> stream = Path.vstreamFromList(List.of());

      VStreamPath<Integer> metered = stream.metered(Duration.ofMillis(50));

      List<Integer> result = metered.toList().unsafeRun();
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("metered handles single element stream")
    void handlesSingleElement() {
      VStreamPath<String> stream = Path.vstreamFromList(List.of("only"));

      VStreamPath<String> metered = stream.metered(Duration.ofMillis(10));

      List<String> result = metered.toList().unsafeRun();
      assertThat(result).containsExactly("only");
    }
  }
}
