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
 *   <li>{@link ValidatedUserDetailsService} - User authentication with error accumulation (starts
 *       empty; register accounts explicitly)
 *   <li>{@link EitherAuthenticationConverter} - JWT conversion with Either error handling (rejects
 *       the token when authority extraction fails)
 *   <li>{@link EitherAuthorizationManager} - Authorization decisions using composable Either-based
 *       rules
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

  /**
   * Creates a new security auto-configuration with the given properties.
   *
   * @param properties the HKJ configuration properties
   */
  public HkjSecurityAutoConfiguration(HkjProperties properties) {
    this.properties = properties;
  }

  /**
   * Provides a ValidatedUserDetailsService bean when explicitly enabled.
   *
   * <p>This service uses Validated for username validation with error accumulation. The bean starts
   * <strong>empty</strong> — register accounts via {@link ValidatedUserDetailsService#addUser}.
   *
   * <p><strong>Opt-in, and application-wide once enabled:</strong> {@code
   * ValidatedUserDetailsService} implements {@link UserDetailsService}, so when it is the only bean
   * of that type Spring Security adopts it as the global user store (and Boot's generated default
   * user backs off). Enabling this without registering accounts means every form/basic login fails.
   * It is therefore disabled by default; enable it only when you intend to populate and use it:
   *
   * <pre>
   * hkj:
   *   security:
   *     validated-user-details: true  # default: false (opt-in)
   * </pre>
   *
   * @return validated user details service
   */
  @Bean
  @ConditionalOnMissingBean(ValidatedUserDetailsService.class)
  @ConditionalOnProperty(prefix = "hkj.security", name = "validated-user-details")
  public ValidatedUserDetailsService validatedUserDetailsService() {
    return new ValidatedUserDetailsService();
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
    HkjProperties.Security security = properties.getSecurity();
    return new EitherAuthenticationConverter(
        security.getJwtAuthoritiesClaim(),
        security.getJwtAuthorityPrefix(),
        security.isRejectMissingAuthoritiesClaim());
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
