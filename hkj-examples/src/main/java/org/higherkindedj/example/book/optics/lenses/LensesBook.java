// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.lenses;

import java.util.Locale;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/lenses.html">Lenses</a> page. The page
 * {@code {{#include}}}s the anchored region, and {@code LensesBookTest} holds the claims the page
 * makes about this code.
 */
public final class LensesBook {

  /** The composed lens the page builds in its Step 2. */
  static final Lens<Employee, String> EMPLOYEE_TO_STREET =
      EmployeeLenses.company().andThen(CompanyLenses.address()).andThen(AddressLenses.street());

  private LensesBook() {}

  static Employee moveByWithers(Employee employee) {
    // ANCHOR: cascade
    Employee moved =
        employee.withCompany(
            employee.company().withAddress(employee.company().address().withStreet("456 Main St")));
    // ANCHOR_END: cascade
    return moved;
  }

  // ANCHOR: boundary_fix
  // The boundary normalises what comes in, and the record keeps what it is given
  static ContactEmail fromRequest(String raw) {
    return new ContactEmail(raw.strip().toLowerCase(Locale.ROOT));
  }

  // ANCHOR_END: boundary_fix
}

// ANCHOR: normalised_email
// The record behind the page's ticket: its constructor lowercases every address
@GenerateLenses
record NormalisedEmail(String value) {
  NormalisedEmail {
    value = value.toLowerCase(Locale.ROOT);
  }
}

// ANCHOR_END: normalised_email

// ANCHOR: contact_email
@GenerateLenses
record ContactEmail(String value) {}

// ANCHOR_END: contact_email

// Each record carries the instance withers Lombok's @With generates for it, written out by hand.
// Lombok's also return `this` when the new value is the one already held, which no claim here
// depends on.

@GenerateLenses
record Address(String street, String city) {
  Address withStreet(String street) {
    return new Address(street, city);
  }
}

@GenerateLenses
record Company(String name, Address address) {
  Company withAddress(Address address) {
    return new Company(name, address);
  }
}

@GenerateLenses
record Employee(String name, Company company) {
  Employee withCompany(Company company) {
    return new Employee(name, company);
  }
}

/** The Coupled Fields page's range. */
@GenerateLenses
record Range(int lo, int hi) {
  Range {
    if (lo > hi) {
      throw new IllegalArgumentException("lo (" + lo + ") must be <= hi (" + hi + ")");
    }
  }

  Range withLo(int lo) {
    return new Range(lo, hi);
  }

  Range withHi(int hi) {
    return new Range(lo, hi);
  }
}
