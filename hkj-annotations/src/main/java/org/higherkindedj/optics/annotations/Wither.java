// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the lens should use a wither method for copying.
 *
 * <p>Apply this to abstract methods in an {@link OpticsSpec} interface to generate a lens that uses
 * a wither (copy-with) method for creating modified copies of the source object.
 *
 * <p>The processor will generate code like:
 *
 * <pre>{@code
 * Lens.of(
 *     source -> source.getFieldName(),
 *     (source, newValue) -> source.withFieldName(newValue)
 * )
 * }</pre>
 *
 * <p>Wither methods are common in immutable classes, particularly in the {@code java.time} package
 * (e.g., {@code LocalDate.withYear()}, {@code LocalDate.withMonth()}).
 *
 * <p>Example:
 *
 * <pre>{@code
 * @ImportOptics
 * interface LocalDateOptics extends OpticsSpec<LocalDate> {
 *
 *     @Wither(value = "withYear", getter = "getYear")
 *     Lens<LocalDate, Integer> year();
 *
 *     @Wither(value = "withMonth", getter = "getMonthValue")
 *     Lens<LocalDate, Integer> monthValue();
 *
 *     @Wither(value = "withDayOfMonth", getter = "getDayOfMonth")
 *     Lens<LocalDate, Integer> dayOfMonth();
 * }
 * }</pre>
 *
 * @see OpticsSpec
 * @see ViaBuilder
 * @see ViaConstructor
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface Wither {

  /**
   * The wither method name on the source type.
   *
   * <p>For example, "withName" for a name field, "withYear" for a year field.
   *
   * @return the wither method name
   */
  String value();

  /**
   * Getter method name on the source object.
   *
   * <p>If empty (the default), the processor uses the optic method name as the getter. For example,
   * if the optic method is {@code name()}, the getter is assumed to be {@code name()}
   * (record-style). For JavaBean-style classes, specify the getter explicitly (e.g., {@code
   * "getName"}).
   *
   * @return the getter method name, or empty to derive from optic method name
   */
  String getter() default "";
}
