// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.bridge.domain;

import java.math.BigDecimal;
import org.higherkindedj.example.optics.bridge.external.ContactDetails;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * OfficeWorker record - a local domain type that references the external {@link ContactDetails}.
 *
 * <p>The Focus DSL will generate navigation paths for all fields. For the {@code contact} field,
 * Focus stops at the ContactDetails boundary - we use spec interface optics to continue navigation
 * into it.
 */
@GenerateFocus
@GenerateLenses
public record OfficeWorker(String id, String name, ContactDetails contact, BigDecimal salary) {
  public OfficeWorker {
    if (salary.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("Salary cannot be negative");
    }
  }
}
