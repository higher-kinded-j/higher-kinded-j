// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client;

/**
 * Client-side inverse of the server's error-response encoding (status code via {@code
 * ErrorStatusCodeStrategy} plus the {@code {"success":false,"error":…}} body): maps a failed HTTP
 * response back into a typed error {@code E}.
 *
 * <p>Where the server turns a typed error into a status code plus that body, a decoder turns the
 * response back into the error so the typed error channel is preserved across services. Decoders
 * are pluggable as a Spring bean (or built per-call via {@link ResponseErrorDecoderFactory});
 * {@link JsonResponseErrorDecoder} is the default, decoding the {@code error} node of the envelope
 * into the declared error type.
 *
 * @param <E> the typed error this decoder produces
 */
@FunctionalInterface
public interface ResponseErrorDecoder<E> {

  /**
   * Decodes a failed HTTP response into a typed error.
   *
   * @param response the status, body and headers of the failed response
   * @return the decoded typed error (never {@code null})
   */
  E decode(ClientErrorResponse response);
}
