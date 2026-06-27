// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherorboth;

import static org.higherkindedj.hkt.eitherorboth.EitherOrBothKindHelper.EITHER_OR_BOTH;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Implements {@link Traverse} and {@link Foldable} for {@link EitherOrBoth}, using {@link
 * EitherOrBothKind.Witness} as the higher-kinded type witness.
 *
 * <p>Traversal and folding are right-biased: they operate on the right (success) value of a {@link
 * EitherOrBoth.Right} or {@link EitherOrBoth.Both} and pass a {@link EitherOrBoth.Left} through
 * unchanged. A {@link EitherOrBoth.Both} keeps its warnings while its right value is traversed or
 * folded.
 *
 * @param <L> the type of the left-hand (warning/error) value
 */
public final class EitherOrBothTraverse<L> implements Traverse<EitherOrBothKind.Witness<L>> {

  private static final EitherOrBothTraverse<?> INSTANCE = new EitherOrBothTraverse<>();

  private EitherOrBothTraverse() {}

  /**
   * Returns the singleton {@code EitherOrBothTraverse} for the fixed left type {@code L}.
   *
   * @param <L> the left type
   * @return the singleton instance
   */
  @SuppressWarnings("unchecked")
  public static <L> EitherOrBothTraverse<L> instance() {
    return (EitherOrBothTraverse<L>) INSTANCE;
  }

  @Override
  public <A, B> Kind<EitherOrBothKind.Witness<L>, B> map(
      Function<? super A, ? extends B> f, Kind<EitherOrBothKind.Witness<L>, A> fa) {
    Validation.function().validateMap(f, fa);
    return EITHER_OR_BOTH.widen(EITHER_OR_BOTH.narrow(fa).map(f));
  }

  /**
   * Traverses the right value, preserving the case: a {@link EitherOrBoth.Left} is lifted unchanged
   * into {@code G}; a {@link EitherOrBoth.Right} maps its value through {@code f}; a {@link
   * EitherOrBoth.Both} maps its right value through {@code f} while retaining its warnings.
   *
   * @param <G> the applicative context witness
   * @param <A> the input right type
   * @param <B> the output right type
   * @param applicative the {@link Applicative} for {@code G}; must not be null
   * @param f the effectful function; must not be null
   * @param ta the structure to traverse; must not be null
   * @return {@code Kind<G, Kind<EitherOrBothKind.Witness<L>, B>>}; never null
   * @throws NullPointerException if {@code applicative}, {@code f}, or {@code ta} is null
   */
  @Override
  public <G extends WitnessArity<TypeArity.Unary>, A, B>
      Kind<G, Kind<EitherOrBothKind.Witness<L>, B>> traverse(
          Applicative<G> applicative,
          Function<? super A, ? extends Kind<G, ? extends B>> f,
          Kind<EitherOrBothKind.Witness<L>, A> ta) {
    Validation.function().validateTraverse(applicative, f, ta);

    EitherOrBoth<L, A> eob = EITHER_OR_BOTH.narrow(ta);

    return eob.fold(
        left -> applicative.of(EITHER_OR_BOTH.widen(EitherOrBoth.left(left))),
        right -> applicative.map(b -> EITHER_OR_BOTH.widen(EitherOrBoth.right(b)), f.apply(right)),
        (left, right) ->
            applicative.map(b -> EITHER_OR_BOTH.widen(EitherOrBoth.both(left, b)), f.apply(right)));
  }

  /**
   * Folds the right value into a {@link Monoid}: a {@link EitherOrBoth.Left} yields {@code
   * monoid.empty()}; a {@link EitherOrBoth.Right} and {@link EitherOrBoth.Both} both contribute
   * {@code f(right)}.
   *
   * @param <A> the right element type
   * @param <M> the monoidal type
   * @param monoid the {@link Monoid} used to combine results; must not be null
   * @param f the mapping function; must not be null
   * @param fa the structure to fold; must not be null
   * @return the aggregated result; never null
   * @throws NullPointerException if {@code monoid}, {@code f}, or {@code fa} is null
   */
  @Override
  public <A, M> M foldMap(
      Monoid<M> monoid,
      Function<? super A, ? extends M> f,
      Kind<EitherOrBothKind.Witness<L>, A> fa) {
    Validation.function().validateFoldMap(monoid, f, fa);

    EitherOrBoth<L, A> eob = EITHER_OR_BOTH.narrow(fa);
    return eob.fold(left -> monoid.empty(), f, (left, right) -> f.apply(right));
  }
}
