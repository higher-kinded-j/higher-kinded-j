// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import org.higherkindedj.hkt.either.Either;

/**
 * Jackson serializer for {@link Either} types.
 *
 * <p>Serializes Either values as JSON objects with the following structure:
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
 * <p>This serializer is useful when Either values appear nested within other objects. For top-level
 * controller return values, the {@link
 * org.higherkindedj.spring.web.returnvalue.EitherReturnValueHandler} provides a cleaner unwrapped
 * format.
 */
public class EitherSerializer extends JsonSerializer<Either<?, ?>> {

  @Override
  public void serialize(Either<?, ?> value, JsonGenerator gen, SerializerProvider serializers)
      throws IOException {
    value.fold(
        left -> {
          try {
            gen.writeStartObject();
            gen.writeBooleanField("isRight", false);
            gen.writeObjectField("left", left);
            gen.writeEndObject();
          } catch (IOException e) {
            throw new RuntimeException("Failed to serialize Either.Left", e);
          }
          return null;
        },
        right -> {
          try {
            gen.writeStartObject();
            gen.writeBooleanField("isRight", true);
            gen.writeObjectField("right", right);
            gen.writeEndObject();
          } catch (IOException e) {
            throw new RuntimeException("Failed to serialize Either.Right", e);
          }
          return null;
        });
  }
}
