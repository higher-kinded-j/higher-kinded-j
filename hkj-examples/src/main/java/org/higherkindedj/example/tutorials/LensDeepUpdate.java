// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.tutorials;

import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * Demonstrates deep immutable updates using Lens composition.
 *
 * <p>This example showcases the power of optics for working with nested immutable data structures.
 * Rather than writing verbose "copy-and-update" cascades, lenses let you express updates concisely
 * and safely.
 *
 * <h2>The Problem: Verbose Nested Updates</h2>
 *
 * <p>With plain Java records, updating a deeply nested field requires creating new instances at
 * every level:
 *
 * <pre>{@code
 * // Update user's street name (4 levels deep)
 * User updated = new User(
 *     user.name(),
 *     new Profile(
 *         user.profile().bio(),
 *         new Address(
 *             new Street("New Street", user.profile().address().street().number()),
 *             user.profile().address().city()
 *         )
 *     )
 * );
 * }</pre>
 *
 * <p>This is:
 *
 * <ul>
 *   <li>Error-prone (easy to copy wrong fields)
 *   <li>Difficult to read (nested constructor calls)
 *   <li>Not reusable (can't extract the "path" logic)
 *   <li>Scales poorly (exponentially worse with depth)
 * </ul>
 *
 * <h2>The Solution: Lens Composition</h2>
 *
 * <p>Lenses provide a composable, reusable abstraction for field access:
 *
 * <pre>{@code
 * // Define the path once
 * Lens<User, String> userToStreetName = UserLenses.profile()
 *     .andThen(ProfileLenses.address())
 *     .andThen(AddressLenses.street())
 *     .andThen(StreetLenses.name());
 *
 * // Use it anywhere
 * User updated = userToStreetName.set("New Street", user);
 * }</pre>
 *
 * <p>This is:
 *
 * <ul>
 *   <li>Type-safe (compiler checks the path)
 *   <li>Readable (expresses intent clearly)
 *   <li>Reusable (the path is a first-class value)
 *   <li>Composable (build complex paths from simple pieces)
 * </ul>
 *
 * <h2>Related Tutorials</h2>
 *
 * <ul>
 *   <li>Optics Tutorial 01: Lens Basics
 *   <li>Optics Tutorial 02: Lens Composition
 *   <li>Optics Tutorial 06: Generated Optics
 * </ul>
 *
 * @see <a href="https://higher-kinded-j.github.io/optics/lenses.html">Lenses Guide</a>
 * @see <a href="https://higher-kinded-j.github.io/optics/composing_optics.html">Composing
 *     Optics</a>
 */
public final class LensDeepUpdate {

  private LensDeepUpdate() {
    // Utility class - no instantiation
  }

  // ============================================================================
  // Domain Model (with @GenerateLenses annotations)
  // ============================================================================

  /** Represents a street address. */
  @GenerateLenses
  public record Street(String name, int number) {}

  /** Represents a full postal address. */
  @GenerateLenses
  public record Address(Street street, String city, String postalCode) {}

  /** Represents a user's profile information. */
  @GenerateLenses
  public record Profile(String bio, Address address, String phoneNumber) {}

  /** Represents a complete user entity. */
  @GenerateLenses
  public record User(String name, String email, Profile profile) {}

  // ============================================================================
  // Demonstration
  // ============================================================================

  /**
   * Demonstrates lens composition for deep updates.
   *
   * @param args Command line arguments (unused)
   */
  public static void main(String[] args) {
    System.out.println("=== Lens Deep Update Examples ===\n");

    // Create a user with nested structure
    User user =
        new User(
            "Alice",
            "alice@example.com",
            new Profile(
                "Software engineer",
                new Address(new Street("Main St", 123), "Springfield", "12345"),
                "+1-555-0100"));

    System.out.println("Original User:");
    printUser(user);

    // Example 1: Update street name (4 levels deep)
    demonstrateStreetNameUpdate(user);

    // Example 2: Update multiple nested fields
    demonstrateMultipleUpdates(user);

    // Example 3: Reusable lens paths
    demonstrateReusableLenses(user);

    System.out.println("\n=== Key Takeaway ===");
    System.out.println("Lenses turn nested updates from:");
    System.out.println("  new Outer(new Middle(new Inner(newValue))) ❌");
    System.out.println("Into:");
    System.out.println("  composedLens.set(newValue, object) ✓");
  }

  /**
   * Demonstrates updating a deeply nested field (street name) using lens composition.
   *
   * <p>This showcases the primary value proposition of lenses: deep updates become simple, one-line
   * operations.
   */
  private static void demonstrateStreetNameUpdate(User original) {
    System.out.println("\n1. Deep Update: Change street name");

    // Compose lenses to create a path to the street name
    Lens<User, String> userToStreetName =
        UserLenses.profile()
            .andThen(ProfileLenses.address())
            .andThen(AddressLenses.street())
            .andThen(StreetLenses.name());

    // Update using the composed lens
    User updated = userToStreetName.set("Oak Avenue", original);

    System.out.println("  Path: User → Profile → Address → Street → name");
    System.out.println("  Old value: " + userToStreetName.get(original));
    System.out.println("  New value: " + userToStreetName.get(updated));
    System.out.println("  Original unchanged: " + userToStreetName.get(original));
  }

  /**
   * Demonstrates updating multiple nested fields independently.
   *
   * <p>Each lens operates independently. You can compose different paths and apply them
   * sequentially or use one lens multiple times.
   */
  private static void demonstrateMultipleUpdates(User original) {
    System.out.println("\n2. Multiple Updates: Change city and postal code");

    // Create paths to different fields
    Lens<User, String> userToCity =
        UserLenses.profile().andThen(ProfileLenses.address()).andThen(AddressLenses.city());

    Lens<User, String> userToPostalCode =
        UserLenses.profile().andThen(ProfileLenses.address()).andThen(AddressLenses.postalCode());

    // Apply updates sequentially
    User updated = userToCity.set("Shelbyville", original);
    updated = userToPostalCode.set("54321", updated);

    System.out.println("  Updated city: " + userToCity.get(updated));
    System.out.println("  Updated postal code: " + userToPostalCode.get(updated));
    System.out.println("  Other fields preserved: " + UserLenses.name().get(updated));
  }

  /**
   * Demonstrates defining reusable lens paths.
   *
   * <p>Lenses are first-class values. You can define them once and reuse them throughout your
   * codebase, improving maintainability and reducing duplication.
   */
  private static void demonstrateReusableLenses(User original) {
    System.out.println("\n3. Reusable Lenses: Extract common paths");

    // Define commonly-used paths as constants
    Lens<User, Address> userToAddress = UserLenses.profile().andThen(ProfileLenses.address());

    Lens<User, Street> userToStreet = userToAddress.andThen(AddressLenses.street());

    // Use the same lens for both reading and writing
    Street currentStreet = userToStreet.get(original);
    System.out.println("  Current street: " + currentStreet);

    // Modify the street using Lens.modify (applies a function)
    User updated =
        userToStreet.modify(street -> new Street(street.name(), street.number() + 100), original);

    System.out.println("  Modified street number: " + userToStreet.get(updated).number());
    System.out.println("  (Original street number: " + currentStreet.number() + ")");
  }

  /** Helper method to print user details. */
  private static void printUser(User user) {
    System.out.println("  Name: " + user.name());
    System.out.println("  Email: " + user.email());
    System.out.println("  Bio: " + user.profile().bio());
    System.out.println("  Street: " + user.profile().address().street().name());
    System.out.println("  City: " + user.profile().address().city());
  }
}
