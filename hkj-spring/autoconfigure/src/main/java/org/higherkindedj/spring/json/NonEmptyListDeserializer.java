// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.json;

import java.util.ArrayList;
import java.util.List;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * Jackson 3.x deserializer for {@link NonEmptyList} types.
 *
 * <p>Reads a JSON array into a {@code NonEmptyList}. An <b>empty array</b> is rejected — a {@code
 * NonEmptyList} cannot be empty, so {@code []} is a structural error rather than something silently
 * coerced. This keeps the "non-empty by construction" guarantee intact across the wire.
 *
 * <p>Note: due to type erasure this produces {@code NonEmptyList<Object>}; for strongly-typed
 * results use a DTO or a Jackson {@code TypeReference}.
 *
 * @see NonEmptyListSerializer
 */
public class NonEmptyListDeserializer extends StdDeserializer<NonEmptyList<?>> {

  private static final long serialVersionUID = 1L;

  /** Creates a new NonEmptyListDeserializer for the NonEmptyList type. */
  @SuppressWarnings("unchecked")
  public NonEmptyListDeserializer() {
    super((Class<NonEmptyList<?>>) (Class<?>) NonEmptyList.class);
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
      Object value = ctxt.readTreeAsValue(element, Object.class);
      if (value == null) {
        throw ctxt.weirdStringException(
            "", NonEmptyList.class, "NonEmptyList JSON array must not contain null elements");
      }
      elements.add(value);
    }
    return NonEmptyList.of(elements.get(0), elements.subList(1, elements.size()));
  }
}
