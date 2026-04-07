// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Payment processing service using Free monad effect handlers.
 *
 * <p>The {@link org.higherkindedj.example.payment.service.PaymentService} class demonstrates how to
 * write business logic as a pure program that describes what to do without specifying how. The same
 * program is then interpreted in four different ways: production, testing, quoting, and auditing.
 *
 * @see org.higherkindedj.example.payment.effect
 * @see org.higherkindedj.example.payment.interpreter
 */
@NullMarked
package org.higherkindedj.example.payment.service;

import org.jspecify.annotations.NullMarked;
