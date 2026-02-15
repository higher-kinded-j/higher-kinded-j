// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Collection;
import java.util.Map;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.hkt.validated.Validated;
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
 * Return value handler that converts {@link ValidationPath} and {@link Validated} return values
 * from controller methods into appropriate HTTP responses.
 *
 * <p>This handler is part of the Effect Path API integration for Spring Boot 4.0.1+. It handles
 * validation results with error accumulation semantics, collecting ALL validation errors rather
 * than failing on the first error.
 *
 * <p>Supports both:
 *
 * <ul>
 *   <li>{@link ValidationPath} - The Effect Path API wrapper
 *   <li>{@link Validated} - The raw HKT Validated type
 * </ul>
 *
 * <p>Conversion rules:
 *
 * <ul>
 *   <li>{@code Valid<A>} → HTTP 200 OK with value as JSON body
 *   <li>{@code Invalid<E>} → HTTP 400 Bad Request with all accumulated errors
 * </ul>
 *
 * <p>The response format for validation errors:
 *
 * <pre>{@code
 * {
 *   "valid": false,
 *   "errors": [...accumulated errors...],
 *   "errorCount": 3
 * }
 * }</pre>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @PostMapping("/users")
 * public Validated<List<ValidationError>, User> createUser(@RequestBody CreateUserRequest req) {
 *     return validateEmail(req.email())
 *         .zipWith(validateName(req.firstName()), (email, name) -> new User(email, name));
 * }
 *
 * @PostMapping("/users")
 * public ValidationPath<List<ValidationError>, User> createUser(@RequestBody CreateUserRequest req) {
 *     return Path.<List<ValidationError>, String>valid(req.email())
 *         .zipWith3Accum(
 *             validateName(req.firstName()),
 *             validateAge(req.age()),
 *             User::new
 *         );
 * }
 * }</pre>
 *
 * @see ValidationPath
 * @see Validated
 */
public class ValidationPathReturnValueHandler implements HandlerMethodReturnValueHandler {

  private final JsonMapper jsonMapper;
  private final ObjectWriter objectWriter;
  private final int invalidStatus;

  /**
   * Creates a new ValidationPathReturnValueHandler with the specified settings.
   *
   * @param jsonMapper the Jackson 3.x JsonMapper for JSON serialization
   * @param invalidStatus the HTTP status code for invalid results (default 400)
   */
  public ValidationPathReturnValueHandler(JsonMapper jsonMapper, int invalidStatus) {
    this.jsonMapper = jsonMapper;
    this.objectWriter = jsonMapper.writer();
    this.invalidStatus = invalidStatus;
  }

  @Override
  public boolean supportsReturnType(MethodParameter returnType) {
    Class<?> paramType = returnType.getParameterType();
    return ValidationPath.class.isAssignableFrom(paramType)
        || Validated.class.isAssignableFrom(paramType);
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

    Validated<?, ?> validated = extractValidated(returnValue);
    if (validated == null) {
      return;
    }

    // Fold to HTTP response
    validated.fold(
        errors -> {
          writeInvalidResponse(errors, response);
          return null;
        },
        value -> {
          writeValidResponse(value, response);
          return null;
        });
  }

  /**
   * Extracts the Validated from either a ValidationPath or raw Validated.
   *
   * @param returnValue the return value
   * @return the Validated, or null if not supported
   */
  @Nullable
  private Validated<?, ?> extractValidated(@Nullable Object returnValue) {
    if (returnValue instanceof ValidationPath<?, ?> path) {
      return path.run();
    } else if (returnValue instanceof Validated<?, ?> validated) {
      return validated;
    }
    return null;
  }

  /**
   * Writes an invalid (errors) response to the HTTP response.
   *
   * @param errors the accumulated validation errors
   * @param response the HTTP response
   */
  private void writeInvalidResponse(Object errors, HttpServletResponse response) {
    try {
      response.setStatus(invalidStatus);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);

      int errorCount = countErrors(errors);
      Map<String, Object> body = Map.of("valid", false, "errors", errors, "errorCount", errorCount);

      objectWriter.writeValue(response.getWriter(), body);
    } catch (Exception e) {
      throw new RuntimeException("Failed to write validation error response", e);
    }
  }

  /**
   * Writes a valid (success) response to the HTTP response.
   *
   * @param value the valid value
   * @param response the HTTP response
   */
  private void writeValidResponse(Object value, HttpServletResponse response) {
    try {
      response.setStatus(HttpStatus.OK.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      objectWriter.writeValue(response.getWriter(), value);
    } catch (Exception e) {
      throw new RuntimeException("Failed to write success response", e);
    }
  }

  /**
   * Counts the number of errors for the error count field.
   *
   * @param errors the errors object (may be a collection or single error)
   * @return the count of errors
   */
  private int countErrors(Object errors) {
    if (errors instanceof Collection<?> collection) {
      return collection.size();
    } else if (errors instanceof Object[] array) {
      return array.length;
    } else {
      return 1;
    }
  }
}
