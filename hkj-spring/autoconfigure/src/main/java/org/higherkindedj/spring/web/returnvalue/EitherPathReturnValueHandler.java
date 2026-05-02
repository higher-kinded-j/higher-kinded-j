// Copyright (c) 2025 - 2026 Magnus Smith
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
 *   <li>{@code Right<A>} → HTTP 200 OK with value as JSON body (or the status declared by {@link
 *       org.springframework.web.bind.annotation.ResponseStatus @ResponseStatus} on the handler
 *       method/class)
 *   <li>{@code Left<E>} → HTTP 4xx/5xx with error as JSON body
 * </ul>
 *
 * <p>Error status codes are determined by the supplied {@link ErrorStatusCodeStrategy}. By default
 * the auto-configuration installs {@link DefaultErrorStatusCodeStrategy}, which combines explicit
 * {@code hkj.web.error-status-mappings} entries with the heuristics in {@link
 * ErrorStatusCodeMapper}.
 *
 * <p>If the {@code Left} value implements {@link HttpHeaderCarrier}, its headers are copied onto
 * the response before the body is written — this is how throttling errors surface a {@code
 * Retry-After} header, for example.
 *
 * @see EitherPath
 * @see Either
 * @see ErrorStatusCodeStrategy
 * @see HttpHeaderCarrier
 */
public class EitherPathReturnValueHandler implements HandlerMethodReturnValueHandler {

  private final JsonMapper jsonMapper;
  private final ObjectWriter objectWriter;
  private final int defaultErrorStatus;
  private final ErrorStatusCodeStrategy errorStatusCodeStrategy;

  /**
   * Backward-compatible constructor preserved for programmatic adopters who built the handler
   * directly without going through the auto-configuration. Equivalent to constructing with a {@link
   * DefaultErrorStatusCodeStrategy} backed by an empty mapping table — i.e. heuristics only.
   *
   * @param jsonMapper the Jackson 3.x JsonMapper for JSON serialization
   * @param defaultErrorStatus the default HTTP status code for errors
   */
  public EitherPathReturnValueHandler(JsonMapper jsonMapper, int defaultErrorStatus) {
    this(jsonMapper, defaultErrorStatus, new DefaultErrorStatusCodeStrategy(Map.of()));
  }

  /**
   * Creates a new EitherPathReturnValueHandler with the specified settings.
   *
   * @param jsonMapper the Jackson 3.x JsonMapper for JSON serialization
   * @param defaultErrorStatus the default HTTP status code for errors when no rule matches
   * @param errorStatusCodeStrategy the strategy that resolves the status code for an error
   */
  public EitherPathReturnValueHandler(
      JsonMapper jsonMapper,
      int defaultErrorStatus,
      ErrorStatusCodeStrategy errorStatusCodeStrategy) {
    this.jsonMapper = jsonMapper;
    this.objectWriter = jsonMapper.writer();
    this.defaultErrorStatus = defaultErrorStatus;
    this.errorStatusCodeStrategy = errorStatusCodeStrategy;
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

    int successStatus =
        SuccessStatusResolver.resolveSuccessStatus(returnType, HttpStatus.OK.value());

    // Fold to HTTP response
    either.fold(
        error -> {
          writeErrorResponse(error, response);
          return null;
        },
        value -> {
          writeSuccessResponse(value, response, successStatus);
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
      int statusCode = errorStatusCodeStrategy.statusCodeFor(error, defaultErrorStatus);
      response.setStatus(statusCode);
      ErrorResponseHeaders.applyTo(error, response);
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
   * @param status the HTTP status code to set
   */
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
