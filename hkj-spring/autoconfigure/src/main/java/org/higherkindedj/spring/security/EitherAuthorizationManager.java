// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.security;

import java.util.function.Supplier;
import org.higherkindedj.hkt.either.Either;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Authorization manager using Either for functional authorization decisions.
 *
 * <p>Demonstrates functional approach to Spring Security authorization:
 *
 * <ul>
 *   <li>Check authorization rules with Either
 *   <li>Left: Authorization error (access denied)
 *   <li>Right: Authorization success (access granted)
 *   <li>Compose multiple authorization rules with flatMap
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @Bean
 * public SecurityFilterChain filterChain(HttpSecurity http) {
 *     var authManager = new EitherAuthorizationManager();
 *
 *     http.authorizeHttpRequests(authz -> authz
 *         .requestMatchers("/api/admin/**").access(authManager)
 *         .anyRequest().authenticated());
 *
 *     return http.build();
 * }
 * }</pre>
 *
 * <p>Benefits of Either for authorization:
 *
 * <ul>
 *   <li>Type-safe error handling
 *   <li>Composable authorization rules
 *   <li>Clear success/failure semantics
 *   <li>Better logging and auditing
 * </ul>
 */
public class EitherAuthorizationManager
    implements AuthorizationManager<RequestAuthorizationContext> {

  /**
   * Authorizes access using Either for functional decision making.
   *
   * @param authentication the authentication supplier
   * @param context the request context
   * @return authorization result (may be null when abstaining)
   */
  @Override
  public @Nullable AuthorizationResult authorize(
      Supplier<? extends @Nullable Authentication> authentication,
      RequestAuthorizationContext context) {

    Either<AuthorizationError, AuthorizationSuccess> result =
        checkAuthentication(authentication.get())
            .flatMap(auth -> checkAuthorities(auth))
            .flatMap(auth -> checkRequestPath(auth, context));

    // Fold Either to AuthorizationDecision
    return result.fold(
        error -> new AuthorizationDecision(false), success -> new AuthorizationDecision(true));
  }

  /**
   * Checks if authentication is present and valid.
   *
   * @param authentication the authentication
   * @return Either containing authentication or error
   */
  private Either<AuthorizationError, Authentication> checkAuthentication(
      Authentication authentication) {
    if (authentication == null) {
      return Either.left(new AuthorizationError("No authentication present"));
    }

    if (!authentication.isAuthenticated()) {
      return Either.left(new AuthorizationError("Authentication not authenticated"));
    }

    return Either.right(authentication);
  }

  /**
   * Checks if authentication has required authorities.
   *
   * @param authentication the authentication
   * @return Either containing authentication or error
   */
  private Either<AuthorizationError, Authentication> checkAuthorities(
      Authentication authentication) {
    boolean hasAdminRole =
        authentication.getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));

    if (!hasAdminRole) {
      return Either.left(new AuthorizationError("Missing required ADMIN role"));
    }

    return Either.right(authentication);
  }

  /**
   * Checks if request path is allowed.
   *
   * @param authentication the authentication
   * @param context the request context
   * @return Either containing success or error
   */
  private Either<AuthorizationError, AuthorizationSuccess> checkRequestPath(
      Authentication authentication, RequestAuthorizationContext context) {

    String path = context.getRequest().getRequestURI();

    // Example: block certain paths even for admins
    if (path != null && path.startsWith("/api/admin/dangerous")) {
      return Either.left(new AuthorizationError("Dangerous path access denied"));
    }

    return Either.right(new AuthorizationSuccess(authentication.getName(), path));
  }

  /** Error type for authorization failures. */
  public record AuthorizationError(String reason) {}

  /** Success type for authorization. */
  public record AuthorizationSuccess(String principal, String allowedPath) {}
}
