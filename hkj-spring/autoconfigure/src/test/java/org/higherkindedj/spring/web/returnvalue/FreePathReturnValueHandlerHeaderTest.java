// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import org.higherkindedj.hkt.effect.FreePath;
import org.higherkindedj.hkt.effect.boundary.EffectBoundary;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Targeted coverage test for the {@link HttpHeaderCarrier} integration line added to {@link
 * FreePathReturnValueHandler#writeFailureResponse}. The handler has no general unit-test suite, so
 * this file focuses narrowly on the failure path.
 *
 * <p>The simplest way to reach {@code writeFailureResponse} without standing up a real {@link
 * EffectBoundary} interpreter is to make {@link ApplicationContext#getBean(Class)} throw — the
 * handler catches the exception and writes a failure response. By making that exception itself a
 * {@link HttpHeaderCarrier} we exercise the "headers applied" branch; with a plain exception we
 * exercise the "no headers" branch.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FreePathReturnValueHandler header-carrier coverage")
class FreePathReturnValueHandlerHeaderTest {

  @Mock private HttpServletResponse response;

  @Mock private NativeWebRequest webRequest;

  @Mock private MethodParameter returnType;

  @Mock private ModelAndViewContainer mavContainer;

  @Mock private ApplicationContext applicationContext;

  private FreePathReturnValueHandler handler;
  private FreePath<MaybeKind.Witness, String> freePath;
  private PrintWriter printWriter;
  private StringWriter stringWriter;

  @BeforeEach
  void setUp() throws Exception {
    JsonMapper jsonMapper = JsonMapper.builder().build();
    handler = new FreePathReturnValueHandler(jsonMapper, 500, false, applicationContext);

    // Any FreePath instance suffices — the boundary lookup fails before interpretation runs.
    freePath = FreePath.pure("ignored", Instances.monadError(maybe()));
    stringWriter = new StringWriter();
    printWriter = new PrintWriter(stringWriter);

    lenient().when(webRequest.getNativeResponse(HttpServletResponse.class)).thenReturn(response);
    lenient().when(response.getWriter()).thenReturn(printWriter);
  }

  @Test
  @DisplayName("Boundary-lookup exception that carries headers surfaces them on the response")
  void writesRetryAfter() throws Exception {
    when(applicationContext.getBean(EffectBoundary.class))
        .thenThrow(new ThrottledLookupFailure(45));

    handler.handleReturnValue(freePath, returnType, mavContainer, webRequest);

    verify(response).addHeader("Retry-After", "45");
    assertThat(stringWriter.toString()).contains("\"success\":false");
  }

  @Test
  @DisplayName("Plain boundary-lookup failure adds no carrier headers")
  void noHeadersForPlainFailure() throws Exception {
    when(applicationContext.getBean(EffectBoundary.class))
        .thenThrow(new NoSuchBeanDefinitionException(EffectBoundary.class));

    handler.handleReturnValue(freePath, returnType, mavContainer, webRequest);

    verify(response, never()).addHeader("Retry-After", "45");
  }

  /** Lookup failure that doubles as a Retry-After header carrier. */
  static class ThrottledLookupFailure extends RuntimeException implements HttpHeaderCarrier {
    private final int seconds;

    ThrottledLookupFailure(int seconds) {
      super("throttled");
      this.seconds = seconds;
    }

    @Override
    public Map<String, String> headers() {
      return Map.of("Retry-After", Integer.toString(seconds));
    }
  }
}
