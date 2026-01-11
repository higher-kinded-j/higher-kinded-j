// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.free_ap;

import static org.higherkindedj.hkt.id.IdKindHelper.ID;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.free_ap.FreeAp;
import org.higherkindedj.hkt.free_ap.FreeApAnalyzer;
import org.higherkindedj.hkt.free_ap.FreeApApplicative;
import org.higherkindedj.hkt.free_ap.FreeApKindHelper;
import org.higherkindedj.hkt.free_ap.SelectiveAnalyzer;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;

/**
 * Demonstrates permission checking for file operations before execution.
 *
 * <p>This example shows a real-world pattern: using static analysis to check if a program has
 * permission to perform its operations BEFORE executing any of them.
 *
 * <p>Key concepts demonstrated:
 *
 * <ul>
 *   <li>File operation DSL with Read, Write, and Delete
 *   <li>Permission levels (GUEST, USER, ADMIN)
 *   <li>Pre-execution permission analysis
 *   <li>Detailed permission violation reporting
 *   <li>Safe execution after permission verification
 * </ul>
 *
 * <p>This pattern is particularly valuable in:
 *
 * <ul>
 *   <li>Multi-tenant systems where users have different permissions
 *   <li>Audit-sensitive environments requiring pre-execution approval
 *   <li>Sandboxed execution environments
 *   <li>API gateways with fine-grained access control
 * </ul>
 *
 * <p>Run with: ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.basic.free_ap.PermissionCheckingExample
 */
public class PermissionCheckingExample {

  // ============================================================================
  // Permission Model
  // ============================================================================

  /** Permission levels in ascending order of privilege. */
  enum Permission {
    READ,
    WRITE,
    DELETE
  }

  /** User roles with associated permissions. */
  enum Role {
    GUEST(EnumSet.of(Permission.READ)),
    USER(EnumSet.of(Permission.READ, Permission.WRITE)),
    ADMIN(EnumSet.allOf(Permission.class));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
      this.permissions = permissions;
    }

    public boolean hasPermission(Permission permission) {
      return permissions.contains(permission);
    }

    public Set<Permission> getPermissions() {
      return permissions;
    }
  }

  /** Represents a user with a role. */
  record User(String name, Role role) {
    public boolean canPerform(Permission permission) {
      return role.hasPermission(permission);
    }
  }

  // ============================================================================
  // File Operation DSL
  // ============================================================================

  /** Sealed interface defining file operations. */
  sealed interface FileOp<A> {

    /** Returns the permission required to execute this operation. */
    Permission requiredPermission();

    /** Returns a human-readable description of this operation. */
    String describe();

    /** Read file contents. Requires READ permission. */
    record Read(Path path) implements FileOp<String> {
      @Override
      public Permission requiredPermission() {
        return Permission.READ;
      }

      @Override
      public String describe() {
        return "READ " + path;
      }
    }

    /** Write content to a file. Requires WRITE permission. */
    record Write(Path path, String content) implements FileOp<Integer> {
      @Override
      public Permission requiredPermission() {
        return Permission.WRITE;
      }

      @Override
      public String describe() {
        return "WRITE " + content.length() + " bytes to " + path;
      }
    }

    /** Delete a file. Requires DELETE permission. */
    record Delete(Path path) implements FileOp<Boolean> {
      @Override
      public Permission requiredPermission() {
        return Permission.DELETE;
      }

      @Override
      public String describe() {
        return "DELETE " + path;
      }
    }
  }

  /** HKT bridge for FileOp. */
  interface FileOpKind<A> extends Kind<FileOpKind.Witness, A> {
    final class Witness implements WitnessArity<TypeArity.Unary> {
      private Witness() {}
    }
  }

  /** Helper for FileOp HKT conversions. */
  enum FileOpHelper {
    FILE_OP;

    record Holder<A>(FileOp<A> op) implements FileOpKind<A> {}

    public <A> Kind<FileOpKind.Witness, A> widen(FileOp<A> op) {
      return new Holder<>(op);
    }

    @SuppressWarnings("unchecked")
    public <A> FileOp<A> narrow(Kind<FileOpKind.Witness, ?> kind) {
      return (FileOp<A>) ((Holder<?>) kind).op();
    }
  }

  // ============================================================================
  // Smart Constructors
  // ============================================================================

  static FreeAp<FileOpKind.Witness, String> readFile(Path path) {
    return FreeAp.lift(FileOpHelper.FILE_OP.widen(new FileOp.Read(path)));
  }

  static FreeAp<FileOpKind.Witness, String> readFile(String path) {
    return readFile(Path.of(path));
  }

  static FreeAp<FileOpKind.Witness, Integer> writeFile(Path path, String content) {
    return FreeAp.lift(FileOpHelper.FILE_OP.widen(new FileOp.Write(path, content)));
  }

  static FreeAp<FileOpKind.Witness, Integer> writeFile(String path, String content) {
    return writeFile(Path.of(path), content);
  }

  static FreeAp<FileOpKind.Witness, Boolean> deleteFile(Path path) {
    return FreeAp.lift(FileOpHelper.FILE_OP.widen(new FileOp.Delete(path)));
  }

  static FreeAp<FileOpKind.Witness, Boolean> deleteFile(String path) {
    return deleteFile(Path.of(path));
  }

  // ============================================================================
  // Permission Checking
  // ============================================================================

  /** Result of permission analysis. */
  sealed interface PermissionCheckResult {
    record Allowed(String summary) implements PermissionCheckResult {}

    record Denied(String summary, List<PermissionViolation> violations)
        implements PermissionCheckResult {}
  }

  /** A single permission violation. */
  record PermissionViolation(FileOp<?> operation, Permission required, Permission highest) {
    public String describe() {
      String highestDesc = highest == null ? "NONE" : highest.toString();
      return String.format(
          "%s requires %s permission (user has up to %s)", operation.describe(), required, highestDesc);
    }
  }

  /**
   * Checks if a user has permission to execute all operations in a program.
   *
   * <p>This method analyses the program WITHOUT executing it, returning detailed information about
   * any permission violations.
   */
  static PermissionCheckResult checkPermissions(FreeAp<FileOpKind.Witness, ?> program, User user) {

    // Collect all operations from the program
    List<Kind<FileOpKind.Witness, ?>> operations = FreeApAnalyzer.collectOperations(program);

    if (operations.isEmpty()) {
      return new PermissionCheckResult.Allowed("Program contains no file operations");
    }

    // Check each operation for permission violations
    List<PermissionViolation> violations =
        operations.stream()
            .map(FileOpHelper.FILE_OP::narrow)
            .filter(op -> !user.canPerform(op.requiredPermission()))
            .map(
                op -> {
                  Permission highest = getHighestPermission(user);
                  return new PermissionViolation(op, op.requiredPermission(), highest);
                })
            .toList();

    if (violations.isEmpty()) {
      int opCount = operations.size();
      return new PermissionCheckResult.Allowed(
          String.format(
              "All %d operations are permitted for user '%s' (%s)",
              opCount, user.name(), user.role()));
    } else {
      return new PermissionCheckResult.Denied(
          String.format(
              "%d of %d operations require higher permissions",
              violations.size(), operations.size()),
          violations);
    }
  }

  private static Permission getHighestPermission(User user) {
    if (user.canPerform(Permission.DELETE)) return Permission.DELETE;
    if (user.canPerform(Permission.WRITE)) return Permission.WRITE;
    if (user.canPerform(Permission.READ)) return Permission.READ;
    return null;
  }

  // ============================================================================
  // Example Programs
  // ============================================================================

  /** A read-only program: suitable for GUEST users. */
  static FreeAp<FileOpKind.Witness, String> readOnlyProgram() {
    return readFile("/data/config.json")
        .map2(readFile("/data/settings.json"), (config, settings) -> config + "\n" + settings);
  }

  /** A program that reads and writes: suitable for USER level. */
  static FreeAp<FileOpKind.Witness, String> readWriteProgram() {
    return readFile("/data/input.txt")
        .map2(
            writeFile("/data/output.txt", "processed content"),
            (input, bytesWritten) -> "Processed " + input.length() + " chars");
  }

  /** A program with delete operations: requires ADMIN. */
  static FreeAp<FileOpKind.Witness, String> adminProgram() {
    FreeApApplicative<FileOpKind.Witness> app = FreeApApplicative.instance();

    return FreeApKindHelper.FREE_AP.narrow(
        app.map4(
            FreeApKindHelper.FREE_AP.widen(readFile("/data/old_data.txt")),
            FreeApKindHelper.FREE_AP.widen(writeFile("/data/archive.txt", "archived")),
            FreeApKindHelper.FREE_AP.widen(deleteFile("/data/old_data.txt")),
            FreeApKindHelper.FREE_AP.widen(writeFile("/data/log.txt", "cleanup complete")),
            (content, archived, deleted, logged) ->
                deleted ? "Successfully archived and cleaned up" : "Cleanup failed"));
  }

  // ============================================================================
  // Demonstrations
  // ============================================================================

  /** Demonstrates permission checking for different user roles. */
  static void demonstratePermissionChecking() {
    System.out.println("=== Permission Checking Demo ===\n");

    User guest = new User("guest_user", Role.GUEST);
    User regularUser = new User("john_doe", Role.USER);
    User admin = new User("admin", Role.ADMIN);

    FreeAp<FileOpKind.Witness, String> program = adminProgram();

    System.out.println("Program operations:");
    FreeApAnalyzer.collectOperations(program).stream()
        .map(FileOpHelper.FILE_OP::narrow)
        .forEach(op -> System.out.println("  - " + op.describe()));
    System.out.println();

    // Check permissions for each user
    for (User user : List.of(guest, regularUser, admin)) {
      System.out.println("Checking permissions for: " + user.name() + " (" + user.role() + ")");
      PermissionCheckResult result = checkPermissions(program, user);

      switch (result) {
        case PermissionCheckResult.Allowed allowed -> {
          System.out.println("  [ALLOWED] " + allowed.summary());
        }
        case PermissionCheckResult.Denied denied -> {
          System.out.println("  [DENIED] " + denied.summary());
          for (PermissionViolation v : denied.violations()) {
            System.out.println("    - " + v.describe());
          }
        }
      }
      System.out.println();
    }
  }

  /** Demonstrates the full workflow with permission-based execution. */
  static void demonstrateSecureExecution() {
    System.out.println("=== Secure Execution Workflow ===\n");

    User user = new User("jane_doe", Role.USER);
    FreeAp<FileOpKind.Witness, String> program = readWriteProgram();

    System.out.println("User: " + user.name() + " (" + user.role() + ")");
    System.out.println("Program: Read input file, write output file\n");

    // Step 1: Analyse
    System.out.println("Step 1: Static Analysis");
    int opCount = FreeApAnalyzer.countOperations(program);
    Map<Class<?>, Integer> groups =
        FreeApAnalyzer.groupByType(program, FileOpHelper.FILE_OP::narrow);

    System.out.println("  Operations: " + opCount);
    groups.forEach((cls, count) -> System.out.println("    " + cls.getSimpleName() + ": " + count));

    // Step 2: Permission Check
    System.out.println("\nStep 2: Permission Check");
    PermissionCheckResult permResult = checkPermissions(program, user);

    switch (permResult) {
      case PermissionCheckResult.Allowed allowed -> {
        System.out.println("  [ALLOWED] " + allowed.summary());
      }
      case PermissionCheckResult.Denied denied -> {
        System.out.println("  [DENIED] " + denied.summary());
        System.out.println("  Execution blocked. Exiting.\n");
        return;
      }
    }

    // Step 3: Execute
    System.out.println("\nStep 3: Execute Program");
    Natural<FileOpKind.Witness, IdKind.Witness> interpreter = createMockInterpreter();
    Kind<IdKind.Witness, String> result = program.foldMap(interpreter, IdMonad.instance());
    String output = ID.narrow(result).value();

    System.out.println("  Result: " + output);
    System.out.println("\nExecution completed successfully!\n");
  }

  /** Demonstrates analysing permission requirements of a program. */
  static void demonstratePermissionAnalysis() {
    System.out.println("=== Permission Requirements Analysis ===\n");

    FreeAp<FileOpKind.Witness, String> program = adminProgram();

    // Collect all required permissions
    Set<FileOp<?>> operations =
        SelectiveAnalyzer.collectPossibleEffects(program, FileOpHelper.FILE_OP::narrow);

    Set<Permission> requiredPermissions =
        operations.stream()
            .map(FileOp::requiredPermission)
            .collect(java.util.stream.Collectors.toSet());

    System.out.println("Program requires these permissions:");
    requiredPermissions.forEach(p -> System.out.println("  - " + p));

    // Determine minimum role required
    Role minimumRole;
    if (requiredPermissions.contains(Permission.DELETE)) {
      minimumRole = Role.ADMIN;
    } else if (requiredPermissions.contains(Permission.WRITE)) {
      minimumRole = Role.USER;
    } else {
      minimumRole = Role.GUEST;
    }

    System.out.println("\nMinimum role required: " + minimumRole);
    System.out.println();
  }

  // ============================================================================
  // Mock Interpreter
  // ============================================================================

  /** Mock file system for demonstration. */
  private static final Map<String, String> MOCK_FILE_SYSTEM = new HashMap<>();

  static {
    MOCK_FILE_SYSTEM.put("/data/config.json", "{\"version\": 1}");
    MOCK_FILE_SYSTEM.put("/data/settings.json", "{\"theme\": \"dark\"}");
    MOCK_FILE_SYSTEM.put("/data/input.txt", "Hello, World!");
    MOCK_FILE_SYSTEM.put("/data/old_data.txt", "Old data to be archived");
  }

  @SuppressWarnings("unchecked")
  private static Natural<FileOpKind.Witness, IdKind.Witness> createMockInterpreter() {
    return new Natural<>() {
      @Override
      public <A> Kind<IdKind.Witness, A> apply(Kind<FileOpKind.Witness, A> fa) {
        FileOp<?> op = FileOpHelper.FILE_OP.narrow(fa);
        Object result =
            switch (op) {
              case FileOp.Read read -> {
                String content = MOCK_FILE_SYSTEM.getOrDefault(read.path().toString(), "");
                System.out.println(
                    "    [MOCK] Reading " + read.path() + " (" + content.length() + " bytes)");
                yield content;
              }
              case FileOp.Write write -> {
                MOCK_FILE_SYSTEM.put(write.path().toString(), write.content());
                System.out.println(
                    "    [MOCK] Writing " + write.content().length() + " bytes to " + write.path());
                yield write.content().length();
              }
              case FileOp.Delete delete -> {
                boolean existed = MOCK_FILE_SYSTEM.remove(delete.path().toString()) != null;
                System.out.println(
                    "    [MOCK] Deleting " + delete.path() + " (existed: " + existed + ")");
                yield existed;
              }
            };
        return ID.widen(Id.of((A) result));
      }
    };
  }

  // ============================================================================
  // Main
  // ============================================================================

  public static void main(String[] args) {
    System.out.println("============================================");
    System.out.println("  Permission Checking with Free Applicative");
    System.out.println("============================================\n");

    demonstratePermissionChecking();
    demonstrateSecureExecution();
    demonstratePermissionAnalysis();

    System.out.println("============================================");
    System.out.println("  Summary");
    System.out.println("============================================");
    System.out.println("This example demonstrated:");
    System.out.println("  1. File operation DSL with Read/Write/Delete");
    System.out.println("  2. Role-based permission model (GUEST/USER/ADMIN)");
    System.out.println("  3. Static permission analysis BEFORE execution");
    System.out.println("  4. Detailed violation reporting");
    System.out.println("  5. Safe execution only after permission check");
    System.out.println();
    System.out.println("The key insight: we analysed what operations the");
    System.out.println("program would perform WITHOUT executing them.");
    System.out.println("============================================\n");
  }
}
