package org.simulation.hkt.optional;

import org.simulation.hkt.Kind;
import org.simulation.hkt.Functor;

import java.util.Optional;
import java.util.function.Function;
import static org.simulation.hkt.optional.OptionalKindHelper.*;

public class OptionalFunctor implements Functor<OptionalKind<?>> {

  @Override
  public <A, B> OptionalKind<B> map(Function<A, B> f, Kind<OptionalKind<?>, A> fa) {
    Optional<A> optional = unwrap(fa);
    Optional<B> result = optional.map(f);
    return wrap(result);
  }

}

