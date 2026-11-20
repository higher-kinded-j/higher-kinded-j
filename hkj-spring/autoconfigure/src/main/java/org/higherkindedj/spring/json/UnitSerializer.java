// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.json;

import org.higherkindedj.hkt.Unit;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import tools.jackson.databind.ser.std.StdScalarSerializer;

/**
 * Jackson 3.x serialiser for {@link Unit}.
 *
 * <p>Writes JSON {@code null}, whatever the mapper's enum settings. A {@code Unit} carries no
 * information, so it has nothing to write, and {@code null} is the form a {@code Void} success had:
 * moving a field typed {@code Either<E, Void>} to {@code Either<E, Unit>} leaves its JSON as it
 * was.
 *
 * @see UnitDeserializer
 */
public class UnitSerializer extends StdScalarSerializer<Unit> {

  private static final long serialVersionUID = 1L;

  /** Creates a new UnitSerializer for the {@link Unit} type. */
  public UnitSerializer() {
    super(Unit.class);
  }

  @Override
  public void serialize(Unit value, JsonGenerator gen, SerializationContext ctxt) {
    gen.writeNull();
  }

  /** Describes a {@code Unit} as JSON null to a schema generator, as it is written. */
  @Override
  public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType typeHint) {
    visitor.expectNullFormat(typeHint);
  }
}
