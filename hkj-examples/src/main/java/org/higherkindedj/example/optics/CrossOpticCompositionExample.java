// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.util.Prisms;
import org.higherkindedj.optics.util.Traversals;

/**
 * A runnable example demonstrating cross-optic composition patterns.
 *
 * <p>This example showcases the new direct composition methods:
 *
 * <ul>
 *   <li>{@code Lens.andThen(Prism)} - Returns a {@code Traversal}
 *   <li>{@code Prism.andThen(Lens)} - Returns a {@code Traversal}
 * </ul>
 *
 * <p>These compositions follow the standard optic composition rules where composing with a Prism
 * (which may not match) results in a Traversal (zero-or-one focus) rather than maintaining the
 * original optic type.
 */
public class CrossOpticCompositionExample {

  // --- Domain models for Lens >>> Prism example ---
  @GenerateLenses
  record Config(String name, Optional<DatabaseSettings> database) {}

  @GenerateLenses
  record DatabaseSettings(String host, int port, String username) {}

  // --- Domain models for Prism >>> Lens example ---
  sealed interface ApiResponse permits Success, Failure {}

  @GenerateLenses
  record Success(ResponseData data, String timestamp) implements ApiResponse {}

  @GenerateLenses
  record ResponseData(String message, int count) {}

  record Failure(String error, int code) implements ApiResponse {}

  // --- Domain models for combined example ---
  sealed interface Shape permits Circle, Rectangle {}

  @GenerateLenses
  record Circle(double radius, String colour) implements Shape {}

  @GenerateLenses
  record Rectangle(double width, double height, String colour) implements Shape {}

  public static void main(String[] args) {
    System.out.println("=== Cross-Optic Composition Examples ===\n");

    demonstrateLensAndThenPrism();
    demonstratePrismAndThenLens();
    demonstrateChainedComposition();
    demonstrateRealWorldScenario();
  }

  /**
   * Demonstrates Lens >>> Prism = Traversal.
   *
   * <p>When you have a product type (record) containing an optional field, you can compose a Lens
   * with the {@code Prisms.some()} prism to get a Traversal that focuses on the value when present.
   */
  private static void demonstrateLensAndThenPrism() {
    System.out.println("--- Lens.andThen(Prism) = Traversal ---");
    System.out.println("Scenario: Config with optional DatabaseSettings\n");

    // Create optics
    Lens<Config, Optional<DatabaseSettings>> databaseLens =
        Lens.of(Config::database, (config, db) -> new Config(config.name(), db));

    Prism<Optional<DatabaseSettings>, DatabaseSettings> somePrism = Prisms.some();

    // Direct composition: Lens >>> Prism = Traversal
    Traversal<Config, DatabaseSettings> databaseTraversal = databaseLens.andThen(somePrism);

    // Test data
    Config configWithDb =
        new Config("production", Optional.of(new DatabaseSettings("localhost", 5432, "admin")));
    Config configWithoutDb = new Config("development", Optional.empty());

    // Get all focused values
    List<DatabaseSettings> withDbResult = Traversals.getAll(databaseTraversal, configWithDb);
    List<DatabaseSettings> withoutDbResult = Traversals.getAll(databaseTraversal, configWithoutDb);

    System.out.println("Config with database:");
    System.out.println("  Input: " + configWithDb);
    System.out.println("  Focused values: " + withDbResult);
    System.out.println();

    System.out.println("Config without database:");
    System.out.println("  Input: " + configWithoutDb);
    System.out.println("  Focused values: " + withoutDbResult + " (empty - prism didn't match)");
    System.out.println();

    // Modify the focused value
    Config modifiedConfig =
        Traversals.modify(
            databaseTraversal,
            db -> new DatabaseSettings(db.host(), db.port() + 1, db.username()),
            configWithDb);

    System.out.println("After modifying port (+1):");
    System.out.println("  Result: " + modifiedConfig);
    System.out.println();

    // Modification on empty config leaves it unchanged
    Config unchangedConfig =
        Traversals.modify(
            databaseTraversal,
            db -> new DatabaseSettings("new-host", 9999, db.username()),
            configWithoutDb);

    System.out.println("Modifying config without database:");
    System.out.println("  Result: " + unchangedConfig + " (unchanged - nothing to modify)");
    System.out.println();
  }

  /**
   * Demonstrates Prism >>> Lens = Traversal.
   *
   * <p>When you have a sum type (sealed interface) and want to access a field from a specific case,
   * compose a Prism with a Lens to get a Traversal.
   */
  private static void demonstratePrismAndThenLens() {
    System.out.println("--- Prism.andThen(Lens) = Traversal ---");
    System.out.println("Scenario: ApiResponse sum type with Success/Failure cases\n");

    // Create optics
    Prism<ApiResponse, Success> successPrism =
        Prism.of(resp -> resp instanceof Success s ? Optional.of(s) : Optional.empty(), s -> s);

    Lens<Success, ResponseData> dataLens =
        Lens.of(Success::data, (success, data) -> new Success(data, success.timestamp()));

    // Direct composition: Prism >>> Lens = Traversal
    Traversal<ApiResponse, ResponseData> successDataTraversal = successPrism.andThen(dataLens);

    // Test data
    ApiResponse successResponse =
        new Success(new ResponseData("Operation completed", 42), "2024-01-15T10:30:00Z");
    ApiResponse failureResponse = new Failure("Not found", 404);

    // Get all focused values
    List<ResponseData> fromSuccess = Traversals.getAll(successDataTraversal, successResponse);
    List<ResponseData> fromFailure = Traversals.getAll(successDataTraversal, failureResponse);

    System.out.println("Success response:");
    System.out.println("  Input: " + successResponse);
    System.out.println("  Focused ResponseData: " + fromSuccess);
    System.out.println();

    System.out.println("Failure response:");
    System.out.println("  Input: " + failureResponse);
    System.out.println("  Focused ResponseData: " + fromFailure + " (empty - prism didn't match)");
    System.out.println();

    // Modify only success responses
    ApiResponse modifiedSuccess =
        Traversals.modify(
            successDataTraversal,
            data -> new ResponseData(data.message().toUpperCase(), data.count()),
            successResponse);

    ApiResponse unchangedFailure =
        Traversals.modify(
            successDataTraversal,
            data -> new ResponseData(data.message().toUpperCase(), data.count()),
            failureResponse);

    System.out.println("After uppercasing message:");
    System.out.println("  Success: " + modifiedSuccess);
    System.out.println("  Failure: " + unchangedFailure + " (unchanged)");
    System.out.println();
  }

  /**
   * Demonstrates chaining multiple cross-optic compositions.
   *
   * <p>You can chain Traversals to reach deeply nested values across multiple optic types.
   */
  private static void demonstrateChainedComposition() {
    System.out.println("--- Chained Cross-Optic Composition ---");
    System.out.println("Scenario: Config -> Optional<DatabaseSettings> -> host (String)\n");

    // Create optics
    Lens<Config, Optional<DatabaseSettings>> databaseLens =
        Lens.of(Config::database, (config, db) -> new Config(config.name(), db));

    Prism<Optional<DatabaseSettings>, DatabaseSettings> somePrism = Prisms.some();

    Lens<DatabaseSettings, String> hostLens =
        Lens.of(
            DatabaseSettings::host,
            (db, host) -> new DatabaseSettings(host, db.port(), db.username()));

    // Chain: Lens >>> Prism = Traversal, then Traversal >>> Lens.asTraversal() = Traversal
    Traversal<Config, String> hostTraversal =
        databaseLens.andThen(somePrism).andThen(hostLens.asTraversal());

    // Test data
    Config config =
        new Config("prod", Optional.of(new DatabaseSettings("db.example.com", 5432, "admin")));

    // Get all hosts
    List<String> hosts = Traversals.getAll(hostTraversal, config);
    System.out.println("Hosts in config: " + hosts);

    // Modify host
    Config modifiedConfig = Traversals.modify(hostTraversal, String::toUpperCase, config);
    System.out.println("After uppercasing host: " + modifiedConfig);
    System.out.println();
  }

  /**
   * Demonstrates a real-world scenario with shapes.
   *
   * <p>This example shows how to access and modify fields of specific shape variants.
   */
  private static void demonstrateRealWorldScenario() {
    System.out.println("--- Real-World Scenario: Shape Processing ---");
    System.out.println("Scenario: Process only Circle shapes in a collection\n");

    // Create optics for Circle
    Prism<Shape, Circle> circlePrism =
        Prism.of(shape -> shape instanceof Circle c ? Optional.of(c) : Optional.empty(), c -> c);

    Lens<Circle, Double> radiusLens =
        Lens.of(Circle::radius, (circle, r) -> new Circle(r, circle.colour()));

    Lens<Circle, String> colourLens =
        Lens.of(Circle::colour, (circle, c) -> new Circle(circle.radius(), c));

    // Prism >>> Lens = Traversal
    Traversal<Shape, Double> circleRadiusTraversal = circlePrism.andThen(radiusLens);
    Traversal<Shape, String> circleColourTraversal = circlePrism.andThen(colourLens);

    // Test data
    List<Shape> shapes =
        List.of(
            new Circle(5.0, "red"),
            new Rectangle(10.0, 20.0, "blue"),
            new Circle(3.0, "green"),
            new Rectangle(15.0, 25.0, "yellow"));

    System.out.println("Original shapes: " + shapes);
    System.out.println();

    // Extract all circle radii using Traversals.forList() combined with our traversal
    Traversal<List<Shape>, Double> allCircleRadii =
        Traversals.<Shape>forList().andThen(circleRadiusTraversal);

    List<Double> radii = Traversals.getAll(allCircleRadii, shapes);
    System.out.println("All circle radii: " + radii);

    // Double all circle radii
    List<Shape> modifiedShapes = Traversals.modify(allCircleRadii, r -> r * 2, shapes);
    System.out.println("After doubling circle radii: " + modifiedShapes);
    System.out.println();

    // Change all circle colours to "purple"
    Traversal<List<Shape>, String> allCircleColours =
        Traversals.<Shape>forList().andThen(circleColourTraversal);

    List<Shape> recolouredShapes =
        Traversals.modify(allCircleColours, _ -> "purple", modifiedShapes);
    System.out.println("After recolouring circles: " + recolouredShapes);
    System.out.println();

    System.out.println("Note: Rectangle shapes are unchanged throughout all operations.");
  }
}
