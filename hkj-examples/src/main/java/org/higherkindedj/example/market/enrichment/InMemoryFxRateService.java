// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.market.enrichment;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * In-memory FX rate service with simulated lookup latency.
 *
 * <p>Pre-loaded with common currency pairs for demo purposes.
 */
public class InMemoryFxRateService implements FxRateService {

  private final Map<String, BigDecimal> rates = new ConcurrentHashMap<>();
  private final long lookupDelayMs;

  public InMemoryFxRateService(long lookupDelayMs) {
    this.lookupDelayMs = lookupDelayMs;
    loadDefaults();
  }

  public InMemoryFxRateService() {
    this(2); // 2ms simulated latency
  }

  private void loadDefaults() {
    rates.put("USD", BigDecimal.ONE);
    rates.put("GBP", new BigDecimal("1.27"));
    rates.put("JPY", new BigDecimal("0.0067"));
    rates.put("EUR", new BigDecimal("1.08"));
  }

  public void setRate(String currency, BigDecimal rate) {
    rates.put(currency, rate);
  }

  @Override
  public VTask<BigDecimal> rateToUsd(String sourceCurrency) {
    return VTask.of(
        () -> {
          if (lookupDelayMs > 0) {
            Thread.sleep(lookupDelayMs);
          }
          BigDecimal rate = rates.get(sourceCurrency);
          if (rate == null) {
            throw new RuntimeException("Unknown currency: " + sourceCurrency);
          }
          return rate;
        });
  }
}
