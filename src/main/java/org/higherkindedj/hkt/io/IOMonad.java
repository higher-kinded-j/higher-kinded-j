package org.higherkindedj.hkt.io;

import static org.higherkindedj.hkt.io.IOKindHelper.unwrap;
import static org.higherkindedj.hkt.io.IOKindHelper.wrap;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.jspecify.annotations.NonNull;

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
