// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Java record for which a Lenses utility class should be generated. The generated class
 * will be named by appending "Lenses" to the record's name.
 *
 * <p>By default, the generated class is placed in the same package as the annotated record. Use the
 * {@link #targetPackage()} element to specify a different package for the generated class.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface GenerateLenses {

  /**
   * The package where the generated class should be placed. If empty (the default), the generated
   * class will be placed in the same package as the annotated record.
   *
   * @return the target package name, or empty string to use the source package
   */
  String targetPackage() default "";
}
