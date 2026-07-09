// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.time.Instant;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.error.ErrorEnvelope;
import org.higherkindedj.hkt.time.TimeSource;
import org.junit.jupiter.api.DisplayName;

/** Coverage contract for {@link ErrorEnvelopeAssert}. See {@link AssertContract}. */
@DisplayName("ErrorEnvelopeAssert contract")
class ErrorEnvelopeAssertContractTest
    extends AssertContract<ErrorEnvelope<String>, ErrorEnvelopeAssert<String>> {

  private static final Instant FROZEN = Instant.parse("2026-07-07T00:00:00Z");
  private static final ErrorEnvelope<String> ENVELOPE =
      ErrorEnvelope.of(
          TimeSource.of(SteppableClock.startingAt(FROZEN)),
          "OUT_OF_STOCK",
          "Out of stock",
          "ctx-value");
  private static final ErrorEnvelope<String> OTHER =
      ErrorEnvelope.of(
          TimeSource.of(SteppableClock.startingAt(FROZEN.plusSeconds(60))),
          "PAYMENT_DECLINED",
          "Payment declined",
          "other-ctx");

  @Override
  protected Function<ErrorEnvelope<String>, ErrorEnvelopeAssert<String>> entry() {
    return ErrorEnvelopeAssert::assertThatErrorEnvelope;
  }

  @Override
  protected Stream<Row<ErrorEnvelope<String>, ErrorEnvelopeAssert<String>>> rows() {
    return Stream.of(
        row("hasCode match", ENVELOPE, OTHER, a -> a.hasCode("OUT_OF_STOCK")),
        row("hasMessage match", ENVELOPE, OTHER, a -> a.hasMessage("Out of stock")),
        row("hasMessageContaining match", ENVELOPE, OTHER, a -> a.hasMessageContaining("stock")),
        row("hasTimestamp match", ENVELOPE, OTHER, a -> a.hasTimestamp(FROZEN)),
        row("hasContext match", ENVELOPE, OTHER, a -> a.hasContext("ctx-value")),
        passOnly("hasContextSatisfying passes", ENVELOPE, a -> a.hasContextSatisfying(c -> {})),
        failOnly(
            "hasContextSatisfying inner throws",
            ENVELOPE,
            a ->
                a.hasContextSatisfying(
                    c -> {
                      throw new AssertionError("inner");
                    })));
  }
}
