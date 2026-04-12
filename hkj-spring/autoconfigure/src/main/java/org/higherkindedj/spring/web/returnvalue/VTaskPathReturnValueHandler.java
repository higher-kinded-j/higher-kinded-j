// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.higherkindedj.hkt.effect.VTaskPath;
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
 * Return value handler that converts {@link VTaskPath} return values from controller methods into
 * asynchronous HTTP responses executed on virtual threads.
 *
 * <p>This handler brings HKJ's virtual thread concurrency model to Spring MVC controllers.
 * Controllers can return {@code VTaskPath<T>} directly, and the computation is executed
 * asynchronously on a virtual thread using Spring's {@link DeferredResult} mechanism.
 *
 * <p>Key advantages over {@link CompletableFuturePathReturnValueHandler}:
 *
 * <ul>
 *   <li>Virtual threads: No thread pool sizing or tuning needed — scales to millions of concurrent
 *       tasks
 *   <li>Lazy execution: The VTask is not evaluated until the handler processes it
 *   <li>Composable: Supports map, flatMap, retry, circuit breaker, bulkhead, timeout
 *   <li>Structured concurrency: Works with {@link org.higherkindedj.hkt.vtask.Scope} for fan-out
 *       patterns
 * </ul>
 *
 * <p>Conversion rules:
 *
 * <ul>
 *   <li>Successful execution → HTTP 200 OK with result as JSON body
 *   <li>Failed execution → HTTP 500 Internal Server Error (configurable)
 *   <li>Timeout → HTTP 504 Gateway Timeout
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @GetMapping("/users/{id}")
 * public VTaskPath<User> getUser(@PathVariable String id) {
 *     return Path.vtask(() -> userRepository.findById(id))
 *         .map(user -> enrichWithProfile(user))
 *         .withRetry(RetryPolicy.exponentialBackoff(3, Duration.ofMillis(100)));
 * }
 *
 * @GetMapping("/dashboard/{userId}")
 * public VTaskPath<Dashboard> getDashboard(@PathVariable String userId) {
 *     return Scope.<Object>allSucceed()
 *         .fork(VTask.of(() -> userService.getProfile(userId)))
 *         .fork(VTask.of(() -> orderService.getRecentOrders(userId)))
 *         .joinAsVTaskPath()
 *         .map(results -> Dashboard.from(results));
 * }
 * }</pre>
 *
 * @see VTaskPath
 * @see org.higherkindedj.hkt.vtask.VTask
 * @see org.higherkindedj.hkt.vtask.Scope
 */
public class VTaskPathReturnValueHandler implements AsyncHandlerMethodReturnValueHandler {

  private static final Logger log = LoggerFactory.getLogger(VTaskPathReturnValueHandler.class);

  private final JsonMapper jsonMapper;
  private final ObjectWriter objectWriter;
  private final int failureStatus;
  private final boolean includeExceptionDetails;
  private final long timeoutMillis;
  private final @Nullable HkjMetricsService metricsService;

  /**
   * Creates a new VTaskPathReturnValueHandler with the specified settings.
   *
   * @param jsonMapper the Jackson 3.x JsonMapper for JSON serialization
   * @param failureStatus the HTTP status code for failures (default 500)
   * @param includeExceptionDetails whether to include exception details in error responses
   * @param timeoutMillis timeout for VTask operations in milliseconds (0 = no timeout)
   * @param metricsService the metrics service for recording VTask invocations (may be null)
   */
  public VTaskPathReturnValueHandler(
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
    return VTaskPath.class.isAssignableFrom(returnType.getParameterType());
  }

  @Override
  public boolean isAsyncReturnValue(@Nullable Object returnValue, MethodParameter returnType) {
    return returnValue instanceof VTaskPath;
  }

  @Override
  public void handleReturnValue(
      @Nullable Object returnValue,
      MethodParameter returnType,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest)
      throws Exception {

    if (!(returnValue instanceof VTaskPath<?> vtaskPath)) {
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
            writeTimeoutResponse(response);
          } catch (Exception e) {
            log.error("Failed to write timeout response", e);
          }
        });

    int successStatus =
        SuccessStatusResolver.resolveSuccessStatus(returnType, HttpStatus.OK.value());

    // Execute the VTask asynchronously on a virtual thread
    long startTime = System.currentTimeMillis();
    vtaskPath
        .runAsync()
        .whenComplete(
            (result, throwable) -> {
              long durationMillis = System.currentTimeMillis() - startTime;
              try {
                if (throwable != null) {
                  log.error("VTaskPath failed", throwable);
                  Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                  if (metricsService != null) {
                    metricsService.recordVTaskError(cause.getClass().getSimpleName());
                    metricsService.recordVTaskDuration(durationMillis);
                  }
                  writeFailureResponse(throwable, response);
                } else {
                  if (metricsService != null) {
                    metricsService.recordVTaskSuccess();
                    metricsService.recordVTaskDuration(durationMillis);
                  }
                  writeSuccessResponse(result, response, successStatus);
                }
                deferredResult.setResult(null);
              } catch (Exception e) {
                log.error("Failed to write async response", e);
                deferredResult.setErrorResult(e);
              }
            });

    // Start async processing
    WebAsyncUtils.getAsyncManager(webRequest)
        .startDeferredResultProcessing(deferredResult, mavContainer);
  }

  private void writeFailureResponse(Throwable throwable, HttpServletResponse response) {
    try {
      response.setStatus(failureStatus);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);

      // Unwrap CompletionException if present
      Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;

      Map<String, Object> errorBody;
      if (includeExceptionDetails) {
        errorBody =
            Map.of(
                "success",
                false,
                "error",
                Map.of(
                    "type",
                    cause.getClass().getSimpleName(),
                    "message",
                    cause.getMessage() != null ? cause.getMessage() : "No message"));
      } else {
        errorBody =
            Map.of("success", false, "error", "An error occurred during virtual thread execution");
      }

      objectWriter.writeValue(response.getWriter(), errorBody);
    } catch (Exception e) {
      throw new RuntimeException("Failed to write failure response", e);
    }
  }

  private void writeTimeoutResponse(HttpServletResponse response) {
    try {
      response.setStatus(HttpStatus.GATEWAY_TIMEOUT.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);

      Map<String, Object> body = Map.of("success", false, "error", "VTask request timed out");
      objectWriter.writeValue(response.getWriter(), body);
    } catch (Exception e) {
      throw new RuntimeException("Failed to write timeout response", e);
    }
  }

  private void writeSuccessResponse(Object value, HttpServletResponse response, int status) {
    try {
      response.setStatus(status);
      if (status != HttpStatus.NO_CONTENT.value()) {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectWriter.writeValue(response.getWriter(), value);
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to write success response", e);
    }
  }
}
