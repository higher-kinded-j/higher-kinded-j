// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.clientexample;

/**
 * The typed error carried back across the HTTP boundary by {@link UserClientApi}.
 *
 * <p>The remote service returns a failure as {@code {"success":false,"error":<error>}}; the default
 * decoder binds the {@code error} node to this type. The server's {@code UserNotFoundError} carries
 * the missing id, so {@code userId} is populated on a 404; {@code message} is filled when the
 * service includes one. A concrete error type binds with Jackson directly. A sealed {@code
 * DomainError} hierarchy would instead need {@code @JsonTypeInfo}/{@code @JsonSubTypes} so the
 * decoder could reconstruct the exact subtype.
 *
 * @param userId the id that could not be found (present on a not-found error)
 * @param message a human-readable message, when the service supplies one
 */
public record ApiError(String userId, String message) {}
