// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherf;

import static org.higherkindedj.hkt.util.validation.Operation.CONSTRUCTION;
import static org.higherkindedj.hkt.util.validation.Operation.MAP;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.NullMarked;

/**
 * Functor instance for {@link EitherF}.
 *
 * <p>Delegates mapping to the underlying functor on whichever side (Left/Right) is present. This
 * requires both {@code Functor<F>} and {@code Functor<G>} at construction time, making it a {@code
 * final class} rather than the usual stateless enum singleton.
 *
 * @param <F> The witness type for the left effect algebra
 * @param <G> The witness type for the right effect algebra
 * @see EitherF
 * @see EitherFKind
 */
@NullMarked
public final class EitherFFunctor<
        F extends WitnessArity<TypeArity.Unary>, G extends WitnessArity<TypeArity.Unary>>
    implements Functor<EitherFKind.Witness<F, G>> {

  private final Functor<F> functorF;
  private final Functor<G> functorG;

  /**
   * Creates an EitherFFunctor with the required sub-functor instances.
   *
   * @param functorF The functor instance for the left effect algebra. Must not be null.
   * @param functorG The functor instance for the right effect algebra. Must not be null.
   */
  public EitherFFunctor(Functor<F> functorF, Functor<G> functorG) {
    this.functorF = Validation.function().require(functorF, "functorF", CONSTRUCTION);
    this.functorG = Validation.function().require(functorG, "functorG", CONSTRUCTION);
  }

  /**
   * Maps a function over the value inside this EitherF, delegating to the appropriate sub-functor.
   *
   * <p>If the EitherF is a Left, delegates to {@code functorF.map}. If it is a Right, delegates to
   * {@code functorG.map}.
   *
   * @param f The function to apply. Must not be null.
   * @param fa The EitherF wrapped as a Kind. Must not be null.
   * @param <A> The input value type
   * @param <B> The output value type
   * @return A new Kind wrapping the mapped EitherF
   */
  @Override
  public <A, B> Kind<EitherFKind.Witness<F, G>, B> map(
      Function<? super A, ? extends B> f, Kind<EitherFKind.Witness<F, G>, A> fa) {
    Validation.function().require(f, "f", MAP);
    Validation.kind().requireNonNull(fa, MAP);

    EitherF<F, G, A> eitherF = EitherFKindHelper.EITHERF.narrow(fa);
    EitherF<F, G, B> mapped =
        eitherF.fold(
            left -> new EitherF.Left<>(functorF.map(f, left)),
            right -> new EitherF.Right<>(functorG.map(f, right)));
    return EitherFKindHelper.EITHERF.widen(mapped);
  }
}
