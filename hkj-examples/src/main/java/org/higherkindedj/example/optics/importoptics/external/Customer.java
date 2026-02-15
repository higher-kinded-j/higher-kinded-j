// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.importoptics.external;

/**
 * External record types used to demonstrate {@code @ImportOptics} with records.
 *
 * <p>These types simulate external library types that cannot be annotated directly.
 */
public record Customer(String id, String name, String email, int loyaltyPoints) {}
