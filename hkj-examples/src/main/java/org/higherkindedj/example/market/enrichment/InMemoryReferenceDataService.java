// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.market.enrichment;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.higherkindedj.example.market.model.Instrument;
import org.higherkindedj.example.market.model.value.Symbol;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * In-memory reference data service with simulated lookup latency.
 *
 * <p>Pre-loaded with common financial instruments for demo purposes.
 */
public class InMemoryReferenceDataService implements ReferenceDataService {

  private final Map<Symbol, Instrument> instruments = new ConcurrentHashMap<>();
  private final long lookupDelayMs;

  public InMemoryReferenceDataService(long lookupDelayMs) {
    this.lookupDelayMs = lookupDelayMs;
    loadDefaults();
  }

  public InMemoryReferenceDataService() {
    this(2); // 2ms simulated latency
  }

  private void loadDefaults() {
    add(new Instrument(new Symbol("AAPL"), "Apple Inc.", "Equity", 100));
    add(new Instrument(new Symbol("GOOGL"), "Alphabet Inc.", "Equity", 100));
    add(new Instrument(new Symbol("MSFT"), "Microsoft Corp.", "Equity", 100));
    add(new Instrument(new Symbol("TSLA"), "Tesla Inc.", "Equity", 100));
    add(new Instrument(new Symbol("AMZN"), "Amazon.com Inc.", "Equity", 100));
  }

  public void add(Instrument instrument) {
    instruments.put(instrument.symbol(), instrument);
  }

  @Override
  public VTask<Instrument> lookup(Symbol symbol) {
    return VTask.of(
        () -> {
          if (lookupDelayMs > 0) {
            Thread.sleep(lookupDelayMs);
          }
          Instrument inst = instruments.get(symbol);
          if (inst == null) {
            throw new RuntimeException("Unknown instrument: " + symbol);
          }
          return inst;
        });
  }
}
