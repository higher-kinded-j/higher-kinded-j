// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article2.demo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.higherkindedj.article2.domain.Address;
import org.higherkindedj.article2.domain.Department;
import org.higherkindedj.article2.domain.Employee;
import org.higherkindedj.article2.optics.Lens;
import org.higherkindedj.article2.optics.Traversal;

/**
 * Demonstrates traversal operations from Article 2.
 *
 * <p>This demo shows:
 *
 * <ul>
 *   <li>Basic list traversal
 *   <li>Composing traversals for nested collections
 *   <li>Filtered traversals with predicates
 *   <li>Aggregation with foldMap
 * </ul>
 */
public final class TraversalDemo {

  public static void main(String[] args) {
    System.out.println("=== Traversal Demo (Article 2) ===\n");

    basicListTraversal();
    nestedCollectionTraversal();
    filteredTraversal();
    aggregationWithFold();
  }

  private static void basicListTraversal() {
    System.out.println("--- Basic List Traversal ---\n");

    Traversal<List<String>, String> listTraversal = Traversal.list();

    List<String> names = List.of("alice", "bob", "charlie");
    System.out.println("Original: " + names);

    // Modify all elements
    List<String> uppercased = listTraversal.modify(String::toUpperCase, names);
    System.out.println("Uppercased: " + uppercased);

    // Set all to same value
    List<String> allBob = listTraversal.set("bob", names);
    System.out.println("All bob: " + allBob);

    // Get all elements
    List<String> all = listTraversal.getAll(names);
    System.out.println("Get all: " + all);

    // Count elements
    int count = listTraversal.count(names);
    System.out.println("Count: " + count);
    System.out.println();
  }

  private static void nestedCollectionTraversal() {
    System.out.println("--- Nested Collection Traversal ---\n");

    // Create a department with staff
    Department engineering =
        new Department(
            "Engineering",
            new Employee(
                "M001",
                "Alice Manager",
                new Address("1 Boss Lane", "London", "E1 1AA"),
                new BigDecimal("120000")),
            List.of(
                new Employee(
                    "E001",
                    "Bob",
                    new Address("10 Tech Road", "London", "E1 2BB"),
                    new BigDecimal("75000")),
                new Employee(
                    "E002",
                    "Carol",
                    new Address("20 Code Street", "Manchester", "M1 3CC"),
                    new BigDecimal("80000")),
                new Employee(
                    "E003",
                    "David",
                    new Address("30 Dev Avenue", "London", "E1 4DD"),
                    new BigDecimal("70000"))));

    System.out.println("Department: " + engineering.name());
    System.out.println("Staff count: " + engineering.staff().size());

    // Compose: Department → staff list → each employee → address → city
    Lens<Department, List<Employee>> staffLens = Department.Lenses.staff();
    Traversal<List<Employee>, Employee> eachEmployee = Traversal.list();
    Lens<Employee, Address> addressLens = Employee.Lenses.address();
    Lens<Address, String> cityLens = Address.Lenses.city();

    Traversal<Department, String> allStaffCities =
        staffLens.andThen(eachEmployee).andThen(addressLens).andThen(cityLens);

    // Get all staff cities
    List<String> cities = allStaffCities.getAll(engineering);
    System.out.println("Staff cities: " + cities);

    // Relocate all staff to Edinburgh
    Department relocated = allStaffCities.set("Edinburgh", engineering);
    System.out.println("After relocation: " + allStaffCities.getAll(relocated));

    // Compose for streets
    Traversal<Department, String> allStaffStreets =
        staffLens.andThen(eachEmployee).andThen(addressLens).andThen(Address.Lenses.street());

    // Add suffix to all streets
    Department updated = allStaffStreets.modify(s -> s + " (verified)", engineering);
    System.out.println("Updated streets: " + allStaffStreets.getAll(updated));
    System.out.println();
  }

  private static void filteredTraversal() {
    System.out.println("--- Filtered Traversal ---\n");

    List<Employee> employees =
        List.of(
            new Employee(
                "E001",
                "Alice",
                new Address("1 Grey Street", "Newcastle", "NE1 1AA"),
                new BigDecimal("75000")),
            new Employee(
                "E002",
                "Bob",
                new Address("2 Manchester Ave", "Manchester", "M1 2BB"),
                new BigDecimal("70000")),
            new Employee(
                "E003",
                "Carol",
                new Address("3 Quayside", "Newcastle", "NE1 3CC"),
                new BigDecimal("80000")),
            new Employee(
                "E004",
                "David",
                new Address("4 Leeds Street", "Leeds", "LS1 4DD"),
                new BigDecimal("65000")));

    System.out.println("All employees: " + employees.stream().map(Employee::name).toList());

    // Only employees in Newcastle
    Traversal<List<Employee>, Employee> newcastleEmployees =
        Traversal.<Employee>list().filtered(e -> e.address().city().equals("Newcastle"));

    List<Employee> inNewcastle = newcastleEmployees.getAll(employees);
    System.out.println("Newcastle employees: " + inNewcastle.stream().map(Employee::name).toList());

    // Give Newcastle employees a 10% raise
    Traversal<List<Employee>, BigDecimal> newcastleSalaries =
        newcastleEmployees.andThen(Employee.Lenses.salary());

    List<Employee> afterRaise =
        newcastleSalaries.modify(sal -> sal.multiply(new BigDecimal("1.10")), employees);

    System.out.println("\nAfter 10% raise for Newcastle employees:");
    afterRaise.forEach(e -> System.out.println("  " + e.name() + ": £" + e.salary()));
    System.out.println();
  }

  private static void aggregationWithFold() {
    System.out.println("--- Aggregation with Fold ---\n");

    List<Employee> employees =
        List.of(
            new Employee(
                "E001", "Alice", new Address("1 Road", "London", "E1"), new BigDecimal("75000")),
            new Employee(
                "E002", "Bob", new Address("2 Road", "Manchester", "M1"), new BigDecimal("70000")),
            new Employee(
                "E003", "Carol", new Address("3 Road", "London", "E1"), new BigDecimal("80000")));

    Traversal<List<Employee>, Employee> allEmployees = Traversal.list();

    // Sum all salaries
    Traversal<List<Employee>, BigDecimal> allSalaries =
        allEmployees.andThen(Employee.Lenses.salary());

    BigDecimal totalSalary =
        allSalaries.foldMap(BigDecimal::add, BigDecimal.ZERO, s -> s, employees);
    System.out.println("Total salary: £" + totalSalary);

    // Count employees
    int count = allSalaries.foldMap(Integer::sum, 0, _ -> 1, employees);
    System.out.println("Employee count: " + count);

    // Collect unique cities
    Traversal<List<Employee>, String> allCities =
        allEmployees.andThen(Employee.Lenses.address()).andThen(Address.Lenses.city());

    Set<String> uniqueCities = allCities.getAll(employees).stream().collect(Collectors.toSet());
    System.out.println("Unique cities: " + uniqueCities);

    // Average salary
    BigDecimal avgSalary =
        totalSalary.divide(BigDecimal.valueOf(count), 2, java.math.RoundingMode.HALF_UP);
    System.out.println("Average salary: £" + avgSalary);
    System.out.println();
  }
}
