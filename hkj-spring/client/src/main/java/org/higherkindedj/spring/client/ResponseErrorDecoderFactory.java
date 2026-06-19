// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client;

/**
 * Builds a {@link ResponseErrorDecoder} for a given error type.
 *
 * <p>Generated clients autowire a single factory bean and build a per-method decoder for the error
 * type each method declares, rather than requiring one decoder bean per error type. The default
 * {@link JsonResponseErrorDecoderFactory} is provided by auto-configuration, backed by the
 * application's Jackson mapper.
 */
@FunctionalInterface
public interface ResponseErrorDecoderFactory {

  /**
   * Creates a decoder that produces errors of the given type. The default implementation may apply
   * a global, configuration-driven status → error-type mapping (see {@code
   * hkj.client.status-error-mappings}).
   *
   * @param errorType the declared error type
   * @param <E> the typed error
   * @return a decoder for {@code errorType}
   */
  <E> ResponseErrorDecoder<E> create(Class<E> errorType);

  /**
   * Creates a decoder for exactly {@code errorType}, ignoring any global status mapping. Used for
   * explicit per-status overrides ({@link OnStatus}) so a per-method mapping always wins over the
   * global baseline. Defaults to {@link #create}.
   *
   * @param errorType the declared error type
   * @param <E> the typed error
   * @return a decoder bound directly to {@code errorType}
   */
  default <E> ResponseErrorDecoder<E> plain(Class<E> errorType) {
    return create(errorType);
  }
}
