// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

/**
 * Strategy for resolving the HTTP status code carried by an error value emitted from an Effect Path
 * handler.
 *
 * <p>The auto-configuration registers a {@link DefaultErrorStatusCodeStrategy} bean by default,
 * combining the {@code hkj.web.error-status-mappings} property map with the built-in heuristics in
 * {@link ErrorStatusCodeMapper}. Adopters who need finer control — for example, mapping a single
 * error class to different status codes based on its fields ({@code MfaThrottledError.retryAfter()
 * ≥ N → 503}) — can replace it by declaring their own bean of this type:
 *
 * <pre>{@code
 * @Bean
 * ErrorStatusCodeStrategy errorStatusCodeStrategy() {
 *     return (error, defaultStatus) -> switch (error) {
 *         case MfaThrottledError t when t.retryAfter() > 60 -> 503;
 *         case MfaThrottledError ignored -> 429;
 *         default -> ErrorStatusCodeMapper.determineStatusCode(error, defaultStatus);
 *     };
 * }
 * }</pre>
 *
 * <p>Implementations are invoked once per error response on the request thread (or the async
 * completion thread for {@code CompletableFuturePath} / {@code VTaskPath}), so they should be
 * thread-safe and side-effect-free.
 */
@FunctionalInterface
public interface ErrorStatusCodeStrategy {

  /**
   * Resolves the HTTP status code for an error value.
   *
   * @param error the error object emitted by the handler (never {@code null})
   * @param defaultStatus the configured default status to fall back to
   * @return the HTTP status code to write on the response
   */
  int statusCodeFor(Object error, int defaultStatus);
}
