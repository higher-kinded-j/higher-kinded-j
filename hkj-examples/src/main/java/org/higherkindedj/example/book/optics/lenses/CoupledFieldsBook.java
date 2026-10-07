// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.lenses;

import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.indexed.Pair;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/coupled_fields.html">Coupled Fields</a>
 * page. The page {@code {{#include}}}s the anchored regions, and {@code CoupledFieldsBookTest}
 * holds the claims the page makes about this code. The {@code Range} is the one the Lenses page
 * shares.
 */
public final class CoupledFieldsBook {

  /** The page's hand-written lens onto the lower bound. */
  static final Lens<Range, Integer> loLens = Lens.of(Range::lo, (r, lo) -> new Range(lo, r.hi()));

  /** The page's hand-written lens onto the upper bound. */
  static final Lens<Range, Integer> hiLens = Lens.of(Range::hi, (r, hi) -> new Range(r.lo(), hi));

  /** The page's paired lens, which constructs once. */
  static final Lens<Range, Pair<Integer, Integer>> boundsLens =
      Lens.paired(loLens, hiLens, Range::new);

  private CoupledFieldsBook() {}

  static Range loFirst() {
    // ANCHOR: lo_first
    Range range = new Range(1, 2);

    // Goal: Range(1, 2) → Range(11, 12)

    // Attempt 1: update lo first, which asks for Range(11, 2)
    Range step1 = loLens.set(11, range);
    // THROWS: "lo (11) must be <= hi (2)"
    // ANCHOR_END: lo_first
    return step1;
  }

  /** The two steps of the page's second attempt, so the test can read each. */
  record Steps(Range step1, Range step2) {}

  static Steps hiFirst(Range range) {
    // ANCHOR: hi_first
    Range step1 = hiLens.set(12, range); // Range(1, 12) - OK!
    Range step2 = loLens.set(11, step1); // Range(11, 12) - OK!
    // ANCHOR_END: hi_first
    return new Steps(step1, step2);
  }

  static Range shiftDown() {
    // ANCHOR: narrow
    Range narrow = new Range(10, 11);

    // If we update hi first: Range(10, 1) - THROWS!
    // If we update lo first: Range(0, 11) → Range(0, 1) - OK
    Range shiftedDown = hiLens.set(1, loLens.set(0, narrow));
    // ANCHOR_END: narrow
    return shiftedDown;
  }

  static Range shiftUpTogether() {
    // ANCHOR: paired_shift
    Range range = new Range(1, 2);

    // Shift up by 10 - both values updated atomically
    Range shifted = boundsLens.modify(p -> Pair.of(p.first() + 10, p.second() + 10), range);
    // Result: Range(11, 12)
    // ANCHOR_END: paired_shift
    return shifted;
  }
}
