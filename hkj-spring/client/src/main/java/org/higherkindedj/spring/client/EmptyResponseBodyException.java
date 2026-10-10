// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClientException;

/**
 * Raised when a 2xx response has no body but the declared success type needs one. A {@code Right}
 * always holds a value, so {@link HkjClientExchange#either} and {@link
 * HkjClientExchange#eitherVTask} cannot turn an empty body into one.
 *
 * <p>The request has already been made when this is raised, so its effect on the server stands. A
 * retry policy that retries every throwable would send it again: exclude this type from one, and
 * read {@link #headers()} for anything the response carried, such as a {@code Location}. The
 * lasting fix is the declaration: {@code MaybePath<T>} where the body may be missing, or {@code
 * Unit} as the success type where the endpoint sends none.
 */
public class EmptyResponseBodyException extends RestClientException {

  private static final long serialVersionUID = 1L;

  private final HttpStatusCode statusCode;
  private final HttpHeaders headers;

  /**
   * Creates a new exception for a 2xx response that had no body.
   *
   * @param statusCode the response's status
   * @param headers the response's headers
   */
  public EmptyResponseBodyException(HttpStatusCode statusCode, HttpHeaders headers) {
    super(
        "The "
            + statusCode.value()
            + " response had no body, but a Right needs a value: declare the method MaybePath<T>"
            + " if the body may be missing, or use Unit as the success type if the endpoint sends"
            + " none");
    this.statusCode = statusCode;
    this.headers = headers;
  }

  /**
   * The status of the response that had no body.
   *
   * @return the status
   */
  public HttpStatusCode statusCode() {
    return statusCode;
  }

  /**
   * The headers of the response that had no body.
   *
   * @return the headers
   */
  public HttpHeaders headers() {
    return headers;
  }
}
