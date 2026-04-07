// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.model;

import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Customer details for payment processing.
 *
 * @param id the customer identifier
 * @param name the customer's name
 * @param email the customer's email address
 * @param accountId the financial account identifier
 * @param backupMethod optional backup payment method for fallback
 */
@NullMarked
public record Customer(
    CustomerId id,
    String name,
    String email,
    CustomerId accountId,
    @Nullable PaymentMethod backupMethod) {

  public Customer {
    Objects.requireNonNull(id, "id cannot be null");
    Objects.requireNonNull(name, "name cannot be null");
    Objects.requireNonNull(email, "email cannot be null");
    Objects.requireNonNull(accountId, "accountId cannot be null");
  }

  /**
   * Whether the customer has a backup payment method.
   *
   * @return true if a backup method is configured
   */
  public boolean hasBackupMethod() {
    return backupMethod != null;
  }
}
