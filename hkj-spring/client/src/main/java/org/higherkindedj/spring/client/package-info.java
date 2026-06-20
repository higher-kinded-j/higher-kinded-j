// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Client-side HTTP integration for Higher-Kinded-J.
 *
 * <p>This package is the client-side inverse of the server-side {@code
 * org.higherkindedj.spring.web.returnvalue} handlers. Where the server maps an {@code EitherPath}
 * returned from a controller into an HTTP response (a typed error becoming a 4xx/5xx body), this
 * package maps an HTTP response back into an {@code EitherPath}/{@code VTaskPath}/{@code
 * MaybePath}, decoding a typed error from the response so the typed error channel is preserved
 * end-to-end across service-to-service calls.
 *
 * <p>{@link org.higherkindedj.spring.client.HkjClientExchange} provides the runtime translators;
 * {@link org.higherkindedj.spring.client.ResponseErrorDecoder} is the pluggable status/body → error
 * mapping, with {@link org.higherkindedj.spring.client.JsonResponseErrorDecoder} as the default.
 */
@NullMarked
package org.higherkindedj.spring.client;

import org.jspecify.annotations.NullMarked;
