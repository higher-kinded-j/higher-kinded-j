// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.service.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.higherkindedj.example.order.error.OrderError;
import org.higherkindedj.example.order.model.Customer;
import org.higherkindedj.example.order.model.DiscountResult;
import org.higherkindedj.example.order.model.value.Money;
import org.higherkindedj.example.order.model.value.Percentage;
import org.higherkindedj.example.order.model.value.PromoCode;
import org.higherkindedj.example.order.service.DiscountService;
import org.higherkindedj.hkt.either.Either;

/** In-memory implementation of DiscountService for testing and examples. */
public class InMemoryDiscountService implements DiscountService {

  private final Map<String, PromoCode> validCodes = new ConcurrentHashMap<>();

  public InMemoryDiscountService() {
    // Pre-populate with sample promo codes
    validCodes.put("SAVE10", new PromoCode("SAVE10", Percentage.of(10)));
    validCodes.put("SAVE20", new PromoCode("SAVE20", Percentage.of(20)));
    validCodes.put("HALFPRICE", new PromoCode("HALFPRICE", Percentage.of(50)));
  }

  public void addPromoCode(PromoCode code) {
    validCodes.put(code.code(), code);
  }

  @Override
  public Either<OrderError, PromoCode> validatePromoCode(String code) {
    var promoCode = validCodes.get(code.toUpperCase());
    if (promoCode == null) {
      return Either.left(OrderError.DiscountError.invalidCode(code));
    }
    return Either.right(promoCode);
  }

  @Override
  public Either<OrderError, DiscountResult> calculateLoyaltyDiscount(
      Customer customer, Money subtotal) {
    var percentage =
        switch (customer.loyaltyTier()) {
          case PLATINUM -> Percentage.of(15);
          case GOLD -> Percentage.of(10);
          case SILVER -> Percentage.of(5);
          case BRONZE -> Percentage.ZERO;
        };

    if (percentage.equals(Percentage.ZERO)) {
      return Either.right(DiscountResult.noDiscount(subtotal));
    }

    var loyaltyCode = new PromoCode("LOYALTY_" + customer.loyaltyTier(), percentage);
    return Either.right(DiscountResult.withPromoCode(loyaltyCode, subtotal));
  }

  @Override
  public Either<OrderError, DiscountResult> applyPromoCode(PromoCode promoCode, Money subtotal) {
    return Either.right(DiscountResult.withPromoCode(promoCode, subtotal));
  }

  @Override
  public Either<OrderError, DiscountResult> selectBestDiscount(DiscountResult... discounts) {
    if (discounts.length == 0) {
      return Either.left(OrderError.DiscountError.invalidCode("No discounts provided"));
    }

    var best = discounts[0];
    for (var discount : discounts) {
      if (discount.discountAmount().amount().compareTo(best.discountAmount().amount()) > 0) {
        best = discount;
      }
    }
    return Either.right(best);
  }
}
