// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.effect;

import org.higherkindedj.hkt.effect.annotation.ComposeEffects;
import org.jspecify.annotations.NullMarked;

/**
 * Composed effect set for payment processing.
 *
 * <p>This record triggers annotation processing via {@link ComposeEffects @ComposeEffects},
 * generating HKT boilerplate (Kind, Functor, Ops, and Interpreter base classes) for each
 * constituent effect algebra. The generated code is consumed by {@link PaymentEffectsWiring} and
 * the interpreter classes.
 *
 * <p>This record is primarily a marker for the annotation processor; application code should use
 * {@link PaymentEffectsWiring} as the main integration point for inject instances, the composed
 * functor, and the {@code BoundSet}.
 *
 * @param gateway payment gateway operations
 * @param fraud fraud detection operations
 * @param ledger accounting ledger operations
 * @param notification notification operations
 */
@NullMarked
@ComposeEffects
public record PaymentEffects(
    Class<PaymentGatewayOp<?>> gateway,
    Class<FraudCheckOp<?>> fraud,
    Class<LedgerOp<?>> ledger,
    Class<NotificationOp<?>> notification) {}
