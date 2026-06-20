// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatusCode;

/**
 * Thrown by {@link JsonResponseErrorDecoder} when a non-2xx response cannot be decoded into the
 * declared error type — for example an empty body, a body that is not the {@code {"success":false,
 * "error":…}} envelope, or a payload that does not bind to the target type (typically when calling
 * a non-Higher-Kinded-J server).
 *
 * <p>Callers that must talk to such servers should supply a custom {@link ResponseErrorDecoder}
 * that maps the status code to one of their error types instead of relying on the JSON envelope.
 */
public class ResponseErrorDecodeException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient HttpStatusCode status;
  private final @Nullable String responseBody;

  /**
   * Creates a new decode exception.
   *
   * @param status the status code of the response that could not be decoded
   * @param responseBody the raw response body, or {@code null} when absent
   * @param cause the underlying parsing failure, or {@code null}
   */
  public ResponseErrorDecodeException(
      HttpStatusCode status, @Nullable String responseBody, @Nullable Throwable cause) {
    super("Failed to decode error response (status " + status.value() + ")", cause);
    this.status = status;
    this.responseBody = responseBody;
  }

  /**
   * The status code of the response that could not be decoded.
   *
   * @return the HTTP status code
   */
  public HttpStatusCode status() {
    return status;
  }

  /**
   * The raw response body that could not be decoded.
   *
   * @return the response body, or {@code null} when absent
   */
  public @Nullable String responseBody() {
    return responseBody;
  }
}
