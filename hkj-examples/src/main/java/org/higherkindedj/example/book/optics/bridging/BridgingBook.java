// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.bridging;

import java.util.List;
import org.higherkindedj.example.optics.bridge.CompanyBridge;
import org.higherkindedj.example.optics.bridge.domain.Company;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/focus_external_bridging.html">Focus DSL
 * with External Libraries</a> page, where it uses the bridge. The page {@code {{#include}}}s the
 * anchored region, and {@code BridgingBookTest} holds the values its comments claim. The bridge
 * itself, and the records it crosses, are the ones in {@code
 * org.higherkindedj.example.optics.bridge} that the page includes from.
 */
public final class BridgingBook {

  private BridgingBook() {}

  /** What the page's reads and write through the bridge return, so the test can read each. */
  record Reads(String city, Company moved, List<String> emails, List<String> phones) {}

  static Reads readThroughTheBridge(Company acme) {
    // ANCHOR: bridge_reads
    String city = CompanyBridge.HEADQUARTERS_CITY.get(acme);
    // "New York": read straight through the Immutables Address

    Company moved = CompanyBridge.HEADQUARTERS_CITY.set("Seattle", acme);
    // headquarters.city is "Seattle"; every other Address field, and acme itself, is untouched

    List<String> emails = CompanyBridge.allCompanyEmails().getAll(acme);
    // [alice@acme.com, bob@acme.com, carol@acme.com]: three records deep, across the boundary

    List<String> phones = CompanyBridge.allCompanyPhones().getAll(acme);
    // [617-555-0101, 617-555-0102, 312-555-0201]
    // ANCHOR_END: bridge_reads
    return new Reads(city, moved, emails, phones);
  }
}
