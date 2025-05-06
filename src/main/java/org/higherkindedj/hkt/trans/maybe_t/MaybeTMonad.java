package org.higherkindedj.hkt.trans.maybe_t;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.maybe.Maybe;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Implements the {@link MonadError} interface for the {@link MaybeTKind} monad transformer.
 * The error type {@code E} is fixed to {@link Void}, as {@code MaybeT} inherently represents
 * failure as an absence of a value (similar to {@code Maybe.nothing()}).
 *
 * <p>This class requires a {@link Monad} instance for the outer monad {@code F} to operate.
 * It uses {@link MaybeTKindHelper} to convert between the {@code Kind} representation
 * ({@code MaybeTKind<F, ?>}) and the concrete {@link MaybeT} type.
 *
 * @param <F> The witness type of the outer monad (e.g., {@code OptionalKind<?>}).
 */
public class MaybeTMonad<F> implements MonadError<MaybeTKind<F, ?>, Void> {

  private final @NonNull Monad<F> outerMonad;

  /**
   * Constructs a {@code MaybeTMonad} instance.
   *
   * @param outerMonad The {@link Monad} instance for the outer monad {@code F}.
   * Must not be null.
   * @throws NullPointerException if {@code outerMonad} is null.
   */
  public MaybeTMonad(@NonNull Monad<F> outerMonad) {
    this.outerMonad =
        Objects.requireNonNull(outerMonad, "Outer Monad instance cannot be null for MaybeTMonad");
  }

  /**
   * Lifts a value {@code a} into the {@code MaybeTKind<F, ?>} context.
   * If {@code value} is non-null, it results in {@code F<Just(value)>}.
   * If {@code value} is null, it results in {@code F<Nothing>}.
   *
   * @param <A> The type of the value.
   * @param value The value to lift. Can be null.
   * @return A {@code Kind<MaybeTKind<F, ?>, A>} representing the lifted value.
   */
  @Override
  public <A> @NonNull Kind<MaybeTKind<F, ?>, A> of(@Nullable A value) {
    // Lift using Maybe.fromNullable then outerMonad.of
    Kind<F, Maybe<A>> lifted = outerMonad.of(Maybe.fromNullable(value));
    // Wrap the concrete MaybeT using the helper
    return MaybeTKindHelper.wrap(MaybeT.fromKind(lifted));
  }

  /**
   * Maps a function {@code f} over the value within a {@code MaybeTKind<F, A>}.
   * If the wrapped {@code Kind<F, Maybe<A>>} contains {@code Just(a)}, the function is applied to {@code a}.
   * If it contains {@code Nothing}, or if the function {@code f} returns null,
   * the result is {@code F<Nothing>}.
   * The transformation is applied within the context of the outer monad {@code F}.
   *
   * @param <A> The original type of the value.
   * @param <B> The new type of the value after applying the function.
   * @param f The function to apply. Must not be null.
   * @param fa The {@code Kind<MaybeTKind<F, ?>, A>} to map over. Must not be null.
   * @return A new {@code Kind<MaybeTKind<F, ?>, B>} with the function applied.
   */
  @Override
  public <A, B> @NonNull Kind<MaybeTKind<F, ?>, B> map(
      @NonNull Function<A, B> f, @NonNull Kind<MaybeTKind<F, ?>, A> fa) {
    Objects.requireNonNull(f, "Function f cannot be null for map");
    Objects.requireNonNull(fa, "Kind fa cannot be null for map");
    // Unwrap the Kind to get the concrete MaybeT
    MaybeT<F, A> maybeT = MaybeTKindHelper.unwrap(fa);
    // The map operation on Maybe handles mapping to null by returning Nothing.
    // F<Maybe<A>> map (maybeA -> maybeA.map(f))
    Kind<F, Maybe<B>> newValue = outerMonad.map(maybe -> maybe.map(f), maybeT.value());
    // Wrap the result MaybeT using the helper
    return MaybeTKindHelper.wrap(MaybeT.fromKind(newValue));
  }

  /**
   * Applies a function wrapped in {@code Kind<MaybeTKind<F, ?>, Function<A, B>>}
   * to a value wrapped in {@code Kind<MaybeTKind<F, ?>, A>}.
   *
   * <p>The behavior is as follows:
   * <ul>
   * <li>If both the function and value are present (i.e., {@code F<Just(Function)>} and {@code F<Just(Value)>}),
   * the function is applied, resulting in {@code F<Just(Result)>}.
   * <li>If either the function or value is {@code Nothing} (i.e., {@code F<Nothing>}),
   * the result is {@code F<Nothing>}.
   * <li>This logic is handled by {@code flatMap} and {@code map} on the inner {@link Maybe}
   * and the outer monad {@code F}.
   * </ul>
   *
   * @param <A> The type of the input value.
   * @param <B> The type of the result value.
   * @param ff The wrapped function. Must not be null.
   * @param fa The wrapped value. Must not be null.
   * @return A new {@code Kind<MaybeTKind<F, ?>, B>} representing the application.
   */
  @Override
  public <A, B> @NonNull Kind<MaybeTKind<F, ?>, B> ap(
      @NonNull Kind<MaybeTKind<F, ?>, Function<A, B>> ff, @NonNull Kind<MaybeTKind<F, ?>, A> fa) {
    Objects.requireNonNull(ff, "Kind ff cannot be null for ap");
    Objects.requireNonNull(fa, "Kind fa cannot be null for ap");
    // Unwrap the Kinds
    MaybeT<F, Function<A, B>> funcT = MaybeTKindHelper.unwrap(ff);
    MaybeT<F, A> valT = MaybeTKindHelper.unwrap(fa);

    // The logic F<Maybe<Function>> flatMap ( maybeF -> F<Maybe<Value>> map (maybeV -> maybeF.flatMap(f -> maybeV.map(f))) )
    // can be simplified by using Maybe's applicative-like behavior.
    // outerMonad.map(maybeFunc -> maybeFunc.flatMap(f -> maybeVal.map(f)), funcT.value()) when maybeVal is fixed.
    // Here we need to combine two F<Maybe<T>>.
    Kind<F, Maybe<B>> resultValue =
        outerMonad.flatMap( // F<Maybe<Function<A,B>>>
            maybeF -> // Maybe<Function<A,B>>
                outerMonad.map( // F<Maybe<A>>
                    maybeA -> // Maybe<A>
                        maybeF.flatMap( // Maybe<Function<A,B>> to Maybe<B>
                            maybeA::map), // f -> maybeA.map(f) which is Maybe<A> -> Maybe<B>
                    valT.value()),
            funcT.value());
    // Wrap the result MaybeT
    return MaybeTKindHelper.wrap(MaybeT.fromKind(resultValue));
  }

  /**
   * Applies a function {@code f} that returns a {@code Kind<MaybeTKind<F, ?>, B>} to the
   * value within a {@code Kind<MaybeTKind<F, ?>, A>}, and flattens the result.
   *
   * <p>If the input {@code ma} contains {@code F<Just(a)>}, {@code f(a)} is invoked. The resulting
   * {@code Kind<MaybeTKind<F, ?>, B>} (which internally is {@code F<Maybe<B>>}) becomes the result.
   * If {@code ma} contains {@code F<Nothing>}, or if the inner {@code Maybe} is {@code Nothing},
   * the result is {@code F<Nothing>} within the {@code MaybeTKind} context.
   *
   * @param <A> The original type of the value.
   * @param <B> The type of the value in the resulting {@code Kind}.
   * @param f The function to apply, returning a new {@code Kind}. Must not be null.
   * @param ma The {@code Kind<MaybeTKind<F, ?>, A>} to transform. Must not be null.
   * @return A new {@code Kind<MaybeTKind<F, ?>, B>}.
   */
  @Override
  public <A, B> @NonNull Kind<MaybeTKind<F, ?>, B> flatMap(
      @NonNull Function<A, Kind<MaybeTKind<F, ?>, B>> f, @NonNull Kind<MaybeTKind<F, ?>, A> ma) {
    Objects.requireNonNull(f, "Function f cannot be null for flatMap");
    Objects.requireNonNull(ma, "Kind ma cannot be null for flatMap");
    // Unwrap the input Kind
    MaybeT<F, A> maybeT = MaybeTKindHelper.unwrap(ma);

    Kind<F, Maybe<B>> newValue =
        outerMonad.flatMap( // Operating on F<Maybe<A>>
            maybeA -> // maybeA is Maybe<A>
                maybeA
                    .map( // If Just(a), apply inner function
                        a -> {
                          // Apply f: A -> Kind<MaybeTKind<F, ?>, B>
                          Kind<MaybeTKind<F, ?>, B> resultKind = f.apply(a);
                          // Unwrap the result Kind to get MaybeT<F, B>
                          MaybeT<F, B> resultT = MaybeTKindHelper.unwrap(resultKind);
                          // Extract the inner F<Maybe<B>>
                          return resultT.value(); // This is Kind<F, Maybe<B>>
                        })
                    // If maybeA was Nothing, map returns Nothing.
                    // We need to lift this Nothing into the context of F.
                    // So, if Maybe<A> is Nothing, this becomes Maybe<Kind<F, Maybe<B>>>.nothing()
                    // Then orElse provides a Kind<F, Maybe<B>> directly.
                    .orElse(outerMonad.of(Maybe.nothing())), // If Maybe<A> is Nothing, result is F<Nothing>
            maybeT.value() // The initial Kind<F, Maybe<A>>
        );
    // Wrap the final MaybeT
    return MaybeTKindHelper.wrap(MaybeT.fromKind(newValue));
  }

  // --- MonadError Methods (Error Type E = Void) ---

  /**
   * Raises an error in the {@code MaybeTKind<F, ?>} context. For {@code MaybeT}, an error
   * is represented by the {@code Nothing} state, so this method returns a
   * {@code MaybeTKind} wrapping {@code F<Nothing>}.
   * The provided {@code error} of type {@link Void} is ignored.
   *
   * @param <A> The type parameter for the resulting {@code Kind}, though it will be empty.
   * @param error The error value ({@code null} for {@link Void}).
   * @return A {@code Kind<MaybeTKind<F, ?>, A>} representing {@code F<Nothing>}.
   */
  @Override
  public <A> @NonNull Kind<MaybeTKind<F, ?>, A> raiseError(@Nullable Void error) {
    // The error state for MaybeT is F<Nothing>
    // Wrap the concrete MaybeT using the helper
    return MaybeTKindHelper.wrap(MaybeT.nothing(outerMonad));
  }

  /**
   * Handles an error (represented by {@code Nothing}) in the {@code Kind<MaybeTKind<F, ?>, A>}.
   * If the input {@code ma} represents {@code F<Nothing>}, the {@code handler} function
   * is applied. The {@code Void} parameter to the handler will be {@code null}.
   * If {@code ma} represents {@code F<Just(a)>}, it is returned unchanged.
   * This operation is performed within the context of the outer monad {@code F}.
   *
   * @param <A> The type of the value.
   * @param ma The {@code Kind<MaybeTKind<F, ?>, A>} to handle. Must not be null.
   * @param handler The function to apply if {@code ma} represents {@code F<Nothing>}.
   * It takes a {@link Void} (which will be null) and returns a new
   * {@code Kind<MaybeTKind<F, ?>, A>}. Must not be null.
   * @return A {@code Kind<MaybeTKind<F, ?>, A>}, either the original or the result of the handler.
   */
  @Override
  public <A> @NonNull Kind<MaybeTKind<F, ?>, A> handleErrorWith(
      @NonNull Kind<MaybeTKind<F, ?>, A> ma,
      @NonNull Function<Void, Kind<MaybeTKind<F, ?>, A>> handler) {
    Objects.requireNonNull(ma, "Kind ma cannot be null for handleErrorWith");
    Objects.requireNonNull(handler, "Function handler cannot be null for handleErrorWith");
    // Unwrap the input Kind
    MaybeT<F, A> maybeT = MaybeTKindHelper.unwrap(ma);

    Kind<F, Maybe<A>> handledValue =
        outerMonad.flatMap( // Operating on F<Maybe<A>>
            maybeA -> { // maybeA is Maybe<A>
              if (maybeA.isJust()) {
                // Value is Just, lift it back into F: F<Just(a)>
                return outerMonad.of(maybeA);
              } else {
                // Value is Nothing, apply the handler.
                // handler(null) returns Kind<MaybeTKind<F, ?>, A>
                Kind<MaybeTKind<F, ?>, A> resultKind = handler.apply(null);
                // Unwrap the handler's result Kind to get MaybeT<F, A>
                MaybeT<F, A> resultT = MaybeTKindHelper.unwrap(resultKind);
                // Return the F<Maybe<A>> from the handler's result.
                return resultT.value();
              }
            },
            maybeT.value() // The initial Kind<F, Maybe<A>>
        );
    // Wrap the final MaybeT
    return MaybeTKindHelper.wrap(MaybeT.fromKind(handledValue));
  }
}
