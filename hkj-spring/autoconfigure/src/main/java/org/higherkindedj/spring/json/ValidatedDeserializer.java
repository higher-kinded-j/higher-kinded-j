// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.json;

import org.higherkindedj.hkt.validated.Validated;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
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
 * <p>When the target generic type is known — a field typed {@code Validated<E, A>} or a {@code
 * TypeReference<Validated<E, A>>} — the {@code errors} and {@code value} types are resolved via
 * {@link #createContextual(DeserializationContext, BeanProperty)} and the present branch is
 * deserialized to it. For a raw {@code Validated.class} read the branch types are unknown and fall
 * back to {@code Object} (numbers/strings round-trip; JSON objects become maps).
 */
public class ValidatedDeserializer extends StdDeserializer<Validated<?, ?>> {

  private static final long serialVersionUID = 1L;

  /**
   * Error/value types resolved from the contextual/property type, or {@code null} for a raw read.
   */
  private final JavaType errorType;

  private final JavaType valueType;

  /** Creates an unresolved deserializer (used for module registration). */
  public ValidatedDeserializer() {
    this(null, null);
  }

  /** Creates a deserializer bound to resolved error/value types. */
  @SuppressWarnings("unchecked")
  public ValidatedDeserializer(JavaType errorType, JavaType valueType) {
    super((Class<Validated<?, ?>>) (Class<?>) Validated.class);
    this.errorType = errorType;
    this.valueType = valueType;
  }

  @Override
  public ValueDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
    JavaType type = (property != null) ? property.getType() : ctxt.getContextualType();
    if (type != null && type.hasRawClass(Validated.class) && type.containedTypeCount() == 2) {
      return new ValidatedDeserializer(type.containedType(0), type.containedType(1));
    }
    return this;
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
      return Validated.valid(readAs(ctxt, node.get("value"), valueType));
    } else {
      if (!node.has("errors")) {
        throw ctxt.weirdStringException(
            "", Validated.class, "Validated with valid=false must have 'errors' field");
      }
      return Validated.invalid(readAs(ctxt, node.get("errors"), errorType));
    }
  }

  /** Reads {@code node} to the resolved {@code type} if known, otherwise to {@code Object}. */
  private static Object readAs(DeserializationContext ctxt, JsonNode node, JavaType type) {
    return (type != null)
        ? ctxt.readTreeAsValue(node, type)
        : ctxt.readTreeAsValue(node, Object.class);
  }
}
