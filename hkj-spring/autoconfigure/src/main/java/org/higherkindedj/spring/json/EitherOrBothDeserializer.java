// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.json;

import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * Jackson 3.x deserializer for {@link EitherOrBoth} (the inclusive-or).
 *
 * <p>Reads the tag-based form produced by {@link EitherOrBothSerializer}:
 *
 * <pre>
 * { "kind": "left",  "left":  &lt;warnings&gt; }
 * { "kind": "right", "right": &lt;value&gt; }
 * { "kind": "both",  "left":  &lt;warnings&gt;, "right": &lt;value&gt; }
 * </pre>
 *
 * <p>When the target generic type is known (a field typed {@code EitherOrBoth<L, R>} or a {@code
 * TypeReference<EitherOrBoth<L, R>>}), the {@code left} and {@code right} types are resolved via
 * {@link #createContextual(DeserializationContext, BeanProperty)}. For a raw {@code
 * EitherOrBoth.class} read the branch types are unknown and fall back to {@code Object}.
 */
public class EitherOrBothDeserializer extends StdDeserializer<EitherOrBoth<?, ?>> {

  private static final long serialVersionUID = 1L;

  /**
   * Left/right types resolved from the contextual/property type, or {@code null} for a raw read.
   */
  private final JavaType leftType;

  private final JavaType rightType;

  /** Creates an unresolved deserializer (used for module registration). */
  public EitherOrBothDeserializer() {
    this(null, null);
  }

  /** Creates a deserializer bound to resolved left/right types. */
  @SuppressWarnings("unchecked")
  public EitherOrBothDeserializer(JavaType leftType, JavaType rightType) {
    super((Class<EitherOrBoth<?, ?>>) (Class<?>) EitherOrBoth.class);
    this.leftType = leftType;
    this.rightType = rightType;
  }

  @Override
  public ValueDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
    JavaType type = (property != null) ? property.getType() : ctxt.getContextualType();
    if (type != null && type.hasRawClass(EitherOrBoth.class) && type.containedTypeCount() == 2) {
      return new EitherOrBothDeserializer(type.containedType(0), type.containedType(1));
    }
    return this;
  }

  @Override
  public EitherOrBoth<?, ?> deserialize(JsonParser p, DeserializationContext ctxt)
      throws JacksonException {
    JsonNode node = p.readValueAsTree();

    if (!node.has("kind")) {
      throw ctxt.weirdStringException(
          "", EitherOrBoth.class, "EitherOrBoth JSON must have a 'kind' field");
    }

    String kind = node.get("kind").asString();
    return switch (kind) {
      case "left" -> EitherOrBoth.left(readField(ctxt, node, "left", leftType));
      case "right" -> EitherOrBoth.right(readField(ctxt, node, "right", rightType));
      case "both" ->
          EitherOrBoth.both(
              readField(ctxt, node, "left", leftType), readField(ctxt, node, "right", rightType));
      default ->
          throw ctxt.weirdStringException(
              kind, EitherOrBoth.class, "EitherOrBoth 'kind' must be left, right or both");
    };
  }

  /** Reads a required field to the resolved {@code type} if known, otherwise to {@code Object}. */
  private static Object readField(
      DeserializationContext ctxt, JsonNode node, String field, JavaType type) {
    if (!node.has(field)) {
      throw ctxt.weirdStringException(
          "", EitherOrBoth.class, "EitherOrBoth is missing required '" + field + "' field");
    }
    JsonNode child = node.get(field);
    return (type != null)
        ? ctxt.readTreeAsValue(child, type)
        : ctxt.readTreeAsValue(child, Object.class);
  }
}
