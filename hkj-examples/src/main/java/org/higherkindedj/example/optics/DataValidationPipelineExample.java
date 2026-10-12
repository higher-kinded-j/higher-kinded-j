// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;

/**
 * A runnable example demonstrating ETL data validation and transformation pipelines using prisms.
 *
 * <p>This example showcases:
 *
 * <ul>
 *   <li>Type-safe validation result handling with sealed interfaces
 *   <li>Validation rule composition using {@code orElse()} for fallback strategies
 *   <li>Conditional transformations with {@code modifyWhen()}
 *   <li>Data cleansing and normalisation using {@code modify()}
 *   <li>Pipeline error reporting and recovery patterns
 * </ul>
 */
public class DataValidationPipelineExample {

  // Validation result variants
  @GeneratePrisms
  public sealed interface ImportVerdict permits Valid, Invalid, Warning, Skipped {}

  @GenerateLenses
  public record Valid(CustomerImportRow value) implements ImportVerdict {}

  @GenerateLenses
  public record Invalid(CustomerImportRow originalValue, List<String> errors)
      implements ImportVerdict {}

  @GenerateLenses
  public record Warning(CustomerImportRow value, List<String> warnings) implements ImportVerdict {}

  @GenerateLenses
  public record Skipped(CustomerImportRow value, String reason) implements ImportVerdict {}

  // One row of a customer import file, every field still the string the file held
  public record CustomerImportRow(
      String id, String name, String email, String dateOfBirth, String status) {}

  // A row the import loads: the chapter's Customer, with what the import keeps beside it
  public record ImportedCustomer(
      String id, Customer customer, LocalDate dateOfBirth, String status) {}

  // Prisms for validation results
  private static final Prism<ImportVerdict, Valid> VALID = ImportVerdictPrisms.valid();
  private static final Prism<ImportVerdict, Invalid> INVALID = ImportVerdictPrisms.invalid();
  private static final Prism<ImportVerdict, Warning> WARNING = ImportVerdictPrisms.warning();
  private static final Prism<ImportVerdict, Skipped> SKIPPED = ImportVerdictPrisms.skipped();

  public static void main(String[] args) {
    System.out.println("=== Data Validation Pipeline with Prisms ===\n");

    demonstrateBasicValidation();
    demonstrateDataCleansing();
    demonstrateValidationPipeline();
    demonstrateErrorReporting();
    demonstrateBatchValidation();
  }

  private static void demonstrateBasicValidation() {
    System.out.println("--- Basic Validation ---");

    CustomerImportRow validCustomer =
        new CustomerImportRow("C001", "Alice Smith", "alice@example.com", "1990-05-15", "active");
    CustomerImportRow invalidCustomer =
        new CustomerImportRow("C002", "", "not-an-email", "invalid-date", "active");

    ImportVerdict result1 = validateCustomer(validCustomer);
    ImportVerdict result2 = validateCustomer(invalidCustomer);

    System.out.println("Valid customer is valid: " + VALID.matches(result1));
    System.out.println("Invalid customer is invalid: " + INVALID.matches(result2));

    // Extract validation errors
    List<String> errors = INVALID.mapOptional(Invalid::errors, result2).orElse(List.of());

    System.out.println("Validation errors: " + errors);

    System.out.println();
  }

  private static void demonstrateDataCleansing() {
    System.out.println("--- Data Cleansing with Conditional Modification ---");

    CustomerImportRow messyCustomer =
        new CustomerImportRow(
            " c003 ", "  bob jones  ", " BOB@EXAMPLE.COM ", "1985-03-20", " ACTIVE ");

    ImportVerdict result = new Valid(messyCustomer);

    // Clean up whitespace and normalise case
    ImportVerdict cleaned =
        VALID.modify(
            valid ->
                new Valid(
                    new CustomerImportRow(
                        valid.value().id().trim().toLowerCase(),
                        normaliseName(valid.value().name()),
                        valid.value().email().trim().toLowerCase(),
                        valid.value().dateOfBirth().trim(),
                        valid.value().status().trim().toLowerCase())),
            result);

    System.out.println("Original: " + messyCustomer);
    System.out.println(
        "Cleaned:  " + VALID.mapOptional(Valid::value, cleaned).orElse(messyCustomer));

    // Conditionally fix common email typos
    CustomerImportRow typoCustomer =
        new CustomerImportRow("C004", "Charlie", "charlie@gmial.com", "1992-07-10", "active");

    ImportVerdict typoResult = new Valid(typoCustomer);

    ImportVerdict fixed =
        VALID.modifyWhen(
            valid -> valid.value().email().contains("@gmial."),
            valid ->
                new Valid(
                    new CustomerImportRow(
                        valid.value().id(),
                        valid.value().name(),
                        valid.value().email().replace("@gmial.", "@gmail."),
                        valid.value().dateOfBirth(),
                        valid.value().status())),
            typoResult);

    System.out.println("Email with typo: " + typoCustomer.email());
    System.out.println(
        "Fixed email:     " + VALID.mapOptional(v -> v.value().email(), fixed).orElse("N/A"));

    System.out.println();
  }

  private static void demonstrateValidationPipeline() {
    System.out.println("--- Multi-Stage Validation Pipeline ---");

    List<CustomerImportRow> rawData =
        List.of(
            new CustomerImportRow("C001", "Alice", "alice@example.com", "1990-05-15", "active"),
            new CustomerImportRow("C002", "", "bob@example.com", "1985-03-20", "active"),
            new CustomerImportRow(
                "C003", "Charlie", "charlie@example.com", "not-a-date", "inactive"),
            new CustomerImportRow("", "Dave", "dave@example.com", "1992-07-10", "active"));

    // Process each customer through the pipeline
    for (CustomerImportRow customer : rawData) {
      ImportVerdict result = validateCustomer(customer);

      // Extract different result types, loading a valid row as an imported customer
      Optional<ImportedCustomer> imported = VALID.mapOptional(valid -> load(valid.value()), result);
      Optional<List<String>> errors = INVALID.mapOptional(Invalid::errors, result);
      Optional<List<String>> warnings = WARNING.mapOptional(Warning::warnings, result);

      System.out.println("Customer ID: " + customer.id());
      if (imported.isPresent()) {
        System.out.println("  Status: VALID");
        System.out.println("  Data: " + imported.get());
      } else if (errors.isPresent()) {
        System.out.println("  Status: INVALID");
        System.out.println("  Errors: " + errors.get());
      } else if (warnings.isPresent()) {
        System.out.println("  Status: WARNING");
        System.out.println("  Warnings: " + warnings.get());
      }
    }

    System.out.println();
  }

  private static void demonstrateErrorReporting() {
    System.out.println("--- Error Reporting and Recovery ---");

    CustomerImportRow customer =
        new CustomerImportRow("C005", "Eve", "eve@example.com", "invalid-date", "active");

    ImportVerdict result = validateCustomer(customer);

    // Extract first error or warning using mapOptional
    String errorMessage =
        INVALID
            .mapOptional(inv -> inv.errors().isEmpty() ? null : inv.errors().get(0), result)
            .or(
                () ->
                    WARNING.mapOptional(
                        warn -> warn.warnings().isEmpty() ? null : warn.warnings().get(0), result))
            .orElse("No errors or warnings");

    System.out.println("Primary error/warning: " + errorMessage);

    // Extract the original value for logging, regardless of validation status
    CustomerImportRow original =
        VALID
            .mapOptional(Valid::value, result)
            .or(() -> INVALID.mapOptional(Invalid::originalValue, result))
            .or(() -> WARNING.mapOptional(Warning::value, result))
            .or(() -> SKIPPED.mapOptional(Skipped::value, result))
            .orElse(new CustomerImportRow("UNKNOWN", "", "", "", ""));

    System.out.println("Original customer ID for logging: " + original.id());

    System.out.println();
  }

  private static void demonstrateBatchValidation() {
    System.out.println("--- Batch Validation Statistics ---");

    List<CustomerImportRow> batch =
        List.of(
            new CustomerImportRow("C001", "Alice", "alice@example.com", "1990-05-15", "active"),
            new CustomerImportRow("C002", "", "bob@example.com", "1985-03-20", "active"),
            new CustomerImportRow("C003", "Charlie", "charlie@example.com", "2020-01-01", "active"),
            new CustomerImportRow("C004", "Dave", "dave@example.com", "invalid", "inactive"),
            new CustomerImportRow("C005", "Eve", "eve@example.com", "1995-12-25", "active"));

    List<ImportVerdict> results =
        batch.stream().map(DataValidationPipelineExample::validateCustomer).toList();

    long validCount = results.stream().filter(VALID::matches).count();
    long invalidCount = results.stream().filter(INVALID::matches).count();
    long warningCount = results.stream().filter(WARNING::matches).count();

    System.out.println("Batch validation results:");
    System.out.println("  Valid:    " + validCount);
    System.out.println("  Invalid:  " + invalidCount);
    System.out.println("  Warnings: " + warningCount);
    System.out.println("  Total:    " + results.size());

    // Calculate success rate
    double successRate = (double) validCount / results.size() * 100;
    System.out.println("  Success rate: " + String.format("%.1f%%", successRate));

    System.out.println();
  }

  // Helper validation methods

  private static ImportVerdict validateCustomer(CustomerImportRow customer) {
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    // Validate ID
    if (customer.id() == null || customer.id().trim().isEmpty()) {
      errors.add("Customer ID is required");
    }

    // Validate name
    if (customer.name() == null || customer.name().trim().isEmpty()) {
      errors.add("Customer name is required");
    }

    // Validate email
    if (customer.email() == null
        || !customer.email().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
      errors.add("Invalid email format");
    }

    // Validate date of birth
    try {
      LocalDate dob = LocalDate.parse(customer.dateOfBirth());
      if (dob.isAfter(LocalDate.now().minusYears(18))) {
        warnings.add("Customer is under 18 years old");
      }
    } catch (DateTimeParseException e) {
      errors.add("Invalid date of birth format (expected YYYY-MM-DD)");
    }

    // Return appropriate result
    if (!errors.isEmpty()) {
      return new Invalid(customer, errors);
    } else if (!warnings.isEmpty()) {
      return new Warning(customer, warnings);
    } else {
      return new Valid(customer);
    }
  }

  // Loads a row that passed validation, so its date of birth parses
  private static ImportedCustomer load(CustomerImportRow row) {
    return new ImportedCustomer(
        row.id(),
        new Customer(row.name(), new EmailAddress(row.email())),
        LocalDate.parse(row.dateOfBirth()),
        row.status());
  }

  private static String normaliseName(String name) {
    if (name == null) {
      return "";
    }
    return Arrays.stream(name.trim().split("\\s+"))
        .map(
            word ->
                word.isEmpty()
                    ? word
                    : word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
        .collect(Collectors.joining(" "));
  }
}
