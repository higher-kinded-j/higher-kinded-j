// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

import java.util.Objects;

public enum CoreTypeValidator {
  CORE_TYPE_VALIDATOR;

  /** Validates a value for core type constructors (Either, Validated, etc.) */
  public <T> T requireValue(T value, Class<?> typeClass, Operation operation) {
    String context = typeClass.getSimpleName() + "." + operation;
    return Objects.requireNonNull(value, context + " value cannot be null");
  }

  public <T> T requireValue(T value, String valueName, Class<?> typeClass, Operation operation) {
    String context = typeClass.getSimpleName() + "." + operation;
    return Objects.requireNonNull(value, "%s %s cannot be null".formatted(context, valueName));
  }

  /** Validates an error value for error types */
  public <E> E requireError(E error, Class<?> typeClass, Operation operation) {
    String context = typeClass.getSimpleName() + "." + operation;
    return Objects.requireNonNull(error, context + " error cannot be null");
  }
}
