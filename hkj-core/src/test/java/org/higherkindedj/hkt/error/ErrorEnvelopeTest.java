// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import java.time.Instant;
import org.higherkindedj.hkt.time.TimeSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ErrorEnvelope - the shared typed-error carrier")
class ErrorEnvelopeTest {

  private static final Instant FROZEN = Instant.parse("2026-07-07T00:00:00Z");
  private static final TimeSource TIME = TimeSource.fixed(FROZEN);

  private record OrderContext(String orderId, String traceId) {}

  private record NodeContext(String node) {}

  private static final OrderContext CONTEXT = new OrderContext("o-1", "t-1");

  @Nested
  @DisplayName("Factories")
  class Factories {

    @Test
    @DisplayName("of stamps the timestamp from the given TimeSource")
    void ofStampsFromTimeSource() {
      ErrorEnvelope<OrderContext> envelope =
          ErrorEnvelope.of(TIME, "OUT_OF_STOCK", "Out of stock", CONTEXT);
      assertThat(envelope.code()).isEqualTo("OUT_OF_STOCK");
      assertThat(envelope.message()).isEqualTo("Out of stock");
      assertThat(envelope.timestamp()).isEqualTo(FROZEN);
      assertThat(envelope.context()).isEqualTo(CONTEXT);
    }

    @Test
    @DisplayName("the convenience factory stamps from the system clock")
    void convenienceStampsFromSystemClock() {
      // A generous tolerance rather than a strict wall-clock sandwich: the system clock is not
      // monotonic, and an NTP adjustment must not flake this test.
      ErrorEnvelope<OrderContext> envelope =
          ErrorEnvelope.of("OUT_OF_STOCK", "Out of stock", CONTEXT);
      assertThat(Duration.between(envelope.timestamp(), Instant.now()).abs())
          .isLessThan(Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("all inputs are eagerly guarded with named messages")
    void inputsAreGuarded() {
      assertThatNullPointerException()
          .isThrownBy(() -> ErrorEnvelope.of(null, "C", "m", CONTEXT))
          .withMessage("time must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> ErrorEnvelope.of(TIME, null, "m", CONTEXT))
          .withMessage("code must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> ErrorEnvelope.of(TIME, "C", null, CONTEXT))
          .withMessage("message must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> ErrorEnvelope.of(TIME, "C", "m", null))
          .withMessage("context must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> new ErrorEnvelope<>("C", "m", null, CONTEXT))
          .withMessage("timestamp must not be null");
    }
  }

  @Nested
  @DisplayName("Withers")
  class Withers {

    private final ErrorEnvelope<OrderContext> envelope =
        ErrorEnvelope.of(TIME, "OUT_OF_STOCK", "Out of stock", CONTEXT);

    @Test
    @DisplayName("withMessage replaces the message and preserves everything else")
    void withMessageReplacesOnlyMessage() {
      ErrorEnvelope<OrderContext> updated = envelope.withMessage("Out of stock: 3 items");
      assertThat(updated.message()).isEqualTo("Out of stock: 3 items");
      assertThat(updated.code()).isEqualTo(envelope.code());
      assertThat(updated.timestamp()).isEqualTo(envelope.timestamp());
      assertThat(updated.context()).isEqualTo(envelope.context());
      assertThat(envelope.message()).isEqualTo("Out of stock");
    }

    @Test
    @DisplayName("withContext replaces the context, and may change its type")
    void withContextReplacesContext() {
      ErrorEnvelope<NodeContext> updated = envelope.withContext(new NodeContext("n-1"));
      assertThat(updated.context()).isEqualTo(new NodeContext("n-1"));
      assertThat(updated.code()).isEqualTo(envelope.code());
      assertThat(updated.message()).isEqualTo(envelope.message());
      assertThat(updated.timestamp()).isEqualTo(envelope.timestamp());
      assertThat(envelope.context()).isEqualTo(CONTEXT);
    }

    @Test
    @DisplayName("wither inputs are guarded through the canonical constructor")
    void witherInputsAreGuarded() {
      assertThatNullPointerException()
          .isThrownBy(() -> envelope.withMessage(null))
          .withMessage("message must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> envelope.withContext(null))
          .withMessage("context must not be null");
    }
  }
}
