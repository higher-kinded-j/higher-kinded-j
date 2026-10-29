// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

import java.util.Locale;
import java.util.Objects;

public enum CoreTypeValidator {
  CORE_TYPE_VALIDATOR;

  /** Validates a value for core type constructors (Either, Validated, etc.) */
  public <T> T requireValue(T value, Class<?> typeClass, Operation operation) {
    Objects.requireNonNull(typeClass, "typeClass cannot be null");
    Objects.requireNonNull(operation, "operation cannot be null");

    if (value == null) {
      throw new NullPointerException(context(typeClass, operation) + " value cannot be null");
    }
    return value;
  }

  public <T> T requireValue(T value, String valueName, Class<?> typeClass, Operation operation) {
    Objects.requireNonNull(valueName, "valueName cannot be null");
    Objects.requireNonNull(typeClass, "typeClass cannot be null");
    Objects.requireNonNull(operation, "operation cannot be null");

    if (value == null) {
      throw new NullPointerException(
          String.format(
              Locale.ROOT, "%s %s cannot be null", context(typeClass, operation), valueName));
    }
    return value;
  }

  /** Validates an error value for error types */
  public <E> E requireError(E error, Class<?> typeClass, Operation operation) {
    Objects.requireNonNull(typeClass, "typeClass cannot be null");
    Objects.requireNonNull(operation, "operation cannot be null");

    if (error == null) {
      throw new NullPointerException(context(typeClass, operation) + " error cannot be null");
    }
    return error;
  }

  // Built only when a check fails, so a passing check allocates nothing
  private static String context(Class<?> typeClass, Operation operation) {
    return typeClass.getSimpleName() + "." + operation;
  }
}
