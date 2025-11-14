// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.benchmarks;

import java.util.concurrent.TimeUnit;
import org.higherkindedj.hkt.maybe.Maybe;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * JMH benchmarks for Maybe type operations.
 *
 * <p>These benchmarks measure:
 *
 * <ul>
 *   <li>Throughput of common operations (map, flatMap, etc.)
 *   <li>Performance of Just vs Nothing paths
 *   <li>Cost of operation chaining
 *   <li>Comparison with Optional where applicable
 * </ul>
 *
 * <p>Run with: {@code ./gradlew jmh} or {@code ./gradlew :hkj-benchmarks:jmh}
 *
 * <p>Run specific benchmark: {@code ./gradlew jmh --includes=".*MaybeBenchmark.*"}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(
    value = 2,
    jvmArgs = {"-Xms2G", "-Xmx2G"})
public class MaybeBenchmark {

  private Maybe<Integer> just;
  private Maybe<Integer> nothing;

  @Setup
  public void setup() {
    just = Maybe.just(42);
    nothing = Maybe.nothing();
  }

  /** Baseline: simple map operation on Just. */
  @Benchmark
  public Maybe<Integer> justMap() {
    return just.map(x -> x + 1);
  }

  /**
   * Baseline: simple map operation on Nothing.
   *
   * <p>Should be very fast due to instance reuse optimization.
   */
  @Benchmark
  public Maybe<Integer> nothingMap(Blackhole blackhole) {
    Maybe<Integer> result = nothing.map(x -> x + 1);
    blackhole.consume(result);
    return result;
  }

  /** Chained operations on Just. */
  @Benchmark
  public boolean justChainedOperations() {
    return just.map(x -> x + 1).flatMap(x -> Maybe.just(x * 2)).isJust();
  }

  /**
   * Chained operations on Nothing.
   *
   * <p>Should short-circuit efficiently.
   */
  @Benchmark
  public boolean nothingChainedOperations() {
    return nothing.map(x -> x + 1).flatMap(x -> Maybe.just(x * 2)).isNothing();
  }

  /** FlatMap operation on Just. */
  @Benchmark
  public Maybe<Integer> justFlatMap() {
    return just.flatMap(x -> Maybe.just(x * 2));
  }

  /** FlatMap operation on Nothing. */
  @Benchmark
  public Maybe<Integer> nothingFlatMap() {
    return nothing.flatMap(x -> Maybe.just(x * 2));
  }

  /** Long chain of map operations on Just. */
  @Benchmark
  public Maybe<Integer> justLongChain() {
    Maybe<Integer> result = just;
    for (int i = 0; i < 100; i++) {
      result = result.map(x -> x + 1);
    }
    return result;
  }

  /**
   * Long chain of map operations on Nothing.
   *
   * <p>Tests efficiency of instance reuse.
   */
  @Benchmark
  public Maybe<Integer> nothingLongChain() {
    Maybe<Integer> result = nothing;
    for (int i = 0; i < 100; i++) {
      result = result.map(x -> x + 1);
    }
    return result;
  }

  /**
   * Pattern matching with map and orElse.
   *
   * <p>Simulates fold-like behavior.
   */
  @Benchmark
  public String justPatternMatch() {
    return just.map(val -> "value: " + val).orElse("nothing");
  }

  /** Pattern matching with map and orElse on Nothing. */
  @Benchmark
  public String nothingPatternMatch() {
    return nothing.map(val -> "value: " + val).orElse("nothing");
  }

  /**
   * OrElse operation on Just.
   *
   * <p>Should return original value without evaluating alternative.
   */
  @Benchmark
  public Integer justOrElse() {
    return just.orElse(0);
  }

  /**
   * OrElse operation on Nothing.
   *
   * <p>Should evaluate and return alternative.
   */
  @Benchmark
  public Integer nothingOrElse() {
    return nothing.orElse(0);
  }

  /** OrElseGet with supplier on Just. */
  @Benchmark
  public Integer justOrElseGet() {
    return just.orElseGet(() -> 0);
  }

  /** OrElseGet with supplier on Nothing. */
  @Benchmark
  public Integer nothingOrElseGet() {
    return nothing.orElseGet(() -> 0);
  }

  /** Filter simulation using flatMap on Just (passing predicate). */
  @Benchmark
  public Maybe<Integer> justFilterPass() {
    return just.flatMap(x -> x > 0 ? Maybe.just(x) : Maybe.nothing());
  }

  /** Filter simulation using flatMap on Just (failing predicate). */
  @Benchmark
  public Maybe<Integer> justFilterFail() {
    return just.flatMap(x -> x < 0 ? Maybe.just(x) : Maybe.nothing());
  }

  /** Filter simulation using flatMap on Nothing. */
  @Benchmark
  public Maybe<Integer> nothingFilter() {
    return nothing.flatMap(x -> x > 0 ? Maybe.just(x) : Maybe.nothing());
  }

  /** Mixed operations simulating real-world usage. */
  @Benchmark
  public String realWorldPattern(Blackhole blackhole) {
    Maybe<String> result =
        just.map(x -> x * 2)
            .flatMap(x -> x > 50 ? Maybe.just(x) : Maybe.nothing())
            .flatMap(x -> Maybe.just(x.toString()))
            .map(s -> "Result: " + s);

    blackhole.consume(result.isJust());
    return result.orElseGet(() -> "None");
  }

  /** Construction cost of Just. */
  @Benchmark
  public Maybe<Integer> constructJust() {
    return Maybe.just(42);
  }

  /** Construction cost of Nothing. */
  @Benchmark
  public Maybe<Integer> constructNothing() {
    return Maybe.nothing();
  }

  /** Nested flatMap operations. */
  @Benchmark
  public Maybe<Integer> nestedFlatMap() {
    return just.flatMap(x -> Maybe.just(x + 1))
        .flatMap(x -> Maybe.just(x * 2))
        .flatMap(x -> Maybe.just(x - 5))
        .flatMap(x -> Maybe.just(x / 3));
  }

  /**
   * Convert to nullable value.
   *
   * <p>Simulates conversion overhead.
   */
  @Benchmark
  public Integer justToNullable() {
    return just.isJust() ? just.get() : null;
  }

  /** Convert Nothing to nullable value. */
  @Benchmark
  public Integer nothingToNullable() {
    return nothing.isJust() ? nothing.get() : null;
  }

  /** FromNullable conversion on non-null value. */
  @Benchmark
  public Maybe<Integer> fromNullablePresent() {
    return Maybe.fromNullable(42);
  }

  /** FromNullable conversion on null value. */
  @Benchmark
  public Maybe<Integer> fromNullableEmpty() {
    return Maybe.fromNullable(null);
  }
}
