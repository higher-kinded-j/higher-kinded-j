// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.Monoids;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for NonDetPath.
 *
 * <p>Tests cover factory methods, non-deterministic operations, Composable/Combinable/Chainable
 * operations, and conversions.
 */
@DisplayName("NonDetPath<A> Complete Test Suite")
class NonDetPathTest {

  private static final String TEST_VALUE = "test";

  @Nested
  @DisplayName("Factory Methods via Path")
  class FactoryMethodsTests {

    @Test
    @DisplayName("Path.list(List) creates NonDetPath from list")
    void listFromListCreatesPath() {
      NonDetPath<Integer> path = Path.list(List.of(1, 2, 3));

      assertThat(path.run()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("Path.list(varargs) creates NonDetPath from varargs")
    void listFromVarargsCreatesPath() {
      NonDetPath<String> path = Path.list("a", "b", "c");

      assertThat(path.run()).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("Path.listPure() creates single-element NonDetPath")
    void listPureCreatesSingleElement() {
      NonDetPath<String> path = Path.listPure(TEST_VALUE);

      assertThat(path.run()).containsExactly(TEST_VALUE);
    }

    @Test
    @DisplayName("Path.listEmpty() creates empty NonDetPath")
    void listEmptyCreatesEmpty() {
      NonDetPath<String> path = Path.listEmpty();

      assertThat(path.run()).isEmpty();
    }

    @Test
    @DisplayName("NonDetPath.of(List) creates path from list")
    void staticOfFromList() {
      NonDetPath<Integer> path = NonDetPath.of(List.of(1, 2, 3));

      assertThat(path.run()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("NonDetPath.range() creates range of integers")
    void staticRangeCreatesRange() {
      NonDetPath<Integer> path = NonDetPath.range(1, 5);

      assertThat(path.run()).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("NonDetPath.pure() creates single element")
    void staticPureCreatesSingle() {
      NonDetPath<String> path = NonDetPath.pure(TEST_VALUE);

      assertThat(path.run()).containsExactly(TEST_VALUE);
    }

    @Test
    @DisplayName("NonDetPath.empty() creates empty path")
    void staticEmptyCreatesEmpty() {
      NonDetPath<String> path = NonDetPath.empty();

      assertThat(path.run()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Run and Terminal Methods")
  class RunAndTerminalMethodsTests {

    @Test
    @DisplayName("run() returns underlying list")
    void runReturnsUnderlyingList() {
      NonDetPath<Integer> path = NonDetPath.of(List.of(1, 2, 3));

      assertThat(path.run()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("headOption() returns first element")
    void headOptionReturnsFirst() {
      NonDetPath<Integer> path = NonDetPath.of(List.of(1, 2, 3));

      assertThat(path.headOption()).contains(1);
    }

    @Test
    @DisplayName("headOption() returns empty for empty list")
    void headOptionReturnsEmptyForEmpty() {
      NonDetPath<Integer> path = NonDetPath.empty();

      assertThat(path.headOption()).isEmpty();
    }

    @Test
    @DisplayName("isEmpty() returns true for empty list")
    void isEmptyReturnsTrue() {
      NonDetPath<Integer> path = NonDetPath.empty();

      assertThat(path.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("isEmpty() returns false for non-empty list")
    void isEmptyReturnsFalse() {
      NonDetPath<Integer> path = NonDetPath.of(List.of(1));

      assertThat(path.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("size() returns element count")
    void sizeReturnsCount() {
      NonDetPath<Integer> path = NonDetPath.of(List.of(1, 2, 3));

      assertThat(path.size()).isEqualTo(3);
    }

    @Test
    @DisplayName("lastOption() returns last element")
    void lastOptionReturnsLast() {
      NonDetPath<Integer> path = NonDetPath.of(List.of(1, 2, 3));

      assertThat(path.lastOption()).contains(3);
    }

    @Test
    @DisplayName("lastOption() returns empty for empty path")
    void lastOptionReturnsEmptyForEmpty() {
      NonDetPath<Integer> path = NonDetPath.empty();

      assertThat(path.lastOption()).isEmpty();
    }

    @Test
    @DisplayName("anyMatch() returns true when any element matches")
    void anyMatchReturnsTrueWhenAnyMatches() {
      NonDetPath<Integer> path = NonDetPath.of(List.of(1, 2, 3));

      assertThat(path.anyMatch(n -> n == 2)).isTrue();
      assertThat(path.anyMatch(n -> n > 3)).isFalse();
    }

    @Test
    @DisplayName("anyMatch() returns false for empty path")
    void anyMatchReturnsFalseForEmpty() {
      NonDetPath<Integer> path = NonDetPath.empty();

      assertThat(path.anyMatch(n -> true)).isFalse();
    }

    @Test
    @DisplayName("anyMatch() validates null predicate")
    void anyMatchValidatesNullPredicate() {
      NonDetPath<Integer> path = NonDetPath.of(List.of(1));

      assertThatNullPointerException()
          .isThrownBy(() -> path.anyMatch(null))
          .withMessageContaining("predicate must not be null");
    }

    @Test
    @DisplayName("allMatch() returns true only when every element matches")
    void allMatchReturnsTrueWhenAllMatch() {
      NonDetPath<Integer> path = NonDetPath.of(List.of(2, 4, 6));

      assertThat(path.allMatch(n -> n % 2 == 0)).isTrue();
      assertThat(path.allMatch(n -> n > 2)).isFalse();
    }

    @Test
    @DisplayName("allMatch() returns true for empty path")
    void allMatchReturnsTrueForEmpty() {
      NonDetPath<Integer> path = NonDetPath.empty();

      assertThat(path.allMatch(n -> false)).isTrue();
    }

    @Test
    @DisplayName("allMatch() validates null predicate")
    void allMatchValidatesNullPredicate() {
      NonDetPath<Integer> path = NonDetPath.of(List.of(1));

      assertThatNullPointerException()
          .isThrownBy(() -> path.allMatch(null))
          .withMessageContaining("predicate must not be null");
    }

    @Test
    @DisplayName("get() returns element at valid index")
    void getReturnsElementAtIndex() {
      NonDetPath<String> path = NonDetPath.of(List.of("a", "b", "c"));

      assertThat(path.get(0)).contains("a");
      assertThat(path.get(2)).contains("c");
    }

    @Test
    @DisplayName("get() returns empty for a negative index or one past the end")
    void getReturnsEmptyForInvalidIndex() {
      NonDetPath<String> path = NonDetPath.of(List.of("a", "b", "c"));

      assertThat(path.get(-1)).isEmpty();
      assertThat(path.get(3)).isEmpty();
    }

    @Test
    @DisplayName("get() returns empty for empty path")
    void getReturnsEmptyForEmpty() {
      NonDetPath<String> path = NonDetPath.empty();

      assertThat(path.get(0)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Composable Operations (map, peek)")
  class ComposableOperationsTests {

    @Test
    @DisplayName("map() transforms all elements")
    void mapTransformsAllElements() {
      NonDetPath<Integer> path = NonDetPath.of(List.of(1, 2, 3));

      NonDetPath<Integer> result = path.map(n -> n * 2);

      assertThat(result.run()).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("map() validates null mapper")
    void mapValidatesNullMapper() {
      NonDetPath<String> path = NonDetPath.pure(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.map(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("map() chains correctly")
    void mapChainsCorrectly() {
      NonDetPath<String> path = NonDetPath.of(List.of("hello", "world"));

      NonDetPath<String> result = path.map(String::toUpperCase).map(s -> s + "!");

      assertThat(result.run()).containsExactly("HELLO!", "WORLD!");
    }

    @Test
    @DisplayName("peek() observes elements without modifying")
    void peekObservesElementsWithoutModifying() {
      NonDetPath<Integer> path = NonDetPath.of(List.of(1, 2, 3));
      AtomicInteger sum = new AtomicInteger(0);

      NonDetPath<Integer> result = path.peek(sum::addAndGet);

      assertThat(result.run()).containsExactly(1, 2, 3);
      assertThat(sum.get()).isEqualTo(6);
    }
  }

  @Nested
  @DisplayName("Chainable Operations (via, flatMap, then)")
  class ChainableOperationsTests {

    @Test
    @DisplayName("via() performs flatMap producing cartesian product")
    void viaPerformsFlatMap() {
      NonDetPath<Integer> path = NonDetPath.of(List.of(1, 2));

      NonDetPath<String> result = path.via(n -> NonDetPath.of(List.of(n + "a", n + "b")));

      assertThat(result.run()).containsExactly("1a", "1b", "2a", "2b");
    }

    @Test
    @DisplayName("via() validates null mapper")
    void viaValidatesNullMapper() {
      NonDetPath<String> path = NonDetPath.pure(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.via(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("via() validates non-null result")
    void viaValidatesNonNullResult() {
      NonDetPath<String> path = NonDetPath.pure(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.via(s -> null))
          .withMessageContaining("mapper must not return null");
    }

    @Test
    @DisplayName("via() validates result is NonDetPath")
    @SuppressWarnings("path-type-mismatch") // the mismatch is the behaviour under test
    void viaValidatesResultType() {
      NonDetPath<String> path = NonDetPath.pure(TEST_VALUE);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.via(Mismatched.step(s -> Path.just(s))))
          .withMessageContaining("via mapper must return NonDetPath");
    }

    @Test
    @DisplayName("via() with empty produces empty")
    void viaWithEmptyProducesEmpty() {
      NonDetPath<Integer> path = NonDetPath.of(List.of(1, 2, 3));

      NonDetPath<String> result = path.via(n -> NonDetPath.empty());

      assertThat(result.run()).isEmpty();
    }

    @Test
    @DisplayName("flatMap() is alias for via")
    void flatMapIsAliasForVia() {
      NonDetPath<Integer> path = NonDetPath.of(List.of(1, 2));

      NonDetPath<Integer> viaResult = path.via(n -> NonDetPath.of(List.of(n, n * 10)));
      @SuppressWarnings("unchecked")
      NonDetPath<Integer> flatMapResult =
          (NonDetPath<Integer>) path.flatMap(n -> NonDetPath.of(List.of(n, n * 10)));

      assertThat(flatMapResult.run()).isEqualTo(viaResult.run());
    }

    @Test
    @DisplayName("then() replaces each element with supplied path")
    void thenReplacesElements() {
      NonDetPath<String> path = NonDetPath.of(List.of("a", "b"));

      NonDetPath<Integer> result = path.then(() -> NonDetPath.of(List.of(1, 2)));

      assertThat(result.run()).containsExactly(1, 2, 1, 2);
    }
  }

  @Nested
  @DisplayName("Combinable Operations (zipWith)")
  class CombinableOperationsTests {

    @Test
    @DisplayName("zipWith() produces cartesian product")
    void zipWithProducesCartesianProduct() {
      NonDetPath<Integer> first = NonDetPath.of(List.of(1, 2));
      NonDetPath<String> second = NonDetPath.of(List.of("a", "b"));

      NonDetPath<String> result = first.zipWith(second, (n, s) -> n + s);

      assertThat(result.run()).containsExactly("1a", "1b", "2a", "2b");
    }

    @Test
    @DisplayName("zipWith() validates null parameters")
    void zipWithValidatesNullParameters() {
      NonDetPath<String> path = NonDetPath.pure(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(null, (a, b) -> a + b))
          .withMessageContaining("other must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(NonDetPath.pure("x"), null))
          .withMessageContaining("combiner must not be null");
    }

    @Test
    @DisplayName("zipWith() throws when given non-NonDetPath")
    @SuppressWarnings("path-type-mismatch") // the mismatch is the behaviour under test
    void zipWithThrowsWhenGivenNonNonDetPath() {
      NonDetPath<String> path = NonDetPath.pure(TEST_VALUE);
      IdPath<Integer> idPath = Path.id(42);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.zipWith(Mismatched.other(idPath), (s, i) -> s + i))
          .withMessageContaining("Cannot zipWith non-NonDetPath");
    }

    @Test
    @DisplayName("zipWith3() produces triple cartesian product")
    void zipWith3ProducesTripleCartesianProduct() {
      NonDetPath<Integer> first = NonDetPath.of(List.of(1, 2));
      NonDetPath<String> second = NonDetPath.of(List.of("a"));
      NonDetPath<Boolean> third = NonDetPath.of(List.of(true, false));

      NonDetPath<String> result =
          first.zipWith3(second, third, (n, s, b) -> n + s + (b ? "T" : "F"));

      assertThat(result.run()).containsExactly("1aT", "1aF", "2aT", "2aF");
    }
  }

  @Nested
  @DisplayName("List-Specific Operations")
  class ListSpecificOperationsTests {

    @Test
    @DisplayName("filter() keeps matching elements")
    void filterKeepsMatchingElements() {
      NonDetPath<Integer> path = NonDetPath.of(List.of(1, 2, 3, 4, 5));

      NonDetPath<Integer> result = path.filter(n -> n % 2 == 0);

      assertThat(result.run()).containsExactly(2, 4);
    }

    @Test
    @DisplayName("take() limits elements")
    void takeLimitsElements() {
      NonDetPath<Integer> path = NonDetPath.of(List.of(1, 2, 3, 4, 5));

      NonDetPath<Integer> result = path.take(3);

      assertThat(result.run()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("drop() skips elements")
    void dropSkipsElements() {
      NonDetPath<Integer> path = NonDetPath.of(List.of(1, 2, 3, 4, 5));

      NonDetPath<Integer> result = path.drop(2);

      assertThat(result.run()).containsExactly(3, 4, 5);
    }

    @Test
    @DisplayName("takeWhile() takes elements while predicate holds")
    void takeWhileTakesWhilePredicateHolds() {
      NonDetPath<Integer> path = NonDetPath.of(List.of(1, 2, 3, 1, 2));

      NonDetPath<Integer> result = path.takeWhile(n -> n < 3);

      assertThat(result.run()).containsExactly(1, 2);
    }

    @Test
    @DisplayName("takeWhile() returns empty for empty path")
    void takeWhileReturnsEmptyForEmpty() {
      NonDetPath<Integer> path = NonDetPath.empty();

      assertThat(path.takeWhile(n -> true).run()).isEmpty();
    }

    @Test
    @DisplayName("takeWhile() validates null predicate")
    void takeWhileValidatesNullPredicate() {
      NonDetPath<Integer> path = NonDetPath.of(List.of(1));

      assertThatNullPointerException()
          .isThrownBy(() -> path.takeWhile(null))
          .withMessageContaining("predicate must not be null");
    }

    @Test
    @DisplayName("dropWhile() drops elements while predicate holds")
    void dropWhileDropsWhilePredicateHolds() {
      NonDetPath<Integer> path = NonDetPath.of(List.of(1, 2, 3, 1, 2));

      NonDetPath<Integer> result = path.dropWhile(n -> n < 3);

      assertThat(result.run()).containsExactly(3, 1, 2);
    }

    @Test
    @DisplayName("dropWhile() returns empty for empty path")
    void dropWhileReturnsEmptyForEmpty() {
      NonDetPath<Integer> path = NonDetPath.empty();

      assertThat(path.dropWhile(n -> true).run()).isEmpty();
    }

    @Test
    @DisplayName("dropWhile() validates null predicate")
    void dropWhileValidatesNullPredicate() {
      NonDetPath<Integer> path = NonDetPath.of(List.of(1));

      assertThatNullPointerException()
          .isThrownBy(() -> path.dropWhile(null))
          .withMessageContaining("predicate must not be null");
    }

    @Test
    @DisplayName("distinct() removes duplicates")
    void distinctRemovesDuplicates() {
      NonDetPath<Integer> path = NonDetPath.of(List.of(1, 2, 2, 3, 1, 4));

      NonDetPath<Integer> result = path.distinct();

      assertThat(result.run()).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("concat() combines two lists")
    void concatCombinesLists() {
      NonDetPath<Integer> first = NonDetPath.of(List.of(1, 2));
      NonDetPath<Integer> second = NonDetPath.of(List.of(3, 4));

      NonDetPath<Integer> result = first.concat(second);

      assertThat(result.run()).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("foldLeft() reduces to single value")
    void foldLeftReducesToSingleValue() {
      NonDetPath<Integer> path = NonDetPath.of(List.of(1, 2, 3, 4));

      Integer result = path.foldLeft(0, Integer::sum);

      assertThat(result).isEqualTo(10);
    }

    @Test
    @DisplayName("foldRight() folds from right")
    void foldRightFoldsFromRight() {
      NonDetPath<String> path = NonDetPath.of("a", "b", "c");

      String result = path.foldRight("", (a, acc) -> a + acc);

      assertThat(result).isEqualTo("abc");
    }

    @Test
    @DisplayName("foldRight() returns initial value for an empty list")
    void foldRightReturnsInitialForEmpty() {
      NonDetPath<String> path = NonDetPath.empty();

      assertThat(path.foldRight("z", (a, acc) -> a + acc)).isEqualTo("z");
    }

    @Test
    @DisplayName("foldRight() with a single element combines element and initial value")
    void foldRightSingleElement() {
      NonDetPath<String> path = NonDetPath.of("a");

      assertThat(path.foldRight("z", (a, acc) -> a + acc)).isEqualTo("az");
    }

    @Test
    @DisplayName("foldRight() throws NullPointerException when f is null")
    void foldRightThrowsWhenFunctionNull() {
      NonDetPath<String> path = NonDetPath.of("a", "b");

      assertThatThrownBy(() -> path.foldRight("", null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("f must not be null");
    }

    @Test
    @DisplayName("fold() reduces multiple elements with identity")
    void foldReducesMultipleElements() {
      NonDetPath<Integer> path = NonDetPath.of(1, 2, 3, 4);

      Integer result = path.fold(0, Integer::sum);

      assertThat(result).isEqualTo(10);
    }

    @Test
    @DisplayName("fold() combines elements from left to right")
    void foldCombinesInOrder() {
      NonDetPath<String> path = NonDetPath.of("a", "b", "c");

      assertThat(path.fold("", String::concat)).isEqualTo("abc");
    }

    @Test
    @DisplayName("fold() returns identity for an empty list")
    void foldReturnsIdentityForEmpty() {
      NonDetPath<Integer> path = NonDetPath.empty();

      assertThat(path.fold(42, Integer::sum)).isEqualTo(42);
    }

    @Test
    @DisplayName("fold() with a single element combines identity and element")
    void foldSingleElement() {
      NonDetPath<Integer> path = NonDetPath.of(7);

      assertThat(path.fold(0, Integer::sum)).isEqualTo(7);
    }

    @Test
    @DisplayName("fold() throws NullPointerException when op is null")
    void foldThrowsWhenOpNull() {
      NonDetPath<Integer> path = NonDetPath.of(1, 2, 3);

      assertThatThrownBy(() -> path.fold(0, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("op must not be null");
    }

    @Test
    @DisplayName("foldMap() maps and combines via a Monoid (type-changing)")
    void foldMapCombinesViaMonoid() {
      NonDetPath<Integer> path = NonDetPath.of(1, 2, 3);

      String result = path.foldMap(Monoids.string(), i -> i + ",");

      assertThat(result).isEqualTo("1,2,3,");
    }

    @Test
    @DisplayName("foldMap() returns monoid identity for an empty list")
    void foldMapReturnsEmptyForEmptyList() {
      NonDetPath<Integer> path = NonDetPath.empty();

      assertThat(path.foldMap(Monoids.integerAddition(), i -> i)).isEqualTo(0);
    }

    @Test
    @DisplayName("foldMap() with a single element")
    void foldMapSingleElement() {
      NonDetPath<Integer> path = NonDetPath.of(5);

      assertThat(path.foldMap(Monoids.integerAddition(), i -> i * 2)).isEqualTo(10);
    }

    @Test
    @DisplayName("foldMap() throws NullPointerException when monoid is null")
    void foldMapThrowsWhenMonoidNull() {
      NonDetPath<Integer> path = NonDetPath.of(1);

      assertThatThrownBy(() -> path.foldMap(null, i -> i))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("monoid must not be null");
    }

    @Test
    @DisplayName("foldMap() throws NullPointerException when f is null")
    void foldMapThrowsWhenFunctionNull() {
      NonDetPath<Integer> path = NonDetPath.of(1);

      assertThatThrownBy(() -> path.foldMap(Monoids.integerAddition(), null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("f must not be null");
    }

    @Test
    @DisplayName("reverse() reverses order")
    void reverseReversesOrder() {
      NonDetPath<Integer> path = NonDetPath.of(List.of(1, 2, 3));

      NonDetPath<Integer> result = path.reverse();

      assertThat(result.run()).containsExactly(3, 2, 1);
    }
  }

  @Nested
  @DisplayName("Conversion Methods")
  class ConversionMethodsTests {

    @Test
    @DisplayName("toMaybePath() returns Just for first element")
    void toMaybePathReturnsJustForFirst() {
      NonDetPath<Integer> path = NonDetPath.of(List.of(1, 2, 3));

      MaybePath<Integer> result = path.toMaybePath();

      assertThat(result.run().isJust()).isTrue();
      assertThat(result.run().get()).isEqualTo(1);
    }

    @Test
    @DisplayName("toMaybePath() returns Nothing for empty")
    void toMaybePathReturnsNothingForEmpty() {
      NonDetPath<Integer> path = NonDetPath.empty();

      MaybePath<Integer> result = path.toMaybePath();

      assertThat(result.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("toIOPath() returns IOPath producing list")
    void toIOPathProducesList() {
      NonDetPath<Integer> path = NonDetPath.of(List.of(1, 2, 3));

      IOPath<List<Integer>> result = path.toIOPath();

      assertThat(result.unsafeRun()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("toStreamPath() converts to StreamPath")
    void toStreamPathConverts() {
      NonDetPath<Integer> path = NonDetPath.of(List.of(1, 2, 3));

      StreamPath<Integer> result = path.toStreamPath();

      assertThat(result.toList()).containsExactly(1, 2, 3);
    }
  }

  @Nested
  @DisplayName("Object Methods")
  class ObjectMethodsTests {

    @Test
    @DisplayName("equals() returns true for same instance")
    void equalsReturnsTrueForSameInstance() {
      NonDetPath<Integer> path = NonDetPath.of(List.of(1, 2, 3));

      assertThat(path).isEqualTo(path);
    }

    @Test
    @DisplayName("equals() returns false for non-NonDetPath")
    void equalsReturnsFalseForNonNonDetPath() {
      NonDetPath<Integer> path = NonDetPath.of(List.of(1, 2, 3));

      assertThat(path.equals("not a NonDetPath")).isFalse();
      assertThat(path.equals(null)).isFalse();
      assertThat(path.equals(Path.id(42))).isFalse();
    }

    @Test
    @DisplayName("equals() compares underlying lists")
    void equalsComparesUnderlyingLists() {
      NonDetPath<Integer> path1 = NonDetPath.of(List.of(1, 2, 3));
      NonDetPath<Integer> path2 = NonDetPath.of(List.of(1, 2, 3));
      NonDetPath<Integer> path3 = NonDetPath.of(List.of(1, 2));

      assertThat(path1).isEqualTo(path2);
      assertThat(path1).isNotEqualTo(path3);
    }

    @Test
    @DisplayName("hashCode() is consistent with equals")
    void hashCodeIsConsistentWithEquals() {
      NonDetPath<Integer> path1 = NonDetPath.of(List.of(1, 2, 3));
      NonDetPath<Integer> path2 = NonDetPath.of(List.of(1, 2, 3));

      assertThat(path1.hashCode()).isEqualTo(path2.hashCode());
    }

    @Test
    @DisplayName("toString() provides meaningful representation")
    void toStringProvidesMeaningfulRepresentation() {
      NonDetPath<Integer> path = NonDetPath.of(List.of(1, 2, 3));

      assertThat(path.toString()).contains("NonDetPath");
      assertThat(path.toString()).contains("1");
    }
  }

  @Nested
  @DisplayName("Practical Usage Patterns")
  class PracticalUsagePatternsTests {

    @Test
    @DisplayName("Can generate all pairs")
    void canGenerateAllPairs() {
      NonDetPath<String> pairs =
          NonDetPath.of(List.of(1, 2, 3))
              .via(x -> NonDetPath.of(List.of("a", "b")).map(y -> x + y));

      assertThat(pairs.run()).containsExactly("1a", "1b", "2a", "2b", "3a", "3b");
    }

    @Test
    @DisplayName("Can solve constraint problem")
    void canSolveConstraintProblem() {
      // Find all pairs (x, y) where x + y = 5 and both are in [1, 4]
      NonDetPath<String> solutions =
          NonDetPath.range(1, 5)
              .via(
                  x ->
                      NonDetPath.range(1, 5)
                          .filter(y -> x + y == 5)
                          .map(y -> "(" + x + "," + y + ")"));

      assertThat(solutions.run()).containsExactly("(1,4)", "(2,3)", "(3,2)", "(4,1)");
    }
  }
}
