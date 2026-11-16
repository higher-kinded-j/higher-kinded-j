// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.actuator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.effect.boundary.EffectBoundary;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ObservableEffectBoundary Tests")
class ObservableEffectBoundaryTest {

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final ObservableEffectBoundary<IOKind.Witness> boundary =
      new ObservableEffectBoundary<>(
          EffectBoundary.of(Natural.<IOKind.Witness>identity()), new HkjMetricsService(registry));

  @Nested
  @DisplayName("runAsync()")
  class RunAsyncTests {

    @Test
    @DisplayName("Should record a success by the time the future completes")
    void recordsASuccess() {
      assertThat(boundary.runAsync(Free.<IOKind.Witness, String>pure("done")).join())
          .isEqualTo("done");

      assertThat(
              registry
                  .get("hkj.effect.boundary.invocations")
                  .tag("result", "success")
                  .counter()
                  .count())
          .isEqualTo(1.0);
    }

    @Test
    @DisplayName(
        "Should record one outcome when a cancel lands while the success is being recorded")
    void recordsOneOutcomeWhenACancelRacesTheSuccess() throws Exception {
      CountDownLatch recordingSuccess = new CountDownLatch(1);
      CountDownLatch successMayFinish = new CountDownLatch(1);
      HkjMetricsService slowSuccess =
          new HkjMetricsService(registry) {
            @Override
            public void recordEffectBoundarySuccess() {
              super.recordEffectBoundarySuccess();
              recordingSuccess.countDown();
              try {
                successMayFinish.await(5, TimeUnit.SECONDS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            }
          };
      ObservableEffectBoundary<IOKind.Witness> racing =
          new ObservableEffectBoundary<>(
              EffectBoundary.of(Natural.<IOKind.Witness>identity()), slowSuccess);

      CompletableFuture<String> future = racing.runAsync(Free.<IOKind.Witness, String>pure("done"));
      assertThat(recordingSuccess.await(5, TimeUnit.SECONDS)).isTrue();
      // The success is still being recorded, so the future is not yet complete and the cancel lands
      assertThat(future.cancel(true)).isTrue();
      successMayFinish.countDown();

      assertThat(
              registry
                  .get("hkj.effect.boundary.invocations")
                  .tag("result", "success")
                  .counter()
                  .count())
          .isEqualTo(1.0);
      assertThat(
              registry
                  .get("hkj.effect.boundary.invocations")
                  .tag("result", "error")
                  .counter()
                  .count())
          .isZero();
    }

    @Test
    @DisplayName("Should interrupt the program, and record an error, when its future is cancelled")
    @SuppressWarnings("migration-nudge") // lifts an IO instruction directly, to block in it
    void cancellingTheFutureInterruptsTheProgram() throws Exception {
      CountDownLatch started = new CountDownLatch(1);
      CountDownLatch interrupted = new CountDownLatch(1);
      Free<IOKind.Witness, String> program =
          Free.liftF(
              IO_OP.widen(
                  IO.delay(
                      () -> {
                        started.countDown();
                        try {
                          Thread.sleep(5000);
                        } catch (InterruptedException e) {
                          interrupted.countDown();
                        }
                        return "slept";
                      })),
              IOMonad.INSTANCE);
      CompletableFuture<String> future = boundary.runAsync(program);
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

      assertThat(future.cancel(true)).isTrue();

      assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(
              registry
                  .get("hkj.effect.boundary.invocations")
                  .tag("result", "error")
                  .counter()
                  .count())
          .isEqualTo(1.0);
      assertThat(
              registry
                  .get("hkj.effect.boundary.errors")
                  .tag("error_type", "CancellationException")
                  .counter()
                  .count())
          .isEqualTo(1.0);
    }
  }
}
