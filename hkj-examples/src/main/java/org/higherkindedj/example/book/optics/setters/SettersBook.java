// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.setters;

import static org.higherkindedj.hkt.instances.Witnesses.optional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.optics.Setter;
import org.higherkindedj.optics.annotations.GenerateSetters;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/setters.html">Setters</a> page. The page
 * {@code {{#include}}}s the anchored regions, and {@code SettersBookTest} holds the claims the page
 * makes about this code.
 *
 * <p>Each method returns the values its region's comments show, in the order they appear.
 */
public final class SettersBook {

  private SettersBook() {}

  static List<User> modify(UserSettings settings) {
    // ANCHOR: modify
    Setter<User, String> usernameSetter =
        Setter.fromGetSet(
            User::username, (u, name) -> new User(name, u.email(), u.loginCount(), u.settings()));

    User user = new User("JOHN_DOE", "john@example.com", 10, settings);

    // Transform username to lowercase
    User normalised = usernameSetter.modify(String::toLowerCase, user);
    // User[username=john_doe, email=john@example.com, loginCount=10, settings=...]

    // Append suffix
    User suffixed = usernameSetter.modify(name -> name + "_admin", user);
    // User[username=JOHN_DOE_admin, email=john@example.com, loginCount=10, settings=...]
    // ANCHOR_END: modify
    return List.of(normalised, suffixed);
  }

  static User set(UserSettings settings) {
    // ANCHOR: set
    Setter<User, Integer> loginCountSetter =
        Setter.fromGetSet(
            User::loginCount, (u, count) -> new User(u.username(), u.email(), count, u.settings()));

    User user = new User("john", "john@example.com", 10, settings);
    User reset = loginCountSetter.set(0, user);
    // User[username=john, email=john@example.com, loginCount=0, settings=...]
    // ANCHOR_END: set
    return reset;
  }

  static List<User> compose() {
    // ANCHOR: compose
    Setter<User, UserSettings> settingsSetter =
        Setter.fromGetSet(
            User::settings, (u, s) -> new User(u.username(), u.email(), u.loginCount(), s));

    Setter<UserSettings, String> themeSetter =
        Setter.fromGetSet(
            UserSettings::theme,
            (s, theme) ->
                new UserSettings(theme, s.notifications(), s.fontSize(), s.preferences()));

    // Compose: User → UserSettings → String
    Setter<User, String> userThemeSetter = settingsSetter.andThen(themeSetter);

    User user =
        new User("john", "john@example.com", 10, new UserSettings("light", true, 14, Map.of()));

    User darkModeUser = userThemeSetter.set("dark", user);
    // the settings now hold the theme "dark"; everything else is unchanged
    // ANCHOR_END: compose

    // ANCHOR: deep_chain
    Setter<UserSettings, Integer> fontSizeSetter =
        Setter.fromGetSet(
            UserSettings::fontSize,
            (s, size) -> new UserSettings(s.theme(), s.notifications(), size, s.preferences()));

    // Compose: User → UserSettings → Integer, reusing settingsSetter
    Setter<User, Integer> userFontSizeSetter = settingsSetter.andThen(fontSizeSetter);

    User largerFont = userFontSizeSetter.modify(size -> size + 2, user);
    // the font size is now 16, two points larger than the 14 it was
    // ANCHOR_END: deep_chain
    return List.of(darkModeUser, largerFont);
  }

  static List<List<Integer>> forList() {
    // ANCHOR: for_list
    Setter<List<Integer>, Integer> listSetter = Setter.forList();

    List<Integer> numbers = List.of(1, 2, 3, 4, 5);

    // Double all values
    List<Integer> doubled = listSetter.modify(x -> x * 2, numbers);
    // [2, 4, 6, 8, 10]

    // Set all to same value
    List<Integer> allZeros = listSetter.set(0, numbers);
    // [0, 0, 0, 0, 0]
    // ANCHOR_END: for_list
    return List.of(doubled, allZeros);
  }

  static List<Map<String, Integer>> forMapValues() {
    // ANCHOR: for_map_values
    Setter<Map<String, Integer>, Integer> mapSetter = Setter.forMapValues();

    // a TreeMap, so the scores come back in key order
    Map<String, Integer> scores = new TreeMap<>(Map.of("Alice", 85, "Bob", 90, "Charlie", 78));

    // Add 5 points to all scores
    Map<String, Integer> curved = mapSetter.modify(score -> Math.min(100, score + 5), scores);
    // {Alice=90, Bob=95, Charlie=83}

    // Reset all scores
    Map<String, Integer> reset = mapSetter.set(0, scores);
    // {Alice=0, Bob=0, Charlie=0}
    // ANCHOR_END: for_map_values
    return List.of(curved, reset);
  }

  static List<Inventory> nested() {
    // ANCHOR: nested
    Setter<Inventory, List<Product>> productsSetter =
        Setter.fromGetSet(
            Inventory::products, (inv, prods) -> new Inventory(prods, inv.warehouseId()));

    Setter<List<Product>, Product> productListSetter = Setter.forList();

    Setter<Product, BigDecimal> priceSetter =
        Setter.fromGetSet(
            Product::price, (p, price) -> new Product(p.name(), price, p.stock(), p.tags()));

    // Compose: Inventory → List<Product> → Product
    Setter<Inventory, Product> allProductsSetter = productsSetter.andThen(productListSetter);

    Inventory inventory =
        new Inventory(
            List.of(
                new Product("Laptop", new BigDecimal("999.99"), 50, List.of("electronics")),
                new Product("Keyboard", new BigDecimal("79.99"), 100, List.of("accessories")),
                new Product("Monitor", new BigDecimal("299.99"), 30, List.of("displays"))),
            "WH-001");

    // Apply 10% discount to all products, rounded to the penny
    Function<BigDecimal, BigDecimal> tenPercentOff =
        price -> price.multiply(new BigDecimal("0.9")).setScale(2, RoundingMode.HALF_EVEN);

    Inventory discounted =
        allProductsSetter.modify(product -> priceSetter.modify(tenPercentOff, product), inventory);
    // the prices are now 899.99, 71.99 and 269.99

    // Restock all products
    Setter<Product, Integer> stockSetter =
        Setter.fromGetSet(
            Product::stock, (p, stock) -> new Product(p.name(), p.price(), stock, p.tags()));

    Inventory restocked =
        allProductsSetter.modify(
            product -> stockSetter.modify(stock -> stock + 10, product), inventory);
    // the stock levels are now 60, 110 and 40
    // ANCHOR_END: nested
    return List.of(discounted, restocked);
  }

  static List<Optional<User>> modifyF(UserSettings settings) {
    // ANCHOR: modify_f
    Setter<User, String> usernameSetter =
        Setter.fromGetSet(
            User::username, (u, name) -> new User(name, u.email(), u.loginCount(), u.settings()));

    // Validation: username must be at least 3 characters and lowercase
    Function<String, Kind<OptionalKind.Witness, String>> validateUsername =
        username -> {
          if (username.length() >= 3 && username.matches("[a-z_]+")) {
            return OptionalKindHelper.OPTIONAL.widen(Optional.of(username));
          } else {
            return OptionalKindHelper.OPTIONAL.widen(Optional.empty());
          }
        };

    User validUser = new User("john_doe", "john@example.com", 10, settings);
    Kind<OptionalKind.Witness, User> result =
        usernameSetter.modifyF(validateUsername, validUser, Instances.monadError(optional()));

    Optional<User> validated = OptionalKindHelper.OPTIONAL.narrow(result);
    // Optional[User[username=john_doe, email=john@example.com, loginCount=10, settings=...]]

    User invalidUser = new User("ab", "a@test.com", 0, settings); // Too short
    Kind<OptionalKind.Witness, User> invalidResult =
        usernameSetter.modifyF(validateUsername, invalidUser, Instances.monadError(optional()));

    Optional<User> invalidValidated = OptionalKindHelper.OPTIONAL.narrow(invalidResult);
    // Optional.empty: "ab" fails the validation
    // ANCHOR_END: modify_f
    return List.of(validated, invalidValidated);
  }

  static List<Optional<List<Integer>>> sequencing() {
    // ANCHOR: sequencing
    Setter<List<Integer>, Integer> listSetter = Setter.forList();

    List<Integer> numbers = List.of(1, 2, 3);

    Function<Integer, Kind<OptionalKind.Witness, Integer>> doubleIfPositive =
        n -> {
          if (n > 0) {
            return OptionalKindHelper.OPTIONAL.widen(Optional.of(n * 2));
          } else {
            return OptionalKindHelper.OPTIONAL.widen(Optional.empty());
          }
        };

    Kind<OptionalKind.Witness, List<Integer>> result =
        listSetter.modifyF(doubleIfPositive, numbers, Instances.monadError(optional()));

    Optional<List<Integer>> doubled = OptionalKindHelper.OPTIONAL.narrow(result);
    // Optional[[2, 4, 6]]

    // With negative number (will fail)
    List<Integer> withNegative = List.of(1, -2, 3);
    Kind<OptionalKind.Witness, List<Integer>> failedResult =
        listSetter.modifyF(doubleIfPositive, withNegative, Instances.monadError(optional()));

    Optional<List<Integer>> failed = OptionalKindHelper.OPTIONAL.narrow(failedResult);
    // Optional.empty: -2 fails, so the whole list does
    // ANCHOR_END: sequencing
    return List.of(doubled, failed);
  }

  static List<String> identity() {
    // ANCHOR: identity
    Setter<String, String> identitySetter = Setter.identity();

    String result = identitySetter.modify(String::toUpperCase, "hello");
    // "HELLO"

    String replaced = identitySetter.set("world", "hello");
    // "world"
    // ANCHOR_END: identity
    return List.of(result, replaced);
  }

  static List<Product> normalise() {
    // ANCHOR: normalise
    Setter<List<Product>, Product> productSetter = Setter.forList();
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
            new Product("  LAPTOP  ", new BigDecimal("999.99"), 50, List.of()),
            new Product("keyboard", new BigDecimal("79.99"), 100, List.of()),
            new Product("MONITOR", new BigDecimal("299.99"), 30, List.of()));

    List<Product> normalised =
        productSetter.modify(product -> nameSetter.modify(normalise, product), rawProducts);
    // [Product[name=Laptop, ...], Product[name=Keyboard, ...], Product[name=Monitor, ...]]
    // ANCHOR_END: normalise
    return normalised;
  }
}

/** The page's user. */
@GenerateSetters
record User(String username, String email, int loginCount, UserSettings settings) {}

/** The page's user settings. */
@GenerateSetters
record UserSettings(
    String theme, boolean notifications, int fontSize, Map<String, String> preferences) {}

/** The page's product: its price is money, so it is a {@code BigDecimal}. */
@GenerateSetters
record Product(String name, BigDecimal price, int stock, List<String> tags) {}

/** The page's inventory of products. */
@GenerateSetters
record Inventory(List<Product> products, String warehouseId) {}
