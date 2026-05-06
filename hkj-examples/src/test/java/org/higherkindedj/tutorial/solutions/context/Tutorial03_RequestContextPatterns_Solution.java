// Copyright (c) 2025 - 2026 Magnus Smith
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

/**
 * Solution for Tutorial03 RequestContextPatterns — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
 */
@DisplayName("Tutorial 03: RequestContext Patterns - Solutions")
public class Tutorial03_RequestContextPatterns_Solution {

  @Nested
  @DisplayName("Part 1: Trace ID Patterns")
  class TraceIdPatterns {

    /**
     * Why this is idiomatic: {@code RequestContext.generateTraceId()} produces a URL-safe Base64
     * string of fixed length. Using the helper keeps every component of the system on the same
     * trace-id format.
     *
     * <p>Alternative: a {@code UUID.randomUUID().toString()}. Longer, contains hyphens, doesn't
     * match the regex; pick the helper to stay consistent with downstream tools.
     *
     * <p>Common wrong attempt: roll a custom encoding. Trace ids must compare across systems; reuse
     * the canonical helper.
     */
    @Test
    @DisplayName("Exercise 1: Generate trace ID")
    void exercise1_generateTraceId() {
      // SOLUTION: Use RequestContext.generateTraceId()
      // Returns a 16-character URL-safe Base64 encoded random string
      String traceId = RequestContext.generateTraceId();
      assertThat(traceId).matches("[A-Za-z0-9_-]{16}");
    }

    /**
     * Why this is idiomatic: a single shared {@code TRACE_ID} scoped value lets every component
     * read the current trace without explicit threading. {@code Context.ask} surfaces it inside any
     * computation that runs in scope.
     *
     * <p>Alternative: pass the trace id as a method parameter everywhere. Possible but pollutes
     * every signature in the call chain.
     *
     * <p>Common wrong attempt: stash the trace id in a {@code ThreadLocal}. Works on platform
     * threads; virtual threads need {@code ScopedValue} for inheritance to survive task spawning.
     */
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

    /**
     * Why this is idiomatic: {@code Context.asks(TRACE_ID, formatter)} produces a ready- to-log
     * prefix from the current trace id. Loggers call {@code .run()} and get a scoped string.
     *
     * <p>Alternative: read the trace id and format at every log site. Same outcome; the named
     * context centralises the format.
     *
     * <p>Common wrong attempt: build the prefix once at startup. The trace id changes per request;
     * the context defers the formatting until the right scope is active.
     */
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

    /**
     * Why this is idiomatic: {@code RequestContext.getTraceIdOrDefault(fallback)} collapses the
     * bound/unbound branches into a total accessor. Always returns a string, never throws.
     *
     * <p>Alternative: catch {@code NoSuchElementException} from a raw {@code .get()}. Same outcome;
     * the named helper signals the intent.
     *
     * <p>Common wrong attempt: rely on the trace id being bound everywhere. Background tasks and
     * bootstrap code may run without a request scope; default explicitly.
     */
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

    /**
     * Why this is idiomatic: {@code TENANT_ID} is the per-request multi-tenant key. Read it via
     * {@code Context.ask} like any other scoped value; the rest of the request can use it for
     * routing.
     *
     * <p>Alternative: pull the tenant id from a header at every layer. Loses the implicit
     * propagation; use the context once, propagate everywhere.
     *
     * <p>Common wrong attempt: store the tenant id in a class-level field. Concurrent requests
     * would race on the field; {@code ScopedValue} is per-call by design.
     */
    @Test
    @DisplayName("Exercise 5: Read tenant ID")
    void exercise5_readTenantId() throws Exception {
      // SOLUTION: Use Context.ask() with RequestContext.TENANT_ID
      Context<String, String> getTenant = Context.ask(RequestContext.TENANT_ID);

      String result =
          ScopedValue.where(RequestContext.TENANT_ID, "acme-corp").call(() -> getTenant.run());

      assertThat(result).isEqualTo("acme-corp");
    }

    /**
     * Why this is idiomatic: derive the data source name from the tenant id with {@code
     * Context.asks(TENANT_ID, fn)}. The mapping is centralised; no caller encodes the prefix
     * convention.
     *
     * <p>Alternative: hard-code "db_" + tenantId at every call site. Drift introduces subtle bugs;
     * the derivation belongs in one place.
     *
     * <p>Common wrong attempt: use a static map of tenant-id to data source. The derivation may be
     * a function, not a lookup; encode whichever fits.
     */
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

    /**
     * Why this is idiomatic: a tenant-scoped cache prefix prevents cross-tenant key collisions. The
     * context computes the prefix from the active tenant.
     *
     * <p>Alternative: include the tenant id in every cache key manually. Easy to forget; a derived
     * prefix is a one-call source of truth.
     *
     * <p>Common wrong attempt: cache without the tenant prefix and "trust" upstream separation.
     * Defence in depth — the prefix makes leaks impossible by construction.
     */
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

    /**
     * Why this is idiomatic: a {@code DEADLINE} scoped value carries the absolute cut- off time for
     * the current request. Components read it without knowing how the deadline was negotiated.
     *
     * <p>Alternative: pass the deadline as a parameter through every method. Works, but pollutes
     * signatures.
     *
     * <p>Common wrong attempt: store the deadline as a relative duration. Offsets need a clock to
     * be useful; an absolute {@code Instant} is what the deadline truly is.
     */
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

    /**
     * Why this is idiomatic: remaining time is "now minus deadline" — derive it from the scoped
     * deadline at the moment of the read. {@code Context.asks} runs the lambda when the consumer
     * asks.
     *
     * <p>Alternative: compute the duration once and pass it everywhere. Becomes stale as time
     * passes; recompute at the use site.
     *
     * <p>Common wrong attempt: cache the result outside the context. The remaining time changes
     * every millisecond; do not cache.
     */
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

    /**
     * Why this is idiomatic: bind both {@code REQUEST_TIME} and {@code DEADLINE} so the timeout
     * calculation has both endpoints. The two contexts read independently in the same scope.
     *
     * <p>Alternative: compute the duration once and store as a single scoped value. Loses the
     * absolute timestamps; logging the difference is harder.
     *
     * <p>Common wrong attempt: assume request time changes within a request. It is fixed at the
     * start; deadline is fixed at request build; only the remaining duration shifts.
     */
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

    /**
     * Why this is idiomatic: the request's {@code Locale} flows through every layer via a scoped
     * value. Localised messages, date formatters, and number formatters all pick it up without an
     * argument.
     *
     * <p>Alternative: pass {@code Locale} as a parameter. Pollutes signatures with a cross-cutting
     * concern.
     *
     * <p>Common wrong attempt: read {@code Locale.getDefault()}. That returns the JVM default, not
     * the user's; bind the request locale explicitly.
     */
    @Test
    @DisplayName("Exercise 11: Read locale")
    void exercise11_readLocale() throws Exception {
      // SOLUTION: Use Context.ask() with RequestContext.LOCALE
      Context<Locale, Locale> getLocale = Context.ask(RequestContext.LOCALE);

      Locale result =
          ScopedValue.where(RequestContext.LOCALE, Locale.FRENCH).call(() -> getLocale.run());

      assertThat(result).isEqualTo(Locale.FRENCH);
    }

    /**
     * Why this is idiomatic: a method reference {@code Locale::getLanguage} is a one-liner that
     * extracts the language tag. Combined with {@code Context.asks} the read is fused.
     *
     * <p>Alternative: lambda {@code l -> l.getLanguage()}. Identical at runtime; the method
     * reference reads cleaner.
     *
     * <p>Common wrong attempt: split the code from the country and lose the locale altogether.
     * Stick to the {@code Locale} type for both extraction and reuse.
     */
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
