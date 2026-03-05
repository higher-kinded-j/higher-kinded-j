// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.market.model;

import java.time.Duration;
import java.util.Objects;

/**
 * An exchange that provides price feeds.
 *
 * @param name the exchange name (e.g. "NYSE", "LSE", "TSE")
 * @param region the geographic region
 * @param currency the native currency for this exchange
 * @param typicalLatency the typical simulated latency for ticks from this exchange
 */
public record Exchange(String name, String region, String currency, Duration typicalLatency) {
  public Exchange {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(region, "region must not be null");
    Objects.requireNonNull(currency, "currency must not be null");
    Objects.requireNonNull(typicalLatency, "typicalLatency must not be null");
  }

  public static final Exchange NYSE = new Exchange("NYSE", "US", "USD", Duration.ofMillis(5));
  public static final Exchange LSE = new Exchange("LSE", "UK", "GBP", Duration.ofMillis(15));
  public static final Exchange TSE = new Exchange("TSE", "JP", "JPY", Duration.ofMillis(25));
}
