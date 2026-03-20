// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.focus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.util.Traversals;

/**
 * Demonstrates cross-ecosystem container navigation using the Focus DSL.
 *
 * <p>This example models a financial portfolio risk analysis system where different subsystems use
 * different collection and container libraries — a realistic scenario in large organisations with
 * heterogeneous tech stacks.
 *
 * <h2>Container Ecosystem Coverage</h2>
 *
 * <table>
 *   <tr><th>Container</th><th>Library</th><th>Cardinality</th><th>Path Type</th></tr>
 *   <tr><td>{@code ImmutableList<AssetClass>}</td><td>Eclipse Collections</td><td>ZERO_OR_MORE</td><td>TraversalPath</td></tr>
 *   <tr><td>{@code ImmutableList<Position>}</td><td>Eclipse Collections</td><td>ZERO_OR_MORE</td><td>TraversalPath</td></tr>
 *   <tr><td>{@code Either<PricingError, MarketPrice>}</td><td>HKJ</td><td>ZERO_OR_ONE</td><td>AffinePath</td></tr>
 *   <tr><td>{@code Either<ComplianceViolation, Approval>}</td><td>HKJ</td><td>ZERO_OR_ONE</td><td>AffinePath</td></tr>
 *   <tr><td>{@code Try<ValuationResult>}</td><td>HKJ</td><td>ZERO_OR_ONE</td><td>AffinePath</td></tr>
 *   <tr><td>{@code Validated<List<String>, RiskReport>}</td><td>HKJ</td><td>ZERO_OR_ONE</td><td>AffinePath</td></tr>
 *   <tr><td>{@code Optional<StopLoss>}</td><td>JDK</td><td>ZERO_OR_ONE</td><td>AffinePath</td></tr>
 *   <tr><td>{@code Map<String, Double>}</td><td>JDK</td><td>ZERO_OR_MORE</td><td>TraversalPath</td></tr>
 * </table>
 *
 * <h2>Key Concepts</h2>
 *
 * <ul>
 *   <li>Eclipse Collections {@code ImmutableList} recognised via SPI and traversed automatically
 *   <li>HKJ {@code Either}, {@code Try}, {@code Validated} narrowing via {@code some(Affine)}
 *   <li>JDK {@code Optional} and {@code Map} standard widening
 *   <li>Navigator chains crossing 3+ container ecosystems in a single expression
 *   <li>Path kind propagation: FOCUS → TRAVERSAL → TRAVERSAL → AFFINE
 * </ul>
 *
 * @see org.higherkindedj.optics.each.EachInstances#fromIterableCollecting
 * @see org.higherkindedj.optics.util.Affines
 */
public class PortfolioRiskExample {

  // ============= Leaf Value Types =============

  /** A market price quote with bid/ask spread and originating exchange. */
  public record MarketPrice(double bid, double ask, String exchange) {}

  /** A stop-loss order configuration with trigger price and strategy. */
  public record StopLoss(double triggerPrice, String strategy) {}

  /** An error that occurred during pricing. */
  public record PricingError(String code, String message) {}

  /** A valuation result snapshot with NAV, unrealised PnL, and timestamp. */
  public record ValuationResult(double nav, double unrealisedPnL, Instant asOf) {}

  /** A compliance violation with the rule breached and detail. */
  public record ComplianceViolation(String rule, String detail) {}

  /** An approval stamp with approver and timestamp. */
  public record Approval(String approvedBy, Instant approvedAt) {}

  /** A risk report with VaR, max drawdown, and methodology. */
  public record RiskReport(double var95, double maxDrawdown, String methodology) {}

  // ============= Navigable Records (3 levels deep) =============

  /**
   * A trading position with ticker, quantity, optional stop-loss, and a live price that may have
   * failed to fetch.
   */
  @GenerateFocus(generateNavigators = true)
  public record Position(
      String ticker,
      int quantity,
      Optional<StopLoss> stopLoss, // JDK Optional  → AffinePath
      Either<PricingError, MarketPrice> livePrice // HKJ Either  → AffinePath
      ) {}

  /**
   * An asset class grouping positions, with a valuation that may have failed and currency
   * exposures.
   */
  @GenerateFocus(generateNavigators = true)
  public record AssetClass(
      String className,
      ImmutableList<Position> positions, // Eclipse Collections → TraversalPath
      Try<ValuationResult> latestValuation, // HKJ Try            → AffinePath
      Map<String, Double> exposures // JDK Map            → TraversalPath
      ) {}

  /**
   * A portfolio containing asset class holdings, a validated risk report, and compliance status.
   */
  @GenerateFocus(generateNavigators = true)
  public record Portfolio(
      String portfolioId,
      ImmutableList<AssetClass> holdings, // Eclipse Collections → TraversalPath
      Validated<List<String>, RiskReport> risk, // HKJ Validated      → AffinePath
      Either<ComplianceViolation, Approval> status // HKJ Either         → AffinePath
      ) {}

  // ============= Examples =============

  public static void main(String[] args) {
    System.out.println("=== Portfolio Risk Analysis: Cross-Ecosystem Navigation ===\n");

    Portfolio portfolio = createSamplePortfolio();

    basicFocusPaths(portfolio);
    navigatorTraversal(portfolio);
    affineContainerAccess(portfolio);
    mixedEcosystemQuery(portfolio);
    fullRiskPipeline(portfolio);
  }

  // --- Scenario 1: Basic Focus Paths ---

  /**
   * Shows that each field produces the correct path type based on its container's cardinality and
   * library origin.
   */
  private static void basicFocusPaths(Portfolio portfolio) {
    System.out.println("--- 1. Basic Focus Paths ---");

    // portfolioId: plain String → FocusPath (no widening)
    FocusPath<Portfolio, String> idPath = PortfolioFocus.portfolioId();
    System.out.println("Portfolio ID: " + idPath.get(portfolio));

    // status: Either<ComplianceViolation, Approval> → AffinePath (ZERO_OR_ONE via SPI)
    var statusPath = PortfolioFocus.status();
    System.out.println("Status path type: " + statusPath.getClass().getSimpleName());
    statusPath
        .getOptional(portfolio)
        .ifPresent(a -> System.out.println("Approved by: " + a.approvedBy()));

    // risk: Validated<List<String>, RiskReport> → AffinePath (ZERO_OR_ONE via SPI)
    var riskPath = PortfolioFocus.risk();
    riskPath
        .getOptional(portfolio)
        .ifPresent(r -> System.out.printf("VaR(95): %.2f%%%n", r.var95()));

    System.out.println();
  }

  // --- Scenario 2: Navigator Traversal ---

  /**
   * Demonstrates navigator chains that cross Eclipse Collections boundaries. The navigator
   * automatically applies {@code fromIterableCollecting} for ImmutableList traversal.
   */
  private static void navigatorTraversal(Portfolio portfolio) {
    System.out.println("--- 2. Navigator Traversal (Eclipse Collections) ---");

    // Traverse all holdings → all positions → extract tickers
    // Crosses: Eclipse ImmutableList (TRAVERSAL) → Eclipse ImmutableList (TRAVERSAL)
    TraversalPath<Portfolio, String> allTickers = PortfolioFocus.holdings().positions().ticker();

    List<String> tickers = Traversals.getAll(allTickers.toTraversal(), portfolio);
    System.out.println("All tickers: " + tickers);

    // Compare with imperative equivalent:
    // List<String> tickers = new ArrayList<>();
    // for (AssetClass ac : portfolio.holdings()) {
    //     for (Position p : ac.positions()) {
    //         tickers.add(p.ticker());
    //     }
    // }

    System.out.println();
  }

  // --- Scenario 3: Affine Container Access ---

  /**
   * Navigates through HKJ container types (Either, Try) to safely extract values that may not be
   * present.
   */
  private static void affineContainerAccess(Portfolio portfolio) {
    System.out.println("--- 3. Affine Container Access (HKJ Either/Try) ---");

    // Navigate into Try<ValuationResult> via navigator
    // Path: holdings (TRAVERSAL) → latestValuation (Try → AFFINE widened to TRAVERSAL)
    var valuations = PortfolioFocus.holdings().latestValuation();
    List<ValuationResult> successfulValuations =
        Traversals.getAll(valuations.toTraversal(), portfolio);
    System.out.println("Successful valuations: " + successfulValuations.size());
    for (ValuationResult v : successfulValuations) {
      System.out.printf("  NAV: %.2f, Unrealised PnL: %.2f%n", v.nav(), v.unrealisedPnL());
    }

    // Navigate into Either<PricingError, MarketPrice>
    // Path: holdings (TRAVERSAL) → positions (TRAVERSAL) → livePrice (Either → TRAVERSAL)
    var allPrices = PortfolioFocus.holdings().positions().livePrice();
    List<MarketPrice> prices = Traversals.getAll(allPrices.toTraversal(), portfolio);
    System.out.println("Successful prices: " + prices.size());
    for (MarketPrice p : prices) {
      System.out.printf("  %s: bid=%.2f ask=%.2f%n", p.exchange(), p.bid(), p.ask());
    }

    System.out.println();
  }

  // --- Scenario 4: Mixed Ecosystem Query ---

  /**
   * Combines Eclipse Collections traversal, HKJ Either affine, and JDK Optional affine in a single
   * navigator chain.
   */
  private static void mixedEcosystemQuery(Portfolio portfolio) {
    System.out.println("--- 4. Mixed Ecosystem Query ---");

    // "Find all stop-loss trigger prices across all positions"
    // Path kind propagation: FOCUS → TRAVERSAL (EC) → TRAVERSAL (EC) → AFFINE (Optional)
    var stopLosses = PortfolioFocus.holdings().positions().stopLoss();
    List<StopLoss> activeStopLosses = Traversals.getAll(stopLosses.toTraversal(), portfolio);
    System.out.println("Active stop-losses: " + activeStopLosses.size());
    for (StopLoss sl : activeStopLosses) {
      System.out.printf("  Trigger: %.2f (%s)%n", sl.triggerPrice(), sl.strategy());
    }

    System.out.println();
  }

  // --- Scenario 5: Full Risk Pipeline ---

  /**
   * A realistic business query combining all container types: "If the portfolio is approved and the
   * risk report is valid, compute total market value across all positions with successful pricing."
   *
   * <p>The imperative equivalent would be ~30 lines of nested null checks, instanceof checks, and
   * for loops. With HKJ navigators, it's a handful of path expressions.
   */
  private static void fullRiskPipeline(Portfolio portfolio) {
    System.out.println("--- 5. Full Risk Pipeline ---");

    // Check compliance status
    var approval = PortfolioFocus.status().getOptional(portfolio);
    if (approval.isEmpty()) {
      System.out.println("Portfolio not approved — skipping risk analysis.");
      return;
    }
    System.out.println("Approved by: " + approval.get().approvedBy());

    // Check risk report
    var riskReport = PortfolioFocus.risk().getOptional(portfolio);
    if (riskReport.isEmpty()) {
      System.out.println("Risk report invalid — skipping.");
      return;
    }
    System.out.printf(
        "Risk methodology: %s, VaR(95): %.2f%%%n",
        riskReport.get().methodology(), riskReport.get().var95());

    // Compute total market value from all successful prices
    var allPrices = PortfolioFocus.holdings().positions().livePrice();
    List<MarketPrice> prices = Traversals.getAll(allPrices.toTraversal(), portfolio);

    double totalMidValue = 0;
    for (MarketPrice mp : prices) {
      totalMidValue += (mp.bid() + mp.ask()) / 2.0;
    }
    System.out.printf(
        "Total mid-market value (from %d prices): %.2f%n", prices.size(), totalMidValue);

    // Imperative equivalent (~30 lines):
    // double total = 0;
    // if (portfolio.status() instanceof Either.Right<?,?> r) {
    //     if (portfolio.risk() instanceof Validated.Valid<?,?> v) {
    //         for (AssetClass ac : portfolio.holdings()) {
    //             for (Position pos : ac.positions()) {
    //                 if (pos.livePrice() instanceof Either.Right<?,?> pr) {
    //                     MarketPrice mp = (MarketPrice) pr.value();
    //                     total += (mp.bid() + mp.ask()) / 2.0;
    //                 }
    //             }
    //         }
    //     }
    // }

    System.out.println();
  }

  // ============= Sample Data =============

  private static Portfolio createSamplePortfolio() {
    var equities =
        new AssetClass(
            "Equities",
            Lists.immutable.of(
                new Position(
                    "AAPL",
                    100,
                    Optional.of(new StopLoss(145.00, "trailing")),
                    Either.right(new MarketPrice(150.25, 150.50, "NYSE"))),
                new Position(
                    "GOOGL",
                    50,
                    Optional.empty(),
                    Either.right(new MarketPrice(2750.00, 2752.00, "NASDAQ"))),
                new Position(
                    "TSLA",
                    25,
                    Optional.of(new StopLoss(200.00, "fixed")),
                    Either.left(new PricingError("TIMEOUT", "Exchange not responding"))),
                new Position(
                    "MSFT",
                    75,
                    Optional.of(new StopLoss(380.00, "trailing")),
                    Either.right(new MarketPrice(390.10, 390.30, "NYSE")))),
            Try.of(() -> new ValuationResult(125_000.00, 3_200.00, Instant.now())),
            Map.of("USD", 0.85, "EUR", 0.10, "GBP", 0.05));

    var fixedIncome =
        new AssetClass(
            "Fixed Income",
            Lists.immutable.of(
                new Position(
                    "US10Y",
                    200,
                    Optional.empty(),
                    Either.right(new MarketPrice(98.50, 98.75, "CME"))),
                new Position(
                    "DE10Y",
                    150,
                    Optional.of(new StopLoss(95.00, "fixed")),
                    Either.right(new MarketPrice(101.25, 101.50, "EUREX")))),
            Try.failure(new RuntimeException("Valuation service unavailable")),
            Map.of("USD", 0.60, "EUR", 0.40));

    return new Portfolio(
        "PF-2026-001",
        Lists.immutable.of(equities, fixedIncome),
        Validated.valid(new RiskReport(2.35, 8.7, "Historical VaR")),
        Either.right(new Approval("Risk Committee", Instant.now())));
  }
}
