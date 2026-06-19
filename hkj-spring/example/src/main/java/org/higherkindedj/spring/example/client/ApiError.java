// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.client;

/**
 * The typed error carried across the HTTP boundary by {@link UserClientApi}.
 *
 * <p>A concrete error type binds directly with Jackson — no polymorphic type information needed. A
 * sealed {@code DomainError} hierarchy would instead need
 * {@code @JsonTypeInfo}/{@code @JsonSubTypes} so the decoder can pick the subtype.
 *
 * @param code a machine-readable error code (e.g. {@code "NOT_FOUND"})
 * @param message a human-readable message
 */
public record ApiError(String code, String message) {}
