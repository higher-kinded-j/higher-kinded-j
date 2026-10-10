// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.importoptics.external;

/** A card, held by the payment gateway as a token. */
public record CardInstrument(String token) implements PaymentInstrument {}
