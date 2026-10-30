// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations;

import java.util.Objects;
import java.util.UUID;

// ANCHOR: audit_entry
/** One thing that happened to an order, kept for the audit log. */
public record AuditEntry(UUID orderId, String event) {

  public AuditEntry {
    Objects.requireNonNull(orderId, "orderId");
    Objects.requireNonNull(event, "event");
  }
}
// ANCHOR_END: audit_entry
