# Quick Reference

You've walked through nine pipeline stages, fourteen HKJ features, and enough concurrent
plumbing to make a thread pool blush. This page is the one you bookmark. Come back here
when you're adapting the pipeline to your own domain, when you can't remember whether you
need `parEvalMap` or `parEvalMapUnordered`, or when you just want to find the right source
file without scrolling through three chapters.

---

## HKJ Features Used

| Stage | HKJ Feature | What It Does |
|-------|-------------|--------------|
| **Generate** | `VStream.unfold` | Stateful infinite stream from a seed |
| **Merge** | `VStreamPar.merge` | Concurrent multi-source merging with virtual threads |
| **Enrich** | `Par.map2` | Run two VTasks in parallel, combine results |
| **Enrich** | `VStreamPar.parEvalMap` | Bounded parallel map preserving order |
| **Risk** | `VStreamPar.parEvalMapUnordered` | Bounded parallel map in completion order |
| **Window** | `VStream.chunk` | Group elements into fixed-size batches |
| **Aggregate** | `VStream.map` | Transform each chunk into a summary |
| **Detect** | `VStream.flatMap` | Each element produces zero or more results |
| **Dispatch** | `VStream.mapTask` | Apply effectful function per element |
| **Dispatch** | `Scope.allSucceed` | Fork/join with all-must-succeed semantics |
| **Throttle** | `VStreamThrottle.throttle` | Rate-limit emissions |
| **Recover** | `VStream.recoverWith` | Switch to fallback stream on failure |
| **Protect** | `CircuitBreaker` | Trip open after repeated failures |
| **Limit** | `VStream.take` | Safety valve: caps total elements |

---

## Which Operator Should I Pick?

The feature table tells you *what's available*. This section tells you *when to reach
for each one*.

**I need to run two independent tasks and combine their results.**
Use `Par.map2`. It forks both into virtual threads and joins. If either fails, the scope
cancels the other. This is what the enrichment stage does with its reference-data and
FX-rate lookups.

**I need to apply an effectful function to every element in a stream.**
If downstream order matters (e.g. enrichment feeds into risk assessment), use
`parEvalMap`. It processes elements concurrently but emits them in the original order.
If order does not matter and you want maximum throughput (e.g. independent risk
calculations), use `parEvalMapUnordered`. It emits results as they complete.

**I need to fan out to multiple channels and all must succeed.**
Use `Scope.allSucceed` with `fork` for each channel and `join` to wait. If any channel
fails, the scope cancels the rest. This is the alert dispatch pattern.

**I need to handle a source that might fail.**
Use `recoverWith` to switch to a fallback stream on error. Wrap the source in a
`CircuitBreaker` if you want to fail fast after repeated errors rather than retrying
a known-broken source.

**I need to combine multiple live sources into a single stream.**
Use `VStreamPar.merge`. It forks one virtual thread per source and interleaves elements
by arrival order. Do not use `concat`; that would drain the first source completely before
starting the second.

---

## Architecture Decision Summary

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Tick generation | `unfold` over `iterate` | Need `S → (A, S)` where state differs from output |
| Feed combination | `merge` over `concat` | Live feeds must interleave by arrival time |
| Lookup parallelism | `Par.map2` over `flatMap` | Two independent lookups: 50ms vs 100ms |
| Stream parallelism | `parEvalMap` for enrichment | Order matters for downstream processing |
| Risk parallelism | `parEvalMapUnordered` | Order irrelevant, maximise throughput |
| Windowing | `chunk` (fixed-size) | Deterministic, simple; time-based is a config change |
| Detection | `flatMap` over `map` + `filter` | Natural 0-to-many mapping per view |
| Alert dispatch | `Scope.allSucceed` | All-or-nothing: every channel must confirm |
| Feed failover | `recoverWith` over retry | Outages are sustained, not transient |

---

## Source Files

<div class="source-index">
  <div class="source-group sg-core">
    <div class="source-group-title">Pipeline Core</div>
    <ul class="source-group-files">
      <li><a href="https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/market/pipeline/MarketDataPipeline.java">MarketDataPipeline.java</a></li>
      <li><a href="https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/market/pipeline/PipelineConfig.java">PipelineConfig.java</a></li>
    </ul>
  </div>
  <div class="source-group sg-feed">
    <div class="source-group-title">Feed Layer</div>
    <ul class="source-group-files">
      <li><a href="https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/market/feed/SimulatedExchangeFeed.java">SimulatedExchangeFeed.java</a></li>
      <li><a href="https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/market/feed/FeedMerger.java">FeedMerger.java</a></li>
    </ul>
  </div>
  <div class="source-group sg-process">
    <div class="source-group-title">Processing Stages</div>
    <ul class="source-group-files">
      <li><a href="https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/market/enrichment/TickEnricher.java">TickEnricher.java</a></li>
      <li><a href="https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/market/risk/RiskPipeline.java">RiskPipeline.java</a></li>
      <li><a href="https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/market/risk/RiskCalculator.java">RiskCalculator.java</a></li>
      <li><a href="https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/market/aggregation/WindowAggregator.java">WindowAggregator.java</a></li>
    </ul>
  </div>
  <div class="source-group sg-alert">
    <div class="source-group-title">Alert Layer</div>
    <ul class="source-group-files">
      <li><a href="https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/market/alert/AnomalyDetector.java">AnomalyDetector.java</a></li>
      <li><a href="https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/market/alert/AlertDispatcher.java">AlertDispatcher.java</a></li>
    </ul>
  </div>
  <div class="source-group sg-resil">
    <div class="source-group-title">Resilience</div>
    <ul class="source-group-files">
      <li><a href="https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/market/resilience/FeedResilience.java">FeedResilience.java</a></li>
    </ul>
  </div>
  <div class="source-group sg-model">
    <div class="source-group-title">Domain Model</div>
    <ul class="source-group-files">
      <li><a href="https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/market/model/PriceTick.java">PriceTick.java</a></li>
      <li><a href="https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/market/model/EnrichedTick.java">EnrichedTick.java</a></li>
      <li><a href="https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/market/model/RiskAssessment.java">RiskAssessment.java</a></li>
      <li><a href="https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/market/model/AggregatedView.java">AggregatedView.java</a></li>
      <li><a href="https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/market/model/Alert.java">Alert.java</a></li>
    </ul>
  </div>
  <div class="source-group sg-demo">
    <div class="source-group-title">Demo and Tests</div>
    <ul class="source-group-files">
      <li><a href="https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/market/runner/MarketDataDemo.java">MarketDataDemo.java</a></li>
      <li><a href="https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/market/MarketDataPipelineTest.java">MarketDataPipelineTest.java</a></li>
    </ul>
  </div>
</div>

---

## Running

### Demo (all 9 steps with output)

```bash
./gradlew :hkj-examples:run \
  -PmainClass=org.higherkindedj.example.market.runner.MarketDataDemo
```

### Tests

```bash
./gradlew :hkj-examples:test --tests "*.MarketDataPipelineTest"
```

---

## Related Documentation

| Topic | Link |
|-------|------|
| **VStream** | [Core pull-based streaming type](../monads/vstream.md) |
| **VStream Parallel** | [parEvalMap, merge, and concurrency](../monads/vstream_parallel.md) |
| **VStream Advanced** | [recoverWith, recover, onFinalize](../monads/vstream_advanced.md) |
| **VTask** | [Virtual thread effect type](../monads/vtask_monad.md) |
| **Structured Concurrency** | [Scope, Par, StructuredTaskScope](../monads/vtask_scope.md) |
| **Resilience** | [Retry, Circuit Breaker, Bulkhead, Saga](../resilience/ch_intro.md) |
| **Order Processing** | [Another complete application example](examples_order.md) |

---

<div style="text-align: center; margin: 1.5em 0;">
  <a href="https://xkcd.com/2347/" target="_blank" rel="noopener">
    <img src="https://imgs.xkcd.com/comics/dependency.png"
         alt="XKCD 2347: Dependency — 'Someday ImageMagick will finally break for good and we&#39;ll have a long period of scrambling as we try to reassemble civilization from the rubble.'"
         style="max-width: 400px; width: 100%;" />
  </a>
  <br/><small><em>XKCD 2347 — Dependency. Now you know why Step 8 exists.</em></small>
</div>

---

The entire pipeline (nine stages, fourteen features, three exchange feeds) composes
into a single expression that a new team member can read top to bottom. Every design
decision is explicit in the code and documented in the table above. When you adapt this
to your own domain, you will not be copying a framework; you will be reusing a vocabulary
of composable operations that each do one thing well.

That's the point of higher-kinded Java: not the type theory, but the fact that your
Monday morning is a little less terrifying.

---

**Previous:** [Alerts and Resilience](market_alerts.md) | **Up:** [Market Data Pipeline](examples_market_data.md)
