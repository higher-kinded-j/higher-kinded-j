# Alerts and Resilience

> *"The future is already here. It's just not evenly distributed."*
>
> -- William Gibson

Neither is failure. In a live system, one exchange drops out while the others keep
streaming, one alert channel times out while the rest succeed, one batch of ticks arrives
late while the next is already being processed. The stages you'll build here deal with
that unevenness: detecting anomalies, dispatching alerts, throttling output, and
recovering from failures that are always somewhere in the pipeline, just not evenly
distributed.

~~~admonish info title="What You'll Build"
In this chapter you'll complete the pipeline with anomaly detection, multi-channel alert
dispatch, rate limiting, resilient feed failover, and the final end-to-end composition.
These are the stages where the pipeline stops being a data transformation and starts
*acting on* the data.
~~~

---

## Step 6: Anomaly Detection and Alert Dispatch {#step-6}

**Problem:** Each aggregated window may produce zero, one, or multiple alerts depending on
the anomaly rules. Then each alert must be dispatched to all notification channels
concurrently (log, email, webhook), and all channels must succeed.

**HKJ features:**
- `VStream.flatMap(f)`: each element maps to a sub-stream; results are concatenated
- `VStream.mapTask(f)`: applies an effectful function per element
- `Scope.allSucceed()` with `fork` and `join`: concurrent fan-out requiring all
  channels to succeed

### Detection with `flatMap`

```java
public VStream<Alert> detect(VStream<AggregatedView> views) {
    return views.flatMap(view -> {
        List<Alert> alerts = checkView(view);
        return alerts.isEmpty() ? VStream.empty() : VStream.fromList(alerts);
    });
}
```

<div class="pipeline-diagram">
  <div class="pipeline-row">
    <span class="pipeline-node pn-blue">AggView₁</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-label">checkView()</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-node pn-red">Alert, Alert</span>
    <span class="pipeline-arrow">↘</span>
  </div>
  <div class="pipeline-row">
    <span class="pipeline-node pn-blue">AggView₂</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-label">checkView()</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-node pn-red">Alert</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-node pn-purple" style="font-weight:700">flatMap flattens</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-node pn-red">VStream&lt;Alert&gt; — 5 total</span>
  </div>
  <div class="pipeline-row">
    <span class="pipeline-node pn-blue">AggView₃</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-label">checkView()</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-node pn-grey-dashed">(empty)</span>
    <span class="pipeline-arrow" style="opacity:0.4">⤳</span>
    <span class="pipeline-label" style="opacity:0.4">skipped</span>
  </div>
  <div class="pipeline-row">
    <span class="pipeline-node pn-blue">AggView₄</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-label">checkView()</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-node pn-red">Alert, Alert</span>
    <span class="pipeline-arrow">↗</span>
  </div>
</div>

`flatMap` is the right choice here because each view can produce a **variable number** of
alerts. `map` would give `VStream<List<Alert>>`; `flatMap` flattens to `VStream<Alert>`.

The anomaly rules:
- **High risk score** (> 0.5) → `WARNING` or `CRITICAL`
- **Wide spread** (> 1%) → `WARNING`
- **High volume** (> threshold) → `INFO`

### Dispatch with `mapTask` + `Scope.allSucceed`

```java
public VStream<Alert> dispatch(VStream<Alert> alerts) {
    return alerts.mapTask(alert -> dispatchOne(alert).map(u -> alert));
}

public VTask<Unit> dispatchOne(Alert alert) {
    Scope<Unit, List<Unit>> scope = Scope.allSucceed();
    for (AlertChannel channel : channels) {
        scope = scope.fork(VTask.exec(() -> channel.handler().accept(alert)));
    }
    return scope.join().map(results -> {
        dispatchedAlerts.add(alert);
        return Unit.INSTANCE;
    });
}
```

```
  Scope.allSucceed — fan-out to all channels
  ════════════════════════════════════════════

  Alert₁ ──▶ Scope.allSucceed()
              ├── fork ──▶ logChannel.accept(alert)     ──▶ ✓
              ├── fork ──▶ emailChannel.accept(alert)   ──▶ ✓
              └── fork ──▶ webhookChannel.accept(alert) ──▶ ✓
              │
              join() ──▶ all succeeded ──▶ Alert₁ passes through
```

`Scope.allSucceed` uses structured concurrency: all forked tasks run within a
`StructuredTaskScope`, and if **any** channel fails, the entire dispatch fails fast and
remaining channels are cancelled.

~~~admonish tip title="The Imperative Alternative"
The manual version requires careful coordination:

```java
// Imperative: manual fan-out, manual error collection
ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
List<Future<?>> futures = new ArrayList<>();
for (AlertChannel channel : channels) {
    futures.add(pool.submit(() -> channel.handler().accept(alert)));
}
List<Exception> errors = new ArrayList<>();
for (Future<?> f : futures) {
    try {
        f.get(5, TimeUnit.SECONDS);
    } catch (Exception e) {
        errors.add(e);
        // Should we cancel the others? How?
    }
}
if (!errors.isEmpty()) {
    // Which error do we propagate? What about partial dispatch?
    throw errors.get(0);
}
```

With `Scope.allSucceed`, fail-fast cancellation is automatic: the first failure cancels
all other forked tasks within the scope. No manual error collection or cancellation logic.
~~~

~~~admonish note title="Design Decision: Why allSucceed over anySucceed?"
If the log channel fails but email succeeds, is that acceptable? In this pipeline, **no**
: we want all-or-nothing dispatch per alert. `Scope.allSucceed` enforces this. If you
needed "best effort" delivery (succeed if at least one channel works), you'd use
`Scope.anySucceed()` instead, or `recover` on individual channel tasks.
~~~

---

## Step 7: Rate-Limited Publishing with `VStreamThrottle` {#step-7}

**Problem:** Downstream consumers have rate limits. The webhook endpoint allows at most
100 requests per second. We need to emit at most N elements per time window to avoid
overwhelming them.

**HKJ feature:** `VStreamThrottle.throttle(stream, maxElements, window)`: bounds the
emission rate of a stream.

```java
VStream<PriceTick> throttled =
    VStreamThrottle.throttle(feed.ticks(), 3, Duration.ofMillis(100));
```

```
  throttle(stream, 3, 100ms)
  ══════════════════════════

  Time  0ms          100ms         200ms         300ms
  ─────┬─────────────┬─────────────┬─────────────┬─────
       │ t₁ t₂ t₃    │ t₄ t₅ t₆    │ t₇ t₈ t₉    │ ...
       │ ←─ 3 max ─→ │ ←─ 3 max ─→ │ ←─ 3 max ─→ │
       │ per window  │  per window │  per window │
```

This allows at most 3 elements per 100ms window, adding controlled delays as needed.
Virtual threads make the delay cheap: the throttled thread simply sleeps without consuming
a platform thread.

---

## Step 8: Resilient Feeds with `CircuitBreaker` + `recoverWith` {#step-8}

**Problem:** Exchange feeds fail. Network partitions, exchange maintenance windows,
rate-limiting by the exchange itself. When NYSE goes down, the pipeline shouldn't crash;
it should fail over to a backup feed.

**HKJ features:**
- `CircuitBreaker`: trips open after repeated failures, preventing cascade
- `VStream.recoverWith(error -> fallbackStream)`: switches to a fallback stream when the
  first pull fails

```java
public class FeedResilience {
    public VStream<PriceTick> withFallback(
            VStream<PriceTick> primary, VStream<PriceTick> fallback) {
        return primary.recoverWith(error -> fallback);
    }
}
```

<div class="pipeline-diagram">
  <div class="pipeline-row pipeline-row-center">
    <span class="pipeline-node pn-blue">Primary Feed — NYSE</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-node pn-grey">pull()</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-node pn-fail">Connection Refused</span>
  </div>
  <div class="pipeline-arrow-down">▼</div>
  <div class="pipeline-row pipeline-row-center">
    <span class="pipeline-node pn-warn">recoverWith</span>
  </div>
  <div class="pipeline-arrow-down">▼</div>
  <div class="pipeline-row pipeline-row-center">
    <span class="pipeline-node pn-blue">Fallback Feed — LSE</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-node pn-grey">pull()</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-node pn-ok">tick₁, tick₂, ...</span>
  </div>
</div>

~~~admonish note title="recoverWith Semantics"
`recoverWith` wraps the **first pull** of the stream. If the primary feed fails to connect
(first pull throws), recovery kicks in and the fallback stream takes over entirely. This
models connection-failure scenarios, the most common failure mode for exchange feeds.

For per-element recovery (mid-stream errors), use `recover(error -> recoveryValue)` instead,
which recursively wraps every pull in the stream.
~~~

~~~admonish note title="Design Decision: Why recoverWith over retry?"
Retry is the right strategy for **transient** failures (a single dropped packet, a brief
overload). But exchange outages are typically **sustained**; retrying the same dead
endpoint wastes time. `recoverWith` gives immediate failover: the moment the primary fails,
the fallback takes over with zero retry delay. For transient failures within a feed, the
`CircuitBreaker` handles them at a lower level, tripping open after a configurable failure
threshold.
~~~

---

## Step 9: Full Pipeline, End-to-End Integration {#step-9}

All nine stages compose into a single lazy pipeline. Each method returns a `VStream` that
describes the transformation. Nothing executes until `.toList().run()`.

```java
public class MarketDataPipeline {
    public VStream<PriceTick> mergedTicks() {
        return FeedMerger.merge(feeds).take(config.maxTicks());
    }

    public VStream<EnrichedTick> enrichedTicks() {
        return enricher.enrich(mergedTicks());
    }

    public VStream<RiskAssessment> assessedTicks() {
        return riskPipeline.assess(enrichedTicks());
    }

    public VStream<AggregatedView> aggregatedViews() {
        return WindowAggregator.aggregate(assessedTicks(), config.windowSize());
    }

    public VStream<Alert> alerts() {
        return anomalyDetector.detect(aggregatedViews());
    }

    public VStream<Alert> fullPipeline() {
        return alertDispatcher.dispatch(alerts());
    }
}
```

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
    <span class="pipeline-node pn-blue">take(50)</span>
  </div>
  <div class="pipeline-arrow-down">▼</div>
  <div class="pipeline-row pipeline-row-center">
    <span class="pipeline-node pn-orange">enrich — Par.map2 ×8</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-node pn-orange">risk — parEvalMapUnordered ×4</span>
  </div>
  <div class="pipeline-arrow-down">▼</div>
  <div class="pipeline-row pipeline-row-center">
    <span class="pipeline-node pn-red">chunk(5) — map</span>
    <span class="pipeline-arrow">→</span>
    <span class="pipeline-node pn-red">detect — flatMap 0..n</span>
  </div>
  <div class="pipeline-arrow-down">▼</div>
  <div class="pipeline-row pipeline-row-center">
    <span class="pipeline-node pn-purple">dispatch — Scope.allSucceed</span>
  </div>
  <div class="pipeline-arrow-down">▼ ▼ ▼</div>
  <div class="pipeline-row pipeline-row-center">
    <span class="pipeline-node pn-purple">Log</span>
    <span class="pipeline-node pn-purple">Email</span>
    <span class="pipeline-node pn-purple">Webhook</span>
  </div>
</div>

Usage:

```java
PipelineConfig config = new PipelineConfig(
    8,   // enrichment concurrency
    4,   // risk concurrency
    5,   // window size
    50   // max ticks (safety valve)
);

MarketDataPipeline pipeline = new MarketDataPipeline(
    List.of(nyse, lse, tse), enricher, riskPipeline,
    anomalyDetector, alertDispatcher, config);

List<Alert> alerts = pipeline.fullPipeline().toList().run();
// Drives the entire pipeline: all stages execute lazily
```

Notice how `PipelineConfig` exposes the key tuning knobs without exposing implementation
details. You can change concurrency levels, window sizes, and element limits without
touching any stage code.

~~~admonish note title="Design Decision: Why lazy end-to-end?"
The full pipeline is a chain of method calls that return `VStream` values. No intermediate
lists are materialised, no threads are started, no connections are opened. This has three
benefits:
1. **Testability.** You can call `pipeline.assessedTicks().take(3).toList().run()` to test
   just the first three stages in isolation.
2. **Composability.** Add a new stage by wrapping an existing `VStream` method.
3. **Resource efficiency.** Only elements that are actually consumed get processed; if you
   `take(10)` from a merged feed of millions, only ~10 ticks are generated
~~~

---

## Try It Yourself

~~~admonish example title="Exercises"
1. **Add a new alert channel.** Create a `SlackChannel` that formats alerts as Slack
   webhook payloads. Add it to the dispatcher's channel list and verify it receives
   alerts alongside the existing channels.

2. **Change anomaly thresholds.** Lower the risk score threshold in `AnomalyDetector`
   from 0.5 to 0.3. How does this affect the number of alerts generated? What about 0.1?

3. **Combine resilience patterns.** Wrap the primary feed in both a `CircuitBreaker` *and*
   `recoverWith`. Configure the breaker to trip after 3 failures. Simulate failures and
   verify the fallback activates.

4. **Partial pipeline testing.** Write a test that exercises only steps 1-5 (up to
   aggregation) by calling `pipeline.aggregatedViews().take(4).toList().run()`. Verify
   the VWAP calculations are correct.
~~~

---

~~~admonish tip title="See Also"
- [Resilience Patterns](../resilience/ch_intro.md): Retry, Circuit Breaker, Bulkhead, Saga
- [VStream Advanced Features](../monads/vstream_advanced.md): `recoverWith`, `recover`, `onFinalize`
- [Structured Concurrency](../monads/vtask_scope.md): `Scope.allSucceed`, `Scope.anySucceed`
- [Quick Reference](market_reference.md): feature summary table and source file index
~~~

---

**Previous:** [Building the Pipeline](market_building.md) | **Next:** [Quick Reference →](market_reference.md)
