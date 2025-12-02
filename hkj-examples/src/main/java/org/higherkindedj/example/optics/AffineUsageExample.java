// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.util.Affines;
import org.higherkindedj.optics.util.Prisms;

/**
 * A runnable example demonstrating how to use Affines for working with optional fields and
 * zero-or-one element focus patterns in immutable data structures.
 *
 * <p>Affines sit between Lenses and Traversals in the optic hierarchy: they focus on exactly zero
 * or one element, making them ideal for {@code Optional<T>} fields and nullable properties.
 */
public class AffineUsageExample {

  // 1. Define a nested, immutable data model with optional fields.
  public record UserProfile(String username, Optional<ContactInfo> contact) {}

  public record ContactInfo(String email, Optional<String> phone) {}

  public record AppConfig(String appName, Optional<DatabaseConfig> database) {}

  public record DatabaseConfig(String host, int port, Optional<PoolConfig> pool) {}

  public record PoolConfig(int minSize, int maxSize) {}

  public static void main(String[] args) {
    // 2. Create initial data with nested optionals.
    var contactWithPhone = new ContactInfo("alice@example.com", Optional.of("+44 7700 900000"));
    var contactWithoutPhone = new ContactInfo("bob@example.com", Optional.empty());
    var userWithContact = new UserProfile("alice", Optional.of(contactWithPhone));
    var userWithoutContact = new UserProfile("charlie", Optional.empty());

    System.out.println("=".repeat(60));
    System.out.println("AFFINE USAGE EXAMPLE");
    System.out.println("=".repeat(60));
    System.out.println();

    // =======================================================================
    // SCENARIO 1: Using Affines utility class
    // =======================================================================

    System.out.println("--- Scenario 1: Using Affines.some() ---");
    System.out.println();

    // The Affines utility class provides ready-made affines for common patterns.
    Affine<Optional<String>, String> someAffine = Affines.some();

    Optional<String> presentValue = Optional.of("hello");
    Optional<String> emptyValue = Optional.empty();

    System.out.println("Present value: " + presentValue);
    System.out.println("getOptional on present: " + someAffine.getOptional(presentValue));
    System.out.println("getOptional on empty: " + someAffine.getOptional(emptyValue));
    System.out.println("set on present: " + someAffine.set("world", presentValue));
    System.out.println("set on empty: " + someAffine.set("world", emptyValue));
    System.out.println();

    // =======================================================================
    // SCENARIO 2: Composing Lens with Prism to create Affine
    // =======================================================================

    System.out.println("--- Scenario 2: Lens >>> Prism = Affine ---");
    System.out.println();

    // Create lenses for our domain model.
    Lens<UserProfile, Optional<ContactInfo>> contactLens =
        Lens.of(UserProfile::contact, (u, c) -> new UserProfile(u.username(), c));

    Lens<ContactInfo, Optional<String>> phoneLens =
        Lens.of(ContactInfo::phone, (c, p) -> new ContactInfo(c.email(), p));

    Lens<ContactInfo, String> emailLens =
        Lens.of(ContactInfo::email, (c, e) -> new ContactInfo(e, c.phone()));

    // Prism for extracting from Optional.
    Prism<Optional<ContactInfo>, ContactInfo> contactPrism = Prisms.some();
    Prism<Optional<String>, String> phonePrism = Prisms.some();

    // Composition: Lens >>> Prism = Affine.
    Affine<UserProfile, ContactInfo> userContactAffine = contactLens.andThen(contactPrism);

    System.out.println("User with contact: " + userWithContact);
    System.out.println("getOptional contact: " + userContactAffine.getOptional(userWithContact));
    System.out.println();

    System.out.println("User without contact: " + userWithoutContact);
    System.out.println("getOptional contact: " + userContactAffine.getOptional(userWithoutContact));
    System.out.println();

    // =======================================================================
    // SCENARIO 3: Chaining Affine compositions for deep optional access
    // =======================================================================

    System.out.println("--- Scenario 3: Deep Optional Access ---");
    System.out.println();

    // Chain compositions to access deeply nested optionals.
    // UserProfile -> Optional<ContactInfo> -> ContactInfo -> Optional<String> -> String
    Affine<UserProfile, String> userPhoneAffine =
        contactLens
            .andThen(contactPrism) // Lens >>> Prism = Affine
            .andThen(phoneLens) // Affine >>> Lens = Affine
            .andThen(phonePrism); // Affine >>> Prism = Affine

    System.out.println("User with contact+phone: " + userWithContact);
    System.out.println("getOptional phone: " + userPhoneAffine.getOptional(userWithContact));
    System.out.println();

    // Create a user with contact but no phone.
    var userWithContactNoPhone = new UserProfile("bob", Optional.of(contactWithoutPhone));
    System.out.println("User with contact but no phone: " + userWithContactNoPhone);
    System.out.println("getOptional phone: " + userPhoneAffine.getOptional(userWithContactNoPhone));
    System.out.println();

    // =======================================================================
    // SCENARIO 4: Using Affine convenience methods
    // =======================================================================

    System.out.println("--- Scenario 4: Convenience Methods ---");
    System.out.println();

    // matches() and doesNotMatch().
    System.out.println(
        "userContactAffine.matches(userWithContact): "
            + userContactAffine.matches(userWithContact));
    System.out.println(
        "userContactAffine.matches(userWithoutContact): "
            + userContactAffine.matches(userWithoutContact));
    System.out.println(
        "userContactAffine.doesNotMatch(userWithoutContact): "
            + userContactAffine.doesNotMatch(userWithoutContact));
    System.out.println();

    // getOrElse() for default values.
    ContactInfo defaultContact = new ContactInfo("default@example.com", Optional.empty());
    System.out.println(
        "getOrElse on user with contact: "
            + userContactAffine.getOrElse(defaultContact, userWithContact));
    System.out.println(
        "getOrElse on user without contact: "
            + userContactAffine.getOrElse(defaultContact, userWithoutContact));
    System.out.println();

    // mapOptional() for transformations.
    System.out.println(
        "mapOptional to get email from contact: "
            + userContactAffine.mapOptional(ContactInfo::email, userWithContact));
    System.out.println(
        "mapOptional on user without contact: "
            + userContactAffine.mapOptional(ContactInfo::email, userWithoutContact));
    System.out.println();

    // =======================================================================
    // SCENARIO 5: Modifying through Affines
    // =======================================================================

    System.out.println("--- Scenario 5: Modifications ---");
    System.out.println();

    // modify() applies a function if the value is present.
    UserProfile modifiedUser =
        userContactAffine.modify(
            contact -> new ContactInfo(contact.email().toUpperCase(), contact.phone()),
            userWithContact);
    System.out.println("After modify (uppercase email): " + modifiedUser);

    // modify() on absent value returns unchanged.
    UserProfile unchangedUser =
        userContactAffine.modify(
            contact -> new ContactInfo(contact.email().toUpperCase(), contact.phone()),
            userWithoutContact);
    System.out.println("Modify on absent returns unchanged: " + unchangedUser);
    System.out.println("Same object? " + (unchangedUser == userWithoutContact));
    System.out.println();

    // modifyWhen() with predicate.
    UserProfile conditionalModify =
        userPhoneAffine.modifyWhen(
            phone -> phone.startsWith("+44"), phone -> phone.replace("+44", "0"), userWithContact);
    System.out.println("Conditional modify (UK phone): " + conditionalModify);
    System.out.println();

    // set() replaces the value.
    UserProfile userWithNewPhone = userPhoneAffine.set("+1 555 123 4567", userWithContact);
    System.out.println("After set new phone: " + userWithNewPhone);
    System.out.println();

    // =======================================================================
    // SCENARIO 6: List element affines
    // =======================================================================

    System.out.println("--- Scenario 6: List Element Affines ---");
    System.out.println();

    List<String> names = List.of("Alice", "Bob", "Charlie");
    List<String> emptyList = List.of();

    Affine<List<String>, String> headAffine = Affines.listHead();
    Affine<List<String>, String> lastAffine = Affines.listLast();
    Affine<List<String>, String> secondAffine = Affines.listAt(1);

    System.out.println("List: " + names);
    System.out.println("listHead getOptional: " + headAffine.getOptional(names));
    System.out.println("listLast getOptional: " + lastAffine.getOptional(names));
    System.out.println("listAt(1) getOptional: " + secondAffine.getOptional(names));
    System.out.println();

    System.out.println("Empty list: " + emptyList);
    System.out.println("listHead on empty: " + headAffine.getOptional(emptyList));
    System.out.println();

    // Modify first element.
    List<String> modifiedList = headAffine.modify(String::toUpperCase, names);
    System.out.println("After modifying head: " + modifiedList);
    System.out.println();

    // =======================================================================
    // SCENARIO 7: Real-world configuration example
    // =======================================================================

    System.out.println("--- Scenario 7: Configuration Management ---");
    System.out.println();

    var fullConfig =
        new AppConfig(
            "MyApp",
            Optional.of(new DatabaseConfig("localhost", 5432, Optional.of(new PoolConfig(5, 20)))));

    var minimalConfig = new AppConfig("MinimalApp", Optional.empty());

    // Build affines for configuration access.
    Lens<AppConfig, Optional<DatabaseConfig>> dbLens =
        Lens.of(AppConfig::database, (c, db) -> new AppConfig(c.appName(), db));

    Lens<DatabaseConfig, String> hostLens =
        Lens.of(DatabaseConfig::host, (db, h) -> new DatabaseConfig(h, db.port(), db.pool()));

    Lens<DatabaseConfig, Optional<PoolConfig>> poolLens =
        Lens.of(DatabaseConfig::pool, (db, p) -> new DatabaseConfig(db.host(), db.port(), p));

    Lens<PoolConfig, Integer> maxSizeLens =
        Lens.of(PoolConfig::maxSize, (p, m) -> new PoolConfig(p.minSize(), m));

    // Compose for deep access.
    Affine<AppConfig, DatabaseConfig> dbAffine = dbLens.andThen(Prisms.some());
    Affine<AppConfig, String> dbHostAffine = dbAffine.andThen(hostLens);
    Affine<AppConfig, Integer> poolMaxSizeAffine =
        dbAffine.andThen(poolLens).andThen(Prisms.some()).andThen(maxSizeLens);

    System.out.println("Full config: " + fullConfig);
    System.out.println("Database host: " + dbHostAffine.getOptional(fullConfig));
    System.out.println("Pool max size: " + poolMaxSizeAffine.getOptional(fullConfig));
    System.out.println();

    System.out.println("Minimal config: " + minimalConfig);
    System.out.println("Database host: " + dbHostAffine.getOptional(minimalConfig));
    System.out.println("Pool max size: " + poolMaxSizeAffine.getOptional(minimalConfig));
    System.out.println();

    // Update deeply nested configuration.
    AppConfig updatedConfig = poolMaxSizeAffine.set(50, fullConfig);
    System.out.println("After setting pool max size to 50:");
    System.out.println("Updated config: " + updatedConfig);
    System.out.println("New pool max size: " + poolMaxSizeAffine.getOptional(updatedConfig));
    System.out.println();

    // Modification on minimal config does nothing.
    AppConfig unchangedConfig = poolMaxSizeAffine.set(50, minimalConfig);
    System.out.println("Setting on minimal config (no database):");
    System.out.println("Same object? " + (unchangedConfig == minimalConfig));
    System.out.println();

    System.out.println("=".repeat(60));
    System.out.println("Example complete!");
    System.out.println("=".repeat(60));
  }
}
