// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trampoline;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NullMarked;

/**
 * Utility methods for working with {@link Trampoline} to ensure stack safety in various functional
 * operations.
 *
 * <p>This class provides helper methods for making traverse operations and applicative operations
 * stack-safe by wrapping them in {@code Trampoline} computations.
 *
 * <h2>Use Cases</h2>
 *
 * <p><b>1. Stack-Safe List Traversal:</b>
 *
 * <pre>{@code
 * // For very large lists with potentially unsafe applicatives
 * List<Integer> largeList = IntStream.range(0, 100000).boxed().collect(Collectors.toList());
 *
 * Kind<MyApplicative.Witness, List<String>> result =
 *     TrampolineUtils.traverseListStackSafe(
 *         largeList,
 *         i -> myApplicative.of("item-" + i),
 *         myApplicative
 *     );
 * }</pre>
 *
 * <p><b>2. Stack-Safe Applicative Combinations:</b>
 *
 * <pre>{@code
 * // Chain many map2 operations safely
 * Kind<F, Integer> result = initial;
 * for (int i = 0; i < 10000; i++) {
 *     final int value = i;
 *     result = TrampolineUtils.map2StackSafe(
 *         result,
 *         applicative.of(value),
 *         (acc, v) -> acc + v,
 *         applicative
 *     );
 * }
 * }</pre>
 *
 * <h2>When to Use</h2>
 *
 * <p>Use these utilities when:
 *
 * <ul>
 *   <li>Processing very large collections (>10,000 elements)
 *   <li>Using custom {@code Applicative} instances that may not be stack-safe
 *   <li>Chaining many {@code map2} or {@code flatMap} operations
 *   <li>You need guaranteed stack safety regardless of the underlying applicative implementation
 * </ul>
 *
 * <h2>Performance Considerations</h2>
 *
 * <p>These utilities add a small overhead due to trampoline wrapping. For most standard
 * applicatives (Id, Optional, Either) on moderately-sized lists (<10,000 elements), the standard
 * traverse implementations are sufficient and more efficient.
 *
 * @see Trampoline
 * @see Applicative
 */
@NullMarked
public final class TrampolineUtils {
  /** Private constructor to prevent instantiation. */
  private TrampolineUtils() {}

  /**
   * Traverses a list using an applicative function in a stack-safe manner.
   *
   * <p>This method uses {@link Trampoline} internally to ensure that traversing large lists
   * (100,000+ elements) will not cause {@code StackOverflowError}, regardless of the {@code
   * Applicative} instance's implementation.
   *
   * <p>The traversal builds the result list from left to right, maintaining the order of elements.
   *
   * <h3>Example:</h3>
   *
   * <pre>{@code
   * List<Integer> numbers = IntStream.range(0, 50000).boxed().collect(Collectors.toList());
   *
   * // Apply validation to each element
   * Kind<Validated.Witness<String>, List<String>> validated =
   *     TrampolineUtils.traverseListStackSafe(
   *         numbers,
   *         n -> n % 2 == 0
   *             ? Validated.valid("even-" + n)
   *             : Validated.invalid("Odd number: " + n),
   *         validatedApplicative
   *     );
   * }</pre>
   *
   * @param <F> The applicative effect type witness
   * @param <A> The element type of the input list
   * @param <B> The element type of the output list
   * @param list The list to traverse
   * @param f The effectful function to apply to each element
   * @param applicative The applicative instance
   * @return The traversed list wrapped in the applicative effect
   */
  public static <F, A, B> Kind<F, List<B>> traverseListStackSafe(
      final List<A> list,
      final Function<? super A, ? extends Kind<F, ? extends B>> f,
      final Applicative<F> applicative) {

    final Trampoline<Kind<F, List<B>>> trampoline =
        traverseListTrampoline(list, 0, applicative.of(new ArrayList<>()), f, applicative);

    return trampoline.run();
  }

  /**
   * Tail-recursive helper for traversing a list using Trampoline.
   *
   * @param list The list to traverse
   * @param index Current index in the list
   * @param accumulator The accumulated result so far (Kind of List)
   * @param f The effectful function
   * @param applicative The applicative instance
   * @param <F> The applicative effect type witness
   * @param <A> Input element type
   * @param <B> Output element type
   * @return A Trampoline that will produce the final result when run
   */
  private static <F, A, B> Trampoline<Kind<F, List<B>>> traverseListTrampoline(
      final List<A> list,
      final int index,
      final Kind<F, List<B>> accumulator,
      final Function<? super A, ? extends Kind<F, ? extends B>> f,
      final Applicative<F> applicative) {

    // Base case: processed all elements
    if (index >= list.size()) {
      return Trampoline.done(accumulator);
    }

    // Recursive case: process next element
    final A element = list.get(index);
    final Kind<F, ? extends B> effectOfB = f.apply(element);

    final Kind<F, List<B>> nextAccumulator =
        applicative.map2(
            accumulator,
            effectOfB,
            (listB, b) -> {
              // Create new list to ensure correctness for multi-branch Applicatives (e.g., List)
              List<B> newList = new ArrayList<>(listB);
              newList.add((B) b);
              return newList;
            });

    return Trampoline.defer(
        () -> traverseListTrampoline(list, index + 1, nextAccumulator, f, applicative));
  }

  /**
   * Sequences a list of applicative effects into an applicative of a list in a stack-safe manner.
   *
   * <p>This is the stack-safe version of the standard {@code sequence} operation. It converts
   * {@code List<Kind<F, A>>} into {@code Kind<F, List<A>>}.
   *
   * <h3>Example:</h3>
   *
   * <pre>{@code
   * List<Kind<Optional.Witness, Integer>> optionals = List.of(
   *     Optional.of(1),
   *     Optional.of(2),
   *     Optional.of(3),
   *     // ... 50,000 more elements
   * );
   *
   * Kind<Optional.Witness, List<Integer>> result =
   *     TrampolineUtils.sequenceStackSafe(optionals, optionalApplicative);
   * // Result: Optional.of(List.of(1, 2, 3, ...))
   * }</pre>
   *
   * @param <F> The applicative effect type witness
   * @param <A> The element type
   * @param effects The list of effectful values
   * @param applicative The applicative instance
   * @return All effects sequenced into a single effect containing a list
   */
  public static <F, A> Kind<F, List<A>> sequenceStackSafe(
      final List<Kind<F, A>> effects, final Applicative<F> applicative) {

    return traverseListStackSafe(effects, Function.identity(), applicative);
  }
}
