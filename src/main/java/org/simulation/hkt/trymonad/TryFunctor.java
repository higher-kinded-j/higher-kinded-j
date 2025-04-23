package org.simulation.hkt.trymonad;

import org.simulation.hkt.Functor;
import org.simulation.hkt.Kind;
import java.util.function.Function;
import static org.simulation.hkt.trymonad.TryKindHelper.*;

public class TryFunctor implements Functor<TryKind<?>> {

  @Override
  public <A, B> Kind<TryKind<?>, B> map(Function<A, B> f, Kind<TryKind<?>, A> fa) {
    // 1. Unwrap the input Kind<TryKind<?>, A> to get the underlying Try<A>
    Try<A> tryA = unwrap(fa);

    // 2. Use the underlying Try's map method. It handles Success/Failure cases
    //    and catches exceptions from the mapping function 'f'.
    Try<B> resultTry = tryA.map(f);

    // 3. Wrap the resulting Try<B> back into TryKind<B>
    return wrap(resultTry);
  }
}