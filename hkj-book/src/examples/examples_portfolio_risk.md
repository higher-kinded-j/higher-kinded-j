# Portfolio Risk Analysis: Cross-Ecosystem Navigation

> *"The problem is not that we have too many tools; it's that each tool speaks a different language."*

Every large Java organisation accumulates collection libraries the way a kitchen accumulates
spice jars. The pricing team swears by Eclipse Collections for immutable, memory-efficient
lists. The risk engine returns `Validated` so it can accumulate every rule violation in one
pass. The compliance gateway wraps approvals in `Either` because you need to know *why*
something was rejected. And the trade-capture service uses plain `Optional` because it was
written in 2017.

None of these choices are wrong, but navigating across all of them is painful. This example
shows how Higher-Kinded-J's Focus DSL lets you compose a single fluent path expression that crosses three
container ecosystems without manual unwrapping, casting, or iteration.

~~~admonish info title="What You'll Learn"
- How SPI-based type widening automatically handles containers from different libraries
- Path kind propagation: how `FOCUS`, `TRAVERSAL`, and `AFFINE` compose
- Navigator chains that cross JDK, Eclipse Collections, and Higher-Kinded-J containers
- The difference between `FocusPath`, `AffinePath`, and `TraversalPath`, and when each applies
~~~

~~~admonish example title="Run It Now"
```bash
./gradlew :hkj-examples:run \
  -PmainClass=org.higherkindedj.example.optics.focus.PortfolioRiskExample
```
[View source on GitHub](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/focus/PortfolioRiskExample.java)
~~~

---

## The Problem

You're building a risk dashboard for a portfolio management system. The domain model is
three levels deep, and each level uses containers from a different library:

```
Portfolio
├── portfolioId : String                                      ← plain value
├── holdings    : ImmutableList<AssetClass>                    ← Eclipse Collections
├── risk        : Validated<List<String>, RiskReport>          ← HKJ
└── status      : Either<ComplianceViolation, Approval>       ← HKJ

AssetClass
├── className        : String                                 ← plain value
├── positions        : ImmutableList<Position>                 ← Eclipse Collections
├── latestValuation  : Try<ValuationResult>                    ← HKJ
└── exposures        : Map<String, Double>                     ← JDK

Position
├── ticker    : String                                        ← plain value
├── quantity  : int                                           ← plain value
├── stopLoss  : Optional<StopLoss>                            ← JDK
└── livePrice : Either<PricingError, MarketPrice>              ← HKJ
```

A business analyst asks: *"Show me all the live market prices across every position in
every asset class, but only the ones that actually succeeded."*

The imperative version looks like this:

```java
List<MarketPrice> prices = new ArrayList<>();
for (AssetClass ac : portfolio.holdings()) {
    for (Position pos : ac.positions()) {
        if (pos.livePrice() instanceof Either.Right<?,?> r) {
            prices.add((MarketPrice) r.value());
        }
    }
}
```

Three levels of nesting, a type check, and a cast. And this is a *simple* query. The
full risk pipeline, checking compliance, validating the risk report, then aggregating
prices, balloons to 30+ lines of nested conditionals.

---

## The Solution

Annotate each record with `@GenerateFocus(generateNavigators = true)`:

```java
@GenerateFocus(generateNavigators = true)
public record Position(
    String ticker,
    int quantity,
    Optional<StopLoss> stopLoss,                   // JDK Optional  → AffinePath
    Either<PricingError, MarketPrice> livePrice     // HKJ Either    → AffinePath
) {}

@GenerateFocus(generateNavigators = true)
public record AssetClass(
    String className,
    ImmutableList<Position> positions,              // Eclipse Collections → TraversalPath
    Try<ValuationResult> latestValuation,           // HKJ Try            → AffinePath
    Map<String, Double> exposures                   // JDK Map            → TraversalPath
) {}

@GenerateFocus(generateNavigators = true)
public record Portfolio(
    String portfolioId,
    ImmutableList<AssetClass> holdings,             // Eclipse Collections → TraversalPath
    Validated<List<String>, RiskReport> risk,       // HKJ Validated      → AffinePath
    Either<ComplianceViolation, Approval> status    // HKJ Either         → AffinePath
) {}
```

The annotation processor inspects every field's container type. For each one, it consults
the `TraversableGenerator` SPI to determine the cardinality, `ZERO_OR_MORE` for
collections, `ZERO_OR_ONE` for error-or-value types, and generates navigator methods
that return the correctly widened path type.

Now the same query becomes:

```java
List<MarketPrice> prices = Traversals.getAll(
    PortfolioFocus.holdings().positions().livePrice().toTraversal(), portfolio);
```

One line. No nesting, no casts, no manual iteration.

---

## How Path Kinds Propagate

The key insight is that path kinds follow a *widening* rule as you compose through
containers. Once a path widens, it stays wide:

```
  Path Kind         Meaning                 Can narrow back?
  ─────────         ───────                 ────────────────
  FocusPath         Exactly one value       –
  AffinePath        Zero or one value       No
  TraversalPath     Zero or more values     No
```

When you compose two path segments, the result takes the *wider* kind:

```
  Outer          Inner           Result
  ─────          ─────           ──────
  FOCUS     ──→  FOCUS      ──→  FOCUS
  FOCUS     ──→  AFFINE     ──→  AFFINE
  FOCUS     ──→  TRAVERSAL  ──→  TRAVERSAL
  TRAVERSAL ──→  AFFINE     ──→  TRAVERSAL
  TRAVERSAL ──→  TRAVERSAL  ──→  TRAVERSAL
  AFFINE    ──→  FOCUS      ──→  AFFINE
```

Here is how it plays out in the portfolio example:

```
PortfolioFocus
    .holdings()          FOCUS → TRAVERSAL     (ImmutableList is ZERO_OR_MORE)
    .positions()         TRAVERSAL → TRAVERSAL (ImmutableList is ZERO_OR_MORE)
    .livePrice()         TRAVERSAL → TRAVERSAL (Either is ZERO_OR_ONE, but
                                                TRAVERSAL ∘ AFFINE = TRAVERSAL)
```

The final path is a `TraversalPath<Portfolio, MarketPrice>`. It reaches into every
position across every asset class, filters out pricing failures, and collects the
successful `MarketPrice` values, all encoded in the type.

---

## Container Ecosystem Map

The example exercises eight different container types from three ecosystems. The
annotation processor handles each one via the SPI:

| Container | Library | Cardinality | Generated Path | SPI Generator |
|-----------|---------|-------------|----------------|---------------|
| `ImmutableList<AssetClass>` | Eclipse Collections | ZERO_OR_MORE | `TraversalPath` | `EclipseImmutableListGenerator` |
| `ImmutableList<Position>` | Eclipse Collections | ZERO_OR_MORE | `TraversalPath` | `EclipseImmutableListGenerator` |
| `Either<PricingError, MarketPrice>` | HKJ | ZERO_OR_ONE | `AffinePath` | `EitherGenerator` |
| `Either<ComplianceViolation, Approval>` | HKJ | ZERO_OR_ONE | `AffinePath` | `EitherGenerator` |
| `Try<ValuationResult>` | HKJ | ZERO_OR_ONE | `AffinePath` | `TryGenerator` |
| `Validated<List<String>, RiskReport>` | HKJ | ZERO_OR_ONE | `AffinePath` | `ValidatedGenerator` |
| `Optional<StopLoss>` | JDK | ZERO_OR_ONE | `AffinePath` | `OptionalGenerator` |
| `Map<String, Double>` | JDK | ZERO_OR_MORE | `TraversalPath` | `MapValueGenerator` |

No manual widening is needed in navigator chains. The generated code calls the
appropriate `each()` or `some()` method automatically.

---

## The Five Scenarios

The example demonstrates five progressively more complex queries.

### 1. Basic Focus Paths

**The problem:** How do you know which path type a field will produce?

**The solution:** The SPI determines the path type from the container's cardinality:

```java
// Plain String → FocusPath (no widening)
FocusPath<Portfolio, String> idPath = PortfolioFocus.portfolioId();

// Either → AffinePath (ZERO_OR_ONE via SPI)
var statusPath = PortfolioFocus.status();
statusPath.getOptional(portfolio)
    .ifPresent(a -> System.out.println("Approved by: " + a.approvedBy()));

// Validated → AffinePath (ZERO_OR_ONE via SPI)
var riskPath = PortfolioFocus.risk();
riskPath.getOptional(portfolio)
    .ifPresent(r -> System.out.printf("VaR(95): %.2f%%%n", r.var95()));
```

### 2. Navigator Traversal

**The problem:** Extracting values from nested collections normally requires nested loops.

**The solution:** Navigator chains cross two Eclipse Collections `ImmutableList` boundaries automatically:

```java
// Path: Eclipse ImmutableList (TRAVERSAL) → Eclipse ImmutableList (TRAVERSAL)
TraversalPath<Portfolio, String> allTickers =
    PortfolioFocus.holdings().positions().ticker();

List<String> tickers = Traversals.getAll(allTickers.toTraversal(), portfolio);
// → [AAPL, GOOGL, TSLA, MSFT, US10Y, DE10Y]
```

Compare with the imperative equivalent: two nested loops and a mutable list.

### 3. Affine Container Access

**The problem:** Containers like `Try` and `Either` may or may not hold a value. Extracting values safely requires `instanceof` checks or pattern matching at every level.

**The solution:** Affine paths silently skip absent values, collecting only the successes:

```java
// Try<ValuationResult>: only successful valuations are collected
var valuations = PortfolioFocus.holdings().latestValuation();
List<ValuationResult> successful =
    Traversals.getAll(valuations.toTraversal(), portfolio);
// → 1 result (equities succeeded; fixed income's valuation service was unavailable)

// Either<PricingError, MarketPrice>: only Right values pass through
var allPrices = PortfolioFocus.holdings().positions().livePrice();
List<MarketPrice> prices =
    Traversals.getAll(allPrices.toTraversal(), portfolio);
// → 5 prices (TSLA's pricing timed out, so it is excluded)
```

### 4. Mixed Ecosystem Query

**The problem:** A single query may need to traverse Eclipse Collections, then JDK `Optional`, crossing library boundaries.

**The solution:** The navigator chain handles ecosystem boundaries transparently:

```java
// Path: FOCUS → TRAVERSAL (EC) → TRAVERSAL (EC) → AFFINE (Optional) = TRAVERSAL
var stopLosses = PortfolioFocus.holdings().positions().stopLoss();
List<StopLoss> active = Traversals.getAll(stopLosses.toTraversal(), portfolio);
// → 3 stop-losses (GOOGL and US10Y have no stop-loss configured)
```

Three ecosystems, four container types, one expression.

### 5. Full Risk Pipeline

**The problem:** A realistic business query combines compliance checks, risk validation, and price aggregation. The imperative version is ~30 lines of nested `instanceof` checks, `for` loops, and manual accumulation.

**The solution:** Each concern becomes a named path expression:

```java
// Check compliance (Either → AffinePath)
var approval = PortfolioFocus.status().getOptional(portfolio);
if (approval.isEmpty()) { return; }

// Check risk report (Validated → AffinePath)
var riskReport = PortfolioFocus.risk().getOptional(portfolio);
if (riskReport.isEmpty()) { return; }

// Aggregate prices (Eclipse Collections → HKJ Either → TraversalPath)
var allPrices = PortfolioFocus.holdings().positions().livePrice();
List<MarketPrice> prices =
    Traversals.getAll(allPrices.toTraversal(), portfolio);

double totalMidValue = 0;
for (MarketPrice mp : prices) {
    totalMidValue += (mp.bid() + mp.ask()) / 2.0;
}
```

The optics version reads as a sequence of named queries rather than a wall of nested conditionals.

---

## Why This Matters

The Focus DSL does not just save lines of code. It changes how you *think* about
navigating complex data:

| Concern | Imperative | Focus DSL |
|---------|-----------|-----------|
| **Container awareness** | You must know which type to `instanceof` check | The SPI tells the processor |
| **Error handling** | Manual checks at every level | Affine paths silently skip absent values |
| **Composition** | Nested loops that do not compose | Paths compose with `.via()` and navigator chaining |
| **Type safety** | Casts required after `instanceof` | Fully typed: `TraversalPath<Portfolio, MarketPrice>` |
| **Library coupling** | Import every container type | Only `@GenerateFocus` and path types |

When a new container library appears, say Guava's `ImmutableList` or Vavr's `Option`,
you register a `TraversableGenerator` via SPI, and every `@GenerateFocus` record that uses
that container type gets correct navigator methods on the next compile. No existing code
changes.

---

## Source Files

| File | Description |
|------|-------------|
| [PortfolioRiskExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/focus/PortfolioRiskExample.java) | Complete runnable example with all 5 scenarios |
| [ContainerNavigationExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/focus/ContainerNavigationExample.java) | Companion example: manual container navigation with `some(Affine)` |
| [Tutorial20_ContainerNavigation.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial20_ContainerNavigation.java) | Hands-on tutorial exercises |

~~~admonish tip title="See Also"
- [Focus DSL Reference](../optics/focus_dsl.md) - Full annotation processor documentation
~~~

~~~admonish info title="Hands-On Learning"
Practice container navigation in [Tutorial 20: Container Navigation](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial20_ContainerNavigation.java).
~~~

---

**Previous:** [Market Data Pipeline](examples_market_data.md)
