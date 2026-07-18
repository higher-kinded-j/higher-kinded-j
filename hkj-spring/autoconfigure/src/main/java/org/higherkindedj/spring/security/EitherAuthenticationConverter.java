// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.security;

import java.util.Collection;
import java.util.List;
import org.higherkindedj.hkt.either.Either;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Converts JWT tokens to Authentication using Either for error handling.
 *
 * <p>This converter demonstrates functional error handling in Spring Security:
 *
 * <ul>
 *   <li>Parse JWT claims with Either (success/failure)
 *   <li>Extract authorities with validation
 *   <li>Reject the token with a {@link BadCredentialsException} when extraction fails
 * </ul>
 *
 * <p>A malformed authorities claim (wrong type, or a collection containing a non-string element)
 * never produces an authenticated token: the {@code Left} branch is folded into a thrown {@link
 * BadCredentialsException}, which Spring Security surfaces as an authentication failure (HTTP 401).
 * A <em>missing</em> claim is rejected the same way only in the strict/default mode ({@code
 * rejectMissingClaim = true}); in lenient mode a token that simply lacks the claim authenticates
 * with no authorities.
 *
 * <p>Example usage in SecurityConfig:
 *
 * <pre>{@code
 * @Bean
 * public SecurityFilterChain filterChain(HttpSecurity http) {
 *     http.oauth2ResourceServer(oauth2 -> oauth2
 *         .jwt(jwt -> jwt
 *             .jwtAuthenticationConverter(new EitherAuthenticationConverter())));
 *     return http.build();
 * }
 * }</pre>
 */
public class EitherAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

  private final String authoritiesClaimName;
  private final String authorityPrefix;
  private final boolean rejectMissingClaim;

  /**
   * Creates a new EitherAuthenticationConverter with default settings.
   *
   * <p>Defaults:
   *
   * <ul>
   *   <li>authoritiesClaimName: "roles"
   *   <li>authorityPrefix: "ROLE_"
   *   <li>rejectMissingClaim: true (a token without the claim is rejected)
   * </ul>
   */
  public EitherAuthenticationConverter() {
    this("roles", "ROLE_", true);
  }

  /**
   * Creates a new EitherAuthenticationConverter that rejects a missing authorities claim.
   *
   * @param authoritiesClaimName the JWT claim name containing authorities
   * @param authorityPrefix the prefix to add to each authority
   */
  public EitherAuthenticationConverter(String authoritiesClaimName, String authorityPrefix) {
    this(authoritiesClaimName, authorityPrefix, true);
  }

  /**
   * Creates a new EitherAuthenticationConverter with custom settings.
   *
   * @param authoritiesClaimName the JWT claim name containing authorities
   * @param authorityPrefix the prefix to add to each authority
   * @param rejectMissingClaim when {@code true} (the safe default) a token that lacks the
   *     authorities claim is rejected with {@link BadCredentialsException}; when {@code false} such
   *     a token authenticates with no authorities (lenient — for IdPs that legitimately issue
   *     role-less tokens, e.g. client-credentials). A malformed (wrong-type) claim is always
   *     rejected regardless of this flag.
   */
  public EitherAuthenticationConverter(
      String authoritiesClaimName, String authorityPrefix, boolean rejectMissingClaim) {
    this.authoritiesClaimName = authoritiesClaimName;
    this.authorityPrefix = authorityPrefix;
    this.rejectMissingClaim = rejectMissingClaim;
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    Either<AuthConversionError, AbstractAuthenticationToken> result =
        extractAuthorities(jwt).map(authorities -> createAuthenticationToken(jwt, authorities));

    return result.fold(
        error -> {
          if (error.missingClaim() && !rejectMissingClaim) {
            // Lenient: a legitimately role-less token authenticates with no authorities.
            return createAuthenticationToken(jwt, List.of());
          }
          throw new BadCredentialsException("JWT authority conversion failed: " + error.message());
        },
        token -> token);
  }

  /**
   * Extracts authorities from JWT using Either for error handling.
   *
   * @param jwt the JWT token
   * @return Either containing authorities or error
   */
  private Either<AuthConversionError, Collection<GrantedAuthority>> extractAuthorities(Jwt jwt) {
    try {
      Object claim = jwt.getClaim(authoritiesClaimName);

      if (claim == null) {
        return Either.left(
            new AuthConversionError("Missing authorities claim: " + authoritiesClaimName, true));
      }

      if (claim instanceof Collection<?> claimCollection) {
        // A collection containing any non-string element is a malformed claim, not a role-less
        // token: reject the whole claim rather than silently dropping the bad elements (which would
        // let e.g. ["ADMIN", 123] authenticate with ROLE_ADMIN). Malformed claims are rejected
        // regardless of the lenient rejectMissingClaim flag.
        if (claimCollection.stream().anyMatch(item -> !(item instanceof String))) {
          return Either.left(
              new AuthConversionError(
                  "Invalid authorities claim element type: " + authoritiesClaimName, false));
        }

        List<GrantedAuthority> authorities =
            claimCollection.stream()
                .map(String.class::cast)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(authorityPrefix + role))
                .toList();

        return Either.right(authorities);
      }

      return Either.left(
          new AuthConversionError(
              "Invalid authorities claim type: " + claim.getClass().getName(), false));

    } catch (Exception e) {
      return Either.left(
          new AuthConversionError("Error extracting authorities: " + e.getMessage(), false));
    }
  }

  /**
   * Creates authentication token with principal and authorities.
   *
   * @param jwt the JWT token
   * @param authorities the granted authorities
   * @return authentication token
   */
  private AbstractAuthenticationToken createAuthenticationToken(
      Jwt jwt, Collection<GrantedAuthority> authorities) {

    return new UsernamePasswordAuthenticationToken(jwt.getSubject(), jwt, authorities);
  }

  /**
   * Error type for authentication conversion failures.
   *
   * @param message the error message describing the conversion failure
   * @param missingClaim {@code true} when the failure is a simply-absent authorities claim (a token
   *     that may legitimately carry no roles), {@code false} for a malformed claim
   */
  public record AuthConversionError(String message, boolean missingClaim) {}
}
