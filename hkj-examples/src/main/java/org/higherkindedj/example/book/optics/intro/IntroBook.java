// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.intro;

import org.higherkindedj.optics.annotations.GenerateFocus;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/ch_intro.html">Optics</a> chapter
 * introduction. The page {@code {{#include}}}s the anchored regions, and {@code IntroBookTest}
 * holds the claims the page makes about this code.
 */
public final class IntroBook {

  private IntroBook() {}

  static Employee moveByWithers(Employee employee) {
    // ANCHOR: cascade
    // Lombok's @With: a wither rebuilds only its own record, so each layer is threaded by hand
    Employee moved =
        employee.withCompany(
            employee.company().withAddress(employee.company().address().withStreet("456 Main St")));
    // ANCHOR_END: cascade
    return moved;
  }

  static Employee moveByFocus(Employee employee) {
    // ANCHOR: focus
    // The Focus DSL: one generated path, and every layer it passes through is rebuilt for you
    Employee moved = EmployeeFocus.company().address().street().set("456 Main St", employee);
    // ANCHOR_END: focus
    return moved;
  }
}

// Each record carries the withers this example calls, as Lombok's @With would generate them.

@GenerateFocus(generateNavigators = true)
record Address(String street, String city) {
  Address withStreet(String street) {
    return new Address(street, city);
  }
}

@GenerateFocus(generateNavigators = true)
record Company(String name, Address address) {
  Company withAddress(Address address) {
    return new Company(name, address);
  }
}

@GenerateFocus(generateNavigators = true)
record Employee(String name, Company company) {
  Employee withCompany(Company company) {
    return new Employee(name, company);
  }
}
