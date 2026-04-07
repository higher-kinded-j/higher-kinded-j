// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.model;

import java.math.BigDecimal;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

/**
 * Monetary amount with currency.
 *
 * @param amount the numeric amount
 * @param currency the ISO 4217 currency code (e.g. "GBP", "USD")
 */
@NullMarked
public record Money(BigDecimal amount, String currency) {

  public Money {
    Objects.requireNonNull(amount, "amount cannot be null");
    Objects.requireNonNull(currency, "currency cannot be null");
  }

  /**
   * Creates a Money value in GBP.
   *
   * @param amount the numeric amount
   * @return a Money instance in GBP
   */
  public static Money gbp(BigDecimal amount) {
    return new Money(amount, "GBP");
  }

  /**
   * Creates a Money value in GBP from a string.
   *
   * @param amount the numeric amount as a string
   * @return a Money instance in GBP
   */
  public static Money gbp(String amount) {
    return gbp(new BigDecimal(amount));
  }

  /**
   * Checks whether this amount is less than another.
   *
   * @param other the amount to compare against
   * @return true if this amount is strictly less than the other
   */
  public boolean lessThan(Money other) {
    return amount.compareTo(other.amount) < 0;
  }

  @Override
  public String toString() {
    return currency + " " + amount.toPlainString();
  }
}
