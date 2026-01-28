// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.bridge;

import org.higherkindedj.example.optics.bridge.domain.Company;
import org.higherkindedj.example.optics.bridge.domain.CompanyFocus;
import org.higherkindedj.example.optics.bridge.domain.Department;
import org.higherkindedj.example.optics.bridge.domain.DepartmentFocus;
import org.higherkindedj.example.optics.bridge.domain.Employee;
import org.higherkindedj.example.optics.bridge.domain.EmployeeFocus;
import org.higherkindedj.example.optics.bridge.external.AddressOptics;
import org.higherkindedj.example.optics.bridge.external.ContactInfoOptics;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.TraversalPath;

/**
 * Bridge utilities connecting Focus DSL navigation to external type optics.
 *
 * <p>This class demonstrates the bridge pattern for maintaining fluent, IDE-discoverable navigation
 * across library boundaries. The Focus DSL provides paths through our local domain types (Company,
 * Department, Employee), and spec interface optics continue navigation into external types
 * (Address, ContactInfo).
 *
 * <h2>Pattern</h2>
 *
 * <pre>{@code
 * // 1. Use Focus to navigate local types
 * CompanyFocus.headquarters()  // Focus<Company, Address>
 *
 * // 2. Convert to Lens and compose with external optics
 *     .toLens()                 // Lens<Company, Address>
 *     .andThen(AddressOptics.city())  // Lens<Company, String>
 * }</pre>
 *
 * <h2>Benefits</h2>
 *
 * <ul>
 *   <li>Full IDE autocomplete at each step
 *   <li>Type safety across boundaries
 *   <li>Clean composition - Focus + Spec optics work together
 *   <li>Reusable bridge optics for common paths
 * </ul>
 *
 * <h2>Generated Focus for Collections</h2>
 *
 * <p>Note: For {@code List<T>} fields, the generated Focus returns {@code TraversalPath<S, T>}
 * directly (already traversed), not {@code FocusPath<S, List<T>>}. So:
 *
 * <ul>
 *   <li>{@code DepartmentFocus.staff()} returns {@code TraversalPath<Department, Employee>}
 *   <li>{@code CompanyFocus.departments()} returns {@code TraversalPath<Company, Department>}
 * </ul>
 */
public final class CompanyBridge {

  private CompanyBridge() {}

  // ========================================================================
  // Company → Address (headquarters) bridging
  // ========================================================================

  /**
   * Lens from Company to headquarters city.
   *
   * <p>Path: Company → headquarters (Focus) → city (AddressOptics)
   */
  public static final Lens<Company, String> HEADQUARTERS_CITY =
      CompanyFocus.headquarters().toLens().andThen(AddressOptics.city());

  /** Lens from Company to headquarters postcode. */
  public static final Lens<Company, String> HEADQUARTERS_POSTCODE =
      CompanyFocus.headquarters().toLens().andThen(AddressOptics.postcode());

  /** Lens from Company to headquarters country. */
  public static final Lens<Company, String> HEADQUARTERS_COUNTRY =
      CompanyFocus.headquarters().toLens().andThen(AddressOptics.country());

  // ========================================================================
  // Department → Address (location) bridging
  // ========================================================================

  /** Lens from Department to location city. */
  public static Lens<Department, String> departmentCity() {
    return DepartmentFocus.location().toLens().andThen(AddressOptics.city());
  }

  /** Lens from Department to location country. */
  public static Lens<Department, String> departmentCountry() {
    return DepartmentFocus.location().toLens().andThen(AddressOptics.country());
  }

  // ========================================================================
  // Employee → ContactInfo bridging
  // ========================================================================

  /** Lens from Employee to contact email. */
  public static Lens<Employee, String> employeeEmail() {
    return EmployeeFocus.contact().toLens().andThen(ContactInfoOptics.email());
  }

  /** Lens from Employee to contact phone. */
  public static Lens<Employee, String> employeePhone() {
    return EmployeeFocus.contact().toLens().andThen(ContactInfoOptics.phone());
  }

  // ========================================================================
  // Deep traversals across boundaries
  // TraversalPath provides getAll() and modifyAll() directly
  // ========================================================================

  /**
   * TraversalPath over all staff emails in a department.
   *
   * <p>Path: Department → staff[] (Focus) → contact → email (spec optics)
   *
   * <p>Note: DepartmentFocus.staff() already returns TraversalPath&lt;Department, Employee&gt; (the
   * generator auto-traverses List fields), so we compose directly with .via().
   */
  public static TraversalPath<Department, String> allStaffEmails() {
    // staff() returns TraversalPath<Department, Employee>
    // via(FocusPath) composes to TraversalPath<Department, ContactInfo>
    // via(Lens) composes to TraversalPath<Department, String>
    return DepartmentFocus.staff().via(EmployeeFocus.contact()).via(ContactInfoOptics.email());
  }

  /** TraversalPath over all staff phone numbers in a department. */
  public static TraversalPath<Department, String> allStaffPhones() {
    return DepartmentFocus.staff().via(EmployeeFocus.contact()).via(ContactInfoOptics.phone());
  }

  /**
   * TraversalPath over all department location cities in a company.
   *
   * <p>Path: Company → departments[] (Focus) → location → city (spec optics)
   */
  public static TraversalPath<Company, String> allDepartmentCities() {
    // departments() returns TraversalPath<Company, Department>
    return CompanyFocus.departments().via(DepartmentFocus.location()).via(AddressOptics.city());
  }

  /**
   * TraversalPath over all employee emails across the entire company.
   *
   * <p>Path: Company → departments[] → staff[] → contact → email
   */
  public static TraversalPath<Company, String> allCompanyEmails() {
    // departments() returns TraversalPath<Company, Department>
    // via(TraversalPath) composes to TraversalPath<Company, Employee>
    return CompanyFocus.departments()
        .via(DepartmentFocus.staff())
        .via(EmployeeFocus.contact())
        .via(ContactInfoOptics.email());
  }

  /** TraversalPath over all employee phone numbers across the entire company. */
  public static TraversalPath<Company, String> allCompanyPhones() {
    return CompanyFocus.departments()
        .via(DepartmentFocus.staff())
        .via(EmployeeFocus.contact())
        .via(ContactInfoOptics.phone());
  }
}
