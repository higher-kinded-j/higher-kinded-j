// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherf;

import static java.util.Objects.requireNonNull;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.WitnessArity;
import org.jspecify.annotations.NullMarked;

/**
 * Utility for combining multiple interpreters into a single interpreter for composed effect types.
 *
 * <p>When multiple effect algebras are composed via {@link EitherF}, each algebra has its own
 * interpreter ({@link Natural Natural<F, M>}). This class provides {@code combine} methods that
 * merge these individual interpreters into a single interpreter for the composed type.
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * Natural<ConsoleOpKind.Witness, IOKind.Witness> consoleInterp = ...;
 * Natural<DbOpKind.Witness, IOKind.Witness> dbInterp = ...;
 *
 * Natural<EitherFKind.Witness<ConsoleOpKind.Witness, DbOpKind.Witness>, IOKind.Witness> combined =
 *     Interpreters.combine(consoleInterp, dbInterp);
 * }</pre>
 *
 * @see EitherF
 * @see Natural
 */
@NullMarked
public final class Interpreters {

  private Interpreters() {}

  /**
   * Combines two interpreters into one for their EitherF composition.
   *
   * @param interpF The interpreter for effect F. Must not be null.
   * @param interpG The interpreter for effect G. Must not be null.
   * @param <F> The left effect type
   * @param <G> The right effect type
   * @param <M> The target monad type
   * @return A combined interpreter for {@code EitherF<F, G>}
   */
  public static <F extends WitnessArity<?>, G extends WitnessArity<?>, M extends WitnessArity<?>>
      Natural<EitherFKind.Witness<F, G>, M> combine(Natural<F, M> interpF, Natural<G, M> interpG) {
    requireNonNull(interpF, "interpF must not be null");
    requireNonNull(interpG, "interpG must not be null");
    return new Natural<>() {
      @Override
      public <A> Kind<M, A> apply(Kind<EitherFKind.Witness<F, G>, A> fa) {
        EitherF<F, G, A> eitherF = EitherFKindHelper.EITHERF.narrow(fa);
        return eitherF.fold(interpF::apply, interpG::apply);
      }
    };
  }

  /**
   * Combines three interpreters into one for their right-nested EitherF composition.
   *
   * <p>The resulting interpreter handles {@code EitherF<F, EitherF<G, H>>}.
   *
   * @param interpF The interpreter for effect F. Must not be null.
   * @param interpG The interpreter for effect G. Must not be null.
   * @param interpH The interpreter for effect H. Must not be null.
   * @param <F> The first effect type
   * @param <G> The second effect type
   * @param <H> The third effect type
   * @param <M> The target monad type
   * @return A combined interpreter for the three-way composition
   */
  public static <
          F extends WitnessArity<?>,
          G extends WitnessArity<?>,
          H extends WitnessArity<?>,
          M extends WitnessArity<?>>
      Natural<EitherFKind.Witness<F, EitherFKind.Witness<G, H>>, M> combine(
          Natural<F, M> interpF, Natural<G, M> interpG, Natural<H, M> interpH) {
    requireNonNull(interpF, "interpF must not be null");
    requireNonNull(interpG, "interpG must not be null");
    requireNonNull(interpH, "interpH must not be null");
    Natural<EitherFKind.Witness<G, H>, M> innerCombined = combine(interpG, interpH);
    return combine(interpF, innerCombined);
  }

  /**
   * Combines four interpreters into one for their right-nested EitherF composition.
   *
   * <p>The resulting interpreter handles {@code EitherF<F, EitherF<G, EitherF<H, I>>>}.
   *
   * @param interpF The interpreter for effect F. Must not be null.
   * @param interpG The interpreter for effect G. Must not be null.
   * @param interpH The interpreter for effect H. Must not be null.
   * @param interpI The interpreter for effect I. Must not be null.
   * @param <F> The first effect type
   * @param <G> The second effect type
   * @param <H> The third effect type
   * @param <I> The fourth effect type
   * @param <M> The target monad type
   * @return A combined interpreter for the four-way composition
   */
  public static <
          F extends WitnessArity<?>,
          G extends WitnessArity<?>,
          H extends WitnessArity<?>,
          I extends WitnessArity<?>,
          M extends WitnessArity<?>>
      Natural<EitherFKind.Witness<F, EitherFKind.Witness<G, EitherFKind.Witness<H, I>>>, M> combine(
          Natural<F, M> interpF,
          Natural<G, M> interpG,
          Natural<H, M> interpH,
          Natural<I, M> interpI) {
    requireNonNull(interpF, "interpF must not be null");
    requireNonNull(interpG, "interpG must not be null");
    requireNonNull(interpH, "interpH must not be null");
    requireNonNull(interpI, "interpI must not be null");
    Natural<EitherFKind.Witness<G, EitherFKind.Witness<H, I>>, M> innerCombined =
        combine(interpG, interpH, interpI);
    return combine(interpF, innerCombined);
  }
}
