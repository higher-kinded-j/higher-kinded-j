// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.id;

import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Implements the {@link Selective} type class for {@link Id}.
 *
 * <p>The Identity selective is the simplest selective functor. Since Id has no effects or branching
 * logic, all conditional operations eagerly evaluate and simply unwrap the values.
 *
 * <p>Key operations:
 *
 * <ul>
 *   <li>{@link #select(Kind, Kind)}: Conditionally applies a function based on a Choice.
 *   <li>{@link #branch(Kind, Kind, Kind)}: Provides two-way conditional choice with different
 *       handlers.
 *   <li>{@link #whenS(Kind, Kind)}: Conditionally executes an effect based on a boolean.
 *   <li>{@link #ifS(Kind, Kind, Kind)}: Ternary conditional for selective functors.
 * </ul>
 *
 * <p>Since Id has no effects, all operations evaluate eagerly and immediately return results.
 *
 * <p>This class is a singleton, accessible via {@link #instance()}.
 *
 * @see Id
 * @see IdMonad
 * @see Selective
 * @see Choice
 */
public final class IdSelective extends IdMonad implements Selective<IdKind.Witness> {

  private static final IdSelective INSTANCE = new IdSelective();
  private static final Class<IdSelective> ID_SELECTIVE_CLASS = IdSelective.class;

  /** Private constructor to enforce singleton pattern. */
  private IdSelective() {
    super();
  }

  /**
   * Returns the singleton instance of {@link IdSelective}.
   *
   * @return The singleton {@code IdSelective} instance.
   */
  public static IdSelective instance() {
    return INSTANCE;
  }

  /**
   * The core selective operation for Id.
   *
   * <p>Since Id has no effects, this eagerly evaluates the choice. If the choice contains a Left
   * value, the function is applied. If it contains a Right value, that value is returned directly.
   *
   * <p>Behavior:
   *
   * <ul>
   *   <li>If choice is {@code Right(b)}: Returns {@code Id(b)}, function is not evaluated.
   *   <li>If choice is {@code Left(a)}: Applies the function to {@code a} and returns {@code
   *       Id(f(a))}.
   * </ul>
   *
   * @param fab A non-null {@link Kind Kind&lt;IdKind.Witness, Choice&lt;A, B&gt;&gt;} containing
   *     the choice
   * @param ff A non-null {@link Kind Kind&lt;IdKind.Witness, Function&lt;A, B&gt;&gt;} containing
   *     the function
   * @param <A> The input type of the function (the type inside Left)
   * @param <B> The output type and the type inside Right
   * @return A non-null {@link Kind Kind&lt;IdKind.Witness, B&gt;} with the result
   * @throws NullPointerException if {@code fab} or {@code ff} is null, or if the unwrapped values
   *     are null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fab} or {@code ff} cannot
   *     be unwrapped.
   */
  @Override
  public <A, B> Kind<IdKind.Witness, B> select(
      Kind<IdKind.Witness, Choice<A, B>> fab, Kind<IdKind.Witness, Function<A, B>> ff) {

    Validation.kind().requireNonNull(fab, ID_SELECTIVE_CLASS, SELECT, "choice");
    Validation.kind().requireNonNull(ff, ID_SELECTIVE_CLASS, SELECT, "function");

    Choice<A, B> choice = ID.narrow(fab).value();
    Function<A, B> function = ID.narrow(ff).value();

    Validation.function().requireFunction(choice, "choice", ID_SELECTIVE_CLASS, SELECT);
    Validation.function().requireFunction(function, "function", ID_SELECTIVE_CLASS, SELECT);

    // If choice is Right(b), we already have our value
    if (choice.isRight()) {
      return ID.widen(Id.of(choice.getRight()));
    }

    // Choice is Left(a), so apply the function
    A value = choice.getLeft();
    B result = function.apply(value);

    return ID.widen(Id.of(result));
  }

  /**
   * Selective branching for Id.
   *
   * <p>Eagerly evaluates the choice and applies the appropriate handler.
   *
   * @param fab A {@link Kind} representing {@code Id<Choice<A, B>>}. Must not be null.
   * @param fl A {@link Kind} representing {@code Id<Function<A, C>>} for the Left case. Must not be
   *     null.
   * @param fr A {@link Kind} representing {@code Id<Function<B, C>>} for the Right case. Must not
   *     be null.
   * @param <A> The type inside {@code Left} of the Choice.
   * @param <B> The type inside {@code Right} of the Choice.
   * @param <C> The result type.
   * @return A {@link Kind} representing {@code Id<C>}. Never null.
   * @throws NullPointerException if any parameter is null, or if unwrapped values are null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if parameters cannot be unwrapped.
   */
  @Override
  public <A, B, C> Kind<IdKind.Witness, C> branch(
      Kind<IdKind.Witness, Choice<A, B>> fab,
      Kind<IdKind.Witness, Function<A, C>> fl,
      Kind<IdKind.Witness, Function<B, C>> fr) {

    Validation.kind().requireNonNull(fab, ID_SELECTIVE_CLASS, BRANCH, "choice");
    Validation.kind().requireNonNull(fl, ID_SELECTIVE_CLASS, BRANCH, "leftHandler");
    Validation.kind().requireNonNull(fr, ID_SELECTIVE_CLASS, BRANCH, "rightHandler");

    Choice<A, B> choice = ID.narrow(fab).value();
    Validation.function().requireFunction(choice, "choice", ID_SELECTIVE_CLASS, BRANCH);

    if (choice.isLeft()) {
      // Use left handler
      Function<A, C> leftFunction = ID.narrow(fl).value();
      Validation.function()
          .requireFunction(leftFunction, "leftHandler", ID_SELECTIVE_CLASS, BRANCH);
      C result = leftFunction.apply(choice.getLeft());
      return ID.widen(Id.of(result));
    } else {
      // Use right handler
      Function<B, C> rightFunction = ID.narrow(fr).value();
      Validation.function()
          .requireFunction(rightFunction, "rightHandler", ID_SELECTIVE_CLASS, BRANCH);
      C result = rightFunction.apply(choice.getRight());
      return ID.widen(Id.of(result));
    }
  }

  /**
   * Conditionally executes a Unit-returning effect based on a boolean condition.
   *
   * <p>Eagerly evaluates the condition. If true, validates and returns the effect's Unit result. If
   * false, returns Unit.INSTANCE without evaluating the effect.
   *
   * <p>Key improvement: Returns {@code Id.of(Unit.INSTANCE)} instead of {@code Id.of(null)}, making
   * the "no-op" case explicit and type-safe.
   *
   * <p><b>Validation:</b> When the condition is true, the Unit value inside the effect is validated
   * to ensure it's not null. When the condition is false, the effect is not evaluated or validated.
   *
   * @param fcond A {@link Kind} representing {@code Id<Boolean>}. Must not be null.
   * @param fa A {@link Kind} representing {@code Id<Unit>} the effect to execute if condition is
   *     true. Must not be null, and the Unit value must not be null if the condition is true.
   * @return A {@link Kind} representing {@code Id<Unit>}. Never null.
   * @throws NullPointerException if any parameter is null, or if unwrapped values are null when
   *     accessed.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if parameters cannot be unwrapped.
   */
  @Override
  public Kind<IdKind.Witness, Unit> whenS(
      Kind<IdKind.Witness, Boolean> fcond, Kind<IdKind.Witness, Unit> fa) {

    Validation.kind().requireNonNull(fcond, ID_SELECTIVE_CLASS, WHEN_S, "condition");
    Validation.kind().requireNonNull(fa, ID_SELECTIVE_CLASS, WHEN_S, "effect");

    Boolean condition = ID.narrow(fcond).value();
    Validation.function().requireFunction(condition, "condition", ID_SELECTIVE_CLASS, WHEN_S);

    if (condition) {
      // Execute and return the effect - but validate its Unit value first
      Unit unit = ID.narrow(fa).value();
      Validation.function().requireFunction(unit, "effect", ID_SELECTIVE_CLASS, WHEN_S);
      return fa;
    } else {
      // Condition is false, return Unit wrapped in Id (not null!)
      return ID.widen(Id.of(Unit.INSTANCE));
    }
  }

  /**
   * A ternary conditional operator for Id selective functors.
   *
   * <p>Eagerly evaluates the condition and returns the appropriate branch without evaluating the
   * other.
   *
   * @param fcond A {@link Kind} representing {@code Id<Boolean>}. Must not be null.
   * @param fthen A {@link Kind} representing {@code Id<A>} for the true branch. Must not be null.
   * @param felse A {@link Kind} representing {@code Id<A>} for the false branch. Must not be null.
   * @param <A> The type of the result.
   * @return A {@link Kind} representing {@code Id<A>}. Never null.
   * @throws NullPointerException if any parameter is null, or if the condition value is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if parameters cannot be unwrapped.
   */
  @Override
  public <A> Kind<IdKind.Witness, A> ifS(
      Kind<IdKind.Witness, Boolean> fcond,
      Kind<IdKind.Witness, A> fthen,
      Kind<IdKind.Witness, A> felse) {

    Validation.kind().requireNonNull(fcond, ID_SELECTIVE_CLASS, IF_S, "condition");
    Validation.kind().requireNonNull(fthen, ID_SELECTIVE_CLASS, IF_S, "thenBranch");
    Validation.kind().requireNonNull(felse, ID_SELECTIVE_CLASS, IF_S, "elseBranch");

    Boolean condition = ID.narrow(fcond).value();
    Validation.function().requireFunction(condition, "condition", ID_SELECTIVE_CLASS, IF_S);

    // Return the appropriate branch
    // Note: We don't evaluate both branches - this is key for selective functors
    return condition ? fthen : felse;
  }

  @Override
  public String toString() {
    return "IdSelective";
  }
}
