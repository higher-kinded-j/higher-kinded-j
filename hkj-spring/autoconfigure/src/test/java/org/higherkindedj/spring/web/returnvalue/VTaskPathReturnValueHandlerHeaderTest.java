// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.async.StandardServletAsyncWebRequest;
import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.method.support.ModelAndViewContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Targeted coverage test for the {@link HttpHeaderCarrier} integration line added to {@link
 * VTaskPathReturnValueHandler#writeFailureResponse}. There are no general unit tests for this
 * handler in the suite, so this file focuses narrowly on the new branch.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VTaskPathReturnValueHandler header-carrier coverage")
class VTaskPathReturnValueHandlerHeaderTest {

  @Mock private MethodParameter returnType;

  private VTaskPathReturnValueHandler handler;
  private MockHttpServletRequest mockRequest;
  private MockHttpServletResponse mockResponse;
  private ServletWebRequest webRequest;
  private ModelAndViewContainer mavContainer;

  @BeforeEach
  void setUp() {
    JsonMapper jsonMapper = JsonMapper.builder().build();
    handler =
        new VTaskPathReturnValueHandler(
            jsonMapper, HttpStatus.INTERNAL_SERVER_ERROR.value(), false, 30000, null);

    mockRequest = new MockHttpServletRequest();
    mockRequest.setAsyncSupported(true);
    mockResponse = new MockHttpServletResponse();
    webRequest = new ServletWebRequest(mockRequest, mockResponse);
    mavContainer = new ModelAndViewContainer();

    WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(webRequest);
    asyncManager.setAsyncWebRequest(new StandardServletAsyncWebRequest(mockRequest, mockResponse));
  }

  @Test
  @DisplayName("Failure throwable that implements HttpHeaderCarrier surfaces its headers")
  void writesRetryAfter() throws Exception {
    VTaskPath<String> path = Path.vtaskFail(new ThrottledFailure(45));

    handler.handleReturnValue(path, returnType, mavContainer, webRequest);

    await()
        .atMost(Duration.ofSeconds(2))
        .pollInterval(Duration.ofMillis(10))
        .untilAsserted(() -> assertThat(mockResponse.getHeader("Retry-After")).isEqualTo("45"));
  }

  @Test
  @DisplayName("Plain failure adds no carrier headers")
  void noHeadersForPlainFailure() throws Exception {
    VTaskPath<String> path = Path.vtaskFail(new RuntimeException("boom"));

    handler.handleReturnValue(path, returnType, mavContainer, webRequest);

    await()
        .atMost(Duration.ofSeconds(2))
        .pollInterval(Duration.ofMillis(10))
        .untilAsserted(() -> assertThat(mockResponse.getHeader("Retry-After")).isNull());
  }

  /** Test exception that surfaces a Retry-After header via {@link HttpHeaderCarrier}. */
  static class ThrottledFailure extends RuntimeException implements HttpHeaderCarrier {
    private final int seconds;

    ThrottledFailure(int seconds) {
      super("throttled");
      this.seconds = seconds;
    }

    @Override
    public Map<String, String> headers() {
      return Map.of("Retry-After", Integer.toString(seconds));
    }
  }
}
