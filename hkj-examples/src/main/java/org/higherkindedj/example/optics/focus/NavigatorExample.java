// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.focus;

import java.util.Map;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.focus.FocusPath;

/**
 * Demonstrates fluent navigation using generated navigator classes.
 *
 * <p>This example shows how to use {@code @GenerateFocus(generateNavigators = true)} to enable
 * fluent cross-type navigation without explicit {@code .via()} calls.
 *
 * <h2>Key Concepts</h2>
 *
 * <ul>
 *   <li>Enabling navigator generation with {@code generateNavigators = true}
 *   <li>Fluent cross-type navigation: {@code CompanyFocus.headquarters().city()}
 *   <li>Navigator delegate methods: {@code get()}, {@code set()}, {@code modify()}
 *   <li>Choosing which fields get navigators with {@code includeFields} and {@code excludeFields}
 *   <li>What {@code maxNavigatorDepth} changes: only {@code maxNavigatorDepth = 1} changes the
 *       generated code
 *   <li>Composing with {@code .via()} from a navigator's underlying path
 *   <li>Using {@code widenCollections = true} to auto-widen SPI ZERO_OR_MORE types
 *   <li>SPI generator priority for resolving conflicts between overlapping generators
 * </ul>
 *
 * <h2>Comparison: With vs Without Navigators</h2>
 *
 * <p><strong>Without navigators</strong> (explicit composition):
 *
 * <pre>{@code
 * String city = CompanyFocus.headquarters()
 *     .via(AddressFocus.city().toLens())
 *     .get(company);
 * }</pre>
 *
 * <p><strong>With navigators</strong> (fluent navigation):
 *
 * <pre>{@code
 * String city = CompanyFocus.headquarters().city().get(company);
 * }</pre>
 *
 * <p>Navigators are generated for fields whose types are also annotated with
 * {@code @GenerateFocus(generateNavigators = true)}.
 */
public class NavigatorExample {

  // ============= Domain Model with Navigators Enabled =============

  /**
   * A company with a headquarters address.
   *
   * <p>With {@code generateNavigators = true}, the processor generates a {@code
   * HeadquartersNavigator} inner class in {@code CompanyFocus} that enables fluent navigation to
   * {@code Address} fields.
   */
  @GenerateFocus(generateNavigators = true)
  public record Company(String name, Address headquarters, int employeeCount) {}

  /**
   * An address with street and city fields.
   *
   * <p>Both fields are navigable from parent types when navigators are enabled.
   */
  @GenerateFocus(generateNavigators = true)
  public record Address(String street, String city, String postcode) {}

  // ============= Domain Model with a Navigator Depth Setting =============

  /**
   * An organisation with nested department structure.
   *
   * <p>It sets {@code maxNavigatorDepth = 2}, which leaves the chain unbroken. Each hop returns the
   * navigator that the {@code Focus} class of the record it leaves declares for that field: {@code
   * mainDivision()} returns {@code OrganisationFocus.MainDivisionNavigator}, and its {@code
   * department()} returns {@code DivisionFocus.DepartmentNavigator}. Each navigator is generated
   * once, in that one class, so the generated code is the same at every value above 1. Only {@code
   * maxNavigatorDepth = 1} changes the generated code: it makes a navigator's own navigation
   * methods return plain paths.
   */
  @GenerateFocus(generateNavigators = true, maxNavigatorDepth = 2)
  public record Organisation(String name, Division mainDivision) {}

  /** A division containing a department. */
  @GenerateFocus(generateNavigators = true)
  public record Division(String name, Department department) {}

  /** A department with a manager name. */
  @GenerateFocus(generateNavigators = true)
  public record Department(String name, String managerName) {}

  // ============= Domain Model with Field Filtering =============

  /**
   * A person with multiple addresses, demonstrating field filtering.
   *
   * <p>The {@code includeFields} attribute restricts navigator generation to only the specified
   * fields. Here, only {@code homeAddress} gets a navigator; {@code workAddress} uses standard
   * {@code FocusPath}.
   */
  @GenerateFocus(
      generateNavigators = true,
      includeFields = {"homeAddress"})
  public record Person(String name, Address homeAddress, Address workAddress) {}

  // ============= Domain Model with SPI-Aware Navigator Widening =============

  /**
   * A warehouse with inventory tracked as a Map and a location address.
   *
   * <p>The {@code inventory} field is a {@code Map<String, Integer>}, which the SPI recognises via
   * {@code MapValueGenerator} with {@code ZERO_OR_MORE} cardinality. Its values are not themselves
   * navigable, so the path stops at the map until {@code widenCollections} says otherwise — the
   * same answer whether the field is reached through {@code WarehouseFocus.inventory()} or through
   * a navigator on a record holding a {@code Warehouse}.
   *
   * <p>The {@code verifiedName} field is an {@code Either<String, String>}, which the SPI
   * recognises via {@code EitherGenerator} with {@code ZERO_OR_ONE} cardinality. A zero-or-one
   * container is always stepped into, so that path is an {@code AffinePath}.
   */
  @GenerateFocus(generateNavigators = true)
  public record Warehouse(
      String name,
      Map<String, Integer> inventory,
      Either<String, String> verifiedName,
      Address location) {}

  // ============= Domain Model with widenCollections =============

  /**
   * A shop with inventory tracked as a Map, demonstrating {@code widenCollections = true}.
   *
   * <p>By default, SPI-registered ZERO_OR_MORE container types (like {@code Map<K,V>}) produce
   * {@code FocusPath} in the generated Focus class. With {@code widenCollections = true}, they
   * automatically widen to {@code TraversalPath}, eliminating the need to manually call {@code
   * .each(eachInstance)}.
   *
   * <p>Compare:
   *
   * <ul>
   *   <li><b>Without</b> {@code widenCollections}: {@code ShopFocus.stock()} returns {@code
   *       FocusPath<Shop, Map<String, Integer>>} — requires manual {@code .each(mapValuesEach())}
   *   <li><b>With</b> {@code widenCollections}: {@code ShopWidenedFocus.stock()} returns {@code
   *       TraversalPath<ShopWidened, Integer>} — already widened
   * </ul>
   */
  @GenerateFocus
  public record Shop(String name, Map<String, Integer> stock) {}

  /** Same as {@link Shop} but with {@code widenCollections = true}. */
  @GenerateFocus(widenCollections = true)
  public record ShopWidened(String name, Map<String, Integer> stock) {}

  // ============= Examples =============

  public static void main(String[] args) {
    System.out.println("=== Navigator Example ===\n");

    basicNavigatorUsage();
    navigatorDelegateMethods();
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
   * CompanyFocus.headquarters().city()  // Returns FocusPath<Company, String>
   * }</pre>
   *
   * <p>Instead of:
   *
   * <pre>{@code
   * CompanyFocus.headquarters().via(AddressFocus.city().toLens())
   * }</pre>
   */
  static void basicNavigatorUsage() {
    System.out.println("--- Basic Navigator Usage ---");

    Company company =
        new Company("Acme Corp", new Address("123 Main St", "London", "SW1A 1AA"), 100);

    // Without navigators - explicit composition with .via()
    String cityManual =
        CompanyFocus.headquarters().toPath().via(AddressFocus.city().toLens()).get(company);
    System.out.println("City (manual .via() composition): " + cityManual);

    // With navigators - fluent cross-type navigation
    String cityNavigator = CompanyFocus.headquarters().city().get(company);
    System.out.println("City (navigator):                 " + cityNavigator);

    // Navigate to a different field just as easily
    String street = CompanyFocus.headquarters().street().get(company);
    System.out.println("Street (navigator):               " + street);

    System.out.println();
  }

  /**
   * Demonstrates navigator delegate methods.
   *
   * <p>Navigator classes delegate all {@code FocusPath} operations to the underlying path:
   *
   * <ul>
   *   <li>{@code get(source)} - Extract the focused value
   *   <li>{@code set(value, source)} - Replace the focused value
   *   <li>{@code modify(f, source)} - Transform the focused value
   *   <li>{@code toPath()} - Access the underlying {@code FocusPath}
   *   <li>{@code toLens()} - Extract the underlying {@code Lens}
   * </ul>
   */
  static void navigatorDelegateMethods() {
    System.out.println("--- Navigator Delegate Methods ---");

    Company company = new Company("TechCo", new Address("456 Oak Ave", "Manchester", "M1 1AA"), 50);

    // get() on the navigator - extract the nested Address
    Address hq = CompanyFocus.headquarters().get(company);
    System.out.println("get():    " + hq);

    // set() on the navigator - replace the entire Address
    Company movedCompany =
        CompanyFocus.headquarters().set(new Address("789 New St", "Birmingham", "B1 1AA"), company);
    System.out.println(
        "set():    Moved to " + CompanyFocus.headquarters().get(movedCompany).city());

    // modify() on the navigator - transform the Address
    Company updatedCompany =
        CompanyFocus.headquarters()
            .modify(
                addr -> new Address(addr.street().toUpperCase(), addr.city(), addr.postcode()),
                company);
    System.out.println(
        "modify(): Street is now " + CompanyFocus.headquarters().get(updatedCompany).street());

    // Navigated delegate methods work on nested paths too
    FocusPath<Company, String> cityPath = CompanyFocus.headquarters().city();
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
   *       {@code widenCollections = true}, or when the element is itself a navigable record and the
   *       container has to be stepped into to reach it
   * </ul>
   *
   * <p>A navigator method reports the same path type as the static Focus method for the same
   * component, so this rule is read once and holds whichever way the field is reached:
   *
   * <pre>{@code
   * // Either<String, String> field → AffinePath (via EitherGenerator SPI)
   * WarehouseFocus.verifiedName()  // Returns AffinePath<Warehouse, String>
   *
   * // Map<String, Integer> field → the path stops at the map without widenCollections
   * WarehouseFocus.inventory()  // Returns FocusPath<Warehouse, Map<String, Integer>>
   * }</pre>
   */
  static void spiAwareNavigationExample() {
    System.out.println("--- SPI-Aware Navigator Path Widening ---");

    Warehouse warehouse =
        new Warehouse(
            "Central",
            Map.of("widgets", 100, "gadgets", 50),
            Either.right("Verified Central"),
            new Address("10 Dock Rd", "Bristol", "BS1 1AA"));

    // location is a plain record field → navigator returns FocusPath delegates
    String locationCity = WarehouseFocus.location().city().get(warehouse);
    System.out.println("location().city():    " + locationCity);

    Warehouse movedWarehouse = WarehouseFocus.location().city().set("Cardiff", warehouse);
    System.out.println(
        "After set(Cardiff):   " + WarehouseFocus.location().city().get(movedWarehouse));

    // Standard Focus fields on Warehouse for non-navigable types
    String name = WarehouseFocus.name().get(warehouse);
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
   * FocusPath<Shop, Map<String, Integer>> stock = ShopFocus.stock();
   * TraversalPath<Shop, Integer> values = stock.each(EachInstances.mapValuesEach());
   *
   * // With widenCollections = true:
   * TraversalPath<ShopWidened, Integer> values = ShopWidenedFocus.stock();
   * }</pre>
   */
  static void widenCollectionsExample() {
    System.out.println("--- widenCollections Attribute ---");

    Shop shop = new Shop("Corner Shop", Map.of("apples", 50, "bread", 30, "milk", 20));
    ShopWidened shopW =
        new ShopWidened("Corner Shop", Map.of("apples", 50, "bread", 30, "milk", 20));

    // Without widenCollections: stock() returns FocusPath<Shop, Map<String, Integer>>
    // Must manually widen to traverse into Map values.
    var stockPath = ShopFocus.stock(); // FocusPath<Shop, Map<String, Integer>>
    System.out.println("ShopFocus.stock() type:        " + stockPath.getClass().getSimpleName());
    System.out.println("  Raw Map value:               " + stockPath.get(shop));

    // With widenCollections = true: stock() returns TraversalPath<ShopWidened, Integer>
    // Already widened — no manual .each() call needed.
    var stockWidened = ShopWidenedFocus.stock(); // TraversalPath<ShopWidened, Integer>
    System.out.println("ShopWidenedFocus.stock() type: " + stockWidened.getClass().getSimpleName());
    System.out.println("  All stock values:            " + stockWidened.getAll(shopW));
    System.out.println(
        "  Total stock:                 "
            + stockWidened.getAll(shopW).stream().mapToInt(Integer::intValue).sum());

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
   * returns the navigator declared in the {@code Focus} class of the record the hop leaves, so the
   * chain runs on to the leaf. Only {@code maxNavigatorDepth = 1} changes the generated code: a
   * navigator's own navigation methods then return plain paths, and a further hop is composed with
   * {@code .via()}.
   *
   * <pre>{@code
   * @GenerateFocus(generateNavigators = true, maxNavigatorDepth = 2)
   * record Organisation(Division mainDivision) {}
   *
   * // Hop 1: returns OrganisationFocus.MainDivisionNavigator, over the Division
   * OrganisationFocus.mainDivision()
   *
   * // Hop 2: returns DivisionFocus.DepartmentNavigator, over the Department
   * OrganisationFocus.mainDivision().department()
   *
   * // Hop 3: returns a plain FocusPath to the department's name, which is a leaf
   * OrganisationFocus.mainDivision().department().name()
   *
   * // .toPath() hands over the underlying FocusPath, for composing with .via()
   * OrganisationFocus.mainDivision().department().toPath().via(DepartmentFocus.managerName().toLens())
   * }</pre>
   */
  static void navigatorDepthExample() {
    System.out.println("--- Navigator Depth ---");

    Department engineering = new Department("Engineering", "Alice");
    Division rd = new Division("R&D", engineering);
    Organisation org = new Organisation("Acme Corp", rd);

    // mainDivision() returns a navigator over the Division, which offers its name()
    String divisionName = OrganisationFocus.mainDivision().name().get(org);
    System.out.println("Division name:    " + divisionName);

    // department() returns DivisionFocus.DepartmentNavigator: Division is the record it leaves
    Department dept = OrganisationFocus.mainDivision().department().get(org);
    System.out.println("Department:       " + dept.name());

    // maxNavigatorDepth = 2 does not stop the chain: the Department's fields are one hop further
    String deptName = OrganisationFocus.mainDivision().department().name().get(org);
    System.out.println("Department name:  " + deptName);

    // Or use .toPath() to hand over the underlying FocusPath, for composing with .via()
    String manager =
        OrganisationFocus.mainDivision()
            .department()
            .toPath()
            .via(DepartmentFocus.managerName().toLens())
            .get(org);
    System.out.println("Via toPath().via(): " + manager);

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
   * <p>If both are specified, {@code includeFields} takes precedence.
   *
   * <pre>{@code
   * @GenerateFocus(generateNavigators = true, includeFields = {"homeAddress"})
   * record Person(String name, Address homeAddress, Address workAddress) {}
   *
   * // homeAddress gets a navigator
   * PersonFocus.homeAddress().city()  // Returns FocusPath<Person, String>
   *
   * // workAddress uses standard FocusPath (no navigator)
   * PersonFocus.workAddress()  // Returns FocusPath<Person, Address>
   * PersonFocus.workAddress().via(AddressFocus.city().toLens())  // Explicit composition
   * }</pre>
   */
  static void fieldFilteringExample() {
    System.out.println("--- Field Filtering ---");

    Address home = new Address("1 Home Lane", "Oxford", "OX1 1AA");
    Address work = new Address("2 Office St", "Reading", "RG1 1AA");
    Person person = new Person("Bob", home, work);

    // homeAddress has a navigator (included in includeFields)
    String homeCity = PersonFocus.homeAddress().city().get(person);
    System.out.println("homeAddress (navigator): " + homeCity);

    // workAddress returns plain FocusPath (not in includeFields)
    FocusPath<Person, Address> workPath = PersonFocus.workAddress();
    String workCity = workPath.via(AddressFocus.city().toLens()).get(person);
    System.out.println("workAddress (via()):     " + workCity);

    // Modify through the navigator
    Person movedPerson = PersonFocus.homeAddress().city().set("Cambridge", person);
    System.out.println(
        "After move home:         " + PersonFocus.homeAddress().city().get(movedPerson));

    System.out.println();
  }
}
