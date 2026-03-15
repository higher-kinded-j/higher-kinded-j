// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.benchmarks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.optics.Fold;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * JMH benchmarks for Fold.plus(), Fold.empty(), and Fold.sum() combination operations.
 *
 * <p>These benchmarks measure:
 *
 * <ul>
 *   <li>Throughput of getAll on a single fold vs combined folds
 *   <li>Overhead of plus(2), plus(5), and sum(5) combinations
 *   <li>Comparison with manual Stream.concat alternative
 *   <li>Scaling with different collection sizes
 * </ul>
 *
 * <p>Expected results:
 *
 * <ul>
 *   <li>Single fold should be the baseline (fastest)
 *   <li>plus(2) should have roughly 2x the cost of a single fold
 *   <li>plus(5) and sum(5) should be comparable to each other
 *   <li>Manual Stream.concat should be slightly faster due to fewer abstraction layers
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-benchmarks:jmh -Pincludes=".*FoldPlusBenchmark.*"}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class FoldPlusBenchmark {

  record Item(String name, int price) {}

  record Container(
      List<Item> itemsA,
      List<Item> itemsB,
      List<Item> itemsC,
      List<Item> itemsD,
      List<Item> itemsE) {}

  @Param({"10", "100", "1000"})
  private int collectionSize;

  private Container container;
  private Fold<Container, Item> foldA;
  private Fold<Container, Item> foldB;
  private Fold<Container, Item> foldC;
  private Fold<Container, Item> foldD;
  private Fold<Container, Item> foldE;
  private Fold<Container, Item> plusTwo;
  private Fold<Container, Item> plusFive;
  private Fold<Container, Item> sumFive;

  private static final Monoid<Integer> SUM_MONOID =
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

  @Setup
  public void setup() {
    List<Item> items = new ArrayList<>(collectionSize);
    for (int i = 0; i < collectionSize; i++) {
      items.add(new Item("item" + i, i * 10));
    }

    container =
        new Container(
            List.copyOf(items),
            List.copyOf(items),
            List.copyOf(items),
            List.copyOf(items),
            List.copyOf(items));

    foldA = Fold.of(Container::itemsA);
    foldB = Fold.of(Container::itemsB);
    foldC = Fold.of(Container::itemsC);
    foldD = Fold.of(Container::itemsD);
    foldE = Fold.of(Container::itemsE);

    plusTwo = foldA.plus(foldB);
    plusFive = foldA.plus(foldB).plus(foldC).plus(foldD).plus(foldE);
    sumFive = Fold.sum(foldA, foldB, foldC, foldD, foldE);
  }

  /** Baseline: single fold getAll. */
  @Benchmark
  public List<Item> singleFoldGetAll() {
    return foldA.getAll(container);
  }

  /** Two folds combined with plus, getAll. */
  @Benchmark
  public List<Item> plusTwoGetAll() {
    return plusTwo.getAll(container);
  }

  /** Five folds combined with chained plus, getAll. */
  @Benchmark
  public List<Item> plusFiveGetAll() {
    return plusFive.getAll(container);
  }

  /** Five folds combined with Fold.sum(), getAll. */
  @Benchmark
  public List<Item> sumFiveGetAll() {
    return sumFive.getAll(container);
  }

  /** Manual alternative: Stream.concat for two lists. */
  @Benchmark
  public List<Item> manualConcatTwo() {
    return Stream.concat(container.itemsA().stream(), container.itemsB().stream()).toList();
  }

  /** Manual alternative: Stream.of + flatMap for five lists. */
  @Benchmark
  public List<Item> manualConcatFive() {
    return Stream.of(
            container.itemsA(),
            container.itemsB(),
            container.itemsC(),
            container.itemsD(),
            container.itemsE())
        .flatMap(List::stream)
        .toList();
  }

  /** Baseline: single fold foldMap with sum monoid. */
  @Benchmark
  public int singleFoldFoldMap() {
    return foldA.foldMap(SUM_MONOID, Item::price, container);
  }

  /** Two folds combined, foldMap with sum monoid. */
  @Benchmark
  public int plusTwoFoldMap() {
    return plusTwo.foldMap(SUM_MONOID, Item::price, container);
  }

  /** Five folds combined, foldMap with sum monoid. */
  @Benchmark
  public int plusFiveFoldMap() {
    return plusFive.foldMap(SUM_MONOID, Item::price, container);
  }
}
