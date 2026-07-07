// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.higherkindedj.hkt.assertions.IOAssert.assertThatIO;
import static org.higherkindedj.hkt.assertions.VTaskAssert.assertThatVTask;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.io.IO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TimeSource - java.time.Clock lifted into IO/VTask")
class TimeSourceTest {

  private static final Instant FROZEN = Instant.parse("2026-07-07T00:00:00Z");

  @Nested
  @DisplayName("Factories")
  class Factories {

    @Test
    @DisplayName("fixed freezes every read at the given instant, in UTC")
    void fixedFreezesTime() {
      TimeSource time = TimeSource.fixed(FROZEN);
      assertThat(time.now().unsafeRunSync()).isEqualTo(FROZEN);
      assertThat(time.now().unsafeRunSync()).isEqualTo(FROZEN);
      assertThat(time.clock().getZone()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    @DisplayName("of lifts any JDK clock, including offset clocks")
    void ofLiftsAnyJdkClock() {
      Clock offset = Clock.offset(Clock.fixed(FROZEN, ZoneOffset.UTC), Duration.ofHours(1));
      TimeSource time = TimeSource.of(offset);
      assertThat(time.now().unsafeRunSync()).isEqualTo(FROZEN.plus(Duration.ofHours(1)));
    }

    @Test
    @DisplayName("system reads live UTC time")
    void systemReadsLiveTime() {
      // A generous tolerance rather than a strict wall-clock sandwich: the system clock is not
      // monotonic, and an NTP adjustment must not flake this test.
      Instant read = TimeSource.system().now().unsafeRunSync();
      assertThat(Duration.between(read, Instant.now()).abs()).isLessThan(Duration.ofMinutes(1));
      assertThat(TimeSource.system().clock().getZone()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    @DisplayName("all inputs are eagerly guarded with named messages")
    void inputsAreGuarded() {
      assertThatNullPointerException()
          .isThrownBy(() -> TimeSource.of(null))
          .withMessage("clock must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> TimeSource.fixed(null))
          .withMessage("instant must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> new TimeSource(null))
          .withMessage("clock must not be null");
    }
  }

  @Nested
  @DisplayName("Effectful reads")
  class EffectfulReads {

    @Test
    @DisplayName("now is lazy: the clock is not read until the IO runs, and each run reads afresh")
    void nowIsLazyAndRereads() {
      AtomicInteger reads = new AtomicInteger();
      Clock counting =
          new Clock() {
            @Override
            public ZoneOffset getZone() {
              return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
              return this;
            }

            @Override
            public Instant instant() {
              reads.incrementAndGet();
              return FROZEN;
            }
          };
      IO<Instant> now = TimeSource.of(counting).now();
      assertThatIO(now).isNotExecutedYet();
      assertThat(reads).hasValue(0);
      assertThatIO(now).whenExecuted().hasValue(FROZEN);
      assertThatIO(now).whenExecuted().hasValue(FROZEN);
      assertThat(reads).hasValue(2);
    }

    @Test
    @DisplayName("nowAsync reads the same clock through VTask")
    void nowAsyncReadsThroughVTask() {
      assertThatVTask(TimeSource.fixed(FROZEN).nowAsync()).whenRun().succeeds().hasValue(FROZEN);
    }
  }
}
