// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;

public class IOApplicative extends IOFunctor implements Applicative<IOKind.Witness> {
  @Override
  public <A> Kind<IOKind.Witness, A> of(A value) {
    // 'of'/'pure' typically captures a pure value, delaying its evaluation
    // until unsafeRunSync. Contrast with delay() which takes a Supplier.
    return IO_OP.widen(IO.delay(() -> value));
  }

  @Override
  public <A, B> Kind<IOKind.Witness, B> ap(
      Kind<IOKind.Witness, ? extends Function<A, B>> ff, Kind<IOKind.Witness, A> fa) {
    IO<? extends Function<A, B>> ioF = IO_OP.narrow(ff);
    IO<A> ioA = IO_OP.narrow(fa);
    // IO<B> ioB = IO { ioF.unsafeRunSync().apply(ioA.unsafeRunSync()) }
    IO<B> ioB = IO.delay(() -> ioF.unsafeRunSync().apply(ioA.unsafeRunSync()));
    return IO_OP.widen(ioB);
  }
}
