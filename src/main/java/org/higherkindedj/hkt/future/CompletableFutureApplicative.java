package org.higherkindedj.hkt.future;

import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.unwrap;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.wrap;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Implements the {@link Applicative} type class for {@link CompletableFuture}, using {@link
 * CompletableFutureKind} as the higher-kinded type witness.
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
 * @see CompletableFutureKindHelper
 * @see java.util.concurrent.CompletableFuture
 */
public class CompletableFutureApplicative extends CompletableFutureFunctor
    implements Applicative<CompletableFutureKind<?>> {

  /**
   * Lifts a pure value into the {@link CompletableFuture} applicative context.
   *
   * <p>The provided value is wrapped in an immediately completed {@link CompletableFuture}. If the
   * value is {@code null}, it results in a {@code CompletableFuture} completed with {@code null}.
   *
   * @param value The value to lift. Can be {@code null}.
   * @param <A> The type of the value.
   * @return A non-null {@code Kind<CompletableFutureKind<?>, A>} representing an already completed
   *     {@link CompletableFuture} holding the given {@code value}. This corresponds to {@code
   *     CompletableFuture.completedFuture(value)}.
   */
  @Override
  public <A> @NonNull Kind<CompletableFutureKind<?>, A> of(@Nullable A value) {
    // CompletableFuture.completedFuture(value) correctly handles a null value.
    return wrap(CompletableFuture.completedFuture(value));
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
   * @param ff A non-null {@code Kind<CompletableFutureKind<?>, Function<A, B>>} representing the
   *     asynchronously available function.
   * @param fa A non-null {@code Kind<CompletableFutureKind<?>, A>} representing the asynchronously
   *     available value.
   * @param <A> The input type of the function and the type of the value in {@code fa}.
   * @param <B> The output type of the function and the type of the value in the resulting {@code
   *     CompletableFuture}. The result of {@code func.apply(val)} can be {@code null} if type
   *     {@code B} is nullable.
   * @return A non-null {@code Kind<CompletableFutureKind<?>, B>} representing a new {@link
   *     CompletableFuture} that will complete with the result of applying the function to the
   *     value, or complete exceptionally if any of the preceding stages fail.
   * @throws NullPointerException if {@code ff} or {@code fa} is null (as per {@link
   *     org.higherkindedj.hkt.future.CompletableFutureKindHelper#unwrap(Kind)}).
   */
  @Override
  public <A, B> @NonNull Kind<CompletableFutureKind<?>, B> ap(
      @NonNull Kind<CompletableFutureKind<?>, Function<A, B>> ff,
      @NonNull Kind<CompletableFutureKind<?>, A> fa) {
    // unwrap will handle null checks for ff and fa or throw KindUnwrapException
    CompletableFuture<Function<A, B>> futureF = unwrap(ff);
    CompletableFuture<A> futureA = unwrap(fa);

    // Use thenCombine to wait for both the future containing the function
    // and the future containing the value to complete.
    // Then, apply the function to the value.
    // The lambda (func, val) -> func.apply(val) will be executed asynchronously
    // when both futureF and futureA have completed successfully.
    // The result of func.apply(val) can be null if B is a nullable type.
    CompletableFuture<B> futureB =
        futureF.thenCombine(
            futureA,
            (func, val) -> {
              // It's good practice to ensure the function itself is not null if that's a
              // possibility,
              // though `ap` typically assumes ff contains a valid function if it completes
              // successfully.
              // If `func` could be null from `futureF` completing with `null`, a check would be
              // needed here.
              // However, `Function<A,B>` itself is usually non-null.
              return func.apply(val);
            });

    /*
     * Alternative implementation using thenCompose (more monadic style):
     * This style first waits for futureF to complete, then, using its result (the function),
     * it composes another asynchronous step that waits for futureA and applies the function.
     * CompletableFuture<B> futureB = futureF.thenCompose(func ->
     * futureA.thenApply(val -> func.apply(val))
     * );
     * Both approaches achieve a similar outcome for Applicative's `ap`.
     * `thenCombine` is often more direct for `ap` as it expresses waiting for two
     * independent futures and then combining their results.
     */

    return wrap(futureB);
  }
}
