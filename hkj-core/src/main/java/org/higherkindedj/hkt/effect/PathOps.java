// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Each;
import org.higherkindedj.optics.util.Traversals;

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

  // ===== NonDetPath Operations =====

  /**
   * Converts a list of NonDetPaths into a NonDetPath of list (Cartesian product).
   *
   * <p>This operation produces all possible combinations of elements from each path. The result is
   * the Cartesian product of all inner lists.
   *
   * <pre>{@code
   * List<NonDetPath<Integer>> paths = List.of(
   *     NonDetPath.of(List.of(1, 2)),
   *     NonDetPath.of(List.of(3, 4))
   * );
   * NonDetPath<List<Integer>> result = PathOps.sequenceNonDet(paths);
   * // result.run() = [[1,3], [1,4], [2,3], [2,4]]
   * }</pre>
   *
   * @param paths the list of paths to sequence; must not be null
   * @param <A> the element type
   * @return a NonDetPath containing all combinations
   * @throws NullPointerException if paths is null
   */
  public static <A> NonDetPath<List<A>> sequenceNonDet(List<NonDetPath<A>> paths) {
    Objects.requireNonNull(paths, "paths must not be null");

    if (paths.isEmpty()) {
      return NonDetPath.of(List.of(List.of()));
    }

    // Start with a list containing a single empty list
    NonDetPath<List<A>> result = NonDetPath.pure(new ArrayList<>());

    for (NonDetPath<A> path : paths) {
      result =
          result.via(
              acc ->
                  path.map(
                      a -> {
                        List<A> newAcc = new ArrayList<>(acc);
                        newAcc.add(a);
                        return newAcc;
                      }));
    }

    return result;
  }

  /**
   * Maps a function over a list and sequences the results (Cartesian product).
   *
   * <p>Applies the function to each element and produces all possible combinations of results.
   *
   * @param items the items to traverse; must not be null
   * @param f the function to apply; must not be null
   * @param <A> the input element type
   * @param <B> the output element type
   * @return a NonDetPath containing all combinations
   * @throws NullPointerException if items or f is null
   */
  public static <A, B> NonDetPath<List<B>> traverseNonDet(
      List<A> items, Function<A, NonDetPath<B>> f) {
    Objects.requireNonNull(items, "items must not be null");
    Objects.requireNonNull(f, "f must not be null");

    if (items.isEmpty()) {
      return NonDetPath.of(List.of(List.of()));
    }

    NonDetPath<List<B>> result = NonDetPath.pure(new ArrayList<>());

    for (A item : items) {
      NonDetPath<B> path = f.apply(item);
      result =
          result.via(
              acc ->
                  path.map(
                      b -> {
                        List<B> newAcc = new ArrayList<>(acc);
                        newAcc.add(b);
                        return newAcc;
                      }));
    }

    return result;
  }

  /**
   * Flattens a NonDetPath of NonDetPaths into a single NonDetPath.
   *
   * <p>Concatenates all inner lists.
   *
   * @param nested the nested NonDetPath to flatten; must not be null
   * @param <A> the element type
   * @return a flattened NonDetPath
   * @throws NullPointerException if nested is null
   */
  public static <A> NonDetPath<A> flatten(NonDetPath<NonDetPath<A>> nested) {
    Objects.requireNonNull(nested, "nested must not be null");

    List<A> flattened =
        nested.run().stream().flatMap(innerPath -> innerPath.run().stream()).toList();

    return NonDetPath.of(flattened);
  }

  // ===== ListPath Operations =====

  /**
   * Converts a list of ListPaths into a ListPath of lists using positional zipping.
   *
   * <p>Unlike {@link #sequenceNonDet}, which produces a Cartesian product, this method transposes
   * the list of lists, pairing elements at corresponding positions.
   *
   * <pre>{@code
   * List<ListPath<Integer>> paths = List.of(
   *     ListPath.of(1, 2, 3),
   *     ListPath.of(4, 5, 6)
   * );
   * ListPath<List<Integer>> result = PathOps.sequenceListPath(paths);
   * // result.run() = [[1, 4], [2, 5], [3, 6]]
   * }</pre>
   *
   * @param paths the list of paths to sequence; must not be null
   * @param <A> the element type
   * @return a ListPath containing lists of corresponding elements
   * @throws NullPointerException if paths is null
   */
  public static <A> ListPath<List<A>> sequenceListPath(List<ListPath<A>> paths) {
    Objects.requireNonNull(paths, "paths must not be null");

    if (paths.isEmpty()) {
      return ListPath.of(List.of(List.of()));
    }

    // Find the minimum size among all lists
    int minSize = paths.stream().mapToInt(ListPath::size).min().orElse(0);

    // Transpose: collect element at position i from each list
    List<List<A>> result = new ArrayList<>(minSize);
    for (int i = 0; i < minSize; i++) {
      final int index = i;
      List<A> row = paths.stream().map(path -> path.run().get(index)).toList();
      result.add(row);
    }

    return ListPath.of(result);
  }

  /**
   * Applies a function to each item and sequences the results positionally.
   *
   * <pre>{@code
   * List<String> items = List.of("a", "b");
   * ListPath<List<String>> result = PathOps.traverseListPath(
   *     items,
   *     s -> ListPath.of(s.toUpperCase(), s + s)
   * );
   * // result.run() = [["A", "aa"], ["B", "bb"]]
   * }</pre>
   *
   * @param items the items to traverse; must not be null
   * @param f the function to apply to each item; must not be null
   * @param <A> the input item type
   * @param <B> the element type of the resulting ListPaths
   * @return a ListPath of lists containing corresponding transformed elements
   * @throws NullPointerException if items or f is null
   */
  public static <A, B> ListPath<List<B>> traverseListPath(
      List<A> items, Function<A, ListPath<B>> f) {
    Objects.requireNonNull(items, "items must not be null");
    Objects.requireNonNull(f, "f must not be null");

    if (items.isEmpty()) {
      return ListPath.of(List.of(List.of()));
    }

    List<ListPath<B>> mapped = items.stream().map(f).toList();
    return sequenceListPath(mapped);
  }

  /**
   * Flattens a ListPath of ListPaths by concatenating all inner lists.
   *
   * @param nested the nested ListPath to flatten; must not be null
   * @param <A> the element type
   * @return a flattened ListPath
   * @throws NullPointerException if nested is null
   */
  public static <A> ListPath<A> flattenListPath(ListPath<ListPath<A>> nested) {
    Objects.requireNonNull(nested, "nested must not be null");

    List<A> flattened =
        nested.run().stream().flatMap(innerPath -> innerPath.run().stream()).toList();

    return ListPath.of(flattened);
  }

  /**
   * Zips a list of ListPaths element-wise, producing a ListPath of tuples (as lists).
   *
   * <p>This is similar to Python's {@code zip(*lists)} - it transposes rows and columns.
   *
   * @param paths the list of paths to zip; must not be null
   * @param <A> the element type
   * @return a ListPath where each element is a list of corresponding elements
   * @throws NullPointerException if paths is null
   */
  public static <A> ListPath<List<A>> zipAll(List<ListPath<A>> paths) {
    return sequenceListPath(paths);
  }

  // ===== CompletableFuturePath Operations =====

  /**
   * Converts a list of CompletableFuturePaths into a CompletableFuturePath of list.
   *
   * <p>Runs all futures concurrently and collects all results. If any future fails, the result
   * fails with that exception.
   *
   * @param paths the list of paths to sequence; must not be null
   * @param <A> the element type
   * @return a CompletableFuturePath containing a list of all results
   * @throws NullPointerException if paths is null
   */
  public static <A> CompletableFuturePath<List<A>> sequenceFuture(
      List<CompletableFuturePath<A>> paths) {
    Objects.requireNonNull(paths, "paths must not be null");

    if (paths.isEmpty()) {
      return CompletableFuturePath.completed(List.of());
    }

    List<CompletableFuture<A>> futures =
        paths.stream().map(CompletableFuturePath::toCompletableFuture).collect(Collectors.toList());

    CompletableFuture<List<A>> combined =
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(_ -> futures.stream().map(CompletableFuture::join).toList());

    return CompletableFuturePath.fromFuture(combined);
  }

  /**
   * Maps a function over a list and sequences the results concurrently.
   *
   * <p>Applies the function to each element concurrently and collects all results. If any
   * application fails, the result fails with that exception.
   *
   * @param items the items to traverse; must not be null
   * @param f the function to apply; must not be null
   * @param <A> the input element type
   * @param <B> the output element type
   * @return a CompletableFuturePath containing a list of all results
   * @throws NullPointerException if items or f is null
   */
  public static <A, B> CompletableFuturePath<List<B>> traverseFuture(
      List<A> items, Function<A, CompletableFuturePath<B>> f) {
    Objects.requireNonNull(items, "items must not be null");
    Objects.requireNonNull(f, "f must not be null");

    if (items.isEmpty()) {
      return CompletableFuturePath.completed(List.of());
    }

    List<CompletableFuture<B>> futures =
        items.stream().map(item -> f.apply(item).toCompletableFuture()).toList();

    CompletableFuture<List<B>> combined =
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(_ -> futures.stream().map(CompletableFuture::join).toList());

    return CompletableFuturePath.fromFuture(combined);
  }

  /**
   * Returns the first successful future path, or the last failure if all fail.
   *
   * <p>Note: Unlike {@link #firstSuccess(List)} for TryPath, this races the futures concurrently
   * rather than trying them sequentially.
   *
   * @param paths the paths to race; must not be null or empty
   * @param <A> the element type
   * @return the first completed successful path, or a path with the last exception
   * @throws NullPointerException if paths is null
   * @throws IllegalArgumentException if paths is empty
   */
  public static <A> CompletableFuturePath<A> firstCompletedSuccess(
      List<CompletableFuturePath<A>> paths) {
    Objects.requireNonNull(paths, "paths must not be null");
    if (paths.isEmpty()) {
      throw new IllegalArgumentException("paths must not be empty");
    }

    if (paths.size() == 1) {
      return paths.getFirst();
    }

    // Use anyOf to race, but we need the first SUCCESS, not just first completion
    // This requires a more complex implementation
    CompletableFuture<A> result = new CompletableFuture<>();
    List<Throwable> failures = new ArrayList<>();

    for (CompletableFuturePath<A> path : paths) {
      path.toCompletableFuture()
          .whenComplete(
              (value, ex) -> {
                if (ex == null && !result.isDone()) {
                  result.complete(value);
                } else if (ex != null) {
                  synchronized (failures) {
                    failures.add(ex);
                    if (failures.size() == paths.size() && !result.isDone()) {
                      result.completeExceptionally(failures.getLast());
                    }
                  }
                }
              });
    }

    return CompletableFuturePath.fromFuture(result);
  }

  // ===== Parallel IOPath Operations =====

  /**
   * Executes a list of IOPaths in parallel and collects results.
   *
   * <p>All IOPaths are executed concurrently using CompletableFuture. If any IOPath fails, the
   * result fails with that exception.
   *
   * @param paths the IOPaths to execute in parallel; must not be null
   * @param <A> the element type
   * @return an IOPath containing a list of all results
   * @throws NullPointerException if paths is null
   */
  public static <A> IOPath<List<A>> parSequenceIO(List<IOPath<A>> paths) {
    Objects.requireNonNull(paths, "paths must not be null");

    if (paths.isEmpty()) {
      return Path.ioPure(List.of());
    }

    return new IOPath<>(
        IO.delay(
            () -> {
              List<CompletableFuture<A>> futures =
                  paths.stream()
                      .map(path -> CompletableFuture.supplyAsync(path::unsafeRun))
                      .toList();

              try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
                return futures.stream().map(CompletableFuture::join).toList();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Parallel execution interrupted", e);
              } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException re) {
                  throw re;
                }
                throw new RuntimeException(cause);
              }
            }));
  }

  /**
   * Executes a list of CompletableFuturePaths in parallel and collects results.
   *
   * <p>All futures run concurrently. If any fails, the result fails.
   *
   * @param paths the paths to execute; must not be null
   * @param <A> the element type
   * @return a CompletableFuturePath containing a list of all results
   * @throws NullPointerException if paths is null
   */
  public static <A> CompletableFuturePath<List<A>> parSequenceFuture(
      List<CompletableFuturePath<A>> paths) {
    // This already exists as sequenceFuture, which is already parallel
    return sequenceFuture(paths);
  }

  /**
   * Combines three IOPaths in parallel.
   *
   * @param first the first IOPath; must not be null
   * @param second the second IOPath; must not be null
   * @param third the third IOPath; must not be null
   * @param combiner the function to combine results; must not be null
   * @param <A> the type of the first value
   * @param <B> the type of the second value
   * @param <C> the type of the third value
   * @param <D> the type of the combined result
   * @return an IOPath containing the combined result
   * @throws NullPointerException if any argument is null
   */
  public static <A, B, C, D> IOPath<D> parZip3(
      IOPath<A> first,
      IOPath<B> second,
      IOPath<C> third,
      Function3<? super A, ? super B, ? super C, ? extends D> combiner) {
    Objects.requireNonNull(first, "first must not be null");
    Objects.requireNonNull(second, "second must not be null");
    Objects.requireNonNull(third, "third must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    return new IOPath<>(
        IO.delay(
            () -> {
              CompletableFuture<A> futureA = CompletableFuture.supplyAsync(first::unsafeRun);
              CompletableFuture<B> futureB = CompletableFuture.supplyAsync(second::unsafeRun);
              CompletableFuture<C> futureC = CompletableFuture.supplyAsync(third::unsafeRun);

              try {
                CompletableFuture.allOf(futureA, futureB, futureC).get();
                return combiner.apply(futureA.join(), futureB.join(), futureC.join());
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Parallel execution interrupted", e);
              } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException re) {
                  throw re;
                }
                throw new RuntimeException(cause);
              }
            }));
  }

  /**
   * Combines four IOPaths in parallel.
   *
   * @param first the first IOPath; must not be null
   * @param second the second IOPath; must not be null
   * @param third the third IOPath; must not be null
   * @param fourth the fourth IOPath; must not be null
   * @param combiner the function to combine results; must not be null
   * @param <A> the type of the first value
   * @param <B> the type of the second value
   * @param <C> the type of the third value
   * @param <D> the type of the fourth value
   * @param <E> the type of the combined result
   * @return an IOPath containing the combined result
   * @throws NullPointerException if any argument is null
   */
  public static <A, B, C, D, E> IOPath<E> parZip4(
      IOPath<A> first,
      IOPath<B> second,
      IOPath<C> third,
      IOPath<D> fourth,
      Function4<? super A, ? super B, ? super C, ? super D, ? extends E> combiner) {
    Objects.requireNonNull(first, "first must not be null");
    Objects.requireNonNull(second, "second must not be null");
    Objects.requireNonNull(third, "third must not be null");
    Objects.requireNonNull(fourth, "fourth must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    return new IOPath<>(
        IO.delay(
            () -> {
              CompletableFuture<A> futureA = CompletableFuture.supplyAsync(first::unsafeRun);
              CompletableFuture<B> futureB = CompletableFuture.supplyAsync(second::unsafeRun);
              CompletableFuture<C> futureC = CompletableFuture.supplyAsync(third::unsafeRun);
              CompletableFuture<D> futureD = CompletableFuture.supplyAsync(fourth::unsafeRun);

              try {
                CompletableFuture.allOf(futureA, futureB, futureC, futureD).get();
                return combiner.apply(
                    futureA.join(), futureB.join(), futureC.join(), futureD.join());
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Parallel execution interrupted", e);
              } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException re) {
                  throw re;
                }
                throw new RuntimeException(cause);
              }
            }));
  }

  /**
   * Races multiple IOPaths, returning the first to complete successfully.
   *
   * <p>All IOPaths are executed concurrently. The first to complete successfully wins. If all fail,
   * the last failure is propagated.
   *
   * @param paths the IOPaths to race; must not be null or empty
   * @param <A> the element type
   * @return an IOPath that completes with the first successful result
   * @throws NullPointerException if paths is null
   * @throws IllegalArgumentException if paths is empty
   */
  public static <A> IOPath<A> raceIO(List<IOPath<A>> paths) {
    Objects.requireNonNull(paths, "paths must not be null");
    if (paths.isEmpty()) {
      throw new IllegalArgumentException("paths must not be empty");
    }

    if (paths.size() == 1) {
      return paths.getFirst();
    }

    return new IOPath<>(
        IO.delay(
            () -> {
              CompletableFuture<A> result = new CompletableFuture<>();
              List<Throwable> failures = new ArrayList<>();

              List<CompletableFuture<A>> futures =
                  paths.stream()
                      .map(path -> CompletableFuture.supplyAsync(path::unsafeRun))
                      .toList();

              for (CompletableFuture<A> future : futures) {
                future.whenComplete(
                    (value, ex) -> {
                      if (ex == null && !result.isDone()) {
                        result.complete(value);
                        // Cancel others (best effort)
                        futures.forEach(f -> f.cancel(true));
                      } else if (ex != null) {
                        synchronized (failures) {
                          failures.add(ex);
                          if (failures.size() == paths.size() && !result.isDone()) {
                            result.completeExceptionally(failures.getLast());
                          }
                        }
                      }
                    });
              }

              try {
                return result.get();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Race interrupted", e);
              } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException re) {
                  throw re;
                }
                throw new RuntimeException(cause);
              }
            }));
  }

  // ===== Each-based Traversal Operations =====
  //
  // Performance Note: These methods use a two-pass approach:
  // 1. First pass: Collect all elements from the structure into an intermediate List
  // 2. Second pass: Apply the effectful function to each element with fail-fast semantics
  //
  // This approach was chosen to provide fail-fast behavior for Maybe/Either/Try - once a
  // failure is encountered, processing stops immediately without evaluating remaining elements.
  // However, the intermediate List allocation may be inefficient for very large structures.
  //
  // For traverseEachValidated, which accumulates all errors rather than failing fast, a
  // single-pass foldMap-based approach could avoid the intermediate allocation. This
  // optimization may be added in a future version.

  /**
   * Traverses a structure using an {@link org.higherkindedj.optics.Each} instance, applying a
   * MaybePath-returning function to each element.
   *
   * <p>This method extracts all elements from the structure using the Each instance, applies the
   * effectful function to each element, and collects the results. If any element produces Nothing,
   * the entire result is Nothing (fail-fast semantics).
   *
   * <p><strong>Performance:</strong> This method first collects all elements into an intermediate
   * list, then processes them. The fail-fast behavior means processing stops at the first Nothing,
   * but the initial element collection traverses the entire structure. For very large structures
   * where early failure is likely, consider streaming approaches or lazy evaluation patterns.
   *
   * <pre>{@code
   * // Validate all orders in a user's order list
   * Each<List<Order>, Order> listEach = EachInstances.listEach();
   * MaybePath<List<Order>> validated = PathOps.traverseEachMaybe(
   *     user.orders(),
   *     listEach,
   *     order -> validateOrder(order)  // Returns MaybePath<Order>
   * );
   * }</pre>
   *
   * @param structure the structure to traverse; must not be null
   * @param each the Each instance for extracting elements; must not be null
   * @param f the function to apply to each element; must not be null
   * @param <S> the structure type
   * @param <A> the element type
   * @return a MaybePath containing a list of results, or Nothing if any element fails
   * @throws NullPointerException if any argument is null
   */
  public static <S, A> MaybePath<List<A>> traverseEachMaybe(
      S structure, Each<S, A> each, Function<A, MaybePath<A>> f) {
    Objects.requireNonNull(structure, "structure must not be null");
    Objects.requireNonNull(each, "each must not be null");
    Objects.requireNonNull(f, "f must not be null");

    List<A> elements = Traversals.getAll(each.each(), structure);
    return traverseMaybe(elements, f);
  }

  /**
   * Traverses a structure using an {@link org.higherkindedj.optics.Each} instance, applying an
   * EitherPath-returning function to each element.
   *
   * <p>If any element produces a Left error, the entire result is that Left error (fail-fast
   * semantics).
   *
   * <p><strong>Performance:</strong> This method first collects all elements into an intermediate
   * list, then processes them. The fail-fast behavior means processing stops at the first Left, but
   * the initial element collection traverses the entire structure. For very large structures where
   * early failure is likely, consider streaming approaches or lazy evaluation patterns.
   *
   * <pre>{@code
   * Each<List<Order>, Order> listEach = EachInstances.listEach();
   * EitherPath<String, List<Order>> result = PathOps.traverseEachEither(
   *     user.orders(),
   *     listEach,
   *     order -> processOrder(order)  // Returns EitherPath<String, Order>
   * );
   * }</pre>
   *
   * @param structure the structure to traverse; must not be null
   * @param each the Each instance for extracting elements; must not be null
   * @param f the function to apply to each element; must not be null
   * @param <E> the error type
   * @param <S> the structure type
   * @param <A> the element type
   * @return an EitherPath containing a list of results, or the first error
   * @throws NullPointerException if any argument is null
   */
  public static <E, S, A> EitherPath<E, List<A>> traverseEachEither(
      S structure, Each<S, A> each, Function<A, EitherPath<E, A>> f) {
    Objects.requireNonNull(structure, "structure must not be null");
    Objects.requireNonNull(each, "each must not be null");
    Objects.requireNonNull(f, "f must not be null");

    List<A> elements = Traversals.getAll(each.each(), structure);
    return traverseEither(elements, f);
  }

  /**
   * Traverses a structure using an {@link org.higherkindedj.optics.Each} instance, applying a
   * ValidationPath-returning function to each element with error accumulation.
   *
   * <p>Unlike {@link #traverseEachEither}, this method accumulates all errors using the provided
   * Semigroup instead of failing on the first error. All elements are processed regardless of
   * earlier failures.
   *
   * <p><strong>Performance:</strong> This method first collects all elements into an intermediate
   * list, then processes them. Since error accumulation requires processing all elements anyway, a
   * future optimization could use a single-pass foldMap approach with a suitable Monoid to avoid
   * the intermediate list allocation for very large structures.
   *
   * <pre>{@code
   * Each<List<Order>, Order> listEach = EachInstances.listEach();
   * Semigroup<List<String>> errorSemigroup = Semigroups.listConcat();
   *
   * ValidationPath<List<String>, List<Order>> result = PathOps.traverseEachValidated(
   *     user.orders(),
   *     listEach,
   *     order -> validateOrder(order),  // Returns ValidationPath<List<String>, Order>
   *     errorSemigroup
   * );
   * // Result contains all validation errors if any, or all valid orders
   * }</pre>
   *
   * @param structure the structure to traverse; must not be null
   * @param each the Each instance for extracting elements; must not be null
   * @param f the function to apply to each element; must not be null
   * @param semigroup the Semigroup for accumulating errors; must not be null
   * @param <E> the error type
   * @param <S> the structure type
   * @param <A> the element type
   * @return a ValidationPath containing a list of results, or all accumulated errors
   * @throws NullPointerException if any argument is null
   */
  public static <E, S, A> ValidationPath<E, List<A>> traverseEachValidated(
      S structure, Each<S, A> each, Function<A, ValidationPath<E, A>> f, Semigroup<E> semigroup) {
    Objects.requireNonNull(structure, "structure must not be null");
    Objects.requireNonNull(each, "each must not be null");
    Objects.requireNonNull(f, "f must not be null");
    Objects.requireNonNull(semigroup, "semigroup must not be null");

    List<A> elements = Traversals.getAll(each.each(), structure);
    return traverseValidated(elements, f, semigroup);
  }

  /**
   * Traverses a structure using an {@link org.higherkindedj.optics.Each} instance, applying a
   * TryPath-returning function to each element.
   *
   * <p>If any element throws an exception, the entire result is that failure (fail-fast semantics).
   *
   * <p><strong>Performance:</strong> This method first collects all elements into an intermediate
   * list, then processes them. The fail-fast behavior means processing stops at the first failure,
   * but the initial element collection traverses the entire structure. For very large structures
   * where early failure is likely, consider streaming approaches or lazy evaluation patterns.
   *
   * <pre>{@code
   * Each<List<Order>, Order> listEach = EachInstances.listEach();
   * TryPath<List<Order>> result = PathOps.traverseEachTry(
   *     user.orders(),
   *     listEach,
   *     order -> processOrderUnsafe(order)  // Returns TryPath<Order>
   * );
   * }</pre>
   *
   * @param structure the structure to traverse; must not be null
   * @param each the Each instance for extracting elements; must not be null
   * @param f the function to apply to each element; must not be null
   * @param <S> the structure type
   * @param <A> the element type
   * @return a TryPath containing a list of results, or the first failure
   * @throws NullPointerException if any argument is null
   */
  public static <S, A> TryPath<List<A>> traverseEachTry(
      S structure, Each<S, A> each, Function<A, TryPath<A>> f) {
    Objects.requireNonNull(structure, "structure must not be null");
    Objects.requireNonNull(each, "each must not be null");
    Objects.requireNonNull(f, "f must not be null");

    List<A> elements = Traversals.getAll(each.each(), structure);
    return traverseTry(elements, f);
  }
}
