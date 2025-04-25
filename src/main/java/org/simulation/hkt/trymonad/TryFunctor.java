package org.simulation.hkt.trymonad;

import static org.simulation.hkt.trymonad.TryKindHelper.*;

import java.util.function.Function;
import org.jspecify.annotations.NonNull;
import org.simulation.hkt.Functor;
import org.simulation.hkt.Kind;

public class TryFunctor implements Functor<TryKind<?>> {

  @Override
  public <A, B> @NonNull Kind<TryKind<?>, B> map(
      @NonNull Function<A, B> f, @NonNull Kind<TryKind<?>, A> fa) {
    // 1. Unwrap the input Kind<TryKind<?>, A> to get the underlying Try<A>
    Try<A> tryA = unwrap(fa); // unwrap handles null/invalid fa

    // 2. Use the underlying Try's map method. It handles Success/Failure cases
    //    and catches exceptions from the mapping function 'f'.
    Try<B> resultTry = tryA.map(f); // map requires non-null f

    // 3. Wrap the resulting Try<B> back into TryKind<B>
    return wrap(resultTry); // wrap requires non-null resultTry
  }
}
