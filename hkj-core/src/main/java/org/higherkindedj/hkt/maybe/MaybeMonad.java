// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * Monad and MonadError implementation for MaybeKind. The error type E is {@link Unit}, representing
 * the Nothing state. Provides Functor and Monad operations for the Maybe type within the HKT
 * Higher-Kinded-J.
 */
public class MaybeMonad extends MaybeFunctor
    implements MonadError<MaybeKind.Witness, Unit>, MonadZero<MaybeKind.Witness> {

  /** Singleton instance of {@code MaybeMonad}. */
  public static final MaybeMonad INSTANCE = new MaybeMonad();

  private static final Class<MaybeMonad> MAYBE_MONAD_CLASS = MaybeMonad.class;

  /** Private constructor to enforce the singleton pattern. */
  protected MaybeMonad() {
    super();
  }

  /**
   * Lifts a potentially null value into the Maybe context. If the value is null, creates Nothing;
   * otherwise creates Just(value).
   *
   * @param <A> The type of the value
   * @param value The value to lift, can be null
   * @return A Kind representing Just(value) if value is non-null, or Nothing if value is null
   */
  @Override
  public <A> Kind<MaybeKind.Witness, A> of(@Nullable A value) {
    return MAYBE.widen(Maybe.fromNullable(value));
  }

  /**
   * Sequentially composes two Maybe computations. If the first computation succeeds (Just), applies
   * the function to the value. If the first computation fails (Nothing), propagates the Nothing.
   *
   * @param <A> The type of the value in the input Maybe
   * @param <B> The type of the value in the resulting Maybe
   * @param f The function to apply to the value if present. Must not be null.
   * @param ma The Maybe to transform. Must not be null.
   * @return The result of the computation chain
   * @throws NullPointerException if f or ma is null
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if ma cannot be unwrapped
   */
  @Override
  public <A, B> Kind<MaybeKind.Witness, B> flatMap(
      Function<? super A, ? extends Kind<MaybeKind.Witness, B>> f, Kind<MaybeKind.Witness, A> ma) {

    Validation.function().requireFlatMapper(f, "f", MAYBE_MONAD_CLASS, FLAT_MAP);
    Validation.kind().requireNonNull(ma, MAYBE_MONAD_CLASS, FLAT_MAP);

    Maybe<A> maybeA = MAYBE.narrow(ma);

    Maybe<B> resultMaybe =
        maybeA.flatMap(
            a -> {
              Kind<MaybeKind.Witness, B> kindB = f.apply(a);
              Validation.function()
                  .requireNonNullResult(kindB, "f", MAYBE_MONAD_CLASS, FLAT_MAP, Maybe.class);
              return MAYBE.narrow(kindB);
            });

    return MAYBE.widen(resultMaybe);
  }

  /**
   * Applies a function wrapped in a Maybe to a value wrapped in a Maybe. If both are Just, applies
   * the function to the value. If either is Nothing, the result is Nothing.
   *
   * @param <A> The input type of the function
   * @param <B> The output type of the function
   * @param ff The Maybe containing the function. Must not be null.
   * @param fa The Maybe containing the value. Must not be null.
   * @return The result of applying the function if both are Just, otherwise Nothing
   * @throws NullPointerException if ff or fa is null
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if ff or fa cannot be unwrapped
   */
  @Override
  public <A, B> Kind<MaybeKind.Witness, B> ap(
      Kind<MaybeKind.Witness, ? extends Function<A, B>> ff, Kind<MaybeKind.Witness, A> fa) {

    Validation.kind().requireNonNull(ff, MAYBE_MONAD_CLASS, AP, "function");
    Validation.kind().requireNonNull(fa, MAYBE_MONAD_CLASS, AP, "argument");

    Maybe<? extends Function<A, B>> maybeF = MAYBE.narrow(ff);
    Maybe<A> maybeA = MAYBE.narrow(fa);

    Maybe<B> resultMaybe = maybeF.flatMap(maybeA::map);
    return MAYBE.widen(resultMaybe);
  }

  // --- MonadError Methods ---

  /**
   * Lifts the error state (Nothing) into the Maybe context. The input 'error' (Unit) is ignored,
   * but {@link Unit#INSTANCE} should be passed for consistency.
   *
   * @param <A> The phantom type parameter of the value
   * @param error The error value (Unit, typically {@link Unit#INSTANCE})
   * @return A Kind representing Nothing
   */
  @Override
  public <A> Kind<MaybeKind.Witness, A> raiseError(@Nullable Unit error) {
    // Note: For Unit error type, no validation needed - Unit is always valid
    return MAYBE.nothing();
  }

  /**
   * Handles the error state (Nothing) within the Maybe context. If 'ma' is Just, it's returned
   * unchanged. If 'ma' is Nothing, the 'handler' function is applied with {@link Unit#INSTANCE}.
   *
   * @param <A> The type of the value within the Maybe
   * @param ma The Maybe to potentially recover from. Must not be null.
   * @param handler Function to handle the Nothing state. Must not be null.
   * @return Original Kind if Just, or result of handler if Nothing
   * @throws NullPointerException if ma or handler is null
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if ma cannot be unwrapped
   */
  @Override
  public <A> Kind<MaybeKind.Witness, A> handleErrorWith(
      Kind<MaybeKind.Witness, A> ma,
      Function<? super Unit, ? extends Kind<MaybeKind.Witness, A>> handler) {

    Validation.kind().requireNonNull(ma, MAYBE_MONAD_CLASS, HANDLE_ERROR_WITH, "source");
    Validation.function().requireFunction(handler, "handler", MAYBE_MONAD_CLASS, HANDLE_ERROR_WITH);

    Maybe<A> maybe = MAYBE.narrow(ma);

    if (maybe.isNothing()) {
      Kind<MaybeKind.Witness, A> result = handler.apply(Unit.INSTANCE);
      Validation.function()
          .requireNonNullResult(
              result, "handler", MAYBE_MONAD_CLASS, HANDLE_ERROR_WITH, Maybe.class);
      return result;
    }

    return ma;
  }

  @Override
  public <A> Kind<MaybeKind.Witness, A> recoverWith(
      final Kind<MaybeKind.Witness, A> ma, final Kind<MaybeKind.Witness, A> fallback) {

    Validation.kind().requireNonNull(ma, MAYBE_MONAD_CLASS, RECOVER_WITH, "source");
    Validation.kind().requireNonNull(fallback, MAYBE_MONAD_CLASS, RECOVER_WITH, "fallback");

    return handleErrorWith(ma, error -> fallback);
  }

  /**
   * Returns the zero element for the Maybe monad, which is {@code Nothing}. The result is
   * polymorphic and can be safely cast to any {@code Kind<MaybeKind.Witness, T>}.
   *
   * @param <T> The desired inner type of the zero value
   * @return The {@code Nothing} instance as a {@code Kind<MaybeKind.Witness, T>}
   */
  @Override
  public <T> Kind<MaybeKind.Witness, T> zero() {
    return MAYBE.nothing();
  }

  // --- Alternative Methods ---

  /**
   * Combines two Maybe values, returning the first if it's Just, otherwise evaluating and returning
   * the second.
   *
   * <p>This implements the Alternative pattern for Maybe, providing a fallback mechanism. The
   * second argument is lazy (supplied via {@link java.util.function.Supplier}) to avoid unnecessary
   * computation when the first Maybe is Just.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<MaybeKind.Witness, String> primary = MAYBE.just("value");
   * Kind<MaybeKind.Witness, String> fallback = () -> MAYBE.just("default");
   *
   * Kind<MaybeKind.Witness, String> result = orElse(primary, fallback);
   * // result is Just("value")
   *
   * Kind<MaybeKind.Witness, String> result2 = orElse(MAYBE.nothing(), fallback);
   * // result2 is Just("default")
   * }</pre>
   *
   * @param <A> The type of the value within the Maybe
   * @param ma The first Maybe to try. Must not be null.
   * @param mb A {@link java.util.function.Supplier} providing the fallback Maybe. Must not be null.
   * @return The first Maybe if it's Just, otherwise the result of the supplier
   * @throws NullPointerException if ma or mb is null
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if ma cannot be unwrapped
   */
  @Override
  public <A> Kind<MaybeKind.Witness, A> orElse(
      Kind<MaybeKind.Witness, A> ma, Supplier<Kind<MaybeKind.Witness, A>> mb) {

    Validation.kind().requireNonNull(ma, MAYBE_MONAD_CLASS, OR_ELSE, "first alternative");
    Validation.function().requireFunction(mb, "mb", MAYBE_MONAD_CLASS, OR_ELSE);

    Maybe<A> maybeA = MAYBE.narrow(ma);

    if (maybeA.isJust()) {
      return ma;
    }

    Kind<MaybeKind.Witness, A> result = mb.get();
    Validation.function()
        .requireNonNullResult(result, "mb", MAYBE_MONAD_CLASS, OR_ELSE, Maybe.class);

    return result;
  }
}
