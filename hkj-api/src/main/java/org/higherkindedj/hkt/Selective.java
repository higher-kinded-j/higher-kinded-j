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
 * <p>The key operation is {@link #select(Kind, Kind)}, which takes an {@code F<Either<A, B>>} and
 * an {@code F<Function<A, B>>}, and returns an {@code F<B>}. If the first argument is a {@code
 * Left(a)}, the function from the second argument is applied to {@code a}. If it's a {@code
 * Right(b)}, the second argument is ignored and {@code b} is returned directly.
 *
 * <p>Key properties and operations:
 *
 * <ul>
 *   <li>It extends {@link Applicative}, so it provides {@code of}, {@code ap}, and {@code map}.
 *   <li>{@link #select(Kind, Kind)}: Conditionally applies an effectful function based on the
 *       result of a previous computation.
 *   <li>{@link #branch(Kind, Kind, Kind)}: Provides a two-way conditional choice, allowing
 *       different handlers for {@code Left} and {@code Right} cases.
 *   <li>{@link #whenS(Kind, Kind)}: Conditionally performs an effect only if a boolean condition is
 *       true.
 *   <li>{@link #ifS(Kind, Kind, Kind)}: A ternary conditional operator for selective functors.
 * </ul>
 *
 * <p>Selective functors must satisfy certain laws:
 *
 * <pre>
 * 1. Identity:     select(of(Right(x)), f) == of(x)
 * 2. Distributivity: select(x, of(f)) == select(x.map(e -> e.map(f)), of(identity))
 * 3. Associativity: select(select(x, f), g) == select(x, select(f.map(distribute), g))
 *                   (where distribute :: (a -> c) -> Either a (b -> c) -> Either (a, b) c)
 * </pre>
 *
 * <p>Selective functors are useful for:
 *
 * <ul>
 *   <li>Static analysis: All effects are visible upfront, unlike with Monads
 *   <li>Validation with early termination: Can skip unnecessary effects
 *   <li>Speculative execution: Can run both branches in parallel and select the result
 *   <li>Conditional effects without full monadic power
 * </ul>
 *
 * <p><b>Examples:</b>
 *
 * <pre>{@code
 * // Using whenS for conditional effects
 * Selective<F> selective = ...;
 * Kind<F, Boolean> condition = ...;
 * Kind<F, Unit> effect = ...;
 * Kind<F, Unit> result = selective.whenS(condition, effect);
 *
 * // Using ifS for ternary conditional
 * Kind<F, Boolean> condition = ...;
 * Kind<F, String> thenBranch = ...;
 * Kind<F, String> elseBranch = ...;
 * Kind<F, String> result = selective.ifS(condition, thenBranch, elseBranch);
 *
 * // Using branch for Either-based conditionals
 * Kind<F, Either<A, B>> either = ...;
 * Kind<F, Function<A, C>> leftHandler = ...;
 * Kind<F, Function<B, C>> rightHandler = ...;
 * Kind<F, C> result = selective.branch(either, leftHandler, rightHandler);
 * }</pre>
 *
 * @param <F> The higher-kinded type witness representing the type constructor of the selective
 *     context (e.g., {@code OptionalKind.Witness}, {@code ListKind.Witness}).
 * @see Applicative
 * @see Monad
 * @see Kind
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
     * <p>Example:
     *
     * <pre>{@code
     * // Selective validation: only fetch details if ID is present
     * Kind<F, Choice<UserId, User>> maybeUserId = ...; // Left(id) or Right(user)
     * Kind<F, Function<UserId, User>> fetchUser = ...;
     * Kind<F, User> result = selective.select(maybeUserId, fetchUser);
     * // fetchUser is only executed if maybeUserId is Left
     * }</pre>
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
     * <p>Example:
     *
     * <pre>{@code
     * Kind<F, Choice<Error, Success>> result = ...;
     * Kind<F, Function<Error, Response>> handleError = ...;
     * Kind<F, Function<Success, Response>> handleSuccess = ...;
     * Kind<F, Response> response = selective.branch(result, handleError, handleSuccess);
     * }</pre>
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

        // The default implementation uses map and select operations.
        // Implementations may override this for better efficiency.
        // Step 1: Transform Choice<A, B> into Choice<A, Choice<B, C>> by mapping Right values to Left
        Kind<F, Choice<A, Choice<B, C>>> transformed =
                map(
                        choice ->
                                choice.isLeft()
                                        ? new SimpleChoice<>(true, choice.getLeft(), null)
                                        : new SimpleChoice<>(
                                        false, null, new SimpleChoice<>(true, choice.getRight(), null)),
                        fab);

        // Step 2: Create a function that handles A -> Choice<B, C> by applying fl
        Kind<F, Function<A, Choice<B, C>>> leftHandler =
                map(f -> (Function<A, Choice<B, C>>) a -> new SimpleChoice<>(false, null, f.apply(a)), fl);

        // Step 3: First select - resolves the outer choice
        Kind<F, Choice<B, C>> intermediate = select(transformed, leftHandler);

        // Step 4: Second select - resolves the inner choice with right handler
        return select(intermediate, fr);
    }

    /**
     * Simple implementation of Choice for use in default methods. This is a minimal, non-public
     * implementation used internally by the Selective interface.
     */
    final class SimpleChoice<L, R> implements Choice<L, R> {
        private final boolean isLeft;
        private final L left;
        private final R right;

        public SimpleChoice(boolean isLeft, L left, R right) {
            this.isLeft = isLeft;
            this.left = left;
            this.right = right;
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
        public <R2> Choice<L, R2> map(Function<? super R, ? extends R2> mapper) {
            return isLeft
                    ? new SimpleChoice<>(true, left, null)
                    : new SimpleChoice<>(false, null, mapper.apply(right));
        }

        @Override
        public <L2> Choice<L2, R> mapLeft(Function<? super L, ? extends L2> mapper) {
            return isLeft
                    ? new SimpleChoice<>(true, mapper.apply(left), null)
                    : new SimpleChoice<>(false, null, right);
        }

        @Override
        public Choice<R, L> swap() {
            return new SimpleChoice<>(!isLeft, right, left);
        }

        @Override
        public <R2> Choice<L, R2> flatMap(Function<? super R, ? extends Choice<L, R2>> mapper) {
            if (isLeft) {
                return new SimpleChoice<>(true, left, null);
            }
            Choice<L, R2> result = mapper.apply(right);
            return result.isLeft()
                    ? new SimpleChoice<>(true, result.getLeft(), null)
                    : new SimpleChoice<>(false, null, result.getRight());
        }
    }

    /**
     * Conditionally performs an effect based on a boolean condition. If the condition is {@code
     * true}, the effect {@code fa} is executed and its result is returned. If the condition is {@code
     * false}, {@code fa} is not executed and {@code of(null)} is returned.
     *
     * <p>This is useful for performing side effects only when a condition is met.
     *
     * <p>Example:
     *
     * <pre>{@code
     * Kind<F, Boolean> shouldLog = ...;
     * Kind<F, Unit> logEffect = ...;
     * Kind<F, Unit> result = selective.whenS(shouldLog, logEffect);
     * // logEffect is only executed if shouldLog is true
     * }</pre>
     *
     * @param fcond A non-null {@link Kind Kind&lt;F, Boolean&gt;} representing an effectful boolean
     *     condition.
     * @param fa A non-null {@link Kind Kind&lt;F, A&gt;} representing the effect to execute if the
     *     condition is true.
     * @param <A> The type of the effect's result.
     * @return A non-null {@link Kind Kind&lt;F, A&gt;} representing the result. If the condition was
     *     false, returns {@code of(null)} which may represent a unit type.
     */
    @SuppressWarnings("unchecked")
    default <A> Kind<F, A> whenS(Kind<F, Boolean> fcond, Kind<F, A> fa) {
        requireNonNull(fcond, "Kind<F, Boolean> fcond for whenS cannot be null");
        requireNonNull(fa, "Kind<F, A> fa for whenS cannot be null");

        // Transform Boolean to Choice<Unit, A>
        // If true: Left(unit) - need to execute fa to get the value
        // If false: Right(null) - skip fa, use null as result
        Kind<F, Choice<A, A>> condition =
                map2(
                        fa,
                        fcond,
                        (aVal, b) ->
                                b
                                        ? new SimpleChoice<A, A>(true, aVal, null) // Left - will select from fa
                                        : new SimpleChoice<A, A>(false, null, null) // Right - skip fa
                );

        // Create identity function to handle the Left case
        Kind<F, Function<A, A>> identity = of(a -> a);

        return select(condition, identity);
    }

    /**
     * A ternary conditional operator for selective functors. If the condition is {@code true},
     * returns the result of {@code fthen}, otherwise returns the result of {@code felse}.
     *
     * <p>Unlike a monadic bind, both {@code fthen} and {@code felse} are visible upfront, allowing
     * for static analysis and potentially parallel execution.
     *
     * <p>Example:
     *
     * <pre>{@code
     * Kind<F, Boolean> isValid = ...;
     * Kind<F, String> successMessage = ...;
     * Kind<F, String> errorMessage = ...;
     * Kind<F, String> result = selective.ifS(isValid, successMessage, errorMessage);
     * }</pre>
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

        // Create a Choice<A, A> based on the condition
        // If true: Left(thenVal) - select from fthen
        // If false: Right(elseVal) - select from felse
        Kind<F, Choice<A, A>> eitherChoice =
                map3(
                        fthen,
                        felse,
                        fcond,
                        (thenVal, elseVal, b) ->
                                b
                                        ? new SimpleChoice<A, A>(true, thenVal, null)
                                        : new SimpleChoice<A, A>(false, null, elseVal));

        // Create identity function to handle Left case
        Kind<F, Function<A, A>> identity = of(a -> a);

        return select(eitherChoice, identity);
    }

    /**
     * Returns the first successful value from a list of alternatives. This is useful for implementing
     * "try this, else try that" patterns.
     *
     * <p>Each element in the list is attempted in order until one succeeds (returns a {@code Right}),
     * or until all fail (all return {@code Left}).
     *
     * <p>Example:
     *
     * <pre>{@code
     * // Try multiple data sources
     * List<Kind<F, Choice<Error, Data>>> sources = List.of(
     *   tryPrimaryDatabase,
     *   trySecondaryDatabase,
     *   tryCache
     * );
     * Kind<F, Choice<Error, Data>> result = selective.orElse(sources);
     * }</pre>
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

    /**
     * Helper method for {@link #orElse(java.util.List)}. Combines two alternatives, selecting the
     * second if the first fails.
     *
     * @param first The first alternative to try.
     * @param second The second alternative to try if the first fails.
     * @param <E> The error type.
     * @param <A> The success type.
     * @return The result of the first successful alternative.
     */
    private <E, A> Kind<F, Choice<E, A>> selectOrElse(
            Kind<F, Choice<E, A>> first, Kind<F, Choice<E, A>> second) {
        // Transform first: Choice<E, A> -> Choice<E, Choice<E, A>>
        // If first is Right(a), we want Right(Right(a))
        // If first is Left(e), we want Left(e) which will trigger the function
        Kind<F, Choice<E, Choice<E, A>>> transformed =
                map(
                        choice ->
                                choice.isRight()
                                        ? new SimpleChoice<E, Choice<E, A>>(
                                        false, null, new SimpleChoice<>(false, null, choice.getRight()))
                                        : new SimpleChoice<E, Choice<E, A>>(true, choice.getLeft(), null),
                        first);

        // Create a function that returns the second choice when applied
        Kind<F, Function<E, Choice<E, A>>> getSecond =
                map(choiceA -> (Function<E, Choice<E, A>>) e -> choiceA, second);

        // Select with the transformed choice
        return select(transformed, getSecond);
    }

    /**
     * Applies multiple effectful functions in sequence, each depending on whether the previous
     * computation succeeded or failed.
     *
     * <p>This allows building complex validation or computation chains where each step can
     * short-circuit on failure.
     *
     * <p>Example:
     *
     * <pre>{@code
     * Kind<F, Choice<Error, String>> input = ...;
     * List<Kind<F, Function<String, Choice<Error, String>>>> validations = List.of(
     *   validateNotEmpty,
     *   validateLength,
     *   validateFormat
     * );
     * Kind<F, Choice<Error, String>> result = selective.apS(input, validations);
     * }</pre>
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
            // Transform Choice<E, A> -> Choice<A, Choice<E, A>>
            // If Right(a), transform to Left(a) so the function gets applied
            // If Left(e), transform to Right(Left(e)) to short-circuit
            Kind<F, Choice<A, Choice<E, A>>> transformed =
                    map(
                            choice ->
                                    choice.isRight()
                                            ? new SimpleChoice<A, Choice<E, A>>(true, choice.getRight(), null)
                                            : new SimpleChoice<A, Choice<E, A>>(
                                            false, null, new SimpleChoice<E, A>(true, choice.getLeft(), null)),
                            result);

            // Apply select with the transformed choice and the function
            Kind<F, Choice<E, A>> applied = select(transformed, func);
            result = applied;
        }
        return result;
    }
}