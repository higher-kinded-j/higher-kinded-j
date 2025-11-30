// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article1.problem;

import java.util.ArrayList;
import java.util.List;

/**
 * Demonstrates the "nested update problem" with Java records.
 *
 * <p>Pattern matching in Java 21+ excels at reading nested data, but provides no help for writing.
 * This class shows the painful ceremony required to update deeply nested immutable structures.
 */
public class NestedUpdateProblem {

  /**
   * The task: Update the manager's street address in the Engineering department.
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
        Employee newManager = new Employee(manager.id(), manager.name(), newAddress);

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

  /**
   * Even worse: Update the street for ALL employees in a department.
   *
   * <p>The nested loops and reconstruction become deeply nested and error-prone.
   */
  public static Company updateAllEmployeeStreets(
      Company company, String deptName, String newStreet) {
    List<Department> updatedDepts = new ArrayList<>();

    for (Department dept : company.departments()) {
      if (dept.name().equals(deptName)) {
        // Update manager
        Employee manager = dept.manager();
        Address managerAddr = manager.address();
        Address newManagerAddr = new Address(newStreet, managerAddr.city(), managerAddr.postcode());
        Employee newManager = new Employee(manager.id(), manager.name(), newManagerAddr);

        // Update all staff
        List<Employee> updatedStaff = new ArrayList<>();
        for (Employee emp : dept.staff()) {
          Address empAddr = emp.address();
          Address newEmpAddr = new Address(newStreet, empAddr.city(), empAddr.postcode());
          Employee newEmp = new Employee(emp.id(), emp.name(), newEmpAddr);
          updatedStaff.add(newEmp);
        }

        Department newDept = new Department(dept.name(), newManager, List.copyOf(updatedStaff));
        updatedDepts.add(newDept);
      } else {
        updatedDepts.add(dept);
      }
    }

    return new Company(company.name(), company.headquarters(), List.copyOf(updatedDepts));
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

  public static void main(String[] args) {
    Company company = sampleCompany();

    System.out.println("=== The Nested Update Problem ===\n");
    System.out.println("Original manager address:");
    printManagerAddress(company, "Engineering");

    // Task: Change the Engineering manager's street to "100 New Street"
    Company updated = updateManagerStreet(company, "Engineering", "100 New Street");

    System.out.println("\nAfter update:");
    printManagerAddress(updated, "Engineering");

    System.out.println("\n--- Lines of code for a simple street change: ~20 ---");
    System.out.println("--- Error opportunities: numerous ---");
    System.out.println("--- Pattern matching helps here: not at all ---");
  }

  private static void printManagerAddress(Company company, String deptName) {
    company.departments().stream()
        .filter(d -> d.name().equals(deptName))
        .findFirst()
        .ifPresent(
            dept -> {
              Address addr = dept.manager().address();
              System.out.printf(
                  "  %s: %s, %s, %s%n",
                  dept.manager().name(), addr.street(), addr.city(), addr.postcode());
            });
  }
}
