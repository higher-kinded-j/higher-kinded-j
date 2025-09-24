// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional_t;

import static org.higherkindedj.hkt.optional_t.OptionalTKindHelper.OPTIONAL_T;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.unit.Unit;
import org.higherkindedj.hkt.util.validation.DomainValidator;
import org.higherkindedj.hkt.util.validation.FunctionValidator;
import org.higherkindedj.hkt.util.validation.KindValidator;
import org.jspecify.annotations.Nullable;

/**
 * Implements the {@link MonadError} interface for {@link OptionalT}. The witness for {@code
 * OptionalT<F, ?>} is {@link OptionalTKind.Witness Witness&lt;F&gt;}. The error type {@code E} is
 * fixed to {@link Unit}, as {@code OptionalT} inherently represents failure as an absence of a
 * value (similar to {@code Optional.empty()}).
 *
 * <p>This class requires a {@link Monad} instance for the outer monad {@code F} to operate. It uses
 * {@link OptionalTKindHelper} to convert between the {@code Kind<OptionalTKind.Witness<F>, A>}
 * representation and the concrete {@link OptionalT} type.
 *
 * @param <F> The witness type of the outer monad (e.g., {@code IOKind.Witness}).
 */
public class OptionalTMonad<F> implements MonadError<OptionalTKind.Witness<F>, Unit> {

  private static final Class<OptionalTMonad> OPTIONAL_T_MONAD_CLASS = OptionalTMonad.class;
  private final Monad<F> outerMonad;

  /**
   * Constructs an {@code OptionalTMonad} instance.
   *
   * @param outerMonad The {@link Monad} instance for the outer monad {@code F}. Must not be null.
   * @throws NullPointerException if {@code outerMonad} is null.
   */
  public OptionalTMonad(Monad<F> outerMonad) {
    this.outerMonad =
        DomainValidator.requireOuterMonad(outerMonad, OPTIONAL_T_MONAD_CLASS, CONSTRUCTION);
  }

  /**
   * Lifts a value {@code a} into the {@code Kind<OptionalTKind.Witness<F>, A>} context. Uses {@link
   * Optional#ofNullable(Object)} to handle potential nulls, resulting in {@code
   * F<Optional.of(value)>} or {@code F<Optional.empty()>}.
   *
   * @param <A> The type of the value.
   * @param value The value to lift. Can be null.
   * @return A {@code Kind<OptionalTKind.Witness<F>, A>} representing the lifted value.
   */
  @Override
  public <A> Kind<OptionalTKind.Witness<F>, A> of(@Nullable A value) {
    Kind<F, Optional<A>> lifted = outerMonad.of(Optional.ofNullable(value));
    return OPTIONAL_T.widen(OptionalT.fromKind(lifted));
  }

  /**
   * Maps a function {@code f} over the value within a {@code Kind<OptionalTKind.Witness<F>, A>}. If
   * the wrapped {@code Kind<F, Optional<A>>} contains {@code Optional.of(a)}, the function is
   * applied. If it contains {@code Optional.empty()}, or if {@code f} returns null, the result is
   * {@code F<Optional.empty()>}. The transformation is applied within the context of the outer
   * monad {@code F}.
   *
   * @param <A> The original type of the value.
   * @param <B> The new type of the value after applying the function.
   * @param f The function to apply. Must not be null.
   * @param fa The {@code Kind<OptionalTKind.Witness<F>, A>} to map over. Must not be null.
   * @return A new {@code Kind<OptionalTKind.Witness<F>, B>} with the function applied.
   * @throws NullPointerException if {@code f} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fa} is not a valid {@code
   *     OptionalT} representation.
   */
  @Override
  public <A, B> Kind<OptionalTKind.Witness<F>, B> map(
      Function<? super A, ? extends @Nullable B> f, Kind<OptionalTKind.Witness<F>, A> fa) {

    FunctionValidator.requireMapper(f, OPTIONAL_T_MONAD_CLASS, MAP);
    KindValidator.requireNonNull(fa, OPTIONAL_T_MONAD_CLASS, MAP);

    OptionalT<F, A> optionalT = OPTIONAL_T.narrow(fa);
    Kind<F, Optional<B>> newValue = outerMonad.map(opt -> opt.map(f), optionalT.value());
    return OPTIONAL_T.widen(OptionalT.fromKind(newValue));
  }

  /**
   * Applies a function wrapped in {@code Kind<OptionalTKind.Witness<F>, Function<A, B>>} to a value
   * wrapped in {@code Kind<OptionalTKind.Witness<F>, A>}.
   *
   * <p>The behavior is as follows:
   *
   * <ul>
   *   <li>If both the function and value are present (i.e., {@code F<Optional.of(Function)>} and
   *       {@code F<Optional.of(Value)>}), the function is applied. If the application results in a
   *       null, it becomes {@code F<Optional.empty()>}. Otherwise, {@code F<Optional.of(Result)>}.
   *   <li>If either the function or value is {@code empty} (i.e., {@code F<Optional.empty()>}), the
   *       result is {@code F<Optional.empty()>}.
   * </ul>
   *
   * @param <A> The type of the input value.
   * @param <B> The type of the result value.
   * @param ff The wrapped function. Must not be null.
   * @param fa The wrapped value. Must not be null.
   * @return A new {@code Kind<OptionalTKind.Witness<F>, B>} representing the application.
   * @throws NullPointerException if {@code ff} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ff} or {@code fa} is not
   *     a valid {@code OptionalT} representation.
   */
  @Override
  public <A, B> Kind<OptionalTKind.Witness<F>, B> ap(
      Kind<OptionalTKind.Witness<F>, ? extends Function<A, @Nullable B>> ff,
      Kind<OptionalTKind.Witness<F>, A> fa) {

    KindValidator.requireNonNull(ff, OPTIONAL_T_MONAD_CLASS, AP, "function");
    KindValidator.requireNonNull(fa, OPTIONAL_T_MONAD_CLASS, AP, "argument");

    OptionalT<F, ? extends Function<A, @Nullable B>> funcT = OPTIONAL_T.narrow(ff);
    OptionalT<F, A> valT = OPTIONAL_T.narrow(fa);

    Kind<F, Optional<B>> resultValue =
        outerMonad.flatMap(
            optF -> outerMonad.map(optA -> optF.flatMap(optA::map), valT.value()), funcT.value());
    return OPTIONAL_T.widen(OptionalT.fromKind(resultValue));
  }

  /**
   * Applies a function {@code f} that returns a {@code Kind<OptionalTKind.Witness<F>, B>} to the
   * value within a {@code Kind<OptionalTKind.Witness<F>, A>}, and flattens the result.
   *
   * <p>If the input {@code ma} contains {@code F<Optional.of(a)>}, {@code f(a)} is invoked. The
   * resulting {@code Kind<OptionalTKind.Witness<F>, B>} (which internally is {@code
   * F<Optional<B>>}) becomes the result. If {@code ma} contains {@code F<Optional.empty()>}, or if
   * the inner {@code Optional} is {@code empty}, the result is {@code F<Optional.empty()>}.
   *
   * @param <A> The original type of the value.
   * @param <B> The type of the value in the resulting {@code Kind}.
   * @param f The function to apply, returning a new {@code Kind}. Must not be null.
   * @param ma The {@code Kind<OptionalTKind.Witness<F>, A>} to transform. Must not be null.
   * @return A new {@code Kind<OptionalTKind.Witness<F>, B>}.
   * @throws NullPointerException if {@code f} or {@code ma} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ma} is not a valid {@code
   *     OptionalT} representation.
   */
  @Override
  public <A, B> Kind<OptionalTKind.Witness<F>, B> flatMap(
      Function<? super A, ? extends Kind<OptionalTKind.Witness<F>, B>> f,
      Kind<OptionalTKind.Witness<F>, A> ma) {

    FunctionValidator.requireFlatMapper(f, OPTIONAL_T_MONAD_CLASS, FLAT_MAP);
    KindValidator.requireNonNull(ma, OPTIONAL_T_MONAD_CLASS, FLAT_MAP);

    OptionalT<F, A> optionalT = OPTIONAL_T.narrow(ma);

    Kind<F, Optional<B>> newValue =
        outerMonad.flatMap(
            optA ->
                optA.map(
                        a -> {
                          Kind<OptionalTKind.Witness<F>, B> resultKind = f.apply(a);
                          FunctionValidator.requireNonNullResult(
                              resultKind, OPTIONAL_T_MONAD_CLASS, FLAT_MAP);
                          OptionalT<F, B> resultT = OPTIONAL_T.narrow(resultKind);
                          return resultT.value();
                        })
                    .orElseGet(() -> outerMonad.of(Optional.empty())),
            optionalT.value());
    return OPTIONAL_T.widen(OptionalT.fromKind(newValue));
  }

  // --- MonadError Methods (Error Type E = Unit) ---

  /**
   * Raises an error in the {@code Kind<OptionalTKind.Witness<F>, A>} context. For {@code
   * OptionalT}, an error is represented by the {@code empty} state, so this method returns a {@code
   * Kind} wrapping {@code F<Optional.empty()>}. The provided {@code error} of type {@link Unit}
   * (typically {@link Unit#INSTANCE}) is ignored.
   *
   * @param <A> The type parameter for the resulting {@code Kind}, though it will be empty.
   * @param error The error value ({@link Unit#INSTANCE}). Must be non-null.
   * @return A {@code Kind<OptionalTKind.Witness<F>, A>} representing {@code F<Optional.empty()>}.
   */
  @Override
  public <A> Kind<OptionalTKind.Witness<F>, A> raiseError(Unit error) {
    // Note: error parameter is ignored since Optional.empty() doesn't carry error information
    return OPTIONAL_T.widen(OptionalT.none(outerMonad));
  }

  /**
   * Handles an error (represented by {@code empty}) in the {@code Kind<OptionalTKind.Witness<F>,
   * A>}. If the input {@code ma} represents {@code F<Optional.empty()>}, the {@code handler}
   * function is applied. The {@link Unit} parameter to the handler will be {@link Unit#INSTANCE}.
   * If {@code ma} represents {@code F<Optional.of(a)>}, it is returned unchanged. This operation is
   * performed within the context of the outer monad {@code F}.
   *
   * @param <A> The type of the value.
   * @param ma The {@code Kind<OptionalTKind.Witness<F>, A>} to handle. Must not be null.
   * @param handler The function to apply if {@code ma} represents {@code F<Optional.empty()>}. It
   *     takes a {@link Unit} (which will be {@link Unit#INSTANCE}) and returns a new {@code
   *     Kind<OptionalTKind.Witness<F>, A>}. Must not be null.
   * @return A {@code Kind<OptionalTKind.Witness<F>, A>}, either the original or the result of the
   *     handler.
   * @throws NullPointerException if {@code ma} or {@code handler} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ma} is not a valid {@code
   *     OptionalT} representation.
   */
  @Override
  public <A> Kind<OptionalTKind.Witness<F>, A> handleErrorWith(
      Kind<OptionalTKind.Witness<F>, A> ma,
      Function<? super Unit, ? extends Kind<OptionalTKind.Witness<F>, A>> handler) {

    KindValidator.requireNonNull(ma, OPTIONAL_T_MONAD_CLASS, HANDLE_ERROR_WITH, "source");
    FunctionValidator.requireFunction(
        handler, "handler", OPTIONAL_T_MONAD_CLASS, HANDLE_ERROR_WITH);

    OptionalT<F, A> optionalT = OPTIONAL_T.narrow(ma);

    Kind<F, Optional<A>> handledValue =
        outerMonad.flatMap(
            optA -> {
              if (optA.isPresent()) {
                return outerMonad.of(optA);
              } else {
                Kind<OptionalTKind.Witness<F>, A> resultKind = handler.apply(Unit.INSTANCE);
                FunctionValidator.requireNonNullResult(
                    resultKind, OPTIONAL_T_MONAD_CLASS, HANDLE_ERROR_WITH);
                OptionalT<F, A> resultT = OPTIONAL_T.narrow(resultKind);
                return resultT.value();
              }
            },
            optionalT.value());
    return OPTIONAL_T.widen(OptionalT.fromKind(handledValue));
  }
}
