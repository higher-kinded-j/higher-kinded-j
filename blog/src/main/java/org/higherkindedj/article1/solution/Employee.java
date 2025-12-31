// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article1.solution;

import java.math.BigDecimal;

/**
 * Employee record with lenses.
 *
 * <p>Lenses allow composition: {@code Employee.Lenses.address()} can be composed with {@code
 * Address.Lenses.street()} to create a lens focusing directly on an employee's street.
 */
public record Employee(String id, String name, BigDecimal salary, Address address) {

  /** Lenses for Employee fields. */
  public static final class Lenses {
    private Lenses() {}

    public static Lens<Employee, String> id() {
      return Lens.of(
          Employee::id,
          (newId, emp) -> new Employee(newId, emp.name(), emp.salary(), emp.address()));
    }

    public static Lens<Employee, String> name() {
      return Lens.of(
          Employee::name,
          (newName, emp) -> new Employee(emp.id(), newName, emp.salary(), emp.address()));
    }

    public static Lens<Employee, BigDecimal> salary() {
      return Lens.of(
          Employee::salary,
          (newSalary, emp) -> new Employee(emp.id(), emp.name(), newSalary, emp.address()));
    }

    public static Lens<Employee, Address> address() {
      return Lens.of(
          Employee::address,
          (newAddr, emp) -> new Employee(emp.id(), emp.name(), emp.salary(), newAddr));
    }
  }
}
