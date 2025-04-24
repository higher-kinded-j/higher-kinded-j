package org.simulation.hkt.future;

import org.jspecify.annotations.NonNull;
import org.simulation.hkt.Kind;
import org.simulation.hkt.Monad;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.simulation.hkt.future.CompletableFutureKindHelper.*;

public class CompletableFutureMonad extends CompletableFutureApplicative implements Monad<CompletableFutureKind<?>> {

    @Override
    public <A, B> @NonNull Kind<CompletableFutureKind<?>, B> flatMap(@NonNull Function<A, Kind<CompletableFutureKind<?>, B>> f, @NonNull Kind<CompletableFutureKind<?>, A> ma) {
        CompletableFuture<A> futureA = unwrap(ma);

        // Use thenCompose for monadic bind (A -> Future<B>)
        CompletableFuture<B> futureB = futureA.thenCompose(a -> { // `a` can be null if futureA completed with null
            Kind<CompletableFutureKind<?>, B> kindB = f.apply(a);
            return unwrap(kindB);
        });

        return wrap(futureB);
    }
}