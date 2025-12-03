// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free_ap;

import static org.higherkindedj.hkt.free_ap.FreeApKindHelper.FREE_AP;
import static org.higherkindedj.hkt.util.validation.Operation.MAP;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Functor instance for FreeAp.
 *
 * <p>This Functor instance is always valid for any type constructor F, regardless of whether F
 * itself has any instances. The map operation simply composes with the applicative structure.
 *
 * <h2>Functor Laws</h2>
 *
 * <p>This implementation satisfies the Functor laws by construction:
 *
 * <ul>
 *   <li><b>Identity:</b> {@code map(x -> x, fa) ≡ fa}
 *   <li><b>Composition:</b> {@code map(g, map(f, fa)) ≡ map(g.compose(f), fa)}
 * </ul>
 *
 * @param <F> The underlying instruction set type (not required to have any instances)
 */
public class FreeApFunctor<F> implements Functor<FreeApKind.Witness<F>> {

  private static final Class<FreeApFunctor> FREE_AP_FUNCTOR_CLASS = FreeApFunctor.class;

  private static final FreeApFunctor<?> INSTANCE = new FreeApFunctor<>();

  /** Creates a new FreeApFunctor instance. */
  protected FreeApFunctor() {}

  /**
   * Returns a singleton instance of FreeApFunctor.
   *
   * @param <F> The underlying instruction set type
   * @return A FreeApFunctor instance
   */
  @SuppressWarnings("unchecked")
  public static <F> FreeApFunctor<F> instance() {
    return (FreeApFunctor<F>) INSTANCE;
  }

  /**
   * Maps a function over a FreeAp value.
   *
   * @param f The function to apply. Must not be null.
   * @param fa The FreeAp value to map over. Must not be null.
   * @param <A> The input type
   * @param <B> The output type
   * @return A new FreeAp with the function applied
   * @throws NullPointerException if f or fa is null
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if fa cannot be unwrapped
   */
  @Override
  public <A, B> Kind<FreeApKind.Witness<F>, B> map(
      Function<? super A, ? extends B> f, Kind<FreeApKind.Witness<F>, A> fa) {
    Validation.function().requireMapper(f, "f", FREE_AP_FUNCTOR_CLASS, MAP);
    Validation.kind().requireNonNull(fa, FREE_AP_FUNCTOR_CLASS, MAP);

    FreeAp<F, A> freeAp = FREE_AP.narrow(fa);
    FreeAp<F, B> mapped = freeAp.map(f);
    return FREE_AP.widen(mapped);
  }
}
