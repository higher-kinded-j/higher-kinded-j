// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.higherkindedj.hkt.effect.MaybePath;
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
 * Tests for {@link MaybePathReturnValueHandler}.
 *
 * <p>Verifies HTTP response mapping for MaybePath return types.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MaybePathReturnValueHandler Tests")
class MaybePathReturnValueHandlerTest {

  @Mock private HttpServletResponse response;

  @Mock private NativeWebRequest webRequest;

  @Mock private MethodParameter returnType;

  @Mock private ModelAndViewContainer mavContainer;

  private MaybePathReturnValueHandler handler;
  private StringWriter stringWriter;
  private PrintWriter printWriter;
  private JsonMapper jsonMapper;

  @BeforeEach
  void setUp() throws Exception {
    jsonMapper = JsonMapper.builder().build();
    handler = new MaybePathReturnValueHandler(jsonMapper, HttpStatus.NOT_FOUND.value());

    stringWriter = new StringWriter();
    printWriter = new PrintWriter(stringWriter);

    lenient().when(webRequest.getNativeResponse(HttpServletResponse.class)).thenReturn(response);
    lenient().when(response.getWriter()).thenReturn(printWriter);
  }

  @Nested
  @DisplayName("supportsReturnType Tests")
  class SupportsReturnTypeTests {

    @Test
    @DisplayName("Should support MaybePath return type")
    void shouldSupportMaybePathReturnType() {
      when(returnType.getParameterType()).thenReturn((Class) MaybePath.class);

      boolean result = handler.supportsReturnType(returnType);

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should not support non-MaybePath return type")
    void shouldNotSupportNonMaybePathReturnType() {
      when(returnType.getParameterType()).thenReturn((Class) String.class);

      boolean result = handler.supportsReturnType(returnType);

      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("handleReturnValue - Just (Present) Tests")
  class JustValueTests {

    @Test
    @DisplayName("Should handle Just value with HTTP 200")
    void shouldHandleJustValueWith200() throws Exception {
      TestUser user = new TestUser("1", "alice@example.com");
      MaybePath<TestUser> path = Path.just(user);

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
    @DisplayName("Should serialize Just value with primitive")
    void shouldSerializeJustValueWithPrimitive() throws Exception {
      MaybePath<Integer> path = Path.just(42);

      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.OK.value());
      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).isEqualTo("42");
    }

    @Test
    @DisplayName("Should serialize Just value with string")
    void shouldSerializeJustValueWithString() throws Exception {
      MaybePath<String> path = Path.just("hello world");

      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.OK.value());
      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).isEqualTo("\"hello world\"");
    }
  }

  @Nested
  @DisplayName("handleReturnValue - Nothing (Absent) Tests")
  class NothingValueTests {

    @Test
    @DisplayName("Should handle Nothing with HTTP 404")
    void shouldHandleNothingWith404() throws Exception {
      MaybePath<TestUser> path = Path.nothing();

      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.NOT_FOUND.value());
      verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);

      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).contains("\"success\":false");
      assertThat(json).contains("\"error\":\"Resource not found\"");
    }

    @Test
    @DisplayName("Should handle Nothing with custom status")
    void shouldHandleNothingWithCustomStatus() throws Exception {
      MaybePathReturnValueHandler customHandler =
          new MaybePathReturnValueHandler(jsonMapper, HttpStatus.NO_CONTENT.value());
      MaybePath<TestUser> path = Path.nothing();

      customHandler.handleReturnValue(path, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.NO_CONTENT.value());
    }
  }

  @Nested
  @DisplayName("handleReturnValue - From Nullable Tests")
  class FromNullableTests {

    @Test
    @DisplayName("Should handle maybe from non-null value")
    void shouldHandleMaybeFromNonNullValue() throws Exception {
      String value = "present";
      MaybePath<String> path = Path.maybe(value);

      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.OK.value());
      printWriter.flush();
      String json = stringWriter.toString();
      assertThat(json).isEqualTo("\"present\"");
    }

    @Test
    @DisplayName("Should handle maybe from null value")
    void shouldHandleMaybeFromNullValue() throws Exception {
      String value = null;
      MaybePath<String> path = Path.maybe(value);

      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.NOT_FOUND.value());
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
    @DisplayName("Should handle non-MaybePath returnValue gracefully")
    void shouldHandleNonMaybePathReturnValue() throws Exception {
      handler.handleReturnValue("not a MaybePath", returnType, mavContainer, webRequest);

      verify(mavContainer).setRequestHandled(true);
      verify(response, never()).setStatus(anyInt());
    }

    @Test
    @DisplayName("Should handle null response gracefully")
    void shouldHandleNullResponseGracefully() throws Exception {
      when(webRequest.getNativeResponse(HttpServletResponse.class)).thenReturn(null);
      MaybePath<String> path = Path.just("test");

      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      verify(mavContainer).setRequestHandled(true);
    }
  }

  // Test DTOs
  record TestUser(String id, String email) {}
}
