// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.validated.Validated;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * UserDetailsService using Validated for comprehensive user validation.
 *
 * <p>Demonstrates error accumulation with Validated in Spring Security:
 *
 * <ul>
 *   <li>Validate username format
 *   <li>Validate account status (enabled, non-locked, etc.)
 *   <li>Accumulate ALL validation errors
 *   <li>Return detailed error information
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @Bean
 * public UserDetailsService userDetailsService() {
 *     return new ValidatedUserDetailsService();
 * }
 *
 * @Bean
 * public AuthenticationManager authManager(HttpSecurity http) {
 *     return http.getSharedObject(AuthenticationManagerBuilder.class)
 *         .userDetailsService(userDetailsService())
 *         .passwordEncoder(passwordEncoder())
 *         .and()
 *         .build();
 * }
 * }</pre>
 *
 * <p>Benefits of Validated for user details:
 *
 * <ul>
 *   <li>Complete error reporting (not just first error)
 *   <li>Better UX - users see all validation issues
 *   <li>Type-safe validation
 *   <li>Composable validation rules
 * </ul>
 */
public class ValidatedUserDetailsService implements UserDetailsService {

  private final Map<String, UserDetails> users = new ConcurrentHashMap<>();

  // Semigroup for combining error lists
  private static final Semigroup<List<UserValidationError>> ERROR_SEMIGROUP =
      (a, b) -> {
        List<UserValidationError> combined = new ArrayList<>(a);
        combined.addAll(b);
        return combined;
      };

  /** Creates a new ValidatedUserDetailsService with sample users. */
  public ValidatedUserDetailsService() {
    // Add sample users for testing
    users.put(
        "admin",
        User.builder().username("admin").password("{noop}admin123").roles("ADMIN", "USER").build());

    users.put(
        "user", User.builder().username("user").password("{noop}user123").roles("USER").build());

    // Disabled user for testing
    users.put(
        "disabled",
        User.builder()
            .username("disabled")
            .password("{noop}disabled123")
            .roles("USER")
            .disabled(true)
            .build());
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    // Validate username and load user
    Validated<List<UserValidationError>, UserDetails> validated = validateAndLoadUser(username);

    // Fold Validated to UserDetails or throw exception
    return validated.fold(
        errors -> {
          // Collect all error messages
          String errorMessages =
              errors.stream()
                  .map(UserValidationError::message)
                  .reduce((a, b) -> a + "; " + b)
                  .orElse("Unknown error");

          throw new UsernameNotFoundException(errorMessages);
        },
        userDetails -> userDetails);
  }

  /**
   * Validates username and loads user details.
   *
   * @param username the username to validate and load
   * @return Validated containing user details or validation errors
   */
  private Validated<List<UserValidationError>, UserDetails> validateAndLoadUser(String username) {
    // Start with username validation
    return validateUsername(username).flatMap(this::loadUser).flatMap(this::validateUserAccount);
  }

  /**
   * Validates username format.
   *
   * @param username the username
   * @return Validated containing username or errors
   */
  private Validated<List<UserValidationError>, String> validateUsername(String username) {
    List<UserValidationError> errors = new ArrayList<>();

    if (username == null || username.isBlank()) {
      errors.add(new UserValidationError("Username cannot be empty"));
    }

    if (username != null && username.length() < 3) {
      errors.add(new UserValidationError("Username must be at least 3 characters"));
    }

    if (username != null && username.length() > 50) {
      errors.add(new UserValidationError("Username must be at most 50 characters"));
    }

    if (username != null && !username.matches("^[a-zA-Z0-9_-]+$")) {
      errors.add(
          new UserValidationError(
              "Username can only contain letters, numbers, underscores, and hyphens"));
    }

    return errors.isEmpty() ? Validated.valid(username) : Validated.invalid(errors);
  }

  /**
   * Loads user from repository.
   *
   * @param username the username
   * @return Validated containing user details or error
   */
  private Validated<List<UserValidationError>, UserDetails> loadUser(String username) {
    UserDetails user = users.get(username);

    if (user == null) {
      return Validated.invalid(List.of(new UserValidationError("User not found: " + username)));
    }

    return Validated.valid(user);
  }

  /**
   * Validates user account status.
   *
   * @param userDetails the user details
   * @return Validated containing user details or errors
   */
  private Validated<List<UserValidationError>, UserDetails> validateUserAccount(
      UserDetails userDetails) {
    List<UserValidationError> errors = new ArrayList<>();

    if (!userDetails.isEnabled()) {
      errors.add(new UserValidationError("Account is disabled"));
    }

    if (!userDetails.isAccountNonLocked()) {
      errors.add(new UserValidationError("Account is locked"));
    }

    if (!userDetails.isAccountNonExpired()) {
      errors.add(new UserValidationError("Account has expired"));
    }

    if (!userDetails.isCredentialsNonExpired()) {
      errors.add(new UserValidationError("Credentials have expired"));
    }

    return errors.isEmpty() ? Validated.valid(userDetails) : Validated.invalid(errors);
  }

  /**
   * Adds a user to the service.
   *
   * @param userDetails the user details to add
   */
  public void addUser(UserDetails userDetails) {
    users.put(userDetails.getUsername(), userDetails);
  }

  /** Validation error for user details. */
  public record UserValidationError(String message) {}
}
