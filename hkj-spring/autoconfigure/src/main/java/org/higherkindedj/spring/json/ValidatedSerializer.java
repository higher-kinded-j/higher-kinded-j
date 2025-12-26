// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.json;

import org.higherkindedj.hkt.validated.Validated;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Jackson 3.x serializer for {@link Validated} types.
 *
 * <p>Serializes Validated values as JSON objects with the following structure:
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
 * <p>This format matches what the {@link
 * org.higherkindedj.spring.web.returnvalue.ValidationPathReturnValueHandler} produces, ensuring
 * consistency across nested and top-level Validated values.
 */
public class ValidatedSerializer extends StdSerializer<Validated<?, ?>> {

  private static final long serialVersionUID = 1L;

  @SuppressWarnings("unchecked")
  public ValidatedSerializer() {
    super((Class<Validated<?, ?>>) (Class<?>) Validated.class);
  }

  @Override
  public void serialize(Validated<?, ?> value, JsonGenerator gen, SerializationContext ctxt) {
    value.fold(
        errors -> {
          gen.writeStartObject();
          gen.writeName("valid");
          gen.writeBoolean(false);
          gen.writeName("errors");
          gen.writePOJO(errors);
          gen.writeEndObject();
          return null;
        },
        valid -> {
          gen.writeStartObject();
          gen.writeName("valid");
          gen.writeBoolean(true);
          gen.writeName("value");
          gen.writePOJO(valid);
          gen.writeEndObject();
          return null;
        });
  }
}
