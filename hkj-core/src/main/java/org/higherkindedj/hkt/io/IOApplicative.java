// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.util.validation.Operation.AP;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Implements the {@link Applicative} type class for {@link IO}, using {@link IOKind.Witness} as the
 * higher-kinded type witness.
 *
 * <p>This implementation provides the ability to lift pure values into IO computations and to apply
 * functions wrapped in IO to values wrapped in IO, while maintaining lazy evaluation semantics.
 *
 * @see Applicative
 * @see IOFunctor
 * @see IO
 * @see IOKind
 * @see IOKind.Witness
 * @see IOKindHelper
 */
public class IOApplicative extends IOFunctor implements Applicative<IOKind.Witness> {

  private static final Class<IOApplicative> IO_APPLICATIVE_CLASS = IOApplicative.class;

  /** Singleton instance of {@code IOMonad}. */
  public static final IOApplicative INSTANCE = new IOApplicative();

  /** Private constructor to enforce the singleton pattern. */
  protected IOApplicative() {
    super();
  }

  /**
   * Lifts a pure value into the IO applicative context, creating an IO computation that will
   * immediately return the given value when executed.
   *
   * <p>This operation delays the evaluation of the value until {@code unsafeRunSync()} is called,
   * maintaining the lazy semantics of IO. The lifted value can be {@code null}.
   *
   * @param <A> The type of the value.
   * @param value The value to lift into the IO context. Can be {@code null}.
   * @return A {@code Kind<IOKind.Witness, A>} representing an IO computation that returns {@code
   *     value}. Never null.
   */
  @Override
  public <A> Kind<IOKind.Witness, A> of(A value) {
    // 'of'/'pure' captures a pure value, delaying its evaluation until unsafeRunSync
    return IO_OP.widen(IO.delay(() -> value));
  }

  /**
   * Applies an IO-wrapped function to an IO-wrapped value, creating a new IO computation.
   *
   * <p>This operation sequences the evaluation of both IOs: when the resulting IO is executed, it
   * first executes the function IO, then the argument IO, and finally applies the function to the
   * value.
   *
   * @param <A> The input type of the function.
   * @param <B> The output type of the function.
   * @param ff The {@code Kind<IOKind.Witness, Function<A, B>>} containing the function. Must not be
   *     null.
   * @param fa The {@code Kind<IOKind.Witness, A>} containing the argument value. Must not be null.
   * @return A {@code Kind<IOKind.Witness, B>} representing the IO computation that applies the
   *     function to the value. Never null.
   * @throws NullPointerException if {@code ff} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ff} or {@code fa} cannot
   *     be unwrapped.
   */
  @Override
  public <A, B> Kind<IOKind.Witness, B> ap(
      Kind<IOKind.Witness, ? extends Function<A, B>> ff, Kind<IOKind.Witness, A> fa) {

    Validation.kind().requireNonNull(ff, IO_APPLICATIVE_CLASS, AP, "function");
    Validation.kind().requireNonNull(fa, IO_APPLICATIVE_CLASS, AP, "argument");

    IO<? extends Function<A, B>> ioF = IO_OP.narrow(ff);
    IO<A> ioA = IO_OP.narrow(fa);

    // Create an IO that sequences: run ioF, run ioA, apply function to value
    IO<B> ioB = IO.delay(() -> ioF.unsafeRunSync().apply(ioA.unsafeRunSync()));
    return IO_OP.widen(ioB);
  }
}
