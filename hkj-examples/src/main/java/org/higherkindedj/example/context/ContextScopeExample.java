// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.context;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.higherkindedj.hkt.context.Context;
import org.higherkindedj.hkt.context.RequestContext;
import org.higherkindedj.hkt.context.SecurityContext;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * Examples demonstrating Context integration with VTask and Scope for concurrent operations.
 *
 * <p>ScopedValue bindings automatically propagate to child virtual threads, making Context ideal
 * for structured concurrency. This example shows how to use Context with VTask and Scope to
 * maintain request context across parallel operations.
 *
 * <p>This example demonstrates:
 *
 * <ul>
 *   <li>Context propagation to VTask operations
 *   <li>Parallel operations with shared context
 *   <li>Security context in concurrent workflows
 *   <li>Deadline propagation in scoped operations
 *   <li>Context-aware error handling
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.context.ContextScopeExample}
 *
 * @see org.higherkindedj.hkt.context.Context
 * @see org.higherkindedj.hkt.vtask.VTask
 * @see org.higherkindedj.hkt.vtask.Scope
 */
public class ContextScopeExample {

  public static void main(String[] args) throws Exception {
    System.out.println("=== Context with VTask and Scope ===\n");

    contextToVTaskExample();
    parallelWithSharedContext();
    securityContextInScope();
    deadlinePropagationExample();
    realWorldDashboardExample();
  }

  // ============================================================
  // Context to VTask conversion
  // ============================================================

  private static void contextToVTaskExample() throws Exception {
    System.out.println("--- Context to VTask Conversion ---\n");

    String traceId = RequestContext.generateTraceId();

    // Create a context computation
    Context<String, String> getTraceContext =
        Context.asks(RequestContext.TRACE_ID, id -> "Executing with trace: " + id);

    // Convert to VTask - captures the current scope
    // The VTask must be run within the same scope binding
    ScopedValue.where(RequestContext.TRACE_ID, traceId)
        .run(
            () -> {
              VTask<String> task = getTraceContext.toVTask();

              // Execute the task
              Try<String> result = task.runSafe();
              result.fold(
                  value -> {
                    System.out.println("VTask result: " + value);
                    return null;
                  },
                  error -> {
                    System.out.println("VTask error: " + error.getMessage());
                    return null;
                  });
            });

    // Chain context operations, then convert
    ScopedValue.where(RequestContext.TRACE_ID, traceId)
        .where(RequestContext.TENANT_ID, "acme")
        .run(
            () -> {
              Context<String, String> traceCtx = Context.ask(RequestContext.TRACE_ID);
              Context<String, String> tenantCtx = Context.ask(RequestContext.TENANT_ID);

              // Build a combined result
              VTask<String> combined =
                  VTask.succeed("Request Info:")
                      .flatMap(
                          header -> traceCtx.toVTask().map(trace -> header + "\n  Trace: " + trace))
                      .flatMap(
                          msg -> tenantCtx.toVTask().map(tenant -> msg + "\n  Tenant: " + tenant));

              Try<String> result = combined.runSafe();
              System.out.println(result.orElse("error"));
            });
    System.out.println();
  }

  // ============================================================
  // Parallel operations with shared context
  // ============================================================

  private static void parallelWithSharedContext() throws Exception {
    System.out.println("--- Parallel Operations with Shared Context ---\n");

    String traceId = RequestContext.generateTraceId();
    String tenantId = "acme-corp";

    System.out.println("Starting parallel operations with trace: " + traceId.substring(0, 8));

    ScopedValue.where(RequestContext.TRACE_ID, traceId)
        .where(RequestContext.TENANT_ID, tenantId)
        .run(
            () -> {
              // Create tasks that read from context
              // Each task runs on a virtual thread that inherits the scoped values
              VTask<String> fetchUser =
                  VTask.of(
                      () -> {
                        String trace = RequestContext.TRACE_ID.get();
                        String tenant = RequestContext.TENANT_ID.get();
                        Thread.sleep(100);
                        return String.format(
                            "User[trace=%s,tenant=%s]", trace.substring(0, 8), tenant);
                      });

              VTask<String> fetchOrders =
                  VTask.of(
                      () -> {
                        String trace = RequestContext.TRACE_ID.get();
                        String tenant = RequestContext.TENANT_ID.get();
                        Thread.sleep(100);
                        return String.format(
                            "Orders[trace=%s,tenant=%s]", trace.substring(0, 8), tenant);
                      });

              VTask<String> fetchPrefs =
                  VTask.of(
                      () -> {
                        String trace = RequestContext.TRACE_ID.get();
                        String tenant = RequestContext.TENANT_ID.get();
                        Thread.sleep(100);
                        return String.format(
                            "Prefs[trace=%s,tenant=%s]", trace.substring(0, 8), tenant);
                      });

              long start = System.currentTimeMillis();

              // Fork all tasks in a scope - they run in parallel on virtual threads
              VTask<List<String>> allResults =
                  Scope.<String>allSucceed()
                      .fork(fetchUser)
                      .fork(fetchOrders)
                      .fork(fetchPrefs)
                      .join();

              List<String> results = allResults.runSafe().orElse(List.of());
              long elapsed = System.currentTimeMillis() - start;

              System.out.println("Results (parallel, ~100ms expected):");
              results.forEach(r -> System.out.println("  " + r));
              System.out.println("Elapsed: " + elapsed + "ms");
            });
    System.out.println();
  }

  // ============================================================
  // Security context in scoped operations
  // ============================================================

  private static void securityContextInScope() throws Exception {
    System.out.println("--- Security Context in Scope ---\n");

    Principal admin = () -> "admin@example.com";
    Set<String> roles = Set.of("user", "admin");

    ScopedValue.where(SecurityContext.PRINCIPAL, admin)
        .where(SecurityContext.ROLES, roles)
        .run(
            () -> {
              // Each parallel task can check security
              VTask<String> adminTask =
                  VTask.of(
                      () -> {
                        // Verify admin access
                        if (!SecurityContext.ROLES.get().contains("admin")) {
                          throw new SecurityException("Admin required");
                        }
                        Thread.sleep(50);
                        return "Admin operation completed by "
                            + SecurityContext.PRINCIPAL.get().getName();
                      });

              VTask<String> userTask =
                  VTask.of(
                      () -> {
                        Principal p = SecurityContext.PRINCIPAL.get();
                        Thread.sleep(50);
                        return "User operation for " + p.getName();
                      });

              VTask<String> auditTask =
                  VTask.of(
                      () -> {
                        Principal p = SecurityContext.PRINCIPAL.get();
                        Set<String> r = SecurityContext.ROLES.get();
                        Thread.sleep(50);
                        return String.format("Audit: %s with roles %s", p.getName(), r);
                      });

              VTask<List<String>> results =
                  Scope.<String>allSucceed().fork(adminTask).fork(userTask).fork(auditTask).join();

              List<String> values = results.runSafe().orElse(List.of());
              values.forEach(v -> System.out.println("  " + v));
            });
    System.out.println();
  }

  // ============================================================
  // Deadline propagation
  // ============================================================

  private static void deadlinePropagationExample() throws Exception {
    System.out.println("--- Deadline Propagation in Scope ---\n");

    Instant requestTime = Instant.now();
    Instant deadline = requestTime.plus(Duration.ofMillis(500));

    ScopedValue.where(RequestContext.REQUEST_TIME, requestTime)
        .where(RequestContext.DEADLINE, deadline)
        .run(
            () -> {
              // Tasks can check remaining time
              VTask<String> task1 =
                  VTask.of(
                      () -> {
                        Duration remaining =
                            Duration.between(Instant.now(), RequestContext.DEADLINE.get());
                        Thread.sleep(100);
                        return "Task1 completed, " + remaining.toMillis() + "ms was remaining";
                      });

              VTask<String> task2 =
                  VTask.of(
                      () -> {
                        Duration remaining =
                            Duration.between(Instant.now(), RequestContext.DEADLINE.get());
                        Thread.sleep(150);
                        return "Task2 completed, " + remaining.toMillis() + "ms was remaining";
                      });

              // Use scope timeout that aligns with deadline
              Duration scopeTimeout = Duration.between(Instant.now(), deadline);

              VTask<List<String>> results =
                  Scope.<String>allSucceed().timeout(scopeTimeout).fork(task1).fork(task2).join();

              Try<List<String>> result = results.runSafe();
              result.fold(
                  values -> {
                    System.out.println("All tasks completed within deadline:");
                    values.forEach(v -> System.out.println("  " + v));
                    return null;
                  },
                  error -> {
                    System.out.println("Deadline exceeded: " + error.getClass().getSimpleName());
                    return null;
                  });
            });

    // Example with tight deadline that causes timeout
    System.out.println("\nWith tight deadline (100ms for 200ms of work):");
    Instant tightDeadline = Instant.now().plus(Duration.ofMillis(100));

    ScopedValue.where(RequestContext.DEADLINE, tightDeadline)
        .run(
            () -> {
              VTask<String> slowTask =
                  VTask.of(
                      () -> {
                        Thread.sleep(200);
                        return "Slow task done";
                      });

              Duration remaining = Duration.between(Instant.now(), tightDeadline);
              VTask<List<String>> results =
                  Scope.<String>allSucceed()
                      .timeout(remaining)
                      .fork(slowTask)
                      .join()
                      .recover(e -> List.of("Deadline exceeded, using default"));

              List<String> values = results.runSafe().orElse(List.of());
              values.forEach(v -> System.out.println("  " + v));
            });
    System.out.println();
  }

  // ============================================================
  // Real-world dashboard with full context
  // ============================================================

  private static void realWorldDashboardExample() throws Exception {
    System.out.println("--- Real-World: Dashboard with Full Context ---\n");

    // Set up full request context
    Principal user = () -> "jane@company.com";
    Set<String> roles = Set.of("user", "premium");
    String traceId = RequestContext.generateTraceId();
    String tenantId = "company-xyz";
    Instant requestTime = Instant.now();
    Instant deadline = requestTime.plus(Duration.ofSeconds(2));

    System.out.println("Loading dashboard for: " + user.getName());
    System.out.println("Trace: " + traceId.substring(0, 8));
    System.out.println("Tenant: " + tenantId);
    System.out.println();

    long start = System.currentTimeMillis();

    ScopedValue.where(SecurityContext.PRINCIPAL, user)
        .where(SecurityContext.ROLES, roles)
        .where(RequestContext.TRACE_ID, traceId)
        .where(RequestContext.TENANT_ID, tenantId)
        .where(RequestContext.REQUEST_TIME, requestTime)
        .where(RequestContext.DEADLINE, deadline)
        .run(
            () -> {
              // Dashboard components, each requiring different context
              VTask<String> userProfile =
                  VTask.of(
                      () -> {
                        Principal p = SecurityContext.PRINCIPAL.get();
                        Thread.sleep(100);
                        return "Profile: " + p.getName();
                      });

              VTask<String> notifications =
                  VTask.of(
                      () -> {
                        String tenant = RequestContext.TENANT_ID.get();
                        Thread.sleep(100);
                        return "Notifications: 5 unread [" + tenant + "]";
                      });

              VTask<String> premiumFeatures =
                  VTask.of(
                      () -> {
                        Set<String> r = SecurityContext.ROLES.get();
                        Thread.sleep(100);
                        if (r.contains("premium")) {
                          return "Premium: Analytics dashboard enabled";
                        } else {
                          return "Premium: Upgrade to access";
                        }
                      });

              VTask<String> auditLog =
                  VTask.of(
                      () -> {
                        String trace = RequestContext.TRACE_ID.get();
                        Principal p = SecurityContext.PRINCIPAL.get();
                        Thread.sleep(50);
                        return String.format(
                            "Audit[%s]: %s accessed dashboard", trace.substring(0, 8), p.getName());
                      });

              Duration timeout = Duration.between(Instant.now(), RequestContext.DEADLINE.get());

              VTask<List<String>> dashboard =
                  Scope.<String>allSucceed()
                      .timeout(timeout)
                      .fork(userProfile)
                      .fork(notifications)
                      .fork(premiumFeatures)
                      .fork(auditLog)
                      .join();

              Try<List<String>> result = dashboard.runSafe();
              result.fold(
                  components -> {
                    System.out.println("Dashboard loaded:");
                    components.forEach(c -> System.out.println("  " + c));
                    return null;
                  },
                  error -> {
                    System.out.println("Dashboard error: " + error.getMessage());
                    return null;
                  });
            });

    long elapsed = System.currentTimeMillis() - start;
    System.out.println("\nTotal time: " + elapsed + "ms (parallel, ~100ms expected)");
    System.out.println();
  }
}
