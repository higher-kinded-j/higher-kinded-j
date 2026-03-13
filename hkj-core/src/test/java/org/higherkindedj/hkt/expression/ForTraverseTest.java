// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
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
import org.higherkindedj.hkt.maybe.MaybeTraverse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for traverse, sequence, and flatTraverse methods on For comprehension steps.
 *
 * <p>These methods allow traversing a collection within a for-comprehension, applying an effectful
 * function to each element and collecting the results.
 */
@DisplayName("For Comprehension Traverse Tests")
class ForTraverseTest {

  @Nested
  @DisplayName("MonadicSteps1 traverse with Identity Monad")
  class MonadicSteps1TraverseIdTest {
    private final IdMonad idMonad = IdMonad.instance();
    private final ListTraverse listTraverse = ListTraverse.INSTANCE;

    @Test
    @DisplayName("traverse: should traverse a list, applying an Id effect to each element")
    void traverseListWithId() {
      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(Arrays.asList(1, 2, 3)))
              .traverse(
                  listTraverse, list -> LIST.widen(list), (Integer i) -> Id.<Integer>of(i * 10))
              .yield(
                  (original, traversed) -> {
                    List<Integer> list = LIST.narrow(traversed);
                    return original.toString() + " -> " + list.toString();
                  });
      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("[1, 2, 3] -> [10, 20, 30]");
    }

    @Test
    @DisplayName("traverse: should work with extractor that selects a field")
    void traverseWithExtractor() {
      Kind<IdKind.Witness, List<String>> result =
          For.from(idMonad, Id.of(Arrays.asList("a", "b", "c")))
              .traverse(
                  listTraverse,
                  list -> LIST.widen(list),
                  (String s) -> Id.<String>of(s.toUpperCase()))
              .yield((original, traversed) -> LIST.narrow(traversed));
      assertThat(IdKindHelper.ID.unwrap(result)).containsExactly("A", "B", "C");
    }

    @Test
    @DisplayName("traverse: should handle empty list")
    void traverseEmptyList() {
      Kind<IdKind.Witness, List<Integer>> result =
          For.from(idMonad, Id.of(List.<Integer>of()))
              .traverse(
                  listTraverse, list -> LIST.widen(list), (Integer i) -> Id.<Integer>of(i * 10))
              .yield((original, traversed) -> LIST.narrow(traversed));
      assertThat(IdKindHelper.ID.unwrap(result)).isEmpty();
    }

    @Test
    @DisplayName("traverse: should reject null traversable")
    void traverseNullTraversable() {
      assertThatThrownBy(
              () ->
                  For.from(idMonad, Id.of(List.of(1)))
                      .traverse(null, list -> LIST.widen(list), (Integer i) -> Id.of(i)))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("traversable");
    }

    @Test
    @DisplayName("traverse: should reject null extractor")
    void traverseNullExtractor() {
      assertThatThrownBy(
              () ->
                  For.from(idMonad, Id.of(List.of(1)))
                      .traverse(listTraverse, null, (Integer i) -> Id.of(i)))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("extractor");
    }

    @Test
    @DisplayName("traverse: should reject null function")
    void traverseNullFunction() {
      assertThatThrownBy(
              () ->
                  For.from(idMonad, Id.of(List.of(1)))
                      .traverse(listTraverse, list -> LIST.widen(list), null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("function");
    }
  }

  @Nested
  @DisplayName("MonadicSteps1 sequence with Identity Monad")
  class MonadicSteps1SequenceIdTest {
    private final IdMonad idMonad = IdMonad.instance();
    private final ListTraverse listTraverse = ListTraverse.INSTANCE;

    @Test
    @DisplayName("sequence: should turn List<Id<Int>> into Id<List<Int>>")
    void sequenceListOfIds() {
      List<Kind<IdKind.Witness, Integer>> listOfIds = Arrays.asList(Id.of(1), Id.of(2), Id.of(3));
      Kind<ListKind.Witness, Kind<IdKind.Witness, Integer>> kindList = LIST.widen(listOfIds);

      Kind<IdKind.Witness, List<Integer>> result =
          For.from(idMonad, Id.of(kindList))
              .sequence(listTraverse, Function.identity())
              .yield((original, sequenced) -> LIST.narrow(sequenced));

      assertThat(IdKindHelper.ID.unwrap(result)).containsExactly(1, 2, 3);
    }
  }

  @Nested
  @DisplayName("MonadicSteps1 flatTraverse with Identity Monad")
  class MonadicSteps1FlatTraverseIdTest {
    private final IdMonad idMonad = IdMonad.instance();
    private final ListTraverse listTraverse = ListTraverse.INSTANCE;
    private final ListMonad listMonad = ListMonad.INSTANCE;

    @Test
    @DisplayName("flatTraverse: should traverse and flatten inner lists using Id monad")
    void flatTraverseListWithId() {
      Kind<IdKind.Witness, List<Integer>> result =
          For.from(idMonad, Id.of(Arrays.asList(1, 2, 3)))
              .flatTraverse(
                  listTraverse,
                  listMonad,
                  list -> LIST.widen(list),
                  (Integer i) ->
                      Id.<Kind<ListKind.Witness, Integer>>of(LIST.widen(Arrays.asList(i, i * 10))))
              .yield((original, traversed) -> LIST.narrow(traversed));
      assertThat(IdKindHelper.ID.unwrap(result)).containsExactly(1, 10, 2, 20, 3, 30);
    }
  }

  @Nested
  @DisplayName("MonadicSteps1 traverse with Maybe Monad")
  class MonadicSteps1TraverseMaybeTest {
    private final MaybeMonad maybeMonad = MaybeMonad.INSTANCE;
    private final ListTraverse listTraverse = ListTraverse.INSTANCE;

    @Test
    @DisplayName("traverse: should succeed when all elements return Just")
    void traverseAllJust() {
      Kind<MaybeKind.Witness, List<Integer>> result =
          For.from(maybeMonad, MAYBE.just(Arrays.asList(1, 2, 3)))
              .traverse(listTraverse, list -> LIST.widen(list), (Integer i) -> MAYBE.just(i * 2))
              .yield((original, traversed) -> LIST.narrow(traversed));
      Maybe<List<Integer>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("traverse: should fail when any element returns Nothing")
    void traverseWithNothing() {
      Kind<MaybeKind.Witness, List<Integer>> result =
          For.from(maybeMonad, MAYBE.just(Arrays.asList(1, 2, 3)))
              .traverse(
                  listTraverse,
                  list -> LIST.widen(list),
                  (Integer i) -> i == 2 ? MAYBE.<Integer>nothing() : MAYBE.just(i * 2))
              .yield((original, traversed) -> LIST.narrow(traversed));
      Maybe<List<Integer>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isFalse();
    }

    @Test
    @DisplayName("traverse: should propagate Nothing from initial computation")
    void traverseWithNothingInitial() {
      Kind<MaybeKind.Witness, List<Integer>> result =
          For.from(maybeMonad, MAYBE.<List<Integer>>nothing())
              .traverse(listTraverse, list -> LIST.widen(list), (Integer i) -> MAYBE.just(i * 2))
              .yield((original, traversed) -> LIST.narrow(traversed));
      Maybe<List<Integer>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isFalse();
    }
  }

  @Nested
  @DisplayName("MonadicSteps1 flatTraverse with Maybe Monad")
  class MonadicSteps1FlatTraverseMaybeTest {
    private final MaybeMonad maybeMonad = MaybeMonad.INSTANCE;
    private final ListTraverse listTraverse = ListTraverse.INSTANCE;
    private final ListMonad listMonad = ListMonad.INSTANCE;

    @Test
    @DisplayName("flatTraverse: should traverse and flatten inner lists")
    void flatTraverseListInMaybe() {
      // Start with a list of ints, traverse with a function that returns Maybe<List<Int>>
      // then flatten the nested List<List<Int>> into List<Int>
      Kind<MaybeKind.Witness, List<Integer>> result =
          For.from(maybeMonad, MAYBE.just(Arrays.asList(1, 2, 3)))
              .flatTraverse(
                  listTraverse,
                  listMonad,
                  list -> LIST.widen(list),
                  (Integer i) ->
                      MAYBE.<Kind<ListKind.Witness, Integer>>just(
                          LIST.widen(Arrays.asList(i, i * 10))))
              .yield((original, traversed) -> LIST.narrow(traversed));
      Maybe<List<Integer>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).containsExactly(1, 10, 2, 20, 3, 30);
    }

    @Test
    @DisplayName("flatTraverse: should reject null innerMonad")
    void flatTraverseNullInnerMonad() {
      assertThatThrownBy(
              () ->
                  For.from(maybeMonad, MAYBE.just(List.of(1)))
                      .flatTraverse(
                          listTraverse,
                          null,
                          list -> LIST.widen(list),
                          (Integer i) -> MAYBE.just(LIST.widen(List.of(i)))))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("innerMonad");
    }
  }

  @Nested
  @DisplayName("MonadicSteps1 traverse with MaybeTraverse")
  class MonadicSteps1TraverseMaybeTraverseTest {
    private final IdMonad idMonad = IdMonad.instance();
    private final MaybeTraverse maybeTraverse = MaybeTraverse.INSTANCE;

    @Test
    @DisplayName("traverse: should traverse Maybe.just with Id effect")
    void traverseMaybeJust() {
      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(MAYBE.just(42)))
              .traverse(maybeTraverse, Function.identity(), (Integer i) -> Id.<String>of("v" + i))
              .yield(
                  (original, traversed) -> {
                    Maybe<String> m = MAYBE.narrow(traversed);
                    return m.isJust() ? m.get() : "nothing";
                  });
      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("v42");
    }

    @Test
    @DisplayName("traverse: should traverse Maybe.nothing preserving structure")
    void traverseMaybeNothing() {
      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(MAYBE.<Integer>nothing()))
              .traverse(maybeTraverse, Function.identity(), (Integer i) -> Id.<String>of("v" + i))
              .yield(
                  (original, traversed) -> {
                    Maybe<String> m = MAYBE.narrow(traversed);
                    return m.isJust() ? m.get() : "nothing";
                  });
      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("nothing");
    }
  }

  @Nested
  @DisplayName("FilterableSteps1 traverse with Maybe Monad")
  class FilterableSteps1TraverseTest {
    private final MaybeMonad maybeMonad = MaybeMonad.INSTANCE;
    private final ListTraverse listTraverse = ListTraverse.INSTANCE;

    @Test
    @DisplayName("traverse: should work on filterable comprehension")
    void traverseOnFilterable() {
      Kind<MaybeKind.Witness, List<Integer>> result =
          For.from(maybeMonad, MAYBE.just(Arrays.asList(1, 2, 3)))
              .traverse(listTraverse, list -> LIST.widen(list), (Integer i) -> MAYBE.just(i * 2))
              .yield((original, traversed) -> LIST.narrow(traversed));
      Maybe<List<Integer>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("sequence: should work on filterable comprehension")
    void sequenceOnFilterable() {
      List<Kind<MaybeKind.Witness, Integer>> listOfMaybes =
          Arrays.asList(MAYBE.just(1), MAYBE.just(2), MAYBE.just(3));
      Kind<ListKind.Witness, Kind<MaybeKind.Witness, Integer>> kindList = LIST.widen(listOfMaybes);

      Kind<MaybeKind.Witness, List<Integer>> result =
          For.from(maybeMonad, MAYBE.just(kindList))
              .sequence(listTraverse, Function.identity())
              .yield((original, sequenced) -> LIST.narrow(sequenced));
      Maybe<List<Integer>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("flatTraverse: should work on filterable comprehension")
    void flatTraverseOnFilterable() {
      Kind<MaybeKind.Witness, List<Integer>> result =
          For.from(maybeMonad, MAYBE.just(Arrays.asList(1, 2)))
              .flatTraverse(
                  listTraverse,
                  ListMonad.INSTANCE,
                  list -> LIST.widen(list),
                  (Integer i) ->
                      MAYBE.<Kind<ListKind.Witness, Integer>>just(
                          LIST.widen(Arrays.asList(i, i * 100))))
              .yield((original, traversed) -> LIST.narrow(traversed));
      Maybe<List<Integer>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).containsExactly(1, 100, 2, 200);
    }
  }

  @Nested
  @DisplayName("Chained traverse after from")
  class ChainedTraverseTest {
    private final IdMonad idMonad = IdMonad.instance();
    private final ListTraverse listTraverse = ListTraverse.INSTANCE;

    @Test
    @DisplayName("from then traverse: should chain from followed by traverse at arity 2")
    void fromThenTraverse() {
      Kind<IdKind.Witness, String> result =
          For.from(idMonad, Id.of(10))
              .from(a -> Id.of(Arrays.asList(a, a + 1, a + 2)))
              .traverse(listTraverse, t -> LIST.widen(t._2()), (Integer i) -> Id.<Integer>of(i * 2))
              .yield(
                  (a, list, traversed) -> {
                    List<Integer> tList = LIST.narrow(traversed);
                    return a + ":" + tList;
                  });
      assertThat(IdKindHelper.ID.unwrap(result)).isEqualTo("10:[20, 22, 24]");
    }
  }
}
