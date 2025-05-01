package org.higherkindedj.hkt.io;

import static org.higherkindedj.hkt.io.IOKindHelper.*;

import java.util.function.Function;
import org.higherkindedj.hkt.*;
import org.jspecify.annotations.NonNull;

public class IOFunctor implements Functor<IOKind<?>> {
  @Override
  public <A, B> @NonNull Kind<IOKind<?>, B> map(
      @NonNull Function<A, B> f, @NonNull Kind<IOKind<?>, A> fa) {
    IO<A> ioA = unwrap(fa);
    IO<B> ioB = ioA.map(f); // Use IO's own map
    return wrap(ioB);
  }
}
