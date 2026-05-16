// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKindHelper;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.expression.ForPath;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Solution for Tutorial03 ForTraverseComprehension — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
 */
@DisplayName("Tutorial 03: For Traverse Comprehension (Solutions)")
public class Tutorial03_ForTraverseComprehension_Solution {

  private final MonadZero<MaybeKind.Witness> maybeMonad = Instances.monadZero(maybe());
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

    /**
     * Why this is idiomatic: {@code traverse} flips a {@code List<Maybe<Integer>>} into a {@code
     * Maybe<List<Integer>>}. One missing parse takes down the whole list — exactly the "all or
     * nothing" semantics the result type promises.
     *
     * <p>Alternative: {@code list.stream().map(safeParseInt).collect(...)} and post-process the
     * collected {@code Maybe}s. Same answer; {@code traverse} is the named, fused version.
     *
     * <p>Common wrong attempt: catch the {@code NumberFormatException} and substitute a default.
     * The list now lies — every element looks parsed even when the input was rubbish. Let the empty
     * {@code Maybe} surface the failure.
     */
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

    /**
     * Why this is idiomatic: a single {@code Nothing} in the middle of the list short-circuits the
     * whole traversal. {@code traverse} preserves the monad's failure semantics — the combined
     * result is empty whenever any element is empty.
     *
     * <p>Alternative: filter out unparseable strings before traversing. That hides the failure
     * silently; reach for it only when partial results are explicitly acceptable.
     *
     * <p>Common wrong attempt: assume {@code traverse} returns a list with the failed element
     * elided. It does not — the list as a whole succeeds or fails together.
     */
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

    /**
     * Why this is idiomatic: {@code from} produces an intermediate value, {@code traverse} walks
     * it. Each step is named, the tuple parameter on the extractor lets the {@code traverse} reach
     * back to the previous binding.
     *
     * <p>Alternative: pre-build the list outside the comprehension and pass it as the initial
     * source. Equivalent for static lists; the {@code from}-then-{@code traverse} shape stays
     * declarative when the list is computed from earlier bindings.
     *
     * <p>Common wrong attempt: try to access the previous step by name (e.g. {@code a}) inside the
     * extractor. Comprehension bindings live in a tuple — pull them out via {@code t._1()} / {@code
     * t._2()} or accept a tuple-typed lambda parameter.
     */
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

    /**
     * Why this is idiomatic: {@code sequence} is {@code traverse} with the identity element
     * function — every input is already in the target monad, so all that remains is to flip the
     * structure. {@code List<Maybe<Int>>} becomes {@code Maybe<List<Int>>}.
     *
     * <p>Alternative: {@code traverse(...)} with {@code Function.identity()} explicitly. Same
     * answer; {@code sequence} is the conventional name for "I already have the effects, just
     * combine them".
     *
     * <p>Common wrong attempt: try to {@code map} over the list to extract values. The values are
     * wrapped in {@code Maybe}; the only way to get a single {@code Maybe<List>} is to sequence (or
     * traverse-with-identity).
     */
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

    /**
     * Why this is idiomatic: {@code let} after {@code traverse} captures a pure derived value (the
     * running total) without leaving the comprehension. The lambda receives a tuple of (original,
     * traversed) so both are visible to the summary.
     *
     * <p>Alternative: extract the traversed list, compute the total outside the comprehension, and
     * call {@code yield} with both. Same outcome; {@code let} keeps the derivation inline for
     * readers who want to see it next to the traversal.
     *
     * <p>Common wrong attempt: try to {@code traverse} a second time to compute the sum. Traverse
     * expects an effect at every step; for a pure summary use {@code let}.
     */
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

    /**
     * Why this is idiomatic: {@code when} adds a guard against the traversed result. The predicate
     * runs once on the whole traversed list; the guard either passes (the comprehension keeps
     * going) or fails (the comprehension collapses to {@code Nothing}).
     *
     * <p>Alternative: chain a {@code let} that computes the total, then a {@code when} on that
     * binding. Equivalent; reach for the chained form when the predicate is expensive and the value
     * is reused.
     *
     * <p>Common wrong attempt: filter inside the traversal's effect function. Each element is
     * processed independently, so filtering before reduction does not let you guard on the combined
     * result.
     */
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

    /**
     * Why this is idiomatic: the symmetric counterpart — when the {@code when} predicate returns
     * {@code false}, the comprehension's empty branch fires. The traversal still completed; the
     * guard refused the result on a downstream condition.
     *
     * <p>Alternative: assert on the result outside the comprehension and throw on failure.
     * Equivalent semantics for a test; in production code the typed empty result lets callers keep
     * composing.
     *
     * <p>Common wrong attempt: assume {@code when} reverts to a previous binding when it fails. It
     * does not — the entire comprehension becomes {@code Nothing}; bind a fallback later with
     * {@code recover} or move the guard up so it runs before any work.
     */
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

    /**
     * Why this is idiomatic: {@code flatTraverse} runs an effectful function that returns a list
     * per element and concatenates the results — the fused {@code traverse}-then- {@code flatten}.
     * {@code [1, 2, 3]} becomes {@code [1, 10, 2, 20, 3, 30]} in one pass.
     *
     * <p>Alternative: {@code traverse} into a {@code List<List<Integer>>} and flatten with {@code
     * stream().flatMap(List::stream).toList()}. Equivalent; one combined call is tidier when the
     * flattening is the whole point.
     *
     * <p>Common wrong attempt: write the function to return a flat list and call {@code traverse}.
     * The element function would have to take responsibility for combining — {@code flatTraverse}
     * keeps that out of the per-element logic.
     */
    @Test
    @DisplayName("Each element produces a list; flatten the result")
    void exercise7_flatTraverse() {
      // SOLUTION: flatTraverse traverses each element with a function that returns
      // Maybe<List<Int>>, then flattens the nested List<List<Int>> into List<Int>.
      Kind<MaybeKind.Witness, List<Integer>> result =
          For.from(maybeMonad, MAYBE.just(Arrays.asList(1, 2, 3)))
              .flatTraverse(
                  listTraverse,
                  Instances.monadZero(list()),
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

    /**
     * Why this is idiomatic: {@code yield} sees both the original list and the traversed result;
     * building the summary uses both without re-reading any source. The comprehension keeps the
     * original visible all the way to the end.
     *
     * <p>Alternative: capture the original input in an outer {@code let} and ignore the yielded
     * tuple binding. Same answer; the tuple-shaped {@code yield} is the canonical spelling.
     *
     * <p>Common wrong attempt: compute the original-vs-traversed comparison inside the traversal's
     * element function. It cannot see the rest of the list mid-traverse; decisions about the whole
     * list belong in {@code let} or {@code yield}.
     */
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

    /**
     * Why this is idiomatic: inside a {@code ForPath} comprehension, {@code traverse} works exactly
     * the same way as {@code For} — the path-shaped wrapper just changes the run surface. Same
     * combinator, different effect type.
     *
     * <p>Alternative: drop down to the underlying {@code For} comprehension. Doable; the {@code
     * ForPath} variant keeps the result a {@code MaybePath}, which composes with other path values
     * without an extra widen step.
     *
     * <p>Common wrong attempt: forget that {@code MaybePath.run()} is needed to extract the
     * underlying {@code Maybe}. The comprehension result is a description; only {@code run}
     * actually executes it.
     */
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

    /**
     * Why this is idiomatic: every element produces an {@code Either<String, Integer>}; {@code
     * traverse} flips that into an {@code Either<String, List<Integer>>} — typed error preserved,
     * success collected.
     *
     * <p>Alternative: short-circuit by hand with a {@code for} loop and an early {@code return}.
     * Equivalent; the loop ties the failure handling to the iteration mechanics, while {@code
     * traverse} keeps it in the type.
     *
     * <p>Common wrong attempt: assume widening is automatic. {@code Either.right(...)} must be
     * widened to {@code Kind} via {@code EITHER.widen} for the comprehension to consume it; the
     * helper makes this one line.
     */
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

    /**
     * Why this is idiomatic: a {@code Left} on any element collapses the traverse to that {@code
     * Left}. The remaining elements are not processed — short-circuiting carries all the way up to
     * the {@code EitherPath} result.
     *
     * <p>Alternative: collect every error with {@code Validated} and a semigroup. Pick that for
     * "show all errors"; pick {@code traverse} on {@code Either} when the first failure is enough.
     *
     * <p>Common wrong attempt: assume the rest of the list still runs and you can read the partial
     * successes from somewhere. Once an element returns {@code Left}, downstream elements never see
     * the traverse — there is no partial result to recover.
     */
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

    /**
     * Why this is idiomatic: the per-item function parses, then maps the parse result into a {@code
     * ValidatedItem}. {@code traverse} aggregates; {@code let} computes the total; {@code yield}
     * writes the summary. Each step does one job.
     *
     * <p>Alternative: {@code stream().map(...).reduce(...)} the items eagerly and short-circuit by
     * throwing on a bad parse. Same total in the happy path; loses the typed "all-or-nothing"
     * answer the {@code Maybe} carries.
     *
     * <p>Common wrong attempt: skip {@code let} and recompute the total from the traversed list
     * inside {@code yield}. Possible, but the comprehension's named bindings exist precisely so the
     * projection only spells out the final string.
     */
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

    /**
     * Why this is idiomatic: a single unparseable price collapses the entire validation to {@code
     * Nothing}. The summary line is never built, the total is never asked for — exactly the failure
     * semantics a strict order-validation step needs.
     *
     * <p>Alternative: collect every bad item and report all of them at once with {@code Validated}.
     * Pick that when the user wants to fix all the errors before resubmitting; pick this fail-fast
     * spelling when "first error wins" is enough.
     *
     * <p>Common wrong attempt: assume the items before the bad one are still validated. They are
     * processed, but {@code Maybe} discards their results when the traverse short- circuits — there
     * is no partial list to read from.
     */
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
