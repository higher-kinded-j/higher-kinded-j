// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.higherkindedj.example.order.audit.AuditLogWriter;
import org.higherkindedj.hkt.assertions.SteppableClock;
import org.higherkindedj.hkt.time.TimeSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests that {@link AuditLogWriter} stamps each entry from an injected {@link TimeSource}, and that
 * its older constructors still stamp one.
 */
@DisplayName("AuditLogWriter")
class AuditLogWriterTest {

  private static final Instant START = Instant.parse("2026-07-07T00:00:00Z");

  @Test
  @DisplayName("info, warn and error each stamp the injected clock's time")
  void eachLevelUsesInjectedTime() {
    var clock = SteppableClock.startingAt(START);
    var writer = new AuditLogWriter("Test", TimeSource.of(clock));

    var info = writer.info("Validate", "details", 1);
    var warn = writer.warn("Check", "details", 2);
    var error = writer.error("Fail", "details", 3);

    assertThat(info.written().entries().getFirst().timestamp()).isEqualTo(START);
    assertThat(warn.written().entries().getFirst().timestamp()).isEqualTo(START);
    assertThat(error.written().entries().getFirst().timestamp()).isEqualTo(START);
  }

  @Test
  @DisplayName("advancing the clock moves the next timestamp")
  void advancingClockMovesTimestamp() {
    var clock = SteppableClock.startingAt(START);
    var writer = new AuditLogWriter("Test", TimeSource.of(clock));

    clock.advance(Duration.ofSeconds(30));
    var later = writer.info("Later", "details", 1);

    assertThat(later.written().entries().getFirst().timestamp()).isEqualTo(START.plusSeconds(30));
  }

  @Test
  @DisplayName("the older constructors still stamp a timestamp")
  void olderConstructorsStillWork() {
    var oneArgument = new AuditLogWriter("Test").info("Action", "details", 1);
    var noArguments = new AuditLogWriter().info("Action", "details", 1);

    assertThat(oneArgument.written().entries().getFirst().timestamp()).isNotNull();
    assertThat(noArguments.written().entries().getFirst().timestamp()).isNotNull();
  }
}
