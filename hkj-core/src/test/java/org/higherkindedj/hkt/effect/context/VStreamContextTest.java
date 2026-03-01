// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.context;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for VStreamContext.
 *
 * <p>Tests cover factory methods, transformation operations, filtering, terminal operations,
 * parallel operations, and conversion methods.
 */
@DisplayName("VStreamContext<A> Complete Test Suite")
class VStreamContextTest {

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethodsTests {

    @Test
    @DisplayName("of() wraps an existing VStream")
    void ofWrapsVStream() {
      VStreamContext<Integer> ctx = VStreamContext.of(VStream.of(1, 2, 3));

      assertThat(ctx.toList()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("of() validates non-null stream")
    void ofValidatesNonNullStream() {
      assertThatNullPointerException()
          .isThrownBy(() -> VStreamContext.of(null))
          .withMessageContaining("stream must not be null");
    }

    @Test
    @DisplayName("fromList() creates context from list")
    void fromListCreatesFromList() {
      VStreamContext<String> ctx = VStreamContext.fromList(List.of("a", "b", "c"));

      assertThat(ctx.toList()).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("fromList() validates non-null list")
    void fromListValidatesNonNullList() {
      assertThatNullPointerException()
          .isThrownBy(() -> VStreamContext.fromList(null))
          .withMessageContaining("list must not be null");
    }

    @Test
    @DisplayName("pure() creates single-element context")
    void pureCreatesSingleElement() {
      VStreamContext<String> ctx = VStreamContext.pure("hello");

      assertThat(ctx.toList()).containsExactly("hello");
    }

    @Test
    @DisplayName("empty() creates empty context")
    void emptyCreatesEmpty() {
      VStreamContext<Integer> ctx = VStreamContext.empty();

      assertThat(ctx.toList()).isEmpty();
    }

    @Test
    @DisplayName("range() creates integer range")
    void rangeCreatesIntegerRange() {
      VStreamContext<Integer> ctx = VStreamContext.range(1, 5);

      assertThat(ctx.toList()).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("fromPath() wraps existing VStreamPath")
    void fromPathWrapsVStreamPath() {
      var path = Path.vstreamOf("x", "y");
      VStreamContext<String> ctx = VStreamContext.fromPath(path);

      assertThat(ctx.toList()).containsExactly("x", "y");
    }

    @Test
    @DisplayName("fromPath() validates non-null path")
    void fromPathValidatesNonNullPath() {
      assertThatNullPointerException()
          .isThrownBy(() -> VStreamContext.fromPath(null))
          .withMessageContaining("path must not be null");
    }
  }

  @Nested
  @DisplayName("Transformation Operations")
  class TransformationTests {

    @Test
    @DisplayName("map() transforms elements")
    void mapTransformsElements() {
      VStreamContext<Integer> ctx = VStreamContext.fromList(List.of(1, 2, 3));

      List<Integer> result = ctx.map(x -> x * 2).toList();

      assertThat(result).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("map() validates non-null mapper")
    void mapValidatesNonNullMapper() {
      VStreamContext<Integer> ctx = VStreamContext.pure(1);

      assertThatNullPointerException()
          .isThrownBy(() -> ctx.map(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("via() chains dependent computations")
    void viaChainsComputations() {
      VStreamContext<Integer> ctx = VStreamContext.fromList(List.of(1, 2, 3));

      List<Integer> result = ctx.via(x -> VStreamContext.fromList(List.of(x, x * 10))).toList();

      assertThat(result).containsExactly(1, 10, 2, 20, 3, 30);
    }

    @Test
    @DisplayName("via() validates non-null function")
    void viaValidatesNonNullFn() {
      VStreamContext<Integer> ctx = VStreamContext.pure(1);

      assertThatNullPointerException()
          .isThrownBy(() -> ctx.via(null))
          .withMessageContaining("fn must not be null");
    }

    @Test
    @DisplayName("flatMap() is an alias for via()")
    void flatMapIsAliasForVia() {
      VStreamContext<Integer> ctx = VStreamContext.fromList(List.of(1, 2));

      List<Integer> result = ctx.flatMap(x -> VStreamContext.fromList(List.of(x, -x))).toList();

      assertThat(result).containsExactly(1, -1, 2, -2);
    }

    @Test
    @DisplayName("then() sequences independent computation")
    void thenSequencesComputation() {
      VStreamContext<Integer> ctx = VStreamContext.pure(99);

      List<String> result = ctx.then(() -> VStreamContext.fromList(List.of("a", "b"))).toList();

      assertThat(result).containsExactly("a", "b");
    }

    @Test
    @DisplayName("then() validates non-null supplier")
    void thenValidatesNonNullSupplier() {
      VStreamContext<Integer> ctx = VStreamContext.pure(1);

      assertThatNullPointerException()
          .isThrownBy(() -> ctx.then(null))
          .withMessageContaining("supplier must not be null");
    }

    @Test
    @DisplayName("peek() performs side effect without modifying elements")
    void peekPerformsSideEffect() {
      AtomicInteger sum = new AtomicInteger(0);
      VStreamContext<Integer> ctx = VStreamContext.fromList(List.of(1, 2, 3));

      List<Integer> result = ctx.peek(sum::addAndGet).toList();

      assertThat(result).containsExactly(1, 2, 3);
      assertThat(sum.get()).isEqualTo(6);
    }
  }

  @Nested
  @DisplayName("Filtering Operations")
  class FilteringTests {

    @Test
    @DisplayName("filter() keeps matching elements")
    void filterKeepsMatchingElements() {
      VStreamContext<Integer> ctx = VStreamContext.fromList(List.of(1, 2, 3, 4, 5));

      List<Integer> result = ctx.filter(x -> x % 2 == 0).toList();

      assertThat(result).containsExactly(2, 4);
    }

    @Test
    @DisplayName("filter() validates non-null predicate")
    void filterValidatesNonNullPredicate() {
      VStreamContext<Integer> ctx = VStreamContext.pure(1);

      assertThatNullPointerException()
          .isThrownBy(() -> ctx.filter(null))
          .withMessageContaining("predicate must not be null");
    }

    @Test
    @DisplayName("take() limits to first n elements")
    void takeLimitsToN() {
      VStreamContext<Integer> ctx = VStreamContext.fromList(List.of(1, 2, 3, 4, 5));

      List<Integer> result = ctx.take(3).toList();

      assertThat(result).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("drop() skips first n elements")
    void dropSkipsN() {
      VStreamContext<Integer> ctx = VStreamContext.fromList(List.of(1, 2, 3, 4, 5));

      List<Integer> result = ctx.drop(2).toList();

      assertThat(result).containsExactly(3, 4, 5);
    }

    @Test
    @DisplayName("takeWhile() takes while predicate holds")
    void takeWhileTakesWhileTrue() {
      VStreamContext<Integer> ctx = VStreamContext.fromList(List.of(1, 2, 3, 4, 5));

      List<Integer> result = ctx.takeWhile(x -> x < 4).toList();

      assertThat(result).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("dropWhile() drops while predicate holds")
    void dropWhileDropsWhileTrue() {
      VStreamContext<Integer> ctx = VStreamContext.fromList(List.of(1, 2, 3, 4, 5));

      List<Integer> result = ctx.dropWhile(x -> x < 3).toList();

      assertThat(result).containsExactly(3, 4, 5);
    }

    @Test
    @DisplayName("distinct() removes duplicates")
    void distinctRemovesDuplicates() {
      VStreamContext<Integer> ctx = VStreamContext.fromList(List.of(1, 2, 2, 3, 1, 3));

      List<Integer> result = ctx.distinct().toList();

      assertThat(result).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("concat() appends another context")
    void concatAppendsOther() {
      VStreamContext<Integer> first = VStreamContext.fromList(List.of(1, 2));
      VStreamContext<Integer> second = VStreamContext.fromList(List.of(3, 4));

      List<Integer> result = first.concat(second).toList();

      assertThat(result).containsExactly(1, 2, 3, 4);
    }
  }

  @Nested
  @DisplayName("Terminal Operations")
  class TerminalTests {

    @Test
    @DisplayName("toList() collects all elements")
    void toListCollectsAll() {
      List<String> result = VStreamContext.fromList(List.of("a", "b")).toList();

      assertThat(result).containsExactly("a", "b");
    }

    @Test
    @DisplayName("toList() returns empty list for empty stream")
    void toListReturnsEmptyForEmpty() {
      List<String> result = VStreamContext.<String>empty().toList();

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("headOption() returns first element")
    void headOptionReturnsFirst() {
      Optional<Integer> result = VStreamContext.fromList(List.of(10, 20, 30)).headOption();

      assertThat(result).hasValue(10);
    }

    @Test
    @DisplayName("headOption() returns empty for empty stream")
    void headOptionReturnsEmptyForEmpty() {
      Optional<Integer> result = VStreamContext.<Integer>empty().headOption();

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("count() counts elements")
    void countCountsElements() {
      long result = VStreamContext.fromList(List.of(1, 2, 3, 4, 5)).count();

      assertThat(result).isEqualTo(5);
    }

    @Test
    @DisplayName("count() returns 0 for empty stream")
    void countReturnsZeroForEmpty() {
      long result = VStreamContext.empty().count();

      assertThat(result).isEqualTo(0);
    }

    @Test
    @DisplayName("fold() reduces elements")
    void foldReducesElements() {
      Integer result = VStreamContext.fromList(List.of(1, 2, 3, 4)).fold(0, Integer::sum);

      assertThat(result).isEqualTo(10);
    }

    @Test
    @DisplayName("exists() returns true when match found")
    void existsReturnsTrueOnMatch() {
      boolean result = VStreamContext.fromList(List.of(1, 2, 3)).exists(x -> x == 2);

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("exists() returns false when no match")
    void existsReturnsFalseOnNoMatch() {
      boolean result = VStreamContext.fromList(List.of(1, 2, 3)).exists(x -> x == 99);

      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("forAll() returns true when all match")
    void forAllReturnsTrueWhenAllMatch() {
      boolean result = VStreamContext.fromList(List.of(2, 4, 6)).forAll(x -> x % 2 == 0);

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("forAll() returns false when some don't match")
    void forAllReturnsFalseWhenSomeDontMatch() {
      boolean result = VStreamContext.fromList(List.of(2, 3, 6)).forAll(x -> x % 2 == 0);

      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("find() returns first matching element")
    void findReturnsFirstMatch() {
      Optional<Integer> result = VStreamContext.fromList(List.of(1, 2, 3, 4)).find(x -> x > 2);

      assertThat(result).hasValue(3);
    }

    @Test
    @DisplayName("find() returns empty when no match")
    void findReturnsEmptyWhenNoMatch() {
      Optional<Integer> result = VStreamContext.fromList(List.of(1, 2, 3)).find(x -> x > 10);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("forEach() executes side effect for each element")
    void forEachExecutesSideEffect() {
      AtomicInteger sum = new AtomicInteger(0);
      VStreamContext.fromList(List.of(1, 2, 3)).forEach(sum::addAndGet);

      assertThat(sum.get()).isEqualTo(6);
    }
  }

  @Nested
  @DisplayName("Parallel Operations")
  class ParallelTests {

    @Test
    @DisplayName("parEvalMap() applies effectful function with bounded concurrency")
    void parEvalMapAppliesFunction() {
      VStreamContext<Integer> ctx = VStreamContext.fromList(List.of(1, 2, 3));

      List<Integer> result = ctx.parEvalMap(2, x -> VTask.of(() -> x * 10)).toList();

      assertThat(result).containsExactly(10, 20, 30);
    }

    @Test
    @DisplayName("chunk() groups elements into chunks")
    void chunkGroupsElements() {
      VStreamContext<Integer> ctx = VStreamContext.fromList(List.of(1, 2, 3, 4, 5));

      List<List<Integer>> result = ctx.chunk(2).toList();

      assertThat(result).containsExactly(List.of(1, 2), List.of(3, 4), List.of(5));
    }
  }

  @Nested
  @DisplayName("Conversion Methods")
  class ConversionTests {

    @Test
    @DisplayName("toVStream() returns underlying VStream")
    void toVStreamReturnsUnderlying() {
      VStreamContext<Integer> ctx = VStreamContext.fromList(List.of(1, 2, 3));

      VStream<Integer> stream = ctx.toVStream();

      assertThat(stream.toList().run()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("toPath() returns underlying VStreamPath")
    void toPathReturnsUnderlying() {
      VStreamContext<Integer> ctx = VStreamContext.fromList(List.of(1, 2, 3));

      var path = ctx.toPath();

      assertThat(path).isNotNull();
      assertThat(path.run().toList().run()).containsExactly(1, 2, 3);
    }
  }

  @Nested
  @DisplayName("Laziness")
  class LazinessTests {

    @Test
    @DisplayName("operations are lazy until terminal operation")
    void operationsAreLazy() {
      AtomicBoolean mapExecuted = new AtomicBoolean(false);

      VStreamContext<Integer> ctx =
          VStreamContext.fromList(List.of(1, 2, 3))
              .map(
                  x -> {
                    mapExecuted.set(true);
                    return x * 2;
                  });

      assertThat(mapExecuted).isFalse();

      ctx.toList(); // trigger execution

      assertThat(mapExecuted).isTrue();
    }
  }

  @Nested
  @DisplayName("Pipeline Composition")
  class PipelineTests {

    @Test
    @DisplayName("complex pipeline produces correct results")
    void complexPipelineProducesCorrectResults() {
      List<String> result =
          VStreamContext.range(1, 20)
              .filter(n -> n % 2 == 0)
              .map(n -> "Even: " + n)
              .take(3)
              .toList();

      assertThat(result).containsExactly("Even: 2", "Even: 4", "Even: 6");
    }

    @Test
    @DisplayName("toString() returns descriptive string")
    void toStringReturnsDescriptiveString() {
      VStreamContext<Integer> ctx = VStreamContext.pure(42);

      assertThat(ctx.toString()).isEqualTo("VStreamContext(<stream>)");
    }
  }
}
