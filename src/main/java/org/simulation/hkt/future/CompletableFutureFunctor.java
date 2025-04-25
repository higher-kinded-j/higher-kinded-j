package org.simulation.hkt.future;

import static org.simulation.hkt.future.CompletableFutureKindHelper.*;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.simulation.hkt.Functor;
import org.simulation.hkt.Kind;

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
