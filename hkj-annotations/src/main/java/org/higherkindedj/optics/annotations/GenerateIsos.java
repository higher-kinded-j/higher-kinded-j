// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation to trigger the generation of Isomorphism (Iso) optics.
 *
 * <p>This should be placed on a method that returns an Iso. The annotation processor will then
 * generate a static field containing the Iso instance.
 *
 * <p>By default, the generated class is placed in the same package as the enclosing class. Use the
 * {@link #targetPackage()} element to specify a different package for the generated class.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface GenerateIsos {

  /**
   * The package where the generated class should be placed. If empty (the default), the generated
   * class will be placed in the same package as the enclosing class.
   *
   * @return the target package name, or empty string to use the source package
   */
  String targetPackage() default "";
}
