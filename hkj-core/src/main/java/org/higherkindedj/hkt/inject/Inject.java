// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.inject;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.WitnessArity;
import org.jspecify.annotations.NullMarked;

/**
 * Witnesses that effect type {@code F} can be embedded into a larger effect type {@code G}.
 *
 * <p>Think of it like an interface constraint: {@code Inject<ConsoleOp, G>} means "G supports
 * console operations." This is the mechanism by which individual effect algebras are composed into
 * a larger combined effect type via {@link org.higherkindedj.hkt.eitherf.EitherF}.
 *
 * <h2>Usage</h2>
 *
 * <p>Inject instances are typically obtained from {@link InjectInstances} and used with {@code
 * Free.translate} to lift single-effect programs into combined-effect programs:
 *
 * <pre>{@code
 * Inject<ConsoleOpKind.Witness, AppEffects> consoleInject = ...;
 * Functor<AppEffects> functor = ...;
 * Free<AppEffects, String> combined = Free.translate(consoleProgram, consoleInject::inject, functor);
 * }</pre>
 *
 * @param <F> The effect type being injected (source)
 * @param <G> The combined effect type (target)
 * @see InjectInstances
 * @see org.higherkindedj.hkt.eitherf.EitherF
 */
@NullMarked
@FunctionalInterface
public interface Inject<F extends WitnessArity<?>, G extends WitnessArity<?>> {

  /**
   * Injects an instruction from effect type {@code F} into the combined effect type {@code G}.
   *
   * @param fa The instruction in F. Must not be null.
   * @param <A> The result type of the instruction
   * @return The instruction lifted into G. Never null.
   */
  <A> Kind<G, A> inject(Kind<F, A> fa);
}
