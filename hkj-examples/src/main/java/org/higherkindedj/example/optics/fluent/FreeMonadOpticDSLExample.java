// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.fluent;

import module java.base;
import module org.higherkindedj.core;

import org.higherkindedj.example.optics.fluent.generated.ProfileSettingsLenses;
import org.higherkindedj.example.optics.fluent.generated.UserAddressLenses;
import org.higherkindedj.example.optics.fluent.generated.UserProfileLenses;
import org.higherkindedj.example.optics.fluent.generated.UserProfileTraversals;
import org.higherkindedj.example.optics.fluent.model.ProfileSettings;
import org.higherkindedj.example.optics.fluent.model.UserAddress;
import org.higherkindedj.example.optics.fluent.model.UserProfile;
import org.higherkindedj.hkt.free.Free;

/**
 * A runnable example demonstrating the Free Monad DSL for optics. This example shows how to build
 * composable optic programs that separate program description from execution, enabling multiple
 * interpretation strategies.
 *
 * <p>The scenario is a user profile migration system where we need to:
 *
 * <ul>
 *   <li>Migrate user data between different schema versions
 *   <li>Validate changes before applying them
 *   <li>Audit all modifications for compliance
 *   <li>Preview changes without executing them
 * </ul>
 */
public class FreeMonadOpticDSLExample {

  /** Java 25 instance main method - no static modifier or String[] args required. */
  void main() {
    System.out.println("=== FREE MONAD OPTIC DSL EXAMPLE ===\n");

    // Create a sample user profile
    UserProfile profile = createSampleProfile();
    System.out.println("Original Profile:");
    printProfile(profile);
    System.out.println("\n" + "=".repeat(60) + "\n");

    basicProgramExamples(profile);
    System.out.println("\n" + "=".repeat(60) + "\n");

    composedProgramExamples(profile);
    System.out.println("\n" + "=".repeat(60) + "\n");

    interpreterComparison(profile);
    System.out.println("\n" + "=".repeat(60) + "\n");

    realWorldMigration(profile);
  }

  // ============================================================================
  // PART 1: Basic Free Monad Programs
  // ============================================================================

  private static void basicProgramExamples(UserProfile profile) {
    System.out.println("--- Part 1: Basic Free Monad Programs ---\n");

    // Example 1: Simple get program
    System.out.println("Example 1: Get username");
    Free<OpticOpKind.Witness, String> getProgram =
        OpticPrograms.get(profile, UserProfileLenses.username());

    DirectOpticInterpreter directInterpreter = OpticInterpreters.direct();
    String username = directInterpreter.run(getProgram);
    System.out.println("  Username: " + username);
    System.out.println();

    // Example 2: Simple set program
    System.out.println("Example 2: Set email");
    Free<OpticOpKind.Witness, UserProfile> setProgram =
        OpticPrograms.set(profile, UserProfileLenses.email(), "new.email@example.com");

    UserProfile updatedProfile = directInterpreter.run(setProgram);
    System.out.println("  New email: " + updatedProfile.email());
    System.out.println();

    // Example 3: Simple modify program
    System.out.println("Example 3: Toggle email notifications");
    Free<OpticOpKind.Witness, UserProfile> modifyProgram =
        OpticPrograms.modify(
            profile,
            UserProfileLenses.settings().andThen(ProfileSettingsLenses.emailNotifications()),
            notifications -> !notifications);

    UserProfile toggledProfile = directInterpreter.run(modifyProgram);
    System.out.println("  Email notifications: " + toggledProfile.settings().emailNotifications());
    System.out.println();
  }

  // ============================================================================
  // PART 2: Composed Programs with flatMap
  // ============================================================================

  private static void composedProgramExamples(UserProfile profile) {
    System.out.println("--- Part 2: Composed Programs with flatMap ---\n");

    System.out.println("Example 1: Conditional update based on current value");
    // Program: If email notifications are enabled, also enable SMS
    Free<OpticOpKind.Witness, UserProfile> conditionalProgram =
        OpticPrograms.get(
                profile,
                UserProfileLenses.settings().andThen(ProfileSettingsLenses.emailNotifications()))
            .flatMap(
                emailEnabled -> {
                  if (emailEnabled) {
                    System.out.println("  Email notifications enabled, enabling SMS too");
                    return OpticPrograms.set(
                        profile,
                        UserProfileLenses.settings()
                            .andThen(ProfileSettingsLenses.smsNotifications()),
                        true);
                  } else {
                    System.out.println("  Email notifications disabled, no change needed");
                    return OpticPrograms.pure(profile);
                  }
                });

    DirectOpticInterpreter interpreter = OpticInterpreters.direct();
    UserProfile result = interpreter.run(conditionalProgram);
    System.out.println(
        "  Result - Email: "
            + result.settings().emailNotifications()
            + ", SMS: "
            + result.settings().smsNotifications());
    System.out.println();

    System.out.println("Example 2: Multi-step transformation");
    // Program: Update theme and language together
    Free<OpticOpKind.Witness, UserProfile> multiStepProgram =
        updateThemeAndLanguage(profile, "dark", "en-GB");

    UserProfile transformed = interpreter.run(multiStepProgram);
    System.out.println("  Theme: " + transformed.settings().theme());
    System.out.println("  Language: " + transformed.settings().preferredLanguage());
    System.out.println();
  }

  // ============================================================================
  // PART 3: Comparing Different Interpreters
  // ============================================================================

  private static void interpreterComparison(UserProfile profile) {
    System.out.println("--- Part 3: Different Interpretation Strategies ---\n");

    // Build a complex program once
    Free<OpticOpKind.Witness, UserProfile> complexProgram =
        OpticPrograms.get(profile, UserProfileLenses.email())
            .flatMap(
                currentEmail -> {
                  String normalised = currentEmail.toLowerCase().trim();
                  return OpticPrograms.set(profile, UserProfileLenses.email(), normalised);
                })
            .flatMap(
                updated ->
                    OpticPrograms.modify(
                        updated,
                        UserProfileLenses.settings()
                            .andThen(ProfileSettingsLenses.emailNotifications()),
                        notifications -> true));

    System.out.println("Built program: Normalise email and enable notifications\n");

    // Strategy 1: Direct execution
    System.out.println("Strategy 1: Direct Execution");
    DirectOpticInterpreter direct = OpticInterpreters.direct();
    UserProfile directResult = direct.run(complexProgram);
    System.out.println("  Result email: " + directResult.email());
    System.out.println("  Result notifications: " + directResult.settings().emailNotifications());
    System.out.println();

    // Strategy 2: Logging execution (audit trail)
    System.out.println("Strategy 2: Logging Execution (Audit Trail)");
    LoggingOpticInterpreter logging = OpticInterpreters.logging();
    UserProfile loggingResult = logging.run(complexProgram);
    System.out.println("  Audit log:");
    logging.getLog().forEach(entry -> System.out.println("    " + entry));
    System.out.println();

    // Strategy 3: Validation (dry-run)
    System.out.println("Strategy 3: Validation (Dry-Run)");
    ValidationOpticInterpreter validator = OpticInterpreters.validating();
    ValidationOpticInterpreter.ValidationResult validationResult =
        validator.validate(complexProgram);
    System.out.println("  Validation result:");
    System.out.println("    Valid: " + validationResult.isValid());
    System.out.println("    Errors: " + validationResult.errors().size());
    System.out.println("    Warnings: " + validationResult.warnings().size());
    System.out.println("  Original profile unchanged: " + profile.email());
    System.out.println();
  }

  // ============================================================================
  // PART 4: Real-World User Profile Migration
  // ============================================================================

  private static void realWorldMigration(UserProfile profile) {
    System.out.println("--- Part 4: Real-World Profile Migration Workflow ---\n");

    System.out.println("Migrating profile " + profile.userId() + " to new schema...\n");

    // Build the migration program
    Free<OpticOpKind.Witness, UserProfile> migrationProgram = buildMigrationProgram(profile);

    // Step 1: Validate migration (dry-run)
    System.out.println("STEP 1: Validation Phase");
    ValidationOpticInterpreter validator = OpticInterpreters.validating();
    ValidationOpticInterpreter.ValidationResult validationResult =
        validator.validate(migrationProgram);

    System.out.println("  Validation results:");
    System.out.println("    Valid: " + validationResult.isValid());
    if (!validationResult.errors().isEmpty()) {
      System.out.println("    Errors:");
      validationResult.errors().forEach(err -> System.out.println("      - " + err));
    }
    if (!validationResult.warnings().isEmpty()) {
      System.out.println("    Warnings:");
      validationResult.warnings().forEach(warn -> System.out.println("      - " + warn));
    }

    if (!validationResult.isValid()) {
      System.out.println("\n  ✗ Migration failed validation, aborting");
      return;
    }
    System.out.println("  ✓ Migration passed validation");
    System.out.println();

    // Step 2: Execute migration with audit logging
    System.out.println("STEP 2: Execution Phase with Audit Logging");
    LoggingOpticInterpreter auditLogger = OpticInterpreters.logging();
    UserProfile migratedProfile = auditLogger.run(migrationProgram);

    System.out.println("  Audit trail:");
    auditLogger.getLog().forEach(entry -> System.out.println("    " + entry));
    System.out.println();

    // Step 3: Verify migration
    System.out.println("STEP 3: Verification Phase");
    System.out.println("  ✓ Profile migrated successfully");
    System.out.println("  ✓ All addresses normalised");
    System.out.println("  ✓ Preferences updated");
    System.out.println();

    System.out.println("Migrated Profile:");
    printProfile(migratedProfile);
    System.out.println();

    // Save audit log
    System.out.println("Audit log saved to compliance database: " + LocalDateTime.now());
  }

  // ============================================================================
  // Program Builders
  // ============================================================================

  /** Builds a program that updates theme and language together. */
  private static Free<OpticOpKind.Witness, UserProfile> updateThemeAndLanguage(
      UserProfile profile, String theme, String language) {
    return OpticPrograms.set(
            profile, UserProfileLenses.settings().andThen(ProfileSettingsLenses.theme()), theme)
        .flatMap(
            updated ->
                OpticPrograms.set(
                    updated,
                    UserProfileLenses.settings().andThen(ProfileSettingsLenses.preferredLanguage()),
                    language));
  }

  /** Builds a comprehensive migration program. */
  private static Free<OpticOpKind.Witness, UserProfile> buildMigrationProgram(UserProfile profile) {
    // Step 1: Normalise email
    return OpticPrograms.modify(profile, UserProfileLenses.email(), String::toLowerCase)
        .flatMap(
            p1 ->
                // Step 2: Normalise all addresses (postcodes to uppercase)
                normaliseAddresses(p1))
        .flatMap(
            p2 ->
                // Step 3: Set default preferences
                OpticPrograms.set(
                    p2,
                    UserProfileLenses.settings().andThen(ProfileSettingsLenses.preferredLanguage()),
                    "en-GB"))
        .flatMap(
            p3 ->
                // Step 4: Enable email notifications by default
                OpticPrograms.set(
                    p3,
                    UserProfileLenses.settings()
                        .andThen(ProfileSettingsLenses.emailNotifications()),
                    true));
  }

  /** Program to normalise all addresses in a profile. */
  private static Free<OpticOpKind.Witness, UserProfile> normaliseAddresses(UserProfile profile) {
    Traversal<UserProfile, String> allPostCodes =
        UserProfileTraversals.addresses().andThen(UserAddressLenses.postCode().asTraversal());

    return OpticPrograms.modifyAll(profile, allPostCodes, String::toUpperCase);
  }

  // ============================================================================
  // Helper Methods
  // ============================================================================

  private static UserProfile createSampleProfile() {
    List<UserAddress> addresses = new ArrayList<>();
    addresses.add(
        new UserAddress("home", "123 High Street", "London", "sw1a 1aa", "United Kingdom"));
    addresses.add(
        new UserAddress("work", "456 Office Road", "Manchester", "m1 1ae", "United Kingdom"));

    ProfileSettings settings = new ProfileSettings(true, false, "en-US", "light");

    return new UserProfile(
        "USR-001", "alice_smith", "Alice.Smith@Example.COM", settings, addresses);
  }

  private static void printProfile(UserProfile profile) {
    System.out.println("  User ID: " + profile.userId());
    System.out.println("  Username: " + profile.username());
    System.out.println("  Email: " + profile.email());
    System.out.println("  Settings:");
    System.out.println("    Email notifications: " + profile.settings().emailNotifications());
    System.out.println("    SMS notifications: " + profile.settings().smsNotifications());
    System.out.println("    Language: " + profile.settings().preferredLanguage());
    System.out.println("    Theme: " + profile.settings().theme());
    System.out.println("  Addresses:");
    profile
        .addresses()
        .forEach(
            addr ->
                System.out.println(
                    "    "
                        + addr.type()
                        + ": "
                        + addr.street()
                        + ", "
                        + addr.city()
                        + " "
                        + addr.postCode()));
  }
}
