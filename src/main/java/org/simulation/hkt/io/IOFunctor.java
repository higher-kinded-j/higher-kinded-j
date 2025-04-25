package org.simulation.hkt.io;

import org.jspecify.annotations.NonNull;
import org.simulation.hkt.*;
import java.util.function.Function;
import static org.simulation.hkt.io.IOKindHelper.*;


public class IOFunctor implements Functor<IOKind<?>> {
  @Override
  public <A, B> @NonNull Kind<IOKind<?>, B> map(
      @NonNull Function<A, B> f, @NonNull Kind<IOKind<?>, A> fa) {
    IO<A> ioA = unwrap(fa);
    IO<B> ioB = ioA.map(f); // Use IO's own map
    return wrap(ioB);
  }
}


