// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.unit.Unit;
import org.higherkindedj.hkt.util.validation.FunctionValidator;
import org.higherkindedj.hkt.util.validation.KindValidator;
import org.jspecify.annotations.Nullable;

/**
 * Monad and MonadError implementation for MaybeKind. The error type E is {@link Unit}, representing
 * the Nothing state. Provides Functor and Monad operations for the Maybe type within the HKT
 * Higher-Kinded-J.
 */
public final class MaybeMonad extends MaybeFunctor
    implements MonadError<MaybeKind.Witness, Unit>, MonadZero<MaybeKind.Witness> {

  /** Singleton instance of {@code MaybeMonad}. */
  public static final MaybeMonad INSTANCE = new MaybeMonad();

  private static final Class<MaybeMonad> MAYBE_MONAD_CLASS = MaybeMonad.class;

  /** Private constructor to enforce the singleton pattern. */
  private MaybeMonad() {
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

    FunctionValidator.requireFlatMapper(f, "f", MAYBE_MONAD_CLASS, FLAT_MAP);
    KindValidator.requireNonNull(ma, MAYBE_MONAD_CLASS, FLAT_MAP);

    Maybe<A> maybeA = MAYBE.narrow(ma);

    Maybe<B> resultMaybe =
        maybeA.flatMap(
            a -> {
              Kind<MaybeKind.Witness, B> kindB = f.apply(a);
              FunctionValidator.requireNonNullResult(
                  kindB, "f", MAYBE_MONAD_CLASS, FLAT_MAP, Maybe.class);
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

    KindValidator.requireNonNull(ff, MAYBE_MONAD_CLASS, AP, "function");
    KindValidator.requireNonNull(fa, MAYBE_MONAD_CLASS, AP, "argument");

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

    KindValidator.requireNonNull(ma, MAYBE_MONAD_CLASS, HANDLE_ERROR_WITH, "source");
    FunctionValidator.requireFunction(handler, "handler", MAYBE_MONAD_CLASS, HANDLE_ERROR_WITH);

    Maybe<A> maybe = MAYBE.narrow(ma);

    if (maybe.isNothing()) {
      Kind<MaybeKind.Witness, A> result = handler.apply(Unit.INSTANCE);
      FunctionValidator.requireNonNullResult(
          result, "handler", MAYBE_MONAD_CLASS, HANDLE_ERROR_WITH, Maybe.class);
      return result;
    }

    return ma;
  }

  @Override
  public <A> Kind<MaybeKind.Witness, A> recoverWith(
      final Kind<MaybeKind.Witness, A> ma, final Kind<MaybeKind.Witness, A> fallback) {

    KindValidator.requireNonNull(ma, MAYBE_MONAD_CLASS, RECOVER_WITH, "source");
    KindValidator.requireNonNull(fallback, MAYBE_MONAD_CLASS, RECOVER_WITH, "fallback");

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
}
