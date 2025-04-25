package org.simulation.hkt.reader;

import static org.simulation.hkt.reader.ReaderKindHelper.*;

import java.util.function.Function;
import org.jspecify.annotations.NonNull;
import org.simulation.hkt.Kind;
import org.simulation.hkt.Monad;

public class ReaderMonad<R> extends ReaderApplicative<R> implements Monad<ReaderKind<R, ?>> {

  @Override
  public <A, B> @NonNull Kind<ReaderKind<R, ?>, B> flatMap(
      @NonNull Function<A, Kind<ReaderKind<R, ?>, B>> f, @NonNull Kind<ReaderKind<R, ?>, A> ma) {

    Reader<R, A> readerA = unwrap(ma);

    // flatMap implementation: r -> f(readerA(r)).run(r)
    // The function 'f' returns a Kind, which needs to be unwrapped inside.
    Reader<R, B> readerB =
        readerA.flatMap(
            a -> {
              Kind<ReaderKind<R, ?>, B> kindB = f.apply(a);
              return unwrap(kindB); // Adapt the function f to return Reader<R, B>
            });

    return wrap(readerB);
  }
}
