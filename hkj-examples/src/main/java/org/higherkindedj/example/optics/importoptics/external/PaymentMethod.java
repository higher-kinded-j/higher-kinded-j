// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.importoptics.external;

/**
 * External sealed interface used to demonstrate {@code @ImportOptics} with sum types.
 *
 * <p>These types simulate external library types that cannot be annotated directly.
 */
public sealed interface PaymentMethod permits CreditCard, BankTransfer, DigitalWallet {}
