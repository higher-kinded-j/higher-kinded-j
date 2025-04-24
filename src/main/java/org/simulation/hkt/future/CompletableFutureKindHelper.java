package org.simulation.hkt.future;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.simulation.hkt.Kind;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class CompletableFutureKindHelper {


    private CompletableFutureKindHelper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Unwraps a CompletableFutureKind back to the concrete CompletableFuture<A> type.
     * Handles null or invalid Kind types by returning a failed future.
     * @param kind The Kind instance (Nullable).
     * @return The underlying CompletableFuture or a failed one (NonNull).
     */
    @SuppressWarnings("unchecked") // For casting CompletableFutureHolder
    public static <A> @NonNull CompletableFuture<A> unwrap(@Nullable Kind<CompletableFutureKind<?>, A> kind) {
        return switch (kind) {
            case CompletableFutureHolder<?> holder -> {
                // Check if the held future itself is null
                CompletableFuture<?> heldFuture = holder.future();
                if (heldFuture == null) {
                    yield CompletableFuture.failedFuture(new NullPointerException("CompletableFutureHolder contained null Future"));
                } else {
                    yield (CompletableFuture<A>) heldFuture;
                }
            }
            case null -> CompletableFuture.failedFuture(new NullPointerException("Cannot unwrap null Kind for CompletableFuture"));
            default -> CompletableFuture.failedFuture(new IllegalArgumentException("Kind instance is not a CompletableFutureHolder: " + kind.getClass().getName()));
        };
    }


    /**
     * Wraps a concrete CompletableFuture<A> value into the CompletableFutureKind simulation type.
     * Requires a non-null CompletableFuture.
     * @param future The Future to wrap (NonNull).
     * @return The wrapped Kind (NonNull).
     */
    public static <A> @NonNull CompletableFutureKind<A> wrap(@NonNull CompletableFuture<A> future) {
        // Explicitly check for null to ensure consistent behavior
        Objects.requireNonNull(future, "Input CompletableFuture cannot be null for wrap");
        return new CompletableFutureHolder<>(future);
    }

    /**
     * Internal holder record for the HKT simulation of CompletableFuture.
     * Field assumed NonNull based on wrap check.
     */
    record CompletableFutureHolder<A>(@NonNull CompletableFuture<A> future) implements CompletableFutureKind<A> { }

    // --- Convenience methods ---

    /**
     * Gets the result of the CompletableFuture Kind, blocking if necessary.
     * Rethrows exceptions from the future, wrapped in RuntimeException if checked.
     * Primarily for testing or scenarios where blocking is acceptable.
     * @param kind The Kind instance (NonNull).
     * @return The result (Nullability depends on A).
     */
    public static <A> @Nullable A join(@NonNull Kind<CompletableFutureKind<?>, A> kind) {
        try {
            return unwrap(kind).join(); // join() can return null if future completes with null
        } catch (CompletionException e) {
            // Rethrow cause if possible
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            if (cause instanceof Error err) throw err;
            throw e;
        }

    }
}