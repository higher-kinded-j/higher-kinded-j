// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.lazy;

import static org.higherkindedj.hkt.lazy.LazyKindHelper.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Implements the {@link Monad}, {@link Applicative}, and {@link Functor} interfaces for the {@link
 * Lazy} type, using {@link LazyKind.Witness} as its higher-kinded type witness.
 *
 * <p>This class provides the standard monadic operations (map, flatMap, of/pure, ap) for {@link
 * Lazy} computations, allowing them to be used in generic functional programming contexts that
 * operate over HKTs.
 *
 * @see Lazy
 * @see LazyKind
 * @see LazyKind.Witness
 * @see LazyKindHelper
 */
public class LazyMonad
    implements Monad<LazyKind.Witness>, Applicative<LazyKind.Witness>, Functor<LazyKind.Witness> {

  /** Singleton instance of {@code LazyMonad}. */
  public static final LazyMonad INSTANCE = new LazyMonad();

  /** Private constructor to enforce the singleton pattern. */
  private LazyMonad() {
    // Private constructor
  }

  /**
   * Maps a function over a {@code Kind<LazyKind.Witness, A>}.
   *
   * @param <A> The input type.
   * @param <B> The output type.
   * @param f The function to map.
   * @param fa The {@code Kind<LazyKind.Witness, A>} to map over.
   * @return A new {@code Kind<LazyKind.Witness, B>} containing the result of applying the function.
   */
  @Override
  public <A, B> @NonNull Kind<LazyKind.Witness, B> map(
      @NonNull Function<A, B> f, @NonNull Kind<LazyKind.Witness, A> fa) {
    Lazy<A> lazyA = LAZY.narrow(fa);
    Lazy<B> lazyB = lazyA.map(f); // Use Lazy's map
    return LAZY.widen(lazyB);
  }

  /**
   * Lifts a value into a {@code Kind<LazyKind.Witness, A>}. For {@link Lazy}, this creates an
   * already evaluated {@code Lazy} instance.
   *
   * @param <A> The type of the value.
   * @param value The value to lift.
   * @return A {@code Kind<LazyKind.Witness, A>} containing the value.
   */
  @Override
  public <A> @NonNull Kind<LazyKind.Witness, A> of(@Nullable A value) {
    // 'of'/'pure' creates a Lazy that is already evaluated
    return LAZY.widen(Lazy.now(value));
  }

  /**
   * Applies a function wrapped in a {@code Kind<LazyKind.Witness, Function<A, B>>} to a value
   * wrapped in a {@code Kind<LazyKind.Witness, A>}.
   *
   * @param <A> The input type of the function.
   * @param <B> The output type of the function.
   * @param ff The {@code Kind<LazyKind.Witness, Function<A, B>>} containing the function.
   * @param fa The {@code Kind<LazyKind.Witness, A>} containing the value.
   * @return A new {@code Kind<LazyKind.Witness, B>} containing the result.
   */
  @Override
  public <A, B> @NonNull Kind<LazyKind.Witness, B> ap(
      @NonNull Kind<LazyKind.Witness, Function<A, B>> ff, @NonNull Kind<LazyKind.Witness, A> fa) {
    Lazy<Function<A, B>> lazyF = LAZY.narrow(ff);
    Lazy<A> lazyA = LAZY.narrow(fa);

    // Defer the application: force F, force A, then apply
    Lazy<B> lazyB = Lazy.defer(() -> lazyF.force().apply(lazyA.force()));
    return LAZY.widen(lazyB);
  }

  /**
   * Sequentially composes two actions, passing the result of the first into a function that
   * produces the second.
   *
   * @param <A> The input type of the first action.
   * @param <B> The output type of the second action.
   * @param f The function that takes the result of the first action and returns a {@code
   *     Kind<LazyKind.Witness, B>}.
   * @param ma The first action as a {@code Kind<LazyKind.Witness, A>}.
   * @return A new {@code Kind<LazyKind.Witness, B>} representing the composed action.
   */
  @Override
  public <A, B> @NonNull Kind<LazyKind.Witness, B> flatMap(
      @NonNull Function<A, Kind<LazyKind.Witness, B>> f, @NonNull Kind<LazyKind.Witness, A> ma) {
    Lazy<A> lazyA = LAZY.narrow(ma);

    // Adapt the function for Lazy's flatMap
    Lazy<B> lazyB = lazyA.flatMap(a -> LAZY.narrow(f.apply(a)));
    return LAZY.widen(lazyB);
  }
}
