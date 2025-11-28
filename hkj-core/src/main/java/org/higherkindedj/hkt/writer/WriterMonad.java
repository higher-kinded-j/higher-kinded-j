// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

import static org.higherkindedj.hkt.util.validation.Operation.FLAT_MAP;
import static org.higherkindedj.hkt.writer.WriterKindHelper.WRITER;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Implements the {@link Monad} interface for the {@link Writer} type. This provides the full
 * monadic capabilities for {@code Writer}, including {@code flatMap}, building upon the {@link
 * WriterApplicative} functionality.
 *
 * <p>A {@link Monoid} for the log type {@code W} is required to combine logs during sequencing
 * operations like {@code flatMap}.
 *
 * @param <W> The fixed log type of the {@link Writer}, which must have a {@link Monoid}.
 * @see Writer
 * @see WriterKind
 * @see WriterKind.Witness
 * @see Monad
 * @see WriterApplicative
 * @see Monoid
 * @see WriterKindHelper
 */
public class WriterMonad<W> extends WriterApplicative<W> implements Monad<WriterKind.Witness<W>> {

  private static final Class<WriterMonad> WRITER_MAONAD_CLASS = WriterMonad.class;

  /**
   * Constructs a {@code WriterMonad}.
   *
   * @param monoidW The {@link Monoid} instance for the log type {@code W}. Must not be null. This
   *     is essential for combining logs in {@code flatMap}.
   * @throws NullPointerException if {@code monoidW} is null.
   */
  public WriterMonad(Monoid<W> monoidW) {
    super(monoidW);
  }

  /**
   * Sequentially composes two {@link Writer} actions, passing the result of the first into a
   * function that produces the second, and combining their logs.
   *
   * <p>This method unwraps the initial {@code Kind<WriterKind.Witness<W>, A>} to a {@code Writer<W,
   * A>}. It then uses the {@code Writer}'s own {@code flatMap} method, adapting the input function
   * {@code f} (which returns a {@code Kind}) to one that returns a concrete {@code Writer} by
   * unwrapping. The logs are combined according to the {@link Monoid} for {@code W}.
   *
   * @param f A function that takes a value of type {@code A} (from the result of {@code ma}) and
   *     returns a {@code Kind<WriterKind.Witness<W>, B>}. Must not be null.
   * @param ma The higher-kinded representation of the initial {@code Writer<W, A>}. Must not be
   *     null.
   * @param <A> The value type of the initial {@code Writer}.
   * @param <B> The value type of the {@code Writer} produced by the function {@code f}.
   * @return A {@code Kind<WriterKind.Witness<W>, B>} representing the composed {@code Writer<W,
   *     B>}. Never null.
   * @throws NullPointerException if {@code f} or {@code ma} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ma} or the result of
   *     {@code f} cannot be unwrapped to a valid {@code Writer} representation.
   */
  @Override
  public <A, B> Kind<WriterKind.Witness<W>, B> flatMap(
      Function<? super A, ? extends Kind<WriterKind.Witness<W>, B>> f,
      Kind<WriterKind.Witness<W>, A> ma) {

    Validation.function().validateFlatMap(f, ma, WRITER_MAONAD_CLASS);

    Writer<W, A> writerA = WRITER.narrow(ma);

    // Adapt the function f: A -> Kind<WriterKind.Witness<W>, B>
    // to a function A -> Writer<W, B> for Writer's native flatMap.
    Writer<W, B> writerB =
        writerA.flatMap(
            this.monoidW,
            a -> {
              Kind<WriterKind.Witness<W>, B> kindB = f.apply(a);
              Validation.function()
                  .requireNonNullResult(kindB, "f", WRITER_MAONAD_CLASS, FLAT_MAP, Kind.class);
              return WRITER.narrow(kindB);
            });

    return WRITER.widen(writerB);
  }
}
