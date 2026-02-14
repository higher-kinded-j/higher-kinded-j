// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.jspecify.annotations.NullMarked;

/**
 * Utility class providing {@link Traversal} and {@link Prism} instances for working with {@link
 * Try} types.
 *
 * <p>This class provides optics for focusing on values inside a {@code Try}, both the {@code
 * Success} case and the {@code Failure} case (exception).
 */
@NullMarked
public final class TryTraversals {
  /** Private constructor to prevent instantiation. */
  private TryTraversals() {}

  /**
   * Creates a {@link Traversal} that focuses on the value in the {@code Success} case of a {@link
   * Try}.
   *
   * <p>This traversal matches when the {@code Try} is {@code Success} and allows modification of
   * the contained value. When the {@code Try} is {@code Failure}, the traversal has zero targets
   * and the result is unchanged.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Traversal<Try<Integer>, Integer> successTraversal = TryTraversals.success();
   *
   * Try<Integer> successValue = Try.success(42);
   * Try<Integer> modified = Traversals.modify(
   *     successTraversal,
   *     x -> x * 2,
   *     successValue
   * );  // Try.success(84)
   *
   * Try<Integer> failure = Try.failure(new Exception("error"));
   * Try<Integer> unchanged = Traversals.modify(
   *     successTraversal,
   *     x -> x * 2,
   *     failure
   * );  // Try.failure(exception) - no change
   *
   * // Composing to extract all successful results from a list
   * Traversal<List<Try<Integer>>, Integer> allSuccesses =
   *     Traversals.forList().andThen(successTraversal);
   * List<Integer> results = Traversals.getAll(allSuccesses, tries);
   * }</pre>
   *
   * @param <A> The type of the success value.
   * @return A {@link Traversal} focusing on the {@code Success} case.
   */
  public static <A> Traversal<Try<A>, A> success() {
    return new Traversal<>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, Try<A>> modifyF(
          Function<A, Kind<F, A>> f, Try<A> source, Applicative<F> applicative) {
        return switch (source) {
          case Try.Success<A>(var value) -> applicative.map(Try::success, f.apply(value));
          case Try.Failure<A> failure -> applicative.of(source);
        };
      }
    };
  }

  /**
   * Creates a {@link Traversal} that focuses on the exception in the {@code Failure} case of a
   * {@link Try}.
   *
   * <p>This traversal matches when the {@code Try} is {@code Failure} and allows modification of
   * the contained {@link Throwable}. When the {@code Try} is {@code Success}, the traversal has
   * zero targets and the result is unchanged.
   *
   * <p>This is useful for transforming or enriching exception information across multiple {@code
   * Try} values.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Traversal<Try<Integer>, Throwable> failureTraversal = TryTraversals.failure();
   *
   * Try<Integer> failure = Try.failure(new Exception("error"));
   * Try<Integer> wrapped = Traversals.modify(
   *     failureTraversal,
   *     ex -> new RuntimeException("Wrapped: " + ex.getMessage(), ex),
   *     failure
   * );  // Try.failure(RuntimeException wrapping original)
   *
   * Try<Integer> success = Try.success(42);
   * Try<Integer> unchanged = Traversals.modify(
   *     failureTraversal,
   *     ex -> new RuntimeException("Wrapped", ex),
   *     success
   * );  // Try.success(42) - no change
   *
   * // Composing to collect all exceptions from a list
   * Traversal<List<Try<Integer>>, Throwable> allFailures =
   *     Traversals.forList().andThen(failureTraversal);
   * List<Throwable> exceptions = Traversals.getAll(allFailures, tries);
   * }</pre>
   *
   * @param <A> The type of the success value (phantom type in failure case).
   * @return A {@link Traversal} focusing on the {@code Failure} case.
   */
  public static <A> Traversal<Try<A>, Throwable> failure() {
    return new Traversal<>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, Try<A>> modifyF(
          Function<Throwable, Kind<F, Throwable>> f, Try<A> source, Applicative<F> applicative) {
        return switch (source) {
          case Try.Failure<A>(var cause) -> applicative.map(Try::<A>failure, f.apply(cause));
          case Try.Success<A> success -> applicative.of(source);
        };
      }
    };
  }

  /**
   * Creates a {@link Prism} that focuses on the value in the {@code Success} case of a {@link Try}.
   *
   * <p>This is the same as {@link Prisms#success()}, provided here for convenience and
   * discoverability.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Prism<Try<Integer>, Integer> successPrism = TryTraversals.successPrism();
   *
   * Try<Integer> successValue = Try.success(42);
   * Optional<Integer> extracted = successPrism.getOptional(successValue);  // Optional.of(42)
   *
   * Try<Integer> failure = Try.failure(new Exception("error"));
   * Optional<Integer> noMatch = successPrism.getOptional(failure);  // Optional.empty()
   * }</pre>
   *
   * @param <A> The type of the success value.
   * @return A {@link Prism} focusing on the {@code Success} case.
   */
  public static <A> Prism<Try<A>, A> successPrism() {
    return Prisms.success();
  }

  /**
   * Creates a {@link Prism} that focuses on the exception in the {@code Failure} case of a {@link
   * Try}.
   *
   * <p>This is the same as {@link Prisms#failure()}, provided here for convenience and
   * discoverability.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Prism<Try<Integer>, Throwable> failurePrism = TryTraversals.failurePrism();
   *
   * Exception error = new Exception("error");
   * Try<Integer> failure = Try.failure(error);
   * Optional<Throwable> extracted = failurePrism.getOptional(failure);
   * // Optional.of(error)
   *
   * Try<Integer> success = Try.success(42);
   * Optional<Throwable> noMatch = failurePrism.getOptional(success);  // Optional.empty()
   * }</pre>
   *
   * @param <A> The type of the success value (phantom type in failure case).
   * @return A {@link Prism} focusing on the {@code Failure} case.
   */
  public static <A> Prism<Try<A>, Throwable> failurePrism() {
    return Prisms.failure();
  }
}
