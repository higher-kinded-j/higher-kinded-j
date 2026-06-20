// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Builds composite {@link ResponseErrorDecoder}s — in particular a status-dispatching decoder that
 * routes specific HTTP status codes to specific error subtypes, falling back to a default type for
 * everything else.
 *
 * <p>This backs the {@link OnStatus} overrides on a {@link HkjHttpClient} method: the generated
 * client builds the decoder once per call site.
 *
 * <pre>{@code
 * ResponseErrorDecoder<DomainError> decoder =
 *     ResponseErrorDecoders.<DomainError>forDefault(factory, DomainError.class)
 *         .on(404, UserNotFoundError.class)
 *         .on(409, ConflictError.class)
 *         .build();
 * }</pre>
 */
public final class ResponseErrorDecoders {

  private ResponseErrorDecoders() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Starts a status-dispatching decoder whose fallback decodes into {@code defaultType}.
   *
   * @param factory the factory used to build per-type decoders
   * @param defaultType the error type for statuses with no override
   * @param <E> the (super)type of every error this decoder produces
   * @return a builder
   */
  public static <E> Builder<E> forDefault(
      ResponseErrorDecoderFactory factory, Class<E> defaultType) {
    return new Builder<>(factory, defaultType);
  }

  /**
   * Builder for a status-dispatching {@link ResponseErrorDecoder}.
   *
   * @param <E> the (super)type of every error produced
   */
  public static final class Builder<E> {

    private final ResponseErrorDecoderFactory factory;
    private final Class<E> defaultType;
    private final Map<Integer, ResponseErrorDecoder<? extends E>> byStatus = new HashMap<>();

    private Builder(ResponseErrorDecoderFactory factory, Class<E> defaultType) {
      this.factory = Objects.requireNonNull(factory, "factory");
      this.defaultType = Objects.requireNonNull(defaultType, "defaultType");
    }

    /**
     * Routes responses with the given status to a decoder for {@code errorType}.
     *
     * @param status the HTTP status code
     * @param errorType the error subtype to decode for this status
     * @return this builder
     */
    public Builder<E> on(int status, Class<? extends E> errorType) {
      // plain(): an explicit @OnStatus override must bind exactly this type, never be re-routed by
      // the global hkj.client.status-error-mappings.
      byStatus.put(status, factory.plain(errorType));
      return this;
    }

    /**
     * Builds the status-dispatching decoder.
     *
     * @return a decoder that routes by status, falling back to the default type
     */
    public ResponseErrorDecoder<E> build() {
      ResponseErrorDecoder<E> fallback = factory.create(defaultType);
      Map<Integer, ResponseErrorDecoder<? extends E>> overrides = Map.copyOf(byStatus);
      return response -> {
        ResponseErrorDecoder<? extends E> decoder = overrides.get(response.statusValue());
        return decoder != null ? decoder.decode(response) : fallback.decode(response);
      };
    }
  }
}
