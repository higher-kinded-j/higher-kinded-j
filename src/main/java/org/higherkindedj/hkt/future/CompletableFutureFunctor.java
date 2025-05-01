package org.higherkindedj.hkt.future;

import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.*;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class CompletableFutureFunctor implements Functor<CompletableFutureKind<?>> {

  @Override
  public <A, B> @NonNull Kind<CompletableFutureKind<?>, B> map(
      @NonNull Function<A, @Nullable B> f,
      @NonNull Kind<CompletableFutureKind<?>, A> fa) { // Allow function result to be null
    CompletableFuture<A> futureA = unwrap(fa);
    // thenApply handles null result from f correctly (future completes with null)
    CompletableFuture<B> futureB = futureA.thenApply(f);
    return wrap(futureB);
  }
}
