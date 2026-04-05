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
 * <p>The annotated record must have 2-4 fields, each referencing a {@code Class<?>} of an effect
 * algebra annotated with {@link EffectAlgebra @EffectAlgebra}.
 *
 * <h2>Generated Classes</h2>
 *
 * <p>For an annotated record {@code MyEffects}, the processor generates a {@code MyEffectsSupport}
 * class with:
 *
 * <ul>
 *   <li>Static factory methods for all {@code Inject} instance combinations
 *   <li>A {@code BoundSet} record containing {@code Bound} instances for all effects
 *   <li>A {@code functor()} method returning the composed {@code Functor} instance
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
 * @see org.higherkindedj.hkt.inject.Inject
 * @see org.higherkindedj.hkt.eitherf.EitherF
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
