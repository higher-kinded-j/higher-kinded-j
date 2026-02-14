// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.FocusPaths;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 12: Focus DSL - Type-Safe Path Navigation
 *
 * <p>The Focus DSL provides an ergonomic, type-safe way to navigate through nested data structures.
 * Instead of manually composing optics, you build navigation paths that automatically track type
 * changes as you traverse deeper.
 *
 * <p>Key concepts:
 *
 * <ul>
 *   <li><b>FocusPath</b>: Wraps a Lens - focuses on exactly one element (always present)
 *   <li><b>AffinePath</b>: Wraps an Affine - focuses on zero or one element (may be absent)
 *   <li><b>TraversalPath</b>: Wraps a Traversal - focuses on zero or more elements
 * </ul>
 *
 * <p>Path type transitions:
 *
 * <pre>
 *   FocusPath --via(Prism)--> AffinePath --via(Traversal)--> TraversalPath
 *             --via(Affine)-->            --via(Lens)     -->
 *             --via(Traversal)-->
 * </pre>
 *
 * <p>Prerequisites: Complete Tutorials 1-6 (core optics) before this one.
 */
public class Tutorial12_FocusDSL {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  /**
   * Exercise 1: Creating a FocusPath from a Lens
   *
   * <p>FocusPath wraps a Lens, providing a fluent API for navigation. You create one using
   * FocusPath.of(lens).
   *
   * <p>Task: Create a FocusPath and use it to get and set values
   */
  @Test
  void exercise1_basicFocusPath() {
    record Person(String name, int age) {}

    // Create a lens for the name field
    Lens<Person, String> nameLens = Lens.of(Person::name, (p, n) -> new Person(n, p.age()));

    Person alice = new Person("Alice", 30);

    // TODO: Replace null with FocusPath.of(nameLens)
    FocusPath<Person, String> namePath = answerRequired();

    // Use the path to get the name
    // TODO: Replace null with namePath.get(alice)
    String name = answerRequired();
    assertThat(name).isEqualTo("Alice");

    // Use the path to set a new name
    // TODO: Replace null with namePath.set("Alicia", alice)
    Person renamed = answerRequired();
    assertThat(renamed.name()).isEqualTo("Alicia");
    assertThat(renamed.age()).isEqualTo(30); // Age unchanged
  }

  /**
   * Exercise 2: Composing paths with via()
   *
   * <p>Paths can be composed using the via() method. When you compose a FocusPath with another
   * Lens, you get a FocusPath that navigates deeper.
   *
   * <p>Task: Compose paths to navigate through nested structures
   */
  @Test
  void exercise2_composingPaths() {
    record Street(String name) {}

    record Address(Street street, String city) {}

    record Person(String name, Address address) {}

    // Create lenses for each level
    Lens<Person, Address> addressLens = Lens.of(Person::address, (p, a) -> new Person(p.name(), a));
    Lens<Address, Street> streetLens = Lens.of(Address::street, (a, s) -> new Address(s, a.city()));
    Lens<Street, String> streetNameLens = Lens.of(Street::name, (s, n) -> new Street(n));

    Person person = new Person("Alice", new Address(new Street("High Street"), "London"));

    // Build a path from Person to street name
    // TODO: Replace null with FocusPath.of(addressLens).via(streetLens).via(streetNameLens)
    FocusPath<Person, String> streetNamePath = answerRequired();

    // Get the street name
    // TODO: Replace null with streetNamePath.get(person)
    String streetName = answerRequired();
    assertThat(streetName).isEqualTo("High Street");

    // Modify the street name
    // TODO: Replace null with streetNamePath.modify(String::toUpperCase, person)
    Person updated = answerRequired();
    assertThat(updated.address().street().name()).isEqualTo("HIGH STREET");
  }

  /**
   * Exercise 3: AffinePath for optional values
   *
   * <p>When navigating through Optional fields or using Prisms, the path becomes an AffinePath
   * because the target may not exist.
   *
   * <p>Task: Use AffinePath to navigate through optional data
   */
  @Test
  void exercise3_affinePathBasics() {
    record Config(Optional<String> apiKey) {}

    Lens<Config, Optional<String>> apiKeyLens = Lens.of(Config::apiKey, (c, k) -> new Config(k));

    Config withKey = new Config(Optional.of("secret-123"));
    Config withoutKey = new Config(Optional.empty());

    // Create a path that unwraps the Optional
    // FocusPath + some() -> AffinePath
    // TODO: Replace null with FocusPath.of(apiKeyLens).some()
    AffinePath<Config, String> keyPath = answerRequired();

    // getOptional returns Optional<A>
    // TODO: Replace null with keyPath.getOptional(withKey)
    Optional<String> foundKey = answerRequired();
    assertThat(foundKey).contains("secret-123");

    // TODO: Replace null with keyPath.getOptional(withoutKey)
    Optional<String> missingKey = answerRequired();
    assertThat(missingKey).isEmpty();

    // matches() checks if the target exists
    // TODO: Replace null with keyPath.matches(withKey)
    boolean hasKey = answerRequired();
    assertThat(hasKey).isTrue();

    // TODO: Replace null with keyPath.matches(withoutKey)
    boolean noKey = answerRequired();
    assertThat(noKey).isFalse();
  }

  /**
   * Exercise 4: TraversalPath for collections
   *
   * <p>When navigating into collections, the path becomes a TraversalPath that can focus on
   * multiple elements.
   *
   * <p>Task: Use TraversalPath to work with list elements
   */
  @Test
  void exercise4_traversalPathBasics() {
    record Team(String name, List<String> members) {}

    Lens<Team, List<String>> membersLens = Lens.of(Team::members, (t, m) -> new Team(t.name(), m));

    Team team = new Team("Engineering", List.of("Alice", "Bob", "Charlie"));

    // Create a path that traverses all members
    // FocusPath + each() -> TraversalPath
    // TODO: Replace null with FocusPath.of(membersLens).each()
    TraversalPath<Team, String> membersPath = answerRequired();

    // getAll returns all focused elements
    // TODO: Replace null with membersPath.getAll(team)
    List<String> allMembers = answerRequired();
    assertThat(allMembers).containsExactly("Alice", "Bob", "Charlie");

    // modifyAll transforms all elements
    // TODO: Replace null with membersPath.modifyAll(String::toUpperCase, team)
    Team shouting = answerRequired();
    assertThat(shouting.members()).containsExactly("ALICE", "BOB", "CHARLIE");

    // count returns the number of focused elements
    // TODO: Replace null with membersPath.count(team)
    int memberCount = answerRequired();
    assertThat(memberCount).isEqualTo(3);
  }

  /**
   * Exercise 5: Accessing specific list elements
   *
   * <p>The at(index) method creates an AffinePath for a specific list index. It returns empty if
   * the index is out of bounds.
   *
   * <p>Task: Focus on specific elements by index
   */
  @Test
  void exercise5_listIndexAccess() {
    record Playlist(String name, List<String> songs) {}

    Lens<Playlist, List<String>> songsLens =
        Lens.of(Playlist::songs, (p, s) -> new Playlist(p.name(), s));

    Playlist playlist = new Playlist("Favourites", List.of("Song A", "Song B", "Song C"));

    // Create a path to the first song
    // FocusPath + at(0) -> AffinePath
    // TODO: Replace null with FocusPath.of(songsLens).at(0)
    AffinePath<Playlist, String> firstSongPath = answerRequired();

    // Get the first song
    // TODO: Replace null with firstSongPath.getOptional(playlist)
    Optional<String> firstSong = answerRequired();
    assertThat(firstSong).contains("Song A");

    // Create a path to an out-of-bounds index
    AffinePath<Playlist, String> tenthSongPath = FocusPath.of(songsLens).at(10);

    // TODO: Replace null with tenthSongPath.getOptional(playlist)
    Optional<String> noSong = answerRequired();
    assertThat(noSong).isEmpty();

    // Set a specific song
    // TODO: Replace null with firstSongPath.set("New First Song", playlist)
    Playlist updated = answerRequired();
    assertThat(updated.songs()).containsExactly("New First Song", "Song B", "Song C");
  }

  /**
   * Exercise 6: Map navigation
   *
   * <p>Maps can be navigated using atKey(key) for a specific key.
   *
   * <p>Task: Navigate through map structures
   */
  @Test
  void exercise6_mapNavigation() {
    record Settings(Map<String, String> values) {}

    Lens<Settings, Map<String, String>> valuesLens =
        Lens.of(Settings::values, (s, v) -> new Settings(v));

    Settings settings =
        new Settings(
            Map.of(
                "theme", "dark",
                "language", "en",
                "timezone", "UTC"));

    // Create a path to a specific map key
    // FocusPath + atKey("theme") -> AffinePath
    // TODO: Replace null with FocusPath.of(valuesLens).atKey("theme")
    AffinePath<Settings, String> themePath = answerRequired();

    // Get the theme value
    // TODO: Replace null with themePath.getOptional(settings)
    Optional<String> theme = answerRequired();
    assertThat(theme).contains("dark");

    // Check a missing key
    AffinePath<Settings, String> missingPath = FocusPath.of(valuesLens).atKey("missing");
    assertThat(missingPath.getOptional(settings)).isEmpty();
  }

  /**
   * Exercise 7: Deep nested navigation
   *
   * <p>The real power of Focus DSL shows when navigating deeply nested structures with mixed
   * optionality.
   *
   * <p>Task: Navigate through a complex nested structure
   */
  @Test
  void exercise7_deepNavigation() {
    record Department(String name, Optional<String> manager) {}

    record Company(String name, List<Department> departments) {}

    Lens<Company, List<Department>> deptLens =
        Lens.of(Company::departments, (c, d) -> new Company(c.name(), d));
    Lens<Department, Optional<String>> managerLens =
        Lens.of(Department::manager, (d, m) -> new Department(d.name(), m));

    Company company =
        new Company(
            "TechCorp",
            List.of(
                new Department("Engineering", Optional.of("Alice")),
                new Department("Sales", Optional.empty()),
                new Department("Marketing", Optional.of("Charlie"))));

    // Path to all department managers (only those that exist)
    // Company -> List<Department> -> Department -> Optional<String> -> String
    // This chains: each() (list), via(managerLens) (lens), some() (optional)
    TraversalPath<Company, String> managersPath =
        FocusPath.of(deptLens).<Department>each().via(managerLens).<String>some();

    // TODO: Replace null with managersPath.getAll(company)
    List<String> managers = answerRequired();
    // Should only get managers that exist (Alice and Charlie, not Sales which is empty)
    assertThat(managers).containsExactly("Alice", "Charlie");

    // Modify all existing managers
    // TODO: Replace null with managersPath.modifyAll(String::toUpperCase, company)
    Company updated = answerRequired();

    // Verify modifications
    List<String> updatedManagers =
        FocusPath.of(deptLens).<Department>each().via(managerLens).<String>some().getAll(updated);
    assertThat(updatedManagers).containsExactly("ALICE", "CHARLIE");
  }

  /**
   * Exercise 8: Filtering traversals
   *
   * <p>TraversalPath provides a filter() method to focus only on elements matching a predicate.
   *
   * <p>Task: Filter elements during traversal
   */
  @Test
  void exercise8_filteringTraversals() {
    record Product(String name, double price) {}

    record Inventory(List<Product> products) {}

    Lens<Inventory, List<Product>> productsLens =
        Lens.of(Inventory::products, (i, p) -> new Inventory(p));
    Lens<Product, Double> priceLens = Lens.of(Product::price, (p, pr) -> new Product(p.name(), pr));

    Inventory inventory =
        new Inventory(
            List.of(
                new Product("Widget", 10.0),
                new Product("Gadget", 150.0),
                new Product("Gizmo", 75.0),
                new Product("Thingamajig", 200.0)));

    // Path to all products
    TraversalPath<Inventory, Product> allProducts = FocusPath.of(productsLens).each();

    // Filter to only expensive products (price > 100)
    // TODO: Replace null with allProducts.filter(p -> p.price() > 100)
    TraversalPath<Inventory, Product> expensiveProducts = answerRequired();

    // Get expensive product names
    List<String> expensiveNames =
        expensiveProducts.getAll(inventory).stream().map(Product::name).toList();
    assertThat(expensiveNames).containsExactly("Gadget", "Thingamajig");

    // Apply a 10% discount to all expensive products
    Inventory discounted =
        expensiveProducts.modifyAll(p -> new Product(p.name(), p.price() * 0.9), inventory);

    // Verify discounts applied only to expensive items
    Product gadget =
        discounted.products().stream().filter(p -> p.name().equals("Gadget")).findFirst().get();
    assertThat(gadget.price()).isEqualTo(135.0); // Was 150, now 135

    Product widget =
        discounted.products().stream().filter(p -> p.name().equals("Widget")).findFirst().get();
    assertThat(widget.price()).isEqualTo(10.0); // Unchanged (was not expensive)
  }

  /**
   * Exercise 9: Conversion between path types
   *
   * <p>Paths can be converted to their underlying optics using toLens(), toAffine(), and
   * toTraversal(). They can also be viewed as less-specific path types using asAffine() and
   * asTraversal().
   *
   * <p>Task: Convert paths for use with other optics APIs
   */
  @Test
  void exercise9_pathConversion() {
    record Box(String content) {}

    Lens<Box, String> contentLens = Lens.of(Box::content, (b, c) -> new Box(c));

    FocusPath<Box, String> contentPath = FocusPath.of(contentLens);

    // Convert FocusPath to the underlying Lens
    // TODO: Replace null with contentPath.toLens()
    Lens<Box, String> extractedLens = answerRequired();
    assertThat(extractedLens.get(new Box("hello"))).isEqualTo("hello");

    // View FocusPath as AffinePath (always successful, so Optional is always present)
    AffinePath<Box, String> asAffine = contentPath.asAffine();
    assertThat(asAffine.getOptional(new Box("world"))).contains("world");

    // View FocusPath as TraversalPath (focuses on exactly one element)
    TraversalPath<Box, String> asTraversal = contentPath.asTraversal();
    assertThat(asTraversal.getAll(new Box("test"))).containsExactly("test");
  }

  /**
   * Exercise 10: Using FocusPaths utility class
   *
   * <p>FocusPaths provides pre-built optics for common collection operations.
   *
   * <p>Task: Use FocusPaths utilities directly
   */
  @Test
  void exercise10_focusPathsUtility() {
    // FocusPaths provides optics for lists
    var listTraversal = FocusPaths.<String>listElements();
    var firstElement = FocusPaths.<String>listAt(0);
    var lastElement = FocusPaths.<String>listLast();

    List<String> fruits = List.of("apple", "banana", "cherry");

    // Use listElements traversal
    // TODO: Replace null with appropriate call using listTraversal
    // Hint: Use org.higherkindedj.optics.util.Traversals.getAll(traversal, source)
    List<String> allFruits = Traversals.getAll(listTraversal, fruits);
    assertThat(allFruits).containsExactly("apple", "banana", "cherry");

    // Use listAt affine for first element
    // TODO: Replace null with firstElement.getOptional(fruits)
    Optional<String> first = answerRequired();
    assertThat(first).contains("apple");

    // Use listLast affine
    // TODO: Replace null with lastElement.getOptional(fruits)
    Optional<String> last = answerRequired();
    assertThat(last).contains("cherry");

    // FocusPaths also provides optionalSome for unwrapping Optional
    var someAffine = FocusPaths.<String>optionalSome();

    Optional<String> maybeValue = Optional.of("present");
    // TODO: Replace null with someAffine.getOptional(maybeValue)
    Optional<String> unwrapped = answerRequired();
    assertThat(unwrapped).contains("present");

    Optional<String> emptyValue = Optional.empty();
    assertThat(someAffine.getOptional(emptyValue)).isEmpty();
  }

  /**
   * Congratulations! You have completed Tutorial 12: Focus DSL
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>Creating FocusPath from Lens using FocusPath.of()
   *   <li>Composing paths with via() for deep navigation
   *   <li>AffinePath for optional/nullable values using some()
   *   <li>TraversalPath for collections using each()
   *   <li>Accessing specific elements with at(index) and atKey(key)
   *   <li>Filtering traversals with filter()
   *   <li>Converting paths with toLens(), asAffine(), asTraversal()
   *   <li>Using FocusPaths utility for common operations
   * </ul>
   *
   * <p>Key Takeaways:
   *
   * <ul>
   *   <li>FocusPath wraps Lens - always focuses on exactly one element
   *   <li>AffinePath wraps Affine - may focus on zero or one element
   *   <li>TraversalPath wraps Traversal - may focus on zero or more elements
   *   <li>Path types automatically widen as you navigate through optional/collection types
   *   <li>Use the generated *Focus classes (via @GenerateFocus) for record types
   * </ul>
   *
   * <p>Next Steps:
   *
   * <ul>
   *   <li>Use @GenerateFocus annotation to generate Focus classes for your records
   *   <li>Combine Focus DSL with Free Monad DSL (Tutorial 11) for complex workflows
   *   <li>Build type-safe navigation for your domain models
   * </ul>
   */
}
