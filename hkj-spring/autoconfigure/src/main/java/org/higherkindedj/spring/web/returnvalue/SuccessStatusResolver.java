// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Utility class for resolving the success HTTP status code for a controller method.
 *
 * <p>Honors Spring's {@link ResponseStatus} annotation when declared on the handler method or on
 * its containing controller class (including meta-annotated usages, e.g. a custom
 * {@code @CreatedStatus} annotation that is itself annotated with {@code @ResponseStatus}).
 *
 * <p>Precedence:
 *
 * <ol>
 *   <li>{@code @ResponseStatus} on the handler method
 *   <li>{@code @ResponseStatus} on the containing controller class
 *   <li>The supplied fallback status (typically {@link HttpStatus#OK})
 * </ol>
 *
 * <p>This allows controllers to use canonical REST semantics with Effect Path return types:
 *
 * <pre>{@code
 * @PostMapping
 * @ResponseStatus(HttpStatus.CREATED)
 * public EitherPath<DomainError, User> createUser(...) { ... }
 *
 * @DeleteMapping("/{id}")
 * @ResponseStatus(HttpStatus.NO_CONTENT)
 * public MaybePath<Void> deleteUser(...) { ... }
 * }</pre>
 */
public final class SuccessStatusResolver {

  private SuccessStatusResolver() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Resolves the success HTTP status code for the given return type.
   *
   * @param returnType the method parameter describing the controller method's return type (may be
   *     null, in which case {@code defaultStatus} is returned)
   * @param defaultStatus the status code to use when no {@code @ResponseStatus} annotation is
   *     present on the method or its declaring class
   * @return the resolved HTTP status code
   */
  public static int resolveSuccessStatus(@Nullable MethodParameter returnType, int defaultStatus) {
    if (returnType == null) {
      return defaultStatus;
    }

    Method method = returnType.getMethod();
    ResponseStatus annotation = null;
    if (method != null) {
      annotation = AnnotatedElementUtils.findMergedAnnotation(method, ResponseStatus.class);
    }
    if (annotation == null) {
      Class<?> containingClass = returnType.getContainingClass();
      if (containingClass != null) {
        annotation =
            AnnotatedElementUtils.findMergedAnnotation(containingClass, ResponseStatus.class);
      }
    }

    if (annotation != null) {
      return annotation.code().value();
    }
    return defaultStatus;
  }
}
