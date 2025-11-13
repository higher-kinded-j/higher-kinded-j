/*
 * Copyright (c) 2025 Magnus Smith
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 */

package org.higherkindedj.benchmarks;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.higherkindedj.hkt.either.Either;
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
 * JMH benchmarks for Either type operations.
 *
 * <p>These benchmarks measure:
 *
 * <ul>
 *   <li>Throughput of common operations (map, flatMap, etc.)
 *   <li>Performance of Left vs Right paths
 *   <li>Cost of operation chaining
 *   <li>Memory allocation characteristics
 * </ul>
 *
 * <p>Run with: {@code ./gradlew jmh} or {@code ./gradlew :hkj-benchmarks:jmh}
 *
 * <p>Run specific benchmark: {@code ./gradlew jmh --includes=".*EitherBenchmark.*"}
 *
 * <p>Run with GC profiling: {@code ./gradlew jmh -Pjmh.profilers=gc}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(value = 2, jvmArgs = {"-Xms2G", "-Xmx2G"})
public class EitherBenchmark {

  private Either<String, Integer> right;
  private Either<String, Integer> left;

  @Setup
  public void setup() {
    right = Either.right(42);
    left = Either.left("error");
  }

  /**
   * Baseline: simple map operation on Right.
   *
   * <p>Measures the overhead of a single map transformation.
   */
  @Benchmark
  public Either<String, Integer> rightMap() {
    return right.map(x -> x + 1);
  }

  /**
   * Baseline: simple map operation on Left.
   *
   * <p>Should be very fast due to instance reuse optimization.
   */
  @Benchmark
  public Either<String, Integer> leftMap(Blackhole blackhole) {
    Either<String, Integer> result = left.map(x -> x + 1);
    blackhole.consume(result);
    return result;
  }

  /**
   * Chained operations on Right.
   *
   * <p>Measures the cost of composing multiple operations.
   */
  @Benchmark
  public boolean rightChainedOperations() {
    return right.map(x -> x + 1).flatMap(x -> Either.right(x * 2)).isRight();
  }

  /**
   * Chained operations on Left.
   *
   * <p>Should short-circuit and be very efficient.
   */
  @Benchmark
  public boolean leftChainedOperations() {
    return left.map(x -> x + 1).flatMap(x -> Either.right(x * 2)).isLeft();
  }

  /**
   * FlatMap operation on Right.
   *
   * <p>Measures monadic bind performance.
   */
  @Benchmark
  public Either<String, Integer> rightFlatMap() {
    return right.flatMap(x -> Either.right(x * 2));
  }

  /**
   * FlatMap operation on Left.
   *
   * <p>Should reuse the same instance.
   */
  @Benchmark
  public Either<String, Integer> leftFlatMap() {
    return left.flatMap(x -> Either.right(x * 2));
  }

  /**
   * Long chain of map operations on Right.
   *
   * <p>Simulates real-world composition patterns.
   */
  @Benchmark
  public Either<String, Integer> rightLongChain() {
    Either<String, Integer> result = right;
    for (int i = 0; i < 100; i++) {
      result = result.map(x -> x + 1);
    }
    return result;
  }

  /**
   * Long chain of map operations on Left.
   *
   * <p>Tests efficiency of instance reuse across many operations.
   */
  @Benchmark
  public Either<String, Integer> leftLongChain() {
    Either<String, Integer> result = left;
    for (int i = 0; i < 100; i++) {
      result = result.map(x -> x + 1);
    }
    return result;
  }

  /**
   * Pattern matching with fold.
   *
   * <p>Measures the overhead of eliminating the Either.
   */
  @Benchmark
  public String rightFold() {
    return right.fold(err -> "error: " + err, val -> "value: " + val);
  }

  /**
   * Pattern matching with fold on Left.
   */
  @Benchmark
  public String leftFold() {
    return left.fold(err -> "error: " + err, val -> "value: " + val);
  }

  /**
   * Fold-based recovery on Right.
   *
   * <p>Measures the cost of recovering from Left with fold.
   */
  @Benchmark
  public Integer rightRecovery() {
    return right.fold(err -> 0, val -> val);
  }

  /**
   * Fold-based recovery on Left.
   *
   * <p>Should evaluate and return the alternative.
   */
  @Benchmark
  public Integer leftRecovery() {
    return left.fold(err -> 0, val -> val);
  }

  /**
   * Mixed operations simulating real-world usage.
   *
   * <p>Combines map, flatMap, and fold in a realistic pattern.
   */
  @Benchmark
  public String realWorldPattern(Blackhole blackhole) {
    Either<String, String> result =
        right
            .map(x -> x * 2)
            .flatMap(x -> x > 50 ? Either.right(x) : Either.left("too small"))
            .map(Object::toString);

    blackhole.consume(result.isRight());
    return result.fold(err -> "Error: " + err, val -> "Success: " + val);
  }

  /**
   * Construction cost of Right.
   */
  @Benchmark
  public Either<String, Integer> constructRight() {
    return Either.right(42);
  }

  /**
   * Construction cost of Left.
   */
  @Benchmark
  public Either<String, Integer> constructLeft() {
    return Either.left("error");
  }

  /**
   * Nested flatMap operations.
   *
   * <p>Tests performance of deeply nested monadic compositions.
   */
  @Benchmark
  public Either<String, Integer> nestedFlatMap() {
    return right
        .flatMap(x -> Either.right(x + 1))
        .flatMap(x -> Either.right(x * 2))
        .flatMap(x -> Either.right(x - 5))
        .flatMap(x -> Either.right(x / 3));
  }
}
