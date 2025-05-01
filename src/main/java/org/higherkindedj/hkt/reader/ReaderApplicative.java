package org.higherkindedj.hkt.reader;

import static org.higherkindedj.hkt.reader.ReaderKindHelper.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class ReaderApplicative<R> extends ReaderFunctor<R>
    implements Applicative<ReaderKind<R, ?>> {

  @Override
  public <A> @NonNull Kind<ReaderKind<R, ?>, A> of(@Nullable A value) {
    // 'of' creates a Reader that ignores the environment and returns the value.
    return ReaderKindHelper.constant(value);
  }

  @Override
  public <A, B> @NonNull Kind<ReaderKind<R, ?>, B> ap(
      @NonNull Kind<ReaderKind<R, ?>, Function<A, B>> ff, @NonNull Kind<ReaderKind<R, ?>, A> fa) {

    Reader<R, Function<A, B>> readerF = unwrap(ff);
    Reader<R, A> readerA = unwrap(fa);

    // Implement ap: r -> readerF(r).apply(readerA(r))
    Reader<R, B> readerB =
        (R r) -> {
          Function<A, B> func = readerF.run(r); // Get the function from the environment
          A val = readerA.run(r); // Get the value from the environment
          return func.apply(val); // Apply the function
        };

    return wrap(readerB);
  }
}
