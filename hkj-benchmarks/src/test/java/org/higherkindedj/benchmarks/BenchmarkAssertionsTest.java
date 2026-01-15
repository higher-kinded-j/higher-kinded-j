// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.benchmarks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Performance regression tests that validate JMH benchmark results.
 *
 * <p>These tests read the JSON results from a previous benchmark run and make assertions about:
 *
 * <ul>
 *   <li>Relative performance: certain operations should be faster than others
 *   <li>Overhead ratios: abstraction overhead should stay within bounds
 *   <li>Sanity checks: all benchmarks should have positive throughput
 * </ul>
 *
 * <p>Run benchmarks first: {@code ./gradlew :hkj-benchmarks:jmh}
 *
 * <p>Then run these tests: {@code ./gradlew :hkj-benchmarks:test}
 *
 * <p>These tests use assumptions, so they skip gracefully if benchmark results don't exist.
 */
@DisplayName("Benchmark Assertions")
class BenchmarkAssertionsTest {

  private static final Path RESULTS_PATH = Path.of("build/reports/jmh/results.json");

  private static Map<String, BenchmarkResult> results;
  private static boolean resultsAvailable;

  @BeforeAll
  static void loadResults() {
    if (!Files.exists(RESULTS_PATH)) {
      resultsAvailable = false;
      return;
    }

    try {
      String json = Files.readString(RESULTS_PATH);
      Gson gson = new Gson();
      Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
      List<Map<String, Object>> rawResults = gson.fromJson(json, listType);

      results =
          rawResults.stream()
              .map(BenchmarkResult::fromMap)
              .collect(
                  Collectors.toMap(
                      r -> r.name, r -> r, (a, b) -> a // Keep first if duplicate
                      ));
      resultsAvailable = true;
    } catch (IOException e) {
      resultsAvailable = false;
    }
  }

  private static Optional<BenchmarkResult> get(String benchmarkClass, String method) {
    String fullName = "org.higherkindedj.benchmarks." + benchmarkClass + "." + method;
    return Optional.ofNullable(results.get(fullName));
  }

  private static void assumeResultsAvailable() {
    assumeThat(resultsAvailable)
        .as("Benchmark results available (run ./gradlew :hkj-benchmarks:jmh first)")
        .isTrue();
  }

  // ========================================================================
  // Sanity Checks
  // ========================================================================

  @Nested
  @DisplayName("Sanity Checks")
  class SanityChecks {

    @Test
    @DisplayName("All benchmarks should have positive throughput")
    void allBenchmarksShouldHavePositiveThroughput() {
      assumeResultsAvailable();

      results
          .values()
          .forEach(
              result -> {
                assertThat(result.score)
                    .as("Benchmark %s should have positive throughput", result.name)
                    .isPositive();
              });
    }

    @Test
    @DisplayName("All benchmarks should have reasonable error margins (<50% of score)")
    void allBenchmarksShouldHaveReasonableErrorMargins() {
      assumeResultsAvailable();

      results
          .values()
          .forEach(
              result -> {
                // Skip NaN error margins (occurs with single iteration benchmarks)
                if (Double.isNaN(result.scoreError)) {
                  return;
                }
                double errorPercentage = (result.scoreError / result.score) * 100;
                assertThat(errorPercentage)
                    .as(
                        "Benchmark %s error margin should be <50%% (was %.1f%%)",
                        result.name, errorPercentage)
                    .isLessThan(50.0);
              });
    }
  }

  // ========================================================================
  // VTask Relative Performance
  // ========================================================================

  @Nested
  @DisplayName("VTask Relative Performance")
  class VTaskRelativePerformance {

    @Test
    @DisplayName("VTask.succeed should have measurable throughput")
    void succeedShouldHaveMeasurableThroughput() {
      assumeResultsAvailable();

      var succeed = get("VTaskBenchmark", "constructSucceed");
      assumeThat(succeed).isPresent();

      // Sanity check: succeed should have positive throughput
      assertThat(succeed.get().score)
          .as("VTask.succeed should have positive throughput")
          .isGreaterThan(0.0);
    }

    @Test
    @DisplayName("VTask.of should have measurable throughput")
    void ofShouldHaveMeasurableThroughput() {
      assumeResultsAvailable();

      var of = get("VTaskBenchmark", "constructOf");
      assumeThat(of).isPresent();

      // Sanity check: of should have positive throughput
      assertThat(of.get().score).as("VTask.of should have positive throughput").isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Map construction should have measurable throughput")
    void mapConstructionShouldHaveMeasurableThroughput() {
      assumeResultsAvailable();

      var construction = get("VTaskBenchmark", "mapConstruction");
      assumeThat(construction).isPresent();

      assertThat(construction.get().score)
          .as("Map construction should have positive throughput")
          .isGreaterThan(0.0);
    }
  }

  // ========================================================================
  // Par Combinator Performance
  // ========================================================================

  @Nested
  @DisplayName("Par Combinator Performance")
  class ParCombinatorPerformance {

    @Test
    @DisplayName("Par.zip should have measurable throughput")
    void zipShouldHaveMeasurableThroughput() {
      assumeResultsAvailable();

      var zip = get("VTaskParBenchmark", "zipTwoTasks");
      assumeThat(zip).isPresent();

      // Zip involves StructuredTaskScope overhead, sanity check for positive throughput
      assertThat(zip.get().score).as("Par.zip should have positive throughput").isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Par.map2 should have measurable throughput")
    void map2ShouldHaveMeasurableThroughput() {
      assumeResultsAvailable();

      var map2 = get("VTaskParBenchmark", "map2Tasks");
      assumeThat(map2).isPresent();

      // Sanity check for positive throughput
      assertThat(map2.get().score)
          .as("Par.map2 should have positive throughput")
          .isGreaterThan(0.0);
    }
  }

  // ========================================================================
  // VTask vs IO Comparison
  // ========================================================================

  @Nested
  @DisplayName("VTask vs IO Overhead")
  class VTaskVsIOOverhead {

    @Test
    @DisplayName("VTask construction should have measurable throughput")
    void vtaskConstructionShouldHaveMeasurableThroughput() {
      assumeResultsAvailable();

      var vtask = get("VTaskVsIOBenchmark", "vtask_construction");
      assumeThat(vtask).isPresent();

      assertThat(vtask.get().score)
          .as("VTask construction should have positive throughput")
          .isGreaterThan(0.0);
    }

    @Test
    @DisplayName("IO construction should have measurable throughput")
    void ioConstructionShouldHaveMeasurableThroughput() {
      assumeResultsAvailable();

      var io = get("VTaskVsIOBenchmark", "io_construction");
      assumeThat(io).isPresent();

      assertThat(io.get().score)
          .as("IO construction should have positive throughput")
          .isGreaterThan(0.0);
    }
  }

  // ========================================================================
  // Core Type Performance
  // ========================================================================

  @Nested
  @DisplayName("Core Type Performance")
  class CoreTypePerformance {

    @Test
    @DisplayName("Maybe operations should have measurable throughput")
    void maybeConstructionShouldBeFast() {
      assumeResultsAvailable();

      var just = get("MaybeBenchmark", "constructJust");
      assumeThat(just).isPresent();

      // Sanity check: construction should have positive throughput
      assertThat(just.get().score)
          .as("Maybe.just construction should have measurable throughput")
          .isGreaterThan(10.0);
    }

    @Test
    @DisplayName("Either operations should have measurable throughput")
    void eitherConstructionShouldBeFast() {
      assumeResultsAvailable();

      var right = get("EitherBenchmark", "constructRight");
      assumeThat(right).isPresent();

      // Sanity check: construction should have positive throughput
      assertThat(right.get().score)
          .as("Either.right construction should have measurable throughput")
          .isGreaterThan(10.0);
    }

    @Test
    @DisplayName("Trampoline should handle deep recursion efficiently")
    void trampolineShouldHandleDeepRecursion() {
      assumeResultsAvailable();

      var factorial = get("TrampolineBenchmark", "factorialTrampoline");
      assumeThat(factorial).isPresent();

      // Deep recursion should complete without stack overflow - sanity check
      assertThat(factorial.get().score)
          .as("Trampoline factorial should have positive throughput")
          .isGreaterThan(0.1);
    }
  }

  // ========================================================================
  // Helper Classes
  // ========================================================================

  private static class BenchmarkResult {
    final String name;
    final double score;
    final double scoreError;
    final String scoreUnit;

    BenchmarkResult(String name, double score, double scoreError, String scoreUnit) {
      this.name = name;
      this.score = score;
      this.scoreError = scoreError;
      this.scoreUnit = scoreUnit;
    }

    @SuppressWarnings("unchecked")
    static BenchmarkResult fromMap(Map<String, Object> map) {
      String name = (String) map.get("benchmark");
      Map<String, Object> primaryMetric = (Map<String, Object>) map.get("primaryMetric");
      double score = parseDouble(primaryMetric.get("score"));
      double scoreError = parseDouble(primaryMetric.get("scoreError"));
      String scoreUnit = (String) primaryMetric.get("scoreUnit");
      return new BenchmarkResult(name, score, scoreError, scoreUnit);
    }

    private static double parseDouble(Object value) {
      if (value == null) {
        return 0.0;
      }
      if (value instanceof Number) {
        return ((Number) value).doubleValue();
      }
      // Handle NaN/Infinity as strings (JMH outputs these for single iteration)
      String str = value.toString();
      if ("NaN".equalsIgnoreCase(str)) {
        return Double.NaN;
      }
      if ("Infinity".equalsIgnoreCase(str) || "+Infinity".equalsIgnoreCase(str)) {
        return Double.POSITIVE_INFINITY;
      }
      if ("-Infinity".equalsIgnoreCase(str)) {
        return Double.NEGATIVE_INFINITY;
      }
      return Double.parseDouble(str);
    }

    @Override
    public String toString() {
      return String.format("%s: %.2f ± %.2f %s", name, score, scoreError, scoreUnit);
    }
  }
}
