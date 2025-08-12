// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Traverse;
import org.jspecify.annotations.NonNull;

/**
 * Implements the {@link Traverse} and {@link Foldable} typeclasses for {@link Either}, using {@link
 * EitherKind.Witness} as the higher-kinded type witness.
 *
 * <p>Traversal and folding operations are right-biased, meaning they operate on the value inside a
 * {@link Either.Right} and pass through a {@link Either.Left} unchanged.
 *
 * @param <E> The type of the left-hand value (typically an error).
 */
public final class EitherTraverse<E> implements Traverse<EitherKind.Witness<E>> {

  private static final EitherTraverse<?> INSTANCE = new EitherTraverse<>();

  private EitherTraverse() {}

  @SuppressWarnings("unchecked")
  public static <E> EitherTraverse<E> instance() {
    return (EitherTraverse<E>) INSTANCE;
  }

  @Override
  public <A, B> @NonNull Kind<EitherKind.Witness<E>, B> map(
      @NonNull Function<? super A, ? extends B> f, @NonNull Kind<EitherKind.Witness<E>, A> fa) {
    return EITHER.widen(EITHER.narrow(fa).map(f));
  }

  @Override
  public <G, A, B> @NonNull Kind<G, Kind<EitherKind.Witness<E>, B>> traverse(
      @NonNull Applicative<G> applicative,
      @NonNull Kind<EitherKind.Witness<E>, A> ta,
      @NonNull Function<? super A, ? extends Kind<G, ? extends B>> f) {

    return EITHER
        .narrow(ta)
        .fold(
            // Left case: If it's a Left, wrap the left value in a new Either and lift it
            // into the applicative context.
            leftValue -> applicative.of(EITHER.widen(Either.left(leftValue))),

            // Right case: If it's a Right, apply the function `f` to the right value.
            // This yields a Kind<G, B>. Then, map over that to wrap the `B` back into a Right.
            rightValue -> applicative.map(b -> EITHER.widen(Either.right(b)), f.apply(rightValue)));
  }

  @Override
  public <A, M> M foldMap(
      @NonNull Monoid<M> monoid,
      @NonNull Function<? super A, ? extends M> f,
      @NonNull Kind<EitherKind.Witness<E>, A> fa) {
    return EITHER.narrow(fa).fold(left -> monoid.empty(), f);
  }
}
