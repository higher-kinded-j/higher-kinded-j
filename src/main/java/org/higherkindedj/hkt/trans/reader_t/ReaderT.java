package org.higherkindedj.hkt.trans.reader_t;

import static java.util.Objects.requireNonNull;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.jspecify.annotations.NonNull;

/**
 * Represents the Reader Transformer Monad, {@code ReaderT<F, R, A>}. It wraps a computation that
 * depends on a read-only environment of type {@code R} to produce a monadic value {@code Kind<F,
 * A>}, where {@code F} is the witness type of an outer monad.
 *
 * <p>This class is implemented as an immutable record that holds the core function {@code R ->
 * Kind<F, A>}. It allows composing operations that require reading from an environment with other
 * monadic effects.
 *
 * <p>For higher-kinded type emulation, {@code ReaderTKind<F, R, A>} (which this class implements)
 * allows {@code ReaderT} to be treated as {@code Kind<ReaderTKind<F, R, ?>, A>}. The type
 * constructor {@code ReaderTKind<F, R, ?>} serves as the witness for a ReaderT with a fixed outer
 * monad witness {@code F} and a fixed environment type {@code R}.
 *
 * @param <F> The witness type of the outer monad (e.g., {@code OptionalKind.Witness}, {@code
 *     IOKind.Witness}). This represents the type constructor of the underlying monad.
 * @param <R> The type of the read-only environment.
 * @param <A> The type of the value produced within the outer monad {@code F}.
 * @param run The core function of this {@code ReaderT}, which takes an environment of type {@code
 *     R} and returns a monadic value {@code Kind<F, A>}. Must not be null.
 * @see ReaderTKind
 * @see ReaderTMonad
 * @see ReaderTKindHelper
 * @see Monad
 * @see Kind
 */
public record ReaderT<F, R, A>(@NonNull Function<R, Kind<F, A>> run)
    implements ReaderTKind<F, R, A> {

  /**
   * Constructs a new {@link ReaderT}. The {@code run} function is the core of the {@code ReaderT},
   * defining how to produce a monadic value {@code Kind<F, A>} from an environment {@code R}.
   *
   * @param run The function {@code R -> Kind<F, A>}. Must not be null.
   * @throws NullPointerException if {@code run} is null.
   */
  public ReaderT { // Canonical constructor
    requireNonNull(run, "run function cannot be null for ReaderT");
  }

  /**
   * Factory method to create a {@link ReaderT} instance from a given run function.
   *
   * @param <F> The witness type of the outer monad.
   * @param <R> The type of the environment.
   * @param <A> The type of the value.
   * @param runFunction The function {@code R -> Kind<F, A>} that defines the {@code ReaderT}. Must
   *     not be null.
   * @return A new {@link ReaderT} instance. Never null.
   * @throws NullPointerException if {@code runFunction} is null.
   */
  public static <F, R, A> @NonNull ReaderT<F, R, A> of(
      @NonNull Function<R, Kind<F, A>> runFunction) {
    return new ReaderT<>(runFunction);
  }

  /**
   * Lifts a monadic value {@code Kind<F, A>} into the {@link ReaderT} context. The resulting {@code
   * ReaderT} will produce the given monadic value {@code fa} regardless of the environment {@code
   * R}.
   *
   * @param <F> The witness type of the outer monad.
   * @param <R> The type of the environment (it will be ignored).
   * @param <A> The type of the value within the outer monad.
   * @param outerMonad An instance of {@link Monad} for the outer type {@code F}, used for its
   *     {@code of} method if direct lifting of {@code fa} is required, though in this specific
   *     lift, it's more about context. (Note: current impl. doesn't use outerMonad.of) Must not be
   *     null.
   * @param fa The monadic value {@code Kind<F, A>} to lift. Must not be null.
   * @return A new {@link ReaderT} that wraps {@code fa}. Never null.
   * @throws NullPointerException if {@code outerMonad} or {@code fa} is null.
   */
  public static <F, R, A> @NonNull ReaderT<F, R, A> lift(
      @NonNull Monad<F> outerMonad, @NonNull Kind<F, A> fa) {
    requireNonNull(outerMonad, "Outer Monad cannot be null for lift");
    requireNonNull(fa, "Input Kind<F, A> cannot be null for lift");
    return new ReaderT<>(r -> fa);
  }

  /**
   * Creates a {@link ReaderT} from a function {@code R -> A}, lifting the result {@code A} into the
   * outer monad {@code F} using the provided {@code outerMonad}'s {@code of} method.
   *
   * @param <F> The witness type of the outer monad.
   * @param <R> The type of the environment.
   * @param <A> The type of the value produced by the function {@code f} and lifted into {@code F}.
   * @param outerMonad The {@link Monad} instance for the outer type {@code F}. Must not be null.
   * @param f The function {@code R -> A} which computes a value from the environment. Must not be
   *     null.
   * @return A new {@link ReaderT} instance. Never null.
   * @throws NullPointerException if {@code outerMonad} or {@code f} is null.
   */
  public static <F, R, A> @NonNull ReaderT<F, R, A> reader(
      @NonNull Monad<F> outerMonad, @NonNull Function<R, A> f) {
    requireNonNull(outerMonad, "Outer Monad cannot be null for reader");
    requireNonNull(f, "Function cannot be null for reader");
    return new ReaderT<>(r -> outerMonad.of(f.apply(r)));
  }

  /**
   * Creates a {@link ReaderT} that provides the environment {@code R} itself, lifted into the outer
   * monad {@code F}. This is the "ask" operation for {@code ReaderT}.
   *
   * @param <F> The witness type of the outer monad.
   * @param <R> The type of the environment, which will also be the value type within {@code F}.
   * @param outerMonad The {@link Monad} instance for the outer type {@code F}. Must not be null.
   * @return A new {@link ReaderT} that, when run, yields {@code outerMonad.of(r)}. Never null.
   * @throws NullPointerException if {@code outerMonad} is null.
   */
  public static <F, R> @NonNull ReaderT<F, R, R> ask(@NonNull Monad<F> outerMonad) {
    requireNonNull(outerMonad, "Outer Monad cannot be null for ask");
    return new ReaderT<>(r -> outerMonad.of(r));
  }

  /**
   * Accesses the underlying function {@code R -> Kind<F, A>} of this {@link ReaderT}. This method
   * is part of the {@link ReaderTKind} interface implementation.
   *
   * @return The function {@code R -> Kind<F, A>}. Never null.
   */
  @Override
  public @NonNull Function<R, Kind<F, A>> run() {
    return run; // Returns the record component
  }
}
