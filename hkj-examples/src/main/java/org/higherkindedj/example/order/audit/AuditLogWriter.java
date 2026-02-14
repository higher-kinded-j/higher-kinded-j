// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.audit;

import static java.util.stream.Collectors.joining;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.higherkindedj.example.order.audit.AuditLog.AuditEntry;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.WriterPath;

/**
 * Convenience class for creating WriterPath instances with audit logging.
 *
 * <p>Provides factory methods for common logging patterns that integrate with the WriterPath monad
 * for automatic accumulation of audit entries.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * var result = writer.info("Order validated", order.orderId())
 *     .via(__ -> processPayment(order))
 *     .via(payment -> writer.info("Payment processed", payment.transactionId(), payment))
 *     .via(__ -> createShipment(order));
 *
 * var auditLog = result.written();  // Accumulated audit log
 * var value = result.value();       // Final value
 * }</pre>
 */
public final class AuditLogWriter {

  private final String workflowName;
  private String currentStep = "unknown";

  /**
   * Creates an audit log writer for a workflow.
   *
   * @param workflowName the name of the workflow for log context
   */
  public AuditLogWriter(String workflowName) {
    this.workflowName = workflowName;
  }

  /** Creates a default audit log writer. */
  public AuditLogWriter() {
    this("OrderWorkflow");
  }

  /**
   * Sets the current step for subsequent log entries.
   *
   * @param step the step name
   * @return this writer for chaining
   */
  public AuditLogWriter step(String step) {
    this.currentStep = step;
    return this;
  }

  /**
   * Logs an info message and returns a value.
   *
   * @param action the action being logged
   * @param details additional details
   * @param value the value to return
   * @param <A> the value type
   * @return a WriterPath with the log entry and value
   */
  public <A> WriterPath<AuditLog, A> info(String action, String details, A value) {
    var entry = new AuditEntry(formatAction(action), details, Instant.now());
    return WriterPath.writer(value, AuditLog.of(entry), AuditLog.monoid());
  }

  /**
   * Logs an info message without a return value.
   *
   * @param action the action being logged
   * @param details additional details
   * @return a WriterPath with the log entry
   */
  public WriterPath<AuditLog, Unit> info(String action, String details) {
    return info(action, details, Unit.INSTANCE);
  }

  /**
   * Logs an info message with Map context and returns a value.
   *
   * @param action the action being logged
   * @param context context map for structured logging
   * @param value the value to return
   * @param <A> the value type
   * @return a WriterPath with the log entry and value
   */
  public <A> WriterPath<AuditLog, A> infoWithContext(
      String action, Map<String, Object> context, A value) {
    var details =
        context.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(joining(", "));
    return info(action, details, value);
  }

  /**
   * Logs a warning message and returns a value.
   *
   * @param action the action being logged
   * @param details additional details
   * @param value the value to return
   * @param <A> the value type
   * @return a WriterPath with the log entry and value
   */
  public <A> WriterPath<AuditLog, A> warn(String action, String details, A value) {
    var entry = new AuditEntry("[WARN] " + formatAction(action), details, Instant.now());
    return WriterPath.writer(value, AuditLog.of(entry), AuditLog.monoid());
  }

  /**
   * Logs an error message and returns a value.
   *
   * @param action the action being logged
   * @param details additional details
   * @param value the value to return
   * @param <A> the value type
   * @return a WriterPath with the log entry and value
   */
  public <A> WriterPath<AuditLog, A> error(String action, String details, A value) {
    var entry = new AuditEntry("[ERROR] " + formatAction(action), details, Instant.now());
    return WriterPath.writer(value, AuditLog.of(entry), AuditLog.monoid());
  }

  /**
   * Logs a timed operation.
   *
   * @param action the action being logged
   * @param duration the duration of the operation
   * @param value the value to return
   * @param <A> the value type
   * @return a WriterPath with the log entry and value
   */
  public <A> WriterPath<AuditLog, A> timed(String action, Duration duration, A value) {
    var details = "completed in " + duration.toMillis() + "ms";
    return info(action, details, value);
  }

  /**
   * Creates a WriterPath that just returns a value without logging.
   *
   * @param value the value to wrap
   * @param <A> the value type
   * @return a WriterPath with empty log
   */
  public <A> WriterPath<AuditLog, A> pure(A value) {
    return WriterPath.pure(value, AuditLog.monoid());
  }

  /**
   * Creates a WriterPath that only logs without a meaningful value.
   *
   * @param action the action being logged
   * @param details additional details
   * @return a WriterPath that logs only
   */
  public WriterPath<AuditLog, Unit> log(String action, String details) {
    return info(action, details, Unit.INSTANCE);
  }

  private String formatAction(String action) {
    return "[" + workflowName + ":" + currentStep + "] " + action;
  }
}
