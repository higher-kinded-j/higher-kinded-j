// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.junit.jupiter.api.Test;

/**
 * SOLUTION for Tutorial 01: Lens Basics - Immutable Field Access
 *
 * <p>This file contains complete, working solutions for all exercises in Tutorial01_LensBasics.
 *
 * <p>Compare your solutions with these to learn different approaches and best practices.
 */
public class Tutorial01_LensBasics_Solution {

  // Simple person record - we'll manually create lenses for this
  record Person(String name, int age, String email) {}

  // Manually created lens for the 'name' field
  static final Lens<Person, String> nameLens =
      Lens.of(Person::name, (person, newName) -> new Person(newName, person.age(), person.email()));

  // Manually created lens for the 'age' field
  static final Lens<Person, Integer> ageLens =
      Lens.of(Person::age, (person, newAge) -> new Person(person.name(), newAge, person.email()));

  /**
   * Exercise 1: Getting a value with a Lens
   *
   * <p>SOLUTION: Use lens.get(source) to extract the value
   */
  @Test
  void exercise1_gettingValue() {
    Person person = new Person("Alice", 30, "alice@example.com");

    // SOLUTION: Call get() on the lens
    String name = nameLens.get(person);

    assertThat(name).isEqualTo("Alice");
  }

  /**
   * Exercise 2: Setting a value with a Lens
   *
   * <p>SOLUTION: Use lens.set(newValue, source) to create an updated copy
   */
  @Test
  void exercise2_settingValue() {
    Person person = new Person("Alice", 30, "alice@example.com");

    // SOLUTION: set() creates a new Person with the updated name
    Person updated = nameLens.set("Bob", person);

    assertThat(updated.name()).isEqualTo("Bob");
    assertThat(updated.age()).isEqualTo(30); // Other fields unchanged
    assertThat(person.name()).isEqualTo("Alice"); // Original unchanged
  }

  /**
   * Exercise 3: Modifying a value with a Lens
   *
   * <p>SOLUTION: Use lens.modify(function, source) to transform the value
   */
  @Test
  void exercise3_modifyingValue() {
    Person person = new Person("Alice", 30, "alice@example.com");

    // SOLUTION: modify() applies a function to transform the value
    Person updated = ageLens.modify(age -> age + 1, person);

    assertThat(updated.age()).isEqualTo(31);
    assertThat(person.age()).isEqualTo(30); // Original unchanged
  }

  /**
   * Exercise 4: Chaining multiple updates
   *
   * <p>SOLUTION: Chain lens operations by passing the result of one to the next
   */
  @Test
  void exercise4_chainingUpdates() {
    Person person = new Person("Alice", 30, "alice@example.com");

    // SOLUTION: Chain operations - set name first, then modify age
    Person updated = ageLens.modify(age -> age + 5, nameLens.set("Bob", person));

    assertThat(updated.name()).isEqualTo("Bob");
    assertThat(updated.age()).isEqualTo(35);
  }

  /**
   * Exercise 5: Using generated lenses
   *
   * <p>SOLUTION: Generated lenses are accessed via static methods on the {RecordName}Lenses class
   */
  @Test
  void exercise5_generatedLenses() {
    @GenerateLenses
    record Product(String id, String name, double price) {}

    // Manual lens implementations (annotation processor would generate ProductLenses class)
    class ProductLenses {
      public static Lens<Product, String> name() {
        return Lens.of(Product::name, (p, newName) -> new Product(p.id(), newName, p.price()));
      }

      public static Lens<Product, Double> price() {
        return Lens.of(Product::price, (p, newPrice) -> new Product(p.id(), p.name(), newPrice));
      }
    }

    Product product = new Product("PROD-001", "Laptop", 999.99);

    // SOLUTION: Use ProductLenses.name() to access the generated lens
    String name = ProductLenses.name().get(product);

    assertThat(name).isEqualTo("Laptop");

    // SOLUTION: Use ProductLenses.price() with modify to increase the price
    Product updated = ProductLenses.price().modify(p -> p * 1.1, product);

    assertThat(updated.price()).isCloseTo(1099.989, within(0.01));
  }

  /**
   * Exercise 6: Creating a custom lens
   *
   * <p>SOLUTION: Use Lens.of(getter, setter) where setter is a BiFunction
   */
  @Test
  void exercise6_customLens() {
    Person person = new Person("Alice", 30, "alice@example.com");

    // SOLUTION: Create lens with Lens.of(getter, (source, newValue) -> updatedSource)
    Lens<Person, String> emailLens =
        Lens.of(Person::email, (p, newEmail) -> new Person(p.name(), p.age(), newEmail));

    String email = emailLens.get(person);
    assertThat(email).isEqualTo("alice@example.com");

    Person updated = emailLens.set("bob@example.com", person);
    assertThat(updated.email()).isEqualTo("bob@example.com");
  }

  /**
   * Exercise 7: Using modify with method references
   *
   * <p>SOLUTION: Method references like String::toUpperCase work perfectly with modify
   */
  @Test
  void exercise7_methodReferences() {
    Person person = new Person("alice", 30, "alice@example.com");

    // SOLUTION: Pass String::toUpperCase as the transformation function
    Person updated = nameLens.modify(String::toUpperCase, person);

    assertThat(updated.name()).isEqualTo("ALICE");
  }

  /**
   * Congratulations! You've completed Tutorial 01: Lens Basics
   *
   * <p>Key takeaways from the solutions: ✓ Lenses provide get, set, and modify operations ✓
   * Operations are immutable - they create new copies ✓ Generated lenses follow the pattern
   * {RecordName}Lenses.{fieldName}() ✓ Custom lenses use Lens.of(getter, BiFunction setter) ✓
   * Method references work great with modify()
   *
   * <p>Next: Tutorial 02 - Lens Composition
   */
}
