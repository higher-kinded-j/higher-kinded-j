// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import java.util.Objects;
import org.higherkindedj.hkt.error.ErrorEnvelope;
import org.higherkindedj.optics.annotations.GenerateErrorEnvelope;
import org.jspecify.annotations.Nullable;

/**
 * The two error-envelope contexts behind a checkpoint on the book's <a
 * href="https://higher-kinded-j.github.io/latest/mapping/merge_envelopes.html">Merge and Error
 * Envelopes</a> page: one rejects the all-absent context, one accepts it.
 *
 * <p>{@code RefundError} is deliberately broken. Its companion {@code RefundErrors} fails its first
 * use, and every use after, so only {@code MergeBookTest} may touch it: a class initialises once
 * per JVM, and the test must be the use that initialises it.
 */
public final class EnvelopeContextBook {

  private EnvelopeContextBook() {}
}

// ANCHOR: strict_context
record RefundErrorContext(TraceId traceId) {
  RefundErrorContext {
    Objects.requireNonNull(traceId, "traceId");
  }
}

@GenerateErrorEnvelope
sealed interface RefundError {
  ErrorEnvelope<RefundErrorContext> envelope();

  record RefundWindowClosed(String orderId, ErrorEnvelope<RefundErrorContext> envelope)
      implements RefundError {}
}

// ANCHOR_END: strict_context

// ANCHOR: lenient_context
record ChargebackErrorContext(@Nullable TraceId traceId) {
  ChargebackErrorContext {
    if (traceId != null && traceId.value().isBlank()) {
      throw new IllegalArgumentException("traceId is blank");
    }
  }
}

@GenerateErrorEnvelope
sealed interface ChargebackError {
  ErrorEnvelope<ChargebackErrorContext> envelope();

  record ChargebackOpened(String orderId, ErrorEnvelope<ChargebackErrorContext> envelope)
      implements ChargebackError {}
}

// ANCHOR_END: lenient_context
