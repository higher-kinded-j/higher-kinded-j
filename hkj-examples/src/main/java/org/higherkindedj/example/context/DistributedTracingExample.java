// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.context;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.higherkindedj.hkt.context.RequestContext;
import org.higherkindedj.hkt.context.SecurityContext;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * Comprehensive example demonstrating distributed tracing patterns with Context.
 *
 * <p>This example simulates a microservice architecture where requests flow through multiple
 * services. It shows how Context and ScopedValue enable consistent tracing, security propagation,
 * and observability across service boundaries and concurrent operations.
 *
 * <p>This example demonstrates:
 *
 * <ul>
 *   <li>End-to-end request tracing across services
 *   <li>Span creation and hierarchy
 *   <li>Context propagation in service calls
 *   <li>Simulated MDC-style logging
 *   <li>Collecting distributed traces for analysis
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.context.DistributedTracingExample}
 *
 * @see org.higherkindedj.hkt.context.Context
 * @see org.higherkindedj.hkt.context.RequestContext
 */
public class DistributedTracingExample {

  // Simulated distributed trace collector
  private static final List<SpanRecord> traceCollector = new CopyOnWriteArrayList<>();

  // Span ID generator
  private static final AtomicLong spanCounter = new AtomicLong(1);

  public static void main(String[] args) throws Exception {
    System.out.println("=== Distributed Tracing with Context ===\n");

    // Clear any previous traces
    traceCollector.clear();

    // Simulate an incoming HTTP request to the API Gateway
    simulateIncomingRequest();

    // Print collected trace
    printTraceTree();
  }

  // ============================================================
  // Simulate incoming request flow
  // ============================================================

  private static void simulateIncomingRequest() throws Exception {
    System.out.println("--- Incoming Request: GET /api/orders/123 ---\n");

    // API Gateway receives request and establishes context
    String traceId = RequestContext.generateTraceId();
    String correlationId = "corr-" + traceId.substring(0, 8);
    String spanId = generateSpanId();
    Principal user = () -> "customer@example.com";
    Set<String> roles = Set.of("user", "customer");
    String tenantId = "shop-abc";
    Instant requestTime = Instant.now();
    Instant deadline = requestTime.plus(Duration.ofSeconds(5));

    System.out.println("Gateway established context:");
    System.out.println("  Trace ID: " + traceId);
    System.out.println("  Correlation ID: " + correlationId);
    System.out.println("  User: " + user.getName());
    System.out.println("  Tenant: " + tenantId);
    System.out.println();

    // Execute request within full context
    ScopedValue.where(RequestContext.TRACE_ID, traceId)
        .where(RequestContext.CORRELATION_ID, correlationId)
        .where(RequestContext.TENANT_ID, tenantId)
        .where(RequestContext.REQUEST_TIME, requestTime)
        .where(RequestContext.DEADLINE, deadline)
        .where(SecurityContext.PRINCIPAL, user)
        .where(SecurityContext.ROLES, roles)
        .where(CURRENT_SPAN_ID, spanId)
        .where(PARENT_SPAN_ID, "root")
        .run(
            () -> {
              // Record root span
              recordSpan(
                  "api-gateway",
                  "GET /api/orders/123",
                  () -> {
                    // API Gateway calls Order Service
                    Try<OrderDetails> result = callOrderService("123").runSafe();

                    result.fold(
                        order -> {
                          System.out.println("\nRequest completed successfully!");
                          System.out.println("Order: " + order);
                          return null;
                        },
                        error -> {
                          System.out.println("\nRequest failed: " + error.getMessage());
                          return null;
                        });
                  });
            });
  }

  // ============================================================
  // Simulated service calls
  // ============================================================

  private static VTask<OrderDetails> callOrderService(String orderId) {
    return VTask.of(
        () -> {
          String newSpanId = generateSpanId();
          String parentSpan = CURRENT_SPAN_ID.get();

          // Propagate context to child span
          return ScopedValue.where(CURRENT_SPAN_ID, newSpanId)
              .where(PARENT_SPAN_ID, parentSpan)
              .call(
                  () -> {
                    recordSpan(
                        "order-service",
                        "getOrder(" + orderId + ")",
                        () -> {
                          log("Fetching order " + orderId);
                          simulateWork(50);
                        });

                    // Order service needs to call other services in parallel
                    VTask<CustomerInfo> customerTask = callCustomerService("cust-456");
                    VTask<List<ProductInfo>> productsTask =
                        callProductService(List.of("prod-1", "prod-2"));
                    VTask<ShippingInfo> shippingTask = callShippingService("ship-789");

                    VTask<List<Object>> allResults =
                        Scope.<Object>allSucceed()
                            .fork(customerTask)
                            .fork(productsTask)
                            .fork(shippingTask)
                            .join();

                    List<Object> results =
                        allResults
                            .runSafe()
                            .fold(
                                v -> v,
                                e -> {
                                  throw new RuntimeException(e);
                                });

                    @SuppressWarnings("unchecked")
                    CustomerInfo customer = (CustomerInfo) results.get(0);
                    @SuppressWarnings("unchecked")
                    List<ProductInfo> products = (List<ProductInfo>) results.get(1);
                    ShippingInfo shipping = (ShippingInfo) results.get(2);

                    return new OrderDetails(orderId, customer, products, shipping);
                  });
        });
  }

  private static VTask<CustomerInfo> callCustomerService(String customerId) {
    return VTask.of(
        () -> {
          String newSpanId = generateSpanId();
          String parentSpan = CURRENT_SPAN_ID.get();

          return ScopedValue.where(CURRENT_SPAN_ID, newSpanId)
              .where(PARENT_SPAN_ID, parentSpan)
              .call(
                  () -> {
                    recordSpan(
                        "customer-service",
                        "getCustomer(" + customerId + ")",
                        () -> {
                          log("Fetching customer " + customerId);
                          simulateWork(75);
                        });
                    return new CustomerInfo(customerId, "Jane Smith", "jane@example.com");
                  });
        });
  }

  private static VTask<List<ProductInfo>> callProductService(List<String> productIds) {
    return VTask.of(
        () -> {
          String newSpanId = generateSpanId();
          String parentSpan = CURRENT_SPAN_ID.get();

          return ScopedValue.where(CURRENT_SPAN_ID, newSpanId)
              .where(PARENT_SPAN_ID, parentSpan)
              .call(
                  () -> {
                    recordSpan(
                        "product-service",
                        "getProducts(" + productIds.size() + ")",
                        () -> {
                          log("Fetching " + productIds.size() + " products");
                          simulateWork(60);
                        });
                    return productIds.stream()
                        .map(id -> new ProductInfo(id, "Product " + id, 29.99))
                        .toList();
                  });
        });
  }

  private static VTask<ShippingInfo> callShippingService(String shippingId) {
    return VTask.of(
        () -> {
          String newSpanId = generateSpanId();
          String parentSpan = CURRENT_SPAN_ID.get();

          return ScopedValue.where(CURRENT_SPAN_ID, newSpanId)
              .where(PARENT_SPAN_ID, parentSpan)
              .call(
                  () -> {
                    recordSpan(
                        "shipping-service",
                        "getShipping(" + shippingId + ")",
                        () -> {
                          log("Fetching shipping " + shippingId);

                          // Shipping service calls inventory service
                          VTask<InventoryStatus> inventoryTask =
                              callInventoryService("warehouse-1");
                          InventoryStatus inventory =
                              inventoryTask
                                  .runSafe()
                                  .fold(
                                      v -> v,
                                      e -> {
                                        throw new RuntimeException(e);
                                      });

                          log("Inventory status: " + inventory.status());
                        });
                    return new ShippingInfo(shippingId, "Express", "2-3 business days");
                  });
        });
  }

  private static VTask<InventoryStatus> callInventoryService(String warehouseId) {
    return VTask.of(
        () -> {
          String newSpanId = generateSpanId();
          String parentSpan = CURRENT_SPAN_ID.get();

          return ScopedValue.where(CURRENT_SPAN_ID, newSpanId)
              .where(PARENT_SPAN_ID, parentSpan)
              .call(
                  () -> {
                    recordSpan(
                        "inventory-service",
                        "checkInventory(" + warehouseId + ")",
                        () -> {
                          log("Checking inventory at " + warehouseId);
                          simulateWork(40);
                        });
                    return new InventoryStatus(warehouseId, "IN_STOCK", 150);
                  });
        });
  }

  // ============================================================
  // Span tracking
  // ============================================================

  private static final ScopedValue<String> CURRENT_SPAN_ID = ScopedValue.newInstance();
  private static final ScopedValue<String> PARENT_SPAN_ID = ScopedValue.newInstance();

  private static String generateSpanId() {
    return "span-" + spanCounter.getAndIncrement();
  }

  private static void recordSpan(String service, String operation, Runnable work) {
    String traceId = RequestContext.TRACE_ID.get();
    String spanId = CURRENT_SPAN_ID.get();
    String parentSpanId = PARENT_SPAN_ID.get();
    String tenantId = RequestContext.TENANT_ID.get();
    Instant startTime = Instant.now();

    try {
      work.run();
    } finally {
      Duration duration = Duration.between(startTime, Instant.now());
      traceCollector.add(
          new SpanRecord(
              traceId, spanId, parentSpanId, service, operation, tenantId, startTime, duration));
    }
  }

  private static void log(String message) {
    String traceId = RequestContext.getTraceIdOrDefault("unknown");
    String spanId = CURRENT_SPAN_ID.orElse("unknown");
    String tenant = RequestContext.TENANT_ID.orElse("unknown");

    System.out.printf(
        "[trace=%s span=%s tenant=%s] %s%n",
        traceId.substring(0, Math.min(8, traceId.length())), spanId, tenant, message);
  }

  private static void simulateWork(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  // ============================================================
  // Print trace tree
  // ============================================================

  private static void printTraceTree() {
    System.out.println("\n=== Collected Trace ===\n");

    if (traceCollector.isEmpty()) {
      System.out.println("No spans collected");
      return;
    }

    // Group by trace ID
    Map<String, List<SpanRecord>> byTrace = new ConcurrentHashMap<>();
    for (SpanRecord span : traceCollector) {
      byTrace.computeIfAbsent(span.traceId(), k -> new CopyOnWriteArrayList<>()).add(span);
    }

    for (Map.Entry<String, List<SpanRecord>> entry : byTrace.entrySet()) {
      System.out.println("Trace: " + entry.getKey());
      System.out.println();

      // Build parent-child relationships
      Map<String, List<SpanRecord>> children = new ConcurrentHashMap<>();
      SpanRecord root = null;

      for (SpanRecord span : entry.getValue()) {
        if ("root".equals(span.parentSpanId())) {
          root = span;
        } else {
          children
              .computeIfAbsent(span.parentSpanId(), k -> new CopyOnWriteArrayList<>())
              .add(span);
        }
      }

      // Print tree
      if (root != null) {
        printSpanTree(root, children, 0);
      }
    }

    // Summary statistics
    System.out.println("\n--- Summary ---");
    System.out.println("Total spans: " + traceCollector.size());

    long totalDuration = traceCollector.stream().mapToLong(s -> s.duration().toMillis()).sum();
    System.out.println("Total span time: " + totalDuration + "ms");

    Map<String, Long> byService = new ConcurrentHashMap<>();
    for (SpanRecord span : traceCollector) {
      byService.merge(span.service(), span.duration().toMillis(), Long::sum);
    }

    System.out.println("Time by service:");
    byService.forEach((service, time) -> System.out.println("  " + service + ": " + time + "ms"));
  }

  private static void printSpanTree(
      SpanRecord span, Map<String, List<SpanRecord>> children, int depth) {
    String indent = "  ".repeat(depth);
    System.out.printf(
        "%s├─ %s [%s] %dms%n",
        indent, span.service(), span.operation(), span.duration().toMillis());

    List<SpanRecord> childSpans = children.get(span.spanId());
    if (childSpans != null) {
      for (SpanRecord child : childSpans) {
        printSpanTree(child, children, depth + 1);
      }
    }
  }

  // ============================================================
  // Data records
  // ============================================================

  record SpanRecord(
      String traceId,
      String spanId,
      String parentSpanId,
      String service,
      String operation,
      String tenantId,
      Instant startTime,
      Duration duration) {}

  record OrderDetails(
      String orderId, CustomerInfo customer, List<ProductInfo> products, ShippingInfo shipping) {}

  record CustomerInfo(String id, String name, String email) {}

  record ProductInfo(String id, String name, double price) {}

  record ShippingInfo(String id, String method, String estimate) {}

  record InventoryStatus(String warehouseId, String status, int quantity) {}
}
