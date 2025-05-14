package org.higherkindedj.hkt.trans.maybe_t;

import java.util.Objects;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.jspecify.annotations.NonNull;

/**
 * Represents the concrete implementation of the Maybe Transformer Monad (MaybeT). It wraps a
 * monadic value of type {@code Kind<F, Maybe<A>>}, where {@code F} is the outer monad and {@code
 * Maybe<A>} is the inner optional value.
 *
 * <p>This class is a record, making it an immutable data holder for the wrapped value. It
 * implements {@link MaybeTKind} to participate in higher-kinded type simulations, allowing it to be
 * treated as {@code Kind<MaybeTKind.Witness<F>, A>}.
 *
 * @param <F> The witness type of the outer monad (e.g., {@code OptionalKind.Witness}).
 * @param <A> The type of the value potentially held by the inner {@link Maybe}.
 * @param value The underlying monadic value {@code Kind<F, Maybe<A>>}. Must not be null.
 * @see MaybeTKind
 * @see MaybeTMonad
 * @see MaybeTKindHelper
 */
public record MaybeT<F, A>(@NonNull Kind<F, Maybe<A>> value) implements MaybeTKind<F, A> {

  /**
   * Canonical constructor for {@code MaybeT}.
   *
   * @param value The underlying monadic value {@code Kind<F, Maybe<A>>}.
   * @throws NullPointerException if {@code value} is null.
   */
  public MaybeT { // Canonical constructor
    Objects.requireNonNull(value, "Wrapped value cannot be null for MaybeT");
  }

  /**
   * Creates a {@code MaybeT} from an existing {@code Kind<F, Maybe<A>>}.
   *
   * @param value The monadic value to wrap. Must not be null.
   * @param <F> The witness type of the outer monad.
   * @param <A> The type of the value in the inner {@link Maybe}.
   * @return A new {@code MaybeT} instance.
   * @throws NullPointerException if {@code value} is null.
   */
  public static <F, A> @NonNull MaybeT<F, A> fromKind(@NonNull Kind<F, Maybe<A>> value) {
    return new MaybeT<>(value);
  }

  /**
   * Lifts a non-null value {@code a} into {@code MaybeT<F, A>}, resulting in {@code F<Just(a)>}.
   *
   * @param outerMonad The {@link Monad} instance for the outer type {@code F}. Must not be null.
   * @param a The value to wrap. Must not be null (enforced by {@link Maybe#just(Object)}).
   * @param <F> The witness type of the outer monad.
   * @param <A> The type of the value to wrap.
   * @return A new {@code MaybeT} instance representing {@code outerMonad.of(Maybe.just(a))}.
   * @throws NullPointerException if {@code outerMonad} or {@code a} is null.
   */
  public static <F, A extends @NonNull Object> @NonNull MaybeT<F, A> just(
      @NonNull Monad<F> outerMonad, @NonNull A a) {
    Objects.requireNonNull(outerMonad, "Outer Monad cannot be null for just");
    // Maybe.just itself will throw if 'a' is null.
    Kind<F, Maybe<A>> lifted = outerMonad.of(Maybe.just(a));
    return new MaybeT<>(lifted);
  }

  /**
   * Creates a {@code MaybeT<F, A>} representing the {@code Nothing} state, resulting in {@code
   * F<Nothing>}.
   *
   * @param outerMonad The {@link Monad} instance for the outer type {@code F}. Must not be null.
   * @param <F> The witness type of the outer monad.
   * @param <A> The type of the value in the inner {@link Maybe} (will be absent).
   * @return A new {@code MaybeT} instance representing {@code outerMonad.of(Maybe.nothing())}.
   * @throws NullPointerException if {@code outerMonad} is null.
   */
  public static <F, A> @NonNull MaybeT<F, A> nothing(@NonNull Monad<F> outerMonad) {
    Objects.requireNonNull(outerMonad, "Outer Monad cannot be null for nothing");
    Kind<F, Maybe<A>> lifted = outerMonad.of(Maybe.nothing());
    return new MaybeT<>(lifted);
  }

  /**
   * Lifts a plain {@link Maybe<A>} into {@code MaybeT<F, A>}, resulting in {@code F<Maybe<A>>}.
   *
   * @param outerMonad The {@link Monad} instance for the outer type {@code F}. Must not be null.
   * @param maybe The {@link Maybe} instance to lift. Must not be null.
   * @param <F> The witness type of the outer monad.
   * @param <A> The type of the value in the {@link Maybe}.
   * @return A new {@code MaybeT} instance representing {@code outerMonad.of(maybe)}.
   * @throws NullPointerException if {@code outerMonad} or {@code maybe} is null.
   */
  public static <F, A> @NonNull MaybeT<F, A> fromMaybe(
      @NonNull Monad<F> outerMonad, @NonNull Maybe<A> maybe) {
    Objects.requireNonNull(outerMonad, "Outer Monad cannot be null for fromMaybe");
    Objects.requireNonNull(maybe, "Input Maybe cannot be null for fromMaybe");
    Kind<F, Maybe<A>> lifted = outerMonad.of(maybe);
    return new MaybeT<>(lifted);
  }

  /**
   * Lifts a monadic value {@code Kind<F, A>} into {@code MaybeT<F, A>}, resulting in {@code
   * F<Maybe<A>>}. The value {@code A} inside {@code F} is mapped to {@code Maybe<A>} using {@link
   * Maybe#fromNullable(Object)}.
   *
   * @param outerMonad The {@link Monad} instance for the outer type {@code F}. Must not be null.
   * @param fa The monadic value {@code Kind<F, A>} to lift. Must not be null.
   * @param <F> The witness type of the outer monad.
   * @param <A> The type of the value in {@code fa}.
   * @return A new {@code MaybeT} instance representing {@code outerMonad.map(Maybe::fromNullable,
   *     fa)}.
   * @throws NullPointerException if {@code outerMonad} or {@code fa} is null.
   */
  public static <F, A> @NonNull MaybeT<F, A> liftF(
      @NonNull Monad<F> outerMonad, @NonNull Kind<F, A> fa) {
    Objects.requireNonNull(outerMonad, "Outer Monad cannot be null for liftF");
    Objects.requireNonNull(fa, "Input Kind<F, A> cannot be null for liftF");
    // Use Maybe.fromNullable for safety when mapping the value inside F
    Kind<F, Maybe<A>> mapped = outerMonad.map(Maybe::fromNullable, fa);
    return new MaybeT<>(mapped);
  }

  /**
   * Accessor for the underlying monadic value.
   *
   * @return The {@code @NonNull Kind<F, Maybe<A>>} wrapped by this {@code MaybeT}.
   */
  @Override
  public @NonNull Kind<F, Maybe<A>> value() {
    return value;
  }
}
