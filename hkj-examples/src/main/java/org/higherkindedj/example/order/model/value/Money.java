// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.model.value;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * Currency-aware money value object.
 *
 * <p>Provides arithmetic operations that preserve currency safety and proper decimal handling.
 *
 * @param amount the monetary amount
 * @param currency the currency
 */
@GenerateLenses
public record Money(BigDecimal amount, Currency currency) {

  /** Zero pounds sterling. */
  public static final Money ZERO_GBP = new Money(BigDecimal.ZERO, Currency.getInstance("GBP"));

  public Money {
    Objects.requireNonNull(amount, "amount cannot be null");
    Objects.requireNonNull(currency, "currency cannot be null");
  }

  /**
   * Creates a Money instance for the given amount in GBP.
   *
   * @param amount the amount
   * @return a Money in GBP
   */
  public static Money gbp(BigDecimal amount) {
    return new Money(amount.setScale(2, RoundingMode.HALF_UP), Currency.getInstance("GBP"));
  }

  /**
   * Creates a Money instance for the given amount in GBP.
   *
   * @param amount the amount as a string
   * @return a Money in GBP
   */
  public static Money gbp(String amount) {
    return gbp(new BigDecimal(amount));
  }

  /**
   * Adds another Money value, requiring matching currencies.
   *
   * @param other the amount to add
   * @return the sum
   * @throws IllegalArgumentException if currencies do not match
   */
  public Money add(Money other) {
    requireSameCurrency(other);
    return new Money(amount.add(other.amount).setScale(2, RoundingMode.HALF_UP), currency);
  }

  /**
   * Subtracts another Money value, requiring matching currencies.
   *
   * @param other the amount to subtract
   * @return the difference
   * @throws IllegalArgumentException if currencies do not match
   */
  public Money subtract(Money other) {
    requireSameCurrency(other);
    return new Money(amount.subtract(other.amount).setScale(2, RoundingMode.HALF_UP), currency);
  }

  /**
   * Multiplies by a quantity.
   *
   * @param quantity the multiplier
   * @return the product
   */
  public Money multiply(int quantity) {
    return new Money(
        amount.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP), currency);
  }

  /**
   * Applies a percentage discount.
   *
   * @param discount the discount percentage
   * @return the discounted amount
   */
  public Money applyDiscount(Percentage discount) {
    var multiplier = BigDecimal.ONE.subtract(discount.asFraction());
    return new Money(amount.multiply(multiplier).setScale(2, RoundingMode.HALF_UP), currency);
  }

  /**
   * Checks if this amount is positive.
   *
   * @return true if amount is greater than zero
   */
  public boolean isPositive() {
    return amount.compareTo(BigDecimal.ZERO) > 0;
  }

  /**
   * Checks if this amount is zero or negative.
   *
   * @return true if amount is zero or less
   */
  public boolean isZeroOrNegative() {
    return amount.compareTo(BigDecimal.ZERO) <= 0;
  }

  private void requireSameCurrency(Money other) {
    if (!currency.equals(other.currency)) {
      throw new IllegalArgumentException(
          "Currency mismatch: " + currency + " vs " + other.currency);
    }
  }

  @Override
  public String toString() {
    return currency.getSymbol() + amount.setScale(2, RoundingMode.HALF_UP);
  }
}
