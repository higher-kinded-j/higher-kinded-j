// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Traverse;
import org.jspecify.annotations.NonNull;

/**
 * The Traverse instance for {@link java.util.Optional}. Traversal is performed on the present
 * value. If the Optional is empty, the operation short-circuits.
 */
public final class OptionalTraverse implements Traverse<OptionalKind.Witness> {

  public static final OptionalTraverse INSTANCE = new OptionalTraverse();

  private OptionalTraverse() {}

  @Override
  public <A, B> @NonNull Kind<OptionalKind.Witness, B> map(
      @NonNull Function<A, B> f, @NonNull Kind<OptionalKind.Witness, A> fa) {
    return OPTIONAL.widen(OPTIONAL.narrow(fa).map(f));
  }

  @Override
  public <G, A, B> Kind<G, Kind<OptionalKind.Witness, B>> traverse(
      @NonNull Applicative<G> applicative,
      @NonNull Kind<OptionalKind.Witness, A> ta,
      @NonNull Function<? super A, ? extends Kind<G, ? extends B>> f) {

    final Optional<A> optional = OPTIONAL.narrow(ta);

    if (optional.isPresent()) {
      final A value = optional.get();
      final Kind<G, ? extends B> g_of_b = f.apply(value);

      @SuppressWarnings("unchecked")
      final Kind<G, B> g_of_b_casted = (Kind<G, B>) g_of_b;

      // Map the result into a new Optional and widen to a Kind
      return applicative.map(b -> OPTIONAL.widen(Optional.of(b)), g_of_b_casted);
    } else {
      // If empty, do nothing. Just lift the empty Optional into the applicative.
      return applicative.of(OPTIONAL.widen(Optional.empty()));
    }
  }
}
