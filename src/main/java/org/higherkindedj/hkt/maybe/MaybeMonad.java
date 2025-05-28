// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.higherkindedj.hkt.maybe.MaybeKindHelper.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.unit.Unit;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Monad and MonadError implementation for MaybeKind. The error type E is {@link Unit}, representing
 * the Nothing state. Provides Functor and Monad operations for the Maybe type within the HKT
 * Higher-Kinded-J.
 */
public class MaybeMonad extends MaybeFunctor implements MonadError<MaybeKind.Witness, Unit> {

  @Override
  public <A> @NonNull MaybeKind<A> of(@Nullable A value) {
    return wrap(Maybe.fromNullable(value));
  }

  @Override
  public <A, B> @NonNull MaybeKind<B> flatMap(
      @NonNull Function<A, Kind<MaybeKind.Witness, B>> f, @NonNull Kind<MaybeKind.Witness, A> ma) {
    Maybe<A> maybeA = unwrap(ma);

    Maybe<B> resultMaybe =
        maybeA.flatMap(
            a -> {
              Kind<MaybeKind.Witness, B> kindB = f.apply(a);
              return unwrap(kindB);
            });

    return wrap(resultMaybe);
  }

  @Override
  public <A, B> @NonNull MaybeKind<B> ap(
      @NonNull Kind<MaybeKind.Witness, Function<A, B>> ff, @NonNull Kind<MaybeKind.Witness, A> fa) {
    Maybe<Function<A, B>> maybeF = unwrap(ff);
    Maybe<A> maybeA = unwrap(fa);

    Maybe<B> resultMaybe = maybeF.flatMap(f -> maybeA.map(f));

    return wrap(resultMaybe);
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
  public <A> @NonNull MaybeKind<A> raiseError(@NonNull Unit error) {
    return MaybeKindHelper.nothing();
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
  public <A> @NonNull MaybeKind<A> handleErrorWith(
      @NonNull Kind<MaybeKind.Witness, A> ma,
      @NonNull Function<Unit, Kind<MaybeKind.Witness, A>> handler) {
    return unwrap(ma).isNothing() ? (MaybeKind<A>) handler.apply(Unit.INSTANCE) : (MaybeKind<A>) ma;
  }
}
