package org.simulation.hkt.maybe;

import org.simulation.hkt.Kind;
import org.simulation.hkt.Functor;

import java.util.function.Function;

import static org.simulation.hkt.maybe.MaybeKindHelper.unwrap;
import static org.simulation.hkt.maybe.MaybeKindHelper.wrap;

public class MaybeFunctor implements Functor<MaybeKind<?>> {

  @Override
  public <A, B> MaybeKind<B> map(Function<A, B> f, Kind<MaybeKind<?>, A> ma) {
    // 1. Unwrap the input MaybeKind<A> to get the underlying Maybe<A>
    Maybe<A> maybeA = unwrap(ma);

    // 2. Use the underlying Maybe's map method.
    Maybe<B> resultMaybe = maybeA.map(f);

    // 3. Wrap the resulting Maybe<B> back into MaybeKind<B>
    return wrap(resultMaybe);
  }
}
