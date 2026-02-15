// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TryPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tests for {@link TryPathReturnValueHandler}.
 *
 * <p>Verifies HTTP response mapping for TryPath return types.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TryPathReturnValueHandler Tests")
class TryPathReturnValueHandlerTest {

  @Mock private HttpServletResponse response;

  @Mock private NativeWebRequest webRequest;

  @Mock private MethodParameter returnType;

  @Mock private ModelAndViewContainer mavContainer;

  private TryPathReturnValueHandler handler;
  private StringWriter stringWriter;
  private PrintWriter printWriter;
  private JsonMapper jsonMapper;

  @BeforeEach
  void setUp() throws Exception {
    jsonMapper = JsonMapper.builder().build();
    handler =
        new TryPathReturnValueHandler(jsonMapper, HttpStatus.INTERNAL_SERVER_ERROR.value(), false);

    stringWriter = new StringWriter();
    printWriter = new PrintWriter(stringWriter);

    lenient().when(webRequest.getNativeResponse(HttpServletResponse.class)).thenReturn(response);
    lenient().when(response.getWriter()).thenReturn(printWriter);
  }

  @Nested
  @DisplayName("supportsReturnType Tests")
  class SupportsReturnTypeTests {

    @Test
    @DisplayName("Should support TryPath return type")
    void shouldSupportTryPathReturnType() {
      when(returnType.getParameterType()).thenReturn((Class) TryPath.class);

      boolean result = handler.supportsReturnType(returnType);

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should not support non-TryPath return type")
    void shouldNotSupportNonTryPathReturnType() {
      when(returnType.getParameterType()).thenReturn((Class) String.class);

      boolean result = handler.supportsReturnType(returnType);

      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("handleReturnValue - Success Tests")
  class SuccessValueTests {

    @Test
    @DisplayName("Should handle Success value with HTTP 200")
    void shouldHandleSuccessValueWith200() throws Exception {
      TestUser user = new TestUser("1", "alice@example.com");
      TryPath<TestUser> path = Path.success(user);

      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.OK.value());
      verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
      verify(mavContainer).setRequestHandled(true);

      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).contains("\"id\":\"1\"");
      assertThat(json).contains("\"email\":\"alice@example.com\"");
    }

    @Test
    @DisplayName("Should handle Success from tryOf supplier")
    void shouldHandleSuccessFromTryOf() throws Exception {
      TryPath<Integer> path = Path.tryOf(() -> 42);

      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.OK.value());
      printWriter.flush();
      assertThat(stringWriter.toString()).isEqualTo("42");
    }
  }

  @Nested
  @DisplayName("handleReturnValue - Failure Tests")
  class FailureValueTests {

    @Test
    @DisplayName("Should handle Failure with HTTP 500 and hidden details")
    void shouldHandleFailureWith500AndHiddenDetails() throws Exception {
      TryPath<TestUser> path = Path.failure(new RuntimeException("Database error"));

      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
      verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);

      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).contains("\"success\":false");
      assertThat(json).contains("\"error\":\"An internal error occurred\"");
      assertThat(json).doesNotContain("Database error");
    }

    @Test
    @DisplayName("Should include exception details when configured")
    void shouldIncludeExceptionDetailsWhenConfigured() throws Exception {
      TryPathReturnValueHandler detailedHandler =
          new TryPathReturnValueHandler(jsonMapper, HttpStatus.INTERNAL_SERVER_ERROR.value(), true);
      TryPath<TestUser> path = Path.failure(new IllegalArgumentException("Invalid ID format"));

      detailedHandler.handleReturnValue(path, returnType, mavContainer, webRequest);

      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).contains("\"success\":false");
      assertThat(json).contains("\"type\":\"IllegalArgumentException\"");
      assertThat(json).contains("\"message\":\"Invalid ID format\"");
    }

    @Test
    @DisplayName("Should handle failure from tryOf with throwing supplier")
    void shouldHandleFailureFromTryOf() throws Exception {
      TryPath<Integer> path =
          Path.tryOf(
              () -> {
                throw new ArithmeticException("Division by zero");
              });

      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).contains("\"success\":false");
    }

    @Test
    @DisplayName("Should use custom failure status")
    void shouldUseCustomFailureStatus() throws Exception {
      TryPathReturnValueHandler customHandler =
          new TryPathReturnValueHandler(jsonMapper, HttpStatus.SERVICE_UNAVAILABLE.value(), false);
      TryPath<TestUser> path = Path.failure(new RuntimeException("Service down"));

      customHandler.handleReturnValue(path, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("Should handle null returnValue gracefully")
    void shouldHandleNullReturnValue() throws Exception {
      handler.handleReturnValue(null, returnType, mavContainer, webRequest);

      verify(mavContainer).setRequestHandled(true);
      verify(response, never()).setStatus(anyInt());
    }

    @Test
    @DisplayName("Should handle non-TryPath returnValue gracefully")
    void shouldHandleNonTryPathReturnValue() throws Exception {
      handler.handleReturnValue("not a TryPath", returnType, mavContainer, webRequest);

      verify(mavContainer).setRequestHandled(true);
      verify(response, never()).setStatus(anyInt());
    }

    @Test
    @DisplayName("Should handle exception with null message")
    void shouldHandleExceptionWithNullMessage() throws Exception {
      TryPathReturnValueHandler detailedHandler =
          new TryPathReturnValueHandler(jsonMapper, 500, true);
      TryPath<TestUser> path = Path.failure(new RuntimeException((String) null));

      detailedHandler.handleReturnValue(path, returnType, mavContainer, webRequest);

      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).contains("\"message\":\"No message\"");
    }
  }

  // Test DTOs
  record TestUser(String id, String email) {}
}
