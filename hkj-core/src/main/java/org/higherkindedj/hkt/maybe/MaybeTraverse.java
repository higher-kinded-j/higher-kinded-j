// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Traverse;
import org.jspecify.annotations.NonNull;

/**
 * The Traverse instance for {@link Maybe}. Traversal is performed on the 'Just' value. If the
 * instance is 'Nothing', the operation short-circuits.
 */
public final class MaybeTraverse implements Traverse<MaybeKind.Witness> {

  public static final MaybeTraverse INSTANCE = new MaybeTraverse();

  private MaybeTraverse() {}

  @Override
  public <A, B> @NonNull Kind<MaybeKind.Witness, B> map(
      @NonNull Function<A, B> f, @NonNull Kind<MaybeKind.Witness, A> fa) {
    return MAYBE.widen(MAYBE.narrow(fa).map(f));
  }

  @Override
  public <G, A, B> Kind<G, Kind<MaybeKind.Witness, B>> traverse(
      @NonNull Applicative<G> applicative,
      @NonNull Kind<MaybeKind.Witness, A> ta,
      @NonNull Function<? super A, ? extends Kind<G, ? extends B>> f) {

    final Maybe<A> maybe = MAYBE.narrow(ta);

    if (maybe.isJust()) {
      final A value = maybe.get();
      final Kind<G, ? extends B> g_of_b = f.apply(value);

      @SuppressWarnings("unchecked")
      final Kind<G, B> g_of_b_casted = (Kind<G, B>) g_of_b;

      // Map the result into a new Just and widen to a Kind
      return applicative.map(b -> MAYBE.widen(Maybe.just(b)), g_of_b_casted);
    } else {
      // If Nothing, do nothing. Just lift the Nothing instance into the applicative.
      return applicative.of(MAYBE.widen(Maybe.nothing()));
    }
  }
}
