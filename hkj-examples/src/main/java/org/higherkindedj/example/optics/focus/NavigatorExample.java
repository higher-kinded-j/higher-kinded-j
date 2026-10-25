// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.focus;

import java.util.List;
import java.util.Map;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.focus.FocusPath;

/**
 * Demonstrates fluent navigation using generated navigator classes.
 *
 * <p>This example shows the navigators {@code @GenerateFocus} generates, which enable fluent
 * cross-type navigation without explicit {@code .via()} calls.
 *
 * <h2>Key Concepts</h2>
 *
 * <ul>
 *   <li>Fluent cross-type navigation: {@code CompanyFocus.address().city()}
 *   <li>A navigator is a path: {@code get()}, {@code set()}, {@code modify()} and the rest work on
 *       it directly
 *   <li>Choosing which fields get navigators with {@code includeFields} and {@code excludeFields}
 *   <li>What {@code maxNavigatorDepth} changes: only a value of 1 or less changes the generated
 *       code
 *   <li>Composing with {@code .via()} straight from a navigator
 *   <li>Using {@code widenCollections = true} to auto-widen SPI ZERO_OR_MORE types
 *   <li>SPI generator priority for resolving conflicts between overlapping generators
 * </ul>
 *
 * <h2>Comparison: Explicit Composition vs Fluent Navigation</h2>
 *
 * <p><strong>Explicit composition</strong>, which a navigator still accepts:
 *
 * <pre>{@code
 * String city = CompanyFocus.address()
 *     .via(AddressFocus.city())
 *     .get(company);
 * }</pre>
 *
 * <p><strong>Fluent navigation</strong>:
 *
 * <pre>{@code
 * String city = CompanyFocus.address().city().get(company);
 * }</pre>
 *
 * <p>Navigators are generated for fields whose types are also annotated with
 * {@code @GenerateFocus}. A record turns them off with {@code @GenerateFocus(generateNavigators =
 * false)}.
 */
public class NavigatorExample {

  // ============= Domain Model with Navigators Enabled =============

  /**
   * A company with an address.
   *
   * <p>The processor generates an {@code AddressNavigator} inner class in {@code CompanyFocus}, a
   * {@code FocusPath} that also navigates fluently to {@code Address} fields.
   */
  @GenerateFocus
  public record Company(String name, Address address) {}

  /**
   * An address with street and city fields.
   *
   * <p>Both fields are navigable from parent types through their navigators.
   */
  @GenerateFocus
  public record Address(String street, String city, String postcode) {}

  // ============= Domain Model with a Navigator Depth Setting =============

  /**
   * An organisation with nested department structure.
   *
   * <p>It sets {@code maxNavigatorDepth = 2}, which leaves the chain unbroken. Each hop returns the
   * navigator that the {@code Focus} class of the record it leaves declares for that field: {@code
   * division()} returns {@code OrganisationFocus.DivisionNavigator}, and its {@code department()}
   * returns {@code DivisionFocus.DepartmentNavigator}. Each navigator is generated once, in that
   * one class, so the generated code is the same at every value above 1. Only a value of 1 or less
   * changes the generated code: it makes a navigator's own navigation methods return plain paths.
   */
  @GenerateFocus(maxNavigatorDepth = 2)
  public record Organisation(Division division) {}

  /** A division containing a department. */
  @GenerateFocus
  public record Division(String name, Department department) {}

  /** A department and its staff. */
  @GenerateFocus
  public record Department(String name, List<Employee> staff) {}

  /** A member of a department's staff, and the company they work for. */
  public record Employee(String name, Company company) {}

  // ============= Domain Model with Field Filtering =============

  /**
   * A commuter with a home address and a work address, demonstrating field filtering.
   *
   * <p>The {@code includeFields} attribute restricts navigator generation to only the specified
   * fields. Here, only {@code homeAddress} gets a navigator; {@code workAddress} uses standard
   * {@code FocusPath}.
   */
  @GenerateFocus(includeFields = {"homeAddress"})
  public record Commuter(String name, Address homeAddress, Address workAddress) {}

  // ============= Domain Model with SPI-Aware Navigator Widening =============

  /**
   * A distributor with inventory tracked as a Map and a location address.
   *
   * <p>The {@code inventory} field is a {@code Map<String, Integer>}, which the SPI recognises via
   * {@code MapValueGenerator} with {@code ZERO_OR_MORE} cardinality. The path stops at the map
   * until {@code widenCollections} or {@code widenFields} widens it, whatever its values are, and
   * the answer is the same whether the field is reached through {@code
   * DistributorFocus.inventory()} or through a navigator on a record holding a {@code Distributor}.
   *
   * <p>The {@code verifiedName} field is an {@code Either<String, String>}, which the SPI
   * recognises via {@code EitherGenerator} with {@code ZERO_OR_ONE} cardinality. A zero-or-one
   * container is always stepped into, so that path is an {@code AffinePath}.
   */
  @GenerateFocus
  public record Distributor(
      String name,
      Map<String, Integer> inventory,
      Either<String, String> verifiedName,
      Address location) {}

  // ============= Domain Model with widenCollections =============

  /**
   * A grocer with stock tracked as a Map, demonstrating {@code widenCollections = true}.
   *
   * <p>By default, SPI-registered ZERO_OR_MORE container types (like {@code Map<K,V>}) produce
   * {@code FocusPath} in the generated Focus class. With {@code widenCollections = true}, they
   * automatically widen to {@code TraversalPath}, eliminating the need to manually call {@code
   * .each(eachInstance)}.
   *
   * <p>Compare:
   *
   * <ul>
   *   <li><b>Without</b> {@code widenCollections}: {@code GrocerFocus.stock()} returns {@code
   *       FocusPath<Grocer, Map<String, Integer>>} — requires manual {@code .each(mapValuesEach())}
   *   <li><b>With</b> {@code widenCollections}: {@code GrocerWidenedFocus.stock()} returns {@code
   *       TraversalPath<GrocerWidened, Integer>} — already widened
   * </ul>
   */
  @GenerateFocus
  public record Grocer(String name, Map<String, Integer> stock) {}

  /** Same as {@link Grocer} but with {@code widenCollections = true}. */
  @GenerateFocus(widenCollections = true)
  public record GrocerWidened(String name, Map<String, Integer> stock) {}

  // ============= Examples =============

  public static void main(String[] args) {
    System.out.println("=== Navigator Example ===\n");

    basicNavigatorUsage();
    navigatorPathMethods();
    spiAwareNavigationExample();
    widenCollectionsExample();
    spiPriorityExample();
    navigatorDepthExample();
    fieldFilteringExample();
  }

  /**
   * Demonstrates basic fluent navigation using generated navigators.
   *
   * <p>When the annotation processor runs on {@code Company} and {@code Address}, it generates
   * navigator classes that enable:
   *
   * <pre>{@code
   * CompanyFocus.address().city()  // Returns FocusPath<Company, String>
   * }</pre>
   *
   * <p>Instead of:
   *
   * <pre>{@code
   * CompanyFocus.address().via(AddressFocus.city().toLens())
   * }</pre>
   */
  static void basicNavigatorUsage() {
    System.out.println("--- Basic Navigator Usage ---");

    Company company = new Company("Acme Corp", new Address("123 Main St", "London", "SW1A 1AA"));

    // Explicit composition with .via(), straight on the navigator
    String cityManual = CompanyFocus.address().via(AddressFocus.city()).get(company);
    System.out.println("City (manual .via() composition): " + cityManual);

    // Fluent cross-type navigation
    String cityNavigator = CompanyFocus.address().city().get(company);
    System.out.println("City (navigator):                 " + cityNavigator);

    // Navigate to a different field just as easily
    String street = CompanyFocus.address().street().get(company);
    System.out.println("Street (navigator):               " + street);

    System.out.println();
  }

  /**
   * Demonstrates that a navigator is a path.
   *
   * <p>A navigator is a {@code FocusPath}, with a navigation method for each field of the record it
   * reaches, so every path operation works on it directly:
   *
   * <ul>
   *   <li>{@code get(source)} - Extract the focused value
   *   <li>{@code set(value, source)} - Replace the focused value
   *   <li>{@code modify(f, source)} - Transform the focused value
   *   <li>{@code via(...)}, {@code toLens()} and the rest - as on any {@code FocusPath}
   * </ul>
   */
  static void navigatorPathMethods() {
    System.out.println("--- A Navigator Is a Path ---");

    Company company = new Company("TechCo", new Address("456 Oak Ave", "Manchester", "M1 1AA"));

    // get() on the navigator - extract the nested Address
    Address address = CompanyFocus.address().get(company);
    System.out.println("get():    " + address);

    // set() on the navigator - replace the entire Address
    Company movedCompany =
        CompanyFocus.address().set(new Address("789 New St", "Birmingham", "B1 1AA"), company);
    System.out.println("set():    Moved to " + CompanyFocus.address().get(movedCompany).city());

    // modify() on the navigator - transform the Address
    Company updatedCompany =
        CompanyFocus.address()
            .modify(
                addr -> new Address(addr.street().toUpperCase(), addr.city(), addr.postcode()),
                company);
    System.out.println(
        "modify(): Street is now " + CompanyFocus.address().get(updatedCompany).street());

    // The path a navigation method returns works the same way
    FocusPath<Company, String> cityPath = CompanyFocus.address().city();
    System.out.println("Nested get():    " + cityPath.get(company));

    Company renamedCity = cityPath.set("Edinburgh", company);
    System.out.println("Nested set():    " + cityPath.get(renamedCity));

    Company uppercasedCity = cityPath.modify(String::toUpperCase, company);
    System.out.println("Nested modify(): " + cityPath.get(uppercasedCity));

    System.out.println();
  }

  /**
   * Demonstrates SPI-aware navigator path widening.
   *
   * <p>The {@code TraversableGenerator} SPI allows the processor to recognise container types
   * beyond the hardcoded {@code Optional}, {@code Maybe}, {@code List}, {@code Set}, and {@code
   * Collection}. Each SPI generator declares a {@code Cardinality}, which decides the tier its
   * container reaches:
   *
   * <ul>
   *   <li>{@code ZERO_OR_ONE} (Either, Try, Validated) → {@code AffinePath}, always
   *   <li>{@code ZERO_OR_MORE} (Map, arrays, third-party collections) → {@code TraversalPath} under
   *       {@code widenCollections = true}, or {@code widenFields} naming the field; a navigator
   *       over a navigable element follows the widening and never forces it
   * </ul>
   *
   * <p>A navigator method reports the same path type as the static Focus method for the same
   * component, so this rule is read once and holds whichever way the field is reached:
   *
   * <pre>{@code
   * // Either<String, String> field → AffinePath (via EitherGenerator SPI)
   * DistributorFocus.verifiedName()  // Returns AffinePath<Distributor, String>
   *
   * // Map<String, Integer> field → the path stops at the map without widenCollections
   * DistributorFocus.inventory()  // Returns FocusPath<Distributor, Map<String, Integer>>
   * }</pre>
   */
  static void spiAwareNavigationExample() {
    System.out.println("--- SPI-Aware Navigator Path Widening ---");

    Distributor distributor =
        new Distributor(
            "Central",
            Map.of("widgets", 100, "gadgets", 50),
            Either.right("Verified Central"),
            new Address("10 Dock Rd", "Bristol", "BS1 1AA"));

    // location is a plain record field → its navigator is a FocusPath that navigates on
    String locationCity = DistributorFocus.location().city().get(distributor);
    System.out.println("location().city():    " + locationCity);

    Distributor movedDistributor = DistributorFocus.location().city().set("Cardiff", distributor);
    System.out.println(
        "After set(Cardiff):   " + DistributorFocus.location().city().get(movedDistributor));

    // Standard Focus fields on Distributor for non-navigable types
    String name = DistributorFocus.name().get(distributor);
    System.out.println("name():               " + name);

    System.out.println();
    System.out.println("Path tier by container:");
    System.out.println("  ZERO_OR_ONE  → AffinePath:    Either, Try, Validated, Optional, Maybe");
    System.out.println("  ZERO_OR_MORE → TraversalPath: List, Set, Collection (built in)");
    System.out.println("                                Map and the rest under widenCollections");
    System.out.println();
  }

  /**
   * Demonstrates the {@code widenCollections} annotation attribute.
   *
   * <p>By default, SPI-registered ZERO_OR_MORE container types (like {@code Map<K,V>}) produce
   * {@code FocusPath} in the generated Focus class. Users must manually call {@code
   * .each(eachInstance)} to get a {@code TraversalPath}.
   *
   * <p>With {@code @GenerateFocus(widenCollections = true)}, the processor automatically applies
   * the SPI's optic expression, producing {@code TraversalPath} directly.
   *
   * <pre>{@code
   * // Without widenCollections (default):
   * FocusPath<Grocer, Map<String, Integer>> stock = GrocerFocus.stock();
   * TraversalPath<Grocer, Integer> values = stock.each(EachInstances.mapValuesEach());
   *
   * // With widenCollections = true:
   * TraversalPath<GrocerWidened, Integer> values = GrocerWidenedFocus.stock();
   * }</pre>
   */
  static void widenCollectionsExample() {
    System.out.println("--- widenCollections Attribute ---");

    Grocer grocer = new Grocer("Corner Shop", Map.of("apples", 50, "bread", 30, "milk", 20));
    GrocerWidened grocerW =
        new GrocerWidened("Corner Shop", Map.of("apples", 50, "bread", 30, "milk", 20));

    // Without widenCollections: stock() returns FocusPath<Grocer, Map<String, Integer>>
    // Must manually widen to traverse into Map values.
    var stockPath = GrocerFocus.stock(); // FocusPath<Grocer, Map<String, Integer>>
    System.out.println("GrocerFocus.stock() type:        " + stockPath.getClass().getSimpleName());
    System.out.println("  Raw Map value:                 " + stockPath.get(grocer));

    // With widenCollections = true: stock() returns TraversalPath<GrocerWidened, Integer>
    // Already widened — no manual .each(...) call needed.
    var stockWidened = GrocerWidenedFocus.stock(); // TraversalPath<GrocerWidened, Integer>
    System.out.println(
        "GrocerWidenedFocus.stock() type: " + stockWidened.getClass().getSimpleName());
    System.out.println("  All stock values:              " + stockWidened.getAll(grocerW));
    System.out.println(
        "  Total stock:                   "
            + stockWidened.getAll(grocerW).stream().mapToInt(Integer::intValue).sum());

    System.out.println();
  }

  /**
   * Demonstrates the SPI priority system for resolving conflicts.
   *
   * <p>When multiple {@code TraversableGenerator} SPI providers support the same type, priority
   * determines which one wins. Higher values win; equal priorities emit a compile-time warning.
   *
   * <p>Priority constants:
   *
   * <ul>
   *   <li>{@code PRIORITY_FALLBACK} (-100) — catch-all generators
   *   <li>{@code PRIORITY_DEFAULT} (0) — standard generators (the default)
   *   <li>{@code PRIORITY_OVERRIDE} (100) — explicit overrides of built-in generators
   * </ul>
   *
   * <p>This system allows third-party libraries to provide custom generators that override or
   * coexist with built-in ones without conflicts.
   */
  static void spiPriorityExample() {
    System.out.println("--- SPI Generator Priority ---");

    System.out.println("TraversableGenerator priority constants:");
    System.out.println("  PRIORITY_FALLBACK (-100): Catch-all / fallback generators");
    System.out.println("  PRIORITY_DEFAULT  (  0): Standard generators (built-in)");
    System.out.println("  PRIORITY_OVERRIDE ( 100): Explicit overrides of built-ins");
    System.out.println();
    System.out.println("Resolution rules:");
    System.out.println("  1. Generators are sorted by priority (highest first)");
    System.out.println("  2. The first matching generator wins");
    System.out.println("  3. Equal-priority conflicts emit a compile-time WARNING");
    System.out.println("  4. Higher-priority match silently takes precedence");
    System.out.println();
    System.out.println("Example: A custom ImmutableList generator with PRIORITY_OVERRIDE");
    System.out.println("would override the built-in Eclipse Collections generator.");

    System.out.println();
  }

  /**
   * Demonstrates what {@code maxNavigatorDepth} does, and does not, change.
   *
   * <p>With any value above 1, the default 3 included, each hop into another navigable record
   * returns the navigator declared in the {@code Focus} class of the record the hop leaves, or its
   * form for a wider path, so depth ends no chain. Only a value of 1 or less changes the generated
   * code: a navigator's own navigation methods then return plain paths, and a further hop is
   * composed with {@code .via()}.
   *
   * <pre>{@code
   * @GenerateFocus(maxNavigatorDepth = 2)
   * record Organisation(Division division) {}
   *
   * // Hop 1: returns OrganisationFocus.DivisionNavigator, over the Division
   * OrganisationFocus.division()
   *
   * // Hop 2: returns DivisionFocus.DepartmentNavigator, over the Department
   * OrganisationFocus.division().department()
   *
   * // Hop 3: returns a plain FocusPath to the department's name, which is a leaf
   * OrganisationFocus.division().department().name()
   *
   * // A navigator is a FocusPath, so .via() composes straight on
   * OrganisationFocus.division().department().via(DepartmentFocus.staff())
   * }</pre>
   */
  static void navigatorDepthExample() {
    System.out.println("--- Navigator Depth ---");

    Company acme = new Company("Acme Corp", new Address("123 Main St", "London", "SW1A 1AA"));
    Department engineering = new Department("Engineering", List.of(new Employee("Alice", acme)));
    Division rd = new Division("R&D", engineering);
    Organisation org = new Organisation(rd);

    // division() returns a navigator over the Division, which offers its name()
    String divisionName = OrganisationFocus.division().name().get(org);
    System.out.println("Division name:    " + divisionName);

    // department() returns DivisionFocus.DepartmentNavigator: Division is the record it leaves
    Department dept = OrganisationFocus.division().department().get(org);
    System.out.println("Department:       " + dept.name());

    // maxNavigatorDepth = 2 does not stop the chain: the Department's fields are one hop further
    String deptName = OrganisationFocus.division().department().name().get(org);
    System.out.println("Department name:  " + deptName);

    // Or compose with .via(), straight on the navigator
    List<Employee> staff =
        OrganisationFocus.division().department().via(DepartmentFocus.staff()).getAll(org);
    System.out.println("Via .via():       " + staff.getFirst().name());

    System.out.println();
  }

  /**
   * Demonstrates field filtering with {@code includeFields} and {@code excludeFields}.
   *
   * <p>Control which fields get navigator generation:
   *
   * <ul>
   *   <li>{@code includeFields = {"field1", "field2"}} - Only these fields get navigators
   *   <li>{@code excludeFields = {"field3"}} - These fields use standard {@code FocusPath}
   * </ul>
   *
   * <p>Set one or the other: beside a non-empty {@code includeFields}, {@code excludeFields} is
   * never read, and the processor warns. A name that matches no component is an error.
   *
   * <pre>{@code
   * @GenerateFocus(includeFields = {"homeAddress"})
   * record Commuter(String name, Address homeAddress, Address workAddress) {}
   *
   * // homeAddress gets a navigator
   * CommuterFocus.homeAddress().city()  // Returns FocusPath<Commuter, String>
   *
   * // workAddress uses standard FocusPath (no navigator)
   * CommuterFocus.workAddress()  // Returns FocusPath<Commuter, Address>
   * CommuterFocus.workAddress().via(AddressFocus.city().toLens())  // Explicit composition
   * }</pre>
   */
  static void fieldFilteringExample() {
    System.out.println("--- Field Filtering ---");

    Address home = new Address("1 Home Lane", "Oxford", "OX1 1AA");
    Address work = new Address("2 Office St", "Reading", "RG1 1AA");
    Commuter person = new Commuter("Bob", home, work);

    // homeAddress has a navigator (included in includeFields)
    String homeCity = CommuterFocus.homeAddress().city().get(person);
    System.out.println("homeAddress (navigator): " + homeCity);

    // workAddress returns plain FocusPath (not in includeFields)
    FocusPath<Commuter, Address> workPath = CommuterFocus.workAddress();
    String workCity = workPath.via(AddressFocus.city().toLens()).get(person);
    System.out.println("workAddress (via()):     " + workCity);

    // Modify through the navigator
    Commuter movedPerson = CommuterFocus.homeAddress().city().set("Cambridge", person);
    System.out.println(
        "After move home:         " + CommuterFocus.homeAddress().city().get(movedPerson));

    System.out.println();
  }
}
