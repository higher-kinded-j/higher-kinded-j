// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.either.Either;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Return value handler that converts {@link EitherPath} and {@link Either} return values from
 * controller methods into appropriate HTTP responses.
 *
 * <p>This handler is part of the Effect Path API integration for Spring Boot 4.0.1+. It provides
 * railway-oriented programming semantics at the controller layer, automatically converting success
 * and failure paths to HTTP responses.
 *
 * <p>Supports both:
 *
 * <ul>
 *   <li>{@link EitherPath} - The Effect Path API wrapper
 *   <li>{@link Either} - The raw HKT Either type
 * </ul>
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
 *   <li>Default → configurable (default 400 Bad Request)
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @GetMapping("/users/{id}")
 * public Either<UserError, User> getUser(@PathVariable String id) {
 *     return userService.findById(id);
 * }
 *
 * @GetMapping("/users/{id}")
 * public EitherPath<UserError, User> getUser(@PathVariable String id) {
 *     return Path.either(userService.findById(id))
 *         .peek(user -> log.info("Found user: {}", user.id()));
 * }
 * }</pre>
 *
 * @see EitherPath
 * @see Either
 * @see ErrorStatusCodeMapper
 */
public class EitherPathReturnValueHandler implements HandlerMethodReturnValueHandler {

  private final JsonMapper jsonMapper;
  private final ObjectWriter objectWriter;
  private final int defaultErrorStatus;

  /**
   * Creates a new EitherPathReturnValueHandler with the specified settings.
   *
   * @param jsonMapper the Jackson 3.x JsonMapper for JSON serialization
   * @param defaultErrorStatus the default HTTP status code for errors
   */
  public EitherPathReturnValueHandler(JsonMapper jsonMapper, int defaultErrorStatus) {
    this.jsonMapper = jsonMapper;
    this.objectWriter = jsonMapper.writer();
    this.defaultErrorStatus = defaultErrorStatus;
  }

  @Override
  public boolean supportsReturnType(MethodParameter returnType) {
    Class<?> paramType = returnType.getParameterType();
    return EitherPath.class.isAssignableFrom(paramType) || Either.class.isAssignableFrom(paramType);
  }

  @Override
  public void handleReturnValue(
      @Nullable Object returnValue,
      MethodParameter returnType,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest) {

    mavContainer.setRequestHandled(true);
    HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);

    if (response == null) {
      return;
    }

    Either<?, ?> either = extractEither(returnValue);
    if (either == null) {
      return;
    }

    // Fold to HTTP response
    either.fold(
        error -> {
          writeErrorResponse(error, response);
          return null;
        },
        value -> {
          writeSuccessResponse(value, response);
          return null;
        });
  }

  /**
   * Extracts the Either from either an EitherPath or raw Either.
   *
   * @param returnValue the return value
   * @return the Either, or null if not supported
   */
  @Nullable
  private Either<?, ?> extractEither(@Nullable Object returnValue) {
    if (returnValue instanceof EitherPath<?, ?> path) {
      return path.run();
    } else if (returnValue instanceof Either<?, ?> either) {
      return either;
    }
    return null;
  }

  /**
   * Writes an error (Left) value to the HTTP response.
   *
   * @param error the error value
   * @param response the HTTP response
   */
  private void writeErrorResponse(Object error, HttpServletResponse response) {
    try {
      int statusCode = ErrorStatusCodeMapper.determineStatusCode(error, defaultErrorStatus);
      response.setStatus(statusCode);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);

      Map<String, Object> errorBody = Map.of("success", false, "error", error);
      objectWriter.writeValue(response.getWriter(), errorBody);
    } catch (Exception e) {
      throw new RuntimeException("Failed to write error response", e);
    }
  }

  /**
   * Writes a success (Right) value to the HTTP response.
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
