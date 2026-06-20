// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an HTTP client interface for which Higher-Kinded-J generates an Effect-Path client.
 *
 * <p>Applied to an interface whose methods return Effect Paths ({@code EitherPath<E, T>}, {@code
 * VTaskPath<Either<E, T>>}, {@code MaybePath<T>}) alongside the standard Spring exchange
 * annotations ({@code @HttpExchange}, {@code @GetExchange}, …). The annotation processor generates
 * a client that folds each HTTP outcome into the declared Path, decoding a typed error {@code E}
 * from non-2xx responses via an injected {@link ResponseErrorDecoder}.
 *
 * <pre>{@code
 * @HttpExchange("/users")
 * @HkjHttpClient
 * interface UserApi {
 *   @GetExchange("/{id}")
 *   EitherPath<UserError, UserDto> getUser(@PathVariable String id);
 * }
 * }</pre>
 *
 * <p>Bean wiring (base URL, timeouts, API versioning) is configuration-driven via Spring's
 * {@code @ImportHttpServices} HTTP Service Groups and {@code spring.http.serviceclient.<group>.*}
 * properties; the generated client autowires the proxied native interface rather than building a
 * {@code RestClientAdapter} by hand.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface HkjHttpClient {

  /**
   * The HTTP Service Group name used for bean wiring and {@code
   * spring.http.serviceclient.<group>.*} configuration. When blank, the group is derived from the
   * annotated interface's simple name.
   *
   * @return the group name, or empty to derive it from the interface name
   */
  String group() default "";
}
