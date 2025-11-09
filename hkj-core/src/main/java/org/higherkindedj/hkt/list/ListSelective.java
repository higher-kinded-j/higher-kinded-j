// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.list;

import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Implements the {@link Selective} type class for {@link List}. This provides selective applicative
 * operations that allow conditional execution based on the result of previous computations.
 *
 * <p>The Selective interface sits between {@link org.higherkindedj.hkt.Applicative} and {@link
 * org.higherkindedj.hkt.Monad} in terms of power. It allows for static analysis of effects while
 * still supporting conditional behavior.
 *
 * <p>For List, selective operations work with non-deterministic computations, where each element in
 * the list represents a possible outcome. The selective operations handle choices for each element
 * independently.
 *
 * <p><b>Unit Usage:</b> The {@link #whenS(Kind, Kind)} method now uses {@link Unit} to represent
 * skipped effects in the non-deterministic context. When a condition is false, {@code
 * Unit.INSTANCE} is added to the result list, making it explicit that "this branch was not taken"
 * rather than using null.
 *
 * <p>Key operations:
 *
 * <ul>
 *   <li>{@link #select(Kind, Kind)}: Conditionally applies functions to values based on Choice.
 *   <li>{@link #branch(Kind, Kind, Kind)}: Provides two-way conditional choice with different
 *       handlers.
 *   <li>{@link #whenS(Kind, Kind)}: Conditionally executes a Unit-returning effect based on a
 *       boolean.
 *   <li>{@link #ifS(Kind, Kind, Kind)}: Ternary conditional for selective functors.
 * </ul>
 *
 * @see List
 * @see ListMonad
 * @see Selective
 * @see Choice
 * @see Unit
 */
public final class ListSelective extends ListMonad implements Selective<ListKind.Witness> {

  /** Singleton instance of {@code ListSelective}. */
  public static final ListSelective INSTANCE = new ListSelective();

  private static final Class<ListSelective> LIST_SELECTIVE_CLASS = ListSelective.class;

  /** Private constructor to enforce singleton pattern. */
  private ListSelective() {
    super();
  }

  /**
   * The core selective operation for List. Given a list of choices and a list of functions, applies
   * each function to the corresponding {@code Left} value, or returns the {@code Right} value
   * directly.
   *
   * <p>This operation processes each element independently, collecting all results.
   *
   * <p>Behavior:
   *
   * <ul>
   *   <li>For each {@code Right(b)} in the choice list: Include {@code b} in the result.
   *   <li>For each {@code Left(a)} in the choice list: Apply each function from {@code ff} to
   *       {@code a}, adding all results to the output.
   * </ul>
   *
   * @param fab A {@link Kind} representing {@code List<Choice<A, B>>}. Must not be null.
   * @param ff A {@link Kind} representing {@code List<Function<A, B>>}. Must not be null.
   * @param <A> The input type of the function (the type inside {@code Left} of the Choice).
   * @param <B> The output type and the type inside {@code Right} of the Choice.
   * @return A {@link Kind} representing {@code List<B>}. Never null.
   * @throws NullPointerException if {@code fab} or {@code ff} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fab} or {@code ff} cannot
   *     be unwrapped.
   */
  @Override
  public <A, B> Kind<ListKind.Witness, B> select(
      Kind<ListKind.Witness, Choice<A, B>> fab, Kind<ListKind.Witness, Function<A, B>> ff) {

    Validation.kind().requireNonNull(fab, LIST_SELECTIVE_CLASS, SELECT, "choice");
    Validation.kind().requireNonNull(ff, LIST_SELECTIVE_CLASS, SELECT, "function");

    List<Choice<A, B>> choices = LIST.narrow(fab);
    List<Function<A, B>> functions = LIST.narrow(ff);

    if (choices.isEmpty()) {
      return LIST.widen(Collections.emptyList());
    }

    List<B> result = new ArrayList<>();

    for (Choice<A, B> choice : choices) {
      if (choice.isRight()) {
        // Already have the value, add it directly
        result.add(choice.getRight());
      } else {
        // Need to apply functions - non-deterministic application
        A value = choice.getLeft();
        if (functions.isEmpty()) {
          // No functions available, result is empty for this choice
          continue;
        }
        for (Function<A, B> func : functions) {
          result.add(func.apply(value));
        }
      }
    }

    return LIST.widen(result);
  }

  /**
   * Optimized implementation of {@code branch} for List. Provides a two-way conditional choice,
   * applying the appropriate handler based on whether each Choice is Left or Right.
   *
   * @param fab A {@link Kind} representing {@code List<Choice<A, B>>}. Must not be null.
   * @param fl A {@link Kind} representing {@code List<Function<A, C>>} for the Left case. Must not
   *     be null.
   * @param fr A {@link Kind} representing {@code List<Function<B, C>>} for the Right case. Must not
   *     be null.
   * @param <A> The type inside {@code Left} of the Choice.
   * @param <B> The type inside {@code Right} of the Choice.
   * @param <C> The result type.
   * @return A {@link Kind} representing {@code List<C>}. Never null.
   */
  @Override
  public <A, B, C> Kind<ListKind.Witness, C> branch(
      Kind<ListKind.Witness, Choice<A, B>> fab,
      Kind<ListKind.Witness, Function<A, C>> fl,
      Kind<ListKind.Witness, Function<B, C>> fr) {

    Validation.kind().requireNonNull(fab, LIST_SELECTIVE_CLASS, BRANCH, "choice");
    Validation.kind().requireNonNull(fl, LIST_SELECTIVE_CLASS, BRANCH, "leftHandler");
    Validation.kind().requireNonNull(fr, LIST_SELECTIVE_CLASS, BRANCH, "rightHandler");

    List<Choice<A, B>> choices = LIST.narrow(fab);
    List<Function<A, C>> leftFunctions = LIST.narrow(fl);
    List<Function<B, C>> rightFunctions = LIST.narrow(fr);

    if (choices.isEmpty()) {
      return LIST.widen(Collections.emptyList());
    }

    List<C> result = new ArrayList<>();

    for (Choice<A, B> choice : choices) {
      if (choice.isLeft()) {
        // Apply left handlers
        A value = choice.getLeft();
        if (leftFunctions.isEmpty()) {
          continue;
        }
        for (Function<A, C> func : leftFunctions) {
          result.add(func.apply(value));
        }
      } else {
        // Apply right handlers
        B value = choice.getRight();
        if (rightFunctions.isEmpty()) {
          continue;
        }
        for (Function<B, C> func : rightFunctions) {
          result.add(func.apply(value));
        }
      }
    }

    return LIST.widen(result);
  }

  /**
   * Optimized implementation of {@code whenS} for List. Conditionally executes a Unit-returning
   * effect based on boolean conditions.
   *
   * <p>For List (representing non-deterministic computation), this operation works as follows:
   *
   * <ul>
   *   <li>For each {@code true} condition: Includes all effect results from the effect list
   *   <li>For each {@code false} condition: Includes {@code Unit.INSTANCE} (operation skipped)
   * </ul>
   *
   * <p>This maintains the non-deterministic semantics of List while providing type-safe handling of
   * skipped effects.
   *
   * <p><b>Example:</b>
   *
   * <pre>{@code
   * List<Boolean> conditions = List.of(true, false, true);
   * List<Unit> effects = List.of(Unit.INSTANCE, Unit.INSTANCE);
   *
   * Kind<ListKind.Witness, Unit> result = listSelective.whenS(
   *     LIST.widen(conditions),
   *     LIST.widen(effects)
   * );
   * // Result list contains: [Unit.INSTANCE, Unit.INSTANCE, Unit.INSTANCE, Unit.INSTANCE]
   * // (2 effects for first true, 1 Unit for false, 2 effects for second true)
   * }</pre>
   *
   * @param fcond A {@link Kind} representing {@code List<Boolean>}. Must not be null.
   * @param fa A {@link Kind} representing {@code List<Unit>} to execute if condition is true. Must
   *     not be null.
   * @return A {@link Kind} representing {@code List<Unit>}. Never null.
   */
  @Override
  public Kind<ListKind.Witness, Unit> whenS(
      Kind<ListKind.Witness, Boolean> fcond, Kind<ListKind.Witness, Unit> fa) {

    Validation.kind().requireNonNull(fcond, LIST_SELECTIVE_CLASS, WHEN_S, "condition");
    Validation.kind().requireNonNull(fa, LIST_SELECTIVE_CLASS, WHEN_S, "effect");

    List<Boolean> conditions = LIST.narrow(fcond);
    List<Unit> effects = LIST.narrow(fa);

    if (conditions.isEmpty()) {
      return LIST.widen(Collections.emptyList());
    }

    List<Unit> result = new ArrayList<>();

    // For lists, we combine conditions with effects
    // Each true condition pairs with each effect value
    // Each false condition produces Unit.INSTANCE
    for (Boolean condition : conditions) {
      if (condition) {
        if (effects.isEmpty()) {
          continue;
        }
        // Add all effect values for this true condition
        result.addAll(effects);
      } else {
        // Condition is false, add Unit.INSTANCE
        result.add(Unit.INSTANCE);
      }
    }

    return LIST.widen(result);
  }

  /**
   * Optimized implementation of {@code ifS} for List. A ternary conditional operator for selective
   * functors.
   *
   * <p>For each condition, includes results from the then-branch if true, or from the else-branch
   * if false.
   *
   * @param fcond A {@link Kind} representing {@code List<Boolean>}. Must not be null.
   * @param fthen A {@link Kind} representing {@code List<A>} for the true branch. Must not be null.
   * @param felse A {@link Kind} representing {@code List<A>} for the false branch. Must not be
   *     null.
   * @param <A> The type of the result.
   * @return A {@link Kind} representing {@code List<A>}. Never null.
   */
  @Override
  public <A> Kind<ListKind.Witness, A> ifS(
      Kind<ListKind.Witness, Boolean> fcond,
      Kind<ListKind.Witness, A> fthen,
      Kind<ListKind.Witness, A> felse) {

    Validation.kind().requireNonNull(fcond, LIST_SELECTIVE_CLASS, IF_S, "condition");
    Validation.kind().requireNonNull(fthen, LIST_SELECTIVE_CLASS, IF_S, "thenBranch");
    Validation.kind().requireNonNull(felse, LIST_SELECTIVE_CLASS, IF_S, "elseBranch");

    List<Boolean> conditions = LIST.narrow(fcond);
    List<A> thenValues = LIST.narrow(fthen);
    List<A> elseValues = LIST.narrow(felse);

    if (conditions.isEmpty()) {
      return LIST.widen(Collections.emptyList());
    }

    List<A> result = new ArrayList<>();

    for (Boolean condition : conditions) {
      if (condition) {
        // Add all values from then-branch
        result.addAll(thenValues);
      } else {
        // Add all values from else-branch
        result.addAll(elseValues);
      }
    }

    return LIST.widen(result);
  }
}
