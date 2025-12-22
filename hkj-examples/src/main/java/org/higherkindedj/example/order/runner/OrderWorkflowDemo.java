// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.runner;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.example.order.config.WorkflowConfig;
import org.higherkindedj.example.order.model.OrderLineRequest;
import org.higherkindedj.example.order.model.OrderRequest;
import org.higherkindedj.example.order.model.PaymentMethod;
import org.higherkindedj.example.order.model.ShippingAddress;
import org.higherkindedj.example.order.service.impl.InMemoryCustomerService;
import org.higherkindedj.example.order.service.impl.InMemoryDiscountService;
import org.higherkindedj.example.order.service.impl.InMemoryInventoryService;
import org.higherkindedj.example.order.service.impl.InMemoryNotificationService;
import org.higherkindedj.example.order.service.impl.InMemoryPaymentService;
import org.higherkindedj.example.order.service.impl.InMemoryShippingService;
import org.higherkindedj.example.order.workflow.OrderWorkflow;

/**
 * Demonstrates the Order Workflow V2 using ForPath composition.
 *
 * <p>This example shows how the Effect Path API and ForPath comprehensions enable clean, readable
 * workflow composition in modern Java.
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.order.runner.OrderWorkflowDemo}
 */
public class OrderWorkflowDemo {

  public static void main(String[] args) {
    System.out.println("=== Order Workflow V2 Demo ===\n");
    System.out.println("Demonstrates ForPath comprehensions for workflow composition.\n");

    // Create services
    var customerService = new InMemoryCustomerService();
    var discountService = new InMemoryDiscountService();
    var inventoryService = new InMemoryInventoryService();
    var paymentService = new InMemoryPaymentService();
    var shippingService = new InMemoryShippingService();
    var notificationService = new InMemoryNotificationService();
    var config = WorkflowConfig.defaults();

    // Create workflow
    var workflow =
        new OrderWorkflow(
            customerService,
            inventoryService,
            discountService,
            paymentService,
            shippingService,
            notificationService,
            config);

    // Run successful order
    System.out.println("--- Scenario 1: Successful Order ---");
    runSuccessfulOrder(workflow);

    // Run order with invalid customer
    System.out.println("\n--- Scenario 2: Invalid Customer ---");
    runInvalidCustomerOrder(workflow);

    // Run order with promo code
    System.out.println("\n--- Scenario 3: Order with Promo Code ---");
    runOrderWithPromoCode(workflow);

    System.out.println("\n=== Demo Complete ===");
  }

  private static void runSuccessfulOrder(OrderWorkflow workflow) {
    var request =
        new OrderRequest(
            "CUST-001", // Alice Smith, GOLD tier
            List.of(new OrderLineRequest("PROD-001", 2), new OrderLineRequest("PROD-002", 1)),
            Optional.empty(), // No promo code
            new ShippingAddress("Alice Smith", "123 High Street", "London", "SW1A 1AA", "GB"),
            new PaymentMethod.CreditCard("4242424242424242", "12", "2025", "123"));

    var result = workflow.process(request).run();

    result.fold(
        error -> {
          System.out.println("Order failed: " + error.message());
          System.out.println("  Error code: " + error.code());
          return null;
        },
        success -> {
          System.out.println("Order successful!");
          System.out.println("  Order ID: " + success.orderId());
          System.out.println("  Transaction: " + success.transactionId());
          System.out.println("  Tracking: " + success.trackingNumber());
          System.out.println("  Total: " + success.totalCharged());
          System.out.println("  Audit entries: " + success.auditLog().entries().size());
          return null;
        });
  }

  private static void runInvalidCustomerOrder(OrderWorkflow workflow) {
    var request =
        new OrderRequest(
            "CUST-999", // Non-existent customer
            List.of(new OrderLineRequest("PROD-001", 1)),
            Optional.empty(),
            new ShippingAddress("Unknown Person", "456 Low Street", "Manchester", "M1 1AA", "GB"),
            new PaymentMethod.CreditCard("4242424242424242", "12", "2025", "123"));

    var result = workflow.process(request).run();

    result.fold(
        error -> {
          System.out.println("Order failed (as expected): " + error.message());
          System.out.println("  Error code: " + error.code());
          return null;
        },
        success -> {
          System.out.println("Order unexpectedly succeeded!");
          return null;
        });
  }

  private static void runOrderWithPromoCode(OrderWorkflow workflow) {
    var request =
        new OrderRequest(
            "CUST-002", // Bob Jones, SILVER tier
            List.of(new OrderLineRequest("PROD-001", 3), new OrderLineRequest("PROD-003", 2)),
            Optional.of("SAVE20"), // 20% off promo code
            new ShippingAddress("Bob Jones", "789 Medium Street", "Birmingham", "B1 1AA", "GB"),
            new PaymentMethod.DebitCard("5555555555554444", "06", "2026"));

    var result = workflow.process(request).run();

    result.fold(
        error -> {
          System.out.println("Order failed: " + error.message());
          return null;
        },
        success -> {
          System.out.println("Order with promo code successful!");
          System.out.println("  Order ID: " + success.orderId());
          System.out.println("  Total (with 20% discount): " + success.totalCharged());
          System.out.println("  Tracking: " + success.trackingNumber());
          return null;
        });
  }
}
