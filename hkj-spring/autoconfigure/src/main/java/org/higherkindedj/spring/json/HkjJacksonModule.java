// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.json;

import com.fasterxml.jackson.databind.module.SimpleModule;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.validated.Validated;

/**
 * Jackson module that registers custom serializers and deserializers for higher-kinded-j types.
 *
 * <p>This module provides JSON serialization support for:
 *
 * <ul>
 *   <li>{@link Either} - Serialized as {"isRight": boolean, "left"|"right": value}
 *   <li>{@link Validated} - Serialized as {"valid": boolean, "value"|"errors": value}
 * </ul>
 *
 * <p>The module is automatically registered when using Spring Boot's auto-configuration. For manual
 * registration:
 *
 * <pre>
 * ObjectMapper mapper = new ObjectMapper();
 * mapper.registerModule(new HkjJacksonModule());
 * </pre>
 *
 * <p><b>Note on Return Value Handlers:</b> When Either or Validated are returned directly from
 * Spring controllers, the {@link org.higherkindedj.spring.web.returnvalue.EitherReturnValueHandler}
 * and {@link org.higherkindedj.spring.web.returnvalue.ValidatedReturnValueHandler} take precedence
 * and provide unwrapped responses for cleaner APIs. These Jackson serializers are primarily useful
 * when Either or Validated appear nested within other response objects.
 */
public class HkjJacksonModule extends SimpleModule {

  private static final long serialVersionUID = 1L;

  @SuppressWarnings({"unchecked", "rawtypes"})
  public HkjJacksonModule() {
    super("HkjJacksonModule");

    // Either serialization/deserialization
    // Raw type cast needed because Either<?, ?> is generic
    addSerializer((Class) Either.class, new EitherSerializer());
    addDeserializer((Class) Either.class, new EitherDeserializer());

    // Validated serialization/deserialization
    // Raw type cast needed because Validated<?, ?> is generic
    addSerializer((Class) Validated.class, new ValidatedSerializer());
    addDeserializer((Class) Validated.class, new ValidatedDeserializer());
  }

  @Override
  public String getModuleName() {
    return "HkjJacksonModule";
  }
}
