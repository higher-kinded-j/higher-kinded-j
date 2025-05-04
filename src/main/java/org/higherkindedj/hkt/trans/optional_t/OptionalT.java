package org.higherkindedj.hkt.trans.optional_t;

import java.util.Objects;
import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.jspecify.annotations.NonNull;

/**
 * Represents the Optional Transformer Monad (OptionalT), implemented as an immutable record.
 *
 * <p>OptionalT simplifies working with nested monadic structures of the form {@code
 * F<Optional<A>>}, where {@code F} is an outer monad (like {@code CompletableFutureKind} or {@code
 * IOKind}) and {@code Optional} is used for inner optionality handling.
 *
 * <p>This record wraps a single value of type {@link Kind}{@code <F, Optional<A>>}. Operations like
 * {@code map}, {@code flatMap}, {@code ap}, etc., are defined in the corresponding type class
 * instance, {@link OptionalTMonad}, which operates on instances of this {@code OptionalT} record.
 *
 * <p>Using {@code OptionalT} allows chaining operations that return {@code Optional} within the
 * context of the outer monad {@code F}, automatically handling the short-circuiting logic of {@code
 * Optional.empty()} and propagating the context of {@code F}.
 *
 * @param <F> The witness type of the outer monad (e.g., {@code CompletableFutureKind<?>}, {@code
 *     IOKind<?>}). This monad dictates the context in which the {@code Optional} is evaluated.
 * @param <A> The type of the value potentially held by the inner {@link Optional}.
 * @param value The wrapped {@link Kind}{@code <F, Optional<A>>} representing the nested monadic
 *     structure. (NonNull)
 * @see OptionalTKind
 * @see OptionalTMonad
 * @see Optional
 * @see Kind
 */
public record OptionalT<F, A>(@NonNull Kind<F, Optional<A>> value) implements OptionalTKind<F, A> {

  /**
   * Canonical constructor for the OptionalT record. Ensures the wrapped value is not null.
   * Typically, instances should be created using the provided static factory methods.
   *
   * @param value The {@link Kind}{@code <F, Optional<A>>} to wrap. Must not be null. (NonNull)
   * @throws NullPointerException if the provided value is null.
   */
  public OptionalT {
    Objects.requireNonNull(value, "Wrapped value cannot be null for OptionalT");
  }

  // --- Static Factory Methods ---

  /**
   * Wraps an existing {@code Kind<F, Optional<A>>} value into an OptionalT.
   *
   * @param value The {@code Kind<F, Optional<A>>} to wrap. Must not be null. (NonNull)
   * @param <F> The witness type of the outer monad.
   * @param <A> The type of the value in the Optional.
   * @return A new {@code OptionalT<F, A>} instance wrapping the provided value. (NonNull)
   * @throws NullPointerException if value is null.
   */
  public static <F, A> @NonNull OptionalT<F, A> fromKind(@NonNull Kind<F, Optional<A>> value) {
    return new OptionalT<>(value);
  }

  /**
   * Lifts a pure 'present' value {@code a} into the OptionalT context as {@code F<Optional.of(a)>}.
   * Requires the {@link Monad} instance for the outer context {@code F}. Note: Using Optional.of
   * requires 'a' to be non-null. Use {@link #liftF} or {@link #fromOptional} if 'a' might be null
   * or already wrapped.
   *
   * @param outerMonad The Monad instance for the outer context {@code F}. (NonNull)
   * @param a The value to lift, must be non-null. (NonNull)
   * @param <F> The witness type of the outer monad.
   * @param <A> The type of the value.
   * @return An {@code OptionalT<F, A>} instance representing a present value. (NonNull)
   * @throws NullPointerException if 'a' is null.
   */
  public static <F, A extends @NonNull Object> @NonNull OptionalT<F, A> some(
      @NonNull Monad<F> outerMonad, @NonNull A a) {
    // Use Optional.of which checks for null
    Kind<F, Optional<A>> lifted = outerMonad.of(Optional.of(a));
    return new OptionalT<>(lifted);
  }

  /**
   * Lifts the 'empty' state into the OptionalT context as {@code F<Optional.empty()>}. Requires the
   * {@link Monad} instance for the outer context {@code F}. This is often used as the
   * implementation for {@code MonadError.raiseError} for OptionalT.
   *
   * @param outerMonad The Monad instance for the outer context {@code F}. (NonNull)
   * @param <F> The witness type of the outer monad.
   * @param <A> The type parameter for the Optional (phantom type).
   * @return An {@code OptionalT<F, A>} instance representing an empty Optional. (NonNull)
   */
  public static <F, A> @NonNull OptionalT<F, A> none(@NonNull Monad<F> outerMonad) {
    Kind<F, Optional<A>> lifted = outerMonad.of(Optional.empty());
    return new OptionalT<>(lifted);
  }

  /**
   * Lifts a plain {@code Optional<A>} value into the OptionalT context as {@code F<Optional>}.
   * Useful for integrating results from synchronous functions that return {@code Optional} into an
   * {@code OptionalT} workflow. Requires the {@link Monad} instance for the outer context {@code
   * F}.
   *
   * @param outerMonad The Monad instance for the outer context {@code F}. (NonNull)
   * @param optional The {@code Optional<A>} value to lift. Must not be null itself. (NonNull)
   * @param <F> The witness type of the outer monad.
   * @param <A> The type of the value in the Optional.
   * @return An {@code OptionalT<F, A>} instance wrapping the lifted {@code Optional}. (NonNull)
   * @throws NullPointerException if optional is null.
   */
  public static <F, A> @NonNull OptionalT<F, A> fromOptional(
      @NonNull Monad<F> outerMonad, @NonNull Optional<A> optional) {
    Objects.requireNonNull(optional, "Input Optional cannot be null for fromOptional");
    Kind<F, Optional<A>> lifted = outerMonad.of(optional);
    return new OptionalT<>(lifted);
  }

  /**
   * Lifts a value {@code fa} already in the outer monad context {@code Kind<F, A>} into the
   * OptionalT context as {@code F<Optional.ofNullable(a)>}. This maps the value inside {@code F}
   * using {@code Optional::ofNullable}, transforming {@code F<A>} into {@code F<Optional<A>>}, and
   * then wraps the result in {@code OptionalT}. If the original {@code fa} represented an error
   * state in the outer monad {@code F} (e.g., an empty Optional, a failed Future), that state is
   * preserved. Requires the {@link Monad} instance for the outer context {@code F}.
   *
   * @param outerMonad The Monad instance for the outer context {@code F}. (NonNull)
   * @param fa The value already wrapped in the outer monad context {@code Kind<F, A>}. (NonNull)
   * @param <F> The witness type of the outer monad.
   * @param <A> The type of the value.
   * @return An {@code OptionalT<F, A>} instance where the original value from {@code fa} is now
   *     wrapped in an {@code Optional} inside the outer context {@code F}. (NonNull)
   */
  public static <F, A> @NonNull OptionalT<F, A> liftF(
      @NonNull Monad<F> outerMonad, @NonNull Kind<F, A> fa) {
    Kind<F, Optional<A>> mapped = outerMonad.map(Optional::ofNullable, fa);
    return new OptionalT<>(mapped);
  }

  /**
   * Accessor for the wrapped {@link Kind}{@code <F, Optional<A>>} value. Generated automatically by
   * the record.
   *
   * @return The underlying {@link Kind}{@code <F, Optional<A>>}. (NonNull)
   */
  @Override
  public @NonNull Kind<F, Optional<A>> value() {
    return value;
  }
}
