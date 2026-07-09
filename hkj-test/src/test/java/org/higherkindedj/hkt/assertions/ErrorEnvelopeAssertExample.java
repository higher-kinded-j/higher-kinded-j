// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.ErrorEnvelopeAssert.assertThatErrorEnvelope;

import java.time.Instant;
import org.higherkindedj.hkt.error.ErrorEnvelope;
import org.higherkindedj.hkt.time.TimeSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Showcase for {@link ErrorEnvelopeAssert}. */
@DisplayName("ErrorEnvelopeAssert showcase")
class ErrorEnvelopeAssertExample {

  record OrderContext(String orderId, String traceId) {}

  @Test
  @DisplayName("a frozen TimeSource makes the whole envelope, timestamp included, assertable")
  void frozenEnvelope() {
    Instant frozen = Instant.parse("2026-07-07T09:30:00Z");
    TimeSource time = TimeSource.of(SteppableClock.startingAt(frozen));

    ErrorEnvelope<OrderContext> envelope =
        ErrorEnvelope.of(time, "OUT_OF_STOCK", "Out of stock", new OrderContext("o-1", "t-9"));

    assertThatErrorEnvelope(envelope)
        .hasCode("OUT_OF_STOCK")
        .hasMessageContaining("stock")
        .hasTimestamp(frozen)
        .hasContextSatisfying(ctx -> assertThat(ctx.orderId()).isEqualTo("o-1"));
  }
}
