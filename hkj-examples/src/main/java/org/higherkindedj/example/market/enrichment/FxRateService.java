// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.market.enrichment;

import java.math.BigDecimal;
import org.higherkindedj.hkt.vtask.VTask;

/** Provides foreign exchange rates for currency conversion. */
public interface FxRateService {

  /**
   * Fetches the exchange rate from the source currency to USD.
   *
   * @param sourceCurrency the source currency code (e.g. "GBP", "JPY")
   * @return a VTask producing the conversion rate
   */
  VTask<BigDecimal> rateToUsd(String sourceCurrency);
}
