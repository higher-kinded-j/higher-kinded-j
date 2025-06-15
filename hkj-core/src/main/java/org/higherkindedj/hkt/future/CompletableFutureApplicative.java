// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.future;

import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Implements the {@link Applicative} type class for {@link java.util.concurrent.CompletableFuture},
 * using {@link CompletableFutureKind.Witness} as the higher-kinded type witness.
 *
 * <p>This class provides the mechanisms to:
 *
 * <ul>
 *   <li>Lift pure values into a {@code CompletableFuture} context ({@link #of(Object)}).
 *   <li>Apply a function wrapped in a {@code CompletableFuture} to a value also wrapped in a {@code
 *       CompletableFuture} ({@link #ap(Kind, Kind)}).
 * </ul>
 *
 * It extends {@link CompletableFutureFunctor} to inherit the {@code map} operation.
 *
 * <p>Operations are performed asynchronously, leveraging the capabilities of {@link
 * CompletableFuture}. For instance, {@link #ap(Kind, Kind)} uses {@link
 * CompletableFuture#thenCombine(CompletionStage, BiFunction)} to wait for both the future holding
 * the function and the future holding the value before applying the function.
 *
 * @see Applicative
 * @see CompletableFutureFunctor
 * @see CompletableFutureKind
 * @see CompletableFutureKind.Witness
 * @see CompletableFutureKindHelper
 * @see java.util.concurrent.CompletableFuture
 */
public class CompletableFutureApplicative extends CompletableFutureFunctor
    implements Applicative<CompletableFutureKind.Witness> {

  /** Constructs a new {@code CompletableFutureApplicative} instance. */
  public CompletableFutureApplicative() {
    // Default constructor
  }

  /**
   * Lifts a pure value into the {@link CompletableFuture} applicative context.
   *
   * <p>The provided value is wrapped in an immediately completed {@link CompletableFuture}. If the
   * value is {@code null}, it results in a {@code CompletableFuture} completed with {@code null}.
   *
   * @param <A> The type of the value.
   * @param value The value to lift. Can be {@code null}.
   * @return A non-null {@code Kind<CompletableFutureKind.Witness, A>} representing an already
   *     completed {@link CompletableFuture} holding the given {@code value}. This corresponds to
   *     {@code CompletableFuture.completedFuture(value)}.
   */
  @Override
  public <A> @NonNull Kind<CompletableFutureKind.Witness, A> of(@Nullable A value) {
    return FUTURE.widen(CompletableFuture.completedFuture(value));
  }

  /**
   * Applies a function, wrapped in a {@link CompletableFuture}, to a value, also wrapped in a
   * {@link CompletableFuture}.
   *
   * <p>This operation waits for both the {@code CompletableFuture} containing the function ({@code
   * ff}) and the {@code CompletableFuture} containing the value ({@code fa}) to complete. Once both
   * are complete, the function is applied to the value. The result of this application is then
   * wrapped in a new {@code CompletableFuture}.
   *
   * <p>If either of the input {@code CompletableFuture}s completes exceptionally, the resulting
   * {@code CompletableFuture} will also complete exceptionally with that throwable.
   *
   * <p>The implementation uses {@link
   * CompletableFuture#thenCombine(java.util.concurrent.CompletionStage,
   * java.util.function.BiFunction)} to achieve this asynchronous coordination.
   *
   * @param <A> The input type of the function and the type of the value in {@code fa}.
   * @param <B> The output type of the function and the type of the value in the resulting {@code
   *     CompletableFuture}. The result of {@code func.apply(val)} can be {@code null} if type
   *     {@code B} is nullable.
   * @param ff A non-null {@code Kind<CompletableFutureKind.Witness, Function<A, B>>} representing
   *     the asynchronously available function.
   * @param fa A non-null {@code Kind<CompletableFutureKind.Witness, A>} representing the
   *     asynchronously available value.
   * @return A non-null {@code Kind<CompletableFutureKind.Witness, B>} representing a new {@link
   *     CompletableFuture} that will complete with the result of applying the function to the
   *     value, or complete exceptionally if any of the preceding stages fail.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ff} or {@code fa} cannot
   *     be unwrapped.
   */
  @Override
  public <A, B> @NonNull Kind<CompletableFutureKind.Witness, B> ap(
      @NonNull Kind<CompletableFutureKind.Witness, Function<A, B>> ff,
      @NonNull Kind<CompletableFutureKind.Witness, A> fa) {
    CompletableFuture<Function<A, B>> futureF = FUTURE.narrow(ff);
    CompletableFuture<A> futureA = FUTURE.narrow(fa);

    CompletableFuture<B> futureB = futureF.thenCombine(futureA, (func, val) -> func.apply(val));
    return FUTURE.widen(futureB);
  }
}
