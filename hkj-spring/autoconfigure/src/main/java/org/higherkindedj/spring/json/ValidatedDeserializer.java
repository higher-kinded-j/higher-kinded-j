// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import org.higherkindedj.hkt.validated.Validated;

/**
 * Jackson deserializer for {@link Validated} types.
 *
 * <p>Deserializes JSON objects with the following structure:
 *
 * <pre>
 * // Valid value
 * {
 *   "valid": true,
 *   "value": &lt;value&gt;
 * }
 *
 * // Invalid value
 * {
 *   "valid": false,
 *   "errors": &lt;errors&gt;
 * }
 * </pre>
 *
 * <p>Note: Due to Java's type erasure, the deserializer produces Validated&lt;Object, Object&gt;.
 * For strongly-typed deserialization, use custom DTOs or configure Jackson TypeReferences.
 */
public class ValidatedDeserializer extends JsonDeserializer<Validated<?, ?>> {

  @Override
  public Validated<?, ?> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    JsonNode node = p.getCodec().readTree(p);

    if (!node.has("valid")) {
      throw new IOException("Validated JSON must have 'valid' field");
    }

    boolean isValid = node.get("valid").asBoolean();

    if (isValid) {
      if (!node.has("value")) {
        throw new IOException("Validated with valid=true must have 'value' field");
      }
      Object value = ctxt.readTreeAsValue(node.get("value"), Object.class);
      return Validated.valid(value);
    } else {
      if (!node.has("errors")) {
        throw new IOException("Validated with valid=false must have 'errors' field");
      }
      Object errors = ctxt.readTreeAsValue(node.get("errors"), Object.class);
      return Validated.invalid(errors);
    }
  }
}
