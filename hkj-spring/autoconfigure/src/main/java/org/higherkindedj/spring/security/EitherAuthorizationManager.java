// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.security;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.higherkindedj.hkt.either.Either;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Authorization manager using Either for functional authorization decisions.
 *
 * <p>Authorization policy is supplied as an ordered list of {@link AuthorizationRule}s, each
 * returning {@code Either<AuthorizationError, Authentication>}. Rules are chained with {@code
 * flatMap}: the first {@code Left} denies access; if every rule passes, access is granted. An
 * authentication-present check always runs before the supplied rules.
 *
 * <p>The no-arg constructor keeps the historical default policy — {@link #requireAuthority
 * requireAuthority("ROLE_ADMIN")} plus {@link #denyPathPrefix
 * denyPathPrefix("/api/admin/dangerous")} — as two explicit, named rules. Construct with your own
 * rules for real applications.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @Bean
 * public SecurityFilterChain filterChain(HttpSecurity http) {
 *     var authManager = new EitherAuthorizationManager(List.of(
 *         EitherAuthorizationManager.requireAuthority("ROLE_ADMIN"),
 *         EitherAuthorizationManager.denyPathPrefix("/api/admin/dangerous")));
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
   * A single authorization rule: passes the authentication through on success, or denies with an
   * {@link AuthorizationError}.
   */
  @FunctionalInterface
  public interface AuthorizationRule {

    /**
     * Checks this rule against the authenticated request.
     *
     * @param authentication the (already authenticated) authentication
     * @param context the request context
     * @return {@code Right(authentication)} to allow the chain to continue, or {@code Left(error)}
     *     to deny
     */
    Either<AuthorizationError, Authentication> check(
        Authentication authentication, RequestAuthorizationContext context);
  }

  private final List<AuthorizationRule> rules;

  /**
   * Creates a manager with the historical default policy: require {@code ROLE_ADMIN} and deny paths
   * under {@code /api/admin/dangerous}.
   *
   * <p>Prefer {@link #EitherAuthorizationManager(List)} with your own rules in real applications.
   */
  public EitherAuthorizationManager() {
    this(List.of(requireAuthority("ROLE_ADMIN"), denyPathPrefix("/api/admin/dangerous")));
  }

  /**
   * Creates a manager with the given ordered rules. All rules must pass for access to be granted.
   *
   * @param rules the authorization rules, evaluated in order
   */
  public EitherAuthorizationManager(List<AuthorizationRule> rules) {
    this.rules = List.copyOf(rules);
  }

  /**
   * Rule requiring the given granted authority.
   *
   * @param authority the required authority (e.g. {@code "ROLE_ADMIN"})
   * @return the rule
   */
  public static AuthorizationRule requireAuthority(String authority) {
    Objects.requireNonNull(authority, "authority");
    return (authentication, context) ->
        // Compare from the (non-null) required authority side: GrantedAuthority.getAuthority()
        // may return null, which would NPE if it were the receiver.
        authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()))
            ? Either.right(authentication)
            : Either.left(new AuthorizationError("Missing required authority: " + authority));
  }

  /**
   * Rule denying the given path and any request under it.
   *
   * <p>Matching delegates to Spring Security's {@link PathPatternRequestMatcher}, which works on
   * the decoded, normalised request path — a raw {@code getRequestURI().startsWith(...)} check
   * would be bypassable with percent-encoding (e.g. {@code /api/%64angerous}).
   *
   * <p>The prefix is normalised: it must be non-blank, start with {@code /}, and contain no {@link
   * PathPatternRequestMatcher} metacharacter ({@code * ? { }} — those would parse as pattern
   * syntax, not a literal prefix, so e.g. {@code "/api/{segment}"} could deny more than intended);
   * every trailing {@code /} is stripped. Normalising trailing slashes is a security requirement,
   * not cosmetics — a raw {@code "/api/admin/"} or {@code "/api/admin//"} would otherwise build the
   * descendants matcher as {@code "/api/admin//**"}, whose empty segment matches no real descendant
   * and silently fails open. The root prefix {@code "/"} is special-cased to a {@code "/**"}
   * descendants pattern for the same reason.
   *
   * @param pathPrefix the denied path prefix (a literal segment prefix, e.g. {@code "/api/admin"})
   * @return the rule
   * @throws IllegalArgumentException if the prefix is blank, lacks a leading {@code /}, or contains
   *     a PathPattern metacharacter ({@code * ? { }})
   */
  public static AuthorizationRule denyPathPrefix(String pathPrefix) {
    if (pathPrefix == null || pathPrefix.isBlank()) {
      throw new IllegalArgumentException("pathPrefix must not be blank");
    }
    if (!pathPrefix.startsWith("/")) {
      throw new IllegalArgumentException("pathPrefix must start with '/': " + pathPrefix);
    }
    if (pathPrefix.chars().anyMatch(c -> c == '*' || c == '?' || c == '{' || c == '}')) {
      throw new IllegalArgumentException(
          "pathPrefix must be a literal prefix without PathPattern metacharacters (* ? { }): "
              + pathPrefix);
    }
    // Strip *every* trailing slash, not just one: "/api/admin//" would otherwise normalise to
    // "/api/admin/" and rebuild the fail-open "/api/admin//**" descendants matcher.
    String stripped = pathPrefix;
    while (stripped.length() > 1 && stripped.endsWith("/")) {
      stripped = stripped.substring(0, stripped.length() - 1);
    }
    // Effectively final for the returned rule lambda below.
    String normalised = stripped;
    // For the root "/", the descendants pattern is "/**" (not "//**", which matches nothing).
    String descendantsPattern = normalised.equals("/") ? "/**" : normalised + "/**";
    RequestMatcher exact = PathPatternRequestMatcher.withDefaults().matcher(normalised);
    RequestMatcher descendants =
        PathPatternRequestMatcher.withDefaults().matcher(descendantsPattern);
    return (authentication, context) ->
        exact.matches(context.getRequest()) || descendants.matches(context.getRequest())
            ? Either.left(new AuthorizationError("Access denied to path: " + normalised))
            : Either.right(authentication);
  }

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

    Either<AuthorizationError, Authentication> result = checkAuthentication(authentication.get());
    for (AuthorizationRule rule : rules) {
      result = result.flatMap(auth -> rule.check(auth, context));
    }

    return result.fold(_ -> new AuthorizationDecision(false), _ -> new AuthorizationDecision(true));
  }

  /**
   * Checks if authentication is present and valid.
   *
   * @param authentication the authentication
   * @return Either containing authentication or error
   */
  private Either<AuthorizationError, Authentication> checkAuthentication(
      @Nullable Authentication authentication) {
    if (authentication == null) {
      return Either.left(new AuthorizationError("No authentication present"));
    }

    if (!authentication.isAuthenticated()) {
      return Either.left(new AuthorizationError("Authentication not authenticated"));
    }

    return Either.right(authentication);
  }

  /**
   * Error type for authorization failures.
   *
   * @param reason the reason authorization was denied
   */
  public record AuthorizationError(String reason) {}
}
