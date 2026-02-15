// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.higherkindedj.hkt.effect.MaybePath;
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
 * Return value handler that converts {@link MaybePath} return values from controller methods into
 * appropriate HTTP responses.
 *
 * <p>This handler is part of the Effect Path API integration for Spring Boot 4.0.1+. It handles
 * optional values with railway-oriented programming semantics.
 *
 * <p>Conversion rules:
 *
 * <ul>
 *   <li>{@code Just<A>} → HTTP 200 OK with value as JSON body
 *   <li>{@code Nothing} → HTTP 404 Not Found (configurable)
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @GetMapping("/users/{id}")
 * public MaybePath<User> getUser(@PathVariable String id) {
 *     return Path.maybe(userRepository.findById(id))
 *         .peek(user -> log.info("Found user: {}", user.id()));
 * }
 * }</pre>
 *
 * @see MaybePath
 */
public class MaybePathReturnValueHandler implements HandlerMethodReturnValueHandler {

  private final JsonMapper jsonMapper;
  private final ObjectWriter objectWriter;
  private final int nothingStatus;

  /**
   * Creates a new MaybePathReturnValueHandler with the specified settings.
   *
   * @param jsonMapper the Jackson 3.x JsonMapper for JSON serialization
   * @param nothingStatus the HTTP status code for Nothing values (default 404)
   */
  public MaybePathReturnValueHandler(JsonMapper jsonMapper, int nothingStatus) {
    this.jsonMapper = jsonMapper;
    this.objectWriter = jsonMapper.writer();
    this.nothingStatus = nothingStatus;
  }

  @Override
  public boolean supportsReturnType(MethodParameter returnType) {
    return MaybePath.class.isAssignableFrom(returnType.getParameterType());
  }

  @Override
  public void handleReturnValue(
      @Nullable Object returnValue,
      MethodParameter returnType,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest) {

    mavContainer.setRequestHandled(true);
    HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);

    if (response == null || !(returnValue instanceof MaybePath<?> path)) {
      return;
    }

    // Extract underlying Maybe and convert to HTTP response
    var maybe = path.run();
    if (maybe.isJust()) {
      writeJustResponse(maybe.get(), response);
    } else {
      writeNothingResponse(response);
    }
  }

  /**
   * Writes a Nothing response to the HTTP response.
   *
   * @param response the HTTP response
   */
  private void writeNothingResponse(HttpServletResponse response) {
    try {
      response.setStatus(nothingStatus);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);

      Map<String, Object> body = Map.of("success", false, "error", "Resource not found");
      objectWriter.writeValue(response.getWriter(), body);
    } catch (Exception e) {
      throw new RuntimeException("Failed to write nothing response", e);
    }
  }

  /**
   * Writes a Just value to the HTTP response.
   *
   * @param value the value
   * @param response the HTTP response
   */
  private void writeJustResponse(Object value, HttpServletResponse response) {
    try {
      response.setStatus(HttpStatus.OK.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      objectWriter.writeValue(response.getWriter(), value);
    } catch (Exception e) {
      throw new RuntimeException("Failed to write success response", e);
    }
  }
}
