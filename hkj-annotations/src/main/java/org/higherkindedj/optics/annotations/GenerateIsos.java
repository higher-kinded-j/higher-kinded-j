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
 * <p>The method has to be {@code static}, take no arguments, be reachable from the package the
 * companion is written into, and return an {@code Iso} whose two type arguments name no type
 * variable. Every one of those is a question about the generated field rather than about the
 * method: a {@code public static final} field has nowhere to declare a type parameter and no
 * receiver to call an instance method on. A method that breaks any of them is refused where it is
 * declared rather than generated for — give the iso concrete type arguments, {@code
 * Iso<Box<String>, String>} rather than {@code <T> Iso<Box<T>, T>}, or drop the annotation and call
 * the method directly.
 *
 * <p>The rule is about what the iso names, not what the method declares: {@code <T> Iso<Box,
 * String> boxIso()} is accepted, because {@code T} is inferred at the call and never reaches the
 * field's type.
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
