// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.bridge;

import org.higherkindedj.example.optics.bridge.domain.Office;
import org.higherkindedj.example.optics.bridge.domain.OfficeFocus;
import org.higherkindedj.example.optics.bridge.domain.OfficeNetwork;
import org.higherkindedj.example.optics.bridge.domain.OfficeNetworkFocus;
import org.higherkindedj.example.optics.bridge.domain.OfficeWorker;
import org.higherkindedj.example.optics.bridge.domain.OfficeWorkerFocus;
import org.higherkindedj.example.optics.bridge.external.AddressOptics;
import org.higherkindedj.example.optics.bridge.external.ContactDetailsOptics;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.TraversalPath;

/**
 * Bridge utilities connecting Focus DSL navigation to external type optics.
 *
 * <p>This class demonstrates the bridge pattern for maintaining fluent, IDE-discoverable navigation
 * across library boundaries. The Focus DSL provides paths through our local domain types
 * (OfficeNetwork, Office, OfficeWorker), and spec interface optics continue navigation into
 * external types (Address, ContactDetails).
 *
 * <h2>Pattern</h2>
 *
 * <pre>{@code
 * // 1. Use Focus to navigate local types
 * OfficeNetworkFocus.headquarters()  // FocusPath<OfficeNetwork, Address>
 *
 * // 2. Convert to Lens and compose with external optics
 *     .toLens()                 // Lens<OfficeNetwork, Address>
 *     .andThen(AddressOptics.city())  // Lens<OfficeNetwork, String>
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
 *   <li>{@code OfficeFocus.staff()} returns {@code TraversalPath<Office, OfficeWorker>}
 *   <li>{@code OfficeNetworkFocus.offices()} returns {@code TraversalPath<OfficeNetwork, Office>}
 * </ul>
 */
public final class CompanyBridge {

  private CompanyBridge() {}

  // ========================================================================
  // OfficeNetwork → Address (headquarters) bridging
  // ========================================================================

  /**
   * Lens from OfficeNetwork to headquarters city.
   *
   * <p>Path: OfficeNetwork → headquarters (Focus) → city (AddressOptics)
   */
  // ANCHOR: lens_bridge
  public static final Lens<OfficeNetwork, String> HEADQUARTERS_CITY =
      OfficeNetworkFocus.headquarters().toLens().andThen(AddressOptics.city());

  // ANCHOR_END: lens_bridge

  /** Lens from OfficeNetwork to headquarters postcode. */
  public static final Lens<OfficeNetwork, String> HEADQUARTERS_POSTCODE =
      OfficeNetworkFocus.headquarters().toLens().andThen(AddressOptics.postcode());

  // ========================================================================
  // Office → Address (location) bridging
  // ========================================================================

  /** Lens from Office to location city. */
  public static Lens<Office, String> officeCity() {
    return OfficeFocus.location().toLens().andThen(AddressOptics.city());
  }

  // ========================================================================
  // OfficeWorker → ContactDetails bridging
  // ========================================================================

  /** Lens from OfficeWorker to contact email. */
  public static Lens<OfficeWorker, String> employeeEmail() {
    return OfficeWorkerFocus.contact().toLens().andThen(ContactDetailsOptics.email());
  }

  /** Lens from OfficeWorker to contact phone. */
  public static Lens<OfficeWorker, String> employeePhone() {
    return OfficeWorkerFocus.contact().toLens().andThen(ContactDetailsOptics.phone());
  }

  // ========================================================================
  // Deep traversals across boundaries
  // TraversalPath provides getAll() and modifyAll() directly
  // ========================================================================

  /**
   * TraversalPath over all staff emails in an office.
   *
   * <p>Path: Office → staff[] (Focus) → contact → email (spec optics)
   *
   * <p>Note: OfficeFocus.staff() already returns TraversalPath&lt;Office, OfficeWorker&gt; (the
   * generator auto-traverses List fields), so we compose directly with .via().
   */
  public static TraversalPath<Office, String> allStaffEmails() {
    // staff() returns TraversalPath<Office, OfficeWorker>
    // via(FocusPath) composes to TraversalPath<Office, ContactDetails>
    // via(Lens) composes to TraversalPath<Office, String>
    return OfficeFocus.staff().via(OfficeWorkerFocus.contact()).via(ContactDetailsOptics.email());
  }

  /** TraversalPath over all staff phone numbers in an office. */
  public static TraversalPath<Office, String> allStaffPhones() {
    return OfficeFocus.staff().via(OfficeWorkerFocus.contact()).via(ContactDetailsOptics.phone());
  }

  /**
   * TraversalPath over all office location cities in a company.
   *
   * <p>Path: OfficeNetwork → offices[] (Focus) → location → city (spec optics)
   */
  public static TraversalPath<OfficeNetwork, String> allOfficeCities() {
    // offices() returns TraversalPath<OfficeNetwork, Office>
    return OfficeNetworkFocus.offices().via(OfficeFocus.location()).via(AddressOptics.city());
  }

  /**
   * TraversalPath over all employee emails across the entire company.
   *
   * <p>Path: OfficeNetwork → offices[] → staff[] → contact → email
   */
  // ANCHOR: traversal_bridge
  public static TraversalPath<OfficeNetwork, String> allCompanyEmails() {
    // offices() returns TraversalPath<OfficeNetwork, Office>
    // via(TraversalPath) composes to TraversalPath<OfficeNetwork, OfficeWorker>
    return OfficeNetworkFocus.offices()
        .via(OfficeFocus.staff())
        .via(OfficeWorkerFocus.contact())
        .via(ContactDetailsOptics.email());
  }

  // ANCHOR_END: traversal_bridge

  /** TraversalPath over all employee phone numbers across the entire company. */
  public static TraversalPath<OfficeNetwork, String> allCompanyPhones() {
    return OfficeNetworkFocus.offices()
        .via(OfficeFocus.staff())
        .via(OfficeWorkerFocus.contact())
        .via(ContactDetailsOptics.phone());
  }
}
