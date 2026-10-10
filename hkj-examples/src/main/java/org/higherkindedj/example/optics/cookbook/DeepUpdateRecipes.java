// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.cookbook;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.higherkindedj.example.book.optics.cast.Address;
import org.higherkindedj.example.book.optics.cast.AddressLenses;
import org.higherkindedj.example.book.optics.cast.Consignment;
import org.higherkindedj.example.book.optics.cast.ConsignmentLenses;
import org.higherkindedj.example.book.optics.cast.ConsignmentState;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.CustomerLenses;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.EmailAddressLenses;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderLenses;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Update;
import org.higherkindedj.optics.Lens;

/**
 * Cookbook recipes for deep immutable updates on nested structures.
 *
 * <p>Problem: Updating deeply nested fields in immutable records requires verbose reconstruction.
 *
 * <p>Solution: Compose lenses to create a path, then use {@code set} or {@code modify}.
 *
 * <p>The records are the chapter's order service, imported from its cast package with the lenses
 * generated for them.
 */
public class DeepUpdateRecipes {

  private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  // Ada's order of a lamp, with the customer's email as given
  private static Order order(String email) {
    return new Order(
        ORDER_ID,
        new Customer("Ada", new EmailAddress(email)),
        List.of(new LineItem("LAMP", 1, new BigDecimal("40.00"))),
        Instant.parse("2026-10-01T09:00:00Z"),
        Currency.getInstance("GBP"),
        OrderStatus.NEW);
  }

  public static void main(String[] args) {
    System.out.println("=== Deep Update Recipes ===\n");

    recipeUpdateDeeplyNestedField();
    recipeModifyWithFunction();
    recipeComposeReusablePaths();
  }

  /**
   * Recipe: Update a deeply nested field.
   *
   * <p>Pattern: {@code path.set(newValue, source)}
   */
  private static void recipeUpdateDeeplyNestedField() {
    System.out.println("--- Recipe: Update Deeply Nested Field ---");

    // The verbose way (without optics):
    Order order = order("ada@example.com");

    // Without optics (error-prone, verbose):
    Order verboseUpdate =
        new Order(
            order.id(),
            new Customer(order.customer().name(), new EmailAddress("ada@example.org")),
            order.lines(),
            order.placedAt(),
            order.currency(),
            order.status());

    System.out.println("Without optics: " + verboseUpdate.customer());

    // With optics (clear, composable):
    Lens<Order, Customer> customerLens = OrderLenses.customer();
    Lens<Customer, EmailAddress> emailLens = CustomerLenses.email();
    Lens<EmailAddress, String> valueLens = EmailAddressLenses.value();

    // Compose into a single path
    Lens<Order, String> orderEmail = customerLens.andThen(emailLens).andThen(valueLens);

    Order cleanUpdate = orderEmail.set("ada@example.org", order);
    System.out.println("With optics: " + cleanUpdate.customer());
    System.out.println();
  }

  /**
   * Recipe: Modify a nested field with a function.
   *
   * <p>Pattern: {@code path.modify(function, source)}
   */
  private static void recipeModifyWithFunction() {
    System.out.println("--- Recipe: Modify with Function ---");

    Order order = order("Ada@Example.COM");

    Lens<Order, String> orderEmail =
        OrderLenses.customer().andThen(CustomerLenses.email()).andThen(EmailAddressLenses.value());

    // Lower-case the email address
    Order updated = orderEmail.modify(String::toLowerCase, order);

    System.out.println("Original: " + order.customer());
    System.out.println("Email lower-cased: " + updated.customer());
    System.out.println();
  }

  /**
   * Recipe: Create reusable composed paths.
   *
   * <p>Pattern: Store composed lenses as constants for reuse.
   */
  private static void recipeComposeReusablePaths() {
    System.out.println("--- Recipe: Reusable Composed Paths ---");

    // Define paths once
    Lens<Consignment, Address> toLens = ConsignmentLenses.to();
    Lens<Address, String> cityLens = AddressLenses.city();
    Lens<Address, String> postcodeLens = AddressLenses.postcode();

    // Reusable paths
    Lens<Consignment, String> consignmentCity = toLens.andThen(cityLens);
    Lens<Consignment, String> consignmentPostcode = toLens.andThen(postcodeLens);

    Consignment consignment =
        new Consignment(
            ORDER_ID,
            new Address("1 Main St", "Manchester", "M1 1AA"),
            new ConsignmentState.Pending());

    // Reuse the paths AND the composed transformation: fold both writes into one update.
    // (Edits.combine(Edit.set(path, v), ...) sugars exactly this fold - see
    // org.higherkindedj.optics.edit.)
    Update<Consignment> redirect =
        Monoids.<Consignment>update()
            .combineAll(
                List.of(
                    c -> consignmentCity.set("Edinburgh", c),
                    c -> consignmentPostcode.set("EH1 1AA", c)));

    Consignment redirected = redirect.apply(consignment);

    System.out.println("Original: " + consignment.to());
    System.out.println("Redirected: " + redirected.to());
    System.out.println();
  }
}
