// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.Arrays;
import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 03: For Traverse Comprehension - Traversing Collections in Effectful Contexts
 *
 * <p>In this tutorial, you'll learn how to use {@code traverse}, {@code sequence}, and {@code
 * flatTraverse} within for-comprehensions. These operations let you apply an effectful function to
 * every element of a collection, collecting the results while respecting the surrounding monadic
 * context (e.g., short-circuiting on failure).
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>{@code traverse}: apply an effectful function to each element and collect results
 *   <li>{@code sequence}: flip a collection of monadic values into a monadic collection
 *   <li>{@code flatTraverse}: traverse then flatten nested inner collections
 *   <li>Short-circuit behaviour: one failure aborts the whole traversal
 *   <li>Chaining {@code .let()}, {@code .when()}, and {@code .yield()} after traverse
 *   <li>Using traverse with both {@code For} and {@code ForPath} comprehensions
 * </ul>
 *
 * <p>Prerequisites: Complete Tutorials 01 and 02 before this one.
 *
 * <p>Replace each {@code answerRequired()} call with the correct code to make the tests pass.
 */
@DisplayName("Tutorial 03: For Traverse Comprehension")
public class Tutorial03_ForTraverseComprehension {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new UnsupportedOperationException("Replace this with your answer");
  }

  private final MaybeMonad maybeMonad = MaybeMonad.INSTANCE;
  private final ListTraverse listTraverse = ListTraverse.INSTANCE;

  // --- Domain models for exercises ---

  /** Represents an order item with a name and price string that may need parsing. */
  record OrderItem(String name, String priceText) {}

  /** Represents a validated order item with a parsed numeric price. */
  record ValidatedItem(String name, int price) {}

  /** Helper to parse a string to an Integer within Maybe. */
  private Kind<MaybeKind.Witness, Integer> safeParseInt(String s) {
    try {
      return MAYBE.just(Integer.parseInt(s));
    } catch (NumberFormatException e) {
      return MAYBE.nothing();
    }
  }

  // =========================================================================
  // Exercise 1: Basic traverse
  // =========================================================================

  /**
   * Exercise 1: Basic traverse - parse a list of strings to integers.
   *
   * <p>The {@code traverse} method applies an effectful function to each element of a collection
   * and collects the results. If all applications succeed, you get a monadic value containing the
   * collected list. The extractor converts the initial value into a {@code Kind<T, C>} for the
   * traversable, and the function maps each element into the monad.
   *
   * <p>Task: Use {@code For.from()} with {@code .traverse()} to parse each string to an Integer
   * using {@code safeParseInt}.
   */
  @Nested
  @DisplayName("Exercise 1: Basic traverse")
  class Exercise1 {

    @Test
    @DisplayName("Traverse a list of strings, parsing each to Integer with Maybe")
    void exercise1_basicTraverse() {
      List<String> inputs = Arrays.asList("1", "2", "3");

      // TODO: Replace answerRequired() with a For.from() comprehension that:
      //       1. Starts with MAYBE.just(inputs)
      //       2. Calls .traverse(listTraverse, list -> LIST.widen(list), (String s) ->
      // safeParseInt(s))
      //       3. Yields LIST.narrow(traversed)
      // Hint: .yield((original, traversed) -> LIST.narrow(traversed))
      Kind<MaybeKind.Witness, List<Integer>> result = answerRequired();

      Maybe<List<Integer>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).containsExactly(1, 2, 3);
    }
  }

  // =========================================================================
  // Exercise 2: Traverse short-circuit
  // =========================================================================

  /**
   * Exercise 2: Observe short-circuit behaviour during traverse.
   *
   * <p>When any element's effectful function returns a failure (Nothing for Maybe), the entire
   * traverse short-circuits and produces a failure. This is a key property: you don't get partial
   * results.
   *
   * <p>Task: Traverse a list where one element fails to parse, and verify that the whole result is
   * Nothing.
   */
  @Nested
  @DisplayName("Exercise 2: Traverse short-circuit")
  class Exercise2 {

    @Test
    @DisplayName("One Nothing short-circuits the whole traverse")
    void exercise2_traverseShortCircuit() {
      List<String> inputs = Arrays.asList("1", "oops", "3");

      // TODO: Replace answerRequired() with the same pattern as Exercise 1,
      //       but using this inputs list where "oops" cannot be parsed.
      // Hint: The structure is identical to Exercise 1; only the input data differs
      Kind<MaybeKind.Witness, List<Integer>> result = answerRequired();

      Maybe<List<Integer>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isFalse();
    }
  }

  // =========================================================================
  // Exercise 3: Traverse after from
  // =========================================================================

  /**
   * Exercise 3: Chain {@code .from()} then {@code .traverse()} on a generated step.
   *
   * <p>You can chain traverse after a {@code .from()} step. The extractor at step 2 receives a
   * tuple, so you access the second element using {@code t._2()} to get the list produced by {@code
   * from()}.
   *
   * <p>Task: Start with {@code MAYBE.just(10)}, use {@code .from()} to generate a list {@code [10,
   * 11, 12]}, then traverse to double each element.
   */
  @Nested
  @DisplayName("Exercise 3: Traverse after from")
  class Exercise3 {

    @Test
    @DisplayName("Chain .from() then .traverse() on a generated step")
    void exercise3_traverseAfterFrom() {
      // TODO: Replace answerRequired() with a comprehension that:
      //       1. For.from(maybeMonad, MAYBE.just(10))
      //       2. .from(a -> MAYBE.just(Arrays.asList(a, a + 1, a + 2)))
      //       3. .traverse(listTraverse, t -> LIST.widen(t._2()), (Integer i) -> MAYBE.just(i * 2))
      //       4. .yield((a, list, traversed) -> a + " -> " + LIST.narrow(traversed))
      // Hint: The extractor t -> LIST.widen(t._2()) extracts the list from the tuple
      Kind<MaybeKind.Witness, String> result = answerRequired();

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("10 -> [20, 22, 24]");
    }
  }

  // =========================================================================
  // Exercise 4: Sequence
  // =========================================================================

  /**
   * Exercise 4: Sequence a list of pre-built monadic values.
   *
   * <p>While {@code traverse} applies a function to each element, {@code sequence} simply flips the
   * structure: it turns a {@code List<Maybe<Int>>} into a {@code Maybe<List<Int>>}. This is useful
   * when you already have a collection of monadic values.
   *
   * <p>Task: Use {@code .sequence()} to collect a list of Maybe values into a single Maybe list.
   */
  @Nested
  @DisplayName("Exercise 4: Sequence")
  class Exercise4 {

    @Test
    @DisplayName("Sequence a list of pre-built monadic values")
    void exercise4_sequence() {
      List<Kind<MaybeKind.Witness, Integer>> listOfMaybes =
          Arrays.asList(MAYBE.just(10), MAYBE.just(20), MAYBE.just(30));
      Kind<ListKind.Witness, Kind<MaybeKind.Witness, Integer>> kindList = LIST.widen(listOfMaybes);

      // TODO: Replace answerRequired() with a comprehension that:
      //       1. For.from(maybeMonad, MAYBE.just(kindList))
      //       2. .sequence(listTraverse, Function.identity())
      //       3. .yield((original, sequenced) -> LIST.narrow(sequenced))
      // Hint: sequence is like traverse with the identity function
      Kind<MaybeKind.Witness, List<Integer>> result = answerRequired();

      Maybe<List<Integer>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).containsExactly(10, 20, 30);
    }
  }

  // =========================================================================
  // Exercise 5: Traverse + let
  // =========================================================================

  /**
   * Exercise 5: Use {@code .let()} after {@code .traverse()} to compute a summary.
   *
   * <p>After traverse produces a collected result, you can chain {@code .let()} to derive a new
   * value from it. The let function receives a tuple of all prior values, so you access the
   * traversed result at the appropriate position.
   *
   * <p>Task: Parse a list of price strings, then use let to compute their sum.
   */
  @Nested
  @DisplayName("Exercise 5: Traverse + let")
  class Exercise5 {

    @Test
    @DisplayName("Use .let() after .traverse() to compute a summary")
    void exercise5_traverseWithLet() {
      List<String> prices = Arrays.asList("10", "20", "30");

      // TODO: Replace answerRequired() with a comprehension that:
      //       1. For.from(maybeMonad, MAYBE.just(prices))
      //       2. .traverse(listTraverse, list -> LIST.widen(list), (String s) -> safeParseInt(s))
      //       3. .let(t -> { sum the integers from LIST.narrow(t._2()) })
      //       4. .yield((original, traversed, total) -> "Total: " + total)
      // Hint: Use parsed.stream().mapToInt(Integer::intValue).sum() for the sum
      Kind<MaybeKind.Witness, String> result = answerRequired();

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("Total: 60");
    }
  }

  // =========================================================================
  // Exercise 6: Traverse + when guard
  // =========================================================================

  /**
   * Exercise 6: Filter after traverse using a predicate on the result.
   *
   * <p>Using {@code For.from()} instead of {@code For.from()} enables the {@code .when()} guard.
   * After traverse, you can apply a predicate to decide whether the result should be kept (Just) or
   * discarded (Nothing).
   *
   * <p>Task: Parse prices, then use when to keep the result only if the sum exceeds 50.
   */
  @Nested
  @DisplayName("Exercise 6: Traverse + when guard")
  class Exercise6 {

    @Test
    @DisplayName("Filter after traverse using a predicate on result - passes")
    void exercise6_traverseWithWhenPass() {
      List<String> prices = Arrays.asList("10", "20", "30");

      // TODO: Replace answerRequired() with a comprehension that:
      //       1. For.from(maybeMonad, MAYBE.just(prices))
      //       2. .traverse(listTraverse, list -> LIST.widen(list), (String s) -> safeParseInt(s))
      //       3. .when(t -> { sum from LIST.narrow(t._2()) is > 50 })
      //       4. .yield((original, traversed) -> LIST.narrow(traversed))
      // Hint: Use For.from() to enable when(); the predicate receives a Tuple2
      Kind<MaybeKind.Witness, List<Integer>> result = answerRequired();

      Maybe<List<Integer>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).containsExactly(10, 20, 30);
    }

    @Test
    @DisplayName("Filter after traverse using a predicate on result - fails")
    void exercise6_traverseWithWhenFail() {
      List<String> prices = Arrays.asList("10", "20", "30");

      // TODO: Replace answerRequired() with the same pattern, but with a threshold > 100
      //       so the guard fails and produces Nothing.
      // Hint: Change the sum threshold in the when() predicate to > 100
      Kind<MaybeKind.Witness, List<Integer>> result = answerRequired();

      Maybe<List<Integer>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isFalse();
    }
  }

  // =========================================================================
  // Exercise 7: flatTraverse
  // =========================================================================

  /**
   * Exercise 7: Use flatTraverse to expand and flatten.
   *
   * <p>{@code flatTraverse} is like traverse, but each element's function returns a monadic value
   * containing a collection. After traversal, the nested collections are flattened into a single
   * list. This is useful when each input element maps to multiple output elements.
   *
   * <p>Task: Given {@code [1, 2, 3]}, produce {@code [1, 10, 2, 20, 3, 30]} by mapping each element
   * to {@code [i, i * 10]}.
   */
  @Nested
  @DisplayName("Exercise 7: flatTraverse")
  class Exercise7 {

    @Test
    @DisplayName("Each element produces a list; flatten the result")
    void exercise7_flatTraverse() {
      // TODO: Replace answerRequired() with a comprehension that:
      //       1. For.from(maybeMonad, MAYBE.just(Arrays.asList(1, 2, 3)))
      //       2. .flatTraverse(listTraverse, ListMonad.INSTANCE, list -> LIST.widen(list),
      //            (Integer i) -> MAYBE.<Kind<ListKind.Witness, Integer>>just(
      //                LIST.widen(Arrays.asList(i, i * 10))))
      //       3. .yield((original, traversed) -> LIST.narrow(traversed))
      // Hint: flatTraverse needs both a Traverse and an inner Monad for the flattening
      Kind<MaybeKind.Witness, List<Integer>> result = answerRequired();

      Maybe<List<Integer>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).containsExactly(1, 10, 2, 20, 3, 30);
    }
  }

  // =========================================================================
  // Exercise 8: Traverse + yield
  // =========================================================================

  /**
   * Exercise 8: Project the final tuple including the traversed result.
   *
   * <p>The yield function after traverse receives both the original value and the traversed result.
   * You can use both to build an informative final output.
   *
   * <p>Task: Traverse a list of strings to uppercase them, then yield a string showing both the
   * original and uppercased versions.
   */
  @Nested
  @DisplayName("Exercise 8: Traverse + yield")
  class Exercise8 {

    @Test
    @DisplayName("Project the final tuple including the traversed result")
    void exercise8_traverseWithYield() {
      // TODO: Replace answerRequired() with a comprehension that:
      //       1. For.from(maybeMonad, MAYBE.just(Arrays.asList("a", "b", "c")))
      //       2. .traverse(listTraverse, list -> LIST.widen(list),
      //            (String s) -> MAYBE.just(s.toUpperCase()))
      //       3. .yield((original, traversed) ->
      //            "Original: " + original + ", Uppercased: " + LIST.narrow(traversed))
      // Hint: The yield function receives both values; build a descriptive string
      Kind<MaybeKind.Witness, String> result = answerRequired();

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("Original: [a, b, c], Uppercased: [A, B, C]");
    }
  }

  // =========================================================================
  // Exercise 9: ForPath traverse (MaybePath)
  // =========================================================================

  /**
   * Exercise 9: Traverse within a MaybePath comprehension.
   *
   * <p>{@code ForPath} provides the same traverse capability but within the Path abstraction. For
   * MaybePath, the effectful function returns values in the Maybe monad, and the result is a
   * MaybePath containing the collected list.
   *
   * <p>Task: Use {@code ForPath.from()} with {@code Path.just()} to traverse and double each
   * element.
   */
  @Nested
  @DisplayName("Exercise 9: ForPath traverse (MaybePath)")
  class Exercise9 {

    @Test
    @DisplayName("Traverse within a MaybePath comprehension")
    void exercise9_forPathMaybeTraverse() {
      // TODO: Replace answerRequired() with:
      //       ForPath.from(Path.just(Arrays.asList(1, 2, 3)))
      //           .traverse(listTraverse, list -> LIST.widen(list),
      //               (Integer i) -> MAYBE.just(i * 2))
      //           .yield((original, traversed) -> LIST.narrow(traversed))
      // Hint: ForPath.from(Path.just(...)) creates a MaybePath comprehension
      MaybePath<List<Integer>> result = answerRequired();

      Maybe<List<Integer>> maybe = result.run();
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).containsExactly(2, 4, 6);
    }
  }

  // =========================================================================
  // Exercise 10: ForPath traverse (EitherPath)
  // =========================================================================

  /**
   * Exercise 10: Traverse within an EitherPath comprehension.
   *
   * <p>For EitherPath, the effectful function returns values via {@code Path.right()} (success) or
   * {@code Path.left()} (failure). Note the use of {@code .runKind()} to extract the Kind value
   * from the Path for the traverse function.
   *
   * <p>Task: Use {@code ForPath.from()} with {@code Path.right()} to traverse and double each
   * element. Also observe that a Left short-circuits.
   */
  @Nested
  @DisplayName("Exercise 10: ForPath traverse (EitherPath)")
  class Exercise10 {

    @Test
    @DisplayName("Traverse within an EitherPath comprehension")
    void exercise10_forPathEitherTraverse() {
      // TODO: Replace answerRequired() with:
      //       ForPath.from(Path.<String, List<Integer>>right(Arrays.asList(1, 2, 3)))
      //           .traverse(listTraverse, list -> LIST.widen(list),
      //               (Integer i) -> EitherKindHelper.EITHER.widen(Either.<String, Integer>right(i
      // * 2)))
      //           .yield((original, traversed) -> LIST.narrow(traversed))
      // Hint: Use EitherKindHelper.EITHER.widen(Either.right(value)) for each element
      EitherPath<String, List<Integer>> result = answerRequired();

      Either<String, List<Integer>> either = result.run();
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("EitherPath traverse short-circuits on Left")
    void exercise10_forPathEitherTraverseLeft() {
      // TODO: Replace answerRequired() with the same pattern, but return Left for element 2:
      //       (Integer i) -> i == 2
      //           ? EitherKindHelper.EITHER.widen(Either.<String, Integer>left("error at 2"))
      //           : EitherKindHelper.EITHER.widen(Either.<String, Integer>right(i * 2))
      // Hint: One Left causes the entire result to be Left
      EitherPath<String, List<Integer>> result = answerRequired();

      Either<String, List<Integer>> either = result.run();
      assertThat(either.isLeft()).isTrue();
    }
  }

  // =========================================================================
  // Exercise 11: Real-world: validate order items
  // =========================================================================

  /**
   * Exercise 11: Validate and summarise order items using traverse.
   *
   * <p>This exercise combines traverse with let to build a realistic validation pipeline. Each
   * order item has a price as a string that needs parsing. If all prices parse successfully, we
   * compute the total. If any price is invalid, the whole validation fails.
   *
   * <p>Task: Traverse order items, parsing each price. Chain let to compute the total, then yield a
   * summary string.
   */
  @Nested
  @DisplayName("Exercise 11: Real-world - validate order items")
  class Exercise11 {

    @Test
    @DisplayName("Validate and summarise order items using traverse")
    void exercise11_validateOrderItems() {
      List<OrderItem> items =
          Arrays.asList(
              new OrderItem("Widget", "25"),
              new OrderItem("Gadget", "30"),
              new OrderItem("Gizmo", "15"));

      // TODO: Replace answerRequired() with a comprehension that:
      //       1. For.from(maybeMonad, MAYBE.just(items))
      //       2. .traverse(listTraverse, list -> LIST.widen(list), (OrderItem item) -> {
      //            Parse item.priceText() with safeParseInt, then map the result to
      //            create a ValidatedItem(item.name(), price)
      //          })
      //       3. .let(t -> { sum the prices from LIST.narrow(t._2()) })
      //       4. .yield((items_, validated, total) ->
      //            "Items: " + LIST.narrow(validated).size() + ", Total: " + total)
      // Hint: Use maybeMonad.map(price -> new ValidatedItem(item.name(), price), parsed)
      //       to transform each parsed price into a ValidatedItem
      Kind<MaybeKind.Witness, String> result = answerRequired();

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("Items: 3, Total: 70");
    }

    @Test
    @DisplayName("Validate order items with an invalid price")
    void exercise11_validateOrderItemsInvalid() {
      List<OrderItem> items =
          Arrays.asList(
              new OrderItem("Widget", "25"),
              new OrderItem("Gadget", "N/A"),
              new OrderItem("Gizmo", "15"));

      // TODO: Replace answerRequired() with the same validation pipeline as above.
      //       The "N/A" price will cause safeParseInt to return Nothing,
      //       short-circuiting the entire traverse.
      // Hint: Use the exact same code as above; the different input data causes the failure
      Kind<MaybeKind.Witness, String> result = answerRequired();

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isFalse();
    }
  }

  /**
   * Congratulations! You've completed Tutorial 03: For Traverse Comprehension
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>How to use {@code traverse} to apply effectful functions across collections
   *   <li>Short-circuit behaviour when any element fails
   *   <li>Chaining {@code .from()} before {@code .traverse()} with tuple extractors
   *   <li>Using {@code sequence} to flip {@code List<F<A>>} into {@code F<List<A>>}
   *   <li>Deriving values from traversed results with {@code .let()}
   *   <li>Guarding traversed results with {@code .when()} via {@code from (with MonadZero)}
   *   <li>Using {@code flatTraverse} for expand-and-flatten patterns
   *   <li>Combining original and traversed values in {@code .yield()}
   *   <li>Traversing within MaybePath and EitherPath via {@code ForPath}
   *   <li>Building real-world validation pipelines with traverse
   * </ul>
   *
   * <p>Next: Explore the full For comprehension API in the documentation, or try combining traverse
   * with ForState for lens-based state workflows.
   */
}
