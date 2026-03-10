// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import java.util.function.Function;
import org.jspecify.annotations.NullMarked;

/**
 * The MonadWriter type class abstracts the capability of accumulating output alongside computation.
 *
 * <p>This is an MTL-style capability interface: rather than requiring a concrete transformer stack
 * like {@code WriterT}, code can be written against {@code MonadWriter} to declare that it needs
 * output-accumulation capabilities without specifying how they are provided.
 *
 * <p>The output type {@code W} must have a {@link Monoid} instance for combining outputs.
 *
 * <p>Laws:
 *
 * <ul>
 *   <li><b>Tell-empty:</b> {@code tell(monoid.empty()) == of(Unit.INSTANCE)}
 *   <li><b>Tell-combine:</b> {@code flatMap(_ -> tell(b), tell(a)) == tell(monoid.combine(a, b))}
 *   <li><b>Listen-tell:</b> {@code listen(tell(w)) == map(_ -> Pair.of(Unit.INSTANCE, w), tell(w))}
 * </ul>
 *
 * @param <F> The witness type for the monad.
 * @param <W> The type of the output (must form a {@link Monoid}).
 * @see Monad
 * @see Monoid
 * @see Pair
 */
@NullMarked
public interface MonadWriter<F extends WitnessArity<TypeArity.Unary>, W> extends Monad<F> {

  /**
   * Appends the given value to the accumulated output.
   *
   * @param w The output value to append. Must not be null.
   * @return A monadic value containing {@link Unit#INSTANCE}.
   */
  Kind<F, Unit> tell(W w);

  /**
   * Runs a computation and returns its result paired with the accumulated output.
   *
   * @param ma The computation to listen to. Must not be null.
   * @param <A> The result type of the computation.
   * @return A monadic value containing a {@link Pair} of the result and the accumulated output.
   */
  <A> Kind<F, Pair<A, W>> listen(Kind<F, A> ma);

  /**
   * Runs a computation that returns a pair of a value and a function to transform the output.
   * Applies the function to the accumulated output.
   *
   * @param ma The computation returning a pair of (value, output-transformer). Must not be null.
   * @param <A> The result type of the computation.
   * @return A monadic value containing the result, with the output transformed.
   */
  <A> Kind<F, A> pass(Kind<F, Pair<A, Function<W, W>>> ma);

  /**
   * Like {@link #listen(Kind)}, but transforms the accumulated output using the given function.
   *
   * <p>This is equivalent to {@code map(t -> Pair.of(t.first(), f.apply(t.second())), listen(ma))}.
   *
   * @param f The function to transform the output. Must not be null.
   * @param ma The computation to listen to. Must not be null.
   * @param <A> The result type of the computation.
   * @param <B> The type of the transformed output.
   * @return A monadic value containing a {@link Pair} of the result and the transformed output.
   */
  default <A, B> Kind<F, Pair<A, B>> listens(Function<W, B> f, Kind<F, A> ma) {
    return map(t -> Pair.of(t.first(), f.apply(t.second())), listen(ma));
  }

  /**
   * Modifies the accumulated output of a computation using the given function.
   *
   * <p>This is equivalent to {@code pass(map(a -> Pair.of(a, f), ma))}.
   *
   * @param f The function to transform the output. Must not be null.
   * @param ma The computation whose output will be modified. Must not be null.
   * @param <A> The result type of the computation.
   * @return A monadic value containing the result, with the output transformed.
   */
  default <A> Kind<F, A> censor(Function<W, W> f, Kind<F, A> ma) {
    return pass(map(a -> Pair.of(a, f), ma));
  }
}
