// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.model;

import java.util.Optional;
import org.higherkindedj.example.order.model.value.Money;
import org.higherkindedj.example.order.model.value.Percentage;
import org.higherkindedj.example.order.model.value.PromoCode;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * Result of applying discounts to an order.
 *
 * @param appliedCode the promo code that was applied, if any
 * @param discountPercentage the total discount percentage applied
 * @param discountAmount the monetary discount amount
 * @param finalTotal the final total after discounts
 */
@GenerateLenses
public record DiscountResult(
    Optional<PromoCode> appliedCode,
    Percentage discountPercentage,
    Money discountAmount,
    Money finalTotal) {
  /**
   * Creates a result with no discount applied.
   *
   * @param subtotal the original subtotal
   * @return a DiscountResult with zero discount
   */
  public static DiscountResult noDiscount(Money subtotal) {
    return new DiscountResult(Optional.empty(), Percentage.ZERO, Money.ZERO_GBP, subtotal);
  }

  /**
   * Creates a result with a promo code discount.
   *
   * @param promoCode the applied promo code
   * @param subtotal the original subtotal
   * @return a DiscountResult with the discount applied
   */
  public static DiscountResult withPromoCode(PromoCode promoCode, Money subtotal) {
    var discountAmount = subtotal.subtract(subtotal.applyDiscount(promoCode.discount()));
    var finalTotal = subtotal.applyDiscount(promoCode.discount());
    return new DiscountResult(
        Optional.of(promoCode), promoCode.discount(), discountAmount, finalTotal);
  }
}
