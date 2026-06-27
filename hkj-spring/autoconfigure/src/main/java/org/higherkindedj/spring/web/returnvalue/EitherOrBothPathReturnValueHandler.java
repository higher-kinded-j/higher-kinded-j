// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.higherkindedj.hkt.effect.EitherOrBothPath;
import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
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
 * Return value handler that converts {@link EitherOrBothPath} and {@link EitherOrBoth} (the
 * inclusive-or) return values from controller methods into HTTP responses.
 *
 * <p>{@code EitherOrBoth} models "success that may also carry non-fatal warnings", so this handler
 * has three branches rather than two:
 *
 * <ul>
 *   <li>{@code Right<A>} → success status (200, or the {@code @ResponseStatus} on the handler) with
 *       the value as the JSON body.
 *   <li>{@code Both<W, A>} → the same success status with the value as the JSON body, and the
 *       warnings surfaced (never silently dropped) in the {@value #WARNINGS_HEADER} response header
 *       as JSON.
 *   <li>{@code Left<W>} → 4xx/5xx with the warnings as the JSON error body; the status is resolved
 *       by the supplied {@link ErrorStatusCodeStrategy}, matching {@link
 *       EitherPathReturnValueHandler}.
 * </ul>
 *
 * <p>If a {@code Left} value implements {@link HttpHeaderCarrier}, its headers are copied onto the
 * response before the body is written.
 *
 * @see EitherOrBothPath
 * @see EitherOrBoth
 * @see ErrorStatusCodeStrategy
 */
public class EitherOrBothPathReturnValueHandler implements HandlerMethodReturnValueHandler {

  /** Response header carrying the JSON-encoded warnings of a {@code Both} result. */
  public static final String WARNINGS_HEADER = "X-Hkj-Warnings";

  private final JsonMapper jsonMapper;
  private final ObjectWriter objectWriter;
  private final int defaultErrorStatus;
  private final ErrorStatusCodeStrategy errorStatusCodeStrategy;

  /**
   * Backward-compatible constructor using heuristics-only error status resolution.
   *
   * @param jsonMapper the Jackson 3.x JsonMapper for JSON serialization
   * @param defaultErrorStatus the default HTTP status code for a {@code Left} when no rule matches
   */
  public EitherOrBothPathReturnValueHandler(JsonMapper jsonMapper, int defaultErrorStatus) {
    this(jsonMapper, defaultErrorStatus, new DefaultErrorStatusCodeStrategy(Map.of()));
  }

  /**
   * Creates a new handler with the specified settings.
   *
   * @param jsonMapper the Jackson 3.x JsonMapper for JSON serialization
   * @param defaultErrorStatus the default HTTP status code for a {@code Left} when no rule matches
   * @param errorStatusCodeStrategy the strategy that resolves the status code for a {@code Left}
   */
  public EitherOrBothPathReturnValueHandler(
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
    return EitherOrBothPath.class.isAssignableFrom(paramType)
        || EitherOrBoth.class.isAssignableFrom(paramType);
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

    EitherOrBoth<?, ?> eob = extractEitherOrBoth(returnValue);
    if (eob == null) {
      return;
    }

    int successStatus =
        SuccessStatusResolver.resolveSuccessStatus(returnType, HttpStatus.OK.value());

    eob.<Void>fold(
        warnings -> {
          writeErrorResponse(warnings, response);
          return null;
        },
        value -> {
          writeSuccessResponse(value, response, successStatus);
          return null;
        },
        (warnings, value) -> {
          writeBothResponse(warnings, value, response, successStatus);
          return null;
        });
  }

  @Nullable
  private EitherOrBoth<?, ?> extractEitherOrBoth(@Nullable Object returnValue) {
    if (returnValue instanceof EitherOrBothPath<?, ?> path) {
      return path.run();
    } else if (returnValue instanceof EitherOrBoth<?, ?> eob) {
      return eob;
    }
    return null;
  }

  private void writeErrorResponse(Object warnings, HttpServletResponse response) {
    try {
      int statusCode = errorStatusCodeStrategy.statusCodeFor(warnings, defaultErrorStatus);
      response.setStatus(statusCode);
      ErrorResponseHeaders.applyTo(warnings, response);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);

      Map<String, Object> errorBody = Map.of("success", false, "error", warnings);
      objectWriter.writeValue(response.getWriter(), errorBody);
    } catch (Exception e) {
      throw new RuntimeException("Failed to write error response", e);
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

  private void writeBothResponse(
      Object warnings, Object value, HttpServletResponse response, int status) {
    try {
      response.setStatus(status);
      // Warnings are surfaced as a header so the success body stays the bare value.
      response.setHeader(WARNINGS_HEADER, objectWriter.writeValueAsString(warnings));
      if (status != HttpStatus.NO_CONTENT.value()) {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectWriter.writeValue(response.getWriter(), value);
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to write success-with-warnings response", e);
    }
  }
}
