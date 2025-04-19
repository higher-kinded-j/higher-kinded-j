package org.simulation.hkt.maybe;

import org.simulation.hkt.Kind;
import org.simulation.hkt.Monad;

import java.util.function.Function;

import static org.simulation.hkt.maybe.MaybeKindHelper.*;

/**
 * Monad implementation for MaybeKind.
 * Provides Functor and Monad operations for the Maybe type within the HKT simulation.
 */
public class MaybeMonad extends MaybeFunctor implements Monad<MaybeKind<?>> {

  @Override
  public <A> MaybeKind<A> of(A value) {
    return wrap(Maybe.fromNullable(value));
  }

  @Override
  public <A, B> MaybeKind<B> flatMap(Function<A, Kind<MaybeKind<?>, B>> f, Kind<MaybeKind<?>, A> ma) {
    Maybe<A> maybeA = unwrap(ma);

    Maybe<B> resultMaybe = maybeA.flatMap(a -> {
      Kind<MaybeKind<?>, B> kindB = f.apply(a);
      return unwrap(kindB);
    });

    return wrap(resultMaybe);
  }


  @Override
  public <A, B> Kind<MaybeKind<?>, B> ap(Kind<MaybeKind<?>, Function<A, B>> ff, Kind<MaybeKind<?>, A> fa) {
    Maybe<Function<A, B>> maybeF = unwrap(ff);
    Maybe<A> maybeA = unwrap(fa);

    // If function Maybe is Just AND value Maybe is Just, apply function
    // Otherwise, return Nothing. Maybe's flatMap/map handles this.
    Maybe<B> resultMaybe = maybeF.flatMap(f -> maybeA.map(f)); // flatMap on function, map on value

    return wrap(resultMaybe);
  }

}

