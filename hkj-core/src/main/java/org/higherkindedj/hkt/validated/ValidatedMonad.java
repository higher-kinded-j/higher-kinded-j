// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.higherkindedj.hkt.util.validation.Operation.*;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.higherkindedj.hkt.function.Function5;
import org.higherkindedj.hkt.util.validation.*;

/**
 * Monad instance for {@link Validated}. The error type {@code E} is fixed for this Monad instance.
 * Implements {@link MonadError} which transitively includes {@link org.higherkindedj.hkt.Monad
 * Monad} and {@link org.higherkindedj.hkt.Applicative Applicative}.
 *
 * <p><b>Important Note on Monad Laws:</b> With this implementation, the {@link #ap(Kind, Kind) ap}
 * method (from the {@code Applicative} superclass) accumulates errors, while {@link
 * #flatMap(Function, Kind) flatMap} will still fail fast on the first {@code Invalid} result. This
 * means that the monad law stating that {@code ap} should be equivalent to a {@code flatMap}
 * implementation (i.e., {@code ap(fab, fa)} should equal {@code fab.flatMap(f -> fa.map(f))}) will
 * not hold. This is a common and accepted trade-off when using {@code Validated} to accumulate
 * errors.
 *
 * @param <E> The type of the error value. For ValidatedMonad, this error type E is expected to be
 *     non-null.
 */
public final class ValidatedMonad<E> implements MonadError<ValidatedKind.Witness<E>, E> {

  private Class<ValidatedMonad> VALIDATED_MONAD_CLASS = ValidatedMonad.class;

  private final Semigroup<E> semigroup;

  private ValidatedMonad(Semigroup<E> semigroup) {
    this.semigroup = CoreTypeValidator.requireValue(semigroup, VALIDATED_MONAD_CLASS, CONSTRUCTION);
  }

  /**
   * Provides an instance of {@code ValidatedMonad} for a given error type {@code E}, which requires
   * a {@link Semigroup} for error accumulation in {@code ap}.
   *
   * @param semigroup The semigroup for combining errors. Must not be null.
   * @param <E> The error type.
   * @return A new instance of {@code ValidatedMonad}.
   * @throws NullPointerException if {@code semigroup} is null.
   */
  public static <E> ValidatedMonad<E> instance(Semigroup<E> semigroup) {
    return new ValidatedMonad<>(semigroup);
  }

  @Override
  public <A, B> Kind<ValidatedKind.Witness<E>, B> map(
      Function<? super A, ? extends B> f, Kind<ValidatedKind.Witness<E>, A> fa) {

    FunctionValidator.requireMapper(f, "f", VALIDATED_MONAD_CLASS, MAP);
    KindValidator.requireNonNull(fa, VALIDATED_MONAD_CLASS, MAP);

    Validated<E, A> validated = VALIDATED.narrow(fa);
    Validated<E, B> result = validated.map(f);
    return VALIDATED.widen(result);
  }

  /**
   * Lifts a pure value {@code A} into the {@code Validated} context, creating a {@code
   * Kind<ValidatedKind.Witness<E>, A>} that represents a {@code Valid(value)}. This method is part
   * of the {@link org.higherkindedj.hkt.Applicative Applicative} interface.
   *
   * @param value The value to lift. Must not be null.
   * @param <A> The type of the value.
   * @return A {@code Kind} instance representing {@code Validated.valid(value)}.
   * @throws NullPointerException if value is null.
   */
  @Override
  public <A> Kind<ValidatedKind.Witness<E>, A> of(A value) {
    // Valid requires non-null value, so validate here
    CoreTypeValidator.requireValue(value, VALIDATED_MONAD_CLASS, OF);

    Validated<E, A> validInstance = Validated.valid(value);
    return VALIDATED.widen(validInstance);
  }

  @Override
  public <A, B> Kind<ValidatedKind.Witness<E>, B> ap(
      Kind<ValidatedKind.Witness<E>, ? extends Function<A, B>> ff,
      Kind<ValidatedKind.Witness<E>, A> fa) {

    KindValidator.requireNonNull(ff, VALIDATED_MONAD_CLASS, AP, "function");
    KindValidator.requireNonNull(fa, VALIDATED_MONAD_CLASS, AP, "argument");

    Validated<E, ? extends Function<A, B>> fnValidated = VALIDATED.narrow(ff);
    Validated<E, A> valueValidated = VALIDATED.narrow(fa);

    // Ensure the function type matches what Validated.ap expects
    Validated<E, Function<? super A, ? extends B>> fnValidatedWithWildcards =
        fnValidated.map(f -> (Function<? super A, ? extends B>) f);

    Validated<E, B> result = valueValidated.ap(fnValidatedWithWildcards, semigroup);
    return VALIDATED.widen(result);
  }

  /**
   * Applies a function that returns a {@code Kind<ValidatedKind.Witness<E>, B>} to the value
   * contained in a {@code Kind<ValidatedKind.Witness<E>, A>}, effectively chaining operations.
   *
   * @param f The function to apply. Must not be null and must not return a null {@code Kind}.
   * @param ma The {@code Kind} instance containing the value to transform. Must not be null.
   * @param <A> The type of the value in the input {@code Kind}.
   * @param <B> The type of the value in the output {@code Kind}.
   * @return A {@code Kind} instance representing the result of the flatMap operation.
   * @throws NullPointerException if {@code f} is null, {@code ma} is null, or {@code f} returns a
   *     null {@code Kind}.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ma} cannot be unwrapped
   *     to a valid {@code Validated} representation.
   */
  @Override
  public <A, B> Kind<ValidatedKind.Witness<E>, B> flatMap(
      Function<? super A, ? extends Kind<ValidatedKind.Witness<E>, B>> f,
      Kind<ValidatedKind.Witness<E>, A> ma) {

    FunctionValidator.requireFlatMapper(f, "f", VALIDATED_MONAD_CLASS, FLAT_MAP);
    KindValidator.requireNonNull(ma, VALIDATED_MONAD_CLASS, FLAT_MAP);

    Validated<E, A> validatedValue = VALIDATED.narrow(ma);
    Validated<E, B> result =
        validatedValue.flatMap(
            a -> {
              Kind<ValidatedKind.Witness<E>, B> kindResult = f.apply(a);
              FunctionValidator.requireNonNullResult(kindResult, "f", VALIDATED_MONAD_CLASS, FLAT_MAP, Validated.class);
              return VALIDATED.narrow(kindResult);
            });
    return VALIDATED.widen(result);
  }

  // --- MonadError Implementation ---

  /**
   * Lifts an error value {@code error} into the Validated context, creating an {@code
   * Invalid(error)}. For {@code Validated}, the error {@code E} must be non-null.
   *
   * @param error The non-null error value to lift.
   * @param <A> The phantom type parameter of the value (since this represents an error state).
   * @return The error wrapped as {@code Kind<ValidatedKind.Witness<E>, A>}.
   * @throws NullPointerException if error is null.
   */
  @Override
  public <A> Kind<ValidatedKind.Witness<E>, A> raiseError(E error) {
    // Validated.invalid already validates non-null, but be explicit
    CoreTypeValidator.requireError(error, VALIDATED_MONAD_CLASS);
    return VALIDATED.invalid(error);
  }

  /**
   * Handles an error within the Validated context. If {@code ma} represents a {@code Valid} value,
   * it's returned unchanged. If {@code ma} represents an {@code Invalid(e)}, the {@code handler}
   * function is applied to {@code e} to potentially recover with a new monadic value.
   *
   * @param ma The monadic value ({@code Kind<ValidatedKind.Witness<E>, A>}) potentially containing
   *     an error. Must not be null.
   * @param handler A function that takes an error {@code e} of type {@code E} and returns a new
   *     monadic value ({@code Kind<ValidatedKind.Witness<E>, A>}). Must not be null, and must not
   *     return null.
   * @param <A> The type of the value within the monad.
   * @return The original monadic value if it was {@code Valid}, or the result of the {@code
   *     handler} if it was {@code Invalid}. Guaranteed non-null.
   * @throws NullPointerException if ma, handler, or the result of the handler is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ma} cannot be unwrapped
   *     to a valid {@code Validated} representation.
   */
  @Override
  public <A> Kind<ValidatedKind.Witness<E>, A> handleErrorWith(
      Kind<ValidatedKind.Witness<E>, A> ma,
      Function<? super E, ? extends Kind<ValidatedKind.Witness<E>, A>> handler) {

    KindValidator.requireNonNull(ma, VALIDATED_MONAD_CLASS, HANDLE_ERROR_WITH, "source");
    FunctionValidator.requireFunction(handler, "handler", VALIDATED_MONAD_CLASS, HANDLE_ERROR_WITH);

    Validated<E, A> validated = VALIDATED.narrow(ma);

    if (validated.isInvalid()) {
      E errorValue = validated.getError();
      Kind<ValidatedKind.Witness<E>, A> resultFromHandler = handler.apply(errorValue);
      FunctionValidator.requireNonNullResult(resultFromHandler, "handler", VALIDATED_MONAD_CLASS, HANDLE_ERROR_WITH, Kind.class);
      return resultFromHandler;
    } else {
      return ma;
    }
  }

    @Override
    public <A, B, C> Kind<ValidatedKind.Witness<E>, C> map2(
            Kind<ValidatedKind.Witness<E>, A> fa,
            Kind<ValidatedKind.Witness<E>, B> fb,
            BiFunction<? super A, ? super B, ? extends C> f) {

        KindValidator.requireNonNull(fa, ValidatedMonad.class, MAP_2, "first");
        KindValidator.requireNonNull(fb, ValidatedMonad.class, MAP_2, "second");
        FunctionValidator.requireFunction(f, "combining function", ValidatedMonad.class, MAP_2);

        Validated<E, A> va = VALIDATED.narrow(fa);
        Validated<E, B> vb = VALIDATED.narrow(fb);

        // Collect all errors if any exist
        if (va.isInvalid() || vb.isInvalid()) {
            List<E> errors = new ArrayList<>();
            if (va.isInvalid()) errors.add(va.getError());
            if (vb.isInvalid()) errors.add(vb.getError());

            // Combine all errors using the semigroup
            E combinedError = errors.stream().reduce(semigroup::combine).orElseThrow();
            return VALIDATED.invalid(combinedError);
        }

        // Both valid - apply the function
        C result = f.apply(va.get(), vb.get());
        FunctionValidator.requireNonNullResult(result, "combining function", ValidatedMonad.class, MAP_2);
        return VALIDATED.valid(result);
    }


    @Override
    public <A, B, C, D> Kind<ValidatedKind.Witness<E>, D> map3(
            Kind<ValidatedKind.Witness<E>, A> fa,
            Kind<ValidatedKind.Witness<E>, B> fb,
            Kind<ValidatedKind.Witness<E>, C> fc,
            Function3<? super A, ? super B, ? super C, ? extends D> f) {

        KindValidator.requireNonNull(fa, ValidatedMonad.class, MAP_3, "first");
        KindValidator.requireNonNull(fb, ValidatedMonad.class, MAP_3, "second");
        KindValidator.requireNonNull(fc, ValidatedMonad.class, MAP_3, "third");
        FunctionValidator.requireFunction(f, "f", ValidatedMonad.class, MAP_3);

        Validated<E, A> va = VALIDATED.narrow(fa);
        Validated<E, B> vb = VALIDATED.narrow(fb);
        Validated<E, C> vc = VALIDATED.narrow(fc);

        // Collect all errors if any exist
        if (va.isInvalid() || vb.isInvalid() || vc.isInvalid()) {
            List<E> errors = new ArrayList<>();
            if (va.isInvalid()) errors.add(va.getError());
            if (vb.isInvalid()) errors.add(vb.getError());
            if (vc.isInvalid()) errors.add(vc.getError());

            // Combine all errors using the semigroup
            E combinedError = errors.stream().reduce(semigroup::combine).orElseThrow();
            return VALIDATED.invalid(combinedError);
        }

        // All valid - apply the function
        D result = f.apply(va.get(), vb.get(), vc.get());
        FunctionValidator.requireNonNullResult(result, "f", ValidatedMonad.class, MAP_3);
        return VALIDATED.valid(result);
    }

    @Override
    public <A, B, C, D, R> Kind<ValidatedKind.Witness<E>, R> map4(
            Kind<ValidatedKind.Witness<E>, A> fa,
            Kind<ValidatedKind.Witness<E>, B> fb,
            Kind<ValidatedKind.Witness<E>, C> fc,
            Kind<ValidatedKind.Witness<E>, D> fd,
            Function4<? super A, ? super B, ? super C, ? super D, ? extends R> f) {

        KindValidator.requireNonNull(fa, ValidatedMonad.class, MAP_4, "first");
        KindValidator.requireNonNull(fb, ValidatedMonad.class, MAP_4, "second");
        KindValidator.requireNonNull(fc, ValidatedMonad.class, MAP_4, "third");
        KindValidator.requireNonNull(fd, ValidatedMonad.class, MAP_4, "fourth");
        FunctionValidator.requireFunction(f, "f", ValidatedMonad.class, MAP_4);

        Validated<E, A> va = VALIDATED.narrow(fa);
        Validated<E, B> vb = VALIDATED.narrow(fb);
        Validated<E, C> vc = VALIDATED.narrow(fc);
        Validated<E, D> vd = VALIDATED.narrow(fd);

        // Collect all errors if any exist
        if (va.isInvalid() || vb.isInvalid() || vc.isInvalid() || vd.isInvalid()) {
            List<E> errors = new ArrayList<>();
            if (va.isInvalid()) errors.add(va.getError());
            if (vb.isInvalid()) errors.add(vb.getError());
            if (vc.isInvalid()) errors.add(vc.getError());
            if (vd.isInvalid()) errors.add(vd.getError());

            // Combine all errors using the semigroup
            E combinedError = errors.stream().reduce(semigroup::combine).orElseThrow();
            return VALIDATED.invalid(combinedError);
        }

        // All valid - apply the function
        R result = f.apply(va.get(), vb.get(), vc.get(), vd.get());
        FunctionValidator.requireNonNullResult(result, "f", ValidatedMonad.class, MAP_4);
        return VALIDATED.valid(result);
    }

    @Override
    public <A, B, C, D, E1, R> Kind<ValidatedKind.Witness<E>, R> map5(
            Kind<ValidatedKind.Witness<E>, A> fa,
            Kind<ValidatedKind.Witness<E>, B> fb,
            Kind<ValidatedKind.Witness<E>, C> fc,
            Kind<ValidatedKind.Witness<E>, D> fd,
            Kind<ValidatedKind.Witness<E>, E1> fe,
            Function5<? super A, ? super B, ? super C, ? super D, ? super E1, ? extends R> f) {

        KindValidator.requireNonNull(fa, ValidatedMonad.class, MAP_5, "first");
        KindValidator.requireNonNull(fb, ValidatedMonad.class, MAP_5, "second");
        KindValidator.requireNonNull(fc, ValidatedMonad.class, MAP_5, "third");
        KindValidator.requireNonNull(fd, ValidatedMonad.class, MAP_5, "fourth");
        KindValidator.requireNonNull(fe, ValidatedMonad.class, MAP_5, "fifth");
        FunctionValidator.requireFunction(f, "f", ValidatedMonad.class, MAP_5);

        Validated<E, A> va = VALIDATED.narrow(fa);
        Validated<E, B> vb = VALIDATED.narrow(fb);
        Validated<E, C> vc = VALIDATED.narrow(fc);
        Validated<E, D> vd = VALIDATED.narrow(fd);
        Validated<E, E1> ve = VALIDATED.narrow(fe);

        // Collect all errors if any exist
        if (va.isInvalid() || vb.isInvalid() || vc.isInvalid() || vd.isInvalid() || ve.isInvalid()) {
            List<E> errors = new ArrayList<>();
            if (va.isInvalid()) errors.add(va.getError());
            if (vb.isInvalid()) errors.add(vb.getError());
            if (vc.isInvalid()) errors.add(vc.getError());
            if (vd.isInvalid()) errors.add(vd.getError());
            if (ve.isInvalid()) errors.add(ve.getError());

            // Combine all errors using the semigroup
            E combinedError = errors.stream().reduce(semigroup::combine).orElseThrow();
            return VALIDATED.invalid(combinedError);
        }

        // All valid - apply the function
        R result = f.apply(va.get(), vb.get(), vc.get(), vd.get(), ve.get());
        FunctionValidator.requireNonNullResult(result, "f", ValidatedMonad.class, MAP_5);
        return VALIDATED.valid(result);
    }

    @Override
    public <A> Kind<ValidatedKind.Witness<E>, A> recoverWith(
            Kind<ValidatedKind.Witness<E>, A> ma,
            Kind<ValidatedKind.Witness<E>, A> fallback) {

        KindValidator.requireNonNull(ma, ValidatedMonad.class, RECOVER_WITH, "source");
        KindValidator.requireNonNull(fallback, ValidatedMonad.class, RECOVER_WITH, "fallback");

        return handleErrorWith(ma, e -> fallback);
    }
}
