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
 * <p>The method has to be {@code static} and declare no type parameters of its own, because the
 * generated field is a {@code public static final} one initialised by calling it: a field has
 * nowhere to declare type parameters, and a static initialiser has no instance to call an instance
 * method on. A method that is either is refused at the declaration rather than generated for. Fix
 * the type arguments where the method is declared - {@code Iso<Box<String>, String>} rather than
 * {@code <T> Iso<Box<T>, T>} - or expose the method itself instead of a generated field.
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
