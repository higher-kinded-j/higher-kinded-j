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
import org.higherkindedj.optics.focus.TraversalPath;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 19: Navigator Generation - Fluent Cross-Type Navigation
 *
 * <p>When you annotate records with {@code @GenerateFocus(generateNavigators = true)}, the
 * processor generates navigator classes that enable fluent, dot-chained navigation across type
 * boundaries, without explicit {@code .via()} calls.
 *
 * <p>This tutorial teaches you how navigator path widening works at a conceptual level, using
 * manually composed paths to illustrate the behaviour that the generated code provides
 * automatically.
 *
 * <p>Key concepts:
 *
 * <ul>
 *   <li><b>Navigator delegation</b>: Navigators wrap a path and delegate get/set/modify
 *   <li><b>Path kind widening</b>: FocusPath widens to AffinePath or TraversalPath through
 *       container types
 *   <li><b>SPI-aware widening</b>: The {@code TraversableGenerator} SPI with {@code Cardinality}
 *       determines correct widening for types like Map, Either, Try, and Validated
 *   <li><b>Compound widening</b>: AFFINE + TRAVERSAL = TRAVERSAL; widening only increases
 *   <li><b>Depth limiting</b>: {@code maxNavigatorDepth} controls how deep navigation goes
 * </ul>
 *
 * <p>Prerequisites: Complete Tutorial 12 (Focus DSL) before this one.
 *
 * <p>See the documentation: Focus DSL in hkj-book
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
public class Tutorial19_NavigatorGeneration {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // --- Domain models for exercises ---

  record Address(String street, String city, String postcode) {}

  record Company(String name, Address headquarters, int employeeCount) {}

  record Department(String deptName, List<String> members) {}

  record Organisation(String orgName, Optional<Address> registeredOffice) {}

  record Warehouse(String id, Map<String, Integer> inventory) {}

  // --- Lenses (simulating what @GenerateFocus generates) ---

  static final Lens<Company, Address> companyHqLens =
      Lens.of(Company::headquarters, (c, a) -> new Company(c.name(), a, c.employeeCount()));

  static final Lens<Address, String> addressCityLens =
      Lens.of(Address::city, (a, city) -> new Address(a.street(), city, a.postcode()));

  static final Lens<Address, String> addressStreetLens =
      Lens.of(Address::street, (a, street) -> new Address(street, a.city(), a.postcode()));

  static final Lens<Company, String> companyNameLens =
      Lens.of(Company::name, (c, n) -> new Company(n, c.headquarters(), c.employeeCount()));

  static final Lens<Organisation, Optional<Address>> orgOfficeLens =
      Lens.of(Organisation::registeredOffice, (o, addr) -> new Organisation(o.orgName(), addr));

  static final Lens<Warehouse, Map<String, Integer>> warehouseInventoryLens =
      Lens.of(Warehouse::inventory, (w, inv) -> new Warehouse(w.id(), inv));

  /**
   * Exercise 1: Navigator delegation with get
   *
   * <p>A navigator wraps a FocusPath and delegates operations to it. In generated code, calling
   * {@code CompanyFocus.headquarters()} returns a {@code HeadquartersNavigator} that wraps a {@code
   * FocusPath<Company, Address>}. The navigator's {@code city()} method composes further.
   *
   * <p>Here we simulate the navigator pattern manually: compose a path from Company to city, then
   * use it to extract a value.
   *
   * <p>Task: Compose companyHqLens and addressCityLens into a FocusPath, then get the city
   */
  @Test
  void exercise1_navigatorDelegationGet() {
    Company acme = new Company("Acme", new Address("123 Main St", "London", "SW1A 1AA"), 100);

    // TODO: Create a FocusPath for headquarters, then compose via addressCityLens
    // Hint: FocusPath.of(companyHqLens).via(addressCityLens)
    FocusPath<Company, String> cityPath = answerRequired();

    // TODO: Use the path to get the city from acme
    String city = answerRequired();

    assertThat(city).isEqualTo("London");
  }

  /**
   * Exercise 2: Navigator set and modify
   *
   * <p>Navigators delegate set and modify to the underlying path. Setting replaces the focused
   * value; modifying transforms it with a function. Both return a new structure with the rest
   * unchanged.
   *
   * <p>Task: Use the composed path to set and modify the city
   */
  @Test
  void exercise2_navigatorSetAndModify() {
    Company acme = new Company("Acme", new Address("123 Main St", "London", "SW1A 1AA"), 100);

    FocusPath<Company, String> cityPath = FocusPath.of(companyHqLens).via(addressCityLens);

    // TODO: Use cityPath.set to change the city to "Edinburgh"
    Company relocated = answerRequired();

    assertThat(relocated.headquarters().city()).isEqualTo("Edinburgh");
    assertThat(relocated.name()).isEqualTo("Acme"); // Name unchanged
    assertThat(relocated.employeeCount()).isEqualTo(100); // Count unchanged

    // TODO: Use cityPath.modify to convert the city to upper case
    // Hint: cityPath.modify(String::toUpperCase, acme)
    Company shouted = answerRequired();

    assertThat(shouted.headquarters().city()).isEqualTo("LONDON");
  }

  /**
   * Exercise 3: Path widening with Optional (AffinePath)
   *
   * <p>When a field is {@code Optional<A>}, the navigator widens from FocusPath to AffinePath.
   * AffinePath focuses on zero or one element; its {@code getOptional()} returns an {@code
   * Optional<A>} instead of a bare value.
   *
   * <p>Task: Create an AffinePath through an Optional field and retrieve the value
   */
  @Test
  void exercise3_pathWideningOptional() {
    Organisation org =
        new Organisation("HKJ Ltd", Optional.of(new Address("1 Elm Rd", "Oxford", "OX1 1AA")));
    Organisation noOffice = new Organisation("Ghost Ltd", Optional.empty());

    // TODO: Create a FocusPath for registeredOffice, then widen to AffinePath via .some()
    // Hint: FocusPath.of(orgOfficeLens).some()
    AffinePath<Organisation, Address> officePath = answerRequired();

    // TODO: Use officePath.getOptional to get the address from org
    Optional<Address> presentAddress = answerRequired();

    assertThat(presentAddress).isPresent();
    assertThat(presentAddress.get().city()).isEqualTo("Oxford");

    // TODO: Use officePath.getOptional on noOffice (should be empty)
    Optional<Address> absentAddress = answerRequired();

    assertThat(absentAddress).isEmpty();
  }

  /**
   * Exercise 4: Path widening with collections (TraversalPath)
   *
   * <p>When a field is a {@code List<A>}, the navigator widens to TraversalPath. TraversalPath
   * focuses on zero or more elements. Its {@code getAll()} returns a {@code List<A>} of all focused
   * values, and {@code modifyAll()} transforms every element.
   *
   * <p>Task: Create a TraversalPath through a List field
   */
  @Test
  void exercise4_pathWideningCollection() {
    Department engineering = new Department("Engineering", List.of("Alice", "Bob", "Charlie"));

    Lens<Department, List<String>> membersLens =
        Lens.of(Department::members, (d, m) -> new Department(d.deptName(), m));

    // TODO: Create a FocusPath for members, then widen to TraversalPath via .each()
    // Hint: FocusPath.of(membersLens).each()
    TraversalPath<Department, String> allMembers = answerRequired();

    // TODO: Use allMembers.getAll to get all member names
    List<String> names = answerRequired();

    assertThat(names).containsExactly("Alice", "Bob", "Charlie");

    // TODO: Use allMembers.modifyAll to convert all names to upper case
    Department shouted = answerRequired();

    assertThat(shouted.members()).containsExactly("ALICE", "BOB", "CHARLIE");
  }

  /**
   * Exercise 5: SPI-aware widening with Map (TraversalPath)
   *
   * <p>The {@code TraversableGenerator} SPI recognises {@code Map<K,V>} via {@code
   * MapValueGenerator} and reports {@code Cardinality.ZERO_OR_MORE}. In navigator generation, this
   * means a Map field produces a TraversalPath, just like List or Set.
   *
   * <p>Here we simulate this by creating a TraversalPath over Map values using {@code .each()}.
   *
   * <p>Task: Create a TraversalPath that focuses on all Map values
   */
  @Test
  void exercise5_spiAwareWideningMap() {
    Warehouse warehouse = new Warehouse("W1", Map.of("bolts", 100, "nuts", 250, "washers", 50));

    // TODO: Create a FocusPath for inventory, then widen to TraversalPath via
    // .each(mapValuesEach())
    // Hint: FocusPath.of(warehouseInventoryLens).each(EachInstances.mapValuesEach())
    TraversalPath<Warehouse, Integer> allQuantities = answerRequired();

    // TODO: Use allQuantities.getAll to retrieve all inventory quantities
    List<Integer> quantities = answerRequired();

    assertThat(quantities).containsExactlyInAnyOrder(100, 250, 50);
  }

  /**
   * Exercise 6: Compound widening (AFFINE + TRAVERSAL = TRAVERSAL)
   *
   * <p>When you navigate through an Optional field (AFFINE) and then into a collection field
   * (TRAVERSAL), the path widens to TraversalPath. Widening only ever increases:
   *
   * <ul>
   *   <li>FOCUS + AFFINE = AFFINE
   *   <li>FOCUS + TRAVERSAL = TRAVERSAL
   *   <li>AFFINE + TRAVERSAL = TRAVERSAL
   *   <li>TRAVERSAL + anything = TRAVERSAL
   * </ul>
   *
   * <p>Task: Navigate through Optional then into a collection to produce a TraversalPath
   */
  @Test
  void exercise6_compoundWidening() {
    record Team(String teamName, Optional<Department> optionalDept) {}

    Team team =
        new Team("Alpha", Optional.of(new Department("Engineering", List.of("Alice", "Bob"))));
    Team emptyTeam = new Team("Ghost", Optional.empty());

    Lens<Team, Optional<Department>> deptLens =
        Lens.of(Team::optionalDept, (t, d) -> new Team(t.teamName(), d));
    Lens<Department, List<String>> membersLens =
        Lens.of(Department::members, (d, m) -> new Department(d.deptName(), m));

    // TODO: Compose a path through Optional<Department> then into List<String> members
    // This should produce a TraversalPath (AFFINE + TRAVERSAL = TRAVERSAL)
    // Hint: Break into steps: AffinePath<Team, Department> deptPath =
    // FocusPath.of(deptLens).some();
    //       then deptPath.via(membersLens).each()
    TraversalPath<Team, String> allMembers = answerRequired();

    // TODO: Use allMembers.getAll on team (should have 2 members)
    List<String> members = answerRequired();

    assertThat(members).containsExactly("Alice", "Bob");

    // TODO: Use allMembers.getAll on emptyTeam (should be empty)
    List<String> noMembers = answerRequired();

    assertThat(noMembers).isEmpty();
  }

  /**
   * Exercise 7: Navigator depth limiting
   *
   * <p>The {@code maxNavigatorDepth} annotation attribute controls how many levels of navigator
   * classes are generated. Beyond this depth, navigation falls back to explicit {@code .via()}
   * composition.
   *
   * <p>For example, with {@code maxNavigatorDepth = 1}:
   *
   * <pre>{@code
   * CompanyFocus.headquarters()           // → HeadquartersNavigator (depth 1)
   * CompanyFocus.headquarters().street()   // → FocusPath (plain path, no deeper navigator)
   * }</pre>
   *
   * <p>Beyond the limit, you compose manually using {@code .via()}.
   *
   * <p>Task: Compose a three-level path manually (simulating beyond-depth navigation)
   */
  @Test
  void exercise7_depthLimiting() {
    record Building(String buildingName, Address address) {}

    record Campus(String campusName, Building mainBuilding) {}

    record University(String uniName, Campus campus) {}

    Lens<University, Campus> campusLens =
        Lens.of(University::campus, (u, c) -> new University(u.uniName(), c));
    Lens<Campus, Building> buildingLens =
        Lens.of(Campus::mainBuilding, (c, b) -> new Campus(c.campusName(), b));
    Lens<Building, Address> addressLens =
        Lens.of(Building::address, (b, a) -> new Building(b.buildingName(), a));

    University oxford =
        new University(
            "Oxford",
            new Campus(
                "Main", new Building("Bodleian", new Address("Broad St", "Oxford", "OX1 3BG"))));

    // TODO: Compose a path from University all the way to city
    // Navigate: University → Campus → Building → Address → city
    // Hint: FocusPath.of(campusLens).via(buildingLens).via(addressLens).via(addressCityLens)
    FocusPath<University, String> deepCityPath = answerRequired();

    // TODO: Use the path to get the city
    String city = answerRequired();

    assertThat(city).isEqualTo("Oxford");

    // TODO: Use the path to set a new city
    University moved = answerRequired();

    assertThat(moved.campus().mainBuilding().address().city()).isEqualTo("Cambridge");
  }

  /**
   * Congratulations! You've completed Tutorial 19: Navigator Generation
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>How navigators wrap FocusPath and delegate get/set/modify
   *   <li>How path widening works: FocusPath → AffinePath → TraversalPath
   *   <li>That the SPI determines correct widening for Map, Either, Try, and Validated
   *   <li>That compound widening only increases (AFFINE + TRAVERSAL = TRAVERSAL)
   *   <li>How maxNavigatorDepth controls generation depth, with .via() as a fallback
   * </ul>
   *
   * <p>Key takeaways:
   *
   * <ul>
   *   <li>In production, use @GenerateFocus(generateNavigators = true) to get this behaviour
   *       automatically
   *   <li>The TraversableGenerator SPI is extensible; custom generators can declare their own
   *       Cardinality
   *   <li>Navigator generation respects includeFields and excludeFields for fine-grained control
   * </ul>
   *
   * <p>Next: See the Solutions Reference for all solutions
   */
}
