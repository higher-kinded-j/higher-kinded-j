// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.context;

import java.security.Principal;
import java.util.Set;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.context.Context;
import org.higherkindedj.hkt.context.SecurityContext;

/**
 * Examples demonstrating SecurityContext for authentication and authorisation patterns.
 *
 * <p>SecurityContext provides pre-defined ScopedValue constants for security-related data:
 * principal, roles, permissions, auth token, and session ID. These enable consistent security
 * context propagation through virtual thread hierarchies.
 *
 * <p>This example demonstrates:
 *
 * <ul>
 *   <li>Authentication checking with isAuthenticated/requireAuthenticated
 *   <li>Role-based access control with hasRole/requireRole
 *   <li>Permission checking patterns
 *   <li>Combining authentication and authorisation
 *   <li>Error handling for security failures
 *   <li>Anonymous user handling
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.context.SecurityContextExample}
 *
 * @see org.higherkindedj.hkt.context.SecurityContext
 * @see org.higherkindedj.hkt.context.Context
 */
public class SecurityContextExample {

  public static void main(String[] args) throws Exception {
    System.out.println("=== SecurityContext Examples ===\n");

    authenticationExample();
    roleCheckingExample();
    permissionCheckingExample();
    combinedSecurityExample();
    errorHandlingExample();
    anonymousUserExample();
  }

  // ============================================================
  // Authentication: Principal checking
  // ============================================================

  private static void authenticationExample() throws Exception {
    System.out.println("--- Authentication Checking ---\n");

    // Create a principal for an authenticated user
    Principal alice = () -> "alice@example.com";

    // isAuthenticated returns boolean
    Context<Principal, Boolean> isAuth = SecurityContext.isAuthenticated();

    // requireAuthenticated throws if not authenticated
    Context<Principal, Principal> requireAuth = SecurityContext.requireAuthenticated();

    // With authenticated user
    ScopedValue.where(SecurityContext.PRINCIPAL, alice)
        .run(
            () -> {
              System.out.println("Is authenticated: " + isAuth.run());
              System.out.println("Principal: " + requireAuth.run().getName());
            });

    // With null principal (anonymous)
    ScopedValue.where(SecurityContext.PRINCIPAL, (Principal) null)
        .run(
            () -> {
              System.out.println("With null principal:");
              System.out.println("  Is authenticated: " + isAuth.run());

              try {
                requireAuth.run();
                System.out.println("  Unexpected: should have thrown");
              } catch (SecurityContext.UnauthenticatedException e) {
                System.out.println("  Expected exception: " + e.getMessage());
              }
            });
    System.out.println();
  }

  // ============================================================
  // Role checking: RBAC patterns
  // ============================================================

  private static void roleCheckingExample() throws Exception {
    System.out.println("--- Role-Based Access Control ---\n");

    Set<String> adminRoles = Set.of("user", "admin", "superuser");
    Set<String> userRoles = Set.of("user", "viewer");

    // hasRole: Returns boolean
    Context<Set<String>, Boolean> isAdmin = SecurityContext.hasRole("admin");
    Context<Set<String>, Boolean> isUser = SecurityContext.hasRole("user");

    // requireRole: Throws UnauthorisedException if role missing
    Context<Set<String>, Unit> requireAdmin = SecurityContext.requireRole("admin");

    // hasAnyRole / hasAllRoles for multiple roles
    Context<Set<String>, Boolean> hasAdminOrModerator =
        SecurityContext.hasAnyRole("admin", "moderator");
    Context<Set<String>, Boolean> hasAllRequired = SecurityContext.hasAllRoles("user", "verified");

    System.out.println("Admin user:");
    ScopedValue.where(SecurityContext.ROLES, adminRoles)
        .run(
            () -> {
              System.out.println("  Is admin: " + isAdmin.run());
              System.out.println("  Is user: " + isUser.run());
              System.out.println("  Has admin or moderator: " + hasAdminOrModerator.run());
              System.out.println("  Has user and verified: " + hasAllRequired.run());
              requireAdmin.run(); // Succeeds silently
              System.out.println("  requireAdmin: passed");
            });

    System.out.println("Regular user:");
    ScopedValue.where(SecurityContext.ROLES, userRoles)
        .run(
            () -> {
              System.out.println("  Is admin: " + isAdmin.run());
              System.out.println("  Is user: " + isUser.run());

              try {
                requireAdmin.run();
                System.out.println("  Unexpected: should have thrown");
              } catch (SecurityContext.UnauthorisedException e) {
                System.out.println("  Expected exception: " + e.getMessage());
              }
            });
    System.out.println();
  }

  // ============================================================
  // Permission checking: Fine-grained access
  // ============================================================

  private static void permissionCheckingExample() throws Exception {
    System.out.println("--- Permission-Based Access Control ---\n");

    Set<String> editorPermissions =
        Set.of(
            "documents:read",
            "documents:write",
            "documents:delete",
            "comments:read",
            "comments:write");

    Set<String> viewerPermissions = Set.of("documents:read", "comments:read");

    // hasPermission: Returns boolean
    Context<Set<String>, Boolean> canRead = SecurityContext.hasPermission("documents:read");
    Context<Set<String>, Boolean> canWrite = SecurityContext.hasPermission("documents:write");
    Context<Set<String>, Boolean> canDelete = SecurityContext.hasPermission("documents:delete");

    // requirePermission: Throws UnauthorisedException if missing
    Context<Set<String>, Unit> requireWrite = SecurityContext.requirePermission("documents:write");

    System.out.println("Editor permissions:");
    ScopedValue.where(SecurityContext.PERMISSIONS, editorPermissions)
        .run(
            () -> {
              System.out.println("  Can read: " + canRead.run());
              System.out.println("  Can write: " + canWrite.run());
              System.out.println("  Can delete: " + canDelete.run());
              requireWrite.run();
              System.out.println("  requireWrite: passed");
            });

    System.out.println("Viewer permissions:");
    ScopedValue.where(SecurityContext.PERMISSIONS, viewerPermissions)
        .run(
            () -> {
              System.out.println("  Can read: " + canRead.run());
              System.out.println("  Can write: " + canWrite.run());
              System.out.println("  Can delete: " + canDelete.run());

              try {
                requireWrite.run();
                System.out.println("  Unexpected: should have thrown");
              } catch (SecurityContext.UnauthorisedException e) {
                System.out.println("  Expected exception: " + e.getMessage());
              }
            });
    System.out.println();
  }

  // ============================================================
  // Combined security: Real-world patterns
  // ============================================================

  private static void combinedSecurityExample() throws Exception {
    System.out.println("--- Combined Security Checks ---\n");

    // A real service method might combine multiple checks
    Principal admin = () -> "admin@company.com";
    Set<String> roles = Set.of("user", "admin");
    Set<String> permissions = Set.of("reports:read", "reports:generate", "users:manage");
    String authToken = "Bearer eyJhbGciOiJIUzI1NiIs...";
    String sessionId = "sess-" + System.currentTimeMillis();

    System.out.println("Full security context:");
    ScopedValue.where(SecurityContext.PRINCIPAL, admin)
        .where(SecurityContext.ROLES, roles)
        .where(SecurityContext.PERMISSIONS, permissions)
        .where(SecurityContext.AUTH_TOKEN, authToken)
        .where(SecurityContext.SESSION_ID, sessionId)
        .run(
            () -> {
              // Check authentication
              Context<Principal, Principal> requireAuth = SecurityContext.requireAuthenticated();
              Principal user = requireAuth.run();
              System.out.println("  Authenticated as: " + user.getName());

              // Check authorisation
              Context<Set<String>, Unit> requireAdmin = SecurityContext.requireRole("admin");
              requireAdmin.run();
              System.out.println("  Admin role: verified");

              // Check specific permission
              Context<Set<String>, Unit> requireReports =
                  SecurityContext.requirePermission("reports:generate");
              requireReports.run();
              System.out.println("  Permission reports:generate: verified");

              // Access token for downstream calls
              String token = SecurityContext.AUTH_TOKEN.get();
              System.out.println("  Auth token available: " + (token != null && !token.isEmpty()));

              // Session tracking
              String session = SecurityContext.SESSION_ID.get();
              System.out.println("  Session: " + session);
            });
    System.out.println();
  }

  // ============================================================
  // Error handling: Security failure patterns
  // ============================================================

  private static void errorHandlingExample() throws Exception {
    System.out.println("--- Security Error Handling ---\n");

    Set<String> userRoles = Set.of("user");

    // Using recover to provide graceful degradation
    Context<Set<String>, String> adminFeature =
        SecurityContext.requireRole("admin")
            .map(unit -> "Admin feature accessed")
            .recover(error -> "Feature unavailable: insufficient permissions");

    // Using recoverWith for alternative behaviour
    Context<Set<String>, String> premiumContent =
        SecurityContext.requireRole("premium")
            .map(unit -> "Premium content here")
            .recoverWith(error -> Context.succeed("Subscribe to access premium content"));

    // Using mapError to transform exceptions
    Context<Set<String>, Unit> auditedAccess =
        SecurityContext.requireRole("auditor")
            .mapError(error -> new SecurityException("Audit access denied", error));

    ScopedValue.where(SecurityContext.ROLES, userRoles)
        .run(
            () -> {
              System.out.println("Admin feature: " + adminFeature.run());
              System.out.println("Premium content: " + premiumContent.run());

              try {
                auditedAccess.run();
              } catch (SecurityException e) {
                System.out.println(
                    "Audited access: " + e.getClass().getSimpleName() + " - " + e.getMessage());
              }
            });
    System.out.println();
  }

  // ============================================================
  // Anonymous users
  // ============================================================

  private static void anonymousUserExample() throws Exception {
    System.out.println("--- Anonymous User Handling ---\n");

    // Some operations allow anonymous access
    Context<Principal, String> getWelcome =
        SecurityContext.isAuthenticated()
            .flatMap(
                isAuth -> {
                  if (isAuth) {
                    return SecurityContext.requireAuthenticated()
                        .map(principal -> "Welcome back, " + principal.getName() + "!");
                  } else {
                    return Context.succeed("Welcome, guest! Please log in.");
                  }
                });

    // Conditional feature access
    Context<Principal, String> userProfile =
        SecurityContext.isAuthenticated()
            .flatMap(
                isAuth -> {
                  if (isAuth) {
                    return SecurityContext.requireAuthenticated()
                        .map(p -> "Profile for: " + p.getName());
                  } else {
                    return Context.succeed("Login required to view profile");
                  }
                });

    // Anonymous user (null principal)
    System.out.println("Anonymous user:");
    ScopedValue.where(SecurityContext.PRINCIPAL, (Principal) null)
        .run(
            () -> {
              System.out.println("  " + getWelcome.run());
              System.out.println("  " + userProfile.run());
            });

    // Authenticated user
    Principal bob = () -> "bob@example.com";
    System.out.println("Authenticated user:");
    ScopedValue.where(SecurityContext.PRINCIPAL, bob)
        .run(
            () -> {
              System.out.println("  " + getWelcome.run());
              System.out.println("  " + userProfile.run());
            });
    System.out.println();
  }
}
