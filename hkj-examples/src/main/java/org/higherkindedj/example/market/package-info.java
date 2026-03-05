/**
 * Market Data Aggregator — Virtual Threads Capstone Example.
 *
 * <p>A progressive tutorial building a real-time market data processing pipeline that demonstrates
 * the full power of Higher-Kinded-J's virtual thread integration. The pipeline ingests simulated
 * price feeds from multiple exchanges, enriches ticks with reference data, applies risk
 * calculations, detects anomalies, and dispatches alerts.
 *
 * <p><b>HKJ features demonstrated:</b>
 *
 * <ul>
 *   <li>{@link org.higherkindedj.hkt.vstream.VStream} — Lazy, pull-based streaming
 *   <li>{@link org.higherkindedj.hkt.vstream.VStreamPar} — Concurrent stream merging and parallel
 *       processing
 *   <li>{@link org.higherkindedj.hkt.vstream.VStreamThrottle} — Rate-limited emission
 *   <li>{@link org.higherkindedj.hkt.vtask.Par} — Parallel combinator (map2 for concurrent lookups)
 *   <li>{@link org.higherkindedj.hkt.vtask.Scope} — Structured concurrency for alert dispatch
 *   <li>{@link org.higherkindedj.hkt.resilience.CircuitBreaker} — Feed failure protection
 *   <li>{@link org.higherkindedj.hkt.resilience.RetryPolicy} — Retry with backoff
 * </ul>
 *
 * <p>Run the demo with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.market.runner.MarketDataDemo}
 *
 * @see org.higherkindedj.example.market.runner.MarketDataDemo
 */
@NullMarked
package org.higherkindedj.example.market;

import org.jspecify.annotations.NullMarked;
