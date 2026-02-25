// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.test.assertions.VStreamPathAssert.assertThatVStreamPath;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for VStreamPath.
 *
 * <p>Tests cover factory methods, Composable/Combinable/Chainable operations, stream-specific
 * operations, terminal operations, focus bridge, conversions, and null validation.
 */
@DisplayName("VStreamPath<A> Complete Test Suite")
class VStreamPathTest {

  private static final String TEST_VALUE = "test";
  private static final Integer TEST_INT = 42;

  @Nested
  @DisplayName("Factory Methods via Path")
  class FactoryMethodsTests {

    @Test
    @DisplayName("Path.vstream() creates VStreamPath from VStream")
    void vstreamCreatesPathFromVStream() {
      VStreamPath<Integer> path = Path.vstream(VStream.fromList(List.of(1, 2, 3)));

      assertThatVStreamPath(path).producesElements(1, 2, 3);
    }

    @Test
    @DisplayName("Path.vstreamOf() creates VStreamPath from varargs")
    void vstreamOfCreatesPath() {
      VStreamPath<String> path = Path.vstreamOf("a", "b", "c");

      assertThatVStreamPath(path).producesElements("a", "b", "c");
    }

    @Test
    @DisplayName("Path.vstreamFromList() creates VStreamPath from list")
    void vstreamFromListCreatesPath() {
      VStreamPath<Integer> path = Path.vstreamFromList(List.of(1, 2, 3));

      assertThatVStreamPath(path).producesElements(1, 2, 3);
    }

    @Test
    @DisplayName("Path.vstreamPure() creates single-element VStreamPath")
    void vstreamPureCreatesSingleElement() {
      VStreamPath<Integer> path = Path.vstreamPure(TEST_INT);

      assertThatVStreamPath(path).producesElements(TEST_INT);
    }

    @Test
    @DisplayName("Path.vstreamEmpty() creates empty VStreamPath")
    void vstreamEmptyCreatesEmpty() {
      VStreamPath<Integer> path = Path.vstreamEmpty();

      assertThatVStreamPath(path).isEmpty();
    }

    @Test
    @DisplayName("Path.vstreamIterate() creates infinite stream")
    void vstreamIterateCreatesInfinite() {
      VStreamPath<Integer> path = Path.vstreamIterate(1, n -> n + 1).take(5);

      assertThatVStreamPath(path).producesElements(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("Path.vstreamGenerate() creates from supplier")
    void vstreamGenerateCreatesFromSupplier() {
      AtomicInteger counter = new AtomicInteger(0);
      VStreamPath<Integer> path = Path.vstreamGenerate(counter::incrementAndGet).take(3);

      assertThatVStreamPath(path).producesElements(1, 2, 3);
    }

    @Test
    @DisplayName("Path.vstreamRange() creates integer range")
    void vstreamRangeCreatesRange() {
      VStreamPath<Integer> path = Path.vstreamRange(1, 6);

      assertThatVStreamPath(path).producesElements(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("Path.vstreamRange() with empty range creates empty stream")
    void vstreamRangeEmptyRange() {
      VStreamPath<Integer> path = Path.vstreamRange(5, 5);

      assertThatVStreamPath(path).isEmpty();
    }

    @Test
    @DisplayName("Path.vstreamUnfold() creates stream by unfolding from seed")
    void vstreamUnfoldCreatesStreamFromSeed() {
      // Unfold from 1, producing values 1,2,3 and stopping when state > 3
      VStreamPath<Integer> path =
          Path.vstreamUnfold(
              1,
              state ->
                  VTask.succeed(
                      state > 3
                          ? Optional.empty()
                          : Optional.of(new VStream.Seed<>(state, state + 1))));

      assertThatVStreamPath(path).producesElements(1, 2, 3);
    }

    @Test
    @DisplayName("Path.vstreamUnfold() validates non-null function")
    void vstreamUnfoldValidatesNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> Path.vstreamUnfold(0, null))
          .withMessageContaining("f must not be null");
    }

    @Test
    @DisplayName("Path.vstream() validates non-null stream")
    void vstreamValidatesNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> Path.vstream(null))
          .withMessageContaining("stream must not be null");
    }

    @Test
    @DisplayName("Path.vstreamOf() validates non-null elements")
    void vstreamOfValidatesNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> Path.vstreamOf((Object[]) null))
          .withMessageContaining("elements must not be null");
    }

    @Test
    @DisplayName("Path.vstreamFromList() validates non-null list")
    void vstreamFromListValidatesNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> Path.vstreamFromList(null))
          .withMessageContaining("list must not be null");
    }
  }

  @Nested
  @DisplayName("run()")
  class RunTests {

    @Test
    @DisplayName("run() returns the underlying VStream")
    void runReturnsUnderlying() {
      VStream<Integer> vs = VStream.of(1, 2, 3);
      VStreamPath<Integer> path = Path.vstream(vs);

      assertThat(path.run()).isSameAs(vs);
    }
  }

  @Nested
  @DisplayName("Composable Operations (Functor)")
  class ComposableTests {

    @Test
    @DisplayName("map() transforms elements")
    void mapTransformsElements() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3);

      VStreamPath<String> result = path.map(n -> "v:" + n);

      assertThatVStreamPath(result).producesElements("v:1", "v:2", "v:3");
    }

    @Test
    @DisplayName("map() with identity returns equivalent stream")
    void mapIdentity() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3);

      VStreamPath<Integer> result = path.map(x -> x);

      assertThatVStreamPath(result).producesElements(1, 2, 3);
    }

    @Test
    @DisplayName("map() validates non-null mapper")
    void mapValidatesNonNull() {
      VStreamPath<Integer> path = Path.vstreamPure(1);

      assertThatNullPointerException()
          .isThrownBy(() -> path.map(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("peek() observes elements without modifying")
    void peekObservesElements() {
      AtomicInteger sum = new AtomicInteger(0);
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3);

      VStreamPath<Integer> peeked = path.peek(sum::addAndGet);
      // Not materialised yet
      assertThat(sum.get()).isZero();

      assertThatVStreamPath(peeked).producesElements(1, 2, 3);
      assertThat(sum.get()).isEqualTo(6);
    }

    @Test
    @DisplayName("peek() validates non-null consumer")
    void peekValidatesNonNull() {
      VStreamPath<Integer> path = Path.vstreamPure(1);

      assertThatNullPointerException()
          .isThrownBy(() -> path.peek(null))
          .withMessageContaining("consumer must not be null");
    }

    @Test
    @DisplayName("asUnit() maps all elements to Unit")
    void asUnitMapsToUnit() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3);

      VStreamPath<Unit> result = path.asUnit();

      assertThatVStreamPath(result).hasCount(3);
      List<Unit> elements = result.toList().unsafeRun();
      assertThat(elements).containsExactly(Unit.INSTANCE, Unit.INSTANCE, Unit.INSTANCE);
    }
  }

  @Nested
  @DisplayName("Chainable Operations (Monad)")
  class ChainableTests {

    @Test
    @DisplayName("via() flatMaps to sub-streams")
    void viaFlatMapsToSubStreams() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3);

      VStreamPath<Integer> result = path.via(n -> Path.vstreamOf(n, n * 10));

      assertThatVStreamPath(result).producesElements(1, 10, 2, 20, 3, 30);
    }

    @Test
    @DisplayName("via() with empty sub-stream skips element")
    void viaWithEmptySubStream() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3);

      VStreamPath<Integer> result =
          path.via(n -> n == 2 ? Path.vstreamEmpty() : Path.vstreamPure(n));

      assertThatVStreamPath(result).producesElements(1, 3);
    }

    @Test
    @DisplayName("flatMap() is alias for via()")
    void flatMapIsAliasForVia() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2);

      VStreamPath<String> result = path.flatMap(n -> Path.vstreamOf("a:" + n, "b:" + n));

      assertThatVStreamPath(result).producesElements("a:1", "b:1", "a:2", "b:2");
    }

    @Test
    @DisplayName("via() validates non-null mapper")
    void viaValidatesNonNull() {
      VStreamPath<Integer> path = Path.vstreamPure(1);

      assertThatNullPointerException()
          .isThrownBy(() -> path.via(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("via() rejects non-VStreamPath return")
    void viaRejectsNonVStreamPath() {
      VStreamPath<Integer> path = Path.vstreamPure(1);

      VStreamPath<Integer> result = path.via(n -> Path.listPath(n));

      assertThatThrownBy(() -> result.toList().unsafeRun())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("via mapper must return VStreamPath");
    }

    @Test
    @DisplayName("then() ignores value and chains next stream")
    void thenChainsNextStream() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2);
      VStreamPath<String> next = Path.vstreamOf("a", "b");

      VStreamPath<String> result = path.then(() -> next);

      // then() flatMaps each element to the next stream
      assertThatVStreamPath(result).producesElements("a", "b", "a", "b");
    }

    @Test
    @DisplayName("then() validates non-null supplier")
    void thenValidatesNonNull() {
      VStreamPath<Integer> path = Path.vstreamPure(1);

      assertThatNullPointerException()
          .isThrownBy(() -> path.then(null))
          .withMessageContaining("supplier must not be null");
    }
  }

  @Nested
  @DisplayName("Combinable Operations (Applicative)")
  class CombinableTests {

    @Test
    @DisplayName("zipWith() pairs elements positionally")
    void zipWithPairsPositionally() {
      VStreamPath<Integer> nums = Path.vstreamOf(1, 2, 3);
      VStreamPath<String> strs = Path.vstreamOf("a", "b", "c");

      VStreamPath<String> result = nums.zipWith(strs, (n, s) -> n + s);

      assertThatVStreamPath(result).producesElements("1a", "2b", "3c");
    }

    @Test
    @DisplayName("zipWith() stops at shorter stream")
    void zipWithStopsAtShorter() {
      VStreamPath<Integer> nums = Path.vstreamOf(1, 2, 3, 4, 5);
      VStreamPath<String> strs = Path.vstreamOf("a", "b");

      VStreamPath<String> result = nums.zipWith(strs, (n, s) -> n + s);

      assertThatVStreamPath(result).producesElements("1a", "2b");
    }

    @Test
    @DisplayName("zipWith() validates non-null other")
    void zipWithValidatesNonNullOther() {
      VStreamPath<Integer> path = Path.vstreamPure(1);

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(null, (a, b) -> a))
          .withMessageContaining("other must not be null");
    }

    @Test
    @DisplayName("zipWith() validates non-null combiner")
    void zipWithValidatesNonNullCombiner() {
      VStreamPath<Integer> path = Path.vstreamPure(1);
      VStreamPath<Integer> other = Path.vstreamPure(2);

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(other, null))
          .withMessageContaining("combiner must not be null");
    }

    @Test
    @DisplayName("zipWith() rejects non-VStreamPath other")
    void zipWithRejectsNonVStreamPath() {
      VStreamPath<Integer> path = Path.vstreamPure(1);
      StreamPath<Integer> other = StreamPath.pure(2);

      assertThatThrownBy(() -> path.zipWith(other, (a, b) -> a + b))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Cannot zipWith non-VStreamPath");
    }

    @Test
    @DisplayName("zipWith3() combines three streams")
    void zipWith3CombinesThreeStreams() {
      VStreamPath<Integer> a = Path.vstreamOf(1, 2);
      VStreamPath<String> b = Path.vstreamOf("x", "y");
      VStreamPath<Boolean> c = Path.vstreamOf(true, false);

      VStreamPath<String> result = a.zipWith3(b, c, (i, s, bool) -> i + s + bool);

      assertThatVStreamPath(result).producesElements("1xtrue", "2yfalse");
    }

    @Test
    @DisplayName("zipWith3() validates non-null arguments")
    void zipWith3ValidatesNonNull() {
      VStreamPath<Integer> path = Path.vstreamPure(1);
      VStreamPath<Integer> other = Path.vstreamPure(2);

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith3(null, other, (a, b, c) -> a))
          .withMessageContaining("second must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith3(other, null, (a, b, c) -> a))
          .withMessageContaining("third must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith3(other, other, null))
          .withMessageContaining("combiner must not be null");
    }
  }

  @Nested
  @DisplayName("Stream-Specific Operations")
  class StreamSpecificTests {

    @Test
    @DisplayName("filter() keeps matching elements")
    void filterKeepsMatchingElements() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3, 4, 5);

      VStreamPath<Integer> result = path.filter(n -> n % 2 == 0);

      assertThatVStreamPath(result).producesElements(2, 4);
    }

    @Test
    @DisplayName("filter() validates non-null predicate")
    void filterValidatesNonNull() {
      VStreamPath<Integer> path = Path.vstreamPure(1);

      assertThatNullPointerException()
          .isThrownBy(() -> path.filter(null))
          .withMessageContaining("predicate must not be null");
    }

    @Test
    @DisplayName("take() limits elements")
    void takeLimitsElements() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3, 4, 5);

      VStreamPath<Integer> result = path.take(3);

      assertThatVStreamPath(result).producesElements(1, 2, 3);
    }

    @Test
    @DisplayName("take(0) produces empty")
    void takeZeroProducesEmpty() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3);

      VStreamPath<Integer> result = path.take(0);

      assertThatVStreamPath(result).isEmpty();
    }

    @Test
    @DisplayName("drop() skips elements")
    void dropSkipsElements() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3, 4, 5);

      VStreamPath<Integer> result = path.drop(2);

      assertThatVStreamPath(result).producesElements(3, 4, 5);
    }

    @Test
    @DisplayName("takeWhile() takes while predicate holds")
    void takeWhileTakesWhilePredicateHolds() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3, 4, 5);

      VStreamPath<Integer> result = path.takeWhile(n -> n < 4);

      assertThatVStreamPath(result).producesElements(1, 2, 3);
    }

    @Test
    @DisplayName("takeWhile() validates non-null predicate")
    void takeWhileValidatesNonNull() {
      VStreamPath<Integer> path = Path.vstreamPure(1);

      assertThatNullPointerException()
          .isThrownBy(() -> path.takeWhile(null))
          .withMessageContaining("predicate must not be null");
    }

    @Test
    @DisplayName("dropWhile() drops while predicate holds")
    void dropWhileDropsWhilePredicateHolds() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3, 4, 5);

      VStreamPath<Integer> result = path.dropWhile(n -> n < 3);

      assertThatVStreamPath(result).producesElements(3, 4, 5);
    }

    @Test
    @DisplayName("dropWhile() validates non-null predicate")
    void dropWhileValidatesNonNull() {
      VStreamPath<Integer> path = Path.vstreamPure(1);

      assertThatNullPointerException()
          .isThrownBy(() -> path.dropWhile(null))
          .withMessageContaining("predicate must not be null");
    }

    @Test
    @DisplayName("distinct() removes duplicates")
    void distinctRemovesDuplicates() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 2, 3, 1, 3, 4);

      VStreamPath<Integer> result = path.distinct();

      assertThatVStreamPath(result).producesElements(1, 2, 3, 4);
    }

    @Test
    @DisplayName("concat() appends another stream")
    void concatAppendsStream() {
      VStreamPath<Integer> first = Path.vstreamOf(1, 2);
      VStreamPath<Integer> second = Path.vstreamOf(3, 4);

      VStreamPath<Integer> result = first.concat(second);

      assertThatVStreamPath(result).producesElements(1, 2, 3, 4);
    }

    @Test
    @DisplayName("concat() validates non-null other")
    void concatValidatesNonNull() {
      VStreamPath<Integer> path = Path.vstreamPure(1);

      assertThatNullPointerException()
          .isThrownBy(() -> path.concat(null))
          .withMessageContaining("other must not be null");
    }
  }

  @Nested
  @DisplayName("Terminal Operations (bridge to VTaskPath)")
  class TerminalOperationsTests {

    @Test
    @DisplayName("toList() collects all elements")
    void toListCollectsAll() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3);

      List<Integer> result = path.toList().unsafeRun();

      assertThat(result).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("toList() returns empty list for empty stream")
    void toListReturnsEmptyForEmpty() {
      VStreamPath<Integer> path = Path.vstreamEmpty();

      List<Integer> result = path.toList().unsafeRun();

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("fold() reduces elements")
    void foldReducesElements() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3, 4, 5);

      Integer result = path.fold(0, Integer::sum).unsafeRun();

      assertThat(result).isEqualTo(15);
    }

    @Test
    @DisplayName("foldLeft() with accumulator")
    void foldLeftWithAccumulator() {
      VStreamPath<String> path = Path.vstreamOf("a", "b", "c");

      String result = path.foldLeft("", (acc, s) -> acc + s).unsafeRun();

      assertThat(result).isEqualTo("abc");
    }

    @Test
    @DisplayName("foldMap() with monoid")
    void foldMapWithMonoid() {
      Monoid<Integer> sumMonoid =
          new Monoid<>() {
            @Override
            public Integer empty() {
              return 0;
            }

            @Override
            public Integer combine(Integer a, Integer b) {
              return a + b;
            }
          };

      VStreamPath<String> path = Path.vstreamOf("a", "bb", "ccc");

      Integer result = path.foldMap(sumMonoid, String::length).unsafeRun();

      assertThat(result).isEqualTo(6);
    }

    @Test
    @DisplayName("headOption() returns first element")
    void headOptionReturnsFirst() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3);

      Optional<Integer> result = path.headOption().unsafeRun();

      assertThat(result).contains(1);
    }

    @Test
    @DisplayName("headOption() returns empty for empty stream")
    void headOptionEmptyForEmpty() {
      VStreamPath<Integer> path = Path.vstreamEmpty();

      Optional<Integer> result = path.headOption().unsafeRun();

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("lastOption() returns last element")
    void lastOptionReturnsLast() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3);

      Optional<Integer> result = path.lastOption().unsafeRun();

      assertThat(result).contains(3);
    }

    @Test
    @DisplayName("lastOption() returns empty for empty stream")
    void lastOptionEmptyForEmpty() {
      VStreamPath<Integer> path = Path.vstreamEmpty();

      Optional<Integer> result = path.lastOption().unsafeRun();

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("count() counts elements")
    void countCountsElements() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3, 4, 5);

      Long result = path.count().unsafeRun();

      assertThat(result).isEqualTo(5L);
    }

    @Test
    @DisplayName("count() returns 0 for empty stream")
    void countZeroForEmpty() {
      VStreamPath<Integer> path = Path.vstreamEmpty();

      Long result = path.count().unsafeRun();

      assertThat(result).isEqualTo(0L);
    }

    @Test
    @DisplayName("exists() finds matching element")
    void existsFindsMatch() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3, 4, 5);

      Boolean result = path.exists(n -> n == 3).unsafeRun();

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("exists() returns false when no match")
    void existsFalseWhenNoMatch() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3);

      Boolean result = path.exists(n -> n > 10).unsafeRun();

      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("forAll() returns true when all match")
    void forAllTrueWhenAllMatch() {
      VStreamPath<Integer> path = Path.vstreamOf(2, 4, 6);

      Boolean result = path.forAll(n -> n % 2 == 0).unsafeRun();

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("forAll() returns false when any doesn't match")
    void forAllFalseWhenAnyDoesntMatch() {
      VStreamPath<Integer> path = Path.vstreamOf(2, 3, 4);

      Boolean result = path.forAll(n -> n % 2 == 0).unsafeRun();

      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("forAll() returns true for empty stream")
    void forAllTrueForEmpty() {
      VStreamPath<Integer> path = Path.vstreamEmpty();

      Boolean result = path.forAll(n -> n > 100).unsafeRun();

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("find() finds first matching element")
    void findFindsFirstMatch() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3, 4, 5);

      Optional<Integer> result = path.find(n -> n > 3).unsafeRun();

      assertThat(result).contains(4);
    }

    @Test
    @DisplayName("find() returns empty when no match")
    void findEmptyWhenNoMatch() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3);

      Optional<Integer> result = path.find(n -> n > 10).unsafeRun();

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("forEach() executes action on each element")
    void forEachExecutesAction() {
      AtomicInteger sum = new AtomicInteger(0);
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3);

      path.forEach(sum::addAndGet).unsafeRun();

      assertThat(sum.get()).isEqualTo(6);
    }

    @Test
    @DisplayName("forEach() validates non-null consumer")
    void forEachValidatesNonNull() {
      VStreamPath<Integer> path = Path.vstreamPure(1);

      assertThatNullPointerException()
          .isThrownBy(() -> path.forEach(null))
          .withMessageContaining("consumer must not be null");
    }
  }

  @Nested
  @DisplayName("Focus Bridge")
  class FocusBridgeTests {

    record Pair(String name, int value) {}

    @Test
    @DisplayName("focus(FocusPath) extracts focused values")
    void focusPathExtractsValues() {
      Lens<Pair, String> nameLens = Lens.of(Pair::name, (p, n) -> new Pair(n, p.value));
      FocusPath<Pair, String> focusPath = FocusPath.of(nameLens);

      VStreamPath<Pair> path = Path.vstreamOf(new Pair("Alice", 1), new Pair("Bob", 2));

      VStreamPath<String> result = path.focus(focusPath);

      assertThatVStreamPath(result).producesElements("Alice", "Bob");
    }

    @Test
    @DisplayName("focus(FocusPath) validates non-null path")
    void focusPathValidatesNonNull() {
      VStreamPath<Integer> path = Path.vstreamPure(1);

      assertThatNullPointerException()
          .isThrownBy(() -> path.focus((FocusPath<Integer, ?>) null))
          .withMessageContaining("path must not be null");
    }

    @Test
    @DisplayName("focus(AffinePath) filters and extracts matching values")
    void affinePathFiltersAndExtracts() {
      Affine<String, Integer> parseIntAffine =
          Affine.of(
              s -> {
                try {
                  return Optional.of(Integer.parseInt(s));
                } catch (NumberFormatException e) {
                  return Optional.empty();
                }
              },
              (s, i) -> String.valueOf(i));

      AffinePath<String, Integer> affinePath = AffinePath.of(parseIntAffine);

      VStreamPath<String> path = Path.vstreamOf("1", "hello", "42", "world", "3");

      VStreamPath<Integer> result = path.focus(affinePath);

      assertThatVStreamPath(result).producesElements(1, 42, 3);
    }

    @Test
    @DisplayName("focus(AffinePath) validates non-null path")
    void affinePathValidatesNonNull() {
      VStreamPath<Integer> path = Path.vstreamPure(1);

      assertThatNullPointerException()
          .isThrownBy(() -> path.focus((AffinePath<Integer, ?>) null))
          .withMessageContaining("path must not be null");
    }
  }

  @Nested
  @DisplayName("Conversions")
  class ConversionTests {

    @Test
    @DisplayName("first() returns first element as VTaskPath")
    void firstReturnsFirstElement() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3);

      Integer result = path.first().unsafeRun();

      assertThat(result).isEqualTo(1);
    }

    @Test
    @DisplayName("first() throws for empty stream")
    void firstThrowsForEmpty() {
      VStreamPath<Integer> path = Path.vstreamEmpty();

      assertThatThrownBy(() -> path.first().unsafeRun())
          .isInstanceOf(NoSuchElementException.class)
          .hasMessageContaining("VStreamPath is empty");
    }

    @Test
    @DisplayName("last() returns last element as VTaskPath")
    void lastReturnsLastElement() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3);

      Integer result = path.last().unsafeRun();

      assertThat(result).isEqualTo(3);
    }

    @Test
    @DisplayName("last() throws for empty stream")
    void lastThrowsForEmpty() {
      VStreamPath<Integer> path = Path.vstreamEmpty();

      assertThatThrownBy(() -> path.last().unsafeRun())
          .isInstanceOf(NoSuchElementException.class)
          .hasMessageContaining("VStreamPath is empty");
    }

    @Test
    @DisplayName("toStreamPath() materialises to StreamPath")
    void toStreamPathMaterialises() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3);

      StreamPath<Integer> streamPath = path.toStreamPath();

      assertThat(streamPath.toList()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("toListPath() materialises to ListPath")
    void toListPathMaterialises() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3);

      ListPath<Integer> listPath = path.toListPath();

      assertThat(listPath.run()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("toNonDetPath() materialises to NonDetPath")
    void toNonDetPathMaterialises() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3);

      NonDetPath<Integer> nonDetPath = path.toNonDetPath();

      assertThat(nonDetPath.run()).containsExactly(1, 2, 3);
    }
  }

  @Nested
  @DisplayName("Laziness")
  class LazinessTests {

    @Test
    @DisplayName("Stream operations are lazy - no execution until terminal")
    void operationsAreLazy() {
      AtomicBoolean executed = new AtomicBoolean(false);

      VStreamPath<Integer> path =
          Path.vstreamOf(1, 2, 3)
              .map(
                  n -> {
                    executed.set(true);
                    return n * 2;
                  })
              .filter(n -> n > 2)
              .take(1);

      // No execution yet
      assertThat(executed).isFalse();

      // Now materialise
      path.toList().unsafeRun();
      assertThat(executed).isTrue();
    }

    @Test
    @DisplayName("take() limits execution of infinite stream")
    void takeLimitsInfiniteStream() {
      VStreamPath<Integer> infinite = Path.vstreamIterate(1, n -> n + 1);

      VStreamPath<Integer> limited = infinite.take(5);

      assertThatVStreamPath(limited).producesElements(1, 2, 3, 4, 5);
    }
  }

  @Nested
  @DisplayName("Object Methods")
  class ObjectMethodsTests {

    @Test
    @DisplayName("toString() returns descriptive string")
    void toStringReturnsDescriptive() {
      VStreamPath<Integer> path = Path.vstreamPure(1);

      assertThat(path.toString()).isEqualTo("VStreamPath(<stream>)");
    }
  }

  @Nested
  @DisplayName("Composition Chains")
  class CompositionChainTests {

    @Test
    @DisplayName("Complex pipeline with map, filter, take")
    void complexPipeline() {
      VStreamPath<String> result =
          Path.vstreamRange(1, 100).filter(n -> n % 2 == 0).map(n -> "Even: " + n).take(3);

      assertThatVStreamPath(result).producesElements("Even: 2", "Even: 4", "Even: 6");
    }

    @Test
    @DisplayName("Pipeline with flatMap and terminal")
    void pipelineWithFlatMap() {
      VStreamPath<Integer> result =
          Path.vstreamOf(1, 2, 3).via(n -> Path.vstreamOf(n, n * 10)).filter(n -> n > 5);

      assertThatVStreamPath(result).producesElements(10, 20, 30);
    }

    @Test
    @DisplayName("Pipeline folds with monoid")
    void pipelineFoldWithMonoid() {
      Monoid<String> stringMonoid =
          new Monoid<>() {
            @Override
            public String empty() {
              return "";
            }

            @Override
            public String combine(String a, String b) {
              return a.isEmpty() ? b : a + ", " + b;
            }
          };

      String result =
          Path.vstreamOf(1, 2, 3).map(n -> "item" + n).foldMap(stringMonoid, s -> s).unsafeRun();

      assertThat(result).isEqualTo("item1, item2, item3");
    }
  }

  // ===== Stage 5: Parallel and Chunk Operations =====

  @Nested
  @DisplayName("Parallel Operations")
  class ParallelOperations {

    @Test
    @DisplayName("parEvalMap() processes all elements")
    void parEvalMapProcessesAllElements() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3, 4, 5);

      VStreamPath<Integer> result = path.parEvalMap(2, n -> VTask.succeed(n * 2));

      assertThatVStreamPath(result).producesElements(2, 4, 6, 8, 10);
    }

    @Test
    @DisplayName("parEvalMap() preserves order")
    void parEvalMapPreservesOrder() {
      VStreamPath<Integer> path = Path.vstreamFromList(List.of(5, 4, 3, 2, 1));

      VStreamPath<Integer> result =
          path.parEvalMap(
              3,
              n ->
                  VTask.of(
                      () -> {
                        Thread.sleep(n * 5L);
                        return n * 10;
                      }));

      assertThatVStreamPath(result).producesElements(50, 40, 30, 20, 10);
    }

    @Test
    @DisplayName("parEvalMapUnordered() processes all elements")
    void parEvalMapUnorderedProcessesAllElements() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3, 4, 5);

      VStreamPath<Integer> result = path.parEvalMapUnordered(3, n -> VTask.succeed(n * 2));

      assertThatVStreamPath(result)
          .satisfies(elements -> assertThat(elements).containsExactlyInAnyOrder(2, 4, 6, 8, 10));
    }

    @Test
    @DisplayName("parCollect() collects correctly")
    void parCollectCollectsCorrectly() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3, 4, 5);

      VTaskPath<List<Integer>> result = path.parCollect(2);

      assertThat(result.unsafeRun()).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("parEvalMap() on empty stream returns empty")
    void parEvalMapOnEmptyStreamReturnsEmpty() {
      VStreamPath<Integer> path = Path.vstreamEmpty();

      VStreamPath<Integer> result = path.parEvalMap(4, n -> VTask.succeed(n));

      assertThatVStreamPath(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("Chunk Operations")
  class ChunkOperations {

    @Test
    @DisplayName("chunk() groups correctly")
    void chunkGroupsCorrectly() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3, 4, 5);

      VStreamPath<List<Integer>> chunked = path.chunk(2);

      List<List<Integer>> result = chunked.toList().unsafeRun();
      assertThat(result).hasSize(3);
      assertThat(result.get(0)).containsExactly(1, 2);
      assertThat(result.get(1)).containsExactly(3, 4);
      assertThat(result.get(2)).containsExactly(5);
    }

    @Test
    @DisplayName("mapChunked() applies batch function")
    void mapChunkedAppliesBatchFunction() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3, 4, 5, 6);

      VStreamPath<Integer> result =
          path.mapChunked(3, chunk -> chunk.stream().map(n -> n * 10).toList());

      assertThatVStreamPath(result).producesElements(10, 20, 30, 40, 50, 60);
    }

    @Test
    @DisplayName("Pipeline: chunk then parEvalMap for batch processing")
    void pipelineChunkThenParEvalMap() {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3, 4, 5, 6);

      // Chunk into batches of 3, then process each batch in parallel
      VStreamPath<List<Integer>> chunked = path.chunk(3);
      VStreamPath<Integer> result =
          chunked.parEvalMap(
              2, batch -> VTask.of(() -> batch.stream().mapToInt(Integer::intValue).sum()));

      assertThatVStreamPath(result).producesElements(6, 15);
    }

    @Test
    @DisplayName("chunk() on empty stream returns empty")
    void chunkOnEmptyReturnsEmpty() {
      VStreamPath<Integer> path = Path.vstreamEmpty();

      VStreamPath<List<Integer>> result = path.chunk(3);

      assertThatVStreamPath(result).isEmpty();
    }
  }
}
