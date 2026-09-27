// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.json;

import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.Validated;
import tools.jackson.databind.module.SimpleModule;

/**
 * Jackson 3.x module that registers custom serialisers and deserialisers for higher-kinded-j types.
 *
 * <p>This module provides JSON serialisation support for:
 *
 * <ul>
 *   <li>{@link Either} - Serialised as {"isRight": boolean, "left"|"right": value}
 *   <li>{@link Validated} - Serialised as {"valid": boolean, "value"|"errors": value}
 *   <li>{@link EitherOrBoth} - Serialised as {"kind": "left"|"right"|"both", "left"?, "right"?}
 *   <li>{@link NonEmptyList} - Serialised as a JSON array; an empty array is rejected on read
 * </ul>
 *
 * <p>The module is automatically registered when using Spring Boot's auto-configuration. For manual
 * registration with Jackson 3.x:
 *
 * <pre>
 * JsonMapper mapper = JsonMapper.builder()
 *     .addModule(new HkjJacksonModule())
 *     .build();
 * </pre>
 *
 * <p><b>Note on Return Value Handlers:</b> When Effect Path types (EitherPath, ValidationPath,
 * etc.) are returned directly from Spring controllers, the Path-based return value handlers take
 * precedence and provide unwrapped responses for cleaner APIs. These Jackson serialisers are
 * primarily useful when Either or Validated appear nested within other response objects.
 */
public class HkjJacksonModule extends SimpleModule {

  private static final long serialVersionUID = 1L;

  /** Creates a new HkjJacksonModule and registers serialisers for HKJ types. */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public HkjJacksonModule() {
    super("HkjJacksonModule");

    // Either serialisation/deserialisation
    // Raw type cast needed because Either<?, ?> is generic
    addSerializer((Class) Either.class, new EitherSerializer());
    addDeserializer((Class) Either.class, new EitherDeserializer());

    // Validated serialisation/deserialisation
    // Raw type cast needed because Validated<?, ?> is generic
    addSerializer((Class) Validated.class, new ValidatedSerializer());
    addDeserializer((Class) Validated.class, new ValidatedDeserializer());

    // EitherOrBoth (inclusive-or) serialisation/deserialisation
    // Raw type cast needed because EitherOrBoth<?, ?> is generic
    addSerializer((Class) EitherOrBoth.class, new EitherOrBothSerializer());
    addDeserializer((Class) EitherOrBoth.class, new EitherOrBothDeserializer());

    // NonEmptyList serialisation/deserialisation
    // Raw type cast needed because NonEmptyList<?> is generic
    addSerializer((Class) NonEmptyList.class, new NonEmptyListSerializer());
    addDeserializer((Class) NonEmptyList.class, new NonEmptyListDeserializer());
  }

  @Override
  public String getModuleName() {
    return "HkjJacksonModule";
  }
}
