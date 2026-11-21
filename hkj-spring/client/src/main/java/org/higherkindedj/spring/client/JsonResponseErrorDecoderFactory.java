// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
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
 *
 * <p>Neither the mapping nor {@link #create} or {@link #plain} takes {@code Void} or {@code void}:
 * no {@code Left} can hold one, so the factory and the decoder refuse it when built, with {@link
 * IllegalArgumentException}. {@code Unit} is the error type that carries nothing.
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
   * @throws IllegalArgumentException if a status maps to {@code Void}, {@code void} or another
   *     primitive
   */
  public JsonResponseErrorDecoderFactory(
      JsonMapper mapper, Map<Integer, Class<?>> statusErrorTypes) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
    this.statusErrorTypes =
        requireNoPrimitive(
            requireNoVoid(
                Map.copyOf(Objects.requireNonNull(statusErrorTypes, "statusErrorTypes"))));
  }

  private static Map<Integer, Class<?>> requireNoVoid(Map<Integer, Class<?>> statusErrorTypes) {
    statusErrorTypes.entrySet().stream()
        .filter(entry -> JsonResponseErrorDecoder.holdsNoValue(entry.getValue()))
        .findFirst()
        .ifPresent(
            entry -> {
              throw new IllegalArgumentException(
                  "JsonResponseErrorDecoderFactory: status "
                      + entry.getKey()
                      + " maps to error type "
                      + entry.getValue().getSimpleName()
                      + ", which cannot hold a value, but a Left always holds one. Map it to"
                      + " org.higherkindedj.hkt.Unit, the error type that carries nothing, or"
                      + " remove the mapping.");
            });
    return statusErrorTypes;
  }

  private static Map<Integer, Class<?>> requireNoPrimitive(
      Map<Integer, Class<?>> statusErrorTypes) {
    statusErrorTypes.entrySet().stream()
        .filter(entry -> entry.getValue().isPrimitive())
        .findFirst()
        .ifPresent(
            entry -> {
              throw new IllegalArgumentException(
                  "JsonResponseErrorDecoderFactory: status "
                      + entry.getKey()
                      + " maps to the primitive "
                      + entry.getValue().getName()
                      + ", which no error can be. Map it to an error class, or remove the"
                      + " mapping.");
            });
    return statusErrorTypes;
  }

  @Override
  public <E> ResponseErrorDecoder<E> plain(Class<E> errorType) {
    return new JsonResponseErrorDecoder<>(mapper, Objects.requireNonNull(errorType, "errorType"));
  }

  @Override
  public <E> ResponseErrorDecoder<E> create(Class<E> errorType) {
    Objects.requireNonNull(errorType, "errorType");
    ResponseErrorDecoder<E> fallback = plain(errorType);
    // Keep only the configured statuses whose error type narrows to E; an empty map (no global
    // mapping, or none assignable) means every status decodes into the declared type.
    Map<Integer, ResponseErrorDecoder<? extends E>> dispatch =
        statusErrorTypes.entrySet().stream()
            .filter(entry -> errorType.isAssignableFrom(entry.getValue()))
            .collect(
                Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, entry -> subtypeDecoder(entry.getValue(), errorType)));
    if (dispatch.isEmpty()) {
      return fallback;
    }
    return response -> {
      ResponseErrorDecoder<? extends E> decoder = dispatch.get(response.statusValue());
      return decoder != null ? decoder.decode(response) : fallback.decode(response);
    };
  }

  /** A {@link #plain} decoder for {@code type} narrowed as a subtype of {@code errorType}. */
  private <E> ResponseErrorDecoder<? extends E> subtypeDecoder(Class<?> type, Class<E> errorType) {
    return plain(type.asSubclass(errorType));
  }
}
