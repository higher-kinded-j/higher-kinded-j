// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

import static org.higherkindedj.hkt.writer.WriterKindHelper.unwrap;
import static org.higherkindedj.hkt.writer.WriterKindHelper.wrap;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NonNull;

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
   */
  @Override
  public <A, B> @NonNull Kind<WriterKind.Witness<W>, B> map(
      @NonNull Function<A, B> f, @NonNull Kind<WriterKind.Witness<W>, A> fa) {
    Writer<W, A> writerA = unwrap(fa);
    Writer<W, B> writerB = writerA.map(f); // Delegates to Writer's own map method
    return wrap(writerB);
  }
}
