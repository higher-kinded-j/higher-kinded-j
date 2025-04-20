package org.simulation.hkt.maybe;

import org.simulation.hkt.Kind;
import org.simulation.hkt.Monad;
import org.simulation.hkt.MonadError;

import java.util.function.Function;

import static org.simulation.hkt.maybe.MaybeKindHelper.*;

/**
 * Monad and MonadError implementation for MaybeKind.
 * The error type E is Void, representing the Nothing state.
 * Provides Functor and Monad operations for the Maybe type within the HKT simulation.
 */
public class MaybeMonad extends MaybeFunctor implements MonadError<MaybeKind<?>, Void> {


  @Override
  public <A> MaybeKind<A> of(A value) {
    return wrap(Maybe.fromNullable(value));
  }

  @Override
  public <A, B> MaybeKind<B> flatMap(Function<A, Kind<MaybeKind<?>, B>> f, Kind<MaybeKind<?>, A> ma) {
    Maybe<A> maybeA = unwrap(ma);

    Maybe<B> resultMaybe = maybeA.flatMap(a -> {
      Kind<MaybeKind<?>, B> kindB = f.apply(a);
      return unwrap(kindB);
    });

    return wrap(resultMaybe);
  }


  @Override
  public <A, B> Kind<MaybeKind<?>, B> ap(Kind<MaybeKind<?>, Function<A, B>> ff, Kind<MaybeKind<?>, A> fa) {
    Maybe<Function<A, B>> maybeF = unwrap(ff);
    Maybe<A> maybeA = unwrap(fa);

    // If function Maybe is Just AND value Maybe is Just, apply function
    // Otherwise, return Nothing. Maybe's flatMap/map handles this.
    Maybe<B> resultMaybe = maybeF.flatMap(f -> maybeA.map(f)); // flatMap on function, map on value

    return wrap(resultMaybe);
  }

  // --- MonadError Methods ---

  /**
   * Lifts the error state (Nothing) into the Maybe context.
   * The input 'error' (Void) is ignored.
   *
   * @param error The error value (Void, ignored).
   * @param <A>   The phantom type parameter of the value.
   * @return A MaybeKind representing Nothing.
   */
  @Override
  public <A> Kind<MaybeKind<?>, A> raiseError(Void error) {
    // For Maybe, the error state is always Nothing, regardless of the Void error value.
    return MaybeKindHelper.nothing(); //
  }

  /**
   * Handles the error state (Nothing) within the Maybe context.
   * If 'ma' is Just, it's returned unchanged.
   * If 'ma' is Nothing, the 'handler' function is applied (with null input as error is Void).
   *
   * @param ma      The MaybeKind value.
   * @param handler Function Void -> Kind<MaybeKind<?>, A> to handle the Nothing state.
   * @param <A>     The type of the value within the Maybe.
   * @return Original Kind if Just, or result of handler if Nothing.
   */
  @Override
  public <A> Kind<MaybeKind<?>, A> handleErrorWith(Kind<MaybeKind<?>, A> ma, Function<Void, Kind<MaybeKind<?>, A>> handler) {
    Maybe<A> maybe = unwrap(ma); //

    if (maybe.isNothing()) { //
      // Apply the handler (passing null because the error type is Void)
      return handler.apply(null);
    } else {
      // It's Just, return the original Kind
      return ma;
    }
  }

}

