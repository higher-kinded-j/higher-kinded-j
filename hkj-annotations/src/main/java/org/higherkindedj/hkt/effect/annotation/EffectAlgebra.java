// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a sealed interface as an effect algebra, triggering code generation of HKT boilerplate.
 *
 * <p>The annotated interface must be:
 *
 * <ul>
 *   <li>A {@code sealed interface} with exactly one type parameter (the result type {@code A})
 *   <li>All permitted subtypes must be record types
 *   <li>Permitted records must not introduce additional generic type parameters beyond {@code <A>}
 * </ul>
 *
 * <h2>Generated Classes</h2>
 *
 * <p>For an annotated interface {@code FooOp<A>}, the processor generates:
 *
 * <ol>
 *   <li>{@code FooOpKind} — Kind marker interface with inner {@code Witness} class
 *   <li>{@code FooOpKindHelper} — Enum singleton with {@code widen()}/{@code narrow()} methods
 *   <li>{@code FooOpFunctor} — {@code Functor<Witness>} implementation
 *   <li>{@code FooOpOps} — Smart constructors lifting operations into {@code Free}, plus inner
 *       {@code Bound} class for composed effects
 *   <li>{@code FooOpInterpreter} — Abstract interpreter skeleton implementing {@code
 *       Natural<Witness, M>}
 * </ol>
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * @EffectAlgebra
 * public sealed interface ConsoleOp<A> {
 *     record ReadLine<A>() implements ConsoleOp<A> {}
 *     record PrintLine<A>(String message) implements ConsoleOp<A> {}
 * }
 * }</pre>
 *
 * @see org.higherkindedj.hkt.Kind
 * @see org.higherkindedj.hkt.Functor
 * @see org.higherkindedj.hkt.free.Free
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface EffectAlgebra {

  /**
   * Target package for generated classes. Defaults to the same package as the annotated interface.
   *
   * @return the target package name, or empty string for same package
   */
  String targetPackage() default "";
}
