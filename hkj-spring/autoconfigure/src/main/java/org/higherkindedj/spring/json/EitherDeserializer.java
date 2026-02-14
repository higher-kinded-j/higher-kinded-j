// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.json;

import org.higherkindedj.hkt.either.Either;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * Jackson 3.x deserializer for {@link Either} types.
 *
 * <p>Deserializes JSON objects with the following structure:
 *
 * <pre>
 * // Right value
 * {
 *   "isRight": true,
 *   "right": &lt;value&gt;
 * }
 *
 * // Left value
 * {
 *   "isRight": false,
 *   "left": &lt;error&gt;
 * }
 * </pre>
 *
 * <p>Note: Due to Java's type erasure, the deserializer produces Either&lt;Object, Object&gt;. For
 * strongly-typed deserialization, use custom DTOs or configure Jackson TypeReferences.
 */
public class EitherDeserializer extends StdDeserializer<Either<?, ?>> {

  private static final long serialVersionUID = 1L;

  @SuppressWarnings("unchecked")
  public EitherDeserializer() {
    super((Class<Either<?, ?>>) (Class<?>) Either.class);
  }

  @Override
  public Either<?, ?> deserialize(JsonParser p, DeserializationContext ctxt)
      throws JacksonException {
    JsonNode node = p.readValueAsTree();

    if (!node.has("isRight")) {
      throw ctxt.weirdStringException("", Either.class, "Either JSON must have 'isRight' field");
    }

    boolean isRight = node.get("isRight").asBoolean();

    if (isRight) {
      if (!node.has("right")) {
        throw ctxt.weirdStringException(
            "", Either.class, "Either with isRight=true must have 'right' field");
      }
      Object value = ctxt.readTreeAsValue(node.get("right"), Object.class);
      return Either.right(value);
    } else {
      if (!node.has("left")) {
        throw ctxt.weirdStringException(
            "", Either.class, "Either with isRight=false must have 'left' field");
      }
      Object error = ctxt.readTreeAsValue(node.get("left"), Object.class);
      return Either.left(error);
    }
  }
}
