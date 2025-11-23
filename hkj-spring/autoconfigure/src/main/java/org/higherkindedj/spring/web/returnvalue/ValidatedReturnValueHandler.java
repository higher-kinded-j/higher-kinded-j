// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.higherkindedj.hkt.validated.Validated;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Return value handler that automatically converts {@link Validated} return values from controller
 * methods into appropriate HTTP responses.
 *
 * <p>Conversion rules:
 *
 * <ul>
 *   <li>{@code Valid<A>} → HTTP 200 OK with value as JSON body
 *   <li>{@code Invalid<E>} → HTTP 400 Bad Request with errors as JSON body
 * </ul>
 *
 * <p>The key advantage of Validated over Either is <strong>error accumulation</strong>. When using
 * Applicative operations with Validated, ALL validation errors are collected and returned together,
 * rather than failing fast at the first error.
 *
 * <p>Example response for Invalid:
 *
 * <pre>{@code
 * {
 *   "valid": false,
 *   "errors": [
 *     {"field": "email", "message": "Invalid email format"},
 *     {"field": "firstName", "message": "First name is required"},
 *     {"field": "age", "message": "Must be at least 18"}
 *   ]
 * }
 * }</pre>
 */
public class ValidatedReturnValueHandler implements HandlerMethodReturnValueHandler {

  private final ObjectMapper objectMapper;

  /** Creates a new ValidatedReturnValueHandler with default settings. */
  public ValidatedReturnValueHandler() {
    this(new ObjectMapper());
  }

  /**
   * Creates a new ValidatedReturnValueHandler with a custom ObjectMapper.
   *
   * @param objectMapper the ObjectMapper for JSON serialization
   */
  public ValidatedReturnValueHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean supportsReturnType(MethodParameter returnType) {
    return Validated.class.isAssignableFrom(returnType.getParameterType());
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

    if (returnValue instanceof Validated<?, ?> validated) {
      validated.fold(
          errors -> {
            handleInvalid(errors, response);
            return null;
          },
          value -> {
            handleValid(value, response);
            return null;
          });
    }
  }

  /**
   * Handles invalid (error) values by writing them to the HTTP response.
   *
   * @param errors the error value(s)
   * @param response the HTTP response
   */
  private void handleInvalid(Object errors, HttpServletResponse response) {
    try {
      response.setStatus(HttpStatus.BAD_REQUEST.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);

      Map<String, Object> errorBody = Map.of("valid", false, "errors", errors);

      objectMapper.writeValue(response.getWriter(), errorBody);
    } catch (IOException e) {
      throw new RuntimeException("Failed to write invalid response", e);
    }
  }

  /**
   * Handles valid (success) values by writing them to the HTTP response.
   *
   * @param value the success value
   * @param response the HTTP response
   */
  private void handleValid(Object value, HttpServletResponse response) {
    try {
      response.setStatus(HttpStatus.OK.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      objectMapper.writeValue(response.getWriter(), value);
    } catch (IOException e) {
      throw new RuntimeException("Failed to write valid response", e);
    }
  }
}
