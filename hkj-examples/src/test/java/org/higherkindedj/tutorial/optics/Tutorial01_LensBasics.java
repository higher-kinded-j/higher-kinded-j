// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 01: Lens Basics - Immutable Field Access
 *
 * <p>A Lens is an optic that focuses on a single field within a larger structure. It provides a
 * functional way to get and set values immutably.
 *
 * <p>Key Concepts: - get: extracts the value from a structure - set: creates a new structure with
 * an updated value - modify: applies a function to transform the value - Immutable: original
 * structure is never modified
 *
 * <p>Why Lenses? - Avoid verbose record copying (new Person(p.name(), p.age() + 1, p.email())) -
 * Type-safe field access - Composable (we'll see this in the next tutorial)
 */
public class Tutorial01_LensBasics {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  /*
   * ========================================================================
   * IMPORTANT: Manual Lens Implementation (For Educational Purposes Only)
   * ========================================================================
   *
   * In this introductory tutorial, we manually create lenses to help you understand the basics.
   * This is ONLY for learning - in real projects, NEVER write these manually!
   *
   * What you should do in real projects:
   * ────────────────────────────────────────────────────────────────────────
   * 1. Annotate your records with @GenerateLenses
   * 2. The annotation processor automatically generates optimized lenses
   * 3. Use the generated lenses from companion classes (e.g., PersonLenses.name())
   *
   * Example of real-world usage:
   *
   *   @GenerateLenses
   *   record Person(String name, int age, String email) {}
   *
   *   // The processor generates:
   *   // - PersonLenses.name()   -> Lens<Person, String>
   *   // - PersonLenses.age()    -> Lens<Person, Integer>
   *   // - PersonLenses.email()  -> Lens<Person, String>
   *
   *   // Usage:
   *   Person updated = PersonLenses.age().modify(age -> age + 1, person);
   *
   * Why we show manual implementations here:
   * ────────────────────────────────────────────────────────────────────────
   * - Understanding how Lenses work "under the hood" makes you a better user
   * - You'll appreciate what the annotation processor does for you
   * - Helpful for debugging or when you need custom lenses for special cases
   *
   * Note: You'll see generated lenses in action in Exercise 5 and Tutorial 06!
   */

  // Simple person record - we'll manually create lenses for this (FOR LEARNING ONLY)
  record Person(String name, int age, String email) {}

  // Manually created lens for the 'name' field (simulating what @GenerateLenses creates)
  static final Lens<Person, String> nameLens =
      Lens.of(Person::name, (person, newName) -> new Person(newName, person.age(), person.email()));

  // Manually created lens for the 'age' field (simulating what @GenerateLenses creates)
  static final Lens<Person, Integer> ageLens =
      Lens.of(Person::age, (person, newAge) -> new Person(person.name(), newAge, person.email()));

  /**
   * Exercise 1: Getting a value with a Lens
   *
   * <p>The get method extracts a field value from a structure.
   *
   * <p>Task: Use a lens to get the name from a person
   */
  @Test
  void exercise1_gettingValue() {
    Person person = new Person("Alice", 30, "alice@example.com");

    // TODO: Replace null with code that uses nameLens.get() to extract the name
    String name = answerRequired();

    assertThat(name).isEqualTo("Alice");
  }

  /**
   * Exercise 2: Setting a value with a Lens
   *
   * <p>The set method creates a NEW structure with the field updated. The original is unchanged.
   *
   * <p>Task: Use a lens to update the name
   */
  @Test
  void exercise2_settingValue() {
    Person person = new Person("Alice", 30, "alice@example.com");

    // TODO: Replace null with code that uses nameLens.set() to change the name to "Bob"
    // Hint: nameLens.set("Bob", person)
    Person updated = answerRequired();

    assertThat(updated.name()).isEqualTo("Bob");
    assertThat(updated.age()).isEqualTo(30); // Other fields unchanged
    assertThat(person.name()).isEqualTo("Alice"); // Original unchanged
  }

  /**
   * Exercise 3: Modifying a value with a Lens
   *
   * <p>The modify method applies a function to transform the value.
   *
   * <p>Task: Use a lens to increment the age by 1
   */
  @Test
  void exercise3_modifyingValue() {
    Person person = new Person("Alice", 30, "alice@example.com");

    // TODO: Replace null with code that uses ageLens.modify() to increment the age
    // Hint: ageLens.modify(age -> age + 1, person)
    Person updated = answerRequired();

    assertThat(updated.age()).isEqualTo(31);
    assertThat(person.age()).isEqualTo(30); // Original unchanged
  }

  /**
   * Exercise 4: Chaining multiple updates
   *
   * <p>You can chain multiple lens operations to update multiple fields.
   *
   * <p>Task: Update both name and age
   */
  @Test
  void exercise4_chainingUpdates() {
    Person person = new Person("Alice", 30, "alice@example.com");

    // TODO: Replace null with code that:
    // 1. Updates the name to "Bob"
    // 2. Then increments the age by 5
    // Hint: Chain two lens operations - nameLens.set(...) then ageLens.modify(...)
    Person updated = answerRequired();

    assertThat(updated.name()).isEqualTo("Bob");
    assertThat(updated.age()).isEqualTo(35);
  }

  /**
   * Exercise 5: Using generated lenses
   *
   * <p>The @GenerateLenses annotation automatically creates lens instances for record fields.
   *
   * <p>Task: Use generated lenses to access and modify fields
   */
  @Test
  void exercise5_generatedLenses() {
    @GenerateLenses
    record Product(String id, String name, double price) {}

    Product product = new Product("PROD-001", "Laptop", 999.99);

    // TODO: Replace null with code that uses the generated ProductLenses.name() lens
    // to get the product name
    String name = answerRequired();

    assertThat(name).isEqualTo("Laptop");

    // TODO: Replace null with code that uses ProductLenses.price() lens
    // to increase the price by 10%
    // Hint: ProductLenses.price().modify(p -> p * 1.1, product)
    Product updated = answerRequired();

    assertThat(updated.price()).isCloseTo(1099.989, within(0.01));
  }

  /**
   * Exercise 6: Creating a custom lens
   *
   * <p>You can create your own lenses manually using Lens.of()
   *
   * <p>Task: Create a lens for the email field
   */
  @Test
  void exercise6_customLens() {
    Person person = new Person("Alice", 30, "alice@example.com");

    // TODO: Replace null with code that creates a lens for the email field
    // Hint: Lens.of(Person::email, newEmail -> person -> new Person(...))
    Lens<Person, String> emailLens = answerRequired();

    String email = emailLens.get(person);
    assertThat(email).isEqualTo("alice@example.com");

    Person updated = emailLens.set("bob@example.com", person);
    assertThat(updated.email()).isEqualTo("bob@example.com");
  }

  /**
   * Exercise 7: Using modify with method references
   *
   * <p>Method references work great with lens.modify()
   *
   * <p>Task: Use a method reference to transform a string field
   */
  @Test
  void exercise7_methodReferences() {
    Person person = new Person("alice", 30, "alice@example.com");

    // TODO: Replace null with code that uses nameLens.modify() with String::toUpperCase
    // to capitalise the name
    Person updated = answerRequired();

    assertThat(updated.name()).isEqualTo("ALICE");
  }

  /**
   * Congratulations! You've completed Tutorial 01: Lens Basics
   *
   * <p>You now understand: ✓ How to use get to extract values ✓ How to use set to update values
   * immutably ✓ How to use modify to transform values ✓ That the original structure is never
   * modified ✓ How to use @GenerateLenses for automatic lens creation ✓ How to create custom lenses
   * manually
   *
   * <p>Next: Tutorial 02 - Lens Composition
   */
}
