// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.jspecify.annotations.NonNull;

public class IOMonad extends IOApplicative implements Monad<IOKind.Witness> {
  /** Singleton instance of {@code MaybeMonad}. */
  public static final IOMonad INSTANCE = new IOMonad();

  /** Private constructor to enforce the singleton pattern. */
  private IOMonad() {
    // Private constructor
  }

  @Override
  public <A, B> @NonNull Kind<IOKind.Witness, B> flatMap(
      @NonNull Function<A, Kind<IOKind.Witness, B>> f, @NonNull Kind<IOKind.Witness, A> ma) {
    IO<A> ioA = IO_OP.narrow(ma);
    // Need to adapt f: A -> Kind<IO.Witness, B> to A -> IO<B> for IO's flatMap
    IO<B> ioB = ioA.flatMap(a -> IO_OP.narrow(f.apply(a)));
    return IO_OP.widen(ioB);
  }
}
