// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe_t;

import static org.higherkindedj.hkt.maybe_t.MaybeTKindHelper.MAYBE_T;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.unit.Unit;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Implements the {@link MonadError} interface for {@link MaybeT}. The witness for {@code MaybeT<F,
 * ?>} is {@link MaybeTKind.Witness Witness&lt;F&gt;}. The error type {@code E} is fixed to {@link
 * Unit}, as {@code MaybeT} inherently represents failure as an absence of a value (similar to
 * {@code Maybe.nothing()}).
 *
 * <p>This class requires a {@link Monad} instance for the outer monad {@code F} to operate. It uses
 * {@link MaybeTKindHelper} to convert between the {@code Kind<MaybeTKind.Witness<F>, A>}
 * representation and the concrete {@link MaybeT} type.
 *
 * @param <F> The witness type of the outer monad (e.g., {@code OptionalKind.Witness}).
 */
public class MaybeTMonad<F> implements MonadError<MaybeTKind.Witness<F>, Unit> {
  private final @NonNull Monad<F> outerMonad;

  /**
   * Constructs a {@code MaybeTMonad} instance.
   *
   * @param outerMonad The {@link Monad} instance for the outer monad {@code F}. Must not be null.
   * @throws NullPointerException if {@code outerMonad} is null.
   */
  public MaybeTMonad(@NonNull Monad<F> outerMonad) {
    this.outerMonad =
        Objects.requireNonNull(outerMonad, "Outer Monad instance cannot be null for MaybeTMonad");
  }

  /**
   * Lifts a value {@code a} into the {@code Kind<MaybeTKind.Witness<F>, A>} context. If {@code
   * value} is non-null, it results in {@code F<Just(value)>}. If {@code value} is null, it results
   * in {@code F<Nothing>}.
   *
   * @param <A> The type of the value.
   * @param value The value to lift. Can be null.
   * @return A {@code Kind<MaybeTKind.Witness<F>, A>} representing the lifted value.
   */
  @Override
  public <A> @NonNull Kind<MaybeTKind.Witness<F>, A> of(@Nullable A value) {
    Kind<F, Maybe<A>> lifted = outerMonad.of(Maybe.fromNullable(value));
    return MAYBE_T.widen(MaybeT.fromKind(lifted));
  }

  /**
   * Maps a function {@code f} over the value within a {@code Kind<MaybeTKind.Witness<F>, A>}. If
   * the wrapped {@code Kind<F, Maybe<A>>} contains {@code Just(a)}, the function is applied to
   * {@code a}. If it contains {@code Nothing}, or if the function {@code f} returns null, the
   * result is {@code F<Nothing>}. The transformation is applied within the context of the outer
   * monad {@code F}.
   *
   * @param <A> The original type of the value.
   * @param <B> The new type of the value after applying the function.
   * @param f The function to apply. Must not be null.
   * @param fa The {@code Kind<MaybeTKind.Witness<F>, A>} to map over. Must not be null.
   * @return A new {@code Kind<MaybeTKind.Witness<F>, B>} with the function applied.
   */
  @Override
  public <A, B> @NonNull Kind<MaybeTKind.Witness<F>, B> map(
      @NonNull Function<? super A, ? extends B> f, @NonNull Kind<MaybeTKind.Witness<F>, A> fa) {
    Objects.requireNonNull(f, "Function f cannot be null for map");
    Objects.requireNonNull(fa, "Kind fa cannot be null for map");
    MaybeT<F, A> maybeT = MAYBE_T.narrow(fa);
    Kind<F, Maybe<B>> newValue = outerMonad.map(maybe -> maybe.map(f), maybeT.value());
    return MAYBE_T.widen(MaybeT.fromKind(newValue));
  }

  /**
   * Applies a function wrapped in {@code Kind<MaybeTKind.Witness<F>, Function<A, B>>} to a value
   * wrapped in {@code Kind<MaybeTKind.Witness<F>, A>}.
   *
   * <p>The behavior is as follows:
   *
   * <ul>
   *   <li>If both the function and value are present (i.e., {@code F<Just(Function)>} and {@code
   *       F<Just(Value)>}), the function is applied, resulting in {@code F<Just(Result)>}.
   *   <li>If either the function or value is {@code Nothing} (i.e., {@code F<Nothing>}), the result
   *       is {@code F<Nothing>}.
   *   <li>This logic is handled by {@code flatMap} and {@code map} on the inner {@link Maybe} and
   *       the outer monad {@code F}.
   * </ul>
   *
   * @param <A> The type of the input value.
   * @param <B> The type of the result value.
   * @param ff The wrapped function. Must not be null.
   * @param fa The wrapped value. Must not be null.
   * @return A new {@code Kind<MaybeTKind.Witness<F>, B>} representing the application.
   */
  @Override
  public <A, B> @NonNull Kind<MaybeTKind.Witness<F>, B> ap(
      @NonNull Kind<MaybeTKind.Witness<F>, ? extends Function<A, B>> ff,
      @NonNull Kind<MaybeTKind.Witness<F>, A> fa) {
    Objects.requireNonNull(ff, "Kind ff cannot be null for ap");
    Objects.requireNonNull(fa, "Kind fa cannot be null for ap");
    MaybeT<F, ? extends Function<A, B>> funcT = MAYBE_T.narrow(ff);
    MaybeT<F, A> valT = MAYBE_T.narrow(fa);

    Kind<F, Maybe<B>> resultValue =
        outerMonad.flatMap(
            maybeF -> outerMonad.map(maybeA -> maybeF.flatMap(maybeA::map), valT.value()),
            funcT.value());
    return MAYBE_T.widen(MaybeT.fromKind(resultValue));
  }

  /**
   * Applies a function {@code f} that returns a {@code Kind<MaybeTKind.Witness<F>, B>} to the value
   * within a {@code Kind<MaybeTKind.Witness<F>, A>}, and flattens the result.
   *
   * <p>If the input {@code ma} contains {@code F<Just(a)>}, {@code f(a)} is invoked. The resulting
   * {@code Kind<MaybeTKind.Witness<F>, B>} (which internally is {@code F<Maybe<B>>}) becomes the
   * result. If {@code ma} contains {@code F<Nothing>}, or if the inner {@code Maybe} is {@code
   * Nothing}, the result is {@code F<Nothing>} within the {@code MaybeTKind} context.
   *
   * @param <A> The original type of the value.
   * @param <B> The type of the value in the resulting {@code Kind}.
   * @param f The function to apply, returning a new {@code Kind}. Must not be null.
   * @param ma The {@code Kind<MaybeTKind.Witness<F>, A>} to transform. Must not be null.
   * @return A new {@code Kind<MaybeTKind.Witness<F>, B>}.
   */
  @Override
  public <A, B> @NonNull Kind<MaybeTKind.Witness<F>, B> flatMap(
      @NonNull Function<? super A, ? extends Kind<MaybeTKind.Witness<F>, B>> f,
      @NonNull Kind<MaybeTKind.Witness<F>, A> ma) {
    Objects.requireNonNull(f, "Function f cannot be null for flatMap");
    Objects.requireNonNull(ma, "Kind ma cannot be null for flatMap");
    MaybeT<F, A> maybeT = MAYBE_T.narrow(ma);

    Kind<F, Maybe<B>> newValue =
        outerMonad.flatMap(
            maybeA ->
                maybeA
                    .map(
                        a -> {
                          Kind<MaybeTKind.Witness<F>, B> resultKind = f.apply(a);
                          MaybeT<F, B> resultT = MAYBE_T.narrow(resultKind);
                          return resultT.value();
                        })
                    .orElse(
                        outerMonad.of(
                            Maybe.nothing())), // If Maybe<A> is Nothing, result is F<Nothing>
            maybeT.value());
    return MAYBE_T.widen(MaybeT.fromKind(newValue));
  }

  // --- MonadError Methods (Error Type E = Unit) ---

  /**
   * Raises an error in the {@code Kind<MaybeTKind.Witness<F>, A>} context. For {@code MaybeT}, an
   * error is represented by the {@code Nothing} state, so this method returns a {@code Kind}
   * wrapping {@code F<Nothing>}. The provided {@code error} of type {@link Unit} (typically {@link
   * Unit#INSTANCE}) is ignored.
   *
   * @param <A> The type parameter for the resulting {@code Kind}, though it will be empty.
   * @param error The error value ({@link Unit#INSTANCE}).
   * @return A {@code Kind<MaybeTKind.Witness<F>, A>} representing {@code F<Nothing>}.
   */
  @Override
  public <A> @NonNull Kind<MaybeTKind.Witness<F>, A> raiseError(@NonNull Unit error) {
    return MAYBE_T.widen(MaybeT.nothing(outerMonad));
  }

  /**
   * Handles an error (represented by {@code Nothing}) in the {@code Kind<MaybeTKind.Witness<F>,
   * A>}. If the input {@code ma} represents {@code F<Nothing>}, the {@code handler} function is
   * applied. The {@link Unit} parameter to the handler will be {@link Unit#INSTANCE}. If {@code ma}
   * represents {@code F<Just(a)>}, it is returned unchanged. This operation is performed within the
   * context of the outer monad {@code F}.
   *
   * @param <A> The type of the value.
   * @param ma The {@code Kind<MaybeTKind.Witness<F>, A>} to handle. Must not be null.
   * @param handler The function to apply if {@code ma} represents {@code F<Nothing>}. It takes a
   *     {@link Unit} (which will be {@link Unit#INSTANCE}) and returns a new {@code
   *     Kind<MaybeTKind.Witness<F>, A>}. Must not be null.
   * @return A {@code Kind<MaybeTKind.Witness<F>, A>}, either the original or the result of the
   *     handler.
   */
  @Override
  public <A> @NonNull Kind<MaybeTKind.Witness<F>, A> handleErrorWith(
      @NonNull Kind<MaybeTKind.Witness<F>, A> ma,
      @NonNull Function<? super Unit, ? extends Kind<MaybeTKind.Witness<F>, A>> handler) {
    Objects.requireNonNull(ma, "Kind ma cannot be null for handleErrorWith");
    Objects.requireNonNull(handler, "Function handler cannot be null for handleErrorWith");
    MaybeT<F, A> maybeT = MAYBE_T.narrow(ma);

    Kind<F, Maybe<A>> handledValue =
        outerMonad.flatMap(
            maybeA -> {
              if (maybeA.isJust()) {
                return outerMonad.of(maybeA); // If Just(a), return F<Just(a)>
              } else { // If Nothing
                Kind<MaybeTKind.Witness<F>, A> resultKind = handler.apply(Unit.INSTANCE);
                MaybeT<F, A> resultT = MAYBE_T.narrow(resultKind);
                return resultT.value(); // This is Kind<F, Maybe<A>>
              }
            },
            maybeT.value());
    return MAYBE_T.widen(MaybeT.fromKind(handledValue));
  }
}
