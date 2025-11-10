// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.coverage;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Reports test coverage for type class implementations.
 *
 * <p>Analyses test classes to determine which aspects of type class testing are covered:
 *
 * <ul>
 *   <li>Operation Tests - Basic functionality testing
 *   <li>Validation Tests - Null parameter validation
 *   <li>Exception Tests - Exception propagation
 *   <li>Law Tests - Algebraic law verification
 *   <li>Edge Case Tests - Boundary and corner cases
 * </ul>
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>{@code
 * @Test
 * @DisplayName("Verify test coverage is complete")
 * void verifyTestCoverage() {
 *     TestCoverageReport report = TestCoverageReporter.analyse(MyMonadTest.class);
 *     report.printReport();
 *     assertThat(report.isComplete()).isTrue();
 * }
 * }</pre>
 *
 * <h2>Detection Heuristics:</h2>
 *
 * <p>The reporter uses method naming patterns and nested class names to detect coverage:
 *
 * <ul>
 *   <li>Operations: Methods containing "operation", "map", "flatMap", "ap", etc.
 *   <li>Validations: Methods containing "validation", "null", "assert.*Null"
 *   <li>Exceptions: Methods containing "exception", "propagat", "throwing"
 *   <li>Laws: Methods containing "law", "identity", "composition", "associativ"
 *   <li>Edge Cases: Nested class "EdgeCase" or methods with "edge", "boundary"
 * </ul>
 */
public final class TestCoverageReporter {

  private TestCoverageReporter() {
    throw new AssertionError("TestCoverageReporter is a utility class");
  }

  /**
   * Analyses a test class and returns a coverage report.
   *
   * @param testClass The test class to analyse
   * @return A detailed coverage report
   */
  public static TestCoverageReport analyse(Class<?> testClass) {
    return new Builder(testClass)
        .checkOperationTests()
        .checkValidationTests()
        .checkExceptionTests()
        .checkLawTests()
        .checkEdgeCaseTests()
        .build();
  }

  /**
   * Analyses a test class with custom detection patterns.
   *
   * @param testClass The test class to analyse
   * @param config Configuration for custom detection
   * @return A detailed coverage report
   */
  public static TestCoverageReport analyse(Class<?> testClass, CoverageConfig config) {
    return new Builder(testClass)
        .withConfig(config)
        .checkOperationTests()
        .checkValidationTests()
        .checkExceptionTests()
        .checkLawTests()
        .checkEdgeCaseTests()
        .build();
  }

  // ============================================================================
  // Builder Class
  // ============================================================================

  private static final class Builder {
    private final Class<?> testClass;
    private CoverageConfig config;
    private boolean hasOperationTests;
    private boolean hasValidationTests;
    private boolean hasExceptionTests;
    private boolean hasLawTests;
    private boolean hasEdgeCaseTests;
    private Set<String> operationTestMethods;
    private Set<String> validationTestMethods;
    private Set<String> exceptionTestMethods;
    private Set<String> lawTestMethods;
    private Set<String> edgeCaseTestMethods;

    Builder(Class<?> testClass) {
      this.testClass = testClass;
      this.config = CoverageConfig.standard();
      this.operationTestMethods = new HashSet<>();
      this.validationTestMethods = new HashSet<>();
      this.exceptionTestMethods = new HashSet<>();
      this.lawTestMethods = new HashSet<>();
      this.edgeCaseTestMethods = new HashSet<>();
    }

    Builder withConfig(CoverageConfig config) {
      this.config = config;
      return this;
    }

    Builder checkOperationTests() {
      hasOperationTests = detectTests(config.operationPatterns, operationTestMethods);
      return this;
    }

    Builder checkValidationTests() {
      hasValidationTests = detectTests(config.validationPatterns, validationTestMethods);
      return this;
    }

    Builder checkExceptionTests() {
      hasExceptionTests = detectTests(config.exceptionPatterns, exceptionTestMethods);
      return this;
    }

    Builder checkLawTests() {
      hasLawTests = detectTests(config.lawPatterns, lawTestMethods);
      return this;
    }

    Builder checkEdgeCaseTests() {
      // Check for EdgeCaseTests nested class
      boolean hasNestedClass =
          Arrays.stream(testClass.getDeclaredClasses())
              .anyMatch(c -> c.getSimpleName().matches(".*EdgeCase.*"));

      hasEdgeCaseTests =
          hasNestedClass || detectTests(config.edgeCasePatterns, edgeCaseTestMethods);
      return this;
    }

    private boolean detectTests(String[] patterns, Set<String> foundMethods) {
      return scanForPatterns(testClass, patterns, foundMethods);
    }

    private boolean scanForPatterns(Class<?> clazz, String[] patterns, Set<String> foundMethods) {
      boolean found = false;

      // Scan methods in this class
      for (Method method : clazz.getDeclaredMethods()) {
        if (method.isAnnotationPresent(Test.class)) {
          String methodName = method.getName().toLowerCase();
          for (String pattern : patterns) {
            if (methodName.contains(pattern.toLowerCase())) {
              foundMethods.add(clazz.getSimpleName() + "." + method.getName());
              found = true;
            }
          }
        }
      }

      // Recursively scan nested classes
      for (Class<?> nested : clazz.getDeclaredClasses()) {
        if (scanForPatterns(nested, patterns, foundMethods)) {
          found = true;
        }
      }

      return found;
    }

    TestCoverageReport build() {
      return new TestCoverageReport(
          testClass,
          hasOperationTests,
          hasValidationTests,
          hasExceptionTests,
          hasLawTests,
          hasEdgeCaseTests,
          operationTestMethods,
          validationTestMethods,
          exceptionTestMethods,
          lawTestMethods,
          edgeCaseTestMethods);
    }
  }

  // ============================================================================
  // Coverage Report Class
  // ============================================================================

  /** Represents the test coverage report for a type class implementation. */
  public static final class TestCoverageReport {
    private final Class<?> testClass;
    private final boolean hasOperationTests;
    private final boolean hasValidationTests;
    private final boolean hasExceptionTests;
    private final boolean hasLawTests;
    private final boolean hasEdgeCaseTests;
    private final Set<String> operationTestMethods;
    private final Set<String> validationTestMethods;
    private final Set<String> exceptionTestMethods;
    private final Set<String> lawTestMethods;
    private final Set<String> edgeCaseTestMethods;

    private TestCoverageReport(
        Class<?> testClass,
        boolean hasOperationTests,
        boolean hasValidationTests,
        boolean hasExceptionTests,
        boolean hasLawTests,
        boolean hasEdgeCaseTests,
        Set<String> operationTestMethods,
        Set<String> validationTestMethods,
        Set<String> exceptionTestMethods,
        Set<String> lawTestMethods,
        Set<String> edgeCaseTestMethods) {
      this.testClass = testClass;
      this.hasOperationTests = hasOperationTests;
      this.hasValidationTests = hasValidationTests;
      this.hasExceptionTests = hasExceptionTests;
      this.hasLawTests = hasLawTests;
      this.hasEdgeCaseTests = hasEdgeCaseTests;
      this.operationTestMethods = operationTestMethods;
      this.validationTestMethods = validationTestMethods;
      this.exceptionTestMethods = exceptionTestMethods;
      this.lawTestMethods = lawTestMethods;
      this.edgeCaseTestMethods = edgeCaseTestMethods;
    }

    public boolean hasOperationTests() {
      return hasOperationTests;
    }

    public boolean hasValidationTests() {
      return hasValidationTests;
    }

    public boolean hasExceptionTests() {
      return hasExceptionTests;
    }

    public boolean hasLawTests() {
      return hasLawTests;
    }

    public boolean hasEdgeCaseTests() {
      return hasEdgeCaseTests;
    }

    /**
     * Returns true if all core test categories are covered.
     *
     * <p>Core categories are: Operations, Validations, Exceptions, and Laws. Edge cases are
     * considered optional.
     *
     * @return true if core coverage is complete
     */
    public boolean isComplete() {
      return hasOperationTests && hasValidationTests && hasExceptionTests && hasLawTests;
    }

    /**
     * Returns true if all test categories including edge cases are covered.
     *
     * @return true if comprehensive coverage is complete
     */
    public boolean isComprehensive() {
      return isComplete() && hasEdgeCaseTests;
    }

    /**
     * Returns the coverage percentage (0-100).
     *
     * <p>Based on core categories (Operations, Validations, Exceptions, Laws).
     *
     * @return coverage percentage
     */
    public int getCoveragePercentage() {
      int total = 4; // Core categories
      int covered = 0;
      if (hasOperationTests) covered++;
      if (hasValidationTests) covered++;
      if (hasExceptionTests) covered++;
      if (hasLawTests) covered++;
      return (covered * 100) / total;
    }

    /**
     * Returns the comprehensive coverage percentage including edge cases (0-100).
     *
     * @return comprehensive coverage percentage
     */
    public int getComprehensiveCoveragePercentage() {
      int total = 5; // Including edge cases
      int covered = 0;
      if (hasOperationTests) covered++;
      if (hasValidationTests) covered++;
      if (hasExceptionTests) covered++;
      if (hasLawTests) covered++;
      if (hasEdgeCaseTests) covered++;
      return (covered * 100) / total;
    }

    /** Prints a formatted coverage report to standard output. */
    public void printReport() {
      System.out.println("=".repeat(80));
      System.out.println("Test Coverage Report: " + testClass.getSimpleName());
      System.out.println("=".repeat(80));
      System.out.println();

      printCategory("Operations", hasOperationTests, operationTestMethods);
      printCategory("Validations", hasValidationTests, validationTestMethods);
      printCategory("Exceptions", hasExceptionTests, exceptionTestMethods);
      printCategory("Laws", hasLawTests, lawTestMethods);
      printCategory("Edge Cases", hasEdgeCaseTests, edgeCaseTestMethods);

      System.out.println();
      System.out.println("-".repeat(80));
      System.out.println(
          "Core Coverage:          "
              + getCoveragePercentage()
              + "% "
              + (isComplete() ? "✓ Complete" : "✗ Incomplete"));
      System.out.println(
          "Comprehensive Coverage: "
              + getComprehensiveCoveragePercentage()
              + "% "
              + (isComprehensive() ? "✓ Complete" : "✗ Incomplete"));
      System.out.println("=".repeat(80));
    }

    private void printCategory(String name, boolean hasCoverage, Set<String> methods) {
      String status = hasCoverage ? "✓" : "✗";
      String statusText = hasCoverage ? "Covered" : "Missing";

      System.out.printf("%-20s %s %s", name + ":", status, statusText);

      if (!methods.isEmpty() && methods.size() <= 5) {
        System.out.print(" (" + methods.size() + " test" + (methods.size() > 1 ? "s" : "") + ")");
      } else if (methods.size() > 5) {
        System.out.print(" (" + methods.size() + " tests)");
      }

      System.out.println();

      // Print method names if not too many
      if (!methods.isEmpty() && methods.size() <= 3) {
        for (String method : methods) {
          System.out.println("                       - " + method);
        }
      }
    }

    /**
     * Returns a concise summary string.
     *
     * @return summary of coverage
     */
    public String getSummary() {
      return String.format(
          "%s: %d%% coverage (%s)",
          testClass.getSimpleName(),
          getCoveragePercentage(),
          isComplete() ? "complete" : "incomplete");
    }

    @Override
    public String toString() {
      return getSummary();
    }
  }

  // ============================================================================
  // Coverage Configuration
  // ============================================================================

  /** Configuration for customising coverage detection patterns. */
  public static final class CoverageConfig {
    private final String[] operationPatterns;
    private final String[] validationPatterns;
    private final String[] exceptionPatterns;
    private final String[] lawPatterns;
    private final String[] edgeCasePatterns;

    private CoverageConfig(
        String[] operationPatterns,
        String[] validationPatterns,
        String[] exceptionPatterns,
        String[] lawPatterns,
        String[] edgeCasePatterns) {
      this.operationPatterns = operationPatterns;
      this.validationPatterns = validationPatterns;
      this.exceptionPatterns = exceptionPatterns;
      this.lawPatterns = lawPatterns;
      this.edgeCasePatterns = edgeCasePatterns;
    }

    /**
     * Returns standard coverage detection patterns.
     *
     * @return standard configuration
     */
    public static CoverageConfig standard() {
      return new CoverageConfig(
          new String[] {
            "operation", "map", "flatmap", "ap", "apply", "fold", "traverse", "sequence"
          },
          new String[] {"validation", "null", "assertnull", "validate", "require"},
          new String[] {"exception", "propagat", "throwing", "error"},
          new String[] {"law", "identity", "composition", "associativ", "homomorphism"},
          new String[] {"edge", "boundary", "corner", "null", "empty"});
    }

    /**
     * Builder for custom coverage configurations.
     *
     * @return a new builder
     */
    public static ConfigBuilder builder() {
      return new ConfigBuilder();
    }

    public static final class ConfigBuilder {
      private String[] operationPatterns = standard().operationPatterns;
      private String[] validationPatterns = standard().validationPatterns;
      private String[] exceptionPatterns = standard().exceptionPatterns;
      private String[] lawPatterns = standard().lawPatterns;
      private String[] edgeCasePatterns = standard().edgeCasePatterns;

      public ConfigBuilder operationPatterns(String... patterns) {
        this.operationPatterns = patterns;
        return this;
      }

      public ConfigBuilder validationPatterns(String... patterns) {
        this.validationPatterns = patterns;
        return this;
      }

      public ConfigBuilder exceptionPatterns(String... patterns) {
        this.exceptionPatterns = patterns;
        return this;
      }

      public ConfigBuilder lawPatterns(String... patterns) {
        this.lawPatterns = patterns;
        return this;
      }

      public ConfigBuilder edgeCasePatterns(String... patterns) {
        this.edgeCasePatterns = patterns;
        return this;
      }

      public CoverageConfig build() {
        return new CoverageConfig(
            operationPatterns,
            validationPatterns,
            exceptionPatterns,
            lawPatterns,
            edgeCasePatterns);
      }
    }
  }
}
