// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import static org.higherkindedj.hkt.io.IOKindHelper.*;
import static org.higherkindedj.hkt.util.validation.Operation.MAP;

import java.util.function.Function;
import org.higherkindedj.hkt.*;
import org.higherkindedj.hkt.util.validation.FunctionValidator;
import org.higherkindedj.hkt.util.validation.KindValidator;

/**
 * Implements the {@link Functor} type class for {@link IO}, using {@link IOKind.Witness} as the
 * higher-kinded type witness.
 *
 * <p>This implementation provides the ability to transform the result of an IO computation using a
 * pure function, while maintaining the lazy evaluation semantics of IO.
 *
 * @see Functor
 * @see IO
 * @see IOKind
 * @see IOKind.Witness
 * @see IOKindHelper
 */
public class IOFunctor implements Functor<IOKind.Witness> {

  private static final Class<IOFunctor> IO_FUNCTOR_CLASS = IOFunctor.class;

  /**
   * Constructs a new {@code IOFunctor} instance. This constructor is public to allow instantiation
   * where needed, although typically functor operations are accessed via an {@link IOMonad}
   * instance.
   */
  public IOFunctor() {
    // Default constructor
  }

  /**
   * Applies a function to the result of an IO computation, creating a new IO computation that will
   * apply the function when executed.
   *
   * <p>This operation maintains the lazy evaluation semantics of IO - the function is not applied
   * until the resulting IO is executed with {@code unsafeRunSync()}.
   *
   * @param <A> The type of the result of the input IO computation.
   * @param <B> The type of the result after applying the function.
   * @param f The function to apply to the IO result. Must not be null.
   * @param fa The {@code Kind<IOKind.Witness, A>} representing the IO computation to transform.
   *     Must not be null.
   * @return A {@code Kind<IOKind.Witness, B>} representing the transformed IO computation. Never
   *     null.
   * @throws NullPointerException if {@code f} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fa} cannot be unwrapped.
   */
  @Override
  public <A, B> Kind<IOKind.Witness, B> map(
      Function<? super A, ? extends B> f, Kind<IOKind.Witness, A> fa) {

    FunctionValidator.requireMapper(f, IO_FUNCTOR_CLASS, MAP);
    KindValidator.requireNonNull(fa, IO_FUNCTOR_CLASS, MAP);

    IO<A> ioA = IO_OP.narrow(fa);
    IO<B> ioB = ioA.map(f);
    return IO_OP.widen(ioB);
  }
}
