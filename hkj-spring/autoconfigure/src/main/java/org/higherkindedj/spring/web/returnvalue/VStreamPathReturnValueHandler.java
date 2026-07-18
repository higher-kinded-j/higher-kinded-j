// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.higherkindedj.hkt.effect.VStreamPath;
import org.higherkindedj.hkt.vstream.VStream;
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
 *   <li>Error on the first pull → configured failure status (default 500) with a JSON error body;
 *       the response commits (and SSE headers flush) once the first step resolves, so streams that
 *       may idle before their first step should emit an early heartbeat element
 *   <li>Error mid-stream → SSE {@code event: error} with error details
 *   <li>Timeout → HTTP 504 Gateway Timeout (before streaming) or stream abort (mid-stream)
 *   <li>Client disconnect → pull loop stops; upstream production ceases
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

    // Signals the pull loop to stop (timeout or request completion, incl. client disconnect).
    AtomicBoolean aborted = new AtomicBoolean(false);
    // First claimant may write the initial response bytes; the loser must not touch it.
    AtomicBoolean responseOwned = new AtomicBoolean(false);

    deferredResult.onTimeout(
        () -> {
          aborted.set(true);
          if (responseOwned.compareAndSet(false, true)) {
            try {
              response.setStatus(HttpStatus.GATEWAY_TIMEOUT.value());
              JsonResponses.setJsonContentType(response);
              Map<String, Object> body =
                  Map.of("success", false, "error", "VStream request timed out");
              objectWriter.writeValue(response.getWriter(), body);
            } catch (Exception e) {
              log.error("Failed to write timeout response", e);
            }
          }
          // Settle the DeferredResult so Spring does not dispatch an
          // AsyncRequestTimeoutException onto the committed response.
          deferredResult.setResult(null);
        });
    deferredResult.onCompletion(() -> aborted.set(true));

    int successStatus =
        SuccessStatusResolver.resolveSuccessStatus(returnType, HttpStatus.OK.value());

    // Enter async mode BEFORE spawning the producer, so the container is async by the time the
    // producer can touch or commit the response (avoids a commit-before-startAsync race).
    WebAsyncUtils.getAsyncManager(webRequest)
        .startDeferredResultProcessing(deferredResult, mavContainer);

    // Execute the VStream on a virtual thread, pulling elements and writing SSE events
    Thread.ofVirtual()
        .name("hkj-vstream-handler")
        .start(
            () -> {
              AtomicLong elementCount = new AtomicLong(0);
              boolean owned = false;
              boolean committed = false;
              try {
                // A bodiless status (204/205/304) has no body and needs no stream execution —
                // short-circuit before running or pulling the stream, so such an endpoint never
                // blocks, fails, or times out on a (possibly long) first pull.
                if (JsonResponses.isBodilessStatus(successStatus)) {
                  if (!responseOwned.compareAndSet(false, true)) {
                    return; // a timeout already owns and has settled the response
                  }
                  owned = true;
                  if (aborted.get()) {
                    // Request completed/disconnected before we claimed the response.
                    if (metricsService != null) {
                      metricsService.recordVStreamError("StreamAborted");
                      metricsService.recordVStreamElements(0);
                    }
                    deferredResult.setResult(null);
                    return;
                  }
                  response.setStatus(successStatus);
                  if (metricsService != null) {
                    metricsService.recordVStreamSuccess();
                    metricsService.recordVStreamElements(0);
                  }
                  deferredResult.setResult(null);
                  return;
                }

                VStream<?> stream = streamPath.run();

                // The first step is pulled BEFORE claiming the response or setting any status: a
                // timeout during this (possibly long) first pull is then owned by onTimeout, which
                // writes the 504 — not left as a broken 200-empty stream. A stream that fails on
                // this first pull still gets the configured failure status (below). Once the first
                // step resolves the headers flush promptly so EventSource.onopen fires and proxies
                // see the response; streams that may idle before their first step should emit an
                // early heartbeat element.
                VStream.Step<?> firstStep = stream.pull().run();

                // Claim the response; if a timeout already owns it, it has settled the request.
                if (!responseOwned.compareAndSet(false, true)) {
                  return;
                }
                owned = true;
                if (aborted.get()) {
                  // Request completed/disconnected between the first pull and our claim.
                  if (metricsService != null) {
                    metricsService.recordVStreamError("StreamAborted");
                    metricsService.recordVStreamElements(0);
                  }
                  deferredResult.setResult(null);
                  return;
                }

                response.setStatus(successStatus);
                response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.setHeader("Cache-Control", "no-cache");
                PrintWriter writer = response.getWriter();
                response.flushBuffer();
                committed = true;

                boolean completed = pullAndWrite(firstStep, writer, elementCount, aborted);

                if (completed) {
                  writer.write("event: complete\ndata: {\"done\":true}\n\n");
                  writer.flush();
                  if (metricsService != null) {
                    metricsService.recordVStreamSuccess();
                    metricsService.recordVStreamElements(elementCount.get());
                  }
                } else if (metricsService != null) {
                  metricsService.recordVStreamError("StreamAborted");
                  metricsService.recordVStreamElements(elementCount.get());
                }

                deferredResult.setResult(null);
              } catch (ClientDisconnectedException e) {
                log.debug("SSE client disconnected after {} elements", elementCount.get());
                if (metricsService != null) {
                  metricsService.recordVStreamError("ClientDisconnected");
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
                  if (committed) {
                    // Mid-stream failure on an already-open SSE stream → error event.
                    writeErrorEvent(response, e);
                    deferredResult.setResult(null);
                  } else if (owned || responseOwned.compareAndSet(false, true)) {
                    // First-pull failure on an uncommitted response we own → failure status.
                    // Settle normally so Spring's async error dispatch does not reset the body.
                    writeFailureResponse(response, e);
                    deferredResult.setResult(null);
                  } else {
                    // A timeout owns the uncommitted response; leave its 504 in place.
                    deferredResult.setResult(null);
                  }
                } catch (Exception writeError) {
                  log.error("Failed to write error response", writeError);
                  deferredResult.setErrorResult(e);
                }
              }
            });
  }

  /**
   * Processes the given step and keeps pulling, writing each element as an SSE event.
   *
   * @return {@code true} when the stream ran to completion, {@code false} when it was aborted by
   *     timeout or request completion
   * @throws ClientDisconnectedException when the client has gone away (detected via {@link
   *     PrintWriter#checkError()})
   */
  private boolean pullAndWrite(
      VStream.Step<?> firstStep,
      PrintWriter writer,
      AtomicLong elementCount,
      AtomicBoolean aborted) {
    VStream.Step<?> step = firstStep;
    while (true) {
      // Re-checked after every (potentially long-blocking) pull as well as before it: once a
      // timeout has settled the DeferredResult, the container may recycle the response, and a
      // late write could land on an unrelated request.
      if (aborted.get()) {
        return false;
      }
      VStream<?> next;
      switch (step) {
        case VStream.Step.Emit<?> emit -> {
          String json;
          try {
            json = objectWriter.writeValueAsString(emit.value());
          } catch (Exception e) {
            throw new RuntimeException("Failed to serialize stream element", e);
          }
          writer.write("data: " + json + "\n\n");
          // PrintWriter swallows IOExceptions; checkError() (which also flushes) is the only
          // disconnect signal. Without it an infinite stream would keep a virtual thread
          // pulling forever.
          if (writer.checkError()) {
            throw new ClientDisconnectedException();
          }
          elementCount.incrementAndGet();
          next = emit.tail();
        }
        case VStream.Step.Skip<?> skip -> next = skip.tail();
        case VStream.Step.Done<?> done -> {
          return true;
        }
      }
      if (aborted.get()) {
        return false;
      }
      step = next.pull().run();
    }
  }

  /**
   * Writes the configured failure status with a JSON error body. Only valid while the response is
   * uncommitted (i.e. the stream failed before its first element).
   */
  private void writeFailureResponse(HttpServletResponse response, Exception e) throws Exception {
    response.reset();
    response.setStatus(failureStatus);
    // Preserve any headers the failure carries (e.g. Retry-After, WWW-Authenticate), matching the
    // other pre-commit failure writers.
    ErrorResponseHeaders.applyTo(e, response);
    JsonResponses.setJsonContentType(response);
    Map<String, Object> errorBody;
    if (includeExceptionDetails) {
      errorBody =
          Map.of(
              "success",
              false,
              "error",
              Map.of(
                  "type",
                  e.getClass().getSimpleName(),
                  "message",
                  e.getMessage() != null ? e.getMessage() : "No message"));
    } else {
      errorBody = Map.of("success", false, "error", "Stream processing failed");
    }
    objectWriter.writeValue(response.getWriter(), errorBody);
  }

  /** Raised internally when {@link PrintWriter#checkError()} reports a broken connection. */
  private static final class ClientDisconnectedException extends RuntimeException {
    ClientDisconnectedException() {
      super("SSE client disconnected", null, false, false);
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
