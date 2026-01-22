// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.context;

import java.security.Principal;
import java.util.Objects;
import java.util.Set;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.maybe.Maybe;

/**
 * Pre-defined {@link ScopedValue} instances for security context values and helper methods for
 * authentication and authorisation checks.
 *
 * <p>{@code SecurityContext} provides a standard set of scoped values for user identity, roles,
 * permissions, and authentication tokens. These values propagate automatically to child virtual
 * threads forked within the same scope, ensuring consistent security context across concurrent
 * operations.
 *
 * <p><b>Usage Example:</b>
 *
 * <pre>{@code
 * // At the authentication filter
 * public void doFilter(Request request, Response response, FilterChain chain) {
 *     AuthResult auth = authenticate(request);
 *
 *     ScopedValue
 *         .where(SecurityContext.PRINCIPAL, auth.principal())
 *         .where(SecurityContext.ROLES, auth.roles())
 *         .where(SecurityContext.AUTH_TOKEN, auth.token())
 *         .run(() -> chain.doFilter(request, response));
 * }
 *
 * // In a protected service method
 * public Order createOrder(OrderRequest request) {
 *     SecurityContext.requireRole("CUSTOMER").run();  // Guard clause
 *     return orderRepository.save(buildOrder(request));
 * }
 * }</pre>
 *
 * <p><b>Security Note:</b> Never rely solely on context propagation for security. Always validate
 * authentication at system boundaries, check authorisation before sensitive operations, and log
 * security-relevant events with full context.
 *
 * @see Context
 * @see RequestContext
 * @see ScopedValue
 */
public final class SecurityContext {

  private SecurityContext() {
    // Utility class - no instantiation
  }

  /**
   * The authenticated principal (user identity).
   *
   * <p>A {@code null} value indicates an anonymous/unauthenticated user. An unbound {@link
   * ScopedValue} indicates a configuration error (security context not established).
   */
  public static final ScopedValue<Principal> PRINCIPAL = ScopedValue.newInstance();

  /**
   * Set of roles granted to the current user.
   *
   * <p>Should be an empty set for anonymous users. If bound to {@code null}, role checks will
   * return {@code false} as a defensive measure.
   *
   * <p>Roles are typically coarse-grained (e.g., "ADMIN", "USER", "MANAGER").
   */
  public static final ScopedValue<Set<String>> ROLES = ScopedValue.newInstance();

  /**
   * Set of fine-grained permissions granted to the current user.
   *
   * <p>For systems that need more granularity than roles. Permissions are typically in the form
   * "resource:action" (e.g., "document:read", "user:delete").
   *
   * <p>If bound to {@code null}, permission checks will return {@code false} as a defensive
   * measure.
   */
  public static final ScopedValue<Set<String>> PERMISSIONS = ScopedValue.newInstance();

  /**
   * Authentication token for propagation to downstream services.
   *
   * <p>Typically a JWT, OAuth token, or similar bearer token. Used when making authenticated
   * requests to downstream services.
   */
  public static final ScopedValue<String> AUTH_TOKEN = ScopedValue.newInstance();

  /**
   * Session identifier for audit and tracking.
   *
   * <p>Links requests to a user session for audit logging and session management.
   */
  public static final ScopedValue<String> SESSION_ID = ScopedValue.newInstance();

  // ===== AUTHENTICATION CHECKS =====

  /**
   * Returns a {@link Context} that checks if the current context has an authenticated principal.
   *
   * <p>Returns {@code false} for anonymous users (null principal).
   *
   * @return A Context that evaluates to {@code true} if authenticated.
   */
  public static Context<Principal, Boolean> isAuthenticated() {
    return Context.asks(PRINCIPAL, principal -> principal != null);
  }

  /**
   * Returns a {@link Context} that requires authentication, failing if the user is anonymous.
   *
   * @return A Context containing the Principal, or failing with {@link UnauthenticatedException}.
   */
  public static Context<Principal, Principal> requireAuthenticated() {
    return Context.<Principal>ask(PRINCIPAL)
        .flatMap(
            principal ->
                principal != null
                    ? Context.succeed(principal)
                    : Context.fail(new UnauthenticatedException("Authentication required")));
  }

  /**
   * Returns a {@link Context} that gets the principal if authenticated, or empty {@link Maybe} if
   * anonymous.
   *
   * <p>Useful for features that work differently for authenticated vs anonymous users.
   *
   * @return A Context containing Maybe of the Principal.
   */
  public static Context<Principal, Maybe<Principal>> principalIfPresent() {
    return Context.asks(
        PRINCIPAL, principal -> principal != null ? Maybe.just(principal) : Maybe.nothing());
  }

  // ===== ROLE CHECKS =====

  /**
   * Returns a {@link Context} that checks if the current user has a specific role.
   *
   * <p>Returns {@code false} if the user is anonymous or doesn't have the role.
   *
   * @param role The role to check. Must not be null.
   * @return A Context that evaluates to {@code true} if the user has the role.
   * @throws NullPointerException if {@code role} is null.
   */
  public static Context<Set<String>, Boolean> hasRole(String role) {
    Objects.requireNonNull(role, "role cannot be null");
    return Context.asks(ROLES, roles -> roles != null && roles.contains(role));
  }

  /**
   * Returns a {@link Context} that checks if the current user has any of the specified roles.
   *
   * @param roles The roles to check (at least one must match). Must not be null.
   * @return A Context that evaluates to {@code true} if the user has any of the roles.
   * @throws NullPointerException if {@code roles} is null.
   */
  public static Context<Set<String>, Boolean> hasAnyRole(String... roles) {
    Objects.requireNonNull(roles, "roles cannot be null");
    Set<String> required = Set.of(roles);
    return Context.asks(
        ROLES, userRoles -> userRoles != null && userRoles.stream().anyMatch(required::contains));
  }

  /**
   * Returns a {@link Context} that checks if the current user has all of the specified roles.
   *
   * @param roles The roles to check (all must match). Must not be null.
   * @return A Context that evaluates to {@code true} if the user has all the roles.
   * @throws NullPointerException if {@code roles} is null.
   */
  public static Context<Set<String>, Boolean> hasAllRoles(String... roles) {
    Objects.requireNonNull(roles, "roles cannot be null");
    Set<String> required = Set.of(roles);
    return Context.asks(ROLES, userRoles -> userRoles != null && userRoles.containsAll(required));
  }

  /**
   * Returns a {@link Context} that requires a specific role, failing if the user doesn't have it.
   *
   * @param role The required role. Must not be null.
   * @return A Context containing {@link Unit} on success, or failing with {@link
   *     UnauthorisedException}.
   * @throws NullPointerException if {@code role} is null.
   */
  public static Context<Set<String>, Unit> requireRole(String role) {
    Objects.requireNonNull(role, "role cannot be null");
    return hasRole(role)
        .flatMap(
            has ->
                has
                    ? Context.succeed(Unit.INSTANCE)
                    : Context.fail(new UnauthorisedException("Role required: " + role)));
  }

  /**
   * Returns a {@link Context} that requires any of the specified roles.
   *
   * @param roles The roles (at least one required). Must not be null.
   * @return A Context containing {@link Unit} on success, or failing with {@link
   *     UnauthorisedException}.
   * @throws NullPointerException if {@code roles} is null.
   */
  public static Context<Set<String>, Unit> requireAnyRole(String... roles) {
    Objects.requireNonNull(roles, "roles cannot be null");
    return hasAnyRole(roles)
        .flatMap(
            has ->
                has
                    ? Context.succeed(Unit.INSTANCE)
                    : Context.fail(
                        new UnauthorisedException(
                            "One of these roles required: " + String.join(", ", roles))));
  }

  /**
   * Returns a {@link Context} that requires all of the specified roles.
   *
   * @param roles The roles (all required). Must not be null.
   * @return A Context containing {@link Unit} on success, or failing with {@link
   *     UnauthorisedException}.
   * @throws NullPointerException if {@code roles} is null.
   */
  public static Context<Set<String>, Unit> requireAllRoles(String... roles) {
    Objects.requireNonNull(roles, "roles cannot be null");
    return hasAllRoles(roles)
        .flatMap(
            has ->
                has
                    ? Context.succeed(Unit.INSTANCE)
                    : Context.fail(
                        new UnauthorisedException(
                            "All of these roles required: " + String.join(", ", roles))));
  }

  // ===== PERMISSION CHECKS =====

  /**
   * Returns a {@link Context} that checks if the current user has a specific permission.
   *
   * @param permission The permission to check. Must not be null.
   * @return A Context that evaluates to {@code true} if the user has the permission.
   * @throws NullPointerException if {@code permission} is null.
   */
  public static Context<Set<String>, Boolean> hasPermission(String permission) {
    Objects.requireNonNull(permission, "permission cannot be null");
    return Context.asks(PERMISSIONS, perms -> perms != null && perms.contains(permission));
  }

  /**
   * Returns a {@link Context} that requires a specific permission.
   *
   * @param permission The required permission. Must not be null.
   * @return A Context containing {@link Unit} on success, or failing with {@link
   *     UnauthorisedException}.
   * @throws NullPointerException if {@code permission} is null.
   */
  public static Context<Set<String>, Unit> requirePermission(String permission) {
    Objects.requireNonNull(permission, "permission cannot be null");
    return hasPermission(permission)
        .flatMap(
            has ->
                has
                    ? Context.succeed(Unit.INSTANCE)
                    : Context.fail(
                        new UnauthorisedException("Permission required: " + permission)));
  }

  // ===== EXCEPTION TYPES =====

  /**
   * Exception thrown when authentication is required but not present. Maps to HTTP 401
   * Unauthorised.
   */
  public static class UnauthenticatedException extends RuntimeException {
    /** Serial version UID. */
    private static final long serialVersionUID = 1L;

    /**
     * Creates a new UnauthenticatedException.
     *
     * @param message The exception message.
     */
    public UnauthenticatedException(String message) {
      super(message);
    }
  }

  /**
   * Exception thrown when the user lacks required roles or permissions. Maps to HTTP 403 Forbidden.
   */
  public static class UnauthorisedException extends RuntimeException {
    /** Serial version UID. */
    private static final long serialVersionUID = 1L;

    /**
     * Creates a new UnauthorisedException.
     *
     * @param message The exception message.
     */
    public UnauthorisedException(String message) {
      super(message);
    }
  }
}
