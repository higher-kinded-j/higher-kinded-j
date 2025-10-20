// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * The Traverse and Foldable instance for {@link Maybe}.
 *
 * <p>Traversal and folding are performed on the 'Just' value. If the instance is 'Nothing', these
 * operations short-circuit or return an empty/identity value.
 */
public enum MaybeTraverse implements Traverse<MaybeKind.Witness> {
  INSTANCE;

  private static final Class<MaybeTraverse> MAYBE_TRAVERSE_CLASS = MaybeTraverse.class;

  @Override
  public <A, B> Kind<MaybeKind.Witness, B> map(
      Function<? super A, ? extends B> f, Kind<MaybeKind.Witness, A> fa) {

    Validation.function().requireMapper(f, "f", MAYBE_TRAVERSE_CLASS, MAP);
    Validation.kind().requireNonNull(fa, MAYBE_TRAVERSE_CLASS, MAP);

    return MAYBE.widen(MAYBE.narrow(fa).map(f));
  }

  @Override
  public <G, A, B> Kind<G, Kind<MaybeKind.Witness, B>> traverse(
      Applicative<G> applicative,
      Function<? super A, ? extends Kind<G, ? extends B>> f,
      Kind<MaybeKind.Witness, A> ta) {

    Validation.function()
        .requireApplicative(applicative, "applicative", MAYBE_TRAVERSE_CLASS, TRAVERSE);
    Validation.function().requireMapper(f, "f", MAYBE_TRAVERSE_CLASS, TRAVERSE);
    Validation.kind().requireNonNull(ta, MAYBE_TRAVERSE_CLASS, TRAVERSE);

    final Maybe<A> maybe = MAYBE.narrow(ta);

    if (maybe.isJust()) {
      // Just case: Apply the effectful function and map the result back into a Just.
      return applicative.map(b -> MAYBE.widen(Maybe.just(b)), f.apply(maybe.get()));
    } else {
      // Nothing case: Lift the Nothing instance directly into the applicative context.
      return applicative.of(MAYBE.widen(Maybe.nothing()));
    }
  }

  @Override
  public <A, M> M foldMap(
      Monoid<M> monoid, Function<? super A, ? extends M> f, Kind<MaybeKind.Witness, A> fa) {

    Validation.function().requireMonoid(monoid, "monoid", MAYBE_TRAVERSE_CLASS, FOLD_MAP);
    Validation.function().requireMapper(f, "f", MAYBE_TRAVERSE_CLASS, FOLD_MAP);
    Validation.kind().requireNonNull(fa, MAYBE_TRAVERSE_CLASS, FOLD_MAP);

    final Maybe<A> maybe = MAYBE.narrow(fa);
    // If Just, map the value. If Nothing, return the monoid's empty value.
    if (maybe.isJust()) {
      return f.apply(maybe.get());
    } else {
      return monoid.empty();
    }
  }
}
