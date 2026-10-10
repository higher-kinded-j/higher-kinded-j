// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.importoptics;

import org.higherkindedj.example.optics.importoptics.external.Coordinate;
import org.higherkindedj.optics.Lens;

/**
 * A runnable example demonstrating how to use {@code @ImportOptics} to generate lenses for external
 * record types.
 *
 * <p>This example shows that records from external libraries (simulated here by the {@code
 * external} package, whose {@code Coordinate} stands for a geocoding SDK's type) can have lenses
 * generated via the {@code @ImportOptics} annotation in package-info.java.
 */
public class ImportOpticsRecordExample {

  public static void main(String[] args) {
    System.out.println("=== ImportOptics Record Example ===\n");

    // 1. Create an external record instance: where a warehouse stands
    Coordinate warehouse = new Coordinate(51.5072, -0.1276);
    System.out.println("Original coordinate: " + warehouse);
    System.out.println();

    // 2. Use generated lenses to access fields
    // The annotation processor generates CoordinateLenses with lenses for each component
    Lens<Coordinate, Double> latLens = CoordinateLenses.lat();
    Lens<Coordinate, Double> lonLens = CoordinateLenses.lon();

    double lat = latLens.get(warehouse);
    double lon = lonLens.get(warehouse);

    System.out.println("Latitude via lens: " + lat);
    System.out.println("Longitude via lens: " + lon);
    System.out.println();

    // 3. Use lenses to create updated copies immutably
    Coordinate movedNorth = latLens.set(53.4808, warehouse);
    System.out.println("After latitude change: " + movedNorth);

    Coordinate movedEast = lonLens.set(0.1218, warehouse);
    System.out.println("After longitude change: " + movedEast);
    System.out.println();

    // 4. Use modify for computed updates
    // Round the latitude to two decimal places
    Coordinate rounded = latLens.modify(l -> Math.round(l * 100) / 100.0, warehouse);
    System.out.println("After rounding latitude: " + rounded);

    // Mirror the longitude across the Greenwich meridian
    Coordinate mirrored = lonLens.modify(l -> -l, warehouse);
    System.out.println("After mirroring longitude: " + mirrored);
    System.out.println();

    // 5. Use generated convenience methods
    // The processor also generates withX() helper methods
    Coordinate viaWithMethod = CoordinateLenses.withLat(warehouse, 52.4862);
    System.out.println("Via withLat method: " + viaWithMethod);

    // 6. Chain multiple updates
    Coordinate fullyUpdated =
        CoordinateLenses.withLon(CoordinateLenses.withLat(warehouse, 55.9533), -3.1883);
    System.out.println("Fully updated coordinate: " + fullyUpdated);
    System.out.println();

    // 7. Original remains unchanged (immutability)
    System.out.println("Original unchanged: " + warehouse);

    System.out.println("\n=== Example Complete ===");
  }
}
