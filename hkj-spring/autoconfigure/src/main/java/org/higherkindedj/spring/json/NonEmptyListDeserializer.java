// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.json;

import java.util.ArrayList;
import java.util.List;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * Jackson 3.x deserializer for {@link NonEmptyList} types.
 *
 * <p>Reads a JSON array into a {@code NonEmptyList}. An <b>empty array</b> is rejected — a {@code
 * NonEmptyList} cannot be empty, so {@code []} is a structural error rather than something silently
 * coerced. This keeps the "non-empty by construction" guarantee intact across the wire.
 *
 * <p>When the target generic type is known — a field typed {@code NonEmptyList<Foo>} or a {@code
 * TypeReference<NonEmptyList<Foo>>} — the element type is resolved via {@link
 * #createContextual(DeserializationContext, BeanProperty)} and elements are deserialized to {@code
 * Foo}. For a raw {@code NonEmptyList.class} read the element type is unknown and elements fall
 * back to {@code Object} (numbers/strings round-trip; JSON objects become maps).
 *
 * @see NonEmptyListSerializer
 */
public class NonEmptyListDeserializer extends StdDeserializer<NonEmptyList<?>> {

  private static final long serialVersionUID = 1L;

  /** Element type resolved from the contextual/property type, or {@code null} for a raw read. */
  private final JavaType elementType;

  /** Creates an unresolved deserializer (used for module registration). */
  public NonEmptyListDeserializer() {
    this(null);
  }

  /** Creates a deserializer bound to a resolved element type. */
  @SuppressWarnings("unchecked")
  public NonEmptyListDeserializer(JavaType elementType) {
    super((Class<NonEmptyList<?>>) (Class<?>) NonEmptyList.class);
    this.elementType = elementType;
  }

  @Override
  public ValueDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
    JavaType type = (property != null) ? property.getType() : ctxt.getContextualType();
    if (type != null && type.hasRawClass(NonEmptyList.class) && type.containedTypeCount() == 1) {
      JavaType elem = type.containedType(0);
      if (elem != null && !elem.hasRawClass(Object.class)) {
        return new NonEmptyListDeserializer(elem);
      }
    }
    return this;
  }

  @Override
  public NonEmptyList<?> deserialize(JsonParser p, DeserializationContext ctxt)
      throws JacksonException {
    JsonNode node = p.readValueAsTree();

    if (!node.isArray()) {
      throw ctxt.weirdStringException("", NonEmptyList.class, "NonEmptyList JSON must be an array");
    }
    if (node.isEmpty()) {
      throw ctxt.weirdStringException(
          "", NonEmptyList.class, "NonEmptyList JSON array must not be empty");
    }

    List<Object> elements = new ArrayList<>();
    for (JsonNode element : node) {
      Object value =
          (elementType != null)
              ? ctxt.readTreeAsValue(element, elementType)
              : ctxt.readTreeAsValue(element, Object.class);
      if (value == null) {
        throw ctxt.weirdStringException(
            "", NonEmptyList.class, "NonEmptyList JSON array must not contain null elements");
      }
      elements.add(value);
    }
    return NonEmptyList.of(elements.getFirst(), elements.subList(1, elements.size()));
  }
}
