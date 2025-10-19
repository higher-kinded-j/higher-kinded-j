// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.lazy;

import static org.higherkindedj.hkt.lazy.LazyKindHelper.*;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.util.validation.FunctionValidator;
import org.higherkindedj.hkt.util.validation.KindValidator;
import org.jspecify.annotations.Nullable;

/**
 * Implements the {@link Monad}, {@link Applicative}, and {@link Functor} interfaces for the {@link
 * Lazy} type, using {@link LazyKind.Witness} as its higher-kinded type witness.
 *
 * <p>This class provides the standard monadic operations (map, flatMap, of/pure, ap) for {@link
 * Lazy} computations, allowing them to be used in generic functional programming contexts that
 * operate over HKTs. This class is a stateless singleton, accessible via {@link #INSTANCE}.
 *
 * @see Lazy
 * @see LazyKind
 * @see LazyKind.Witness
 * @see LazyKindHelper
 */
public class LazyMonad
    implements Monad<LazyKind.Witness>, Applicative<LazyKind.Witness>, Functor<LazyKind.Witness> {

  private static final Class<LazyMonad> LAZY_MONAD_CLASS = LazyMonad.class;

  /** Singleton instance of {@code LazyMonad}. */
  public static final LazyMonad INSTANCE = new LazyMonad();

  /** Private constructor to enforce the singleton pattern. */
  private LazyMonad() {
    // Private constructor
  }

  /**
   * Applies a function to the result of a Lazy computation, creating a new Lazy computation that
   * will apply the function when forced.
   *
   * <p>This operation maintains lazy evaluation - the function is not applied until the resulting
   * Lazy is forced with {@code force()}.
   *
   * @param <A> The type of the result of the input Lazy computation.
   * @param <B> The type of the result after applying the function.
   * @param f The function to apply to the Lazy result. Must not be null.
   * @param fa The {@code Kind<LazyKind.Witness, A>} to transform. Must not be null.
   * @return A new {@code Kind<LazyKind.Witness, B>} containing the transformed computation. Never
   *     null.
   * @throws NullPointerException if {@code f} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fa} cannot be unwrapped.
   */
  @Override
  public <A, B> Kind<LazyKind.Witness, B> map(
      Function<? super A, ? extends B> f, Kind<LazyKind.Witness, A> fa) {

    FunctionValidator.requireMapper(f, "f", LAZY_MONAD_CLASS, MAP);
    KindValidator.requireNonNull(fa, LAZY_MONAD_CLASS, MAP);

    Lazy<A> lazyA = LAZY.narrow(fa);
    Lazy<B> lazyB = lazyA.map(f);
    return LAZY.widen(lazyB);
  }

  /**
   * Lifts a value into a {@code Kind<LazyKind.Witness, A>}. For {@link Lazy}, this creates an
   * already evaluated {@code Lazy} instance.
   *
   * @param <A> The type of the value.
   * @param value The value to lift. Can be {@code null}.
   * @return A {@code Kind<LazyKind.Witness, A>} containing the value. Never null.
   */
  @Override
  public <A> Kind<LazyKind.Witness, A> of(@Nullable A value) {
    // 'of'/'pure' creates a Lazy that is already evaluated
    return LAZY.widen(Lazy.now(value));
  }

  /**
   * Applies a function wrapped in a {@code Kind<LazyKind.Witness, Function<A, B>>} to a value
   * wrapped in a {@code Kind<LazyKind.Witness, A>}.
   *
   * <p>When the resulting Lazy is forced, it forces both the function Lazy and the argument Lazy,
   * then applies the function to the value.
   *
   * @param <A> The input type of the function.
   * @param <B> The output type of the function.
   * @param ff The {@code Kind<LazyKind.Witness, Function<A, B>>} containing the function. Must not
   *     be null.
   * @param fa The {@code Kind<LazyKind.Witness, A>} containing the value. Must not be null.
   * @return A new {@code Kind<LazyKind.Witness, B>} containing the result. Never null.
   * @throws NullPointerException if {@code ff} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ff} or {@code fa} cannot
   *     be unwrapped.
   */
  @Override
  public <A, B> Kind<LazyKind.Witness, B> ap(
      Kind<LazyKind.Witness, ? extends Function<A, B>> ff, Kind<LazyKind.Witness, A> fa) {

    KindValidator.requireNonNull(ff, LAZY_MONAD_CLASS, AP, "function");
    KindValidator.requireNonNull(fa, LAZY_MONAD_CLASS, AP, "argument");

    Lazy<? extends Function<A, B>> lazyF = LAZY.narrow(ff);
    Lazy<A> lazyA = LAZY.narrow(fa);

    // Defer the application: force F, force A, then apply
    Lazy<B> lazyB = Lazy.defer(() -> lazyF.force().apply(lazyA.force()));
    return LAZY.widen(lazyB);
  }

  /**
   * Sequentially composes two Lazy computations, where the second computation (produced by function
   * {@code f}) depends on the result of the first computation ({@code ma}).
   *
   * <p>When the resulting Lazy is forced, it forces the first Lazy to get a value, applies the
   * function {@code f} to get a new Lazy, then forces that new Lazy to get the final result.
   *
   * @param <A> The type of the result of the first computation {@code ma}.
   * @param <B> The type of the result of the second computation returned by function {@code f}.
   * @param f A function that takes the result of the first computation and returns a new {@code
   *     Kind<LazyKind.Witness, B>}. Must not be null.
   * @param ma The first Lazy computation as a {@code Kind<LazyKind.Witness, A>}. Must not be null.
   * @return A new {@code Kind<LazyKind.Witness, B>} representing the composed computation. Never
   *     null.
   * @throws NullPointerException if {@code f} or {@code ma} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ma} or the {@code Kind}
   *     returned by {@code f} cannot be unwrapped.
   */
  @Override
  public <A, B> Kind<LazyKind.Witness, B> flatMap(
      Function<? super A, ? extends Kind<LazyKind.Witness, B>> f, Kind<LazyKind.Witness, A> ma) {

    FunctionValidator.requireFlatMapper(f, "f", LAZY_MONAD_CLASS, FLAT_MAP);
    KindValidator.requireNonNull(ma, LAZY_MONAD_CLASS, FLAT_MAP);

    Lazy<A> lazyA = LAZY.narrow(ma);
    // Adapt the function for Lazy's flatMap
    Lazy<B> lazyB =
        lazyA.flatMap(
            a -> {
              Kind<LazyKind.Witness, B> kindB = f.apply(a);
              FunctionValidator.requireNonNullResult(
                  kindB, "f", LAZY_MONAD_CLASS, FLAT_MAP, Kind.class);
              return LAZY.narrow(kindB);
            });
    return LAZY.widen(lazyB);
  }
}
