// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SteppableClock - a test clock that only moves when told to")
class SteppableClockTest {

  private static final Instant START = Instant.parse("2026-07-07T00:00:00Z");

  @Test
  @DisplayName("stays frozen until advanced, then moves by exactly the step")
  void freezesAndAdvances() {
    SteppableClock clock = SteppableClock.startingAt(START);
    assertThat(clock.instant()).isEqualTo(START);
    assertThat(clock.instant()).isEqualTo(START);

    clock.advance(Duration.ofMinutes(16));
    assertThat(clock.instant()).isEqualTo(START.plus(Duration.ofMinutes(16)));

    clock.set(START);
    assertThat(clock.instant()).isEqualTo(START);
  }

  @Test
  @DisplayName("is fixed to UTC and keeps its identity across withZone")
  void zoneHandling() {
    SteppableClock clock = SteppableClock.startingAt(START);
    assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    assertThat(clock.withZone(ZoneOffset.ofHours(2))).isSameAs(clock);
  }

  @Test
  @DisplayName("all inputs are eagerly guarded with named messages")
  void inputsAreGuarded() {
    assertThatNullPointerException()
        .isThrownBy(() -> SteppableClock.startingAt(null))
        .withMessage("start must not be null");
    SteppableClock clock = SteppableClock.startingAt(START);
    assertThatNullPointerException()
        .isThrownBy(() -> clock.advance(null))
        .withMessage("step must not be null");
    assertThatNullPointerException()
        .isThrownBy(() -> clock.set(null))
        .withMessage("instant must not be null");
  }
}
