// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import module java.base;
import module org.higherkindedj.core;

/**
 * A real-world example demonstrating limiting traversals for time-series data analysis.
 *
 * <p>This example simulates financial and operational analytics where:
 *
 * <ul>
 *   <li>Recent data points (last N days) are analysed for trends
 *   <li>Historical data (older periods) requires different treatment
 *   <li>Rolling windows calculate moving averages
 *   <li>Seasonal adjustments are applied to specific periods
 * </ul>
 *
 * <p>Key patterns demonstrated:
 *
 * <ul>
 *   <li>Recent period focus using {@code takingLast(n)}
 *   <li>Historical data using {@code droppingLast(n)}
 *   <li>Rolling window calculations using {@code slicing(from, to)}
 *   <li>Trend analysis by comparing periods
 * </ul>
 */
public class TimeSeriesWindowingExample {

  // Domain models for time-series analytics
  public record DailyMetric(
      LocalDate date, double revenue, int transactions, double avgOrderValue, boolean adjusted) {
    DailyMetric applySeasonalAdjustment(double factor) {
      return new DailyMetric(date, revenue * factor, transactions, avgOrderValue * factor, true);
    }

    DailyMetric recalculateAvg() {
      double newAvg = transactions > 0 ? revenue / transactions : 0.0;
      return new DailyMetric(date, revenue, transactions, newAvg, adjusted);
    }
  }

  public record TrendAnalysis(String period, double avgRevenue, double growth, String direction) {}

  public record MovingAverage(LocalDate endDate, int windowSize, double average) {}

  /** Java 25 instance main method - no static modifier or String[] args required. */
  void main() {
    List<DailyMetric> metrics = generateMetrics(30);

    System.out.println("=== Time-Series Windowing with Limiting Traversals ===\n");
    System.out.println("Dataset: " + metrics.size() + " days of sales metrics");
    System.out.println(
        "Date range: " + metrics.get(0).date() + " to " + metrics.get(metrics.size() - 1).date());
    System.out.println();

    demonstrateRecentPeriodAnalysis(metrics);
    demonstrateHistoricalComparison(metrics);
    demonstrateRollingWindowCalculation(metrics);
    demonstrateSeasonalAdjustment(metrics);
    demonstrateTrendDetection(metrics);
    demonstrateOutlierHandling(metrics);
  }

  private static void demonstrateRecentPeriodAnalysis(List<DailyMetric> metrics) {
    System.out.println("--- Scenario 1: Recent Period Analysis (Last 7 Days) ---");

    // Focus on last 7 days for recent performance
    Traversal<List<DailyMetric>, DailyMetric> last7Days = ListTraversals.takingLast(7);

    List<DailyMetric> recentMetrics = Traversals.getAll(last7Days, metrics);

    System.out.println("Last 7 days performance:");
    double totalRevenue = recentMetrics.stream().mapToDouble(DailyMetric::revenue).sum();
    int totalTransactions = recentMetrics.stream().mapToInt(DailyMetric::transactions).sum();
    double avgDailyRevenue = totalRevenue / 7;

    System.out.printf("  Total Revenue: £%.2f%n", totalRevenue);
    System.out.printf("  Total Transactions: %d%n", totalTransactions);
    System.out.printf("  Average Daily Revenue: £%.2f%n", avgDailyRevenue);
    System.out.printf("  Average Order Value: £%.2f%n", totalRevenue / totalTransactions);

    // Highlight best and worst recent days
    DailyMetric bestDay =
        recentMetrics.stream()
            .max((a, b) -> Double.compare(a.revenue(), b.revenue()))
            .orElseThrow();
    DailyMetric worstDay =
        recentMetrics.stream()
            .min((a, b) -> Double.compare(a.revenue(), b.revenue()))
            .orElseThrow();

    System.out.printf("%nBest recent day: %s with £%.2f%n", bestDay.date(), bestDay.revenue());
    System.out.printf("Worst recent day: %s with £%.2f%n", worstDay.date(), worstDay.revenue());

    System.out.println();
  }

  private static void demonstrateHistoricalComparison(List<DailyMetric> metrics) {
    System.out.println("--- Scenario 2: Historical vs Recent Comparison ---");

    // Historical: Everything except last 7 days
    Traversal<List<DailyMetric>, DailyMetric> historical = ListTraversals.droppingLast(7);
    // Recent: Last 7 days
    Traversal<List<DailyMetric>, DailyMetric> recent = ListTraversals.takingLast(7);

    List<DailyMetric> historicalData = Traversals.getAll(historical, metrics);
    List<DailyMetric> recentData = Traversals.getAll(recent, metrics);

    double historicalAvg =
        historicalData.stream().mapToDouble(DailyMetric::revenue).average().orElse(0.0);
    double recentAvg = recentData.stream().mapToDouble(DailyMetric::revenue).average().orElse(0.0);

    double growthPercent = ((recentAvg - historicalAvg) / historicalAvg) * 100;

    System.out.printf("Historical period (%d days):%n", historicalData.size());
    System.out.printf("  Average daily revenue: £%.2f%n", historicalAvg);

    System.out.printf("%nRecent period (7 days):%n");
    System.out.printf("  Average daily revenue: £%.2f%n", recentAvg);

    System.out.printf("%nGrowth: %+.1f%%%n", growthPercent);
    String trend =
        growthPercent > 5
            ? "📈 Strong growth"
            : growthPercent > 0 ? "📊 Moderate growth" : "📉 Decline";
    System.out.println("Trend: " + trend);

    System.out.println();
  }

  private static void demonstrateRollingWindowCalculation(List<DailyMetric> metrics) {
    System.out.println("--- Scenario 3: Rolling Window (5-Day Moving Average) ---");

    int windowSize = 5;
    List<MovingAverage> movingAverages = new ArrayList<>();

    // Calculate moving average for each possible window
    for (int i = windowSize - 1; i < metrics.size(); i++) {
      int start = i - windowSize + 1;
      int end = i + 1;

      Traversal<List<DailyMetric>, DailyMetric> window = ListTraversals.slicing(start, end);
      List<DailyMetric> windowData = Traversals.getAll(window, metrics);

      double avg = windowData.stream().mapToDouble(DailyMetric::revenue).average().orElse(0.0);
      LocalDate endDate = metrics.get(i).date();

      movingAverages.add(new MovingAverage(endDate, windowSize, avg));
    }

    System.out.printf("%d-day moving averages (showing last 10):%n", windowSize);

    // Show last 10 moving averages
    Traversal<List<MovingAverage>, MovingAverage> lastTenMA = ListTraversals.takingLast(10);
    List<MovingAverage> recentMAs = Traversals.getAll(lastTenMA, movingAverages);

    for (MovingAverage ma : recentMAs) {
      System.out.printf("  %s: £%.2f%n", ma.endDate(), ma.average());
    }

    // Detect trend in moving average
    MovingAverage firstMA = recentMAs.get(0);
    MovingAverage lastMA = recentMAs.get(recentMAs.size() - 1);
    double maTrend = ((lastMA.average() - firstMA.average()) / firstMA.average()) * 100;

    System.out.printf("%nMA Trend over last 10 periods: %+.1f%%%n", maTrend);

    System.out.println();
  }

  private static void demonstrateSeasonalAdjustment(List<DailyMetric> metrics) {
    System.out.println("--- Scenario 4: Seasonal Adjustment by Period ---");

    // Apply different seasonal factors to different periods
    // First week: Winter slowdown (0.9)
    // Middle weeks: Normal (1.0)
    // Last week: Holiday boost (1.15)

    Traversal<List<DailyMetric>, DailyMetric> firstWeek = ListTraversals.taking(7);
    Traversal<List<DailyMetric>, DailyMetric> middleWeeks = ListTraversals.slicing(7, 23);
    Traversal<List<DailyMetric>, DailyMetric> lastWeek = ListTraversals.takingLast(7);

    System.out.println("Applying seasonal adjustment factors:");
    System.out.println("  First week (Winter): 0.9x");
    System.out.println("  Middle weeks (Normal): 1.0x");
    System.out.println("  Last week (Holiday): 1.15x");

    List<DailyMetric> step1 =
        Traversals.modify(firstWeek, m -> m.applySeasonalAdjustment(0.9), metrics);
    List<DailyMetric> step2 =
        Traversals.modify(middleWeeks, m -> m.applySeasonalAdjustment(1.0), step1);
    List<DailyMetric> adjusted =
        Traversals.modify(lastWeek, m -> m.applySeasonalAdjustment(1.15), step2);

    // Compare unadjusted vs adjusted totals
    double originalTotal = metrics.stream().mapToDouble(DailyMetric::revenue).sum();
    double adjustedTotal = adjusted.stream().mapToDouble(DailyMetric::revenue).sum();

    System.out.printf("%nOriginal total revenue: £%.2f%n", originalTotal);
    System.out.printf("Seasonally adjusted total: £%.2f%n", adjustedTotal);
    System.out.printf(
        "Adjustment impact: %+.1f%%%n", ((adjustedTotal - originalTotal) / originalTotal) * 100);

    // Verify adjustment flags
    long adjustedCount = adjusted.stream().filter(DailyMetric::adjusted).count();
    System.out.printf("%nRecords marked as adjusted: %d/%d%n", adjustedCount, adjusted.size());

    System.out.println();
  }

  private static void demonstrateTrendDetection(List<DailyMetric> metrics) {
    System.out.println("--- Scenario 5: Multi-Period Trend Detection ---");

    // Divide data into equal periods for trend analysis
    Traversal<List<DailyMetric>, DailyMetric> period1 = ListTraversals.taking(10);
    Traversal<List<DailyMetric>, DailyMetric> period2 = ListTraversals.slicing(10, 20);
    Traversal<List<DailyMetric>, DailyMetric> period3 = ListTraversals.takingLast(10);

    List<DailyMetric> p1Data = Traversals.getAll(period1, metrics);
    List<DailyMetric> p2Data = Traversals.getAll(period2, metrics);
    List<DailyMetric> p3Data = Traversals.getAll(period3, metrics);

    double p1Avg = p1Data.stream().mapToDouble(DailyMetric::revenue).average().orElse(0.0);
    double p2Avg = p2Data.stream().mapToDouble(DailyMetric::revenue).average().orElse(0.0);
    double p3Avg = p3Data.stream().mapToDouble(DailyMetric::revenue).average().orElse(0.0);

    double p2Growth = ((p2Avg - p1Avg) / p1Avg) * 100;
    double p3Growth = ((p3Avg - p2Avg) / p2Avg) * 100;

    System.out.println("Period analysis (10 days each):");
    System.out.printf("  Period 1: £%.2f avg/day%n", p1Avg);
    System.out.printf("  Period 2: £%.2f avg/day (%+.1f%% vs P1)%n", p2Avg, p2Growth);
    System.out.printf("  Period 3: £%.2f avg/day (%+.1f%% vs P2)%n", p3Avg, p3Growth);

    // Determine overall trend
    String overallTrend;
    if (p2Growth > 0 && p3Growth > 0) {
      overallTrend = "📈 Consistent upward trend";
    } else if (p2Growth < 0 && p3Growth < 0) {
      overallTrend = "📉 Consistent downward trend";
    } else if (p3Growth > p2Growth) {
      overallTrend = "🔄 Recovering/accelerating";
    } else {
      overallTrend = "🔄 Decelerating/plateauing";
    }

    System.out.println("\nOverall trend: " + overallTrend);

    System.out.println();
  }

  private static void demonstrateOutlierHandling(List<DailyMetric> metrics) {
    System.out.println("--- Scenario 6: Outlier Detection and Smoothing ---");

    // Use middle portion to calculate baseline (exclude first and last 5 days)
    Traversal<List<DailyMetric>, DailyMetric> middlePortion =
        ListTraversals.slicing(5, metrics.size() - 5);

    List<DailyMetric> baseline = Traversals.getAll(middlePortion, metrics);

    double mean = baseline.stream().mapToDouble(DailyMetric::revenue).average().orElse(0.0);
    double variance =
        baseline.stream().mapToDouble(m -> Math.pow(m.revenue() - mean, 2)).average().orElse(0.0);
    double stdDev = Math.sqrt(variance);

    System.out.printf("Baseline statistics (middle %d days):%n", baseline.size());
    System.out.printf("  Mean revenue: £%.2f%n", mean);
    System.out.printf("  Standard deviation: £%.2f%n", stdDev);

    double lowerBound = mean - (2 * stdDev);
    double upperBound = mean + (2 * stdDev);
    System.out.printf("  Normal range: £%.2f - £%.2f%n", lowerBound, upperBound);

    // Find outliers in entire dataset
    long outlierCount =
        metrics.stream().filter(m -> m.revenue() < lowerBound || m.revenue() > upperBound).count();
    System.out.printf("%nOutliers detected: %d days%n", outlierCount);

    // Smooth outliers by capping to bounds
    Lens<DailyMetric, Double> revenueLens =
        Lens.of(
            DailyMetric::revenue,
            (m, newRev) ->
                new DailyMetric(
                    m.date(), newRev, m.transactions(), m.avgOrderValue(), m.adjusted()));

    Traversal<List<DailyMetric>, Double> allRevenues =
        Traversals.<DailyMetric>forList().andThen(revenueLens.asTraversal());

    List<DailyMetric> smoothed =
        Traversals.modify(
            allRevenues, rev -> Math.max(lowerBound, Math.min(upperBound, rev)), metrics);

    double originalVariance =
        metrics.stream().mapToDouble(m -> Math.pow(m.revenue() - mean, 2)).average().orElse(0.0);
    double smoothedVariance =
        smoothed.stream().mapToDouble(m -> Math.pow(m.revenue() - mean, 2)).average().orElse(0.0);

    System.out.printf("Variance before smoothing: %.2f%n", originalVariance);
    System.out.printf("Variance after smoothing: %.2f%n", smoothedVariance);
    System.out.printf(
        "Variance reduction: %.1f%%%n",
        ((originalVariance - smoothedVariance) / originalVariance) * 100);

    System.out.println();
    System.out.println("=== Time-Series Windowing Examples Complete ===");
  }

  // Generate sample metrics with some variability
  private static List<DailyMetric> generateMetrics(int days) {
    List<DailyMetric> metrics = new ArrayList<>();
    LocalDate startDate = LocalDate.now().minusDays(days - 1);

    IntStream.range(0, days)
        .forEach(
            i -> {
              LocalDate date = startDate.plusDays(i);

              // Base revenue with growth trend
              double baseRevenue = 1000.0 + (i * 15.0);

              // Add weekly seasonality (weekends higher)
              int dayOfWeek = date.getDayOfWeek().getValue();
              double seasonalFactor = dayOfWeek >= 6 ? 1.3 : 1.0;

              // Add some randomness
              double noise = (Math.random() - 0.5) * 200;

              double revenue = (baseRevenue * seasonalFactor) + noise;
              int transactions = (int) (revenue / (15 + Math.random() * 10));
              double avgOrder = revenue / transactions;

              metrics.add(new DailyMetric(date, revenue, transactions, avgOrder, false));
            });

    return metrics;
  }
}
