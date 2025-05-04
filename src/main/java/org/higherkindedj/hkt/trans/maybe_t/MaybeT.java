package org.higherkindedj.hkt.trans.maybe_t;

import java.util.Objects;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.jspecify.annotations.NonNull;

/** Represents the Maybe Transformer Monad (MaybeT). Wraps a Kind<F, Maybe<A>>. */
public record MaybeT<F, A>(@NonNull Kind<F, Maybe<A>> value) implements MaybeTKind<F, A> {

  public MaybeT {
    Objects.requireNonNull(value, "Wrapped value cannot be null for MaybeT");
  }

  // Static Factory Methods
  public static <F, A> @NonNull MaybeT<F, A> fromKind(@NonNull Kind<F, Maybe<A>> value) {
    return new MaybeT<>(value);
  }

  /** Lifts a non-null value 'a' into F<Just(a)>. Throws if 'a' is null. */
  public static <F, A extends @NonNull Object> @NonNull MaybeT<F, A> just(
      @NonNull Monad<F> outerMonad, @NonNull A a) {
    // Maybe.just enforces non-null 'a'
    Kind<F, Maybe<A>> lifted = outerMonad.of(Maybe.just(a));
    return new MaybeT<>(lifted);
  }

  /** Lifts the Nothing state into F<Nothing>. */
  public static <F, A> @NonNull MaybeT<F, A> nothing(@NonNull Monad<F> outerMonad) {
    Kind<F, Maybe<A>> lifted = outerMonad.of(Maybe.nothing());
    return new MaybeT<>(lifted);
  }

  /** Lifts a plain Maybe<A> into F<Maybe<A>>. */
  public static <F, A> @NonNull MaybeT<F, A> fromMaybe(
      @NonNull Monad<F> outerMonad, @NonNull Maybe<A> maybe) {
    Objects.requireNonNull(maybe, "Input Maybe cannot be null for fromMaybe");
    Kind<F, Maybe<A>> lifted = outerMonad.of(maybe);
    return new MaybeT<>(lifted);
  }

  /** Lifts Kind<F, A> into F<Maybe<A>> using Maybe.fromNullable. */
  public static <F, A> @NonNull MaybeT<F, A> liftF(
      @NonNull Monad<F> outerMonad, @NonNull Kind<F, A> fa) {
    // Use Maybe.fromNullable for safety when mapping the value inside F
    Kind<F, Maybe<A>> mapped = outerMonad.map(Maybe::fromNullable, fa);
    return new MaybeT<>(mapped);
  }

  @Override
  public @NonNull Kind<F, Maybe<A>> value() {
    return value;
  }
}
