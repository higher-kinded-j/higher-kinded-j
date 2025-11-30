// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article2.domain;

import java.math.BigDecimal;
import org.higherkindedj.article2.optics.Lens;

/**
 * An employee with id, name, address, and salary.
 *
 * <p>In production with higher-kinded-j, you would annotate this with {@code @GenerateLenses}.
 */
public record Employee(String id, String name, Address address, BigDecimal salary) {

  /** Convenience constructor without salary. */
  public Employee(String id, String name, Address address) {
    this(id, name, address, BigDecimal.ZERO);
  }

  /** Lens accessors for Employee fields. */
  public static final class Lenses {
    private Lenses() {}

    public static Lens<Employee, String> id() {
      return Lens.of(
          Employee::id,
          (newId, emp) -> new Employee(newId, emp.name(), emp.address(), emp.salary()));
    }

    public static Lens<Employee, String> name() {
      return Lens.of(
          Employee::name,
          (newName, emp) -> new Employee(emp.id(), newName, emp.address(), emp.salary()));
    }

    public static Lens<Employee, Address> address() {
      return Lens.of(
          Employee::address,
          (newAddress, emp) -> new Employee(emp.id(), emp.name(), newAddress, emp.salary()));
    }

    public static Lens<Employee, BigDecimal> salary() {
      return Lens.of(
          Employee::salary,
          (newSalary, emp) -> new Employee(emp.id(), emp.name(), emp.address(), newSalary));
    }
  }
}
