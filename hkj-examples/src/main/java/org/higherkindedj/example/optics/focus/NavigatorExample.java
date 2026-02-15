// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.focus;

import org.higherkindedj.optics.Lens;
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

  // ============= Manual Lenses for Demonstration =============

  // These lenses simulate what the processor generates
  static final Lens<Company, Address> companyHeadquartersLens =
      Lens.of(Company::headquarters, (c, a) -> new Company(c.name(), a, c.employeeCount()));

  static final Lens<Address, String> addressCityLens =
      Lens.of(Address::city, (a, city) -> new Address(a.street(), city, a.postcode()));

  static final Lens<Address, String> addressStreetLens =
      Lens.of(Address::street, (a, street) -> new Address(street, a.city(), a.postcode()));

  // ============= Examples =============

  public static void main(String[] args) {
    System.out.println("=== Navigator Example ===\n");

    basicNavigatorUsage();
    navigatorDelegateMethods();
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

    // Without navigators - explicit composition required
    FocusPath<Company, Address> headquartersPath = FocusPath.of(companyHeadquartersLens);
    FocusPath<Company, String> cityPathManual = headquartersPath.via(addressCityLens);
    String cityManual = cityPathManual.get(company);
    System.out.println("City (manual composition): " + cityManual);

    // With navigators - the generated CompanyFocus.headquarters() returns a
    // HeadquartersNavigator that has a city() method, enabling:
    //
    //   String city = CompanyFocus.headquarters().city().get(company);
    //
    // This example shows what the generated code enables.
    // The navigator wraps the underlying FocusPath and delegates operations.
    System.out.println("With navigators: CompanyFocus.headquarters().city().get(company)");
    System.out.println("  -> Returns the city without explicit .via() call\n");
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

    // Demonstrate the operations that navigators support
    FocusPath<Company, Address> hqPath = FocusPath.of(companyHeadquartersLens);

    // get() - extract value
    Address hq = hqPath.get(company);
    System.out.println("get(): " + hq.city());

    // set() - replace value
    Company movedCompany = hqPath.set(new Address("789 New St", "Birmingham", "B1 1AA"), company);
    System.out.println("set(): Moved to " + hqPath.get(movedCompany).city());

    // modify() - transform value
    Company updatedCompany =
        hqPath.modify(
            addr -> new Address(addr.street().toUpperCase(), addr.city(), addr.postcode()),
            company);
    System.out.println("modify(): Street is now " + hqPath.get(updatedCompany).street());

    // With navigators, these same operations work on the nested path:
    //   CompanyFocus.headquarters().city().get(company)
    //   CompanyFocus.headquarters().city().set("Edinburgh", company)
    //   CompanyFocus.headquarters().city().modify(String::toUpperCase, company)
    System.out.println("\nNavigators enable the same operations on nested paths.\n");
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
   * // Depth 1: Returns DivisionNavigator
   * OrganisationFocus.mainDivision()
   *
   * // Depth 2: Returns FocusPath (not a navigator)
   * OrganisationFocus.mainDivision().department()
   *
   * // Beyond depth: Use .via() for further navigation
   * OrganisationFocus.mainDivision().department().via(DepartmentFocus.managerName().toLens())
   * }</pre>
   */
  static void depthLimitingExample() {
    System.out.println("--- Depth Limiting ---");

    System.out.println("With maxNavigatorDepth = 2:");
    System.out.println("  Depth 1: OrganisationFocus.mainDivision() -> DivisionNavigator");
    System.out.println("  Depth 2: .department() -> FocusPath<Organisation, Department>");
    System.out.println("  Beyond: Use .via(DepartmentFocus.managerName().toLens())\n");

    // This prevents excessive code generation for deeply nested structures
    // while still allowing navigation via explicit composition.
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

    System.out.println("With includeFields = {\"homeAddress\"}:");
    System.out.println(
        "  PersonFocus.homeAddress() -> HomeAddressNavigator (has city(), street())");
    System.out.println("  PersonFocus.workAddress() -> FocusPath<Person, Address> (no navigator)");
    System.out.println(
        "\nExcludeFields works similarly - excluded fields get standard FocusPath.\n");
  }
}
