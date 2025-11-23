// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import org.higherkindedj.hkt.validated.Validated;

/**
 * Jackson serializer for {@link Validated} types.
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
 * org.higherkindedj.spring.web.returnvalue.ValidatedReturnValueHandler} produces, ensuring
 * consistency across nested and top-level Validated values.
 */
public class ValidatedSerializer extends JsonSerializer<Validated<?, ?>> {

  @Override
  public void serialize(Validated<?, ?> value, JsonGenerator gen, SerializerProvider serializers)
      throws IOException {
    value.fold(
        errors -> {
          try {
            gen.writeStartObject();
            gen.writeBooleanField("valid", false);
            gen.writeObjectField("errors", errors);
            gen.writeEndObject();
          } catch (IOException e) {
            throw new RuntimeException("Failed to serialize Validated.Invalid", e);
          }
          return null;
        },
        valid -> {
          try {
            gen.writeStartObject();
            gen.writeBooleanField("valid", true);
            gen.writeObjectField("value", valid);
            gen.writeEndObject();
          } catch (IOException e) {
            throw new RuntimeException("Failed to serialize Validated.Valid", e);
          }
          return null;
        });
  }
}
