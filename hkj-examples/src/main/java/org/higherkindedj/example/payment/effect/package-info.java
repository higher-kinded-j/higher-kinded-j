// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Effect algebras for the Payment Processing example.
 *
 * <p>Each sealed interface describes a set of operations that a program can request. The {@link
 * org.higherkindedj.hkt.effect.annotation.EffectAlgebra @EffectAlgebra} annotation triggers code
 * generation of HKT boilerplate (Kind, KindHelper, Functor, Ops, Interpreter).
 *
 * <h2>Effect Algebras</h2>
 *
 * <ul>
 *   <li>{@link org.higherkindedj.example.payment.effect.PaymentGatewayOp} - Authorise, Charge,
 *       Refund
 *   <li>{@link org.higherkindedj.example.payment.effect.FraudCheckOp} - CheckTransaction
 *   <li>{@link org.higherkindedj.example.payment.effect.LedgerOp} - RecordEntry, GetBalance
 *   <li>{@link org.higherkindedj.example.payment.effect.NotificationOp} - SendReceipt,
 *       AlertFraudTeam
 * </ul>
 *
 * <h2>Composition</h2>
 *
 * <p>{@link org.higherkindedj.example.payment.effect.PaymentEffects} composes all four algebras via
 * {@link org.higherkindedj.hkt.effect.annotation.ComposeEffects @ComposeEffects}, generating a
 * {@code PaymentEffectsSupport} class with {@code Inject} instances and a {@code BoundSet}.
 *
 * @see org.higherkindedj.hkt.effect.annotation.EffectAlgebra
 * @see org.higherkindedj.hkt.effect.annotation.ComposeEffects
 */
@NullMarked
package org.higherkindedj.example.payment.effect;

import org.jspecify.annotations.NullMarked;
