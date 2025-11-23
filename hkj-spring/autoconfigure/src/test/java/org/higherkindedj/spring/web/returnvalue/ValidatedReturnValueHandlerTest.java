// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import org.higherkindedj.hkt.validated.Validated;
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
@DisplayName("ValidatedReturnValueHandler Tests")
class ValidatedReturnValueHandlerTest {

  @Mock private HttpServletResponse response;

  @Mock private NativeWebRequest webRequest;

  @Mock private MethodParameter returnType;

  @Mock private ModelAndViewContainer mavContainer;

  private ValidatedReturnValueHandler handler;
  private StringWriter stringWriter;
  private PrintWriter printWriter;

  @BeforeEach
  void setUp() throws Exception {
    handler = new ValidatedReturnValueHandler();

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
    @DisplayName("Should support Validated return type")
    void shouldSupportValidatedReturnType() {
      when(returnType.getParameterType()).thenReturn((Class) Validated.class);

      boolean result = handler.supportsReturnType(returnType);

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should not support non-Validated return type")
    void shouldNotSupportNonValidatedReturnType() {
      when(returnType.getParameterType()).thenReturn((Class) String.class);

      boolean result = handler.supportsReturnType(returnType);

      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("handleReturnValue - Valid (Success) Tests")
  class ValidValueTests {

    @Test
    @DisplayName("Should handle Valid value with HTTP 200")
    void shouldHandleValidValueWith200() throws Exception {
      TestUser user = new TestUser("1", "alice@example.com");
      Validated<String, TestUser> validated = Validated.valid(user);

      handler.handleReturnValue(validated, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.OK.value());
      verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
      verify(mavContainer).setRequestHandled(true);

      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).contains("\"id\":\"1\"");
      assertThat(json).contains("\"email\":\"alice@example.com\"");
    }

    @Test
    @DisplayName("Should handle Valid value with complex object")
    void shouldHandleValidValueWithComplexObject() throws Exception {
      TestComplexObject obj = new TestComplexObject("test", 42, true);
      Validated<String, TestComplexObject> validated = Validated.valid(obj);

      handler.handleReturnValue(validated, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.OK.value());

      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).contains("\"name\":\"test\"");
      assertThat(json).contains("\"value\":42");
      assertThat(json).contains("\"active\":true");
    }

    @Test
    @DisplayName("Should handle Valid value with null response gracefully")
    void shouldHandleValidValueWithNullResponse() throws Exception {
      when(webRequest.getNativeResponse(HttpServletResponse.class)).thenReturn(null);
      Validated<String, TestUser> validated =
          Validated.valid(new TestUser("1", "test@example.com"));

      handler.handleReturnValue(validated, returnType, mavContainer, webRequest);

      verify(mavContainer).setRequestHandled(true);
      verify(response, never()).setStatus(anyInt());
    }
  }

  @Nested
  @DisplayName("handleReturnValue - Invalid (Error) Tests")
  class InvalidValueTests {

    @Test
    @DisplayName("Should handle Invalid with single error")
    void shouldHandleInvalidWithSingleError() throws Exception {
      TestValidationError error = new TestValidationError("email", "Invalid format");
      Validated<TestValidationError, TestUser> validated = Validated.invalid(error);

      handler.handleReturnValue(validated, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.BAD_REQUEST.value());
      verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);

      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).contains("\"valid\":false");
      assertThat(json).contains("\"errors\"");
    }

    @Test
    @DisplayName("Should handle Invalid with multiple errors (accumulated)")
    void shouldHandleInvalidWithMultipleErrors() throws Exception {
      List<TestValidationError> errors =
          List.of(
              new TestValidationError("email", "Invalid email format"),
              new TestValidationError("firstName", "First name required"),
              new TestValidationError("lastName", "Last name required"));
      Validated<List<TestValidationError>, TestUser> validated = Validated.invalid(errors);

      handler.handleReturnValue(validated, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.BAD_REQUEST.value());

      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).contains("\"valid\":false");
      assertThat(json).contains("\"errors\"");
      assertThat(json).contains("email");
      assertThat(json).contains("firstName");
      assertThat(json).contains("lastName");
    }

    @Test
    @DisplayName("Should handle Invalid with string error")
    void shouldHandleInvalidWithStringError() throws Exception {
      Validated<String, TestUser> validated = Validated.invalid("Validation failed");

      handler.handleReturnValue(validated, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.BAD_REQUEST.value());

      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).contains("\"valid\":false");
      assertThat(json).contains("\"errors\":\"Validation failed\"");
    }

    @Test
    @DisplayName("Should handle Invalid with list of strings")
    void shouldHandleInvalidWithListOfStrings() throws Exception {
      List<String> errors = List.of("Error 1", "Error 2", "Error 3");
      Validated<List<String>, TestUser> validated = Validated.invalid(errors);

      handler.handleReturnValue(validated, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.BAD_REQUEST.value());

      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).contains("\"valid\":false");
      assertThat(json).contains("Error 1");
      assertThat(json).contains("Error 2");
      assertThat(json).contains("Error 3");
    }
  }

  @Nested
  @DisplayName("Error Accumulation Tests")
  class ErrorAccumulationTests {

    @Test
    @DisplayName("Should demonstrate error accumulation vs fail-fast")
    void shouldDemonstrateErrorAccumulation() throws Exception {
      // This is what makes Validated different from Either
      // All three errors are accumulated and returned together
      List<TestValidationError> errors =
          List.of(
              new TestValidationError("email", "Invalid email format"),
              new TestValidationError("firstName", "First name cannot be empty"),
              new TestValidationError("lastName", "Last name cannot be empty"));
      Validated<List<TestValidationError>, TestUser> validated = Validated.invalid(errors);

      handler.handleReturnValue(validated, returnType, mavContainer, webRequest);

      printWriter.flush();
      String json = stringWriter.toString();

      // Verify ALL three errors are in the response
      assertThat(json).contains("Invalid email format");
      assertThat(json).contains("First name cannot be empty");
      assertThat(json).contains("Last name cannot be empty");
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
    @DisplayName("Should handle non-Validated returnValue gracefully")
    void shouldHandleNonValidatedReturnValue() throws Exception {
      String notValidated = "just a string";

      handler.handleReturnValue(notValidated, returnType, mavContainer, webRequest);

      verify(mavContainer).setRequestHandled(true);
      verify(response, never()).setStatus(anyInt());
    }
  }

  // Test DTOs
  record TestUser(String id, String email) {}

  record TestComplexObject(String name, int value, boolean active) {}

  record TestValidationError(String field, String message) {}
}
