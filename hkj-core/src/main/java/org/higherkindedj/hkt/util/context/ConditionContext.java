// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.context;

import java.util.Objects;

/** Context for validating ranges and conditions */
public record ConditionContext(String parameterName, String condition)
    implements ValidationContext {

  public ConditionContext {
    Objects.requireNonNull(parameterName, "parameterName cannot be null");
    Objects.requireNonNull(condition, "condition cannot be null");
  }

  public static ConditionContext range(String parameterName) {
    return new ConditionContext(parameterName, "range validation");
  }

  public static ConditionContext custom(String parameterName, String condition) {
    return new ConditionContext(parameterName, condition);
  }

  @Override
  public String nullParameterMessage() {
    return String.format("%s cannot be null", parameterName);
  }

  @Override
  public String nullInputMessage() {
    return nullParameterMessage();
  }

  @Override
  public String customMessage(String template, Object... args) {
    return String.format(template, args);
  }

  public <T extends Comparable<T>> String rangeMessage(T value, T min, T max) {
    return "%s must be between %s and %s, got %s".formatted(parameterName, min, max, value);
  }
}
