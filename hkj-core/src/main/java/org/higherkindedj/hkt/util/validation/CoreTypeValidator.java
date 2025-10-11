// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

import java.util.Objects;

public final class CoreTypeValidator {

  private CoreTypeValidator() {
    throw new AssertionError("CoreTypeValidator is a utility class");
  }

  /** Validates a value for core type constructors (Either, Validated, etc.) */
  public static <T> T requireValue(T value, Class<?> typeClass, Operation operation) {
    String context = typeClass.getSimpleName() + "." + operation;
    return Objects.requireNonNull(value, context + " value cannot be null");
  }

  public static <T> T requireValue(
      T value, String valueName, Class<?> typeClass, Operation operation) {
    String context = typeClass.getSimpleName() + "." + operation;
    return Objects.requireNonNull(value, "%s %s cannot be null".formatted(context, valueName));
  }

  /** Validates an error value for error types */
  public static <E> E requireError(E error, Class<?> typeClass) {
    String context = typeClass.getSimpleName();
    return Objects.requireNonNull(error, context + " error cannot be null");
  }

  /** Validates a supplier result */
  public static <T> T requireSupplierResult(T result, String supplierName, Class<?> typeClass) {
    if (result == null) {
      throw new NullPointerException(
          supplierName + " returned null for " + typeClass.getSimpleName());
    }
    return result;
  }
}
