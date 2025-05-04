package org.higherkindedj.hkt.trans.maybe_t;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.maybe.Maybe;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class MaybeTMonad<F> implements MonadError<MaybeTKind<F, ?>, Void> {

  private final @NonNull Monad<F> outerMonad;

  public MaybeTMonad(@NonNull Monad<F> outerMonad) {
    this.outerMonad =
        Objects.requireNonNull(outerMonad, "Outer Monad instance cannot be null for MaybeTMonad");
  }

  // Applicative 'of'
  @Override
  public <A> @NonNull Kind<MaybeTKind<F, ?>, A> of(@Nullable A value) {
    // Lift using Maybe.fromNullable then outerMonad.of
    Kind<F, Maybe<A>> lifted = outerMonad.of(Maybe.fromNullable(value));
    return MaybeT.fromKind(lifted);
  }

  // Functor 'map'
  @Override
  public <A, B> @NonNull Kind<MaybeTKind<F, ?>, B> map(
      @NonNull Function<A, B> f, @NonNull Kind<MaybeTKind<F, ?>, A> fa) {
    MaybeT<F, A> maybeT = (MaybeT<F, A>) fa;
    // F<Maybe<A>> map (maybeA -> maybeA.map(f))
    Kind<F, Maybe<B>> newValue = outerMonad.map(maybe -> maybe.map(f), maybeT.value());
    return MaybeT.fromKind(newValue);
  }

  // Applicative 'ap'
  @Override
  public <A, B> @NonNull Kind<MaybeTKind<F, ?>, B> ap(
      @NonNull Kind<MaybeTKind<F, ?>, Function<A, B>> ff, @NonNull Kind<MaybeTKind<F, ?>, A> fa) {
    MaybeT<F, Function<A, B>> funcT = (MaybeT<F, Function<A, B>>) ff;
    MaybeT<F, A> valT = (MaybeT<F, A>) fa;

    // F<Maybe<Function>> flatMap ( maybeF -> F<Maybe<Value>> map (maybeV -> maybeF.flatMap(f ->
    // maybeV.map(f))) )
    Kind<F, Maybe<B>> resultValue =
        outerMonad.flatMap(
            maybeF ->
                outerMonad.map(
                    maybeA ->
                        maybeF.flatMap(
                            maybeA::map), // Inner: Maybe<Func> flatMap (Maybe<Val> map f)
                    valT.value()),
            funcT.value());
    return MaybeT.fromKind(resultValue);
  }

  // Monad 'flatMap'
  @Override
  public <A, B> @NonNull Kind<MaybeTKind<F, ?>, B> flatMap(
      @NonNull Function<A, Kind<MaybeTKind<F, ?>, B>> f, @NonNull Kind<MaybeTKind<F, ?>, A> ma) {
    MaybeT<F, A> maybeT = (MaybeT<F, A>) ma;

    Kind<F, Maybe<B>> newValue =
        outerMonad.flatMap(
            maybeA ->
                maybeA
                    .map(
                        a -> {
                          // Apply f: A -> MaybeT<F, B>
                          MaybeT<F, B> resultT = (MaybeT<F, B>) f.apply(a);
                          // Extract the inner F<Maybe<B>>
                          return resultT.value();
                        })
                    // If maybeA is Nothing, map returns Nothing. We need F<Nothing> in this case.
                    .orElse(
                        outerMonad.of(
                            Maybe.nothing())), // If maybeA is Nothing, result is F<Nothing>
            maybeT.value() // Apply flatMap to the initial F<Maybe<A>>
            );
    return MaybeT.fromKind(newValue);
  }

  // --- MonadError Methods (Error Type E = Void) ---

  @Override
  public <A> @NonNull Kind<MaybeTKind<F, ?>, A> raiseError(@Nullable Void error) {
    // The error state is F<Nothing>
    return MaybeT.nothing(outerMonad);
  }

  @Override
  public <A> @NonNull Kind<MaybeTKind<F, ?>, A> handleErrorWith(
      @NonNull Kind<MaybeTKind<F, ?>, A> ma,
      @NonNull Function<Void, Kind<MaybeTKind<F, ?>, A>> handler) {
    MaybeT<F, A> maybeT = (MaybeT<F, A>) ma;

    Kind<F, Maybe<A>> handledValue =
        outerMonad.flatMap(
            maybeA -> {
              if (maybeA.isJust()) {
                // Value is Just, lift it back: F<Just(a)>
                return outerMonad.of(maybeA);
              } else {
                // Value is Nothing, apply the handler.
                // handler(null) returns Kind<MaybeTKind<F, ?>, A>
                MaybeT<F, A> resultT = (MaybeT<F, A>) handler.apply(null);
                // Return the F<Maybe<A>> from the handler's result.
                return resultT.value();
              }
            },
            maybeT.value() // Apply flatMap to the original F<Maybe<A>>
            );
    return MaybeT.fromKind(handledValue);
  }
}
