// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader;

import static org.higherkindedj.hkt.reader.ReaderKindHelper.READER;
import static org.higherkindedj.hkt.util.validation.Operation.MAP;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Implements the {@link Functor} interface for the {@link Reader} type, using {@link
 * ReaderKind.Witness} as the higher-kinded type witness.
 *
 * <p>A {@code ReaderFunctor} allows mapping a function over the result value {@code A} of a {@code
 * Reader<R, A>}, while keeping the environment type {@code R} fixed. The {@code R} type parameter
 * of this class specifies the fixed environment type for which this functor instance operates.
 *
 * @param <R> The fixed environment type of the {@link Reader} for which this functor instance is
 *     defined.
 * @see Reader
 * @see ReaderKind
 * @see ReaderKind.Witness
 * @see Functor
 * @see ReaderKindHelper
 */
public class ReaderFunctor<R> implements Functor<ReaderKind.Witness<R>> {

  private static final Class<ReaderFunctor> READER_FUNCTOR_CLASS = ReaderFunctor.class;

  /**
   * Maps a function {@code f} over the value {@code A} contained within a {@code
   * Kind<ReaderKind.Witness<R>, A>}.
   *
   * <p>This operation transforms a {@code Reader<R, A>} into a {@code Reader<R, B>} by applying the
   * function {@code f} to the result of the original reader, without altering the required
   * environment {@code R}.
   *
   * @param f The function to map over the reader's result. Must not be null.
   * @param fa The higher-kinded representation of a {@code Reader<R, A>}. Must not be null.
   * @param <A> The original result type of the {@code Reader}.
   * @param <B> The new result type after applying the function {@code f}.
   * @return A new {@code Kind<ReaderKind.Witness<R>, B>} representing the transformed {@code
   *     Reader<R, B>}. Never null.
   * @throws NullPointerException if {@code f} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fa} cannot be unwrapped
   *     to a valid {@code Reader} representation.
   */
  @Override
  public <A, B> Kind<ReaderKind.Witness<R>, B> map(
      Function<? super A, ? extends B> f, Kind<ReaderKind.Witness<R>, A> fa) {

    Validation.function().requireMapper(f, "f", READER_FUNCTOR_CLASS, MAP);
    Validation.kind().requireNonNull(fa, READER_FUNCTOR_CLASS, MAP);

    Reader<R, A> readerA = READER.narrow(fa);
    Reader<R, B> readerB = readerA.map(f);
    return READER.widen(readerB);
  }
}
