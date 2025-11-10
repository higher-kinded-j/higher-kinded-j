// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader;

import static org.higherkindedj.hkt.reader.ReaderKindHelper.READER;
import static org.higherkindedj.hkt.util.validation.Operation.AP;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * Implements the {@link Applicative} interface for the {@link Reader} type, using {@link
 * ReaderKind.Witness} as the higher-kinded type witness.
 *
 * <p>A {@code ReaderApplicative} provides the ability to lift values into the {@link Reader}
 * context (via {@link #of(Object)}) and to apply a {@code Reader} holding a function to a {@code
 * Reader} holding a value (via {@link #ap(Kind, Kind)}). The {@code R} type parameter of this class
 * specifies the fixed environment type for which this applicative instance operates.
 *
 * @param <R> The fixed environment type of the {@link Reader} for which this applicative instance
 *     is defined.
 * @see Reader
 * @see ReaderKind
 * @see ReaderKind.Witness
 * @see Applicative
 * @see ReaderFunctor
 * @see ReaderKindHelper
 */
public class ReaderApplicative<R> extends ReaderFunctor<R>
    implements Applicative<ReaderKind.Witness<R>> {

  private static final Class<ReaderApplicative> READER_APPLICATIVE_CLASS = ReaderApplicative.class;

  /**
   * Lifts a pure value {@code value} into the {@link Reader} context. The resulting {@code
   * Reader<R, A>} will produce the given {@code value} regardless of the environment {@code R} it
   * is run with.
   *
   * @param value The value to lift into the {@code Reader} context. Can be {@code null} if {@code
   *     A} is a nullable type.
   * @param <A> The type of the lifted value.
   * @return A {@code Kind<ReaderKind.Witness<R>, A>} representing a {@code Reader<R, A>} that, when
   *     run, yields the given {@code value}. Never null.
   */
  @Override
  public <A> Kind<ReaderKind.Witness<R>, A> of(@Nullable A value) {
    return READER.constant(value);
  }

  /**
   * Applies a function contained within a {@code Kind<ReaderKind.Witness<R>, Function<A, B>>} to a
   * value contained within a {@code Kind<ReaderKind.Witness<R>, A>}.
   *
   * <p>Both underlying {@code Reader} instances are run with the same environment {@code R}. The
   * function extracted from the first reader is then applied to the value extracted from the second
   * reader.
   *
   * <p>The behaviour is: {@code r -> readerF.run(r).apply(readerA.run(r))}.
   *
   * @param ff The higher-kinded representation of a {@code Reader<R, Function<A, B>>}. Must not be
   *     null.
   * @param fa The higher-kinded representation of a {@code Reader<R, A>}. Must not be null.
   * @param <A> The type of the value to which the function is applied.
   * @param <B> The result type of the function application.
   * @return A new {@code Kind<ReaderKind.Witness<R>, B>} representing the {@code Reader<R, B>} that
   *     results from applying the function. Never null.
   * @throws NullPointerException if {@code ff} or {@code fa} is null, or if the function extracted
   *     from the Reader is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ff} or {@code fa} cannot
   *     be unwrapped to valid {@code Reader} representations.
   */
  @Override
  public <A, B> Kind<ReaderKind.Witness<R>, B> ap(
      Kind<ReaderKind.Witness<R>, ? extends Function<A, B>> ff, Kind<ReaderKind.Witness<R>, A> fa) {

    Validation.kind().requireNonNull(ff, READER_APPLICATIVE_CLASS, AP, "function");
    Validation.kind().requireNonNull(fa, READER_APPLICATIVE_CLASS, AP, "argument");

    Reader<R, ? extends Function<A, B>> readerF = READER.narrow(ff);
    Reader<R, A> readerA = READER.narrow(fa);

    Reader<R, B> readerB =
        (R r) -> {
          Function<A, B> func = readerF.run(r);
          A val = readerA.run(r);

          if (func == null) {
            throw new NullPointerException("Function extracted from Reader for 'ap' was null");
          }

          return func.apply(val);
        };

    return READER.widen(readerB);
  }
}
