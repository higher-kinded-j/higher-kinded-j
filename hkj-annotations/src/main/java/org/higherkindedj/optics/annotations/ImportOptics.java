// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Imports optics for external types into your codebase.
 *
 * <p>Apply this annotation to a package (via {@code package-info.java}) or type to generate optics
 * for classes you do not own. The processor will analyse each class and generate appropriate optics
 * based on its structure:
 *
 * <ul>
 *   <li>Records → Lens per component
 *   <li>Sealed interfaces → Prism per permitted subtype
 *   <li>Enums → Prism per constant
 *   <li>Classes with {@code withX()} methods → Lens per wither
 * </ul>
 *
 * <p>Example usage in {@code package-info.java}:
 *
 * <pre>{@code
 * @ImportOptics({
 *     java.time.LocalDate.class,
 *     com.external.CustomerRecord.class
 * })
 * package com.mycompany.optics;
 *
 * import org.higherkindedj.optics.annotations.ImportOptics;
 * }</pre>
 *
 * <p>For records, the generated class will be named by appending "Lenses" to the record name (e.g.,
 * {@code CustomerRecordLenses}). For sealed interfaces and enums, the generated class will be named
 * by appending "Prisms" (e.g., {@code ShapePrisms}). For classes with wither methods, lenses are
 * generated similarly (e.g., {@code LocalDateLenses}).
 *
 * @see GenerateLenses
 * @see GeneratePrisms
 */
@Target({ElementType.PACKAGE, ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
public @interface ImportOptics {

  /**
   * External classes to generate optics for.
   *
   * <p>Each class will be analysed and appropriate optics generated based on its structure.
   *
   * @return array of classes to import optics for
   */
  Class<?>[] value() default {};

  /**
   * Target package for generated classes.
   *
   * <p>If empty (the default), generated classes are placed in the annotated package. When the
   * annotation is applied to a type, this defaults to that type's package.
   *
   * @return the target package name, or empty to use the annotated package
   */
  String targetPackage() default "";

  /**
   * Allow generation for mutable classes (with setters).
   *
   * <p>By default, the processor refuses to generate lenses for mutable classes because lens laws
   * may not hold when the source object can be mutated. Set this to {@code true} to acknowledge
   * this limitation and generate lenses anyway.
   *
   * <p>Note: This option has no effect on immutable types like records or classes using only wither
   * methods.
   *
   * @return {@code true} to allow mutable class support, {@code false} (default) to reject them
   */
  boolean allowMutable() default false;
}
