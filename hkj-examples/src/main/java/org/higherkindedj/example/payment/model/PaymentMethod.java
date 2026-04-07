// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.model;

import org.jspecify.annotations.NullMarked;

/**
 * Payment method sealed hierarchy for the payment processing example.
 *
 * <p>Supports credit card, debit card, and digital wallet payments.
 */
@NullMarked
public sealed interface PaymentMethod
    permits PaymentMethod.CreditCard, PaymentMethod.DebitCard, PaymentMethod.DigitalWallet {

  /**
   * Credit card payment.
   *
   * @param lastFourDigits last four digits of the card number
   * @param network card network (e.g. "VISA", "MASTERCARD")
   */
  record CreditCard(String lastFourDigits, String network) implements PaymentMethod {}

  /**
   * Debit card payment.
   *
   * @param lastFourDigits last four digits of the card number
   * @param network card network
   */
  record DebitCard(String lastFourDigits, String network) implements PaymentMethod {}

  /**
   * Digital wallet payment.
   *
   * @param provider wallet provider (e.g. "APPLE_PAY", "GOOGLE_PAY")
   * @param walletId wallet identifier or token
   */
  record DigitalWallet(String provider, String walletId) implements PaymentMethod {}
}
