// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.higherkindedj.hkt.maybe.MaybeKindHelper.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.unit.Unit;
import org.jspecify.annotations.NonNull;
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

  /** Private constructor to enforce the singleton pattern. */
  private MaybeMonad() {
    // Private constructor
  }

  @Override
  public <A> @NonNull Kind<MaybeKind.Witness, A> of(@Nullable A value) {
    return MAYBE.widen(Maybe.fromNullable(value));
  }

  @Override
  public <A, B> @NonNull Kind<MaybeKind.Witness, B> flatMap(
      @NonNull Function<? super A, ? extends Kind<MaybeKind.Witness, B>> f,
      @NonNull Kind<MaybeKind.Witness, A> ma) {
    Maybe<A> maybeA = MAYBE.narrow(ma);

    Maybe<B> resultMaybe =
        maybeA.flatMap(
            a -> {
              Kind<MaybeKind.Witness, B> kindB = f.apply(a);
              return MAYBE.narrow(kindB);
            });

    return MAYBE.widen(resultMaybe);
  }

  @Override
  public <A, B> @NonNull Kind<MaybeKind.Witness, B> ap(
      @NonNull Kind<MaybeKind.Witness, ? extends Function<A, B>> ff,
      @NonNull Kind<MaybeKind.Witness, A> fa) {
    Maybe<? extends Function<A, B>> maybeF = MAYBE.narrow(ff);
    Maybe<A> maybeA = MAYBE.narrow(fa);

    Maybe<B> resultMaybe = maybeF.flatMap(maybeA::map);

    return MAYBE.widen(resultMaybe);
  }

  // --- MonadError Methods ---

  /**
   * Lifts the error state (Nothing) into the Maybe context. The input 'error' (Unit) is ignored,
   * but {@link Unit#INSTANCE} should be passed.
   *
   * @param error The error value (Unit, NonNull, typically {@link Unit#INSTANCE}).
   * @param <A> The phantom type parameter of the value.
   * @return A MaybeKind representing Nothing. (NonNull)
   */
  @Override
  public <A> @NonNull Kind<MaybeKind.Witness, A> raiseError(@NonNull Unit error) {
    return MAYBE.nothing();
  }

  /**
   * Handles the error state (Nothing) within the Maybe context. If 'ma' is Just, it's returned
   * unchanged. If 'ma' is Nothing, the 'handler' function is applied (with {@link Unit#INSTANCE} as
   * input).
   *
   * @param ma The MaybeKind value. (NonNull)
   * @param handler Function {@code Unit -> Kind<MaybeKind.Witness, A>} to handle the Nothing state.
   *     (NonNull)
   * @param <A> The type of the value within the Maybe.
   * @return Original Kind if Just, or result of handler if Nothing. (NonNull)
   */
  @Override
  public <A> @NonNull Kind<MaybeKind.Witness, A> handleErrorWith(
      @NonNull Kind<MaybeKind.Witness, A> ma,
      @NonNull Function<? super Unit, ? extends Kind<MaybeKind.Witness, A>> handler) {
    return MAYBE.narrow(ma).isNothing() ? handler.apply(Unit.INSTANCE) : ma;
  }

  /**
   * Returns the zero element for the Maybe monad, which is {@code Nothing}. The result is
   * polymorphic and can be safely cast to any {@code Kind<MaybeKind.Witness, T>}.
   *
   * @param <T> The desired inner type of the zero value.
   * @return The {@code Nothing} instance as a {@code Kind<MaybeKind.Witness, T>}.
   */
  @Override
  public <T> Kind<MaybeKind.Witness, T> zero() {
    return MAYBE.nothing();
  }
}
