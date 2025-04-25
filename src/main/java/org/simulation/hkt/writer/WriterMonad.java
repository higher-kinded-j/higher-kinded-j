package org.simulation.hkt.writer;

import org.jspecify.annotations.NonNull;
import org.simulation.hkt.Kind;
import org.simulation.hkt.Monad;
import org.simulation.hkt.typeclass.Monoid;

import java.util.function.Function;
import static org.simulation.hkt.writer.WriterKindHelper.*;

public class WriterMonad<W> extends WriterApplicative<W> implements Monad<WriterKind<W, ?>> {

  public WriterMonad(@NonNull Monoid<W> monoidW) {
    super(monoidW);
  }

  @Override
  public <A, B> @NonNull Kind<WriterKind<W, ?>, B> flatMap(
      @NonNull Function<A, Kind<WriterKind<W, ?>, B>> f,
      @NonNull Kind<WriterKind<W, ?>, A> ma) {

    Writer<W, A> writerA = unwrap(ma);

    // Use the underlying Writer's flatMap, adapting the function 'f'
    Writer<W, B> writerB = writerA.flatMap(monoidW, a -> {
      Kind<WriterKind<W, ?>, B> kindB = f.apply(a);
      return unwrap(kindB); // Adapt f to return Writer<W, B>
    });

    return wrap(writerB);
  }
}
