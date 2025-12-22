// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.higherkindedj.hkt.Monoid;

/**
 * Immutable audit log that accumulates entries throughout the workflow.
 *
 * <p>This class is designed to work with {@code WriterPath} through its {@link Monoid} instance,
 * enabling automatic accumulation of audit entries.
 *
 * @param entries the list of audit entries
 */
public record AuditLog(List<AuditEntry> entries) {

  /** An empty audit log. */
  public static final AuditLog EMPTY = new AuditLog(List.of());

  /**
   * Creates an audit log with a single entry.
   *
   * @param entry the entry to include
   * @return a new audit log
   */
  public static AuditLog of(AuditEntry entry) {
    return new AuditLog(List.of(entry));
  }

  /**
   * Creates an audit log with a single entry.
   *
   * @param action the action being logged
   * @param details additional details
   * @return a new audit log
   */
  public static AuditLog of(String action, String details) {
    return of(new AuditEntry(action, details, Instant.now()));
  }

  /**
   * Appends another audit log to this one.
   *
   * @param other the audit log to append
   * @return a new audit log with combined entries
   */
  public AuditLog append(AuditLog other) {
    var combined = new ArrayList<>(entries);
    combined.addAll(other.entries);
    return new AuditLog(List.copyOf(combined));
  }

  /**
   * Appends a single entry to this audit log.
   *
   * @param entry the entry to append
   * @return a new audit log with the entry added
   */
  public AuditLog append(AuditEntry entry) {
    var combined = new ArrayList<>(entries);
    combined.add(entry);
    return new AuditLog(List.copyOf(combined));
  }

  /**
   * Returns a Monoid instance for AuditLog.
   *
   * <p>This enables AuditLog to be used with WriterPath for automatic accumulation.
   *
   * @return the AuditLog monoid
   */
  public static Monoid<AuditLog> monoid() {
    return AuditLogMonoid.INSTANCE;
  }

  /**
   * A single audit entry.
   *
   * @param action the action that occurred
   * @param details additional details about the action
   * @param timestamp when the action occurred
   */
  public record AuditEntry(String action, String details, Instant timestamp) {
    /**
     * Creates an entry with the current timestamp.
     *
     * @param action the action
     * @param details the details
     * @return a new audit entry
     */
    public static AuditEntry now(String action, String details) {
      return new AuditEntry(action, details, Instant.now());
    }
  }

  /** Monoid instance for AuditLog. */
  private enum AuditLogMonoid implements Monoid<AuditLog> {
    INSTANCE;

    @Override
    public AuditLog empty() {
      return EMPTY;
    }

    @Override
    public AuditLog combine(AuditLog a, AuditLog b) {
      return a.append(b);
    }
  }
}
