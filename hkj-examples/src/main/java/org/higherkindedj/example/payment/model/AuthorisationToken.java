// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.model;

import java.util.Objects;
import org.jspecify.annotations.NullMarked;

/**
 * Token received from a payment gateway after successful authorisation.
 *
 * @param token the authorisation token value
 * @param amount the authorised amount
 */
@NullMarked
public record AuthorisationToken(String token, Money amount) {

  public AuthorisationToken {
    Objects.requireNonNull(token, "token cannot be null");
    Objects.requireNonNull(amount, "amount cannot be null");
  }
}
