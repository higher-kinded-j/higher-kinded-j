// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.market.enrichment;

import org.higherkindedj.example.market.model.Instrument;
import org.higherkindedj.example.market.model.value.Symbol;
import org.higherkindedj.hkt.vtask.VTask;

/** Looks up reference data for a financial instrument. */
public interface ReferenceDataService {

  /**
   * Fetches instrument reference data for a given symbol.
   *
   * @param symbol the ticker symbol
   * @return a VTask producing the instrument data
   */
  VTask<Instrument> lookup(Symbol symbol);
}
