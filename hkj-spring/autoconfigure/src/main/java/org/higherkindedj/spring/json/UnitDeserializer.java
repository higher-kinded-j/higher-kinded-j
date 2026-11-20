// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.json;

import org.higherkindedj.hkt.Unit;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.io.CharTypes;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.deser.std.StdScalarDeserializer;
import tools.jackson.databind.util.AccessPattern;

/**
 * Jackson 3.x deserialiser for {@link Unit}.
 *
 * <p>Reads JSON {@code null}, the form {@link UnitSerializer} writes, as {@link Unit#INSTANCE}, and
 * so reads the {@code null} a {@code Void} success wrote. It also reads the two strings a mapper
 * without this module writes for a {@code Unit}: {@code "()"}, its {@code toString()}, under
 * Jackson 3's defaults, and {@code "INSTANCE"}, its name, under Jackson 2's. Any other value is
 * refused with a {@link tools.jackson.databind.exc.MismatchedInputException} that names the three
 * forms.
 *
 * <p>A record component typed {@code Unit} that the JSON leaves out reads as {@code Unit.INSTANCE}
 * too, since Jackson fills a missing creator property with the deserialiser's null value. A mapper
 * with {@code UNWRAP_SINGLE_VALUE_ARRAYS} reads {@code ["()"]} as it reads {@code "()"}, and one
 * that reads an empty array as null reads {@code []} as {@code Unit.INSTANCE}, as Jackson's own
 * scalars do.
 *
 * @see UnitSerializer
 */
public class UnitDeserializer extends StdScalarDeserializer<Unit> {

  private static final long serialVersionUID = 1L;

  /** How much of a refused string the message shows, so a long one cannot flood a log. */
  private static final int SHOWN = 100;

  /** Creates a new UnitDeserializer for the {@link Unit} type. */
  public UnitDeserializer() {
    super(Unit.class);
  }

  @Override
  public Unit deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
    // Jackson hands most nulls to getNullValue, but a value inside a type id reaches here
    if (p.hasToken(JsonToken.VALUE_NULL)) {
      return Unit.INSTANCE;
    }
    if (p.hasToken(JsonToken.VALUE_STRING)) {
      String text = p.getString();
      if (text.equals(Unit.INSTANCE.toString()) || text.equals(Unit.INSTANCE.name())) {
        return Unit.INSTANCE;
      }
    }
    // An array reads only where the mapper unwraps one, or reads an empty one as null
    if (p.isExpectedStartArrayToken()
        && (ctxt.isEnabled(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)
            || _findCoercionFromEmptyArray(ctxt) != CoercionAction.Fail)) {
      return _deserializeFromArray(p, ctxt);
    }
    return ctxt.reportInputMismatch(
        Unit.class, "Unit JSON must be null, \"()\" or \"INSTANCE\", not %s", found(p));
  }

  /** A JSON {@code null}, or a missing field, reads as {@code Unit.INSTANCE}. */
  @Override
  public Unit getNullValue(DeserializationContext ctxt) {
    return Unit.INSTANCE;
  }

  @Override
  public AccessPattern getNullAccessPattern() {
    return AccessPattern.CONSTANT;
  }

  /**
   * Describes the refused value: a string escaped and quoted, a structure by kind, a scalar as
   * written, and anything else by its token.
   */
  private static String found(JsonParser p) {
    if (p.hasToken(JsonToken.VALUE_STRING)) {
      StringBuilder quoted = new StringBuilder("\"");
      CharTypes.appendQuoted(quoted, shortened(p.getString()));
      return quoted.append('"').toString();
    }
    // Inside a type id's object, the parser has already moved past the opening brace
    if (p.isExpectedStartObjectToken()
        || p.hasToken(JsonToken.PROPERTY_NAME)
        || p.hasToken(JsonToken.END_OBJECT)) {
      return "an object";
    }
    if (p.isExpectedStartArrayToken()) {
      return "an array";
    }
    String text = p.getString();
    return text != null ? shortened(text) : String.valueOf(p.currentToken());
  }

  /** Cuts text longer than {@link #SHOWN}, never between the two halves of a surrogate pair. */
  private static String shortened(String text) {
    if (text.length() <= SHOWN) {
      return text;
    }
    int end = Character.isHighSurrogate(text.charAt(SHOWN - 1)) ? SHOWN - 1 : SHOWN;
    return text.substring(0, end) + "...";
  }
}
