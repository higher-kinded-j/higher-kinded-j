// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.indexed.Pair;

/**
 * A runnable example demonstrating how to use {@link Lens#paired} to safely update coupled fields
 * that participate in invariants.
 *
 * <p><b>The Problem:</b> When a record has an invariant that spans multiple fields (e.g., {@code lo
 * <= hi} in a Range), sequential lens updates can create invalid intermediate states that violate
 * the invariant.
 *
 * <p><b>The Solution:</b> Use {@code Lens.paired} to combine two lenses into a single lens that
 * focuses on a tuple of both values. This allows atomic updates where both fields are set
 * simultaneously via a single constructor call, bypassing invalid intermediate states.
 */
public class PairedLensExample {

  /**
   * A range with an invariant: lo must be less than or equal to hi.
   *
   * <p>This is a common pattern where two fields are coupled by a constraint.
   */
  public record Range(int lo, int hi) {
    public Range {
      if (lo > hi) {
        throw new IllegalArgumentException(
            "Range invariant violated: lo (" + lo + ") must be <= hi (" + hi + ")");
      }
    }

    /** Convenience method to display the range. */
    @Override
    public String toString() {
      return "[" + lo + ", " + hi + "]";
    }
  }

  /** Individual lens for the 'lo' field. */
  static final Lens<Range, Integer> loLens = Lens.of(Range::lo, (r, lo) -> new Range(lo, r.hi()));

  /** Individual lens for the 'hi' field. */
  static final Lens<Range, Integer> hiLens = Lens.of(Range::hi, (r, hi) -> new Range(r.lo(), hi));

  /**
   * Paired lens that focuses on both bounds as a pair. Uses the Range constructor directly for
   * atomic reconstruction.
   */
  static final Lens<Range, Pair<Integer, Integer>> boundsLens =
      Lens.paired(loLens, hiLens, Range::new);

  public static void main(String[] args) {
    Range range = new Range(1, 2);
    System.out.println("Original range: " + range);
    System.out.println("=".repeat(60));

    // =========================================================================
    // PROBLEM: Sequential updates can violate invariants
    // =========================================================================
    System.out.println("\n1. THE PROBLEM: Sequential updates fail");
    System.out.println("-".repeat(60));

    System.out.println("Trying to shift range " + range + " up by 10 using individual lenses...");
    System.out.println("  Step 1: loLens.set(11, range) would create Range(11, 2)");
    System.out.println("  But 11 > 2 violates the invariant!");

    try {
      loLens.set(11, range);
      System.out.println("  ERROR: Should have thrown!");
    } catch (IllegalArgumentException e) {
      System.out.println("  Caught: " + e.getMessage());
    }

    // =========================================================================
    // SOLUTION: Use paired lens for atomic updates
    // =========================================================================
    System.out.println("\n2. THE SOLUTION: Paired lens enables atomic updates");
    System.out.println("-".repeat(60));

    // Shift up by 10 - both values updated atomically
    Range shiftedUp = boundsLens.modify(t -> Pair.of(t.first() + 10, t.second() + 10), range);
    System.out.println("Shift up by 10:   " + range + " -> " + shiftedUp);

    // Shift down by 10
    Range shiftedDown = boundsLens.modify(t -> Pair.of(t.first() - 10, t.second() - 10), shiftedUp);
    System.out.println("Shift down by 10: " + shiftedUp + " -> " + shiftedDown);

    // =========================================================================
    // Using Pair for transformations
    // =========================================================================
    System.out.println("\n3. Using Pair for transformations");
    System.out.println("-".repeat(60));

    Range scaled = boundsLens.modify(t -> Pair.of(t.first() * 2, t.second() * 2), range);
    System.out.println("Scale by 2:  " + range + " -> " + scaled);

    Range widened = boundsLens.modify(t -> Pair.of(t.first() - 5, t.second() + 5), range);
    System.out.println("Widen by 5:  " + range + " -> " + widened);

    // =========================================================================
    // Get and set operations
    // =========================================================================
    System.out.println("\n4. Get and set operations");
    System.out.println("-".repeat(60));

    Pair<Integer, Integer> bounds = boundsLens.get(range);
    System.out.println(
        "Get bounds: " + range + " -> Pair(" + bounds.first() + ", " + bounds.second() + ")");

    Range replaced = boundsLens.set(Pair.of(100, 200), range);
    System.out.println("Set (100, 200): " + range + " -> " + replaced);

    // =========================================================================
    // Validation still works
    // =========================================================================
    System.out.println("\n5. Validation still enforced on atomic updates");
    System.out.println("-".repeat(60));

    System.out.println("Trying to set invalid bounds (100, 50) where lo > hi...");
    try {
      boundsLens.set(Pair.of(100, 50), range);
      System.out.println("  ERROR: Should have thrown!");
    } catch (IllegalArgumentException e) {
      System.out.println("  Caught: " + e.getMessage());
    }

    // =========================================================================
    // Order-independence demonstration
    // =========================================================================
    System.out.println("\n6. Order-independence: shift narrow range down");
    System.out.println("-".repeat(60));

    Range narrow = new Range(10, 11);
    System.out.println("Narrow range: " + narrow);
    System.out.println("Shifting down by 10...");
    System.out.println("  - If hi updated first: Range(10, 1) would throw (10 > 1)");
    System.out.println("  - If lo updated first: Range(0, 11) -> Range(0, 1) works");
    System.out.println("  - With paired lens: both updated atomically, order doesn't matter");

    Range narrowShifted = boundsLens.modify(t -> Pair.of(t.first() - 10, t.second() - 10), narrow);
    System.out.println("Result: " + narrow + " -> " + narrowShifted);

    System.out.println("\n" + "=".repeat(60));
    System.out.println("Paired lenses solve the coupled-field invariant problem!");
  }
}
