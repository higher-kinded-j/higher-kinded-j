// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.higherkindedj.hkt.effect.EitherPath;
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
 * Tests for {@link EitherPathReturnValueHandler}.
 *
 * <p>Verifies HTTP response mapping for EitherPath return types using Jackson 3.x.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EitherPathReturnValueHandler Tests")
class EitherPathReturnValueHandlerTest {

  @Mock private HttpServletResponse response;

  @Mock private NativeWebRequest webRequest;

  @Mock private MethodParameter returnType;

  @Mock private ModelAndViewContainer mavContainer;

  private EitherPathReturnValueHandler handler;
  private StringWriter stringWriter;
  private PrintWriter printWriter;
  private JsonMapper jsonMapper;

  @BeforeEach
  void setUp() throws Exception {
    jsonMapper = JsonMapper.builder().build();
    handler = new EitherPathReturnValueHandler(jsonMapper, HttpStatus.BAD_REQUEST.value());

    stringWriter = new StringWriter();
    printWriter = new PrintWriter(stringWriter);

    // Lenient stubs - not all tests use handleReturnValue
    lenient().when(webRequest.getNativeResponse(HttpServletResponse.class)).thenReturn(response);
    lenient().when(response.getWriter()).thenReturn(printWriter);
  }

  @Nested
  @DisplayName("supportsReturnType Tests")
  class SupportsReturnTypeTests {

    @Test
    @DisplayName("Should support EitherPath return type")
    void shouldSupportEitherPathReturnType() {
      when(returnType.getParameterType()).thenReturn((Class) EitherPath.class);

      boolean result = handler.supportsReturnType(returnType);

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should not support non-EitherPath return type")
    void shouldNotSupportNonEitherPathReturnType() {
      when(returnType.getParameterType()).thenReturn((Class) String.class);

      boolean result = handler.supportsReturnType(returnType);

      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("handleReturnValue - Right (Success) Tests")
  class RightValueTests {

    @Test
    @DisplayName("Should handle Right value with HTTP 200")
    void shouldHandleRightValueWith200() throws Exception {
      TestUser user = new TestUser("1", "alice@example.com");
      EitherPath<String, TestUser> path = Path.right(user);

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
    @DisplayName("Should handle Right value with null response gracefully")
    void shouldHandleRightValueWithNullResponse() throws Exception {
      when(webRequest.getNativeResponse(HttpServletResponse.class)).thenReturn(null);
      EitherPath<String, TestUser> path = Path.right(new TestUser("1", "test@example.com"));

      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      verify(mavContainer).setRequestHandled(true);
      verify(response, never()).setStatus(anyInt());
    }

    @Test
    @DisplayName("Should serialize Right value with complex object")
    void shouldSerializeRightValueWithComplexObject() throws Exception {
      TestComplexObject obj = new TestComplexObject("test", 42, true);
      EitherPath<String, TestComplexObject> path = Path.right(obj);

      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).contains("\"name\":\"test\"");
      assertThat(json).contains("\"value\":42");
      assertThat(json).contains("\"active\":true");
    }
  }

  @Nested
  @DisplayName("handleReturnValue - Left (Error) Tests")
  class LeftValueTests {

    @Test
    @DisplayName("Should handle Left value with default error status")
    void shouldHandleLeftValueWithDefaultStatus() throws Exception {
      EitherPath<String, TestUser> path = Path.left("Error occurred");

      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.BAD_REQUEST.value());
      verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);

      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).contains("\"success\":false");
      assertThat(json).contains("\"error\":\"Error occurred\"");
    }

    @Test
    @DisplayName("Should handle UserNotFoundError with HTTP 404")
    void shouldHandleUserNotFoundErrorWith404() throws Exception {
      TestUserNotFoundError error = new TestUserNotFoundError("123");
      EitherPath<TestUserNotFoundError, TestUser> path = Path.left(error);

      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.NOT_FOUND.value());

      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).contains("\"success\":false");
    }

    @Test
    @DisplayName("Should handle ValidationError with HTTP 400")
    void shouldHandleValidationErrorWith400() throws Exception {
      TestValidationError error = new TestValidationError("Invalid input");
      EitherPath<TestValidationError, TestUser> path = Path.left(error);

      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.BAD_REQUEST.value());

      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).contains("\"success\":false");
    }

    @Test
    @DisplayName("Should handle AuthorizationError with HTTP 403")
    void shouldHandleAuthorizationErrorWith403() throws Exception {
      TestAuthorizationError error = new TestAuthorizationError("Access denied");
      EitherPath<TestAuthorizationError, TestUser> path = Path.left(error);

      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.FORBIDDEN.value());
    }

    @Test
    @DisplayName("Should handle AuthenticationError with HTTP 401")
    void shouldHandleAuthenticationErrorWith401() throws Exception {
      TestAuthenticationError error = new TestAuthenticationError("Not authenticated");
      EitherPath<TestAuthenticationError, TestUser> path = Path.left(error);

      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
    }
  }

  @Nested
  @DisplayName("Custom Default Status Tests")
  class CustomDefaultStatusTests {

    @Test
    @DisplayName("Should use custom default error status")
    void shouldUseCustomDefaultErrorStatus() throws Exception {
      EitherPathReturnValueHandler customHandler =
          new EitherPathReturnValueHandler(jsonMapper, 500);
      EitherPath<String, TestUser> path = Path.left("Generic error");

      customHandler.handleReturnValue(path, returnType, mavContainer, webRequest);

      verify(response).setStatus(500);
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
    @DisplayName("Should handle non-EitherPath returnValue gracefully")
    void shouldHandleNonEitherPathReturnValue() throws Exception {
      String notAnEitherPath = "just a string";

      handler.handleReturnValue(notAnEitherPath, returnType, mavContainer, webRequest);

      verify(mavContainer).setRequestHandled(true);
      verify(response, never()).setStatus(anyInt());
    }
  }

  // Test DTOs
  record TestUser(String id, String email) {}

  record TestComplexObject(String name, int value, boolean active) {}

  record TestUserNotFoundError(String userId) {}

  record TestValidationError(String message) {}

  record TestAuthorizationError(String message) {}

  record TestAuthenticationError(String message) {}
}
