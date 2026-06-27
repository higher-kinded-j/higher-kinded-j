// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.json;

import org.higherkindedj.hkt.either.Either;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
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
 * <p>When the target generic type is known — a field typed {@code Either<L, R>} or a {@code
 * TypeReference<Either<L, R>>} — the {@code left} and {@code right} types are resolved via {@link
 * #createContextual(DeserializationContext, BeanProperty)} and the present branch is deserialized
 * to it. For a raw {@code Either.class} read the branch types are unknown and fall back to {@code
 * Object} (numbers/strings round-trip; JSON objects become maps).
 */
public class EitherDeserializer extends StdDeserializer<Either<?, ?>> {

  private static final long serialVersionUID = 1L;

  /**
   * Left/right types resolved from the contextual/property type, or {@code null} for a raw read.
   */
  private final JavaType leftType;

  private final JavaType rightType;

  /** Creates an unresolved deserializer (used for module registration). */
  public EitherDeserializer() {
    this(null, null);
  }

  /** Creates a deserializer bound to resolved left/right types. */
  @SuppressWarnings("unchecked")
  public EitherDeserializer(JavaType leftType, JavaType rightType) {
    super((Class<Either<?, ?>>) (Class<?>) Either.class);
    this.leftType = leftType;
    this.rightType = rightType;
  }

  @Override
  public ValueDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
    JavaType type = (property != null) ? property.getType() : ctxt.getContextualType();
    if (type != null && type.hasRawClass(Either.class) && type.containedTypeCount() == 2) {
      return new EitherDeserializer(type.containedType(0), type.containedType(1));
    }
    return this;
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
      return Either.right(readAs(ctxt, node.get("right"), rightType));
    } else {
      if (!node.has("left")) {
        throw ctxt.weirdStringException(
            "", Either.class, "Either with isRight=false must have 'left' field");
      }
      return Either.left(readAs(ctxt, node.get("left"), leftType));
    }
  }

  /** Reads {@code node} to the resolved {@code type} if known, otherwise to {@code Object}. */
  private static Object readAs(DeserializationContext ctxt, JsonNode node, JavaType type) {
    return (type != null)
        ? ctxt.readTreeAsValue(node, type)
        : ctxt.readTreeAsValue(node, Object.class);
  }
}
