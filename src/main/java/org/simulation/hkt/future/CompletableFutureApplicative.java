package org.simulation.hkt.future;

import org.simulation.hkt.Applicative;
import org.simulation.hkt.Kind;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.simulation.hkt.future.CompletableFutureKindHelper.*;

public class CompletableFutureApplicative extends CompletableFutureFunctor implements Applicative<CompletableFutureKind<?>> {

    @Override
    public <A> Kind<CompletableFutureKind<?>, A> of(A value) {
        // Lift using completedFuture
        return wrap(CompletableFuture.completedFuture(value));
    }

    @Override
    public <A, B> Kind<CompletableFutureKind<?>, B> ap(Kind<CompletableFutureKind<?>, Function<A, B>> ff, Kind<CompletableFutureKind<?>, A> fa) {
        CompletableFuture<Function<A, B>> futureF = unwrap(ff);
        CompletableFuture<A> futureA = unwrap(fa);

        // Use thenCombine to wait for both the function and the value, then apply
        CompletableFuture<B> futureB = futureF.thenCombine(futureA, (func, val) -> func.apply(val));

        /* Alternative using thenCompose/thenApply (more monadic style):
        CompletableFuture<B> futureB = futureF.thenCompose(func ->
            futureA.thenApply(val -> func.apply(val))
        );
        */

        return wrap(futureB);
    }

    // Note: Default mapN implementations from Applicative interface will work here.
}
