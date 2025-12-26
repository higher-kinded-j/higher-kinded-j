// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.higherkindedj.hkt.effect.IOPath;
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
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tests for {@link IOPathReturnValueHandler}.
 *
 * <p>Verifies HTTP response mapping for IOPath return types with deferred execution.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IOPathReturnValueHandler Tests")
class IOPathReturnValueHandlerTest {

  @Mock private HttpServletResponse response;

  @Mock private NativeWebRequest webRequest;

  @Mock private MethodParameter returnType;

  @Mock private ModelAndViewContainer mavContainer;

  private IOPathReturnValueHandler handler;
  private StringWriter stringWriter;
  private PrintWriter printWriter;
  private JsonMapper jsonMapper;

  @BeforeEach
  void setUp() throws Exception {
    jsonMapper = JsonMapper.builder().build();
    handler =
        new IOPathReturnValueHandler(jsonMapper, HttpStatus.INTERNAL_SERVER_ERROR.value(), false);

    stringWriter = new StringWriter();
    printWriter = new PrintWriter(stringWriter);

    lenient().when(webRequest.getNativeResponse(HttpServletResponse.class)).thenReturn(response);
    lenient().when(response.getWriter()).thenReturn(printWriter);
  }

  @Nested
  @DisplayName("supportsReturnType Tests")
  class SupportsReturnTypeTests {

    @Test
    @DisplayName("Should support IOPath return type")
    void shouldSupportIOPathReturnType() {
      when(returnType.getParameterType()).thenReturn((Class) IOPath.class);

      boolean result = handler.supportsReturnType(returnType);

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should not support non-IOPath return type")
    void shouldNotSupportNonIOPathReturnType() {
      when(returnType.getParameterType()).thenReturn((Class) String.class);

      boolean result = handler.supportsReturnType(returnType);

      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("handleReturnValue - Successful Execution Tests")
  class SuccessfulExecutionTests {

    @Test
    @DisplayName("Should handle successful IO execution with HTTP 200")
    void shouldHandleSuccessfulIOExecutionWith200() throws Exception {
      TestUser user = new TestUser("1", "alice@example.com");
      IOPath<TestUser> path = Path.io(() -> user);

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
    @DisplayName("Should handle ioPure with HTTP 200")
    void shouldHandleIOPureWith200() throws Exception {
      IOPath<Integer> path = Path.ioPure(42);

      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.OK.value());
      printWriter.flush();
      assertThat(stringWriter.toString()).isEqualTo("42");
    }

    @Test
    @DisplayName("Should execute IO lazily at handler time")
    void shouldExecuteIOLazilyAtHandlerTime() throws Exception {
      int[] counter = {0};
      IOPath<Integer> path =
          Path.io(
              () -> {
                counter[0]++;
                return counter[0];
              });

      // IO should not be executed yet
      assertThat(counter[0]).isEqualTo(0);

      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      // IO should be executed once during handling
      assertThat(counter[0]).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("handleReturnValue - Failed Execution Tests")
  class FailedExecutionTests {

    @Test
    @DisplayName("Should handle IO failure with HTTP 500 and hidden details")
    void shouldHandleIOFailureWith500AndHiddenDetails() throws Exception {
      IOPath<TestUser> path =
          Path.io(
              () -> {
                throw new RuntimeException("Database connection failed");
              });

      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
      verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);

      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).contains("\"success\":false");
      assertThat(json).contains("\"error\":\"An error occurred during execution\"");
      assertThat(json).doesNotContain("Database connection failed");
    }

    @Test
    @DisplayName("Should include exception details when configured")
    void shouldIncludeExceptionDetailsWhenConfigured() throws Exception {
      IOPathReturnValueHandler detailedHandler =
          new IOPathReturnValueHandler(jsonMapper, HttpStatus.INTERNAL_SERVER_ERROR.value(), true);
      IOPath<TestUser> path =
          Path.io(
              () -> {
                throw new IllegalStateException("Invalid state detected");
              });

      detailedHandler.handleReturnValue(path, returnType, mavContainer, webRequest);

      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).contains("\"success\":false");
      assertThat(json).contains("\"type\":\"IllegalStateException\"");
      assertThat(json).contains("\"message\":\"Invalid state detected\"");
    }

    @Test
    @DisplayName("Should use custom failure status")
    void shouldUseCustomFailureStatus() throws Exception {
      IOPathReturnValueHandler customHandler =
          new IOPathReturnValueHandler(jsonMapper, HttpStatus.SERVICE_UNAVAILABLE.value(), false);
      IOPath<TestUser> path =
          Path.io(
              () -> {
                throw new RuntimeException("Service unavailable");
              });

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
    @DisplayName("Should handle non-IOPath returnValue gracefully")
    void shouldHandleNonIOPathReturnValue() throws Exception {
      handler.handleReturnValue("not an IOPath", returnType, mavContainer, webRequest);

      verify(mavContainer).setRequestHandled(true);
      verify(response, never()).setStatus(anyInt());
    }

    @Test
    @DisplayName("Should handle exception with null message")
    void shouldHandleExceptionWithNullMessage() throws Exception {
      IOPathReturnValueHandler detailedHandler =
          new IOPathReturnValueHandler(jsonMapper, 500, true);
      IOPath<TestUser> path =
          Path.io(
              () -> {
                throw new RuntimeException((String) null);
              });

      detailedHandler.handleReturnValue(path, returnType, mavContainer, webRequest);

      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).contains("\"message\":\"No message\"");
    }

    @Test
    @DisplayName("Should handle null response gracefully")
    void shouldHandleNullResponseGracefully() throws Exception {
      when(webRequest.getNativeResponse(HttpServletResponse.class)).thenReturn(null);
      IOPath<String> path = Path.ioPure("test");

      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      verify(mavContainer).setRequestHandled(true);
    }
  }

  // Test DTOs
  record TestUser(String id, String email) {}
}
