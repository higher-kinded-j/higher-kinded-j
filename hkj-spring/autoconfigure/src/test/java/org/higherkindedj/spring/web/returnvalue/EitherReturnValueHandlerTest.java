// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.higherkindedj.hkt.either.Either;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("EitherReturnValueHandler Tests")
class EitherReturnValueHandlerTest {

  @Mock private HttpServletResponse response;

  @Mock private NativeWebRequest webRequest;

  @Mock private MethodParameter returnType;

  @Mock private ModelAndViewContainer mavContainer;

  private EitherReturnValueHandler handler;
  private StringWriter stringWriter;
  private PrintWriter printWriter;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() throws Exception {
    objectMapper = new ObjectMapper();
    handler = new EitherReturnValueHandler(objectMapper, HttpStatus.BAD_REQUEST.value());

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
    @DisplayName("Should support Either return type")
    void shouldSupportEitherReturnType() {
      when(returnType.getParameterType()).thenReturn((Class) Either.class);

      boolean result = handler.supportsReturnType(returnType);

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should not support non-Either return type")
    void shouldNotSupportNonEitherReturnType() {
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
      Either<String, TestUser> either = Either.right(user);

      handler.handleReturnValue(either, returnType, mavContainer, webRequest);

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
      Either<String, TestUser> either = Either.right(new TestUser("1", "test@example.com"));

      handler.handleReturnValue(either, returnType, mavContainer, webRequest);

      verify(mavContainer).setRequestHandled(true);
      verify(response, never()).setStatus(anyInt());
    }

    @Test
    @DisplayName("Should serialize Right value with complex object")
    void shouldSerializeRightValueWithComplexObject() throws Exception {
      TestComplexObject obj = new TestComplexObject("test", 42, true);
      Either<String, TestComplexObject> either = Either.right(obj);

      handler.handleReturnValue(either, returnType, mavContainer, webRequest);

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
      Either<String, TestUser> either = Either.left("Error occurred");

      handler.handleReturnValue(either, returnType, mavContainer, webRequest);

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
      Either<TestUserNotFoundError, TestUser> either = Either.left(error);

      handler.handleReturnValue(either, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.NOT_FOUND.value());

      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).contains("\"success\":false");
    }

    @Test
    @DisplayName("Should handle ValidationError with HTTP 400")
    void shouldHandleValidationErrorWith400() throws Exception {
      TestValidationError error = new TestValidationError("Invalid input");
      Either<TestValidationError, TestUser> either = Either.left(error);

      handler.handleReturnValue(either, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.BAD_REQUEST.value());

      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).contains("\"success\":false");
    }

    @Test
    @DisplayName("Should handle AuthorizationError with HTTP 403")
    void shouldHandleAuthorizationErrorWith403() throws Exception {
      TestAuthorizationError error = new TestAuthorizationError("Access denied");
      Either<TestAuthorizationError, TestUser> either = Either.left(error);

      handler.handleReturnValue(either, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.FORBIDDEN.value());
    }

    @Test
    @DisplayName("Should handle AuthenticationError with HTTP 401")
    void shouldHandleAuthenticationErrorWith401() throws Exception {
      TestAuthenticationError error = new TestAuthenticationError("Not authenticated");
      Either<TestAuthenticationError, TestUser> either = Either.left(error);

      handler.handleReturnValue(either, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
    }
  }

  @Nested
  @DisplayName("Custom Default Status Tests")
  class CustomDefaultStatusTests {

    @Test
    @DisplayName("Should use custom default error status")
    void shouldUseCustomDefaultErrorStatus() throws Exception {
      EitherReturnValueHandler customHandler = new EitherReturnValueHandler(objectMapper, 500);
      Either<String, TestUser> either = Either.left("Generic error");

      customHandler.handleReturnValue(either, returnType, mavContainer, webRequest);

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
    @DisplayName("Should handle non-Either returnValue gracefully")
    void shouldHandleNonEitherReturnValue() throws Exception {
      String notAnEither = "just a string";

      handler.handleReturnValue(notAnEither, returnType, mavContainer, webRequest);

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
