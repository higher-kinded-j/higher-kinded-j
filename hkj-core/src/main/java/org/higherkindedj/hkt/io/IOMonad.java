// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.util.validation.Operation.FLAT_MAP;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Implements the {@link Monad} type class for {@link IO}, using {@link IOKind.Witness} as the
 * higher-kinded type witness.
 *
 * <p>This implementation provides the ability to sequence IO computations, where the second
 * computation depends on the result of the first, while maintaining lazy evaluation semantics. This
 * class is a stateless singleton, accessible via {@link #INSTANCE}.
 *
 * @see Monad
 * @see IOApplicative
 * @see IO
 * @see IOKind
 * @see IOKind.Witness
 * @see IOKindHelper
 */
public class IOMonad extends IOApplicative implements Monad<IOKind.Witness> {

  private static final Class<IOMonad> IO_MONAD_CLASS = IOMonad.class;

  /** Singleton instance of {@code IOMonad}. */
  public static final IOMonad INSTANCE = new IOMonad();

  /** Private constructor to enforce the singleton pattern. */
  protected IOMonad() {
    // Private constructor
  }

  /**
   * Sequentially composes two IO computations, where the second computation (produced by function
   * {@code f}) depends on the result of the first computation ({@code ma}).
   *
   * <p>When the resulting IO is executed, it first runs the first IO computation to get a value,
   * then applies the function {@code f} to that value to get a new IO computation, and finally runs
   * that new IO computation to get the final result.
   *
   * <p>This operation maintains lazy evaluation - no computations are performed until {@code
   * unsafeRunSync()} is called on the resulting IO.
   *
   * @param <A> The type of the result of the first computation {@code ma}.
   * @param <B> The type of the result of the second computation returned by function {@code f}.
   * @param f A function that takes the result of the first computation and returns a new {@code
   *     Kind<IOKind.Witness, B>} representing the next computation. Must not be null.
   * @param ma A {@code Kind<IOKind.Witness, A>} representing the first IO computation. Must not be
   *     null.
   * @return A {@code Kind<IOKind.Witness, B>} representing the sequenced IO computation. Never
   *     null.
   * @throws NullPointerException if {@code f} or {@code ma} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ma} or the {@code Kind}
   *     returned by {@code f} cannot be unwrapped.
   */
  @Override
  public <A, B> Kind<IOKind.Witness, B> flatMap(
      Function<? super A, ? extends Kind<IOKind.Witness, B>> f, Kind<IOKind.Witness, A> ma) {

    Validation.function().validateFlatMap(f, ma, IO_MONAD_CLASS);

    IO<A> ioA = IO_OP.narrow(ma);
    // Adapt f: A -> Kind<IO.Witness, B> to A -> IO<B> for IO's flatMap
    IO<B> ioB =
        ioA.flatMap(
            a -> {
              var kindB = f.apply(a);
              Validation.function()
                  .requireNonNullResult(kindB, "f", IO_MONAD_CLASS, FLAT_MAP, Kind.class);
              return IO_OP.narrow(kindB);
            });
    return IO_OP.widen(ioB);
  }
}
