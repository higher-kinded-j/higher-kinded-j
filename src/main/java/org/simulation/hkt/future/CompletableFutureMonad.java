package org.simulation.hkt.future;

import org.simulation.hkt.Kind;
import org.simulation.hkt.Monad;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.simulation.hkt.future.CompletableFutureKindHelper.*;

public class CompletableFutureMonad extends CompletableFutureApplicative implements Monad<CompletableFutureKind<?>> {

    @Override
    public <A, B> Kind<CompletableFutureKind<?>, B> flatMap(Function<A, Kind<CompletableFutureKind<?>, B>> f, Kind<CompletableFutureKind<?>, A> ma) {
        CompletableFuture<A> futureA = unwrap(ma);

        // Use thenCompose for monadic bind (A -> Future<B>)
        CompletableFuture<B> futureB = futureA.thenCompose(a -> {
            Kind<CompletableFutureKind<?>, B> kindB = f.apply(a);
            return unwrap(kindB); // Unwrap the Kind returned by f
        });

        return wrap(futureB);
    }
}