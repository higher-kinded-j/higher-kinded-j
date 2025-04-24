package org.simulation.hkt.optional;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.simulation.hkt.Kind;
import org.simulation.hkt.Functor;

import java.util.Optional;
import java.util.function.Function;
import static org.simulation.hkt.optional.OptionalKindHelper.*;

public class OptionalFunctor implements Functor<OptionalKind<?>> {

  @Override
  public <A, B> @NonNull OptionalKind<B> map(@NonNull Function<A, @Nullable B> f, @NonNull Kind<OptionalKind<?>, A> fa) { // Allow function result to be null
    Optional<A> optional = unwrap(fa);
    Optional<B> result = optional.map(f);
    return wrap(result);
  }
}