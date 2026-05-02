// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import java.util.Map;

/**
 * Mix-in implemented by error values that wish to add HTTP headers to the response written by an
 * Effect Path return-value handler.
 *
 * <p>The canonical use case is rate-limit / throttling errors that need to surface a {@code
 * Retry-After} header alongside their JSON body:
 *
 * <pre>{@code
 * public record MfaThrottledError(int retryAfterSeconds) implements DomainError, HttpHeaderCarrier {
 *     @Override
 *     public Map<String, String> headers() {
 *         return Map.of("Retry-After", Integer.toString(retryAfterSeconds));
 *     }
 * }
 * }</pre>
 *
 * <p>The handler invokes {@link #headers()} after resolving the status code and before writing the
 * JSON body. Returning {@code null} or an empty map is safe and is treated as "no headers to add".
 * Header names and values are passed through to {@link
 * jakarta.servlet.http.HttpServletResponse#addHeader(String, String)}, so callers are responsible
 * for value escaping where the underlying header grammar requires it.
 *
 * <p>Because headers are added rather than set, multi-valued headers such as {@code
 * WWW-Authenticate}, {@code Set-Cookie}, and {@code Link} accumulate as separate header lines on
 * the response, matching their HTTP grammar. For single-valued headers such as {@code Retry-After},
 * the carrier should ensure the value appears at most once across all payload elements; emitting
 * two distinct values for a single-valued header is undefined behaviour.
 *
 * <p>For collection-typed error payloads (such as {@code ValidationPath} {@code Invalid} values),
 * the handler iterates the collection and applies headers from every element that implements this
 * interface; values accumulate.
 *
 * @see ErrorResponseHeaders
 */
public interface HttpHeaderCarrier {

  /**
   * Returns the headers to copy onto the response. May return {@code null} or an empty map.
   *
   * @return the headers to apply
   */
  Map<String, String> headers();
}
