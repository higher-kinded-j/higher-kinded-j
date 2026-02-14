// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.context;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.higherkindedj.hkt.context.Context;

/**
 * ScopedValue keys for order workflow context propagation.
 *
 * <p>This class provides scoped values for cross-cutting concerns in order processing:
 *
 * <ul>
 *   <li><b>TRACE_ID</b> - Distributed tracing identifier for request correlation
 *   <li><b>TENANT_ID</b> - Multi-tenancy isolation identifier
 *   <li><b>DEADLINE</b> - SLA deadline for the entire order processing
 *   <li><b>PRINCIPAL</b> - The authenticated user placing the order
 *   <li><b>ROLES</b> - Authorization roles for feature access
 * </ul>
 *
 * <p>These scoped values automatically propagate to child virtual threads when using structured
 * concurrency with {@link org.higherkindedj.hkt.vtask.Scope}, enabling consistent context access
 * across parallel operations.
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * // Set up context at workflow entry
 * String result = ScopedValue
 *     .where(OrderContext.TRACE_ID, OrderContext.generateTraceId())
 *     .where(OrderContext.TENANT_ID, "acme-corp")
 *     .where(OrderContext.DEADLINE, Instant.now().plus(Duration.ofSeconds(30)))
 *     .call(() -> workflow.process(request));
 *
 * // Access context in any step (including parallel operations)
 * String traceId = OrderContext.TRACE_ID.get();
 * }</pre>
 *
 * @see org.higherkindedj.hkt.context.Context
 * @see org.higherkindedj.hkt.vtask.Scope
 */
public final class OrderContext {

  private OrderContext() {
    // Utility class - no instantiation
  }

  // ==================== Scoped Value Keys ====================

  /**
   * Trace ID for distributed tracing and log correlation.
   *
   * <p>This value should be set at the entry point of order processing and propagates to all
   * downstream operations, including parallel tasks on virtual threads.
   */
  public static final ScopedValue<String> TRACE_ID = ScopedValue.newInstance();

  /**
   * Tenant identifier for multi-tenant isolation.
   *
   * <p>All operations within a scope inherit this value, ensuring data isolation between tenants.
   */
  public static final ScopedValue<String> TENANT_ID = ScopedValue.newInstance();

  /**
   * Request deadline for SLA enforcement.
   *
   * <p>Operations can check remaining time via {@link #remainingTime()} and fail fast if the
   * deadline has passed.
   */
  public static final ScopedValue<Instant> DEADLINE = ScopedValue.newInstance();

  /**
   * Request start time for latency tracking.
   *
   * <p>Used in conjunction with DEADLINE to calculate remaining time.
   */
  public static final ScopedValue<Instant> REQUEST_TIME = ScopedValue.newInstance();

  /**
   * Authenticated principal (user) placing the order.
   *
   * <p>Used for authorization checks and audit logging.
   */
  public static final ScopedValue<Principal> PRINCIPAL = ScopedValue.newInstance();

  /**
   * Authorization roles for the current user.
   *
   * <p>Used for feature access control (e.g., premium features, admin operations).
   */
  public static final ScopedValue<Set<String>> ROLES = ScopedValue.newInstance();

  // ==================== Utility Methods ====================

  /**
   * Generates a new trace ID for request correlation.
   *
   * @return a new UUID-based trace ID
   */
  public static String generateTraceId() {
    return UUID.randomUUID().toString();
  }

  /**
   * Calculates the remaining time until the deadline.
   *
   * <p>Returns {@link Duration#ZERO} if the deadline has passed or is not set.
   *
   * @return the remaining duration, never negative
   */
  public static Duration remainingTime() {
    if (!DEADLINE.isBound()) {
      return Duration.ofDays(365); // No deadline, effectively infinite
    }
    Duration remaining = Duration.between(Instant.now(), DEADLINE.get());
    return remaining.isNegative() ? Duration.ZERO : remaining;
  }

  /**
   * Checks if the deadline has been exceeded.
   *
   * @return true if the deadline is set and has passed
   */
  public static boolean isDeadlineExceeded() {
    return DEADLINE.isBound() && Instant.now().isAfter(DEADLINE.get());
  }

  /**
   * Gets a short form of the trace ID for logging (first 8 characters).
   *
   * @return abbreviated trace ID, or "no-trace" if not bound
   */
  public static String shortTraceId() {
    if (!TRACE_ID.isBound()) {
      return "no-trace";
    }
    String full = TRACE_ID.get();
    return full.length() > 8 ? full.substring(0, 8) : full;
  }

  /**
   * Checks if the current user has a specific role.
   *
   * @param role the role to check
   * @return true if the user has the role, false if not or if roles are not bound
   */
  public static boolean hasRole(String role) {
    return ROLES.isBound() && ROLES.get().contains(role);
  }

  // ==================== Context Accessors ====================

  /**
   * Creates a Context that reads the trace ID.
   *
   * @return a Context producing the current trace ID
   */
  public static Context<String, String> traceId() {
    return Context.ask(TRACE_ID);
  }

  /**
   * Creates a Context that reads the tenant ID.
   *
   * @return a Context producing the current tenant ID
   */
  public static Context<String, String> tenantId() {
    return Context.ask(TENANT_ID);
  }

  /**
   * Creates a Context that reads the deadline.
   *
   * @return a Context producing the current deadline
   */
  public static Context<Instant, Instant> deadline() {
    return Context.ask(DEADLINE);
  }

  /**
   * Creates a Context that reads the principal.
   *
   * @return a Context producing the current principal
   */
  public static Context<Principal, Principal> principal() {
    return Context.ask(PRINCIPAL);
  }
}
