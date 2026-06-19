// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maps a specific HTTP status code on a {@link HkjHttpClient} method to a specific error type.
 *
 * <p>By default the generated client decodes every non-2xx response into the method's declared
 * error type {@code E}. When a status warrants a distinct subtype, annotate the method:
 *
 * <pre>{@code
 * @GetExchange("/{id}")
 * @OnStatus(value = 404, error = UserNotFoundError.class)
 * @OnStatus(value = 409, error = ConflictError.class)
 * EitherPath<DomainError, UserDto> getUser(@PathVariable String id);
 * }</pre>
 *
 * <p>Each {@code error()} type must be assignable to the method's declared error type. Statuses
 * with no override fall back to decoding into the declared type.
 */
@Documented
@Repeatable(OnStatuses.class)
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface OnStatus {

  /**
   * The HTTP status code this override applies to.
   *
   * @return the status code (e.g. {@code 404})
   */
  int value();

  /**
   * The error type to decode for this status.
   *
   * @return the error type, which must be assignable to the method's declared error type
   */
  Class<?> error();
}
