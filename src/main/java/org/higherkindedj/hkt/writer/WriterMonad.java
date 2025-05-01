package org.higherkindedj.hkt.writer;

import static org.higherkindedj.hkt.writer.WriterKindHelper.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.typeclass.Monoid;
import org.jspecify.annotations.NonNull;

public class WriterMonad<W> extends WriterApplicative<W> implements Monad<WriterKind<W, ?>> {

  public WriterMonad(@NonNull Monoid<W> monoidW) {
    super(monoidW);
  }

  @Override
  public <A, B> @NonNull Kind<WriterKind<W, ?>, B> flatMap(
      @NonNull Function<A, Kind<WriterKind<W, ?>, B>> f, @NonNull Kind<WriterKind<W, ?>, A> ma) {

    Writer<W, A> writerA = unwrap(ma);

    // Use the underlying Writer's flatMap, adapting the function 'f'
    Writer<W, B> writerB =
        writerA.flatMap(
            monoidW,
            a -> {
              Kind<WriterKind<W, ?>, B> kindB = f.apply(a);
              return unwrap(kindB); // Adapt f to return Writer<W, B>
            });

    return wrap(writerB);
  }
}
