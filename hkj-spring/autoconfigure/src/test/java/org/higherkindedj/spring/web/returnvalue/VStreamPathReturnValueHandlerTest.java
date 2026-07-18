// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VStreamPath;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.spring.actuator.HkjMetricsService;
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
import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.method.support.ModelAndViewContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Behavioural tests for {@link VStreamPathReturnValueHandler}: SSE streaming, pre-commit failure
 * status, and client-disconnect detection.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VStreamPathReturnValueHandler Tests")
class VStreamPathReturnValueHandlerTest {

  @Mock private MethodParameter returnType;
  @Mock private HkjMetricsService metricsService;

  private VStreamPathReturnValueHandler handler;
  private MockHttpServletRequest mockRequest;
  private MockHttpServletResponse mockResponse;
  private ServletWebRequest webRequest;
  private ModelAndViewContainer mavContainer;

  @BeforeEach
  void setUp() {
    handler =
        new VStreamPathReturnValueHandler(
            JsonMapper.builder().build(),
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            true,
            30000,
            metricsService);

    mockRequest = new MockHttpServletRequest();
    mockRequest.setAsyncSupported(true);
    mockResponse = new MockHttpServletResponse();
    webRequest = new ServletWebRequest(mockRequest, mockResponse);
    mavContainer = new ModelAndViewContainer();

    WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(webRequest);
    asyncManager.setAsyncWebRequest(new StandardServletAsyncWebRequest(mockRequest, mockResponse));
  }

  @Nested
  @DisplayName("Streaming Tests")
  class StreamingTests {

    @Test
    @DisplayName("Finite stream writes SSE events and a completion frame")
    void finiteStreamWritesSseEvents() throws Exception {
      VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3);

      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      await()
          .atMost(Duration.ofSeconds(2))
          .pollInterval(Duration.ofMillis(10))
          .untilAsserted(
              () -> {
                assertThat(mockResponse.getStatus()).isEqualTo(HttpStatus.OK.value());
                assertThat(mockResponse.getContentType()).startsWith("text/event-stream");
                assertThat(mockResponse.getContentAsString())
                    .contains("data: 1\n\n", "data: 2\n\n", "data: 3\n\n")
                    .contains("event: complete");
              });
      verify(metricsService, timeout(2000)).recordVStreamSuccess();
      verify(metricsService).recordVStreamElements(3);
    }

    @Test
    @DisplayName("Failure before the first element yields the configured failure status")
    void preCommitFailureYieldsFailureStatus() throws Exception {
      VStreamPath<Integer> path =
          Path.vstream(
              VStream.<Integer>generate(
                  () -> {
                    throw new IllegalStateException("boom at start");
                  }));

      handler.handleReturnValue(path, returnType, mavContainer, webRequest);

      await()
          .atMost(Duration.ofSeconds(2))
          .pollInterval(Duration.ofMillis(10))
          .untilAsserted(
              () -> {
                assertThat(mockResponse.getStatus())
                    .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
                assertThat(mockResponse.getContentType()).startsWith("application/json");
                assertThat(mockResponse.getContentAsString()).contains("boom at start");
                assertThat(mockResponse.getContentAsString()).doesNotContain("event:");
              });
    }
  }

  @Nested
  @DisplayName("Client Disconnect Tests")
  class ClientDisconnectTests {

    @Test
    @DisplayName("Infinite stream stops pulling when the client disconnects")
    void infiniteStreamStopsOnDisconnect() throws Exception {
      // Response whose output breaks after a few writes, like a closed socket
      BrokenPipeResponse brokenResponse = new BrokenPipeResponse(3);
      ServletWebRequest brokenWebRequest = new ServletWebRequest(mockRequest, brokenResponse);
      WebAsyncUtils.getAsyncManager(brokenWebRequest)
          .setAsyncWebRequest(new StandardServletAsyncWebRequest(mockRequest, brokenResponse));

      VStreamPath<Integer> path = Path.vstreamIterate(0, n -> n + 1);

      handler.handleReturnValue(path, returnType, mavContainer, brokenWebRequest);

      // Without checkError() detection this would pull (and leak a virtual thread) forever
      verify(metricsService, timeout(2000)).recordVStreamError("ClientDisconnected");
      verify(metricsService, never()).recordVStreamSuccess();
    }
  }

  /** A response whose output stream starts failing after {@code allowedWrites} write calls. */
  static final class BrokenPipeResponse extends MockHttpServletResponse {
    private final AtomicInteger remaining;
    private PrintWriter brokenWriter;

    BrokenPipeResponse(int allowedWrites) {
      this.remaining = new AtomicInteger(allowedWrites);
    }

    @Override
    public synchronized PrintWriter getWriter() {
      if (brokenWriter == null) {
        OutputStream failing =
            new OutputStream() {
              @Override
              public void write(int b) throws IOException {
                if (remaining.get() <= 0) {
                  throw new IOException("Broken pipe");
                }
              }

              @Override
              public void flush() throws IOException {
                if (remaining.decrementAndGet() < 0) {
                  throw new IOException("Broken pipe");
                }
              }
            };
        brokenWriter = new PrintWriter(failing, false);
      }
      return brokenWriter;
    }
  }
}
