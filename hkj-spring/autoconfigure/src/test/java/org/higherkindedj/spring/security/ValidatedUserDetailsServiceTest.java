// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.security;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@DisplayName("ValidatedUserDetailsService Tests")
class ValidatedUserDetailsServiceTest {

  private ValidatedUserDetailsService service;

  @BeforeEach
  void setUp() {
    service = ValidatedUserDetailsService.withSampleUsers();
  }

  @Nested
  @DisplayName("loadUserByUsername Tests")
  class LoadUserByUsernameTests {

    @Test
    @DisplayName("Should load existing user successfully")
    void shouldLoadExistingUser() {
      UserDetails userDetails = service.loadUserByUsername("admin");

      assertThat(userDetails).isNotNull();
      assertThat(userDetails.getUsername()).isEqualTo("admin");
      assertThat(userDetails.getAuthorities()).isNotEmpty();
      assertThat(userDetails.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Should load regular user successfully")
    void shouldLoadRegularUser() {
      UserDetails userDetails = service.loadUserByUsername("user");

      assertThat(userDetails).isNotNull();
      assertThat(userDetails.getUsername()).isEqualTo("user");
      assertThat(userDetails.getAuthorities()).hasSize(1);
    }

    @Test
    @DisplayName("Should throw exception for null username")
    void shouldThrowExceptionForNullUsername() {
      assertThatThrownBy(() -> service.loadUserByUsername(null))
          .isInstanceOf(UsernameNotFoundException.class)
          .hasMessageContaining("Username cannot be empty");
    }

    @Test
    @DisplayName("Should throw exception for empty username")
    void shouldThrowExceptionForEmptyUsername() {
      assertThatThrownBy(() -> service.loadUserByUsername(""))
          .isInstanceOf(UsernameNotFoundException.class)
          .hasMessageContaining("Username cannot be empty");
    }

    @Test
    @DisplayName("Should throw exception for blank username")
    void shouldThrowExceptionForBlankUsername() {
      assertThatThrownBy(() -> service.loadUserByUsername("   "))
          .isInstanceOf(UsernameNotFoundException.class)
          .hasMessageContaining("Username cannot be empty");
    }

    @Test
    @DisplayName("Should throw exception for short username")
    void shouldThrowExceptionForShortUsername() {
      assertThatThrownBy(() -> service.loadUserByUsername("ab"))
          .isInstanceOf(UsernameNotFoundException.class)
          .hasMessageContaining("at least 3 characters");
    }

    @Test
    @DisplayName("Should throw exception for long username")
    void shouldThrowExceptionForLongUsername() {
      String longUsername = "a".repeat(51);

      assertThatThrownBy(() -> service.loadUserByUsername(longUsername))
          .isInstanceOf(UsernameNotFoundException.class)
          .hasMessageContaining("at most 50 characters");
    }

    @Test
    @DisplayName("Should throw exception for invalid characters in username")
    void shouldThrowExceptionForInvalidCharacters() {
      assertThatThrownBy(() -> service.loadUserByUsername("user@domain"))
          .isInstanceOf(UsernameNotFoundException.class)
          .hasMessageContaining("can only contain");
    }

    @Test
    @DisplayName("Should throw exception for non-existent user")
    void shouldThrowExceptionForNonExistentUser() {
      assertThatThrownBy(() -> service.loadUserByUsername("nonexistent"))
          .isInstanceOf(UsernameNotFoundException.class)
          .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("Should load disabled user with its status flags intact")
    void shouldLoadDisabledUserWithStatusFlags() {
      // Status enforcement belongs to the authentication provider, not loadUserByUsername
      UserDetails disabled = service.loadUserByUsername("disabled");

      assertThat(disabled.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Should accumulate multiple validation errors")
    void shouldAccumulateMultipleValidationErrors() {
      // Username with multiple issues: too short AND invalid characters
      assertThatThrownBy(() -> service.loadUserByUsername("a@"))
          .isInstanceOf(UsernameNotFoundException.class)
          .hasMessageMatching(".*at least 3 characters.*can only contain.*");
    }
  }

  @Nested
  @DisplayName("addUser Tests")
  class AddUserTests {

    @Test
    @DisplayName("Should add new user successfully")
    void shouldAddNewUser() {
      UserDetails newUser =
          User.builder().username("newuser").password("{noop}password").roles("USER").build();

      service.addUser(newUser);

      UserDetails loaded = service.loadUserByUsername("newuser");
      assertThat(loaded).isNotNull();
      assertThat(loaded.getUsername()).isEqualTo("newuser");
    }

    @Test
    @DisplayName("Should replace existing user")
    void shouldReplaceExistingUser() {
      UserDetails updatedAdmin =
          User.builder()
              .username("admin")
              .password("{noop}newpassword")
              .roles("SUPER_ADMIN")
              .build();

      service.addUser(updatedAdmin);

      UserDetails loaded = service.loadUserByUsername("admin");
      assertThat(loaded.getPassword()).isEqualTo("{noop}newpassword");
    }
  }

  @Nested
  @DisplayName("Username Validation Tests")
  class UsernameValidationTests {

    @Test
    @DisplayName("Should accept valid alphanumeric username")
    void shouldAcceptValidAlphanumericUsername() {
      UserDetails user =
          User.builder().username("user123").password("{noop}password").roles("USER").build();

      service.addUser(user);

      assertThatCode(() -> service.loadUserByUsername("user123")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should accept username with underscores")
    void shouldAcceptUsernameWithUnderscores() {
      UserDetails user =
          User.builder().username("user_name").password("{noop}password").roles("USER").build();

      service.addUser(user);

      assertThatCode(() -> service.loadUserByUsername("user_name")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should accept username with hyphens")
    void shouldAcceptUsernameWithHyphens() {
      UserDetails user =
          User.builder().username("user-name").password("{noop}password").roles("USER").build();

      service.addUser(user);

      assertThatCode(() -> service.loadUserByUsername("user-name")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should accept username at minimum length")
    void shouldAcceptUsernameAtMinimumLength() {
      UserDetails user =
          User.builder().username("abc").password("{noop}password").roles("USER").build();

      service.addUser(user);

      assertThatCode(() -> service.loadUserByUsername("abc")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should accept username at maximum length")
    void shouldAcceptUsernameAtMaximumLength() {
      String maxLengthUsername = "a".repeat(50);

      UserDetails user =
          User.builder()
              .username(maxLengthUsername)
              .password("{noop}password")
              .roles("USER")
              .build();

      service.addUser(user);

      assertThatCode(() -> service.loadUserByUsername(maxLengthUsername))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("validateAccountStatus Tests")
  class ValidateAccountStatusTests {

    @Test
    @DisplayName("Should pass a fully active account")
    void shouldPassActiveAccount() {
      UserDetails active =
          User.builder().username("active").password("{noop}password").roles("USER").build();

      assertThat(service.validateAccountStatus(active).isValid()).isTrue();
    }

    @Test
    @DisplayName("Should reject locked account")
    void shouldRejectLockedAccount() {
      UserDetails lockedUser =
          User.builder()
              .username("locked")
              .password("{noop}password")
              .roles("USER")
              .accountLocked(true)
              .build();

      var result = service.validateAccountStatus(lockedUser);

      assertThat(result.isInvalid()).isTrue();
      assertThat(result.getError())
          .extracting(ValidatedUserDetailsService.UserValidationError::message)
          .containsExactly("Account is locked");
    }

    @Test
    @DisplayName("Should accumulate multiple account status errors")
    void shouldAccumulateMultipleAccountStatusErrors() {
      UserDetails multipleIssuesUser =
          User.builder()
              .username("issues")
              .password("{noop}password")
              .roles("USER")
              .disabled(true)
              .accountLocked(true)
              .accountExpired(true)
              .build();

      var result = service.validateAccountStatus(multipleIssuesUser);

      assertThat(result.isInvalid()).isTrue();
      assertThat(result.getError())
          .extracting(ValidatedUserDetailsService.UserValidationError::message)
          .containsExactly("Account is disabled", "Account is locked", "Account has expired");
    }
  }

  @Nested
  @DisplayName("Sample Users Factory Tests")
  class SampleUsersFactoryTests {

    @Test
    @DisplayName("Default constructor starts with no users")
    void defaultConstructorStartsEmpty() {
      var empty = new ValidatedUserDetailsService();

      assertThatThrownBy(() -> empty.loadUserByUsername("admin"))
          .isInstanceOf(UsernameNotFoundException.class)
          .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("withSampleUsers pre-populates the demo accounts")
    void withSampleUsersPrePopulatesDemoAccounts() {
      UserDetails admin = service.loadUserByUsername("admin");

      assertThat(admin.getUsername()).isEqualTo("admin");
      assertThat(admin.getAuthorities()).hasSizeGreaterThan(1);
      assertThat(service.loadUserByUsername("user").getUsername()).isEqualTo("user");
      assertThat(service.loadUserByUsername("disabled").isEnabled()).isFalse();
    }
  }
}
