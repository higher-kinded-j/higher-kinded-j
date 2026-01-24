// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.importoptics;

import org.higherkindedj.example.optics.importoptics.external.Customer;
import org.higherkindedj.optics.Lens;

/**
 * A runnable example demonstrating how to use {@code @ImportOptics} to generate lenses for external
 * record types.
 *
 * <p>This example shows that records from external libraries (simulated here by the {@code
 * external} package) can have lenses generated via the {@code @ImportOptics} annotation in
 * package-info.java.
 */
public class ImportOpticsRecordExample {

  public static void main(String[] args) {
    System.out.println("=== ImportOptics Record Example ===\n");

    // 1. Create an external record instance
    Customer customer = new Customer("C001", "Alice Smith", "alice@example.com", 150);
    System.out.println("Original customer: " + customer);
    System.out.println();

    // 2. Use generated lenses to access fields
    // The annotation processor generates CustomerLenses with lenses for each component
    Lens<Customer, String> nameLens = CustomerLenses.name();
    Lens<Customer, String> emailLens = CustomerLenses.email();
    Lens<Customer, Integer> loyaltyPointsLens = CustomerLenses.loyaltyPoints();

    String name = nameLens.get(customer);
    String email = emailLens.get(customer);
    int points = loyaltyPointsLens.get(customer);

    System.out.println("Name via lens: " + name);
    System.out.println("Email via lens: " + email);
    System.out.println("Loyalty points via lens: " + points);
    System.out.println();

    // 3. Use lenses to create updated copies immutably
    Customer updatedName = nameLens.set("Alice Johnson", customer);
    System.out.println("After name change: " + updatedName);

    Customer updatedEmail = emailLens.set("alice.johnson@newdomain.com", customer);
    System.out.println("After email change: " + updatedEmail);
    System.out.println();

    // 4. Use modify for computed updates
    // Add 50 loyalty points
    Customer withBonusPoints = loyaltyPointsLens.modify(p -> p + 50, customer);
    System.out.println("After bonus points: " + withBonusPoints);

    // Double the loyalty points
    Customer doubledPoints = loyaltyPointsLens.modify(p -> p * 2, customer);
    System.out.println("After doubling points: " + doubledPoints);
    System.out.println();

    // 5. Use generated convenience methods
    // The processor also generates withX() helper methods
    Customer viaWithMethod = CustomerLenses.withName(customer, "Bob Williams");
    System.out.println("Via withName method: " + viaWithMethod);

    // 6. Chain multiple updates
    Customer fullyUpdated =
        CustomerLenses.withLoyaltyPoints(
            CustomerLenses.withEmail(
                CustomerLenses.withName(customer, "Carol Davis"), "carol@business.com"),
            500);
    System.out.println("Fully updated customer: " + fullyUpdated);
    System.out.println();

    // 7. Original remains unchanged (immutability)
    System.out.println("Original unchanged: " + customer);

    System.out.println("\n=== Example Complete ===");
  }
}
