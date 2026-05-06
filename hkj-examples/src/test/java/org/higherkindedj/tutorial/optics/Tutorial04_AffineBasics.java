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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 04: Affine Basics — focusing on zero-or-one elements.
 *
 * <p>Pain → Promise. An {@link Optional} field inside a record needs three lines of code to update
 * safely:
 *
 * <pre>
 *   user.email().ifPresent(e -&gt; { ... });
 *   // or
 *   String e = user.email().orElseThrow();
 *   user = new User(user.name(), Optional.of(e.toUpperCase())); // copy + Optional.of
 * </pre>
 *
 * <p>An {@link Affine} captures the same shape as one composable optic:
 *
 * <pre>
 *   user = userEmailAffine.modify(String::toUpperCase, user);
 * </pre>
 *
 * <p>Decision guide:
 *
 * <table>
 *   <tr><th>Optic</th><th>Focus count</th><th>Use case</th></tr>
 *   <tr><td>Lens</td><td>Exactly 1</td><td>Required field</td></tr>
 *   <tr><td>Prism</td><td>0 or 1 (variant)</td><td>Sum-type case</td></tr>
 *   <tr><td>Affine</td><td>0 or 1 (optional)</td><td>Optional field</td></tr>
 *   <tr><td>Traversal</td><td>0 to many</td><td>Collection</td></tr>
 * </table>
 *
 * <p>Affine vs Prism: both focus on zero-or-one, but Prism can {@code build} a complete structure
 * (e.g. constructing a {@code Circle} from its components). Affine cannot build; it only modifies
 * existing structures.
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
   * Exercise 1: {@code Affines.some()} for Optional fields.
   *
   * <pre>
   *   // Nudge:    The Affine has a getOptional method that returns Optional.
   *   // Strategy: someAffine.getOptional(present)
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 1: Affines.some() extracts the inner value of an Optional")
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
   * Exercise 2: Affine.set always succeeds.
   *
   * <pre>
   *   // Nudge:    set takes the new value first, then the source.
   *   // Strategy: someAffine.set("world", empty)
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 2: Affine.set always succeeds, even on absent source")
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
   * Exercise 3: Lens + Prism = Affine.
   *
   * <pre>
   *   // Nudge:    andThen plumbs the prism through the lens.
   *   // Strategy: databaseLens.andThen(somePrism)
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 3: Lens andThen Prism produces an Affine")
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
   * Exercise 4: matches() for presence checking.
   *
   * <pre>
   *   // Nudge:    matches() returns true when the Affine has a focus.
   *   // Strategy: someAffine.matches(present)
   *   // Spoiler:  same call for both.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 4: Affine.matches checks presence without extracting")
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
   * Exercise 5: getOrElse() for default values.
   *
   * <pre>
   *   // Nudge:    getOrElse takes the default first, then the source.
   *   // Strategy: someAffine.getOrElse("default", present)
   *   // Spoiler:  same call shape for the empty case too.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 5: Affine.getOrElse provides a default for absence")
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
   * Exercise 6: Modifying with an Affine.
   *
   * <pre>
   *   // Nudge:    Same shape as Lens.modify; absent source -&gt; absent result.
   *   // Strategy: someAffine.modify(String::toUpperCase, present)
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 6: Affine.modify is a no-op when the focus is absent")
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
   * Exercise 7: Chaining Affines for deep optional access.
   *
   * <pre>
   *   // Nudge:    Four levels: contactLens -&gt; contactPrism -&gt; phoneLens -&gt; phonePrism.
   *   // Strategy: contactLens.andThen(contactPrism).andThen(phoneLens).andThen(phonePrism)
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 7: chain affines for deep optional access")
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
