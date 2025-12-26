// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.higherkindedj.hkt.effect.TryPath;
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
 * Return value handler that converts {@link TryPath} return values from controller methods into
 * appropriate HTTP responses.
 *
 * <p>This handler is part of the Effect Path API integration for Spring Boot 4.0.1+. It handles
 * exception-safe computations, automatically converting success and failure to HTTP responses.
 *
 * <p>Conversion rules:
 *
 * <ul>
 *   <li>{@code Success<A>} → HTTP 200 OK with value as JSON body
 *   <li>{@code Failure<Throwable>} → HTTP 500 Internal Server Error (configurable)
 * </ul>
 *
 * <p>Exception details can be included or hidden based on configuration (recommended to hide in
 * production).
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @GetMapping("/config")
 * public TryPath<Config> getConfig() {
 *     return Path.tryOf(() -> configService.loadConfig())
 *         .recover(ex -> Config.defaults());
 * }
 * }</pre>
 *
 * @see TryPath
 */
public class TryPathReturnValueHandler implements HandlerMethodReturnValueHandler {

  private static final Logger log = LoggerFactory.getLogger(TryPathReturnValueHandler.class);

  private final JsonMapper jsonMapper;
  private final ObjectWriter objectWriter;
  private final int failureStatus;
  private final boolean includeExceptionDetails;

  /**
   * Creates a new TryPathReturnValueHandler with the specified settings.
   *
   * @param jsonMapper the Jackson 3.x JsonMapper for JSON serialization
   * @param failureStatus the HTTP status code for failures (default 500)
   * @param includeExceptionDetails whether to include exception details in the response
   */
  public TryPathReturnValueHandler(
      JsonMapper jsonMapper, int failureStatus, boolean includeExceptionDetails) {
    this.jsonMapper = jsonMapper;
    this.objectWriter = jsonMapper.writer();
    this.failureStatus = failureStatus;
    this.includeExceptionDetails = includeExceptionDetails;
  }

  @Override
  public boolean supportsReturnType(MethodParameter returnType) {
    return TryPath.class.isAssignableFrom(returnType.getParameterType());
  }

  @Override
  public void handleReturnValue(
      @Nullable Object returnValue,
      MethodParameter returnType,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest) {

    mavContainer.setRequestHandled(true);
    HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);

    if (response == null || !(returnValue instanceof TryPath<?> path)) {
      return;
    }

    // Extract underlying Try and convert to HTTP response
    path.run()
        .fold(
            value -> {
              writeSuccessResponse(value, response);
              return null;
            },
            throwable -> {
              log.error("TryPath failure in controller method", throwable);
              writeFailureResponse(throwable, response);
              return null;
            });
  }

  /**
   * Writes a failure (exception) response to the HTTP response.
   *
   * @param throwable the exception that occurred
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
        errorBody = Map.of("success", false, "error", "An internal error occurred");
      }

      objectWriter.writeValue(response.getWriter(), errorBody);
    } catch (Exception e) {
      throw new RuntimeException("Failed to write failure response", e);
    }
  }

  /**
   * Writes a success value to the HTTP response.
   *
   * @param value the success value
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
