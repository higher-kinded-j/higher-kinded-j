// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.prisms;

import java.util.Optional;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.util.Prisms;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/advanced_prism_patterns.html">Advanced
 * Prism Patterns</a> page, in its {@code Prisms.nearly} section. The page {@code {{#include}}}s the
 * anchored region, and {@code AdvancedPrismPatternsBookTest} holds the values its comments claim.
 */
public final class AdvancedPrismPatternsBook {

  private AdvancedPrismPatternsBook() {}

  /** What the page's {@code nearly} prism reads and builds, so the test can read each. */
  record Nearly(Optional<Unit> hit, Optional<Unit> miss, String built) {}

  static Nearly nearly() {
    // ANCHOR: nearly
    // Match any non-empty string
    Prism<String, Unit> nonEmpty = Prisms.nearly("default", s -> !s.isEmpty());

    Optional<Unit> hit = nonEmpty.getOptional("hello"); // Optional.of(Unit.INSTANCE)
    Optional<Unit> miss = nonEmpty.getOptional(""); // Optional.empty()
    String built = nonEmpty.build(Unit.INSTANCE); // "default"
    // ANCHOR_END: nearly
    return new Nearly(hit, miss, built);
  }
}
