// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.example.book.optics.cast.CustomerProfile;
import org.higherkindedj.example.book.optics.cast.CustomerProfileLenses;
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

  // 1. Define a nested, immutable data model with optional fields. A product review sits beside
  //    the chapter's CustomerProfile: an anonymous review has no author, and an author may have no
  //    nickname.
  public record Review(String sku, Optional<CustomerProfile> author) {}

  public record AppConfig(
      String appName, Optional<DatabaseConfig> database, Optional<CacheConfig> cache) {}

  public record DatabaseConfig(String host, int port, Optional<PoolConfig> pool) {}

  public record PoolConfig(int minSize, int maxSize) {}

  public record CacheConfig(String provider, int ttlSeconds) {}

  public static void main(String[] args) {
    // 2. Create initial data with nested optionals.
    var ada = new CustomerProfile("Ada", Optional.of("Countess"), Optional.empty());
    var grace = new CustomerProfile("Grace", Optional.empty(), Optional.empty());
    var signedReview = new Review("LAMP", Optional.of(ada));
    var anonymousReview = new Review("BULB", Optional.empty());

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

    System.out.println("--- Scenario 2: Lens.andThen(Prism) = Affine ---");
    System.out.println();

    // Create lenses for our domain model. CustomerProfile's are generated in the cast package.
    Lens<Review, Optional<CustomerProfile>> authorLens =
        Lens.of(Review::author, (r, a) -> new Review(r.sku(), a));

    Lens<CustomerProfile, Optional<String>> nicknameLens = CustomerProfileLenses.nickname();

    Lens<CustomerProfile, String> nameLens = CustomerProfileLenses.name();

    // Prism for extracting from Optional.
    Prism<Optional<CustomerProfile>, CustomerProfile> authorPrism = Prisms.some();
    Prism<Optional<String>, String> nicknamePrism = Prisms.some();

    // Composition: Lens.andThen(Prism) = Affine.
    Affine<Review, CustomerProfile> reviewAuthorAffine = authorLens.andThen(authorPrism);

    System.out.println("Signed review: " + signedReview);
    System.out.println("getOptional author: " + reviewAuthorAffine.getOptional(signedReview));
    System.out.println();

    System.out.println("Anonymous review: " + anonymousReview);
    System.out.println("getOptional author: " + reviewAuthorAffine.getOptional(anonymousReview));
    System.out.println();

    // =======================================================================
    // SCENARIO 3: Chaining Affine compositions for deep optional access
    // =======================================================================

    System.out.println("--- Scenario 3: Deep Optional Access ---");
    System.out.println();

    // Chain compositions to access deeply nested optionals.
    // Review -> Optional<CustomerProfile> -> CustomerProfile -> Optional<String> -> String
    Affine<Review, String> authorNicknameAffine =
        authorLens
            .andThen(authorPrism) // Lens.andThen(Prism) = Affine
            .andThen(nicknameLens) // Affine.andThen(Lens) = Affine
            .andThen(nicknamePrism); // Affine.andThen(Prism) = Affine

    System.out.println("Review by an author with a nickname: " + signedReview);
    System.out.println("getOptional nickname: " + authorNicknameAffine.getOptional(signedReview));
    System.out.println();

    // Create a review whose author has no nickname.
    var reviewWithoutNickname = new Review("DESK", Optional.of(grace));
    System.out.println("Review by an author without a nickname: " + reviewWithoutNickname);
    System.out.println(
        "getOptional nickname: " + authorNicknameAffine.getOptional(reviewWithoutNickname));
    System.out.println();

    // =======================================================================
    // SCENARIO 4: Using Affine convenience methods
    // =======================================================================

    System.out.println("--- Scenario 4: Convenience Methods ---");
    System.out.println();

    // matches() and doesNotMatch().
    System.out.println(
        "reviewAuthorAffine.matches(signedReview): " + reviewAuthorAffine.matches(signedReview));
    System.out.println(
        "reviewAuthorAffine.matches(anonymousReview): "
            + reviewAuthorAffine.matches(anonymousReview));
    System.out.println(
        "reviewAuthorAffine.doesNotMatch(anonymousReview): "
            + reviewAuthorAffine.doesNotMatch(anonymousReview));
    System.out.println();

    // getOrElse() for default values.
    CustomerProfile guest = new CustomerProfile("Guest", Optional.empty(), Optional.empty());
    System.out.println(
        "getOrElse on signed review: " + reviewAuthorAffine.getOrElse(guest, signedReview));
    System.out.println(
        "getOrElse on anonymous review: " + reviewAuthorAffine.getOrElse(guest, anonymousReview));
    System.out.println();

    // mapOptional() for transformations.
    System.out.println(
        "mapOptional to get the author's name: "
            + reviewAuthorAffine.mapOptional(CustomerProfile::name, signedReview));
    System.out.println(
        "mapOptional on anonymous review: "
            + reviewAuthorAffine.mapOptional(CustomerProfile::name, anonymousReview));
    System.out.println();

    // =======================================================================
    // SCENARIO 5: Modifying through Affines
    // =======================================================================

    System.out.println("--- Scenario 5: Modifications ---");
    System.out.println();

    // modify() applies a function if the value is present.
    Review modifiedReview =
        reviewAuthorAffine.modify(
            author -> nameLens.modify(String::toUpperCase, author), signedReview);
    System.out.println("After modify (uppercase name): " + modifiedReview);

    // modify() on absent value returns unchanged.
    Review unchangedReview =
        reviewAuthorAffine.modify(
            author -> nameLens.modify(String::toUpperCase, author), anonymousReview);
    System.out.println("Modify on absent returns unchanged: " + unchangedReview);
    System.out.println("Same object? " + (unchangedReview == anonymousReview));
    System.out.println();

    // modifyWhen() with predicate.
    Review conditionalModify =
        authorNicknameAffine.modifyWhen(
            nickname -> nickname.startsWith("Count"),
            nickname -> nickname + " of Lovelace",
            signedReview);
    System.out.println("Conditional modify (a countess's nickname): " + conditionalModify);
    System.out.println();

    // set() replaces the value.
    Review reviewWithNewNickname = authorNicknameAffine.set("Lady Lovelace", signedReview);
    System.out.println("After set new nickname: " + reviewWithNewNickname);
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
            Optional.of(new DatabaseConfig("localhost", 5432, Optional.of(new PoolConfig(5, 20)))),
            Optional.empty());

    var minimalConfig = new AppConfig("MinimalApp", Optional.empty(), Optional.empty());

    // Build affines for configuration access.
    Lens<AppConfig, Optional<DatabaseConfig>> dbLens =
        Lens.of(AppConfig::database, (c, db) -> new AppConfig(c.appName(), db, c.cache()));

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
