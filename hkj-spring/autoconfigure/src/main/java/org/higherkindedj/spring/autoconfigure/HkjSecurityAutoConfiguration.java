// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.autoconfigure;

import org.higherkindedj.spring.security.EitherAuthenticationConverter;
import org.higherkindedj.spring.security.EitherAuthorizationManager;
import org.higherkindedj.spring.security.ValidatedUserDetailsService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Auto-configuration for higher-kinded-j Spring Security integration.
 *
 * <p>Provides functional error handling in Spring Security contexts using Either and Validated:
 *
 * <ul>
 *   <li>{@link ValidatedUserDetailsService} - User authentication with error accumulation
 *   <li>{@link EitherAuthenticationConverter} - JWT conversion with Either error handling
 *   <li>{@link EitherAuthorizationManager} - Authorization decisions using Either
 * </ul>
 *
 * <p>Enable via configuration property:
 *
 * <pre>
 * hkj:
 *   security:
 *     enabled: true
 *     validated-user-details: true
 *     either-authentication: true
 * </pre>
 *
 * <p>Example Security Configuration:
 *
 * <pre>{@code
 * @Configuration
 * @EnableWebSecurity
 * public class SecurityConfig {
 *
 *     @Bean
 *     public SecurityFilterChain filterChain(
 *             HttpSecurity http,
 *             EitherAuthorizationManager authManager) throws Exception {
 *
 *         http
 *             .authorizeHttpRequests(authz -> authz
 *                 .requestMatchers("/api/public/**").permitAll()
 *                 .requestMatchers("/api/admin/**").access(authManager)
 *                 .anyRequest().authenticated())
 *             .oauth2ResourceServer(oauth2 -> oauth2
 *                 .jwt(jwt -> jwt
 *                     .jwtAuthenticationConverter(new EitherAuthenticationConverter())));
 *
 *         return http.build();
 *     }
 * }
 * }</pre>
 *
 * <p>Benefits of functional security:
 *
 * <ul>
 *   <li>Type-safe error handling without exceptions
 *   <li>Composable security rules
 *   <li>Complete error reporting (Validated accumulates all errors)
 *   <li>Better testability and maintainability
 * </ul>
 *
 * @see ValidatedUserDetailsService
 * @see EitherAuthenticationConverter
 * @see EitherAuthorizationManager
 */
@AutoConfiguration
@ConditionalOnClass(HttpSecurity.class)
@ConditionalOnProperty(
    prefix = "hkj.security",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
@EnableConfigurationProperties(HkjProperties.class)
public class HkjSecurityAutoConfiguration {

  private final HkjProperties properties;

  public HkjSecurityAutoConfiguration(HkjProperties properties) {
    this.properties = properties;
  }

  /**
   * Provides a ValidatedUserDetailsService bean if none exists.
   *
   * <p>This service uses Validated for comprehensive user validation with error accumulation.
   *
   * <p>Configure via:
   *
   * <pre>
   * hkj:
   *   security:
   *     validated-user-details: true  # default: true
   * </pre>
   *
   * @return validated user details service
   */
  @Bean
  @ConditionalOnMissingBean(ValidatedUserDetailsService.class)
  @ConditionalOnProperty(
      prefix = "hkj.security",
      name = "validated-user-details",
      havingValue = "true",
      matchIfMissing = true)
  public ValidatedUserDetailsService validatedUserDetailsService() {
    return new ValidatedUserDetailsService();
  }

  /**
   * Provides a UserDetailsService bean that delegates to ValidatedUserDetailsService.
   *
   * <p>This allows Spring Security to use the Validated-based user details service without
   * requiring changes to existing security configurations.
   *
   * @param validatedService the validated user details service
   * @return user details service
   */
  @Bean
  @ConditionalOnMissingBean(UserDetailsService.class)
  @ConditionalOnProperty(
      prefix = "hkj.security",
      name = "validated-user-details",
      havingValue = "true",
      matchIfMissing = true)
  public UserDetailsService userDetailsService(ValidatedUserDetailsService validatedService) {
    return validatedService;
  }

  /**
   * Provides an EitherAuthenticationConverter for JWT processing.
   *
   * <p>This converter uses Either for functional error handling during JWT to Authentication
   * conversion.
   *
   * <p>Configure via:
   *
   * <pre>
   * hkj:
   *   security:
   *     either-authentication: true  # default: true
   *     jwt-authorities-claim: "roles"  # default
   *     jwt-authority-prefix: "ROLE_"   # default
   * </pre>
   *
   * @return either authentication converter
   */
  @Bean
  @ConditionalOnMissingBean(EitherAuthenticationConverter.class)
  @ConditionalOnClass(Jwt.class)
  @ConditionalOnProperty(
      prefix = "hkj.security",
      name = "either-authentication",
      havingValue = "true",
      matchIfMissing = true)
  public EitherAuthenticationConverter eitherAuthenticationConverter() {
    String authoritiesClaim =
        properties.getSecurity() != null
            ? properties.getSecurity().getJwtAuthoritiesClaim()
            : "roles";
    String authorityPrefix =
        properties.getSecurity() != null
            ? properties.getSecurity().getJwtAuthorityPrefix()
            : "ROLE_";

    return new EitherAuthenticationConverter(authoritiesClaim, authorityPrefix);
  }

  /**
   * Provides an EitherAuthorizationManager for functional authorization decisions.
   *
   * <p>This manager uses Either to represent authorization success/failure in a functional way,
   * enabling composition and better error tracking.
   *
   * <p>Configure via:
   *
   * <pre>
   * hkj:
   *   security:
   *     either-authorization: true  # default: true
   * </pre>
   *
   * @return either authorization manager
   */
  @Bean
  @ConditionalOnMissingBean(EitherAuthorizationManager.class)
  @ConditionalOnProperty(
      prefix = "hkj.security",
      name = "either-authorization",
      havingValue = "true",
      matchIfMissing = true)
  public EitherAuthorizationManager eitherAuthorizationManager() {
    return new EitherAuthorizationManager();
  }
}
