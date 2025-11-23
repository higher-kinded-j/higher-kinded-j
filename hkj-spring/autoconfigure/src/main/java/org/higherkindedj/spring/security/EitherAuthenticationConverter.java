// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.security;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.higherkindedj.hkt.either.Either;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
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
 *   <li>Convert errors to AuthenticationToken or propagate
 * </ul>
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

  /**
   * Creates a new EitherAuthenticationConverter with default settings.
   *
   * <p>Defaults:
   *
   * <ul>
   *   <li>authoritiesClaimName: "roles"
   *   <li>authorityPrefix: "ROLE_"
   * </ul>
   */
  public EitherAuthenticationConverter() {
    this("roles", "ROLE_");
  }

  /**
   * Creates a new EitherAuthenticationConverter with custom settings.
   *
   * @param authoritiesClaimName the JWT claim name containing authorities
   * @param authorityPrefix the prefix to add to each authority
   */
  public EitherAuthenticationConverter(String authoritiesClaimName, String authorityPrefix) {
    this.authoritiesClaimName = authoritiesClaimName;
    this.authorityPrefix = authorityPrefix;
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    // Use Either for functional error handling
    Either<AuthConversionError, AbstractAuthenticationToken> result =
        extractAuthorities(jwt).map(authorities -> createAuthenticationToken(jwt, authorities));

    // Fold Either to handle both success and error cases
    return result.fold(
        error -> {
          // On error, create unauthenticated token
          // In production, you might throw an exception or return null
          return new UsernamePasswordAuthenticationToken(
              jwt.getSubject(), null, Collections.emptyList());
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
            new AuthConversionError("Missing authorities claim: " + authoritiesClaimName));
      }

      if (claim instanceof Collection<?> claimCollection) {
        List<GrantedAuthority> authorities =
            claimCollection.stream()
                .filter(item -> item instanceof String)
                .map(item -> (String) item)
                .map(role -> new SimpleGrantedAuthority(authorityPrefix + role))
                .collect(Collectors.toList());

        return Either.right(authorities);
      }

      return Either.left(
          new AuthConversionError("Invalid authorities claim type: " + claim.getClass().getName()));

    } catch (Exception e) {
      return Either.left(
          new AuthConversionError("Error extracting authorities: " + e.getMessage()));
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

  /** Error type for authentication conversion failures. */
  public record AuthConversionError(String message) {}
}
