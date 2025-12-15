// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;

/**
 * Utility operations for working with Path types.
 *
 * <p>Provides common functional programming patterns like sequence and traverse that operate across
 * collections of Path values. These operations allow you to transform and combine multiple paths
 * efficiently.
 *
 * <h2>Sequence Operations</h2>
 *
 * <p>Sequence transforms a list of paths into a path of list:
 *
 * <pre>{@code
 * List<MaybePath<Integer>> paths = List.of(Path.just(1), Path.just(2), Path.just(3));
 * MaybePath<List<Integer>> result = PathOps.sequenceMaybe(paths);
 * // result = Just([1, 2, 3])
 *
 * List<MaybePath<Integer>> withNothing = List.of(Path.just(1), Path.nothing(), Path.just(3));
 * MaybePath<List<Integer>> emptyResult = PathOps.sequenceMaybe(withNothing);
 * // emptyResult = Nothing
 * }</pre>
 *
 * <h2>Traverse Operations</h2>
 *
 * <p>Traverse combines map and sequence in one operation:
 *
 * <pre>{@code
 * List<Integer> ids = List.of(1, 2, 3);
 * MaybePath<List<User>> users = PathOps.traverseMaybe(ids, id -> userRepo.findById(id));
 * // returns Just([user1, user2, user3]) if all found, Nothing otherwise
 * }</pre>
 *
 * <h2>Error Handling</h2>
 *
 * <p>Different path types handle errors differently:
 *
 * <ul>
 *   <li>{@code MaybePath}: Returns Nothing if any element is Nothing
 *   <li>{@code EitherPath}: Returns first Left error
 *   <li>{@code ValidationPath}: Accumulates all errors using the provided Semigroup
 *   <li>{@code TryPath}: Returns first failure
 * </ul>
 *
 * @see MaybePath
 * @see EitherPath
 * @see ValidationPath
 * @see TryPath
 */
public final class PathOps {

  private PathOps() {
    // Utility class - no instantiation
  }

  // ===== MaybePath Operations =====

  /**
   * Converts a list of MaybePaths into a MaybePath of list.
   *
   * <p>If all paths contain values, returns a path containing the list of values. If any path is
   * Nothing, returns Nothing.
   *
   * @param paths the list of paths to sequence; must not be null
   * @param <A> the element type
   * @return a MaybePath containing a list, or Nothing if any path is Nothing
   * @throws NullPointerException if paths is null
   */
  public static <A> MaybePath<List<A>> sequenceMaybe(List<MaybePath<A>> paths) {
    Objects.requireNonNull(paths, "paths must not be null");

    List<A> results = new ArrayList<>(paths.size());
    for (MaybePath<A> path : paths) {
      Maybe<A> maybe = path.run();
      if (maybe.isNothing()) {
        return new MaybePath<>(Maybe.nothing());
      }
      results.add(maybe.get());
    }
    return new MaybePath<>(Maybe.just(results));
  }

  /**
   * Maps a function over a list and sequences the results.
   *
   * <p>This is equivalent to mapping the function over the list, then sequencing the results, but
   * potentially more efficient.
   *
   * @param items the items to traverse; must not be null
   * @param f the function to apply; must not be null
   * @param <A> the input element type
   * @param <B> the output element type
   * @return a MaybePath containing a list, or Nothing if any application fails
   * @throws NullPointerException if items or f is null
   */
  public static <A, B> MaybePath<List<B>> traverseMaybe(
      List<A> items, Function<A, MaybePath<B>> f) {
    Objects.requireNonNull(items, "items must not be null");
    Objects.requireNonNull(f, "f must not be null");

    List<B> results = new ArrayList<>(items.size());
    for (A item : items) {
      MaybePath<B> path = f.apply(item);
      Maybe<B> maybe = path.run();
      if (maybe.isNothing()) {
        return new MaybePath<>(Maybe.nothing());
      }
      results.add(maybe.get());
    }
    return new MaybePath<>(Maybe.just(results));
  }

  // ===== EitherPath Operations =====

  /**
   * Converts a list of EitherPaths into an EitherPath of list.
   *
   * <p>If all paths are Right, returns a path containing the list of values. If any path is Left,
   * returns the first Left error encountered.
   *
   * @param paths the list of paths to sequence; must not be null
   * @param <E> the error type
   * @param <A> the element type
   * @return an EitherPath containing a list, or the first error
   * @throws NullPointerException if paths is null
   */
  public static <E, A> EitherPath<E, List<A>> sequenceEither(List<EitherPath<E, A>> paths) {
    Objects.requireNonNull(paths, "paths must not be null");

    List<A> results = new ArrayList<>(paths.size());
    for (EitherPath<E, A> path : paths) {
      Either<E, A> either = path.run();
      if (either.isLeft()) {
        return new EitherPath<>(Either.left(either.getLeft()));
      }
      results.add(either.getRight());
    }
    return new EitherPath<>(Either.right(results));
  }

  /**
   * Maps a function over a list and sequences the results.
   *
   * @param items the items to traverse; must not be null
   * @param f the function to apply; must not be null
   * @param <E> the error type
   * @param <A> the input element type
   * @param <B> the output element type
   * @return an EitherPath containing a list, or the first error
   * @throws NullPointerException if items or f is null
   */
  public static <E, A, B> EitherPath<E, List<B>> traverseEither(
      List<A> items, Function<A, EitherPath<E, B>> f) {
    Objects.requireNonNull(items, "items must not be null");
    Objects.requireNonNull(f, "f must not be null");

    List<B> results = new ArrayList<>(items.size());
    for (A item : items) {
      EitherPath<E, B> path = f.apply(item);
      Either<E, B> either = path.run();
      if (either.isLeft()) {
        return new EitherPath<>(Either.left(either.getLeft()));
      }
      results.add(either.getRight());
    }
    return new EitherPath<>(Either.right(results));
  }

  // ===== ValidationPath Operations =====

  /**
   * Converts a list of ValidationPaths into a ValidationPath of list.
   *
   * <p>If all paths are Valid, returns a path containing the list of values. If any paths are
   * Invalid, accumulates all errors using the provided Semigroup.
   *
   * @param paths the list of paths to sequence; must not be null
   * @param semigroup the Semigroup for error accumulation; must not be null
   * @param <E> the error type
   * @param <A> the element type
   * @return a ValidationPath containing a list, or all accumulated errors
   * @throws NullPointerException if paths or semigroup is null
   */
  public static <E, A> ValidationPath<E, List<A>> sequenceValidated(
      List<ValidationPath<E, A>> paths, Semigroup<E> semigroup) {
    Objects.requireNonNull(paths, "paths must not be null");
    Objects.requireNonNull(semigroup, "semigroup must not be null");

    List<A> results = new ArrayList<>(paths.size());
    E accumulatedErrors = null;

    for (ValidationPath<E, A> path : paths) {
      Validated<E, A> validated = path.run();
      if (validated.isValid()) {
        results.add(validated.get());
      } else {
        E error = validated.getError();
        accumulatedErrors =
            accumulatedErrors == null ? error : semigroup.combine(accumulatedErrors, error);
      }
    }

    if (accumulatedErrors != null) {
      return new ValidationPath<>(Validated.invalid(accumulatedErrors), semigroup);
    }
    return new ValidationPath<>(Validated.valid(results), semigroup);
  }

  /**
   * Maps a function over a list and sequences the results, accumulating errors.
   *
   * @param items the items to traverse; must not be null
   * @param f the function to apply; must not be null
   * @param semigroup the Semigroup for error accumulation; must not be null
   * @param <E> the error type
   * @param <A> the input element type
   * @param <B> the output element type
   * @return a ValidationPath containing a list, or all accumulated errors
   * @throws NullPointerException if any argument is null
   */
  public static <E, A, B> ValidationPath<E, List<B>> traverseValidated(
      List<A> items, Function<A, ValidationPath<E, B>> f, Semigroup<E> semigroup) {
    Objects.requireNonNull(items, "items must not be null");
    Objects.requireNonNull(f, "f must not be null");
    Objects.requireNonNull(semigroup, "semigroup must not be null");

    List<B> results = new ArrayList<>(items.size());
    E accumulatedErrors = null;

    for (A item : items) {
      ValidationPath<E, B> path = f.apply(item);
      Validated<E, B> validated = path.run();
      if (validated.isValid()) {
        results.add(validated.get());
      } else {
        E error = validated.getError();
        accumulatedErrors =
            accumulatedErrors == null ? error : semigroup.combine(accumulatedErrors, error);
      }
    }

    if (accumulatedErrors != null) {
      return new ValidationPath<>(Validated.invalid(accumulatedErrors), semigroup);
    }
    return new ValidationPath<>(Validated.valid(results), semigroup);
  }

  // ===== TryPath Operations =====

  /**
   * Converts a list of TryPaths into a TryPath of list.
   *
   * <p>If all paths are Success, returns a path containing the list of values. If any path fails,
   * returns the first failure encountered.
   *
   * @param paths the list of paths to sequence; must not be null
   * @param <A> the element type
   * @return a TryPath containing a list, or the first failure
   * @throws NullPointerException if paths is null
   */
  public static <A> TryPath<List<A>> sequenceTry(List<TryPath<A>> paths) {
    Objects.requireNonNull(paths, "paths must not be null");

    List<A> results = new ArrayList<>(paths.size());
    for (TryPath<A> path : paths) {
      Try<A> tryValue = path.run();
      // Use fold to safely extract value or return failure
      TryPath<List<A>> maybeFailure =
          tryValue.fold(
              a -> {
                results.add(a);
                return null; // continue processing
              },
              ex -> new TryPath<>(Try.failure(ex)));
      if (maybeFailure != null) {
        return maybeFailure;
      }
    }
    return new TryPath<>(Try.success(results));
  }

  /**
   * Maps a function over a list and sequences the results.
   *
   * @param items the items to traverse; must not be null
   * @param f the function to apply; must not be null
   * @param <A> the input element type
   * @param <B> the output element type
   * @return a TryPath containing a list, or the first failure
   * @throws NullPointerException if items or f is null
   */
  public static <A, B> TryPath<List<B>> traverseTry(List<A> items, Function<A, TryPath<B>> f) {
    Objects.requireNonNull(items, "items must not be null");
    Objects.requireNonNull(f, "f must not be null");

    List<B> results = new ArrayList<>(items.size());
    for (A item : items) {
      TryPath<B> path = f.apply(item);
      Try<B> tryValue = path.run();
      // Use fold to safely extract value or return failure
      TryPath<List<B>> maybeFailure =
          tryValue.fold(
              b -> {
                results.add(b);
                return null; // continue processing
              },
              ex -> new TryPath<>(Try.failure(ex)));
      if (maybeFailure != null) {
        return maybeFailure;
      }
    }
    return new TryPath<>(Try.success(results));
  }

  /**
   * Returns the first successful path, or the last failure if all fail.
   *
   * <p>This is useful for trying multiple fallback strategies until one succeeds.
   *
   * @param paths the paths to try; must not be null or empty
   * @param <A> the element type
   * @return the first successful path, or the last failure
   * @throws NullPointerException if paths is null
   * @throws IllegalArgumentException if paths is empty
   */
  public static <A> TryPath<A> firstSuccess(List<TryPath<A>> paths) {
    Objects.requireNonNull(paths, "paths must not be null");
    if (paths.isEmpty()) {
      throw new IllegalArgumentException("paths must not be empty");
    }

    TryPath<A> lastFailure = null;
    for (TryPath<A> path : paths) {
      if (path.run().isSuccess()) {
        return path;
      }
      lastFailure = path;
    }
    return lastFailure;
  }

  // ===== OptionalPath Operations =====

  /**
   * Converts a list of OptionalPaths into an OptionalPath of list.
   *
   * <p>If all paths contain values, returns a path containing the list of values. If any path is
   * empty, returns empty.
   *
   * @param paths the list of paths to sequence; must not be null
   * @param <A> the element type
   * @return an OptionalPath containing a list, or empty if any path is empty
   * @throws NullPointerException if paths is null
   */
  public static <A> OptionalPath<List<A>> sequenceOptional(List<OptionalPath<A>> paths) {
    Objects.requireNonNull(paths, "paths must not be null");

    List<A> results = new ArrayList<>(paths.size());
    for (OptionalPath<A> path : paths) {
      Optional<A> optional = path.run();
      if (optional.isEmpty()) {
        return new OptionalPath<>(Optional.empty());
      }
      results.add(optional.get());
    }
    return new OptionalPath<>(Optional.of(results));
  }

  /**
   * Maps a function over a list and sequences the results.
   *
   * @param items the items to traverse; must not be null
   * @param f the function to apply; must not be null
   * @param <A> the input element type
   * @param <B> the output element type
   * @return an OptionalPath containing a list, or empty if any application returns empty
   * @throws NullPointerException if items or f is null
   */
  public static <A, B> OptionalPath<List<B>> traverseOptional(
      List<A> items, Function<A, OptionalPath<B>> f) {
    Objects.requireNonNull(items, "items must not be null");
    Objects.requireNonNull(f, "f must not be null");

    List<B> results = new ArrayList<>(items.size());
    for (A item : items) {
      OptionalPath<B> path = f.apply(item);
      Optional<B> optional = path.run();
      if (optional.isEmpty()) {
        return new OptionalPath<>(Optional.empty());
      }
      results.add(optional.get());
    }
    return new OptionalPath<>(Optional.of(results));
  }
}
