package org.simulation.hkt.maybe;

import org.simulation.hkt.Kind;
import org.simulation.hkt.Monad;

import java.util.function.Function;

import static org.simulation.hkt.maybe.MaybeKindHelper.*;

/**
 * Monad implementation for MaybeKind.
 * Provides Functor and Monad operations for the Maybe type within the HKT simulation.
 */
public class MaybeMonad  extends MaybeFunctor implements Monad<MaybeKind<?>> {

  @Override
  public <A> MaybeKind<A> pure(A value) {
    // Lifts a value into the Maybe context.
    // Use Maybe.fromNullable for safety, allowing null input to become Nothing.
    return wrap(Maybe.fromNullable(value));
  }

  @Override
  public <A, B> MaybeKind<B> flatMap(Function<A, Kind<MaybeKind<?>, B>> f, Kind<MaybeKind<?>, A> ma) {
    // 1. Unwrap the input MaybeKind<A> to get the underlying Maybe<A>
    Maybe<A> maybeA = unwrap(ma);

    // 2. Use the underlying Maybe's flatMap method.
    //    The function provided to Maybe.flatMap needs to return a Maybe<B>.
    //    Our input function 'f' returns a Kind<MaybeKind<?>, B>, so we need to unwrap its result.
    Maybe<B> resultMaybe = maybeA.flatMap(a -> {
      Kind<MaybeKind<?>, B> kindB = f.apply(a); // Apply the user's function
      return unwrap(kindB); // Unwrap the result Kind back to Maybe for Maybe.flatMap
    });

    // 3. Wrap the final Maybe<B> back into MaybeKind<B>
    return wrap(resultMaybe);
  }

}

