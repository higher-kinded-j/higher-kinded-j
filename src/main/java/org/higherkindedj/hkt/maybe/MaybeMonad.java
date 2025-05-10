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
public class MaybeMonad extends MaybeFunctor implements MonadError<MaybeKind.Witness, Void> {

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
   * Lifts the error state (Nothing) into the Maybe context. The input 'error' (Void) is ignored.
   *
   * @param error The error value (Void, Nullable).
   * @param <A> The phantom type parameter of the value.
   * @return A MaybeKind representing Nothing. (NonNull)
   */
  @Override
  public <A> @NonNull MaybeKind<A> raiseError(@Nullable Void error) {
    return MaybeKindHelper.nothing();
  }

  /**
   * Handles the error state (Nothing) within the Maybe context. If 'ma' is Just, it's returned
   * unchanged. If 'ma' is Nothing, the 'handler' function is applied (with null input as error is
   * Void).
   *
   * @param ma The MaybeKind value. (NonNull)
   * @param handler Function Void -> {@code Kind<MaybeKind.Witness, A>} to handle the Nothing state.
   *     (NonNull)
   * @param <A> The type of the value within the Maybe.
   * @return Original Kind if Just, or result of handler if Nothing. (NonNull)
   */
  @Override
  public <A> @NonNull MaybeKind<A> handleErrorWith(
      @NonNull Kind<MaybeKind.Witness, A> ma,
      @NonNull Function<Void, Kind<MaybeKind.Witness, A>> handler) {
    // Using a distinct variable name for clarity and to avoid potential conflicts
    final Maybe<A> unwrappedValue = unwrap(ma);

    if (unwrappedValue.isNothing()) {
      // Apply the handler (passing null because the error type is Void)
      // The handler returns Kind<MaybeKind.Witness, A>.
      // Since this method returns MaybeKind<A>, a cast might be needed if the handler
      // could return a different Kind implementation for the same Witness.
      // Assuming handler produces a MaybeKind from this system.
      return (MaybeKind<A>) handler.apply(null);
    } else {
      // It's Just, return the original Kind.
      // ma is Kind<MaybeKind.Witness, A>. Cast to MaybeKind<A> if it's known to be from this
      // system.
      return (MaybeKind<A>) ma;
    }
  }
}
