// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe_t;

import static org.higherkindedj.hkt.maybe_t.MaybeTKindHelper.MAYBE_T;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.util.validation.Validation;
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
public class MaybeTMonad<F extends WitnessArity<TypeArity.Unary>>
    implements MonadError<MaybeTKind.Witness<F>, Unit> {

  private static final Class<MaybeTMonad> MAYBE_T_MONAD_CLASS = MaybeTMonad.class;
  private final Monad<F> outerMonad;

  /**
   * Constructs a {@code MaybeTMonad} instance.
   *
   * @param outerMonad The {@link Monad} instance for the outer monad {@code F}. Must not be null.
   * @throws NullPointerException if {@code outerMonad} is null.
   */
  public MaybeTMonad(Monad<F> outerMonad) {
    this.outerMonad =
        Validation.transformer().requireOuterMonad(outerMonad, MAYBE_T_MONAD_CLASS, CONSTRUCTION);
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
  public <A> Kind<MaybeTKind.Witness<F>, A> of(@Nullable A value) {
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
   * @throws NullPointerException if {@code f} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fa} cannot be unwrapped.
   */
  @Override
  public <A, B> Kind<MaybeTKind.Witness<F>, B> map(
      Function<? super A, ? extends B> f, Kind<MaybeTKind.Witness<F>, A> fa) {

    Validation.function().requireMapper(f, "f", MAYBE_T_MONAD_CLASS, MAP);
    Validation.kind().requireNonNull(fa, MAYBE_T_MONAD_CLASS, MAP);

    MaybeT<F, A> maybeT = MAYBE_T.narrow(fa);
    Kind<F, Maybe<B>> newValue = outerMonad.map(maybe -> maybe.map(f), maybeT.value());
    return MAYBE_T.widen(MaybeT.fromKind(newValue));
  }

  /**
   * Applies a function wrapped in {@code Kind<MaybeTKind.Witness<F>, Function<A, B>>} to a value
   * wrapped in {@code Kind<MaybeTKind.Witness<F>, A>}.
   *
   * @param <A> The type of the input value.
   * @param <B> The type of the result value.
   * @param ff The wrapped function. Must not be null.
   * @param fa The wrapped value. Must not be null.
   * @return A new {@code Kind<MaybeTKind.Witness<F>, B>} representing the application.
   * @throws NullPointerException if {@code ff} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ff} or {@code fa} cannot
   *     be unwrapped.
   */
  @Override
  public <A, B> Kind<MaybeTKind.Witness<F>, B> ap(
      Kind<MaybeTKind.Witness<F>, ? extends Function<A, B>> ff, Kind<MaybeTKind.Witness<F>, A> fa) {

    Validation.kind().requireNonNull(ff, MAYBE_T_MONAD_CLASS, AP, "function");
    Validation.kind().requireNonNull(fa, MAYBE_T_MONAD_CLASS, AP, "argument");

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
   * @param <A> The original type of the value.
   * @param <B> The type of the value in the resulting {@code Kind}.
   * @param f The function to apply, returning a new {@code Kind}. Must not be null.
   * @param ma The {@code Kind<MaybeTKind.Witness<F>, A>} to transform. Must not be null.
   * @return A new {@code Kind<MaybeTKind.Witness<F>, B>}.
   * @throws NullPointerException if {@code f} or {@code ma} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ma} or the result of
   *     {@code f} cannot be unwrapped.
   */
  @Override
  public <A, B> Kind<MaybeTKind.Witness<F>, B> flatMap(
      Function<? super A, ? extends Kind<MaybeTKind.Witness<F>, B>> f,
      Kind<MaybeTKind.Witness<F>, A> ma) {

    Validation.function().requireFlatMapper(f, "f", MAYBE_T_MONAD_CLASS, FLAT_MAP);
    Validation.kind().requireNonNull(ma, MAYBE_T_MONAD_CLASS, FLAT_MAP);

    MaybeT<F, A> maybeT = MAYBE_T.narrow(ma);

    Kind<F, Maybe<B>> newValue =
        outerMonad.flatMap(
            maybeA ->
                maybeA
                    .map(
                        a -> {
                          Kind<MaybeTKind.Witness<F>, B> resultKind = f.apply(a);
                          Validation.function()
                              .requireNonNullResult(
                                  resultKind, "f", MAYBE_T_MONAD_CLASS, FLAT_MAP, Kind.class);
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
  public <A> Kind<MaybeTKind.Witness<F>, A> raiseError(@Nullable Unit error) {
    // Note: error parameter is ignored since Nothing doesn't carry error information
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
   * @throws NullPointerException if {@code ma} or {@code handler} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ma} or the result of
   *     {@code handler} cannot be unwrapped.
   */
  @Override
  public <A> Kind<MaybeTKind.Witness<F>, A> handleErrorWith(
      Kind<MaybeTKind.Witness<F>, A> ma,
      Function<? super Unit, ? extends Kind<MaybeTKind.Witness<F>, A>> handler) {

    Validation.kind().requireNonNull(ma, MAYBE_T_MONAD_CLASS, HANDLE_ERROR_WITH, "source");
    Validation.function()
        .requireFunction(handler, "handler", MAYBE_T_MONAD_CLASS, HANDLE_ERROR_WITH);

    MaybeT<F, A> maybeT = MAYBE_T.narrow(ma);

    Kind<F, Maybe<A>> handledValue =
        outerMonad.flatMap(
            maybeA -> {
              if (maybeA.isJust()) {
                return outerMonad.of(maybeA); // If Just(a), return F<Just(a)>
              } else { // If Nothing
                Kind<MaybeTKind.Witness<F>, A> resultKind = handler.apply(Unit.INSTANCE);
                Validation.function()
                    .requireNonNullResult(
                        resultKind, "handler", MAYBE_T_MONAD_CLASS, HANDLE_ERROR_WITH, Kind.class);
                MaybeT<F, A> resultT = MAYBE_T.narrow(resultKind);
                return resultT.value(); // This is Kind<F, Maybe<A>>
              }
            },
            maybeT.value());
    return MAYBE_T.widen(MaybeT.fromKind(handledValue));
  }
}
