// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.time.Instant;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.error.ErrorEnvelope;

/**
 * Custom AssertJ assertions for {@link ErrorEnvelope} instances.
 *
 * <p>Pairs naturally with a {@code SteppableClock}-backed {@code TimeSource}: freeze the clock,
 * raise the error, and assert the exact timestamp.
 *
 * <h2>Usage Examples:</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.assertions.ErrorEnvelopeAssert.assertThatErrorEnvelope;
 *
 * assertThatErrorEnvelope(error.envelope())
 *     .hasCode("OUT_OF_STOCK")
 *     .hasMessageContaining("stock")
 *     .hasTimestamp(frozenInstant)
 *     .hasContextSatisfying(ctx -> assertThat(ctx.orderId()).isEqualTo(orderId));
 * }</pre>
 *
 * @param <C> The typed context carried by the envelope
 */
public class ErrorEnvelopeAssert<C>
    extends AbstractAssert<ErrorEnvelopeAssert<C>, ErrorEnvelope<C>> {

  /** Entry point. */
  public static <C> ErrorEnvelopeAssert<C> assertThatErrorEnvelope(ErrorEnvelope<C> actual) {
    return new ErrorEnvelopeAssert<>(actual);
  }

  protected ErrorEnvelopeAssert(ErrorEnvelope<C> actual) {
    super(actual, ErrorEnvelopeAssert.class);
  }

  /** Asserts that the envelope carries the expected error code. */
  public ErrorEnvelopeAssert<C> hasCode(String expected) {
    isNotNull();
    Assertions.assertThat(actual.code())
        .withFailMessage("Expected code <%s> but was <%s>", expected, actual.code())
        .isEqualTo(expected);
    return this;
  }

  /** Asserts that the envelope carries the expected message. */
  public ErrorEnvelopeAssert<C> hasMessage(String expected) {
    isNotNull();
    Assertions.assertThat(actual.message())
        .withFailMessage("Expected message <%s> but was <%s>", expected, actual.message())
        .isEqualTo(expected);
    return this;
  }

  /** Asserts that the envelope's message contains the expected substring. */
  public ErrorEnvelopeAssert<C> hasMessageContaining(String expectedSubstring) {
    isNotNull();
    Assertions.assertThat(actual.message())
        .withFailMessage(
            "Expected message to contain <%s> but was <%s>", expectedSubstring, actual.message())
        .contains(expectedSubstring);
    return this;
  }

  /** Asserts that the envelope was stamped at the expected instant. */
  public ErrorEnvelopeAssert<C> hasTimestamp(Instant expected) {
    isNotNull();
    Assertions.assertThat(actual.timestamp())
        .withFailMessage("Expected timestamp <%s> but was <%s>", expected, actual.timestamp())
        .isEqualTo(expected);
    return this;
  }

  /** Asserts that the envelope carries the expected context instance. */
  public ErrorEnvelopeAssert<C> hasContext(C expected) {
    isNotNull();
    Assertions.assertThat(actual.context())
        .withFailMessage("Expected context <%s> but was <%s>", expected, actual.context())
        .isEqualTo(expected);
    return this;
  }

  /** Asserts that the typed context satisfies the given requirements. */
  public ErrorEnvelopeAssert<C> hasContextSatisfying(Consumer<? super C> requirements) {
    isNotNull();
    requirements.accept(actual.context());
    return this;
  }
}
