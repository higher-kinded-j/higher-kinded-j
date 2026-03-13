// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.expression;

import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKindHelper;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.expression.ForPath;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListMonad;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;

/**
 * Demonstrates traverse, sequence, and flatTraverse within For and ForPath comprehensions.
 *
 * <p>Examples include:
 *
 * <ul>
 *   <li>Traverse with ListTraverse + Maybe monad (success and short-circuit cases)
 *   <li>Sequence: turning List&lt;Id&lt;Integer&gt;&gt; into Id&lt;List&lt;Integer&gt;&gt;
 *   <li>FlatTraverse: traversing with inner list flattening
 *   <li>ForPath traverse with MaybePath
 *   <li>ForPath traverse with EitherPath
 * </ul>
 */
public class ForTraverseComprehensionExample {

  public static void main(String[] args) {
    System.out.println("--- Traverse with ListTraverse + Maybe Monad ---");
    traverseWithMaybeExample();
    System.out.println("\n--- Sequence: List<Id<Integer>> to Id<List<Integer>> ---");
    sequenceExample();
    System.out.println("\n--- FlatTraverse: Traverse and Flatten Inner Lists ---");
    flatTraverseExample();
    System.out.println("\n--- ForPath Traverse with MaybePath ---");
    forPathMaybeTraverseExample();
    System.out.println("\n--- ForPath Traverse with EitherPath ---");
    forPathEitherTraverseExample();
  }

  /**
   * Traverse a list [1, 2, 3] within a Maybe comprehension, doubling each element. Demonstrates
   * both the success case (all elements produce Just) and the short-circuit case (one element
   * produces Nothing, causing the entire result to be Nothing).
   */
  private static void traverseWithMaybeExample() {
    final MaybeMonad maybeMonad = MaybeMonad.INSTANCE;

    // Success case: all elements double successfully
    Kind<MaybeKind.Witness, List<Integer>> successResult =
        For.from(maybeMonad, MAYBE.just(Arrays.asList(1, 2, 3)))
            .traverse(
                ListTraverse.INSTANCE, list -> LIST.widen(list), (Integer i) -> MAYBE.just(i * 2))
            .yield((original, traversed) -> LIST.narrow(traversed));

    Maybe<List<Integer>> successMaybe = MAYBE.narrow(successResult);
    System.out.println("Success (double each): " + successMaybe);
    // Just([2, 4, 6])

    // Short-circuit case: element 2 produces Nothing, so entire result is Nothing
    Kind<MaybeKind.Witness, List<Integer>> failResult =
        For.from(maybeMonad, MAYBE.just(Arrays.asList(1, 2, 3)))
            .traverse(
                ListTraverse.INSTANCE,
                list -> LIST.widen(list),
                (Integer i) -> i == 2 ? MAYBE.<Integer>nothing() : MAYBE.just(i * 2))
            .yield((original, traversed) -> LIST.narrow(traversed));

    Maybe<List<Integer>> failMaybe = MAYBE.narrow(failResult);
    System.out.println("Short-circuit (Nothing at 2): " + failMaybe);
    // Nothing
  }

  /**
   * Turn a List&lt;Id&lt;Integer&gt;&gt; into Id&lt;List&lt;Integer&gt;&gt; using sequence within a
   * For comprehension with IdMonad.
   */
  private static void sequenceExample() {
    final IdMonad idMonad = IdMonad.instance();

    List<Kind<IdKind.Witness, Integer>> listOfIds = Arrays.asList(Id.of(1), Id.of(2), Id.of(3));
    Kind<ListKind.Witness, Kind<IdKind.Witness, Integer>> kindList = LIST.widen(listOfIds);

    Kind<IdKind.Witness, List<Integer>> result =
        For.from(idMonad, Id.of(kindList))
            .sequence(ListTraverse.INSTANCE, Function.identity())
            .yield((original, sequenced) -> LIST.narrow(sequenced));

    List<Integer> list = IdKindHelper.ID.unwrap(result);
    System.out.println("Sequenced List<Id<Integer>> to Id<List<Integer>>: " + list);
    // [1, 2, 3]
  }

  /**
   * Traverse a list where each element produces a sub-list, flattening the result. For example, [1,
   * 2] becomes [1, 10, 2, 20] when each element i maps to [i, i*10].
   */
  private static void flatTraverseExample() {
    final MaybeMonad maybeMonad = MaybeMonad.INSTANCE;

    Kind<MaybeKind.Witness, List<Integer>> result =
        For.from(maybeMonad, MAYBE.just(Arrays.asList(1, 2)))
            .flatTraverse(
                ListTraverse.INSTANCE,
                ListMonad.INSTANCE,
                list -> LIST.widen(list),
                (Integer i) ->
                    MAYBE.<Kind<ListKind.Witness, Integer>>just(
                        LIST.widen(Arrays.asList(i, i * 10))))
            .yield((original, traversed) -> LIST.narrow(traversed));

    Maybe<List<Integer>> maybe = MAYBE.narrow(result);
    System.out.println("FlatTraverse [1, 2] -> [i, i*10]: " + maybe);
    // Just([1, 10, 2, 20])
  }

  /**
   * Use ForPath with MaybePath to traverse a list, doubling each element. Demonstrates the
   * path-based API which provides typed result wrappers.
   */
  private static void forPathMaybeTraverseExample() {
    // Success case
    MaybePath<List<Integer>> successResult =
        ForPath.from(Path.just(Arrays.asList(1, 2, 3)))
            .traverse(
                ListTraverse.INSTANCE, list -> LIST.widen(list), (Integer i) -> MAYBE.just(i * 2))
            .yield((original, traversed) -> LIST.narrow(traversed));

    Maybe<List<Integer>> successMaybe = successResult.run();
    System.out.println("MaybePath traverse (success): " + successMaybe);
    // Just([2, 4, 6])

    // Short-circuit case
    MaybePath<List<Integer>> failResult =
        ForPath.from(Path.just(Arrays.asList(1, 2, 3)))
            .traverse(
                ListTraverse.INSTANCE,
                list -> LIST.widen(list),
                (Integer i) -> i == 2 ? MAYBE.<Integer>nothing() : MAYBE.just(i * 2))
            .yield((original, traversed) -> LIST.narrow(traversed));

    Maybe<List<Integer>> failMaybe = failResult.run();
    System.out.println("MaybePath traverse (short-circuit): " + failMaybe);
    // Nothing
  }

  /**
   * Use ForPath with EitherPath to traverse a list, doubling each element. Demonstrates
   * error-handling semantics where a Left value short-circuits the traversal.
   */
  private static void forPathEitherTraverseExample() {
    // Success case
    EitherPath<String, List<Integer>> successResult =
        ForPath.from(Path.<String, List<Integer>>right(Arrays.asList(1, 2, 3)))
            .traverse(
                ListTraverse.INSTANCE,
                list -> LIST.widen(list),
                (Integer i) -> EitherKindHelper.EITHER.widen(Either.<String, Integer>right(i * 2)))
            .yield((original, traversed) -> LIST.narrow(traversed));

    System.out.println("EitherPath traverse (success): " + successResult.run());
    // Right([2, 4, 6])

    // Short-circuit with Left
    EitherPath<String, List<Integer>> failResult =
        ForPath.from(Path.<String, List<Integer>>right(Arrays.asList(1, 2, 3)))
            .traverse(
                ListTraverse.INSTANCE,
                list -> LIST.widen(list),
                (Integer i) ->
                    i == 2
                        ? EitherKindHelper.EITHER.widen(Either.<String, Integer>left("error at 2"))
                        : EitherKindHelper.EITHER.widen(Either.<String, Integer>right(i * 2)))
            .yield((original, traversed) -> LIST.narrow(traversed));

    System.out.println("EitherPath traverse (short-circuit): " + failResult.run());
    // Left(error at 2)
  }
}
