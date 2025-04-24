package org.simulation.hkt.maybe;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.simulation.hkt.Kind;
import org.simulation.hkt.Functor;

import java.util.function.Function;

import static org.simulation.hkt.maybe.MaybeKindHelper.unwrap;
import static org.simulation.hkt.maybe.MaybeKindHelper.wrap;

public class MaybeFunctor implements Functor<MaybeKind<?>> {

  @Override
  public <A, B> @NonNull MaybeKind<B> map(@NonNull Function<A, @Nullable B> f, @NonNull Kind<MaybeKind<?>, A> ma) { // Allow function to return null
    // 1. Unwrap the input MaybeKind<A> to get the underlying Maybe<A>
    Maybe<A> maybeA = unwrap(ma); // Handles null/invalid ma

    // 2. Use the underlying Maybe's map method.
    Maybe<B> resultMaybe = maybeA.map(f); // map requires non-null f

    // 3. Wrap the resulting Maybe<B> back into MaybeKind<B>
    return wrap(resultMaybe); // wrap requires non-null Maybe
  }
}