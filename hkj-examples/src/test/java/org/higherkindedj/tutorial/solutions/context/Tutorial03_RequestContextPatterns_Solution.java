// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.higherkindedj.hkt.context.Context;
import org.higherkindedj.hkt.context.RequestContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Solutions for Tutorial 03: RequestContext Patterns */
@DisplayName("Tutorial 03: RequestContext Patterns - Solutions")
public class Tutorial03_RequestContextPatterns_Solution {

  @Nested
  @DisplayName("Part 1: Trace ID Patterns")
  class TraceIdPatterns {

    @Test
    @DisplayName("Exercise 1: Generate trace ID")
    void exercise1_generateTraceId() {
      // SOLUTION: Use RequestContext.generateTraceId()
      // Returns a 16-character URL-safe Base64 encoded random string
      String traceId = RequestContext.generateTraceId();
      assertThat(traceId).matches("[A-Za-z0-9_-]{16}");
    }

    @Test
    @DisplayName("Exercise 2: Read trace ID")
    void exercise2_readTraceId() throws Exception {
      // SOLUTION: Use Context.ask() with RequestContext.TRACE_ID
      Context<String, String> getTraceId = Context.ask(RequestContext.TRACE_ID);

      String traceId = "test-trace-12345";
      String result =
          ScopedValue.where(RequestContext.TRACE_ID, traceId).call(() -> getTraceId.run());

      assertThat(result).isEqualTo(traceId);
    }

    @Test
    @DisplayName("Exercise 3: Format trace ID for logging")
    void exercise3_formatTraceId() throws Exception {
      // SOLUTION: Use Context.asks() with a formatting function
      Context<String, String> logPrefix =
          Context.asks(RequestContext.TRACE_ID, id -> "[trace=" + id + "]");

      String result =
          ScopedValue.where(RequestContext.TRACE_ID, "abc-123").call(() -> logPrefix.run());

      assertThat(result).isEqualTo("[trace=abc-123]");
    }

    @Test
    @DisplayName("Exercise 4: Trace ID with default")
    void exercise4_traceIdWithDefault() throws Exception {
      String bound =
          ScopedValue.where(RequestContext.TRACE_ID, "bound-trace")
              .call(() -> RequestContext.getTraceIdOrDefault("fallback"));

      // SOLUTION: "bound-trace"
      assertThat(bound).isEqualTo("bound-trace");

      String unbound = RequestContext.getTraceIdOrDefault("fallback");

      // SOLUTION: "fallback"
      assertThat(unbound).isEqualTo("fallback");
    }
  }

  @Nested
  @DisplayName("Part 2: Multi-Tenant Patterns")
  class MultiTenantPatterns {

    @Test
    @DisplayName("Exercise 5: Read tenant ID")
    void exercise5_readTenantId() throws Exception {
      // SOLUTION: Use Context.ask() with RequestContext.TENANT_ID
      Context<String, String> getTenant = Context.ask(RequestContext.TENANT_ID);

      String result =
          ScopedValue.where(RequestContext.TENANT_ID, "acme-corp").call(() -> getTenant.run());

      assertThat(result).isEqualTo("acme-corp");
    }

    @Test
    @DisplayName("Exercise 6: Derive data source from tenant")
    void exercise6_deriveDatasource() throws Exception {
      // SOLUTION: Use Context.asks() to transform
      Context<String, String> getDataSource =
          Context.asks(RequestContext.TENANT_ID, id -> "db_" + id);

      String result =
          ScopedValue.where(RequestContext.TENANT_ID, "customer123")
              .call(() -> getDataSource.run());

      assertThat(result).isEqualTo("db_customer123");
    }

    @Test
    @DisplayName("Exercise 7: Create cache key prefix")
    void exercise7_cacheKeyPrefix() throws Exception {
      // SOLUTION: Format as cache key prefix
      Context<String, String> getCachePrefix =
          Context.asks(RequestContext.TENANT_ID, id -> "tenant:" + id + ":");

      String result =
          ScopedValue.where(RequestContext.TENANT_ID, "shop-xyz").call(() -> getCachePrefix.run());

      assertThat(result).isEqualTo("tenant:shop-xyz:");
    }
  }

  @Nested
  @DisplayName("Part 3: Deadline and Timeout Patterns")
  class DeadlinePatterns {

    @Test
    @DisplayName("Exercise 8: Set and read deadline")
    void exercise8_setAndReadDeadline() throws Exception {
      Instant deadline = Instant.now().plus(Duration.ofSeconds(30));

      // SOLUTION: Use Context.ask() with RequestContext.DEADLINE
      Context<Instant, Instant> getDeadline = Context.ask(RequestContext.DEADLINE);

      Instant result =
          ScopedValue.where(RequestContext.DEADLINE, deadline).call(() -> getDeadline.run());

      assertThat(result).isEqualTo(deadline);
    }

    @Test
    @DisplayName("Exercise 9: Calculate remaining time")
    void exercise9_remainingTime() throws Exception {
      Instant deadline = Instant.now().plus(Duration.ofMillis(500));

      // SOLUTION: Calculate duration from now to deadline
      Context<Instant, Duration> getRemainingTime =
          Context.asks(RequestContext.DEADLINE, d -> Duration.between(Instant.now(), d));

      Duration result =
          ScopedValue.where(RequestContext.DEADLINE, deadline).call(() -> getRemainingTime.run());

      assertThat(result.toMillis()).isGreaterThan(0).isLessThanOrEqualTo(500);
    }

    @Test
    @DisplayName("Exercise 10: Request time and deadline together")
    void exercise10_requestTimeAndDeadline() throws Exception {
      Instant requestTime = Instant.now();
      Instant deadline = requestTime.plus(Duration.ofSeconds(10));

      Context<Instant, Instant> getRequestTime = Context.ask(RequestContext.REQUEST_TIME);
      Context<Instant, Instant> getDeadline = Context.ask(RequestContext.DEADLINE);

      // SOLUTION: Provide requestTime and deadline in where() calls
      String result =
          ScopedValue.where(RequestContext.REQUEST_TIME, requestTime)
              .where(RequestContext.DEADLINE, deadline)
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

  @Nested
  @DisplayName("Part 4: Locale Patterns")
  class LocalePatterns {

    @Test
    @DisplayName("Exercise 11: Read locale")
    void exercise11_readLocale() throws Exception {
      // SOLUTION: Use Context.ask() with RequestContext.LOCALE
      Context<Locale, Locale> getLocale = Context.ask(RequestContext.LOCALE);

      Locale result =
          ScopedValue.where(RequestContext.LOCALE, Locale.FRENCH).call(() -> getLocale.run());

      assertThat(result).isEqualTo(Locale.FRENCH);
    }

    @Test
    @DisplayName("Exercise 12: Extract language code")
    void exercise12_extractLanguage() throws Exception {
      // SOLUTION: Use Locale::getLanguage method reference
      Context<Locale, String> getLanguage =
          Context.asks(RequestContext.LOCALE, Locale::getLanguage);

      String result =
          ScopedValue.where(RequestContext.LOCALE, Locale.GERMAN).call(() -> getLanguage.run());

      assertThat(result).isEqualTo("de");
    }
  }
}
