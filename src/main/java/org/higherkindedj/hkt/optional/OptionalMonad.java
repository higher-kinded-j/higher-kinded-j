// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static org.higherkindedj.hkt.optional.OptionalKindHelper.*;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.unit.Unit;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Implements the {@link MonadError} type class for {@link java.util.Optional}, using {@link
 * OptionalKind.Witness} as the higher-kinded type witness.
 *
 * <p>This class provides monadic operations for {@code Optional}, allowing it to be used in a
 * generic, functional, and composable manner within the Higher-Kinded-J framework. It treats {@link
 * Optional#empty()} as the error state, with {@link Unit} as the phantom error type, signifying
 * absence rather than a specific error value.
 *
 * <p>Key operations include:
 *
 * <ul>
 *   <li>{@link #of(Object)}: Lifts a potentially nullable value into an {@code OptionalKind}. A
 *       null value results in an empty {@code OptionalKind}.
 *   <li>{@link #map(Function, Kind)}: Applies a function to the value within an {@code
 *       OptionalKind} if it's present.
 *   <li>{@link #flatMap(Function, Kind)}: Applies a function that returns an {@code OptionalKind}
 *       to the value within an {@code OptionalKind} if present, and flattens the result.
 *   <li>{@link #ap(Kind, Kind)}: Applies an {@code OptionalKind} of a function to an {@code
 *       OptionalKind} of a value.
 *   <li>{@link #raiseError(Unit)}: Returns an empty {@code OptionalKind}.
 *   <li>{@link #handleErrorWith(Kind, Function)}: Allows recovery from an empty {@code
 *       OptionalKind}.
 * </ul>
 *
 * <p>This class extends {@link OptionalFunctor} and transitively implements {@link
 * org.higherkindedj.hkt.Monad}, {@link org.higherkindedj.hkt.Applicative}, and {@link
 * org.higherkindedj.hkt.Functor}.
 *
 * @see Optional
 * @see OptionalKind
 * @see OptionalKindHelper
 * @see OptionalFunctor
 * @see MonadError
 * @see Kind
 * @see Unit
 */
public class OptionalMonad extends OptionalFunctor
    implements MonadError<OptionalKind.Witness, Unit> {

  /**
   * Constructs a new {@code OptionalMonad} instance. This constructor is public to allow
   * instantiation where needed, although typically a single instance can be reused.
   */
  public OptionalMonad() {
    // Default constructor
  }

  /**
   * Lifts a value into the {@code OptionalKind} context. If the provided {@code value} is {@code
   * null}, this method returns an empty {@code OptionalKind}. Otherwise, it returns an {@code
   * OptionalKind} containing the value. This is equivalent to {@code Optional.ofNullable(value)}
   * wrapped in {@code OptionalKind}.
   *
   * @param <A> The type of the value.
   * @param value The value to lift. Can be {@code null}.
   * @return A non-null {@code Kind<OptionalKind.Witness, A>} representing {@code
   *     Optional.ofNullable(value)}.
   */
  @Override
  public <A> @NonNull Kind<OptionalKind.Witness, A> of(@Nullable A value) {
    return wrap(Optional.ofNullable(value));
  }

  /**
   * Applies a function to the value within an {@code OptionalKind} if it is present, and flattens
   * the {@code OptionalKind} result. If the input {@code OptionalKind} ({@code ma}) is empty, or if
   * the function {@code f} applied to the present value results in an empty {@code OptionalKind},
   * an empty {@code OptionalKind} is returned.
   *
   * @param <A> The type of the value in the input {@code OptionalKind}.
   * @param <B> The type of the value in the {@code OptionalKind} returned by the function {@code
   *     f}.
   * @param f The non-null function to apply to the value if present. This function must return a
   *     {@code Kind<OptionalKind.Witness, B>}.
   * @param ma The non-null {@code Kind<OptionalKind.Witness, A>} to transform.
   * @return A non-null {@code Kind<OptionalKind.Witness, B>} representing the result of the flatMap
   *     operation.
   */
  @Override
  public <A, B> @NonNull Kind<OptionalKind.Witness, B> flatMap(
      @NonNull Function<A, Kind<OptionalKind.Witness, B>> f,
      @NonNull Kind<OptionalKind.Witness, A> ma) {
    Optional<A> optA = unwrap(ma);
    Optional<B> resultOpt =
        optA.flatMap(
            a -> {
              Kind<OptionalKind.Witness, B> kindB = f.apply(a);
              return unwrap(kindB);
            });
    return wrap(resultOpt);
  }

  /**
   * Applies an {@code OptionalKind} containing a function to an {@code OptionalKind} containing a
   * value. If both the function and the value are present, the function is applied to the value,
   * and the result is wrapped in an {@code OptionalKind}. If either is empty, an empty {@code
   * OptionalKind} is returned.
   *
   * @param <A> The input type of the function.
   * @param <B> The output type of the function.
   * @param ff The non-null {@code Kind<OptionalKind.Witness, Function<A, B>>} containing the
   *     function.
   * @param fa The non-null {@code Kind<OptionalKind.Witness, A>} containing the value.
   * @return A non-null {@code Kind<OptionalKind.Witness, B>} representing the result of the
   *     application.
   */
  @Override
  public <A, B> @NonNull Kind<OptionalKind.Witness, B> ap(
      @NonNull Kind<OptionalKind.Witness, Function<A, B>> ff,
      @NonNull Kind<OptionalKind.Witness, A> fa) {
    Optional<Function<A, B>> optF = unwrap(ff);
    Optional<A> optA = unwrap(fa);
    Optional<B> resultOpt = optF.flatMap(optA::map);
    return wrap(resultOpt);
  }

  /**
   * Raises an error in the {@code OptionalKind} context, which corresponds to an empty {@code
   * Optional}. The error parameter (of type {@link Unit}) is typically {@link Unit#INSTANCE}.
   *
   * @param <A> The phantom type of the value for the resulting empty {@code OptionalKind}.
   * @param error The error value (typically {@link Unit#INSTANCE}). Must be non-null.
   * @return A non-null {@code Kind<OptionalKind.Witness, A>} representing {@code Optional.empty()}.
   */
  @Override
  public <A> @NonNull Kind<OptionalKind.Witness, A> raiseError(@NonNull Unit error) {
    return wrap(Optional.empty());
  }

  /**
   * Handles an error (an empty {@code OptionalKind}) by applying a recovery function. If the input
   * {@code OptionalKind} ({@code ma}) is empty, the {@code handler} function is invoked (with
   * {@link Unit#INSTANCE} as the argument) to produce a new {@code OptionalKind}. If {@code ma} is
   * present, it is returned unchanged.
   *
   * @param <A> The type of the value.
   * @param ma The non-null {@code Kind<OptionalKind.Witness, A>} to handle.
   * @param handler The non-null function to apply if {@code ma} is empty. It takes {@link
   *     Unit#INSTANCE} and returns a new {@code Kind<OptionalKind.Witness, A>}.
   * @return A non-null {@code Kind<OptionalKind.Witness, A>}, either the original if present, or
   *     the result of the handler if empty.
   */
  @Override
  public <A> @NonNull Kind<OptionalKind.Witness, A> handleErrorWith(
      @NonNull Kind<OptionalKind.Witness, A> ma,
      @NonNull Function<Unit, Kind<OptionalKind.Witness, A>> handler) {
    Optional<A> optional = unwrap(ma);
    if (optional.isEmpty()) {
      return handler.apply(Unit.INSTANCE);
    } else {
      return ma;
    }
  }
}
