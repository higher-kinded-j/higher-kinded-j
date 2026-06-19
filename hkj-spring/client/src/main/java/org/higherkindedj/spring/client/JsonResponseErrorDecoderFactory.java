// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.json.JsonMapper;

/**
 * Default {@link ResponseErrorDecoderFactory} that produces {@link JsonResponseErrorDecoder}s
 * backed by a shared Jackson {@link JsonMapper}.
 *
 * <p>Optionally applies a global, configuration-driven status → error-type mapping (from {@code
 * hkj.client.status-error-mappings}): for each method's declared error type {@code E}, a configured
 * status whose error type is assignable to {@code E} decodes into that subtype, while other
 * statuses decode into {@code E}. This is the client-side analogue of the server's {@code
 * hkj.web.error-status-mappings}. Per-method {@link OnStatus} overrides take precedence over this
 * global baseline (they use {@link #plain}).
 */
public final class JsonResponseErrorDecoderFactory implements ResponseErrorDecoderFactory {

  private final JsonMapper mapper;
  private final Map<Integer, Class<?>> statusErrorTypes;

  /**
   * Creates a factory with no global status mapping.
   *
   * @param mapper the Jackson 3.x mapper (ideally the application's, carrying {@code
   *     HkjJacksonModule})
   */
  public JsonResponseErrorDecoderFactory(JsonMapper mapper) {
    this(mapper, Map.of());
  }

  /**
   * Creates a factory with a global status → error-type mapping.
   *
   * @param mapper the Jackson 3.x mapper
   * @param statusErrorTypes the global mapping from HTTP status code to error type
   */
  public JsonResponseErrorDecoderFactory(
      JsonMapper mapper, Map<Integer, Class<?>> statusErrorTypes) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
    this.statusErrorTypes =
        Map.copyOf(Objects.requireNonNull(statusErrorTypes, "statusErrorTypes"));
  }

  @Override
  public <E> ResponseErrorDecoder<E> plain(Class<E> errorType) {
    return new JsonResponseErrorDecoder<>(mapper, Objects.requireNonNull(errorType, "errorType"));
  }

  @Override
  public <E> ResponseErrorDecoder<E> create(Class<E> errorType) {
    Objects.requireNonNull(errorType, "errorType");
    ResponseErrorDecoder<E> fallback = plain(errorType);
    if (statusErrorTypes.isEmpty()) {
      return fallback;
    }
    Map<Integer, ResponseErrorDecoder<? extends E>> overrides = new HashMap<>();
    statusErrorTypes.forEach(
        (status, type) -> {
          if (errorType.isAssignableFrom(type)) {
            overrides.put(status, plain(type.asSubclass(errorType)));
          }
        });
    if (overrides.isEmpty()) {
      return fallback;
    }
    Map<Integer, ResponseErrorDecoder<? extends E>> dispatch = Map.copyOf(overrides);
    return response -> {
      ResponseErrorDecoder<? extends E> decoder = dispatch.get(response.statusValue());
      return decoder != null ? decoder.decode(response) : fallback.decode(response);
    };
  }
}
