// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.model;

import java.time.Instant;
import org.higherkindedj.example.order.model.value.Money;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * Confirmation of a successful payment.
 *
 * @param transactionId unique identifier for the payment transaction
 * @param chargedAmount the amount that was charged
 * @param processedAt when the payment was processed
 * @param authorizationCode the authorization code from the payment processor
 */
@GenerateLenses
public record PaymentConfirmation(
    String transactionId, Money chargedAmount, Instant processedAt, String authorizationCode) {}
