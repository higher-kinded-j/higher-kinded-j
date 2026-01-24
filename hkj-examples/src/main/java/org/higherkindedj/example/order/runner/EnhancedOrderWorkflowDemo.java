// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.runner;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.higherkindedj.example.order.config.WorkflowConfig;
import org.higherkindedj.example.order.context.OrderContext;
import org.higherkindedj.example.order.model.OrderLineRequest;
import org.higherkindedj.example.order.model.OrderRequest;
import org.higherkindedj.example.order.model.PaymentMethod;
import org.higherkindedj.example.order.model.Product;
import org.higherkindedj.example.order.model.ShippingAddress;
import org.higherkindedj.example.order.model.ValidatedOrderLine;
import org.higherkindedj.example.order.model.value.Money;
import org.higherkindedj.example.order.model.value.OrderId;
import org.higherkindedj.example.order.model.value.ProductId;
import org.higherkindedj.example.order.service.impl.InMemoryCustomerService;
import org.higherkindedj.example.order.service.impl.InMemoryDiscountService;
import org.higherkindedj.example.order.service.impl.InMemoryInventoryService;
import org.higherkindedj.example.order.service.impl.InMemoryNotificationService;
import org.higherkindedj.example.order.service.impl.InMemoryPaymentService;
import org.higherkindedj.example.order.service.impl.InMemoryShippingService;
import org.higherkindedj.example.order.workflow.EnhancedOrderWorkflow;

/**
 * Demonstrates the Enhanced Order Workflow showcasing recent HKJ innovations.
 *
 * <p>This demo shows how the order workflow integrates:
 *
 * <ul>
 *   <li><b>Context propagation</b> - Trace IDs, tenant isolation, and deadlines flow through all
 *       operations
 *   <li><b>Structured concurrency</b> - Parallel inventory checks with automatic cancellation
 *   <li><b>Resource management</b> - Guaranteed cleanup of reservations on failure
 *   <li><b>Virtual thread execution</b> - Scalable concurrent processing
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.order.runner.EnhancedOrderWorkflowDemo}
 */
public class EnhancedOrderWorkflowDemo {

  public static void main(String[] args) {
    System.out.println("╔══════════════════════════════════════════════════════════════════╗");
    System.out.println("║     Enhanced Order Workflow Demo - HKJ Recent Innovations        ║");
    System.out.println("╠══════════════════════════════════════════════════════════════════╣");
    System.out.println("║  Features demonstrated:                                          ║");
    System.out.println("║  • Context propagation (trace ID, tenant ID, deadline)           ║");
    System.out.println("║  • Structured concurrency (parallel inventory checks)            ║");
    System.out.println("║  • Resource management (reservation cleanup on failure)          ║");
    System.out.println("║  • Virtual thread execution (VTaskPath)                          ║");
    System.out.println("╚══════════════════════════════════════════════════════════════════╝\n");

    // Create workflow with services
    var workflow = createWorkflow();

    // Run scenarios
    runScenario1_SuccessfulOrderWithContext(workflow);
    runScenario2_ParallelInventoryCheck(workflow);
    runScenario3_MultiTenantIsolation(workflow);
    runScenario4_DeadlineEnforcement(workflow);

    System.out.println("\n=== Demo Complete ===");
  }

  private static EnhancedOrderWorkflow createWorkflow() {
    return new EnhancedOrderWorkflow(
        new InMemoryCustomerService(),
        new InMemoryInventoryService(),
        new InMemoryDiscountService(),
        new InMemoryPaymentService(),
        new InMemoryShippingService(),
        new InMemoryNotificationService(),
        WorkflowConfig.defaults());
  }

  // =========================================================================
  // Scenario 1: Successful Order with Full Context
  // =========================================================================

  private static void runScenario1_SuccessfulOrderWithContext(EnhancedOrderWorkflow workflow) {
    System.out.println("┌─────────────────────────────────────────────────────────────────┐");
    System.out.println("│ Scenario 1: Successful Order with Full Context Propagation     │");
    System.out.println("└─────────────────────────────────────────────────────────────────┘");
    System.out.println("  Context values automatically flow to all operations.\n");

    var request = createSampleRequest("CUST-001", Optional.empty());

    // Set up full context
    String traceId = OrderContext.generateTraceId();
    String tenantId = "acme-corp";
    Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
    Principal user = () -> "alice@acme-corp.com";
    Set<String> roles = Set.of("user", "premium");

    System.out.println("  Setting up context:");
    System.out.println("    Trace ID: " + traceId.substring(0, 8) + "...");
    System.out.println("    Tenant:   " + tenantId);
    System.out.println("    Deadline: 30 seconds from now");
    System.out.println("    User:     " + user.getName());
    System.out.println();

    long start = System.currentTimeMillis();

    // Run workflow within context scope
    ScopedValue.where(OrderContext.TRACE_ID, traceId)
        .where(OrderContext.TENANT_ID, tenantId)
        .where(OrderContext.DEADLINE, deadline)
        .where(OrderContext.PRINCIPAL, user)
        .where(OrderContext.ROLES, roles)
        .run(
            () -> {
              var result = workflow.process(request).run().runSafe();

              result.fold(
                  either ->
                      either.fold(
                          error -> {
                            System.out.println("  Order failed: " + error);
                            return null;
                          },
                          success -> {
                            System.out.println("  Order successful!");
                            System.out.println("    " + success);
                            return null;
                          }),
                  error -> {
                    System.out.println("  Execution error: " + error.getMessage());
                    return null;
                  });
            });

    long elapsed = System.currentTimeMillis() - start;
    System.out.println("  Elapsed: " + elapsed + "ms");
    System.out.println();
  }

  // =========================================================================
  // Scenario 2: Parallel Inventory Check
  // =========================================================================

  private static void runScenario2_ParallelInventoryCheck(EnhancedOrderWorkflow workflow) {
    System.out.println("┌─────────────────────────────────────────────────────────────────┐");
    System.out.println("│ Scenario 2: Parallel Inventory Check with Structured Concurrency│");
    System.out.println("└─────────────────────────────────────────────────────────────────┘");
    System.out.println("  Multiple warehouses checked in parallel - first success wins.\n");

    String traceId = OrderContext.generateTraceId();

    System.out.println("  Trace ID: " + traceId.substring(0, 8) + "...");
    System.out.println("  Checking 3 warehouses in parallel...\n");

    long start = System.currentTimeMillis();

    ScopedValue.where(OrderContext.TRACE_ID, traceId)
        .where(OrderContext.TENANT_ID, "demo-tenant")
        .where(OrderContext.DEADLINE, Instant.now().plus(Duration.ofSeconds(30)))
        .run(
            () -> {
              // Build validated order lines for the parallel check
              var orderId = OrderId.generate();
              var validatedLines =
                  List.of(
                      createValidatedOrderLine("PROD-001", 2),
                      createValidatedOrderLine("PROD-002", 1));

              System.out.println("  [Invoking parallel warehouse checks on virtual threads]");
              System.out.println(
                  "  [Context (trace ID) automatically propagates to each thread]\n");

              // Actually invoke the parallel reservation method
              var result =
                  workflow.reserveInventoryParallel(orderId, validatedLines).run().runSafe();

              result.fold(
                  either ->
                      either.fold(
                          error -> {
                            System.out.println("  Parallel check failed: " + error);
                            return null;
                          },
                          reservation -> {
                            System.out.println("  Parallel check completed successfully!");
                            System.out.println(
                                "    Reservation ID: " + reservation.reservationId());
                            System.out.println("    - First warehouse to respond won");
                            System.out.println("    - Other tasks were automatically cancelled");
                            return null;
                          }),
                  error -> {
                    System.out.println("  Execution error: " + error.getMessage());
                    return null;
                  });
            });

    long elapsed = System.currentTimeMillis() - start;
    System.out.println("\n  Elapsed: " + elapsed + "ms (parallel reduces latency)");
    System.out.println();
  }

  /** Creates a validated order line for demo purposes. */
  private static ValidatedOrderLine createValidatedOrderLine(String productIdStr, int quantity) {
    var productId = new ProductId(productIdStr);
    var product =
        new Product(
            productId,
            "Product " + productIdStr,
            "Description",
            Money.gbp("10.00"),
            "General",
            true);
    return ValidatedOrderLine.of(productId, product, quantity);
  }

  // =========================================================================
  // Scenario 3: Multi-Tenant Isolation
  // =========================================================================

  private static void runScenario3_MultiTenantIsolation(EnhancedOrderWorkflow workflow) {
    System.out.println("┌─────────────────────────────────────────────────────────────────┐");
    System.out.println("│ Scenario 3: Multi-Tenant Isolation via Context                 │");
    System.out.println("└─────────────────────────────────────────────────────────────────┘");
    System.out.println("  Same workflow, different tenants - complete isolation.\n");

    var request = createSampleRequest("CUST-001", Optional.empty());

    // Process for Tenant A
    System.out.println("  Processing for Tenant A (acme-corp):");
    ScopedValue.where(OrderContext.TRACE_ID, OrderContext.generateTraceId())
        .where(OrderContext.TENANT_ID, "acme-corp")
        .where(OrderContext.DEADLINE, Instant.now().plus(Duration.ofSeconds(30)))
        .run(
            () -> {
              System.out.println(
                  "    Current tenant: "
                      + OrderContext.TENANT_ID.get()
                      + " [trace="
                      + OrderContext.shortTraceId()
                      + "]");
              // Would process order here - each step sees "acme-corp" as tenant
            });

    // Process for Tenant B
    System.out.println("\n  Processing for Tenant B (widgets-inc):");
    ScopedValue.where(OrderContext.TRACE_ID, OrderContext.generateTraceId())
        .where(OrderContext.TENANT_ID, "widgets-inc")
        .where(OrderContext.DEADLINE, Instant.now().plus(Duration.ofSeconds(30)))
        .run(
            () -> {
              System.out.println(
                  "    Current tenant: "
                      + OrderContext.TENANT_ID.get()
                      + " [trace="
                      + OrderContext.shortTraceId()
                      + "]");
              // Would process order here - each step sees "widgets-inc" as tenant
            });

    System.out.println("\n  Key benefit: No tenant parameter passing through every method!");
    System.out.println("  Context is implicitly available everywhere, including parallel tasks.");
    System.out.println();
  }

  // =========================================================================
  // Scenario 4: Deadline Enforcement
  // =========================================================================

  private static void runScenario4_DeadlineEnforcement(EnhancedOrderWorkflow workflow) {
    System.out.println("┌─────────────────────────────────────────────────────────────────┐");
    System.out.println("│ Scenario 4: SLA Deadline Enforcement                           │");
    System.out.println("└─────────────────────────────────────────────────────────────────┘");
    System.out.println("  Operations fail fast when deadline is exceeded.\n");

    String traceId = OrderContext.generateTraceId();

    // Set a very short deadline that will expire
    Instant tightDeadline = Instant.now().plus(Duration.ofMillis(50));

    System.out.println("  Setting tight deadline: 50ms from now");
    System.out.println("  Trace ID: " + traceId.substring(0, 8) + "...\n");

    ScopedValue.where(OrderContext.TRACE_ID, traceId)
        .where(OrderContext.TENANT_ID, "demo-tenant")
        .where(OrderContext.DEADLINE, tightDeadline)
        .run(
            () -> {
              // Simulate some work
              try {
                Thread.sleep(100); // This exceeds the deadline
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }

              // Check deadline
              if (OrderContext.isDeadlineExceeded()) {
                System.out.println("  Deadline exceeded - failing fast!");
                System.out.println("  Remaining time: " + OrderContext.remainingTime());
              } else {
                System.out.println("  Still within deadline");
              }
            });

    System.out.println("\n  Generous deadline (30 seconds):");
    Instant generousDeadline = Instant.now().plus(Duration.ofSeconds(30));

    ScopedValue.where(OrderContext.TRACE_ID, OrderContext.generateTraceId())
        .where(OrderContext.TENANT_ID, "demo-tenant")
        .where(OrderContext.DEADLINE, generousDeadline)
        .run(
            () -> {
              // Simulate some work
              try {
                Thread.sleep(100);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }

              // Check deadline
              if (OrderContext.isDeadlineExceeded()) {
                System.out.println("  Deadline exceeded");
              } else {
                System.out.println("  Still within deadline");
                System.out.println(
                    "  Remaining time: " + OrderContext.remainingTime().toMillis() + "ms");
              }
            });

    System.out.println();
  }

  // =========================================================================
  // Helper Methods
  // =========================================================================

  private static OrderRequest createSampleRequest(String customerId, Optional<String> promoCode) {
    return new OrderRequest(
        customerId,
        List.of(new OrderLineRequest("PROD-001", 2), new OrderLineRequest("PROD-002", 1)),
        promoCode,
        new ShippingAddress("Test User", "123 Test Street", "London", "SW1A 1AA", "GB"),
        new PaymentMethod.CreditCard("4242424242424242", "12", "2025", "123"));
  }
}
