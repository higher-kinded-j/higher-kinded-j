// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.jspecify.annotations.NullMarked;

/**
 * Utility class providing {@link Traversal} and {@link Prism} instances for working with {@link
 * Validated} types.
 *
 * <p>This class provides optics for focusing on values inside a {@code Validated}, both the {@code
 * Valid} case (success) and the {@code Invalid} case (error).
 */
@NullMarked
public final class ValidatedTraversals {
  /** Private constructor to prevent instantiation. */
  private ValidatedTraversals() {}

  /**
   * Creates a {@link Traversal} that focuses on the value in the {@code Valid} case of a {@link
   * Validated}.
   *
   * <p>This traversal matches when the {@code Validated} is {@code Valid} and allows modification
   * of the contained value. When the {@code Validated} is {@code Invalid}, the traversal has zero
   * targets and the result is unchanged.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Traversal<Validated<String, Integer>, Integer> validTraversal =
   *     ValidatedTraversals.valid();
   *
   * Validated<String, Integer> validValue = Validated.valid(42);
   * Validated<String, Integer> modified = Traversals.modify(
   *     validTraversal,
   *     x -> x * 2,
   *     validValue
   * );  // Validated.valid(84)
   *
   * Validated<String, Integer> invalidValue = Validated.invalid("error");
   * Validated<String, Integer> unchanged = Traversals.modify(
   *     validTraversal,
   *     x -> x * 2,
   *     invalidValue
   * );  // Validated.invalid("error") - no change
   *
   * // Composing to extract all valid results from a list
   * Traversal<List<Validated<String, Integer>>, Integer> allValid =
   *     Traversals.forList().andThen(validTraversal);
   * List<Integer> validResults = Traversals.getAll(allValid, results);
   * }</pre>
   *
   * @param <E> The type of the error value.
   * @param <A> The type of the valid value.
   * @return A {@link Traversal} focusing on the {@code Valid} case.
   */
  public static <E, A> Traversal<Validated<E, A>, A> valid() {
    return new Traversal<>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, Validated<E, A>> modifyF(
          Function<A, Kind<F, A>> f, Validated<E, A> source, Applicative<F> applicative) {
        return source.isValid()
            ? applicative.map(Validated::<E, A>valid, f.apply(source.get()))
            : applicative.of(source);
      }
    };
  }

  /**
   * Creates a {@link Traversal} that focuses on the error value in the {@code Invalid} case of a
   * {@link Validated}.
   *
   * <p>This traversal matches when the {@code Validated} is {@code Invalid} and allows modification
   * of the contained error value. When the {@code Validated} is {@code Valid}, the traversal has
   * zero targets and the result is unchanged.
   *
   * <p>This is useful for transforming or enriching error information across multiple {@code
   * Validated} values.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Traversal<Validated<String, Integer>, String> invalidTraversal =
   *     ValidatedTraversals.invalid();
   *
   * Validated<String, Integer> invalidValue = Validated.invalid("error");
   * Validated<String, Integer> enriched = Traversals.modify(
   *     invalidTraversal,
   *     err -> "Validation Error: " + err,
   *     invalidValue
   * );  // Validated.invalid("Validation Error: error")
   *
   * Validated<String, Integer> validValue = Validated.valid(42);
   * Validated<String, Integer> unchanged = Traversals.modify(
   *     invalidTraversal,
   *     err -> "Validation Error: " + err,
   *     validValue
   * );  // Validated.valid(42) - no change
   *
   * // Composing to collect all validation errors from a list
   * Traversal<List<Validated<String, Integer>>, String> allErrors =
   *     Traversals.forList().andThen(invalidTraversal);
   * List<String> errors = Traversals.getAll(allErrors, results);
   * }</pre>
   *
   * @param <E> The type of the error value.
   * @param <A> The type of the valid value.
   * @return A {@link Traversal} focusing on the {@code Invalid} case.
   */
  public static <E, A> Traversal<Validated<E, A>, E> invalid() {
    return new Traversal<>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, Validated<E, A>> modifyF(
          Function<E, Kind<F, E>> f, Validated<E, A> source, Applicative<F> applicative) {
        return source.isInvalid()
            ? applicative.map(Validated::<E, A>invalid, f.apply(source.getError()))
            : applicative.of(source);
      }
    };
  }

  /**
   * Creates a {@link Prism} that focuses on the value in the {@code Valid} case of a {@link
   * Validated}.
   *
   * <p>This is the same as {@link Prisms#valid()}, provided here for convenience and
   * discoverability.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Prism<Validated<String, Integer>, Integer> validPrism =
   *     ValidatedTraversals.validPrism();
   *
   * Validated<String, Integer> validValue = Validated.valid(42);
   * Optional<Integer> extracted = validPrism.getOptional(validValue);  // Optional.of(42)
   *
   * Validated<String, Integer> invalidValue = Validated.invalid("error");
   * Optional<Integer> noMatch = validPrism.getOptional(invalidValue);  // Optional.empty()
   * }</pre>
   *
   * @param <E> The type of the error value.
   * @param <A> The type of the valid value.
   * @return A {@link Prism} focusing on the {@code Valid} case.
   */
  public static <E, A> Prism<Validated<E, A>, A> validPrism() {
    return Prisms.valid();
  }

  /**
   * Creates a {@link Prism} that focuses on the error value in the {@code Invalid} case of a {@link
   * Validated}.
   *
   * <p>This is the same as {@link Prisms#invalid()}, provided here for convenience and
   * discoverability.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Prism<Validated<String, Integer>, String> invalidPrism =
   *     ValidatedTraversals.invalidPrism();
   *
   * Validated<String, Integer> invalidValue = Validated.invalid("error");
   * Optional<String> extracted = invalidPrism.getOptional(invalidValue);
   * // Optional.of("error")
   *
   * Validated<String, Integer> validValue = Validated.valid(42);
   * Optional<String> noMatch = invalidPrism.getOptional(validValue);  // Optional.empty()
   * }</pre>
   *
   * @param <E> The type of the error value.
   * @param <A> The type of the valid value.
   * @return A {@link Prism} focusing on the {@code Invalid} case.
   */
  public static <E, A> Prism<Validated<E, A>, E> invalidPrism() {
    return Prisms.invalid();
  }
}
