// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Package-level settings for Path code generation, which no processor reads.
 *
 * <p>None of these settings has any effect. Every Path generated for a {@link PathSource} in the
 * package comes out as it would without this annotation, and {@code hkj-processor} reports a note
 * where the annotation is written to say so. Removing it changes nothing that is generated.
 *
 * <p>The one setting with a counterpart is {@link #pathSuffix()}: to name a generated Path class
 * with a suffix other than {@code Path}, set {@link PathSource#suffix()} on the type.
 *
 * @deprecated since 0.4.11, for removal in 0.5.0. It has no effect: remove it, and set {@link
 *     PathSource#suffix()} on a type whose Path class should be named with another suffix. The
 *     {@code MigrateDeprecationsTo0_5_0} OpenRewrite recipe removes it.
 * @see PathSource
 */
@Deprecated(since = "0.4.11", forRemoval = true)
@Target(ElementType.PACKAGE)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface PathConfig {

  /**
   * Has no effect: a generated Path always has a {@code toString()} method.
   *
   * @return ignored
   */
  boolean generateToString() default true;

  /**
   * Has no effect: a generated Path always has {@code equals()} and {@code hashCode()} methods.
   *
   * @return ignored
   */
  boolean generateEquals() default true;

  /**
   * Has no effect: a generated Path class is named with {@link PathSource#suffix()}, which defaults
   * to {@code "Path"}. Set the suffix there instead.
   *
   * @return ignored
   */
  String pathSuffix() default "Path";

  /**
   * Has no effect: a generated Path has no conversion methods to other Path types.
   *
   * @return ignored
   */
  boolean generateConversions() default true;

  /**
   * Has no effect: a generated Path always has a static {@code pure} factory method.
   *
   * @return ignored
   */
  boolean generatePure() default true;

  /**
   * Has no effect: a generated Path class always carries {@code @Generated}.
   *
   * @return ignored
   */
  boolean includeGeneratedAnnotation() default true;

  /**
   * Has no effect: a generated Path class is always final.
   *
   * @return ignored
   */
  boolean makeFinal() default true;

  /**
   * Has no effect: a generated Path imports only the types it names.
   *
   * @return ignored
   */
  String[] additionalImports() default {};
}
