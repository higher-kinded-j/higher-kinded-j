// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.monoid;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;

/**
 * Demonstrates using Optional-based monoids for data aggregation patterns.
 *
 * <p>This example shows how to use {@code firstOptional}, {@code lastOptional}, {@code maximum},
 * and {@code minimum} monoids to elegantly handle common data aggregation scenarios where values
 * may be missing or sparse.
 *
 * <p>Key concepts demonstrated:
 *
 * <ul>
 *   <li>Configuration fallback chains with {@code firstOptional}
 *   <li>Finding maximum and minimum values from sparse data
 *   <li>Using custom comparators for complex type aggregation
 *   <li>Graceful handling of empty collections
 * </ul>
 */
public class OptionalMonoidExample {

  public static void main(String[] args) {
    demonstrateConfigurationFallback();
    demonstrateFindingBestValue();
    demonstrateCustomComparators();
  }

  /**
   * Demonstrates using firstOptional for configuration fallback chains.
   *
   * <p>The {@code firstOptional} monoid selects the first non-empty Optional from a sequence. This
   * is perfect for implementing priority-based configuration loading where you want to try multiple
   * sources in order and use the first one that succeeds.
   */
  private static void demonstrateConfigurationFallback() {
    System.out.println("=== Configuration Fallback Example ===");

    Monoid<Optional<String>> firstAvailable = Monoids.firstOptional();

    // Try multiple configuration sources in priority order
    List<Optional<String>> configSources =
        List.of(
            loadFromCommandLine(), // Highest priority
            loadFromEnvironment(), // Second priority
            loadFromConfigFile(), // Third priority
            loadFromDefaults() // Fallback
            );

    Optional<String> finalConfig = firstAvailable.combineAll(configSources);
    System.out.println("Selected config: " + finalConfig);
    System.out.println("Expected: Optional[env-config] (first non-empty in the priority chain)\n");
  }

  /**
   * Demonstrates finding maximum/minimum values from sparse data.
   *
   * <p>The {@code maximum} and {@code minimum} monoids find the extreme values while gracefully
   * handling missing data. Empty optionals are ignored, and only present values are compared.
   */
  private static void demonstrateFindingBestValue() {
    System.out.println("=== Finding Best Values Example ===");

    List<Optional<Integer>> measurements =
        List.of(
            Optional.of(23),
            Optional.empty(), // Sensor failure
            Optional.of(27),
            Optional.of(25),
            Optional.empty() // Missing data
            );

    Monoid<Optional<Integer>> max = Monoids.maximum();
    Monoid<Optional<Integer>> min = Monoids.minimum();

    Optional<Integer> highest = max.combineAll(measurements);
    Optional<Integer> lowest = min.combineAll(measurements);

    System.out.println("Highest measurement: " + highest); // Optional[27]
    System.out.println("Lowest measurement: " + lowest); // Optional[23]
    System.out.println("Note: Empty optionals were safely ignored during aggregation\n");
  }

  /**
   * Demonstrates using custom comparators with maximum/minimum.
   *
   * <p>For complex types, you can provide custom comparators to define what "maximum" and "minimum"
   * mean. This example shows finding products by price and by name length.
   */
  private static void demonstrateCustomComparators() {
    System.out.println("=== Custom Comparator Example ===");

    record Product(String name, double price) {}

    List<Optional<Product>> offers =
        List.of(
            Optional.of(new Product("Widget", 29.99)),
            Optional.empty(),
            Optional.of(new Product("Gadget", 49.99)),
            Optional.of(new Product("Gizmo", 19.99)));

    // Find cheapest product
    Monoid<Optional<Product>> cheapest = Monoids.minimum(Comparator.comparing(Product::price));

    Optional<Product> bestDeal = cheapest.combineAll(offers);
    System.out.println("Best deal: " + bestDeal); // Gizmo at $19.99

    // Find most expensive product
    Monoid<Optional<Product>> mostExpensive = Monoids.maximum(Comparator.comparing(Product::price));

    Optional<Product> priciest = mostExpensive.combineAll(offers);
    System.out.println("Most expensive: " + priciest); // Gadget at $49.99

    // Find product with longest name
    Monoid<Optional<Product>> longestName =
        Monoids.maximum(Comparator.comparing(p -> p.name().length()));

    Optional<Product> longest = longestName.combineAll(offers);
    System.out.println("Longest name: " + longest); // Gadget or Widget (both 6 chars)

    // Find product with shortest name
    Monoid<Optional<Product>> shortestName =
        Monoids.minimum(Comparator.comparing(p -> p.name().length()));

    Optional<Product> shortest = shortestName.combineAll(offers);
    System.out.println("Shortest name: " + shortest); // Gizmo (5 chars)
  }

  // Stub methods for configuration loading simulation
  private static Optional<String> loadFromCommandLine() {
    return Optional.empty();
  }

  private static Optional<String> loadFromEnvironment() {
    return Optional.of("env-config");
  }

  private static Optional<String> loadFromConfigFile() {
    return Optional.of("file-config");
  }

  private static Optional<String> loadFromDefaults() {
    return Optional.of("default-config");
  }
}
