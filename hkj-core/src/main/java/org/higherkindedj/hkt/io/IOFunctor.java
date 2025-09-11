// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import static org.higherkindedj.hkt.io.IOKindHelper.*;

import java.util.function.Function;
import org.higherkindedj.hkt.*;

public class IOFunctor implements Functor<IOKind.Witness> {
  @Override
  public <A, B> Kind<IOKind.Witness, B> map(
      Function<? super A, ? extends B> f, Kind<IOKind.Witness, A> fa) {
    IO<A> ioA = IO_OP.narrow(fa);
    IO<B> ioB = ioA.map(f); // Use IO's own map
    return IO_OP.widen(ioB);
  }
}
