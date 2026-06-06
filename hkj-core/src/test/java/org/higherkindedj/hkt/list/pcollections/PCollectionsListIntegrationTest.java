// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.list.pcollections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.ListAssert.assertThatList;
import static org.higherkindedj.hkt.assertions.OptionalKindAssert.assertThatOptionalKind;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Alternative;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListSelective;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.pcollections.ConsPStack;
import org.pcollections.PStack;
import org.pcollections.PVector;
import org.pcollections.TreePVector;

/**
 * Validates that PCollections persistent data structures interoperate with the existing {@code
 * ListKind} HKT infrastructure via {@code java.util.List} compatibility.
 *
 * <p>PCollections types ({@link PVector}, {@link PStack}) extend {@link java.util.List}, so the
 * existing {@link org.higherkindedj.hkt.list.ListKindHelper#widen widen}/{@link
 * org.higherkindedj.hkt.list.ListKindHelper#narrow narrow} pair accepts and produces them with no
 * production code changes.
 *
 * <p>Note that operations such as {@code map}/{@code flatMap}/{@code of} return a {@code
 * java.util.List} (currently a {@code Collections.singletonList} or {@code ArrayList}) and
 * therefore lose the persistent type. Phase 1's purpose is to confirm that pipeline correctness
 * holds; preserving persistent types end-to-end is the subject of later phases.
 */
@DisplayName("PCollections × ListKind integration (Phase 1)")
class PCollectionsListIntegrationTest {

  // ---------------------------------------------------------------------
  // widen / narrow round-trips
  // ---------------------------------------------------------------------

  @Nested
  @DisplayName("widen / narrow round-trip")
  class WidenNarrow {

    @Test
    @DisplayName("PVector widens and narrows preserving content and identity")
    void pVectorRoundTrip() {
      PVector<Integer> source = TreePVector.from(List.of(1, 2, 3, 4));
      Kind<ListKind.Witness, Integer> widened = LIST.widen(source);
      List<Integer> narrowed = LIST.narrow(widened);

      assertThat(narrowed).containsExactly(1, 2, 3, 4);
      // No defensive copy: the underlying List instance is preserved.
      assertThat(narrowed).isSameAs(source);
      assertThat(narrowed).isInstanceOf(PVector.class);
    }

    @Test
    @DisplayName("PStack widens and narrows preserving content and identity")
    void pStackRoundTrip() {
      PStack<String> source = ConsPStack.from(List.of("a", "b", "c"));
      Kind<ListKind.Witness, String> widened = LIST.widen(source);
      List<String> narrowed = LIST.narrow(widened);

      assertThat(narrowed).containsExactly("a", "b", "c");
      assertThat(narrowed).isSameAs(source);
      assertThat(narrowed).isInstanceOf(PStack.class);
    }

    @Test
    @DisplayName("Empty PVector widens and narrows correctly")
    void emptyPVectorRoundTrip() {
      PVector<Integer> empty = TreePVector.empty();
      Kind<ListKind.Witness, Integer> widened = LIST.widen(empty);

      assertThatList(widened).isEmpty();
    }
  }

  // ---------------------------------------------------------------------
  // Functor
  // ---------------------------------------------------------------------

  @Nested
  @DisplayName("Functor operations via ListMonad")
  class FunctorOps {

    @Test
    @DisplayName("map applies function to PVector elements")
    void mapPVector() {
      PVector<Integer> input = TreePVector.from(List.of(1, 2, 3));
      Kind<ListKind.Witness, Integer> kind = LIST.widen(input);

      Kind<ListKind.Witness, Integer> mapped = Instances.monadZero(list()).map(x -> x * 10, kind);

      assertThatList(mapped).containsExactly(10, 20, 30);
    }

    @Test
    @DisplayName("map applies function to PStack elements")
    void mapPStack() {
      PStack<String> input = ConsPStack.from(List.of("a", "b"));
      Kind<ListKind.Witness, String> kind = LIST.widen(input);

      Kind<ListKind.Witness, String> mapped =
          Instances.monadZero(list()).map(String::toUpperCase, kind);

      assertThatList(mapped).containsExactly("A", "B");
    }
  }

  // ---------------------------------------------------------------------
  // Monad
  // ---------------------------------------------------------------------

  @Nested
  @DisplayName("Monad operations via ListMonad")
  class MonadOps {

    @Test
    @DisplayName("flatMap on PVector chains and flattens correctly")
    void flatMapPVector() {
      PVector<Integer> input = TreePVector.from(List.of(1, 2));
      Kind<ListKind.Witness, Integer> kind = LIST.widen(input);

      Function<Integer, Kind<ListKind.Witness, String>> dup =
          i -> LIST.widen(TreePVector.from(List.of("v" + i, "v" + i)));

      Kind<ListKind.Witness, String> result = Instances.monadZero(list()).flatMap(dup, kind);

      assertThatList(result).containsExactly("v1", "v1", "v2", "v2");
    }

    @Test
    @DisplayName("ap with PVector of functions and PVector of values")
    void apPVector() {
      Kind<ListKind.Witness, Function<Integer, String>> ff =
          LIST.widen(TreePVector.from(List.of(i -> "n" + i, i -> "x" + (i * 2))));
      Kind<ListKind.Witness, Integer> fa = LIST.widen(TreePVector.from(List.of(1, 2)));

      Kind<ListKind.Witness, String> result = Instances.monadZero(list()).ap(ff, fa);

      assertThatList(result).containsExactly("n1", "n2", "x2", "x4");
    }

    @Test
    @DisplayName("Mixed PVector and PStack in flatMap")
    void mixedPVectorPStack() {
      Kind<ListKind.Witness, Integer> input = LIST.widen(TreePVector.from(List.of(1, 2, 3)));

      Function<Integer, Kind<ListKind.Witness, Integer>> repeatViaPStack =
          i -> LIST.widen(ConsPStack.from(List.of(i, i)));

      Kind<ListKind.Witness, Integer> result =
          Instances.monadZero(list()).flatMap(repeatViaPStack, input);

      assertThatList(result).containsExactly(1, 1, 2, 2, 3, 3);
    }
  }

  // ---------------------------------------------------------------------
  // Traverse / Foldable
  // ---------------------------------------------------------------------

  @Nested
  @DisplayName("Traverse and Foldable via ListTraverse")
  class TraverseOps {

    @Test
    @DisplayName("traverse over PVector with Optional applicative — all present")
    void traversePVectorAllPresent() {
      Kind<ListKind.Witness, Integer> input = LIST.widen(TreePVector.from(List.of(1, 2, 3)));

      Function<Integer, Kind<OptionalKind.Witness, Integer>> safeInc =
          i -> OPTIONAL.widen(Optional.of(i + 1));

      Kind<OptionalKind.Witness, Kind<ListKind.Witness, Integer>> result =
          ListTraverse.INSTANCE.traverse(Instances.monadError(optional()), safeInc, input);

      Optional<List<Integer>> narrowed = OPTIONAL.narrow(result).map(LIST::narrow);
      assertThat(narrowed).hasValue(List.of(2, 3, 4));
    }

    @Test
    @DisplayName("traverse over PVector short-circuits on Optional.empty()")
    void traversePVectorShortCircuits() {
      Kind<ListKind.Witness, Integer> input = LIST.widen(TreePVector.from(List.of(1, 2, 3)));

      Function<Integer, Kind<OptionalKind.Witness, Integer>> failOnTwo =
          i -> OPTIONAL.widen(i == 2 ? Optional.empty() : Optional.of(i));

      Kind<OptionalKind.Witness, Kind<ListKind.Witness, Integer>> result =
          ListTraverse.INSTANCE.traverse(Instances.monadError(optional()), failOnTwo, input);

      assertThatOptionalKind(result).isEmpty();
    }

    @Test
    @DisplayName("foldMap with sum monoid over PVector")
    void foldMapSumPVector() {
      Kind<ListKind.Witness, Integer> input = LIST.widen(TreePVector.from(List.of(1, 2, 3, 4)));

      Integer sum = ListTraverse.INSTANCE.foldMap(Monoids.integerAddition(), i -> i, input);

      assertThat(sum).isEqualTo(10);
    }

    @Test
    @DisplayName("foldMap with string concatenation over PStack")
    void foldMapStringPStack() {
      Kind<ListKind.Witness, String> input = LIST.widen(ConsPStack.from(List.of("a", "b", "c")));

      String concat = ListTraverse.INSTANCE.foldMap(Monoids.string(), s -> s, input);

      assertThat(concat).isEqualTo("abc");
    }
  }

  // ---------------------------------------------------------------------
  // Selective
  // ---------------------------------------------------------------------

  @Nested
  @DisplayName("Selective operations via ListSelective")
  class SelectiveOps {

    @Test
    @DisplayName("select over PVector handles Right values directly")
    void selectPVectorAllRight() {
      PVector<Choice<Integer, String>> choices =
          TreePVector.<Choice<Integer, String>>empty()
              .plus(Selective.right("A"))
              .plus(Selective.right("B"));

      Kind<ListKind.Witness, Choice<Integer, String>> fab = LIST.widen(choices);
      Kind<ListKind.Witness, Function<Integer, String>> ff =
          LIST.widen(TreePVector.from(List.of(i -> "N" + i)));

      Kind<ListKind.Witness, String> result = ListSelective.INSTANCE.select(fab, ff);

      assertThatList(result).containsExactly("A", "B");
    }

    @Test
    @DisplayName("select over PVector applies functions to Left values")
    void selectPVectorMixedChoices() {
      PVector<Choice<Integer, String>> choices =
          TreePVector.<Choice<Integer, String>>empty()
              .plus(Selective.left(1))
              .plus(Selective.right("X"))
              .plus(Selective.left(2));

      Kind<ListKind.Witness, Choice<Integer, String>> fab = LIST.widen(choices);
      Kind<ListKind.Witness, Function<Integer, String>> ff =
          LIST.widen(TreePVector.from(List.of(i -> "N" + i)));

      Kind<ListKind.Witness, String> result = ListSelective.INSTANCE.select(fab, ff);

      assertThatList(result).containsExactly("N1", "X", "N2");
    }
  }

  // ---------------------------------------------------------------------
  // Alternative
  // ---------------------------------------------------------------------

  @Nested
  @DisplayName("Alternative operations via ListMonad")
  class AlternativeOps {

    private final Alternative<ListKind.Witness> alt = Instances.monadZero(list());

    @Test
    @DisplayName("orElse concatenates a PVector with a PStack alternative")
    void orElseConcatenatesPVectorAndPStack() {
      Kind<ListKind.Witness, Integer> primary = LIST.widen(TreePVector.from(List.of(1, 2)));
      Kind<ListKind.Witness, Integer> fallback = LIST.widen(ConsPStack.from(List.of(3, 4)));

      Kind<ListKind.Witness, Integer> result = alt.orElse(primary, () -> fallback);

      assertThatList(result).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("empty() composes with PVector via orElse")
    void emptyComposesWithPVector() {
      Kind<ListKind.Witness, Integer> primary = alt.empty();
      Kind<ListKind.Witness, Integer> fallback = LIST.widen(TreePVector.from(List.of(7, 8, 9)));

      Kind<ListKind.Witness, Integer> result = alt.orElse(primary, () -> fallback);

      assertThatList(result).containsExactly(7, 8, 9);
    }
  }
}
