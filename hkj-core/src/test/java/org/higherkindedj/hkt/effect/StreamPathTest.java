// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for StreamPath.
 *
 * <p>Tests cover factory methods, lazy stream operations, Composable/Combinable/Chainable
 * operations, and conversions.
 */
@DisplayName("StreamPath<A> Complete Test Suite")
class StreamPathTest {

  private static final String TEST_VALUE = "test";

  @Nested
  @DisplayName("Factory Methods via Path")
  class FactoryMethodsTests {

    @Test
    @DisplayName("Path.stream() creates StreamPath from Stream")
    void streamFromStreamCreatesPath() {
      StreamPath<Integer> path = Path.stream(Stream.of(1, 2, 3));

      assertThat(path.toList()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("Path.streamFromList() creates StreamPath from List")
    void streamFromListCreatesPath() {
      StreamPath<Integer> path = Path.streamFromList(List.of(1, 2, 3));

      assertThat(path.toList()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("Path.streamPure() creates single-element StreamPath")
    void streamPureCreatesSingleElement() {
      StreamPath<String> path = Path.streamPure(TEST_VALUE);

      assertThat(path.toList()).containsExactly(TEST_VALUE);
    }

    @Test
    @DisplayName("Path.streamEmpty() creates empty StreamPath")
    void streamEmptyCreatesEmpty() {
      StreamPath<String> path = Path.streamEmpty();

      assertThat(path.toList()).isEmpty();
    }

    @Test
    @DisplayName("Path.streamIterate() creates infinite stream")
    void streamIterateCreatesInfinite() {
      StreamPath<Integer> path = Path.streamIterate(1, n -> n + 1).take(5);

      assertThat(path.toList()).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("StreamPath.of(Stream) creates path from stream")
    void staticOfFromStream() {
      StreamPath<Integer> path = StreamPath.of(Stream.of(1, 2, 3));

      assertThat(path.toList()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("StreamPath.fromList() creates path from list")
    void staticFromList() {
      StreamPath<Integer> path = StreamPath.fromList(List.of(1, 2, 3));

      assertThat(path.toList()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("StreamPath.pure() creates single element")
    void staticPureCreatesSingle() {
      StreamPath<String> path = StreamPath.pure(TEST_VALUE);

      assertThat(path.toList()).containsExactly(TEST_VALUE);
    }

    @Test
    @DisplayName("StreamPath.empty() creates empty path")
    void staticEmptyCreatesEmpty() {
      StreamPath<String> path = StreamPath.empty();

      assertThat(path.toList()).isEmpty();
    }

    @Test
    @DisplayName("StreamPath.iterate() creates infinite stream")
    void staticIterateCreatesInfinite() {
      StreamPath<Integer> path = StreamPath.iterate(0, n -> n + 2).take(5);

      assertThat(path.toList()).containsExactly(0, 2, 4, 6, 8);
    }

    @Test
    @DisplayName("StreamPath.generate() creates from supplier")
    void staticGenerateCreatesFromSupplier() {
      AtomicInteger counter = new AtomicInteger(0);
      StreamPath<Integer> path = StreamPath.generate(counter::incrementAndGet).take(3);

      assertThat(path.toList()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("StreamPath.fromSupplier() creates from stream supplier")
    void staticFromSupplierCreatesPath() {
      AtomicInteger callCount = new AtomicInteger(0);
      StreamPath<Integer> path =
          StreamPath.fromSupplier(
              () -> {
                callCount.incrementAndGet();
                return Stream.of(1, 2, 3);
              });

      // First call
      assertThat(path.toList()).containsExactly(1, 2, 3);
      assertThat(callCount.get()).isEqualTo(1);

      // Second call should create fresh stream
      assertThat(path.toList()).containsExactly(1, 2, 3);
      assertThat(callCount.get()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("Run and Terminal Methods")
  class RunAndTerminalMethodsTests {

    @Test
    @DisplayName("run() returns fresh stream")
    void runReturnsFreshStream() {
      StreamPath<Integer> path = StreamPath.fromList(List.of(1, 2, 3));

      // Can consume run() multiple times
      assertThat(path.run().toList()).containsExactly(1, 2, 3);
      assertThat(path.run().toList()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("toList() collects to list")
    void toListCollectsToList() {
      StreamPath<Integer> path = StreamPath.fromList(List.of(1, 2, 3));

      assertThat(path.toList()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("headOption() returns first element")
    void headOptionReturnsFirst() {
      StreamPath<Integer> path = StreamPath.fromList(List.of(1, 2, 3));

      assertThat(path.headOption()).contains(1);
    }

    @Test
    @DisplayName("headOption() returns empty for empty stream")
    void headOptionReturnsEmptyForEmpty() {
      StreamPath<Integer> path = StreamPath.empty();

      assertThat(path.headOption()).isEmpty();
    }

    @Test
    @DisplayName("count() returns element count")
    void countReturnsElementCount() {
      StreamPath<Integer> path = StreamPath.fromList(List.of(1, 2, 3));

      assertThat(path.count()).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("Composable Operations (map, peek)")
  class ComposableOperationsTests {

    @Test
    @DisplayName("map() transforms elements lazily")
    void mapTransformsElementsLazily() {
      AtomicInteger evalCount = new AtomicInteger(0);
      StreamPath<Integer> path = StreamPath.fromList(List.of(1, 2, 3));

      StreamPath<Integer> result =
          path.map(
              n -> {
                evalCount.incrementAndGet();
                return n * 2;
              });

      assertThat(evalCount.get()).isZero(); // Lazy
      assertThat(result.toList()).containsExactly(2, 4, 6);
      assertThat(evalCount.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("map() validates null mapper")
    void mapValidatesNullMapper() {
      StreamPath<String> path = StreamPath.pure(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.map(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("map() chains correctly")
    void mapChainsCorrectly() {
      StreamPath<String> path = StreamPath.fromList(List.of("hello", "world"));

      StreamPath<String> result = path.map(String::toUpperCase).map(s -> s + "!");

      assertThat(result.toList()).containsExactly("HELLO!", "WORLD!");
    }

    @Test
    @DisplayName("peek() observes elements without modifying")
    void peekObservesElementsWithoutModifying() {
      StreamPath<Integer> path = StreamPath.fromList(List.of(1, 2, 3));
      AtomicInteger sum = new AtomicInteger(0);

      StreamPath<Integer> result = path.peek(sum::addAndGet);

      assertThat(result.toList()).containsExactly(1, 2, 3);
      assertThat(sum.get()).isEqualTo(6);
    }
  }

  @Nested
  @DisplayName("Chainable Operations (via, flatMap, then)")
  class ChainableOperationsTests {

    @Test
    @DisplayName("via() performs flatMap")
    void viaPerformsFlatMap() {
      StreamPath<Integer> path = StreamPath.fromList(List.of(1, 2));

      StreamPath<String> result = path.via(n -> StreamPath.of(n + "a", n + "b"));

      assertThat(result.toList()).containsExactly("1a", "1b", "2a", "2b");
    }

    @Test
    @DisplayName("via() validates null mapper")
    void viaValidatesNullMapper() {
      StreamPath<String> path = StreamPath.pure(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.via(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("via() validates non-null result")
    void viaValidatesNonNullResult() {
      StreamPath<String> path = StreamPath.pure(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.via(s -> null).toList())
          .withMessageContaining("mapper must not return null");
    }

    @Test
    @DisplayName("via() validates result is StreamPath")
    void viaValidatesResultType() {
      StreamPath<String> path = StreamPath.pure(TEST_VALUE);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.via(s -> Path.just(s)).toList())
          .withMessageContaining("via mapper must return StreamPath");
    }

    @Test
    @DisplayName("flatMap() is alias for via")
    void flatMapIsAliasForVia() {
      StreamPath<Integer> path = StreamPath.fromList(List.of(1, 2));

      StreamPath<Integer> viaResult = path.via(n -> StreamPath.of(n, n * 10));
      @SuppressWarnings("unchecked")
      StreamPath<Integer> flatMapResult =
          (StreamPath<Integer>) path.flatMap(n -> StreamPath.of(n, n * 10));

      assertThat(flatMapResult.toList()).isEqualTo(viaResult.toList());
    }

    @Test
    @DisplayName("then() replaces each element with supplied stream")
    void thenReplacesElements() {
      StreamPath<String> path = StreamPath.fromList(List.of("a", "b"));

      StreamPath<Integer> result = path.then(() -> StreamPath.of(1, 2));

      assertThat(result.toList()).containsExactly(1, 2, 1, 2);
    }
  }

  @Nested
  @DisplayName("Combinable Operations (zipWith)")
  class CombinableOperationsTests {

    @Test
    @DisplayName("zipWith() produces cartesian product")
    void zipWithProducesCartesianProduct() {
      StreamPath<Integer> first = StreamPath.fromList(List.of(1, 2));
      StreamPath<String> second = StreamPath.fromList(List.of("a", "b"));

      StreamPath<String> result = first.zipWith(second, (n, s) -> n + s);

      assertThat(result.toList()).containsExactly("1a", "1b", "2a", "2b");
    }

    @Test
    @DisplayName("zipWith() validates null parameters")
    void zipWithValidatesNullParameters() {
      StreamPath<String> path = StreamPath.pure(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(null, (a, b) -> a + b))
          .withMessageContaining("other must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(StreamPath.pure("x"), null))
          .withMessageContaining("combiner must not be null");
    }

    @Test
    @DisplayName("zipWith() throws when given non-StreamPath")
    void zipWithThrowsWhenGivenNonStreamPath() {
      StreamPath<String> path = StreamPath.pure(TEST_VALUE);
      IdPath<Integer> idPath = Path.id(42);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.zipWith(idPath, (s, i) -> s + i))
          .withMessageContaining("Cannot zipWith non-StreamPath");
    }
  }

  @Nested
  @DisplayName("Stream-Specific Operations")
  class StreamSpecificOperationsTests {

    @Test
    @DisplayName("filter() keeps matching elements")
    void filterKeepsMatchingElements() {
      StreamPath<Integer> path = StreamPath.fromList(List.of(1, 2, 3, 4, 5));

      StreamPath<Integer> result = path.filter(n -> n % 2 == 0);

      assertThat(result.toList()).containsExactly(2, 4);
    }

    @Test
    @DisplayName("take() limits elements")
    void takeLimitsElements() {
      StreamPath<Integer> path = StreamPath.iterate(1, n -> n + 1);

      StreamPath<Integer> result = path.take(5);

      assertThat(result.toList()).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("drop() skips elements")
    void dropSkipsElements() {
      StreamPath<Integer> path = StreamPath.fromList(List.of(1, 2, 3, 4, 5));

      StreamPath<Integer> result = path.drop(2);

      assertThat(result.toList()).containsExactly(3, 4, 5);
    }

    @Test
    @DisplayName("takeWhile() takes while predicate holds")
    void takeWhileTakesWhilePredicateHolds() {
      StreamPath<Integer> path = StreamPath.fromList(List.of(1, 2, 3, 4, 5));

      StreamPath<Integer> result = path.takeWhile(n -> n < 4);

      assertThat(result.toList()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("dropWhile() drops while predicate holds")
    void dropWhileDropsWhilePredicateHolds() {
      StreamPath<Integer> path = StreamPath.fromList(List.of(1, 2, 3, 4, 5));

      StreamPath<Integer> result = path.dropWhile(n -> n < 3);

      assertThat(result.toList()).containsExactly(3, 4, 5);
    }

    @Test
    @DisplayName("distinct() removes duplicates")
    void distinctRemovesDuplicates() {
      StreamPath<Integer> path = StreamPath.fromList(List.of(1, 2, 2, 3, 1, 4));

      StreamPath<Integer> result = path.distinct();

      assertThat(result.toList()).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("sorted() sorts elements")
    void sortedSortsElements() {
      StreamPath<Integer> path = StreamPath.fromList(List.of(3, 1, 4, 1, 5));

      StreamPath<Integer> result = path.sorted();

      assertThat(result.toList()).containsExactly(1, 1, 3, 4, 5);
    }

    @Test
    @DisplayName("sorted(comparator) sorts with custom comparator")
    void sortedWithComparatorSorts() {
      StreamPath<String> path = StreamPath.fromList(List.of("apple", "pie", "banana"));

      StreamPath<String> result = path.sorted((a, b) -> Integer.compare(a.length(), b.length()));

      assertThat(result.toList()).containsExactly("pie", "apple", "banana");
    }

    @Test
    @DisplayName("concat() combines two streams")
    void concatCombinesStreams() {
      StreamPath<Integer> first = StreamPath.fromList(List.of(1, 2));
      StreamPath<Integer> second = StreamPath.fromList(List.of(3, 4));

      StreamPath<Integer> result = first.concat(second);

      assertThat(result.toList()).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("foldLeft() reduces to single value")
    void foldLeftReducesToSingleValue() {
      StreamPath<Integer> path = StreamPath.fromList(List.of(1, 2, 3, 4));

      Integer result = path.foldLeft(0, Integer::sum);

      assertThat(result).isEqualTo(10);
    }
  }

  @Nested
  @DisplayName("Conversion Methods")
  class ConversionMethodsTests {

    @Test
    @DisplayName("toNonDetPath() converts to NonDetPath")
    void toNonDetPathConverts() {
      StreamPath<Integer> path = StreamPath.fromList(List.of(1, 2, 3));

      NonDetPath<Integer> result = path.toNonDetPath();

      assertThat(result.run()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("toMaybePath() returns Just for first element")
    void toMaybePathReturnsJustForFirst() {
      StreamPath<Integer> path = StreamPath.fromList(List.of(1, 2, 3));

      MaybePath<Integer> result = path.toMaybePath();

      assertThat(result.run().isJust()).isTrue();
      assertThat(result.run().get()).isEqualTo(1);
    }

    @Test
    @DisplayName("toMaybePath() returns Nothing for empty")
    void toMaybePathReturnsNothingForEmpty() {
      StreamPath<Integer> path = StreamPath.empty();

      MaybePath<Integer> result = path.toMaybePath();

      assertThat(result.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("toIOPath() returns IOPath producing list")
    void toIOPathProducesList() {
      StreamPath<Integer> path = StreamPath.fromList(List.of(1, 2, 3));

      IOPath<List<Integer>> result = path.toIOPath();

      assertThat(result.unsafeRun()).containsExactly(1, 2, 3);
    }
  }

  @Nested
  @DisplayName("Object Methods")
  class ObjectMethodsTests {

    @Test
    @DisplayName("toString() provides meaningful representation")
    void toStringProvidesMeaningfulRepresentation() {
      StreamPath<Integer> path = StreamPath.fromList(List.of(1, 2, 3));

      assertThat(path.toString()).contains("StreamPath");
    }
  }

  @Nested
  @DisplayName("Practical Usage Patterns")
  class PracticalUsagePatternsTests {

    @Test
    @DisplayName("Can process infinite stream with limit")
    void canProcessInfiniteStreamWithLimit() {
      StreamPath<Integer> squares = StreamPath.iterate(1, n -> n + 1).map(n -> n * n).take(5);

      assertThat(squares.toList()).containsExactly(1, 4, 9, 16, 25);
    }

    @Test
    @DisplayName("Can compose stream transformations")
    void canComposeStreamTransformations() {
      StreamPath<String> result =
          StreamPath.fromList(List.of("hello", "world", "foo", "bar"))
              .filter(s -> s.length() > 3)
              .map(String::toUpperCase)
              .sorted();

      assertThat(result.toList()).containsExactly("HELLO", "WORLD");
    }
  }
}
