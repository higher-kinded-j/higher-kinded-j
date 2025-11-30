// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.cookbook;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;

/**
 * Cookbook recipes for conditional updates using Prisms.
 *
 * <p>Problem: Updating values only when they match certain criteria or types.
 *
 * <p>Solution: Use Prisms to focus on specific variants or matching values.
 */
public class ConditionalUpdateRecipes {

  // --- Domain Model ---
  @GeneratePrisms
  public sealed interface OrderStatus permits Pending, Processing, Shipped, Cancelled {}

  @GenerateLenses
  public record Pending(String orderId) implements OrderStatus {}

  @GenerateLenses
  public record Processing(String orderId, String assignee) implements OrderStatus {}

  @GenerateLenses
  public record Shipped(String orderId, String trackingNumber) implements OrderStatus {}

  @GenerateLenses
  public record Cancelled(String orderId, String reason) implements OrderStatus {}

  public static void main(String[] args) {
    System.out.println("=== Conditional Update Recipes ===\n");

    recipeUpdateSpecificVariant();
    recipeFilterByType();
    recipeTypeChecking();
    recipeExclusionFiltering();
  }

  /**
   * Recipe: Update only a specific variant of a sum type.
   *
   * <p>Pattern: {@code prism.modify(function, source)} - no-op if doesn't match
   */
  private static void recipeUpdateSpecificVariant() {
    System.out.println("--- Recipe: Update Specific Variant ---");

    // Prism for Processing orders
    Prism<OrderStatus, Processing> processingPrism =
        Prism.of(
            status -> status instanceof Processing p ? Optional.of(p) : Optional.empty(), p -> p);

    OrderStatus pending = new Pending("ORD-001");
    OrderStatus processing = new Processing("ORD-002", "alice");
    OrderStatus shipped = new Shipped("ORD-003", "TRACK-123");

    // Update only Processing orders - reassign to new person
    OrderStatus updatedPending =
        processingPrism.modify(p -> new Processing(p.orderId(), "bob"), pending);
    OrderStatus updatedProcessing =
        processingPrism.modify(p -> new Processing(p.orderId(), "bob"), processing);
    OrderStatus updatedShipped =
        processingPrism.modify(p -> new Processing(p.orderId(), "bob"), shipped);

    System.out.println("Pending (unchanged): " + updatedPending);
    System.out.println("Processing (reassigned): " + updatedProcessing);
    System.out.println("Shipped (unchanged): " + updatedShipped);
    System.out.println();
  }

  /**
   * Recipe: Filter a collection by variant type.
   *
   * <p>Pattern: {@code stream.filter(prism::matches)}
   */
  private static void recipeFilterByType() {
    System.out.println("--- Recipe: Filter by Variant Type ---");

    Prism<OrderStatus, Pending> pendingPrism =
        Prism.of(status -> status instanceof Pending p ? Optional.of(p) : Optional.empty(), p -> p);

    List<OrderStatus> orders =
        List.of(
            new Pending("ORD-001"),
            new Processing("ORD-002", "alice"),
            new Pending("ORD-003"),
            new Shipped("ORD-004", "TRACK-456"),
            new Cancelled("ORD-005", "Out of stock"));

    // Get only pending orders
    List<OrderStatus> pendingOrders =
        orders.stream().filter(pendingPrism::matches).collect(Collectors.toList());

    System.out.println("All orders: " + orders.size());
    System.out.println("Pending orders: " + pendingOrders);
    System.out.println();
  }

  /**
   * Recipe: Type checking with matches().
   *
   * <p>Pattern: {@code if (prism.matches(value))}
   */
  private static void recipeTypeChecking() {
    System.out.println("--- Recipe: Type Checking ---");

    Prism<OrderStatus, Shipped> shippedPrism =
        Prism.of(status -> status instanceof Shipped s ? Optional.of(s) : Optional.empty(), s -> s);

    OrderStatus order1 = new Processing("ORD-001", "alice");
    OrderStatus order2 = new Shipped("ORD-002", "TRACK-789");

    // Check if orders are shipped
    if (shippedPrism.matches(order1)) {
      System.out.println("Order 1 is shipped");
    } else {
      System.out.println("Order 1 is not shipped yet");
    }

    if (shippedPrism.matches(order2)) {
      // Safe to extract since we checked
      Shipped shipped = shippedPrism.getOptional(order2).orElseThrow();
      System.out.println("Order 2 shipped with tracking: " + shipped.trackingNumber());
    }
    System.out.println();
  }

  /**
   * Recipe: Exclusion filtering with doesNotMatch().
   *
   * <p>Pattern: {@code stream.filter(prism::doesNotMatch)}
   */
  private static void recipeExclusionFiltering() {
    System.out.println("--- Recipe: Exclusion Filtering ---");

    Prism<OrderStatus, Cancelled> cancelledPrism =
        Prism.of(
            status -> status instanceof Cancelled c ? Optional.of(c) : Optional.empty(), c -> c);

    List<OrderStatus> orders =
        List.of(
            new Pending("ORD-001"),
            new Cancelled("ORD-002", "Customer request"),
            new Processing("ORD-003", "bob"),
            new Cancelled("ORD-004", "Payment failed"),
            new Shipped("ORD-005", "TRACK-111"));

    // Get all non-cancelled orders
    List<OrderStatus> activeOrders =
        orders.stream().filter(cancelledPrism::doesNotMatch).collect(Collectors.toList());

    System.out.println("Total orders: " + orders.size());
    System.out.println("Active (non-cancelled) orders: " + activeOrders.size());
    activeOrders.forEach(o -> System.out.println("  " + o));
    System.out.println();
  }
}
