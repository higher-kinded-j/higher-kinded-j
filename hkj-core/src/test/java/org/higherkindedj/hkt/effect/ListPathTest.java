// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for ListPath.
 *
 * <p>Tests cover factory methods, list operations, positional zipWith, and conversions.
 */
@DisplayName("ListPath<A> Complete Test Suite")
class ListPathTest {

  @Nested
  @DisplayName("Factory Methods via Path")
  class FactoryMethodsTests {

    @Test
    @DisplayName("Path.listPath(List) creates ListPath from list")
    void listPathFromListCreatesListPath() {
      ListPath<Integer> path = Path.listPath(List.of(1, 2, 3));

      assertThat(path.run()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("Path.listPath(varargs) creates ListPath from varargs")
    void listPathFromVarargsCreatesListPath() {
      ListPath<String> path = Path.listPath("a", "b", "c");

      assertThat(path.run()).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("Path.listPathPure() creates single-element ListPath")
    void listPathPureCreatesSingleElement() {
      ListPath<Integer> path = Path.listPathPure(42);

      assertThat(path.run()).containsExactly(42);
    }

    @Test
    @DisplayName("Path.listPathEmpty() creates empty ListPath")
    void listPathEmptyCreatesEmpty() {
      ListPath<Integer> path = Path.listPathEmpty();

      assertThat(path.run()).isEmpty();
    }

    @Test
    @DisplayName("Path.listPathRange() creates range ListPath")
    void listPathRangeCreatesRange() {
      ListPath<Integer> path = Path.listPathRange(1, 5);

      assertThat(path.run()).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("ListPath.of(List) wraps list")
    void ofWrapsListImmutably() {
      // Use ArrayList to verify defensive copy (List.copyOf on immutable List.of returns same
      // instance)
      List<Integer> original = new ArrayList<>(List.of(1, 2, 3));
      ListPath<Integer> path = ListPath.of(original);

      assertThat(path.run()).containsExactly(1, 2, 3);
      assertThat(path.run()).isNotSameAs(original);
    }

    @Test
    @DisplayName("ListPath.of(List) validates non-null")
    void ofValidatesNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> ListPath.of((List<Object>) null))
          .withMessageContaining("list must not be null");
    }
  }

  @Nested
  @DisplayName("Terminal Operations")
  class TerminalOperationsTests {

    @Test
    @DisplayName("run() returns immutable list")
    void runReturnsImmutableList() {
      ListPath<Integer> path = ListPath.of(1, 2, 3);

      List<Integer> result = path.run();

      assertThat(result).containsExactly(1, 2, 3);
      assertThatThrownBy(() -> result.add(4)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("headOption() returns first element")
    void headOptionReturnsFirst() {
      ListPath<Integer> path = ListPath.of(1, 2, 3);

      assertThat(path.headOption()).hasValue(1);
    }

    @Test
    @DisplayName("headOption() returns empty for empty list")
    void headOptionReturnsEmptyForEmptyList() {
      ListPath<Integer> path = ListPath.empty();

      assertThat(path.headOption()).isEmpty();
    }

    @Test
    @DisplayName("lastOption() returns last element")
    void lastOptionReturnsLast() {
      ListPath<Integer> path = ListPath.of(1, 2, 3);

      assertThat(path.lastOption()).hasValue(3);
    }

    @Test
    @DisplayName("lastOption() returns empty for empty list")
    void lastOptionReturnsEmptyForEmptyList() {
      ListPath<Integer> path = ListPath.empty();

      assertThat(path.lastOption()).isEmpty();
    }

    @Test
    @DisplayName("isEmpty() returns true for empty list")
    void isEmptyReturnsTrueForEmpty() {
      ListPath<Integer> path = ListPath.empty();

      assertThat(path.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("isEmpty() returns false for non-empty list")
    void isEmptyReturnsFalseForNonEmpty() {
      ListPath<Integer> path = ListPath.of(1);

      assertThat(path.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("size() returns element count")
    void sizeReturnsElementCount() {
      ListPath<Integer> path = ListPath.of(1, 2, 3, 4, 5);

      assertThat(path.size()).isEqualTo(5);
    }

    @Test
    @DisplayName("anyMatch() returns true when predicate matches")
    void anyMatchReturnsTrueWhenMatches() {
      ListPath<Integer> path = ListPath.of(1, 2, 3, 4, 5);

      assertThat(path.anyMatch(n -> n > 3)).isTrue();
    }

    @Test
    @DisplayName("anyMatch() returns false when nothing matches")
    void anyMatchReturnsFalseWhenNoMatch() {
      ListPath<Integer> path = ListPath.of(1, 2, 3);

      assertThat(path.anyMatch(n -> n > 10)).isFalse();
    }

    @Test
    @DisplayName("allMatch() returns true when all match")
    void allMatchReturnsTrueWhenAllMatch() {
      ListPath<Integer> path = ListPath.of(2, 4, 6);

      assertThat(path.allMatch(n -> n % 2 == 0)).isTrue();
    }

    @Test
    @DisplayName("allMatch() returns false when not all match")
    void allMatchReturnsFalseWhenNotAllMatch() {
      ListPath<Integer> path = ListPath.of(2, 3, 4);

      assertThat(path.allMatch(n -> n % 2 == 0)).isFalse();
    }

    @Test
    @DisplayName("get() returns element at valid index")
    void getReturnsElementAtValidIndex() {
      ListPath<String> path = ListPath.of("a", "b", "c");

      assertThat(path.get(1)).hasValue("b");
    }

    @Test
    @DisplayName("get() returns empty for invalid index")
    void getReturnsEmptyForInvalidIndex() {
      ListPath<String> path = ListPath.of("a", "b", "c");

      assertThat(path.get(-1)).isEmpty();
      assertThat(path.get(3)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Composable Operations")
  class ComposableOperationsTests {

    @Test
    @DisplayName("map() transforms each element")
    void mapTransformsEachElement() {
      ListPath<Integer> path = ListPath.of(1, 2, 3);

      ListPath<Integer> result = path.map(n -> n * 2);

      assertThat(result.run()).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("map() validates non-null mapper")
    void mapValidatesNonNullMapper() {
      ListPath<Integer> path = ListPath.of(1, 2, 3);

      assertThatNullPointerException()
          .isThrownBy(() -> path.map(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("peek() observes each element")
    void peekObservesEachElement() {
      ListPath<Integer> path = ListPath.of(1, 2, 3);
      AtomicInteger sum = new AtomicInteger(0);

      ListPath<Integer> result = path.peek(sum::addAndGet);

      assertThat(result).isSameAs(path);
      assertThat(sum.get()).isEqualTo(6);
    }
  }

  @Nested
  @DisplayName("Positional ZipWith Operations")
  class ZipWithOperationsTests {

    @Test
    @DisplayName("zipWith() pairs elements positionally")
    void zipWithPairsPositionally() {
      ListPath<Integer> nums = ListPath.of(1, 2, 3);
      ListPath<String> strs = ListPath.of("a", "b", "c");

      ListPath<String> result = nums.zipWith(strs, (n, s) -> n + s);

      assertThat(result.run()).containsExactly("1a", "2b", "3c");
    }

    @Test
    @DisplayName("zipWith() uses shorter list length")
    void zipWithUsesShorterLength() {
      ListPath<Integer> nums = ListPath.of(1, 2, 3, 4, 5);
      ListPath<String> strs = ListPath.of("a", "b");

      ListPath<String> result = nums.zipWith(strs, (n, s) -> n + s);

      assertThat(result.run()).containsExactly("1a", "2b");
    }

    @Test
    @DisplayName("zipWith() with empty returns empty")
    void zipWithEmptyReturnsEmpty() {
      ListPath<Integer> nums = ListPath.of(1, 2, 3);
      ListPath<String> empty = ListPath.empty();

      ListPath<String> result = nums.zipWith(empty, (n, s) -> n + s);

      assertThat(result.run()).isEmpty();
    }

    @Test
    @DisplayName("zipWith() validates non-null parameters")
    void zipWithValidatesNonNullParameters() {
      ListPath<Integer> path = ListPath.of(1, 2, 3);

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(null, (Integer a, Integer b) -> a + b))
          .withMessageContaining("other must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(ListPath.of("a"), null))
          .withMessageContaining("combiner must not be null");
    }

    @Test
    @DisplayName("zipWith() throws for non-ListPath")
    void zipWithThrowsForNonListPath() {
      ListPath<Integer> path = ListPath.of(1, 2, 3);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.zipWith(NonDetPath.of(4, 5, 6), (a, b) -> a + b))
          .withMessageContaining("Cannot zipWith non-ListPath");
    }

    @Test
    @DisplayName("zipWith3() combines three lists positionally")
    void zipWith3CombinesThreeLists() {
      ListPath<Integer> first = ListPath.of(1, 2, 3);
      ListPath<String> second = ListPath.of("a", "b", "c");
      ListPath<Integer> third = ListPath.of(10, 20, 30);

      ListPath<String> result = first.zipWith3(second, third, (a, b, c) -> a + b + c);

      assertThat(result.run()).containsExactly("1a10", "2b20", "3c30");
    }

    @Test
    @DisplayName("zipWith3() uses shortest list length")
    void zipWith3UsesShortestLength() {
      ListPath<Integer> first = ListPath.of(1, 2, 3, 4);
      ListPath<String> second = ListPath.of("a", "b");
      ListPath<Integer> third = ListPath.of(10, 20, 30);

      ListPath<String> result = first.zipWith3(second, third, (a, b, c) -> a + b + c);

      assertThat(result.run()).containsExactly("1a10", "2b20");
    }
  }

  @Nested
  @DisplayName("Chainable Operations")
  class ChainableOperationsTests {

    @Test
    @DisplayName("via() flatMaps and concatenates results")
    void viaFlatMapsAndConcatenates() {
      ListPath<Integer> path = ListPath.of(1, 2, 3);

      ListPath<Integer> result = path.via(n -> ListPath.of(n, n * 10));

      assertThat(result.run()).containsExactly(1, 10, 2, 20, 3, 30);
    }

    @Test
    @DisplayName("via() with empty returns empty")
    void viaWithEmptyReturnsEmpty() {
      ListPath<Integer> path = ListPath.of(1, 2, 3);

      ListPath<Integer> result = path.via(n -> ListPath.empty());

      assertThat(result.run()).isEmpty();
    }

    @Test
    @DisplayName("via() validates non-null mapper")
    void viaValidatesNonNullMapper() {
      ListPath<Integer> path = ListPath.of(1, 2, 3);

      assertThatNullPointerException()
          .isThrownBy(() -> path.via(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("via() throws for non-ListPath result")
    void viaThrowsForNonListPathResult() {
      ListPath<Integer> path = ListPath.of(1, 2, 3);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.via(n -> NonDetPath.of(n)))
          .withMessageContaining("via mapper must return ListPath");
    }

    @Test
    @DisplayName("then() sequences and concatenates")
    void thenSequencesAndConcatenates() {
      ListPath<Integer> path = ListPath.of(1, 2, 3);

      ListPath<String> result = path.then(() -> ListPath.of("x", "y"));

      assertThat(result.run()).containsExactly("x", "y", "x", "y", "x", "y");
    }
  }

  @Nested
  @DisplayName("List-Specific Operations")
  class ListSpecificOperationsTests {

    @Test
    @DisplayName("filter() keeps matching elements")
    void filterKeepsMatchingElements() {
      ListPath<Integer> path = ListPath.of(1, 2, 3, 4, 5, 6);

      ListPath<Integer> result = path.filter(n -> n % 2 == 0);

      assertThat(result.run()).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("take() takes first n elements")
    void takeTakesFirstN() {
      ListPath<Integer> path = ListPath.of(1, 2, 3, 4, 5);

      ListPath<Integer> result = path.take(3);

      assertThat(result.run()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("drop() drops first n elements")
    void dropDropsFirstN() {
      ListPath<Integer> path = ListPath.of(1, 2, 3, 4, 5);

      ListPath<Integer> result = path.drop(2);

      assertThat(result.run()).containsExactly(3, 4, 5);
    }

    @Test
    @DisplayName("takeWhile() takes while predicate holds")
    void takeWhileTakesWhilePredicateHolds() {
      ListPath<Integer> path = ListPath.of(1, 2, 3, 4, 5);

      ListPath<Integer> result = path.takeWhile(n -> n < 4);

      assertThat(result.run()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("dropWhile() drops while predicate holds")
    void dropWhileDropsWhilePredicateHolds() {
      ListPath<Integer> path = ListPath.of(1, 2, 3, 4, 5);

      ListPath<Integer> result = path.dropWhile(n -> n < 3);

      assertThat(result.run()).containsExactly(3, 4, 5);
    }

    @Test
    @DisplayName("distinct() removes duplicates")
    void distinctRemovesDuplicates() {
      ListPath<Integer> path = ListPath.of(1, 2, 2, 3, 1, 4, 3);

      ListPath<Integer> result = path.distinct();

      assertThat(result.run()).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("concat() concatenates lists")
    void concatConcatenatesLists() {
      ListPath<Integer> first = ListPath.of(1, 2, 3);
      ListPath<Integer> second = ListPath.of(4, 5, 6);

      ListPath<Integer> result = first.concat(second);

      assertThat(result.run()).containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    @DisplayName("foldLeft() folds from left")
    void foldLeftFoldsFromLeft() {
      ListPath<Integer> path = ListPath.of(1, 2, 3, 4);

      Integer result = path.foldLeft(0, Integer::sum);

      assertThat(result).isEqualTo(10);
    }

    @Test
    @DisplayName("foldRight() folds from right")
    void foldRightFoldsFromRight() {
      ListPath<String> path = ListPath.of("a", "b", "c");

      String result = path.foldRight("", (a, acc) -> a + acc);

      assertThat(result).isEqualTo("abc");
    }

    @Test
    @DisplayName("reverse() reverses order")
    void reverseReversesOrder() {
      ListPath<Integer> path = ListPath.of(1, 2, 3);

      ListPath<Integer> result = path.reverse();

      assertThat(result.run()).containsExactly(3, 2, 1);
    }
  }

  @Nested
  @DisplayName("Conversions")
  class ConversionsTests {

    @Test
    @DisplayName("toMaybePath() returns Just with first element")
    void toMaybePathReturnsJustWithFirst() {
      ListPath<Integer> path = ListPath.of(1, 2, 3);

      MaybePath<Integer> result = path.toMaybePath();

      assertThat(result.run().isJust()).isTrue();
      assertThat(result.run().get()).isEqualTo(1);
    }

    @Test
    @DisplayName("toMaybePath() returns Nothing for empty")
    void toMaybePathReturnsNothingForEmpty() {
      ListPath<Integer> path = ListPath.empty();

      MaybePath<Integer> result = path.toMaybePath();

      assertThat(result.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("toIOPath() creates IO producing the list")
    void toIOPathCreatesIOProducingList() {
      ListPath<Integer> path = ListPath.of(1, 2, 3);

      IOPath<List<Integer>> result = path.toIOPath();

      assertThat(result.unsafeRun()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("toStreamPath() creates StreamPath with same elements")
    void toStreamPathCreatesStreamPath() {
      ListPath<Integer> path = ListPath.of(1, 2, 3);

      StreamPath<Integer> result = path.toStreamPath();

      assertThat(result.toList()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("toNonDetPath() creates NonDetPath with same elements")
    void toNonDetPathCreatesNonDetPath() {
      ListPath<Integer> path = ListPath.of(1, 2, 3);

      NonDetPath<Integer> result = path.toNonDetPath();

      assertThat(result.run()).containsExactly(1, 2, 3);
    }
  }

  @Nested
  @DisplayName("Object Methods")
  class ObjectMethodsTests {

    @Test
    @DisplayName("equals() returns true for same instance (reference equality)")
    void equalsReturnsTrueForSameInstance() {
      ListPath<Integer> path = ListPath.of(1, 2, 3);

      assertThat(path.equals(path)).isTrue();
    }

    @Test
    @DisplayName("equals() returns true for same content")
    void equalsReturnsTrueForSameContent() {
      ListPath<Integer> path1 = ListPath.of(1, 2, 3);
      ListPath<Integer> path2 = ListPath.of(1, 2, 3);

      assertThat(path1).isEqualTo(path2);
    }

    @Test
    @DisplayName("equals() returns false for different content")
    void equalsReturnsFalseForDifferentContent() {
      ListPath<Integer> path1 = ListPath.of(1, 2, 3);
      ListPath<Integer> path2 = ListPath.of(1, 2, 4);

      assertThat(path1).isNotEqualTo(path2);
    }

    @Test
    @DisplayName("equals() returns false for non-ListPath")
    void equalsReturnsFalseForNonListPath() {
      ListPath<Integer> path = ListPath.of(1, 2, 3);

      assertThat(path.equals("not a path")).isFalse();
      assertThat(path.equals(null)).isFalse();
    }

    @Test
    @DisplayName("hashCode() is consistent with equals")
    void hashCodeConsistentWithEquals() {
      ListPath<Integer> path1 = ListPath.of(1, 2, 3);
      ListPath<Integer> path2 = ListPath.of(1, 2, 3);

      assertThat(path1.hashCode()).isEqualTo(path2.hashCode());
    }

    @Test
    @DisplayName("toString() shows content")
    void toStringShowsContent() {
      ListPath<Integer> path = ListPath.of(1, 2, 3);

      assertThat(path.toString()).isEqualTo("ListPath([1, 2, 3])");
    }
  }

  @Nested
  @DisplayName("Comparison with NonDetPath")
  class ComparisonWithNonDetPathTests {

    @Test
    @DisplayName("ListPath.zipWith is positional, NonDetPath.zipWith is Cartesian")
    void demonstrateDifference() {
      // Same input data
      List<Integer> nums = List.of(1, 2);
      List<String> strs = List.of("a", "b");

      // ListPath - positional pairing
      ListPath<String> listResult = ListPath.of(nums).zipWith(ListPath.of(strs), (n, s) -> n + s);
      // Result: ["1a", "2b"]

      // NonDetPath - Cartesian product
      NonDetPath<String> nonDetResult =
          NonDetPath.of(nums).zipWith(NonDetPath.of(strs), (n, s) -> n + s);
      // Result: ["1a", "1b", "2a", "2b"]

      assertThat(listResult.run()).containsExactly("1a", "2b");
      assertThat(nonDetResult.run()).containsExactly("1a", "1b", "2a", "2b");
    }
  }
}
