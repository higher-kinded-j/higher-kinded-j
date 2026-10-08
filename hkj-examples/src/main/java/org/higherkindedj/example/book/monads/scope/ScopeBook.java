// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.monads.scope;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.resilience.Retry;
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/monads/vtask_scope.html">Structured Concurrency with
 * Scope</a> page. The page {@code {{#include}}}s the anchored region, so it cannot drift from the
 * API, and the build runs {@code main} to check the output the region claims.
 *
 * <p>The page's other blocks call services, which a runnable example cannot do. They keep {@code
 * <!-- verify -->} markers instead, so they are still compiled.
 */
public final class ScopeBook {

  private ScopeBook() {}

  public static void main(String[] args) {
    // ANCHOR: run_again
    AtomicInteger quoteCalls = new AtomicInteger();
    VTask<String> quote =
        VTask.of(
            () -> {
              if (quoteCalls.incrementAndGet() == 1) {
                throw new IllegalStateException("quote service busy");
              }
              return "quote";
            });
    VTask<List<String>> prices =
        Scope.<String>allSucceed().fork(quote).fork(VTask.succeed("rates")).join();

    List<String> retried = Retry.retryTask(prices, RetryPolicy.fixed(3, Duration.ZERO)).run();
    // [quote, rates]
    List<String> runAgain = prices.run();
    // [quote, rates]
    // ANCHOR_END: run_again
    System.out.println(retried);
    System.out.println(runAgain);
  }
}
