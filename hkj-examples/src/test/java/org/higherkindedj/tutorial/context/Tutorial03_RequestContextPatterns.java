// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.higherkindedj.hkt.context.Context;
import org.higherkindedj.hkt.context.RequestContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tutorial: RequestContext Patterns - Distributed Tracing
 *
 * <p>Learn to use RequestContext for distributed tracing, multi-tenancy, and request lifecycle
 * management. RequestContext provides pre-defined ScopedValues for common request-scoped data.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>RequestContext.TRACE_ID for distributed tracing
 *   <li>RequestContext.CORRELATION_ID for cross-service correlation
 *   <li>RequestContext.TENANT_ID for multi-tenant applications
 *   <li>RequestContext.DEADLINE for timeout propagation
 *   <li>RequestContext.LOCALE for internationalisation
 * </ul>
 *
 * <p>Requirements: Java 25+ (ScopedValue is finalised)
 *
 * <p>Estimated time: 25-30 minutes
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
@DisplayName("Tutorial 03: RequestContext Patterns")
public class Tutorial03_RequestContextPatterns {

  /** Helper method for incomplete exercises. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required - replace answerRequired() with your solution");
  }

  // ===========================================================================
  // Part 1: Trace ID Patterns
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Trace ID Patterns")
  class TraceIdPatterns {

    /**
     * Exercise 1: Generate a trace ID
     *
     * <p>RequestContext.generateTraceId() creates a URL-safe Base64 encoded trace ID suitable for
     * distributed tracing.
     *
     * <p>Task: Generate a trace ID and verify it's a valid 16-character Base64 string
     */
    @Test
    @DisplayName("Exercise 1: Generate trace ID")
    void exercise1_generateTraceId() {
      // TODO: Replace answerRequired() with RequestContext.generateTraceId()
      String traceId = answerRequired();

      // URL-safe Base64 format: 16 characters (letters, digits, _, -)
      assertThat(traceId).matches("[A-Za-z0-9_-]{16}");
    }

    /**
     * Exercise 2: Read TRACE_ID from context
     *
     * <p>Task: Create a Context that reads RequestContext.TRACE_ID
     */
    @Test
    @DisplayName("Exercise 2: Read trace ID")
    void exercise2_readTraceId() throws Exception {
      // TODO: Replace answerRequired() with Context.ask(RequestContext.TRACE_ID)
      Context<String, String> getTraceId = answerRequired();

      String traceId = "test-trace-12345";
      String result =
          ScopedValue.where(RequestContext.TRACE_ID, traceId).call(() -> getTraceId.run());

      assertThat(result).isEqualTo(traceId);
    }

    /**
     * Exercise 3: Create a log prefix with trace ID
     *
     * <p>Task: Create a Context that reads TRACE_ID and formats it as a log prefix "[trace=xxx]"
     */
    @Test
    @DisplayName("Exercise 3: Format trace ID for logging")
    void exercise3_formatTraceId() throws Exception {
      // TODO: Replace answerRequired() with:
      // Context.asks(RequestContext.TRACE_ID, id -> "[trace=" + id + "]")
      Context<String, String> logPrefix = answerRequired();

      String result =
          ScopedValue.where(RequestContext.TRACE_ID, "abc-123").call(() -> logPrefix.run());

      assertThat(result).isEqualTo("[trace=abc-123]");
    }

    /**
     * Exercise 4: Use getTraceIdOrDefault helper
     *
     * <p>RequestContext.getTraceIdOrDefault() provides a fallback when TRACE_ID is not bound.
     *
     * <p>Task: Use the helper to get trace ID with a default
     */
    @Test
    @DisplayName("Exercise 4: Trace ID with default")
    void exercise4_traceIdWithDefault() throws Exception {
      // When bound, returns the bound value
      String bound =
          ScopedValue.where(RequestContext.TRACE_ID, "bound-trace")
              .call(() -> RequestContext.getTraceIdOrDefault("fallback"));

      // TODO: Replace answerRequired() with "bound-trace"
      assertThat(bound).isEqualTo(answerRequired());

      // When not bound, returns the default
      String unbound = RequestContext.getTraceIdOrDefault("fallback");

      // TODO: Replace answerRequired() with "fallback"
      assertThat(unbound).isEqualTo(answerRequired());
    }
  }

  // ===========================================================================
  // Part 2: Multi-Tenant Patterns
  // ===========================================================================

  @Nested
  @DisplayName("Part 2: Multi-Tenant Patterns")
  class MultiTenantPatterns {

    /**
     * Exercise 5: Read tenant ID
     *
     * <p>Task: Create a Context that reads the TENANT_ID
     */
    @Test
    @DisplayName("Exercise 5: Read tenant ID")
    void exercise5_readTenantId() throws Exception {
      // TODO: Replace answerRequired() with Context.ask(RequestContext.TENANT_ID)
      Context<String, String> getTenant = answerRequired();

      String result =
          ScopedValue.where(RequestContext.TENANT_ID, "acme-corp").call(() -> getTenant.run());

      assertThat(result).isEqualTo("acme-corp");
    }

    /**
     * Exercise 6: Derive a data source name from tenant
     *
     * <p>Task: Create a Context that derives a database name from tenant ID
     */
    @Test
    @DisplayName("Exercise 6: Derive data source from tenant")
    void exercise6_deriveDatasource() throws Exception {
      // TODO: Replace answerRequired() with:
      // Context.asks(RequestContext.TENANT_ID, id -> "db_" + id)
      Context<String, String> getDataSource = answerRequired();

      String result =
          ScopedValue.where(RequestContext.TENANT_ID, "customer123")
              .call(() -> getDataSource.run());

      assertThat(result).isEqualTo("db_customer123");
    }

    /**
     * Exercise 7: Create a cache key prefix
     *
     * <p>Task: Create a Context that creates a cache key prefix "tenant:{id}:"
     */
    @Test
    @DisplayName("Exercise 7: Create cache key prefix")
    void exercise7_cacheKeyPrefix() throws Exception {
      // TODO: Replace answerRequired() with:
      // Context.asks(RequestContext.TENANT_ID, id -> "tenant:" + id + ":")
      Context<String, String> getCachePrefix = answerRequired();

      String result =
          ScopedValue.where(RequestContext.TENANT_ID, "shop-xyz").call(() -> getCachePrefix.run());

      assertThat(result).isEqualTo("tenant:shop-xyz:");
    }
  }

  // ===========================================================================
  // Part 3: Deadline and Timeout Patterns
  // ===========================================================================

  @Nested
  @DisplayName("Part 3: Deadline and Timeout Patterns")
  class DeadlinePatterns {

    /**
     * Exercise 8: Set and read a deadline
     *
     * <p>Task: Bind a deadline and read it back
     */
    @Test
    @DisplayName("Exercise 8: Set and read deadline")
    void exercise8_setAndReadDeadline() throws Exception {
      Instant deadline = Instant.now().plus(Duration.ofSeconds(30));

      // TODO: Replace answerRequired() with Context.ask(RequestContext.DEADLINE)
      Context<Instant, Instant> getDeadline = answerRequired();

      Instant result =
          ScopedValue.where(RequestContext.DEADLINE, deadline).call(() -> getDeadline.run());

      assertThat(result).isEqualTo(deadline);
    }

    /**
     * Exercise 9: Calculate remaining time
     *
     * <p>Task: Create a Context that calculates the remaining time until deadline
     */
    @Test
    @DisplayName("Exercise 9: Calculate remaining time")
    void exercise9_remainingTime() throws Exception {
      Instant deadline = Instant.now().plus(Duration.ofMillis(500));

      // TODO: Replace answerRequired() with:
      // Context.asks(RequestContext.DEADLINE, d -> Duration.between(Instant.now(), d))
      Context<Instant, Duration> getRemainingTime = answerRequired();

      Duration result =
          ScopedValue.where(RequestContext.DEADLINE, deadline).call(() -> getRemainingTime.run());

      // Remaining time should be positive (less than 500ms, greater than 0)
      assertThat(result.toMillis()).isGreaterThan(0).isLessThanOrEqualTo(500);
    }

    /**
     * Exercise 10: Bind both request time and deadline
     *
     * <p>Task: Bind REQUEST_TIME and DEADLINE together
     */
    @Test
    @DisplayName("Exercise 10: Request time and deadline together")
    void exercise10_requestTimeAndDeadline() throws Exception {
      Instant requestTime = Instant.now();
      Instant deadline = requestTime.plus(Duration.ofSeconds(10));

      Context<Instant, Instant> getRequestTime = Context.ask(RequestContext.REQUEST_TIME);
      Context<Instant, Instant> getDeadline = Context.ask(RequestContext.DEADLINE);

      // TODO: Replace the answerRequired() values in where() calls
      String result =
          ScopedValue.where(RequestContext.REQUEST_TIME, answerRequired()) // Hint: requestTime
              .where(RequestContext.DEADLINE, answerRequired()) // Hint: deadline
              .call(
                  () -> {
                    Instant start = getRequestTime.run();
                    Instant end = getDeadline.run();
                    Duration timeout = Duration.between(start, end);
                    return "Timeout: " + timeout.getSeconds() + "s";
                  });

      assertThat(result).isEqualTo("Timeout: 10s");
    }
  }

  // ===========================================================================
  // Part 4: Locale and Internationalisation
  // ===========================================================================

  @Nested
  @DisplayName("Part 4: Locale Patterns")
  class LocalePatterns {

    /**
     * Exercise 11: Read locale from context
     *
     * <p>Task: Create a Context that reads the LOCALE
     */
    @Test
    @DisplayName("Exercise 11: Read locale")
    void exercise11_readLocale() throws Exception {
      // TODO: Replace answerRequired() with Context.ask(RequestContext.LOCALE)
      Context<Locale, Locale> getLocale = answerRequired();

      Locale result =
          ScopedValue.where(RequestContext.LOCALE, Locale.FRENCH).call(() -> getLocale.run());

      assertThat(result).isEqualTo(Locale.FRENCH);
    }

    /**
     * Exercise 12: Get language code from locale
     *
     * <p>Task: Create a Context that extracts the language code
     */
    @Test
    @DisplayName("Exercise 12: Extract language code")
    void exercise12_extractLanguage() throws Exception {
      // TODO: Replace answerRequired() with:
      // Context.asks(RequestContext.LOCALE, Locale::getLanguage)
      Context<Locale, String> getLanguage = answerRequired();

      String result =
          ScopedValue.where(RequestContext.LOCALE, Locale.GERMAN).call(() -> getLanguage.run());

      assertThat(result).isEqualTo("de");
    }
  }

  // ===========================================================================
  // Bonus: Complete Request Pipeline
  // ===========================================================================

  @Nested
  @DisplayName("Bonus: Complete Request Pipeline")
  class CompleteRequestPipeline {

    /** This test demonstrates a complete request pipeline with all context values bound. */
    @Test
    @DisplayName("Complete request pipeline")
    void completeRequestPipeline() throws Exception {
      // Set up all request context values
      String traceId = RequestContext.generateTraceId();
      String correlationId = "corr-" + System.currentTimeMillis();
      String tenantId = "acme-corp";
      Instant requestTime = Instant.now();
      Instant deadline = requestTime.plus(Duration.ofSeconds(5));
      Locale locale = Locale.UK;

      // Create contexts for reading values
      Context<String, String> logPrefix =
          Context.asks(
              RequestContext.TRACE_ID,
              id ->
                  String.format(
                      "[trace=%s tenant=%s]",
                      id.substring(0, 8), RequestContext.TENANT_ID.orElse("unknown")));

      // Execute a simulated request
      String result =
          ScopedValue.where(RequestContext.TRACE_ID, traceId)
              .where(RequestContext.CORRELATION_ID, correlationId)
              .where(RequestContext.TENANT_ID, tenantId)
              .where(RequestContext.REQUEST_TIME, requestTime)
              .where(RequestContext.DEADLINE, deadline)
              .where(RequestContext.LOCALE, locale)
              .call(
                  () -> {
                    // Build a status message
                    String prefix = logPrefix.run();
                    Duration remaining =
                        Duration.between(Instant.now(), RequestContext.DEADLINE.get());
                    String lang = RequestContext.LOCALE.get().getLanguage();

                    return String.format(
                        "%s Processing in %s, %dms remaining", prefix, lang, remaining.toMillis());
                  });

      assertThat(result)
          .contains("trace=")
          .contains("tenant=acme-corp")
          .contains("Processing in en");
    }
  }

  /**
   * Congratulations! You've completed Tutorial 03: RequestContext Patterns
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>✓ How to use TRACE_ID for distributed tracing
   *   <li>✓ How to implement multi-tenant patterns with TENANT_ID
   *   <li>✓ How to propagate deadlines with REQUEST_TIME and DEADLINE
   *   <li>✓ How to handle locale for internationalisation
   *   <li>✓ How to combine multiple context values in a request pipeline
   * </ul>
   *
   * <p>Key Takeaways:
   *
   * <ul>
   *   <li>RequestContext provides pre-defined ScopedValues for common patterns
   *   <li>Use generateTraceId() to create unique trace identifiers
   *   <li>Deadlines propagate through the call chain automatically
   *   <li>Context values can be combined for comprehensive request tracking
   * </ul>
   *
   * <p>Next: Tutorial 04 - SecurityContext Patterns for authentication
   */
}
