// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.coyoneda;

import static org.higherkindedj.hkt.coyoneda.CoyonedaKindHelper.COYONEDA;
import static org.higherkindedj.hkt.util.validation.Operation.MAP;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Functor instance for Coyoneda.
 *
 * <p>This Functor instance is always valid for any type constructor F, regardless of whether F
 * itself has a Functor instance. This is the key property that makes Coyoneda the "free functor".
 *
 * <h2>Key Property</h2>
 *
 * <p>Unlike most Functor instances that require the underlying type to support mapping, {@code
 * CoyonedaFunctor<F>} works for <em>any</em> type constructor F because mapping simply composes
 * functions without actually performing any mapping on F.
 *
 * <h2>Map Fusion</h2>
 *
 * <p>Multiple map operations are automatically fused:
 *
 * <pre>{@code
 * CoyonedaFunctor<F> functor = new CoyonedaFunctor<>();
 *
 * Kind<CoyonedaKind.Witness<F>, Integer> coyo = COYONEDA.lift(someKind);
 * Kind<CoyonedaKind.Witness<F>, String> result = functor.map(
 *     x -> Integer.toString(x * 2),
 *     functor.map(x -> x + 1, coyo)
 * );
 * // The two functions are composed internally - only ONE actual map when lowered
 * }</pre>
 *
 * <h2>Functor Laws</h2>
 *
 * <p>This implementation satisfies the Functor laws by construction:
 *
 * <ul>
 *   <li><b>Identity:</b> {@code map(x -> x, fa) ≡ fa} - mapping with identity just composes with
 *       identity
 *   <li><b>Composition:</b> {@code map(g, map(f, fa)) ≡ map(g.compose(f), fa)} - functions are
 *       composed
 * </ul>
 *
 * @param <F> The underlying type constructor (not required to be a Functor)
 */
public class CoyonedaFunctor<F extends WitnessArity<TypeArity.Unary>>
    implements Functor<CoyonedaKind.Witness<F>> {

  private static final Class<CoyonedaFunctor> COYONEDA_FUNCTOR_CLASS = CoyonedaFunctor.class;

  private static final CoyonedaFunctor<?> INSTANCE = new CoyonedaFunctor<>();

  /** Creates a new CoyonedaFunctor instance. */
  public CoyonedaFunctor() {}

  /**
   * Returns a singleton instance of CoyonedaFunctor.
   *
   * @param <F> The underlying type constructor
   * @return A CoyonedaFunctor instance
   */
  @SuppressWarnings("unchecked")
  public static <F extends WitnessArity<TypeArity.Unary>> CoyonedaFunctor<F> instance() {
    return (CoyonedaFunctor<F>) INSTANCE;
  }

  /**
   * Maps a function over a Coyoneda value.
   *
   * <p>This operation does NOT require a Functor instance for F. The function is composed with the
   * existing transformation stored in the Coyoneda, achieving automatic map fusion.
   *
   * @param f The function to apply. Must not be null.
   * @param fa The Coyoneda value to map over. Must not be null and must be a valid CoyonedaKind.
   * @param <A> The input type
   * @param <B> The output type
   * @return A new Coyoneda with the function composed into its transformation
   * @throws NullPointerException if f or fa is null
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if fa cannot be unwrapped
   */
  @Override
  public <A, B> Kind<CoyonedaKind.Witness<F>, B> map(
      Function<? super A, ? extends B> f, Kind<CoyonedaKind.Witness<F>, A> fa) {
    Validation.function().requireMapper(f, "f", COYONEDA_FUNCTOR_CLASS, MAP);
    Validation.kind().requireNonNull(fa, COYONEDA_FUNCTOR_CLASS, MAP);

    Coyoneda<F, A> coyoneda = COYONEDA.narrow(fa);
    Coyoneda<F, B> mapped = coyoneda.map(f);
    return COYONEDA.widen(mapped);
  }
}
