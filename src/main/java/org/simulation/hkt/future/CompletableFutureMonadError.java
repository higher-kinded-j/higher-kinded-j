package org.simulation.hkt.future;

import org.simulation.hkt.Kind;
import org.simulation.hkt.MonadError;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

import static org.simulation.hkt.future.CompletableFutureKindHelper.*;

/**
 * MonadError implementation for CompletableFutureKind.
 * The error type E is Throwable.
 */
public class CompletableFutureMonadError extends CompletableFutureMonad implements MonadError<CompletableFutureKind<?>, Throwable> {

    @Override
    public <A> Kind<CompletableFutureKind<?>, A> raiseError(Throwable error) {
        // Lift an error using failedFuture
        return wrap(CompletableFuture.failedFuture(error));
    }


    @Override
    public <A> Kind<CompletableFutureKind<?>, A> handleErrorWith(Kind<CompletableFutureKind<?>, A> ma, Function<Throwable, Kind<CompletableFutureKind<?>, A>> handler) {
        CompletableFuture<A> futureA = unwrap(ma);

        // Check if the future is already successfully completed. If so, return original Kind.
        // Note: getNow(null) will return null if not complete or completed exceptionally.
        // isDone() && !isCompletedExceptionally() is another way to check.
        if (futureA.isDone() && !futureA.isCompletedExceptionally()) {
            return ma; // Return the original Kind instance as no error handling is needed
        }

        // If not successfully completed, apply the handler using exceptionallyCompose
        CompletableFuture<A> recoveredFuture = futureA.exceptionallyCompose(throwable -> {
            // Extract the real cause if wrapped in CompletionException
            Throwable cause = (throwable instanceof CompletionException && throwable.getCause() != null)
                    ? throwable.getCause() : throwable;
            Kind<CompletableFutureKind<?>, A> recoveryKind = handler.apply(cause);
            return unwrap(recoveryKind); // Unwrap the Kind returned by the handler
        });

        // Wrap the potentially new future returned by exceptionallyCompose
        return wrap(recoveredFuture);
    }
}
