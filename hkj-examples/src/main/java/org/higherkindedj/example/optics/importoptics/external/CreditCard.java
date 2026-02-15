// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.importoptics.external;

/** A credit card payment method. */
public record CreditCard(String cardNumber, String expiryDate) implements PaymentMethod {}
