// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.error;

import java.time.Duration;
import java.util.List;
import org.higherkindedj.example.order.model.value.CustomerId;
import org.higherkindedj.example.order.model.value.ProductId;
import org.jspecify.annotations.Nullable;

/**
 * Typed context schema for {@link OrderError} envelopes (records-as-schema, issue #610).
 *
 * <p>Each component is one piece of diagnostic context an order error may carry; absent pieces are
 * {@code null}. Consumers (logs, metrics, HTTP problem details) read typed fields rather than
 * fishing values out of a {@code Map<String, Object>} by string key, which is what this schema
 * replaced: the components below correspond one-to-one to the keys the hierarchy's old context maps
 * actually carried ({@code customerId}, {@code productId}, {@code productIds}, {@code available},
 * {@code requested}, {@code promoCode}, {@code reason}, {@code operation}, {@code timeoutMs},
 * {@code service} and {@code cause}/{@code exceptionType}).
 *
 * <p>Build instances through the generated {@link OrderErrors#context()} builder, or edit an
 * error's context in place with {@link OrderError#editContext}.
 *
 * @param customerId the customer involved, if any
 * @param productId the single product involved, if any
 * @param productIds the products involved, if any
 * @param availableQuantity the quantity actually available, for stock shortfalls
 * @param requestedQuantity the quantity requested, for stock shortfalls
 * @param promoCode the promo code involved, if any
 * @param reason the underlying reason reported by a collaborator, if any
 * @param operation the operation that failed, for timeouts
 * @param timeout the timeout that elapsed, if one was configured
 * @param service the unavailable service, for circuit-breaker trips
 * @param exceptionType the simple name of the underlying exception, if any
 */
public record OrderErrorContext(
    @Nullable CustomerId customerId,
    @Nullable ProductId productId,
    @Nullable List<ProductId> productIds,
    @Nullable Integer availableQuantity,
    @Nullable Integer requestedQuantity,
    @Nullable String promoCode,
    @Nullable String reason,
    @Nullable String operation,
    @Nullable Duration timeout,
    @Nullable String service,
    @Nullable String exceptionType) {}
