// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.cookbook;

import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * Cookbook recipes for deep immutable updates on nested structures.
 *
 * <p>Problem: Updating deeply nested fields in immutable records requires verbose reconstruction.
 *
 * <p>Solution: Compose lenses to create a path, then use {@code set} or {@code modify}.
 */
public class DeepUpdateRecipes {

  // --- Domain Model ---
  @GenerateLenses
  public record User(String name, Address address) {}

  @GenerateLenses
  public record Address(Street street, String city, String postcode) {}

  @GenerateLenses
  public record Street(String name, int number) {}

  public static void main(String[] args) {
    System.out.println("=== Deep Update Recipes ===\n");

    recipeUpdateDeeplyNestedField();
    recipeModifyWithFunction();
    recipeComposeReusablePaths();
  }

  /**
   * Recipe: Update a deeply nested field.
   *
   * <p>Pattern: {@code path.set(newValue, source)}
   */
  private static void recipeUpdateDeeplyNestedField() {
    System.out.println("--- Recipe: Update Deeply Nested Field ---");

    // The verbose way (without optics):
    User user = new User("Alice", new Address(new Street("High Street", 42), "London", "SW1A 1AA"));

    // Without optics (error-prone, verbose):
    User verboseUpdate =
        new User(
            user.name(),
            new Address(
                new Street("New Street", user.address().street().number()),
                user.address().city(),
                user.address().postcode()));

    System.out.println("Without optics: " + verboseUpdate);

    // With optics (clear, composable):
    Lens<User, Address> addressLens = Lens.of(User::address, (u, addr) -> new User(u.name(), addr));
    Lens<Address, Street> streetLens =
        Lens.of(Address::street, (a, s) -> new Address(s, a.city(), a.postcode()));
    Lens<Street, String> streetNameLens =
        Lens.of(Street::name, (s, name) -> new Street(name, s.number()));

    // Compose into a single path
    Lens<User, String> userStreetName = addressLens.andThen(streetLens).andThen(streetNameLens);

    User cleanUpdate = userStreetName.set("New Street", user);
    System.out.println("With optics: " + cleanUpdate);
    System.out.println();
  }

  /**
   * Recipe: Modify a nested field with a function.
   *
   * <p>Pattern: {@code path.modify(function, source)}
   */
  private static void recipeModifyWithFunction() {
    System.out.println("--- Recipe: Modify with Function ---");

    User user = new User("Bob", new Address(new Street("Baker Street", 221), "London", "NW1 6XE"));

    Lens<User, Address> addressLens = Lens.of(User::address, (u, addr) -> new User(u.name(), addr));
    Lens<Address, Street> streetLens =
        Lens.of(Address::street, (a, s) -> new Address(s, a.city(), a.postcode()));
    Lens<Street, Integer> streetNumberLens =
        Lens.of(Street::number, (s, num) -> new Street(s.name(), num));

    Lens<User, Integer> userStreetNumber =
        addressLens.andThen(streetLens).andThen(streetNumberLens);

    // Increment the street number
    User updated = userStreetNumber.modify(n -> n + 1, user);

    System.out.println("Original: " + user);
    System.out.println("Number incremented: " + updated);
    System.out.println();
  }

  /**
   * Recipe: Create reusable composed paths.
   *
   * <p>Pattern: Store composed lenses as constants for reuse.
   */
  private static void recipeComposeReusablePaths() {
    System.out.println("--- Recipe: Reusable Composed Paths ---");

    // Define paths once
    Lens<User, Address> addressLens = Lens.of(User::address, (u, addr) -> new User(u.name(), addr));
    Lens<Address, String> cityLens =
        Lens.of(Address::city, (a, c) -> new Address(a.street(), c, a.postcode()));
    Lens<Address, String> postcodeLens =
        Lens.of(Address::postcode, (a, p) -> new Address(a.street(), a.city(), p));

    // Reusable paths
    Lens<User, String> userCity = addressLens.andThen(cityLens);
    Lens<User, String> userPostcode = addressLens.andThen(postcodeLens);

    User user = new User("Charlie", new Address(new Street("Main St", 1), "Manchester", "M1 1AA"));

    // Use paths multiple times
    User relocated = userCity.set("Edinburgh", user);
    relocated = userPostcode.set("EH1 1AA", relocated);

    System.out.println("Original: " + user);
    System.out.println("Relocated: " + relocated);
    System.out.println();
  }
}
