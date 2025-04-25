package org.simulation.hkt.optional;

import static org.simulation.hkt.optional.OptionalKindHelper.*;

import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.simulation.hkt.Functor;
import org.simulation.hkt.Kind;

public class OptionalFunctor implements Functor<OptionalKind<?>> {

  @Override
  public <A, B> @NonNull OptionalKind<B> map(
      @NonNull Function<A, @Nullable B> f,
      @NonNull Kind<OptionalKind<?>, A> fa) { // Allow function result to be null
    Optional<A> optional = unwrap(fa);
    Optional<B> result = optional.map(f);
    return wrap(result);
  }
}
