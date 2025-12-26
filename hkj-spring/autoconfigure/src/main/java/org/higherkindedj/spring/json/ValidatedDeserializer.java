// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.json;

import org.higherkindedj.hkt.validated.Validated;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * Jackson 3.x deserializer for {@link Validated} types.
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
public class ValidatedDeserializer extends StdDeserializer<Validated<?, ?>> {

  private static final long serialVersionUID = 1L;

  @SuppressWarnings("unchecked")
  public ValidatedDeserializer() {
    super((Class<Validated<?, ?>>) (Class<?>) Validated.class);
  }

  @Override
  public Validated<?, ?> deserialize(JsonParser p, DeserializationContext ctxt)
      throws JacksonException {
    JsonNode node = p.readValueAsTree();

    if (!node.has("valid")) {
      throw ctxt.weirdStringException(
          "", Validated.class, "Validated JSON must have 'valid' field");
    }

    boolean isValid = node.get("valid").asBoolean();

    if (isValid) {
      if (!node.has("value")) {
        throw ctxt.weirdStringException(
            "", Validated.class, "Validated with valid=true must have 'value' field");
      }
      Object value = ctxt.readTreeAsValue(node.get("value"), Object.class);
      return Validated.valid(value);
    } else {
      if (!node.has("errors")) {
        throw ctxt.weirdStringException(
            "", Validated.class, "Validated with valid=false must have 'errors' field");
      }
      Object errors = ctxt.readTreeAsValue(node.get("errors"), Object.class);
      return Validated.invalid(errors);
    }
  }
}
