package org.higherkindedj.hkt.reader;

import static org.higherkindedj.hkt.reader.ReaderKindHelper.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NonNull;

public class ReaderFunctor<R> implements Functor<ReaderKind<R, ?>> {

  @Override
  public <A, B> @NonNull Kind<ReaderKind<R, ?>, B> map(
      @NonNull Function<A, B> f, @NonNull Kind<ReaderKind<R, ?>, A> fa) {
    // 1. Unwrap to get the underlying Reader<R, A>
    Reader<R, A> readerA = unwrap(fa);
    // 2. Use the Reader's own map method
    Reader<R, B> readerB = readerA.map(f);
    // 3. Wrap the result back into the Kind
    return wrap(readerB);
  }
}
