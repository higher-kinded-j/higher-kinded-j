// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.higherkindedj.hkt.util.validation.Operation.*;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Implements the {@link Selective} type class for {@link Validated}, with a fixed error type {@code
 * E}. This provides selective applicative operations with error accumulation.
 *
 * <p>Unlike {@link org.higherkindedj.hkt.either.EitherSelective} which fails fast on the first
 * error, {@code ValidatedSelective} accumulates all errors using the provided {@link Semigroup}.
 * This is the key advantage of Validated over Either for validation scenarios.
 *
 * <p>The Selective interface sits between {@link org.higherkindedj.hkt.Applicative} and {@link
 * org.higherkindedj.hkt.Monad} in terms of power. For Validated with error accumulation:
 *
 * <ul>
 *   <li>{@link Valid} values: Operations are applied
 *   <li>{@link Invalid} values: Errors are accumulated using the semigroup
 *   <li>Multiple invalids: All errors are combined, not just the first
 * </ul>
 *
 * <p>Key operations:
 *
 * <ul>
 *   <li>{@link #select(Kind, Kind)}: Conditionally applies an effectful function, accumulating
 *       errors.
 *   <li>{@link #branch(Kind, Kind, Kind)}: Provides two-way conditional choice with error
 *       accumulation.
 *   <li>{@link #whenS(Kind, Kind)}: Conditionally executes an effect, accumulating errors.
 *   <li>{@link #ifS(Kind, Kind, Kind)}: Ternary conditional with error accumulation.
 * </ul>
 *
 * <p>This class requires a {@link Semigroup} for combining errors. Use the {@link
 * #instance(Semigroup)} factory method to create instances.
 *
 * @param <E> The fixed type for the error value.
 * @see Validated
 * @see ValidatedMonad
 * @see Selective
 * @see Semigroup
 */
public final class ValidatedSelective<E> extends ValidatedMonad<E>
    implements Selective<ValidatedKind.Witness<E>> {

  private static final Class<ValidatedSelective> VALIDATED_SELECTIVE_CLASS =
      ValidatedSelective.class;

  private final Semigroup<E> semigroup;

  /**
   * Constructs a {@code ValidatedSelective} with the specified semigroup for error accumulation.
   *
   * @param semigroup The semigroup for combining errors. Must not be null.
   */
  private ValidatedSelective(Semigroup<E> semigroup) {
    super(semigroup);
    this.semigroup = semigroup;
  }

  /**
   * Provides an instance of {@code ValidatedSelective} for a given error type {@code E}, which
   * requires a {@link Semigroup} for error accumulation.
   *
   * @param semigroup The semigroup for combining errors. Must not be null.
   * @param <E> The error type.
   * @return A new instance of {@code ValidatedSelective}.
   * @throws NullPointerException if {@code semigroup} is null.
   */
  public static <E> ValidatedSelective<E> instance(Semigroup<E> semigroup) {
    return new ValidatedSelective<>(semigroup);
  }

  /**
   * The core selective operation for Validated with error accumulation. Given an effectful choice
   * {@code fab} and an effectful function {@code ff}, applies the function only if the choice is a
   * {@code Left}, accumulating any errors encountered.
   *
   * <p>Behavior with error accumulation:
   *
   * <ul>
   *   <li>If {@code fab} is {@code Valid(Choice.Right(b))}: Returns {@code Valid(b)}, {@code ff} is
   *       not evaluated.
   *   <li>If {@code fab} is {@code Valid(Choice.Left(a))} and {@code ff} is {@code
   *       Valid(function)}: Returns {@code Valid(function.apply(a))}.
   *   <li>If {@code fab} is {@code Invalid(e1)} and {@code ff} is {@code Invalid(e2)}: Returns
   *       {@code Invalid(semigroup.combine(e1, e2))}.
   *   <li>If only one is {@code Invalid}: Returns that {@code Invalid}.
   * </ul>
   *
   * @param fab A {@link Kind} representing {@code Validated<E, Choice<A, B>>}. Must not be null.
   * @param ff A {@link Kind} representing {@code Validated<E, Function<A, B>>}. Must not be null.
   * @param <A> The input type of the function (the type inside {@code Left} of the Choice).
   * @param <B> The output type and the type inside {@code Right} of the Choice.
   * @return A {@link Kind} representing {@code Validated<E, B>}. Never null.
   * @throws NullPointerException if {@code fab} or {@code ff} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fab} or {@code ff} cannot
   *     be unwrapped.
   */
  @Override
  public <A, B> Kind<ValidatedKind.Witness<E>, B> select(
      Kind<ValidatedKind.Witness<E>, Choice<A, B>> fab,
      Kind<ValidatedKind.Witness<E>, Function<A, B>> ff) {

    Validation.kind().requireNonNull(fab, VALIDATED_SELECTIVE_CLASS, SELECT, "choice");
    Validation.kind().requireNonNull(ff, VALIDATED_SELECTIVE_CLASS, SELECT, "function");

    Validated<E, Choice<A, B>> validatedChoice = VALIDATED.narrow(fab);
    Validated<E, Function<A, B>> validatedFunction = VALIDATED.narrow(ff);

    // Collect errors for accumulation
    List<E> errors = new ArrayList<>();

    // Check if choice has an error
    if (validatedChoice.isInvalid()) {
      errors.add(validatedChoice.getError());
    }

    // If choice is valid and right, return the value without checking function
    if (validatedChoice.isValid()) {
      Choice<A, B> choice = validatedChoice.get();
      if (choice.isRight()) {
        return VALIDATED.widen(Validated.valid(choice.getRight()));
      }

      // Choice is left, so we need the function
      if (validatedFunction.isInvalid()) {
        errors.add(validatedFunction.getError());
      }
    } else {
      // Choice is invalid, still check function to accumulate errors
      if (validatedFunction.isInvalid()) {
        errors.add(validatedFunction.getError());
      }
    }

    // If we have errors, combine and return
    if (!errors.isEmpty()) {
      E combinedError = errors.stream().reduce(semigroup::combine).orElseThrow();
      return VALIDATED.widen(Validated.invalid(combinedError));
    }

    // Both are valid, apply the function
    Choice<A, B> choice = validatedChoice.get();
    Function<A, B> function = validatedFunction.get();
    B result = function.apply(choice.getLeft());

    return VALIDATED.widen(Validated.valid(result));
  }

  /**
   * Optimized implementation of {@code branch} for Validated with error accumulation. Provides a
   * two-way conditional choice, applying the appropriate handler and accumulating all errors.
   *
   * @param fab A {@link Kind} representing {@code Validated<E, Choice<A, B>>}. Must not be null.
   * @param fl A {@link Kind} representing {@code Validated<E, Function<A, C>>} for the Left case.
   *     Must not be null.
   * @param fr A {@link Kind} representing {@code Validated<E, Function<B, C>>} for the Right case.
   *     Must not be null.
   * @param <A> The type inside {@code Left} of the Choice.
   * @param <B> The type inside {@code Right} of the Choice.
   * @param <C> The result type.
   * @return A {@link Kind} representing {@code Validated<E, C>}. Never null.
   */
  @Override
  public <A, B, C> Kind<ValidatedKind.Witness<E>, C> branch(
      Kind<ValidatedKind.Witness<E>, Choice<A, B>> fab,
      Kind<ValidatedKind.Witness<E>, Function<A, C>> fl,
      Kind<ValidatedKind.Witness<E>, Function<B, C>> fr) {

    Validation.kind().requireNonNull(fab, VALIDATED_SELECTIVE_CLASS, BRANCH, "choice");
    Validation.kind().requireNonNull(fl, VALIDATED_SELECTIVE_CLASS, BRANCH, "leftHandler");
    Validation.kind().requireNonNull(fr, VALIDATED_SELECTIVE_CLASS, BRANCH, "rightHandler");

    Validated<E, Choice<A, B>> validatedChoice = VALIDATED.narrow(fab);
    Validated<E, Function<A, C>> leftFunction = VALIDATED.narrow(fl);
    Validated<E, Function<B, C>> rightFunction = VALIDATED.narrow(fr);

    // Collect errors for accumulation
    List<E> errors = new ArrayList<>();

    if (validatedChoice.isInvalid()) {
      errors.add(validatedChoice.getError());
    }

    // Determine which function we need and check for its errors
    if (validatedChoice.isValid()) {
      Choice<A, B> choice = validatedChoice.get();

      if (choice.isLeft()) {
        // Need left function
        if (leftFunction.isInvalid()) {
          errors.add(leftFunction.getError());
        }

        if (!errors.isEmpty()) {
          E combinedError = errors.stream().reduce(semigroup::combine).orElseThrow();
          return VALIDATED.widen(Validated.invalid(combinedError));
        }

        C result = leftFunction.get().apply(choice.getLeft());
        return VALIDATED.widen(Validated.valid(result));
      } else {
        // Need right function
        if (rightFunction.isInvalid()) {
          errors.add(rightFunction.getError());
        }

        if (!errors.isEmpty()) {
          E combinedError = errors.stream().reduce(semigroup::combine).orElseThrow();
          return VALIDATED.widen(Validated.invalid(combinedError));
        }

        C result = rightFunction.get().apply(choice.getRight());
        return VALIDATED.widen(Validated.valid(result));
      }
    } else {
      // Choice is invalid, check both functions to accumulate all errors
      if (leftFunction.isInvalid()) {
        errors.add(leftFunction.getError());
      }
      if (rightFunction.isInvalid()) {
        errors.add(rightFunction.getError());
      }

      E combinedError = errors.stream().reduce(semigroup::combine).orElseThrow();
      return VALIDATED.widen(Validated.invalid(combinedError));
    }
  }

  /**
   * Optimized implementation of {@code whenS} for Validated with error accumulation. Conditionally
   * executes an effect based on a boolean condition, accumulating errors.
   *
   * @param fcond A {@link Kind} representing {@code Validated<E, Boolean>}. Must not be null.
   * @param fa A {@link Kind} representing {@code Validated<E, A>} to execute if condition is true.
   *     Must not be null.
   * @param <A> The type of the effect's result.
   * @return A {@link Kind} representing {@code Validated<E, A>}. Never null.
   */
  @Override
  public <A> Kind<ValidatedKind.Witness<E>, A> whenS(
      Kind<ValidatedKind.Witness<E>, Boolean> fcond, Kind<ValidatedKind.Witness<E>, A> fa) {

    Validation.kind().requireNonNull(fcond, VALIDATED_SELECTIVE_CLASS, WHEN_S, "condition");
    Validation.kind().requireNonNull(fa, VALIDATED_SELECTIVE_CLASS, WHEN_S, "effect");

    Validated<E, Boolean> condValidated = VALIDATED.narrow(fcond);
    Validated<E, A> effectValidated = VALIDATED.narrow(fa);

    // Collect errors
    List<E> errors = new ArrayList<>();

    if (condValidated.isInvalid()) {
      errors.add(condValidated.getError());
    }

    // If condition is valid and false, we don't need the effect
    // If condition is valid and true, we need to check the effect
    if (condValidated.isValid()) {
      boolean condition = condValidated.get();

      if (!condition) {
        // Condition is false, return Invalid with empty error (no-op)
        // Actually, for whenS, we should return a "unit" - for Validated this is tricky
        // The conventional approach is to return null wrapped in Valid
        return VALIDATED.widen(Validated.valid(null));
      }

      // Condition is true, check effect
      if (effectValidated.isInvalid()) {
        errors.add(effectValidated.getError());
      }
    } else {
      // Condition is invalid, still check effect to accumulate errors
      if (effectValidated.isInvalid()) {
        errors.add(effectValidated.getError());
      }
    }

    // If we have errors, combine and return
    if (!errors.isEmpty()) {
      E combinedError = errors.stream().reduce(semigroup::combine).orElseThrow();
      return VALIDATED.widen(Validated.invalid(combinedError));
    }

    // Condition is true and effect is valid, return the effect
    return VALIDATED.widen(effectValidated);
  }

  /**
   * Optimized implementation of {@code ifS} for Validated with error accumulation. A ternary
   * conditional operator that accumulates errors from all branches.
   *
   * @param fcond A {@link Kind} representing {@code Validated<E, Boolean>}. Must not be null.
   * @param fthen A {@link Kind} representing {@code Validated<E, A>} for the true branch. Must not
   *     be null.
   * @param felse A {@link Kind} representing {@code Validated<E, A>} for the false branch. Must not
   *     be null.
   * @param <A> The type of the result.
   * @return A {@link Kind} representing {@code Validated<E, A>}. Never null.
   */
  @Override
  public <A> Kind<ValidatedKind.Witness<E>, A> ifS(
      Kind<ValidatedKind.Witness<E>, Boolean> fcond,
      Kind<ValidatedKind.Witness<E>, A> fthen,
      Kind<ValidatedKind.Witness<E>, A> felse) {

    Validation.kind().requireNonNull(fcond, VALIDATED_SELECTIVE_CLASS, IF_S, "condition");
    Validation.kind().requireNonNull(fthen, VALIDATED_SELECTIVE_CLASS, IF_S, "thenBranch");
    Validation.kind().requireNonNull(felse, VALIDATED_SELECTIVE_CLASS, IF_S, "elseBranch");

    Validated<E, Boolean> condValidated = VALIDATED.narrow(fcond);
    Validated<E, A> thenValidated = VALIDATED.narrow(fthen);
    Validated<E, A> elseValidated = VALIDATED.narrow(felse);

    // Collect errors
    List<E> errors = new ArrayList<>();

    if (condValidated.isInvalid()) {
      errors.add(condValidated.getError());
    }

    // Determine which branch we need based on the condition
    if (condValidated.isValid()) {
      boolean condition = condValidated.get();

      if (condition) {
        // Need then branch
        if (thenValidated.isInvalid()) {
          errors.add(thenValidated.getError());
        }

        if (!errors.isEmpty()) {
          E combinedError = errors.stream().reduce(semigroup::combine).orElseThrow();
          return VALIDATED.widen(Validated.invalid(combinedError));
        }

        return VALIDATED.widen(thenValidated);
      } else {
        // Need else branch
        if (elseValidated.isInvalid()) {
          errors.add(elseValidated.getError());
        }

        if (!errors.isEmpty()) {
          E combinedError = errors.stream().reduce(semigroup::combine).orElseThrow();
          return VALIDATED.widen(Validated.invalid(combinedError));
        }

        return VALIDATED.widen(elseValidated);
      }
    } else {
      // Condition is invalid, accumulate errors from both branches
      if (thenValidated.isInvalid()) {
        errors.add(thenValidated.getError());
      }
      if (elseValidated.isInvalid()) {
        errors.add(elseValidated.getError());
      }

      E combinedError = errors.stream().reduce(semigroup::combine).orElseThrow();
      return VALIDATED.widen(Validated.invalid(combinedError));
    }
  }
}
