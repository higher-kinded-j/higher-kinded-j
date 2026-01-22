// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.context;

import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.Constructor;
import java.security.Principal;
import java.util.Set;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.maybe.Maybe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for {@link SecurityContext}.
 *
 * <p>Coverage includes static ScopedValue fields, authentication methods, role checks, permission
 * checks, and exception types.
 */
@DisplayName("SecurityContext Complete Test Suite")
class SecurityContextTest {

  private final Principal testPrincipal = () -> "testuser@example.com";
  private final Principal adminPrincipal = () -> "admin@example.com";
  private final Set<String> userRoles = Set.of("USER");
  private final Set<String> adminRoles = Set.of("USER", "ADMIN");
  private final Set<String> allRoles = Set.of("USER", "ADMIN", "MANAGER");
  private final Set<String> userPermissions = Set.of("document:read");
  private final Set<String> adminPermissions = Set.of("document:read", "document:write", "user:delete");

  @Nested
  @DisplayName("Static ScopedValue Fields")
  class StaticScopedValueFields {

    @Test
    @DisplayName("PRINCIPAL should be a ScopedValue")
    void principal_shouldBeScopedValue() {
      assertThat(SecurityContext.PRINCIPAL).isNotNull();
    }

    @Test
    @DisplayName("ROLES should be a ScopedValue")
    void roles_shouldBeScopedValue() {
      assertThat(SecurityContext.ROLES).isNotNull();
    }

    @Test
    @DisplayName("PERMISSIONS should be a ScopedValue")
    void permissions_shouldBeScopedValue() {
      assertThat(SecurityContext.PERMISSIONS).isNotNull();
    }

    @Test
    @DisplayName("AUTH_TOKEN should be a ScopedValue")
    void authToken_shouldBeScopedValue() {
      assertThat(SecurityContext.AUTH_TOKEN).isNotNull();
    }

    @Test
    @DisplayName("SESSION_ID should be a ScopedValue")
    void sessionId_shouldBeScopedValue() {
      assertThat(SecurityContext.SESSION_ID).isNotNull();
    }

    @Test
    @DisplayName("All ScopedValues should be distinct instances")
    void allScopedValuesShouldBeDistinct() {
      assertThat(SecurityContext.PRINCIPAL).isNotSameAs(SecurityContext.ROLES);
      assertThat(SecurityContext.ROLES).isNotSameAs(SecurityContext.PERMISSIONS);
      assertThat(SecurityContext.AUTH_TOKEN).isNotSameAs(SecurityContext.SESSION_ID);
    }
  }

  @Nested
  @DisplayName("Authentication Checks")
  class AuthenticationChecks {

    @Nested
    @DisplayName("isAuthenticated()")
    class IsAuthenticatedTests {

      @Test
      @DisplayName("isAuthenticated() should return true when principal is present")
      void isAuthenticated_shouldReturnTrueWhenPrincipalPresent() throws Exception {
        Context<Principal, Boolean> ctx = SecurityContext.isAuthenticated();

        Boolean result =
            ScopedValue.where(SecurityContext.PRINCIPAL, testPrincipal).call(ctx::run);

        assertThat(result).isTrue();
      }

      @Test
      @DisplayName("isAuthenticated() should return false when principal is null")
      void isAuthenticated_shouldReturnFalseWhenPrincipalNull() throws Exception {
        Context<Principal, Boolean> ctx = SecurityContext.isAuthenticated();

        Boolean result =
            ScopedValue.where(SecurityContext.PRINCIPAL, null).call(ctx::run);

        assertThat(result).isFalse();
      }
    }

    @Nested
    @DisplayName("requireAuthenticated()")
    class RequireAuthenticatedTests {

      @Test
      @DisplayName("requireAuthenticated() should return principal when authenticated")
      void requireAuthenticated_shouldReturnPrincipalWhenAuthenticated() throws Exception {
        Context<Principal, Principal> ctx = SecurityContext.requireAuthenticated();

        Principal result =
            ScopedValue.where(SecurityContext.PRINCIPAL, testPrincipal).call(ctx::run);

        assertThat(result).isSameAs(testPrincipal);
      }

      @Test
      @DisplayName("requireAuthenticated() should throw UnauthenticatedException when principal is null")
      void requireAuthenticated_shouldThrowWhenPrincipalNull() throws Exception {
        Context<Principal, Principal> ctx = SecurityContext.requireAuthenticated();

        assertThatThrownBy(
                () -> ScopedValue.where(SecurityContext.PRINCIPAL, null).call(ctx::run))
            .isInstanceOf(SecurityContext.UnauthenticatedException.class)
            .hasMessageContaining("Authentication required");
      }
    }

    @Nested
    @DisplayName("principalIfPresent()")
    class PrincipalIfPresentTests {

      @Test
      @DisplayName("principalIfPresent() should return Just when authenticated")
      void principalIfPresent_shouldReturnJustWhenAuthenticated() throws Exception {
        Context<Principal, Maybe<Principal>> ctx = SecurityContext.principalIfPresent();

        Maybe<Principal> result =
            ScopedValue.where(SecurityContext.PRINCIPAL, testPrincipal).call(ctx::run);

        assertThat(result.isJust()).isTrue();
        assertThat(result.orElse(null)).isSameAs(testPrincipal);
      }

      @Test
      @DisplayName("principalIfPresent() should return Nothing when principal is null")
      void principalIfPresent_shouldReturnNothingWhenPrincipalNull() throws Exception {
        Context<Principal, Maybe<Principal>> ctx = SecurityContext.principalIfPresent();

        Maybe<Principal> result =
            ScopedValue.where(SecurityContext.PRINCIPAL, null).call(ctx::run);

        assertThat(result.isJust()).isFalse();
      }
    }
  }

  @Nested
  @DisplayName("Role Checks")
  class RoleChecks {

    @Nested
    @DisplayName("hasRole()")
    class HasRoleTests {

      @Test
      @DisplayName("hasRole() should return true when user has role")
      void hasRole_shouldReturnTrueWhenUserHasRole() throws Exception {
        Context<Set<String>, Boolean> ctx = SecurityContext.hasRole("USER");

        Boolean result =
            ScopedValue.where(SecurityContext.ROLES, userRoles).call(ctx::run);

        assertThat(result).isTrue();
      }

      @Test
      @DisplayName("hasRole() should return false when user lacks role")
      void hasRole_shouldReturnFalseWhenUserLacksRole() throws Exception {
        Context<Set<String>, Boolean> ctx = SecurityContext.hasRole("ADMIN");

        Boolean result =
            ScopedValue.where(SecurityContext.ROLES, userRoles).call(ctx::run);

        assertThat(result).isFalse();
      }

      @Test
      @DisplayName("hasRole() should return false when roles is null")
      void hasRole_shouldReturnFalseWhenRolesNull() throws Exception {
        Context<Set<String>, Boolean> ctx = SecurityContext.hasRole("USER");

        Boolean result =
            ScopedValue.where(SecurityContext.ROLES, null).call(ctx::run);

        assertThat(result).isFalse();
      }

      @Test
      @DisplayName("hasRole() should throw NullPointerException for null role")
      void hasRole_shouldThrowForNullRole() {
        assertThatNullPointerException()
            .isThrownBy(() -> SecurityContext.hasRole(null))
            .withMessageContaining("role cannot be null");
      }
    }

    @Nested
    @DisplayName("hasAnyRole()")
    class HasAnyRoleTests {

      @Test
      @DisplayName("hasAnyRole() should return true when user has any of the roles")
      void hasAnyRole_shouldReturnTrueWhenUserHasAny() throws Exception {
        Context<Set<String>, Boolean> ctx = SecurityContext.hasAnyRole("ADMIN", "SUPERUSER");

        Boolean result =
            ScopedValue.where(SecurityContext.ROLES, adminRoles).call(ctx::run);

        assertThat(result).isTrue();
      }

      @Test
      @DisplayName("hasAnyRole() should return false when user has none of the roles")
      void hasAnyRole_shouldReturnFalseWhenUserHasNone() throws Exception {
        Context<Set<String>, Boolean> ctx = SecurityContext.hasAnyRole("SUPERUSER", "ROOT");

        Boolean result =
            ScopedValue.where(SecurityContext.ROLES, userRoles).call(ctx::run);

        assertThat(result).isFalse();
      }

      @Test
      @DisplayName("hasAnyRole() should return false when userRoles is null")
      void hasAnyRole_shouldReturnFalseWhenUserRolesNull() throws Exception {
        Context<Set<String>, Boolean> ctx = SecurityContext.hasAnyRole("USER", "ADMIN");

        Boolean result =
            ScopedValue.where(SecurityContext.ROLES, null).call(ctx::run);

        assertThat(result).isFalse();
      }

      @Test
      @DisplayName("hasAnyRole() should throw NullPointerException for null roles")
      void hasAnyRole_shouldThrowForNullRoles() {
        assertThatNullPointerException()
            .isThrownBy(() -> SecurityContext.hasAnyRole((String[]) null))
            .withMessageContaining("roles cannot be null");
      }
    }

    @Nested
    @DisplayName("hasAllRoles()")
    class HasAllRolesTests {

      @Test
      @DisplayName("hasAllRoles() should return true when user has all roles")
      void hasAllRoles_shouldReturnTrueWhenUserHasAll() throws Exception {
        Context<Set<String>, Boolean> ctx = SecurityContext.hasAllRoles("USER", "ADMIN");

        Boolean result =
            ScopedValue.where(SecurityContext.ROLES, adminRoles).call(ctx::run);

        assertThat(result).isTrue();
      }

      @Test
      @DisplayName("hasAllRoles() should return false when user lacks some roles")
      void hasAllRoles_shouldReturnFalseWhenUserLacksSome() throws Exception {
        Context<Set<String>, Boolean> ctx = SecurityContext.hasAllRoles("USER", "ADMIN", "SUPERUSER");

        Boolean result =
            ScopedValue.where(SecurityContext.ROLES, adminRoles).call(ctx::run);

        assertThat(result).isFalse();
      }

      @Test
      @DisplayName("hasAllRoles() should return false when userRoles is null")
      void hasAllRoles_shouldReturnFalseWhenUserRolesNull() throws Exception {
        Context<Set<String>, Boolean> ctx = SecurityContext.hasAllRoles("USER", "ADMIN");

        Boolean result =
            ScopedValue.where(SecurityContext.ROLES, null).call(ctx::run);

        assertThat(result).isFalse();
      }

      @Test
      @DisplayName("hasAllRoles() should throw NullPointerException for null roles")
      void hasAllRoles_shouldThrowForNullRoles() {
        assertThatNullPointerException()
            .isThrownBy(() -> SecurityContext.hasAllRoles((String[]) null))
            .withMessageContaining("roles cannot be null");
      }
    }

    @Nested
    @DisplayName("requireRole()")
    class RequireRoleTests {

      @Test
      @DisplayName("requireRole() should return Unit when user has role")
      void requireRole_shouldReturnUnitWhenUserHasRole() throws Exception {
        Context<Set<String>, Unit> ctx = SecurityContext.requireRole("USER");

        Unit result =
            ScopedValue.where(SecurityContext.ROLES, userRoles).call(ctx::run);

        assertThat(result).isEqualTo(Unit.INSTANCE);
      }

      @Test
      @DisplayName("requireRole() should throw UnauthorisedException when user lacks role")
      void requireRole_shouldThrowWhenUserLacksRole() throws Exception {
        Context<Set<String>, Unit> ctx = SecurityContext.requireRole("ADMIN");

        assertThatThrownBy(
                () -> ScopedValue.where(SecurityContext.ROLES, userRoles).call(ctx::run))
            .isInstanceOf(SecurityContext.UnauthorisedException.class)
            .hasMessageContaining("Role required: ADMIN");
      }

      @Test
      @DisplayName("requireRole() should throw NullPointerException for null role")
      void requireRole_shouldThrowForNullRole() {
        assertThatNullPointerException()
            .isThrownBy(() -> SecurityContext.requireRole(null))
            .withMessageContaining("role cannot be null");
      }
    }

    @Nested
    @DisplayName("requireAnyRole()")
    class RequireAnyRoleTests {

      @Test
      @DisplayName("requireAnyRole() should return Unit when user has any role")
      void requireAnyRole_shouldReturnUnitWhenUserHasAny() throws Exception {
        Context<Set<String>, Unit> ctx = SecurityContext.requireAnyRole("ADMIN", "SUPERUSER");

        Unit result =
            ScopedValue.where(SecurityContext.ROLES, adminRoles).call(ctx::run);

        assertThat(result).isEqualTo(Unit.INSTANCE);
      }

      @Test
      @DisplayName("requireAnyRole() should throw UnauthorisedException when user has none")
      void requireAnyRole_shouldThrowWhenUserHasNone() throws Exception {
        Context<Set<String>, Unit> ctx = SecurityContext.requireAnyRole("SUPERUSER", "ROOT");

        assertThatThrownBy(
                () -> ScopedValue.where(SecurityContext.ROLES, userRoles).call(ctx::run))
            .isInstanceOf(SecurityContext.UnauthorisedException.class)
            .hasMessageContaining("One of these roles required");
      }

      @Test
      @DisplayName("requireAnyRole() should throw NullPointerException for null roles")
      void requireAnyRole_shouldThrowForNullRoles() {
        assertThatNullPointerException()
            .isThrownBy(() -> SecurityContext.requireAnyRole((String[]) null))
            .withMessageContaining("roles cannot be null");
      }
    }

    @Nested
    @DisplayName("requireAllRoles()")
    class RequireAllRolesTests {

      @Test
      @DisplayName("requireAllRoles() should return Unit when user has all roles")
      void requireAllRoles_shouldReturnUnitWhenUserHasAll() throws Exception {
        Context<Set<String>, Unit> ctx = SecurityContext.requireAllRoles("USER", "ADMIN");

        Unit result =
            ScopedValue.where(SecurityContext.ROLES, adminRoles).call(ctx::run);

        assertThat(result).isEqualTo(Unit.INSTANCE);
      }

      @Test
      @DisplayName("requireAllRoles() should throw UnauthorisedException when user lacks some")
      void requireAllRoles_shouldThrowWhenUserLacksSome() throws Exception {
        Context<Set<String>, Unit> ctx = SecurityContext.requireAllRoles("USER", "ADMIN", "SUPERUSER");

        assertThatThrownBy(
                () -> ScopedValue.where(SecurityContext.ROLES, adminRoles).call(ctx::run))
            .isInstanceOf(SecurityContext.UnauthorisedException.class)
            .hasMessageContaining("All of these roles required");
      }

      @Test
      @DisplayName("requireAllRoles() should throw NullPointerException for null roles")
      void requireAllRoles_shouldThrowForNullRoles() {
        assertThatNullPointerException()
            .isThrownBy(() -> SecurityContext.requireAllRoles((String[]) null))
            .withMessageContaining("roles cannot be null");
      }
    }
  }

  @Nested
  @DisplayName("Permission Checks")
  class PermissionChecks {

    @Nested
    @DisplayName("hasPermission()")
    class HasPermissionTests {

      @Test
      @DisplayName("hasPermission() should return true when user has permission")
      void hasPermission_shouldReturnTrueWhenUserHasPermission() throws Exception {
        Context<Set<String>, Boolean> ctx = SecurityContext.hasPermission("document:read");

        Boolean result =
            ScopedValue.where(SecurityContext.PERMISSIONS, userPermissions).call(ctx::run);

        assertThat(result).isTrue();
      }

      @Test
      @DisplayName("hasPermission() should return false when user lacks permission")
      void hasPermission_shouldReturnFalseWhenUserLacksPermission() throws Exception {
        Context<Set<String>, Boolean> ctx = SecurityContext.hasPermission("document:write");

        Boolean result =
            ScopedValue.where(SecurityContext.PERMISSIONS, userPermissions).call(ctx::run);

        assertThat(result).isFalse();
      }

      @Test
      @DisplayName("hasPermission() should return false when permissions is null")
      void hasPermission_shouldReturnFalseWhenPermissionsNull() throws Exception {
        Context<Set<String>, Boolean> ctx = SecurityContext.hasPermission("document:read");

        Boolean result =
            ScopedValue.where(SecurityContext.PERMISSIONS, null).call(ctx::run);

        assertThat(result).isFalse();
      }

      @Test
      @DisplayName("hasPermission() should throw NullPointerException for null permission")
      void hasPermission_shouldThrowForNullPermission() {
        assertThatNullPointerException()
            .isThrownBy(() -> SecurityContext.hasPermission(null))
            .withMessageContaining("permission cannot be null");
      }
    }

    @Nested
    @DisplayName("requirePermission()")
    class RequirePermissionTests {

      @Test
      @DisplayName("requirePermission() should return Unit when user has permission")
      void requirePermission_shouldReturnUnitWhenUserHasPermission() throws Exception {
        Context<Set<String>, Unit> ctx = SecurityContext.requirePermission("document:read");

        Unit result =
            ScopedValue.where(SecurityContext.PERMISSIONS, userPermissions).call(ctx::run);

        assertThat(result).isEqualTo(Unit.INSTANCE);
      }

      @Test
      @DisplayName("requirePermission() should throw UnauthorisedException when user lacks permission")
      void requirePermission_shouldThrowWhenUserLacksPermission() throws Exception {
        Context<Set<String>, Unit> ctx = SecurityContext.requirePermission("user:delete");

        assertThatThrownBy(
                () -> ScopedValue.where(SecurityContext.PERMISSIONS, userPermissions).call(ctx::run))
            .isInstanceOf(SecurityContext.UnauthorisedException.class)
            .hasMessageContaining("Permission required: user:delete");
      }

      @Test
      @DisplayName("requirePermission() should throw NullPointerException for null permission")
      void requirePermission_shouldThrowForNullPermission() {
        assertThatNullPointerException()
            .isThrownBy(() -> SecurityContext.requirePermission(null))
            .withMessageContaining("permission cannot be null");
      }
    }
  }

  @Nested
  @DisplayName("Exception Types")
  class ExceptionTypes {

    @Nested
    @DisplayName("UnauthenticatedException")
    class UnauthenticatedExceptionTests {

      @Test
      @DisplayName("UnauthenticatedException should extend RuntimeException")
      void unauthenticatedException_shouldExtendRuntimeException() {
        assertThat(RuntimeException.class.isAssignableFrom(
            SecurityContext.UnauthenticatedException.class)).isTrue();
      }

      @Test
      @DisplayName("UnauthenticatedException should preserve message")
      void unauthenticatedException_shouldPreserveMessage() {
        SecurityContext.UnauthenticatedException ex =
            new SecurityContext.UnauthenticatedException("Test message");

        assertThat(ex.getMessage()).isEqualTo("Test message");
      }
    }

    @Nested
    @DisplayName("UnauthorisedException")
    class UnauthorisedExceptionTests {

      @Test
      @DisplayName("UnauthorisedException should extend RuntimeException")
      void unauthorisedException_shouldExtendRuntimeException() {
        assertThat(RuntimeException.class.isAssignableFrom(
            SecurityContext.UnauthorisedException.class)).isTrue();
      }

      @Test
      @DisplayName("UnauthorisedException should preserve message")
      void unauthorisedException_shouldPreserveMessage() {
        SecurityContext.UnauthorisedException ex =
            new SecurityContext.UnauthorisedException("Test message");

        assertThat(ex.getMessage()).isEqualTo("Test message");
      }
    }
  }

  @Nested
  @DisplayName("Utility Class Design")
  class UtilityClassDesign {

    @Test
    @DisplayName("SecurityContext should be final")
    void securityContext_shouldBeFinal() {
      assertThat(java.lang.reflect.Modifier.isFinal(SecurityContext.class.getModifiers())).isTrue();
    }

    @Test
    @DisplayName("SecurityContext should have private constructor")
    void securityContext_shouldHavePrivateConstructor() throws Exception {
      Constructor<SecurityContext> constructor = SecurityContext.class.getDeclaredConstructor();
      assertThat(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }
  }

  @Nested
  @DisplayName("Combined ScopedValue Binding")
  class CombinedScopedValueBinding {

    @Test
    @DisplayName("Multiple security ScopedValues can be bound together")
    void multipleScopedValuesCanBeBoundTogether() throws Exception {
      String result =
          ScopedValue.where(SecurityContext.PRINCIPAL, testPrincipal)
              .where(SecurityContext.ROLES, adminRoles)
              .where(SecurityContext.PERMISSIONS, adminPermissions)
              .where(SecurityContext.AUTH_TOKEN, "jwt-token-123")
              .where(SecurityContext.SESSION_ID, "session-456")
              .call(
                  () ->
                      SecurityContext.PRINCIPAL.get().getName()
                          + "|"
                          + SecurityContext.ROLES.get().size()
                          + "|"
                          + SecurityContext.AUTH_TOKEN.get());

      assertThat(result).isEqualTo("testuser@example.com|2|jwt-token-123");
    }
  }
}
