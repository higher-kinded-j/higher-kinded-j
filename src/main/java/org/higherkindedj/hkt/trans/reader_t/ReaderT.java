package org.higherkindedj.hkt.trans.reader_t;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.jspecify.annotations.NonNull;


/**
 * Represents the Reader Transformer Monad (ReaderT).
 * It wraps a computation that depends on an environment R to produce a monadic value F<A>.
 * Implemented as an immutable record holding the function R -> Kind<F, A>.
 *
 * @param <F> The witness type of the outer monad (e.g., OptionalKind<?>).
 * @param <R> The type of the environment (read-only context).
 * @param <A> The type of the value produced within the outer monad F.
 * @param run The core function R -> Kind<F, A>. (NonNull - ADDED @NonNull)
 */
public record ReaderT<F, R, A>(
        // --- Add @NonNull annotation here ---
        @NonNull Function<R, Kind<F, A>> run
) implements ReaderTKind<F, R, A> {

    // Constructor now implicitly uses Objects.requireNonNull on 'run' due to @NonNull
    // The explicit check is slightly redundant but harmless.
    public ReaderT {
        Objects.requireNonNull(run, "run function cannot be null for ReaderT");
    }

    // --- Static Factory Methods (remain the same) ---

    public static <F, R, A> @NonNull ReaderT<F, R, A> of(@NonNull Function<R, Kind<F, A>> runFunction) {
        return new ReaderT<>(runFunction);
    }

    public static <F, R, A> @NonNull ReaderT<F, R, A> lift(@NonNull Monad<F> outerMonad, @NonNull Kind<F, A> fa) {
        Objects.requireNonNull(outerMonad, "Outer Monad cannot be null for lift");
        Objects.requireNonNull(fa, "Input Kind<F, A> cannot be null for lift");
        return new ReaderT<>(r -> fa);
    }

    public static <F, R, A> @NonNull ReaderT<F, R, A> reader(@NonNull Monad<F> outerMonad, @NonNull Function<R, A> f) {
        Objects.requireNonNull(outerMonad, "Outer Monad cannot be null for reader");
        Objects.requireNonNull(f, "Function cannot be null for reader");
        return new ReaderT<>(r -> outerMonad.of(f.apply(r)));
    }

    public static <F, R> @NonNull ReaderT<F, R, R> ask(@NonNull Monad<F> outerMonad) {
        Objects.requireNonNull(outerMonad, "Outer Monad cannot be null for ask");
        return new ReaderT<>(r -> outerMonad.of(r));
    }

    @Override
    public @NonNull Function<R, Kind<F, A>> run() {
        return run;
    }
}