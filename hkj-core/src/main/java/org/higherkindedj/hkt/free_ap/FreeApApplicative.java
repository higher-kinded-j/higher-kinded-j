// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free_ap;

import static org.higherkindedj.hkt.free_ap.FreeApKindHelper.FREE_AP;
import static org.higherkindedj.hkt.util.validation.Operation.MAP;
import static org.higherkindedj.hkt.util.validation.Operation.MAP_2;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * Applicative instance for FreeAp.
 *
 * <p>This Applicative instance is always valid for any type constructor F, regardless of whether F
 * itself has any instances. This is the "free applicative" property - any type constructor gets an
 * Applicative instance for free.
 *
 * <h2>Key Property: Independent Computations</h2>
 *
 * <p>When using {@link #ap(Kind, Kind)}, the function and argument computations are
 * <em>independent</em>. Neither depends on the other's result, which enables:
 *
 * <ul>
 *   <li>Parallel execution (with appropriate interpreters)
 *   <li>Static analysis of the computation structure
 *   <li>Batching and optimization of operations
 * </ul>
 *
 * <h2>Applicative Laws</h2>
 *
 * <p>This implementation satisfies the Applicative laws by construction:
 *
 * <ul>
 *   <li><b>Identity:</b> {@code pure(id).ap(fa) ≡ fa}
 *   <li><b>Homomorphism:</b> {@code pure(f).ap(pure(x)) ≡ pure(f(x))}
 *   <li><b>Interchange:</b> {@code ff.ap(pure(x)) ≡ pure(f -> f(x)).ap(ff)}
 *   <li><b>Composition:</b> {@code pure(.).ap(ff).ap(fg).ap(fa) ≡ ff.ap(fg.ap(fa))}
 * </ul>
 *
 * @param <F> The underlying instruction set type (not required to have any instances)
 */
public class FreeApApplicative<F> extends FreeApFunctor<F>
    implements Applicative<FreeApKind.Witness<F>> {

  private static final Class<FreeApApplicative> FREE_AP_APPLICATIVE_CLASS = FreeApApplicative.class;

  private static final FreeApApplicative<?> INSTANCE = new FreeApApplicative<>();

  /** Creates a new FreeApApplicative instance. */
  protected FreeApApplicative() {
    super();
  }

  /**
   * Returns a singleton instance of FreeApApplicative.
   *
   * @param <F> The underlying instruction set type
   * @return A FreeApApplicative instance
   */
  @SuppressWarnings("unchecked")
  public static <F> FreeApApplicative<F> instance() {
    return (FreeApApplicative<F>) INSTANCE;
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
    Validation.function().requireMapper(f, "f", FREE_AP_APPLICATIVE_CLASS, MAP);
    Validation.kind().requireNonNull(fa, FREE_AP_APPLICATIVE_CLASS, MAP);

    FreeAp<F, A> freeAp = FREE_AP.narrow(fa);
    FreeAp<F, B> mapped = freeAp.map(f);
    return FREE_AP.widen(mapped);
  }

  /**
   * Lifts a pure value into FreeAp.
   *
   * @param value The value to lift (can be null if A allows null)
   * @param <A> The type of the value
   * @return A FreeAp containing the pure value
   */
  @Override
  public <A> Kind<FreeApKind.Witness<F>, A> of(@Nullable A value) {
    return FREE_AP.widen(FreeAp.pure(value));
  }

  /**
   * Applies a function wrapped in FreeAp to a value wrapped in FreeAp.
   *
   * <p>This operation captures <em>independent</em> computations. The function and value
   * computations do not depend on each other and can potentially be executed in parallel by smart
   * interpreters.
   *
   * @param ff The FreeAp containing the function. Must not be null.
   * @param fa The FreeAp containing the value. Must not be null.
   * @param <A> The input type of the function
   * @param <B> The output type of the function
   * @return A FreeAp representing the application
   * @throws NullPointerException if ff or fa is null
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if ff or fa cannot be unwrapped
   */
  @Override
  public <A, B> Kind<FreeApKind.Witness<F>, B> ap(
      Kind<FreeApKind.Witness<F>, ? extends Function<A, B>> ff, Kind<FreeApKind.Witness<F>, A> fa) {
    Validation.kind().validateAp(ff, fa, FREE_AP_APPLICATIVE_CLASS);

    FreeAp<F, ? extends Function<A, B>> freeApF = FREE_AP.narrow(ff);
    FreeAp<F, A> freeApA = FREE_AP.narrow(fa);

    FreeAp<F, B> result = freeApA.ap(freeApF);
    return FREE_AP.widen(result);
  }

  /**
   * Combines two FreeAp values using a binary function.
   *
   * <p>This captures two <em>independent</em> computations. Neither depends on the other's result,
   * which enables parallel execution or batching by smart interpreters.
   *
   * @param fa The first FreeAp value. Must not be null.
   * @param fb The second FreeAp value. Must not be null.
   * @param f A pure function to combine the values. Must not be null.
   * @param <A> The type of the first value
   * @param <B> The type of the second value
   * @param <C> The type of the result
   * @return A FreeAp containing the combined result
   * @throws NullPointerException if fa, fb, or f is null
   */
  @Override
  public <A, B, C> Kind<FreeApKind.Witness<F>, C> map2(
      Kind<FreeApKind.Witness<F>, A> fa,
      Kind<FreeApKind.Witness<F>, B> fb,
      BiFunction<? super A, ? super B, ? extends C> f) {
    Validation.kind().requireNonNull(fa, FREE_AP_APPLICATIVE_CLASS, MAP_2, "first");
    Validation.kind().requireNonNull(fb, FREE_AP_APPLICATIVE_CLASS, MAP_2, "second");
    Validation.function()
        .requireFunction(f, "combining function", FREE_AP_APPLICATIVE_CLASS, MAP_2);

    return ap(map(a -> b -> f.apply(a, b), fa), fb);
  }
}
