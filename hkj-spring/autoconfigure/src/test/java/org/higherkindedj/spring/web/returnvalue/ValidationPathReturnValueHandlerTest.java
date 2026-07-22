// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.spring.web.returnvalue.ValidationPathReturnValueHandler.FieldErrorItem;
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
import tools.jackson.databind.JsonNode;
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
    handler =
        new ValidationPathReturnValueHandler(
            jsonMapper, HttpStatus.BAD_REQUEST.value(), HttpStatus.UNPROCESSABLE_CONTENT.value());

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
      doReturn(ValidationPath.class).when(returnType).getParameterType();

      boolean result = handler.supportsReturnType(returnType);

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should support raw Validated return type")
    void shouldSupportRawValidatedReturnType() {
      doReturn(Validated.class).when(returnType).getParameterType();

      boolean result = handler.supportsReturnType(returnType);

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should not support non-ValidationPath return type")
    void shouldNotSupportNonValidationPathReturnType() {
      doReturn(String.class).when(returnType).getParameterType();

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
          new ValidationPathReturnValueHandler(
              jsonMapper,
              HttpStatus.UNPROCESSABLE_CONTENT.value(),
              HttpStatus.UNPROCESSABLE_CONTENT.value());
      List<String> errors = List.of("Invalid format");
      ValidationPath<List<String>, TestUser> path = Path.invalid(errors, Semigroups.list());

      customHandler.handleReturnValue(path, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
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

    @Test
    @DisplayName("Should count every element of a NonEmptyList error payload")
    void shouldCountNonEmptyListErrors() throws Exception {
      // NonEmptyList is Iterable but not a Collection — must not collapse to errorCount 1
      ValidationPath<NonEmptyList<String>, TestUser> path =
          Path.invalid(
              NonEmptyList.of("Email invalid", "Name blank", "Age negative"),
              NonEmptyList.semigroup());

      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).contains("\"errorCount\":3");
    }
  }

  @Nested
  @DisplayName("HttpHeaderCarrier integration")
  class HeaderInjectionTests {

    @Test
    @DisplayName("Each carrier element of an Invalid collection contributes its headers")
    void carriersInCollectionAccumulateHeaders() throws Exception {
      // WWW-Authenticate is multi-valued per RFC 7235 — each challenge is a separate header line.
      ValidationPath<List<AuthChallengeViolation>, TestUser> path =
          Path.invalid(
              List.of(new AuthChallengeViolation("Basic"), new AuthChallengeViolation("Bearer")),
              Semigroups.list());

      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      verify(response).addHeader("WWW-Authenticate", "Basic");
      verify(response).addHeader("WWW-Authenticate", "Bearer");
    }
  }

  @Nested
  @DisplayName("FieldError payloads - the 422 leg")
  class FieldErrorPayloadTests {

    @Test
    @DisplayName(
        "NonEmptyList of FieldErrors from a raw Validated renders 422 with path-keyed items")
    void nonEmptyListOfFieldErrorsFromRawValidated() throws Exception {
      Validated<NonEmptyList<FieldError>, TestUser> invalid =
          Validated.invalid(
              NonEmptyList.of(
                  FieldError.of("not an email address").at("email"),
                  FieldError.of("must be 5 digits").at("zip").at("address")));

      handler.handleReturnValue(invalid, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
      verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);

      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).contains("\"valid\":false");
      assertThat(json).contains("\"errorCount\":2");
      assertThat(json).contains("\"path\":\"email\"");
      assertThat(json).contains("\"path\":\"address.zip\"");
      assertThat(json).contains("\"segments\":[\"address\",\"zip\"]");
      assertThat(json).contains("\"message\":\"must be 5 digits\"");
    }

    @Test
    @DisplayName("The same payload through a ValidationPath renders identically")
    void fieldErrorsThroughValidationPath() throws Exception {
      ValidationPath<NonEmptyList<FieldError>, TestUser> path =
          Path.invalid(
              NonEmptyList.of(FieldError.of("not an email address").at("email")),
              NonEmptyList.semigroup());

      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).contains("\"path\":\"email\"");
      assertThat(json).contains("\"segments\":[\"email\"]");
      assertThat(json).contains("\"errorCount\":1");
    }

    @Test
    @DisplayName("A plain List of FieldErrors renders 422")
    void plainListOfFieldErrors() throws Exception {
      Validated<List<FieldError>, TestUser> invalid =
          Validated.invalid(List.of(FieldError.of("required").at("name")));

      handler.handleReturnValue(invalid, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
      printWriter.flush();
      assertThat(stringWriter.toString()).contains("\"path\":\"name\"");
    }

    @Test
    @DisplayName("An array of FieldErrors renders 422")
    void arrayOfFieldErrors() throws Exception {
      Validated<FieldError[], TestUser> invalid =
          Validated.invalid(new FieldError[] {FieldError.of("required").at("name")});

      handler.handleReturnValue(invalid, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
      printWriter.flush();
      assertThat(stringWriter.toString()).contains("\"path\":\"name\"");
    }

    @Test
    @DisplayName("A single bare FieldError renders 422 as a one-element list")
    void singleBareFieldError() throws Exception {
      Validated<FieldError, TestUser> invalid =
          Validated.invalid(FieldError.of("not an email address").at("email"));

      handler.handleReturnValue(invalid, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).contains("\"path\":\"email\"");
      assertThat(json).contains("\"errorCount\":1");
    }

    @Test
    @DisplayName("An empty error collection falls back to the generic rendering and status")
    void emptyPayloadFallsBack() throws Exception {
      Validated<List<FieldError>, TestUser> invalid = Validated.invalid(List.of());

      handler.handleReturnValue(invalid, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.BAD_REQUEST.value());
      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).contains("\"errorCount\":0");
      assertThat(json).doesNotContain("\"segments\"");
    }

    @Test
    @DisplayName("A mixed payload falls back to the generic rendering and status")
    void mixedPayloadFallsBack() throws Exception {
      Validated<List<Object>, TestUser> invalid =
          Validated.invalid(List.of(FieldError.of("required").at("name"), "a plain error"));

      handler.handleReturnValue(invalid, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.BAD_REQUEST.value());
      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).contains("\"a plain error\"");
      assertThat(json).doesNotContain("\"segments\"");
      assertThat(json).contains("\"errorCount\":2");
    }

    @Test
    @DisplayName("An unlabelled FieldError renders an empty path and no segments")
    void unlabelledFieldError() throws Exception {
      Validated<NonEmptyList<FieldError>, TestUser> invalid =
          Validated.invalid(NonEmptyList.of(FieldError.of("something went wrong")));

      handler.handleReturnValue(invalid, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).contains("\"path\":\"\"");
      assertThat(json).contains("\"segments\":[]");
    }

    @Test
    @DisplayName("A dotted key is ambiguous in path but exact in segments - and round-trips")
    void dottedKeyStaysExactInSegments() throws Exception {
      // "a.b" is ONE segment; the rendered path cannot distinguish it from nesting (#621),
      // so segments is the lossless location - proven by rebuilding the FieldError from the body.
      FieldError original = FieldError.of("not an email address").at("a.b");
      Validated<NonEmptyList<FieldError>, TestUser> invalid =
          Validated.invalid(NonEmptyList.of(original));

      handler.handleReturnValue(invalid, returnType, mavContainer, webRequest);

      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).contains("\"path\":\"a.b\"");
      assertThat(json).contains("\"segments\":[\"a.b\"]");

      JsonNode errorNode = jsonMapper.readTree(json).get("errors").get(0);
      FieldErrorItem item = jsonMapper.treeToValue(errorNode, FieldErrorItem.class);
      assertThat(new FieldError(item.segments(), item.message())).isEqualTo(original);
    }

    @Test
    @DisplayName("The backward-compatible two-arg constructor defaults FieldError payloads to 422")
    void twoArgConstructorDefaultsFieldErrorsTo422() throws Exception {
      ValidationPathReturnValueHandler compatHandler =
          new ValidationPathReturnValueHandler(jsonMapper, HttpStatus.BAD_REQUEST.value());
      Validated<NonEmptyList<FieldError>, TestUser> invalid =
          Validated.invalid(NonEmptyList.of(FieldError.of("required").at("name")));

      compatHandler.handleReturnValue(invalid, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
    }

    @Test
    @DisplayName("The field-error status knob applies, not the generic invalid status")
    void fieldErrorStatusKnobApplies() throws Exception {
      // Swap the two statuses: a FieldError payload must pick up fieldErrorStatus (here 400),
      // proving the selection is by shape rather than falling through to invalidStatus.
      ValidationPathReturnValueHandler swappedHandler =
          new ValidationPathReturnValueHandler(
              jsonMapper, HttpStatus.UNPROCESSABLE_CONTENT.value(), HttpStatus.BAD_REQUEST.value());
      Validated<NonEmptyList<FieldError>, TestUser> invalid =
          Validated.invalid(NonEmptyList.of(FieldError.of("required").at("name")));

      swappedHandler.handleReturnValue(invalid, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.BAD_REQUEST.value());
    }
  }

  /** Validation error that doubles as a {@code WWW-Authenticate} challenge carrier. */
  record AuthChallengeViolation(String scheme) implements HttpHeaderCarrier {
    @Override
    public Map<String, String> headers() {
      return Map.of("WWW-Authenticate", scheme);
    }
  }

  // Test DTOs
  record TestUser(String id, String email) {}

  record ValidationError(String field, String message) {}
}
