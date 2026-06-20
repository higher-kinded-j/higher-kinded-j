// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client;

import java.util.Objects;
import java.util.Optional;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.trymonad.Try;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Default {@link ResponseErrorDecoder}: decodes the server's {@code {"success":false,"error":…}}
 * error envelope into a declared error type using a Jackson {@link JsonMapper}.
 *
 * <p>The {@code error} node of the envelope is bound to {@code errorType}. If the body is not the
 * envelope shape, the whole body is attempted as {@code errorType} (so a bare error object also
 * works). A concrete {@code errorType} binds directly; a sealed-interface error type needs Jackson
 * polymorphic type information ({@code @JsonTypeInfo}/{@code @JsonSubTypes}) on the hierarchy so
 * Jackson can pick the subtype. Anything that cannot be bound raises {@link
 * ResponseErrorDecodeException}.
 *
 * @param <E> the typed error this decoder produces
 */
public final class JsonResponseErrorDecoder<E> implements ResponseErrorDecoder<E> {

  private final JsonMapper mapper;
  private final Class<E> errorType;

  /**
   * Creates a decoder that binds the response error to {@code errorType}.
   *
   * @param mapper the Jackson 3.x mapper (ideally the application's, carrying {@code
   *     HkjJacksonModule})
   * @param errorType the declared error type to bind the {@code error} node to
   */
  public JsonResponseErrorDecoder(JsonMapper mapper, Class<E> errorType) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
    this.errorType = Objects.requireNonNull(errorType, "errorType");
  }

  @Override
  public E decode(ClientErrorResponse response) {
    String body = response.body();
    if (body == null || body.isBlank()) {
      throw new ResponseErrorDecodeException(response.status(), body, null);
    }
    // Bind in a Try so a Jackson failure becomes a Left rather than a caught-and-rewrapped
    // exception; a null binding (a JSON `null` error node) is its own Left. fold() then either
    // returns the typed error or raises the decode failure, honouring the non-null decode contract.
    return Try.of(() -> bind(body))
        .toEither(failure -> new ResponseErrorDecodeException(response.status(), body, failure))
        .flatMap(
            decoded ->
                decoded == null
                    ? Either.<ResponseErrorDecodeException, E>left(
                        new ResponseErrorDecodeException(response.status(), body, null))
                    : Either.<ResponseErrorDecodeException, E>right(decoded))
        .fold(
            failure -> {
              throw failure;
            },
            decoded -> decoded);
  }

  /** Binds the {@code error} node of the envelope (or the whole body) to {@code errorType}. */
  private E bind(String body) {
    JsonNode root = mapper.readTree(body);
    JsonNode target = Optional.ofNullable(root.get("error")).orElse(root);
    return mapper.treeToValue(target, errorType);
  }
}
