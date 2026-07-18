// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.higherkindedj.hkt.effect.CompletableFuturePath;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.method.support.AsyncHandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Return value handler that converts {@link CompletableFuturePath} return values from controller
 * methods into asynchronous HTTP responses.
 *
 * <p>This handler is part of the Effect Path API integration for Spring Boot 4.0.1+. It provides
 * non-blocking async support using Spring's {@link DeferredResult} mechanism.
 *
 * <p>Key characteristics:
 *
 * <ul>
 *   <li>Non-blocking: Request thread is released while waiting for completion
 *   <li>Composable: Supports all standard Path operations (map, via, etc.)
 *   <li>Error handling: Failed futures are converted to HTTP 500
 * </ul>
 *
 * <p>Conversion rules:
 *
 * <ul>
 *   <li>Successful completion → HTTP 200 OK with result as JSON body
 *   <li>Failed completion → HTTP 500 Internal Server Error (configurable)
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @GetMapping("/users/{id}/async")
 * public CompletableFuturePath<User> getUserAsync(@PathVariable String id) {
 *     return Path.completableFuture(asyncUserService.fetchUser(id))
 *         .map(user -> enrichWithProfile(user));
 * }
 * }</pre>
 *
 * @see CompletableFuturePath
 * @see DeferredResult
 */
public class CompletableFuturePathReturnValueHandler
    implements AsyncHandlerMethodReturnValueHandler {

  private static final Logger log =
      LoggerFactory.getLogger(CompletableFuturePathReturnValueHandler.class);

  private final ObjectWriter objectWriter;
  private final int failureStatus;
  private final boolean includeExceptionDetails;
  private final long timeoutMillis;

  /**
   * Creates a new CompletableFuturePathReturnValueHandler with the specified settings.
   *
   * @param jsonMapper the Jackson 3.x JsonMapper for JSON serialization
   * @param failureStatus the HTTP status code for failures (default 500)
   * @param includeExceptionDetails whether to include exception details in error responses
   * @param timeoutMillis timeout for async operations in milliseconds (0 = no timeout)
   */
  public CompletableFuturePathReturnValueHandler(
      JsonMapper jsonMapper,
      int failureStatus,
      boolean includeExceptionDetails,
      long timeoutMillis) {
    this.objectWriter = jsonMapper.writer();
    this.failureStatus = failureStatus;
    this.includeExceptionDetails = includeExceptionDetails;
    this.timeoutMillis = timeoutMillis;
  }

  @Override
  public boolean supportsReturnType(MethodParameter returnType) {
    return CompletableFuturePath.class.isAssignableFrom(returnType.getParameterType());
  }

  @Override
  public boolean isAsyncReturnValue(@Nullable Object returnValue, MethodParameter returnType) {
    return returnValue instanceof CompletableFuturePath;
  }

  @Override
  public void handleReturnValue(
      @Nullable Object returnValue,
      MethodParameter returnType,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest)
      throws Exception {

    if (!(returnValue instanceof CompletableFuturePath<?> path)) {
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

    // Exactly one of the timeout callback and the completion callback may write the response:
    // after a timeout the container completes (and may recycle) the request, so a late
    // completion must not touch the response object.
    AtomicBoolean responseWritten = new AtomicBoolean(false);

    CompletableFuture<?> future = path.run();

    deferredResult.onTimeout(
        () -> {
          // If completion already won the write race it owns the response and will settle the
          // request — do NOT cancel or setResult here, or we could complete/recycle the response
          // out from under the completion callback's in-flight write.
          if (!responseWritten.compareAndSet(false, true)) {
            return;
          }
          try {
            writeTimeoutResponse(response);
          } catch (Exception e) {
            log.error("Failed to write timeout response", e);
          }
          // cancel() settles the future so the (now no-op) completion callback fires; note the
          // underlying work is NOT interrupted and may still run to completion in the background
          // (see CompletableFuture.cancel semantics).
          future.cancel(true);
          // Settle the DeferredResult so Spring does not dispatch an AsyncRequestTimeoutException
          // onto the committed response.
          deferredResult.setResult(null);
        });

    int successStatus =
        SuccessStatusResolver.resolveSuccessStatus(returnType, HttpStatus.OK.value());

    future.whenComplete(
        (result, throwable) -> {
          if (!responseWritten.compareAndSet(false, true)) {
            return;
          }
          try {
            if (throwable != null) {
              log.error("CompletableFuturePath failed", throwable);
              writeFailureResponse(throwable, response);
            } else {
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

  /**
   * Writes a failure response to the HTTP response.
   *
   * <p>When {@code includeExceptionDetails} is true, a structured error object is created with
   * {@code type} (exception class name) and {@code message} fields. This provides a consistent API
   * for clients while allowing identification of the error type.
   *
   * @param throwable the exception that occurred
   * @param response the HTTP response
   */
  private void writeFailureResponse(Throwable throwable, HttpServletResponse response) {
    try {
      response.setStatus(failureStatus);
      JsonResponses.setJsonContentType(response);

      Throwable cause = JsonResponses.unwrapAsyncException(throwable);

      ErrorResponseHeaders.applyTo(cause, response);

      Map<String, Object> errorBody;
      if (includeExceptionDetails) {
        // Include structured error with type and message for client identification
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
        errorBody = Map.of("success", false, "error", "An error occurred during async execution");
      }

      objectWriter.writeValue(response.getWriter(), errorBody);
    } catch (Exception e) {
      throw new RuntimeException("Failed to write failure response", e);
    }
  }

  /**
   * Writes a timeout response to the HTTP response.
   *
   * @param response the HTTP response
   */
  private void writeTimeoutResponse(HttpServletResponse response) {
    try {
      response.setStatus(HttpStatus.GATEWAY_TIMEOUT.value());
      JsonResponses.setJsonContentType(response);

      Map<String, Object> body = Map.of("success", false, "error", "Request timed out");
      objectWriter.writeValue(response.getWriter(), body);
    } catch (Exception e) {
      throw new RuntimeException("Failed to write timeout response", e);
    }
  }

  /**
   * Writes a success response to the HTTP response.
   *
   * @param value the result value
   * @param response the HTTP response
   * @param status the HTTP status code to set
   */
  private void writeSuccessResponse(Object value, HttpServletResponse response, int status) {
    try {
      response.setStatus(status);
      if (!JsonResponses.isBodilessStatus(status)) {
        JsonResponses.setJsonContentType(response);
        objectWriter.writeValue(response.getWriter(), value);
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to write success response", e);
    }
  }
}
