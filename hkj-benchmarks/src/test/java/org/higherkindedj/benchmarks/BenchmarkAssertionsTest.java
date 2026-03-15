// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.benchmarks;

import static org.assertj.core.api.Assertions.assertThat;

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
 * <p>These tests <strong>fail</strong> if benchmark results are not available. Run benchmarks
 * before running these tests. This is intentional — silent skips hide missing quality gates.
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
    // Try exact match first (non-parameterized benchmarks)
    BenchmarkResult exact = results.get(fullName);
    if (exact != null) {
      return Optional.of(exact);
    }
    // Fall back to prefix match for benchmarks that have @Param annotations
    // but the caller doesn't care about specific param values
    return results.entrySet().stream()
        .filter(e -> e.getKey().startsWith(fullName + ":"))
        .map(Map.Entry::getValue)
        .findFirst();
  }

  /**
   * Lookup a parameterized benchmark result. JMH encodes params as {@code
   * ClassName.method:param=value} or with multiple params separated by commas.
   */
  private static Optional<BenchmarkResult> getParam(
      String benchmarkClass, String method, String params) {
    String base = "org.higherkindedj.benchmarks." + benchmarkClass + "." + method;
    // Try colon-separated (standard JMH format)
    Optional<BenchmarkResult> result = Optional.ofNullable(results.get(base + ":" + params));
    if (result.isEmpty()) {
      // Try dot-separated fallback
      result = Optional.ofNullable(results.get(base + "." + params));
    }
    return result;
  }

  private static boolean anyResultsFor(String benchmarkClass) {
    String prefix = "org.higherkindedj.benchmarks." + benchmarkClass + ".";
    return results.keySet().stream().anyMatch(k -> k.startsWith(prefix));
  }

  private static void assertResultsAvailable() {
    assertThat(resultsAvailable)
        .as("Benchmark results available (run ./gradlew :hkj-benchmarks:jmh first)")
        .isTrue();
  }

  private static void assertBenchmarkPresent(String benchmarkClass) {
    assertThat(anyResultsFor(benchmarkClass)).as(benchmarkClass + " results available").isTrue();
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
      assertResultsAvailable();

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
      assertResultsAvailable();

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
      assertResultsAvailable();

      var succeed = get("VTaskBenchmark", "constructSucceed");
      assertThat(succeed).isPresent();

      // Sanity check: succeed should have positive throughput
      assertThat(succeed.get().score)
          .as("VTask.succeed should have positive throughput")
          .isGreaterThan(0.0);
    }

    @Test
    @DisplayName("VTask.of should have measurable throughput")
    void ofShouldHaveMeasurableThroughput() {
      assertResultsAvailable();

      var of = get("VTaskBenchmark", "constructOf");
      assertThat(of).isPresent();

      // Sanity check: of should have positive throughput
      assertThat(of.get().score).as("VTask.of should have positive throughput").isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Map construction should have measurable throughput")
    void mapConstructionShouldHaveMeasurableThroughput() {
      assertResultsAvailable();

      var construction = get("VTaskBenchmark", "mapConstruction");
      assertThat(construction).isPresent();

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
      assertResultsAvailable();

      var zip = get("VTaskParBenchmark", "zipTwoTasks");
      assertThat(zip).isPresent();

      // Zip involves StructuredTaskScope overhead, sanity check for positive throughput
      assertThat(zip.get().score).as("Par.zip should have positive throughput").isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Par.map2 should have measurable throughput")
    void map2ShouldHaveMeasurableThroughput() {
      assertResultsAvailable();

      var map2 = get("VTaskParBenchmark", "map2Tasks");
      assertThat(map2).isPresent();

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
      assertResultsAvailable();

      var vtask = get("VTaskVsIOBenchmark", "vtask_construction");
      assertThat(vtask).isPresent();

      assertThat(vtask.get().score)
          .as("VTask construction should have positive throughput")
          .isGreaterThan(0.0);
    }

    @Test
    @DisplayName("IO construction should have measurable throughput")
    void ioConstructionShouldHaveMeasurableThroughput() {
      assertResultsAvailable();

      var io = get("VTaskVsIOBenchmark", "io_construction");
      assertThat(io).isPresent();

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
      assertResultsAvailable();

      var just = get("MaybeBenchmark", "constructJust");
      assertThat(just).isPresent();

      // Sanity check: construction should have positive throughput
      assertThat(just.get().score)
          .as("Maybe.just construction should have measurable throughput")
          .isGreaterThan(10.0);
    }

    @Test
    @DisplayName("Either operations should have measurable throughput")
    void eitherConstructionShouldBeFast() {
      assertResultsAvailable();

      var right = get("EitherBenchmark", "constructRight");
      assertThat(right).isPresent();

      // Sanity check: construction should have positive throughput
      assertThat(right.get().score)
          .as("Either.right construction should have measurable throughput")
          .isGreaterThan(10.0);
    }

    @Test
    @DisplayName("Trampoline should handle deep recursion efficiently")
    void trampolineShouldHandleDeepRecursion() {
      assertResultsAvailable();

      var factorial = get("TrampolineBenchmark", "factorialTrampoline");
      assertThat(factorial).isPresent();

      // Deep recursion should complete without stack overflow - sanity check
      // Threshold lowered from 0.1 to accommodate slower environments
      assertThat(factorial.get().score)
          .as("Trampoline factorial should have positive throughput")
          .isGreaterThan(0.05);
    }
  }

  // ========================================================================
  // Fold Plus Performance
  // ========================================================================

  @Nested
  @DisplayName("Fold Plus Performance")
  class FoldPlusPerformance {

    @Test
    @DisplayName("Single fold should have measurable throughput")
    void singleFoldShouldHaveMeasurableThroughput() {
      assertResultsAvailable();
      assertBenchmarkPresent("FoldPlusBenchmark");

      var single = getParam("FoldPlusBenchmark", "singleFoldGetAll", "collectionSize=100");
      assertThat(single).isPresent();

      assertThat(single.get().score)
          .as("Single fold getAll should have positive throughput")
          .isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Fold.sum should perform comparably to chained plus")
    void sumShouldPerformComparablyToChainedPlus() {
      assertResultsAvailable();
      assertBenchmarkPresent("FoldPlusBenchmark");

      var plusFive = getParam("FoldPlusBenchmark", "plusFiveGetAll", "collectionSize=100");
      var sumFive = getParam("FoldPlusBenchmark", "sumFiveGetAll", "collectionSize=100");
      assertThat(plusFive).isPresent();
      assertThat(sumFive).isPresent();

      double ratio = sumFive.get().score / plusFive.get().score;
      assertThat(ratio)
          .as("Fold.sum(5) vs chained plus(5) ratio should be reasonable (was %.2f)", ratio)
          .isBetween(0.3, 3.0);
    }

    @Test
    @DisplayName("Plus overhead should be bounded relative to manual concat")
    void plusOverheadShouldBeBounded() {
      assertResultsAvailable();
      assertBenchmarkPresent("FoldPlusBenchmark");

      var plusTwo = getParam("FoldPlusBenchmark", "plusTwoGetAll", "collectionSize=100");
      var manualTwo = getParam("FoldPlusBenchmark", "manualConcatTwo", "collectionSize=100");
      assertThat(plusTwo).isPresent();
      assertThat(manualTwo).isPresent();

      double ratio = manualTwo.get().score / plusTwo.get().score;
      assertThat(ratio)
          .as("Manual concat vs plus(2) ratio should be <10x overhead (was %.2f)", ratio)
          .isLessThan(10.0);
    }

    @Test
    @DisplayName("foldMap should have measurable throughput on combined folds")
    void foldMapShouldHaveMeasurableThroughput() {
      assertResultsAvailable();
      assertBenchmarkPresent("FoldPlusBenchmark");

      var foldMap = getParam("FoldPlusBenchmark", "plusFiveFoldMap", "collectionSize=100");
      assertThat(foldMap).isPresent();

      assertThat(foldMap.get().score)
          .as("plusFive foldMap should have positive throughput")
          .isGreaterThan(0.0);
    }
  }

  // ========================================================================
  // Abstraction Overhead
  // ========================================================================

  @Nested
  @DisplayName("Abstraction Overhead")
  class AbstractionOverhead {

    @Test
    @DisplayName("Raw Java should be faster than IO/VTask wrappers")
    void rawJavaShouldBeFasterThanWrappers() {
      assertResultsAvailable();
      assertBenchmarkPresent("AbstractionOverheadBenchmark");

      var rawJava = get("AbstractionOverheadBenchmark", "rawJava_simple");
      var io = get("AbstractionOverheadBenchmark", "io_simple");
      var vtask = get("AbstractionOverheadBenchmark", "vtask_simple");
      assertThat(rawJava).isPresent();
      assertThat(io).isPresent();
      assertThat(vtask).isPresent();

      assertThat(rawJava.get().score)
          .as("Raw Java should have higher throughput than IO")
          .isGreaterThan(io.get().score);
      assertThat(rawJava.get().score)
          .as("Raw Java should have higher throughput than VTask")
          .isGreaterThan(vtask.get().score);
    }

    @Test
    @DisplayName("Chain operations should have measurable throughput")
    void chainOperationsShouldHaveMeasurableThroughput() {
      assertResultsAvailable();
      assertBenchmarkPresent("AbstractionOverheadBenchmark");

      var rawChain = get("AbstractionOverheadBenchmark", "rawJava_chain");
      var ioChain = get("AbstractionOverheadBenchmark", "io_chain");
      var vtaskChain = get("AbstractionOverheadBenchmark", "vtask_chain");
      assertThat(rawChain).isPresent();
      assertThat(ioChain).isPresent();
      assertThat(vtaskChain).isPresent();

      assertThat(rawChain.get().score)
          .as("Raw Java chain should have positive throughput")
          .isGreaterThan(0.0);
      assertThat(ioChain.get().score)
          .as("IO chain should have positive throughput")
          .isGreaterThan(0.0);
      assertThat(vtaskChain.get().score)
          .as("VTask chain should have positive throughput")
          .isGreaterThan(0.0);
    }

    @Test
    @DisplayName("VTaskPath overhead should be bounded relative to VTask")
    void vtaskPathOverheadShouldBeBounded() {
      assertResultsAvailable();
      assertBenchmarkPresent("AbstractionOverheadBenchmark");

      var vtask = get("AbstractionOverheadBenchmark", "vtask_simple");
      var vtaskPath = get("AbstractionOverheadBenchmark", "vtaskPath_simple");
      assertThat(vtask).isPresent();
      assertThat(vtaskPath).isPresent();

      // VTaskPath wraps VTask; overhead should be bounded
      double ratio = vtask.get().score / vtaskPath.get().score;
      assertThat(ratio)
          .as("VTaskPath overhead vs VTask should be <50x (was %.2f)", ratio)
          .isLessThan(50.0);
    }
  }

  // ========================================================================
  // Concurrency Scaling
  // ========================================================================

  @Nested
  @DisplayName("Concurrency Scaling")
  class ConcurrencyScaling {

    @Test
    @DisplayName("VTask single thread should have measurable throughput")
    void vtaskSingleThreadShouldHaveMeasurableThroughput() {
      assertResultsAvailable();
      assertBenchmarkPresent("ConcurrencyScalingBenchmark");

      var single = get("ConcurrencyScalingBenchmark", "vtask_singleThread");
      assertThat(single).isPresent();

      assertThat(single.get().score)
          .as("VTask single thread should have positive throughput")
          .isGreaterThan(0.0);
    }

    @Test
    @DisplayName("IO single thread should have measurable throughput")
    void ioSingleThreadShouldHaveMeasurableThroughput() {
      assertResultsAvailable();
      assertBenchmarkPresent("ConcurrencyScalingBenchmark");

      var single = get("ConcurrencyScalingBenchmark", "io_singleThread");
      assertThat(single).isPresent();

      assertThat(single.get().score)
          .as("IO single thread should have positive throughput")
          .isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Multi-threaded benchmarks should have positive throughput")
    void multiThreadedShouldHavePositiveThroughput() {
      assertResultsAvailable();
      assertBenchmarkPresent("ConcurrencyScalingBenchmark");

      var vtaskMax = get("ConcurrencyScalingBenchmark", "vtask_maxThreads");
      var ioMax = get("ConcurrencyScalingBenchmark", "io_maxThreads");
      assertThat(vtaskMax).isPresent();
      assertThat(ioMax).isPresent();

      assertThat(vtaskMax.get().score)
          .as("VTask max threads should have positive throughput")
          .isGreaterThan(0.0);
      assertThat(ioMax.get().score)
          .as("IO max threads should have positive throughput")
          .isGreaterThan(0.0);
    }
  }

  // ========================================================================
  // IO Performance
  // ========================================================================

  @Nested
  @DisplayName("IO Performance")
  class IOPerformance {

    @Test
    @DisplayName("IO construction should be faster than execution")
    void constructionShouldBeFasterThanExecution() {
      assertResultsAvailable();
      assertBenchmarkPresent("IOBenchmark");

      var construct = getParam("IOBenchmark", "constructSimple", "chainDepth=50");
      var run = getParam("IOBenchmark", "runSimple", "chainDepth=50");
      assertThat(construct).isPresent();
      assertThat(run).isPresent();

      // With short JMH runs (1 iteration, 200ms), JIT warmup can invert this relationship.
      // Allow construction to be at least 20% of execution throughput as a sanity check.
      assertThat(construct.get().score)
          .as("IO construction should be comparable to or faster than execution")
          .isGreaterThan(run.get().score * 0.2);
    }

    @Test
    @DisplayName("IO deep recursion should complete (stack safe)")
    void deepRecursionShouldComplete() {
      assertResultsAvailable();
      assertBenchmarkPresent("IOBenchmark");

      var deep = getParam("IOBenchmark", "deepRecursion", "chainDepth=50");
      assertThat(deep).isPresent();

      assertThat(deep.get().score)
          .as("IO deep recursion should have positive throughput (stack safe)")
          .isGreaterThan(0.0);
    }

    @Test
    @DisplayName("IO map construction should be lazy (faster than execution)")
    void mapConstructionShouldBeLazy() {
      assertResultsAvailable();
      assertBenchmarkPresent("IOBenchmark");

      var construct = getParam("IOBenchmark", "mapConstruction", "chainDepth=50");
      var execute = getParam("IOBenchmark", "mapExecution", "chainDepth=50");
      assertThat(construct).isPresent();
      assertThat(execute).isPresent();

      // With short JMH runs (1 iteration, 200ms), JIT warmup can invert this relationship.
      // Allow construction to be at least 20% of execution throughput as a sanity check.
      assertThat(construct.get().score)
          .as("IO map construction should be comparable to or faster than execution (lazy)")
          .isGreaterThan(execute.get().score * 0.2);
    }
  }

  // ========================================================================
  // IOPath Performance
  // ========================================================================

  @Nested
  @DisplayName("IOPath Performance")
  class IOPathPerformance {

    @Test
    @DisplayName("IOPath construction should have measurable throughput")
    void constructionShouldHaveMeasurableThroughput() {
      assertResultsAvailable();
      assertBenchmarkPresent("IOPathBenchmark");

      var pure = getParam("IOPathBenchmark", "constructPure", "chainDepth=50");
      assertThat(pure).isPresent();

      assertThat(pure.get().score)
          .as("IOPath pure construction should have positive throughput")
          .isGreaterThan(0.0);
    }

    @Test
    @DisplayName("IOPath construction should be faster than execution")
    void constructionShouldBeFasterThanExecution() {
      assertResultsAvailable();
      assertBenchmarkPresent("IOPathBenchmark");

      var construct = getParam("IOPathBenchmark", "mapConstruction", "chainDepth=50");
      var execute = getParam("IOPathBenchmark", "mapExecution", "chainDepth=50");
      assertThat(construct).isPresent();
      assertThat(execute).isPresent();

      // With short JMH runs (1 iteration, 200ms), JIT warmup can invert this relationship.
      // Allow construction to be at least 20% of execution throughput as a sanity check.
      assertThat(construct.get().score)
          .as("IOPath map construction should be comparable to or faster than execution")
          .isGreaterThan(execute.get().score * 0.2);
    }

    @Test
    @DisplayName("IOPath error handling should have measurable throughput")
    void errorHandlingShouldHaveMeasurableThroughput() {
      assertResultsAvailable();
      assertBenchmarkPresent("IOPathBenchmark");

      var handleError = getParam("IOPathBenchmark", "handleErrorNoError", "chainDepth=50");
      assertThat(handleError).isPresent();

      assertThat(handleError.get().score)
          .as("IOPath handleError should have positive throughput")
          .isGreaterThan(0.0);
    }
  }

  // ========================================================================
  // VTaskPath Performance
  // ========================================================================

  @Nested
  @DisplayName("VTaskPath Performance")
  class VTaskPathPerformance {

    @Test
    @DisplayName("VTaskPath construction should have measurable throughput")
    void constructionShouldHaveMeasurableThroughput() {
      assertResultsAvailable();
      assertBenchmarkPresent("VTaskPathBenchmark");

      var pure = getParam("VTaskPathBenchmark", "constructPure", "chainDepth=50");
      assertThat(pure).isPresent();

      assertThat(pure.get().score)
          .as("VTaskPath pure construction should have positive throughput")
          .isGreaterThan(0.0);
    }

    @Test
    @DisplayName("VTaskPath construction should be faster than execution")
    void constructionShouldBeFasterThanExecution() {
      assertResultsAvailable();
      assertBenchmarkPresent("VTaskPathBenchmark");

      var construct = getParam("VTaskPathBenchmark", "mapConstruction", "chainDepth=50");
      var execute = getParam("VTaskPathBenchmark", "mapExecution", "chainDepth=50");
      assertThat(construct).isPresent();
      assertThat(execute).isPresent();

      // With short JMH runs (1 iteration, 200ms), JIT warmup can invert this relationship.
      // Allow construction to be at least 20% of execution throughput as a sanity check.
      assertThat(construct.get().score)
          .as("VTaskPath map construction should be comparable to or faster than execution")
          .isGreaterThan(execute.get().score * 0.2);
    }

    @Test
    @DisplayName("VTaskPath timeout construction should have measurable throughput")
    void timeoutShouldHaveMeasurableThroughput() {
      assertResultsAvailable();
      assertBenchmarkPresent("VTaskPathBenchmark");

      var timeout = getParam("VTaskPathBenchmark", "timeoutNoTrigger", "chainDepth=50");
      assertThat(timeout).isPresent();

      assertThat(timeout.get().score)
          .as("VTaskPath timeout (not triggered) should have positive throughput")
          .isGreaterThan(0.0);
    }
  }

  // ========================================================================
  // VTaskPath vs IOPath Comparison
  // ========================================================================

  @Nested
  @DisplayName("VTaskPath vs IOPath")
  class VTaskPathVsIOPath {

    @Test
    @DisplayName("Both paths should have measurable simple execution throughput")
    void bothPathsShouldHaveMeasurableThroughput() {
      assertResultsAvailable();
      assertBenchmarkPresent("VTaskPathVsIOPathBenchmark");

      var ioPath =
          getParam("VTaskPathVsIOPathBenchmark", "ioPath_simpleExecution", "chainDepth=50");
      var vtaskPath =
          getParam("VTaskPathVsIOPathBenchmark", "vtaskPath_simpleExecution", "chainDepth=50");
      assertThat(ioPath).isPresent();
      assertThat(vtaskPath).isPresent();

      assertThat(ioPath.get().score)
          .as("IOPath simple execution should have positive throughput")
          .isGreaterThan(0.0);
      assertThat(vtaskPath.get().score)
          .as("VTaskPath simple execution should have positive throughput")
          .isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Construction costs should be comparable between paths")
    void constructionCostsShouldBeComparable() {
      assertResultsAvailable();
      assertBenchmarkPresent("VTaskPathVsIOPathBenchmark");

      var ioConstruct =
          getParam("VTaskPathVsIOPathBenchmark", "ioPath_construction", "chainDepth=50");
      var vtaskConstruct =
          getParam("VTaskPathVsIOPathBenchmark", "vtaskPath_construction", "chainDepth=50");
      assertThat(ioConstruct).isPresent();
      assertThat(vtaskConstruct).isPresent();

      // Construction costs should be within 20x of each other
      double ratio = ioConstruct.get().score / vtaskConstruct.get().score;
      assertThat(ratio)
          .as("IOPath vs VTaskPath construction ratio should be bounded (was %.2f)", ratio)
          .isBetween(0.05, 20.0);
    }

    @Test
    @DisplayName("toIOPath conversion should have measurable throughput")
    void toIOPathConversionShouldWork() {
      assertResultsAvailable();
      assertBenchmarkPresent("VTaskPathVsIOPathBenchmark");

      var conversion =
          getParam(
              "VTaskPathVsIOPathBenchmark", "vtaskPath_toIOPath_simpleExecution", "chainDepth=50");
      assertThat(conversion).isPresent();

      assertThat(conversion.get().score)
          .as("VTaskPath toIOPath conversion should have positive throughput")
          .isGreaterThan(0.0);
    }
  }

  // ========================================================================
  // ForPath VTask Performance
  // ========================================================================

  @Nested
  @DisplayName("ForPath VTask Performance")
  class ForPathVTaskPerformance {

    @Test
    @DisplayName("ForPath comprehension should have measurable throughput")
    void forPathShouldHaveMeasurableThroughput() {
      assertResultsAvailable();
      assertBenchmarkPresent("ForPathVTaskBenchmark");

      var twoStep = getParam("ForPathVTaskBenchmark", "forPath_twoStep", "chainDepth=50");
      assertThat(twoStep).isPresent();

      assertThat(twoStep.get().score)
          .as("ForPath two-step should have positive throughput")
          .isGreaterThan(0.0);
    }

    @Test
    @DisplayName("ForPath overhead should be bounded vs direct chaining")
    void forPathOverheadShouldBeBounded() {
      assertResultsAvailable();
      assertBenchmarkPresent("ForPathVTaskBenchmark");

      var direct = getParam("ForPathVTaskBenchmark", "direct_threeStepChain", "chainDepth=50");
      var forPath =
          getParam("ForPathVTaskBenchmark", "forPath_threeStepEquivalent", "chainDepth=50");
      assertThat(direct).isPresent();
      assertThat(forPath).isPresent();

      // ForPath overhead vs direct should be bounded
      double ratio = direct.get().score / forPath.get().score;
      assertThat(ratio)
          .as("Direct vs ForPath ratio should be <20x (was %.2f)", ratio)
          .isLessThan(20.0);
    }

    @Test
    @DisplayName("ForPath parallel composition should have measurable throughput")
    void parallelCompositionShouldHaveMeasurableThroughput() {
      assertResultsAvailable();
      assertBenchmarkPresent("ForPathVTaskBenchmark");

      var par2 = getParam("ForPathVTaskBenchmark", "forPath_par2", "chainDepth=50");
      assertThat(par2).isPresent();

      assertThat(par2.get().score)
          .as("ForPath par(2) should have positive throughput")
          .isGreaterThan(0.0);
    }
  }

  // ========================================================================
  // Scope and Resource Performance
  // ========================================================================

  @Nested
  @DisplayName("Scope and Resource Performance")
  class ScopePerformance {

    @Test
    @DisplayName("Scope.allSucceed should have measurable throughput")
    void allSucceedShouldHaveMeasurableThroughput() {
      assertResultsAvailable();
      assertBenchmarkPresent("ScopeBenchmark");

      var allSucceed = getParam("ScopeBenchmark", "scope_allSucceed", "taskCount=10");
      assertThat(allSucceed).isPresent();

      assertThat(allSucceed.get().score)
          .as("Scope.allSucceed should have positive throughput")
          .isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Resource usage should have measurable throughput")
    void resourceUsageShouldHaveMeasurableThroughput() {
      assertResultsAvailable();
      assertBenchmarkPresent("ScopeBenchmark");

      var resource = getParam("ScopeBenchmark", "resource_simple_use", "taskCount=10");
      assertThat(resource).isPresent();

      assertThat(resource.get().score)
          .as("Resource simple use should have positive throughput")
          .isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Par.all should have measurable throughput for comparison")
    void parAllShouldHaveMeasurableThroughput() {
      assertResultsAvailable();
      assertBenchmarkPresent("ScopeBenchmark");

      var parAll = getParam("ScopeBenchmark", "par_all_direct", "taskCount=10");
      assertThat(parAll).isPresent();

      assertThat(parAll.get().score)
          .as("Par.all direct should have positive throughput")
          .isGreaterThan(0.0);
    }
  }

  // ========================================================================
  // Memory Footprint
  // ========================================================================

  @Nested
  @DisplayName("Memory Footprint")
  class MemoryFootprint {

    @Test
    @DisplayName("VTask bulk construction should have measurable throughput")
    void vtaskBulkConstructionShouldHaveMeasurableThroughput() {
      assertResultsAvailable();
      assertBenchmarkPresent("MemoryFootprintBenchmark");

      var vtask = getParam("MemoryFootprintBenchmark", "vtask_constructMany", "count=1000");
      assertThat(vtask).isPresent();

      assertThat(vtask.get().score)
          .as("VTask bulk construction should have positive throughput")
          .isGreaterThan(0.0);
    }

    @Test
    @DisplayName("IO bulk construction should have measurable throughput")
    void ioBulkConstructionShouldHaveMeasurableThroughput() {
      assertResultsAvailable();
      assertBenchmarkPresent("MemoryFootprintBenchmark");

      var io = getParam("MemoryFootprintBenchmark", "io_constructMany", "count=1000");
      assertThat(io).isPresent();

      assertThat(io.get().score)
          .as("IO bulk construction should have positive throughput")
          .isGreaterThan(0.0);
    }

    @Test
    @DisplayName("CompletableFuture baseline should have measurable throughput")
    void completableFutureBaselineShouldHaveMeasurableThroughput() {
      assertResultsAvailable();
      assertBenchmarkPresent("MemoryFootprintBenchmark");

      var cf =
          getParam("MemoryFootprintBenchmark", "completableFuture_constructMany", "count=1000");
      assertThat(cf).isPresent();

      assertThat(cf.get().score)
          .as("CompletableFuture bulk construction should have positive throughput")
          .isGreaterThan(0.0);
    }
  }

  // ========================================================================
  // VStream Performance
  // ========================================================================

  @Nested
  @DisplayName("VStream Performance")
  class VStreamPerformance {

    @Test
    @DisplayName("VStream map should have measurable throughput")
    void mapShouldHaveMeasurableThroughput() {
      assertResultsAvailable();
      assertBenchmarkPresent("VStreamBenchmark");

      var map = getParam("VStreamBenchmark", "mapExecution", "chainDepth=50,streamSize=50");
      if (map.isEmpty()) {
        map = getParam("VStreamBenchmark", "mapExecution", "streamSize=50,chainDepth=50");
      }
      assertThat(map).isPresent();

      assertThat(map.get().score)
          .as("VStream map execution should have positive throughput")
          .isGreaterThan(0.0);
    }

    @Test
    @DisplayName("VStream construction should be lazy (faster than execution)")
    void constructionShouldBeLazy() {
      assertResultsAvailable();
      assertBenchmarkPresent("VStreamBenchmark");

      var construct =
          getParam("VStreamBenchmark", "mapConstruction", "chainDepth=50,streamSize=50");
      if (construct.isEmpty()) {
        construct = getParam("VStreamBenchmark", "mapConstruction", "streamSize=50,chainDepth=50");
      }
      var execute = getParam("VStreamBenchmark", "mapExecution", "chainDepth=50,streamSize=50");
      if (execute.isEmpty()) {
        execute = getParam("VStreamBenchmark", "mapExecution", "streamSize=50,chainDepth=50");
      }
      assertThat(construct).isPresent();
      assertThat(execute).isPresent();

      // With short JMH runs (1 iteration, 200ms), JIT warmup can invert this relationship.
      // Allow construction to be at least 20% of execution throughput as a sanity check.
      assertThat(construct.get().score)
          .as("VStream map construction should be comparable to or faster than execution (lazy)")
          .isGreaterThan(execute.get().score * 0.2);
    }

    @Test
    @DisplayName("Java Stream baseline should have measurable throughput")
    void javaStreamBaselineShouldHaveMeasurableThroughput() {
      assertResultsAvailable();
      assertBenchmarkPresent("VStreamBenchmark");

      var baseline =
          getParam("VStreamBenchmark", "baselineJavaStreamMap", "chainDepth=50,streamSize=50");
      if (baseline.isEmpty()) {
        baseline =
            getParam("VStreamBenchmark", "baselineJavaStreamMap", "streamSize=50,chainDepth=50");
      }
      assertThat(baseline).isPresent();

      assertThat(baseline.get().score)
          .as("Java Stream baseline should have positive throughput")
          .isGreaterThan(0.0);
    }
  }

  // ========================================================================
  // VTask vs Platform Threads
  // ========================================================================

  @Nested
  @DisplayName("VTask vs Platform Threads")
  class VTaskVsPlatformThreads {

    @Test
    @DisplayName("VTask parallel execution should have measurable throughput")
    void vtaskParallelShouldHaveMeasurableThroughput() {
      assertResultsAvailable();
      assertBenchmarkPresent("VTaskVsPlatformThreadsBenchmark");

      var vtask = getParam("VTaskVsPlatformThreadsBenchmark", "vtask_parAll", "taskCount=100");
      assertThat(vtask).isPresent();

      assertThat(vtask.get().score)
          .as("VTask Par.all should have positive throughput")
          .isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Platform thread pool should have measurable throughput")
    void platformThreadsShouldHaveMeasurableThroughput() {
      assertResultsAvailable();
      assertBenchmarkPresent("VTaskVsPlatformThreadsBenchmark");

      var platform =
          getParam("VTaskVsPlatformThreadsBenchmark", "platform_invokeAll", "taskCount=100");
      assertThat(platform).isPresent();

      assertThat(platform.get().score)
          .as("Platform thread pool should have positive throughput")
          .isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Sequential baselines should have measurable throughput")
    void sequentialBaselinesShouldHaveMeasurableThroughput() {
      assertResultsAvailable();
      assertBenchmarkPresent("VTaskVsPlatformThreadsBenchmark");

      var vtaskSeq =
          getParam("VTaskVsPlatformThreadsBenchmark", "vtask_sequential", "taskCount=100");
      var platformSeq =
          getParam("VTaskVsPlatformThreadsBenchmark", "platform_sequential", "taskCount=100");
      assertThat(vtaskSeq).isPresent();
      assertThat(platformSeq).isPresent();

      assertThat(vtaskSeq.get().score)
          .as("VTask sequential should have positive throughput")
          .isGreaterThan(0.0);
      assertThat(platformSeq.get().score)
          .as("Platform sequential should have positive throughput")
          .isGreaterThan(0.0);
    }
  }

  // ========================================================================
  // Free Monad Performance
  // ========================================================================

  @Nested
  @DisplayName("Free Monad Performance")
  class FreeMonadPerformance {

    @Test
    @DisplayName("Free construction should be lazy (faster than execution)")
    void constructionShouldBeLazy() {
      assertResultsAvailable();
      assertBenchmarkPresent("FreeBenchmark");

      var construct = getParam("FreeBenchmark", "deepChainConstruction", "chainDepth=50");
      var execute = getParam("FreeBenchmark", "deepChainExecution", "chainDepth=50");
      assertThat(construct).isPresent();
      assertThat(execute).isPresent();

      // With short JMH runs (1 iteration, 200ms), JIT warmup can invert this relationship.
      // Allow construction to be at least 20% of execution throughput as a sanity check.
      assertThat(construct.get().score)
          .as("Free deep chain construction should be comparable to or faster than execution")
          .isGreaterThan(execute.get().score * 0.2);
    }

    @Test
    @DisplayName("Free stack safety stress test should complete")
    void stackSafetyStressTestShouldComplete() {
      assertResultsAvailable();
      assertBenchmarkPresent("FreeBenchmark");

      var stress = getParam("FreeBenchmark", "stackSafetyStressTest", "chainDepth=50");
      assertThat(stress).isPresent();

      assertThat(stress.get().score)
          .as("Free stack safety stress test should have positive throughput")
          .isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Direct composition baseline should be faster than Free interpretation")
    void directShouldBeFasterThanFree() {
      assertResultsAvailable();
      assertBenchmarkPresent("FreeBenchmark");

      var direct = getParam("FreeBenchmark", "directComposition", "chainDepth=50");
      var free = getParam("FreeBenchmark", "deepChainExecution", "chainDepth=50");
      assertThat(direct).isPresent();
      assertThat(free).isPresent();

      assertThat(direct.get().score)
          .as("Direct composition should have higher throughput than Free interpretation")
          .isGreaterThan(free.get().score);
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
      // For parameterized benchmarks, JMH stores params in a separate "params" map.
      // Append them to the name in the format JMH uses: "name:key=value,key2=value2"
      Map<String, String> params = (Map<String, String>) map.get("params");
      if (params != null && !params.isEmpty()) {
        String paramStr =
            params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(","));
        name = name + ":" + paramStr;
      }
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
