// Copyright (c) 2025 - 2026 Magnus Smith
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

/**
 * Solution for Tutorial04 SecurityContextPatterns — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
 */
@DisplayName("Tutorial 04: SecurityContext Patterns - Solutions")
public class Tutorial04_SecurityContextPatterns_Solution {

  @Nested
  @DisplayName("Part 1: Authentication")
  class AuthenticationPatterns {

    /**
     * Why this is idiomatic: {@code SecurityContext.isAuthenticated()} returns a context that
     * yields {@code true} when a non-null principal is bound. The check is total — unbound
     * principals come back {@code false}, never throw.
     *
     * <p>Alternative: read the principal directly and null-check. Equivalent; the named helper
     * signals the intent and stays consistent across components.
     *
     * <p>Common wrong attempt: assume an unbound scoped value and a {@code null} value are
     * different. {@code isAuthenticated} treats both as "not authenticated".
     */
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

    /**
     * Why this is idiomatic: {@code requireAuthenticated()} succeeds with the principal or throws
     * {@code UnauthenticatedException}. Use it at endpoint boundaries where downstream code may
     * safely assume a user.
     *
     * <p>Alternative: check {@code isAuthenticated} and branch manually. Same outcome; the
     * require-style helper enforces the precondition with a typed exception.
     *
     * <p>Common wrong attempt: catch the exception locally and continue with a default principal.
     * The endpoint should reject unauthenticated requests at the door, not mask them.
     */
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

    /**
     * Why this is idiomatic: chain {@code requireAuthenticated().map(Principal::getName)} to read
     * the user's name once authentication succeeds. The {@code map} stays in the same monad, so the
     * type still throws on missing principals.
     *
     * <p>Alternative: read the principal first, then call {@code getName()}. Same answer; the
     * chained form keeps the contract explicit.
     *
     * <p>Common wrong attempt: skip the require and read directly. Without the gate,
     * unauthenticated requests would return {@code null} or throw NPE.
     */
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

    /**
     * Why this is idiomatic: {@code SecurityContext.hasRole(name)} returns a boolean context — true
     * when the role is in the bound set. No exception, just a clean predicate.
     *
     * <p>Alternative: read the {@code ROLES} scoped set and call {@code .contains}. Same answer;
     * the named context centralises the check.
     *
     * <p>Common wrong attempt: hard-code the role check at every endpoint. Drift between sites; the
     * helper is the single source of truth for role logic.
     */
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

    /**
     * Why this is idiomatic: {@code requireRole(name)} succeeds with {@code Unit} or throws {@code
     * UnauthorisedException} (note the British spelling). Use it as a gate at endpoint boundaries.
     *
     * <p>Alternative: check {@code hasRole} and branch manually. Same outcome; the require-style
     * helper enforces the precondition with a typed exception.
     *
     * <p>Common wrong attempt: re-check the role inside business logic. The boundary gate should
     * suffice; downstream code trusts the contract.
     */
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

    /**
     * Why this is idiomatic: {@code hasAnyRole(a, b)} returns true when the user has at least one
     * of the listed roles. Useful for endpoints that admit several roles.
     *
     * <p>Alternative: chain {@code hasRole(a) || hasRole(b)} manually. Same answer; the vararg
     * helper reads more cleanly for several roles.
     *
     * <p>Common wrong attempt: pass an empty vararg list expecting "any role passes". No matches
     * means {@code false}; supply the actual roles you accept.
     */
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

    /**
     * Why this is idiomatic: {@code hasAllRoles(a, b)} returns true only when every listed role is
     * present. Useful for compound checks like "logged-in and verified".
     *
     * <p>Alternative: chain {@code hasRole(a) && hasRole(b)} manually. Same answer; the vararg
     * helper scales without operator soup.
     *
     * <p>Common wrong attempt: confuse {@code hasAllRoles} with {@code hasAnyRole}. The former is
     * conjunctive, the latter disjunctive; pick by the policy intent.
     */
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

    /**
     * Why this is idiomatic: permissions are typically string-typed verbs (e.g. "documents:write").
     * {@code hasPermission} treats them like roles — a containment check on the bound permission
     * set.
     *
     * <p>Alternative: roll bespoke permission logic. The named helper keeps the permission encoding
     * consistent across components.
     *
     * <p>Common wrong attempt: encode permissions as enums and store them as strings
     * inconsistently. Pick one convention (string or enum) and stick with it.
     */
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

    /**
     * Why this is idiomatic: {@code requirePermission(name)} succeeds with {@code Unit} or throws
     * {@code UnauthorisedException}. Use it before a protected action runs.
     *
     * <p>Alternative: check {@code hasPermission} and throw a custom exception. The library helper
     * provides the same semantics with a typed exception class.
     *
     * <p>Common wrong attempt: bake the check inside the protected operation. The gate belongs at
     * the boundary so the operation's signature stays focused.
     */
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

    /**
     * Why this is idiomatic: authentication and authorisation use different scoped values, so run
     * both checks sequentially in the same {@code where} chain. Auth confirms identity; authz
     * confirms permission.
     *
     * <p>Alternative: write a custom combined helper that does both. Possible; the two-step form
     * keeps each gate inspectable.
     *
     * <p>Common wrong attempt: bind only the principal and skip the role binding. {@code
     * requireRole} would throw because the role set is unbound; bind every scoped value the gates
     * need.
     */
    @Test
    @DisplayName("Exercise 10: Combined auth and authz")
    void exercise10_combinedChecks() throws Exception {
      Principal admin = () -> "admin@example.com";
      Set<String> adminRoles = Set.of("user", "admin");

      // SOLUTION: Authentication and authorisation have different Context types
      // (Context<Principal, ?> vs Context<Set<String>, ?>), so we run both checks
      // sequentially in the same scope
      String result =
          ScopedValue.where(SecurityContext.PRINCIPAL, admin)
              .where(SecurityContext.ROLES, adminRoles)
              .call(
                  () -> {
                    // SOLUTION: Call requireAuthenticated().run() to verify auth
                    Principal p = SecurityContext.requireAuthenticated().run();

                    // SOLUTION: Call requireRole("admin").run() to verify authorisation
                    SecurityContext.requireRole("admin").run();

                    return p.getName();
                  });

      assertThat(result).isEqualTo("admin@example.com");
    }
  }
}
