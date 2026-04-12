// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.higherkindedj.hkt.effect.VStreamPath;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.spring.actuator.HkjMetricsService;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.method.support.AsyncHandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Return value handler that converts {@link VStreamPath} return values from controller methods into
 * Server-Sent Events (SSE) streaming HTTP responses.
 *
 * <p>This handler brings HKJ's pull-based, virtual-thread-powered streaming to Spring MVC.
 * Controllers can return {@code VStreamPath<T>} directly and the stream is lazily evaluated, with
 * each element sent as an SSE event over the HTTP connection.
 *
 * <p>Key characteristics:
 *
 * <ul>
 *   <li>Pull-based streaming: The handler drives element production, natural backpressure via
 *       virtual thread blocking
 *   <li>Virtual threads: Each element pull executes on a virtual thread for scalable concurrency
 *   <li>Lazy: The stream pipeline is not evaluated until the handler starts pulling
 *   <li>SSE format: Each element is emitted as a {@code data:} line followed by a blank line
 *   <li>No Reactor/WebFlux required: Streaming in Spring MVC without reactive complexity
 * </ul>
 *
 * <p>Conversion rules:
 *
 * <ul>
 *   <li>Each emitted element → SSE {@code data:} event with JSON payload
 *   <li>Stream completion → Final SSE {@code event: complete} and connection close
 *   <li>Stream error → SSE {@code event: error} with error details
 *   <li>Timeout → HTTP 504 Gateway Timeout
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
 * public VStreamPath<Event> streamEvents() {
 *     return Path.vstreamIterate(0, n -> n + 1)
 *         .map(n -> new Event("tick-" + n, Instant.now()))
 *         .take(100);
 * }
 *
 * @GetMapping(value = "/users/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
 * public VStreamPath<User> streamUsers() {
 *     return Path.vstream(VStream.fromList(userRepository.findAll()))
 *         .filter(User::isActive)
 *         .map(user -> enrichWithProfile(user));
 * }
 * }</pre>
 *
 * @see VStreamPath
 * @see org.higherkindedj.hkt.vstream.VStream
 */
public class VStreamPathReturnValueHandler implements AsyncHandlerMethodReturnValueHandler {

  private static final Logger log = LoggerFactory.getLogger(VStreamPathReturnValueHandler.class);

  private final JsonMapper jsonMapper;
  private final ObjectWriter objectWriter;
  private final int failureStatus;
  private final boolean includeExceptionDetails;
  private final long timeoutMillis;
  private final @Nullable HkjMetricsService metricsService;

  /**
   * Creates a new VStreamPathReturnValueHandler with the specified settings.
   *
   * @param jsonMapper the Jackson 3.x JsonMapper for JSON serialization
   * @param failureStatus the HTTP status code for stream failures (default 500)
   * @param includeExceptionDetails whether to include exception details in error events
   * @param timeoutMillis timeout for the entire stream in milliseconds (0 = no timeout)
   * @param metricsService the metrics service for recording VStream invocations (may be null)
   */
  public VStreamPathReturnValueHandler(
      JsonMapper jsonMapper,
      int failureStatus,
      boolean includeExceptionDetails,
      long timeoutMillis,
      @Nullable HkjMetricsService metricsService) {
    this.jsonMapper = jsonMapper;
    this.objectWriter = jsonMapper.writer();
    this.failureStatus = failureStatus;
    this.includeExceptionDetails = includeExceptionDetails;
    this.timeoutMillis = timeoutMillis;
    this.metricsService = metricsService;
  }

  @Override
  public boolean supportsReturnType(MethodParameter returnType) {
    return VStreamPath.class.isAssignableFrom(returnType.getParameterType());
  }

  @Override
  public boolean isAsyncReturnValue(@Nullable Object returnValue, MethodParameter returnType) {
    return returnValue instanceof VStreamPath;
  }

  @Override
  public void handleReturnValue(
      @Nullable Object returnValue,
      MethodParameter returnType,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest)
      throws Exception {

    if (!(returnValue instanceof VStreamPath<?> streamPath)) {
      mavContainer.setRequestHandled(true);
      return;
    }

    HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);
    if (response == null) {
      mavContainer.setRequestHandled(true);
      return;
    }

    // Create a DeferredResult for async processing
    DeferredResult<Void> deferredResult =
        timeoutMillis > 0 ? new DeferredResult<>(timeoutMillis) : new DeferredResult<>();

    // Handle timeout
    deferredResult.onTimeout(
        () -> {
          try {
            response.setStatus(HttpStatus.GATEWAY_TIMEOUT.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            Map<String, Object> body =
                Map.of("success", false, "error", "VStream request timed out");
            objectWriter.writeValue(response.getWriter(), body);
          } catch (Exception e) {
            log.error("Failed to write timeout response", e);
          }
        });

    int successStatus =
        SuccessStatusResolver.resolveSuccessStatus(returnType, HttpStatus.OK.value());

    // Execute the VStream on a virtual thread, pulling elements and writing SSE events
    Thread.ofVirtual()
        .name("hkj-vstream-handler")
        .start(
            () -> {
              AtomicLong elementCount = new AtomicLong(0);
              try {
                response.setStatus(successStatus);

                // 204 No Content must not have a body — skip SSE headers and stream execution
                if (successStatus == HttpStatus.NO_CONTENT.value()) {
                  if (metricsService != null) {
                    metricsService.recordVStreamSuccess();
                    metricsService.recordVStreamElements(0);
                  }
                  deferredResult.setResult(null);
                  return;
                }

                response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.setHeader("Cache-Control", "no-cache");
                response.setHeader("Connection", "keep-alive");
                response.flushBuffer();

                PrintWriter writer = response.getWriter();
                VStream<?> stream = streamPath.run();

                pullAndWrite(stream, writer, elementCount);

                // Send completion event
                writer.write("event: complete\ndata: {\"done\":true}\n\n");
                writer.flush();

                if (metricsService != null) {
                  metricsService.recordVStreamSuccess();
                  metricsService.recordVStreamElements(elementCount.get());
                }

                deferredResult.setResult(null);
              } catch (Exception e) {
                log.error("VStreamPath streaming failed", e);
                if (metricsService != null) {
                  metricsService.recordVStreamError(e.getClass().getSimpleName());
                  metricsService.recordVStreamElements(elementCount.get());
                }
                try {
                  writeErrorEvent(response, e);
                } catch (Exception writeError) {
                  log.error("Failed to write error event", writeError);
                }
                deferredResult.setErrorResult(e);
              }
            });

    // Start async processing
    WebAsyncUtils.getAsyncManager(webRequest)
        .startDeferredResultProcessing(deferredResult, mavContainer);
  }

  @SuppressWarnings("preview")
  private void pullAndWrite(VStream<?> stream, PrintWriter writer, AtomicLong elementCount) {
    VStream<?> current = stream;
    while (true) {
      VTask<? extends VStream.Step<?>> pullTask = current.pull();
      VStream.Step<?> step = pullTask.run();

      switch (step) {
        case VStream.Step.Emit<?> emit -> {
          try {
            String json = objectWriter.writeValueAsString(emit.value());
            writer.write("data: " + json + "\n\n");
            writer.flush();
            elementCount.incrementAndGet();
          } catch (Exception e) {
            throw new RuntimeException("Failed to serialize stream element", e);
          }
          current = emit.tail();
        }
        case VStream.Step.Skip<?> skip -> current = skip.tail();
        case VStream.Step.Done<?> done -> {
          return;
        }
      }
    }
  }

  private void writeErrorEvent(HttpServletResponse response, Exception e) {
    try {
      PrintWriter writer = response.getWriter();
      Map<String, Object> errorBody;
      if (includeExceptionDetails) {
        errorBody =
            Map.of(
                "type",
                e.getClass().getSimpleName(),
                "message",
                e.getMessage() != null ? e.getMessage() : "No message");
      } else {
        errorBody = Map.of("error", "Stream processing failed");
      }
      String json = objectWriter.writeValueAsString(errorBody);
      writer.write("event: error\ndata: " + json + "\n\n");
      writer.flush();
    } catch (Exception writeError) {
      throw new RuntimeException("Failed to write error event", writeError);
    }
  }
}
