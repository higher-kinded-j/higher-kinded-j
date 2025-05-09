package org.higherkindedj.hkt.future;

import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.unwrap;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.wrap;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Implements the {@link Functor} type class for {@link java.util.concurrent.CompletableFuture},
 * using {@link CompletableFutureKind} as the higher-kinded type witness.
 *
 * <p>A {@link Functor} provides the ability to apply a function to a value inside a context (in
 * this case, a {@code CompletableFuture}) without needing to explicitly extract the value. The
 * {@link #map(Function, Kind)} operation transforms a {@code CompletableFuture<A>} into a {@code
 * CompletableFuture<B>} by applying a function {@code A -> B} to the completed result of the first
 * future.
 *
 * <p>This implementation leverages {@link CompletableFuture#thenApply(Function)}, which handles the
 * asynchronous application of the function when the future completes.
 *
 * @see Functor
 * @see CompletableFuture
 * @see CompletableFutureKind
 * @see CompletableFutureKindHelper
 */
public class CompletableFutureFunctor implements Functor<CompletableFutureKind<?>> {

  /**
   * Applies a function {@code f} to the value contained within a {@link CompletableFuture} context.
   *
   * <p>If the input {@code CompletableFuture} ({@code fa}) completes successfully with a value
   * {@code a}, the function {@code f} is applied to {@code a}, and the result {@code f(a)} is
   * wrapped in a new {@code CompletableFuture}. If {@code fa} completes exceptionally, the
   * resulting {@code CompletableFuture} also completes exceptionally with the same throwable.
   *
   * <p>The function {@code f} can return {@code null}. If it does, the resulting {@code
   * CompletableFuture} will complete successfully with a {@code null} value.
   *
   * @param f The non-null function to apply to the value inside the {@code CompletableFuture}. This
   *     function takes a value of type {@code A} and can return a {@code @Nullable} value of type
   *     {@code B}.
   * @param fa A non-null {@code Kind<CompletableFutureKind<?>, A>} representing the {@code
   *     CompletableFuture<A>} whose successfully completed value will be transformed.
   * @param <A> The type of the value in the input {@code CompletableFuture}.
   * @param <B> The type of the value in the output {@code CompletableFuture} after applying the
   *     function.
   * @return A non-null {@code Kind<CompletableFutureKind<?>, B>} representing a new {@code
   *     CompletableFuture<B>} that will complete with the transformed value, or complete
   *     exceptionally if {@code fa} or the application of {@code f} fails.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fa} cannot be unwrapped.
   * @throws NullPointerException if {@code f} is null (though annotated {@code @NonNull}).
   */
  @Override
  public <A, B> @NonNull Kind<CompletableFutureKind<?>, B> map(
      @NonNull Function<A, @Nullable B> f, // Function A -> B, where B can be null
      @NonNull Kind<CompletableFutureKind<?>, A> fa) {
    // unwrap will handle null check for fa or throw KindUnwrapException
    CompletableFuture<A> futureA = unwrap(fa);

    // CompletableFuture.thenApply(Function) takes a Function<? super T, ? extends U>.
    // If 'f' returns null, 'futureB' will complete with null, which is acceptable for
    // CompletableFuture<B>.
    CompletableFuture<B> futureB = futureA.thenApply(f);

    return wrap(futureB);
  }
}
