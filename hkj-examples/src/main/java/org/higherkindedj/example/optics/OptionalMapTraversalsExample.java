// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;

/**
 * Demonstrates Optional and Map value traversals for declarative handling of nullable fields and
 * bulk map transformations.
 *
 * <p>This example shows:
 *
 * <ul>
 *   <li>{@code Traversals.forOptional()} - Affine traversals for Optional values (0-1 cardinality)
 *   <li>{@code Traversals.forMapValues()} - Bulk value transformations preserving keys
 *   <li>Composing structure traversals with other optics
 *   <li>Avoiding nested Optional.map() chains
 * </ul>
 *
 * <p>Scenarios covered:
 *
 * <ul>
 *   <li>Feature flag management
 *   <li>Configuration value normalisation
 *   <li>Server registry operations
 *   <li>Nullable field handling
 *   <li>Service endpoint transformations
 * </ul>
 */
public class OptionalMapTraversalsExample {

  // Domain models
  record ServerConfig(String hostname, Optional<Integer> port, Optional<String> sslCertPath) {
    @Override
    public String toString() {
      return String.format(
          "Server[host=%s, port=%s, ssl=%s]",
          hostname, port.map(String::valueOf).orElse("default"), sslCertPath.orElse("none"));
    }
  }

  record ApplicationConfig(
      String appName, Optional<ServerConfig> server, Map<String, String> properties) {
    @Override
    public String toString() {
      return String.format(
          "App[name=%s, server=%s, props=%d]",
          appName, server.map(s -> "configured").orElse("none"), properties.size());
    }
  }

  record FeatureFlags(Map<String, Optional<Boolean>> flags) {
    @Override
    public String toString() {
      long enabled = flags.values().stream().filter(opt -> opt.orElse(false)).count();
      long disabled = flags.values().stream().filter(opt -> !opt.orElse(false)).count();
      long unset = flags.values().stream().filter(Optional::isEmpty).count();
      return String.format("Features[enabled=%d, disabled=%d, unset=%d]", enabled, disabled, unset);
    }
  }

  record ServiceRegistry(Map<String, ServerConfig> services) {
    @Override
    public String toString() {
      return "Registry[services=" + services.size() + "]";
    }
  }

  public static void main(String[] args) {
    System.out.println("=== OPTIONAL AND MAP TRAVERSALS EXAMPLE ===\n");

    demonstrateOptionalTraversals();
    demonstrateMapValueTraversals();
    demonstrateComposedTraversals();
    demonstrateFeatureFlagManagement();

    System.out.println("\n=== OPTIONAL AND MAP TRAVERSALS COMPLETE ===");
  }

  private static void demonstrateOptionalTraversals() {
    System.out.println("--- SCENARIO 1: Optional Traversals ---\n");

    // Basic Optional traversal
    Traversal<Optional<Integer>, Integer> optTraversal = Traversals.forOptional();

    Optional<Integer> maybePort = Optional.of(8080);
    Optional<Integer> offsetPort = Traversals.modify(optTraversal, p -> p + 1000, maybePort);

    System.out.println("Original port: " + maybePort);
    System.out.println("Offset port:   " + offsetPort);

    // Empty Optional remains empty
    Optional<Integer> empty = Optional.empty();
    Optional<Integer> stillEmpty = Traversals.modify(optTraversal, p -> p + 1000, empty);

    System.out.println("\nEmpty optional: " + empty);
    System.out.println("After modify:   " + stillEmpty);

    // Extract as list
    List<Integer> values = Traversals.getAll(optTraversal, maybePort);
    System.out.println("\nExtracted values: " + values);

    // ServerConfig with optional fields
    ServerConfig server =
        new ServerConfig("localhost", Optional.of(8080), Optional.of("/etc/ssl/cert.pem"));

    System.out.println("\n--- Server Configuration ---");
    System.out.println("Original:  " + server);

    // Modify port if present
    ServerConfig offsetServer =
        new ServerConfig(
            server.hostname(),
            Traversals.modify(optTraversal, p -> p + 1000, server.port()),
            server.sslCertPath());

    System.out.println("Offset:    " + offsetServer);

    // Handle server with no port
    ServerConfig serverNoPort =
        new ServerConfig("api.example.com", Optional.empty(), Optional.empty());
    System.out.println("\nServer without port: " + serverNoPort);

    ServerConfig attemptOffset =
        new ServerConfig(
            serverNoPort.hostname(),
            Traversals.modify(optTraversal, p -> p + 1000, serverNoPort.port()),
            serverNoPort.sslCertPath());

    System.out.println("After offset:        " + attemptOffset);
    System.out.println();
  }

  private static void demonstrateMapValueTraversals() {
    System.out.println("--- SCENARIO 2: Map Value Traversals ---\n");

    // Basic Map values traversal
    Traversal<Map<String, Double>, Double> priceTraversal = Traversals.forMapValues();

    Map<String, Double> prices = new HashMap<>();
    prices.put("widget", 10.0);
    prices.put("gadget", 25.0);
    prices.put("gizmo", 15.0);

    System.out.println("Original prices:");
    prices.forEach((k, v) -> System.out.println("  " + k + ": $" + v));

    // Apply 10% increase to all prices
    Map<String, Double> inflated = Traversals.modify(priceTraversal, price -> price * 1.1, prices);

    System.out.println("\nAfter 10% increase:");
    inflated.forEach((k, v) -> System.out.printf("  %s: $%.2f%n", k, v));

    // Extract all values
    List<Double> allPrices = Traversals.getAll(priceTraversal, prices);
    System.out.println("\nAll prices: " + allPrices);

    // Conditional update with filtered
    Traversal<Map<String, Double>, Double> expensiveItems =
        priceTraversal.filtered(price -> price > 20.0);

    Map<String, Double> discounted =
        Traversals.modify(expensiveItems, price -> price * 0.9, prices);

    System.out.println("\n10% discount on expensive items (>$20):");
    discounted.forEach((k, v) -> System.out.printf("  %s: $%.2f%n", k, v));

    // Configuration properties
    Map<String, String> config = new HashMap<>();
    config.put("db.host", "  localhost  ");
    config.put("db.port", "  5432  ");
    config.put("db.name", "  mydb  ");

    Traversal<Map<String, String>, String> stringValues = Traversals.forMapValues();

    System.out.println("\n--- Configuration Properties ---");
    System.out.println("Original:");
    config.forEach((k, v) -> System.out.println("  " + k + "='" + v + "'"));

    // Trim all values
    Map<String, String> trimmed = Traversals.modify(stringValues, String::trim, config);

    System.out.println("\nAfter trimming:");
    trimmed.forEach((k, v) -> System.out.println("  " + k + "='" + v + "'"));
    System.out.println();
  }

  private static void demonstrateComposedTraversals() {
    System.out.println("--- SCENARIO 3: Composed Traversals ---\n");

    // Service registry with multiple servers
    Map<String, ServerConfig> services = new HashMap<>();
    services.put("api", new ServerConfig("api.example.com", Optional.of(8080), Optional.empty()));
    services.put(
        "db",
        new ServerConfig("db.example.com", Optional.of(5432), Optional.of("/etc/ssl/db.pem")));
    services.put(
        "cache", new ServerConfig("cache.example.com", Optional.empty(), Optional.empty()));

    ServiceRegistry registry = new ServiceRegistry(services);

    System.out.println("Original registry:");
    services.forEach((name, config) -> System.out.println("  " + name + ": " + config));

    // Compose: Map values -> Optional -> modify ports
    Traversal<Map<String, ServerConfig>, ServerConfig> allServices = Traversals.forMapValues();
    Traversal<Optional<Integer>, Integer> optPort = Traversals.forOptional();

    // Transform all service ports (where present)
    Map<String, ServerConfig> offsetServices = new HashMap<>();
    services.forEach(
        (name, config) -> {
          Optional<Integer> newPort = Traversals.modify(optPort, p -> p + 1000, config.port());
          offsetServices.put(
              name, new ServerConfig(config.hostname(), newPort, config.sslCertPath()));
        });

    System.out.println("\nAfter port offset (+1000):");
    offsetServices.forEach((name, config) -> System.out.println("  " + name + ": " + config));

    // Extract all configured ports
    List<Integer> configuredPorts =
        services.values().stream().flatMap(config -> config.port().stream()).toList();

    System.out.println("\nAll configured ports: " + configuredPorts);
    System.out.println();
  }

  private static void demonstrateFeatureFlagManagement() {
    System.out.println("--- SCENARIO 4: Feature Flag Management ---\n");

    Map<String, Optional<Boolean>> flags = new HashMap<>();
    flags.put("dark-mode", Optional.of(true));
    flags.put("new-ui", Optional.of(false));
    flags.put("beta-features", Optional.empty()); // Not yet configured
    flags.put("analytics", Optional.of(true));

    FeatureFlags config = new FeatureFlags(flags);

    System.out.println("Original flags:");
    System.out.println("  " + config);
    flags.forEach(
        (name, value) ->
            System.out.println("    " + name + ": " + value.map(String::valueOf).orElse("unset")));

    // Enable all flags that are currently set (respect unset flags)
    Traversal<Map<String, Optional<Boolean>>, Optional<Boolean>> allFlagValues =
        Traversals.forMapValues();
    Traversal<Optional<Boolean>, Boolean> presentFlags = Traversals.forOptional();

    Map<String, Optional<Boolean>> allEnabled = new HashMap<>(flags);
    allFlagValues = Traversals.forMapValues();

    // Manual transformation for nested Optional (simplified)
    Map<String, Optional<Boolean>> enabled = new HashMap<>();
    flags.forEach(
        (name, value) -> {
          if (value.isPresent()) {
            enabled.put(name, Optional.of(true));
          } else {
            enabled.put(name, value); // Keep unset flags as unset
          }
        });

    FeatureFlags allEnabledConfig = new FeatureFlags(enabled);

    System.out.println("\nAfter enabling all set flags:");
    System.out.println("  " + allEnabledConfig);
    enabled.forEach(
        (name, value) ->
            System.out.println("    " + name + ": " + value.map(String::valueOf).orElse("unset")));

    // Disable specific flags
    Map<String, Optional<Boolean>> selective = new HashMap<>(flags);
    selective.put("beta-features", Optional.of(false)); // Explicitly disable

    System.out.println("\nWith beta-features explicitly disabled:");
    selective.forEach(
        (name, value) ->
            System.out.println("    " + name + ": " + value.map(String::valueOf).orElse("unset")));

    // Extract enabled feature names
    List<String> enabledFeatures =
        flags.entrySet().stream()
            .filter(e -> e.getValue().orElse(false))
            .map(Map.Entry::getKey)
            .toList();

    System.out.println("\nCurrently enabled features: " + enabledFeatures);
  }
}
