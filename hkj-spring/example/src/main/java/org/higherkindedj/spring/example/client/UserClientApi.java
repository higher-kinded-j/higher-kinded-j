// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.client;

import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.spring.client.HkjHttpClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Declarative HTTP client for a remote users service that returns Effect Paths.
 *
 * <p>{@code @HkjHttpClient} drives generation of three siblings in this package:
 *
 * <ul>
 *   <li>{@code UserClientApiHttpExchange} — the native {@code @HttpExchange} interface Spring
 *       proxies
 *   <li>{@code UserClientApiClient} — the implementation that folds responses into Effect Paths
 *   <li>{@code UserClientApiClientConfiguration} — the {@code @ImportHttpServices} + bean wiring
 * </ul>
 *
 * <p>Base URL and timeouts come from {@code spring.http.serviceclient.userClientApi.*} (see {@code
 * application.yml}). A {@code Left}/error response decodes the {@code {"success":false,"error":…}}
 * envelope into {@link ApiError}, preserving the typed error channel across services.
 */
@HttpExchange("/users")
@HkjHttpClient
public interface UserClientApi {

  /** Fetches a user by id; a 4xx/5xx response becomes {@code Left(ApiError)}. */
  @GetExchange("/{id}")
  EitherPath<ApiError, UserDto> getUser(@PathVariable String id);

  /**
   * Creates a user, deferred on a virtual thread so callers can layer {@code withRetry}/{@code
   * withCircuitBreaker}/{@code timeout} on the result.
   */
  @PostExchange
  VTaskPath<Either<ApiError, UserDto>> create(@RequestBody UserDto body);
}
