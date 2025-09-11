// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Traverse;

/**
 * The Traverse and Foldable instance for {@link java.util.Optional}.
 *
 * <p>Traversal and folding operations are performed on the present value. If the Optional is empty,
 * these operations short-circuit or return an empty/identity value.
 */
public enum OptionalTraverse implements Traverse<OptionalKind.Witness> {
  INSTANCE;

  @Override
  public <A, B> Kind<OptionalKind.Witness, B> map(
      Function<? super A, ? extends B> f, Kind<OptionalKind.Witness, A> fa) {
    return OPTIONAL.widen(OPTIONAL.narrow(fa).map(f));
  }

  @Override
  public <G, A, B> Kind<G, Kind<OptionalKind.Witness, B>> traverse(
      Applicative<G> applicative,
      Function<? super A, ? extends Kind<G, ? extends B>> f,
      Kind<OptionalKind.Witness, A> ta) {

    return OPTIONAL
        .narrow(ta)
        .map(f)
        .map(gb -> applicative.map(b -> OPTIONAL.widen(Optional.ofNullable(b)), gb))
        .orElse(applicative.of(OPTIONAL.widen(Optional.empty())));
  }

  @Override
  public <A, M> M foldMap(
      Monoid<M> monoid, Function<? super A, ? extends M> f, Kind<OptionalKind.Witness, A> fa) {
    Optional<A> optional = OPTIONAL.narrow(fa);
    // If present, map the value. If empty, return the monoid's empty value.
    if (optional.isPresent()) {
      return f.apply(optional.get());
    } else {
      return monoid.empty();
    }
  }
}
