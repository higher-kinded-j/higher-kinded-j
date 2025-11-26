// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
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
  public sealed interface ValidationResult permits Valid, Invalid, Warning, Skipped {}

  @GenerateLenses
  public record Valid(RawCustomer value) implements ValidationResult {}

  @GenerateLenses
  public record Invalid(RawCustomer originalValue, List<String> errors)
      implements ValidationResult {}

  @GenerateLenses
  public record Warning(RawCustomer value, List<String> warnings) implements ValidationResult {}

  @GenerateLenses
  public record Skipped(RawCustomer value, String reason) implements ValidationResult {}

  // Domain models for customer import
  public record RawCustomer(
      String id, String name, String email, String dateOfBirth, String status) {}

  public record ValidatedCustomer(
      String id, String name, String email, LocalDate dateOfBirth, String status) {}

  // Prisms for validation results
  private static final Prism<ValidationResult, Valid> VALID = ValidationResultPrisms.valid();
  private static final Prism<ValidationResult, Invalid> INVALID = ValidationResultPrisms.invalid();
  private static final Prism<ValidationResult, Warning> WARNING = ValidationResultPrisms.warning();
  private static final Prism<ValidationResult, Skipped> SKIPPED = ValidationResultPrisms.skipped();

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

    RawCustomer validCustomer =
        new RawCustomer("C001", "Alice Smith", "alice@example.com", "1990-05-15", "active");
    RawCustomer invalidCustomer =
        new RawCustomer("C002", "", "not-an-email", "invalid-date", "active");

    ValidationResult result1 = validateCustomer(validCustomer);
    ValidationResult result2 = validateCustomer(invalidCustomer);

    System.out.println("Valid customer is valid: " + VALID.matches(result1));
    System.out.println("Invalid customer is invalid: " + INVALID.matches(result2));

    // Extract validation errors
    List<String> errors = INVALID.mapOptional(Invalid::errors, result2).orElse(List.of());

    System.out.println("Validation errors: " + errors);

    System.out.println();
  }

  private static void demonstrateDataCleansing() {
    System.out.println("--- Data Cleansing with Conditional Modification ---");

    RawCustomer messyCustomer =
        new RawCustomer(" c003 ", "  bob jones  ", " BOB@EXAMPLE.COM ", "1985-03-20", " ACTIVE ");

    ValidationResult result = new Valid(messyCustomer);

    // Clean up whitespace and normalise case
    ValidationResult cleaned =
        VALID.modify(
            valid ->
                new Valid(
                    new RawCustomer(
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
    RawCustomer typoCustomer =
        new RawCustomer("C004", "Charlie", "charlie@gmial.com", "1992-07-10", "active");

    ValidationResult typoResult = new Valid(typoCustomer);

    ValidationResult fixed =
        VALID.modifyWhen(
            valid -> valid.value().email().contains("@gmial."),
            valid ->
                new Valid(
                    new RawCustomer(
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

    List<RawCustomer> rawData =
        List.of(
            new RawCustomer("C001", "Alice", "alice@example.com", "1990-05-15", "active"),
            new RawCustomer("C002", "", "bob@example.com", "1985-03-20", "active"),
            new RawCustomer("C003", "Charlie", "charlie@example.com", "not-a-date", "inactive"),
            new RawCustomer("", "Dave", "dave@example.com", "1992-07-10", "active"));

    // Process each customer through the pipeline
    for (RawCustomer customer : rawData) {
      ValidationResult result = validateCustomer(customer);

      // Extract different result types
      Optional<RawCustomer> validCustomer = VALID.mapOptional(Valid::value, result);
      Optional<List<String>> errors = INVALID.mapOptional(Invalid::errors, result);
      Optional<List<String>> warnings = WARNING.mapOptional(Warning::warnings, result);

      System.out.println("Customer ID: " + customer.id());
      if (validCustomer.isPresent()) {
        System.out.println("  Status: VALID");
        System.out.println("  Data: " + validCustomer.get());
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

    RawCustomer customer =
        new RawCustomer("C005", "Eve", "eve@example.com", "invalid-date", "active");

    ValidationResult result = validateCustomer(customer);

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
    RawCustomer original =
        VALID
            .mapOptional(Valid::value, result)
            .or(() -> INVALID.mapOptional(Invalid::originalValue, result))
            .or(() -> WARNING.mapOptional(Warning::value, result))
            .or(() -> SKIPPED.mapOptional(Skipped::value, result))
            .orElse(new RawCustomer("UNKNOWN", "", "", "", ""));

    System.out.println("Original customer ID for logging: " + original.id());

    System.out.println();
  }

  private static void demonstrateBatchValidation() {
    System.out.println("--- Batch Validation Statistics ---");

    List<RawCustomer> batch =
        List.of(
            new RawCustomer("C001", "Alice", "alice@example.com", "1990-05-15", "active"),
            new RawCustomer("C002", "", "bob@example.com", "1985-03-20", "active"),
            new RawCustomer("C003", "Charlie", "charlie@example.com", "2020-01-01", "active"),
            new RawCustomer("C004", "Dave", "dave@example.com", "invalid", "inactive"),
            new RawCustomer("C005", "Eve", "eve@example.com", "1995-12-25", "active"));

    List<ValidationResult> results =
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

  private static ValidationResult validateCustomer(RawCustomer customer) {
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
