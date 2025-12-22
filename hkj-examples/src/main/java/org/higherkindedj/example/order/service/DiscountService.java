// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.service;

import org.higherkindedj.example.order.error.OrderError;
import org.higherkindedj.example.order.model.Customer;
import org.higherkindedj.example.order.model.DiscountResult;
import org.higherkindedj.example.order.model.value.Money;
import org.higherkindedj.example.order.model.value.PromoCode;
import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
import org.higherkindedj.hkt.effect.annotation.PathVia;
import org.higherkindedj.hkt.either.Either;

/** Service for discount and promotional code operations. */
@GeneratePathBridge
public interface DiscountService {

  /**
   * Validates a promotional code.
   *
   * @param code the code string to validate
   * @return either an error or the validated promo code
   */
  @PathVia(doc = "Validates a promotional code and returns discount details")
  Either<OrderError, PromoCode> validatePromoCode(String code);

  /**
   * Calculates the loyalty discount for a customer.
   *
   * @param customer the customer
   * @return the loyalty discount percentage (0 if no discount)
   */
  @PathVia(doc = "Calculates loyalty tier discount for customer")
  Either<OrderError, DiscountResult> calculateLoyaltyDiscount(Customer customer, Money subtotal);

  /**
   * Applies a promo code discount to the subtotal.
   *
   * @param promoCode the validated promo code
   * @param subtotal the order subtotal
   * @return the discount result
   */
  @PathVia(doc = "Applies promo code discount to order subtotal")
  Either<OrderError, DiscountResult> applyPromoCode(PromoCode promoCode, Money subtotal);

  /**
   * Combines multiple discounts, taking the best one.
   *
   * @param discounts the discount results to combine
   * @return the best discount result
   */
  @PathVia(doc = "Selects the best discount from multiple options")
  Either<OrderError, DiscountResult> selectBestDiscount(DiscountResult... discounts);
}
