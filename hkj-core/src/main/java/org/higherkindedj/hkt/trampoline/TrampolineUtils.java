// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trampoline;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.jspecify.annotations.NullMarked;

/**
 * Utility methods for traversing and sequencing large lists of effects without overflowing the
 * stack, whatever the {@code Applicative}.
 *
 * <p>A left fold with {@code map2} nests one call per element, so running the result of a lazy
 * effect such as {@code IO} recurses once per element. These methods combine the effects as a
 * balanced tree instead, so neither building the effect nor running it nests deeper than about
 * log<sub>2</sub>(n) calls.
 *
 * <h2>Use Cases</h2>
 *
 * <p><b>1. Stack-Safe List Traversal:</b>
 *
 * <pre>{@code
 * // For very large lists under a lazy or custom applicative
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
 * <p><b>2. Stack-Safe Sequencing:</b>
 *
 * <pre>{@code
 * // Collapse many effects into one, without growing the stack
 * List<Kind<MyApplicative.Witness, Integer>> effects = ...;
 *
 * Kind<MyApplicative.Witness, List<Integer>> result =
 *     TrampolineUtils.sequenceStackSafe(effects, myApplicative);
 * }</pre>
 *
 * <h2>When to Use</h2>
 *
 * <p>Use these utilities when:
 *
 * <ul>
 *   <li>Traversing a large list under a lazy applicative such as {@code IO}, or a custom one whose
 *       {@code map2} nests
 *   <li>Accumulating many errors with a {@code Validated} whose semigroup concatenates
 *   <li>You need stack safety regardless of the underlying applicative implementation
 * </ul>
 *
 * <h2>Performance Considerations</h2>
 *
 * <p>Building the result takes time linear in the size of the list. For a strict applicative such
 * as {@code Id}, {@code Optional} or {@code Either}, the standard traverse implementations are
 * already stack-safe and need no help. Under an applicative that combines what it accumulates, such
 * as {@code Validated} with a concatenating semigroup, the balanced tree also does less work: each
 * value is combined about log<sub>2</sub>(n) times rather than once per later element.
 *
 * @see Applicative
 */
@NullMarked
public final class TrampolineUtils {
  /** Private constructor to prevent instantiation. */
  private TrampolineUtils() {}

  /**
   * Traverses a list using an applicative function in a stack-safe manner.
   *
   * <p>The effects are combined as a balanced tree, so traversing a large list (a million elements
   * or more) does not cause a {@code StackOverflowError}, whatever the {@code Applicative}. The
   * function is applied to the elements in order, and the effects are combined left to right, so an
   * {@code Either} reports the earliest failure and a {@code Validated} accumulates its errors in
   * element order.
   *
   * <p>The combinations are grouped as a tree rather than one after another. For a lawful {@code
   * Applicative} over an associative semigroup or monoid that gives the same result as a left fold.
   * A combine that is not associative, such as floating-point addition in a {@code Writer}'s log,
   * can give a different one.
   *
   * <p>The result list keeps the order of the elements. It is unmodifiable, so no run of the effect
   * can change the list another run returns.
   *
   * <h3>Example:</h3>
   *
   * <pre>{@code
   * List<Integer> numbers = IntStream.range(0, 1_000_000).boxed().toList();
   *
   * // Under IO, a left fold would overflow the stack long before a million elements
   * Kind<IOKind.Witness, List<String>> labels =
   *     TrampolineUtils.traverseListStackSafe(
   *         numbers, n -> IO_OP.widen(IO.delay(() -> "item-" + n)), Instances.monad(io()));
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
  public static <F extends WitnessArity<TypeArity.Unary>, A, B>
      Kind<F, List<B>> traverseListStackSafe(
          final List<A> list,
          final Function<? super A, ? extends Kind<F, ? extends B>> f,
          final Applicative<F> applicative) {

    if (list.isEmpty()) {
      return applicative.of(Collections.emptyList());
    }
    return applicative.map(TrampolineUtils::flatten, combine(list, 0, list.size(), f, applicative));
  }

  /** The results of a range of effects, joined in constant time and flattened once at the end. */
  private sealed interface Tree<B> permits Leaf, Branch {}

  private record Leaf<B>(B value) implements Tree<B> {}

  private record Branch<B>(Tree<B> left, Tree<B> right) implements Tree<B> {}

  /**
   * Combines the effects for {@code list[from, to)} as a balanced tree, left half first.
   *
   * @param list The list to traverse
   * @param from The first index of the range, inclusive
   * @param to The last index of the range, exclusive; greater than {@code from}
   * @param f The effectful function
   * @param applicative The applicative instance
   * @param <F> The applicative effect type witness
   * @param <A> Input element type
   * @param <B> Output element type
   * @return The combined effect, nested about log<sub>2</sub>(to - from) deep
   */
  private static <F extends WitnessArity<TypeArity.Unary>, A, B> Kind<F, Tree<B>> combine(
      final List<A> list,
      final int from,
      final int to,
      final Function<? super A, ? extends Kind<F, ? extends B>> f,
      final Applicative<F> applicative) {
    if (to - from == 1) {
      return applicative.map(Leaf::new, f.apply(list.get(from)));
    }
    final int middle = (from + to) >>> 1;
    final Kind<F, Tree<B>> left = combine(list, from, middle, f, applicative);
    final Kind<F, Tree<B>> right = combine(list, middle, to, f, applicative);
    return applicative.map2(left, right, Branch::new);
  }

  private static <B> List<B> flatten(final Tree<B> tree) {
    final List<B> values = new ArrayList<>();
    final Deque<Tree<B>> pending = new ArrayDeque<>();
    pending.push(tree);
    while (!pending.isEmpty()) {
      switch (pending.pop()) {
        case Leaf<B>(B value) -> values.add(value);
        case Branch<B>(Tree<B> left, Tree<B> right) -> {
          pending.push(right);
          pending.push(left);
        }
      }
    }
    return Collections.unmodifiableList(values);
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
   * @return All effects sequenced into a single effect containing a list, unmodifiable as {@link
   *     #traverseListStackSafe} returns it
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A> Kind<F, List<A>> sequenceStackSafe(
      final List<Kind<F, A>> effects, final Applicative<F> applicative) {

    return traverseListStackSafe(effects, Function.identity(), applicative);
  }
}
