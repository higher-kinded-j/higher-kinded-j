// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader;

import static org.higherkindedj.hkt.reader.ReaderKindHelper.unwrap;
import static org.higherkindedj.hkt.reader.ReaderKindHelper.wrap;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NonNull;
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
  public <A> @NonNull Kind<ReaderKind.Witness<R>, A> of(@Nullable A value) {
    // Reader.constant(value) creates a Reader that ignores the environment and returns the value.
    return ReaderKindHelper.constant(value);
  }

  /**
   * Applies a function contained within a {@code Kind<ReaderKind.Witness<R>, Function<A, B>>} to a
   * value contained within a {@code Kind<ReaderKind.Witness<R>, A>}.
   *
   * <p>Both underlying {@code Reader} instances are run with the same environment {@code R}. The
   * function extracted from the first reader is then applied to the value extracted from the second
   * reader.
   *
   * <p>The behavior is: {@code r -> readerF.run(r).apply(readerA.run(r))}.
   *
   * @param ff The higher-kinded representation of a {@code Reader<R, Function<A, B>>}. Must not be
   *     null.
   * @param fa The higher-kinded representation of a {@code Reader<R, A>}. Must not be null.
   * @param <A> The type of the value to which the function is applied.
   * @param <B> The result type of the function application.
   * @return A new {@code Kind<ReaderKind.Witness<R>, B>} representing the {@code Reader<R, B>} that
   *     results from applying the function. Never null.
   */
  @Override
  public <A, B> @NonNull Kind<ReaderKind.Witness<R>, B> ap(
      @NonNull Kind<ReaderKind.Witness<R>, Function<A, B>> ff,
      @NonNull Kind<ReaderKind.Witness<R>, A> fa) {

    Reader<R, Function<A, B>> readerF = unwrap(ff);
    Reader<R, A> readerA = unwrap(fa);

    Reader<R, B> readerB =
        (R r) -> {
          Function<A, B> func = readerF.run(r);
          A val = readerA.run(r);
          return func.apply(val);
        };

    return wrap(readerB);
  }
}
