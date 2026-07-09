// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.market.error;

import org.higherkindedj.example.market.model.value.Symbol;
import org.jspecify.annotations.Nullable;

/**
 * Typed context schema for {@link MarketError} envelopes (records-as-schema, issue #610).
 *
 * <p>Each component is one piece of diagnostic context a market error may carry; absent pieces are
 * {@code null}. Consumers (logs, metrics, alerts) read typed fields rather than parsing them out of
 * the message string.
 *
 * <p>Build instances through the generated {@link MarketErrors#context()} builder, or edit an
 * error's context in place with {@link MarketError#editContext}.
 *
 * @param symbol the instrument involved, if any
 * @param exchange the exchange involved, if any
 */
public record MarketErrorContext(@Nullable Symbol symbol, @Nullable String exchange) {}
