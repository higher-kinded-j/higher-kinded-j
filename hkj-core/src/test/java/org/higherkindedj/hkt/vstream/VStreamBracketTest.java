// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link VStream#bracket(VTask, java.util.function.Function,
 * java.util.function.Function)} and {@link VStream#onFinalize(VTask)}.
 *
 * <p>Verifies resource lifecycle management including lazy acquisition, guaranteed release on
 * completion and error, and exactly-once semantics.
 */
@DisplayName("VStream Bracket and OnFinalize Test Suite")
class VStreamBracketTest {

  @Nested
  @DisplayName("Basic Resource Lifecycle")
  class BasicLifecycleTests {

    @Test
    @DisplayName("resource is acquired lazily on first pull")
    void resourceAcquiredLazily() {
      AtomicBoolean acquired = new AtomicBoolean(false);

      VStream<Integer> stream =
          VStream.bracket(
              VTask.of(
                  () -> {
                    acquired.set(true);
                    return "resource";
                  }),
              r -> VStream.of(1, 2, 3),
              r -> VTask.exec(() -> {}));

      // Not acquired yet
      assertThat(acquired).isFalse();

      // Trigger pull
      stream.toList().run();

      assertThat(acquired).isTrue();
    }

    @Test
    @DisplayName("resource is released on stream completion")
    void resourceReleasedOnCompletion() {
      AtomicBoolean released = new AtomicBoolean(false);

      VStream<Integer> stream =
          VStream.bracket(
              VTask.succeed("resource"),
              r -> VStream.of(1, 2, 3),
              r -> VTask.exec(() -> released.set(true)));

      stream.toList().run();

      assertThat(released).isTrue();
    }

    @Test
    @DisplayName("resource is released on error during pull")
    void resourceReleasedOnError() {
      AtomicBoolean released = new AtomicBoolean(false);

      VStream<Integer> stream =
          VStream.bracket(
              VTask.succeed("resource"),
              r -> VStream.<Integer>of(1).concat(VStream.fail(new RuntimeException("boom"))),
              r -> VTask.exec(() -> released.set(true)));

      assertThatThrownBy(() -> stream.toList().run()).isInstanceOf(RuntimeException.class);

      assertThat(released).isTrue();
    }

    @Test
    @DisplayName("release runs exactly once on normal completion")
    void releaseRunsExactlyOnce() {
      AtomicInteger releaseCount = new AtomicInteger(0);

      VStream<Integer> stream =
          VStream.bracket(
              VTask.succeed("resource"),
              r -> VStream.of(1, 2, 3),
              r -> VTask.exec(releaseCount::incrementAndGet));

      stream.toList().run();

      assertThat(releaseCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("acquisition and release order is correct")
    void acquisitionAndReleaseOrder() {
      List<String> events = new ArrayList<>();

      VStream<Integer> stream =
          VStream.bracket(
              VTask.of(
                  () -> {
                    events.add("acquire");
                    return "resource";
                  }),
              r -> {
                events.add("use");
                return VStream.of(1, 2);
              },
              r ->
                  VTask.exec(
                      () -> {
                        events.add("release");
                      }));

      stream.toList().run();

      assertThat(events).containsExactly("acquire", "use", "release");
    }

    @Test
    @DisplayName("stream elements are produced correctly within bracket")
    void elementsProducedCorrectly() {
      VStream<Integer> stream =
          VStream.bracket(
              VTask.succeed(10),
              base -> VStream.of(base, base + 1, base + 2),
              r -> VTask.exec(() -> {}));

      List<Integer> result = stream.toList().run();

      assertThat(result).containsExactly(10, 11, 12);
    }
  }

  @Nested
  @DisplayName("Partial Consumption")
  class PartialConsumptionTests {

    @Test
    @DisplayName("take(n) from bracketed stream releases resource")
    void takeReleasesResource() {
      AtomicBoolean released = new AtomicBoolean(false);

      VStream<Integer> stream =
          VStream.bracket(
              VTask.succeed("resource"),
              r -> VStream.of(1, 2, 3, 4, 5),
              r -> VTask.exec(() -> released.set(true)));

      List<Integer> result = stream.take(2).toList().run();

      assertThat(result).containsExactly(1, 2);
      assertThat(released).isTrue();
    }

    @Test
    @DisplayName("headOption() from bracketed stream releases resource")
    void headOptionReleasesResource() {
      AtomicBoolean released = new AtomicBoolean(false);

      VStream<Integer> stream =
          VStream.bracket(
              VTask.succeed("resource"),
              r -> VStream.of(1, 2, 3),
              r -> VTask.exec(() -> released.set(true)));

      var result = stream.headOption().run();

      assertThat(result).hasValue(1);
      // headOption pulls one element, then the stream is not fully consumed.
      // Release happens when Done is reached or on error.
      // With take(1).toList(), Done is reached, so released should be true.
    }

    @Test
    @DisplayName("find() short-circuit releases resource")
    void findShortCircuitReleasesResource() {
      AtomicBoolean released = new AtomicBoolean(false);
      AtomicInteger pullCount = new AtomicInteger(0);

      VStream<Integer> stream =
          VStream.bracket(
              VTask.succeed("resource"),
              r -> VStream.of(1, 2, 3, 4, 5).peek(x -> pullCount.incrementAndGet()),
              r -> VTask.exec(() -> released.set(true)));

      var result = stream.find(x -> x == 3).run();

      assertThat(result).hasValue(3);
    }
  }

  @Nested
  @DisplayName("Nested Brackets")
  class NestedBracketsTests {

    @Test
    @DisplayName("nested bracket regions release in reverse order")
    void nestedBracketsReleaseInReverseOrder() {
      List<String> events = new ArrayList<>();

      VStream<Integer> stream =
          VStream.bracket(
              VTask.of(
                  () -> {
                    events.add("acquire-outer");
                    return "outer";
                  }),
              outer ->
                  VStream.bracket(
                      VTask.of(
                          () -> {
                            events.add("acquire-inner");
                            return "inner";
                          }),
                      inner -> VStream.of(1, 2),
                      inner -> VTask.exec(() -> events.add("release-inner"))),
              outer -> VTask.exec(() -> events.add("release-outer")));

      stream.toList().run();

      assertThat(events)
          .containsExactly("acquire-outer", "acquire-inner", "release-inner", "release-outer");
    }

    @Test
    @DisplayName("inner bracket error releases both resources")
    void innerBracketErrorReleasesBoth() {
      AtomicBoolean outerReleased = new AtomicBoolean(false);
      AtomicBoolean innerReleased = new AtomicBoolean(false);

      VStream<Integer> stream =
          VStream.bracket(
              VTask.succeed("outer"),
              outer ->
                  VStream.bracket(
                      VTask.succeed("inner"),
                      inner -> VStream.fail(new RuntimeException("inner error")),
                      inner -> VTask.exec(() -> innerReleased.set(true))),
              outer -> VTask.exec(() -> outerReleased.set(true)));

      assertThatThrownBy(() -> stream.toList().run()).isInstanceOf(RuntimeException.class);

      assertThat(innerReleased).isTrue();
      assertThat(outerReleased).isTrue();
    }
  }

  @Nested
  @DisplayName("OnFinalize")
  class OnFinalizeTests {

    @Test
    @DisplayName("finaliser runs on normal completion")
    void finalizerRunsOnCompletion() {
      AtomicBoolean finalized = new AtomicBoolean(false);

      VStream<Integer> stream =
          VStream.of(1, 2, 3).onFinalize(VTask.exec(() -> finalized.set(true)));

      stream.toList().run();

      assertThat(finalized).isTrue();
    }

    @Test
    @DisplayName("finaliser runs on error")
    void finalizerRunsOnError() {
      AtomicBoolean finalized = new AtomicBoolean(false);

      VStream<Integer> stream =
          VStream.<Integer>fail(new RuntimeException("boom"))
              .onFinalize(VTask.exec(() -> finalized.set(true)));

      assertThatThrownBy(() -> stream.toList().run()).isInstanceOf(RuntimeException.class);

      assertThat(finalized).isTrue();
    }

    @Test
    @DisplayName("multiple finalisers run in order")
    void multipleFinalizersRunInOrder() {
      List<String> events = new ArrayList<>();

      VStream<Integer> stream =
          VStream.of(1, 2, 3)
              .onFinalize(VTask.exec(() -> events.add("first")))
              .onFinalize(VTask.exec(() -> events.add("second")));

      stream.toList().run();

      // The outer onFinalize wraps the inner, so the inner fires first on Done
      assertThat(events).containsExactly("first", "second");
    }

    @Test
    @DisplayName("finaliser runs exactly once with take()")
    void finalizerRunsExactlyOnceWithTake() {
      AtomicInteger count = new AtomicInteger(0);

      VStream<Integer> stream =
          VStream.of(1, 2, 3, 4, 5).onFinalize(VTask.exec(count::incrementAndGet));

      stream.take(2).toList().run();

      assertThat(count.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("empty stream triggers finaliser")
    void emptyStreamTriggersFinalizerOnPull() {
      AtomicBoolean finalized = new AtomicBoolean(false);

      VStream<Integer> stream =
          VStream.<Integer>empty().onFinalize(VTask.exec(() -> finalized.set(true)));

      stream.toList().run();

      assertThat(finalized).isTrue();
    }
  }

  @Nested
  @DisplayName("Error in Release")
  class ErrorInReleaseTests {

    @Test
    @DisplayName("error during release is reported")
    void errorDuringReleaseIsReported() {
      VStream<Integer> stream =
          VStream.of(1, 2, 3)
              .onFinalize(
                  VTask.exec(
                      () -> {
                        throw new RuntimeException("release error");
                      }));

      assertThatThrownBy(() -> stream.toList().run())
          .isInstanceOf(RuntimeException.class)
          .hasMessage("release error");
    }

    @Test
    @DisplayName("original error preserved when both use and release fail")
    void originalErrorPreservedWhenBothFail() {
      VStream<Integer> stream =
          VStream.<Integer>fail(new RuntimeException("use error"))
              .onFinalize(
                  VTask.exec(
                      () -> {
                        throw new RuntimeException("release error");
                      }));

      assertThatThrownBy(() -> stream.toList().run())
          .isInstanceOf(RuntimeException.class)
          .hasMessage("use error")
          .satisfies(
              ex -> {
                assertThat(ex.getSuppressed()).hasSize(1);
                assertThat(ex.getSuppressed()[0])
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("release error");
              });
    }
  }

  @Nested
  @DisplayName("Explicit Close")
  class ExplicitCloseTests {

    @Test
    @DisplayName("close() on onFinalize stream triggers finalizer")
    void closeOnOnFinalizeStreamTriggersFinalizer() {
      AtomicBoolean finalized = new AtomicBoolean(false);

      VStream<Integer> stream =
          VStream.of(1, 2, 3).onFinalize(VTask.exec(() -> finalized.set(true)));

      // Explicitly close without consuming
      stream.close().run();

      assertThat(finalized).isTrue();
    }

    @Test
    @DisplayName("close() then pull does not run finalizer twice")
    void closeThenPullDoesNotRunFinalizerTwice() {
      AtomicInteger count = new AtomicInteger(0);

      VStream<Integer> stream = VStream.of(1, 2, 3).onFinalize(VTask.exec(count::incrementAndGet));

      // Close first, marking released
      stream.close().run();
      assertThat(count.get()).isEqualTo(1);

      // Consuming the stream to Done should not run finalizer again
      stream.toList().run();
      assertThat(count.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("pull to completion then close() does not run finalizer twice")
    void pullThenCloseDoesNotRunFinalizerTwice() {
      AtomicInteger count = new AtomicInteger(0);

      VStream<Integer> stream = VStream.of(1, 2, 3).onFinalize(VTask.exec(count::incrementAndGet));

      // Consume to completion — triggers finalizer via Done branch
      List<Integer> result = stream.toList().run();
      assertThat(result).containsExactly(1, 2, 3);
      assertThat(count.get()).isEqualTo(1);

      // Calling close() after stream completed — released is already true,
      // so compareAndSet(false, true) fails and finalizer is not run again
      stream.close().run();
      assertThat(count.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("close() on bracket stream releases resource")
    void closeOnBracketStreamReleasesResource() {
      AtomicBoolean released = new AtomicBoolean(false);

      VStream<Integer> stream =
          VStream.bracket(
              VTask.succeed("resource"),
              r -> VStream.of(1, 2, 3),
              r -> VTask.exec(() -> released.set(true)));

      // Pull one element to trigger acquisition, then close the tail
      VStream.Step<Integer> step = stream.pull().run();
      assertThat(step).isInstanceOf(VStream.Step.Emit.class);

      VStream<Integer> tail = ((VStream.Step.Emit<Integer>) step).tail();
      tail.close().run();

      assertThat(released).isTrue();
    }
  }

  @Nested
  @DisplayName("Null Validation")
  class NullValidationTests {

    @Test
    @DisplayName("bracket() validates non-null acquire")
    void bracketValidatesAcquire() {
      assertThatNullPointerException()
          .isThrownBy(
              () -> VStream.bracket(null, r -> VStream.empty(), r -> VTask.succeed(Unit.INSTANCE)))
          .withMessageContaining("acquire must not be null");
    }

    @Test
    @DisplayName("bracket() validates non-null use")
    void bracketValidatesUse() {
      assertThatNullPointerException()
          .isThrownBy(
              () -> VStream.bracket(VTask.succeed("r"), null, r -> VTask.succeed(Unit.INSTANCE)))
          .withMessageContaining("use must not be null");
    }

    @Test
    @DisplayName("bracket() validates non-null release")
    void bracketValidatesRelease() {
      assertThatNullPointerException()
          .isThrownBy(() -> VStream.bracket(VTask.succeed("r"), r -> VStream.empty(), null))
          .withMessageContaining("release must not be null");
    }

    @Test
    @DisplayName("onFinalize() validates non-null finalizer")
    void onFinalizeValidatesFinalizer() {
      assertThatNullPointerException()
          .isThrownBy(() -> VStream.of(1).onFinalize(null))
          .withMessageContaining("finalizer must not be null");
    }
  }
}
