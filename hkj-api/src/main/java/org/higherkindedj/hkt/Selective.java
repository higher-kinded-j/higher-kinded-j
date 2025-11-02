// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.NullMarked;

/**
 * Represents the Selective Applicative Functor type class, an algebraic structure that lies between
 * {@link Applicative} and {@link Monad} in terms of power.
 *
 * <p>A Selective Functor extends {@link Applicative} with the ability to conditionally apply
 * effects based on the result of a previous computation. Unlike {@link Monad}, which allows
 * arbitrary dynamic choice of effects, Selective provides a more restricted form of conditional
 * execution where all possible branches must be provided upfront.
 *
 * <p>The key operation is {@link #select(Kind, Kind)}, which takes an {@code F<Choice<A, B>>} and
 * an {@code F<Function<A, B>>}, and returns an {@code F<B>}. If the first argument is a {@code
 * Left(a)}, the function from the second argument is applied to {@code a}. If it's a {@code
 * Right(b)}, the second argument is ignored and {@code b} is returned directly.
 *
 * <p><b>Unit Usage:</b> This interface makes extensive use of {@link Unit} to represent operations
 * that complete successfully but produce no interesting value. This is semantically different from
 * null and provides type safety.
 *
 * @param <F> The higher-kinded type witness representing the type constructor of the selective
 *     context (e.g., {@code OptionalKind.Witness}, {@code ListKind.Witness}).
 * @see Applicative
 * @see Monad
 * @see Kind
 * @see Unit
 */
@NullMarked
public interface Selective<F> extends Applicative<F> {

  /**
   * The core selective operation. Given an effectful choice {@code fab} and an effectful function
   * {@code ff}, applies the function only if the choice is a {@code Left}.
   *
   * <p>If {@code fab} contains {@code Right(b)}, the result is {@code F<b>} and {@code ff} is not
   * evaluated. If {@code fab} contains {@code Left(a)}, the function from {@code ff} is applied to
   * {@code a} to produce {@code F<b>}.
   *
   * <p>This is the fundamental operation that distinguishes Selective from Applicative. Unlike
   * {@code ap}, which always applies the function, {@code select} conditionally applies based on
   * the result.
   *
   * @param fab A non-null {@link Kind Kind&lt;F, Choice&lt;A, B&gt;&gt;} representing an effectful
   *     conditional value.
   * @param ff A non-null {@link Kind Kind&lt;F, Function&lt;A, B&gt;&gt;} representing an effectful
   *     function to apply if {@code fab} is {@code Left}.
   * @param <A> The input type of the function (the type inside {@code Left}).
   * @param <B> The output type and the type inside {@code Right}.
   * @return A non-null {@link Kind Kind&lt;F, B&gt;} representing the result after selective
   *     application.
   */
  <A, B> Kind<F, B> select(Kind<F, Choice<A, B>> fab, Kind<F, Function<A, B>> ff);

  /**
   * A selective version of branching (if-then-else). Given a {@code Choice<A, B>} and handlers for
   * both cases, applies the appropriate handler based on the result.
   *
   * <p>This is a derived operation that can be defined in terms of {@link #select(Kind, Kind)}.
   *
   * @param fab A non-null {@link Kind Kind&lt;F, Choice&lt;A, B&gt;&gt;} representing an effectful
   *     conditional value.
   * @param fl A non-null {@link Kind Kind&lt;F, Function&lt;A, C&gt;&gt;} for handling the {@code
   *     Left} case.
   * @param fr A non-null {@link Kind Kind&lt;F, Function&lt;B, C&gt;&gt;} for handling the {@code
   *     Right} case.
   * @param <A> The type inside {@code Left}.
   * @param <B> The type inside {@code Right}.
   * @param <C> The result type.
   * @return A non-null {@link Kind Kind&lt;F, C&gt;} representing the result after applying the
   *     appropriate handler.
   */
  default <A, B, C> Kind<F, C> branch(
      Kind<F, Choice<A, B>> fab, Kind<F, Function<A, C>> fl, Kind<F, Function<B, C>> fr) {
    requireNonNull(fab, "Kind<F, Choice<A, B>> fab for branch cannot be null");
    requireNonNull(fl, "Kind<F, Function<A, C>> fl for branch cannot be null");
    requireNonNull(fr, "Kind<F, Function<B, C>> fr for branch cannot be null");

    Kind<F, Choice<A, Choice<B, C>>> transformed =
        map(
            choice ->
                choice.isLeft()
                    ? Selective.left(choice.getLeft()) // Use Selective.left()
                    : Selective.right(
                        Selective.left(choice.getRight())), // Use Selective.right() and left()
            fab);

    Kind<F, Function<A, Choice<B, C>>> leftHandler =
        map(
            f -> (Function<A, Choice<B, C>>) a -> Selective.right(f.apply(a)),
            fl); // Use Selective.right()

    Kind<F, Choice<B, C>> intermediate = select(transformed, leftHandler);

    return select(intermediate, fr);
  }

  /**
   * Conditionally performs a Unit-returning effect based on a boolean condition.
   *
   * <p>If the condition is {@code true}, the effect {@code fa} is executed and its result is
   * returned. If the condition is {@code false}, {@code fa} is not executed and {@code
   * of(Unit.INSTANCE)} is returned.
   *
   * <p>This is the primary conditional effect execution operator for Selective functors. It
   * explicitly uses {@link Unit} to represent "operation completed with no interesting result",
   * which is semantically clearer than returning null.
   *
   * <p><b>Semantics:</b>
   *
   * <ul>
   *   <li>Condition true: Execute effect, return Unit
   *   <li>Condition false: Skip effect, return Unit.INSTANCE
   *   <li>Condition error: Propagate error (context-dependent)
   * </ul>
   *
   * <p><b>Example:</b>
   *
   * <pre>{@code
   * // Log only if debug mode is enabled
   * Kind<IO.Witness, Boolean> debugEnabled = IO.of(Config.isDebugMode());
   * Kind<IO.Witness, Unit> logEffect = IO.fromRunnable(() -> log.debug("Debug info"));
   * Kind<IO.Witness, Unit> maybeLog = selective.whenS(debugEnabled, logEffect);
   * }</pre>
   *
   * @param fcond A non-null {@link Kind Kind&lt;F, Boolean&gt;} representing an effectful boolean
   *     condition.
   * @param fa A non-null {@link Kind Kind&lt;F, Unit&gt;} representing the effect to execute if the
   *     condition is true. Must return Unit.
   * @return A non-null {@link Kind Kind&lt;F, Unit&gt;} representing the result. If the condition
   *     was false, returns {@code of(Unit.INSTANCE)}.
   */
  default Kind<F, Unit> whenS(Kind<F, Boolean> fcond, Kind<F, Unit> fa) {
    requireNonNull(fcond, "Kind<F, Boolean> fcond for whenS cannot be null");
    requireNonNull(fa, "Kind<F, Unit> fa for whenS cannot be null");

    // Transform Boolean to Choice<Unit, Unit>
    // If true: Left(unit) - need to execute fa to get the Unit result
    // If false: Right(Unit.INSTANCE) - skip fa, use Unit.INSTANCE directly
    Kind<F, Choice<Unit, Unit>> condition =
        map2(
            fa,
            fcond,
            (unitFromEffect, conditionValue) ->
                conditionValue ? Selective.left(unitFromEffect) : Selective.right(Unit.INSTANCE));

    // Identity function for Unit
    Kind<F, Function<Unit, Unit>> identity = of(u -> Unit.INSTANCE);

    return select(condition, identity);
  }

  /**
   * Convenience method that wraps an effect to return Unit before conditional execution.
   *
   * <p>This is equivalent to: {@code whenS(fcond, map(a -> Unit.INSTANCE, fa))}
   *
   * <p>Use this when you have an effect that returns a value, but you want to discard that value
   * and treat it as a Unit-returning operation. This is useful for side-effecting operations where
   * the return value is not interesting.
   *
   * <p><b>Example:</b>
   *
   * <pre>{@code
   * // Write to database (returns row count, but we don't care)
   * Kind<IO.Witness, Integer> writeResult = database.write(data);
   * Kind<IO.Witness, Boolean> shouldWrite = IO.of(config.shouldPersist());
   *
   * // Discard the Integer result, treat as Unit
   * Kind<IO.Witness, Unit> maybeWrite = selective.whenS_(shouldWrite, writeResult);
   * }</pre>
   *
   * @param fcond The non-null effectful condition
   * @param fa The non-null effect to execute (result will be discarded)
   * @param <A> The type of fa's result (will be discarded)
   * @return Kind<F, Unit> representing the effect execution with result discarded
   */
  default <A> Kind<F, Unit> whenS_(Kind<F, Boolean> fcond, Kind<F, A> fa) {
    requireNonNull(fcond, "condition cannot be null");
    requireNonNull(fa, "effect cannot be null");
    return whenS(fcond, map(a -> Unit.INSTANCE, fa));
  }

  /**
   * A ternary conditional operator for selective functors. If the condition is {@code true},
   * returns the result of {@code fthen}, otherwise returns the result of {@code felse}.
   *
   * <p>Unlike a monadic bind, both {@code fthen} and {@code felse} are visible upfront, allowing
   * for static analysis and potentially parallel execution.
   *
   * @param fcond A non-null {@link Kind Kind&lt;F, Boolean&gt;} representing an effectful boolean
   *     condition.
   * @param fthen A non-null {@link Kind Kind&lt;F, A&gt;} representing the effect to execute if the
   *     condition is true.
   * @param felse A non-null {@link Kind Kind&lt;F, A&gt;} representing the effect to execute if the
   *     condition is false.
   * @param <A> The type of the result.
   * @return A non-null {@link Kind Kind&lt;F, A&gt;} representing the result of the selected
   *     branch.
   */
  default <A> Kind<F, A> ifS(Kind<F, Boolean> fcond, Kind<F, A> fthen, Kind<F, A> felse) {
    requireNonNull(fcond, "Kind<F, Boolean> fcond for ifS cannot be null");
    requireNonNull(fthen, "Kind<F, A> fthen for ifS cannot be null");
    requireNonNull(felse, "Kind<F, A> felse for ifS cannot be null");

    Kind<F, Choice<A, A>> eitherChoice =
        map3(
            fthen,
            felse,
            fcond,
            (thenVal, elseVal, b) ->
                b
                    ? Selective.left(thenVal) // Use Selective.left()
                    : Selective.right(elseVal)); // Use Selective.right()

    Kind<F, Function<A, A>> identity = of(a -> a);

    return select(eitherChoice, identity);
  }

  /**
   * Returns the first successful value from a list of alternatives.
   *
   * @param alternatives A non-null {@link java.util.List} of {@link Kind Kind&lt;F, Choice&lt;E,
   *     A&gt;&gt;} representing alternative computations.
   * @param <E> The error type (type inside {@code Left}).
   * @param <A> The success type (type inside {@code Right}).
   * @return A non-null {@link Kind Kind&lt;F, Choice&lt;E, A&gt;&gt;} representing the first
   *     successful alternative, or the last error if all fail.
   */
  default <E, A> Kind<F, Choice<E, A>> orElse(java.util.List<Kind<F, Choice<E, A>>> alternatives) {
    requireNonNull(alternatives, "List of alternatives for orElse cannot be null");
    if (alternatives.isEmpty()) {
      throw new IllegalArgumentException("orElse requires at least one alternative");
    }

    Kind<F, Choice<E, A>> result = alternatives.get(0);
    for (int i = 1; i < alternatives.size(); i++) {
      Kind<F, Choice<E, A>> next = alternatives.get(i);
      result = selectOrElse(result, next);
    }
    return result;
  }

  /** Helper method for {@link #orElse(java.util.List)}. */
  private <E, A> Kind<F, Choice<E, A>> selectOrElse(
      Kind<F, Choice<E, A>> first, Kind<F, Choice<E, A>> second) {
    Kind<F, Choice<E, Choice<E, A>>> transformed =
        map(
            choice ->
                choice.isRight()
                    ? Selective.right(
                        Selective.right(choice.getRight())) // Use Selective.right() twice
                    : Selective.left(choice.getLeft()), // Use Selective.left()
            first);

    Kind<F, Function<E, Choice<E, A>>> getSecond =
        map(choiceA -> (Function<E, Choice<E, A>>) e -> choiceA, second);

    return select(transformed, getSecond);
  }

  /**
   * Applies multiple effectful functions in sequence, each depending on whether the previous
   * computation succeeded or failed.
   *
   * @param initial A non-null {@link Kind Kind&lt;F, Choice&lt;E, A&gt;&gt;} representing the
   *     initial value.
   * @param functions A non-null {@link java.util.List} of {@link Kind Kind&lt;F, Function&lt;A,
   *     Choice&lt;E, A&gt;&gt;} representing the functions to apply in sequence.
   * @param <E> The error type.
   * @param <A> The value type.
   * @return A non-null {@link Kind Kind&lt;F, Choice&lt;E, A&gt;&gt;} representing the result after
   *     applying all functions, or the first error encountered.
   */
  default <E, A> Kind<F, Choice<E, A>> apS(
      Kind<F, Choice<E, A>> initial, List<Kind<F, Function<A, Choice<E, A>>>> functions) {
    requireNonNull(initial, "Initial value for apS cannot be null");
    requireNonNull(functions, "List of functions for apS cannot be null");

    Kind<F, Choice<E, A>> result = initial;
    for (Kind<F, Function<A, Choice<E, A>>> func : functions) {
      Kind<F, Choice<A, Choice<E, A>>> transformed =
          map(
              choice ->
                  choice.isRight()
                      ? Selective.left(choice.getRight()) // Use Selective.left()
                      : Selective.right(
                          Selective.left(choice.getLeft())), // Use Selective.right() and left()
              result);

      Kind<F, Choice<E, A>> applied = select(transformed, func);
      result = applied;
    }
    return result;
  }

  /**
   * Creates a Choice representing a Left value, with Unit on the right side.
   *
   * <p>This is a convenience factory method for creating left-biased choices without needing to
   * specify a value for the unused right side.
   *
   * <p><b>Example:</b>
   *
   * <pre>{@code
   * Choice<String, Integer> left = Selective.left("error");
   * // Equivalent to: new SimpleChoice<>(true, "error", Unit.INSTANCE)
   * }</pre>
   *
   * @param leftValue The value for the left side
   * @param <L> The left type
   * @param <R> The right type
   * @return A Choice representing a Left value
   */
  static <L, R> Choice<L, R> left(L leftValue) {
    return SimpleChoice.left(leftValue);
  }

  /**
   * Creates a Choice representing a Right value, with Unit on the left side.
   *
   * <p>This is a convenience factory method for creating right-biased choices without needing to
   * specify a value for the unused left side.
   *
   * <p><b>Example:</b>
   *
   * <pre>{@code
   * Choice<String, Integer> right = Selective.right(42);
   * // Equivalent to: new SimpleChoice<>(false, Unit.INSTANCE, 42)
   * }</pre>
   *
   * @param rightValue The value for the right side
   * @param <L> The left type
   * @param <R> The right type
   * @return A Choice representing a Right value
   */
  static <L, R> Choice<L, R> right(R rightValue) {
    return SimpleChoice.right(rightValue);
  }

  /** Simple implementation of Choice for use in default methods. */
  final class SimpleChoice<L, R> implements Choice<L, R> {
    private final boolean isLeft;
    private final L left;
    private final R right;

    /**
     * Package-private constructor for SimpleChoice. Users should prefer the static factory methods
     * {@link Selective#left(Object)} and {@link Selective#right(Object)} for type safety and
     * correct Unit handling.
     *
     * @param isLeft true if this represents a Left value
     * @param left the left value (should be non-null if isLeft is true)
     * @param right the right value (should be non-null if isLeft is false)
     */
    SimpleChoice(boolean isLeft, L left, R right) {
      this.isLeft = isLeft;
      this.left = left;
      this.right = right;
    }

    /**
     * Factory method to create a Left Choice with Unit on the right. Prefer using {@link
     * Selective#left(Object)} instead.
     */
    @SuppressWarnings("unchecked")
    static <L, R> SimpleChoice<L, R> left(L leftValue) {
      return new SimpleChoice<>(true, leftValue, (R) Unit.INSTANCE);
    }

    /**
     * Factory method to create a Right Choice with Unit on the left. Prefer using {@link
     * Selective#right(Object)} instead.
     */
    @SuppressWarnings("unchecked")
    static <L, R> SimpleChoice<L, R> right(R rightValue) {
      return new SimpleChoice<>(false, (L) Unit.INSTANCE, rightValue);
    }

    @Override
    public boolean isLeft() {
      return isLeft;
    }

    @Override
    public boolean isRight() {
      return !isLeft;
    }

    @Override
    public L getLeft() {
      if (!isLeft) throw new java.util.NoSuchElementException("Not a left value");
      return left;
    }

    @Override
    public R getRight() {
      if (isLeft) throw new java.util.NoSuchElementException("Not a right value");
      return right;
    }

    @Override
    public <T> T fold(
        Function<? super L, ? extends T> leftMapper, Function<? super R, ? extends T> rightMapper) {
      return isLeft ? leftMapper.apply(left) : rightMapper.apply(right);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R2> Choice<L, R2> map(Function<? super R, ? extends R2> mapper) {
      return isLeft
          ? new SimpleChoice<>(true, left, (R2) Unit.INSTANCE)
          : new SimpleChoice<>(false, (L) Unit.INSTANCE, mapper.apply(right));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <L2> Choice<L2, R> mapLeft(Function<? super L, ? extends L2> mapper) {
      return isLeft
          ? new SimpleChoice<>(true, mapper.apply(left), (R) Unit.INSTANCE)
          : new SimpleChoice<>(false, (L2) Unit.INSTANCE, right);
    }

    @Override
    public Choice<R, L> swap() {
      return new SimpleChoice<>(!isLeft, right, left);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R2> Choice<L, R2> flatMap(Function<? super R, ? extends Choice<L, R2>> mapper) {
      if (isLeft) {
        return new SimpleChoice<>(true, left, (R2) Unit.INSTANCE);
      }
      Choice<L, R2> result = mapper.apply(right);
      return result.isLeft()
          ? new SimpleChoice<>(true, result.getLeft(), (R2) Unit.INSTANCE)
          : new SimpleChoice<>(false, (L) Unit.INSTANCE, result.getRight());
    }
  }
}
