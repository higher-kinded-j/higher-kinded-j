// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import org.higherkindedj.hkt.effect.CompletableFuturePath;
import org.higherkindedj.hkt.effect.Path;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.async.StandardServletAsyncWebRequest;
import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.method.support.ModelAndViewContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tests for {@link CompletableFuturePathReturnValueHandler}.
 *
 * <p>Verifies HTTP response mapping for CompletableFuturePath return types with async support.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CompletableFuturePathReturnValueHandler Tests")
class CompletableFuturePathReturnValueHandlerTest {

  @Mock private MethodParameter returnType;

  private CompletableFuturePathReturnValueHandler handler;
  private JsonMapper jsonMapper;
  private MockHttpServletRequest mockRequest;
  private MockHttpServletResponse mockResponse;
  private ServletWebRequest webRequest;
  private ModelAndViewContainer mavContainer;

  @BeforeEach
  void setUp() throws Exception {
    jsonMapper = JsonMapper.builder().build();
    handler =
        new CompletableFuturePathReturnValueHandler(
            jsonMapper, HttpStatus.INTERNAL_SERVER_ERROR.value(), false, 30000);

    mockRequest = new MockHttpServletRequest();
    mockRequest.setAsyncSupported(true);
    mockResponse = new MockHttpServletResponse();
    webRequest = new ServletWebRequest(mockRequest, mockResponse);
    mavContainer = new ModelAndViewContainer();

    // Set up async request on the WebAsyncManager
    WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(webRequest);
    asyncManager.setAsyncWebRequest(new StandardServletAsyncWebRequest(mockRequest, mockResponse));
  }

  @Nested
  @DisplayName("supportsReturnType Tests")
  class SupportsReturnTypeTests {

    @Test
    @DisplayName("Should support CompletableFuturePath return type")
    void shouldSupportCompletableFuturePathReturnType() {
      when(returnType.getParameterType()).thenReturn((Class) CompletableFuturePath.class);

      boolean result = handler.supportsReturnType(returnType);

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should not support non-CompletableFuturePath return type")
    void shouldNotSupportNonCompletableFuturePathReturnType() {
      when(returnType.getParameterType()).thenReturn((Class) String.class);

      boolean result = handler.supportsReturnType(returnType);

      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("isAsyncReturnValue Tests")
  class IsAsyncReturnValueTests {

    @Test
    @DisplayName("Should identify CompletableFuturePath as async")
    void shouldIdentifyCompletableFuturePathAsAsync() {
      CompletableFuturePath<String> path = Path.futureCompleted("test");

      boolean result = handler.isAsyncReturnValue(path, returnType);

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should not identify non-CompletableFuturePath as async")
    void shouldNotIdentifyNonCompletableFuturePathAsAsync() {
      String notAsync = "not async";

      boolean result = handler.isAsyncReturnValue(notAsync, returnType);

      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should not identify null as async")
    void shouldNotIdentifyNullAsAsync() {
      boolean result = handler.isAsyncReturnValue(null, returnType);

      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("handleReturnValue - Successful Completion Tests")
  class SuccessfulCompletionTests {

    @Test
    @DisplayName("Should handle already-completed future with HTTP 200")
    void shouldHandleCompletedFutureWith200() throws Exception {
      TestUser user = new TestUser("1", "alice@example.com");
      CompletableFuturePath<TestUser> path = Path.futureCompleted(user);

      // We need to verify the response is written after completion
      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      // Give async processing time to complete
      Thread.sleep(100);

      assertThat(mockResponse.getStatus()).isEqualTo(HttpStatus.OK.value());
      assertThat(mockResponse.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);

      String json = mockResponse.getContentAsString();
      assertThat(json).contains("\"id\":\"1\"");
      assertThat(json).contains("\"email\":\"alice@example.com\"");
    }

    @Test
    @DisplayName("Should handle async supplier completion")
    void shouldHandleAsyncSupplierCompletion() throws Exception {
      CompletableFuturePath<Integer> path = Path.future(CompletableFuture.supplyAsync(() -> 42));

      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      // Give async processing time to complete
      Thread.sleep(200);

      assertThat(mockResponse.getStatus()).isEqualTo(HttpStatus.OK.value());
      assertThat(mockResponse.getContentAsString()).isEqualTo("42");
    }
  }

  @Nested
  @DisplayName("handleReturnValue - Failed Completion Tests")
  class FailedCompletionTests {

    @Test
    @DisplayName("Should handle failed future with HTTP 500 and hidden details")
    void shouldHandleFailedFutureWith500AndHiddenDetails() throws Exception {
      CompletableFuturePath<TestUser> path =
          Path.futureFailed(new RuntimeException("Async operation failed"));

      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      // Give async processing time to complete
      Thread.sleep(100);

      assertThat(mockResponse.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
      assertThat(mockResponse.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);

      String json = mockResponse.getContentAsString();
      assertThat(json).contains("\"success\":false");
      assertThat(json).contains("\"error\":\"An error occurred during async execution\"");
      assertThat(json).doesNotContain("Async operation failed");
    }

    @Test
    @DisplayName("Should include exception details when configured")
    void shouldIncludeExceptionDetailsWhenConfigured() throws Exception {
      CompletableFuturePathReturnValueHandler detailedHandler =
          new CompletableFuturePathReturnValueHandler(jsonMapper, 500, true, 30000);
      CompletableFuturePath<TestUser> path =
          Path.futureFailed(new IllegalArgumentException("Invalid async input"));

      detailedHandler.handleReturnValue(path, returnType, mavContainer, webRequest);

      // Give async processing time to complete
      Thread.sleep(100);

      String json = mockResponse.getContentAsString();
      assertThat(json).contains("\"success\":false");
      assertThat(json).contains("\"type\":\"IllegalArgumentException\"");
      assertThat(json).contains("\"message\":\"Invalid async input\"");
    }

    @Test
    @DisplayName("Should use custom failure status")
    void shouldUseCustomFailureStatus() throws Exception {
      CompletableFuturePathReturnValueHandler customHandler =
          new CompletableFuturePathReturnValueHandler(
              jsonMapper, HttpStatus.BAD_GATEWAY.value(), false, 30000);
      CompletableFuturePath<TestUser> path =
          Path.futureFailed(new RuntimeException("Upstream service failed"));

      customHandler.handleReturnValue(path, returnType, mavContainer, webRequest);

      // Give async processing time to complete
      Thread.sleep(100);

      assertThat(mockResponse.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("Should handle null returnValue gracefully")
    void shouldHandleNullReturnValue() throws Exception {
      handler.handleReturnValue(null, returnType, mavContainer, webRequest);

      assertThat(mavContainer.isRequestHandled()).isTrue();
      // Response status should still be default (200 for MockHttpServletResponse)
      assertThat(mockResponse.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("Should handle non-CompletableFuturePath returnValue gracefully")
    void shouldHandleNonCompletableFuturePathReturnValue() throws Exception {
      handler.handleReturnValue(
          "not a CompletableFuturePath", returnType, mavContainer, webRequest);

      assertThat(mavContainer.isRequestHandled()).isTrue();
      assertThat(mockResponse.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("Should handle null response gracefully")
    void shouldHandleNullResponseGracefully() throws Exception {
      // Create a webRequest without a response
      ServletWebRequest requestOnly = new ServletWebRequest(mockRequest);
      CompletableFuturePath<String> path = Path.futureCompleted("test");

      handler.handleReturnValue(path, returnType, mavContainer, requestOnly);

      assertThat(mavContainer.isRequestHandled()).isTrue();
    }
  }

  @Nested
  @DisplayName("Timeout Configuration Tests")
  class TimeoutConfigurationTests {

    @Test
    @DisplayName("Should create handler with no timeout when timeoutMillis is 0")
    void shouldCreateHandlerWithNoTimeoutWhenZero() {
      CompletableFuturePathReturnValueHandler noTimeoutHandler =
          new CompletableFuturePathReturnValueHandler(jsonMapper, 500, false, 0);

      // Handler should be created successfully
      assertThat(noTimeoutHandler).isNotNull();
    }

    @Test
    @DisplayName("Should create handler with custom timeout")
    void shouldCreateHandlerWithCustomTimeout() {
      CompletableFuturePathReturnValueHandler customTimeoutHandler =
          new CompletableFuturePathReturnValueHandler(jsonMapper, 500, false, 60000);

      // Handler should be created successfully
      assertThat(customTimeoutHandler).isNotNull();
    }
  }

  // Test DTOs
  record TestUser(String id, String email) {}
}
