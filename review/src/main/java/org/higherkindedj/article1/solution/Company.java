// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article1.solution;

import java.util.List;

/** Company record with lenses. */
public record Company(String name, Address headquarters, List<Department> departments) {

  /** Lenses for Company fields. */
  public static final class Lenses {
    private Lenses() {}

    public static Lens<Company, String> name() {
      return Lens.of(
          Company::name,
          (newName, co) -> new Company(newName, co.headquarters(), co.departments()));
    }

    public static Lens<Company, Address> headquarters() {
      return Lens.of(
          Company::headquarters, (newHq, co) -> new Company(co.name(), newHq, co.departments()));
    }

    public static Lens<Company, List<Department>> departments() {
      return Lens.of(
          Company::departments,
          (newDepts, co) -> new Company(co.name(), co.headquarters(), newDepts));
    }
  }
}
