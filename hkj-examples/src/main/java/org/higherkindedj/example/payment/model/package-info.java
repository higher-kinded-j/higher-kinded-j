// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Domain models for the Payment Processing example.
 *
 * <p>This package contains immutable record types representing the payment processing domain. These
 * models are used by the effect algebras and interpreters to demonstrate how the same program can
 * be interpreted in multiple ways: production, testing, auditing, and replay.
 *
 * <h2>Value Types</h2>
 *
 * <ul>
 *   <li>{@link org.higherkindedj.example.payment.model.Money} - Monetary amount with currency
 *   <li>{@link org.higherkindedj.example.payment.model.CustomerId} - Type-safe customer identifier
 *   <li>{@link org.higherkindedj.example.payment.model.TransactionId} - Type-safe transaction
 *       identifier
 * </ul>
 *
 * <h2>Domain Types</h2>
 *
 * <ul>
 *   <li>{@link org.higherkindedj.example.payment.model.Customer} - Customer with account and backup
 *       payment method
 *   <li>{@link org.higherkindedj.example.payment.model.PaymentMethod} - Sealed hierarchy of payment
 *       methods
 *   <li>{@link org.higherkindedj.example.payment.model.RiskScore} - Fraud risk assessment score
 *   <li>{@link org.higherkindedj.example.payment.model.ChargeResult} - Result of a payment charge
 *       attempt
 *   <li>{@link org.higherkindedj.example.payment.model.LedgerEntry} - Accounting ledger record
 *   <li>{@link org.higherkindedj.example.payment.model.PaymentResult} - Final payment outcome
 * </ul>
 */
@NullMarked
package org.higherkindedj.example.payment.model;

import org.jspecify.annotations.NullMarked;
