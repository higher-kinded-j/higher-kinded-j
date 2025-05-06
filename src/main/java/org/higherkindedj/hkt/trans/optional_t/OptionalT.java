package org.higherkindedj.hkt.trans.optional_t;

import java.util.Objects;
import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.jspecify.annotations.NonNull;

/**
 * Represents the concrete implementation of the Optional Transformer Monad (OptionalT). It wraps a
 * monadic value of type {@code Kind<F, Optional<A>>}, where {@code F} is the outer monad and {@code
 * Optional<A>} is the inner optional value.
 *
 * <p>This class is a record, making it an immutable data holder for the wrapped value. To use
 * {@code OptionalT} as a {@code Kind} in higher-kinded type simulations, it should be
 * wrapped/unwrapped using {@link OptionalTKindHelper}.
 *
 * <p>OptionalT is similar to MaybeT but specifically targets Java's {@link java.util.Optional}.
 *
 * @param <F> The witness type of the outer monad (e.g., {@code IO<?>}, {@code ListKind<?>}).
 * @param <A> The type of the value potentially held by the inner {@link Optional}.
 * @param value The underlying monadic value {@code Kind<F, Optional<A>>}. Must not be null.
 * @see OptionalTKind
 * @see OptionalTKindHelper
 * @see OptionalTMonad
 */
public record OptionalT<F, A>(@NonNull Kind<F, Optional<A>> value) {

  /**
   * Canonical constructor for {@code OptionalT}.
   *
   * @param value The underlying monadic value {@code Kind<F, Optional<A>>}.
   * @throws NullPointerException if {@code value} is null.
   */
  public OptionalT {
    Objects.requireNonNull(value, "Wrapped value cannot be null for OptionalT");
  }

  /**
   * Creates an {@code OptionalT} from an existing {@code Kind<F, Optional<A>>}.
   *
   * @param value The monadic value to wrap. Must not be null.
   * @param <F> The witness type of the outer monad.
   * @param <A> The type of the value in the inner {@link Optional}.
   * @return A new {@code OptionalT} instance.
   * @throws NullPointerException if {@code value} is null.
   */
  public static <F, A> @NonNull OptionalT<F, A> fromKind(@NonNull Kind<F, Optional<A>> value) {
    return new OptionalT<>(value);
  }

  /**
   * Lifts a non-null value {@code a} into {@code OptionalT<F, A>}, resulting in {@code
   * F<Optional.of(a)>}.
   *
   * @param outerMonad The {@link Monad} instance for the outer type {@code F}. Must not be null.
   * @param a The value to wrap. Must not be null (enforced by {@link Optional#of(Object)}).
   * @param <F> The witness type of the outer monad.
   * @param <A> The type of the value to wrap.
   * @return A new {@code OptionalT} instance representing {@code outerMonad.of(Optional.of(a))}.
   * @throws NullPointerException if {@code outerMonad} or {@code a} is null.
   */
  public static <F, A extends @NonNull Object> @NonNull OptionalT<F, A> some(
      @NonNull Monad<F> outerMonad, @NonNull A a) {
    Objects.requireNonNull(outerMonad, "Outer Monad cannot be null for some");
    // Optional.of enforces non-null 'a'
    Kind<F, Optional<A>> lifted = outerMonad.of(Optional.of(a));
    return new OptionalT<>(lifted);
  }

  /**
   * Creates an {@code OptionalT<F, A>} representing the {@code Optional.empty()} state, resulting
   * in {@code F<Optional.empty()>}.
   *
   * @param outerMonad The {@link Monad} instance for the outer type {@code F}. Must not be null.
   * @param <F> The witness type of the outer monad.
   * @param <A> The type of the value in the inner {@link Optional} (will be absent).
   * @return A new {@code OptionalT} instance representing {@code outerMonad.of(Optional.empty())}.
   * @throws NullPointerException if {@code outerMonad} is null.
   */
  public static <F, A> @NonNull OptionalT<F, A> none(@NonNull Monad<F> outerMonad) {
    Objects.requireNonNull(outerMonad, "Outer Monad cannot be null for none");
    Kind<F, Optional<A>> lifted = outerMonad.of(Optional.empty());
    return new OptionalT<>(lifted);
  }

  /**
   * Lifts a plain {@link Optional<A>} into {@code OptionalT<F, A>}, resulting in {@code
   * F<Optional<A>>}.
   *
   * @param outerMonad The {@link Monad} instance for the outer type {@code F}. Must not be null.
   * @param optional The {@link Optional} instance to lift. Must not be null.
   * @param <F> The witness type of the outer monad.
   * @param <A> The type of the value in the {@link Optional}.
   * @return A new {@code OptionalT} instance representing {@code outerMonad.of(optional)}.
   * @throws NullPointerException if {@code outerMonad} or {@code optional} is null.
   */
  public static <F, A> @NonNull OptionalT<F, A> fromOptional(
      @NonNull Monad<F> outerMonad, @NonNull Optional<A> optional) {
    Objects.requireNonNull(outerMonad, "Outer Monad cannot be null for fromOptional");
    Objects.requireNonNull(optional, "Input Optional cannot be null for fromOptional");
    Kind<F, Optional<A>> lifted = outerMonad.of(optional);
    return new OptionalT<>(lifted);
  }

  /**
   * Lifts a monadic value {@code Kind<F, A>} into {@code OptionalT<F, A>}, resulting in {@code
   * F<Optional<A>>}. The value {@code A} inside {@code F} is mapped to {@code Optional<A>} using
   * {@link Optional#ofNullable(Object)}.
   *
   * @param outerMonad The {@link Monad} instance for the outer type {@code F}. Must not be null.
   * @param fa The monadic value {@code Kind<F, A>} to lift. Must not be null.
   * @param <F> The witness type of the outer monad.
   * @param <A> The type of the value in {@code fa}.
   * @return A new {@code OptionalT} instance representing {@code
   *     outerMonad.map(Optional::ofNullable, fa)}.
   * @throws NullPointerException if {@code outerMonad} or {@code fa} is null.
   */
  public static <F, A> @NonNull OptionalT<F, A> liftF(
      @NonNull Monad<F> outerMonad, @NonNull Kind<F, A> fa) {
    Objects.requireNonNull(outerMonad, "Outer Monad cannot be null for liftF");
    Objects.requireNonNull(fa, "Input Kind<F, A> cannot be null for liftF");
    Kind<F, Optional<A>> mapped = outerMonad.map(Optional::ofNullable, fa);
    return new OptionalT<>(mapped);
  }

  /**
   * Accessor for the underlying monadic value.
   *
   * @return The {@code @NonNull Kind<F, Optional<A>>} wrapped by this {@code OptionalT}.
   */
  public @NonNull Kind<F, Optional<A>> value() {
    return value;
  }
}
