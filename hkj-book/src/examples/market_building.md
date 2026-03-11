# Building the Pipeline

> *"The trick to forgetting the big picture is to look at everything close-up."*
>
> -- Martin Amis, *The Information*

That's exactly the plan. The previous page laid out the full architecture: nine stages,
fourteen features, three exchange feeds converging into alerts. Now we forget all that and
zoom in, one operator at a time, trusting that the composition will hold the big picture
together when we're done.

~~~admonish info title="What You'll Build"
In this chapter you'll build the first five stages of the market data pipeline, from raw
tick generation through to windowed aggregation. Each stage introduces one or two HKJ
features, with architecture notes explaining *why* each choice was made.
~~~

---

## Step 1: Generate Ticks with `VStream.unfold` {#step-1}

**Problem:** We need a realistic stream of price ticks that simulates a live exchange feed.
Each tick depends on the previous price (random walk), so generation is inherently stateful.

**HKJ feature:** `VStream.unfold(seed, f)`: creates an infinite stream from a seed state
and a function that produces the next element plus the next state.

```java
public VStream<PriceTick> ticks() {
    double[] initialPrices = new double[symbols.size()];
    Arrays.fill(initialPrices, basePrice);

    return VStream.unfold(
        new FeedState(initialPrices, 0, new Random(seed)),
        state -> VTask.of(() -> {
            int idx = state.index();
            Symbol symbol = symbols.get(idx);
            double currentPrice = state.prices()[idx];

            // Random walk: price changes by up to ±volatility%
            double change = (state.random().nextGaussian() * volatility) * currentPrice;
            double newPrice = Math.max(0.01, currentPrice + change);

            // Generate bid/ask with realistic spread
            double spreadBps = 5 + state.random().nextDouble() * 10;
            double halfSpread = newPrice * spreadBps / 20000.0;
            Price bid = Price.of(newPrice - halfSpread);
            Price ask = Price.of(newPrice + halfSpread);

            PriceTick tick = new PriceTick(symbol, bid, ask,
                Volume.of(100 + state.random().nextInt(9900)),
                exchange, Instant.now());

            // Advance state: update price, cycle to next symbol
            double[] updated = state.prices().clone();
            updated[idx] = newPrice;
            int nextIndex = (idx + 1) % symbols.size();

            return Optional.of(new VStream.Seed<>(
                tick, new FeedState(updated, nextIndex, state.random())));
        }));
}
```

```
  unfold(seed₀, f)
  ════════════════

  seed₀ ──▶ f ──▶ (tick₁, seed₁) ──▶ f ──▶ (tick₂, seed₂) ──▶ f ──▶ ...
                     │                         │
                     ▼                         ▼
               Emit(tick₁)              Emit(tick₂)
```

Key points:
- The stream is **infinite**, so use `.take(n)` to limit consumption
- The function returns `Optional<Seed<A, S>>`; returning `Optional.empty()` ends the stream
- The `VTask` wrapper means each tick generation runs on a virtual thread
- Deterministic with a fixed seed, useful for reproducible tests

```java
List<PriceTick> ticks = feed.ticks().take(5).toList().run();
```

~~~admonish note title="Design Decision: Why unfold?"
`unfold` is the dual of `fold`: where `fold` collapses a stream into a value, `unfold`
*expands* a value into a stream. For price feeds, this is natural: each tick depends on
the previous state (price, RNG), and the stream is conceptually infinite. Alternatives
considered:
- **`VStream.fromList`**: no good for infinite or dynamic data
- **`VStream.iterate`**: works for `A → A`, but we need `S → (A, S)` where the state
  type differs from the output type (we emit `PriceTick` but thread `FeedState`)
- **External push**: would require a backpressure protocol; pull-based unfold is simpler
~~~

---

## Step 2: Merge Exchange Feeds with `VStreamPar.merge` {#step-2}

**Problem:** Ticks arrive from multiple exchanges. We need a single unified stream that
interleaves ticks as they arrive, not round-robin but in true arrival order.

**HKJ feature:** `VStreamPar.merge(streams)`: merges multiple streams concurrently, each
consumed on its own virtual thread. Elements are emitted as soon as any source produces one.

```java
public static VStream<PriceTick> merge(List<ExchangeFeed> feeds) {
    List<VStream<PriceTick>> tickStreams = feeds.stream()
        .map(ExchangeFeed::ticks)
        .toList();
    return VStreamPar.merge(tickStreams);
}
```

<div class="pipeline-diagram">
  <div class="pipeline-row pipeline-row-center">
    <span class="pipeline-node pn-green">NYSE</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-label">push</span>
    <span class="pipeline-arrow">↘</span>
  </div>
  <div class="pipeline-row pipeline-row-center">
    <span class="pipeline-node pn-blue">LSE</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-label">push</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-node pn-grey">Shared Queue</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-label">pull</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-node pn-red">Merged Stream</span>
  </div>
  <div class="pipeline-row pipeline-row-center">
    <span class="pipeline-node pn-orange">TSE</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-label">push</span>
    <span class="pipeline-arrow">↗</span>
  </div>
</div>

Each exchange runs on its own **virtual thread**. Elements arrive in the shared queue in
true **arrival order**.

How it works internally:
1. Each source stream is consumed on its own virtual thread within a `StructuredTaskScope`
2. Elements are pushed to a shared `LinkedBlockingQueue`
3. The consumer pulls from the queue, getting elements in true arrival order
4. If any source fails, the error propagates immediately and other sources are cancelled
5. When the consumer is done (e.g. via `take()`), producers are cancelled via `close()`

```java
SimulatedExchangeFeed nyse = new SimulatedExchangeFeed(
    Exchange.NYSE, symbols, 150.0, 0.002, 42L);
SimulatedExchangeFeed lse = new SimulatedExchangeFeed(
    Exchange.LSE,  symbols, 150.0, 0.002, 99L);

List<PriceTick> merged = FeedMerger.merge(nyse, lse).take(10).toList().run();
// Contains ticks from both exchanges, interleaved by arrival order
```

~~~admonish warning title="Cancellation and Resource Safety"
When the consumer terminates early (e.g. `take(20)`), the merge stream's `close()` method
sets a cancellation flag that stops the producer threads. Without this, producers would
continue pushing into the queue indefinitely, a classic resource leak with concurrent
streams. The *structured concurrency* design principle ensures this cleanup happens
automatically.
~~~

~~~admonish note title="Design Decision: Why merge over concat or interleave?"
Three options for combining streams:
- **`concat`** (sequential): drains stream A completely, then stream B. Unusable for
  live feeds because you would never see LSE ticks until NYSE disconnects.
- **`interleave`** (alternating): takes one from A, one from B, one from A, and so on. Gives
  equal airtime but does not reflect real arrival patterns.
- **`merge`** (concurrent): each source runs on its own thread and elements arrive in
  natural order. This is the only option that faithfully models real-time data.
~~~

---

## Step 3: Concurrent Enrichment with `Par.map2` + `VStreamPar.parEvalMap` {#step-3}

**Problem:** Each raw tick needs two pieces of supplementary data: instrument metadata and
an FX conversion rate. These lookups are independent and can run in parallel. Across the
stream, we want bounded concurrency (don't open 10,000 connections at once).

**HKJ features:**
- `Par.map2(taskA, taskB, combiner)`: runs two VTasks concurrently on virtual threads and
  combines their results
- `VStreamPar.parEvalMap(stream, concurrency, f)`: applies an effectful function to each
  element with bounded concurrency, preserving input order

```java
public class TickEnricher {
    // Per-tick: fetch instrument + FX rate in parallel
    public VTask<EnrichedTick> enrichOne(PriceTick tick) {
        VTask<Instrument> instrumentTask = refData.lookup(tick.symbol());
        VTask<BigDecimal> fxTask = fxService.rateToUsd(tick.exchange().currency());

        return Par.map2(instrumentTask, fxTask,
            (instrument, fxRate) -> new EnrichedTick(tick, instrument, fxRate));
    }

    // Across the stream: bounded concurrent enrichment
    public VStream<EnrichedTick> enrich(VStream<PriceTick> ticks) {
        return VStreamPar.parEvalMap(ticks, concurrency, this::enrichOne);
    }
}
```

This creates two levels of concurrency that compose naturally:

```
   ┌──────────────── parEvalMap (across stream, 8 concurrent) ──────────────┐
   │                                                                        │
   │  ┌── Par.map2 ──────────────────────────────┐  (×8 in parallel)        │
   │  │                                          │                          │
   │  │  tick₁ ──┬── refData.lookup(AAPL)  ──┐   │                          │
   │  │          │                           ├──▶ EnrichedTick₁             │
   │  │          └── fxService.rate(USD) ────┘   │                          │
   │  │                                          │                          │
   │  └──────────────────────────────────────────┘                          │
   │                                                                        │
   │  Total in-flight tasks: up to 16 (8 ticks × 2 lookups each)            │
   └────────────────────────────────────────────────────────────────────────┘
```

~~~admonish tip title="The Imperative Alternative"
Without HKJ, the equivalent code typically looks like this:

```java
// Imperative: manual thread management, manual error propagation
ExecutorService pool = Executors.newFixedThreadPool(16);
List<EnrichedTick> results = new ArrayList<>();
Semaphore semaphore = new Semaphore(8);

for (PriceTick tick : mergedTicks) {
    semaphore.acquire();
    pool.submit(() -> {
        try {
            Future<Instrument> instrFuture = pool.submit(() -> refData.lookup(tick.symbol()));
            Future<BigDecimal> fxFuture = pool.submit(() -> fxService.rateToUsd(...));
            Instrument instrument = instrFuture.get();   // blocks platform thread
            BigDecimal fxRate = fxFuture.get();
            synchronized (results) {
                results.add(new EnrichedTick(tick, instrument, fxRate));
            }
        } catch (Exception e) {
            // What do we do here? Log and swallow? Rethrow? Cancel others?
        } finally {
            semaphore.release();
        }
    });
}
pool.shutdown();
pool.awaitTermination(30, TimeUnit.SECONDS);
// results are in completion order, not input order
// error handling is ad-hoc, resource cleanup is manual
```

The HKJ version, `VStreamPar.parEvalMap(ticks, 8, this::enrichOne)`, handles all of
this: bounded concurrency, input-order preservation, fail-fast error propagation, and
automatic resource cleanup.
~~~

~~~admonish note title="Design Decision: Why Par.map2 over sequential flatMap?"
`Par.map2` runs both lookups **simultaneously** on separate virtual threads via
`StructuredTaskScope`. The alternative, `instrumentTask.flatMap(inst -> fxTask.map(...))`,
runs them **sequentially**: the FX lookup waits for the instrument lookup to complete.
For two 50ms API calls, `Par.map2` takes ~50ms total vs ~100ms sequential.

The same pattern is available within for-comprehensions via `ForPath.par()`. For example, `ForPath.par(vtask1, vtask2).yield(...)` uses `Par.map2` under the hood for VTaskPath. See [ForPath Parallel Composition](../effect/forpath_comprehension.md#parallel-composition-with-par) for the full API.
~~~

---

## Step 4: Parallel Risk Assessment with `parEvalMapUnordered` {#step-4}

**Problem:** Each enriched tick needs a risk score. Risk calculations are independent:
the order doesn't matter for downstream aggregation, so we can maximise throughput by
emitting results in completion order.

**HKJ feature:** `VStreamPar.parEvalMapUnordered(stream, concurrency, f)`: same as
`parEvalMap`, but emits results as they complete rather than preserving input order.

```java
public class RiskPipeline {
    public VStream<RiskAssessment> assess(VStream<EnrichedTick> enrichedTicks) {
        return VStreamPar.parEvalMapUnordered(
            enrichedTicks, concurrency, calculator::assess);
    }
}
```

The risk calculator checks for:
- **Wide spreads** (above threshold), indicating possible liquidity issues
- **Volume spikes**, indicating unusual activity
- **Bid-ask inversions**, indicating data anomalies
- Returns a `VTask<RiskAssessment>` with a score (0.0–1.0) and descriptive flags

| Variant | Order | Latency | Best For |
|---------|-------|---------|----------|
| `parEvalMap` | Input order preserved | Waits for slowest in batch | Ordered data required downstream |
| `parEvalMapUnordered` | Completion order | Emits as each completes | Order irrelevant, throughput matters |

Both variants:
- Pull elements in batches of `concurrency`
- Fork each element's VTask via `StructuredTaskScope`
- Fail fast if any element throws (remaining tasks are cancelled)

~~~admonish note title="Design Decision: Why unordered for risk?"
Risk assessment feeds into `chunk()`, which groups consecutive elements into windows. The
**identity** of the ticks in a window matters (we need valid prices), but their **order**
within the window doesn't affect the VWAP calculation or anomaly detection. By using the
unordered variant, a fast risk assessment for tick₅ doesn't have to wait behind a slow
assessment for tick₃.
~~~

---

## Step 5: Windowed Aggregation with `VStream.chunk` {#step-5}

**Problem:** Individual ticks are too granular for anomaly detection. We need to aggregate
them into fixed-size windows and compute summary statistics like VWAP (Volume-Weighted
Average Price).

**HKJ features:**
- `VStream.chunk(size)`: groups consecutive elements into `List<A>` batches
- `VStream.flatMap(f)`: expands per-symbol groups into individual views

```java
public static VStream<AggregatedView> aggregate(
        VStream<RiskAssessment> assessed, int windowSize) {
    return assessed
        .chunk(windowSize)
        .flatMap(window -> VStream.fromList(computeViewsBySymbol(window)));
}
```

<div class="pipeline-diagram">
  <div class="pipeline-row pipeline-row-center">
    <span class="pipeline-node pn-blue">Window 1: t₁ t₂ t₃ t₄ t₅</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-label">computeView()</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-node pn-green">AggregatedView₁</span>
  </div>
  <div class="pipeline-row pipeline-row-center">
    <span class="pipeline-node pn-blue">Window 2: t₆ t₇ t₈ t₉ t₁₀</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-label">computeView()</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-node pn-green">AggregatedView₂</span>
  </div>
</div>

The `computeView` function computes:
- **VWAP**: `Sum(price_i × volume_i) / Sum(volume_i)`, the volume-weighted fair price
- **Best Bid**: highest bid in window
- **Best Ask**: lowest ask in window
- **Total Volume**: sum of volumes
- **Max Risk Score**: highest risk score in window

Chunks are produced lazily: elements within a chunk are eagerly collected, but the next
chunk is only produced when pulled.

~~~admonish note title="Design Decision: Why fixed-size chunks?"
Time-based windows (e.g. "all ticks in the last 5 seconds") are more common in production
but require wall-clock coordination. Fixed-size chunks demonstrate the `chunk` operator
cleanly and are deterministic in tests. The pattern is the same; the only difference is
the grouping predicate.
~~~

---

## Try It Yourself

~~~admonish example title="Exercises"
1. **Add a fourth exchange.** Create a `SimulatedExchangeFeed` for HKEX (Hong Kong),
   merge it with the others, and verify that ticks from all four exchanges appear in the
   merged stream.

2. **Tune concurrency.** Change the enrichment concurrency from 8 to 2, then to 16.
   Run the demo and observe the timing. What's the sweet spot for your machine?

3. **Custom risk flag.** Add a new risk check to `RiskCalculator` that flags ticks where
   the volume exceeds 5,000. Verify it appears in the `RiskAssessment` flags list.

4. **Variable window sizes.** Modify the pipeline to use a window size of 3 instead of 5.
   How does this affect the number of `AggregatedView` outputs for 50 input ticks?
~~~

---

~~~admonish tip title="See Also"
- [VStream](../monads/vstream.md): core pull-based streaming type
- [VStream Parallel Operations](../monads/vstream_parallel.md): deep dive on `parEvalMap` and `merge`
- [VTask](../monads/vtask_monad.md): virtual thread effect type
- [Structured Concurrency](../monads/vtask_scope.md): `Scope`, `Par`, and `StructuredTaskScope`
~~~

---

**Previous:** [Market Data Pipeline](examples_market_data.md) | **Next:** [Alerts and Resilience →](market_alerts.md)
