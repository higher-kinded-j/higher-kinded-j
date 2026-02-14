// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.model;

import org.higherkindedj.optics.annotations.GeneratePrisms;

/**
 * Payment method sealed hierarchy.
 *
 * <p>Using a sealed interface enables exhaustive pattern matching and generates prisms for safe,
 * type-aware access to specific payment types.
 */
@GeneratePrisms
public sealed interface PaymentMethod
    permits PaymentMethod.CreditCard,
        PaymentMethod.DebitCard,
        PaymentMethod.BankTransfer,
        PaymentMethod.DigitalWallet {

  /**
   * Credit card payment details.
   *
   * @param cardNumber masked card number
   * @param expiryMonth expiry month (01-12)
   * @param expiryYear expiry year (YYYY)
   * @param cvv security code (masked in logs)
   */
  record CreditCard(String cardNumber, String expiryMonth, String expiryYear, String cvv)
      implements PaymentMethod {}

  /**
   * Debit card payment details.
   *
   * @param cardNumber masked card number
   * @param expiryMonth expiry month (01-12)
   * @param expiryYear expiry year (YYYY)
   */
  record DebitCard(String cardNumber, String expiryMonth, String expiryYear)
      implements PaymentMethod {}

  /**
   * Bank transfer payment details.
   *
   * @param accountNumber bank account number
   * @param sortCode bank sort code
   */
  record BankTransfer(String accountNumber, String sortCode) implements PaymentMethod {}

  /**
   * Digital wallet payment details.
   *
   * @param walletId wallet identifier or token
   * @param provider wallet provider (e.g., "PAYPAL", "APPLE_PAY", "GOOGLE_PAY")
   */
  record DigitalWallet(String walletId, String provider) implements PaymentMethod {}
}
