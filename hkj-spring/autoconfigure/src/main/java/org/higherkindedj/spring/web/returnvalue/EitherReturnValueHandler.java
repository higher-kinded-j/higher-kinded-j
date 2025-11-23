// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.higherkindedj.hkt.either.Either;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Return value handler that automatically converts {@link Either} return values from controller
 * methods into appropriate HTTP responses.
 *
 * <p>Conversion rules:
 *
 * <ul>
 *   <li>{@code Right<A>} → HTTP 200 OK with value as JSON body
 *   <li>{@code Left<E>} → HTTP 4xx/5xx with error as JSON body
 * </ul>
 *
 * <p>Error status codes are determined by examining the error class name:
 *
 * <ul>
 *   <li>Contains "notfound" → 404 Not Found
 *   <li>Contains "validation" or "invalid" → 400 Bad Request
 *   <li>Contains "authorization" or "forbidden" → 403 Forbidden
 *   <li>Contains "authentication" or "unauthorized" → 401 Unauthorized
 *   <li>Default → 400 Bad Request
 * </ul>
 */
public class EitherReturnValueHandler implements HandlerMethodReturnValueHandler {

  private final ObjectMapper objectMapper;
  private final int defaultErrorStatus;

  /** Creates a new EitherReturnValueHandler with default settings. */
  public EitherReturnValueHandler() {
    this(new ObjectMapper(), HttpStatus.BAD_REQUEST.value());
  }

  /**
   * Creates a new EitherReturnValueHandler with custom settings.
   *
   * @param objectMapper the ObjectMapper for JSON serialization
   * @param defaultErrorStatus the default HTTP status code for errors
   */
  public EitherReturnValueHandler(ObjectMapper objectMapper, int defaultErrorStatus) {
    this.objectMapper = objectMapper;
    this.defaultErrorStatus = defaultErrorStatus;
  }

  @Override
  public boolean supportsReturnType(MethodParameter returnType) {
    return Either.class.isAssignableFrom(returnType.getParameterType());
  }

  @Override
  public void handleReturnValue(
      Object returnValue,
      MethodParameter returnType,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest)
      throws IOException {

    mavContainer.setRequestHandled(true);
    HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);

    if (response == null) {
      return;
    }

    if (returnValue instanceof Either<?, ?> either) {
      either.fold(
          left -> {
            handleError(left, response);
            return null;
          },
          right -> {
            handleSuccess(right, response);
            return null;
          });
    }
  }

  /**
   * Handles error (Left) values by writing them to the HTTP response.
   *
   * @param error the error value
   * @param response the HTTP response
   */
  private void handleError(Object error, HttpServletResponse response) {
    try {
      int statusCode = ErrorStatusCodeMapper.determineStatusCode(error, defaultErrorStatus);
      response.setStatus(statusCode);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);

      Map<String, Object> errorBody = Map.of("success", false, "error", error);

      objectMapper.writeValue(response.getWriter(), errorBody);
    } catch (IOException e) {
      throw new RuntimeException("Failed to write error response", e);
    }
  }

  /**
   * Handles success (Right) values by writing them to the HTTP response.
   *
   * @param value the success value
   * @param response the HTTP response
   */
  private void handleSuccess(Object value, HttpServletResponse response) {
    try {
      response.setStatus(HttpStatus.OK.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      objectMapper.writeValue(response.getWriter(), value);
    } catch (IOException e) {
      throw new RuntimeException("Failed to write success response", e);
    }
  }
}
