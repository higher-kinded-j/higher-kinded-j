// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;

/**
 * The raw HTTP failure handed to a {@link ResponseErrorDecoder}.
 *
 * <p>Carries everything a decoder needs to reconstruct a typed error: the status code, the
 * (possibly absent) response body, and the response headers — the headers allow, for example,
 * reading a {@code Retry-After} value emitted by an {@code HttpHeaderCarrier} error on the server.
 *
 * @param status the HTTP status code of the failed response (never {@code null})
 * @param body the response body as a string, or {@code null} when absent
 * @param headers the response headers, or {@code null} when unavailable
 */
public record ClientErrorResponse(
    HttpStatusCode status, @Nullable String body, @Nullable HttpHeaders headers) {

  /** Canonical constructor. */
  public ClientErrorResponse {
    Objects.requireNonNull(status, "status");
  }

  /**
   * Convenience accessor for the numeric status code.
   *
   * @return the HTTP status code value (e.g. {@code 404})
   */
  public int statusValue() {
    return status.value();
  }

  /**
   * The {@code Retry-After} hint on this response, if present, as a {@link Duration}.
   *
   * <p>Both forms are understood: a delta-seconds value (e.g. {@code "30"}) and an HTTP-date (e.g.
   * {@code "Wed, 21 Oct 2026 07:28:00 GMT"}), the latter resolved against the current time and
   * never negative. This is the hook a custom {@link ResponseErrorDecoder} (or an {@code
   * eitherVTask} caller) can use to honour a server's back-off hint — e.g. seeding a {@code
   * RetryPolicy} or {@code VTaskPath.withRetry} — typically emitted by an {@code HttpHeaderCarrier}
   * error on a 429 or 503.
   *
   * @return the retry-after duration, or empty when the header is absent or unparseable
   */
  public Optional<Duration> retryAfter() {
    return parseRetryAfter(headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER));
  }

  /** Parses a {@code Retry-After} header value (delta-seconds or HTTP-date) into a duration. */
  static Optional<Duration> parseRetryAfter(@Nullable String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    String trimmed = value.trim();
    if (!trimmed.isEmpty() && trimmed.chars().allMatch(Character::isDigit)) {
      try {
        return Optional.of(Duration.ofSeconds(Long.parseLong(trimmed)));
      } catch (NumberFormatException e) {
        return Optional.empty();
      }
    }
    try {
      ZonedDateTime when = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME);
      Duration delta = Duration.between(Instant.now(), when.toInstant());
      return Optional.of(delta.isNegative() ? Duration.ZERO : delta);
    } catch (DateTimeParseException e) {
      return Optional.empty();
    }
  }
}
