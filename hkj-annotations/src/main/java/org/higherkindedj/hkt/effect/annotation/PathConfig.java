// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Global configuration for Path code generation.
 *
 * <p>Apply this annotation to a {@code package-info.java} to configure default settings for all
 * {@code @PathSource} annotations in that package.
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * // In package-info.java
 * @PathConfig(
 *     generateToString = true,
 *     generateEquals = true,
 *     pathSuffix = "Path"
 * )
 * package com.example.effects;
 *
 * import org.higherkindedj.hkt.effect.annotation.PathConfig;
 * }</pre>
 *
 * <h2>Precedence</h2>
 *
 * <p>Settings specified in {@code @PathSource} take precedence over {@code @PathConfig} defaults.
 * This allows package-level defaults with per-type overrides.
 *
 * @see PathSource
 */
@Target(ElementType.PACKAGE)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface PathConfig {

  /**
   * Whether to generate {@code toString()} methods in generated Path classes.
   *
   * <p>Default: {@code true}
   *
   * @return true to generate toString methods
   */
  boolean generateToString() default true;

  /**
   * Whether to generate {@code equals()} and {@code hashCode()} methods.
   *
   * <p>Default: {@code true}
   *
   * @return true to generate equals and hashCode methods
   */
  boolean generateEquals() default true;

  /**
   * The default suffix for generated Path class names.
   *
   * <p>Default: {@code "Path"}
   *
   * @return the default class name suffix
   */
  String pathSuffix() default "Path";

  /**
   * Whether to generate conversion methods to other Path types.
   *
   * <p>This includes methods like {@code toMaybePath()}, {@code toEitherPath()}, etc.
   *
   * <p>Default: {@code true}
   *
   * @return true to generate conversion methods
   */
  boolean generateConversions() default true;

  /**
   * Whether to generate a static {@code pure} factory method.
   *
   * <p>Default: {@code true}
   *
   * @return true to generate the pure method
   */
  boolean generatePure() default true;

  /**
   * Whether to include the {@code Generated} annotation on generated classes.
   *
   * <p>This adds {@code @Generated("org.higherkindedj.hkt.effect.processor.PathProcessor")} to
   * generated files, which can be useful for IDE integration and code coverage exclusion.
   *
   * <p>Default: {@code true}
   *
   * @return true to include the Generated annotation
   */
  boolean includeGeneratedAnnotation() default true;

  /**
   * Whether generated classes should be final.
   *
   * <p>Default: {@code true}
   *
   * @return true to make generated classes final
   */
  boolean makeFinal() default true;

  /**
   * Additional imports to include in generated files.
   *
   * <p>This is useful when custom types are used in conversion methods.
   *
   * <p>Default: empty (no additional imports)
   *
   * @return array of fully qualified class names to import
   */
  String[] additionalImports() default {};
}
