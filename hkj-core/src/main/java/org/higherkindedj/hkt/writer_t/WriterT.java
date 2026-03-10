// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer_t;

import static org.higherkindedj.hkt.util.validation.Operation.*;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Pair;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Represents the Writer Transformer Monad, {@code WriterT<F, W, A>}. It wraps a monadic value of
 * type {@code Kind<F, Pair<A, W>>}, where {@code F} is the witness type of the outer monad, {@code
 * W} is the output/log type, and {@code A} is the value type.
 *
 * <p>The output type {@code W} is accumulated using a {@link Monoid} during monadic composition.
 * The value comes first in the pair ({@code Pair<A, W>}), following the Haskell convention.
 *
 * @param <F> The witness type of the outer monad.
 * @param <W> The type of the accumulated output (must form a {@link Monoid}).
 * @param <A> The type of the computed value.
 * @param run The wrapped monadic computation producing a value-output pair.
 * @see WriterTKind
 * @see WriterTMonad
 * @see WriterTKindHelper
 * @see Monoid
 * @see Pair
 */
public record WriterT<F extends WitnessArity<TypeArity.Unary>, W, A>(Kind<F, Pair<A, W>> run)
    implements WriterTKind<F, W, A> {

  private static final Class<WriterT> WRITER_T_CLASS = WriterT.class;

  /**
   * Compact constructor. Validates that the run function is not null.
   *
   * @param run The wrapped monadic computation. Must not be null.
   * @throws NullPointerException if {@code run} is null.
   */
  public WriterT {
    Validation.kind().requireNonNull(run, CONSTRUCTION, "run");
  }

  /**
   * Creates a {@link WriterT} from an existing {@code Kind<F, Pair<A, W>>}.
   *
   * @param <F> The witness type of the outer monad.
   * @param <W> The type of the accumulated output.
   * @param <A> The type of the value.
   * @param run The wrapped monadic computation. Must not be null.
   * @return A new {@link WriterT} instance.
   * @throws NullPointerException if {@code run} is null.
   */
  public static <F extends WitnessArity<TypeArity.Unary>, W, A> WriterT<F, W, A> fromKind(
      Kind<F, Pair<A, W>> run) {
    return new WriterT<>(run);
  }

  /**
   * Creates a {@link WriterT} with a pure value and an empty output.
   *
   * @param <F> The witness type of the outer monad.
   * @param <W> The type of the accumulated output.
   * @param <A> The type of the value.
   * @param monad The {@link Monad} instance for the outer monad. Must not be null.
   * @param monoid The {@link Monoid} instance for the output type. Must not be null.
   * @param value The value to wrap.
   * @return A new {@link WriterT} instance with the given value and empty output.
   * @throws NullPointerException if {@code monad} or {@code monoid} is null.
   */
  public static <F extends WitnessArity<TypeArity.Unary>, W, A> WriterT<F, W, A> of(
      Monad<F> monad, Monoid<W> monoid, A value) {
    Validation.transformer().requireOuterMonad(monad, WRITER_T_CLASS, OF);
    Validation.function().require(monoid, "monoid", OF);
    return new WriterT<>(monad.of(Pair.of(value, monoid.empty())));
  }

  /**
   * Creates a {@link WriterT} that records the given output with {@link Unit#INSTANCE} as its
   * value.
   *
   * @param <F> The witness type of the outer monad.
   * @param <W> The type of the accumulated output.
   * @param monad The {@link Monad} instance for the outer monad. Must not be null.
   * @param w The output to record. Must not be null.
   * @return A new {@link WriterT} with the specified output and {@link Unit#INSTANCE} as value.
   * @throws NullPointerException if {@code monad} or {@code w} is null.
   */
  public static <F extends WitnessArity<TypeArity.Unary>, W> WriterT<F, W, Unit> tell(
      Monad<F> monad, W w) {
    Validation.transformer().requireOuterMonad(monad, WRITER_T_CLASS, TELL);
    Validation.coreType().requireValue(w, "w", WRITER_T_CLASS, TELL);
    return new WriterT<>(monad.of(Pair.of(Unit.INSTANCE, w)));
  }

  /**
   * Lifts a monadic value into the {@link WriterT} context with empty output.
   *
   * @param <F> The witness type of the outer monad.
   * @param <W> The type of the accumulated output.
   * @param <A> The type of the value.
   * @param monad The {@link Monad} instance for the outer monad. Must not be null.
   * @param monoid The {@link Monoid} instance for the output type. Must not be null.
   * @param fa The monadic value to lift. Must not be null.
   * @return A new {@link WriterT} wrapping the lifted value with empty output.
   * @throws NullPointerException if any argument is null.
   */
  public static <F extends WitnessArity<TypeArity.Unary>, W, A> WriterT<F, W, A> liftF(
      Monad<F> monad, Monoid<W> monoid, Kind<F, A> fa) {
    Validation.transformer().requireOuterMonad(monad, WRITER_T_CLASS, LIFT_F);
    Validation.function().require(monoid, "monoid", LIFT_F);
    Validation.kind().requireNonNull(fa, LIFT_F, "source Kind");
    return new WriterT<>(monad.map(a -> Pair.of(a, monoid.empty()), fa));
  }

  /**
   * Creates a {@link WriterT} with an explicit value and output.
   *
   * @param <F> The witness type of the outer monad.
   * @param <W> The type of the accumulated output.
   * @param <A> The type of the value.
   * @param monad The {@link Monad} instance for the outer monad. Must not be null.
   * @param value The value.
   * @param output The output.
   * @return A new {@link WriterT} with the given value and output.
   * @throws NullPointerException if {@code monad} is null.
   */
  public static <F extends WitnessArity<TypeArity.Unary>, W, A> WriterT<F, W, A> writer(
      Monad<F> monad, A value, W output) {
    Validation.transformer().requireOuterMonad(monad, WRITER_T_CLASS, CONSTRUCTION);
    return new WriterT<>(monad.of(Pair.of(value, output)));
  }
}
