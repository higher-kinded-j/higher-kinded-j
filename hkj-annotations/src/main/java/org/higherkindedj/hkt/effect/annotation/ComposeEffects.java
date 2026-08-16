// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a record type as a composition of multiple {@link EffectAlgebra @EffectAlgebra} effect
 * algebras, triggering generation of support infrastructure.
 *
 * <p>The annotated record must have 2-4 fields, each declared {@code Class<XOp<?>>} naming an
 * effect algebra annotated with {@link EffectAlgebra @EffectAlgebra}. A field of any other shape is
 * a compile error: the algebra's type is what lets the generated support spell the composed
 * witness, so {@code Class<?>} is not enough. An algebra with more than one type parameter cannot
 * take part, since its witness would itself be generic.
 *
 * <h2>Generated Classes</h2>
 *
 * <p>For an annotated record {@code MyEffects}, the processor generates a {@code MyEffectsSupport}
 * class with:
 *
 * <ul>
 *   <li>An {@code injectX()} per field, returning {@code Inject<XKind.Witness, W>} where {@code W}
 *       is the composed, right-nested {@code EitherFKind.Witness}
 *   <li>A {@code BoundSet} record whose components are each algebra's {@code Bound} at {@code W}
 *   <li>A {@code functor(...)} method taking one {@code Functor} per effect and returning the
 *       composed {@code EitherFFunctor}
 * </ul>
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * @ComposeEffects
 * public record AppEffects(
 *     Class<ConsoleOp<?>> console,
 *     Class<DbOp<?>> db
 * ) {}
 * }</pre>
 *
 * @see EffectAlgebra
 * @see "org.higherkindedj.hkt.inject.Inject"
 * @see "org.higherkindedj.hkt.eitherf.EitherF"
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface ComposeEffects {

  /**
   * Target package for generated classes. Defaults to the same package as the annotated record.
   *
   * @return the target package name, or empty string for same package
   */
  String targetPackage() default "";
}
