// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article1.solution;

import java.util.List;
import java.util.function.Function;

/**
 * Demonstrates how optics elegantly solve the nested update problem.
 *
 * <p>The same operations that required 20+ lines of manual reconstruction become single, composable
 * expressions with lenses.
 */
public class OpticsSolution {

  // Compose lenses to create a path from Department to manager's street
  // Department -> Employee (manager) -> Address -> street
  private static final Lens<Employee, String> employeeStreet =
      Employee.Lenses.address().andThen(Address.Lenses.street());

  private static final Lens<Department, String> managerStreet =
      Department.Lenses.manager().andThen(employeeStreet);

  /**
   * Update the manager's street in one line.
   *
   * <p>Compare this to the 20+ lines in {@code NestedUpdateProblem}.
   */
  public static Department updateManagerStreet(Department dept, String newStreet) {
    return managerStreet.set(newStreet, dept);
  }

  /**
   * Transform the manager's street using a function.
   *
   * <p>Even more powerful: modify rather than replace.
   */
  public static Department transformManagerStreet(
      Department dept, Function<String, String> transform) {
    return managerStreet.modify(transform, dept);
  }

  /**
   * Get a traversal over all staff streets in a department.
   *
   * <p>Traversals let us focus on multiple values at once.
   */
  public static Traversal<Department, String> allStaffStreets() {
    return Traversal.fromLens(Department.Lenses.staff(), Traversal.list(), employeeStreet);
  }

  /**
   * Update ALL employee streets (manager + staff) in a department.
   *
   * <p>This operation required deeply nested loops in the manual approach. With optics, we simply
   * compose traversals.
   */
  public static Department updateAllStreets(Department dept, String newStreet) {
    // Update manager
    Department withManager = managerStreet.set(newStreet, dept);
    // Update all staff
    return allStaffStreets().modify(_ -> newStreet, withManager);
  }

  /** Creates sample data for demonstration. */
  public static Company sampleCompany() {
    Address hqAddress = new Address("1 Innovation Way", "London", "EC1A 1BB");

    Address aliceAddr = new Address("10 Oak Lane", "Cambridge", "CB1 2AB");
    Address bobAddr = new Address("20 Elm Street", "Cambridge", "CB2 3CD");
    Address charlieAddr = new Address("30 Pine Road", "Oxford", "OX1 4EF");

    Employee alice = new Employee("E001", "Alice Chen", aliceAddr);
    Employee bob = new Employee("E002", "Bob Smith", bobAddr);
    Employee charlie = new Employee("E003", "Charlie Brown", charlieAddr);

    Department engineering = new Department("Engineering", alice, List.of(bob, charlie));

    Address daveAddr = new Address("40 Maple Ave", "Bristol", "BS1 5GH");
    Address eveAddr = new Address("50 Cedar Close", "Bristol", "BS2 6IJ");

    Employee dave = new Employee("E004", "Dave Wilson", daveAddr);
    Employee eve = new Employee("E005", "Eve Taylor", eveAddr);

    Department sales = new Department("Sales", dave, List.of(eve));

    return new Company("TechCorp", hqAddress, List.of(engineering, sales));
  }

  /** Main entry point for the demonstration. */
  public static void main(String[] args) {
    Company company = sampleCompany();
    Department engineering = company.departments().getFirst();

    System.out.println("=== The Optics Solution ===\n");
    System.out.println("Original manager address:");
    printManagerAddress(engineering);

    // One line to update deeply nested data
    Department updated = updateManagerStreet(engineering, "100 New Street");

    System.out.println("\nAfter update (one line of code!):");
    printManagerAddress(updated);

    System.out.println("\n--- The Comparison ---");
    System.out.println("Manual approach: ~20 lines, error-prone, repetitive");
    System.out.println("Optics approach: 1 line, composable, type-safe");

    System.out.println("\n=== Bonus: Update ALL streets ===");
    System.out.println("Original:");
    printAllAddresses(engineering);

    Department allUpdated = updateAllStreets(engineering, "999 Unified Boulevard");
    System.out.println("\nAfter bulk update:");
    printAllAddresses(allUpdated);
  }

  private static void printManagerAddress(Department dept) {
    Address addr = dept.manager().address();
    System.out.printf(
        "  %s: %s, %s, %s%n", dept.manager().name(), addr.street(), addr.city(), addr.postcode());
  }

  private static void printAllAddresses(Department dept) {
    System.out.printf(
        "  Manager - %s: %s%n", dept.manager().name(), dept.manager().address().street());
    for (Employee emp : dept.staff()) {
      System.out.printf("  Staff   - %s: %s%n", emp.name(), emp.address().street());
    }
  }
}
