package org.higherkindedj.hkt.maybe;

import static org.higherkindedj.hkt.maybe.MaybeKindHelper.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Monad and MonadError implementation for MaybeKind. The error type E is Void, representing the
 * Nothing state. Provides Functor and Monad operations for the Maybe type within the HKT
 * Higher-Kinded-J.
 */
public class MaybeMonad extends MaybeFunctor implements MonadError<MaybeKind<?>, Void> {

  @Override
  public <A> @NonNull MaybeKind<A> of(@Nullable A value) { // Value can be null
    return wrap(Maybe.fromNullable(value)); // fromNullable handles null
  }

  @Override
  public <A, B> @NonNull MaybeKind<B> flatMap(
      @NonNull Function<A, Kind<MaybeKind<?>, B>> f, @NonNull Kind<MaybeKind<?>, A> ma) {
    Maybe<A> maybeA = unwrap(ma); // Handles null/invalid ma

    Maybe<B> resultMaybe =
        maybeA.flatMap(
            a -> { // a is NonNull here
              Kind<MaybeKind<?>, B> kindB = f.apply(a); // f is NonNull
              return unwrap(kindB); // unwrap returns NonNull Maybe
            });

    return wrap(resultMaybe); // wrap requires NonNull Maybe
  }

  @Override
  public <A, B> @NonNull Kind<MaybeKind<?>, B> ap(
      @NonNull Kind<MaybeKind<?>, Function<A, B>> ff, @NonNull Kind<MaybeKind<?>, A> fa) {
    Maybe<Function<A, B>> maybeF = unwrap(ff); // Handles null/invalid ff
    Maybe<A> maybeA = unwrap(fa); // Handles null/invalid fa

    // If function Maybe is Just AND value Maybe is Just, apply function
    // Otherwise, return Nothing. Maybe's flatMap/map handles this.
    Maybe<B> resultMaybe = maybeF.flatMap(f -> maybeA.map(f)); // flatMap on function, map on value

    return wrap(resultMaybe); // wrap requires NonNull Maybe
  }

  // --- MonadError Methods ---

  /**
   * Lifts the error state (Nothing) into the Maybe context. The input 'error' (Void) is ignored.
   *
   * @param error The error value (Void, Nullable).
   * @param <A> The phantom type parameter of the value.
   * @return A MaybeKind representing Nothing. (NonNull)
   */
  @Override
  public <A> @NonNull Kind<MaybeKind<?>, A> raiseError(@Nullable Void error) {
    // For Maybe, the error state is always Nothing, regardless of the Void error value.
    return MaybeKindHelper.nothing(); // nothing() returns NonNull Kind
  }

  /**
   * Handles the error state (Nothing) within the Maybe context. If 'ma' is Just, it's returned
   * unchanged. If 'ma' is Nothing, the 'handler' function is applied (with null input as error is
   * Void).
   *
   * @param ma The MaybeKind value. (NonNull)
   * @param handler Function Void -> {@code Kind<MaybeKind<?>}, A> to handle the Nothing state.
   *     (NonNull)
   * @param <A> The type of the value within the Maybe.
   * @return Original Kind if Just, or result of handler if Nothing. (NonNull)
   */
  @Override
  public <A> @NonNull Kind<MaybeKind<?>, A> handleErrorWith(
      @NonNull Kind<MaybeKind<?>, A> ma, @NonNull Function<Void, Kind<MaybeKind<?>, A>> handler) {
    Maybe<A> maybe = unwrap(ma); // Handles null/invalid ma

    if (maybe.isNothing()) { //
      // Apply the handler (passing null because the error type is Void)
      return handler.apply(null); // Handler must return NonNull Kind
    } else {
      // It's Just, return the original Kind
      return ma; // ma is NonNull
    }
  }
}
