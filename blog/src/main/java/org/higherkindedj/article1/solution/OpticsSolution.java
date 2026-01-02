// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article1.solution;

import java.math.BigDecimal;
import java.util.List;

/**
 * Demonstrates how optics elegantly solve the nested update problem.
 *
 * <p>The same operations that required 15+ lines of manual reconstruction become single, composable
 * expressions with lenses and traversals.
 */
public class OpticsSolution {

  private static final BigDecimal RAISE_MULTIPLIER = new BigDecimal("1.10"); // 10% raise

  // Traversal over all salaries in a department (manager + staff)
  private static final Traversal<Department, BigDecimal> allSalaries =
      Traversal.fromLens(Department.Lenses.staff(), Traversal.list(), Employee.Lenses.salary());

  // Lens to manager's salary
  private static final Lens<Department, BigDecimal> managerSalary =
      Department.Lenses.manager().andThen(Employee.Lenses.salary());

  /**
   * Give everyone in the department a 10% raise.
   *
   * <p>Compare this to the 15+ lines in {@code NestedUpdateProblem}. The business logic (multiply
   * by 1.10) is front and centre, not buried in boilerplate.
   */
  public static Department giveEveryoneARaise(Department dept) {
    // Update manager
    Department withManager = managerSalary.modify(s -> s.multiply(RAISE_MULTIPLIER), dept);
    // Update all staff
    return allSalaries.modify(s -> s.multiply(RAISE_MULTIPLIER), withManager);
  }

  // Compose lenses to create a path from Department to manager's street
  private static final Lens<Employee, String> employeeStreet =
      Employee.Lenses.address().andThen(Address.Lenses.street());

  private static final Lens<Department, String> managerStreet =
      Department.Lenses.manager().andThen(employeeStreet);

  /**
   * Update the manager's street in one line.
   *
   * <p>Compare this to the 20+ lines required manually.
   */
  public static Department updateManagerStreet(Department dept, String newStreet) {
    return managerStreet.set(newStreet, dept);
  }

  /** Creates sample data for demonstration. */
  public static Department sampleDepartment() {
    Address aliceAddr = new Address("10 Oak Lane", "Cambridge", "CB1 2AB");
    Address bobAddr = new Address("20 Elm Street", "Cambridge", "CB2 3CD");
    Address charlieAddr = new Address("30 Pine Road", "Oxford", "OX1 4EF");

    Employee alice = new Employee("E001", "Alice Chen", new BigDecimal("95000.00"), aliceAddr);
    Employee bob = new Employee("E002", "Bob Smith", new BigDecimal("75000.00"), bobAddr);
    Employee charlie =
        new Employee("E003", "Charlie Brown", new BigDecimal("72000.00"), charlieAddr);

    return new Department("Engineering", alice, List.of(bob, charlie));
  }

  /** Creates sample company data for demonstration. */
  public static Company sampleCompany() {
    Address hqAddress = new Address("1 Innovation Way", "London", "EC1A 1BB");

    Department engineering = sampleDepartment();

    Address daveAddr = new Address("40 Maple Ave", "Bristol", "BS1 5GH");
    Address eveAddr = new Address("50 Cedar Close", "Bristol", "BS2 6IJ");

    Employee dave = new Employee("E004", "Dave Wilson", new BigDecimal("85000.00"), daveAddr);
    Employee eve = new Employee("E005", "Eve Taylor", new BigDecimal("68000.00"), eveAddr);

    Department sales = new Department("Sales", dave, List.of(eve));

    return new Company("TechCorp", hqAddress, List.of(engineering, sales));
  }

  /** Main entry point for the demonstration. */
  public static void main(String[] args) {
    Department engineering = sampleDepartment();

    System.out.println("=== The Optics Solution ===\n");
    System.out.println("Task: Give everyone in the department a 10% raise\n");

    System.out.println("Original salaries:");
    printSalaries(engineering);

    // One composed operation to update all salaries
    Department updated = giveEveryoneARaise(engineering);

    System.out.println("\nAfter 10% raise (using optics!):");
    printSalaries(updated);

    System.out.println("\n--- The Comparison ---");
    System.out.println("Manual approach: ~15 lines, business logic buried in boilerplate");
    System.out.println("Optics approach: 2 lines, clear intent, composable, type-safe");

    System.out.println("\n=== Bonus: Update manager's street ===");
    System.out.println("Original: " + engineering.manager().address().street());
    Department streetUpdated = updateManagerStreet(engineering, "100 New Street");
    System.out.println("After:    " + streetUpdated.manager().address().street());
    System.out.println("(One line of code!)");
  }

  private static void printSalaries(Department dept) {
    System.out.printf("  Manager - %s: £%,.2f%n", dept.manager().name(), dept.manager().salary());
    for (Employee emp : dept.staff()) {
      System.out.printf("  Staff   - %s: £%,.2f%n", emp.name(), emp.salary());
    }
  }
}
