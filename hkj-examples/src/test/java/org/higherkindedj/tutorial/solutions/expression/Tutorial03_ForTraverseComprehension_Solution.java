// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.expression;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListMonad;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 03: For Traverse Comprehension (SOLUTIONS)
 *
 * <p>This file contains the complete solutions for Tutorial 03. Each exercise shows the correct
 * implementation with an explanatory SOLUTION comment.
 */
@DisplayName("Tutorial 03: For Traverse Comprehension (Solutions)")
public class Tutorial03_ForTraverseComprehension_Solution {

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

  @Nested
  @DisplayName("Exercise 1: Basic traverse")
  class Exercise1 {

    @Test
    @DisplayName("Traverse a list of strings, parsing each to Integer with Maybe")
    void exercise1_basicTraverse() {
      List<String> inputs = Arrays.asList("1", "2", "3");

      // SOLUTION: Use For.from() with traverse to parse each string to an Integer.
      // The extractor converts the List<String> to Kind<ListKind.Witness, String>,
      // and the function applies safeParseInt to each element.
      Kind<MaybeKind.Witness, List<Integer>> result =
          For.from(maybeMonad, MAYBE.just(inputs))
              .traverse(listTraverse, list -> LIST.widen(list), (String s) -> safeParseInt(s))
              .yield((original, traversed) -> LIST.narrow(traversed));

      Maybe<List<Integer>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).containsExactly(1, 2, 3);
    }
  }

  // =========================================================================
  // Exercise 2: Traverse short-circuit
  // =========================================================================

  @Nested
  @DisplayName("Exercise 2: Traverse short-circuit")
  class Exercise2 {

    @Test
    @DisplayName("One Nothing short-circuits the whole traverse")
    void exercise2_traverseShortCircuit() {
      List<String> inputs = Arrays.asList("1", "oops", "3");

      // SOLUTION: When any element produces Nothing, the entire traverse
      // short-circuits to Nothing. Here "oops" cannot be parsed.
      Kind<MaybeKind.Witness, List<Integer>> result =
          For.from(maybeMonad, MAYBE.just(inputs))
              .traverse(listTraverse, list -> LIST.widen(list), (String s) -> safeParseInt(s))
              .yield((original, traversed) -> LIST.narrow(traversed));

      Maybe<List<Integer>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isFalse();
    }
  }

  // =========================================================================
  // Exercise 3: Traverse after from
  // =========================================================================

  @Nested
  @DisplayName("Exercise 3: Traverse after from")
  class Exercise3 {

    @Test
    @DisplayName("Chain .from() then .traverse() on a generated step")
    void exercise3_traverseAfterFrom() {
      // SOLUTION: Use .from() to produce a list, then .traverse() it.
      // The extractor accesses the second element of the tuple (the list) via t._2().
      Kind<MaybeKind.Witness, String> result =
          For.from(maybeMonad, MAYBE.just(10))
              .from(a -> MAYBE.just(Arrays.asList(a, a + 1, a + 2)))
              .traverse(listTraverse, t -> LIST.widen(t._2()), (Integer i) -> MAYBE.just(i * 2))
              .yield(
                  (a, list, traversed) -> {
                    List<Integer> doubled = LIST.narrow(traversed);
                    return a + " -> " + doubled;
                  });

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("10 -> [20, 22, 24]");
    }
  }

  // =========================================================================
  // Exercise 4: Sequence
  // =========================================================================

  @Nested
  @DisplayName("Exercise 4: Sequence")
  class Exercise4 {

    @Test
    @DisplayName("Sequence a list of pre-built monadic values")
    void exercise4_sequence() {
      List<Kind<MaybeKind.Witness, Integer>> listOfMaybes =
          Arrays.asList(MAYBE.just(10), MAYBE.just(20), MAYBE.just(30));
      Kind<ListKind.Witness, Kind<MaybeKind.Witness, Integer>> kindList = LIST.widen(listOfMaybes);

      // SOLUTION: sequence turns List<Maybe<Int>> into Maybe<List<Int>>.
      // It is equivalent to traverse with the identity function.
      Kind<MaybeKind.Witness, List<Integer>> result =
          For.from(maybeMonad, MAYBE.just(kindList))
              .sequence(listTraverse, Function.identity())
              .yield((original, sequenced) -> LIST.narrow(sequenced));

      Maybe<List<Integer>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).containsExactly(10, 20, 30);
    }
  }

  // =========================================================================
  // Exercise 5: Traverse + let
  // =========================================================================

  @Nested
  @DisplayName("Exercise 5: Traverse + let")
  class Exercise5 {

    @Test
    @DisplayName("Use .let() after .traverse() to compute a summary")
    void exercise5_traverseWithLet() {
      List<String> prices = Arrays.asList("10", "20", "30");

      // SOLUTION: After traverse, chain .let() to compute a derived value.
      // The let function receives a Tuple2 of (original, traversed).
      Kind<MaybeKind.Witness, String> result =
          For.from(maybeMonad, MAYBE.just(prices))
              .traverse(listTraverse, list -> LIST.widen(list), (String s) -> safeParseInt(s))
              .let(
                  t -> {
                    List<Integer> parsed = LIST.narrow(t._2());
                    return parsed.stream().mapToInt(Integer::intValue).sum();
                  })
              .yield((original, traversed, total) -> "Total: " + total);

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("Total: 60");
    }
  }

  // =========================================================================
  // Exercise 6: Traverse + when guard
  // =========================================================================

  @Nested
  @DisplayName("Exercise 6: Traverse + when guard")
  class Exercise6 {

    @Test
    @DisplayName("Filter after traverse using a predicate on result - passes")
    void exercise6_traverseWithWhenPass() {
      List<String> prices = Arrays.asList("10", "20", "30");

      // SOLUTION: Use fromFilterable to enable when(). After traverse,
      // apply when() with a predicate on the tuple to guard the result.
      Kind<MaybeKind.Witness, List<Integer>> result =
          For.from(maybeMonad, MAYBE.just(prices))
              .traverse(listTraverse, list -> LIST.widen(list), (String s) -> safeParseInt(s))
              .when(
                  t -> {
                    List<Integer> parsed = LIST.narrow(t._2());
                    return parsed.stream().mapToInt(Integer::intValue).sum() > 50;
                  })
              .yield((original, traversed) -> LIST.narrow(traversed));

      Maybe<List<Integer>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).containsExactly(10, 20, 30);
    }

    @Test
    @DisplayName("Filter after traverse using a predicate on result - fails")
    void exercise6_traverseWithWhenFail() {
      List<String> prices = Arrays.asList("10", "20", "30");

      // SOLUTION: When the guard predicate returns false, the result is Nothing.
      Kind<MaybeKind.Witness, List<Integer>> result =
          For.from(maybeMonad, MAYBE.just(prices))
              .traverse(listTraverse, list -> LIST.widen(list), (String s) -> safeParseInt(s))
              .when(
                  t -> {
                    List<Integer> parsed = LIST.narrow(t._2());
                    return parsed.stream().mapToInt(Integer::intValue).sum() > 100;
                  })
              .yield((original, traversed) -> LIST.narrow(traversed));

      Maybe<List<Integer>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isFalse();
    }
  }

  // =========================================================================
  // Exercise 7: flatTraverse
  // =========================================================================

  @Nested
  @DisplayName("Exercise 7: flatTraverse")
  class Exercise7 {

    @Test
    @DisplayName("Each element produces a list; flatten the result")
    void exercise7_flatTraverse() {
      // SOLUTION: flatTraverse traverses each element with a function that returns
      // Maybe<List<Int>>, then flattens the nested List<List<Int>> into List<Int>.
      Kind<MaybeKind.Witness, List<Integer>> result =
          For.from(maybeMonad, MAYBE.just(Arrays.asList(1, 2, 3)))
              .flatTraverse(
                  listTraverse,
                  ListMonad.INSTANCE,
                  list -> LIST.widen(list),
                  (Integer i) ->
                      MAYBE.<Kind<ListKind.Witness, Integer>>just(
                          LIST.widen(Arrays.asList(i, i * 10))))
              .yield((original, traversed) -> LIST.narrow(traversed));

      Maybe<List<Integer>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).containsExactly(1, 10, 2, 20, 3, 30);
    }
  }

  // =========================================================================
  // Exercise 8: Traverse + yield
  // =========================================================================

  @Nested
  @DisplayName("Exercise 8: Traverse + yield")
  class Exercise8 {

    @Test
    @DisplayName("Project the final tuple including the traversed result")
    void exercise8_traverseWithYield() {
      // SOLUTION: The yield function receives both the original value and the
      // traversed result. Use both to build the final output.
      Kind<MaybeKind.Witness, String> result =
          For.from(maybeMonad, MAYBE.just(Arrays.asList("a", "b", "c")))
              .traverse(
                  listTraverse, list -> LIST.widen(list), (String s) -> MAYBE.just(s.toUpperCase()))
              .yield(
                  (original, traversed) -> {
                    List<String> upper = LIST.narrow(traversed);
                    return "Original: " + original + ", Uppercased: " + upper;
                  });

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("Original: [a, b, c], Uppercased: [A, B, C]");
    }
  }

  // =========================================================================
  // Exercise 9: ForPath traverse (MaybePath)
  // =========================================================================

  @Nested
  @DisplayName("Exercise 9: ForPath traverse (MaybePath)")
  class Exercise9 {

    @Test
    @DisplayName("Traverse within a MaybePath comprehension")
    void exercise9_forPathMaybeTraverse() {
      // SOLUTION: ForPath.from() creates a MaybePath comprehension.
      // The traverse applies an effectful function to each element.
      MaybePath<List<Integer>> result =
          ForPath.from(Path.just(Arrays.asList(1, 2, 3)))
              .traverse(listTraverse, list -> LIST.widen(list), (Integer i) -> MAYBE.just(i * 2))
              .yield((original, traversed) -> LIST.narrow(traversed));

      Maybe<List<Integer>> maybe = result.run();
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).containsExactly(2, 4, 6);
    }
  }

  // =========================================================================
  // Exercise 10: ForPath traverse (EitherPath)
  // =========================================================================

  @Nested
  @DisplayName("Exercise 10: ForPath traverse (EitherPath)")
  class Exercise10 {

    @Test
    @DisplayName("Traverse within an EitherPath comprehension")
    void exercise10_forPathEitherTraverse() {
      // SOLUTION: EitherPath traverse uses Either.right() for each element,
      // widened to Kind via EitherKindHelper.
      EitherPath<String, List<Integer>> result =
          ForPath.from(Path.<String, List<Integer>>right(Arrays.asList(1, 2, 3)))
              .traverse(
                  listTraverse,
                  list -> LIST.widen(list),
                  (Integer i) ->
                      EitherKindHelper.EITHER.widen(Either.<String, Integer>right(i * 2)))
              .yield((original, traversed) -> LIST.narrow(traversed));

      Either<String, List<Integer>> either = result.run();
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("EitherPath traverse short-circuits on Left")
    void exercise10_forPathEitherTraverseLeft() {
      // SOLUTION: When any element returns Left, the whole traverse fails.
      EitherPath<String, List<Integer>> result =
          ForPath.from(Path.<String, List<Integer>>right(Arrays.asList(1, 2, 3)))
              .traverse(
                  listTraverse,
                  list -> LIST.widen(list),
                  (Integer i) ->
                      i == 2
                          ? EitherKindHelper.EITHER.widen(
                              Either.<String, Integer>left("error at 2"))
                          : EitherKindHelper.EITHER.widen(Either.<String, Integer>right(i * 2)))
              .yield((original, traversed) -> LIST.narrow(traversed));

      Either<String, List<Integer>> either = result.run();
      assertThat(either.isLeft()).isTrue();
    }
  }

  // =========================================================================
  // Exercise 11: Real-world: validate order items
  // =========================================================================

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

      // SOLUTION: Combine traverse with let to validate each item's price,
      // collect the validated items, and compute a total.
      Kind<MaybeKind.Witness, String> result =
          For.from(maybeMonad, MAYBE.just(items))
              .traverse(
                  listTraverse,
                  list -> LIST.widen(list),
                  (OrderItem item) -> {
                    Kind<MaybeKind.Witness, Integer> parsed = safeParseInt(item.priceText());
                    return maybeMonad.map(price -> new ValidatedItem(item.name(), price), parsed);
                  })
              .let(
                  t -> {
                    List<ValidatedItem> validated = LIST.narrow(t._2());
                    return validated.stream().mapToInt(ValidatedItem::price).sum();
                  })
              .yield(
                  (items_, validated, total) ->
                      "Items: " + LIST.narrow(validated).size() + ", Total: " + total);

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

      // SOLUTION: One invalid price causes the entire validation to fail.
      Kind<MaybeKind.Witness, String> result =
          For.from(maybeMonad, MAYBE.just(items))
              .traverse(
                  listTraverse,
                  list -> LIST.widen(list),
                  (OrderItem item) -> {
                    Kind<MaybeKind.Witness, Integer> parsed = safeParseInt(item.priceText());
                    return maybeMonad.map(price -> new ValidatedItem(item.name(), price), parsed);
                  })
              .let(
                  t -> {
                    List<ValidatedItem> validated = LIST.narrow(t._2());
                    return validated.stream().mapToInt(ValidatedItem::price).sum();
                  })
              .yield(
                  (items_, validated, total) ->
                      "Items: " + LIST.narrow(validated).size() + ", Total: " + total);

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isFalse();
    }
  }
}
