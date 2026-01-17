// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.indexed.Pair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ListPrisms Utility Class Tests")
class ListPrismsTest {

  @Nested
  @DisplayName("cons() Prism Tests")
  class ConsTests {

    @Test
    @DisplayName("should decompose non-empty list into head and tail")
    void cons_shouldDecomposeNonEmptyList() {
      Prism<List<Integer>, Pair<Integer, List<Integer>>> cons = ListPrisms.cons();
      List<Integer> list = List.of(1, 2, 3, 4, 5);

      Optional<Pair<Integer, List<Integer>>> result = cons.getOptional(list);

      assertThat(result).isPresent();
      assertThat(result.get().first()).isEqualTo(1);
      assertThat(result.get().second()).containsExactly(2, 3, 4, 5);
    }

    @Test
    @DisplayName("should decompose single-element list into head and empty tail")
    void cons_shouldDecomposeSingleElementList() {
      Prism<List<String>, Pair<String, List<String>>> cons = ListPrisms.cons();
      List<String> list = List.of("only");

      Optional<Pair<String, List<String>>> result = cons.getOptional(list);

      assertThat(result).isPresent();
      assertThat(result.get().first()).isEqualTo("only");
      assertThat(result.get().second()).isEmpty();
    }

    @Test
    @DisplayName("should return empty for empty list")
    void cons_shouldReturnEmptyForEmptyList() {
      Prism<List<Integer>, Pair<Integer, List<Integer>>> cons = ListPrisms.cons();
      List<Integer> emptyList = Collections.emptyList();

      Optional<Pair<Integer, List<Integer>>> result = cons.getOptional(emptyList);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should build list from head and tail pair")
    void cons_shouldBuildListFromPair() {
      Prism<List<Integer>, Pair<Integer, List<Integer>>> cons = ListPrisms.cons();

      List<Integer> result = cons.build(Pair.of(0, List.of(1, 2, 3)));

      assertThat(result).containsExactly(0, 1, 2, 3);
    }

    @Test
    @DisplayName("should build single-element list from head and empty tail")
    void cons_shouldBuildSingleElementList() {
      Prism<List<String>, Pair<String, List<String>>> cons = ListPrisms.cons();

      List<String> result = cons.build(Pair.of("alone", List.of()));

      assertThat(result).containsExactly("alone");
    }

    @Test
    @DisplayName("should modify head using prism modify")
    void cons_shouldModifyHead() {
      Prism<List<Integer>, Pair<Integer, List<Integer>>> cons = ListPrisms.cons();
      List<Integer> list = List.of(1, 2, 3);

      List<Integer> result = cons.modify(pair -> Pair.of(pair.first() * 10, pair.second()), list);

      assertThat(result).containsExactly(10, 2, 3);
    }

    @Test
    @DisplayName("should return unchanged list when modifying empty list")
    void cons_shouldReturnUnchangedForEmptyListModify() {
      Prism<List<Integer>, Pair<Integer, List<Integer>>> cons = ListPrisms.cons();
      List<Integer> emptyList = List.of();

      List<Integer> result =
          cons.modify(pair -> Pair.of(pair.first() * 10, pair.second()), emptyList);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should match non-empty lists")
    void cons_shouldMatchNonEmptyLists() {
      Prism<List<Integer>, Pair<Integer, List<Integer>>> cons = ListPrisms.cons();

      assertThat(cons.matches(List.of(1, 2, 3))).isTrue();
      assertThat(cons.matches(List.of(1))).isTrue();
      assertThat(cons.matches(List.of())).isFalse();
    }

    @Test
    @DisplayName("headTail should be alias for cons")
    void headTail_shouldBeAliasForCons() {
      Prism<List<Integer>, Pair<Integer, List<Integer>>> headTail = ListPrisms.headTail();
      List<Integer> list = List.of(1, 2, 3);

      Optional<Pair<Integer, List<Integer>>> result = headTail.getOptional(list);

      assertThat(result).isPresent();
      assertThat(result.get().first()).isEqualTo(1);
      assertThat(result.get().second()).containsExactly(2, 3);
    }
  }

  @Nested
  @DisplayName("snoc() Prism Tests")
  class SnocTests {

    @Test
    @DisplayName("should decompose non-empty list into init and last")
    void snoc_shouldDecomposeNonEmptyList() {
      Prism<List<Integer>, Pair<List<Integer>, Integer>> snoc = ListPrisms.snoc();
      List<Integer> list = List.of(1, 2, 3, 4, 5);

      Optional<Pair<List<Integer>, Integer>> result = snoc.getOptional(list);

      assertThat(result).isPresent();
      assertThat(result.get().first()).containsExactly(1, 2, 3, 4);
      assertThat(result.get().second()).isEqualTo(5);
    }

    @Test
    @DisplayName("should decompose single-element list into empty init and last")
    void snoc_shouldDecomposeSingleElementList() {
      Prism<List<String>, Pair<List<String>, String>> snoc = ListPrisms.snoc();
      List<String> list = List.of("only");

      Optional<Pair<List<String>, String>> result = snoc.getOptional(list);

      assertThat(result).isPresent();
      assertThat(result.get().first()).isEmpty();
      assertThat(result.get().second()).isEqualTo("only");
    }

    @Test
    @DisplayName("should return empty for empty list")
    void snoc_shouldReturnEmptyForEmptyList() {
      Prism<List<Integer>, Pair<List<Integer>, Integer>> snoc = ListPrisms.snoc();
      List<Integer> emptyList = Collections.emptyList();

      Optional<Pair<List<Integer>, Integer>> result = snoc.getOptional(emptyList);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should build list from init and last pair")
    void snoc_shouldBuildListFromPair() {
      Prism<List<Integer>, Pair<List<Integer>, Integer>> snoc = ListPrisms.snoc();

      List<Integer> result = snoc.build(Pair.of(List.of(1, 2, 3), 4));

      assertThat(result).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("should build single-element list from empty init and last")
    void snoc_shouldBuildSingleElementList() {
      Prism<List<String>, Pair<List<String>, String>> snoc = ListPrisms.snoc();

      List<String> result = snoc.build(Pair.of(List.of(), "alone"));

      assertThat(result).containsExactly("alone");
    }

    @Test
    @DisplayName("should modify last using prism modify")
    void snoc_shouldModifyLast() {
      Prism<List<Integer>, Pair<List<Integer>, Integer>> snoc = ListPrisms.snoc();
      List<Integer> list = List.of(1, 2, 3);

      List<Integer> result = snoc.modify(pair -> Pair.of(pair.first(), pair.second() * 10), list);

      assertThat(result).containsExactly(1, 2, 30);
    }

    @Test
    @DisplayName("should match non-empty lists")
    void snoc_shouldMatchNonEmptyLists() {
      Prism<List<Integer>, Pair<List<Integer>, Integer>> snoc = ListPrisms.snoc();

      assertThat(snoc.matches(List.of(1, 2, 3))).isTrue();
      assertThat(snoc.matches(List.of(1))).isTrue();
      assertThat(snoc.matches(List.of())).isFalse();
    }

    @Test
    @DisplayName("initLast should be alias for snoc")
    void initLast_shouldBeAliasForSnoc() {
      Prism<List<Integer>, Pair<List<Integer>, Integer>> initLast = ListPrisms.initLast();
      List<Integer> list = List.of(1, 2, 3);

      Optional<Pair<List<Integer>, Integer>> result = initLast.getOptional(list);

      assertThat(result).isPresent();
      assertThat(result.get().first()).containsExactly(1, 2);
      assertThat(result.get().second()).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("empty() Prism Tests")
  class EmptyTests {

    @Test
    @DisplayName("should match empty list")
    void empty_shouldMatchEmptyList() {
      Prism<List<Integer>, Unit> empty = ListPrisms.empty();

      Optional<Unit> result = empty.getOptional(List.of());

      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(Unit.INSTANCE);
    }

    @Test
    @DisplayName("should not match non-empty list")
    void empty_shouldNotMatchNonEmptyList() {
      Prism<List<Integer>, Unit> empty = ListPrisms.empty();

      Optional<Unit> result = empty.getOptional(List.of(1, 2, 3));

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should build empty list from Unit")
    void empty_shouldBuildEmptyList() {
      Prism<List<String>, Unit> empty = ListPrisms.empty();

      List<String> result = empty.build(Unit.INSTANCE);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("matches should correctly identify empty lists")
    void empty_matchesShouldIdentifyEmptyLists() {
      Prism<List<Integer>, Unit> empty = ListPrisms.empty();

      assertThat(empty.matches(List.of())).isTrue();
      assertThat(empty.matches(List.of(1))).isFalse();
      assertThat(empty.matches(List.of(1, 2, 3))).isFalse();
    }
  }

  @Nested
  @DisplayName("head() Affine Tests")
  class HeadTests {

    @Test
    @DisplayName("should get first element of non-empty list")
    void head_shouldGetFirstElement() {
      Affine<List<String>, String> head = ListPrisms.head();

      Optional<String> result = head.getOptional(List.of("first", "second", "third"));

      assertThat(result).contains("first");
    }

    @Test
    @DisplayName("should return empty for empty list")
    void head_shouldReturnEmptyForEmptyList() {
      Affine<List<String>, String> head = ListPrisms.head();

      Optional<String> result = head.getOptional(List.of());

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should set first element on empty list")
    void head_shouldSetFirstElementOnEmptyList() {
      Affine<List<Integer>, Integer> head = ListPrisms.head();

      List<Integer> result = head.set(42, List.of());

      assertThat(result).containsExactly(42);
    }

    @Test
    @DisplayName("should set first element preserving rest of list")
    void head_shouldSetFirstElementPreservingRest() {
      Affine<List<Integer>, Integer> head = ListPrisms.head();

      List<Integer> result = head.set(99, List.of(1, 2, 3));

      assertThat(result).containsExactly(99, 2, 3);
    }

    @Test
    @DisplayName("should modify first element")
    void head_shouldModifyFirstElement() {
      Affine<List<String>, String> head = ListPrisms.head();
      List<String> list = List.of("lower", "case");

      List<String> result = head.modify(String::toUpperCase, list);

      assertThat(result).containsExactly("LOWER", "case");
    }
  }

  @Nested
  @DisplayName("last() Affine Tests")
  class LastTests {

    @Test
    @DisplayName("should get last element of non-empty list")
    void last_shouldGetLastElement() {
      Affine<List<String>, String> last = ListPrisms.last();

      Optional<String> result = last.getOptional(List.of("first", "second", "third"));

      assertThat(result).contains("third");
    }

    @Test
    @DisplayName("should return empty for empty list")
    void last_shouldReturnEmptyForEmptyList() {
      Affine<List<String>, String> last = ListPrisms.last();

      Optional<String> result = last.getOptional(List.of());

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should set last element on empty list")
    void last_shouldSetLastElementOnEmptyList() {
      Affine<List<Integer>, Integer> last = ListPrisms.last();

      List<Integer> result = last.set(42, List.of());

      assertThat(result).containsExactly(42);
    }

    @Test
    @DisplayName("should set last element preserving rest of list")
    void last_shouldSetLastElementPreservingRest() {
      Affine<List<Integer>, Integer> last = ListPrisms.last();

      List<Integer> result = last.set(99, List.of(1, 2, 3));

      assertThat(result).containsExactly(1, 2, 99);
    }

    @Test
    @DisplayName("should modify last element")
    void last_shouldModifyLastElement() {
      Affine<List<Integer>, Integer> last = ListPrisms.last();
      List<Integer> list = List.of(1, 2, 3);

      List<Integer> result = last.modify(x -> x * 100, list);

      assertThat(result).containsExactly(1, 2, 300);
    }
  }

  @Nested
  @DisplayName("tail() Affine Tests")
  class TailTests {

    @Test
    @DisplayName("should get tail of non-empty list")
    void tail_shouldGetTail() {
      Affine<List<Integer>, List<Integer>> tail = ListPrisms.tail();

      Optional<List<Integer>> result = tail.getOptional(List.of(1, 2, 3, 4));

      assertThat(result).isPresent();
      assertThat(result.get()).containsExactly(2, 3, 4);
    }

    @Test
    @DisplayName("should get empty tail for single-element list")
    void tail_shouldGetEmptyTailForSingleElement() {
      Affine<List<String>, List<String>> tail = ListPrisms.tail();

      Optional<List<String>> result = tail.getOptional(List.of("only"));

      assertThat(result).isPresent();
      assertThat(result.get()).isEmpty();
    }

    @Test
    @DisplayName("should return empty for empty list")
    void tail_shouldReturnEmptyForEmptyList() {
      Affine<List<Integer>, List<Integer>> tail = ListPrisms.tail();

      Optional<List<Integer>> result = tail.getOptional(List.of());

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should set new tail preserving head")
    void tail_shouldSetNewTail() {
      Affine<List<Integer>, List<Integer>> tail = ListPrisms.tail();

      List<Integer> result = tail.set(List.of(20, 30, 40), List.of(1, 2, 3, 4));

      assertThat(result).containsExactly(1, 20, 30, 40);
    }

    @Test
    @DisplayName("should modify tail of list")
    void tail_shouldModifyTail() {
      Affine<List<Integer>, List<Integer>> tail = ListPrisms.tail();
      List<Integer> list = List.of(1, 2, 3, 4);

      List<Integer> result = tail.modify(t -> t.stream().map(x -> x * 10).toList(), list);

      assertThat(result).containsExactly(1, 20, 30, 40);
    }

    @Test
    @DisplayName("should return empty list unchanged when setting tail on empty list")
    void tail_shouldReturnEmptyListUnchangedWhenSettingOnEmpty() {
      Affine<List<Integer>, List<Integer>> tail = ListPrisms.tail();

      List<Integer> result = tail.set(List.of(10, 20), List.of());

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("init() Affine Tests")
  class InitTests {

    @Test
    @DisplayName("should get init of non-empty list")
    void init_shouldGetInit() {
      Affine<List<Integer>, List<Integer>> init = ListPrisms.init();

      Optional<List<Integer>> result = init.getOptional(List.of(1, 2, 3, 4));

      assertThat(result).isPresent();
      assertThat(result.get()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("should get empty init for single-element list")
    void init_shouldGetEmptyInitForSingleElement() {
      Affine<List<String>, List<String>> init = ListPrisms.init();

      Optional<List<String>> result = init.getOptional(List.of("only"));

      assertThat(result).isPresent();
      assertThat(result.get()).isEmpty();
    }

    @Test
    @DisplayName("should return empty for empty list")
    void init_shouldReturnEmptyForEmptyList() {
      Affine<List<Integer>, List<Integer>> init = ListPrisms.init();

      Optional<List<Integer>> result = init.getOptional(List.of());

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should set new init preserving last")
    void init_shouldSetNewInit() {
      Affine<List<Integer>, List<Integer>> init = ListPrisms.init();

      List<Integer> result = init.set(List.of(10, 20, 30), List.of(1, 2, 3, 4));

      assertThat(result).containsExactly(10, 20, 30, 4);
    }

    @Test
    @DisplayName("should modify init of list")
    void init_shouldModifyInit() {
      Affine<List<Integer>, List<Integer>> init = ListPrisms.init();
      List<Integer> list = List.of(1, 2, 3, 4);

      List<Integer> result = init.modify(i -> i.stream().map(x -> x * 10).toList(), list);

      assertThat(result).containsExactly(10, 20, 30, 4);
    }

    @Test
    @DisplayName("should return empty list unchanged when setting init on empty list")
    void init_shouldReturnEmptyListUnchangedWhenSettingOnEmpty() {
      Affine<List<Integer>, List<Integer>> init = ListPrisms.init();

      List<Integer> result = init.set(List.of(10, 20), List.of());

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("Composition Tests")
  class CompositionTests {

    @Test
    @DisplayName("cons composed with cons should access nested head")
    void consComposedWithCons_shouldAccessNestedHead() {
      // Access the head of the tail (second element)
      Prism<List<Integer>, Pair<Integer, List<Integer>>> cons = ListPrisms.cons();
      List<Integer> list = List.of(1, 2, 3, 4, 5);

      // First cons: get (1, [2, 3, 4, 5])
      Optional<Pair<Integer, List<Integer>>> first = cons.getOptional(list);
      assertThat(first).isPresent();

      // Second cons on the tail: get (2, [3, 4, 5])
      Optional<Pair<Integer, List<Integer>>> second = cons.getOptional(first.get().second());
      assertThat(second).isPresent();
      assertThat(second.get().first()).isEqualTo(2);
    }

    @Test
    @DisplayName("snoc composed with snoc should access nested last")
    void snocComposedWithSnoc_shouldAccessNestedLast() {
      // Access the last of the init (second-to-last element)
      Prism<List<Integer>, Pair<List<Integer>, Integer>> snoc = ListPrisms.snoc();
      List<Integer> list = List.of(1, 2, 3, 4, 5);

      // First snoc: get ([1, 2, 3, 4], 5)
      Optional<Pair<List<Integer>, Integer>> first = snoc.getOptional(list);
      assertThat(first).isPresent();

      // Second snoc on the init: get ([1, 2, 3], 4)
      Optional<Pair<List<Integer>, Integer>> second = snoc.getOptional(first.get().first());
      assertThat(second).isPresent();
      assertThat(second.get().second()).isEqualTo(4);
    }

    @Test
    @DisplayName("cons and snoc can be used together for complex manipulations")
    void consAndSnoc_canBeUsedTogether() {
      Prism<List<Integer>, Pair<Integer, List<Integer>>> cons = ListPrisms.cons();
      Prism<List<Integer>, Pair<List<Integer>, Integer>> snoc = ListPrisms.snoc();
      List<Integer> list = List.of(1, 2, 3, 4, 5);

      // Get head using cons
      Optional<Integer> head = cons.getOptional(list).map(Pair::first);
      assertThat(head).contains(1);

      // Get last using snoc
      Optional<Integer> last = snoc.getOptional(list).map(Pair::second);
      assertThat(last).contains(5);

      // Modify both: multiply head by 10 and last by 100
      List<Integer> modifiedHead =
          cons.modify(pair -> Pair.of(pair.first() * 10, pair.second()), list);
      List<Integer> finalResult =
          snoc.modify(pair -> Pair.of(pair.first(), pair.second() * 100), modifiedHead);

      assertThat(finalResult).containsExactly(10, 2, 3, 4, 500);
    }
  }

  @Nested
  @DisplayName("Prism Law Tests")
  class PrismLawTests {

    @Test
    @DisplayName("cons: build-getOptional law (review then preview recovers value)")
    void cons_buildGetOptionalLaw() {
      Prism<List<Integer>, Pair<Integer, List<Integer>>> cons = ListPrisms.cons();
      Pair<Integer, List<Integer>> pair = Pair.of(1, List.of(2, 3, 4));

      // build >>> getOptional == Some
      List<Integer> built = cons.build(pair);
      Optional<Pair<Integer, List<Integer>>> recovered = cons.getOptional(built);

      assertThat(recovered).isPresent();
      assertThat(recovered.get().first()).isEqualTo(pair.first());
      assertThat(recovered.get().second()).containsExactlyElementsOf(pair.second());
    }

    @Test
    @DisplayName("cons: getOptional-build law (preview then review recovers structure)")
    void cons_getOptionalBuildLaw() {
      Prism<List<Integer>, Pair<Integer, List<Integer>>> cons = ListPrisms.cons();
      List<Integer> list = List.of(1, 2, 3, 4);

      // getOptional >>> build == identity (when getOptional succeeds)
      Optional<Pair<Integer, List<Integer>>> maybeValue = cons.getOptional(list);
      assertThat(maybeValue).isPresent();

      List<Integer> rebuilt = cons.build(maybeValue.get());
      assertThat(rebuilt).containsExactlyElementsOf(list);
    }

    @Test
    @DisplayName("snoc: build-getOptional law")
    void snoc_buildGetOptionalLaw() {
      Prism<List<Integer>, Pair<List<Integer>, Integer>> snoc = ListPrisms.snoc();
      Pair<List<Integer>, Integer> pair = Pair.of(List.of(1, 2, 3), 4);

      List<Integer> built = snoc.build(pair);
      Optional<Pair<List<Integer>, Integer>> recovered = snoc.getOptional(built);

      assertThat(recovered).isPresent();
      assertThat(recovered.get().first()).containsExactlyElementsOf(pair.first());
      assertThat(recovered.get().second()).isEqualTo(pair.second());
    }

    @Test
    @DisplayName("snoc: getOptional-build law")
    void snoc_getOptionalBuildLaw() {
      Prism<List<Integer>, Pair<List<Integer>, Integer>> snoc = ListPrisms.snoc();
      List<Integer> list = List.of(1, 2, 3, 4);

      Optional<Pair<List<Integer>, Integer>> maybeValue = snoc.getOptional(list);
      assertThat(maybeValue).isPresent();

      List<Integer> rebuilt = snoc.build(maybeValue.get());
      assertThat(rebuilt).containsExactlyElementsOf(list);
    }

    @Test
    @DisplayName("empty: build-getOptional law")
    void empty_buildGetOptionalLaw() {
      Prism<List<String>, Unit> empty = ListPrisms.empty();

      List<String> built = empty.build(Unit.INSTANCE);
      Optional<Unit> recovered = empty.getOptional(built);

      assertThat(recovered).contains(Unit.INSTANCE);
    }
  }

  @Nested
  @DisplayName("Edge Case Tests")
  class EdgeCaseTests {

    @Test
    @DisplayName("should handle null elements in list (if list allows)")
    void shouldHandleListWithNullElements() {
      // Note: List.of doesn't allow nulls, but ArrayList does
      ArrayList<String> listWithNull = new ArrayList<>();
      listWithNull.add("first");
      listWithNull.add(null);
      listWithNull.add("third");

      Prism<List<String>, Pair<String, List<String>>> cons = ListPrisms.cons();

      Optional<Pair<String, List<String>>> result = cons.getOptional(listWithNull);

      assertThat(result).isPresent();
      assertThat(result.get().first()).isEqualTo("first");
      assertThat(result.get().second()).containsExactly(null, "third");
    }

    @Test
    @DisplayName("should handle very long lists")
    void shouldHandleVeryLongLists() {
      List<Integer> longList = IntStream.range(0, 10000).boxed().toList();

      Prism<List<Integer>, Pair<Integer, List<Integer>>> cons = ListPrisms.cons();

      Optional<Pair<Integer, List<Integer>>> result = cons.getOptional(longList);

      assertThat(result).isPresent();
      assertThat(result.get().first()).isEqualTo(0);
      assertThat(result.get().second()).hasSize(9999);
    }

    @Test
    @DisplayName("built lists should be immutable")
    void builtListsShouldBeImmutable() {
      Prism<List<Integer>, Pair<Integer, List<Integer>>> cons = ListPrisms.cons();
      List<Integer> built = cons.build(Pair.of(1, List.of(2, 3)));

      Assertions.assertThatThrownBy(() -> built.add(4))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  @DisplayName("Trampoline-based Stack-Safe Operations Tests")
  class TrampolineOperationsTests {

    @Test
    @DisplayName("foldRight should fold list from right to left")
    void foldRight_shouldFoldFromRightToLeft() {
      List<String> list = List.of("a", "b", "c");

      String result = ListPrisms.foldRight(list, "", (s, acc) -> s + acc);

      assertThat(result).isEqualTo("abc");
    }

    @Test
    @DisplayName("foldRight should return initial value for empty list")
    void foldRight_shouldReturnInitialForEmptyList() {
      List<Integer> emptyList = List.of();

      Integer result = ListPrisms.foldRight(emptyList, 42, Integer::sum);

      assertThat(result).isEqualTo(42);
    }

    @Test
    @DisplayName("foldRight should handle large lists without stack overflow")
    void foldRight_shouldHandleLargeLists() {
      List<Integer> largeList = IntStream.range(0, 10000).boxed().toList();

      Integer sum = ListPrisms.foldRight(largeList, 0, Integer::sum);

      assertThat(sum).isEqualTo(49995000);
    }

    @Test
    @DisplayName("mapTrampoline should transform all elements")
    void mapTrampoline_shouldTransformAllElements() {
      List<Integer> list = List.of(1, 2, 3, 4, 5);

      List<String> result = ListPrisms.mapTrampoline(list, Object::toString);

      assertThat(result).containsExactly("1", "2", "3", "4", "5");
    }

    @Test
    @DisplayName("mapTrampoline should return empty list for empty input")
    void mapTrampoline_shouldReturnEmptyForEmptyList() {
      List<Integer> emptyList = List.of();

      List<String> result = ListPrisms.mapTrampoline(emptyList, Object::toString);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("filterTrampoline should keep matching elements")
    void filterTrampoline_shouldKeepMatchingElements() {
      List<Integer> list = List.of(1, 2, 3, 4, 5, 6);

      List<Integer> result = ListPrisms.filterTrampoline(list, n -> n % 2 == 0);

      assertThat(result).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("filterTrampoline should return empty when no elements match")
    void filterTrampoline_shouldReturnEmptyWhenNoMatch() {
      List<Integer> list = List.of(1, 3, 5);

      List<Integer> result = ListPrisms.filterTrampoline(list, n -> n % 2 == 0);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("filterTrampoline should return empty for empty list")
    void filterTrampoline_shouldReturnEmptyForEmptyList() {
      List<Integer> emptyList = List.of();

      List<Integer> result = ListPrisms.filterTrampoline(emptyList, n -> true);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("reverseTrampoline should reverse list order")
    void reverseTrampoline_shouldReverseListOrder() {
      List<Integer> list = List.of(1, 2, 3, 4, 5);

      List<Integer> result = ListPrisms.reverseTrampoline(list);

      assertThat(result).containsExactly(5, 4, 3, 2, 1);
    }

    @Test
    @DisplayName("reverseTrampoline should return empty for empty list")
    void reverseTrampoline_shouldReturnEmptyForEmptyList() {
      List<Integer> emptyList = List.of();

      List<Integer> result = ListPrisms.reverseTrampoline(emptyList);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("reverseTrampoline should handle single element list")
    void reverseTrampoline_shouldHandleSingleElement() {
      List<String> list = List.of("only");

      List<String> result = ListPrisms.reverseTrampoline(list);

      assertThat(result).containsExactly("only");
    }

    @Test
    @DisplayName("flatMapTrampoline should flatten mapped lists")
    void flatMapTrampoline_shouldFlattenMappedLists() {
      List<Integer> list = List.of(1, 2, 3);

      List<Integer> result = ListPrisms.flatMapTrampoline(list, n -> List.of(n, n * 10));

      assertThat(result).containsExactly(1, 10, 2, 20, 3, 30);
    }

    @Test
    @DisplayName("flatMapTrampoline should return empty for empty list")
    void flatMapTrampoline_shouldReturnEmptyForEmptyList() {
      List<Integer> emptyList = List.of();

      List<Integer> result = ListPrisms.flatMapTrampoline(emptyList, n -> List.of(n, n));

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("flatMapTrampoline should handle functions returning empty lists")
    void flatMapTrampoline_shouldHandleEmptyResults() {
      List<Integer> list = List.of(1, 2, 3);

      List<Integer> result = ListPrisms.flatMapTrampoline(list, n -> List.of());

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("zipWithTrampoline should combine lists element-by-element")
    void zipWithTrampoline_shouldCombineLists() {
      List<String> names = List.of("Alice", "Bob", "Charlie");
      List<Integer> ages = List.of(25, 30, 35);

      List<String> result =
          ListPrisms.zipWithTrampoline(names, ages, (name, age) -> name + ":" + age);

      assertThat(result).containsExactly("Alice:25", "Bob:30", "Charlie:35");
    }

    @Test
    @DisplayName("zipWithTrampoline should stop at shorter list")
    void zipWithTrampoline_shouldStopAtShorterList() {
      List<Integer> list1 = List.of(1, 2, 3, 4, 5);
      List<Integer> list2 = List.of(10, 20);

      List<Integer> result = ListPrisms.zipWithTrampoline(list1, list2, Integer::sum);

      assertThat(result).containsExactly(11, 22);
    }

    @Test
    @DisplayName("zipWithTrampoline should return empty when first list is empty")
    void zipWithTrampoline_shouldReturnEmptyWhenFirstListEmpty() {
      List<Integer> emptyList = List.of();
      List<Integer> list2 = List.of(1, 2, 3);

      List<Integer> result = ListPrisms.zipWithTrampoline(emptyList, list2, Integer::sum);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("zipWithTrampoline should return empty when second list is empty")
    void zipWithTrampoline_shouldReturnEmptyWhenSecondListEmpty() {
      List<Integer> list1 = List.of(1, 2, 3);
      List<Integer> emptyList = List.of();

      List<Integer> result = ListPrisms.zipWithTrampoline(list1, emptyList, Integer::sum);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("takeTrampoline should take first n elements")
    void takeTrampoline_shouldTakeFirstNElements() {
      List<Integer> list = List.of(1, 2, 3, 4, 5);

      List<Integer> result = ListPrisms.takeTrampoline(list, 3);

      assertThat(result).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("takeTrampoline should return entire list when n > size")
    void takeTrampoline_shouldReturnEntireListWhenNExceedsSize() {
      List<Integer> list = List.of(1, 2, 3);

      List<Integer> result = ListPrisms.takeTrampoline(list, 10);

      assertThat(result).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("takeTrampoline should return empty when n is 0")
    void takeTrampoline_shouldReturnEmptyWhenNIsZero() {
      List<Integer> list = List.of(1, 2, 3);

      List<Integer> result = ListPrisms.takeTrampoline(list, 0);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("takeTrampoline should return empty for empty list")
    void takeTrampoline_shouldReturnEmptyForEmptyList() {
      List<Integer> emptyList = List.of();

      List<Integer> result = ListPrisms.takeTrampoline(emptyList, 5);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("dropTrampoline should drop first n elements")
    void dropTrampoline_shouldDropFirstNElements() {
      List<Integer> list = List.of(1, 2, 3, 4, 5);

      List<Integer> result = ListPrisms.dropTrampoline(list, 2);

      assertThat(result).containsExactly(3, 4, 5);
    }

    @Test
    @DisplayName("dropTrampoline should return empty when n >= size")
    void dropTrampoline_shouldReturnEmptyWhenNExceedsSize() {
      List<Integer> list = List.of(1, 2, 3);

      List<Integer> result = ListPrisms.dropTrampoline(list, 5);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("dropTrampoline should return entire list when n is 0")
    void dropTrampoline_shouldReturnEntireListWhenNIsZero() {
      List<Integer> list = List.of(1, 2, 3);

      List<Integer> result = ListPrisms.dropTrampoline(list, 0);

      assertThat(result).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("dropTrampoline should return empty for empty list")
    void dropTrampoline_shouldReturnEmptyForEmptyList() {
      List<Integer> emptyList = List.of();

      List<Integer> result = ListPrisms.dropTrampoline(emptyList, 5);

      assertThat(result).isEmpty();
    }
  }
}
