// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.json;

import org.higherkindedj.hkt.either.Either;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

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
 * org.higherkindedj.spring.web.returnvalue.EitherPathReturnValueHandler} provides a cleaner
 * unwrapped format using the Effect Path API.
 */
public class EitherSerializer extends StdSerializer<Either<?, ?>> {

  private static final long serialVersionUID = 1L;

  @SuppressWarnings("unchecked")
  public EitherSerializer() {
    super((Class<Either<?, ?>>) (Class<?>) Either.class);
  }

  @Override
  public void serialize(Either<?, ?> value, JsonGenerator gen, SerializationContext ctxt) {
    value.fold(
        left -> {
          gen.writeStartObject();
          gen.writeName("isRight");
          gen.writeBoolean(false);
          gen.writeName("left");
          gen.writePOJO(left);
          gen.writeEndObject();
          return null;
        },
        right -> {
          gen.writeStartObject();
          gen.writeName("isRight");
          gen.writeBoolean(true);
          gen.writeName("right");
          gen.writePOJO(right);
          gen.writeEndObject();
          return null;
        });
  }
}
