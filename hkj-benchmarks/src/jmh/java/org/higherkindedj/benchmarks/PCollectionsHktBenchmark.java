// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.benchmarks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListKindHelper;
import org.higherkindedj.hkt.list.ListMonad;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.pcollections.PVector;
import org.pcollections.TreePVector;

/**
 * PCollections HKT compatibility benchmarks.
 *
 * <p>Compares {@code java.util.ArrayList} (the JDK reference) against PCollections {@link PVector}
 * when both are widened into {@link ListKind} and processed through the existing HKT pipeline
 * ({@link ListMonad}, {@link ListTraverse}). The goal is to quantify the cost of using persistent
 * collections through the {@code java.util.List}-based widen/narrow boundary so that Phases 2 and 3
 * can be evaluated against a baseline.
 *
 * <p>Run with: {@code ./gradlew :hkj-benchmarks:jmh -Pincludes=".*PCollectionsHktBenchmark.*"}.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class PCollectionsHktBenchmark {

  @Param({"10", "100", "1000"})
  public int size;

  private List<Integer> jdkList;
  private PVector<Integer> pVector;

  private Kind<ListKind.Witness, Integer> kindJdk;
  private Kind<ListKind.Witness, Integer> kindPVector;

  @Setup
  public void setup() {
    jdkList = new ArrayList<>(size);
    PVector<Integer> v = TreePVector.empty();
    for (int i = 0; i < size; i++) {
      jdkList.add(i);
      v = v.plus(i);
    }
    pVector = v;
    kindJdk = ListKindHelper.LIST.widen(jdkList);
    kindPVector = ListKindHelper.LIST.widen(pVector);
  }

  // ---------------------------------------------------------------------
  // widen / narrow boundary cost
  // ---------------------------------------------------------------------

  @Benchmark
  public List<Integer> widenNarrowJdk() {
    Kind<ListKind.Witness, Integer> k = ListKindHelper.LIST.widen(jdkList);
    return ListKindHelper.LIST.narrow(k);
  }

  @Benchmark
  public List<Integer> widenNarrowPVector() {
    Kind<ListKind.Witness, Integer> k = ListKindHelper.LIST.widen(pVector);
    return ListKindHelper.LIST.narrow(k);
  }

  // ---------------------------------------------------------------------
  // map
  // ---------------------------------------------------------------------

  @Benchmark
  public Kind<ListKind.Witness, Integer> mapJdk() {
    return ListMonad.INSTANCE.map(x -> x + 1, kindJdk);
  }

  @Benchmark
  public Kind<ListKind.Witness, Integer> mapPVector() {
    return ListMonad.INSTANCE.map(x -> x + 1, kindPVector);
  }

  // ---------------------------------------------------------------------
  // flatMap
  // ---------------------------------------------------------------------

  @Benchmark
  public Kind<ListKind.Witness, Integer> flatMapJdk() {
    return ListMonad.INSTANCE.flatMap(x -> ListKindHelper.LIST.widen(List.of(x, x + 1)), kindJdk);
  }

  @Benchmark
  public Kind<ListKind.Witness, Integer> flatMapPVector() {
    return ListMonad.INSTANCE.flatMap(
        x -> ListKindHelper.LIST.widen(TreePVector.from(List.of(x, x + 1))), kindPVector);
  }

  // ---------------------------------------------------------------------
  // traverse with Optional applicative
  // ---------------------------------------------------------------------

  private static final Function<Integer, Kind<OptionalKind.Witness, Integer>> SAFE_INC =
      i -> OptionalKindHelper.OPTIONAL.widen(Optional.of(i + 1));

  @Benchmark
  public Optional<List<Integer>> traverseJdk() {
    Kind<OptionalKind.Witness, Kind<ListKind.Witness, Integer>> result =
        ListTraverse.INSTANCE.traverse(OptionalMonad.INSTANCE, SAFE_INC, kindJdk);
    return OptionalKindHelper.OPTIONAL.narrow(result).map(ListKindHelper.LIST::narrow);
  }

  @Benchmark
  public Optional<List<Integer>> traversePVector() {
    Kind<OptionalKind.Witness, Kind<ListKind.Witness, Integer>> result =
        ListTraverse.INSTANCE.traverse(OptionalMonad.INSTANCE, SAFE_INC, kindPVector);
    return OptionalKindHelper.OPTIONAL.narrow(result).map(ListKindHelper.LIST::narrow);
  }

  // ---------------------------------------------------------------------
  // foldMap (sum)
  // ---------------------------------------------------------------------

  @Benchmark
  public Integer foldMapJdk() {
    return ListTraverse.INSTANCE.foldMap(Monoids.integerAddition(), i -> i, kindJdk);
  }

  @Benchmark
  public Integer foldMapPVector() {
    return ListTraverse.INSTANCE.foldMap(Monoids.integerAddition(), i -> i, kindPVector);
  }
}
