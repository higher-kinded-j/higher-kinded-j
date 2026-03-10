// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import java.util.function.Function;
import org.jspecify.annotations.NullMarked;

/**
 * The MonadReader type class abstracts the capability of reading from a shared environment.
 *
 * <p>This is an MTL-style capability interface: rather than requiring a concrete transformer stack
 * like {@code ReaderT}, code can be written against {@code MonadReader} to declare that it needs
 * environment-reading capabilities without specifying how they are provided.
 *
 * <p>Laws:
 *
 * <ul>
 *   <li><b>Ask idempotent:</b> {@code flatMap(_ -> ask(), ask()) == ask()}
 *   <li><b>Local-ask coherence:</b> {@code local(f, ask()) == map(f, ask())}
 *   <li><b>Local composition:</b> {@code local(f, local(g, ma)) == local(g.compose(f), ma)}
 *   <li><b>Local identity:</b> {@code local(identity, ma) == ma}
 * </ul>
 *
 * @param <F> The witness type for the monad.
 * @param <R> The type of the environment.
 * @see Monad
 */
@NullMarked
public interface MonadReader<F extends WitnessArity<TypeArity.Unary>, R> extends Monad<F> {

  /**
   * Returns the current environment.
   *
   * @return A monadic value containing the environment.
   */
  Kind<F, R> ask();

  /**
   * Runs a computation in a modified environment. The function {@code f} transforms the environment
   * before it is made available to the computation {@code ma}.
   *
   * @param f The function to modify the environment. Must not be null.
   * @param ma The computation to run in the modified environment. Must not be null.
   * @param <A> The result type of the computation.
   * @return The result of running {@code ma} with the modified environment.
   */
  <A> Kind<F, A> local(Function<R, R> f, Kind<F, A> ma);

  /**
   * Extracts a value from the environment using the given function. This is a convenience method
   * equivalent to {@code map(f, ask())}.
   *
   * @param f The function to extract a value from the environment. Must not be null.
   * @param <A> The type of the extracted value.
   * @return A monadic value containing the extracted value.
   */
  default <A> Kind<F, A> reader(Function<R, A> f) {
    return map(f, ask());
  }

  /**
   * Extracts a value from the environment. This is an alias for {@link #reader(Function)}.
   *
   * @param f The function to extract a value from the environment. Must not be null.
   * @param <A> The type of the extracted value.
   * @return A monadic value containing the extracted value.
   */
  default <A> Kind<F, A> asks(Function<R, A> f) {
    return reader(f);
  }
}
