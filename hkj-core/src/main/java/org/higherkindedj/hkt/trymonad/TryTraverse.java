// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Traverse;
import org.jspecify.annotations.NonNull;

/**
 * The Traverse instance for {@link Try}. Traversal is performed on the 'Success' value. If the
 * instance is 'Failure', the operation short-circuits.
 */
public final class TryTraverse implements Traverse<TryKind.Witness> {

  public static final TryTraverse INSTANCE = new TryTraverse();

  private TryTraverse() {}

  @Override
  public <A, B> @NonNull Kind<TryKind.Witness, B> map(
      @NonNull Function<A, B> f, @NonNull Kind<TryKind.Witness, A> fa) {
    // Delegate to Try's own map method and widen the result.
    return TRY.widen(TRY.narrow(fa).map(f));
  }

  @Override
  public <G, A, B> Kind<G, Kind<TryKind.Witness, B>> traverse(
      @NonNull Applicative<G> applicative,
      @NonNull Kind<TryKind.Witness, A> ta,
      @NonNull Function<? super A, ? extends Kind<G, ? extends B>> f) {

    final Try<A> tryA = TRY.narrow(ta);

    // Use the fold method for a type-safe implementation.
    return tryA.fold(
        successValue -> {
          // Case 1: The Try is a Success.
          // Apply the effectful function `f` to the value.
          final Kind<G, ? extends B> g_of_b = f.apply(successValue);

          // This cast helps the type-inferencer with the '? extends B' wildcard.
          @SuppressWarnings("unchecked")
          final Kind<G, B> g_of_b_casted = (Kind<G, B>) g_of_b;

          // Map over the result to wrap the new value `B` back into a `Success`,
          // then widen to a Kind.
          return applicative.map(b -> TRY.widen(Try.<B>success(b)), g_of_b_casted);
        },
        cause -> {
          // Case 2: The Try is a Failure.
          // We do nothing and simply lift the Failure instance into the Applicative context 'G'.
          // We use the static factory from Try to ensure the correct generic type.
          final Try<B> result = Try.failure(cause);
          return applicative.of(TRY.widen(result));
        });
  }
}
