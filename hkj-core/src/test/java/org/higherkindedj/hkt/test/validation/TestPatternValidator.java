// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.validation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Validates that test classes follow standardised patterns.
 *
 * <p>Ensures consistency across test implementations by checking for:
 *
 * <ul>
 *   <li>Required nested test classes
 *   <li>Standard test method names
 *   <li>Proper use of @DisplayName annotations
 *   <li>Test structure compliance
 * </ul>
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>{@code
 * @Test
 * @DisplayName("Validate test structure follows standards")
 * void validateTestStructure() {
 *     TestPatternValidator.validateTestClass(MyMonadTest.class);
 * }
 * }</pre>
 *
 * <h2>Standard Structure Expected:</h2>
 *
 * <pre>
 * MyTypeClassTest
 * ├── CompleteTestSuite
 * │   └── runCompleteTestPattern()
 * ├── OperationTests
 * │   └── [operation-specific tests]
 * ├── IndividualComponents
 * │   ├── testOperationsOnly()
 * │   ├── testValidationsOnly()
 * │   ├── testExceptionPropagationOnly()
 * │   └── testLawsOnly()
 * ├── EdgeCasesTests
 * │   └── [edge case tests]
 * └── PerformanceTests (optional)
 *     └── [performance tests]
 * </pre>
 */
public final class TestPatternValidator {

  private TestPatternValidator() {
    throw new AssertionError("TestPatternValidator is a utility class");
  }

  // ============================================================================
  // Main Validation Methods
  // ============================================================================

  /**
   * Validates that a test class follows standardised patterns.
   *
   * <p>Performs comprehensive validation including structure, naming, and annotations.
   *
   * @param testClass The test class to validate
   * @throws ValidationException if the test class doesn't follow standards
   */
  public static void validateTestClass(Class<?> testClass) {
    ValidationResult result = new ValidationResult(testClass);

    // Check nested classes
    result.addAll(validateNestedClasses(testClass));

    // Check test methods
    result.addAll(validateTestMethods(testClass));

    // Check annotations
    result.addAll(validateAnnotations(testClass));

    // Throw if validation failed
    if (result.hasErrors()) {
      throw new ValidationException(result);
    }
  }

  /**
   * Validates test class structure and returns a detailed report.
   *
   * <p>Unlike {@link #validateTestClass(Class)}, this method doesn't throw an exception. Instead,
   * it returns a report that can be examined programmatically.
   *
   * @param testClass The test class to validate
   * @return A validation result with details about compliance
   */
  public static ValidationResult validateAndReport(Class<?> testClass) {
    ValidationResult result = new ValidationResult(testClass);

    result.addAll(validateNestedClasses(testClass));
    result.addAll(validateTestMethods(testClass));
    result.addAll(validateAnnotations(testClass));

    return result;
  }

  // ============================================================================
  // Nested Class Validation
  // ============================================================================

  private static List<ValidationIssue> validateNestedClasses(Class<?> testClass) {
    List<ValidationIssue> issues = new ArrayList<>();

    // Required nested classes - be flexible with naming
    String[] requiredPatterns =
        new String[] {
          "Complete.*TestSuite", // Matches CompleteTestSuite, CompleteApplicativeTestSuite, etc.
          "Operation.*Tests?", // Matches OperationTests, OperationTest
          "Individual.*Components?" // Matches IndividualComponents, IndividualComponent
        };

    for (String pattern : requiredPatterns) {
      if (!hasNestedClassMatching(testClass, pattern)) {
        issues.add(
            ValidationIssue.error(
                "Missing required nested class matching pattern: " + pattern,
                "Add @Nested class matching pattern "
                    + pattern
                    + " to "
                    + testClass.getSimpleName()));
      }
    }

    // Check that nested TEST classes have @Nested and @DisplayName
    // Exclude records, enums, and other non-test nested classes
    for (Class<?> nested : testClass.getDeclaredClasses()) {
      // Skip records, enums, and classes without test methods
      if (nested.isRecord() || nested.isEnum() || !hasAnyTestMethods(nested)) {
        continue;
      }

      if (!nested.isAnnotationPresent(Nested.class)) {
        issues.add(
            ValidationIssue.warning(
                "Nested test class " + nested.getSimpleName() + " missing @Nested annotation",
                "Add @Nested annotation to " + nested.getSimpleName()));
      }

      if (!nested.isAnnotationPresent(DisplayName.class)) {
        issues.add(
            ValidationIssue.warning(
                "Nested test class " + nested.getSimpleName() + " missing @DisplayName annotation",
                "Add @DisplayName annotation to " + nested.getSimpleName()));
      }
    }

    return issues;
  }

  // Add this helper method:
  private static boolean hasNestedClassMatching(Class<?> testClass, String pattern) {
    return Arrays.stream(testClass.getDeclaredClasses())
        .anyMatch(c -> c.getSimpleName().matches(pattern));
  }

  // Add this helper method:
  private static boolean hasAnyTestMethods(Class<?> clazz) {
    return Arrays.stream(clazz.getDeclaredMethods())
        .anyMatch(m -> m.isAnnotationPresent(Test.class));
  }

  // ============================================================================
  // Test Method Validation
  // ============================================================================

  private static List<ValidationIssue> validateTestMethods(Class<?> testClass) {
    List<ValidationIssue> issues = new ArrayList<>();

    // Check for required test method in CompleteTestSuite
    Class<?> completeTestSuite = findNestedClass(testClass, "CompleteTestSuite");
    if (completeTestSuite != null) {
      if (!hasTestMethod(completeTestSuite, "runCompleteTestPattern")
          && !hasTestMethod(completeTestSuite, "runComplete.*TestPattern")) {
        issues.add(
            ValidationIssue.error(
                "CompleteTestSuite missing runCompleteTestPattern() method",
                "Add a test method that runs the complete test pattern"));
      }
    }

    // Check for required methods in IndividualComponents
    Class<?> individualComponents = findNestedClass(testClass, "IndividualComponents");
    if (individualComponents != null) {
      String[] requiredMethods =
          new String[] {
            "testOperationsOnly",
            "testValidationsOnly",
            "testExceptionPropagationOnly",
            "testLawsOnly"
          };

      for (String methodName : requiredMethods) {
        if (!hasTestMethod(individualComponents, methodName)) {
          issues.add(
              ValidationIssue.warning(
                  "IndividualComponents missing recommended method: " + methodName,
                  "Add @Test method " + methodName + "() to IndividualComponents"));
        }
      }
    }

    // Check that all test methods have @DisplayName
    validateTestMethodAnnotations(testClass, issues);

    return issues;
  }

  private static void validateTestMethodAnnotations(Class<?> clazz, List<ValidationIssue> issues) {
    for (Method method : clazz.getDeclaredMethods()) {
      if (method.isAnnotationPresent(Test.class)) {
        if (!method.isAnnotationPresent(DisplayName.class)) {
          issues.add(
              ValidationIssue.warning(
                  "Test method "
                      + method.getName()
                      + " in "
                      + clazz.getSimpleName()
                      + " missing @DisplayName",
                  "Add @DisplayName annotation to " + method.getName()));
        }
      }
    }

    // Recursively check nested classes
    for (Class<?> nested : clazz.getDeclaredClasses()) {
      validateTestMethodAnnotations(nested, issues);
    }
  }

  // ============================================================================
  // Annotation Validation
  // ============================================================================

  private static List<ValidationIssue> validateAnnotations(Class<?> testClass) {
    List<ValidationIssue> issues = new ArrayList<>();

    if (!testClass.isAnnotationPresent(DisplayName.class)) {
      issues.add(
          ValidationIssue.warning(
              "Test class " + testClass.getSimpleName() + " missing @DisplayName annotation",
              "Add @DisplayName(\"" + testClass.getSimpleName() + " Complete Test Suite\")"));
    }

    return issues;
  }

  // ============================================================================
  // Helper Methods
  // ============================================================================

  private static boolean hasNestedClass(Class<?> testClass, String nestedClassName) {
    return findNestedClass(testClass, nestedClassName) != null;
  }

  private static Class<?> findNestedClass(Class<?> testClass, String nestedClassName) {
    return Arrays.stream(testClass.getDeclaredClasses())
        .filter(c -> c.getSimpleName().equals(nestedClassName))
        .findFirst()
        .orElse(null);
  }

  private static boolean hasTestMethod(Class<?> clazz, String methodNamePattern) {
    return Arrays.stream(clazz.getDeclaredMethods())
        .filter(m -> m.isAnnotationPresent(Test.class))
        .anyMatch(m -> m.getName().matches(methodNamePattern));
  }

  // ============================================================================
  // Validation Result Classes
  // ============================================================================

  /** Represents the result of validating a test class. */
  public static final class ValidationResult {
    private final Class<?> testClass;
    private final List<ValidationIssue> issues;

    private ValidationResult(Class<?> testClass) {
      this.testClass = testClass;
      this.issues = new ArrayList<>();
    }

    void add(ValidationIssue issue) {
      issues.add(issue);
    }

    void addAll(List<ValidationIssue> moreIssues) {
      issues.addAll(moreIssues);
    }

    public boolean hasErrors() {
      return issues.stream().anyMatch(i -> i.severity == Severity.ERROR);
    }

    public boolean hasWarnings() {
      return issues.stream().anyMatch(i -> i.severity == Severity.WARNING);
    }

    public boolean hasIssues() {
      return !issues.isEmpty();
    }

    public List<ValidationIssue> getErrors() {
      return issues.stream().filter(i -> i.severity == Severity.ERROR).collect(Collectors.toList());
    }

    public List<ValidationIssue> getWarnings() {
      return issues.stream()
          .filter(i -> i.severity == Severity.WARNING)
          .collect(Collectors.toList());
    }

    public List<ValidationIssue> getAllIssues() {
      return new ArrayList<>(issues);
    }

    /** Prints a formatted report of all validation issues. */
    public void printReport() {
      System.out.println("=".repeat(80));
      System.out.println("Test Pattern Validation Report: " + testClass.getSimpleName());
      System.out.println("=".repeat(80));

      if (!hasIssues()) {
        System.out.println("✓ All checks passed - test follows standardised patterns");
        return;
      }

      List<ValidationIssue> errors = getErrors();
      List<ValidationIssue> warnings = getWarnings();

      if (!errors.isEmpty()) {
        System.out.println("\nERRORS (" + errors.size() + "):");
        System.out.println("-".repeat(80));
        errors.forEach(error -> System.out.println(error.format()));
      }

      if (!warnings.isEmpty()) {
        System.out.println("\nWARNINGS (" + warnings.size() + "):");
        System.out.println("-".repeat(80));
        warnings.forEach(warning -> System.out.println(warning.format()));
      }

      System.out.println("\n" + "=".repeat(80));
      System.out.println(
          "Summary: " + errors.size() + " error(s), " + warnings.size() + " warning(s)");
      System.out.println("=".repeat(80));
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("ValidationResult for ")
          .append(testClass.getSimpleName())
          .append(": ")
          .append(issues.size())
          .append(" issue(s)");
      if (hasErrors()) {
        sb.append(" (").append(getErrors().size()).append(" error(s))");
      }
      return sb.toString();
    }
  }

  /** Represents a single validation issue. */
  public static final class ValidationIssue {
    private final Severity severity;
    private final String message;
    private final String recommendation;

    private ValidationIssue(Severity severity, String message, String recommendation) {
      this.severity = severity;
      this.message = message;
      this.recommendation = recommendation;
    }

    static ValidationIssue error(String message, String recommendation) {
      return new ValidationIssue(Severity.ERROR, message, recommendation);
    }

    static ValidationIssue warning(String message, String recommendation) {
      return new ValidationIssue(Severity.WARNING, message, recommendation);
    }

    public Severity getSeverity() {
      return severity;
    }

    public String getMessage() {
      return message;
    }

    public String getRecommendation() {
      return recommendation;
    }

    String format() {
      String icon = severity == Severity.ERROR ? "✗" : "⚠";
      return icon + " " + message + "\n  → " + recommendation + "\n";
    }

    @Override
    public String toString() {
      return severity + ": " + message;
    }
  }

  /** Severity levels for validation issues. */
  public enum Severity {
    ERROR,
    WARNING
  }

  // ============================================================================
  // Exception Class
  // ============================================================================

  /** Exception thrown when test class validation fails. */
  public static final class ValidationException extends RuntimeException {
    private final ValidationResult result;

    private ValidationException(ValidationResult result) {
      super(buildMessage(result));
      this.result = result;
    }

    public ValidationResult getResult() {
      return result;
    }

    private static String buildMessage(ValidationResult result) {
      StringBuilder sb = new StringBuilder();
      sb.append("Test class ")
          .append(result.testClass.getSimpleName())
          .append(" does not follow standardised patterns:\n");

      List<ValidationIssue> errors = result.getErrors();
      for (int i = 0; i < errors.size(); i++) {
        ValidationIssue error = errors.get(i);
        sb.append("  ").append(i + 1).append(". ").append(error.message).append("\n");
        sb.append("     → ").append(error.recommendation).append("\n");
      }

      return sb.toString();
    }
  }
}
