# Market Data Pipeline: Virtual Threads Capstone

> *"The system that would not be watched, would not be trusted."*
>
> -- Gary Stevenson

Stevenson was talking about financial markets, but the same principle applies to the
software that processes them. A pipeline you can't observe, can't compose, and can't
reason about is one you'll never trust in production. That's why this capstone example
doesn't just *work*; it's built so you can see exactly what each stage does, swap any
piece out, and know that failures will not hide.

~~~admonish info title="What You'll Learn"
- How to build a **complete, concurrent data pipeline** from feed generation to alert dispatch
- Progressive introduction of fourteen HKJ features, each solving a concrete problem
- Design principles that make the pipeline composable, safe, and resilient
- When and why to choose each concurrency primitive
~~~

~~~admonish example title="Run It Now"
```bash
./gradlew :hkj-examples:run \
  -PmainClass=org.higherkindedj.example.market.runner.MarketDataDemo
```
[View source on GitHub](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-examples/src/main/java/org/higherkindedj/example/market)
~~~

---

## The Scenario

It's Monday morning. You're the lead engineer on the market data team at a mid-size trading
firm. Three exchange feeds (NYSE, LSE, and the Tokyo Stock Exchange) push tens of thousands
of price ticks per second into your system. Each tick is raw: just a symbol, a bid, an ask,
a volume, and a timestamp. Before anyone can act on this data, your pipeline must:

1. **Merge** the feeds into a single stream in true arrival order
2. **Enrich** each tick with instrument metadata and an FX conversion rate
3. **Assess risk**: flag ticks with anomalous spreads, volume spikes, or inversions
4. **Aggregate** into time windows, computing VWAP and other summary statistics
5. **Detect anomalies**: compare aggregated views against thresholds
6. **Dispatch alerts** to multiple channels simultaneously (log, email, webhook)
7. **Protect** the pipeline with rate limiting, circuit breakers, and feed failover

The legacy system handles this with a tangle of thread pools, `CompletableFuture` chains,
manual `try-catch` propagation, and `synchronized` blocks that nobody dares refactor.
You've been asked to rebuild it.

Here's what the new pipeline looks like with HKJ:

```java
List<Alert> alerts = pipeline.fullPipeline().toList().run();
```

One line to *run* it. The rest is composition.

---

## Architecture Overview

<div class="pipeline-diagram">
  <div class="pipeline-row pipeline-row-center">
    <span class="pipeline-node pn-green">NYSE</span>
    <span class="pipeline-node pn-green">LSE</span>
    <span class="pipeline-node pn-green">TSE</span>
  </div>
  <div class="pipeline-arrow-down">▼ ▼ ▼</div>
  <div class="pipeline-row pipeline-row-center">
    <span class="pipeline-node pn-blue">merge</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-node pn-blue">take(n)</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-node pn-orange">enrich</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-node pn-orange">risk assess</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-node pn-red">chunk(5)</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-node pn-red">detect</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-node pn-purple">dispatch</span>
  </div>
  <div class="pipeline-arrow-down">▼ ▼ ▼</div>
  <div class="pipeline-row pipeline-row-center">
    <span class="pipeline-node pn-purple">Log</span>
    <span class="pipeline-node pn-purple">Email</span>
    <span class="pipeline-node pn-purple">Webhook</span>
  </div>
</div>

Each stage is a **lazy stream transformation**. Nothing executes until the terminal
`.toList().run()` drives evaluation from the end. Data flows left-to-right through
composable operators, each one a single method call.

### What Makes This Different

| Concern | Legacy Approach | HKJ Approach |
|---------|----------------|--------------|
| **Concurrency** | Thread pools, `ExecutorService`, manual task submission | Virtual threads via `Par.map2`, `parEvalMap`, `Scope` |
| **Error handling** | Nested `try-catch`, `CompletableFuture.exceptionally` | Fail-fast propagation, `recoverWith` for failover |
| **Backpressure** | Reactive streams protocol (`request(n)`) | Pull-based: consumer drives evaluation naturally |
| **Resource cleanup** | `finally` blocks, easy to miss | Structured concurrency: scope cancels all children |
| **Composition** | Callback chains, hard to reorder stages | Each stage is a function `VStream<A> → VStream<B>` |

---

## Design Principles

These four principles guide every design decision in the pipeline. Each step in the
walkthrough calls back to them.

### Lazy Composition

Every operator returns a **description** of work, not a result. The pipeline definition
allocates no threads, opens no connections, and generates no ticks. Execution only begins
when a terminal operation like `.toList().run()` starts pulling.

This means you can build, test, and compose pipeline fragments independently. Assemble
the full pipeline from smaller pieces that each work in isolation.

### Bounded Concurrency Without Backpressure

VStream is pull-based: the consumer drives evaluation by requesting elements. The parallel
operations add bounded concurrency on top of this pull model. At most `concurrency` elements
are in flight at any time. Because virtual threads block cheaply, no explicit backpressure
protocol is needed; if the consumer is slow, the producer simply blocks until the consumer
pulls the next element.

### Structured Concurrency Throughout

All parallelism in the pipeline uses `StructuredTaskScope`:
- `Par.map2` forks two tasks within a scope
- `parEvalMap` forks a batch of tasks within a scope
- `Scope.allSucceed` forks alert channels within a scope
- `VStreamPar.merge` forks one consumer per source within a scope

If any task fails, the scope cancels all remaining tasks. No orphaned threads, no leaked
resources.

### Fail-Fast Error Handling

Errors propagate immediately at every level:
- If any enrichment lookup fails, the entire batch fails
- If any risk calculation throws, the pipeline surfaces the error
- If any alert channel fails, the dispatch for that alert fails
- If any source stream errors during merge, the merged stream fails

No silent failures, no swallowed exceptions. Recovery is explicit (`recoverWith`), not
accidental.

<div style="text-align: center; margin: 1.5em 0;">
  <a href="https://xkcd.com/2200/" target="_blank" rel="noopener">
    <img src="https://imgs.xkcd.com/comics/unreachable_state.png"
         alt="XKCD 2200: Unreachable State — 'ERROR: We&#39;ve reached an unreachable state. Anything is possible. The limits were in our heads all along. Follow your dreams.'"
         style="max-width: 350px; width: 100%;" />
  </a>
  <br/><small><em>XKCD 2200 — Unreachable State. The pipeline disagrees.</em></small>
</div>

---

## The Domain Model

The pipeline operates on a progression of record types. Each stage adds a layer of
information:

<div class="pipeline-diagram">
  <div class="pipeline-row pipeline-row-center">
    <span class="pipeline-node pn-green">PriceTick</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-label">enrich</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-node pn-blue">EnrichedTick</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-label">assess</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-node pn-orange">RiskAssessment</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-label">aggregate</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-node pn-red">AggregatedView</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-label">detect</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-node pn-purple">Alert</span>
  </div>
</div>

```java
// Raw tick from an exchange
record PriceTick(Symbol symbol, Price bid, Price ask, Volume volume,
                 Exchange exchange, Instant timestamp) {
    Price mid()    { ... }  // (bid + ask) / 2
    Price spread() { ... }  // ask - bid
}

// Tick enriched with instrument metadata and FX rate
record EnrichedTick(PriceTick tick, Instrument instrument, BigDecimal fxRate) {
    Price midInUsd() { ... }  // mid * fxRate
}

// Tick with risk assessment attached
record RiskAssessment(EnrichedTick tick, double riskScore, List<String> flags) {
    boolean isHighRisk() { return riskScore > 0.7; }
}

// Aggregate view over a window of ticks
record AggregatedView(Symbol symbol, Price vwap, Price bestBid, Price bestAsk,
                      Volume totalVolume, int tickCount, double maxRiskScore) {
    Price spread() { return bestAsk.minus(bestBid); }
}

// Alert raised when an anomaly is detected
record Alert(Symbol symbol, Severity severity, String message, Instant timestamp) {
    enum Severity { CRITICAL, WARNING, INFO }
}
```

Each record is immutable. Transformation flows forward only; no stage mutates a previous
stage's output.

---

## HKJ Feature Map

Every HKJ feature used in the pipeline, mapped to the stage where it appears:

| Stage | HKJ Feature | What It Does | Page |
|-------|-------------|--------------|------|
| **Generate** | `VStream.unfold` | Stateful infinite stream from a seed | [Building](market_building.md#step-1) |
| **Merge** | `VStreamPar.merge` | Concurrent multi-source merging | [Building](market_building.md#step-2) |
| **Enrich** | `Par.map2` | Run two VTasks in parallel, combine | [Building](market_building.md#step-3) |
| **Enrich** | `VStreamPar.parEvalMap` | Bounded parallel map, order preserved | [Building](market_building.md#step-3) |
| **Risk** | `parEvalMapUnordered` | Bounded parallel map, completion order | [Building](market_building.md#step-4) |
| **Window** | `VStream.chunk` | Group elements into fixed-size batches | [Building](market_building.md#step-5) |
| **Aggregate** | `VStream.map` | Transform each chunk into a summary | [Building](market_building.md#step-5) |
| **Detect** | `VStream.flatMap` | Each element produces zero or more results | [Alerts](market_alerts.md#step-6) |
| **Dispatch** | `VStream.mapTask` | Apply effectful function per element | [Alerts](market_alerts.md#step-6) |
| **Dispatch** | `Scope.allSucceed` | Fork/join, all channels must succeed | [Alerts](market_alerts.md#step-6) |
| **Throttle** | `VStreamThrottle.throttle` | Rate-limit emissions | [Alerts](market_alerts.md#step-7) |
| **Recover** | `VStream.recoverWith` | Switch to fallback stream on failure | [Alerts](market_alerts.md#step-8) |
| **Protect** | `CircuitBreaker` | Trip open after repeated failures | [Alerts](market_alerts.md#step-8) |
| **Limit** | `VStream.take` | Safety valve: caps total elements | [Building](market_building.md#step-2) |

---

## Chapter Contents

1. **[Building the Pipeline](market_building.md)**: Steps 1–5, covering tick generation, feed merging,
   concurrent enrichment, risk assessment, and windowed aggregation
2. **[Alerts and Resilience](market_alerts.md)**: Steps 6–9, covering anomaly detection, multi-channel
   dispatch, rate limiting, circuit breakers, and the full end-to-end pipeline
3. **[Quick Reference](market_reference.md)**: Feature summary, source file index, and
   running instructions

---

**Next:** [Building the Pipeline →](market_building.md)
