// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.context;

import java.util.Objects;

/** Context for validating Kind-related operations */
public record KindContext(Class<?> targetType, String operation) implements ValidationContext {

  public KindContext {
    Objects.requireNonNull(targetType, "targetType cannot be null");
    Objects.requireNonNull(operation, "operation cannot be null");
  }

  public static KindContext narrow(Class<?> targetType) {
    return new KindContext(targetType, "narrow");
  }

  public static KindContext widen(Class<?> targetType) {
    return new KindContext(targetType, "widen");
  }

  @Override
  public String nullParameterMessage() {
    return "Cannot %s null Kind for %s".formatted(operation, targetType.getSimpleName());
  }

  @Override
  public String nullInputMessage() {
    return "Input %s cannot be null for %s".formatted(targetType.getSimpleName(), operation);
  }

  @Override
  public String customMessage(String template, Object... args) {
    return String.format(template, args);
  }

  public String invalidTypeMessage(String actualClassName) {
    return "Kind instance is not a %s: %s".formatted(targetType.getSimpleName(), actualClassName);
  }
}
