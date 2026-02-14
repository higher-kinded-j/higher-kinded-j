// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.util.*;
import org.higherkindedj.optics.At;
import org.higherkindedj.optics.Ixed;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.at.AtInstances;
import org.higherkindedj.optics.ixed.IxedInstances;
import org.higherkindedj.optics.util.Traversals;

/**
 * A runnable example demonstrating the Ixed type class for safe indexed access to existing
 * elements.
 *
 * <p>Ixed provides a Traversal that focuses on zero or one element at a given index:
 *
 * <ul>
 *   <li>Reading returns Optional.empty() for missing indices
 *   <li>Updating is a no-op for missing indices (no insertion)
 *   <li>Structure is always preserved (no size changes)
 * </ul>
 *
 * <p>This contrasts with At which provides full CRUD semantics including insert and delete.
 */
public class IxedUsageExample {

  // Domain model for server configuration with nested collections
  // @GenerateLenses creates ServerConfigLenses with lens accessors for each field
  @GenerateLenses
  public record ServerConfig(
      String name,
      Map<String, Integer> ports,
      Map<String, String> environment,
      List<String> allowedOrigins) {}

  public static void main(String[] args) {
    System.out.println("=== Ixed Type Class Usage Examples ===\n");

    demonstrateMapSafeAccess();
    demonstrateIxedVsAt();
    demonstrateListSafeAccess();
    demonstrateLensComposition();
    demonstrateExistenceChecking();
    demonstrateIxedFromAt();
  }

  private static void demonstrateMapSafeAccess() {
    System.out.println("--- Map Safe Access (No Insertion) ---");

    // Create Ixed instance for Map<String, Integer>
    Ixed<Map<String, Integer>, String, Integer> mapIx = IxedInstances.mapIx();

    // Initial data
    Map<String, Integer> ports = new HashMap<>(Map.of("http", 8080, "https", 8443));

    System.out.println("Initial ports: " + ports);

    // Safe read operations
    System.out.println("HTTP port: " + IxedInstances.get(mapIx, "http", ports));
    System.out.println("FTP port (missing): " + IxedInstances.get(mapIx, "ftp", ports));

    // Safe update - only affects existing keys
    Map<String, Integer> updatedPorts = IxedInstances.update(mapIx, "http", 9000, ports);
    System.out.println("After update 'http': " + updatedPorts);

    // Attempted update of non-existent key - NO-OP!
    Map<String, Integer> samePorts = IxedInstances.update(mapIx, "ftp", 21, ports);
    System.out.println("After 'update' non-existent 'ftp': " + samePorts);
    System.out.println("FTP was NOT added (Ixed doesn't insert)");

    // Modify with function
    Map<String, Integer> doubled = IxedInstances.modify(mapIx, "https", x -> x * 2, ports);
    System.out.println("After doubling 'https': " + doubled);

    // Original unchanged (immutability)
    System.out.println("Original unchanged: " + ports);
    System.out.println();
  }

  private static void demonstrateIxedVsAt() {
    System.out.println("--- Ixed vs At: Insertion Behaviour ---");

    At<Map<String, Integer>, String, Integer> mapAt = AtInstances.mapAt();
    Ixed<Map<String, Integer>, String, Integer> mapIx = IxedInstances.mapIx();

    Map<String, Integer> empty = new HashMap<>();

    // At CAN insert new entries
    Map<String, Integer> withNew = mapAt.insertOrUpdate("newKey", 42, empty);
    System.out.println("At.insertOrUpdate on empty map: " + withNew);

    // Ixed CANNOT insert - update is a no-op
    Map<String, Integer> stillEmpty = IxedInstances.update(mapIx, "newKey", 42, empty);
    System.out.println("Ixed.update on empty map: " + stillEmpty);
    System.out.println("Ixed preserves structure - no insertion occurred");
    System.out.println();
  }

  private static void demonstrateListSafeAccess() {
    System.out.println("--- List Safe Indexed Access ---");

    Ixed<List<String>, Integer, String> listIx = IxedInstances.listIx();

    List<String> origins = new ArrayList<>(List.of("localhost", "example.com", "api.example.com"));

    System.out.println("Initial origins: " + origins);

    // Safe bounds checking - no exceptions
    System.out.println("Index 1: " + IxedInstances.get(listIx, 1, origins));
    System.out.println("Index 10 (out of bounds): " + IxedInstances.get(listIx, 10, origins));
    System.out.println("Index -1 (negative): " + IxedInstances.get(listIx, -1, origins));

    // Safe update within bounds
    List<String> updated = IxedInstances.update(listIx, 1, "www.example.com", origins);
    System.out.println("After update index 1: " + updated);

    // Update out of bounds - no-op, no exception!
    List<String> unchanged = IxedInstances.update(listIx, 10, "invalid.com", origins);
    System.out.println("After 'update' out-of-bounds index 10: " + unchanged);
    System.out.println("No exception thrown, list unchanged");

    // Functional modification
    List<String> uppercased = IxedInstances.modify(listIx, 0, String::toUpperCase, origins);
    System.out.println("After uppercase index 0: " + uppercased);

    // Original unchanged (immutability)
    System.out.println("Original unchanged: " + origins);
    System.out.println();
  }

  private static void demonstrateLensComposition() {
    System.out.println("--- Deep Composition: Lens + Ixed ---");

    // Use generated lenses from @GenerateLenses annotation
    Lens<ServerConfig, Map<String, Integer>> portsLens = ServerConfigLenses.ports();

    ServerConfig config =
        new ServerConfig(
            "production",
            new HashMap<>(Map.of("http", 8080, "https", 8443, "ws", 8765)),
            new HashMap<>(Map.of("NODE_ENV", "production", "LOG_LEVEL", "info")),
            new ArrayList<>(List.of("*.example.com")));

    System.out.println("Initial config: " + config);

    // Compose: ServerConfig → Map<String, Integer> → Integer (0-or-1)
    Ixed<Map<String, Integer>, String, Integer> portIx = IxedInstances.mapIx();
    Traversal<ServerConfig, Integer> httpPortTraversal =
        portsLens.asTraversal().andThen(portIx.ix("http"));

    // Safe access through composition
    List<Integer> httpPorts = Traversals.getAll(httpPortTraversal, config);
    System.out.println("HTTP port via traversal: " + httpPorts);

    // Safe modification through composition
    ServerConfig updatedConfig = Traversals.modify(httpPortTraversal, p -> p + 1000, config);
    System.out.println("After incrementing HTTP port: " + updatedConfig.ports());

    // Non-existent key = empty focus
    Traversal<ServerConfig, Integer> ftpPortTraversal =
        portsLens.asTraversal().andThen(portIx.ix("ftp"));

    List<Integer> ftpPorts = Traversals.getAll(ftpPortTraversal, config);
    System.out.println("FTP port (missing): " + ftpPorts);

    ServerConfig stillSameConfig = Traversals.modify(ftpPortTraversal, p -> p + 1, config);
    System.out.println("After 'modify' missing FTP: " + stillSameConfig.ports());
    System.out.println("Config unchanged - Ixed didn't insert FTP");
    System.out.println();
  }

  private static void demonstrateExistenceChecking() {
    System.out.println("--- Existence Checking ---");

    Ixed<Map<String, Integer>, String, Integer> portIx = IxedInstances.mapIx();

    Map<String, Integer> ports = new HashMap<>(Map.of("http", 8080, "https", 8443, "ws", 8765));

    System.out.println("Contains 'http': " + IxedInstances.contains(portIx, "http", ports));
    System.out.println("Contains 'ftp': " + IxedInstances.contains(portIx, "ftp", ports));

    // Pattern: Check before deciding on operation
    String keyToUpdate = "ws";
    if (IxedInstances.contains(portIx, keyToUpdate, ports)) {
      Map<String, Integer> newPorts = IxedInstances.update(portIx, keyToUpdate, 9999, ports);
      System.out.println("WebSocket port updated to 9999: " + newPorts);
    } else {
      System.out.println(keyToUpdate + " not found - would need At to insert");
    }
    System.out.println();
  }

  private static void demonstrateIxedFromAt() {
    System.out.println("--- Creating Ixed from At ---");

    At<Map<String, String>, String, String> stringMapAt = AtInstances.mapAt();
    Ixed<Map<String, String>, String, String> envIx = IxedInstances.fromAt(stringMapAt);

    Map<String, String> env = new HashMap<>(Map.of("NODE_ENV", "production", "LOG_LEVEL", "info"));
    System.out.println("Initial environment: " + env);

    // Use derived Ixed for safe operations
    Map<String, String> updatedEnv = IxedInstances.update(envIx, "LOG_LEVEL", "debug", env);
    System.out.println("After update LOG_LEVEL: " + updatedEnv);

    Map<String, String> unchangedEnv = IxedInstances.update(envIx, "NEW_VAR", "value", env);
    System.out.println("After 'update' non-existent NEW_VAR: " + unchangedEnv);
    System.out.println("NEW_VAR not added - Ixed from At still can't insert");

    System.out.println("\n=== All operations maintain immutability and structure ===");
  }
}
