// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.context;

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

/**
 * Tutorial: SecurityContext Patterns - Authentication and Authorisation
 *
 * <p>Learn to use SecurityContext for authentication and role-based access control. SecurityContext
 * provides pre-defined ScopedValues for security-related data that propagates through virtual
 * thread hierarchies.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>SecurityContext.PRINCIPAL for the authenticated user
 *   <li>SecurityContext.ROLES for role-based access control
 *   <li>SecurityContext.PERMISSIONS for fine-grained authorisation
 *   <li>hasRole() vs requireRole() patterns
 *   <li>Handling anonymous users
 * </ul>
 *
 * <p>Requirements: Java 25+ (ScopedValue is finalised)
 *
 * <p>Estimated time: 25-30 minutes
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
@DisplayName("Tutorial 04: SecurityContext Patterns")
public class Tutorial04_SecurityContextPatterns {

  /** Helper method for incomplete exercises. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required - replace answerRequired() with your solution");
  }

  // ===========================================================================
  // Part 1: Authentication Patterns
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Authentication")
  class AuthenticationPatterns {

    /**
     * Exercise 1: Check if user is authenticated
     *
     * <p>SecurityContext.isAuthenticated() returns a Context that checks if the PRINCIPAL is set
     * and non-null.
     *
     * <p>Task: Use isAuthenticated() and verify the result
     */
    @Test
    @DisplayName("Exercise 1: Check authentication status")
    void exercise1_checkAuthentication() throws Exception {
      Principal user = () -> "alice@example.com";

      // TODO: Replace answerRequired() with SecurityContext.isAuthenticated()
      Context<Principal, Boolean> isAuth = answerRequired();

      // With authenticated user
      Boolean authenticated =
          ScopedValue.where(SecurityContext.PRINCIPAL, user).call(() -> isAuth.run());

      assertThat(authenticated).isTrue();

      // With null principal (anonymous)
      Boolean anonymous =
          ScopedValue.where(SecurityContext.PRINCIPAL, (Principal) null).call(() -> isAuth.run());

      assertThat(anonymous).isFalse();
    }

    /**
     * Exercise 2: Require authentication
     *
     * <p>SecurityContext.requireAuthenticated() returns a Context that throws
     * UnauthenticatedException if the user is not authenticated.
     *
     * <p>Task: Use requireAuthenticated() and handle the exception
     */
    @Test
    @DisplayName("Exercise 2: Require authentication")
    void exercise2_requireAuthentication() throws Exception {
      Principal user = () -> "bob@example.com";

      // TODO: Replace answerRequired() with SecurityContext.requireAuthenticated()
      Context<Principal, Principal> requireAuth = answerRequired();

      // With authenticated user - returns the principal
      Principal result =
          ScopedValue.where(SecurityContext.PRINCIPAL, user).call(() -> requireAuth.run());

      assertThat(result.getName()).isEqualTo("bob@example.com");

      // With null principal - throws UnauthenticatedException
      assertThatThrownBy(
              () ->
                  ScopedValue.where(SecurityContext.PRINCIPAL, (Principal) null)
                      .call(() -> requireAuth.run()))
          .isInstanceOf(SecurityContext.UnauthenticatedException.class);
    }

    /**
     * Exercise 3: Get the authenticated user's name
     *
     * <p>Task: Create a Context that gets the principal's name (if authenticated)
     */
    @Test
    @DisplayName("Exercise 3: Get authenticated user's name")
    void exercise3_getUsername() throws Exception {
      Principal user = () -> "charlie@example.com";

      // TODO: Replace answerRequired() with:
      // SecurityContext.requireAuthenticated().map(Principal::getName)
      Context<Principal, String> getUsername = answerRequired();

      String name =
          ScopedValue.where(SecurityContext.PRINCIPAL, user).call(() -> getUsername.run());

      assertThat(name).isEqualTo("charlie@example.com");
    }
  }

  // ===========================================================================
  // Part 2: Role-Based Access Control
  // ===========================================================================

  @Nested
  @DisplayName("Part 2: Role-Based Access Control")
  class RoleBasedAccessControl {

    /**
     * Exercise 4: Check if user has a role
     *
     * <p>SecurityContext.hasRole(role) returns a Context<Set<String>, Boolean> that checks if the
     * user has the specified role.
     *
     * <p>Task: Use hasRole() to check for admin role
     */
    @Test
    @DisplayName("Exercise 4: Check for role")
    void exercise4_hasRole() throws Exception {
      Set<String> adminRoles = Set.of("user", "admin");
      Set<String> userRoles = Set.of("user");

      // TODO: Replace answerRequired() with SecurityContext.hasRole("admin")
      Context<Set<String>, Boolean> isAdmin = answerRequired();

      Boolean adminHasRole =
          ScopedValue.where(SecurityContext.ROLES, adminRoles).call(() -> isAdmin.run());

      Boolean userHasRole =
          ScopedValue.where(SecurityContext.ROLES, userRoles).call(() -> isAdmin.run());

      assertThat(adminHasRole).isTrue();
      assertThat(userHasRole).isFalse();
    }

    /**
     * Exercise 5: Require a role
     *
     * <p>SecurityContext.requireRole(role) returns a Context that throws UnauthorisedException if
     * the role is missing.
     *
     * <p>Task: Use requireRole() and handle the exception
     */
    @Test
    @DisplayName("Exercise 5: Require a role")
    void exercise5_requireRole() throws Exception {
      Set<String> adminRoles = Set.of("user", "admin");
      Set<String> userRoles = Set.of("user");

      // TODO: Replace answerRequired() with SecurityContext.requireRole("admin")
      Context<Set<String>, Unit> requireAdmin = answerRequired();

      // Admin user - passes without throwing
      Unit adminResult =
          ScopedValue.where(SecurityContext.ROLES, adminRoles).call(() -> requireAdmin.run());

      assertThat(adminResult).isEqualTo(Unit.INSTANCE);

      // Regular user - throws UnauthorisedException
      assertThatThrownBy(
              () ->
                  ScopedValue.where(SecurityContext.ROLES, userRoles)
                      .call(() -> requireAdmin.run()))
          .isInstanceOf(SecurityContext.UnauthorisedException.class)
          .hasMessageContaining("admin");
    }

    /**
     * Exercise 6: Check for any of multiple roles
     *
     * <p>SecurityContext.hasAnyRole(roles...) returns true if the user has ANY of the specified
     * roles.
     *
     * <p>Task: Use hasAnyRole() to check for admin OR moderator
     */
    @Test
    @DisplayName("Exercise 6: Check for any role")
    void exercise6_hasAnyRole() throws Exception {
      Set<String> adminRoles = Set.of("user", "admin");
      Set<String> modRoles = Set.of("user", "moderator");
      Set<String> basicRoles = Set.of("user");

      // TODO: Replace answerRequired() with:
      // SecurityContext.hasAnyRole("admin", "moderator")
      Context<Set<String>, Boolean> isAdminOrMod = answerRequired();

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

    /**
     * Exercise 7: Check for all required roles
     *
     * <p>SecurityContext.hasAllRoles(roles...) returns true only if the user has ALL specified
     * roles.
     *
     * <p>Task: Use hasAllRoles() to check for both user AND verified
     */
    @Test
    @DisplayName("Exercise 7: Check for all roles")
    void exercise7_hasAllRoles() throws Exception {
      Set<String> verifiedUser = Set.of("user", "verified");
      Set<String> unverifiedUser = Set.of("user");

      // TODO: Replace answerRequired() with:
      // SecurityContext.hasAllRoles("user", "verified")
      Context<Set<String>, Boolean> isVerifiedUser = answerRequired();

      Boolean verifiedResult =
          ScopedValue.where(SecurityContext.ROLES, verifiedUser).call(() -> isVerifiedUser.run());

      Boolean unverifiedResult =
          ScopedValue.where(SecurityContext.ROLES, unverifiedUser).call(() -> isVerifiedUser.run());

      assertThat(verifiedResult).isTrue();
      assertThat(unverifiedResult).isFalse();
    }
  }

  // ===========================================================================
  // Part 3: Permission-Based Access Control
  // ===========================================================================

  @Nested
  @DisplayName("Part 3: Permission-Based Access Control")
  class PermissionBasedAccessControl {

    /**
     * Exercise 8: Check for a permission
     *
     * <p>SecurityContext.hasPermission(perm) checks if the user has the specified permission.
     *
     * <p>Task: Use hasPermission() to check write access
     */
    @Test
    @DisplayName("Exercise 8: Check permission")
    void exercise8_hasPermission() throws Exception {
      Set<String> editorPerms = Set.of("documents:read", "documents:write");
      Set<String> viewerPerms = Set.of("documents:read");

      // TODO: Replace answerRequired() with:
      // SecurityContext.hasPermission("documents:write")
      Context<Set<String>, Boolean> canWrite = answerRequired();

      Boolean editorResult =
          ScopedValue.where(SecurityContext.PERMISSIONS, editorPerms).call(() -> canWrite.run());

      Boolean viewerResult =
          ScopedValue.where(SecurityContext.PERMISSIONS, viewerPerms).call(() -> canWrite.run());

      assertThat(editorResult).isTrue();
      assertThat(viewerResult).isFalse();
    }

    /**
     * Exercise 9: Require a permission
     *
     * <p>SecurityContext.requirePermission(perm) throws UnauthorisedException if the permission is
     * missing.
     *
     * <p>Task: Use requirePermission() and handle the exception
     */
    @Test
    @DisplayName("Exercise 9: Require permission")
    void exercise9_requirePermission() throws Exception {
      Set<String> adminPerms = Set.of("users:read", "users:write", "users:delete");
      Set<String> basicPerms = Set.of("users:read");

      // TODO: Replace answerRequired() with:
      // SecurityContext.requirePermission("users:delete")
      Context<Set<String>, Unit> requireDelete = answerRequired();

      // Admin has permission
      Unit adminResult =
          ScopedValue.where(SecurityContext.PERMISSIONS, adminPerms)
              .call(() -> requireDelete.run());

      assertThat(adminResult).isEqualTo(Unit.INSTANCE);

      // Basic user lacks permission
      assertThatThrownBy(
              () ->
                  ScopedValue.where(SecurityContext.PERMISSIONS, basicPerms)
                      .call(() -> requireDelete.run()))
          .isInstanceOf(SecurityContext.UnauthorisedException.class)
          .hasMessageContaining("users:delete");
    }
  }

  // ===========================================================================
  // Part 4: Combining Security Checks
  // ===========================================================================

  @Nested
  @DisplayName("Part 4: Combined Security Checks")
  class CombinedSecurityChecks {

    /**
     * Exercise 10: Combine authentication and authorisation
     *
     * <p>Note: Since authentication uses Context&lt;Principal, ?&gt; and authorization uses
     * Context&lt;Set&lt;String&gt;, ?&gt;, they have different R types and cannot be composed
     * directly with flatMap. Instead, run both checks sequentially in the same scope.
     *
     * <p>Task: Run both requireAuthenticated() and requireRole("admin") in a single scope
     */
    @Test
    @DisplayName("Exercise 10: Combined auth and authz")
    void exercise10_combinedChecks() throws Exception {
      Principal admin = () -> "admin@example.com";
      Set<String> adminRoles = Set.of("user", "admin");

      // Note: This test requires both PRINCIPAL and ROLES to be bound
      // Authentication and authorization have different Context types,
      // so we run both checks in the same scope
      String result =
          ScopedValue.where(SecurityContext.PRINCIPAL, admin)
              .where(SecurityContext.ROLES, adminRoles)
              .call(
                  () -> {
                    // TODO: Replace answerRequired() with:
                    // SecurityContext.requireAuthenticated().run()
                    Principal p = answerRequired();

                    // TODO: Replace answerRequired() with:
                    // SecurityContext.requireRole("admin").run()
                    Unit authz = answerRequired();

                    return p.getName();
                  });

      assertThat(result).isEqualTo("admin@example.com");
    }
  }

  // ===========================================================================
  // Bonus: Real-World Security Patterns
  // ===========================================================================

  @Nested
  @DisplayName("Bonus: Real-World Patterns")
  class RealWorldPatterns {

    /** This test demonstrates a complete security workflow. */
    @Test
    @DisplayName("Complete security workflow")
    void completeSecurityWorkflow() throws Exception {
      Principal user = () -> "manager@company.com";
      Set<String> roles = Set.of("user", "manager");
      Set<String> permissions =
          Set.of("reports:read", "reports:create", "team:read", "team:manage");
      String authToken = "Bearer token123";
      String sessionId = "session-abc";

      // Set up full security context
      String result =
          ScopedValue.where(SecurityContext.PRINCIPAL, user)
              .where(SecurityContext.ROLES, roles)
              .where(SecurityContext.PERMISSIONS, permissions)
              .where(SecurityContext.AUTH_TOKEN, authToken)
              .where(SecurityContext.SESSION_ID, sessionId)
              .call(
                  () -> {
                    // Verify authentication
                    Principal p = SecurityContext.requireAuthenticated().run();

                    // Check role
                    boolean isManager = SecurityContext.hasRole("manager").run();

                    // Check permission
                    boolean canManageTeam = SecurityContext.hasPermission("team:manage").run();

                    return String.format(
                        "User: %s, Manager: %s, Can manage team: %s",
                        p.getName(), isManager, canManageTeam);
                  });

      assertThat(result)
          .contains("User: manager@company.com")
          .contains("Manager: true")
          .contains("Can manage team: true");
    }
  }

  /**
   * Congratulations! You've completed Tutorial 04: SecurityContext Patterns
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>✓ How to check and require authentication
   *   <li>✓ How to implement role-based access control
   *   <li>✓ How to check individual and multiple roles
   *   <li>✓ How to implement permission-based access control
   *   <li>✓ How to combine security checks
   * </ul>
   *
   * <p>Key Takeaways:
   *
   * <ul>
   *   <li>hasX() methods return Boolean for conditional logic
   *   <li>requireX() methods throw exceptions for guard clauses
   *   <li>null PRINCIPAL means anonymous/unauthenticated
   *   <li>Security context propagates to child virtual threads
   * </ul>
   *
   * <p>Next: Tutorial 05 - Context with VTask for concurrent operations
   */
}
