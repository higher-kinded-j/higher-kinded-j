// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article2.demo;

import java.math.BigDecimal;
import java.util.List;
import org.higherkindedj.article2.domain.Customer;
import org.higherkindedj.article2.domain.CustomerLenses;
import org.higherkindedj.article2.domain.LineItem;
import org.higherkindedj.article2.domain.LineItemLenses;
import org.higherkindedj.article2.domain.Order;
import org.higherkindedj.article2.domain.OrderLenses;
import org.higherkindedj.article2.domain.OrderStatus;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;

/**
 * Demonstrates optics composition patterns from Article 2.
 *
 * <p>This demo shows the e-commerce example: applying a discount to all items across all orders for
 * a customer.
 *
 * @see <a href="../../docs/article-2-optics-fundamentals.md">Article 2: Optics Fundamentals</a>
 */
public final class CompositionDemo {

  public static void main(String[] args) {
    System.out.println("=== Composition Demo (Article 2) ===\n");

    deepPathComposition();
    manualVsOpticsComparison();
  }

  private static void deepPathComposition() {
    System.out.println("--- Deep Path Composition ---\n");

    Customer customer = createSampleCustomer();

    System.out.println("Customer: " + customer.name());
    System.out.println("Total order value: £" + customer.totalOrderValue());
    System.out.println();

    // Define the path: Customer -> orders -> each order -> items -> each item -> price
    Traversal<Customer, BigDecimal> allItemPrices =
        CustomerLenses.orders()
            .asTraversal()
            .andThen(Traversals.forList())
            .andThen(OrderLenses.items().asTraversal())
            .andThen(Traversals.forList())
            .andThen(LineItemLenses.price().asTraversal());

    // Get all prices
    List<BigDecimal> prices = Traversals.getAll(allItemPrices, customer);
    System.out.println("All item prices: " + prices);

    // Apply 10% discount to all items
    Customer discounted =
        Traversals.modify(allItemPrices, price -> price.multiply(new BigDecimal("0.90")), customer);

    System.out.println("\nAfter 10% discount:");
    System.out.println("New prices: " + Traversals.getAll(allItemPrices, discounted));
    System.out.println("New total: £" + discounted.totalOrderValue());
    System.out.println();
  }

  private static void manualVsOpticsComparison() {
    System.out.println("--- Manual vs Optics Comparison ---\n");

    Customer customer = createSampleCustomer();

    System.out.println("Task: Apply 10% discount to all items\n");

    // Manual approach (what you'd have to write without optics)
    System.out.println("Manual approach (~20 lines):");
    System.out.println(
        """
            List<Order> updatedOrders = new ArrayList<>();
            for (Order order : customer.orders()) {
                List<LineItem> updatedItems = new ArrayList<>();
                for (LineItem item : order.items()) {
                    BigDecimal newPrice = item.price().multiply(new BigDecimal("0.90"));
                    updatedItems.add(new LineItem(item.productId(), item.quantity(), newPrice));
                }
                updatedOrders.add(new Order(order.orderId(), List.copyOf(updatedItems), order.status()));
            }
            Customer discounted = new Customer(customer.id(), customer.name(), List.copyOf(updatedOrders));
        """);

    // Optics approach
    System.out.println("Optics approach (3 lines):");
    System.out.println(
        """
            Traversal<Customer, BigDecimal> allPrices = CustomerLenses.orders().asTraversal()
                .andThen(Traversals.forList()).andThen(OrderLenses.items().asTraversal())
                .andThen(Traversals.forList()).andThen(LineItemLenses.price().asTraversal());
            Customer discounted = Traversals.modify(allPrices, p -> p.multiply(new BigDecimal("0.90")), customer);
        """);

    // Demonstrate it works
    Traversal<Customer, BigDecimal> allItemPrices =
        CustomerLenses.orders()
            .asTraversal()
            .andThen(Traversals.forList())
            .andThen(OrderLenses.items().asTraversal())
            .andThen(Traversals.forList())
            .andThen(LineItemLenses.price().asTraversal());

    Customer discounted =
        Traversals.modify(allItemPrices, price -> price.multiply(new BigDecimal("0.90")), customer);

    System.out.println("Result verification:");
    System.out.println("  Original total: £" + customer.totalOrderValue());
    System.out.println("  Discounted total: £" + discounted.totalOrderValue());
    System.out.println();
  }

  private static Customer createSampleCustomer() {
    return new Customer(
        "C001",
        "Acme Corp",
        List.of(
            new Order(
                "ORD-001",
                List.of(
                    new LineItem("WIDGET-A", 2, new BigDecimal("25.00")),
                    new LineItem("WIDGET-B", 1, new BigDecimal("50.00")),
                    new LineItem("GADGET-X", 3, new BigDecimal("15.00"))),
                OrderStatus.CONFIRMED),
            new Order(
                "ORD-002",
                List.of(
                    new LineItem("GIZMO-1", 5, new BigDecimal("10.00")),
                    new LineItem("WIDGET-A", 1, new BigDecimal("25.00"))),
                OrderStatus.PENDING)));
  }
}
