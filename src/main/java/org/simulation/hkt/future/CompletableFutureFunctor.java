package org.simulation.hkt.future;

import org.simulation.hkt.Functor;
import org.simulation.hkt.Kind;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.simulation.hkt.future.CompletableFutureKindHelper.*;

public class CompletableFutureFunctor implements Functor<CompletableFutureKind<?>> {

    @Override
    public <A, B> Kind<CompletableFutureKind<?>, B> map(Function<A, B> f, Kind<CompletableFutureKind<?>, A> fa) {
        CompletableFuture<A> futureA = unwrap(fa);
        CompletableFuture<B> futureB = futureA.thenApply(f); // Use thenApply for map
        return wrap(futureB);
    }
}
