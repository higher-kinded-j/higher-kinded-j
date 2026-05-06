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
 * Solution for Tutorial19 NavigatorGeneration — teaching-solution format.
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

  /**
   * Why this is idiomatic: a generated navigator delegates to {@code FocusPath.via} chains. The
   * path reads as a sentence — Company → headquarters → city — and {@code get} returns the leaf
   * string.
   *
   * <p>Alternative: invoke the lenses directly with {@code companyHqLens.andThen(addressCityLens)}.
   * Same answer; the navigator-style spelling is what the generator emits.
   *
   * <p>Common wrong attempt: skip the navigator and re-implement the path elsewhere. The navigator
   * is the single source of truth; duplications drift.
   */
  @Test
  void exercise1_navigatorDelegationGet() {
    Company acme = new Company("Acme", new Address("123 Main St", "London", "SW1A 1AA"), 100);

    // SOLUTION: Compose FocusPath through headquarters then via city lens
    FocusPath<Company, String> cityPath = FocusPath.of(companyHqLens).via(addressCityLens);

    // SOLUTION: Use the path to get the city
    String city = cityPath.get(acme);

    assertThat(city).isEqualTo("London");
  }

  /**
   * Why this is idiomatic: the same navigator path used for {@code get} also serves {@code set} and
   * {@code modify}. Each operation rebuilds the {@code Company} with only the city changed; other
   * fields remain.
   *
   * <p>Alternative: rebuild the {@code Company} explicitly with {@code new Company(..., new
   * Address(..., newCity, ...))}. Same answer; the path-driven version stays one-line.
   *
   * <p>Common wrong attempt: assume {@code modify} mutates the input. Records are immutable; {@code
   * modify} returns a fresh value.
   */
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

  /**
   * Why this is idiomatic: an {@code Optional} field demands an {@code AffinePath}. The generated
   * navigator widens through {@code .some()} so the partiality lives in the type, not in the call
   * sites.
   *
   * <p>Alternative: use a focus path and call {@code .map} after {@code get}. Awkward and loses the
   * corresponding {@code modify}; the affine handles both directions.
   *
   * <p>Common wrong attempt: try to use a {@code FocusPath} on an {@code Optional} field and reach
   * for {@code get}. The path's type prevents that — convert to an affine.
   */
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

  /**
   * Why this is idiomatic: a collection field demands a {@code TraversalPath}. The generator widens
   * through {@code .each()} so the multiplicity is part of the type.
   *
   * <p>Alternative: read the list, stream-map, write back. Same answer; the path keeps the
   * navigation declarative.
   *
   * <p>Common wrong attempt: forget that {@code modifyAll} returns the rebuilt structure; assume
   * the original was mutated. Records and lists alike are immutable.
   */
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

  /**
   * Why this is idiomatic: {@code .each(EachInstances.mapValuesEach())} widens a {@code Map<K, V>}
   * field into a traversal over its values. The SPI declares the container's multiplicity
   * (ZERO_OR_MORE) and the navigator picks the right widener.
   *
   * <p>Alternative: hand-roll a custom traversal that walks the map's values. The SPI handles this
   * for known container types.
   *
   * <p>Common wrong attempt: pass {@code listEach()} for a map field. Wrong instance; the compiler
   * rejects the mismatch.
   */
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

  /**
   * Why this is idiomatic: {@code AffinePath} (Optional) plus {@code TraversalPath} (List) yields a
   * {@code TraversalPath} — the compound shape is the wider of the two. Empty optionals contribute
   * zero focuses; non-empty contribute every member.
   *
   * <p>Alternative: bind intermediate paths to local variables. The compiler can need the help with
   * local records; once they live at the top level the inline chain is fine.
   *
   * <p>Common wrong attempt: assume the result is still affine. The list multiplies the
   * multiplicity; the combined path is a traversal.
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

  /**
   * Why this is idiomatic: turning {@code widenCollections = true} on the navigator generator emits
   * the {@code .each(mapValuesEach())} step automatically — the same call you would write by hand.
   *
   * <p>Alternative: leave widening off and write {@code .each(...)} at every call site. Same
   * answer; turn widening on once a navigator stabilises and the manual call is boilerplate.
   *
   * <p>Common wrong attempt: rely on widening for unknown container types. The SPI needs to know
   * about the container; for custom collections, register an {@code Each} instance first.
   */
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

  /**
   * Why this is idiomatic: the navigator generator can be told to stop at a given depth. Composing
   * the lenses by hand to the depth you actually need keeps the API surface small and prevents
   * callers from drilling into private layers.
   *
   * <p>Alternative: generate every possible navigator and rely on access modifiers. The generated
   * surface is smaller when the depth limit matches the public contract.
   *
   * <p>Common wrong attempt: assume the depth limit silently truncates deeper paths. Past the
   * limit, the helper stops generating; the navigator does not magically extend.
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
