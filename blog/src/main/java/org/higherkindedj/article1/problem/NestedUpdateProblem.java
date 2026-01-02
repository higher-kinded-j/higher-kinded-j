// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article1.problem;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Demonstrates the "nested update problem" with Java records.
 *
 * <p>Pattern matching in Java 21+ excels at reading nested data, but provides no help for writing.
 * This class shows the painful ceremony required to update deeply nested immutable structures.
 */
public class NestedUpdateProblem {

  private static final BigDecimal RAISE_MULTIPLIER = new BigDecimal("1.10"); // 10% raise

  /**
   * The task: Give all employees in a department a 10% raise.
   *
   * <p>This seemingly simple operation requires reconstructing every record in the path. Notice how
   * the actual business logic (multiplying salary by 1.10) is buried in layers of boilerplate.
   */
  public static Department giveEveryoneARaise(Department dept) {
    // Update manager's salary
    Employee manager = dept.manager();
    BigDecimal newManagerSalary = manager.salary().multiply(RAISE_MULTIPLIER);
    Employee newManager =
        new Employee(manager.id(), manager.name(), newManagerSalary, manager.address());

    // Update all staff salaries
    List<Employee> updatedStaff = new ArrayList<>();
    for (Employee emp : dept.staff()) {
      BigDecimal newSalary = emp.salary().multiply(RAISE_MULTIPLIER);
      Employee newEmp = new Employee(emp.id(), emp.name(), newSalary, emp.address());
      updatedStaff.add(newEmp);
    }

    // Rebuild department with updated employees
    return new Department(dept.name(), newManager, List.copyOf(updatedStaff));
  }

  /**
   * The task: Update the manager's street address.
   *
   * <p>This seemingly simple operation requires reconstructing every record in the path from root
   * to leaf.
   */
  public static Company updateManagerStreet(Company company, String deptName, String newStreet) {
    // Find the department, update it, rebuild the list, rebuild the company
    List<Department> updatedDepts = new ArrayList<>();

    for (Department dept : company.departments()) {
      if (dept.name().equals(deptName)) {
        // Found the department - now rebuild from the inside out
        Employee manager = dept.manager();
        Address oldAddress = manager.address();

        // Rebuild address with new street
        Address newAddress = new Address(newStreet, oldAddress.city(), oldAddress.postcode());

        // Rebuild employee with new address
        Employee newManager =
            new Employee(manager.id(), manager.name(), manager.salary(), newAddress);

        // Rebuild department with new manager
        Department newDept = new Department(dept.name(), newManager, dept.staff());

        updatedDepts.add(newDept);
      } else {
        updatedDepts.add(dept);
      }
    }

    // Rebuild company with updated departments
    return new Company(company.name(), company.headquarters(), List.copyOf(updatedDepts));
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

  public static void main(String[] args) {
    Department engineering = sampleDepartment();

    System.out.println("=== The Nested Update Problem ===\n");
    System.out.println("Task: Give everyone in the department a 10% raise\n");

    System.out.println("Original salaries:");
    printSalaries(engineering);

    // The verbose approach
    Department updated = giveEveryoneARaise(engineering);

    System.out.println("\nAfter 10% raise:");
    printSalaries(updated);

    System.out.println("\n--- Lines of code for a simple salary update: ~15 ---");
    System.out.println("--- Business logic buried in boilerplate ---");
    System.out.println("--- Pattern matching helps here: not at all ---");
  }

  private static void printSalaries(Department dept) {
    System.out.printf("  Manager - %s: £%,.2f%n", dept.manager().name(), dept.manager().salary());
    for (Employee emp : dept.staff()) {
      System.out.printf("  Staff   - %s: £%,.2f%n", emp.name(), emp.salary());
    }
  }
}
