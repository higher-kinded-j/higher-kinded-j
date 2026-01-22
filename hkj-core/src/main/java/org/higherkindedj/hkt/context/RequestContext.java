// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.context;

import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pre-defined {@link ScopedValue} instances for common HTTP request context values.
 *
 * <p>{@code RequestContext} provides a standard set of scoped values for request tracing,
 * correlation, localisation, multi-tenancy, and timing. These values propagate automatically to
 * child virtual threads forked within the same scope.
 *
 * <p><b>Usage Example:</b>
 *
 * <pre>{@code
 * // At the request handler entry point
 * public Response handleRequest(HttpRequest request) {
 *     return ScopedValue
 *         .where(RequestContext.TRACE_ID, request.header("X-Trace-ID").orElse(generateTraceId()))
 *         .where(RequestContext.LOCALE, parseLocale(request))
 *         .where(RequestContext.REQUEST_TIME, Instant.now())
 *         .call(() -> processRequest(request));
 * }
 *
 * // Deep in the call stack
 * public void logOperation(String operation) {
 *     String traceId = RequestContext.TRACE_ID.get();
 *     logger.info("[{}] {}", traceId, operation);
 * }
 * }</pre>
 *
 * <p><b>Design Note:</b> This is a utility class with static {@link ScopedValue} fields rather than
 * a bundled record. This design provides flexibility (bind only what you need), granularity
 * (different components can read different values), and natural composition with {@link
 * ScopedValue#where}.
 *
 * @see Context
 * @see SecurityContext
 * @see ScopedValue
 */
public final class RequestContext {

  private RequestContext() {
    // Utility class - no instantiation
  }

  /**
   * Unique identifier for distributed tracing.
   *
   * <p>Typically generated at the edge (API gateway, load balancer) and propagated through all
   * downstream services via HTTP headers (e.g., {@code X-Trace-ID}).
   */
  public static final ScopedValue<String> TRACE_ID = ScopedValue.newInstance();

  /**
   * Correlation ID linking related requests.
   *
   * <p>Used to group requests that are part of the same user action or business transaction, even
   * across separate trace trees. Often propagated via {@code X-Correlation-ID} header.
   */
  public static final ScopedValue<String> CORRELATION_ID = ScopedValue.newInstance();

  /**
   * User's preferred locale for response formatting.
   *
   * <p>Influences date formats, number formats, currency symbols, and message translations.
   * Typically extracted from the {@code Accept-Language} header.
   */
  public static final ScopedValue<Locale> LOCALE = ScopedValue.newInstance();

  /**
   * Tenant identifier for multi-tenant applications.
   *
   * <p>Determines which tenant's data, configuration, and resources to use. May be extracted from
   * headers, subdomains, or path prefixes.
   */
  public static final ScopedValue<String> TENANT_ID = ScopedValue.newInstance();

  /**
   * Timestamp when the request was received.
   *
   * <p>Useful for timeout calculations, latency measurement, and audit logging. Should be set at
   * the earliest point in request processing.
   */
  public static final ScopedValue<Instant> REQUEST_TIME = ScopedValue.newInstance();

  /**
   * Request deadline for timeout propagation.
   *
   * <p>Operations should check this and fail fast if the deadline has passed. Enables consistent
   * timeout behaviour across the entire request processing chain.
   */
  public static final ScopedValue<Instant> DEADLINE = ScopedValue.newInstance();

  // ===== HELPER METHODS =====

  /**
   * Generates a compact, URL-safe trace ID.
   *
   * <p>The generated ID is 16 characters, using Base64 URL-safe encoding without padding. Suitable
   * for use in HTTP headers and logging.
   *
   * @return A new unique trace ID.
   */
  public static String generateTraceId() {
    byte[] bytes = new byte[12];
    ThreadLocalRandom.current().nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * Generates a trace ID with a timestamp prefix.
   *
   * <p>Format: {@code {timestamp-hex}-{random-hex}}. Enables rough time-based sorting of traces.
   *
   * @return A new timestamped trace ID.
   */
  public static String generateTimestampedTraceId() {
    long timestamp = System.currentTimeMillis();
    long random = ThreadLocalRandom.current().nextLong();
    return String.format("%012x-%08x", timestamp, random & 0xFFFFFFFFL);
  }

  /**
   * Returns the current trace ID, or a default value if not bound.
   *
   * @param defaultValue The value to return if TRACE_ID is not bound.
   * @return The current trace ID or the default.
   */
  public static String getTraceIdOrDefault(String defaultValue) {
    return TRACE_ID.isBound() ? TRACE_ID.get() : defaultValue;
  }

  /**
   * Returns the current locale, or a default if not bound.
   *
   * @param defaultLocale The locale to return if LOCALE is not bound.
   * @return The current locale or the default.
   */
  public static Locale getLocaleOrDefault(Locale defaultLocale) {
    return LOCALE.isBound() ? LOCALE.get() : defaultLocale;
  }

  /**
   * Returns the current tenant ID, or a default if not bound.
   *
   * @param defaultTenant The tenant ID to return if TENANT_ID is not bound.
   * @return The current tenant ID or the default.
   */
  public static String getTenantIdOrDefault(String defaultTenant) {
    return TENANT_ID.isBound() ? TENANT_ID.get() : defaultTenant;
  }

  /**
   * Checks if the request deadline has been exceeded.
   *
   * @return {@code true} if DEADLINE is bound and the current time is after the deadline.
   */
  public static boolean isDeadlineExceeded() {
    return DEADLINE.isBound() && Instant.now().isAfter(DEADLINE.get());
  }
}
