// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.json;

import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Jackson 3.x serializer for {@link NonEmptyList} types.
 *
 * <p>Serializes a {@code NonEmptyList} as a plain JSON array of its elements, in order ({@code
 * head} followed by {@code tail}). For example {@code NonEmptyList.of("a", "b")} becomes {@code
 * ["a", "b"]}. This is the natural wire shape for a {@code NonEmptyList<Error>} accumulating-error
 * channel crossing an HTTP boundary.
 *
 * @see NonEmptyListDeserializer
 */
public class NonEmptyListSerializer extends StdSerializer<NonEmptyList<?>> {

  private static final long serialVersionUID = 1L;

  /** Creates a new NonEmptyListSerializer for the NonEmptyList type. */
  @SuppressWarnings("unchecked")
  public NonEmptyListSerializer() {
    super((Class<NonEmptyList<?>>) (Class<?>) NonEmptyList.class);
  }

  @Override
  public void serialize(NonEmptyList<?> value, JsonGenerator gen, SerializationContext ctxt) {
    gen.writeStartArray();
    for (Object element : value.toJavaList()) {
      gen.writePOJO(element);
    }
    gen.writeEndArray();
  }
}
