// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

@ExtendWith(MockitoExtension.class)
@DisplayName("EitherAuthorizationManager Tests")
class EitherAuthorizationManagerTest {

  @Mock private Authentication authentication;

  private MockHttpServletRequest request;

  private EitherAuthorizationManager manager;
  private RequestAuthorizationContext context;

  @BeforeEach
  void setUp() {
    manager = new EitherAuthorizationManager();
    request = new MockHttpServletRequest();
    context = new RequestAuthorizationContext(request);
  }

  @Nested
  @DisplayName("check Tests - Success Cases")
  class CheckSuccessTests {

    @Test
    @DisplayName("Should grant access for admin with valid path")
    void shouldGrantAccessForAdminWithValidPath() {
      when(authentication.isAuthenticated()).thenReturn(true);
      doReturn(createAuthorities("ROLE_ADMIN", "ROLE_USER")).when(authentication).getAuthorities();
      request.setRequestURI("/api/admin/users");

      Supplier<Authentication> authSupplier = () -> authentication;

      AuthorizationDecision decision =
          (AuthorizationDecision) manager.authorize(authSupplier, context);

      assertThat(decision).isNotNull();
      assertThat(decision.isGranted()).isTrue();
    }

    @Test
    @DisplayName("Should grant access for admin with multiple roles")
    void shouldGrantAccessForAdminWithMultipleRoles() {
      when(authentication.isAuthenticated()).thenReturn(true);
      doReturn(createAuthorities("ROLE_ADMIN", "ROLE_USER", "ROLE_MODERATOR"))
          .when(authentication)
          .getAuthorities();
      request.setRequestURI("/api/admin/settings");

      Supplier<Authentication> authSupplier = () -> authentication;

      AuthorizationDecision decision =
          (AuthorizationDecision) manager.authorize(authSupplier, context);

      assertThat(decision.isGranted()).isTrue();
    }
  }

  @Nested
  @DisplayName("check Tests - Failure Cases")
  class CheckFailureTests {

    @Test
    @DisplayName("Should deny access when authentication is null")
    void shouldDenyAccessWhenAuthenticationIsNull() {
      Supplier<Authentication> authSupplier = () -> null;

      AuthorizationDecision decision =
          (AuthorizationDecision) manager.authorize(authSupplier, context);

      assertThat(decision).isNotNull();
      assertThat(decision.isGranted()).isFalse();
    }

    @Test
    @DisplayName("Should deny access when not authenticated")
    void shouldDenyAccessWhenNotAuthenticated() {
      when(authentication.isAuthenticated()).thenReturn(false);

      Supplier<Authentication> authSupplier = () -> authentication;

      AuthorizationDecision decision =
          (AuthorizationDecision) manager.authorize(authSupplier, context);

      assertThat(decision.isGranted()).isFalse();
    }

    @Test
    @DisplayName("Should deny access when missing ADMIN role")
    void shouldDenyAccessWhenMissingAdminRole() {
      when(authentication.isAuthenticated()).thenReturn(true);
      doReturn(createAuthorities("ROLE_USER")).when(authentication).getAuthorities();

      Supplier<Authentication> authSupplier = () -> authentication;

      AuthorizationDecision decision =
          (AuthorizationDecision) manager.authorize(authSupplier, context);

      assertThat(decision.isGranted()).isFalse();
    }

    @Test
    @DisplayName("Should deny access to dangerous path even for admin")
    void shouldDenyAccessToDangerousPathEvenForAdmin() {
      when(authentication.isAuthenticated()).thenReturn(true);
      doReturn(createAuthorities("ROLE_ADMIN")).when(authentication).getAuthorities();
      request.setRequestURI("/api/admin/dangerous/action");

      Supplier<Authentication> authSupplier = () -> authentication;

      AuthorizationDecision decision =
          (AuthorizationDecision) manager.authorize(authSupplier, context);

      assertThat(decision.isGranted()).isFalse();
    }

    @Test
    @DisplayName("Should deny access when no authorities")
    void shouldDenyAccessWhenNoAuthorities() {
      when(authentication.isAuthenticated()).thenReturn(true);
      when(authentication.getAuthorities()).thenReturn(List.of());

      Supplier<Authentication> authSupplier = () -> authentication;

      AuthorizationDecision decision =
          (AuthorizationDecision) manager.authorize(authSupplier, context);

      assertThat(decision.isGranted()).isFalse();
    }
  }

  @Nested
  @DisplayName("Path Authorization Tests")
  class PathAuthorizationTests {

    @Test
    @DisplayName("Should allow safe admin paths")
    void shouldAllowSafeAdminPaths() {
      when(authentication.isAuthenticated()).thenReturn(true);
      doReturn(createAuthorities("ROLE_ADMIN")).when(authentication).getAuthorities();

      String[] safePaths = {
        "/api/admin/users", "/api/admin/settings", "/api/admin/reports", "/api/admin/audit"
      };

      for (String path : safePaths) {
        request.setRequestURI(path);
        Supplier<Authentication> authSupplier = () -> authentication;

        AuthorizationDecision decision =
            (AuthorizationDecision) manager.authorize(authSupplier, context);

        assertThat(decision.isGranted()).as("Should grant access to path: " + path).isTrue();
      }
    }

    @Test
    @DisplayName("Should block all dangerous paths")
    void shouldBlockAllDangerousPaths() {
      when(authentication.isAuthenticated()).thenReturn(true);
      doReturn(createAuthorities("ROLE_ADMIN")).when(authentication).getAuthorities();

      String[] dangerousPaths = {
        "/api/admin/dangerous",
        "/api/admin/dangerous/delete-all",
        "/api/admin/dangerous/reset-system"
      };

      for (String path : dangerousPaths) {
        request.setRequestURI(path);
        Supplier<Authentication> authSupplier = () -> authentication;

        AuthorizationDecision decision =
            (AuthorizationDecision) manager.authorize(authSupplier, context);

        assertThat(decision.isGranted()).as("Should deny access to path: " + path).isFalse();
      }
    }
  }

  @Nested
  @DisplayName("denyPathPrefix Normalisation Tests")
  class DenyPathPrefixNormalisationTests {

    @Test
    @DisplayName("A trailing-slash prefix still denies descendants (no fail-open)")
    void trailingSlashPrefixStillDeniesDescendants() {
      // Regression: "/api/admin/" would build a "/api/admin//**" descendants matcher whose empty
      // segment matches nothing, silently allowing /api/admin/secret.
      var custom =
          new EitherAuthorizationManager(
              List.of(
                  EitherAuthorizationManager.requireAuthority("ROLE_ADMIN"),
                  EitherAuthorizationManager.denyPathPrefix("/api/admin/")));
      when(authentication.isAuthenticated()).thenReturn(true);
      doReturn(createAuthorities("ROLE_ADMIN")).when(authentication).getAuthorities();
      request.setRequestURI("/api/admin/secret");

      AuthorizationDecision decision =
          (AuthorizationDecision) custom.authorize(() -> authentication, context);

      assertThat(decision.isGranted()).isFalse();
    }

    @Test
    @DisplayName("A repeated trailing slash still denies descendants (no fail-open)")
    void repeatedTrailingSlashStillDeniesDescendants() {
      // Regression: "/api/admin//" would strip only one slash → "/api/admin/" → "/api/admin//**",
      // whose empty segment matches nothing, silently allowing /api/admin/secret.
      var custom =
          new EitherAuthorizationManager(
              List.of(
                  EitherAuthorizationManager.requireAuthority("ROLE_ADMIN"),
                  EitherAuthorizationManager.denyPathPrefix("/api/admin//")));
      when(authentication.isAuthenticated()).thenReturn(true);
      doReturn(createAuthorities("ROLE_ADMIN")).when(authentication).getAuthorities();
      request.setRequestURI("/api/admin/secret");

      AuthorizationDecision decision =
          (AuthorizationDecision) custom.authorize(() -> authentication, context);

      assertThat(decision.isGranted()).isFalse();
    }

    @Test
    @DisplayName("Root prefix \"/\" denies descendants, not just the root path (no fail-open)")
    void rootPrefixDeniesDescendants() {
      // Regression: "/" would build a "//**" descendants matcher whose empty segment matches
      // nothing, denying only "/" itself while allowing every real path below it.
      var custom =
          new EitherAuthorizationManager(List.of(EitherAuthorizationManager.denyPathPrefix("/")));
      when(authentication.isAuthenticated()).thenReturn(true);
      request.setRequestURI("/api/users");

      AuthorizationDecision decision =
          (AuthorizationDecision) custom.authorize(() -> authentication, context);

      assertThat(decision.isGranted()).isFalse();
    }

    @Test
    @DisplayName("Rejects a blank, non-slash, or PathPattern-metacharacter prefix at construction")
    void rejectsMalformedPrefixes() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> EitherAuthorizationManager.denyPathPrefix("  "));
      assertThatIllegalArgumentException()
          .isThrownBy(() -> EitherAuthorizationManager.denyPathPrefix("api/admin"));
      // Every PathPattern metacharacter must be rejected: *, ?, and { } capture syntax would make
      // the "literal prefix" match more than intended (e.g. "/api/{segment}").
      assertThatIllegalArgumentException()
          .isThrownBy(() -> EitherAuthorizationManager.denyPathPrefix("/api/admin/**"));
      assertThatIllegalArgumentException()
          .isThrownBy(() -> EitherAuthorizationManager.denyPathPrefix("/api/admin?x"));
      assertThatIllegalArgumentException()
          .isThrownBy(() -> EitherAuthorizationManager.denyPathPrefix("/api/{segment}"));
    }
  }

  @Nested
  @DisplayName("Role Checking Tests")
  class RoleCheckingTests {

    @Test
    @DisplayName("Should require ROLE_ADMIN exactly")
    void shouldRequireRoleAdminExactly() {
      when(authentication.isAuthenticated()).thenReturn(true);
      // No need to stub request URI - test fails at authority check before reaching path check

      // Test with similar but incorrect role names
      String[] incorrectRoles = {
        "ADMIN", // Missing ROLE_ prefix
        "ROLE_ADMINISTRATOR",
        "ROLE_ADMIN_USER"
      };

      for (String role : incorrectRoles) {
        doReturn(createAuthorities(role)).when(authentication).getAuthorities();
        Supplier<Authentication> authSupplier = () -> authentication;

        AuthorizationDecision decision =
            (AuthorizationDecision) manager.authorize(authSupplier, context);

        assertThat(decision.isGranted()).as("Should deny access for role: " + role).isFalse();
      }
    }

    @Test
    @DisplayName("Should accept ROLE_ADMIN with any case")
    void shouldAcceptRoleAdminWithCorrectCase() {
      when(authentication.isAuthenticated()).thenReturn(true);
      doReturn(createAuthorities("ROLE_ADMIN")).when(authentication).getAuthorities();
      request.setRequestURI("/api/admin/users");

      Supplier<Authentication> authSupplier = () -> authentication;

      AuthorizationDecision decision =
          (AuthorizationDecision) manager.authorize(authSupplier, context);

      assertThat(decision.isGranted()).isTrue();
    }

    @Test
    @DisplayName("Should work with ADMIN role among other roles")
    void shouldWorkWithAdminRoleAmongOtherRoles() {
      when(authentication.isAuthenticated()).thenReturn(true);
      doReturn(createAuthorities("ROLE_USER", "ROLE_MODERATOR", "ROLE_ADMIN", "ROLE_AUDITOR"))
          .when(authentication)
          .getAuthorities();
      request.setRequestURI("/api/admin/users");

      Supplier<Authentication> authSupplier = () -> authentication;

      AuthorizationDecision decision =
          (AuthorizationDecision) manager.authorize(authSupplier, context);

      assertThat(decision.isGranted()).isTrue();
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Should deny a percent-encoded dangerous path")
    void shouldDenyPercentEncodedDangerousPath() {
      when(authentication.isAuthenticated()).thenReturn(true);
      doReturn(createAuthorities("ROLE_ADMIN")).when(authentication).getAuthorities();
      // A raw startsWith check on getRequestURI() would be bypassed by this encoding
      request.setRequestURI("/api/admin/%64angerous/action");

      Supplier<Authentication> authSupplier = () -> authentication;

      AuthorizationDecision decision =
          (AuthorizationDecision) manager.authorize(authSupplier, context);

      assertThat(decision.isGranted()).isFalse();
    }

    @Test
    @DisplayName("Should handle empty request URI")
    void shouldHandleEmptyRequestURI() {
      when(authentication.isAuthenticated()).thenReturn(true);
      doReturn(createAuthorities("ROLE_ADMIN")).when(authentication).getAuthorities();
      request.setRequestURI("");

      Supplier<Authentication> authSupplier = () -> authentication;

      AuthorizationDecision decision =
          (AuthorizationDecision) manager.authorize(authSupplier, context);

      assertThat(decision.isGranted()).isTrue();
    }

    @Test
    @DisplayName("Should handle null authentication name")
    void shouldHandleNullAuthenticationName() {
      when(authentication.isAuthenticated()).thenReturn(true);
      doReturn(createAuthorities("ROLE_ADMIN")).when(authentication).getAuthorities();
      request.setRequestURI("/api/admin/users");

      Supplier<Authentication> authSupplier = () -> authentication;

      AuthorizationDecision decision =
          (AuthorizationDecision) manager.authorize(authSupplier, context);

      assertThat(decision.isGranted()).isTrue();
    }
  }

  @Nested
  @DisplayName("Functional Composition Tests")
  class FunctionalCompositionTests {

    @Test
    @DisplayName("Should short-circuit on first failure")
    void shouldShortCircuitOnFirstFailure() {
      // Not authenticated - should fail at first check
      when(authentication.isAuthenticated()).thenReturn(false);

      Supplier<Authentication> authSupplier = () -> authentication;

      AuthorizationDecision decision =
          (AuthorizationDecision) manager.authorize(authSupplier, context);

      assertThat(decision.isGranted()).isFalse();
      // Should not have called getAuthorities since it short-circuited
      verify(authentication, never()).getAuthorities();
    }

    @Test
    @DisplayName("Should evaluate all checks for success case")
    void shouldEvaluateAllChecksForSuccessCase() {
      when(authentication.isAuthenticated()).thenReturn(true);
      doReturn(createAuthorities("ROLE_ADMIN")).when(authentication).getAuthorities();
      request.setRequestURI("/api/admin/users");

      Supplier<Authentication> authSupplier = () -> authentication;

      AuthorizationDecision decision =
          (AuthorizationDecision) manager.authorize(authSupplier, context);

      assertThat(decision.isGranted()).isTrue();

      // All checks should have been called
      verify(authentication).isAuthenticated();
      verify(authentication).getAuthorities();
    }
  }

  // Helper method to create authorities
  @SuppressWarnings("unchecked")
  private Collection<GrantedAuthority> createAuthorities(String... roles) {
    return (Collection<GrantedAuthority>)
        (Collection<?>) List.of(roles).stream().map(SimpleGrantedAuthority::new).toList();
  }
}
