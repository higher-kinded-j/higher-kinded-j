// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.ValidationPath;
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
 * Tests for {@link ValidationPathReturnValueHandler}.
 *
 * <p>Verifies HTTP response mapping for ValidationPath return types with error accumulation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ValidationPathReturnValueHandler Tests")
class ValidationPathReturnValueHandlerTest {

  @Mock private HttpServletResponse response;

  @Mock private NativeWebRequest webRequest;

  @Mock private MethodParameter returnType;

  @Mock private ModelAndViewContainer mavContainer;

  private ValidationPathReturnValueHandler handler;
  private StringWriter stringWriter;
  private PrintWriter printWriter;
  private JsonMapper jsonMapper;

  @BeforeEach
  void setUp() throws Exception {
    jsonMapper = JsonMapper.builder().build();
    handler = new ValidationPathReturnValueHandler(jsonMapper, HttpStatus.BAD_REQUEST.value());

    stringWriter = new StringWriter();
    printWriter = new PrintWriter(stringWriter);

    lenient().when(webRequest.getNativeResponse(HttpServletResponse.class)).thenReturn(response);
    lenient().when(response.getWriter()).thenReturn(printWriter);
  }

  @Nested
  @DisplayName("supportsReturnType Tests")
  class SupportsReturnTypeTests {

    @Test
    @DisplayName("Should support ValidationPath return type")
    void shouldSupportValidationPathReturnType() {
      when(returnType.getParameterType()).thenReturn((Class) ValidationPath.class);

      boolean result = handler.supportsReturnType(returnType);

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should not support non-ValidationPath return type")
    void shouldNotSupportNonValidationPathReturnType() {
      when(returnType.getParameterType()).thenReturn((Class) String.class);

      boolean result = handler.supportsReturnType(returnType);

      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("handleReturnValue - Valid Tests")
  class ValidValueTests {

    @Test
    @DisplayName("Should handle Valid value with HTTP 200")
    void shouldHandleValidValueWith200() throws Exception {
      TestUser user = new TestUser("1", "alice@example.com");
      ValidationPath<List<String>, TestUser> path = Path.valid(user, Semigroups.list());

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
    @DisplayName("Should serialize Valid value with primitive")
    void shouldSerializeValidValueWithPrimitive() throws Exception {
      ValidationPath<List<String>, Integer> path = Path.valid(42, Semigroups.list());

      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.OK.value());
      printWriter.flush();
      assertThat(stringWriter.toString()).isEqualTo("42");
    }
  }

  @Nested
  @DisplayName("handleReturnValue - Invalid Tests")
  class InvalidValueTests {

    @Test
    @DisplayName("Should handle Invalid with single error")
    void shouldHandleInvalidWithSingleError() throws Exception {
      List<String> errors = List.of("Email is required");
      ValidationPath<List<String>, TestUser> path = Path.invalid(errors, Semigroups.list());

      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.BAD_REQUEST.value());
      verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);

      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).contains("\"valid\":false");
      assertThat(json).contains("\"errors\"");
      assertThat(json).contains("\"Email is required\"");
      assertThat(json).contains("\"errorCount\":1");
    }

    @Test
    @DisplayName("Should handle Invalid with multiple accumulated errors")
    void shouldHandleInvalidWithMultipleErrors() throws Exception {
      List<String> errors =
          List.of("Email is required", "Name is too short", "Age must be positive");
      ValidationPath<List<String>, TestUser> path = Path.invalid(errors, Semigroups.list());

      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.BAD_REQUEST.value());

      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).contains("\"valid\":false");
      assertThat(json).contains("\"errorCount\":3");
      assertThat(json).contains("\"Email is required\"");
      assertThat(json).contains("\"Name is too short\"");
      assertThat(json).contains("\"Age must be positive\"");
    }

    @Test
    @DisplayName("Should use custom invalid status")
    void shouldUseCustomInvalidStatus() throws Exception {
      ValidationPathReturnValueHandler customHandler =
          new ValidationPathReturnValueHandler(jsonMapper, HttpStatus.UNPROCESSABLE_ENTITY.value());
      List<String> errors = List.of("Invalid format");
      ValidationPath<List<String>, TestUser> path = Path.invalid(errors, Semigroups.list());

      customHandler.handleReturnValue(path, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
    }

    @Test
    @DisplayName("Should handle Invalid with structured error objects")
    void shouldHandleInvalidWithStructuredErrors() throws Exception {
      List<ValidationError> errors =
          List.of(
              new ValidationError("email", "Email is required"),
              new ValidationError("password", "Password too weak"));
      ValidationPath<List<ValidationError>, TestUser> path =
          Path.invalid(errors, Semigroups.list());

      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).contains("\"field\":\"email\"");
      assertThat(json).contains("\"field\":\"password\"");
      assertThat(json).contains("\"errorCount\":2");
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
    @DisplayName("Should handle non-ValidationPath returnValue gracefully")
    void shouldHandleNonValidationPathReturnValue() throws Exception {
      handler.handleReturnValue("not a ValidationPath", returnType, mavContainer, webRequest);

      verify(mavContainer).setRequestHandled(true);
      verify(response, never()).setStatus(anyInt());
    }

    @Test
    @DisplayName("Should count single non-collection error as 1")
    void shouldCountSingleNonCollectionErrorAsOne() throws Exception {
      // Using a single string error instead of a list
      ValidationPath<String, TestUser> path = Path.invalid("Single error", Semigroups.string());

      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).contains("\"errorCount\":1");
    }
  }

  // Test DTOs
  record TestUser(String id, String email) {}

  record ValidationError(String field, String message) {}
}
