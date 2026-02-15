// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.util.Affines;
import org.higherkindedj.optics.util.Prisms;
import org.junit.jupiter.api.Test;

/**
 * Solutions for Tutorial 04: Affine Basics
 *
 * <p>This file contains the complete solutions for all exercises in Tutorial 04. Refer to this if
 * you get stuck, but try to solve the exercises yourself first.
 */
public class Tutorial04_AffineBasics_Solution {

  // Domain model with optional fields
  record UserProfile(String username, Optional<ContactInfo> contact) {}

  record ContactInfo(String email, Optional<String> phone) {}

  // Configuration with optional sections
  record AppConfig(String appName, Optional<DatabaseConfig> database) {}

  record DatabaseConfig(String host, int port) {}

  /** Exercise 1 Solution: Creating an Affine with Affines.some() */
  @Test
  void exercise1_usingSomeAffine() {
    Affine<Optional<String>, String> someAffine = Affines.some();

    Optional<String> present = Optional.of("hello");
    Optional<String> empty = Optional.empty();

    // SOLUTION: Use someAffine.getOptional() to extract the value
    Optional<String> extracted = someAffine.getOptional(present);

    assertThat(extracted).isPresent();
    assertThat(extracted.get()).isEqualTo("hello");

    // Affine returns empty when the Optional is empty
    Optional<String> notPresent = someAffine.getOptional(empty);
    assertThat(notPresent).isEmpty();
  }

  /** Exercise 2 Solution: Setting values with an Affine */
  @Test
  void exercise2_settingWithAffine() {
    Affine<Optional<String>, String> someAffine = Affines.some();

    Optional<String> empty = Optional.empty();

    // SOLUTION: Use someAffine.set() to set the value
    Optional<String> updated = someAffine.set("world", empty);

    assertThat(updated).isPresent();
    assertThat(updated.get()).isEqualTo("world");
  }

  /** Exercise 3 Solution: Composing Lens with Prism to get an Affine */
  @Test
  void exercise3_lensAndPrismComposition() {
    // Lens to access the optional database field
    Lens<AppConfig, Optional<DatabaseConfig>> databaseLens =
        Lens.of(AppConfig::database, (config, db) -> new AppConfig(config.appName(), db));

    // Prism to extract the value from Optional
    Prism<Optional<DatabaseConfig>, DatabaseConfig> somePrism = Prisms.some();

    // SOLUTION: Compose the lens with the prism using andThen
    Affine<AppConfig, DatabaseConfig> databaseAffine = databaseLens.andThen(somePrism);

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

  /** Exercise 4 Solution: Using matches() for presence checking */
  @Test
  void exercise4_usingMatches() {
    Affine<Optional<String>, String> someAffine = Affines.some();

    Optional<String> present = Optional.of("value");
    Optional<String> empty = Optional.empty();

    // SOLUTION: Use someAffine.matches() to check presence
    boolean hasValue = someAffine.matches(present);
    boolean isEmpty = !someAffine.matches(empty);

    assertThat(hasValue).isTrue();
    assertThat(isEmpty).isTrue();
  }

  /** Exercise 5 Solution: Using getOrElse() for default values */
  @Test
  void exercise5_usingGetOrElse() {
    Affine<Optional<String>, String> someAffine = Affines.some();

    Optional<String> present = Optional.of("configured");
    Optional<String> empty = Optional.empty();

    // SOLUTION: Use someAffine.getOrElse() with a default value
    String value1 = someAffine.getOrElse("default", present);
    String value2 = someAffine.getOrElse("default", empty);

    assertThat(value1).isEqualTo("configured");
    assertThat(value2).isEqualTo("default");
  }

  /** Exercise 6 Solution: Modifying values with an Affine */
  @Test
  void exercise6_modifyingWithAffine() {
    Affine<Optional<String>, String> someAffine = Affines.some();

    Optional<String> present = Optional.of("hello");
    Optional<String> empty = Optional.empty();

    // SOLUTION: Use someAffine.modify() with a transformation function
    Optional<String> uppercased = someAffine.modify(String::toUpperCase, present);

    assertThat(uppercased).isPresent();
    assertThat(uppercased.get()).isEqualTo("HELLO");

    // Modify on empty returns empty unchanged
    Optional<String> stillEmpty = someAffine.modify(String::toUpperCase, empty);
    assertThat(stillEmpty).isEmpty();
  }

  /** Exercise 7 Solution: Chaining Affines for deep optional access */
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

    // SOLUTION: Chain the lenses and prisms together
    // contactLens >>> contactPrism = Affine<UserProfile, ContactInfo>
    // ... >>> phoneLens = Affine<UserProfile, Optional<String>>
    // ... >>> phonePrism = Affine<UserProfile, String>
    Affine<UserProfile, String> userToPhone =
        contactLens.andThen(contactPrism).andThen(phoneLens).andThen(phonePrism);

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
}
