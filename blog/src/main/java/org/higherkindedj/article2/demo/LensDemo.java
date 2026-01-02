// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article2.demo;

import java.math.BigDecimal;
import org.higherkindedj.article2.domain.Address;
import org.higherkindedj.article2.domain.AddressLenses;
import org.higherkindedj.article2.domain.Employee;
import org.higherkindedj.article2.domain.EmployeeLenses;
import org.higherkindedj.optics.Lens;

/**
 * Demonstrates lens operations from Article 2.
 *
 * <p>This demo shows:
 *
 * <ul>
 *   <li>Basic lens operations: get, set, modify
 *   <li>Lens composition with andThen()
 *   <li>The lens laws in action
 * </ul>
 *
 * @see <a href="../../docs/article-2-optics-fundamentals.md">Article 2: Optics Fundamentals</a>
 */
public final class LensDemo {

  public static void main(String[] args) {
    System.out.println("=== Lens Demo (Article 2) ===\n");

    basicLensOperations();
    lensComposition();
    lensLaws();
  }

  private static void basicLensOperations() {
    System.out.println("--- Basic Lens Operations ---\n");

    Address address = new Address("10 Downing Street", "London", "SW1A 2AA");

    // Generated lens from @GenerateLenses annotation
    Lens<Address, String> streetLens = AddressLenses.street();

    // Get: extract the focused value
    String street = streetLens.get(address);
    System.out.println("GET: " + street);

    // Set: return a new structure with the focused value replaced
    Address updated = streetLens.set("100 New Street", address);
    System.out.println("SET: " + updated);

    // Modify: apply a function to the focused value
    Address uppercased = streetLens.modify(String::toUpperCase, address);
    System.out.println("MODIFY: " + uppercased);

    // Original is unchanged (immutability!)
    System.out.println("ORIGINAL: " + address);
    System.out.println();
  }

  private static void lensComposition() {
    System.out.println("--- Lens Composition ---\n");

    Employee employee =
        new Employee(
            "E001",
            "Alice",
            new Address("221B Baker Street", "London", "NW1 6XE"),
            new BigDecimal("75000"));

    // Compose: Employee -> Address -> String (street)
    Lens<Employee, String> employeeStreet =
        EmployeeLenses.address().andThen(AddressLenses.street());

    // Now we can get/set/modify the street directly on an Employee
    String street = employeeStreet.get(employee);
    System.out.println("GET nested street: " + street);

    Employee updated = employeeStreet.set("200 Oak Avenue", employee);
    System.out.println("SET nested street: " + updated.address().street());

    Employee transformed = employeeStreet.modify(s -> s + " (verified)", employee);
    System.out.println("MODIFY nested street: " + transformed.address().street());

    // Deeper composition: Employee -> Address -> City
    Lens<Employee, String> employeeCity = EmployeeLenses.address().andThen(AddressLenses.city());

    Employee relocated = employeeCity.set("Manchester", employee);
    System.out.println("Relocated to: " + relocated.address().city());
    System.out.println();
  }

  private static void lensLaws() {
    System.out.println("--- Lens Laws ---\n");

    Address address = new Address("Original Street", "London", "SW1A 1AA");
    Lens<Address, String> streetLens = AddressLenses.street();

    // Law 1: Get-Set - If you get a value and then set it back, the structure is unchanged
    Address afterGetSet = streetLens.set(streetLens.get(address), address);
    boolean getSetLaw = address.equals(afterGetSet);
    System.out.println("Get-Set Law: lens.set(lens.get(s), s) == s -> " + getSetLaw);

    // Law 2: Set-Get - If you set a value, getting it returns what you set
    String newStreet = "New Street";
    String afterSetGet = streetLens.get(streetLens.set(newStreet, address));
    boolean setGetLaw = newStreet.equals(afterSetGet);
    System.out.println("Set-Get Law: lens.get(lens.set(a, s)) == a -> " + setGetLaw);

    // Law 3: Set-Set - Setting twice is the same as setting once with the final value
    String street1 = "First Street";
    String street2 = "Second Street";
    Address setTwice = streetLens.set(street2, streetLens.set(street1, address));
    Address setOnce = streetLens.set(street2, address);
    boolean setSetLaw = setTwice.equals(setOnce);
    System.out.println(
        "Set-Set Law: lens.set(a2, lens.set(a1, s)) == lens.set(a2, s) -> " + setSetLaw);
    System.out.println();
  }
}
