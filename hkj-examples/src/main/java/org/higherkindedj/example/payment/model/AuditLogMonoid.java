// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.model;

import org.higherkindedj.hkt.Monoid;
import org.jspecify.annotations.NullMarked;

/**
 * Monoid instance for {@link AuditLog}.
 *
 * <p>Provides the identity element ({@link AuditLog#EMPTY}) and the associative combine operation
 * ({@link AuditLog#append(AuditLog)}). Required by {@code WriterTMonad} for accumulating audit
 * entries during interpretation.
 */
@NullMarked
public enum AuditLogMonoid implements Monoid<AuditLog> {
  INSTANCE;

  @Override
  public AuditLog empty() {
    return AuditLog.EMPTY;
  }

  @Override
  public AuditLog combine(AuditLog a, AuditLog b) {
    return a.append(b);
  }
}
