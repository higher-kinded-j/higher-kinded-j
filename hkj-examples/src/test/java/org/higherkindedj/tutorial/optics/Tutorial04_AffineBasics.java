// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.util.Affines;
import org.higherkindedj.optics.util.Prisms;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 04: Affine Basics - Working with Optional Fields
 *
 * <p>An Affine is an optic that focuses on zero or one element. It combines aspects of both Lens
 * and Prism: like a Lens, it can get and set values; like a Prism, the value might not exist.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>getOptional: extracts the value if present, returns Optional
 *   <li>set: updates the value (always succeeds, even if original was absent)
 *   <li>modify: applies a function if the value is present
 *   <li>Lens + Prism composition: produces an Affine, not a Traversal
 * </ul>
 *
 * <p>When to use:
 *
 * <ul>
 *   <li>Optional fields in records (Optional&lt;T&gt;)
 *   <li>Nullable properties in legacy code
 *   <li>When composing a Lens with a Prism
 *   <li>Any zero-or-one focus that isn't a sum type variant
 * </ul>
 *
 * <p>Affine vs Prism: Both focus on zero-or-one elements, but Prism can "build" a complete
 * structure from a part (like constructing a Circle from its components). Affine cannot build; it
 * can only modify existing structures.
 */
public class Tutorial04_AffineBasics {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // Domain model with optional fields
  record UserProfile(String username, Optional<ContactInfo> contact) {}

  record ContactInfo(String email, Optional<String> phone) {}

  // Configuration with optional sections
  record AppConfig(String appName, Optional<DatabaseConfig> database) {}

  record DatabaseConfig(String host, int port) {}

  /**
   * Exercise 1: Creating an Affine with Affines.some()
   *
   * <p>The Affines utility class provides ready-made affines for common patterns. Affines.some()
   * focuses on the value inside an Optional.
   *
   * <p>Task: Use Affines.some() to extract a value from an Optional
   */
  @Test
  void exercise1_usingSomeAffine() {
    Affine<Optional<String>, String> someAffine = Affines.some();

    Optional<String> present = Optional.of("hello");
    Optional<String> empty = Optional.empty();

    // TODO: Replace null with code that uses someAffine.getOptional()
    // to extract the value from 'present'
    Optional<String> extracted = answerRequired();

    assertThat(extracted).isPresent();
    assertThat(extracted.get()).isEqualTo("hello");

    // Affine returns empty when the Optional is empty
    Optional<String> notPresent = someAffine.getOptional(empty);
    assertThat(notPresent).isEmpty();
  }

  /**
   * Exercise 2: Setting values with an Affine
   *
   * <p>Unlike getOptional which may return empty, set always succeeds. It updates the structure to
   * contain the new value.
   *
   * <p>Task: Use set to update an Optional value
   */
  @Test
  void exercise2_settingWithAffine() {
    Affine<Optional<String>, String> someAffine = Affines.some();

    Optional<String> empty = Optional.empty();

    // TODO: Replace null with code that uses someAffine.set()
    // to set "world" into the empty Optional
    // Hint: someAffine.set("world", empty)
    Optional<String> updated = answerRequired();

    assertThat(updated).isPresent();
    assertThat(updated.get()).isEqualTo("world");
  }

  /**
   * Exercise 3: Composing Lens with Prism to get an Affine
   *
   * <p>When you compose a Lens (exactly one element) with a Prism (zero or one element), the result
   * is an Affine (zero or one element). This is more precise than a Traversal.
   *
   * <p>Task: Create an Affine by composing a Lens with Prisms.some()
   */
  @Test
  void exercise3_lensAndPrismComposition() {
    // Lens to access the optional database field
    Lens<AppConfig, Optional<DatabaseConfig>> databaseLens =
        Lens.of(AppConfig::database, (config, db) -> new AppConfig(config.appName(), db));

    // Prism to extract the value from Optional
    Prism<Optional<DatabaseConfig>, DatabaseConfig> somePrism = Prisms.some();

    // TODO: Replace null with code that composes databaseLens with somePrism
    // Hint: databaseLens.andThen(somePrism)
    Affine<AppConfig, DatabaseConfig> databaseAffine = answerRequired();

    AppConfig configWithDb =
        new AppConfig("MyApp", Optional.of(new DatabaseConfig("localhost", 5432)));
    AppConfig configWithoutDb = new AppConfig("MyApp", Optional.empty());

    // Test getOptional on config with database
    Optional<DatabaseConfig> db1 = databaseAffine.getOptional(configWithDb);
    assertThat(db1).isPresent();
    assertThat(db1.get().host()).isEqualTo("localhost");

    // Test getOptional on config without database
    Optional<DatabaseConfig> db2 = databaseAffine.getOptional(configWithoutDb);
    assertThat(db2).isEmpty();
  }

  /**
   * Exercise 4: Using matches() for presence checking
   *
   * <p>The matches() method provides a convenient way to check if an Affine focuses on a value
   * without extracting it.
   *
   * <p>Task: Use matches() to check if optional fields are present
   */
  @Test
  void exercise4_usingMatches() {
    Affine<Optional<String>, String> someAffine = Affines.some();

    Optional<String> present = Optional.of("value");
    Optional<String> empty = Optional.empty();

    // TODO: Replace false with code that uses someAffine.matches()
    // Hint: someAffine.matches(present)
    boolean hasValue = answerRequired();
    boolean isEmpty = answerRequired();

    assertThat(hasValue).isTrue();
    assertThat(isEmpty).isFalse();
  }

  /**
   * Exercise 5: Using getOrElse() for default values
   *
   * <p>getOrElse() extracts the value if present, or returns a default value.
   *
   * <p>Task: Use getOrElse() to provide default values for missing data
   */
  @Test
  void exercise5_usingGetOrElse() {
    Affine<Optional<String>, String> someAffine = Affines.some();

    Optional<String> present = Optional.of("configured");
    Optional<String> empty = Optional.empty();

    // TODO: Replace null with code that uses someAffine.getOrElse()
    // to get the value or return "default" if empty
    // Hint: someAffine.getOrElse("default", empty)
    String value1 = answerRequired();
    String value2 = answerRequired();

    assertThat(value1).isEqualTo("configured");
    assertThat(value2).isEqualTo("default");
  }

  /**
   * Exercise 6: Modifying values with an Affine
   *
   * <p>The modify() method applies a function to the focused value if present. If the value is
   * absent, the structure is returned unchanged.
   *
   * <p>Task: Use modify() to transform optional values
   */
  @Test
  void exercise6_modifyingWithAffine() {
    Affine<Optional<String>, String> someAffine = Affines.some();

    Optional<String> present = Optional.of("hello");
    Optional<String> empty = Optional.empty();

    // TODO: Replace null with code that uses someAffine.modify()
    // to uppercase the string if present
    // Hint: someAffine.modify(String::toUpperCase, present)
    Optional<String> uppercased = answerRequired();

    assertThat(uppercased).isPresent();
    assertThat(uppercased.get()).isEqualTo("HELLO");

    // Modify on empty returns empty unchanged
    Optional<String> stillEmpty = someAffine.modify(String::toUpperCase, empty);
    assertThat(stillEmpty).isEmpty();
  }

  /**
   * Exercise 7: Chaining Affines for deep optional access
   *
   * <p>Affines compose with other Affines to create paths through multiple layers of optionality.
   *
   * <p>Task: Create an Affine path to access a deeply nested optional field
   */
  @Test
  void exercise7_chainingAffines() {
    // Lenses for the record fields
    Lens<UserProfile, Optional<ContactInfo>> contactLens =
        Lens.of(UserProfile::contact, (u, c) -> new UserProfile(u.username(), c));

    Lens<ContactInfo, Optional<String>> phoneLens =
        Lens.of(ContactInfo::phone, (c, p) -> new ContactInfo(c.email(), p));

    // Prism to extract from Optional
    Prism<Optional<ContactInfo>, ContactInfo> contactPrism = Prisms.some();
    Prism<Optional<String>, String> phonePrism = Prisms.some();

    // TODO: Create an Affine from UserProfile to phone number
    // by composing: contactLens >>> contactPrism >>> phoneLens >>> phonePrism
    // Hint: contactLens.andThen(contactPrism).andThen(phoneLens).andThen(phonePrism)
    Affine<UserProfile, String> userToPhone = answerRequired();

    UserProfile userWithPhone =
        new UserProfile(
            "alice", Optional.of(new ContactInfo("alice@example.com", Optional.of("555-1234"))));

    UserProfile userWithoutPhone =
        new UserProfile("bob", Optional.of(new ContactInfo("bob@example.com", Optional.empty())));

    UserProfile userWithoutContact = new UserProfile("charlie", Optional.empty());

    // Test deep access
    Optional<String> phone1 = userToPhone.getOptional(userWithPhone);
    assertThat(phone1).contains("555-1234");

    Optional<String> phone2 = userToPhone.getOptional(userWithoutPhone);
    assertThat(phone2).isEmpty();

    Optional<String> phone3 = userToPhone.getOptional(userWithoutContact);
    assertThat(phone3).isEmpty();

    // Test deep update
    UserProfile updated = userToPhone.set("555-9999", userWithPhone);
    assertThat(userToPhone.getOptional(updated)).contains("555-9999");
  }

  /**
   * Congratulations! You've completed Tutorial 04: Affine Basics
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>How to use Affines.some() for Optional fields
   *   <li>How Lens + Prism composition produces an Affine
   *   <li>Using getOptional, set, and modify operations
   *   <li>Using matches() for presence checking
   *   <li>Using getOrElse() for default values
   *   <li>Chaining Affines for deep optional access
   *   <li>Why Affine is more precise than Traversal for zero-or-one focus
   * </ul>
   *
   * <p>Key insight: An Affine is the natural result of composing paths that might not exist. It's
   * more precise than a Traversal because it guarantees at most one element, giving you stronger
   * type guarantees.
   *
   * <p>Next: Tutorial 05 - Traversal Basics
   */
}
