// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * A runnable example demonstrating how to compose Lenses to perform deep, immutable updates on
 * nested data structures.
 */
public class LensUsageExample {

  // 1. Define a nested, immutable data model.
  // The @GenerateLenses annotation will automatically create Lens implementations
  // for each record component.
  @GenerateLenses
  public record Address(String street, String city) {}

  @GenerateLenses
  public record Company(String name, Address address) {}

  @GenerateLenses
  public record Employee(String name, Company company) {}

  public static void main(String[] args) {
    // 2. Create an initial, nested immutable object.
    var initialAddress = new Address("123 Fake St", "Anytown");
    var initialCompany = new Company("Initech Inc.", initialAddress);
    var initialEmployee = new Employee("Alice", initialCompany);

    System.out.println("Original Employee: " + initialEmployee);
    System.out.println("------------------------------------------");

    // 3. Compose lenses to create a "deep" focus.
    // We want to focus from the Employee all the way down to the street name.
    // The generated lens classes are named after the record with a "Lenses" suffix.
    Lens<Employee, Company> employeeToCompany = EmployeeLenses.company();
    Lens<Company, Address> companyToAddress = CompanyLenses.address();
    Lens<Address, String> addressToStreet = AddressLenses.street();

    // The `andThen` method chains lenses together.
    Lens<Employee, String> employeeToStreet =
        employeeToCompany.andThen(companyToAddress).andThen(addressToStreet);

    // 4. Use the composed lens to perform immutable updates.

    // --- Using `set` to replace a value ---
    // This creates a new Employee object with only the street changed.
    // The original `initialEmployee` remains untouched.
    Employee updatedEmployeeSet = employeeToStreet.set("456 Main St", initialEmployee);

    System.out.println("After `set`:       " + updatedEmployeeSet);
    System.out.println("Original is unchanged: " + initialEmployee);
    System.out.println("------------------------------------------");

    // --- Using `modify` to apply a function to the value ---
    // This is useful for updates based on the existing value.
    Employee updatedEmployeeModify = employeeToStreet.modify(String::toUpperCase, initialEmployee);

    System.out.println("After `modify`:    " + updatedEmployeeModify);
    System.out.println("Original is unchanged: " + initialEmployee);
  }
}
