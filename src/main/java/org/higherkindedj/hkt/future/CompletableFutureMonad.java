// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.future;

import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.unwrap;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.wrap;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Implements the {@link Monad} type class for {@link java.util.concurrent.CompletableFuture}, using
 * {@link CompletableFutureKind.Witness} as the higher-kinded type witness.
 *
 * <p>A {@link Monad} extends {@link org.higherkindedj.hkt.Applicative} (and thus {@link
 * org.higherkindedj.hkt.Functor}) by adding the {@link #flatMap(Function, Kind)} operation (also
 * known as {@code bind} or {@code >>=}). This allows sequencing of asynchronous operations where
 * each step depends on the result of the previous one and returns a new {@code CompletableFuture}.
 *
 * <p>This class provides:
 *
 * <ul>
 *   <li>{@link #of(Object)}: Lifts a pure value into a completed {@code CompletableFuture}.
 *       (Inherited from Applicative)
 *   <li>{@link #ap(Kind, Kind)}: Applies a future of a function to a future of a value. (Inherited
 *       from Applicative)
 *   <li>{@link #map(Function, Kind)}: Applies a pure function to the result of a future. (Inherited
 *       from Functor)
 *   <li>{@link #flatMap(Function, Kind)}: Chains an asynchronous computation that depends on the
 *       result of a preceding {@code CompletableFuture}.
 * </ul>
 *
 * <p>The {@link #flatMap(Function, Kind)} implementation uses {@link
 * CompletableFuture#thenCompose(Function)}, which is the idiomatic way to perform monadic binding
 * with {@code CompletableFuture}.
 *
 * @see Monad
 * @see CompletableFutureApplicative
 * @see CompletableFuture
 * @see CompletableFutureKind
 * @see CompletableFutureKind.Witness
 * @see CompletableFutureKindHelper
 */
public class CompletableFutureMonad extends CompletableFutureApplicative
    implements Monad<CompletableFutureKind.Witness> {

  /** Constructs a new {@code CompletableFutureMonad} instance. */
  public CompletableFutureMonad() {
    // Default constructor
  }

  /**
   * Sequentially composes two asynchronous computations, where the second computation (produced by
   * function {@code f}) depends on the result of the first computation ({@code ma}).
   *
   * <p>If the first {@code CompletableFuture} ({@code ma}) completes successfully with a value
   * {@code a}, the function {@code f} is applied to {@code a}. {@code f} must return a {@code
   * Kind<CompletableFutureKind.Witness, B>}, which represents another {@code CompletableFuture<B>}.
   * The result of this {@code flatMap} operation is this new {@code CompletableFuture<B>}.
   *
   * <p>If {@code ma} completes exceptionally, or if the application of {@code f} throws an
   * exception, or if {@code f} returns a {@code Kind} that unwraps to a {@code CompletableFuture}
   * that later completes exceptionally, then the resulting {@code CompletableFuture} will also
   * complete exceptionally.
   *
   * <p>This operation is analogous to {@code bind} or {@code >>=} in other monadic contexts and is
   * implemented using {@link CompletableFuture#thenCompose(Function)}.
   *
   * @param <A> The type of the result of the first computation {@code ma}.
   * @param <B> The type of the result of the second computation returned by function {@code f}.
   * @param f A non-null function that takes a value of type {@code A} (the result of {@code ma})
   *     and returns a {@code Kind<CompletableFutureKind.Witness, B>}, representing the next
   *     asynchronous computation. The value {@code a} passed to this function can be {@code null}
   *     if the preceding {@code CompletableFuture<A>} completed with {@code null}.
   * @param ma A non-null {@code Kind<CompletableFutureKind.Witness, A>} representing the first
   *     asynchronous computation {@code CompletableFuture<A>}.
   * @return A non-null {@code Kind<CompletableFutureKind.Witness, B>} representing a new {@code
   *     CompletableFuture<B>} that will complete with the result of the composed asynchronous
   *     operations.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ma} or the {@code Kind}
   *     returned by {@code f} cannot be unwrapped.
   * @throws NullPointerException if {@code f} is null, or if {@code f} returns a null {@code Kind}.
   */
  @Override
  public <A, B> @NonNull Kind<CompletableFutureKind.Witness, B> flatMap(
      @NonNull Function<@Nullable A, @NonNull Kind<CompletableFutureKind.Witness, B>> f,
      @NonNull Kind<CompletableFutureKind.Witness, A> ma) {
    CompletableFuture<A> futureA = unwrap(ma);
    CompletableFuture<B> futureB =
        futureA.thenCompose(
            a -> {
              Kind<CompletableFutureKind.Witness, B> kindB = f.apply(a);
              return unwrap(kindB);
            });
    return wrap(futureB);
  }
}
