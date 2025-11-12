// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.stream.StreamAssert.assertThatStream;
import static org.higherkindedj.hkt.stream.StreamKindHelper.STREAM;
import static org.higherkindedj.hkt.stream.StreamOps.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.tuple.Tuple2;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("StreamOps Complete Test Suite")
class StreamOpsTest extends StreamTestBase {

  @Nested
  @DisplayName("Creation Operations")
  class CreationTests {

    @Test
    @DisplayName("fromIterable() creates stream from list")
    void fromIterableCreatesStreamFromList() {
      List<String> list = Arrays.asList("a", "b", "c");
      var stream = fromIterable(list);

      assertThatStream(stream).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("fromIterable() works with empty iterable")
    void fromIterableWorksWithEmptyIterable() {
      List<String> empty = Arrays.asList();
      var stream = fromIterable(empty);

      assertThatStream(stream).isEmpty();
    }

    @Test
    @DisplayName("fromIterable() throws for null")
    void fromIterableThrowsForNull() {
      assertThatThrownBy(() -> fromIterable(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("fromArray() creates stream from array")
    void fromArrayCreatesStreamFromArray() {
      var stream = fromArray(1, 2, 3, 4);

      assertThatStream(stream).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("fromArray() works with empty array")
    void fromArrayWorksWithEmptyArray() {
      Kind<StreamKind.Witness, String> stream = fromArray();

      assertThatStream(stream).isEmpty();
    }

    @Test
    @DisplayName("fromArray() throws for null array")
    void fromArrayThrowsForNull() {
      assertThatThrownBy(() -> fromArray((Integer[]) null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("range() creates range stream")
    void rangeCreatesRangeStream() {
      var stream = range(1, 5);

      assertThatStream(stream).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("range() creates empty stream for invalid range")
    void rangeCreatesEmptyStreamForInvalidRange() {
      var stream = range(5, 5);

      assertThatStream(stream).isEmpty();
    }

    @Test
    @DisplayName("rangeClosed() includes end value")
    void rangeClosedIncludesEndValue() {
      var stream = rangeClosed(1, 5);

      assertThatStream(stream).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("rangeClosed() handles single element")
    void rangeClosedHandlesSingleElement() {
      var stream = rangeClosed(5, 5);

      assertThatStream(stream).containsExactly(5);
    }
  }

  @Nested
  @DisplayName("Materialization Operations")
  class MaterializationTests {

    @Test
    @DisplayName("toList() collects stream elements")
    void toListCollectsStreamElements() {
      var stream = streamOf("a", "b", "c");
      List<String> list = toList(stream);

      assertThat(list).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("toList() returns empty list for empty stream")
    void toListReturnsEmptyListForEmptyStream() {
      Kind<StreamKind.Witness, String> stream = emptyStream();
      List<String> list = toList(stream);

      assertThat(list).isEmpty();
    }

    @Test
    @DisplayName("toList() throws for null")
    void toListThrowsForNull() {
      assertThatThrownBy(() -> toList(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("toSet() collects unique elements")
    void toSetCollectsUniqueElements() {
      Kind<StreamKind.Witness, Integer> stream = STREAM.widen(Stream.of(1, 2, 2, 3, 3, 3));
      Set<Integer> set = toSet(stream);

      assertThat(set).containsExactlyInAnyOrder(1, 2, 3);
    }

    @Test
    @DisplayName("toSet() returns empty set for empty stream")
    void toSetReturnsEmptySetForEmptyStream() {
      Kind<StreamKind.Witness, Integer> stream = emptyStream();
      Set<Integer> set = toSet(stream);

      assertThat(set).isEmpty();
    }

    @Test
    @DisplayName("toSet() throws for null")
    void toSetThrowsForNull() {
      assertThatThrownBy(() -> toSet(null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("Filtering Operations")
  class FilteringTests {

    @Test
    void filter_shouldKeepMatchingElements() {
      Kind<StreamKind.Witness, Integer> stream = range(1, 10);
      Kind<StreamKind.Witness, Integer> evens = filter(n -> n % 2 == 0, stream);
      assertThat(toList(evens)).containsExactly(2, 4, 6, 8);
    }

    @Test
    void filter_shouldReturnEmptyForNoMatches() {
      Kind<StreamKind.Witness, Integer> stream = range(1, 5);
      Kind<StreamKind.Witness, Integer> filtered = filter(n -> n > 10, stream);
      assertThat(toList(filtered)).isEmpty();
    }

    @Test
    void filter_shouldHandleEmptyStream() {
      Kind<StreamKind.Witness, Integer> stream = emptyStream();
      Kind<StreamKind.Witness, Integer> filtered = filter(n -> n > 0, stream);
      assertThat(toList(filtered)).isEmpty();
    }

    @Test
    void filter_shouldThrowForNullPredicate() {
      Kind<StreamKind.Witness, Integer> stream = range(1, 5);
      assertThatThrownBy(() -> filter(null, stream)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void filter_shouldThrowForNullStream() {
      assertThatThrownBy(() -> filter(n -> true, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void take_shouldLimitElements() {
      Kind<StreamKind.Witness, Integer> stream = range(1, 100);
      Kind<StreamKind.Witness, Integer> limited = take(5, stream);
      assertThat(toList(limited)).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void take_shouldWorkWithInfiniteStream() {
      Kind<StreamKind.Witness, Integer> infinite = STREAM.widen(Stream.iterate(1, n -> n + 1));
      Kind<StreamKind.Witness, Integer> limited = take(3, infinite);
      assertThat(toList(limited)).containsExactly(1, 2, 3);
    }

    @Test
    void take_shouldReturnEmptyForZero() {
      Kind<StreamKind.Witness, Integer> stream = range(1, 10);
      Kind<StreamKind.Witness, Integer> limited = take(0, stream);
      assertThat(toList(limited)).isEmpty();
    }

    @Test
    void take_shouldThrowForNegative() {
      Kind<StreamKind.Witness, Integer> stream = range(1, 10);
      assertThatThrownBy(() -> take(-1, stream)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void take_shouldThrowForNullStream() {
      assertThatThrownBy(() -> take(5, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void drop_shouldSkipElements() {
      Kind<StreamKind.Witness, Integer> stream = range(1, 10);
      Kind<StreamKind.Witness, Integer> dropped = drop(5, stream);
      assertThat(toList(dropped)).containsExactly(6, 7, 8, 9);
    }

    @Test
    void drop_shouldReturnEmptyIfDropAll() {
      Kind<StreamKind.Witness, Integer> stream = range(1, 5);
      Kind<StreamKind.Witness, Integer> dropped = drop(10, stream);
      assertThat(toList(dropped)).isEmpty();
    }

    @Test
    void drop_shouldReturnAllForZero() {
      Kind<StreamKind.Witness, Integer> stream = range(1, 5);
      Kind<StreamKind.Witness, Integer> dropped = drop(0, stream);
      assertThat(toList(dropped)).containsExactly(1, 2, 3, 4);
    }

    @Test
    void drop_shouldThrowForNegative() {
      Kind<StreamKind.Witness, Integer> stream = range(1, 10);
      assertThatThrownBy(() -> drop(-1, stream)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void drop_shouldThrowForNullStream() {
      assertThatThrownBy(() -> drop(5, null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("Combination Operations")
  class CombinationTests {

    @Test
    void concat_shouldCombineTwoStreams() {
      Kind<StreamKind.Witness, Integer> first = range(1, 3);
      Kind<StreamKind.Witness, Integer> second = range(3, 6);
      Kind<StreamKind.Witness, Integer> combined = concat(first, second);
      assertThat(toList(combined)).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void concat_shouldHandleEmptyStreams() {
      Kind<StreamKind.Witness, Integer> empty1 = STREAM.widen(Stream.empty());
      Kind<StreamKind.Witness, Integer> nonEmpty1 = range(1, 3);

      Kind<StreamKind.Witness, Integer> result1 = concat(empty1, nonEmpty1);
      assertThat(toList(result1)).containsExactly(1, 2);

      Kind<StreamKind.Witness, Integer> nonEmpty2 = range(1, 3);
      Kind<StreamKind.Witness, Integer> empty2 = STREAM.widen(Stream.empty());

      Kind<StreamKind.Witness, Integer> result2 = concat(nonEmpty2, empty2);
      assertThat(toList(result2)).containsExactly(1, 2);
    }

    @Test
    void concat_shouldThrowForNullFirstStream() {
      Kind<StreamKind.Witness, Integer> stream = range(1, 3);
      assertThatThrownBy(() -> concat(null, stream)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void concat_shouldThrowForNullSecondStream() {
      Kind<StreamKind.Witness, Integer> stream = range(1, 3);
      assertThatThrownBy(() -> concat(stream, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void zip_shouldCombineElementsPairwise() {
      Kind<StreamKind.Witness, String> names = fromArray("Alice", "Bob", "Charlie");
      Kind<StreamKind.Witness, Integer> ages = fromArray(25, 30, 35);

      Kind<StreamKind.Witness, String> profiles = zip(names, ages, (name, age) -> name + ":" + age);

      assertThat(toList(profiles)).containsExactly("Alice:25", "Bob:30", "Charlie:35");
    }

    @Test
    void zip_shouldStopAtShorterStream() {
      Kind<StreamKind.Witness, Integer> short1 = range(1, 3);
      Kind<StreamKind.Witness, Integer> longer = range(1, 10);

      Kind<StreamKind.Witness, String> zipped = zip(short1, longer, (a, b) -> a + "," + b);

      assertThat(toList(zipped)).containsExactly("1,1", "2,2");
    }

    @Test
    void zip_shouldStopWhenSecondStreamIsShorter() {
      Kind<StreamKind.Witness, Integer> longer = range(1, 10);
      Kind<StreamKind.Witness, Integer> short2 = range(1, 3);

      Kind<StreamKind.Witness, String> zipped = zip(longer, short2, (a, b) -> a + "," + b);

      assertThat(toList(zipped)).containsExactly("1,1", "2,2");
    }

    @Test
    void zip_shouldHandleEmptyStreams() {
      Kind<StreamKind.Witness, Integer> empty = STREAM.widen(Stream.empty());
      Kind<StreamKind.Witness, Integer> nonEmpty = range(1, 5);

      Kind<StreamKind.Witness, String> zipped = zip(empty, nonEmpty, (a, b) -> a + "," + b);

      assertThat(toList(zipped)).isEmpty();
    }

    @Test
    void zip_shouldHandleSecondStreamEmpty() {
      Kind<StreamKind.Witness, Integer> nonEmpty = range(1, 5);
      Kind<StreamKind.Witness, Integer> empty = STREAM.widen(Stream.empty());

      Kind<StreamKind.Witness, String> zipped = zip(nonEmpty, empty, (a, b) -> a + "," + b);

      assertThat(toList(zipped)).isEmpty();
    }

    @Test
    void zip_shouldThrowForNullFirstStream() {
      Kind<StreamKind.Witness, Integer> stream = range(1, 3);
      assertThatThrownBy(() -> zip(null, stream, (Integer a, Integer b) -> a + b))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void zip_shouldThrowForNullSecondStream() {
      Kind<StreamKind.Witness, Integer> stream = range(1, 3);
      assertThatThrownBy(() -> zip(stream, null, (Integer a, Integer b) -> a + b))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void zip_shouldThrowForNullCombiner() {
      Kind<StreamKind.Witness, Integer> stream1 = range(1, 3);
      Kind<StreamKind.Witness, Integer> stream2 = range(1, 3);
      assertThatThrownBy(() -> zip(stream1, stream2, null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void zipWithIndex_shouldPairElementsWithIndices() {
      Kind<StreamKind.Witness, String> names = fromArray("Alice", "Bob", "Charlie");
      Kind<StreamKind.Witness, Tuple2<Integer, String>> indexed = zipWithIndex(names);

      List<Tuple2<Integer, String>> result = toList(indexed);

      assertThat(result).hasSize(3);
      assertThat(result.get(0)._1()).isEqualTo(0);
      assertThat(result.get(0)._2()).isEqualTo("Alice");
      assertThat(result.get(1)._1()).isEqualTo(1);
      assertThat(result.get(1)._2()).isEqualTo("Bob");
      assertThat(result.get(2)._1()).isEqualTo(2);
      assertThat(result.get(2)._2()).isEqualTo("Charlie");
    }

    @Test
    void zipWithIndex_shouldHandleEmptyStream() {
      Kind<StreamKind.Witness, String> empty = STREAM.widen(Stream.empty());
      Kind<StreamKind.Witness, Tuple2<Integer, String>> indexed = zipWithIndex(empty);
      assertThat(toList(indexed)).isEmpty();
    }

    @Test
    void zipWithIndex_shouldThrowForNullStream() {
      assertThatThrownBy(() -> zipWithIndex(null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("Effect Operations")
  class EffectTests {

    @Test
    void tap_shouldPerformSideEffectWithoutModifying() {
      List<Integer> sideEffectCapture = new ArrayList<>();

      Kind<StreamKind.Witness, Integer> stream = range(1, 5);
      Kind<StreamKind.Witness, Integer> tapped = tap(sideEffectCapture::add, stream);

      // Side effect not yet executed (lazy)
      assertThat(sideEffectCapture).isEmpty();

      // Force evaluation
      List<Integer> result = toList(tapped);

      // Now side effect has been executed
      assertThat(sideEffectCapture).containsExactly(1, 2, 3, 4);
      // And elements are unchanged
      assertThat(result).containsExactly(1, 2, 3, 4);
    }

    @Test
    void tap_shouldHandleEmptyStream() {
      List<Integer> sideEffectCapture = new ArrayList<>();

      Kind<StreamKind.Witness, Integer> stream = emptyStream();
      Kind<StreamKind.Witness, Integer> tapped = tap(sideEffectCapture::add, stream);

      List<Integer> result = toList(tapped);

      assertThat(sideEffectCapture).isEmpty();
      assertThat(result).isEmpty();
    }

    @Test
    void tap_shouldThrowForNullConsumer() {
      Kind<StreamKind.Witness, Integer> stream = range(1, 3);
      assertThatThrownBy(() -> tap(null, stream)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void tap_shouldThrowForNullStream() {
      assertThatThrownBy(() -> tap(x -> {}, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void forEach_shouldExecuteSideEffects() {
      List<String> captured = new ArrayList<>();

      Kind<StreamKind.Witness, String> stream = fromArray("a", "b", "c");
      forEach(captured::add, stream);

      assertThat(captured).containsExactly("a", "b", "c");
    }

    @Test
    void forEach_shouldConsumeStream() {
      Stream<Integer> original = Stream.of(1, 2, 3);
      Kind<StreamKind.Witness, Integer> stream = STREAM.widen(original);

      forEach(x -> {}, stream);

      // Attempting to use original stream would throw IllegalStateException
      // This documents the consumption behavior
    }

    @Test
    void forEach_shouldHandleEmptyStream() {
      List<String> captured = new ArrayList<>();

      Kind<StreamKind.Witness, String> stream = emptyStream();
      forEach(captured::add, stream);

      assertThat(captured).isEmpty();
    }

    @Test
    void forEach_shouldThrowForNullConsumer() {
      Kind<StreamKind.Witness, Integer> stream = range(1, 3);
      assertThatThrownBy(() -> forEach(null, stream)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void forEach_shouldThrowForNullStream() {
      assertThatThrownBy(() -> forEach(x -> {}, null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    void complexPipeline_shouldComposeOperations() {
      // Create range, filter evens, take 3, multiply by 10
      Kind<StreamKind.Witness, Integer> result =
          StreamMonad.INSTANCE.map(n -> n * 10, take(3, filter(n -> n % 2 == 0, range(1, 20))));

      assertThat(toList(result)).containsExactly(20, 40, 60);
    }

    @Test
    void zipFilterAndCollect_shouldWorkTogether() {
      Kind<StreamKind.Witness, Integer> numbers = range(1, 10);
      Kind<StreamKind.Witness, String> letters =
          fromArray("a", "b", "c", "d", "e", "f", "g", "h", "i");

      Kind<StreamKind.Witness, String> zipped = zip(numbers, letters, (n, l) -> n + l);
      Kind<StreamKind.Witness, String> filtered =
          filter(s -> s.contains("2") || s.contains("5"), zipped);

      assertThat(toList(filtered)).containsExactly("2b", "5e");
    }

    @Test
    void infiniteStreamWithTake_shouldNotHang() {
      Kind<StreamKind.Witness, Integer> infinite = STREAM.widen(Stream.iterate(0, n -> n + 1));

      Kind<StreamKind.Witness, Integer> result =
          drop(10, take(15, filter(n -> n % 2 == 0, infinite)));

      assertThat(toList(result)).containsExactly(20, 22, 24, 26, 28);
    }

    @Test
    void tapForDebugging_shouldNotAffectPipeline() {
      List<Integer> debugLog = new ArrayList<>();

      Kind<StreamKind.Witness, Integer> pipeline =
          tap(
              n -> debugLog.add(n),
              StreamMonad.INSTANCE.map(n -> n * n, filter(n -> n > 2, range(1, 6))));

      List<Integer> result = toList(pipeline);

      assertThat(result).containsExactly(9, 16, 25); // 3², 4², 5²
      assertThat(debugLog).containsExactly(9, 16, 25); // Same values logged
    }
  }
}
