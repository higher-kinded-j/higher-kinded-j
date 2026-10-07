// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.setters;

// ANCHOR: imports
import static org.higherkindedj.hkt.instances.Witnesses.optional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.optics.Setter;

// ANCHOR_END: imports

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/setters.html">Setters</a> page, as its
 * complete, runnable example. The page {@code {{#include}}}s the anchored region, and shows what
 * {@code main} prints from a golden file the book's output gate holds to it.
 */
// ANCHOR: setter_example
public class SetterExample {

  public record User(String username, String email, int loginCount, UserSettings settings) {}

  // A trimmed UserSettings, so the runnable example stays small
  public record UserSettings(String theme, boolean notifications, int fontSize) {}

  public record Product(String name, BigDecimal price, int stock) {}

  public static void main(String[] args) {
    // === Basic Setters ===
    Setter<User, String> usernameSetter =
        Setter.fromGetSet(
            User::username, (u, name) -> new User(name, u.email(), u.loginCount(), u.settings()));

    Setter<User, Integer> loginCountSetter =
        Setter.fromGetSet(
            User::loginCount, (u, count) -> new User(u.username(), u.email(), count, u.settings()));

    UserSettings settings = new UserSettings("light", true, 14);
    User user = new User("JOHN_DOE", "john@example.com", 10, settings);

    // Normalise username
    User normalised = usernameSetter.modify(String::toLowerCase, user);
    System.out.println("Normalised: " + normalised.username());

    // Increment login count
    User incremented = loginCountSetter.modify(count -> count + 1, user);
    System.out.println("Login count: " + incremented.loginCount());

    // === Composition ===
    Setter<User, UserSettings> settingsSetter =
        Setter.fromGetSet(
            User::settings, (u, s) -> new User(u.username(), u.email(), u.loginCount(), s));

    Setter<UserSettings, String> themeSetter =
        Setter.fromGetSet(
            UserSettings::theme,
            (s, theme) -> new UserSettings(theme, s.notifications(), s.fontSize()));

    Setter<User, String> userThemeSetter = settingsSetter.andThen(themeSetter);

    User darkMode = userThemeSetter.set("dark", user);
    System.out.println("Theme: " + darkMode.settings().theme());

    // === Collection Setters ===
    Setter<List<Integer>, Integer> listSetter = Setter.forList();

    List<Integer> numbers = List.of(1, 2, 3, 4, 5);
    List<Integer> doubled = listSetter.modify(x -> x * 2, numbers);
    System.out.println("Doubled: " + doubled);

    // === Product Batch Update ===
    Setter<Product, BigDecimal> priceSetter =
        Setter.fromGetSet(Product::price, (p, price) -> new Product(p.name(), price, p.stock()));

    Setter<List<Product>, Product> productsSetter = Setter.forList();

    List<Product> products =
        List.of(
            new Product("Laptop", new BigDecimal("999.99"), 50),
            new Product("Keyboard", new BigDecimal("79.99"), 100),
            new Product("Monitor", new BigDecimal("299.99"), 30));

    // Apply 10% discount, rounded to the penny
    Function<BigDecimal, BigDecimal> tenPercentOff =
        price -> price.multiply(new BigDecimal("0.9")).setScale(2, RoundingMode.HALF_EVEN);

    List<Product> discounted =
        productsSetter.modify(product -> priceSetter.modify(tenPercentOff, product), products);

    System.out.println("Discounted prices:");
    for (Product p : discounted) {
      System.out.println("  " + p.name() + ": £" + p.price());
    }

    // === Effectful Modification ===
    Function<String, Kind<OptionalKind.Witness, String>> validateUsername =
        username -> {
          if (username.length() >= 3 && username.matches("[a-z_]+")) {
            return OptionalKindHelper.OPTIONAL.widen(Optional.of(username));
          } else {
            return OptionalKindHelper.OPTIONAL.widen(Optional.empty());
          }
        };

    User validUser = new User("john_doe", "john@example.com", 10, settings);
    Kind<OptionalKind.Witness, User> validResult =
        usernameSetter.modifyF(validateUsername, validUser, Instances.monadError(optional()));

    Optional<User> validated = OptionalKindHelper.OPTIONAL.narrow(validResult);
    System.out.println("Valid username: " + validated.map(User::username).orElse("INVALID"));

    User invalidUser = new User("ab", "a@test.com", 0, settings);
    Kind<OptionalKind.Witness, User> invalidResult =
        usernameSetter.modifyF(validateUsername, invalidUser, Instances.monadError(optional()));

    Optional<User> invalidValidated = OptionalKindHelper.OPTIONAL.narrow(invalidResult);
    System.out.println(
        "Invalid username: " + invalidValidated.map(User::username).orElse("INVALID"));

    // === Data Normalisation ===
    Setter<Product, String> nameSetter =
        Setter.fromGetSet(Product::name, (p, name) -> new Product(name, p.price(), p.stock()));

    Function<String, String> titleCase =
        name -> {
          String trimmed = name.trim();
          return trimmed.substring(0, 1).toUpperCase() + trimmed.substring(1).toLowerCase();
        };

    List<Product> rawProducts =
        List.of(
            new Product("  LAPTOP  ", new BigDecimal("999.99"), 50),
            new Product("keyboard", new BigDecimal("79.99"), 100),
            new Product("MONITOR", new BigDecimal("299.99"), 30));

    List<Product> normalisedProducts =
        productsSetter.modify(product -> nameSetter.modify(titleCase, product), rawProducts);

    System.out.println("Normalised product names:");
    for (Product p : normalisedProducts) {
      System.out.println("  - " + p.name());
    }
  }
}
// ANCHOR_END: setter_example
