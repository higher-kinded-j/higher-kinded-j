// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.bridge.domain;

import java.math.BigDecimal;
import org.higherkindedj.example.optics.bridge.external.ContactInfo;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * Employee record - a local domain type that references the external {@link ContactInfo}.
 *
 * <p>The Focus DSL will generate navigation paths for all fields. For the {@code contact} field,
 * Focus stops at the ContactInfo boundary - we use spec interface optics to continue navigation
 * into it.
 */
@GenerateFocus
@GenerateLenses
public record Employee(String id, String name, ContactInfo contact, BigDecimal salary) {
  public Employee {
    if (salary.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("Salary cannot be negative");
    }
  }
}
