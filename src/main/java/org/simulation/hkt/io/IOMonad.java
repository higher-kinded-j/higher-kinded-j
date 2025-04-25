package org.simulation.hkt.io;

import org.jspecify.annotations.NonNull;
import org.simulation.hkt.Kind;
import org.simulation.hkt.Monad;

import java.util.function.Function;

import static org.simulation.hkt.io.IOKindHelper.unwrap;
import static org.simulation.hkt.io.IOKindHelper.wrap;


public class IOMonad extends IOApplicative implements Monad<IOKind<?>> {
  @Override
  public <A, B> @NonNull Kind<IOKind<?>, B> flatMap(
      @NonNull Function<A, Kind<IOKind<?>, B>> f, @NonNull Kind<IOKind<?>, A> ma) {
    IO<A> ioA = unwrap(ma);
    // Need to adapt f: A -> Kind<IO, B> to A -> IO<B> for IO's flatMap
    IO<B> ioB = ioA.flatMap(a -> unwrap(f.apply(a)));
    return wrap(ioB);
  }
}