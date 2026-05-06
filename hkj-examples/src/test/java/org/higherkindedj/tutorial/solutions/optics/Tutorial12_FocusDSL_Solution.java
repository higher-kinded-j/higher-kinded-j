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
 * Solution for Tutorial12 FocusDSL — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
 */
public class Tutorial12_FocusDSL_Solution {

  /**
   * Why this is idiomatic: {@code FocusPath.of(lens)} lifts a lens into the path DSL where {@code
   * via}, {@code each}, {@code at}, and the rest of the navigation vocabulary become available. The
   * path reads as a sentence and accepts further refinements.
   *
   * <p>Alternative: keep the lens and call its methods directly. Same answer; the {@code FocusPath}
   * wrapper is what allows the next step to be {@code .via(...)} rather than a hand-rolled
   * composition.
   *
   * <p>Common wrong attempt: skip the path and call {@code lens.get} for reads but {@code lens.set}
   * with the wrong argument order for writes. The path's {@code get}/{@code set} are source-first
   * by convention; staying inside the DSL keeps the order consistent.
   */
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

  /**
   * Why this is idiomatic: {@code .via(lens)} extends the path one step deeper. Reading the call
   * chain top-to-bottom mirrors how the field would be described in conversation — "person →
   * address → street → name".
   *
   * <p>Alternative: chain {@code andThen} on raw lenses. Equivalent; the {@code via} spelling reads
   * better when the path navigation outweighs the lens algebra.
   *
   * <p>Common wrong attempt: try to compose two paths with {@code andThen}. The DSL provides {@code
   * via} for path-to-path concatenation; reach for {@code andThen} only when working at the raw
   * optic level.
   */
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

  /**
   * Why this is idiomatic: {@code .some()} on a lens-into-{@code Optional} produces an {@code
   * AffinePath} that reads partially. {@code matches} answers presence; {@code getOptional} carries
   * the absent case forward.
   *
   * <p>Alternative: compose the {@code lens} with {@code Prisms.some()} explicitly. Same outcome;
   * the DSL's {@code some()} is a one-word shorthand for the common case.
   *
   * <p>Common wrong attempt: use {@code .get()} on the path expecting a plain value. The affine
   * cannot guarantee a value exists; the type forces you to handle absence.
   */
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

  /**
   * Why this is idiomatic: {@code .each()} promotes a list-shaped lens into a {@code
   * TraversalPath}. {@code getAll}, {@code modifyAll}, and {@code count} attach naturally to the
   * resulting path.
   *
   * <p>Alternative: build a {@code Traversal} explicitly with the listTraversal helper. Same
   * answer; the DSL's {@code each()} is the named shortcut and signals iteration intent cleanly.
   *
   * <p>Common wrong attempt: try to call {@code get} on a traversal-shaped path expecting the first
   * element. {@code getAll} returns the whole collection — pick {@code at(0)} with an affine if
   * "the first element" is what you want.
   */
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

  /**
   * Why this is idiomatic: {@code .at(i)} narrows a list-shaped lens to a single index — an {@code
   * AffinePath} because the index may be out of bounds. {@code set} writes through the same path
   * and rebuilds the list.
   *
   * <p>Alternative: read the list, mutate {@code list.set(i, value)}, write it back. Works for
   * mutable lists; many record-shaped fields are immutable lists where this fails.
   *
   * <p>Common wrong attempt: assume {@code at(10)} on a three-element list throws. It returns
   * {@code Optional.empty()} — the affine carries the absence forward, just like other partial
   * reads.
   */
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

  /**
   * Why this is idiomatic: {@code .atKey(key)} narrows a map-shaped lens to a single value. The
   * result is an {@code AffinePath} because the key may be absent — present and missing keys are
   * answered by the same partial read.
   *
   * <p>Alternative: read the map and call {@code get(key)} expecting a possibly-{@code null} value.
   * Loses the typed absence; the affine wrapper makes the missing case impossible to forget.
   *
   * <p>Common wrong attempt: write through {@code atKey} with a map literal that has a different
   * value type. The lens enforces the map's value type at the call site; mismatch is a compile
   * error.
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

    // SOLUTION: Create path to map key using atKey()
    AffinePath<Settings, String> themePath = FocusPath.of(valuesLens).atKey("theme");

    // SOLUTION: Get the theme value
    Optional<String> theme = themePath.getOptional(settings);
    assertThat(theme).contains("dark");

    AffinePath<Settings, String> missingPath = FocusPath.of(valuesLens).atKey("missing");
    assertThat(missingPath.getOptional(settings)).isEmpty();
  }

  /**
   * Why this is idiomatic: chain {@code each()}, {@code via}, and {@code some()} — every
   * department, then its optional manager, then the present-only manager. The {@code TraversalPath}
   * returns the present managers and rewrites them in a single pass.
   *
   * <p>Alternative: a stream pipeline that filters out empty {@code Optional}s and then maps. Same
   * reads; the corresponding writes are what the path makes a one-line update.
   *
   * <p>Common wrong attempt: forget {@code some()} and try to traverse over an {@code
   * Optional<String>} as if it were always present. The path would be ill-formed; the explicit
   * {@code some()} confirms the partiality.
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

  /**
   * Why this is idiomatic: {@code traversalPath.filter(predicate)} narrows the focus to elements
   * that match. {@code modifyAll} on the filtered path applies the discount only to the expensive
   * products; the cheap ones pass through untouched.
   *
   * <p>Alternative: stream over the products, partition, modify the matching half, and concatenate.
   * Works once; the filtered traversal is the optic-driven version that stays inspectable.
   *
   * <p>Common wrong attempt: pre-filter the list before applying the modify. The cheap products are
   * now missing from the result; let the path's {@code filter} keep them and just exclude them from
   * the rewrite.
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

  /**
   * Why this is idiomatic: {@code toLens}, {@code asAffine}, and {@code asTraversal} drop the path
   * back into the underlying optic when an external API expects that shape. The conversions
   * preserve semantics — a lens always succeeds, the affine and traversal show one focus.
   *
   * <p>Alternative: keep the path everywhere. Possible if the surrounding API accepts {@code
   * FocusPath}; reach for the conversions when interoperating with other libraries.
   *
   * <p>Common wrong attempt: cast a path to a different optic shape. The conversions are named
   * because they encode small contracts (e.g. {@code asAffine} gives an always- matching affine); a
   * cast would skip the validation.
   */
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

  /**
   * Why this is idiomatic: {@code FocusPaths.listElements()}, {@code listAt(0)}, and {@code
   * listLast()} are pre-built traversals/affines for the most common list patterns. Reach for them
   * when the lens-then-{@code each()} chain would just rebuild the same canonical optic.
   *
   * <p>Alternative: build the optics by hand. Equivalent; the utility methods cut the boilerplate
   * to one call.
   *
   * <p>Common wrong attempt: assume {@code listLast()} on an empty list throws. It returns {@code
   * Optional.empty()}; the affine carries the absence forward like any other partial read.
   */
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
