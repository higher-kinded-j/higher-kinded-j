// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

import static org.higherkindedj.hkt.util.validation.Operation.MAP;
import static org.higherkindedj.hkt.writer.WriterKindHelper.WRITER;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.FunctionValidator;
import org.higherkindedj.hkt.util.validation.KindValidator;

/**
 * Implements the {@link Functor} interface for the {@link Writer} type. This allows mapping a
 * function over the result value {@code A} of a {@code Writer<W, A>}, while keeping the log type
 * {@code W} and its accumulated value fixed.
 *
 * @param <W> The fixed log type of the {@link Writer} for which this functor instance is defined.
 * @see Writer
 * @see WriterKind
 * @see WriterKind.Witness
 * @see Functor
 * @see WriterKindHelper
 */
public class WriterFunctor<W> implements Functor<WriterKind.Witness<W>> {

  private static Class<WriterFunctor> WRITER_FUNCTER_CLASS = WriterFunctor.class;

  /**
   * Maps a function {@code f} over the value {@code A} contained within a {@code
   * Kind<WriterKind.Witness<W>, A>}.
   *
   * <p>This operation transforms a {@code Writer<W, A>} into a {@code Writer<W, B>} by applying the
   * function {@code f} to the result of the original writer. The log remains unchanged.
   *
   * @param f The function to map over the writer's result. Must not be null.
   * @param fa The higher-kinded representation of a {@code Writer<W, A>}. Must not be null.
   * @param <A> The original result type of the {@code Writer}.
   * @param <B> The new result type after applying the function {@code f}.
   * @return A new {@code Kind<WriterKind.Witness<W>, B>} representing the transformed {@code
   *     Writer<W, B>}. Never null.
   * @throws NullPointerException if {@code f} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fa} cannot be unwrapped
   *     to a valid {@code Writer} representation.
   */
  @Override
  public <A, B> Kind<WriterKind.Witness<W>, B> map(
      Function<? super A, ? extends B> f, Kind<WriterKind.Witness<W>, A> fa) {

    FunctionValidator.requireMapper(f, "f", WRITER_FUNCTER_CLASS, MAP);
    KindValidator.requireNonNull(fa, WRITER_FUNCTER_CLASS, MAP);

    Writer<W, A> writerA = WRITER.narrow(fa);
    Writer<W, B> writerB = writerA.map(f);
    return WRITER.widen(writerB);
  }
}
