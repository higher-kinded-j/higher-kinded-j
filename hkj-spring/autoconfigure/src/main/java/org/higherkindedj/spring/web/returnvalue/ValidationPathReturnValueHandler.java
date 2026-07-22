// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
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
 *   <li>{@code Invalid} payloads made entirely of located {@link FieldError}s (the shape produced
 *       by a mapper {@code parse}, {@code Path.fields()} or {@code @GenerateAssembly}) → HTTP 422
 *       Unprocessable Content with each error rendered by path
 * </ul>
 *
 * <p>Both statuses are configurable: {@code hkj.web.validation-invalid-status} (default 400) and
 * {@code hkj.web.validation-field-error-status} (default 422).
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
 * <p>For {@code FieldError} payloads each error renders as an object carrying the dot-joined
 * display path plus the exact segments:
 *
 * <pre>{@code
 * {
 *   "valid": false,
 *   "errors": [ { "path": "address.zip", "segments": ["address", "zip"], "message": "must be 5 digits" } ],
 *   "errorCount": 1
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

  private final ObjectWriter objectWriter;
  private final int invalidStatus;
  private final int fieldErrorStatus;

  /**
   * Creates a new ValidationPathReturnValueHandler with the specified settings.
   *
   * @param jsonMapper the Jackson 3.x JsonMapper for JSON serialization
   * @param invalidStatus the HTTP status code for invalid results (default 400)
   * @param fieldErrorStatus the HTTP status code for invalid results made entirely of located
   *     {@link FieldError}s (default 422)
   */
  public ValidationPathReturnValueHandler(
      JsonMapper jsonMapper, int invalidStatus, int fieldErrorStatus) {
    this.objectWriter = jsonMapper.writer();
    this.invalidStatus = invalidStatus;
    this.fieldErrorStatus = fieldErrorStatus;
  }

  /**
   * Backward-compatible constructor preserved for programmatic adopters who built the handler
   * directly without going through the auto-configuration. Equivalent to constructing with the
   * default FieldError status of 422 Unprocessable Content.
   *
   * @param jsonMapper the Jackson 3.x JsonMapper for JSON serialization
   * @param invalidStatus the HTTP status code for invalid results (default 400)
   */
  public ValidationPathReturnValueHandler(JsonMapper jsonMapper, int invalidStatus) {
    this(jsonMapper, invalidStatus, HttpStatus.UNPROCESSABLE_CONTENT.value());
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

    int successStatus =
        SuccessStatusResolver.resolveSuccessStatus(returnType, HttpStatus.OK.value());

    // Fold to HTTP response
    validated.fold(
        errors -> {
          writeInvalidResponse(errors, response);
          return null;
        },
        value -> {
          writeValidResponse(value, response, successStatus);
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
      // A one-shot Iterable (neither Collection nor NonEmptyList) would be exhausted by the first
      // of applyTo/countErrors/Jackson, dropping errors from the later passes. Materialise it once;
      // Collection, NonEmptyList and array payloads are already re-traversable and pass through
      // unchanged so their JSON shape is preserved.
      Object materialised = JsonResponses.materialiseErrors(errors);
      List<FieldErrorItem> fieldErrorItems = fieldErrorItems(materialised);
      response.setStatus(fieldErrorItems == null ? invalidStatus : fieldErrorStatus);
      ErrorResponseHeaders.applyTo(materialised, response);
      JsonResponses.setJsonContentType(response);

      Object renderedErrors = fieldErrorItems == null ? materialised : fieldErrorItems;
      int errorCount = fieldErrorItems == null ? countErrors(materialised) : fieldErrorItems.size();
      Map<String, Object> body =
          Map.of("valid", false, "errors", renderedErrors, "errorCount", errorCount);

      objectWriter.writeValue(response.getWriter(), body);
    } catch (Exception e) {
      throw new RuntimeException("Failed to write validation error response", e);
    }
  }

  /**
   * Renders the payload as located field-error items when it consists entirely of {@link
   * FieldError}s — a {@link NonEmptyList}, {@link Collection} or array of them, or one bare error.
   *
   * @param errors the materialised error payload
   * @return the rendered items, or {@code null} when the payload is not the FieldError shape (a
   *     mixed or empty payload falls back to the generic rendering)
   */
  @Nullable
  private static List<FieldErrorItem> fieldErrorItems(Object errors) {
    Iterable<?> candidates =
        switch (errors) {
          case FieldError fieldError -> List.of(fieldError);
          case NonEmptyList<?> nel -> nel;
          case Collection<?> collection -> collection;
          case Object[] array -> Arrays.asList(array);
          default -> List.of();
        };
    List<FieldErrorItem> items = new ArrayList<>();
    for (Object candidate : candidates) {
      if (!(candidate instanceof FieldError fieldError)) {
        return null;
      }
      items.add(FieldErrorItem.of(fieldError));
    }
    return items.isEmpty() ? null : List.copyOf(items);
  }

  /**
   * Writes a valid (success) response to the HTTP response.
   *
   * @param value the valid value
   * @param response the HTTP response
   * @param status the HTTP status code to set
   */
  private void writeValidResponse(Object value, HttpServletResponse response, int status) {
    try {
      response.setStatus(status);
      if (!JsonResponses.isBodilessStatus(status)) {
        JsonResponses.setJsonContentType(response);
        objectWriter.writeValue(response.getWriter(), value);
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to write success response", e);
    }
  }

  /**
   * Counts the number of errors for the error count field.
   *
   * @param errors the errors object (may be a collection, another iterable such as {@code
   *     NonEmptyList}, an array, or a single error)
   * @return the count of errors
   */
  private int countErrors(Object errors) {
    return switch (errors) {
      case Collection<?> collection -> collection.size();
      // NonEmptyList (the idiomatic accumulation type) is Iterable but not a Collection
      case NonEmptyList<?> nel -> nel.size();
      case Iterable<?> iterable -> {
        int count = 0;
        for (var _ : iterable) {
          count++;
        }
        yield count;
      }
      case Object[] array -> array.length;
      default -> 1;
    };
  }

  /**
   * The rendered form of one {@link FieldError} in an invalid response body — the JSON wire shape
   * of the FieldError leg, kept package-private like every other rendering detail in this handler
   * family.
   *
   * <p>{@code path} is the dot-joined display key ({@link FieldError#pathString()}); {@code
   * segments} is the exact structured location, mirroring {@link FieldError#path()} under the
   * current string-segment model. A segment containing a dot is indistinguishable from nesting in
   * {@code path}, so consumers needing lossless locations must read {@code segments}.
   *
   * @param path the dot-joined path, for example {@code "address.zip"}; empty for unlabelled errors
   * @param segments the exact path segments, outermost first; never null, defensively copied
   * @param message the human-readable description; never null
   */
  record FieldErrorItem(String path, List<String> segments, String message) {

    /**
     * Canonical constructor; validates and defensively copies, mirroring {@link FieldError}.
     *
     * @throws NullPointerException if {@code path}, {@code segments}, any of its elements, or
     *     {@code message} is null
     */
    FieldErrorItem {
      Objects.requireNonNull(path, "path must not be null");
      Objects.requireNonNull(message, "message must not be null");
      segments = List.copyOf(segments);
    }

    /**
     * Renders the given error.
     *
     * @param error the located error to render; must not be null
     * @return the rendered item
     */
    static FieldErrorItem of(FieldError error) {
      return new FieldErrorItem(error.pathString(), error.path(), error.message());
    }
  }
}
