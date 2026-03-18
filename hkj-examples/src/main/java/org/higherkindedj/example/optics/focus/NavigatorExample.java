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
 *   <li>Controlling navigator generation with {@code maxNavigatorDepth}, {@code includeFields}, and
 *       {@code excludeFields}
 *   <li>Falling back to {@code .via()} for deeper navigation beyond the depth limit
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

  // ============= Domain Model with Depth Limiting =============

  /**
   * An organisation with nested department structure.
   *
   * <p>The {@code maxNavigatorDepth = 2} limits how deep navigator generation goes. At depth 2, the
   * navigation returns plain {@code FocusPath} instances instead of further navigators.
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
   * {@code MapValueGenerator} with {@code ZERO_OR_MORE} cardinality. Navigator methods for this
   * field will return {@code TraversalPath} instead of {@code FocusPath}.
   *
   * <p>The {@code verifiedName} field is an {@code Either<String, String>}, which the SPI
   * recognises via {@code EitherGenerator} with {@code ZERO_OR_ONE} cardinality. Navigator methods
   * for this field will return {@code AffinePath} instead of {@code FocusPath}.
   */
  @GenerateFocus(generateNavigators = true)
  public record Warehouse(
      String name,
      Map<String, Integer> inventory,
      Either<String, String> verifiedName,
      Address location) {}

  // ============= Examples =============

  public static void main(String[] args) {
    System.out.println("=== Navigator Example ===\n");

    basicNavigatorUsage();
    navigatorDelegateMethods();
    spiAwareNavigationExample();
    depthLimitingExample();
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
   * Collection}. Each SPI generator declares a {@code Cardinality}:
   *
   * <ul>
   *   <li>{@code ZERO_OR_ONE} (Either, Try, Validated) → navigator returns {@code AffinePath}
   *   <li>{@code ZERO_OR_MORE} (Map, arrays, third-party collections) → navigator returns {@code
   *       TraversalPath}
   * </ul>
   *
   * <p>This means navigators correctly handle SPI-registered types without falling back to {@code
   * FocusPath}:
   *
   * <pre>{@code
   * // Map<String, Integer> field → TraversalPath (via MapValueGenerator SPI)
   * WarehouseFocus.inventory()  // Returns TraversalPath<Warehouse, Integer>
   *
   * // Either<String, String> field → AffinePath (via EitherGenerator SPI)
   * WarehouseFocus.verifiedName()  // Returns AffinePath<Warehouse, String>
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
    System.out.println("SPI cardinality summary:");
    System.out.println("  ZERO_OR_ONE  → AffinePath:    Either, Try, Validated, Optional, Maybe");
    System.out.println("  ZERO_OR_MORE → TraversalPath:  Map, List, Set, Collection");
    System.out.println();
  }

  /**
   * Demonstrates depth limiting with {@code maxNavigatorDepth}.
   *
   * <p>Navigator generation stops at the specified depth. Beyond this, navigation returns plain
   * {@code FocusPath} instances. For deeper access, use {@code .via()} composition.
   *
   * <pre>{@code
   * @GenerateFocus(generateNavigators = true, maxNavigatorDepth = 2)
   * record Organisation(Division mainDivision) {}
   *
   * // Depth 1: Returns MainDivisionNavigator (has nested navigators)
   * OrganisationFocus.mainDivision()
   *
   * // Depth 2: Returns DepartmentNavigator (delegate methods only, no deeper navigators)
   * OrganisationFocus.mainDivision().department()
   *
   * // Use .toPath() to access the underlying FocusPath for .via() composition
   * OrganisationFocus.mainDivision().department().toPath().via(DepartmentFocus.managerName().toLens())
   * }</pre>
   */
  static void depthLimitingExample() {
    System.out.println("--- Depth Limiting ---");

    Department engineering = new Department("Engineering", "Alice");
    Division rd = new Division("R&D", engineering);
    Organisation org = new Organisation("Acme Corp", rd);

    // Depth 1: mainDivision() returns a MainDivisionNavigator
    String divisionName = OrganisationFocus.mainDivision().name().get(org);
    System.out.println("Depth 1 - division name: " + divisionName);

    // Depth 2: department() returns a DepartmentNavigator with delegate methods
    // but no further nested navigators (depth exhausted)
    Department dept = OrganisationFocus.mainDivision().department().get(org);
    System.out.println("Depth 2 - department:    " + dept.name());

    // Access leaf fields directly through the navigator's delegate methods
    String deptName = OrganisationFocus.mainDivision().department().name().get(org);
    System.out.println("Depth 2 - dept name:     " + deptName);

    // Or use .toPath() to get the underlying FocusPath for .via() composition
    String manager =
        OrganisationFocus.mainDivision()
            .department()
            .toPath()
            .via(DepartmentFocus.managerName().toLens())
            .get(org);
    System.out.println("Via toPath().via():       " + manager);

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
