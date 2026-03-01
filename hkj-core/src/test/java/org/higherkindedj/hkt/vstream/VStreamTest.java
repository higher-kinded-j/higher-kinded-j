// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.vstream.VStreamAssert.assertThatVStream;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("VStream Core Operations Test Suite")
class VStreamTest {

  private static final int TEST_VALUE = 42;
  private static final String TEST_STRING = "hello";

  // ===== Factory Methods =====

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethods {

    @Test
    @DisplayName("empty() produces no elements")
    void emptyProducesNoElements() {
      assertThatVStream(VStream.<Integer>empty()).isEmpty();
    }

    @Test
    @DisplayName("of(value) produces single element then completes")
    void ofSingleValueProducesSingleElement() {
      assertThatVStream(VStream.of(TEST_VALUE)).producesElements(TEST_VALUE);
    }

    @Test
    @DisplayName("of(value) with null produces single null element")
    void ofNullValueProducesSingleNull() {
      VStream<String> stream = VStream.of((String) null);
      List<String> result = stream.toList().run();
      assertThat(result).containsExactly((String) null);
    }

    @Test
    @DisplayName("of(varargs) produces elements in order")
    void ofVarargsProducesElementsInOrder() {
      assertThatVStream(VStream.of(1, 2, 3)).producesElements(1, 2, 3);
    }

    @Test
    @DisplayName("of(varargs) with null array throws NullPointerException")
    void ofVarargsWithNullThrows() {
      assertThatThrownBy(() -> VStream.of((Integer[]) null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("fromList() produces elements in list order")
    void fromListProducesElementsInOrder() {
      assertThatVStream(VStream.fromList(List.of("a", "b", "c"))).producesElements("a", "b", "c");
    }

    @Test
    @DisplayName("fromList() with empty list produces empty stream")
    void fromListEmptyProducesEmpty() {
      assertThatVStream(VStream.fromList(List.of())).isEmpty();
    }

    @Test
    @DisplayName("fromList() with null throws NullPointerException")
    void fromListNullThrows() {
      assertThatThrownBy(() -> VStream.fromList(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("fromStream() consumes Java stream lazily")
    void fromStreamConsumesLazily() {
      Stream<Integer> javaStream = Stream.of(1, 2, 3);
      assertThatVStream(VStream.fromStream(javaStream)).producesElements(1, 2, 3);
    }

    @Test
    @DisplayName("fromStream() with null throws NullPointerException")
    void fromStreamNullThrows() {
      assertThatThrownBy(() -> VStream.fromStream(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("succeed() is equivalent to of()")
    void succeedEquivalentToOf() {
      assertThatVStream(VStream.succeed(TEST_VALUE)).producesElements(TEST_VALUE);
    }

    @Test
    @DisplayName("fail() produces failed stream")
    void failProducesFailedStream() {
      RuntimeException error = new RuntimeException("test error");
      assertThatVStream(VStream.<Integer>fail(error))
          .failsWithExceptionType(RuntimeException.class);
    }

    @Test
    @DisplayName("fail() with null throws NullPointerException")
    void failNullThrows() {
      assertThatThrownBy(() -> VStream.fail(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("iterate() produces infinite sequence from seed and step")
    void iterateProducesInfiniteSequence() {
      VStream<Integer> stream = VStream.iterate(1, n -> n * 2);
      assertThatVStream(stream.take(5)).producesElements(1, 2, 4, 8, 16);
    }

    @Test
    @DisplayName("iterate() with null function throws NullPointerException")
    void iterateNullFunctionThrows() {
      assertThatThrownBy(() -> VStream.iterate(0, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("unfold() produces elements until empty is returned")
    void unfoldProducesElementsUntilEmpty() {
      VStream<Integer> stream =
          VStream.unfold(
              1,
              state ->
                  VTask.succeed(
                      state <= 5
                          ? Optional.of(new VStream.Seed<>(state, state + 1))
                          : Optional.empty()));
      assertThatVStream(stream).producesElements(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("unfold() with null function throws NullPointerException")
    void unfoldNullFunctionThrows() {
      assertThatThrownBy(() -> VStream.unfold(0, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("generate() produces infinite stream from supplier")
    void generateProducesInfiniteStream() {
      AtomicInteger counter = new AtomicInteger(0);
      VStream<Integer> stream = VStream.generate(counter::incrementAndGet);
      assertThatVStream(stream.take(4)).producesElements(1, 2, 3, 4);
    }

    @Test
    @DisplayName("generate() with null supplier throws NullPointerException")
    void generateNullSupplierThrows() {
      assertThatThrownBy(() -> VStream.generate(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("range() produces correct integer range")
    void rangeProducesCorrectRange() {
      assertThatVStream(VStream.range(3, 7)).producesElements(3, 4, 5, 6);
    }

    @Test
    @DisplayName("range() with start >= end produces empty stream")
    void rangeEmptyWhenStartGtEnd() {
      assertThatVStream(VStream.range(5, 3)).isEmpty();
      assertThatVStream(VStream.range(5, 5)).isEmpty();
    }

    @Test
    @DisplayName("repeat() produces infinite repeating stream")
    void repeatProducesRepeatingStream() {
      assertThatVStream(VStream.repeat("x").take(3)).producesElements("x", "x", "x");
    }

    @Test
    @DisplayName("defer() defers stream construction until first pull")
    void deferDefersConstruction() {
      AtomicInteger counter = new AtomicInteger(0);
      VStream<Integer> stream =
          VStream.defer(
              () -> {
                counter.incrementAndGet();
                return VStream.of(1, 2, 3);
              });

      assertThat(counter.get()).isZero();
      List<Integer> result = stream.toList().run();
      assertThat(counter.get()).isEqualTo(1);
      assertThat(result).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("defer() with null supplier throws NullPointerException")
    void deferNullThrows() {
      assertThatThrownBy(() -> VStream.defer(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("concat() static produces elements from both streams in order")
    void concatStaticProducesElementsInOrder() {
      VStream<Integer> first = VStream.of(1, 2);
      VStream<Integer> second = VStream.of(3, 4);
      assertThatVStream(VStream.concat(first, second)).producesElements(1, 2, 3, 4);
    }

    @Test
    @DisplayName("concat() with null arguments throws NullPointerException")
    void concatNullThrows() {
      assertThatThrownBy(() -> VStream.concat(null, VStream.empty()))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> VStream.concat(VStream.empty(), null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  // ===== Transformation Operations =====

  @Nested
  @DisplayName("Transformation Operations")
  class TransformationOperations {

    @Test
    @DisplayName("map() transforms each element")
    void mapTransformsElements() {
      VStream<String> stream = VStream.of(1, 2, 3).map(n -> "val:" + n);
      assertThatVStream(stream).producesElements("val:1", "val:2", "val:3");
    }

    @Test
    @DisplayName("map() preserves laziness")
    void mapPreservesLaziness() {
      AtomicInteger counter = new AtomicInteger(0);
      VStream<Integer> stream =
          VStream.of(1, 2, 3)
              .map(
                  n -> {
                    counter.incrementAndGet();
                    return n * 2;
                  });

      assertThat(counter.get()).isZero();
      stream.toList().run();
      assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("map() on empty stream returns empty")
    void mapOnEmptyReturnsEmpty() {
      assertThatVStream(VStream.<Integer>empty().map(n -> n * 2)).isEmpty();
    }

    @Test
    @DisplayName("map() with null function throws NullPointerException")
    void mapNullFunctionThrows() {
      assertThatThrownBy(() -> VStream.of(1).map(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("flatMap() substitutes and flattens")
    void flatMapSubstitutesAndFlattens() {
      VStream<Integer> stream = VStream.of(1, 2, 3).flatMap(n -> VStream.of(n, n * 10));
      assertThatVStream(stream).producesElements(1, 10, 2, 20, 3, 30);
    }

    @Test
    @DisplayName("flatMap() with empty inner streams produces empty results")
    void flatMapWithEmptyInnerStreams() {
      VStream<Integer> stream =
          VStream.of(1, 2, 3).flatMap(n -> n == 2 ? VStream.empty() : VStream.of(n));
      assertThatVStream(stream).producesElements(1, 3);
    }

    @Test
    @DisplayName("flatMap() on empty stream returns empty")
    void flatMapOnEmptyReturnsEmpty() {
      assertThatVStream(VStream.<Integer>empty().flatMap(n -> VStream.of(n, n))).isEmpty();
    }

    @Test
    @DisplayName("flatMap() with null function throws NullPointerException")
    void flatMapNullFunctionThrows() {
      assertThatThrownBy(() -> VStream.of(1).flatMap(null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("via() is equivalent to flatMap()")
    void viaEquivalentToFlatMap() {
      VStream<Integer> stream = VStream.of(1, 2).via(n -> VStream.of(n, n + 100));
      assertThatVStream(stream).producesElements(1, 101, 2, 102);
    }

    @Test
    @DisplayName("mapTask() applies effectful transformation")
    void mapTaskAppliesEffectfulTransformation() {
      VStream<String> stream = VStream.of(1, 2, 3).mapTask(n -> VTask.succeed("v" + n));
      assertThatVStream(stream).producesElements("v1", "v2", "v3");
    }

    @Test
    @DisplayName("mapTask() with null function throws NullPointerException")
    void mapTaskNullFunctionThrows() {
      assertThatThrownBy(() -> VStream.of(1).mapTask(null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  // ===== Filtering Operations =====

  @Nested
  @DisplayName("Filtering Operations")
  class FilteringOperations {

    @Test
    @DisplayName("filter() keeps matching elements")
    void filterKeepsMatchingElements() {
      VStream<Integer> stream = VStream.of(1, 2, 3, 4, 5).filter(n -> n % 2 == 0);
      assertThatVStream(stream).producesElements(2, 4);
    }

    @Test
    @DisplayName("filter() on empty stream returns empty")
    void filterOnEmptyReturnsEmpty() {
      assertThatVStream(VStream.<Integer>empty().filter(n -> n > 0)).isEmpty();
    }

    @Test
    @DisplayName("filter() with no matches returns empty")
    void filterNoMatchesReturnsEmpty() {
      assertThatVStream(VStream.of(1, 2, 3).filter(n -> n > 10)).isEmpty();
    }

    @Test
    @DisplayName("filter() with null predicate throws NullPointerException")
    void filterNullPredicateThrows() {
      assertThatThrownBy(() -> VStream.of(1).filter(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("takeWhile() stops at first non-match")
    void takeWhileStopsAtFirstNonMatch() {
      VStream<Integer> stream = VStream.of(1, 2, 3, 4, 5).takeWhile(n -> n < 4);
      assertThatVStream(stream).producesElements(1, 2, 3);
    }

    @Test
    @DisplayName("takeWhile() with all matching keeps all")
    void takeWhileAllMatchingKeepsAll() {
      assertThatVStream(VStream.of(1, 2, 3).takeWhile(n -> n < 10)).producesElements(1, 2, 3);
    }

    @Test
    @DisplayName("takeWhile() with none matching returns empty")
    void takeWhileNoneMatchingReturnsEmpty() {
      assertThatVStream(VStream.of(1, 2, 3).takeWhile(n -> n > 10)).isEmpty();
    }

    @Test
    @DisplayName("dropWhile() skips initial matches")
    void dropWhileSkipsInitialMatches() {
      VStream<Integer> stream = VStream.of(1, 2, 3, 4, 5).dropWhile(n -> n < 3);
      assertThatVStream(stream).producesElements(3, 4, 5);
    }

    @Test
    @DisplayName("dropWhile() with all matching returns empty")
    void dropWhileAllMatchingReturnsEmpty() {
      assertThatVStream(VStream.of(1, 2, 3).dropWhile(n -> n < 10)).isEmpty();
    }

    @Test
    @DisplayName("dropWhile() with none matching keeps all")
    void dropWhileNoneMatchingKeepsAll() {
      assertThatVStream(VStream.of(1, 2, 3).dropWhile(n -> n > 10)).producesElements(1, 2, 3);
    }

    @Test
    @DisplayName("take(n) returns first n elements")
    void takeReturnsFirstN() {
      assertThatVStream(VStream.of(1, 2, 3, 4, 5).take(3)).producesElements(1, 2, 3);
    }

    @Test
    @DisplayName("take(0) returns empty")
    void takeZeroReturnsEmpty() {
      assertThatVStream(VStream.of(1, 2, 3).take(0)).isEmpty();
    }

    @Test
    @DisplayName("take(n) with n > stream length returns all elements")
    void takeMoreThanLengthReturnsAll() {
      assertThatVStream(VStream.of(1, 2, 3).take(100)).producesElements(1, 2, 3);
    }

    @Test
    @DisplayName("drop(n) skips first n elements")
    void dropSkipsFirstN() {
      assertThatVStream(VStream.of(1, 2, 3, 4, 5).drop(2)).producesElements(3, 4, 5);
    }

    @Test
    @DisplayName("drop(0) returns all elements")
    void dropZeroReturnsAll() {
      assertThatVStream(VStream.of(1, 2, 3).drop(0)).producesElements(1, 2, 3);
    }

    @Test
    @DisplayName("drop(n) with n >= stream length returns empty")
    void dropMoreThanLengthReturnsEmpty() {
      assertThatVStream(VStream.of(1, 2, 3).drop(5)).isEmpty();
    }

    @Test
    @DisplayName("distinct() removes duplicates")
    void distinctRemovesDuplicates() {
      assertThatVStream(VStream.of(1, 2, 2, 3, 1, 3).distinct()).producesElements(1, 2, 3);
    }

    @Test
    @DisplayName("distinct() on already-distinct stream returns same elements")
    void distinctOnDistinctReturnsAll() {
      assertThatVStream(VStream.of(1, 2, 3).distinct()).producesElements(1, 2, 3);
    }
  }

  // ===== Combination Operations =====

  @Nested
  @DisplayName("Combination Operations")
  class CombinationOperations {

    @Test
    @DisplayName("concat() appends streams")
    void concatAppendsStreams() {
      VStream<Integer> stream = VStream.of(1, 2).concat(VStream.of(3, 4));
      assertThatVStream(stream).producesElements(1, 2, 3, 4);
    }

    @Test
    @DisplayName("concat() with empty first returns second")
    void concatEmptyFirstReturnsSecond() {
      assertThatVStream(VStream.<Integer>empty().concat(VStream.of(1, 2))).producesElements(1, 2);
    }

    @Test
    @DisplayName("concat() with empty second returns first")
    void concatEmptySecondReturnsFirst() {
      assertThatVStream(VStream.of(1, 2).concat(VStream.empty())).producesElements(1, 2);
    }

    @Test
    @DisplayName("prepend() adds element at start")
    void prependAddsElementAtStart() {
      assertThatVStream(VStream.of(2, 3).prepend(1)).producesElements(1, 2, 3);
    }

    @Test
    @DisplayName("append() adds element at end")
    void appendAddsElementAtEnd() {
      assertThatVStream(VStream.of(1, 2).append(3)).producesElements(1, 2, 3);
    }

    @Test
    @DisplayName("zipWith() pairs elements positionally")
    void zipWithPairsPositionally() {
      VStream<String> stream =
          VStream.of(1, 2, 3).zipWith(VStream.of("a", "b", "c"), (n, s) -> n + s);
      assertThatVStream(stream).producesElements("1a", "2b", "3c");
    }

    @Test
    @DisplayName("zipWith() stops at shortest stream")
    void zipWithStopsAtShortest() {
      VStream<String> stream = VStream.of(1, 2, 3).zipWith(VStream.of("a", "b"), (n, s) -> n + s);
      assertThatVStream(stream).producesElements("1a", "2b");
    }

    @Test
    @DisplayName("zipWith() with empty stream returns empty")
    void zipWithEmptyReturnsEmpty() {
      VStream<String> stream = VStream.of(1, 2).zipWith(VStream.<String>empty(), (n, s) -> n + s);
      assertThatVStream(stream).isEmpty();
    }

    @Test
    @DisplayName("zipWith() with null arguments throws NullPointerException")
    void zipWithNullThrows() {
      assertThatThrownBy(() -> VStream.of(1).zipWith(null, (a, b) -> a))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> VStream.of(1).zipWith(VStream.of(1), null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("interleave() alternates elements")
    void interleaveAlternatesElements() {
      VStream<Integer> stream = VStream.of(1, 3, 5).interleave(VStream.of(2, 4, 6));
      assertThatVStream(stream).producesElements(1, 2, 3, 4, 5, 6);
    }

    @Test
    @DisplayName("interleave() appends remaining from longer stream")
    void interleaveAppendsRemaining() {
      VStream<Integer> stream = VStream.of(1, 3).interleave(VStream.of(2, 4, 6, 8));
      assertThatVStream(stream).producesElements(1, 2, 3, 4, 6, 8);
    }

    @Test
    @DisplayName("interleave() with empty second returns first")
    void interleaveWithEmptyReturnsFirst() {
      assertThatVStream(VStream.of(1, 2, 3).interleave(VStream.empty())).producesElements(1, 2, 3);
    }

    @Test
    @DisplayName("interleave() with null throws NullPointerException")
    void interleaveNullThrows() {
      assertThatThrownBy(() -> VStream.of(1).interleave(null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  // ===== Terminal Operations =====

  @Nested
  @DisplayName("Terminal Operations")
  class TerminalOperations {

    @Test
    @DisplayName("toList() collects all elements")
    void toListCollectsAll() {
      List<Integer> result = VStream.of(1, 2, 3).toList().run();
      assertThat(result).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("toList() on empty returns empty list")
    void toListOnEmptyReturnsEmptyList() {
      List<Integer> result = VStream.<Integer>empty().toList().run();
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("toList() returns unmodifiable list")
    void toListReturnsUnmodifiable() {
      List<Integer> result = VStream.of(1, 2, 3).toList().run();
      assertThatThrownBy(() -> result.add(4)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("fold() reduces with seed")
    void foldReducesWithSeed() {
      Integer result = VStream.of(1, 2, 3).fold(0, Integer::sum).run();
      assertThat(result).isEqualTo(6);
    }

    @Test
    @DisplayName("fold() on empty returns identity")
    void foldOnEmptyReturnsIdentity() {
      Integer result = VStream.<Integer>empty().fold(99, Integer::sum).run();
      assertThat(result).isEqualTo(99);
    }

    @Test
    @DisplayName("foldLeft() left-folds with accumulator")
    void foldLeftAccumulates() {
      String result = VStream.of(1, 2, 3).foldLeft("", (acc, n) -> acc + n).run();
      assertThat(result).isEqualTo("123");
    }

    @Test
    @DisplayName("headOption() returns first element")
    void headOptionReturnsFirst() {
      Optional<Integer> result = VStream.of(1, 2, 3).headOption().run();
      assertThat(result).contains(1);
    }

    @Test
    @DisplayName("headOption() on empty returns empty")
    void headOptionOnEmptyReturnsEmpty() {
      Optional<Integer> result = VStream.<Integer>empty().headOption().run();
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("lastOption() returns last element")
    void lastOptionReturnsLast() {
      Optional<Integer> result = VStream.of(1, 2, 3).lastOption().run();
      assertThat(result).contains(3);
    }

    @Test
    @DisplayName("lastOption() on empty returns empty")
    void lastOptionOnEmptyReturnsEmpty() {
      Optional<Integer> result = VStream.<Integer>empty().lastOption().run();
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("count() counts elements")
    void countCountsElements() {
      Long result = VStream.of(1, 2, 3, 4, 5).count().run();
      assertThat(result).isEqualTo(5);
    }

    @Test
    @DisplayName("count() on empty returns 0")
    void countOnEmptyReturnsZero() {
      Long result = VStream.<Integer>empty().count().run();
      assertThat(result).isZero();
    }

    @Test
    @DisplayName("exists() short-circuits on match")
    void existsShortCircuitsOnMatch() {
      AtomicInteger counter = new AtomicInteger(0);
      Boolean result =
          VStream.of(1, 2, 3, 4, 5).peek(_ -> counter.incrementAndGet()).exists(n -> n == 3).run();
      assertThat(result).isTrue();
      assertThat(counter.get()).isEqualTo(3); // Only evaluated up to the match
    }

    @Test
    @DisplayName("exists() returns false when no match")
    void existsReturnsFalseWhenNoMatch() {
      Boolean result = VStream.of(1, 2, 3).exists(n -> n > 10).run();
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("exists() on empty returns false")
    void existsOnEmptyReturnsFalse() {
      Boolean result = VStream.<Integer>empty().exists(n -> n > 0).run();
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("forAll() short-circuits on non-match")
    void forAllShortCircuitsOnNonMatch() {
      AtomicInteger counter = new AtomicInteger(0);
      Boolean result =
          VStream.of(2, 4, 5, 8).peek(_ -> counter.incrementAndGet()).forAll(n -> n % 2 == 0).run();
      assertThat(result).isFalse();
      assertThat(counter.get()).isEqualTo(3); // Stops at 5
    }

    @Test
    @DisplayName("forAll() returns true when all match")
    void forAllReturnsTrueWhenAllMatch() {
      Boolean result = VStream.of(2, 4, 6).forAll(n -> n % 2 == 0).run();
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("forAll() on empty returns true (vacuous truth)")
    void forAllOnEmptyReturnsTrue() {
      Boolean result = VStream.<Integer>empty().forAll(n -> n > 100).run();
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("find() returns first matching element")
    void findReturnsFirstMatch() {
      Optional<Integer> result = VStream.of(1, 2, 3, 4).find(n -> n > 2).run();
      assertThat(result).contains(3);
    }

    @Test
    @DisplayName("find() returns empty when no match")
    void findReturnsEmptyWhenNoMatch() {
      Optional<Integer> result = VStream.of(1, 2, 3).find(n -> n > 10).run();
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("forEach() executes side effect for all elements")
    void forEachExecutesSideEffect() {
      List<Integer> collected = new ArrayList<>();
      VStream.of(1, 2, 3).forEach(collected::add).run();
      assertThat(collected).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("drain() consumes stream completely")
    void drainConsumesStream() {
      AtomicInteger counter = new AtomicInteger(0);
      VStream.of(1, 2, 3).peek(_ -> counter.incrementAndGet()).drain().run();
      assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("asUnit() maps all elements to Unit")
    void asUnitMapsToUnit() {
      List<Unit> result = VStream.of(1, 2, 3).asUnit().toList().run();
      assertThat(result).containsExactly(Unit.INSTANCE, Unit.INSTANCE, Unit.INSTANCE);
    }
  }

  // ===== Observation =====

  @Nested
  @DisplayName("Observation Operations")
  class ObservationOperations {

    @Test
    @DisplayName("peek() performs side effect without modifying elements")
    void peekPerformsSideEffect() {
      List<Integer> sideEffects = new ArrayList<>();
      List<Integer> result = VStream.of(1, 2, 3).peek(sideEffects::add).toList().run();
      assertThat(result).containsExactly(1, 2, 3);
      assertThat(sideEffects).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("peek() with null action throws NullPointerException")
    void peekNullActionThrows() {
      assertThatThrownBy(() -> VStream.of(1).peek(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("onComplete() runs action when stream finishes")
    void onCompleteRunsAction() {
      AtomicInteger counter = new AtomicInteger(0);
      VStream.of(1, 2, 3).onComplete(counter::incrementAndGet).toList().run();
      assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("onComplete() on empty stream still runs action")
    void onCompleteOnEmptyRunsAction() {
      AtomicInteger counter = new AtomicInteger(0);
      VStream.empty().onComplete(counter::incrementAndGet).toList().run();
      assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("onComplete() with null action throws NullPointerException")
    void onCompleteNullActionThrows() {
      assertThatThrownBy(() -> VStream.of(1).onComplete(null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  // ===== Error Handling =====

  @Nested
  @DisplayName("Error Handling")
  class ErrorHandling {

    @Test
    @DisplayName("recover() replaces failed pull with value")
    void recoverReplacesFailedPull() {
      VStream<String> stream =
          VStream.<String>fail(new RuntimeException("error")).recover(e -> "recovered");
      assertThatVStream(stream).producesElements("recovered");
    }

    @Test
    @DisplayName("recover() does not affect successful pulls")
    void recoverDoesNotAffectSuccess() {
      VStream<Integer> stream = VStream.of(1, 2, 3).recover(e -> -1);
      assertThatVStream(stream).producesElements(1, 2, 3);
    }

    @Test
    @DisplayName("recover() with null function throws NullPointerException")
    void recoverNullThrows() {
      assertThatThrownBy(() -> VStream.of(1).recover(null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("recoverWith() replaces failed pull with recovery stream")
    void recoverWithReplacesFailedPull() {
      VStream<String> stream =
          VStream.<String>fail(new RuntimeException("error"))
              .recoverWith(e -> VStream.of("a", "b"));
      assertThatVStream(stream).producesElements("a", "b");
    }

    @Test
    @DisplayName("recoverWith() does not affect successful pulls")
    void recoverWithDoesNotAffectSuccess() {
      VStream<Integer> stream = VStream.of(1, 2).recoverWith(e -> VStream.of(-1));
      assertThatVStream(stream).producesElements(1, 2);
    }

    @Test
    @DisplayName("recoverWith() with null function throws NullPointerException")
    void recoverWithNullThrows() {
      assertThatThrownBy(() -> VStream.of(1).recoverWith(null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("mapError() transforms error")
    void mapErrorTransformsError() {
      VStream<String> stream =
          VStream.<String>fail(new RuntimeException("original"))
              .mapError(e -> new IllegalStateException("mapped: " + e.getMessage()));
      assertThatVStream(stream).failsWithExceptionType(IllegalStateException.class);
    }

    @Test
    @DisplayName("mapError() with null function throws NullPointerException")
    void mapErrorNullThrows() {
      assertThatThrownBy(() -> VStream.of(1).mapError(null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("onError() observes error without modifying it")
    void onErrorObservesError() {
      List<String> observedErrors = new ArrayList<>();
      VStream<String> stream =
          VStream.<String>fail(new RuntimeException("test"))
              .onError(e -> observedErrors.add(e.getMessage()));
      assertThatVStream(stream).failsOnPull();
      assertThat(observedErrors).containsExactly("test");
    }

    @Test
    @DisplayName("onError() with null action throws NullPointerException")
    void onErrorNullThrows() {
      assertThatThrownBy(() -> VStream.of(1).onError(null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Error propagation through map chain")
    void errorPropagatesThroughMap() {
      VStream<String> stream =
          VStream.<Integer>fail(new RuntimeException("fail")).map(n -> n * 2).map(String::valueOf);
      assertThatVStream(stream).failsWithExceptionType(RuntimeException.class);
    }
  }

  // ===== Laziness Verification =====

  @Nested
  @DisplayName("Laziness Verification")
  class LazinessVerification {

    @Test
    @DisplayName("No elements produced until terminal operation")
    void noElementsUntilTerminal() {
      AtomicInteger counter = new AtomicInteger(0);
      VStream<Integer> stream =
          VStream.generate(counter::incrementAndGet).map(n -> n * 2).filter(n -> n > 0);

      assertThat(counter.get()).isZero();

      stream.take(3).toList().run();
      assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("Multiple chained operations remain lazy")
    void multipleChainedOperationsLazy() {
      AtomicInteger mapCounter = new AtomicInteger(0);
      AtomicInteger filterCounter = new AtomicInteger(0);

      VStream<Integer> stream =
          VStream.of(1, 2, 3, 4, 5)
              .map(
                  n -> {
                    mapCounter.incrementAndGet();
                    return n * 2;
                  })
              .filter(
                  n -> {
                    filterCounter.incrementAndGet();
                    return n > 4;
                  });

      assertThat(mapCounter.get()).isZero();
      assertThat(filterCounter.get()).isZero();

      stream.toList().run();
      assertThat(mapCounter.get()).isEqualTo(5);
      assertThat(filterCounter.get()).isEqualTo(5);
    }

    @Test
    @DisplayName("take(n) stops pulling after n elements from infinite stream")
    void takeStopsPulling() {
      AtomicInteger counter = new AtomicInteger(0);
      List<Integer> result = VStream.generate(counter::incrementAndGet).take(5).toList().run();

      assertThat(result).containsExactly(1, 2, 3, 4, 5);
      assertThat(counter.get()).isEqualTo(5);
    }
  }

  // ===== Stack Safety =====

  @Nested
  @DisplayName("Stack Safety")
  class StackSafety {

    @Test
    @DisplayName("toList on stream of 100,000 elements does not overflow")
    void toListLargeStreamDoesNotOverflow() {
      List<Integer> source = IntStream.range(0, 100_000).boxed().toList();
      List<Integer> result = VStream.fromList(source).toList().run();
      assertThat(result).hasSize(100_000);
      assertThat(result.getFirst()).isZero();
      assertThat(result.getLast()).isEqualTo(99_999);
    }

    @Test
    @DisplayName("fold on stream of 100,000 elements does not overflow")
    void foldLargeStreamDoesNotOverflow() {
      List<Integer> source = IntStream.range(0, 100_000).boxed().toList();
      Long result = VStream.fromList(source).count().run();
      assertThat(result).isEqualTo(100_000L);
    }

    @Test
    @DisplayName("map chain of 10,000 operations does not overflow")
    void mapChainDoesNotOverflow() {
      VStream<Integer> stream = VStream.of(0);
      for (int i = 0; i < 10_000; i++) {
        stream = stream.map(n -> n + 1);
      }
      List<Integer> result = stream.toList().run();
      assertThat(result).containsExactly(10_000);
    }

    @Test
    @DisplayName("filter on large stream with many skips does not overflow")
    void filterManySkipsDoesNotOverflow() {
      // Stream of 100,000 elements where only every 100th passes the filter
      List<Integer> source = IntStream.range(0, 100_000).boxed().toList();
      List<Integer> result = VStream.fromList(source).filter(n -> n % 100 == 0).toList().run();
      assertThat(result).hasSize(1_000);
      assertThat(result.getFirst()).isZero();
      assertThat(result.getLast()).isEqualTo(99_900);
    }
  }

  // ===== Edge Cases =====

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    @DisplayName("Single-element stream through all combinators")
    void singleElementThroughCombinators() {
      List<Integer> result =
          VStream.of(5)
              .map(n -> n * 2)
              .filter(n -> n > 0)
              .take(10)
              .drop(0)
              .distinct()
              .toList()
              .run();
      assertThat(result).containsExactly(10);
    }

    @Test
    @DisplayName("Empty stream through all combinators")
    void emptyStreamThroughCombinators() {
      List<Integer> result =
          VStream.<Integer>empty()
              .map(n -> n * 2)
              .filter(n -> n > 0)
              .take(10)
              .drop(0)
              .distinct()
              .toList()
              .run();
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Stream reusability - multiple toList calls produce same result")
    void streamReusability() {
      VStream<Integer> stream = VStream.of(1, 2, 3);
      List<Integer> first = stream.toList().run();
      List<Integer> second = stream.toList().run();
      assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("Nested flatMap with varying inner stream sizes")
    void nestedFlatMapVaryingSizes() {
      VStream<Integer> stream =
          VStream.of(0, 1, 2, 3)
              .flatMap(
                  n -> {
                    if (n == 0) return VStream.empty();
                    List<Integer> elements = new ArrayList<>();
                    for (int i = 0; i < n; i++) elements.add(n);
                    return VStream.fromList(elements);
                  });
      assertThatVStream(stream).producesElements(1, 2, 2, 3, 3, 3);
    }

    @Test
    @DisplayName("take from infinite stream terminates")
    void takeFromInfiniteTerminates() {
      List<Integer> result = VStream.iterate(0, n -> n + 1).take(5).toList().run();
      assertThat(result).containsExactly(0, 1, 2, 3, 4);
    }

    @Test
    @DisplayName("exists on infinite stream short-circuits")
    void existsOnInfiniteShortCircuits() {
      Boolean result = VStream.iterate(0, n -> n + 1).exists(n -> n == 100).run();
      assertThat(result).isTrue();
    }
  }

  // ===== Complex Integration Tests =====

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("Complex pipeline: filter, map, flatMap, take, toList")
    void complexPipeline() {
      List<String> result =
          VStream.range(1, 20)
              .filter(n -> n % 2 == 0) // 2, 4, 6, 8, 10, 12, 14, 16, 18
              .map(n -> n * 10) // 20, 40, 60, 80, 100, ...
              .flatMap(n -> VStream.of(n, n + 1)) // 20, 21, 40, 41, 60, 61, ...
              .take(6) // 20, 21, 40, 41, 60, 61
              .map(String::valueOf)
              .toList()
              .run();
      assertThat(result).containsExactly("20", "21", "40", "41", "60", "61");
    }

    @Test
    @DisplayName("Effectful unfold with simulated pagination")
    void effectfulUnfoldPagination() {
      // Simulate paginated API: pages 1-3, each with 2 items
      VStream<String> stream =
          VStream.unfold(
              1,
              page ->
                  VTask.succeed(
                      page <= 3
                          ? Optional.of(new VStream.Seed<>("page" + page + "-item", page + 1))
                          : Optional.empty()));

      assertThatVStream(stream).producesElements("page1-item", "page2-item", "page3-item");
    }

    @Test
    @DisplayName("Error recovery mid-stream via recoverWith")
    void errorRecoveryMidStream() {
      VStream<String> stream =
          VStream.<String>fail(new RuntimeException("primary failed"))
              .recoverWith(
                  e ->
                      VStream.<String>fail(new RuntimeException("secondary failed"))
                          .recoverWith(e2 -> VStream.of("fallback")));

      assertThatVStream(stream).producesElements("fallback");
    }

    @Test
    @DisplayName("Zip with different-length streams")
    void zipDifferentLengths() {
      VStream<String> stream =
          VStream.of(1, 2, 3, 4, 5).zipWith(VStream.of("a", "b", "c"), (n, s) -> n + ":" + s);
      assertThatVStream(stream).producesElements("1:a", "2:b", "3:c");
    }

    @Test
    @DisplayName("Interleave then take produces correct alternation")
    void interleaveThenTake() {
      VStream<Integer> evens = VStream.iterate(0, n -> n + 2);
      VStream<Integer> odds = VStream.iterate(1, n -> n + 2);
      List<Integer> result = evens.interleave(odds).take(8).toList().run();
      assertThat(result).containsExactly(0, 1, 2, 3, 4, 5, 6, 7);
    }

    @Test
    @DisplayName("dropWhile then takeWhile on sorted stream")
    void dropWhileThenTakeWhile() {
      List<Integer> result =
          VStream.range(1, 100).dropWhile(n -> n < 10).takeWhile(n -> n < 15).toList().run();
      assertThat(result).containsExactly(10, 11, 12, 13, 14);
    }

    @Test
    @DisplayName("runSafe() captures error in Try")
    void runSafeCapturesError() {
      var tryResult = VStream.<Integer>fail(new RuntimeException("test error")).runSafe().run();
      assertThat(tryResult.isFailure()).isTrue();
    }

    @Test
    @DisplayName("Seed record stores value and next state")
    void seedRecordStoresValues() {
      VStream.Seed<String, Integer> seed = new VStream.Seed<>("hello", 42);
      assertThat(seed.value()).isEqualTo("hello");
      assertThat(seed.next()).isEqualTo(42);
    }
  }

  // ===== Utility Method Tests =====

  @Nested
  @DisplayName("Utility Methods")
  class UtilityMethods {

    @Test
    @DisplayName("runSafe() returns Try.Success on successful stream")
    void runSafeReturnsSuccessOnSuccess() {
      var result = VStream.of(1, 2, 3).runSafe().run();
      assertThat(result.isSuccess()).isTrue();
      assertThat(result.orElse(List.of())).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("runSafe() returns Try.Failure on failed stream")
    void runSafeReturnsFailureOnFailure() {
      var result = VStream.<Integer>fail(new RuntimeException("fail")).runSafe().run();
      assertThat(result.isFailure()).isTrue();
    }

    @Test
    @DisplayName("fromIterator() produces elements from iterator")
    void fromIteratorProducesElements() {
      Iterator<Integer> iter = List.of(10, 20, 30).iterator();
      assertThatVStream(VStream.fromIterator(iter)).producesElements(10, 20, 30);
    }

    @Test
    @DisplayName("fromIterator() with null throws NullPointerException")
    void fromIteratorNullThrows() {
      assertThatThrownBy(() -> VStream.fromIterator(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("runAsync() collects elements asynchronously")
    void runAsyncCollectsElements() throws Exception {
      var future = VStream.of(1, 2, 3).runAsync();
      assertThat(future.get()).containsExactly(1, 2, 3);
    }
  }

  // ===== Skip Step Propagation Tests =====
  // These tests exercise the Step.Skip branch in every combinator and terminal
  // operation by feeding a filtered stream (which produces Skip steps for
  // non-matching elements) into each operation.

  @Nested
  @DisplayName("Skip Step Propagation")
  class SkipStepPropagation {

    /** A stream of [1..6] where only evens pass, producing Skip for odds. */
    private VStream<Integer> filteredEvens() {
      return VStream.of(1, 2, 3, 4, 5, 6).filter(n -> n % 2 == 0);
    }

    // --- Combinators seeing Skip from source ---

    @Test
    @DisplayName("static concat propagates Skip from first stream")
    void concatPropagatesSkipFromFirst() {
      List<Integer> result = VStream.concat(filteredEvens(), VStream.of(100)).toList().run();
      assertThat(result).containsExactly(2, 4, 6, 100);
    }

    @Test
    @DisplayName("mapTask handles Skip from source")
    void mapTaskHandlesSkip() {
      List<Integer> result = filteredEvens().mapTask(n -> VTask.succeed(n * 10)).toList().run();
      assertThat(result).containsExactly(20, 40, 60);
    }

    @Test
    @DisplayName("nested filter propagates Skip")
    void nestedFilterPropagatesSkip() {
      // Inner filter produces Skip for odds, outer filter produces Skip for 2
      List<Integer> result = filteredEvens().filter(n -> n > 2).toList().run();
      assertThat(result).containsExactly(4, 6);
    }

    @Test
    @DisplayName("dropWhile handles Skip from source")
    void dropWhileHandlesSkip() {
      List<Integer> result = filteredEvens().dropWhile(n -> n < 4).toList().run();
      assertThat(result).containsExactly(4, 6);
    }

    @Test
    @DisplayName("drop handles Skip from source")
    void dropHandlesSkip() {
      List<Integer> result = filteredEvens().drop(1).toList().run();
      assertThat(result).containsExactly(4, 6);
    }

    @Test
    @DisplayName("peek handles Skip from source")
    void peekHandlesSkip() {
      List<Integer> peeked = new ArrayList<>();
      List<Integer> result = filteredEvens().peek(peeked::add).toList().run();
      assertThat(result).containsExactly(2, 4, 6);
      assertThat(peeked).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("onComplete handles Skip from source")
    void onCompleteHandlesSkip() {
      AtomicInteger completed = new AtomicInteger(0);
      List<Integer> result = filteredEvens().onComplete(completed::incrementAndGet).toList().run();
      assertThat(result).containsExactly(2, 4, 6);
      assertThat(completed.get()).isEqualTo(1);
    }

    // --- Terminal operations seeing Skip from source ---

    @Test
    @DisplayName("foldLeft handles Skip from source")
    void foldLeftHandlesSkip() {
      Integer sum = filteredEvens().foldLeft(0, Integer::sum).run();
      assertThat(sum).isEqualTo(12);
    }

    @Test
    @DisplayName("headOption handles Skip from source")
    void headOptionHandlesSkip() {
      // Stream: Skip(1), Emit(2), ... — headOption must skip past the Skip
      Optional<Integer> head = VStream.of(1, 2, 3).filter(n -> n > 1).headOption().run();
      assertThat(head).contains(2);
    }

    @Test
    @DisplayName("lastOption handles Skip from source")
    void lastOptionHandlesSkip() {
      Optional<Integer> last = filteredEvens().lastOption().run();
      assertThat(last).contains(6);
    }

    @Test
    @DisplayName("exists handles Skip from source")
    void existsHandlesSkip() {
      Boolean found = filteredEvens().exists(n -> n == 4).run();
      assertThat(found).isTrue();
    }

    @Test
    @DisplayName("forAll handles Skip from source")
    void forAllHandlesSkip() {
      Boolean allEven = filteredEvens().forAll(n -> n % 2 == 0).run();
      assertThat(allEven).isTrue();
    }

    @Test
    @DisplayName("find handles Skip from source")
    void findHandlesSkip() {
      Optional<Integer> found = filteredEvens().find(n -> n > 3).run();
      assertThat(found).contains(4);
    }

    @Test
    @DisplayName("forEach handles Skip from source")
    void forEachHandlesSkip() {
      List<Integer> collected = new ArrayList<>();
      filteredEvens().forEach(collected::add).run();
      assertThat(collected).containsExactly(2, 4, 6);
    }

    // --- Combination operations seeing Skip from source ---

    @Test
    @DisplayName("zipWith handles Skip from both sides")
    void zipWithHandlesSkipFromBothSides() {
      VStream<Integer> leftFiltered = VStream.of(1, 2, 3, 4).filter(n -> n % 2 == 0);
      VStream<String> rightFiltered =
          VStream.of("a", "b", "c", "d").filter(s -> !s.equals("a") && !s.equals("c"));
      List<String> result = leftFiltered.zipWith(rightFiltered, (n, s) -> n + s).toList().run();
      assertThat(result).containsExactly("2b", "4d");
    }

    @Test
    @DisplayName("interleave handles Skip from source")
    void interleaveHandlesSkip() {
      VStream<Integer> filtered = VStream.of(1, 2, 3, 4).filter(n -> n % 2 == 0);
      List<Integer> result = filtered.interleave(VStream.of(100, 200)).toList().run();
      // Skip steps from filter cause interleave to swap streams,
      // so the unfiltered stream (100, 200) emits first on each swap.
      assertThat(result).containsExactly(100, 2, 200, 4);
    }
  }

  @Nested
  @DisplayName("Close Operations")
  class CloseOperations {

    @Test
    @DisplayName("take() close delegates to underlying stream")
    void takeClosesDelegation() {
      AtomicBoolean finalized = new AtomicBoolean(false);

      VStream<Integer> stream =
          VStream.of(1, 2, 3, 4, 5).onFinalize(VTask.exec(() -> finalized.set(true)));

      // take() wraps the stream and its close() should delegate
      VStream<Integer> taken = stream.take(2);
      taken.close().run();

      assertThat(finalized).isTrue();
    }
  }
}
