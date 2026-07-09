// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.error;

import java.time.Instant;
import java.util.Objects;
import org.higherkindedj.hkt.time.TimeSource;

/**
 * The shared carrier for a typed domain error's common fields - code, message, timestamp and a
 * typed context - so sealed error variants declare only their domain-specific components plus one
 * envelope component (issue #610).
 *
 * <p>The context is records-as-schema: {@code C} is a user-declared record (for example {@code
 * record OrderErrorContext(OrderId orderId, TraceId traceId)}) whose components are nullable, with
 * an all-absent instance as the default. The envelope itself never interprets {@code C}; it simply
 * carries it, typed, from the error site to the consumers (logs, metrics, HTTP problem details).
 *
 * <p>Timestamps come from a {@link TimeSource}, never from {@code Instant.now()}, so error
 * timestamps are deterministic in tests (issue #609):
 *
 * <pre>{@code
 * TimeSource time = TimeSource.fixed(Instant.parse("2026-07-07T00:00:00Z"));
 * ErrorEnvelope<OrderErrorContext> envelope =
 *     ErrorEnvelope.of(time, "OUT_OF_STOCK", "Out of stock", context);
 * }</pre>
 *
 * <p>Construct through the factories; the {@code with*} methods copy the envelope with one field
 * replaced.
 *
 * @param code the stable, machine-readable error code, conventionally UPPER_SNAKE; must not be null
 * @param message the human-readable message; must not be null
 * @param timestamp the instant the error was raised; must not be null
 * @param context the typed context instance (its components may be absent); must not be null
 * @param <C> the context type - a record whose components form the error's typed context
 */
public record ErrorEnvelope<C>(String code, String message, Instant timestamp, C context) {

  /**
   * Validates every field. Prefer the {@link #of(TimeSource, String, String, Object)} and {@link
   * #of(String, String, Object)} factories; this canonical constructor exists because records
   * require it.
   *
   * @throws NullPointerException if any argument is null
   */
  public ErrorEnvelope {
    Objects.requireNonNull(code, "code must not be null");
    Objects.requireNonNull(message, "message must not be null");
    Objects.requireNonNull(timestamp, "timestamp must not be null");
    Objects.requireNonNull(context, "context must not be null");
  }

  /**
   * Creates an envelope stamped from the given time source - the deterministic-in-tests factory.
   *
   * @param time the time source the timestamp is read from; must not be null
   * @param code the error code; must not be null
   * @param message the message; must not be null
   * @param context the typed context; must not be null
   * @param <C> the context type
   * @return the envelope (non-null)
   * @throws NullPointerException if any argument is null
   */
  public static <C> ErrorEnvelope<C> of(TimeSource time, String code, String message, C context) {
    Objects.requireNonNull(time, "time must not be null");
    return new ErrorEnvelope<>(code, message, time.clock().instant(), context);
  }

  /**
   * Creates an envelope stamped from {@link TimeSource#system()} - the live-clock convenience.
   *
   * @param code the error code; must not be null
   * @param message the message; must not be null
   * @param context the typed context; must not be null
   * @param <C> the context type
   * @return the envelope (non-null)
   * @throws NullPointerException if any argument is null
   */
  public static <C> ErrorEnvelope<C> of(String code, String message, C context) {
    return of(TimeSource.system(), code, message, context);
  }

  /**
   * Copies this envelope with the message replaced; code, timestamp and context are preserved.
   *
   * @param message the replacement message; must not be null
   * @return the copied envelope (non-null)
   * @throws NullPointerException if {@code message} is null
   */
  public ErrorEnvelope<C> withMessage(String message) {
    return new ErrorEnvelope<>(code, message, timestamp, context);
  }

  /**
   * Copies this envelope with the context replaced; code, message and timestamp are preserved. The
   * replacement may carry a different context type.
   *
   * @param context the replacement context; must not be null
   * @param <D> the replacement context type
   * @return the copied envelope (non-null)
   * @throws NullPointerException if {@code context} is null
   */
  public <D> ErrorEnvelope<D> withContext(D context) {
    return new ErrorEnvelope<>(code, message, timestamp, context);
  }
}
