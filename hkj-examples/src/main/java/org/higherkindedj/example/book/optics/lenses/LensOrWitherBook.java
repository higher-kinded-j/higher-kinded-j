// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.lenses;

import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * The code shown in the book's <a
 * href="https://higher-kinded-j.github.io/optics/lenses.html#lens-or-wither">Why a lens, when you
 * have {@code @With}?</a> section. The page {@code {{#include}}}s the anchored region, and {@code
 * LensOrWitherBookTest} holds each claim the section makes about it.
 */
public final class LensOrWitherBook {

  /** The composed lens the page builds in its Step 2. */
  static final Lens<Employee, String> EMPLOYEE_TO_STREET =
      EmployeeLenses.company().andThen(CompanyLenses.address()).andThen(AddressLenses.street());

  static final Employee EMPLOYEE =
      new Employee("Alice", new Company("Initech Inc.", new Address("123 Fake St", "Anytown")));

  private LensOrWitherBook() {}

  static Employee moveByWithers(Employee employee) {
    // ANCHOR: cascade
    Employee moved =
        EmployeeLenses.withCompany(
            employee,
            CompanyLenses.withAddress(
                employee.company(),
                AddressLenses.withStreet(employee.company().address(), "456 Main St")));
    // ANCHOR_END: cascade
    return moved;
  }

  static Employee moveByLens(Employee employee) {
    return EMPLOYEE_TO_STREET.set("456 Main St", employee);
  }
}

@GenerateLenses
record Address(String street, String city) {}

@GenerateLenses
record Company(String name, Address address) {}

@GenerateLenses
record Employee(String name, Company company) {}

/** The Coupled Fields page's range, with the withers a Lombok {@code @With} user would call. */
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
