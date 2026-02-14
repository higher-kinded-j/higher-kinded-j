// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.context;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.higherkindedj.hkt.context.Context;
import org.higherkindedj.hkt.context.RequestContext;

/**
 * Examples demonstrating RequestContext for distributed tracing patterns.
 *
 * <p>RequestContext provides pre-defined ScopedValue constants for common request-scoped data:
 * trace IDs, correlation IDs, locale, tenant ID, request time, and deadlines. These values
 * automatically propagate to child virtual threads.
 *
 * <p>This example demonstrates:
 *
 * <ul>
 *   <li>Basic trace ID propagation
 *   <li>Correlation ID patterns
 *   <li>Multi-tenant request handling
 *   <li>Deadline and timeout propagation
 *   <li>Locale-aware processing
 *   <li>Simulated service call chains with context propagation
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.context.RequestContextExample}
 *
 * @see org.higherkindedj.hkt.context.RequestContext
 * @see org.higherkindedj.hkt.context.Context
 */
public class RequestContextExample {

  public static void main(String[] args) throws Exception {
    System.out.println("=== RequestContext Examples ===\n");

    traceIdExample();
    correlationIdExample();
    multiTenantExample();
    deadlineExample();
    localeExample();
    simulatedServiceChain();
  }

  // ============================================================
  // Trace ID: Request tracing
  // ============================================================

  private static void traceIdExample() throws Exception {
    System.out.println("--- Trace ID Propagation ---\n");

    // Generate a trace ID for the request
    String traceId = RequestContext.generateTraceId();
    System.out.println("Generated trace ID: " + traceId);

    // Create a context that reads the trace ID
    Context<String, String> getTraceId = Context.ask(RequestContext.TRACE_ID);
    Context<String, String> logPrefix =
        Context.asks(RequestContext.TRACE_ID, id -> "[trace=" + id + "] ");

    // Run within the trace scope
    ScopedValue.where(RequestContext.TRACE_ID, traceId)
        .run(
            () -> {
              System.out.println("Inside scope:");
              System.out.println("  Trace ID: " + getTraceId.run());
              System.out.println("  Log prefix: " + logPrefix.run() + "Processing request...");

              // Nested operations see the same trace ID
              processStep("Step 1", getTraceId);
              processStep("Step 2", getTraceId);
            });

    // Using the helper method for default handling
    String defaultTrace = RequestContext.getTraceIdOrDefault("no-trace");
    System.out.println("Outside scope (with default): " + defaultTrace);
    System.out.println();
  }

  private static void processStep(String step, Context<String, String> getTraceId) {
    System.out.println("  " + step + " [trace=" + getTraceId.run() + "]");
  }

  // ============================================================
  // Correlation ID: Cross-service correlation
  // ============================================================

  private static void correlationIdExample() throws Exception {
    System.out.println("--- Correlation ID Pattern ---\n");

    // Correlation ID tracks requests across service boundaries
    // Unlike trace ID, it might be set by an upstream service
    String correlationId = "corr-" + System.currentTimeMillis();

    Context<String, String> getCorrelationId = Context.ask(RequestContext.CORRELATION_ID);
    Context<String, String> serviceHeader =
        Context.asks(RequestContext.CORRELATION_ID, id -> "X-Correlation-ID: " + id);

    ScopedValue.where(RequestContext.CORRELATION_ID, correlationId)
        .run(
            () -> {
              System.out.println("Incoming correlation: " + getCorrelationId.run());
              System.out.println("Outgoing header: " + serviceHeader.run());
            });
    System.out.println();
  }

  // ============================================================
  // Multi-tenant: Tenant isolation
  // ============================================================

  private static void multiTenantExample() throws Exception {
    System.out.println("--- Multi-Tenant Request Handling ---\n");

    Context<String, String> getTenantId = Context.ask(RequestContext.TENANT_ID);
    Context<String, String> dataSource =
        Context.asks(RequestContext.TENANT_ID, id -> "datasource_" + id);
    Context<String, String> cachePrefix = Context.asks(RequestContext.TENANT_ID, id -> id + ":");

    // Process requests for different tenants
    for (String tenant : new String[] {"acme-corp", "initech", "umbrella"}) {
      ScopedValue.where(RequestContext.TENANT_ID, tenant)
          .run(
              () -> {
                System.out.println("Processing request for tenant: " + getTenantId.run());
                System.out.println("  Data source: " + dataSource.run());
                System.out.println("  Cache prefix: " + cachePrefix.run());
              });
    }
    System.out.println();
  }

  // ============================================================
  // Deadline: Request timeout propagation
  // ============================================================

  private static void deadlineExample() throws Exception {
    System.out.println("--- Deadline Propagation ---\n");

    Instant requestTime = Instant.now();
    Instant deadline = requestTime.plus(Duration.ofSeconds(30));

    Context<Instant, Instant> getRequestTime = Context.ask(RequestContext.REQUEST_TIME);
    Context<Instant, Instant> getDeadline = Context.ask(RequestContext.DEADLINE);
    Context<Instant, Duration> getRemainingTime =
        Context.asks(RequestContext.DEADLINE, d -> Duration.between(Instant.now(), d));

    ScopedValue.where(RequestContext.REQUEST_TIME, requestTime)
        .where(RequestContext.DEADLINE, deadline)
        .run(
            () -> {
              System.out.println("Request started: " + getRequestTime.run());
              System.out.println("Deadline: " + getDeadline.run());
              System.out.println("Remaining: " + getRemainingTime.run().toMillis() + "ms");

              // Simulate some work
              simulateWork(10);

              System.out.println(
                  "After work, remaining: " + getRemainingTime.run().toMillis() + "ms");
            });
    System.out.println();
  }

  // ============================================================
  // Locale: Internationalisation support
  // ============================================================

  private static void localeExample() throws Exception {
    System.out.println("--- Locale-Aware Processing ---\n");

    Context<Locale, Locale> getLocale = Context.ask(RequestContext.LOCALE);
    Context<Locale, String> getLanguage = Context.asks(RequestContext.LOCALE, Locale::getLanguage);
    Context<Locale, String> getGreeting =
        Context.asks(
            RequestContext.LOCALE,
            locale -> {
              return switch (locale.getLanguage()) {
                case "fr" -> "Bonjour";
                case "de" -> "Guten Tag";
                case "es" -> "Hola";
                case "ja" -> "こんにちは";
                default -> "Hello";
              };
            });

    for (Locale locale :
        new Locale[] {Locale.ENGLISH, Locale.FRENCH, Locale.GERMAN, Locale.JAPANESE}) {
      ScopedValue.where(RequestContext.LOCALE, locale)
          .run(
              () -> {
                System.out.println(getLanguage.run() + ": " + getGreeting.run());
              });
    }
    System.out.println();
  }

  // ============================================================
  // Simulated service chain
  // ============================================================

  private static void simulatedServiceChain() throws Exception {
    System.out.println("--- Simulated Service Chain ---\n");

    String traceId = RequestContext.generateTraceId();
    String correlationId = "corr-" + traceId.substring(0, 8);
    Instant requestTime = Instant.now();
    Instant deadline = requestTime.plus(Duration.ofSeconds(5));
    String tenantId = "acme-corp";

    System.out.println("=== Incoming Request ===");
    System.out.println("Trace ID: " + traceId);
    System.out.println("Correlation ID: " + correlationId);
    System.out.println("Tenant: " + tenantId);
    System.out.println();

    ScopedValue.where(RequestContext.TRACE_ID, traceId)
        .where(RequestContext.CORRELATION_ID, correlationId)
        .where(RequestContext.REQUEST_TIME, requestTime)
        .where(RequestContext.DEADLINE, deadline)
        .where(RequestContext.TENANT_ID, tenantId)
        .run(
            () -> {
              // Simulate a multi-step request processing pipeline
              System.out.println("=== Processing Pipeline ===");

              // Step 1: Validate request
              logWithContext("Validating request");
              simulateWork(50);

              // Step 2: Load user data (would normally call another service)
              logWithContext("Loading user data");
              simulateWork(100);

              // Step 3: Process business logic
              logWithContext("Processing business logic");
              simulateWork(75);

              // Step 4: Save results
              logWithContext("Saving results");
              simulateWork(50);

              logWithContext("Request completed");
            });
    System.out.println();
  }

  private static void logWithContext(String message) {
    String traceId = RequestContext.getTraceIdOrDefault("unknown");
    Duration remaining =
        Duration.between(Instant.now(), RequestContext.DEADLINE.orElse(Instant.MAX));

    System.out.printf(
        "[trace=%s remaining=%dms] %s%n", traceId.substring(0, 8), remaining.toMillis(), message);
  }

  private static void simulateWork(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
