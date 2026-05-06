// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 01: Lens Basics — Immutable Field Access.
 *
 * <p>Pain → Promise. Updating one field in an immutable record means rewriting every other field by
 * hand:
 *
 * <pre>
 *   Person updated = new Person(p.name(), p.age() + 1, p.email()); // copy, copy, change
 * </pre>
 *
 * <p>A {@link Lens} captures the get/set pair for one field as a value we can pass around, compose,
 * and reuse:
 *
 * <pre>
 *   Person updated = PersonLenses.age().modify(age -&gt; age + 1, p);
 * </pre>
 *
 * <p>Java idiom anchor:
 *
 * <ul>
 *   <li>{@code lens.get(s)} ↔ a getter (record accessor).
 *   <li>{@code lens.set(a, s)} ↔ a {@code with*} method on a builder, but type-safe and composable.
 *   <li>{@code lens.modify(fn, s)} ↔ {@code source.toBuilder().field(fn(source.field())).build()}
 *       except as a single first-class operation.
 *   <li>{@link GenerateLenses} ↔ Lombok's {@code @Wither}/Builder, but generates an actual {@code
 *       Lens} value rather than methods.
 * </ul>
 *
 * <p>Key concepts:
 *
 * <ul>
 *   <li>{@code get} extracts a value; {@code set} returns a new structure; {@code modify} applies a
 *       function. The original is never mutated.
 *   <li>Lenses compose with {@code andThen} into deeper paths (Tutorial 02).
 * </ul>
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
   * Exercise 1: Getting a value with a Lens.
   *
   * <p>The {@code get} method extracts a field value from a structure.
   *
   * <pre>
   *   // Nudge:    A Lens has a get method that takes a source and returns the focused value.
   *   // Strategy: nameLens.get(person)
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 1: lens.get extracts the focused field")
  void exercise1_gettingValue() {
    Person person = new Person("Alice", 30, "alice@example.com");

    String name = answerRequired();

    assertThat(name).isEqualTo("Alice");
  }

  /**
   * Exercise 2: Setting a value with a Lens.
   *
   * <p>The {@code set} method creates a NEW structure with the field updated; the original is
   * untouched.
   *
   * <pre>
   *   // Nudge:    set takes the new value first, then the source.
   *   // Strategy: nameLens.set("Bob", person)
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 2: lens.set returns a new structure; original is untouched")
  void exercise2_settingValue() {
    Person person = new Person("Alice", 30, "alice@example.com");

    Person updated = answerRequired();

    assertThat(updated.name()).isEqualTo("Bob");
    assertThat(updated.age()).isEqualTo(30); // Other fields unchanged
    assertThat(person.name()).isEqualTo("Alice"); // Original unchanged
  }

  /**
   * Exercise 3: Modifying a value with a Lens.
   *
   * <p>{@code modify} applies a function to transform the focused value.
   *
   * <pre>
   *   // Nudge:    modify takes the function first, then the source.
   *   // Strategy: ageLens.modify(age -&gt; age + 1, person)
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 3: lens.modify applies a function to the focused field")
  void exercise3_modifyingValue() {
    Person person = new Person("Alice", 30, "alice@example.com");

    Person updated = answerRequired();

    assertThat(updated.age()).isEqualTo(31);
    assertThat(person.age()).isEqualTo(30); // Original unchanged
  }

  /**
   * Exercise 4: Chaining multiple updates.
   *
   * <p>Apply one lens operation then feed the result into the next; each returns a fresh structure.
   *
   * <pre>
   *   // Nudge:    Two updates -&gt; two lens calls in sequence.
   *   // Strategy: ageLens.modify(a -&gt; a + 5, nameLens.set("Bob", person))
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 4: chain multiple lens updates")
  void exercise4_chainingUpdates() {
    Person person = new Person("Alice", 30, "alice@example.com");

    Person updated = answerRequired();

    assertThat(updated.name()).isEqualTo("Bob");
    assertThat(updated.age()).isEqualTo(35);
  }

  /**
   * Exercise 5: Using generated lenses.
   *
   * <p>The {@link GenerateLenses} annotation creates a companion class with one lens per field. In
   * real projects we always reach for the generated form.
   *
   * <pre>
   *   // Nudge:    The processor generates ProductLenses.name() / .price() / .id().
   *   // Strategy: ProductLenses.name().get(product)
   *   //           ProductLenses.price().modify(p -&gt; p * 1.1, product)
   *   // Spoiler:  see hint above.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 5: generated lenses via @GenerateLenses")
  void exercise5_generatedLenses() {
    @GenerateLenses
    record Product(String id, String name, double price) {}

    Product product = new Product("PROD-001", "Laptop", 999.99);

    String name = answerRequired();

    assertThat(name).isEqualTo("Laptop");

    Product updated = answerRequired();

    assertThat(updated.price()).isCloseTo(1099.989, within(0.01));
  }

  /**
   * Exercise 6: Creating a custom lens.
   *
   * <p>Manual lenses are rare in production (use {@code @GenerateLenses}) but useful for unusual
   * cases — derived fields, third-party records the processor cannot reach.
   *
   * <pre>
   *   // Nudge:    Lens.of takes a getter and a (source, newValue) -&gt; newSource setter.
   *   // Strategy: Lens.of(Person::email, (p, e) -&gt; new Person(p.name(), p.age(), e))
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 6: build a custom lens with Lens.of")
  void exercise6_customLens() {
    Person person = new Person("Alice", 30, "alice@example.com");

    Lens<Person, String> emailLens = answerRequired();

    String email = emailLens.get(person);
    assertThat(email).isEqualTo("alice@example.com");

    Person updated = emailLens.set("bob@example.com", person);
    assertThat(updated.email()).isEqualTo("bob@example.com");
  }

  /**
   * Exercise 7: {@code modify} with method references.
   *
   * <p>Any {@code Function<A, A>} works; method references read cleanest.
   *
   * <pre>
   *   // Nudge:    String has a toUpperCase method.
   *   // Strategy: nameLens.modify(String::toUpperCase, person)
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 7: lens.modify with a method reference")
  void exercise7_methodReferences() {
    Person person = new Person("alice", 30, "alice@example.com");

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
