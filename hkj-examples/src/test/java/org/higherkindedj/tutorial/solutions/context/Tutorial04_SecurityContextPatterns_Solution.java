// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.Principal;
import java.util.Set;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.context.Context;
import org.higherkindedj.hkt.context.SecurityContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Solutions for Tutorial 04: SecurityContext Patterns */
@DisplayName("Tutorial 04: SecurityContext Patterns - Solutions")
public class Tutorial04_SecurityContextPatterns_Solution {

  @Nested
  @DisplayName("Part 1: Authentication")
  class AuthenticationPatterns {

    @Test
    @DisplayName("Exercise 1: Check authentication status")
    void exercise1_checkAuthentication() throws Exception {
      Principal user = () -> "alice@example.com";

      // SOLUTION: Use SecurityContext.isAuthenticated()
      Context<Principal, Boolean> isAuth = SecurityContext.isAuthenticated();

      Boolean authenticated =
          ScopedValue.where(SecurityContext.PRINCIPAL, user).call(() -> isAuth.run());
      assertThat(authenticated).isTrue();

      Boolean anonymous =
          ScopedValue.where(SecurityContext.PRINCIPAL, (Principal) null).call(() -> isAuth.run());
      assertThat(anonymous).isFalse();
    }

    @Test
    @DisplayName("Exercise 2: Require authentication")
    void exercise2_requireAuthentication() throws Exception {
      Principal user = () -> "bob@example.com";

      // SOLUTION: Use SecurityContext.requireAuthenticated()
      Context<Principal, Principal> requireAuth = SecurityContext.requireAuthenticated();

      Principal result =
          ScopedValue.where(SecurityContext.PRINCIPAL, user).call(() -> requireAuth.run());
      assertThat(result.getName()).isEqualTo("bob@example.com");

      assertThatThrownBy(
              () ->
                  ScopedValue.where(SecurityContext.PRINCIPAL, (Principal) null)
                      .call(() -> requireAuth.run()))
          .isInstanceOf(SecurityContext.UnauthenticatedException.class);
    }

    @Test
    @DisplayName("Exercise 3: Get authenticated user's name")
    void exercise3_getUsername() throws Exception {
      Principal user = () -> "charlie@example.com";

      // SOLUTION: Chain requireAuthenticated with map
      Context<Principal, String> getUsername =
          SecurityContext.requireAuthenticated().map(Principal::getName);

      String name =
          ScopedValue.where(SecurityContext.PRINCIPAL, user).call(() -> getUsername.run());
      assertThat(name).isEqualTo("charlie@example.com");
    }
  }

  @Nested
  @DisplayName("Part 2: Role-Based Access Control")
  class RoleBasedAccessControl {

    @Test
    @DisplayName("Exercise 4: Check for role")
    void exercise4_hasRole() throws Exception {
      Set<String> adminRoles = Set.of("user", "admin");
      Set<String> userRoles = Set.of("user");

      // SOLUTION: Use SecurityContext.hasRole()
      Context<Set<String>, Boolean> isAdmin = SecurityContext.hasRole("admin");

      Boolean adminHasRole =
          ScopedValue.where(SecurityContext.ROLES, adminRoles).call(() -> isAdmin.run());
      Boolean userHasRole =
          ScopedValue.where(SecurityContext.ROLES, userRoles).call(() -> isAdmin.run());

      assertThat(adminHasRole).isTrue();
      assertThat(userHasRole).isFalse();
    }

    @Test
    @DisplayName("Exercise 5: Require a role")
    void exercise5_requireRole() throws Exception {
      Set<String> adminRoles = Set.of("user", "admin");
      Set<String> userRoles = Set.of("user");

      // SOLUTION: Use SecurityContext.requireRole()
      Context<Set<String>, Unit> requireAdmin = SecurityContext.requireRole("admin");

      Unit adminResult =
          ScopedValue.where(SecurityContext.ROLES, adminRoles).call(() -> requireAdmin.run());
      assertThat(adminResult).isEqualTo(Unit.INSTANCE);

      assertThatThrownBy(
              () ->
                  ScopedValue.where(SecurityContext.ROLES, userRoles)
                      .call(() -> requireAdmin.run()))
          .isInstanceOf(SecurityContext.UnauthorisedException.class)
          .hasMessageContaining("admin");
    }

    @Test
    @DisplayName("Exercise 6: Check for any role")
    void exercise6_hasAnyRole() throws Exception {
      Set<String> adminRoles = Set.of("user", "admin");
      Set<String> modRoles = Set.of("user", "moderator");
      Set<String> basicRoles = Set.of("user");

      // SOLUTION: Use SecurityContext.hasAnyRole()
      Context<Set<String>, Boolean> isAdminOrMod = SecurityContext.hasAnyRole("admin", "moderator");

      Boolean adminResult =
          ScopedValue.where(SecurityContext.ROLES, adminRoles).call(() -> isAdminOrMod.run());
      Boolean modResult =
          ScopedValue.where(SecurityContext.ROLES, modRoles).call(() -> isAdminOrMod.run());
      Boolean basicResult =
          ScopedValue.where(SecurityContext.ROLES, basicRoles).call(() -> isAdminOrMod.run());

      assertThat(adminResult).isTrue();
      assertThat(modResult).isTrue();
      assertThat(basicResult).isFalse();
    }

    @Test
    @DisplayName("Exercise 7: Check for all roles")
    void exercise7_hasAllRoles() throws Exception {
      Set<String> verifiedUser = Set.of("user", "verified");
      Set<String> unverifiedUser = Set.of("user");

      // SOLUTION: Use SecurityContext.hasAllRoles()
      Context<Set<String>, Boolean> isVerifiedUser =
          SecurityContext.hasAllRoles("user", "verified");

      Boolean verifiedResult =
          ScopedValue.where(SecurityContext.ROLES, verifiedUser).call(() -> isVerifiedUser.run());
      Boolean unverifiedResult =
          ScopedValue.where(SecurityContext.ROLES, unverifiedUser).call(() -> isVerifiedUser.run());

      assertThat(verifiedResult).isTrue();
      assertThat(unverifiedResult).isFalse();
    }
  }

  @Nested
  @DisplayName("Part 3: Permission-Based Access Control")
  class PermissionBasedAccessControl {

    @Test
    @DisplayName("Exercise 8: Check permission")
    void exercise8_hasPermission() throws Exception {
      Set<String> editorPerms = Set.of("documents:read", "documents:write");
      Set<String> viewerPerms = Set.of("documents:read");

      // SOLUTION: Use SecurityContext.hasPermission()
      Context<Set<String>, Boolean> canWrite = SecurityContext.hasPermission("documents:write");

      Boolean editorResult =
          ScopedValue.where(SecurityContext.PERMISSIONS, editorPerms).call(() -> canWrite.run());
      Boolean viewerResult =
          ScopedValue.where(SecurityContext.PERMISSIONS, viewerPerms).call(() -> canWrite.run());

      assertThat(editorResult).isTrue();
      assertThat(viewerResult).isFalse();
    }

    @Test
    @DisplayName("Exercise 9: Require permission")
    void exercise9_requirePermission() throws Exception {
      Set<String> adminPerms = Set.of("users:read", "users:write", "users:delete");
      Set<String> basicPerms = Set.of("users:read");

      // SOLUTION: Use SecurityContext.requirePermission()
      Context<Set<String>, Unit> requireDelete = SecurityContext.requirePermission("users:delete");

      Unit adminResult =
          ScopedValue.where(SecurityContext.PERMISSIONS, adminPerms)
              .call(() -> requireDelete.run());
      assertThat(adminResult).isEqualTo(Unit.INSTANCE);

      assertThatThrownBy(
              () ->
                  ScopedValue.where(SecurityContext.PERMISSIONS, basicPerms)
                      .call(() -> requireDelete.run()))
          .isInstanceOf(SecurityContext.UnauthorisedException.class)
          .hasMessageContaining("users:delete");
    }
  }

  @Nested
  @DisplayName("Part 4: Combined Security Checks")
  class CombinedSecurityChecks {

    @Test
    @DisplayName("Exercise 10: Combined auth and authz")
    void exercise10_combinedChecks() throws Exception {
      Principal admin = () -> "admin@example.com";
      Set<String> adminRoles = Set.of("user", "admin");

      // SOLUTION: Authentication and authorization have different Context types
      // (Context<Principal, ?> vs Context<Set<String>, ?>), so we run both checks
      // sequentially in the same scope
      String result =
          ScopedValue.where(SecurityContext.PRINCIPAL, admin)
              .where(SecurityContext.ROLES, adminRoles)
              .call(
                  () -> {
                    // SOLUTION: Call requireAuthenticated().run() to verify auth
                    Principal p = SecurityContext.requireAuthenticated().run();

                    // SOLUTION: Call requireRole("admin").run() to verify authorization
                    SecurityContext.requireRole("admin").run();

                    return p.getName();
                  });

      assertThat(result).isEqualTo("admin@example.com");
    }
  }
}
