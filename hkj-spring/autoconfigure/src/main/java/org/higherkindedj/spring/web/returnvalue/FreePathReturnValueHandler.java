// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.higherkindedj.hkt.effect.FreePath;
import org.higherkindedj.hkt.effect.boundary.EffectBoundary;
import org.higherkindedj.hkt.trymonad.Try;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Return value handler that interprets {@link FreePath} return values from controller methods using
 * an {@link EffectBoundary} bean from the application context, then writes the result as JSON.
 *
 * <p>This handler is the ninth return value handler in the hkj-spring family. It follows the same
 * pattern as {@link IOPathReturnValueHandler}: detect the return type, interpret, serialise the
 * result, and map errors to HTTP status codes.
 *
 * <p>The handler looks up a single {@link EffectBoundary} bean from the application context. If no
 * bean is found, the handler is effectively a no-op (returns null for unsupported return values).
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @GetMapping("/status")
 * public FreePath<OrderEffects, OrderStatus> getStatus(@PathVariable String id) {
 *     return service.getOrderStatus(id);
 *     // Handler interprets the program and serialises the result as JSON
 * }
 * }</pre>
 *
 * @see FreePath
 * @see EffectBoundary
 */
public class FreePathReturnValueHandler implements HandlerMethodReturnValueHandler {

  private static final Logger log = LoggerFactory.getLogger(FreePathReturnValueHandler.class);

  private final JsonMapper jsonMapper;
  private final ObjectWriter objectWriter;
  private final int failureStatus;
  private final boolean includeExceptionDetails;
  private final ApplicationContext applicationContext;
  private volatile EffectBoundary<?> cachedBoundary;

  /**
   * Creates a new FreePathReturnValueHandler.
   *
   * @param jsonMapper the Jackson 3.x JsonMapper for JSON serialisation
   * @param failureStatus the HTTP status code for interpretation failures (default 500)
   * @param includeExceptionDetails whether to include exception details in error responses
   * @param applicationContext the Spring application context for looking up EffectBoundary beans
   */
  public FreePathReturnValueHandler(
      JsonMapper jsonMapper,
      int failureStatus,
      boolean includeExceptionDetails,
      ApplicationContext applicationContext) {
    this.jsonMapper = jsonMapper;
    this.objectWriter = jsonMapper.writer();
    this.failureStatus = failureStatus;
    this.includeExceptionDetails = includeExceptionDetails;
    this.applicationContext = applicationContext;
  }

  @Override
  public boolean supportsReturnType(MethodParameter returnType) {
    return FreePath.class.isAssignableFrom(returnType.getParameterType());
  }

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public void handleReturnValue(
      @Nullable Object returnValue,
      MethodParameter returnType,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest) {

    mavContainer.setRequestHandled(true);
    HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);

    if (response == null || !(returnValue instanceof FreePath<?, ?> freePath)) {
      return;
    }

    // Look up the EffectBoundary bean (cached after first resolution)
    EffectBoundary boundary = cachedBoundary;
    if (boundary == null) {
      try {
        boundary = applicationContext.getBean(EffectBoundary.class);
        cachedBoundary = boundary;
      } catch (Exception e) {
        log.error(
            "No EffectBoundary bean found in ApplicationContext. "
                + "Register an EffectBoundary bean or use @EnableEffectBoundary.",
            e);
        writeFailureResponse(e, response);
        return;
      }
    }

    int successStatus =
        SuccessStatusResolver.resolveSuccessStatus(returnType, HttpStatus.OK.value());

    // Interpret the program via the boundary
    Try<?> result = boundary.runSafe(freePath.toFree());
    result.fold(
        value -> {
          writeSuccessResponse(value, response, successStatus);
          return null;
        },
        throwable -> {
          log.error("FreePath interpretation failed in controller method", throwable);
          writeFailureResponse(throwable, response);
          return null;
        });
  }

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
