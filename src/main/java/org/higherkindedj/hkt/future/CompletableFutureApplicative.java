package org.higherkindedj.hkt.future;

import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.*;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class CompletableFutureApplicative extends CompletableFutureFunctor
    implements Applicative<CompletableFutureKind<?>> {

  @Override
  public <A> @NonNull Kind<CompletableFutureKind<?>, A> of(@Nullable A value) { // Value can be null
    // Lift using completedFuture (handles null value)
    return wrap(CompletableFuture.completedFuture(value));
  }

  @Override
  public <A, B> @NonNull Kind<CompletableFutureKind<?>, B> ap(
      @NonNull Kind<CompletableFutureKind<?>, Function<A, B>> ff,
      @NonNull Kind<CompletableFutureKind<?>, A> fa) {
    CompletableFuture<Function<A, B>> futureF = unwrap(ff);
    CompletableFuture<A> futureA = unwrap(fa);

    // Use thenCombine to wait for both the function and the value, then apply
    // func.apply(val) result can be null if B is Nullable
    CompletableFuture<B> futureB = futureF.thenCombine(futureA, (func, val) -> func.apply(val));

    /* Alternative using thenCompose/thenApply (more monadic style):
    CompletableFuture<B> futureB = futureF.thenCompose(func ->
        futureA.thenApply(val -> func.apply(val))
    );
    */

    return wrap(futureB);
  }
}
