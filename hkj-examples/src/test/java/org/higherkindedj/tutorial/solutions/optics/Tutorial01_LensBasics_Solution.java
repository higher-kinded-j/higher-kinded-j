// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.junit.jupiter.api.Test;

/**
 * Solution for Tutorial 01: Lens Basics — teaching-solution format.
 *
 * <p>Each exercise's Javadoc records:
 *
 * <ul>
 *   <li><b>Why this is idiomatic</b> — what makes the chosen form the standard one
 *   <li><b>Alternative</b> — at least one other shape that also works, with the trade-off
 *   <li><b>Common wrong attempt</b> — a typical first stumble and the symptom it produces
 * </ul>
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
   * Why this is idiomatic: {@code lens.get(source)} reads as "this is the focus on that source" —
   * one method call, no copy, no allocation.
   *
   * <p>Alternative: call the record accessor directly ({@code person.name()}). Same answer; loses
   * the lens reference for downstream composition.
   *
   * <p>Common wrong attempt: passing the source first as in {@code nameLens.get(person,
   * "default")}. {@code Lens.get} takes one argument; for defaulting on absent fields use {@link
   * org.higherkindedj.optics.Affine}.
   */
  @Test
  void exercise1_gettingValue() {
    Person person = new Person("Alice", 30, "alice@example.com");

    // SOLUTION: Call get() on the lens
    String name = nameLens.get(person);

    assertThat(name).isEqualTo("Alice");
  }

  /**
   * Why this is idiomatic: {@code lens.set(newValue, source)} returns a fresh structure; the
   * original is never touched. The assertion against {@code person.name()} at the end is proof.
   *
   * <p>Alternative: {@code lens.modify(ignored -> "Bob", person)}. Same result; loses the "constant
   * value" signal.
   *
   * <p>Common wrong attempt: mutating a non-record holder pretending it is immutable. The lens
   * contract assumes immutability of the focused field; mutation breaks every reasoning principle
   * the optic provides.
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
   * Why this is idiomatic: {@code modify} captures "apply this function to exactly that field"
   * without the get-transform-set ceremony.
   *
   * <p>Alternative: {@code ageLens.set(ageLens.get(person) + 1, person)}. Same answer; three
   * mentions of ageLens instead of one, more places to make a typo.
   *
   * <p>Common wrong attempt: passing the source first, as in {@code ageLens.modify(person, age ->
   * age + 1)}. The contract is function first, then source — symmetric with {@code set}.
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
   * Why this is idiomatic: each lens operation produces a fresh structure that flows into the next.
   * The chain reads inside-out (innermost call runs first), which is the standard functional style.
   *
   * <p>Alternative: extract a local variable for the intermediate result. Same call shape; easier
   * to debug; preferred in production code.
   *
   * <p>Common wrong attempt: trying to apply both updates "at once" by composing functions. Lens
   * chains are sequential — each update flows through the next.
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
   * Why this is idiomatic: the annotation processor generates a {@code ProductLenses} class with
   * one static method per field. We call them like any other static method; the generated code is
   * the same shape as the manual lenses we have been writing.
   *
   * <p>Alternative: use the generated {@code with*} helpers ({@code ProductLenses.withName}). Same
   * outcome; the lens form composes, the helper form does not.
   *
   * <p>Common wrong attempt: trying to use a generated lens for a record defined inside a method
   * body. The annotation processor cannot generate companions for local classes; either move the
   * record to top-level or define lenses manually.
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
   * Why this is idiomatic: {@code Lens.of(getter, setter)} matches the lens contract directly — the
   * getter extracts, the setter rebuilds.
   *
   * <p>Alternative: {@code Lens.of(Person::email, (p, newEmail) -> new Person(p.name(), p.age(),
   * newEmail))} — exactly the form used in the test. The method-reference getter is the cleanest
   * spelling.
   *
   * <p>Common wrong attempt: writing a setter that returns a partially-modified instance (e.g.
   * forgetting to copy one field). The compiler will not catch this; the test will. As a sanity
   * check, lens setters always rebuild every field.
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
   * Why this is idiomatic: any {@code Function<A, A>} works, including method references.
   * Lambda-free spelling reads cleanest when the method already exists.
   *
   * <p>Alternative: a lambda {@code s -> s.toUpperCase()}. Identical, two extra tokens.
   *
   * <p>Common wrong attempt: {@code String::toUpperCase()} (parentheses). That is a method call,
   * not a reference; the compiler will reject it.
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
