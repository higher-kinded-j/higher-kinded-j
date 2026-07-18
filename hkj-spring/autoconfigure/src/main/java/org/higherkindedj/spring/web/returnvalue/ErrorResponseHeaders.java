// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Collection;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Internal helper that copies headers from {@link HttpHeaderCarrier} error payloads onto the
 * outgoing {@link HttpServletResponse}. Centralised so every Effect Path return-value handler
 * applies headers identically.
 *
 * <p>Resolution rules:
 *
 * <ul>
 *   <li>If {@code error} itself implements {@link HttpHeaderCarrier}, its headers are applied.
 *   <li>Else if {@code error} is an {@link Iterable} (including {@code NonEmptyList} and every
 *       {@link Collection}) or array, every element that implements {@link HttpHeaderCarrier}
 *       contributes its headers; values accumulate rather than overwriting one another.
 *   <li>Otherwise nothing is written.
 * </ul>
 *
 * <p>Headers are applied via {@link HttpServletResponse#addHeader(String, String)}, not {@link
 * HttpServletResponse#setHeader(String, String)}. This preserves multi-valued semantics for headers
 * such as {@code WWW-Authenticate}, {@code Set-Cookie}, and {@code Link} (which the HTTP grammar
 * permits to appear multiple times) and avoids overwriting headers set upstream by filters or
 * interceptors.
 *
 * <p>Null keys and null values are skipped silently rather than triggering a servlet container
 * error; the contract on {@link HttpHeaderCarrier#headers()} explicitly permits the empty or null
 * case.
 */
final class ErrorResponseHeaders {

  private ErrorResponseHeaders() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Applies any headers the error payload wishes to surface to the response.
   *
   * @param error the error payload (may be {@code null})
   * @param response the servlet response
   */
  static void applyTo(@Nullable Object error, HttpServletResponse response) {
    if (error == null) {
      return;
    }
    if (error instanceof HttpHeaderCarrier carrier) {
      copy(carrier.headers(), response);
      return;
    }
    // Iterable also covers NonEmptyList, which is Iterable but not a Collection
    if (error instanceof Iterable<?> iterable) {
      for (Object element : iterable) {
        if (element instanceof HttpHeaderCarrier carrier) {
          copy(carrier.headers(), response);
        }
      }
      return;
    }
    if (error instanceof Object[] array) {
      for (Object element : array) {
        if (element instanceof HttpHeaderCarrier carrier) {
          copy(carrier.headers(), response);
        }
      }
    }
  }

  private static void copy(@Nullable Map<String, String> headers, HttpServletResponse response) {
    if (headers == null || headers.isEmpty()) {
      return;
    }
    headers.forEach(
        (name, value) -> {
          if (name != null && value != null) {
            response.addHeader(name, value);
          }
        });
  }
}
