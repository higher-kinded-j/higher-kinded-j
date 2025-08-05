// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Traverse;
import org.jspecify.annotations.NonNull;

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
  public <G, A, B> Kind<G, Kind<EitherKind.Witness<E>, B>> traverse(
      @NonNull Applicative<G> applicative,
      @NonNull Kind<EitherKind.Witness<E>, A> ta,
      @NonNull Function<? super A, ? extends Kind<G, ? extends B>> f) {

    final Either<E, A> either = EITHER.narrow(ta);

    if (either instanceof Either.Left<E, A> left) {
      // we create a new Left with the correct type signature.
      // The value `left.getLeft()` is preserved, but the new Either is now correctly
      // typed as Either<E, B>, which the compiler can lift into the applicative.
      final Either<E, B> result = Either.left(left.getLeft());
      return applicative.of(EITHER.widen(result));
    } else {
      // Case 2: Right.
      final Either.Right<E, A> right = (Either.Right<E, A>) either;
      final Kind<G, ? extends B> g_of_b = f.apply(right.getRight());

      // Add a safe, explicit cast to help Java's type inference.
      // The '? extends B' wildcard from the function signature makes it hard for
      // the compiler to match types in the `map` function. This cast clarifies our intent.
      @SuppressWarnings("unchecked")
      final Kind<G, B> g_of_b_casted = (Kind<G, B>) g_of_b;

      return applicative.map(b -> EITHER.widen(Either.<E, B>right(b)), g_of_b_casted);
    }
  }
}
