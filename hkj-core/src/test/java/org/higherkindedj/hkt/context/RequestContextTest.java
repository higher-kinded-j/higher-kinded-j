// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.context;

import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for {@link RequestContext}.
 *
 * <p>Coverage includes static ScopedValue fields, helper methods, and utility functions.
 */
@DisplayName("RequestContext Complete Test Suite")
class RequestContextTest {

  @Nested
  @DisplayName("Static ScopedValue Fields")
  class StaticScopedValueFields {

    @Test
    @DisplayName("TRACE_ID should be a ScopedValue")
    void traceId_shouldBeScopedValue() {
      assertThat(RequestContext.TRACE_ID).isNotNull();
    }

    @Test
    @DisplayName("CORRELATION_ID should be a ScopedValue")
    void correlationId_shouldBeScopedValue() {
      assertThat(RequestContext.CORRELATION_ID).isNotNull();
    }

    @Test
    @DisplayName("LOCALE should be a ScopedValue")
    void locale_shouldBeScopedValue() {
      assertThat(RequestContext.LOCALE).isNotNull();
    }

    @Test
    @DisplayName("TENANT_ID should be a ScopedValue")
    void tenantId_shouldBeScopedValue() {
      assertThat(RequestContext.TENANT_ID).isNotNull();
    }

    @Test
    @DisplayName("REQUEST_TIME should be a ScopedValue")
    void requestTime_shouldBeScopedValue() {
      assertThat(RequestContext.REQUEST_TIME).isNotNull();
    }

    @Test
    @DisplayName("DEADLINE should be a ScopedValue")
    void deadline_shouldBeScopedValue() {
      assertThat(RequestContext.DEADLINE).isNotNull();
    }

    @Test
    @DisplayName("All ScopedValues should be distinct instances")
    void allScopedValuesShouldBeDistinct() {
      assertThat(RequestContext.TRACE_ID).isNotSameAs(RequestContext.CORRELATION_ID);
      assertThat(RequestContext.TRACE_ID).isNotSameAs(RequestContext.TENANT_ID);
      assertThat(RequestContext.LOCALE).isNotSameAs(RequestContext.REQUEST_TIME);
      assertThat(RequestContext.REQUEST_TIME).isNotSameAs(RequestContext.DEADLINE);
    }
  }

  @Nested
  @DisplayName("generateTraceId()")
  class GenerateTraceIdTests {

    @Test
    @DisplayName("generateTraceId() should return 16-character URL-safe Base64 string")
    void generateTraceId_shouldReturn16CharBase64() {
      String traceId = RequestContext.generateTraceId();

      assertThat(traceId).hasSize(16);
      assertThat(traceId).matches("[A-Za-z0-9_-]{16}");
    }

    @Test
    @DisplayName("generateTraceId() should return unique values")
    void generateTraceId_shouldReturnUniqueValues() {
      String id1 = RequestContext.generateTraceId();
      String id2 = RequestContext.generateTraceId();
      String id3 = RequestContext.generateTraceId();

      assertThat(id1).isNotEqualTo(id2);
      assertThat(id2).isNotEqualTo(id3);
      assertThat(id1).isNotEqualTo(id3);
    }
  }

  @Nested
  @DisplayName("generateTimestampedTraceId()")
  class GenerateTimestampedTraceIdTests {

    @Test
    @DisplayName("generateTimestampedTraceId() should return timestamped format")
    void generateTimestampedTraceId_shouldReturnTimestampedFormat() {
      String traceId = RequestContext.generateTimestampedTraceId();

      // Format: {12-char-hex-timestamp}-{8-char-hex-random}
      assertThat(traceId).matches("[0-9a-f]{12}-[0-9a-f]{8}");
    }

    @Test
    @DisplayName("generateTimestampedTraceId() should return unique values")
    void generateTimestampedTraceId_shouldReturnUniqueValues() {
      String id1 = RequestContext.generateTimestampedTraceId();
      String id2 = RequestContext.generateTimestampedTraceId();

      assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    @DisplayName("generateTimestampedTraceId() should have increasing timestamps")
    void generateTimestampedTraceId_shouldHaveIncreasingTimestamps() {
      String id1 = RequestContext.generateTimestampedTraceId();
      String timestamp1 = id1.substring(0, 12);

      // Small delay to ensure different timestamp
      try {
        Thread.sleep(2);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }

      String id2 = RequestContext.generateTimestampedTraceId();
      String timestamp2 = id2.substring(0, 12);

      // Timestamps should be non-decreasing
      long ts1 = Long.parseLong(timestamp1, 16);
      long ts2 = Long.parseLong(timestamp2, 16);
      assertThat(ts2).isGreaterThanOrEqualTo(ts1);
    }
  }

  @Nested
  @DisplayName("getTraceIdOrDefault()")
  class GetTraceIdOrDefaultTests {

    @Test
    @DisplayName("getTraceIdOrDefault() should return bound value when TRACE_ID is bound")
    void getTraceIdOrDefault_shouldReturnBoundValue() throws Exception {
      String expected = "bound-trace-id";

      String result =
          ScopedValue.where(RequestContext.TRACE_ID, expected)
              .call(() -> RequestContext.getTraceIdOrDefault("default"));

      assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("getTraceIdOrDefault() should return default when TRACE_ID is not bound")
    void getTraceIdOrDefault_shouldReturnDefaultWhenNotBound() {
      String result = RequestContext.getTraceIdOrDefault("default-value");

      assertThat(result).isEqualTo("default-value");
    }
  }

  @Nested
  @DisplayName("getLocaleOrDefault()")
  class GetLocaleOrDefaultTests {

    @Test
    @DisplayName("getLocaleOrDefault() should return bound locale when LOCALE is bound")
    void getLocaleOrDefault_shouldReturnBoundLocale() throws Exception {
      Locale expected = Locale.FRANCE;

      Locale result =
          ScopedValue.where(RequestContext.LOCALE, expected)
              .call(() -> RequestContext.getLocaleOrDefault(Locale.US));

      assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("getLocaleOrDefault() should return default when LOCALE is not bound")
    void getLocaleOrDefault_shouldReturnDefaultWhenNotBound() {
      Locale result = RequestContext.getLocaleOrDefault(Locale.GERMANY);

      assertThat(result).isEqualTo(Locale.GERMANY);
    }
  }

  @Nested
  @DisplayName("getTenantIdOrDefault()")
  class GetTenantIdOrDefaultTests {

    @Test
    @DisplayName("getTenantIdOrDefault() should return bound tenant when TENANT_ID is bound")
    void getTenantIdOrDefault_shouldReturnBoundTenant() throws Exception {
      String expected = "acme-corp";

      String result =
          ScopedValue.where(RequestContext.TENANT_ID, expected)
              .call(() -> RequestContext.getTenantIdOrDefault("default-tenant"));

      assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("getTenantIdOrDefault() should return default when TENANT_ID is not bound")
    void getTenantIdOrDefault_shouldReturnDefaultWhenNotBound() {
      String result = RequestContext.getTenantIdOrDefault("default-tenant");

      assertThat(result).isEqualTo("default-tenant");
    }
  }

  @Nested
  @DisplayName("isDeadlineExceeded()")
  class IsDeadlineExceededTests {

    @Test
    @DisplayName("isDeadlineExceeded() should return false when DEADLINE is not bound")
    void isDeadlineExceeded_shouldReturnFalseWhenNotBound() {
      boolean result = RequestContext.isDeadlineExceeded();

      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isDeadlineExceeded() should return true when deadline has passed")
    void isDeadlineExceeded_shouldReturnTrueWhenDeadlinePassed() throws Exception {
      Instant pastDeadline = Instant.now().minusSeconds(10);

      boolean result =
          ScopedValue.where(RequestContext.DEADLINE, pastDeadline)
              .call(RequestContext::isDeadlineExceeded);

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isDeadlineExceeded() should return false when deadline is in future")
    void isDeadlineExceeded_shouldReturnFalseWhenDeadlineInFuture() throws Exception {
      Instant futureDeadline = Instant.now().plusSeconds(60);

      boolean result =
          ScopedValue.where(RequestContext.DEADLINE, futureDeadline)
              .call(RequestContext::isDeadlineExceeded);

      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("ScopedValue Binding")
  class ScopedValueBinding {

    @Test
    @DisplayName("Multiple ScopedValues can be bound together")
    void multipleScopedValuesCanBeBoundTogether() throws Exception {
      String traceId = "trace-123";
      String tenantId = "tenant-456";
      Locale locale = Locale.FRANCE;

      String result =
          ScopedValue.where(RequestContext.TRACE_ID, traceId)
              .where(RequestContext.TENANT_ID, tenantId)
              .where(RequestContext.LOCALE, locale)
              .call(
                  () ->
                      RequestContext.TRACE_ID.get()
                          + "|"
                          + RequestContext.TENANT_ID.get()
                          + "|"
                          + RequestContext.LOCALE.get().getLanguage());

      assertThat(result).isEqualTo("trace-123|tenant-456|fr");
    }
  }

  @Nested
  @DisplayName("Utility Class Design")
  class UtilityClassDesign {

    @Test
    @DisplayName("RequestContext should be final")
    void requestContext_shouldBeFinal() {
      assertThat(java.lang.reflect.Modifier.isFinal(RequestContext.class.getModifiers())).isTrue();
    }

    @Test
    @DisplayName("RequestContext should have private constructor")
    void requestContext_shouldHavePrivateConstructor() throws Exception {
      Constructor<RequestContext> constructor = RequestContext.class.getDeclaredConstructor();
      assertThat(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }
  }
}
