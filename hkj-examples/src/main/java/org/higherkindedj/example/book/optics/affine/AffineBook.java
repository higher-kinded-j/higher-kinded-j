// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.affine;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.util.Affines;
import org.higherkindedj.optics.util.Prisms;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/affine.html">Affines</a> page, whose
 * Lens-and-Prism example the <a
 * href="https://higher-kinded-j.github.io/latest/optics/composition_rules.html">Composition
 * Rules</a> page shows too. The pages include the anchored regions, and {@code AffineBookTest}
 * holds the values their comments claim.
 *
 * <p>Each region declares its model as local records, so a page shows the model and its use in one
 * block. Their values come back to the test as plain objects, and the test compares what they print
 * with what the page shows.
 */
public final class AffineBook {

  private AffineBook() {}

  /** What the page's hand-built affine returns, so the test can read each value. */
  record ManualResults(
      Optional<String> result, Optional<String> noMatch, Optional<String> updated) {}

  static ManualResults manual() {
    // ANCHOR: manual
    // Affine for accessing the value inside an Optional field
    Affine<Optional<String>, String> someAffine =
        Affine.of(
            Function.identity(), // getOptional: Optional<String> -> Optional<String>
            (opt, value) -> Optional.of(value)); // set: always wrap in Optional.of

    // Usage
    Optional<String> present = Optional.of("hello");
    Optional<String> result = someAffine.getOptional(present); // Optional.of("hello")

    Optional<String> empty = Optional.empty();
    Optional<String> noMatch = someAffine.getOptional(empty); // Optional.empty()

    // Setting always wraps the value
    Optional<String> updated = someAffine.set("world", empty); // Optional.of("world")
    // ANCHOR_END: manual
    return new ManualResults(result, noMatch, updated);
  }

  /** The three values the Lens-and-Prism example computes, in the order the page shows them. */
  static List<Object> lensThenPrism() {
    // ANCHOR: lens_prism
    // Domain model
    record DatabaseSettings(String host, int port) {}
    record Config(Optional<DatabaseSettings> database) {}

    // The lens always reaches the Optional<DatabaseSettings> field
    Lens<Config, Optional<DatabaseSettings>> databaseLens =
        Lens.of(Config::database, (c, db) -> new Config(db));

    // The prism may or may not find DatabaseSettings inside the Optional
    Prism<Optional<DatabaseSettings>, DatabaseSettings> somePrism = Prisms.some();

    // Composition: Lens.andThen(Prism) = Affine
    Affine<Config, DatabaseSettings> databaseAffine = databaseLens.andThen(somePrism);

    // Usage
    Config config1 = new Config(Optional.of(new DatabaseSettings("localhost", 5432)));
    Optional<DatabaseSettings> result1 = databaseAffine.getOptional(config1);
    // result1 = Optional[DatabaseSettings[host=localhost, port=5432]]

    Config config2 = new Config(Optional.empty());
    Optional<DatabaseSettings> result2 = databaseAffine.getOptional(config2);
    // result2 = Optional.empty, since the prism found nothing

    // Setting through the affine: some() can build the Optional, so the empty one is filled
    Config updated = databaseAffine.set(new DatabaseSettings("newhost", 3306), config2);
    // updated = Config[database=Optional[DatabaseSettings[host=newhost, port=3306]]]
    // ANCHOR_END: lens_prism
    return List.of(result1, result2, updated);
  }

  static long presentCount() {
    // ANCHOR: presence
    Affine<Optional<String>, String> someAffine = Affines.some();

    Optional<String> present = Optional.of("hello");
    Optional<String> empty = Optional.empty();

    // Using matches()
    if (someAffine.matches(present)) {
      System.out.println("Value present");
    }

    // Using doesNotMatch()
    if (someAffine.doesNotMatch(empty)) {
      System.out.println("No value");
    }

    // Useful in streams
    List<Optional<String>> values = List.of(Optional.of("a"), Optional.empty(), Optional.of("b"));

    long presentCount = values.stream().filter(someAffine::matches).count(); // 2
    // ANCHOR_END: presence
    return presentCount;
  }

  /** What the page's two guarded writes return. */
  record Guarded(Optional<String> result, Optional<String> guarded) {}

  static Guarded conditional() {
    // ANCHOR: conditional
    Affine<Optional<String>, String> someAffine = Affines.some();

    Optional<String> value = Optional.of("hello world");

    // Only modify if predicate is satisfied
    Optional<String> result =
        someAffine.modifyWhen(s -> s.length() > 5, String::toUpperCase, value);
    // result = Optional.of("HELLO WORLD")

    // Set only when condition is met
    Optional<String> guarded = someAffine.setWhen(s -> s.startsWith("hello"), "goodbye", value);
    // guarded = Optional.of("goodbye")
    // ANCHOR_END: conditional
    return new Guarded(result, guarded);
  }

  static Optional<String> removal() {
    // ANCHOR: removal
    // Create an affine that supports removal
    Affine<Optional<String>, String> removableAffine = Affines.someWithRemove();

    Optional<String> present = Optional.of("hello");
    Optional<String> cleared = removableAffine.remove(present);
    // cleared = Optional.empty()
    // ANCHOR_END: removal
    return cleared;
  }

  /** The three values the deep-access example computes, in the order the page shows them. */
  static List<Object> deepOptional() {
    // ANCHOR: deep_optional
    record Address(String street, Optional<String> postcode) {}
    record User(String name, Optional<Address> address) {}

    // Build affines for each optional field
    Lens<User, Optional<Address>> addressLens =
        Lens.of(User::address, (u, a) -> new User(u.name(), a));

    Lens<Address, Optional<String>> postcodeLens =
        Lens.of(Address::postcode, (a, p) -> new Address(a.street(), p));

    Prism<Optional<Address>, Address> addressPrism = Prisms.some();
    Prism<Optional<String>, String> postcodePrism = Prisms.some();

    // Compose to access nested optional
    Affine<User, String> userPostcode =
        addressLens
            .andThen(addressPrism) // Lens.andThen(Prism) = Affine
            .andThen(postcodeLens) // Affine.andThen(Lens) = Affine
            .andThen(postcodePrism); // Affine.andThen(Prism) = Affine

    // Usage
    User user1 =
        new User("Alice", Optional.of(new Address("123 Main St", Optional.of("SW1A 1AA"))));
    User user2 = new User("Bob", Optional.empty());

    Optional<String> postcode1 = userPostcode.getOptional(user1);
    // Optional.of("SW1A 1AA")

    Optional<String> postcode2 = userPostcode.getOptional(user2);
    // Optional.empty()

    // Update deeply nested optional
    User updated = userPostcode.set("EC1A 1BB", user1);
    // User[name=Alice, address=Optional[Address[street=123 Main St, postcode=Optional[EC1A 1BB]]]]
    // ANCHOR_END: deep_optional
    return List.of(postcode1, postcode2, updated);
  }
}
