// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Traverse;
import org.jspecify.annotations.NonNull;

/**
 * The Traverse and Foldable instance for {@link Try}.
 *
 * <p>Traversal and folding operations are performed on the 'Success' value. If the instance is
 * 'Failure', these operations short-circuit or return an empty/identity value.
 */
public enum TryTraverse implements Traverse<TryKind.Witness> {
  INSTANCE;

  @Override
  public <A, B> @NonNull Kind<TryKind.Witness, B> map(
      @NonNull Function<? super A, ? extends B> f, @NonNull Kind<TryKind.Witness, A> fa) {
    // Delegate to Try's own map method and widen the result.
    return TRY.widen(TRY.narrow(fa).map(f));
  }

  @Override
  public <G, A, B> @NonNull Kind<G, Kind<TryKind.Witness, B>> traverse(
      @NonNull Applicative<G> applicative,
      @NonNull Kind<TryKind.Witness, A> ta,
      @NonNull Function<? super A, ? extends Kind<G, ? extends B>> f) {

    return TRY.narrow(ta)
        .fold(
            // Success case: Apply the effectful function and wrap the result in a Success.
            successValue -> applicative.map(b -> TRY.widen(Try.success(b)), f.apply(successValue)),

            // Failure case: Lift the Failure directly into the applicative context.
            cause -> applicative.of(TRY.widen(Try.failure(cause))));
  }

  @Override
  public <A, M> M foldMap(
      @NonNull Monoid<M> monoid,
      @NonNull Function<? super A, ? extends M> f,
      @NonNull Kind<TryKind.Witness, A> fa) {

    // If the Try is a Success, apply the function `f` to the value.
    // If it's a Failure, return the identity element of the Monoid.
    return TRY.narrow(fa).fold(f, cause -> monoid.empty());
  }
}
