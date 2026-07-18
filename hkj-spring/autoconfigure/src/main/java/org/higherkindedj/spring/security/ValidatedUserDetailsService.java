// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.higherkindedj.hkt.validated.Validated;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * UserDetailsService using Validated for comprehensive username validation.
 *
 * <p>Demonstrates error accumulation with Validated in Spring Security:
 *
 * <ul>
 *   <li>Validate username format, accumulating ALL format errors
 *   <li>Load the user from the in-memory store
 *   <li>Return detailed error information
 * </ul>
 *
 * <p>The service starts <strong>empty</strong>: register users explicitly via {@link
 * #addUser(UserDetails)}. For demos and tests, {@link #withSampleUsers()} creates an instance
 * pre-populated with well-known sample accounts — never use that factory in production.
 *
 * <p>Account-status checks (disabled, locked, expired) are deliberately <em>not</em> performed
 * here: per the {@link UserDetailsService} contract they belong to the authentication provider,
 * which raises the specific {@code DisabledException}/{@code LockedException} variants. Use {@link
 * #validateAccountStatus(UserDetails)} if you need an accumulated functional view of those checks.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @Bean
 * public UserDetailsService userDetailsService(PasswordEncoder encoder) {
 *     var service = new ValidatedUserDetailsService();
 *     service.addUser(User.builder()
 *         .username("alice")
 *         .password(encoder.encode(secret))
 *         .roles("USER")
 *         .build());
 *     return service;
 * }
 * }</pre>
 */
public class ValidatedUserDetailsService implements UserDetailsService {

  private final Map<String, UserDetails> users = new ConcurrentHashMap<>();

  /** Creates a new, empty ValidatedUserDetailsService. Register users via {@link #addUser}. */
  public ValidatedUserDetailsService() {}

  /**
   * Creates a service pre-populated with sample users for demos and tests.
   *
   * <p><strong>Never use in production:</strong> the accounts ({@code admin}, {@code user}, {@code
   * disabled}) have well-known plaintext ({@code {noop}}) passwords.
   *
   * @return a service containing the sample accounts
   */
  public static ValidatedUserDetailsService withSampleUsers() {
    var service = new ValidatedUserDetailsService();
    service.addUser(
        User.builder().username("admin").password("{noop}admin123").roles("ADMIN", "USER").build());
    service.addUser(
        User.builder().username("user").password("{noop}user123").roles("USER").build());
    service.addUser(
        User.builder()
            .username("disabled")
            .password("{noop}disabled123")
            .roles("USER")
            .disabled(true)
            .build());
    return service;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    Validated<List<UserValidationError>, UserDetails> validated =
        validateUsername(username).flatMap(this::loadUser);

    return validated.fold(
        errors -> {
          String errorMessages =
              String.join("; ", errors.stream().map(UserValidationError::message).toList());
          throw new UsernameNotFoundException(errorMessages);
        },
        userDetails -> userDetails);
  }

  /**
   * Validates username format, accumulating all format errors.
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

    return errors.isEmpty() ? Validated.valid(username) : Validated.invalid(List.copyOf(errors));
  }

  /**
   * Loads user from the in-memory store.
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
   * Validates account status (enabled, non-locked, non-expired), accumulating all failures.
   *
   * <p>Not consulted by {@link #loadUserByUsername}: status enforcement is the authentication
   * provider's responsibility. This is a functional convenience for callers that want the
   * accumulated view.
   *
   * @param userDetails the user details
   * @return Validated containing user details or all status errors
   */
  public Validated<List<UserValidationError>, UserDetails> validateAccountStatus(
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

    return errors.isEmpty() ? Validated.valid(userDetails) : Validated.invalid(List.copyOf(errors));
  }

  /**
   * Adds a user to the service.
   *
   * @param userDetails the user details to add
   */
  public void addUser(UserDetails userDetails) {
    users.put(userDetails.getUsername(), userDetails);
  }

  /**
   * Validation error for user details.
   *
   * @param message the validation error message
   */
  public record UserValidationError(String message) {}
}
