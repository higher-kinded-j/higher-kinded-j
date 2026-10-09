// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.benchmarks;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.each.EachInstances;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.util.Traversals;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Modifying every element of a list through a Focus path's {@code each()}, and of an array through
 * {@code EachInstances.arrayEach()}.
 *
 * <p>The cost should grow with the number of elements. {@code BenchmarkAssertionsTest} compares the
 * two sizes: ten times the elements should cost about ten times as long, where a rebuild that
 * copied the list built so far at each element would cost about a hundred.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
public class EachRebuildBenchmark {

  record Basket(List<Integer> items) {}

  private static final TraversalPath<Basket, Integer> ITEMS =
      FocusPath.of(Lens.of(Basket::items, (basket, items) -> new Basket(items))).each();

  private static final Traversal<Integer[], Integer> ARRAY =
      EachInstances.<Integer>arrayEach().each();

  @Param({"10000", "100000"})
  private int elements;

  private Basket basket;
  private Integer[] array;

  @Setup
  public void setup() {
    basket = new Basket(IntStream.range(0, elements).boxed().toList());
    array = basket.items().toArray(Integer[]::new);
  }

  @Benchmark
  public Basket focusEach() {
    return ITEMS.modifyAll(item -> item + 1, basket);
  }

  @Benchmark
  public Integer[] arrayEach() {
    return Traversals.modify(ARRAY, item -> item + 1, array);
  }
}
