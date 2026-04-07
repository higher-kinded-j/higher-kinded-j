// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Interpreters for the Payment Processing example.
 *
 * <p>Each interpreter provides a different execution strategy for the same payment program:
 *
 * <ul>
 *   <li><b>Production</b> — {@code IO} monad with simulated external services
 *   <li><b>Testing</b> — {@code Id} monad with recording/fixed-response interpreters
 *   <li><b>Quote</b> — {@code Id} monad that calculates fees without charging
 *   <li><b>Audit</b> — {@code IO} monad wrapped with audit logging
 * </ul>
 *
 * <p>This demonstrates the core value proposition of effect handlers: the same program can be
 * interpreted in fundamentally different ways without modification.
 *
 * @see org.higherkindedj.example.payment.effect
 */
@NullMarked
package org.higherkindedj.example.payment.interpreter;

import org.jspecify.annotations.NullMarked;
