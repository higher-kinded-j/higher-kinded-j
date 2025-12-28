// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.jspecify.annotations.NullMarked;

/**
 * Utility class providing {@link Traversal} and {@link Prism} instances for working with {@link
 * Either} types.
 *
 * <p>This class provides optics for focusing on values inside an {@code Either}, both the {@code
 * Right} case (success) and the {@code Left} case (error).
 */
@NullMarked
public final class EitherTraversals {
  /** Private constructor to prevent instantiation. */
  private EitherTraversals() {}

  /**
   * Creates a {@link Traversal} that focuses on the value in the {@code Right} case of an {@link
   * Either}.
   *
   * <p>This traversal matches when the {@code Either} is {@code Right} and allows modification of
   * the contained value. When the {@code Either} is {@code Left}, the traversal has zero targets
   * and the result is unchanged.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Traversal<Either<String, Integer>, Integer> rightTraversal =
   *     EitherTraversals.right();
   *
   * Either<String, Integer> rightValue = Either.right(42);
   * Either<String, Integer> modified = Traversals.modify(
   *     rightTraversal,
   *     x -> x * 2,
   *     rightValue
   * );  // Either.right(84)
   *
   * Either<String, Integer> leftValue = Either.left("error");
   * Either<String, Integer> unchanged = Traversals.modify(
   *     rightTraversal,
   *     x -> x * 2,
   *     leftValue
   * );  // Either.left("error") - no change
   *
   * // Composing to extract all successful results from a list
   * Traversal<List<Either<String, Integer>>, Integer> allSuccesses =
   *     Traversals.forList().andThen(rightTraversal);
   * }</pre>
   *
   * @param <L> The type of the {@code Left} value.
   * @param <R> The type of the {@code Right} value.
   * @return A {@link Traversal} focusing on the {@code Right} case.
   */
  public static <L, R> Traversal<Either<L, R>, R> right() {
    return new Traversal<>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, Either<L, R>> modifyF(
          Function<R, Kind<F, R>> f, Either<L, R> source, Applicative<F> applicative) {
        return source.isRight()
            ? applicative.map(Either::<L, R>right, f.apply(source.getRight()))
            : applicative.of(source);
      }
    };
  }

  /**
   * Creates a {@link Traversal} that focuses on the value in the {@code Left} case of an {@link
   * Either}.
   *
   * <p>This traversal matches when the {@code Either} is {@code Left} and allows modification of
   * the contained error value. When the {@code Either} is {@code Right}, the traversal has zero
   * targets and the result is unchanged.
   *
   * <p>This is useful for transforming or enriching error information across multiple {@code
   * Either} values.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Traversal<Either<String, Integer>, String> leftTraversal = EitherTraversals.left();
   *
   * Either<String, Integer> leftValue = Either.left("error");
   * Either<String, Integer> enriched = Traversals.modify(
   *     leftTraversal,
   *     err -> "Error: " + err,
   *     leftValue
   * );  // Either.left("Error: error")
   *
   * Either<String, Integer> rightValue = Either.right(42);
   * Either<String, Integer> unchanged = Traversals.modify(
   *     leftTraversal,
   *     err -> "Error: " + err,
   *     rightValue
   * );  // Either.right(42) - no change
   *
   * // Composing to collect and transform all errors from a list
   * Traversal<List<Either<String, Integer>>, String> allErrors =
   *     Traversals.forList().andThen(leftTraversal);
   * List<String> errors = Traversals.getAll(allErrors, results);
   * }</pre>
   *
   * @param <L> The type of the {@code Left} value.
   * @param <R> The type of the {@code Right} value.
   * @return A {@link Traversal} focusing on the {@code Left} case.
   */
  public static <L, R> Traversal<Either<L, R>, L> left() {
    return new Traversal<>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, Either<L, R>> modifyF(
          Function<L, Kind<F, L>> f, Either<L, R> source, Applicative<F> applicative) {
        return source.isLeft()
            ? applicative.map(Either::<L, R>left, f.apply(source.getLeft()))
            : applicative.of(source);
      }
    };
  }

  /**
   * Creates a {@link Prism} that focuses on the value in the {@code Right} case of an {@link
   * Either}.
   *
   * <p>This is the same as {@link Prisms#right()}, provided here for convenience and
   * discoverability.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Prism<Either<String, Integer>, Integer> rightPrism = EitherTraversals.rightPrism();
   *
   * Either<String, Integer> rightValue = Either.right(42);
   * Optional<Integer> extracted = rightPrism.getOptional(rightValue);  // Optional.of(42)
   *
   * Either<String, Integer> leftValue = Either.left("error");
   * Optional<Integer> noMatch = rightPrism.getOptional(leftValue);  // Optional.empty()
   * }</pre>
   *
   * @param <L> The type of the {@code Left} value.
   * @param <R> The type of the {@code Right} value.
   * @return A {@link Prism} focusing on the {@code Right} case.
   */
  public static <L, R> Prism<Either<L, R>, R> rightPrism() {
    return Prisms.right();
  }

  /**
   * Creates a {@link Prism} that focuses on the value in the {@code Left} case of an {@link
   * Either}.
   *
   * <p>This is the same as {@link Prisms#left()}, provided here for convenience and
   * discoverability.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Prism<Either<String, Integer>, String> leftPrism = EitherTraversals.leftPrism();
   *
   * Either<String, Integer> leftValue = Either.left("error");
   * Optional<String> extracted = leftPrism.getOptional(leftValue);  // Optional.of("error")
   *
   * Either<String, Integer> rightValue = Either.right(42);
   * Optional<String> noMatch = leftPrism.getOptional(rightValue);  // Optional.empty()
   * }</pre>
   *
   * @param <L> The type of the {@code Left} value.
   * @param <R> The type of the {@code Right} value.
   * @return A {@link Prism} focusing on the {@code Left} case.
   */
  public static <L, R> Prism<Either<L, R>, L> leftPrism() {
    return Prisms.left();
  }
}
