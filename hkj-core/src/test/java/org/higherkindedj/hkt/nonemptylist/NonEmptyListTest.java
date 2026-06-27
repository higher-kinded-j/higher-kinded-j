// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.nonemptylist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.NonEmptyListAssert.assertThatNonEmptyList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.maybe.Maybe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("NonEmptyList")
class NonEmptyListTest {

  @Nested
  @DisplayName("construction")
  class Construction {

    @Test
    @DisplayName("of(head) creates a single-element list")
    void ofSingle() {
      NonEmptyList<String> nel = NonEmptyList.of("x");
      assertThat(nel.head()).isEqualTo("x");
      assertThat(nel.tail()).isEmpty();
      assertThat(nel.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("of(head, rest...) creates a multi-element list")
    void ofVarargs() {
      NonEmptyList<Integer> nel = NonEmptyList.of(1, 2, 3);
      assertThat(nel.head()).isEqualTo(1);
      assertThat(nel.tail()).containsExactly(2, 3);
      assertThat(nel.size()).isEqualTo(3);
    }

    @Test
    @DisplayName("of(head, tail) creates a list from an explicit tail")
    void ofHeadAndTail() {
      NonEmptyList<Integer> nel = NonEmptyList.of(1, List.of(2, 3));
      assertThat(nel.toJavaList()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("single(value) creates a single-element list")
    void single() {
      NonEmptyList<Integer> nel = NonEmptyList.single(42);
      assertThat(nel.head()).isEqualTo(42);
      assertThat(nel.tail()).isEmpty();
    }

    @Test
    @DisplayName("fromList(empty) returns Nothing")
    void fromEmptyList() {
      Maybe<NonEmptyList<Integer>> result = NonEmptyList.fromList(List.of());
      assertThat(result.isNothing()).isTrue();
    }

    @Test
    @DisplayName("fromList(non-empty) returns Just")
    void fromNonEmptyList() {
      Maybe<NonEmptyList<Integer>> result = NonEmptyList.fromList(List.of(1, 2, 3));
      assertThat(result.isJust()).isTrue();
      assertThat(result.get().toJavaList()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("fromIterable(empty) returns Nothing")
    void fromEmptyIterable() {
      Maybe<NonEmptyList<Integer>> result = NonEmptyList.fromIterable(new ArrayList<>());
      assertThat(result.isNothing()).isTrue();
    }

    @Test
    @DisplayName("fromIterable(non-empty) returns Just")
    void fromNonEmptyIterable() {
      Maybe<NonEmptyList<Integer>> result = NonEmptyList.fromIterable(List.of(7, 8));
      assertThat(result.isJust()).isTrue();
      assertThat(result.get().toJavaList()).containsExactly(7, 8);
    }

    @Test
    @DisplayName("fromIterable returns the same instance for a NonEmptyList")
    void fromIterableNonEmptyList() {
      NonEmptyList<Integer> nel = NonEmptyList.of(1, 2);
      assertThat(NonEmptyList.fromIterable(nel).get()).isSameAs(nel);
    }

    @Test
    @DisplayName("fromIterable iterates a non-List iterable")
    void fromNonListIterable() {
      Maybe<NonEmptyList<Integer>> result =
          NonEmptyList.fromIterable(new LinkedHashSet<>(List.of(7, 8, 9)));
      assertThat(result.isJust()).isTrue();
      assertThat(result.get().toJavaList()).containsExactly(7, 8, 9);
    }

    @Test
    @DisplayName("fromIterable(empty non-List iterable) returns Nothing")
    void fromEmptyNonListIterable() {
      assertThat(NonEmptyList.fromIterable(new LinkedHashSet<Integer>()).isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("total accessors")
  class Accessors {

    @Test
    @DisplayName("last() returns head when tail is empty")
    void lastOfSingle() {
      assertThat(NonEmptyList.single(9).last()).isEqualTo(9);
    }

    @Test
    @DisplayName("last() returns the final tail element")
    void lastOfMany() {
      assertThat(NonEmptyList.of(1, 2, 3).last()).isEqualTo(3);
    }

    @Test
    @DisplayName("reduce combines all elements left-to-right")
    void reduce() {
      Semigroup<Integer> sum = (a, b) -> a + b;
      assertThat(NonEmptyList.of(1, 2, 3, 4).reduce(sum)).isEqualTo(10);
    }

    @Test
    @DisplayName("reduce on a single element returns that element")
    void reduceSingle() {
      Semigroup<Integer> sum = (a, b) -> a + b;
      assertThat(NonEmptyList.single(5).reduce(sum)).isEqualTo(5);
    }

    @Test
    @DisplayName("min returns the smallest element")
    void min() {
      assertThat(NonEmptyList.of(3, 1, 2).min(Comparator.naturalOrder())).isEqualTo(1);
    }

    @Test
    @DisplayName("max returns the largest element")
    void max() {
      assertThat(NonEmptyList.of(1, 3, 2).max(Comparator.naturalOrder())).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("fluent transforms")
  class Transforms {

    @Test
    @DisplayName("map applies to every element")
    void map() {
      assertThatNonEmptyList(NonEmptyList.of(1, 2, 3).map(n -> n * 10)).containsExactly(10, 20, 30);
    }

    @Test
    @DisplayName("map of a single element stays a singleton")
    void mapSingle() {
      assertThat(NonEmptyList.single(5).map(n -> n * 2)).isEqualTo(NonEmptyList.single(10));
    }

    @Test
    @DisplayName("flatMap flattens and stays non-empty")
    void flatMap() {
      NonEmptyList<Integer> result = NonEmptyList.of(1, 2).flatMap(n -> NonEmptyList.of(n, n * 10));
      assertThatNonEmptyList(result).containsExactly(1, 10, 2, 20);
    }

    @Test
    @DisplayName("flatMap of a single element returns the mapped list")
    void flatMapSingle() {
      assertThatNonEmptyList(NonEmptyList.single(5).flatMap(n -> NonEmptyList.of(n, n + 1)))
          .containsExactly(5, 6);
    }

    @Test
    @DisplayName("foldLeft folds from the seed, visiting head first")
    void foldLeft() {
      String result = NonEmptyList.of(1, 2, 3).foldLeft("start", (acc, n) -> acc + "-" + n);
      assertThat(result).isEqualTo("start-1-2-3");
    }

    @Test
    @DisplayName("reverse on a single element is unchanged")
    void reverseSingle() {
      assertThat(NonEmptyList.single(5).reverse()).isEqualTo(NonEmptyList.single(5));
    }

    @Test
    @DisplayName("reverse reverses element order")
    void reverse() {
      assertThatNonEmptyList(NonEmptyList.of(1, 2, 3).reverse()).containsExactly(3, 2, 1);
    }

    @Test
    @DisplayName("concat joins two lists, left first")
    void concat() {
      assertThatNonEmptyList(NonEmptyList.of(1, 2).concat(NonEmptyList.of(3, 4)))
          .containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("append adds an element at the end")
    void append() {
      assertThatNonEmptyList(NonEmptyList.of(1, 2).append(3)).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("prepend adds an element at the front as the new head")
    void prepend() {
      NonEmptyList<Integer> result = NonEmptyList.of(2, 3).prepend(1);
      assertThatNonEmptyList(result).hasHead(1).containsExactly(1, 2, 3);
    }
  }

  @Nested
  @DisplayName("interop")
  class Interop {

    @Test
    @DisplayName("toJavaList preserves order and is immutable")
    void toJavaList() {
      List<Integer> list = NonEmptyList.of(1, 2, 3).toJavaList();
      assertThat(list).containsExactly(1, 2, 3);
      assertThatThrownBy(() -> list.add(4)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("is iterable in a for-each loop")
    void iterable() {
      int total = 0;
      for (int n : NonEmptyList.of(1, 2, 3)) {
        total += n;
      }
      assertThat(total).isEqualTo(6);
    }

    @Test
    @DisplayName("toString lists all elements")
    void toStringContents() {
      assertThat(NonEmptyList.of(1, 2, 3)).hasToString("NonEmptyList[1, 2, 3]");
    }
  }

  @Nested
  @DisplayName("immutability")
  class Immutability {

    @Test
    @DisplayName("tail() returns an unmodifiable list")
    void tailUnmodifiable() {
      List<Integer> tail = NonEmptyList.of(1, 2, 3).tail();
      assertThatThrownBy(() -> tail.add(9)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("mutating the source list does not affect the NonEmptyList")
    void defensiveCopy() {
      List<Integer> source = new ArrayList<>(List.of(2, 3));
      NonEmptyList<Integer> nel = NonEmptyList.of(1, source);
      source.add(99);
      assertThat(nel.tail()).containsExactly(2, 3);
      assertThat(nel.size()).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("semigroup")
  class SemigroupBehaviour {

    private final Semigroup<NonEmptyList<Integer>> semigroup = NonEmptyList.semigroup();

    @Test
    @DisplayName("combine concatenates left-to-right")
    void combine() {
      assertThat(semigroup.combine(NonEmptyList.of(1, 2), NonEmptyList.of(3)).toJavaList())
          .containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("is associative")
    void associative() {
      NonEmptyList<Integer> a = NonEmptyList.of(1);
      NonEmptyList<Integer> b = NonEmptyList.of(2);
      NonEmptyList<Integer> c = NonEmptyList.of(3);
      assertThat(semigroup.combine(a, semigroup.combine(b, c)))
          .isEqualTo(semigroup.combine(semigroup.combine(a, b), c));
    }

    @Test
    @DisplayName("is not commutative — order is preserved")
    void notCommutative() {
      NonEmptyList<Integer> left = semigroup.combine(NonEmptyList.of(1), NonEmptyList.of(2));
      NonEmptyList<Integer> right = semigroup.combine(NonEmptyList.of(2), NonEmptyList.of(1));
      assertThat(left).isNotEqualTo(right);
      assertThat(left.toJavaList()).containsExactly(1, 2);
      assertThat(right.toJavaList()).containsExactly(2, 1);
    }
  }

  @Nested
  @DisplayName("equality")
  class Equality {

    @Test
    @DisplayName("equal lists are equal with equal hash codes")
    void equalLists() {
      NonEmptyList<Integer> a = NonEmptyList.of(1, 2, 3);
      NonEmptyList<Integer> b = NonEmptyList.of(1, 2, 3);
      assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    @DisplayName("differing lists are not equal")
    void differingLists() {
      assertThat(NonEmptyList.of(1, 2, 3))
          .isNotEqualTo(NonEmptyList.of(1, 2, 4))
          .isNotEqualTo(null)
          .isNotEqualTo("not a list");
    }
  }

  @Nested
  @DisplayName("null safety")
  class NullSafety {

    @Test
    @DisplayName("a null head is rejected")
    void nullHead() {
      assertThatThrownBy(() -> new NonEmptyList<>(null, List.of()))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("a null tail element is rejected")
    void nullTailElement() {
      assertThatThrownBy(() -> NonEmptyList.of(1, Arrays.asList(2, null)))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("map rejects a null mapper")
    void nullMapper() {
      assertThatThrownBy(() -> NonEmptyList.of(1).map(null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("flatMap rejects a null result")
    void nullFlatMapResult() {
      assertThatThrownBy(() -> NonEmptyList.of(1).flatMap(n -> null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("flatMap rejects a null result for a tail element")
    void nullFlatMapTailResult() {
      assertThatThrownBy(
              () -> NonEmptyList.of(1, 2).flatMap(n -> n == 2 ? null : NonEmptyList.single(n)))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("reduce rejects a null semigroup")
    void nullSemigroup() {
      assertThatThrownBy(() -> NonEmptyList.of(1).reduce(null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("fromList rejects a null list")
    void nullFromList() {
      assertThatThrownBy(() -> NonEmptyList.fromList(null))
          .isInstanceOf(NullPointerException.class);
    }
  }
}
