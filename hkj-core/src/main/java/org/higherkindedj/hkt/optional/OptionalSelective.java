// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Implements the {@link Selective} type class for {@link java.util.Optional}. This provides
 * selective applicative operations that allow conditional execution based on the result of previous
 * computations.
 *
 * <p>The Selective interface sits between {@link org.higherkindedj.hkt.Applicative} and {@link
 * org.higherkindedj.hkt.Monad} in terms of power. For Optional, selective operations handle the
 * present and empty cases appropriately:
 *
 * <ul>
 *   <li>Present values: Operations are applied
 *   <li>Empty values: Propagated unchanged (short-circuit)
 * </ul>
 *
 * <p><b>Unit Usage:</b> The {@link #whenS(Kind, Kind)} method uses {@link Unit} to distinguish
 * between two different "empty-like" states:
 *
 * <ul>
 *   <li>{@code Optional.empty()}: No condition to evaluate (input was empty)
 *   <li>{@code Optional.of(Unit.INSTANCE)}: Condition evaluated to false, operation skipped
 * </ul>
 *
 * This distinction is semantically important and improves code clarity. Previously, both cases
 * returned {@code Optional.empty()}, making them indistinguishable.
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
 * @see Optional
 * @see OptionalMonad
 * @see Selective
 * @see Choice
 * @see Unit
 */
public final class OptionalSelective extends OptionalMonad
    implements Selective<OptionalKind.Witness> {

  /** Singleton instance of {@code OptionalSelective}. */
  public static final OptionalSelective INSTANCE = new OptionalSelective();

  private static final Class<OptionalSelective> OPTIONAL_SELECTIVE_CLASS = OptionalSelective.class;

  /** Private constructor to enforce the singleton pattern. */
  private OptionalSelective() {
    super();
  }

  /**
   * The core selective operation for Optional. Given an effectful choice {@code fab} and an
   * effectful function {@code ff}, applies the function only if the choice is a {@code Left}.
   *
   * <p>Behavior:
   *
   * <ul>
   *   <li>If {@code fab} is {@code Optional.empty()}: Returns {@code Optional.empty()}, {@code ff}
   *       is not evaluated.
   *   <li>If {@code fab} is {@code Optional.of(Choice.Right(b))}: Returns {@code Optional.of(b)},
   *       {@code ff} is not evaluated.
   *   <li>If {@code fab} is {@code Optional.of(Choice.Left(a))} and {@code ff} is {@code
   *       Optional.of(function)}: Returns {@code Optional.of(function.apply(a))}.
   *   <li>If {@code fab} is {@code Optional.of(Choice.Left(a))} and {@code ff} is {@code
   *       Optional.empty()}: Returns {@code Optional.empty()}.
   * </ul>
   *
   * @param fab A {@link Kind} representing {@code Optional<Choice<A, B>>}. Must not be null.
   * @param ff A {@link Kind} representing {@code Optional<Function<A, B>>}. Must not be null.
   * @param <A> The input type of the function (the type inside {@code Left} of the Choice).
   * @param <B> The output type and the type inside {@code Right} of the Choice.
   * @return A {@link Kind} representing {@code Optional<B>}. Never null.
   * @throws NullPointerException if {@code fab} or {@code ff} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fab} or {@code ff} cannot
   *     be unwrapped.
   */
  @Override
  public <A, B> Kind<OptionalKind.Witness, B> select(
      Kind<OptionalKind.Witness, Choice<A, B>> fab, Kind<OptionalKind.Witness, Function<A, B>> ff) {

    Validation.kind().requireNonNull(fab, OPTIONAL_SELECTIVE_CLASS, SELECT, "choice");
    Validation.kind().requireNonNull(ff, OPTIONAL_SELECTIVE_CLASS, SELECT, "function");

    Optional<Choice<A, B>> optionalChoice = OPTIONAL.narrow(fab);

    // Short-circuit if empty
    if (optionalChoice.isEmpty()) {
      return OPTIONAL.widen(Optional.empty());
    }

    Choice<A, B> choice = optionalChoice.get();

    // If choice is Right(b), we already have our value
    if (choice.isRight()) {
      return OPTIONAL.widen(Optional.of(choice.getRight()));
    }

    // Choice is Left(a), so we need to apply the function
    Optional<Function<A, B>> optionalFunction = OPTIONAL.narrow(ff);

    if (optionalFunction.isEmpty()) {
      return OPTIONAL.widen(Optional.empty());
    }

    // Apply the function to the value
    A value = choice.getLeft();
    Function<A, B> function = optionalFunction.get();
    B result = function.apply(value);

    return OPTIONAL.widen(Optional.ofNullable(result));
  }

  /**
   * Optimised implementation of {@code branch} for Optional. Provides a two-way conditional choice,
   * applying the appropriate handler based on whether the Choice is Left or Right.
   *
   * @param fab A {@link Kind} representing {@code Optional<Choice<A, B>>}. Must not be null.
   * @param fl A {@link Kind} representing {@code Optional<Function<A, C>>} for the Left case. Must
   *     not be null.
   * @param fr A {@link Kind} representing {@code Optional<Function<B, C>>} for the Right case. Must
   *     not be null.
   * @param <A> The type inside {@code Left} of the Choice.
   * @param <B> The type inside {@code Right} of the Choice.
   * @param <C> The result type.
   * @return A {@link Kind} representing {@code Optional<C>}. Never null.
   */
  @Override
  public <A, B, C> Kind<OptionalKind.Witness, C> branch(
      Kind<OptionalKind.Witness, Choice<A, B>> fab,
      Kind<OptionalKind.Witness, Function<A, C>> fl,
      Kind<OptionalKind.Witness, Function<B, C>> fr) {

    Validation.kind().requireNonNull(fab, OPTIONAL_SELECTIVE_CLASS, BRANCH, "choice");
    Validation.kind().requireNonNull(fl, OPTIONAL_SELECTIVE_CLASS, BRANCH, "leftHandler");
    Validation.kind().requireNonNull(fr, OPTIONAL_SELECTIVE_CLASS, BRANCH, "rightHandler");

    Optional<Choice<A, B>> optionalChoice = OPTIONAL.narrow(fab);

    // Short-circuit on empty
    if (optionalChoice.isEmpty()) {
      return OPTIONAL.widen(Optional.empty());
    }

    Choice<A, B> choice = optionalChoice.get();

    if (choice.isLeft()) {
      // Use left handler
      Optional<Function<A, C>> leftFunction = OPTIONAL.narrow(fl);
      if (leftFunction.isEmpty()) {
        return OPTIONAL.widen(Optional.empty());
      }
      C result = leftFunction.get().apply(choice.getLeft());
      return OPTIONAL.widen(Optional.ofNullable(result));
    } else {
      // Use right handler
      Optional<Function<B, C>> rightFunction = OPTIONAL.narrow(fr);
      if (rightFunction.isEmpty()) {
        return OPTIONAL.widen(Optional.empty());
      }
      C result = rightFunction.get().apply(choice.getRight());
      return OPTIONAL.widen(Optional.ofNullable(result));
    }
  }

  /**
   * Optimised implementation of {@code whenS} for Optional. Conditionally executes a Unit-returning
   * effect based on a boolean condition.
   *
   * <p>This method now uses {@link Unit} to clearly distinguish between different states:
   *
   * <ul>
   *   <li>{@code Optional.empty()} in condition: Returns {@code Optional.empty()} (no condition to
   *       evaluate)
   *   <li>{@code Optional.of(true)}: Executes effect and returns its result
   *   <li>{@code Optional.of(false)}: Returns {@code Optional.of(Unit.INSTANCE)} (condition
   *       evaluated, operation skipped)
   * </ul>
   *
   * <p>This distinction is important:
   *
   * <ul>
   *   <li>{@code Optional.empty()}: "No information available" (condition itself is absent)
   *   <li>{@code Optional.of(Unit.INSTANCE)}: "Operation completed with no interesting result"
   *       (condition was false)
   * </ul>
   *
   * <p><b>Example:</b>
   *
   * <pre>{@code
   * // Condition is present and true - effect executes
   * Optional<Boolean> shouldLog = Optional.of(true);
   * Optional<Unit> logEffect = Optional.of(Unit.INSTANCE);
   * Optional<Unit> result1 = OPTIONAL.narrow(optionalSelective.whenS(
   *     OPTIONAL.widen(shouldLog),
   *     OPTIONAL.widen(logEffect)
   * ));
   * // result1 = Optional.of(Unit.INSTANCE) - effect executed
   *
   * // Condition is present and false - effect skipped
   * Optional<Boolean> shouldNotLog = Optional.of(false);
   * Optional<Unit> result2 = OPTIONAL.narrow(optionalSelective.whenS(
   *     OPTIONAL.widen(shouldNotLog),
   *     OPTIONAL.widen(logEffect)
   * ));
   * // result2 = Optional.of(Unit.INSTANCE) - condition evaluated, effect skipped
   *
   * // Condition is absent - no evaluation possible
   * Optional<Boolean> noCondition = Optional.empty();
   * Optional<Unit> result3 = OPTIONAL.narrow(optionalSelective.whenS(
   *     OPTIONAL.widen(noCondition),
   *     OPTIONAL.widen(logEffect)
   * ));
   * // result3 = Optional.empty() - no condition to evaluate
   * }</pre>
   *
   * @param fcond A {@link Kind} representing {@code Optional<Boolean>}. Must not be null.
   * @param fa A {@link Kind} representing {@code Optional<Unit>} to execute if condition is true.
   *     Must not be null.
   * @return A {@link Kind} representing {@code Optional<Unit>}. Never null.
   */
  @Override
  public Kind<OptionalKind.Witness, Unit> whenS(
      Kind<OptionalKind.Witness, Boolean> fcond, Kind<OptionalKind.Witness, Unit> fa) {

    Validation.kind().requireNonNull(fcond, OPTIONAL_SELECTIVE_CLASS, WHEN_S, "condition");
    Validation.kind().requireNonNull(fa, OPTIONAL_SELECTIVE_CLASS, WHEN_S, "effect");

    Optional<Boolean> condOptional = OPTIONAL.narrow(fcond);

    // Short-circuit on empty - no condition to evaluate
    if (condOptional.isEmpty()) {
      return OPTIONAL.widen(Optional.empty());
    }

    boolean condition = condOptional.get();

    if (condition) {
      // Execute and return the effect
      return fa;
    } else {
      // Condition is false, return Optional.of(Unit.INSTANCE)
      // This is NOT empty - it represents "operation completed, effect skipped"
      return OPTIONAL.widen(Optional.of(Unit.INSTANCE));
    }
  }

  /**
   * Optimised implementation of {@code ifS} for Optional. A ternary conditional operator for
   * selective functors.
   *
   * @param fcond A {@link Kind} representing {@code Optional<Boolean>}. Must not be null.
   * @param fthen A {@link Kind} representing {@code Optional<A>} for the true branch. Must not be
   *     null.
   * @param felse A {@link Kind} representing {@code Optional<A>} for the false branch. Must not be
   *     null.
   * @param <A> The type of the result.
   * @return A {@link Kind} representing {@code Optional<A>}. Never null.
   */
  @Override
  public <A> Kind<OptionalKind.Witness, A> ifS(
      Kind<OptionalKind.Witness, Boolean> fcond,
      Kind<OptionalKind.Witness, A> fthen,
      Kind<OptionalKind.Witness, A> felse) {

    Validation.kind().requireNonNull(fcond, OPTIONAL_SELECTIVE_CLASS, IF_S, "condition");
    Validation.kind().requireNonNull(fthen, OPTIONAL_SELECTIVE_CLASS, IF_S, "thenBranch");
    Validation.kind().requireNonNull(felse, OPTIONAL_SELECTIVE_CLASS, IF_S, "elseBranch");

    Optional<Boolean> condOptional = OPTIONAL.narrow(fcond);

    // Short-circuit on empty
    if (condOptional.isEmpty()) {
      return OPTIONAL.widen(Optional.empty());
    }

    boolean condition = condOptional.get();

    // Return the appropriate branch
    return condition ? fthen : felse;
  }
}
