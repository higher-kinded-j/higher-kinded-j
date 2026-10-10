// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.fluent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.higherkindedj.example.book.optics.cast.Address;
import org.higherkindedj.example.book.optics.cast.AddressLenses;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.CustomerLenses;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.EmailAddressLenses;
import org.higherkindedj.example.optics.fluent.generated.CustomerAccountLenses;
import org.higherkindedj.example.optics.fluent.generated.CustomerAccountTraversals;
import org.higherkindedj.example.optics.fluent.generated.ProfileSettingsLenses;
import org.higherkindedj.example.optics.fluent.model.CustomerAccount;
import org.higherkindedj.example.optics.fluent.model.ProfileSettings;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.free.DirectOpticInterpreter;
import org.higherkindedj.optics.free.LoggingOpticInterpreter;
import org.higherkindedj.optics.free.OpticInterpreters;
import org.higherkindedj.optics.free.OpticOpKind;
import org.higherkindedj.optics.free.OpticPrograms;
import org.higherkindedj.optics.free.ValidationOpticInterpreter;

/**
 * A runnable example demonstrating the Free Monad DSL for optics. This example shows how to build
 * composable optic programs that separate program description from execution, enabling multiple
 * interpretation strategies.
 *
 * <p>The scenario is a customer account migration system where we need to:
 *
 * <ul>
 *   <li>Migrate account data between different schema versions
 *   <li>Validate changes before applying them
 *   <li>Audit all modifications for compliance
 *   <li>Preview changes without executing them
 * </ul>
 */
public class FreeMonadOpticDSLExample {

  // The account holder's name, on the chapter's Customer
  private static final Lens<CustomerAccount, String> NAME =
      CustomerAccountLenses.customer().andThen(CustomerLenses.name());

  // The account holder's email, inside the Customer's EmailAddress
  private static final Lens<CustomerAccount, String> EMAIL =
      CustomerAccountLenses.customer()
          .andThen(CustomerLenses.email())
          .andThen(EmailAddressLenses.value());

  public static void main(String[] args) {
    System.out.println("=== FREE MONAD OPTIC DSL EXAMPLE ===\n");

    // Create a sample customer account
    CustomerAccount account = createSampleAccount();
    System.out.println("Original Account:");
    printAccount(account);
    System.out.println("\n" + "=".repeat(60) + "\n");

    basicProgramExamples(account);
    System.out.println("\n" + "=".repeat(60) + "\n");

    composedProgramExamples(account);
    System.out.println("\n" + "=".repeat(60) + "\n");

    interpreterComparison(account);
    System.out.println("\n" + "=".repeat(60) + "\n");

    realWorldMigration(account);
  }

  // ============================================================================
  // PART 1: Basic Free Monad Programs
  // ============================================================================

  private static void basicProgramExamples(CustomerAccount account) {
    System.out.println("--- Part 1: Basic Free Monad Programs ---\n");

    // Example 1: Simple get program
    System.out.println("Example 1: Get name");
    Free<OpticOpKind.Witness, String> getProgram = OpticPrograms.get(account, NAME);

    DirectOpticInterpreter directInterpreter = OpticInterpreters.direct();
    String name = directInterpreter.run(getProgram);
    System.out.println("  Name: " + name);
    System.out.println();

    // Example 2: Simple set program
    System.out.println("Example 2: Set email");
    Free<OpticOpKind.Witness, CustomerAccount> setProgram =
        OpticPrograms.set(account, EMAIL, "new.email@example.com");

    CustomerAccount updatedAccount = directInterpreter.run(setProgram);
    System.out.println("  New email: " + EMAIL.get(updatedAccount));
    System.out.println();

    // Example 3: Simple modify program
    System.out.println("Example 3: Toggle email notifications");
    Free<OpticOpKind.Witness, CustomerAccount> modifyProgram =
        OpticPrograms.modify(
            account,
            CustomerAccountLenses.settings().andThen(ProfileSettingsLenses.emailNotifications()),
            notifications -> !notifications);

    CustomerAccount toggledAccount = directInterpreter.run(modifyProgram);
    System.out.println("  Email notifications: " + toggledAccount.settings().emailNotifications());
    System.out.println();
  }

  // ============================================================================
  // PART 2: Composed Programs with flatMap
  // ============================================================================

  private static void composedProgramExamples(CustomerAccount account) {
    System.out.println("--- Part 2: Composed Programs with flatMap ---\n");

    System.out.println("Example 1: Conditional update based on current value");
    // Program: If email notifications are enabled, also enable SMS
    Free<OpticOpKind.Witness, CustomerAccount> conditionalProgram =
        OpticPrograms.get(
                account,
                CustomerAccountLenses.settings()
                    .andThen(ProfileSettingsLenses.emailNotifications()))
            .flatMap(
                emailEnabled -> {
                  if (emailEnabled) {
                    System.out.println("  Email notifications enabled, enabling SMS too");
                    return OpticPrograms.set(
                        account,
                        CustomerAccountLenses.settings()
                            .andThen(ProfileSettingsLenses.smsNotifications()),
                        true);
                  } else {
                    System.out.println("  Email notifications disabled, no change needed");
                    return OpticPrograms.pure(account);
                  }
                });

    DirectOpticInterpreter interpreter = OpticInterpreters.direct();
    CustomerAccount result = interpreter.run(conditionalProgram);
    System.out.println(
        "  Result - Email: "
            + result.settings().emailNotifications()
            + ", SMS: "
            + result.settings().smsNotifications());
    System.out.println();

    System.out.println("Example 2: Multi-step transformation");
    // Program: Update theme and language together
    Free<OpticOpKind.Witness, CustomerAccount> multiStepProgram =
        updateThemeAndLanguage(account, "dark", "en-GB");

    CustomerAccount transformed = interpreter.run(multiStepProgram);
    System.out.println("  Theme: " + transformed.settings().theme());
    System.out.println("  Language: " + transformed.settings().preferredLanguage());
    System.out.println();
  }

  // ============================================================================
  // PART 3: Comparing Different Interpreters
  // ============================================================================

  private static void interpreterComparison(CustomerAccount account) {
    System.out.println("--- Part 3: Different Interpretation Strategies ---\n");

    // Build a complex program once
    Free<OpticOpKind.Witness, CustomerAccount> complexProgram =
        OpticPrograms.get(account, EMAIL)
            .flatMap(
                currentEmail -> {
                  String normalised = currentEmail.toLowerCase().trim();
                  return OpticPrograms.set(account, EMAIL, normalised);
                })
            .flatMap(
                updated ->
                    OpticPrograms.modify(
                        updated,
                        CustomerAccountLenses.settings()
                            .andThen(ProfileSettingsLenses.emailNotifications()),
                        notifications -> true));

    System.out.println("Built program: Normalise email and enable notifications\n");

    // Strategy 1: Direct execution
    System.out.println("Strategy 1: Direct Execution");
    DirectOpticInterpreter direct = OpticInterpreters.direct();
    CustomerAccount directResult = direct.run(complexProgram);
    System.out.println("  Result email: " + EMAIL.get(directResult));
    System.out.println("  Result notifications: " + directResult.settings().emailNotifications());
    System.out.println();

    // Strategy 2: Logging execution (audit trail)
    System.out.println("Strategy 2: Logging Execution (Audit Trail)");
    LoggingOpticInterpreter logging = OpticInterpreters.logging();
    CustomerAccount loggingResult = logging.run(complexProgram);
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
    System.out.println("  Original account unchanged: " + EMAIL.get(account));
    System.out.println();
  }

  // ============================================================================
  // PART 4: Real-World Customer Account Migration
  // ============================================================================

  private static void realWorldMigration(CustomerAccount account) {
    System.out.println("--- Part 4: Real-World Account Migration Workflow ---\n");

    System.out.println("Migrating account " + account.accountId() + " to new schema...\n");

    // Build the migration program
    Free<OpticOpKind.Witness, CustomerAccount> migrationProgram = buildMigrationProgram(account);

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
    CustomerAccount migratedAccount = auditLogger.run(migrationProgram);

    System.out.println("  Audit trail:");
    auditLogger.getLog().forEach(entry -> System.out.println("    " + entry));
    System.out.println();

    // Step 3: Verify migration
    System.out.println("STEP 3: Verification Phase");
    System.out.println("  ✓ Account migrated successfully");
    System.out.println("  ✓ All addresses normalised");
    System.out.println("  ✓ Preferences updated");
    System.out.println();

    System.out.println("Migrated Account:");
    printAccount(migratedAccount);
    System.out.println();

    // Save audit log
    System.out.println("Audit log saved to compliance database: " + LocalDateTime.now());
  }

  // ============================================================================
  // Program Builders
  // ============================================================================

  /** Builds a program that updates theme and language together. */
  private static Free<OpticOpKind.Witness, CustomerAccount> updateThemeAndLanguage(
      CustomerAccount account, String theme, String language) {
    return OpticPrograms.set(
            account, CustomerAccountLenses.settings().andThen(ProfileSettingsLenses.theme()), theme)
        .flatMap(
            updated ->
                OpticPrograms.set(
                    updated,
                    CustomerAccountLenses.settings()
                        .andThen(ProfileSettingsLenses.preferredLanguage()),
                    language));
  }

  /** Builds a comprehensive migration program. */
  private static Free<OpticOpKind.Witness, CustomerAccount> buildMigrationProgram(
      CustomerAccount account) {
    // Step 1: Normalise email
    return OpticPrograms.modify(account, EMAIL, String::toLowerCase)
        .flatMap(
            p1 ->
                // Step 2: Normalise all addresses (postcodes to uppercase)
                normaliseAddresses(p1))
        .flatMap(
            p2 ->
                // Step 3: Set default preferences
                OpticPrograms.set(
                    p2,
                    CustomerAccountLenses.settings()
                        .andThen(ProfileSettingsLenses.preferredLanguage()),
                    "en-GB"))
        .flatMap(
            p3 ->
                // Step 4: Enable email notifications by default
                OpticPrograms.set(
                    p3,
                    CustomerAccountLenses.settings()
                        .andThen(ProfileSettingsLenses.emailNotifications()),
                    true));
  }

  /** Program to normalise all addresses in an account. */
  private static Free<OpticOpKind.Witness, CustomerAccount> normaliseAddresses(
      CustomerAccount account) {
    Traversal<CustomerAccount, String> allPostcodes =
        CustomerAccountTraversals.addresses().andThen(AddressLenses.postcode().asTraversal());

    return OpticPrograms.modifyAll(account, allPostcodes, String::toUpperCase);
  }

  // ============================================================================
  // Helper Methods
  // ============================================================================

  private static CustomerAccount createSampleAccount() {
    List<Address> addresses = new ArrayList<>();
    addresses.add(new Address("123 High Street", "London", "sw1a 1aa"));
    addresses.add(new Address("456 Office Road", "Manchester", "m1 1ae"));

    ProfileSettings settings = new ProfileSettings(true, false, "en-US", "light");

    return new CustomerAccount(
        "ACC-001",
        new Customer("Alice Smith", new EmailAddress("Alice.Smith@Example.COM")),
        settings,
        addresses);
  }

  private static void printAccount(CustomerAccount account) {
    System.out.println("  Account ID: " + account.accountId());
    System.out.println("  Name: " + NAME.get(account));
    System.out.println("  Email: " + EMAIL.get(account));
    System.out.println("  Settings:");
    System.out.println("    Email notifications: " + account.settings().emailNotifications());
    System.out.println("    SMS notifications: " + account.settings().smsNotifications());
    System.out.println("    Language: " + account.settings().preferredLanguage());
    System.out.println("    Theme: " + account.settings().theme());
    System.out.println("  Addresses:");
    account
        .addresses()
        .forEach(
            addr ->
                System.out.println(
                    "    " + addr.street() + ", " + addr.city() + " " + addr.postcode()));
  }
}
