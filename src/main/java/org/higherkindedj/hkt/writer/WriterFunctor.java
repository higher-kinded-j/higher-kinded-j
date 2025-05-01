package org.higherkindedj.hkt.writer;

import static org.higherkindedj.hkt.writer.WriterKindHelper.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NonNull;

public class WriterFunctor<W> implements Functor<WriterKind<W, ?>> {
  // Note: Functor instance doesn't need the Monoid, but Monad/Applicative will.

  @Override
  public <A, B> @NonNull Kind<WriterKind<W, ?>, B> map(
      @NonNull Function<A, B> f, @NonNull Kind<WriterKind<W, ?>, A> fa) {
    // 1. Unwrap to get the underlying Writer<W, A>
    Writer<W, A> writerA = unwrap(fa);
    // 2. Use the Writer's own map method
    Writer<W, B> writerB = writerA.map(f);
    // 3. Wrap the result back into the Kind
    return wrap(writerB);
  }
}
