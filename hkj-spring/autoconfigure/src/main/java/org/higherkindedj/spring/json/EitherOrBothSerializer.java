// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.json;

import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Jackson 3.x serializer for {@link EitherOrBoth} (the inclusive-or).
 *
 * <p>Serializes values with a tag-based {@code kind} discriminator, since {@code EitherOrBoth} has
 * three cases:
 *
 * <pre>
 * { "kind": "left",  "left":  &lt;warnings&gt; }
 * { "kind": "right", "right": &lt;value&gt; }
 * { "kind": "both",  "left":  &lt;warnings&gt;, "right": &lt;value&gt; }
 * </pre>
 *
 * <p>This is useful when {@code EitherOrBoth} values appear nested within other objects. For
 * top-level controller return values the {@link
 * org.higherkindedj.spring.web.returnvalue.EitherOrBothPathReturnValueHandler} provides an
 * unwrapped response shaped for HTTP.
 */
public class EitherOrBothSerializer extends StdSerializer<EitherOrBoth<?, ?>> {

  private static final long serialVersionUID = 1L;

  /** Creates a new serializer for {@link EitherOrBoth} types. */
  @SuppressWarnings("unchecked")
  public EitherOrBothSerializer() {
    super((Class<EitherOrBoth<?, ?>>) (Class<?>) EitherOrBoth.class);
  }

  @Override
  public void serialize(EitherOrBoth<?, ?> value, JsonGenerator gen, SerializationContext ctxt) {
    value.<Void>fold(
        left -> {
          gen.writeStartObject();
          gen.writeName("kind");
          gen.writeString("left");
          gen.writeName("left");
          gen.writePOJO(left);
          gen.writeEndObject();
          return null;
        },
        right -> {
          gen.writeStartObject();
          gen.writeName("kind");
          gen.writeString("right");
          gen.writeName("right");
          gen.writePOJO(right);
          gen.writeEndObject();
          return null;
        },
        (left, right) -> {
          gen.writeStartObject();
          gen.writeName("kind");
          gen.writeString("both");
          gen.writeName("left");
          gen.writePOJO(left);
          gen.writeName("right");
          gen.writePOJO(right);
          gen.writeEndObject();
          return null;
        });
  }
}
