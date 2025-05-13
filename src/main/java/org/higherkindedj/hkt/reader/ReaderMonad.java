package org.higherkindedj.hkt.reader;

import static org.higherkindedj.hkt.reader.ReaderKindHelper.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.jspecify.annotations.NonNull;

public class ReaderMonad<R> extends ReaderApplicative<R> implements Monad<ReaderKind.Witness<R>> {

  @Override
  public <A, B> @NonNull Kind<ReaderKind.Witness<R>, B> flatMap(
      @NonNull Function<A, Kind<ReaderKind.Witness<R>, B>> f,
      @NonNull Kind<ReaderKind.Witness<R>, A> ma) {

    Reader<R, A> readerA = unwrap(ma);

    Reader<R, B> readerB =
        readerA.flatMap(
            a -> {
              Kind<ReaderKind.Witness<R>, B> kindB = f.apply(a);
              return unwrap(kindB);
            });

    return wrap(readerB);
  }
}
