// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.Map;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.effect.EitherOrBothPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;
import tools.jackson.databind.json.JsonMapper;

/** Tests for {@link EitherOrBothPathReturnValueHandler}. */
@ExtendWith(MockitoExtension.class)
@DisplayName("EitherOrBothPathReturnValueHandler Tests")
class EitherOrBothPathReturnValueHandlerTest {

  private static final Semigroup<String> SG = Semigroups.string();
  private static final String WARN = EitherOrBothPathReturnValueHandler.WARNINGS_HEADER;

  @Mock private HttpServletResponse response;
  @Mock private NativeWebRequest webRequest;
  @Mock private MethodParameter returnType;
  @Mock private ModelAndViewContainer mavContainer;

  private EitherOrBothPathReturnValueHandler handler;
  private StringWriter stringWriter;
  private PrintWriter printWriter;
  private JsonMapper jsonMapper;

  @BeforeEach
  void setUp() throws Exception {
    jsonMapper = JsonMapper.builder().build();
    handler = new EitherOrBothPathReturnValueHandler(jsonMapper, HttpStatus.BAD_REQUEST.value());
    stringWriter = new StringWriter();
    printWriter = new PrintWriter(stringWriter);
    lenient().when(webRequest.getNativeResponse(HttpServletResponse.class)).thenReturn(response);
    lenient().when(response.getWriter()).thenReturn(printWriter);
  }

  private String body() {
    printWriter.flush();
    return stringWriter.toString();
  }

  @Nested
  @DisplayName("supportsReturnType")
  class SupportsReturnType {

    @Test
    void supportsEitherOrBothPath() {
      doReturn(EitherOrBothPath.class).when(returnType).getParameterType();
      assertThat(handler.supportsReturnType(returnType)).isTrue();
    }

    @Test
    void supportsRawEitherOrBoth() {
      doReturn(EitherOrBoth.class).when(returnType).getParameterType();
      assertThat(handler.supportsReturnType(returnType)).isTrue();
    }

    @Test
    void rejectsOtherTypes() {
      doReturn(String.class).when(returnType).getParameterType();
      assertThat(handler.supportsReturnType(returnType)).isFalse();
    }
  }

  @Nested
  @DisplayName("Right (clean success)")
  class RightTests {

    @Test
    void rightYields200WithBodyAndNoWarningsHeader() {
      handler.handleReturnValue(
          Path.right(new User("1", "a@b.com"), SG), returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.OK.value());
      verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
      verify(mavContainer).setRequestHandled(true);
      verify(response, never()).setHeader(eq(WARN), anyString());
      assertThat(body()).contains("\"id\":\"1\"").contains("\"email\":\"a@b.com\"");
    }

    @Test
    void rawEitherOrBothIsAlsoHandled() {
      handler.handleReturnValue(
          EitherOrBoth.<String, Integer>right(7), returnType, mavContainer, webRequest);
      verify(response).setStatus(HttpStatus.OK.value());
      assertThat(body()).contains("7");
    }

    @Test
    void nullResponseIsHandledGracefully() {
      when(webRequest.getNativeResponse(HttpServletResponse.class)).thenReturn(null);
      handler.handleReturnValue(Path.right(1, SG), returnType, mavContainer, webRequest);
      verify(mavContainer).setRequestHandled(true);
      verify(response, never()).setStatus(anyInt());
    }
  }

  @Nested
  @DisplayName("Both (success with warnings)")
  class BothTests {

    @Test
    void bothYields200WithBodyAndWarningsHeader() {
      handler.handleReturnValue(
          Path.both("deprecated-key", new User("1", "a@b.com"), SG),
          returnType,
          mavContainer,
          webRequest);

      verify(response).setStatus(HttpStatus.OK.value());
      verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
      verify(response).setHeader(WARN, "\"deprecated-key\"");
      // The body is the bare value, exactly as for Right.
      assertThat(body()).contains("\"id\":\"1\"").doesNotContain("deprecated-key");
    }

    @Test
    void bothWithNoContentStatusSkipsBodyButKeepsWarningsHeader() throws Exception {
      MethodParameter rt = methodParamFor("deleteWithWarnings");
      handler.handleReturnValue(Path.both("w", "deleted", SG), rt, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.NO_CONTENT.value());
      verify(response).setHeader(WARN, "\"w\"");
      verify(response, never()).setContentType(anyString());
      assertThat(body()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Left (fatal failure)")
  class LeftTests {

    @Test
    void leftYieldsDefaultErrorStatus() {
      handler.handleReturnValue(Path.left("boom", SG), returnType, mavContainer, webRequest);

      verify(response).setStatus(HttpStatus.BAD_REQUEST.value());
      verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
      assertThat(body()).contains("\"success\":false").contains("\"error\":\"boom\"");
    }

    @Test
    void leftUsesHeuristicStatusForDomainError() {
      EitherOrBothPath<UserNotFoundError, User> path =
          Path.left(new UserNotFoundError("123"), firstSg());
      handler.handleReturnValue(path, returnType, mavContainer, webRequest);
      verify(response).setStatus(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void customDefaultErrorStatusIsUsed() {
      EitherOrBothPathReturnValueHandler custom =
          new EitherOrBothPathReturnValueHandler(jsonMapper, 500);
      custom.handleReturnValue(Path.left("x", SG), returnType, mavContainer, webRequest);
      verify(response).setStatus(500);
    }

    @Test
    void httpHeaderCarrierLeftSurfacesItsHeaders() {
      EitherOrBothPathReturnValueHandler custom =
          new EitherOrBothPathReturnValueHandler(
              jsonMapper, 500, new DefaultErrorStatusCodeStrategy(Map.of("ThrottledError", 429)));
      EitherOrBothPath<ThrottledError, User> path = Path.left(new ThrottledError(45), firstSg());
      custom.handleReturnValue(path, returnType, mavContainer, webRequest);

      verify(response).setStatus(429);
      verify(response).addHeader("Retry-After", "45");
    }
  }

  @Nested
  @DisplayName("Edge cases")
  class EdgeCases {

    @Test
    void nullReturnValueIsIgnored() {
      handler.handleReturnValue(null, returnType, mavContainer, webRequest);
      verify(mavContainer).setRequestHandled(true);
      verify(response, never()).setStatus(anyInt());
    }

    @Test
    void nonPathReturnValueIsIgnored() {
      handler.handleReturnValue("nope", returnType, mavContainer, webRequest);
      verify(mavContainer).setRequestHandled(true);
      verify(response, never()).setStatus(anyInt());
    }
  }

  @Nested
  @DisplayName("@ResponseStatus")
  class ResponseStatusTests {

    @Test
    void honoursCreatedOnRight() throws Exception {
      MethodParameter rt = methodParamFor("createUser");
      handler.handleReturnValue(
          Path.right(new User("1", "a@b.com"), SG), rt, mavContainer, webRequest);
      verify(response).setStatus(HttpStatus.CREATED.value());
    }

    @Test
    void honoursNoContentOnRightAndSkipsBody() throws Exception {
      MethodParameter rt = methodParamFor("deleteUser");
      handler.handleReturnValue(Path.right("deleted", SG), rt, mavContainer, webRequest);
      verify(response).setStatus(HttpStatus.NO_CONTENT.value());
      verify(response, never()).setContentType(anyString());
      assertThat(body()).isEmpty();
    }
  }

  private MethodParameter methodParamFor(String methodName) throws Exception {
    Method method = SampleController.class.getDeclaredMethod(methodName);
    return new MethodParameter(method, -1);
  }

  private static <E> Semigroup<E> firstSg() {
    return (a, b) -> a;
  }

  // Test DTOs
  record User(String id, String email) {}

  record UserNotFoundError(String userId) {}

  record ThrottledError(int retryAfterSeconds) implements HttpHeaderCarrier {
    @Override
    public Map<String, String> headers() {
      return Map.of("Retry-After", Integer.toString(retryAfterSeconds));
    }
  }

  @SuppressWarnings("unused")
  static class SampleController {
    @ResponseStatus(HttpStatus.CREATED)
    public EitherOrBothPath<String, User> createUser() {
      return Path.right(new User("1", "a@b.com"), SG);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    public EitherOrBothPath<String, String> deleteUser() {
      return Path.right("deleted", SG);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    public EitherOrBothPath<String, String> deleteWithWarnings() {
      return Path.both("w", "deleted", SG);
    }
  }
}
