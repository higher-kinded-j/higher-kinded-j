// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.traversal.map;

import module java.base;
import module org.higherkindedj.core;

import static org.higherkindedj.hkt.id.IdKindHelper.ID;

import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.optics.annotations.GenerateTraversals;

/** A runnable example demonstrating how to traverse the values of a {@link Map}. */
public class MapValueTraversalExample {

  /**
   * A record holding a map of feature toggles. {@code @GenerateTraversals} will generate a
   * Traversal over the map's values.
   */
  @GenerateTraversals
  public record FeatureToggles(String environment, Map<String, Boolean> flags) {}

  /** Java 25 instance main method - no static modifier or String[] args required. */
  void main() {
    // 1. Get the generated traversal for the 'flags' field.
    // Note: The generated name is `flagsValues` to indicate we are traversing the values.
    var flagsTraversal = FeatureTogglesTraversals.flags();

    System.out.println("--- Running Traversal Scenarios for Map Values ---");

    var initialToggles =
        new FeatureToggles(
            "production",
            Map.of("enable-new-dashboard", true, "use-alpha-api", false, "dark-mode", true));
    System.out.println("\nInput: " + initialToggles);

    // Use the traversal to modify every value in the map to 'false'.
    // We use modifyF with the Id monad to achieve the "set" operation.
    var updatedToggles =
        ID.narrow(
                flagsTraversal.modifyF(
                    currentValue -> Id.of(false), // The function to apply to each value
                    initialToggles,
                    IdMonad.instance()))
            .value();
    System.out.println("Result (disabling all flags): " + updatedToggles);
    // Expected: FeatureToggles[environment=production, flags={enable-new-dashboard=false,
    // use-alpha-api=false, dark-mode=false}]
  }
}
