// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Payment Processing example demonstrating algebraic effect handlers.
 *
 * <p>This example shows how a single payment processing program can be interpreted in four
 * different ways using Free monad effect handlers:
 *
 * <ol>
 *   <li><b>Production</b> — {@code IO} monad with simulated external services
 *   <li><b>Testing</b> — {@code Id} monad with recording interpreters (no mocks)
 *   <li><b>Quote</b> — {@code Id} monad that estimates fees without side effects
 *   <li><b>High Risk</b> — {@code Id} monad demonstrating risk-based decline
 * </ol>
 *
 * <h2>Package Structure</h2>
 *
 * <ul>
 *   <li>{@code model/} — domain model records (Customer, Money, PaymentResult, etc.)
 *   <li>{@code effect/} — {@code @EffectAlgebra} sealed interfaces (PaymentGatewayOp, etc.)
 *   <li>{@code interpreter/} — concrete interpreters for production, testing, quoting
 *   <li>{@code service/} — business logic as a Free monad program
 * </ul>
 *
 * @see org.higherkindedj.example.payment.PaymentProcessingExample
 * @see org.higherkindedj.example.payment.service.PaymentService
 */
@NullMarked
package org.higherkindedj.example.payment;

import org.jspecify.annotations.NullMarked;
