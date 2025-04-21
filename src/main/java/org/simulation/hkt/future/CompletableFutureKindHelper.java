package org.simulation.hkt.future;

import org.simulation.hkt.Kind;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class CompletableFutureKindHelper {

    /**
     * Unwraps a CompletableFutureKind back to the concrete CompletableFuture<A> type.
     * Handles null or invalid Kind types by returning a failed future.
     */
    public static <A> CompletableFuture<A> unwrap(Kind<CompletableFutureKind<?>, A> kind) {
        return switch (kind) {
            case CompletableFutureHolder<A> holder -> holder.future();
            case null -> CompletableFuture.<A>failedFuture(new NullPointerException("Cannot unwrap null Kind for CompletableFuture"));
            default -> CompletableFuture.<A>failedFuture(new IllegalArgumentException("Kind instance is not a CompletableFutureHolder: " + kind.getClass().getName()));
        };
    }

    /**
     * Wraps a concrete CompletableFuture<A> value into the CompletableFutureKind simulation type.
     */
    public static <A> CompletableFutureKind<A> wrap(CompletableFuture<A> future) {
        return new CompletableFutureHolder<>(future);
    }

    /**
     * Internal holder record for the HKT simulation of CompletableFuture.
     */
    record CompletableFutureHolder<A>(CompletableFuture<A> future) implements CompletableFutureKind<A> { }

    // --- Convenience methods ---

    /**
     * Gets the result of the CompletableFuture Kind, blocking if necessary.
     * Rethrows exceptions from the future, wrapped in RuntimeException if checked.
     * Primarily for testing or scenarios where blocking is acceptable.
     */
    public static <A> A join(Kind<CompletableFutureKind<?>, A> kind) {
        try {
            return unwrap(kind).join();
        } catch (CompletionException e) {
            // Rethrow cause if possible
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            if (cause instanceof Error err) throw err;
            throw e; // Wrap checked exceptions or other throwables
        }
    }
}