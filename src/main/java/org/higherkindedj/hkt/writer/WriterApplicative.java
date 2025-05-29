// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

import static java.util.Objects.requireNonNull;
import static org.higherkindedj.hkt.writer.WriterKindHelper.unwrap;
import static org.higherkindedj.hkt.writer.WriterKindHelper.wrap;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Implements the {@link Applicative} interface for the {@link Writer} type. This provides the
 * ability to lift values into the {@link Writer} context ({@code of}) and to apply a {@code Writer}
 * holding a function to a {@code Writer} holding a value ({@code ap}).
 *
 * <p>A {@link Monoid} for the log type {@code W} is required to combine logs during the {@code ap}
 * operation.
 *
 * @param <W> The fixed log type of the {@link Writer}, which must have a {@link Monoid}.
 * @see Writer
 * @see WriterKind
 * @see WriterKind.Witness
 * @see Applicative
 * @see WriterFunctor
 * @see Monoid
 * @see WriterKindHelper
 */
public class WriterApplicative<W> extends WriterFunctor<W>
    implements Applicative<WriterKind.Witness<W>> {

  protected final @NonNull Monoid<W> monoidW;

  /**
   * Constructs a {@code WriterApplicative}.
   *
   * @param monoidW The {@link Monoid} instance for the log type {@code W}. Must not be null. This
   *     is used for combining logs in operations like {@code ap}.
   */
  public WriterApplicative(@NonNull Monoid<W> monoidW) {
    this.monoidW = requireNonNull(monoidW, "Monoid<W> cannot be null for WriterApplicative");
  }

  /**
   * Lifts a pure value {@code value} into the {@link Writer} context. The resulting {@code
   * Writer<W, A>} will have an empty log (as defined by the {@link Monoid} for {@code W}) and the
   * provided {@code value}.
   *
   * @param value The value to lift into the {@code Writer} context. Can be {@code null}.
   * @param <A> The type of the lifted value.
   * @return A {@code Kind<WriterKind.Witness<W>, A>} representing a {@code Writer<W, A>} with an
   *     empty log and the given {@code value}. Never null.
   */
  @Override
  public <A> @NonNull Kind<WriterKind.Witness<W>, A> of(@Nullable A value) {
    return WriterKindHelper.value(monoidW, value);
  }

  /**
   * Applies a function contained within a {@code Kind<WriterKind.Witness<W>, Function<A, B>>} to a
   * value contained within a {@code Kind<WriterKind.Witness<W>, A>}.
   *
   * <p>The logs from both underlying {@code Writer} instances are combined using the {@link Monoid}
   * for {@code W}. The function from the first writer is applied to the value from the second
   * writer.
   *
   * @param ff The higher-kinded representation of a {@code Writer<W, Function<A, B>>}. Must not be
   *     null.
   * @param fa The higher-kinded representation of a {@code Writer<W, A>}. Must not be null.
   * @param <A> The type of the value to which the function is applied.
   * @param <B> The result type of the function application.
   * @return A new {@code Kind<WriterKind.Witness<W>, B>} representing the {@code Writer<W, B>} that
   *     results from applying the function and combining logs. Never null.
   * @throws NullPointerException if the function extracted from {@code ff} is null, which is
   *     generally not expected for a valid {@code ap} operation.
   */
  @Override
  public <A, B> @NonNull Kind<WriterKind.Witness<W>, B> ap(
      @NonNull Kind<WriterKind.Witness<W>, Function<A, B>> ff,
      @NonNull Kind<WriterKind.Witness<W>, A> fa) {

    Writer<W, Function<A, B>> writerF = unwrap(ff);
    Writer<W, A> writerA = unwrap(fa);

    W combinedLog = monoidW.combine(writerF.log(), writerA.log());

    Function<A, B> func = writerF.value();
    A val = writerA.value();

    requireNonNull(
        func,
        "Function wrapped in Writer for 'ap' was null. "
            + "This may indicate an issue with how the Writer<W, Function<A,B>> was constructed.");
    B resultValue = func.apply(val);

    return wrap(new Writer<>(combinedLog, resultValue));
  }
}
