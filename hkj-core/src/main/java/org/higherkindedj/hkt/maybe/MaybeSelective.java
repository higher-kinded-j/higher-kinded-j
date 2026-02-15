// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Implements the {@link Selective} type class for {@link Maybe}. This provides selective
 * applicative operations that allow conditional execution based on the result of previous
 * computations.
 *
 * <p>The Selective interface sits between {@link org.higherkindedj.hkt.Applicative} and {@link
 * org.higherkindedj.hkt.Monad} in terms of power. For Maybe, selective operations handle the {@link
 * Just} and {@link Nothing} cases appropriately:
 *
 * <ul>
 *   <li>{@link Just} values: Operations are applied
 *   <li>{@link Nothing} values: Propagated unchanged (short-circuit)
 * </ul>
 *
 * <p><b>Unit Usage:</b> The {@link #whenS(Kind, Kind)} method uses {@link Unit} to distinguish
 * between two different "empty-like" states:
 *
 * <ul>
 *   <li>{@code Nothing}: No condition to evaluate (input was Nothing)
 *   <li>{@code Just(Unit.INSTANCE)}: Condition evaluated to false, operation skipped
 * </ul>
 *
 * This distinction is semantically important and improves code clarity. Previously, both cases
 * returned {@code Nothing}, making them indistinguishable. This mirrors the same semantic
 * improvement made in {@link org.higherkindedj.hkt.optional.OptionalSelective}.
 *
 * <p>Key operations:
 *
 * <ul>
 *   <li>{@link #select(Kind, Kind)}: Conditionally applies an effectful function based on a Choice.
 *   <li>{@link #branch(Kind, Kind, Kind)}: Provides two-way conditional choice with different
 *       handlers.
 *   <li>{@link #whenS(Kind, Kind)}: Conditionally executes a Unit-returning effect based on a
 *       boolean.
 *   <li>{@link #ifS(Kind, Kind, Kind)}: Ternary conditional for selective functors.
 * </ul>
 *
 * <p>This class is a singleton, accessible via {@link #INSTANCE}.
 *
 * @see Maybe
 * @see MaybeMonad
 * @see Selective
 * @see Choice
 * @see Unit
 */
public final class MaybeSelective extends MaybeMonad implements Selective<MaybeKind.Witness> {

  /** Singleton instance of {@code MaybeSelective}. */
  public static final MaybeSelective INSTANCE = new MaybeSelective();

  private static final Class<MaybeSelective> MAYBE_SELECTIVE_CLASS = MaybeSelective.class;

  /** Private constructor to enforce the singleton pattern. */
  private MaybeSelective() {
    super();
  }

  /**
   * The core selective operation for Maybe. Given an effectful choice {@code fab} and an effectful
   * function {@code ff}, applies the function only if the choice is a {@code Left}.
   *
   * <p>Behavior:
   *
   * <ul>
   *   <li>If {@code fab} is {@code Nothing}: Returns {@code Nothing}, {@code ff} is not evaluated.
   *   <li>If {@code fab} is {@code Just(Choice.Right(b))}: Returns {@code Just(b)}, {@code ff} is
   *       not evaluated.
   *   <li>If {@code fab} is {@code Just(Choice.Left(a))} and {@code ff} is {@code Just(function)}:
   *       Returns {@code Just(function.apply(a))}.
   *   <li>If {@code fab} is {@code Just(Choice.Left(a))} and {@code ff} is {@code Nothing}: Returns
   *       {@code Nothing}.
   * </ul>
   *
   * @param fab A {@link Kind} representing {@code Maybe<Choice<A, B>>}. Must not be null.
   * @param ff A {@link Kind} representing {@code Maybe<Function<A, B>>}. Must not be null.
   * @param <A> The input type of the function (the type inside {@code Left} of the Choice).
   * @param <B> The output type and the type inside {@code Right} of the Choice.
   * @return A {@link Kind} representing {@code Maybe<B>}. Never null.
   * @throws NullPointerException if {@code fab} or {@code ff} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fab} or {@code ff} cannot
   *     be unwrapped.
   */
  @Override
  public <A, B> Kind<MaybeKind.Witness, B> select(
      Kind<MaybeKind.Witness, Choice<A, B>> fab, Kind<MaybeKind.Witness, Function<A, B>> ff) {

    Validation.kind().requireNonNull(fab, MAYBE_SELECTIVE_CLASS, SELECT, "choice");
    Validation.kind().requireNonNull(ff, MAYBE_SELECTIVE_CLASS, SELECT, "function");

    Maybe<Choice<A, B>> maybeChoice = MAYBE.narrow(fab);

    // Short-circuit if no choice
    if (maybeChoice.isNothing()) {
      return MAYBE.nothing();
    }

    Choice<A, B> choice = maybeChoice.get();

    // If choice is Right(b), we already have our value
    if (choice.isRight()) {
      B rightValue = choice.getRight();
      return MAYBE.widen(Maybe.fromNullable(rightValue));
    }

    // Choice is Left(a), so we need to apply the function
    Maybe<Function<A, B>> maybeFunction = MAYBE.narrow(ff);

    if (maybeFunction.isNothing()) {
      return MAYBE.nothing();
    }

    // Apply the function to the value
    A value = choice.getLeft();
    Function<A, B> function = maybeFunction.get();
    B result = function.apply(value);
    return MAYBE.widen(Maybe.fromNullable(result));
  }

  /**
   * Optimised implementation of {@code branch} for Maybe. Provides a two-way conditional choice,
   * applying the appropriate handler based on whether the Choice is Left or Right.
   *
   * @param fab A {@link Kind} representing {@code Maybe<Choice<A, B>>}. Must not be null.
   * @param fl A {@link Kind} representing {@code Maybe<Function<A, C>>} for the Left case. Must not
   *     be null.
   * @param fr A {@link Kind} representing {@code Maybe<Function<B, C>>} for the Right case. Must
   *     not be null.
   * @param <A> The type inside {@code Left} of the Choice.
   * @param <B> The type inside {@code Right} of the Choice.
   * @param <C> The result type.
   * @return A {@link Kind} representing {@code Maybe<C>}. Never null.
   */
  @Override
  public <A, B, C> Kind<MaybeKind.Witness, C> branch(
      Kind<MaybeKind.Witness, Choice<A, B>> fab,
      Kind<MaybeKind.Witness, Function<A, C>> fl,
      Kind<MaybeKind.Witness, Function<B, C>> fr) {

    Validation.kind().requireNonNull(fab, MAYBE_SELECTIVE_CLASS, BRANCH, "choice");
    Validation.kind().requireNonNull(fl, MAYBE_SELECTIVE_CLASS, BRANCH, "leftHandler");
    Validation.kind().requireNonNull(fr, MAYBE_SELECTIVE_CLASS, BRANCH, "rightHandler");

    Maybe<Choice<A, B>> maybeChoice = MAYBE.narrow(fab);

    // Short-circuit on Nothing
    if (maybeChoice.isNothing()) {
      return MAYBE.nothing();
    }

    Choice<A, B> choice = maybeChoice.get();

    if (choice.isLeft()) {
      // Use left handler
      Maybe<Function<A, C>> leftFunction = MAYBE.narrow(fl);
      if (leftFunction.isNothing()) {
        return MAYBE.nothing();
      }
      C result = leftFunction.get().apply(choice.getLeft());
      return MAYBE.widen(Maybe.fromNullable(result));
    } else {
      // Use right handler
      Maybe<Function<B, C>> rightFunction = MAYBE.narrow(fr);
      if (rightFunction.isNothing()) {
        return MAYBE.nothing();
      }
      C result = rightFunction.get().apply(choice.getRight());
      return MAYBE.widen(Maybe.just(result));
    }
  }

  /**
   * Optimised implementation of {@code whenS} for Maybe. Conditionally executes a Unit-returning
   * effect based on a boolean condition.
   *
   * <p>This method now uses {@link Unit} to clearly distinguish between different states:
   *
   * <ul>
   *   <li>{@code Nothing} in condition: Returns {@code Nothing} (no condition to evaluate)
   *   <li>{@code Just(true)}: Executes effect and returns its result
   *   <li>{@code Just(false)}: Returns {@code Just(Unit.INSTANCE)} (condition evaluated, operation
   *       skipped)
   * </ul>
   *
   * <p>This distinction is semantically important:
   *
   * <ul>
   *   <li>{@code Nothing}: "No information available" (condition itself is absent)
   *   <li>{@code Just(Unit.INSTANCE)}: "Operation completed with no interesting result" (condition
   *       was false)
   * </ul>
   *
   * <p>This mirrors the semantics of {@link org.higherkindedj.hkt.optional.OptionalSelective},
   * where {@code Optional.empty()} means "no value" and {@code Optional.of(Unit.INSTANCE)} means
   * "operation completed with no interesting result".
   *
   * <p><b>Example:</b>
   *
   * <pre>{@code
   * // Condition is present and true - effect executes
   * Maybe<Boolean> shouldLog = Maybe.just(true);
   * Maybe<Unit> logEffect = Maybe.just(Unit.INSTANCE);
   * Maybe<Unit> result1 = MAYBE.narrow(maybeSelective.whenS(
   *     MAYBE.widen(shouldLog),
   *     MAYBE.widen(logEffect)
   * ));
   * // result1 = Just(Unit.INSTANCE) - effect executed
   *
   * // Condition is present and false - effect skipped
   * Maybe<Boolean> shouldNotLog = Maybe.just(false);
   * Maybe<Unit> result2 = MAYBE.narrow(maybeSelective.whenS(
   *     MAYBE.widen(shouldNotLog),
   *     MAYBE.widen(logEffect)
   * ));
   * // result2 = Just(Unit.INSTANCE) - condition evaluated, effect skipped
   *
   * // Condition is absent - no evaluation possible
   * Maybe<Boolean> noCondition = Maybe.nothing();
   * Maybe<Unit> result3 = MAYBE.narrow(maybeSelective.whenS(
   *     MAYBE.widen(noCondition),
   *     MAYBE.widen(logEffect)
   * ));
   * // result3 = Nothing - no condition to evaluate
   * }</pre>
   *
   * @param fcond A {@link Kind} representing {@code Maybe<Boolean>}. Must not be null.
   * @param fa A {@link Kind} representing {@code Maybe<Unit>} to execute if condition is true. Must
   *     not be null.
   * @return A {@link Kind} representing {@code Maybe<Unit>}. Never null.
   */
  @Override
  public Kind<MaybeKind.Witness, Unit> whenS(
      Kind<MaybeKind.Witness, Boolean> fcond, Kind<MaybeKind.Witness, Unit> fa) {

    Validation.kind().requireNonNull(fcond, MAYBE_SELECTIVE_CLASS, WHEN_S, "condition");
    Validation.kind().requireNonNull(fa, MAYBE_SELECTIVE_CLASS, WHEN_S, "effect");

    Maybe<Boolean> condMaybe = MAYBE.narrow(fcond);

    // Short-circuit on Nothing - no condition to evaluate
    if (condMaybe.isNothing()) {
      return MAYBE.nothing();
    }

    boolean condition = condMaybe.get();

    if (condition) {
      // Execute and return the effect
      return fa;
    } else {
      // Condition is false, return Just(Unit.INSTANCE)
      // This is NOT Nothing - it represents "operation completed, effect skipped"
      return MAYBE.widen(Maybe.just(Unit.INSTANCE));
    }
  }

  /**
   * Optimised implementation of {@code ifS} for Maybe. A ternary conditional operator for selective
   * functors.
   *
   * @param fcond A {@link Kind} representing {@code Maybe<Boolean>}. Must not be null.
   * @param fthen A {@link Kind} representing {@code Maybe<A>} for the true branch. Must not be
   *     null.
   * @param felse A {@link Kind} representing {@code Maybe<A>} for the false branch. Must not be
   *     null.
   * @param <A> The type of the result.
   * @return A {@link Kind} representing {@code Maybe<A>}. Never null.
   */
  @Override
  public <A> Kind<MaybeKind.Witness, A> ifS(
      Kind<MaybeKind.Witness, Boolean> fcond,
      Kind<MaybeKind.Witness, A> fthen,
      Kind<MaybeKind.Witness, A> felse) {

    Validation.kind().requireNonNull(fcond, MAYBE_SELECTIVE_CLASS, IF_S, "condition");
    Validation.kind().requireNonNull(fthen, MAYBE_SELECTIVE_CLASS, IF_S, "thenBranch");
    Validation.kind().requireNonNull(felse, MAYBE_SELECTIVE_CLASS, IF_S, "elseBranch");

    Maybe<Boolean> condMaybe = MAYBE.narrow(fcond);

    // Short-circuit on Nothing
    if (condMaybe.isNothing()) {
      return MAYBE.nothing();
    }

    boolean condition = condMaybe.get();

    // Return the appropriate branch
    return condition ? fthen : felse;
  }
}
