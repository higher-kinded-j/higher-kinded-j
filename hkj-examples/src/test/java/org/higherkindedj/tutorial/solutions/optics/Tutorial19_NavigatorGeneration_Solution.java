// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.each.EachInstances;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.junit.jupiter.api.Test;

/**
 * Solutions for Tutorial 19: Navigator Generation - Fluent Cross-Type Navigation
 *
 * <p>These are the complete solutions for all exercises in Tutorial 19.
 */
public class Tutorial19_NavigatorGeneration_Solution {

  // --- Domain models for exercises ---

  record Address(String street, String city, String postcode) {}

  record Company(String name, Address headquarters, int employeeCount) {}

  record Department(String deptName, List<String> members) {}

  record Organisation(String orgName, Optional<Address> registeredOffice) {}

  record Warehouse(String id, Map<String, Integer> inventory) {}

  // --- Lenses ---

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

  @Test
  void exercise1_navigatorDelegationGet() {
    Company acme = new Company("Acme", new Address("123 Main St", "London", "SW1A 1AA"), 100);

    // SOLUTION: Compose FocusPath through headquarters then via city lens
    FocusPath<Company, String> cityPath = FocusPath.of(companyHqLens).via(addressCityLens);

    // SOLUTION: Use the path to get the city
    String city = cityPath.get(acme);

    assertThat(city).isEqualTo("London");
  }

  @Test
  void exercise2_navigatorSetAndModify() {
    Company acme = new Company("Acme", new Address("123 Main St", "London", "SW1A 1AA"), 100);

    FocusPath<Company, String> cityPath = FocusPath.of(companyHqLens).via(addressCityLens);

    // SOLUTION: Use set to change the city
    Company relocated = cityPath.set("Edinburgh", acme);

    assertThat(relocated.headquarters().city()).isEqualTo("Edinburgh");
    assertThat(relocated.name()).isEqualTo("Acme");
    assertThat(relocated.employeeCount()).isEqualTo(100);

    // SOLUTION: Use modify to transform the city
    Company shouted = cityPath.modify(String::toUpperCase, acme);

    assertThat(shouted.headquarters().city()).isEqualTo("LONDON");
  }

  @Test
  void exercise3_pathWideningOptional() {
    Organisation org =
        new Organisation("HKJ Ltd", Optional.of(new Address("1 Elm Rd", "Oxford", "OX1 1AA")));
    Organisation noOffice = new Organisation("Ghost Ltd", Optional.empty());

    // SOLUTION: Create AffinePath via .some() for Optional field
    AffinePath<Organisation, Address> officePath = FocusPath.of(orgOfficeLens).some();

    // SOLUTION: getOptional on present value
    Optional<Address> presentAddress = officePath.getOptional(org);

    assertThat(presentAddress).isPresent();
    assertThat(presentAddress.get().city()).isEqualTo("Oxford");

    // SOLUTION: getOptional on empty value
    Optional<Address> absentAddress = officePath.getOptional(noOffice);

    assertThat(absentAddress).isEmpty();
  }

  @Test
  void exercise4_pathWideningCollection() {
    Department engineering = new Department("Engineering", List.of("Alice", "Bob", "Charlie"));

    Lens<Department, List<String>> membersLens =
        Lens.of(Department::members, (d, m) -> new Department(d.deptName(), m));

    // SOLUTION: Create TraversalPath via .each() for List field
    TraversalPath<Department, String> allMembers = FocusPath.of(membersLens).each();

    // SOLUTION: getAll to retrieve all members
    List<String> names = allMembers.getAll(engineering);

    assertThat(names).containsExactly("Alice", "Bob", "Charlie");

    // SOLUTION: modifyAll to transform all members
    Department shouted = allMembers.modifyAll(String::toUpperCase, engineering);

    assertThat(shouted.members()).containsExactly("ALICE", "BOB", "CHARLIE");
  }

  @Test
  void exercise5_spiAwareWideningMap() {
    Warehouse warehouse = new Warehouse("W1", Map.of("bolts", 100, "nuts", 250, "washers", 50));

    // SOLUTION: Create TraversalPath via .each(mapValuesEach()) for Map field (SPI: ZERO_OR_MORE)
    TraversalPath<Warehouse, Integer> allQuantities =
        FocusPath.of(warehouseInventoryLens).each(EachInstances.mapValuesEach());

    // SOLUTION: getAll to retrieve all inventory quantities
    List<Integer> quantities = allQuantities.getAll(warehouse);

    assertThat(quantities).containsExactlyInAnyOrder(100, 250, 50);
  }

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

    // SOLUTION: Compose through Optional then List (AFFINE + TRAVERSAL = TRAVERSAL)
    // Break chain into steps to help Java's type inference with local records
    AffinePath<Team, Department> deptPath = FocusPath.of(deptLens).some();
    TraversalPath<Team, String> allMembers = deptPath.via(membersLens).each();

    // SOLUTION: getAll on present team
    List<String> members = allMembers.getAll(team);

    assertThat(members).containsExactly("Alice", "Bob");

    // SOLUTION: getAll on empty team
    List<String> noMembers = allMembers.getAll(emptyTeam);

    assertThat(noMembers).isEmpty();
  }

  @Test
  void exercise8_widenCollectionsAndPriority() {
    Warehouse warehouse = new Warehouse("W1", Map.of("bolts", 100, "nuts", 250, "washers", 50));

    // SOLUTION: Create TraversalPath via .each(mapValuesEach()) — this is what
    // widenCollections = true does automatically for ZERO_OR_MORE SPI types
    TraversalPath<Warehouse, Integer> allQuantities =
        FocusPath.of(warehouseInventoryLens).each(EachInstances.mapValuesEach());

    // SOLUTION: modifyAll to double every quantity
    Warehouse doubled = allQuantities.modifyAll(q -> q * 2, warehouse);

    // All quantities should be doubled
    List<Integer> quantities = allQuantities.getAll(doubled);
    assertThat(quantities).containsExactlyInAnyOrder(200, 500, 100);
  }

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

    // SOLUTION: Compose a deep path using .via() chaining
    FocusPath<University, String> deepCityPath =
        FocusPath.of(campusLens).via(buildingLens).via(addressLens).via(addressCityLens);

    // SOLUTION: Get the city
    String city = deepCityPath.get(oxford);

    assertThat(city).isEqualTo("Oxford");

    // SOLUTION: Set a new city
    University moved = deepCityPath.set("Cambridge", oxford);

    assertThat(moved.campus().mainBuilding().address().city()).isEqualTo("Cambridge");
  }
}
