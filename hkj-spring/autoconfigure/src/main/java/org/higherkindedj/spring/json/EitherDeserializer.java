// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import org.higherkindedj.hkt.either.Either;

/**
 * Jackson deserializer for {@link Either} types.
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
public class EitherDeserializer extends JsonDeserializer<Either<?, ?>> {

  @Override
  public Either<?, ?> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    JsonNode node = p.getCodec().readTree(p);

    if (!node.has("isRight")) {
      throw new IOException("Either JSON must have 'isRight' field");
    }

    boolean isRight = node.get("isRight").asBoolean();

    if (isRight) {
      if (!node.has("right")) {
        throw new IOException("Either with isRight=true must have 'right' field");
      }
      Object value = ctxt.readTreeAsValue(node.get("right"), Object.class);
      return Either.right(value);
    } else {
      if (!node.has("left")) {
        throw new IOException("Either with isRight=false must have 'left' field");
      }
      Object error = ctxt.readTreeAsValue(node.get("left"), Object.class);
      return Either.left(error);
    }
  }
}
