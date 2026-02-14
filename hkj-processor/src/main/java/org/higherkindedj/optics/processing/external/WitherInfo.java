// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

/**
 * Information about a wither method detected on a class.
 *
 * <p>A wither method follows the pattern {@code withX(T newValue)} that returns a new instance of
 * the class with the specified field updated. This is the immutable update pattern used by types
 * like {@link java.time.LocalDate}.
 *
 * @param fieldName the derived field name (from "withFieldName" → "fieldName")
 * @param witherMethodName the full name of the wither method (e.g., "withYear")
 * @param parameterType the type of the wither method's parameter
 * @param witherMethod the executable element representing the wither method
 * @param getterMethodName the name of the corresponding getter method (e.g., "getYear" or "year")
 */
public record WitherInfo(
    String fieldName,
    String witherMethodName,
    TypeMirror parameterType,
    ExecutableElement witherMethod,
    String getterMethodName) {

  /**
   * Creates a WitherInfo from a detected wither method.
   *
   * @param witherMethod the wither method element
   * @param fieldName the derived field name
   * @param getterMethodName the corresponding getter method name
   * @return a new WitherInfo for the method
   */
  public static WitherInfo of(
      ExecutableElement witherMethod, String fieldName, String getterMethodName) {
    return new WitherInfo(
        fieldName,
        witherMethod.getSimpleName().toString(),
        witherMethod.getParameters().getFirst().asType(),
        witherMethod,
        getterMethodName);
  }
}
