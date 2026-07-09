// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generates the error-envelope companion for a sealed domain-error hierarchy (issue #610): typed
 * per-variant factories, a fluent context builder, and a context wither - so each variant declares
 * only its domain-specific components plus one {@code ErrorEnvelope<C>} component.
 *
 * <p>The contract: annotate a sealed interface whose permitted variants are all records, each
 * carrying exactly one {@code ErrorEnvelope<C>} component. The context type {@code C} is discovered
 * structurally from that component's type argument - never from a class-literal attribute - and
 * every variant must agree on {@code C}. The context itself is records-as-schema: a record whose
 * nullable components form the typed context, with an all-absent instance as the default.
 *
 * <p><b>The context record's components must be nullable.</b> The companion holds an all-absent
 * context (every component {@code null}) as a {@code static} field, so a context record whose
 * compact constructor rejects {@code null} (for example {@code Objects.requireNonNull} on a
 * component) fails when the companion class is initialised, disabling every factory. Primitive
 * components are rejected at compile time for the same reason, but a null-rejecting constructor
 * cannot be detected by the processor; keep the context record a plain nullable data carrier and
 * annotate its components {@code @Nullable} under {@code @NullMarked}.
 *
 * <pre>{@code
 * record OrderErrorContext(OrderId orderId, TraceId traceId) {}
 *
 * @GenerateErrorEnvelope
 * public sealed interface OrderError {
 *   ErrorEnvelope<OrderErrorContext> envelope();
 *
 *   record OutOfStock(List<ProductId> products, ErrorEnvelope<OrderErrorContext> envelope)
 *       implements OrderError {}
 *
 *   record PaymentDeclined(CardRef card, ErrorEnvelope<OrderErrorContext> envelope)
 *       implements OrderError {}
 * }
 *
 * OrderError error =
 *     OrderErrors.editContext(
 *         OrderErrors.outOfStock(products), ctx -> ctx.orderId(orderId).traceId(traceId));
 * }</pre>
 *
 * <p>For {@code FooError} the generated companion is {@code FooErrors}: per-variant factories
 * taking the domain components ({@code code} is the UPPER_SNAKE variant name, {@code message} its
 * humanised form, the timestamp read from a {@code TimeSource} - an overload accepts one
 * explicitly, the convenience uses {@code TimeSource.system()}), a {@code context()} builder over
 * {@code C}'s components, and {@code editContext(error, edit)} rebuilding the concrete variant via
 * an exhaustive switch over the permitted variants.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface GenerateErrorEnvelope {}
