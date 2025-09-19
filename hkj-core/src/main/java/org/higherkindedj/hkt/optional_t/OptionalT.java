// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional_t;

import static org.higherkindedj.hkt.util.ErrorHandling.*;

import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;

/**
 * Represents the concrete implementation of the Optional Transformer Monad (OptionalT). It wraps a
 * monadic value of type {@code Kind<F, Optional<A>>}, where {@code F} is the outer monad and {@code
 * Optional<A>} is the inner optional value.
 *
 * <p>This class is a record, making it an immutable data holder for the wrapped value. It
 * implements {@link OptionalTKind} to participate in higher-kinded type simulations, allowing it to
 * be treated as {@code Kind<OptionalTKind.Witness<F>, A>}.
 *
 * <p>OptionalT is similar to MaybeT but specifically targets Java's {@link java.util.Optional}.
 *
 * @param <F> The witness type of the outer monad (e.g., {@code IOKind.Witness}, {@code
 *     ListKind.Witness}).
 * @param <A> The type of the value potentially held by the inner {@link Optional}.
 * @param value The underlying monadic value {@code Kind<F, Optional<A>>}. Must not be null.
 * @see OptionalTKind
 * @see OptionalTKindHelper
 * @see OptionalTMonad
 */
public record OptionalT<F, A>(Kind<F, Optional<A>> value) implements OptionalTKind<F, A> {

  /**
   * Canonical constructor for {@code OptionalT}.
   *
   * @param value The underlying monadic value {@code Kind<F, Optional<A>>}.
   * @throws NullPointerException if {@code value} is null.
   */
  public OptionalT { // Canonical constructor
    requireNonNullKind(value, "wrapped value for OptionalT");
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
  public static <F, A> OptionalT<F, A> fromKind(Kind<F, Optional<A>> value) {
    requireNonNullKind(value, "Kind value for fromKind");
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
  public static <F, A extends Object> OptionalT<F, A> some(Monad<F> outerMonad, A a) {
    requireValidOuterMonad(outerMonad, "some");
    // Note: Optional.of(a) will throw NullPointerException if a is null, which is desired behavior
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
  public static <F, A> OptionalT<F, A> none(Monad<F> outerMonad) {
    requireValidOuterMonad(outerMonad, "none");
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
  public static <F, A> OptionalT<F, A> fromOptional(Monad<F> outerMonad, Optional<A> optional) {
    requireValidOuterMonad(outerMonad, "fromOptional");
    requireNonNullForWiden(optional, "Optional");
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
  public static <F, A> OptionalT<F, A> liftF(Monad<F> outerMonad, Kind<F, A> fa) {
    requireValidOuterMonad(outerMonad, "liftF");
    // Note: We don't need to validate fa here as outerMonad.map will handle null checking
    Kind<F, Optional<A>> mapped = outerMonad.map(Optional::ofNullable, fa);
    return new OptionalT<>(mapped);
  }

  /**
   * Accessor for the underlying monadic value.
   *
   * @return The {@code Kind<F, Optional<A>>} wrapped by this {@code OptionalT}.
   */
  @Override
  public Kind<F, Optional<A>> value() {
    return value;
  }
}
