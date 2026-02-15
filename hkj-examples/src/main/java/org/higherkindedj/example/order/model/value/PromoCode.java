// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.model.value;

import java.util.Objects;

/**
 * A validated promotional code.
 *
 * @param code the code string
 * @param discount the discount percentage this code provides
 */
public record PromoCode(String code, Percentage discount) {

  public PromoCode {
    Objects.requireNonNull(code, "Promo code cannot be null");
    Objects.requireNonNull(discount, "Discount cannot be null");
    if (code.isBlank()) {
      throw new IllegalArgumentException("Promo code cannot be blank");
    }
  }

  @Override
  public String toString() {
    return code + " (" + discount + " off)";
  }
}
