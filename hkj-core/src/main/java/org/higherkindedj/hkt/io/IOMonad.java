// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.util.ErrorHandling.requireNonNullFunction;
import static org.higherkindedj.hkt.util.ErrorHandling.requireNonNullKind;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;

public class IOMonad extends IOApplicative implements Monad<IOKind.Witness> {
  /** Singleton instance of {@code MaybeMonad}. */
  public static final IOMonad INSTANCE = new IOMonad();

  /** Private constructor to enforce the singleton pattern. */
  private IOMonad() {
    // Private constructor
  }

  @Override
  public <A, B> Kind<IOKind.Witness, B> flatMap(
      Function<? super A, ? extends Kind<IOKind.Witness, B>> f, Kind<IOKind.Witness, A> ma) {
    requireNonNullFunction(f, "function f for flatMap");
    requireNonNullKind(ma, "source Kind for flatMap");

    IO<A> ioA = IO_OP.narrow(ma);
    // Need to adapt f: A -> Kind<IO.Witness, B> to A -> IO<B> for IO's flatMap
    IO<B> ioB = ioA.flatMap(a -> IO_OP.narrow(f.apply(a)));
    return IO_OP.widen(ioB);
  }
}
