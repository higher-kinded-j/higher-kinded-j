// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import static org.higherkindedj.hkt.io.IOKindHelper.unwrap;
import static org.higherkindedj.hkt.io.IOKindHelper.wrap;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NonNull;

public class IOApplicative extends IOFunctor implements Applicative<IOKind.Witness> {
  @Override
  public <A> @NonNull Kind<IOKind.Witness, A> of(A value) {
    // 'of'/'pure' typically captures a pure value, delaying its evaluation
    // until unsafeRunSync. Contrast with delay() which takes a Supplier.
    return wrap(IO.delay(() -> value));
  }

  @Override
  public <A, B> @NonNull Kind<IOKind.Witness, B> ap(
      @NonNull Kind<IOKind.Witness, Function<A, B>> ff, @NonNull Kind<IOKind.Witness, A> fa) {
    IO<Function<A, B>> ioF = unwrap(ff);
    IO<A> ioA = unwrap(fa);
    // IO<B> ioB = IO { ioF.unsafeRunSync().apply(ioA.unsafeRunSync()) }
    IO<B> ioB = IO.delay(() -> ioF.unsafeRunSync().apply(ioA.unsafeRunSync()));
    return wrap(ioB);
  }
}
