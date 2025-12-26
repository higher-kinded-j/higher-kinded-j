// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.higherkindedj.hkt.effect.IOPath;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Return value handler that converts {@link IOPath} return values from controller methods into
 * appropriate HTTP responses.
 *
 * <p>This handler is part of the Effect Path API integration for Spring Boot 4.0.1+. It handles
 * deferred side-effecting computations, executing them at the framework edge (when the response is
 * being written).
 *
 * <p>Key characteristics:
 *
 * <ul>
 *   <li>Lazy evaluation: IO is not executed until this handler processes it
 *   <li>Resource management: Use {@code ensuring()} for cleanup guarantees
 *   <li>Exception handling: Failures are caught and converted to HTTP 500
 * </ul>
 *
 * <p>Conversion rules:
 *
 * <ul>
 *   <li>Successful execution → HTTP 200 OK with result as JSON body
 *   <li>Failed execution → HTTP 500 Internal Server Error (configurable)
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @GetMapping("/report")
 * public IOPath<Report> generateReport() {
 *     return Path.io(() -> reportService.generate())
 *         .peek(r -> log.info("Generated report: {}", r.id()))
 *         .ensuring(() -> cleanupTempFiles());
 * }
 * }</pre>
 *
 * @see IOPath
 */
public class IOPathReturnValueHandler implements HandlerMethodReturnValueHandler {

  private static final Logger log = LoggerFactory.getLogger(IOPathReturnValueHandler.class);

  private final JsonMapper jsonMapper;
  private final ObjectWriter objectWriter;
  private final int failureStatus;
  private final boolean includeExceptionDetails;

  /**
   * Creates a new IOPathReturnValueHandler with the specified settings.
   *
   * @param jsonMapper the Jackson 3.x JsonMapper for JSON serialization
   * @param failureStatus the HTTP status code for execution failures (default 500)
   * @param includeExceptionDetails whether to include exception details in error responses
   */
  public IOPathReturnValueHandler(
      JsonMapper jsonMapper, int failureStatus, boolean includeExceptionDetails) {
    this.jsonMapper = jsonMapper;
    this.objectWriter = jsonMapper.writer();
    this.failureStatus = failureStatus;
    this.includeExceptionDetails = includeExceptionDetails;
  }

  @Override
  public boolean supportsReturnType(MethodParameter returnType) {
    return IOPath.class.isAssignableFrom(returnType.getParameterType());
  }

  @Override
  public void handleReturnValue(
      @Nullable Object returnValue,
      MethodParameter returnType,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest) {

    mavContainer.setRequestHandled(true);
    HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);

    if (response == null || !(returnValue instanceof IOPath<?> ioPath)) {
      return;
    }

    // Execute the deferred IO at the edge and convert result to HTTP response
    ioPath
        .runSafe()
        .fold(
            value -> {
              writeSuccessResponse(value, response);
              return null;
            },
            throwable -> {
              log.error("IOPath execution failed in controller method", throwable);
              writeFailureResponse(throwable, response);
              return null;
            });
  }

  /**
   * Writes a failure response to the HTTP response.
   *
   * @param throwable the exception that occurred during IO execution
   * @param response the HTTP response
   */
  private void writeFailureResponse(Throwable throwable, HttpServletResponse response) {
    try {
      response.setStatus(failureStatus);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);

      Map<String, Object> errorBody;
      if (includeExceptionDetails) {
        errorBody =
            Map.of(
                "success",
                false,
                "error",
                Map.of(
                    "type",
                    throwable.getClass().getSimpleName(),
                    "message",
                    throwable.getMessage() != null ? throwable.getMessage() : "No message"));
      } else {
        errorBody = Map.of("success", false, "error", "An error occurred during execution");
      }

      objectWriter.writeValue(response.getWriter(), errorBody);
    } catch (Exception e) {
      throw new RuntimeException("Failed to write failure response", e);
    }
  }

  /**
   * Writes a success response to the HTTP response.
   *
   * @param value the result of successful IO execution
   * @param response the HTTP response
   */
  private void writeSuccessResponse(Object value, HttpServletResponse response) {
    try {
      response.setStatus(HttpStatus.OK.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      objectWriter.writeValue(response.getWriter(), value);
    } catch (Exception e) {
      throw new RuntimeException("Failed to write success response", e);
    }
  }
}
