// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.eitherf;

import org.higherkindedj.example.book.foundations.NoticeOp;
import org.higherkindedj.example.book.foundations.OrderOp;
import org.higherkindedj.hkt.effect.annotation.ComposeEffects;
import org.jspecify.annotations.NullMarked;

// ANCHOR: compose_effects
/**
 * The order service's two instruction sets as one. {@code @ComposeEffects} generates {@code
 * OrderEffectsSupport}, with an {@code Inject} for each set, the pair's {@code Functor} and a
 * {@code BoundSet} to hold each set's constructors.
 */
@NullMarked
@ComposeEffects
public record OrderEffects(Class<OrderOp<?>> orders, Class<NoticeOp<?>> notices) {}
// ANCHOR_END: compose_effects
