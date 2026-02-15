// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

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
 * Solutions for Tutorial 12: Focus DSL - Type-Safe Path Navigation
 *
 * <p>These are the completed solutions for all exercises in Tutorial12_FocusDSL.java
 */
public class Tutorial12_FocusDSL_Solution {

  @Test
  void exercise1_basicFocusPath() {
    record Person(String name, int age) {}

    Lens<Person, String> nameLens = Lens.of(Person::name, (p, n) -> new Person(n, p.age()));

    Person alice = new Person("Alice", 30);

    // SOLUTION: Create a FocusPath from the lens
    FocusPath<Person, String> namePath = FocusPath.of(nameLens);

    // SOLUTION: Use the path to get the name
    String name = namePath.get(alice);
    assertThat(name).isEqualTo("Alice");

    // SOLUTION: Use the path to set a new name
    Person renamed = namePath.set("Alicia", alice);
    assertThat(renamed.name()).isEqualTo("Alicia");
    assertThat(renamed.age()).isEqualTo(30);
  }

  @Test
  void exercise2_composingPaths() {
    record Street(String name) {}

    record Address(Street street, String city) {}

    record Person(String name, Address address) {}

    Lens<Person, Address> addressLens = Lens.of(Person::address, (p, a) -> new Person(p.name(), a));
    Lens<Address, Street> streetLens = Lens.of(Address::street, (a, s) -> new Address(s, a.city()));
    Lens<Street, String> streetNameLens = Lens.of(Street::name, (s, n) -> new Street(n));

    Person person = new Person("Alice", new Address(new Street("High Street"), "London"));

    // SOLUTION: Compose paths using via()
    FocusPath<Person, String> streetNamePath =
        FocusPath.of(addressLens).via(streetLens).via(streetNameLens);

    // SOLUTION: Get the street name
    String streetName = streetNamePath.get(person);
    assertThat(streetName).isEqualTo("High Street");

    // SOLUTION: Modify the street name
    Person updated = streetNamePath.modify(String::toUpperCase, person);
    assertThat(updated.address().street().name()).isEqualTo("HIGH STREET");
  }

  @Test
  void exercise3_affinePathBasics() {
    record Config(Optional<String> apiKey) {}

    Lens<Config, Optional<String>> apiKeyLens = Lens.of(Config::apiKey, (c, k) -> new Config(k));

    Config withKey = new Config(Optional.of("secret-123"));
    Config withoutKey = new Config(Optional.empty());

    // SOLUTION: Create path that unwraps Optional using some()
    AffinePath<Config, String> keyPath = FocusPath.of(apiKeyLens).some();

    // SOLUTION: getOptional returns Optional<A>
    Optional<String> foundKey = keyPath.getOptional(withKey);
    assertThat(foundKey).contains("secret-123");

    // SOLUTION: Empty when Optional is empty
    Optional<String> missingKey = keyPath.getOptional(withoutKey);
    assertThat(missingKey).isEmpty();

    // SOLUTION: matches() checks if target exists
    boolean hasKey = keyPath.matches(withKey);
    assertThat(hasKey).isTrue();

    // SOLUTION: False when target doesn't exist
    boolean noKey = keyPath.matches(withoutKey);
    assertThat(noKey).isFalse();
  }

  @Test
  void exercise4_traversalPathBasics() {
    record Team(String name, List<String> members) {}

    Lens<Team, List<String>> membersLens = Lens.of(Team::members, (t, m) -> new Team(t.name(), m));

    Team team = new Team("Engineering", List.of("Alice", "Bob", "Charlie"));

    // SOLUTION: Create traversal path using each()
    TraversalPath<Team, String> membersPath = FocusPath.of(membersLens).each();

    // SOLUTION: getAll returns all focused elements
    List<String> allMembers = membersPath.getAll(team);
    assertThat(allMembers).containsExactly("Alice", "Bob", "Charlie");

    // SOLUTION: modifyAll transforms all elements
    Team shouting = membersPath.modifyAll(String::toUpperCase, team);
    assertThat(shouting.members()).containsExactly("ALICE", "BOB", "CHARLIE");

    // SOLUTION: count returns element count
    int memberCount = membersPath.count(team);
    assertThat(memberCount).isEqualTo(3);
  }

  @Test
  void exercise5_listIndexAccess() {
    record Playlist(String name, List<String> songs) {}

    Lens<Playlist, List<String>> songsLens =
        Lens.of(Playlist::songs, (p, s) -> new Playlist(p.name(), s));

    Playlist playlist = new Playlist("Favourites", List.of("Song A", "Song B", "Song C"));

    // SOLUTION: Create path to first element using at(0)
    AffinePath<Playlist, String> firstSongPath = FocusPath.of(songsLens).at(0);

    // SOLUTION: Get the first song
    Optional<String> firstSong = firstSongPath.getOptional(playlist);
    assertThat(firstSong).contains("Song A");

    AffinePath<Playlist, String> tenthSongPath = FocusPath.of(songsLens).at(10);

    // SOLUTION: Out of bounds returns empty
    Optional<String> noSong = tenthSongPath.getOptional(playlist);
    assertThat(noSong).isEmpty();

    // SOLUTION: Set a specific song
    Playlist updated = firstSongPath.set("New First Song", playlist);
    assertThat(updated.songs()).containsExactly("New First Song", "Song B", "Song C");
  }

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

    // SOLUTION: Create path to map key using atKey()
    AffinePath<Settings, String> themePath = FocusPath.of(valuesLens).atKey("theme");

    // SOLUTION: Get the theme value
    Optional<String> theme = themePath.getOptional(settings);
    assertThat(theme).contains("dark");

    AffinePath<Settings, String> missingPath = FocusPath.of(valuesLens).atKey("missing");
    assertThat(missingPath.getOptional(settings)).isEmpty();
  }

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

    TraversalPath<Company, String> managersPath =
        FocusPath.of(deptLens).<Department>each().via(managerLens).<String>some();

    // SOLUTION: Get all present managers
    List<String> managers = managersPath.getAll(company);
    assertThat(managers).containsExactly("Alice", "Charlie");

    // SOLUTION: Modify all existing managers
    Company updated = managersPath.modifyAll(String::toUpperCase, company);

    List<String> updatedManagers =
        FocusPath.of(deptLens).<Department>each().via(managerLens).<String>some().getAll(updated);
    assertThat(updatedManagers).containsExactly("ALICE", "CHARLIE");
  }

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

    TraversalPath<Inventory, Product> allProducts = FocusPath.of(productsLens).each();

    // SOLUTION: Filter to expensive products
    TraversalPath<Inventory, Product> expensiveProducts = allProducts.filter(p -> p.price() > 100);

    List<String> expensiveNames =
        expensiveProducts.getAll(inventory).stream().map(Product::name).toList();
    assertThat(expensiveNames).containsExactly("Gadget", "Thingamajig");

    Inventory discounted =
        expensiveProducts.modifyAll(p -> new Product(p.name(), p.price() * 0.9), inventory);

    Product gadget =
        discounted.products().stream().filter(p -> p.name().equals("Gadget")).findFirst().get();
    assertThat(gadget.price()).isEqualTo(135.0);

    Product widget =
        discounted.products().stream().filter(p -> p.name().equals("Widget")).findFirst().get();
    assertThat(widget.price()).isEqualTo(10.0);
  }

  @Test
  void exercise9_pathConversion() {
    record Box(String content) {}

    Lens<Box, String> contentLens = Lens.of(Box::content, (b, c) -> new Box(c));

    FocusPath<Box, String> contentPath = FocusPath.of(contentLens);

    // SOLUTION: Convert to underlying Lens
    Lens<Box, String> extractedLens = contentPath.toLens();
    assertThat(extractedLens.get(new Box("hello"))).isEqualTo("hello");

    AffinePath<Box, String> asAffine = contentPath.asAffine();
    assertThat(asAffine.getOptional(new Box("world"))).contains("world");

    TraversalPath<Box, String> asTraversal = contentPath.asTraversal();
    assertThat(asTraversal.getAll(new Box("test"))).containsExactly("test");
  }

  @Test
  void exercise10_focusPathsUtility() {
    var listTraversal = FocusPaths.<String>listElements();
    var firstElement = FocusPaths.<String>listAt(0);
    var lastElement = FocusPaths.<String>listLast();

    List<String> fruits = List.of("apple", "banana", "cherry");

    List<String> allFruits = Traversals.getAll(listTraversal, fruits);
    assertThat(allFruits).containsExactly("apple", "banana", "cherry");

    // SOLUTION: Get first element
    Optional<String> first = firstElement.getOptional(fruits);
    assertThat(first).contains("apple");

    // SOLUTION: Get last element
    Optional<String> last = lastElement.getOptional(fruits);
    assertThat(last).contains("cherry");

    var someAffine = FocusPaths.<String>optionalSome();

    Optional<String> maybeValue = Optional.of("present");
    // SOLUTION: Unwrap optional
    Optional<String> unwrapped = someAffine.getOptional(maybeValue);
    assertThat(unwrapped).contains("present");

    Optional<String> emptyValue = Optional.empty();
    assertThat(someAffine.getOptional(emptyValue)).isEmpty();
  }
}
