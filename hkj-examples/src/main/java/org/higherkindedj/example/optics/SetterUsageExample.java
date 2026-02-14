// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.util.*;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.optics.Setter;
import org.higherkindedj.optics.annotations.GenerateSetters;

/**
 * Comprehensive example demonstrating Setter optics for write-only modifications.
 *
 * <p>This example showcases:
 *
 * <ul>
 *   <li>Basic modify and set operations on fields
 *   <li>Composing Setters for deep modifications in nested structures
 *   <li>Working with collection Setters to modify all elements
 *   <li>Effectful modifications using Applicative contexts
 *   <li>Real-world use cases in data normalization and batch updates
 * </ul>
 *
 * <p>Setter is ideal when you need to:
 *
 * <ul>
 *   <li>Modify values without necessarily reading them first
 *   <li>Apply transformations to multiple elements (like in a Traversal)
 *   <li>Create write-only access patterns for encapsulation
 *   <li>Build data transformation pipelines
 * </ul>
 */
public class SetterUsageExample {

  @GenerateSetters
  public record User(String username, String email, int loginCount, UserSettings settings) {}

  @GenerateSetters
  public record UserSettings(
      String theme, boolean notifications, int fontSize, Map<String, String> preferences) {}

  @GenerateSetters
  public record Product(String name, double price, int stock, List<String> tags) {}

  @GenerateSetters
  public record Inventory(List<Product> products, String warehouseId) {}

  public static void main(String[] args) {
    // Create sample data
    var settings = new UserSettings("light", true, 14, Map.of("language", "en", "timezone", "UTC"));
    var user = new User("johndoe", "john.doe@example.com", 10, settings);

    var product1 = new Product("Laptop", 999.99, 50, List.of("electronics", "computers"));
    var product2 = new Product("Keyboard", 79.99, 100, List.of("electronics", "accessories"));
    var product3 = new Product("Monitor", 299.99, 30, List.of("electronics", "displays"));
    var inventory = new Inventory(List.of(product1, product2, product3), "WH-001");

    System.out.println("=== SETTER USAGE EXAMPLE ===\n");

    // --- SCENARIO 1: Basic Setter Operations ---
    System.out.println("--- Scenario 1: Basic Setter Operations ---");

    Setter<User, String> usernameSetter =
        Setter.fromGetSet(
            User::username, (u, name) -> new User(name, u.email(), u.loginCount(), u.settings()));

    Setter<User, Integer> loginCountSetter =
        Setter.fromGetSet(
            User::loginCount, (u, count) -> new User(u.username(), u.email(), count, u.settings()));

    User renamedUser = usernameSetter.set("jane_doe", user);
    System.out.println("Original username: " + user.username());
    System.out.println("New username: " + renamedUser.username());

    User incrementedLogins = loginCountSetter.modify(count -> count + 1, user);
    System.out.println("Original login count: " + user.loginCount());
    System.out.println("New login count: " + incrementedLogins.loginCount());

    // Multiple modifications
    User normalizedUser = usernameSetter.modify(String::toLowerCase, user);
    System.out.println("Normalized username: " + normalizedUser.username() + "\n");

    // --- SCENARIO 2: Setter Composition ---
    System.out.println("--- Scenario 2: Setter Composition ---");

    Setter<User, UserSettings> settingsSetter =
        Setter.fromGetSet(
            User::settings, (u, s) -> new User(u.username(), u.email(), u.loginCount(), s));

    Setter<UserSettings, String> themeSetter =
        Setter.fromGetSet(
            UserSettings::theme,
            (s, t) -> new UserSettings(t, s.notifications(), s.fontSize(), s.preferences()));

    Setter<UserSettings, Integer> fontSizeSetter =
        Setter.fromGetSet(
            UserSettings::fontSize,
            (s, size) -> new UserSettings(s.theme(), s.notifications(), size, s.preferences()));

    // Compose to create deep setters
    Setter<User, String> userThemeSetter = settingsSetter.andThen(themeSetter);
    Setter<User, Integer> userFontSizeSetter = settingsSetter.andThen(fontSizeSetter);

    User darkModeUser = userThemeSetter.set("dark", user);
    System.out.println("Original theme: " + user.settings().theme());
    System.out.println("New theme: " + darkModeUser.settings().theme());

    User largerFontUser = userFontSizeSetter.modify(size -> size + 2, user);
    System.out.println("Original font size: " + user.settings().fontSize());
    System.out.println("New font size: " + largerFontUser.settings().fontSize() + "\n");

    // --- SCENARIO 3: Collection Setters ---
    System.out.println("--- Scenario 3: Collection Setters ---");

    Setter<List<Double>, Double> listSetter = Setter.forList();
    List<Double> prices = List.of(10.0, 20.0, 30.0);

    List<Double> discountedPrices = listSetter.modify(price -> price * 0.9, prices);
    System.out.println("Original prices: " + prices);
    System.out.println("Discounted prices (10% off): " + discountedPrices);

    Setter<Map<String, Integer>, Integer> mapValuesSetter = Setter.forMapValues();
    Map<String, Integer> scores = new HashMap<>(Map.of("Alice", 85, "Bob", 90, "Charlie", 78));

    Map<String, Integer> curvedScores =
        mapValuesSetter.modify(score -> Math.min(100, score + 5), scores);
    System.out.println("Original scores: " + scores);
    System.out.println("Curved scores (+5): " + curvedScores + "\n");

    // --- SCENARIO 4: Nested Collection Setters ---
    System.out.println("--- Scenario 4: Nested Collection Setters ---");

    Setter<Inventory, List<Product>> productsSetter =
        Setter.fromGetSet(
            Inventory::products, (inv, prods) -> new Inventory(prods, inv.warehouseId()));

    Setter<List<Product>, Product> productListSetter = Setter.forList();

    Setter<Product, Double> priceSetter =
        Setter.fromGetSet(
            Product::price, (p, price) -> new Product(p.name(), price, p.stock(), p.tags()));

    Setter<Product, Integer> stockSetter =
        Setter.fromGetSet(
            Product::stock, (p, stock) -> new Product(p.name(), p.price(), stock, p.tags()));

    // Apply 10% discount to all products
    Setter<Inventory, Product> allProductsSetter = productsSetter.andThen(productListSetter);
    Inventory discountedInventory =
        allProductsSetter.modify(
            product -> priceSetter.modify(price -> price * 0.9, product), inventory);

    System.out.println("Original inventory prices:");
    for (Product p : inventory.products()) {
      System.out.println("  " + p.name() + ": $" + String.format("%.2f", p.price()));
    }

    System.out.println("Discounted inventory prices (10% off):");
    for (Product p : discountedInventory.products()) {
      System.out.println("  " + p.name() + ": $" + String.format("%.2f", p.price()));
    }

    // Restock all products
    Inventory restockedInventory =
        allProductsSetter.modify(
            product -> stockSetter.modify(stock -> stock + 10, product), inventory);

    System.out.println("\nRestocked inventory (+10 units each):");
    for (Product p : restockedInventory.products()) {
      System.out.println("  " + p.name() + ": " + p.stock() + " units");
    }
    System.out.println();

    // --- SCENARIO 5: Effectful Modifications ---
    System.out.println("--- Scenario 5: Effectful Modifications (Optional) ---");

    // Validate and modify username
    Function<String, Kind<OptionalKind.Witness, String>> validateUsername =
        username -> {
          if (username.length() >= 3 && username.matches("[a-z_]+")) {
            return OptionalKindHelper.OPTIONAL.widen(Optional.of(username));
          } else {
            return OptionalKindHelper.OPTIONAL.widen(Optional.empty());
          }
        };

    Kind<OptionalKind.Witness, User> validUserResult =
        usernameSetter.modifyF(validateUsername, user, OptionalMonad.INSTANCE);

    Optional<User> validUser = OptionalKindHelper.OPTIONAL.narrow(validUserResult);
    System.out.println(
        "Valid username modification (johndoe): "
            + validUser.map(User::username).orElse("INVALID"));

    User invalidUser = new User("a", "a@test.com", 0, settings); // Too short
    Kind<OptionalKind.Witness, User> invalidUserResult =
        usernameSetter.modifyF(validateUsername, invalidUser, OptionalMonad.INSTANCE);

    Optional<User> invalidUserOpt = OptionalKindHelper.OPTIONAL.narrow(invalidUserResult);
    System.out.println(
        "Invalid username modification (a): "
            + invalidUserOpt.map(User::username).orElse("INVALID"));

    // Validate stock levels
    Function<Integer, Kind<OptionalKind.Witness, Integer>> ensurePositiveStock =
        stock -> {
          if (stock >= 0) {
            return OptionalKindHelper.OPTIONAL.widen(Optional.of(stock));
          } else {
            return OptionalKindHelper.OPTIONAL.widen(Optional.empty());
          }
        };

    Product validProduct = new Product("Test", 10.0, 5, List.of());
    Kind<OptionalKind.Witness, Product> validStockResult =
        stockSetter.modifyF(ensurePositiveStock, validProduct, OptionalMonad.INSTANCE);

    Optional<Product> validStockOpt = OptionalKindHelper.OPTIONAL.narrow(validStockResult);
    System.out.println(
        "Valid stock modification (5): " + validStockOpt.map(Product::stock).orElse(-1));

    // --- SCENARIO 6: Identity Setter ---
    System.out.println("\n--- Scenario 6: Identity Setter ---");

    Setter<String, String> identitySetter = Setter.identity();
    String uppercased = identitySetter.modify(String::toUpperCase, "hello world");
    System.out.println("Identity setter (toUpperCase): " + uppercased);

    String replaced = identitySetter.set("new value", "old value");
    System.out.println("Identity setter (set): " + replaced + "\n");

    // --- SCENARIO 7: Batch Data Transformation ---
    System.out.println("--- Scenario 7: Batch Data Transformation ---");

    // Normalise all product names (trim, capitalise first letter)
    Setter<Product, String> nameSetter =
        Setter.fromGetSet(
            Product::name, (p, name) -> new Product(name, p.price(), p.stock(), p.tags()));

    Function<String, String> normalise =
        name -> {
          String trimmed = name.trim();
          return trimmed.substring(0, 1).toUpperCase() + trimmed.substring(1).toLowerCase();
        };

    List<Product> rawProducts =
        List.of(
            new Product("  LAPTOP  ", 999.99, 50, List.of()),
            new Product("keyboard", 79.99, 100, List.of()),
            new Product("MONITOR", 299.99, 30, List.of()));

    List<Product> normalisedProducts =
        productListSetter.modify(product -> nameSetter.modify(normalise, product), rawProducts);

    System.out.println("Normalised product names:");
    for (Product p : normalisedProducts) {
      System.out.println("  - " + p.name());
    }

    // Apply currency conversion (USD to EUR)
    double exchangeRate = 0.92;
    List<Product> euroProducts =
        productListSetter.modify(
            product -> priceSetter.modify(price -> price * exchangeRate, product), rawProducts);

    System.out.println("\nPrices in EUR (rate: " + exchangeRate + "):");
    for (int i = 0; i < rawProducts.size(); i++) {
      System.out.println(
          "  "
              + rawProducts.get(i).name().trim()
              + ": $"
              + String.format("%.2f", rawProducts.get(i).price())
              + " -> EUR "
              + String.format("%.2f", euroProducts.get(i).price()));
    }

    System.out.println("\n=== SETTER USAGE EXAMPLE COMPLETE ===");
  }
}
