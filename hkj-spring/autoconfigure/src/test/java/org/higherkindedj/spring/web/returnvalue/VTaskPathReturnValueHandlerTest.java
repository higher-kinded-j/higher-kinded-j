// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.AsyncEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.method.support.ModelAndViewContainer;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("VTaskPathReturnValueHandler Tests")
class VTaskPathReturnValueHandlerTest {

  @Mock private MethodParameter returnType;

  private VTaskPathReturnValueHandler handler;
  private MockHttpServletRequest mockRequest;
  private MockHttpServletResponse mockResponse;
  private ServletWebRequest webRequest;
  private ModelAndViewContainer mavContainer;
  private StandardServletAsyncWebRequest asyncWebRequest;

  @BeforeEach
  void setUp() {
    handler =
        new VTaskPathReturnValueHandler(
            JsonMapper.builder().build(),
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            false,
            30000,
            null);
    mockRequest = new MockHttpServletRequest();
    mockRequest.setAsyncSupported(true);
    mockResponse = new MockHttpServletResponse();
    webRequest = new ServletWebRequest(mockRequest, mockResponse);
    mavContainer = new ModelAndViewContainer();
    asyncWebRequest = new StandardServletAsyncWebRequest(mockRequest, mockResponse);
    WebAsyncUtils.getAsyncManager(webRequest).setAsyncWebRequest(asyncWebRequest);
  }

  @Nested
  @DisplayName("handleReturnValue - Timeout Tests")
  class TimeoutTests {

    @Test
    @DisplayName("The timeout writes 504 and interrupts the task, without waiting for it to stop")
    void timeoutWrites504AndInterruptsTheTask() throws Exception {
      CountDownLatch started = new CountDownLatch(1);
      CountDownLatch interrupted = new CountDownLatch(1);
      CountDownLatch released = new CountDownLatch(1);
      CountDownLatch exited = new CountDownLatch(1);
      // Ignores the interrupt and holds until released, so a handler that waited for it to stop
      // would return only once it had exited
      VTaskPath<String> path =
          Path.vtask(
              () -> {
                try {
                  started.countDown();
                  try {
                    Thread.sleep(5000);
                  } catch (InterruptedException e) {
                    interrupted.countDown();
                  }
                  if (!released.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("the test did not release this task");
                  }
                  return "too late";
                } finally {
                  exited.countDown();
                }
              });

      try {
        handler.handleReturnValue(path, returnType, mavContainer, webRequest);
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        // Simulate the container's async timeout event
        asyncWebRequest.onTimeout(new AsyncEvent(mockRequest.getAsyncContext()));

        assertThat(exited.getCount()).isEqualTo(1);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT.value());
        assertThat(mockResponse.getContentAsString()).contains("timed out");
        assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
      } finally {
        released.countDown();
      }
    }
  }
}
