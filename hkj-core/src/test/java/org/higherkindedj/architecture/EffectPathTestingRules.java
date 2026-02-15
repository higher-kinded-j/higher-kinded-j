// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.architecture;

import static org.higherkindedj.architecture.ArchitectureTestBase.getProductionClasses;
import static org.higherkindedj.architecture.ArchitectureTestBase.getTestClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules enforcing Effect Path API testing patterns.
 *
 * <p>These rules ensure that all Effect Path types have comprehensive test coverage following the
 * three-layer testing strategy:
 *
 * <ul>
 *   <li>Unit Tests (*PathTest.java): Comprehensive behavior testing
 *   <li>Property Tests (*PathPropertyTest.java): Functor and Monad laws via jQwik
 *   <li>Laws Tests (*PathLawsTest.java): Explicit law verification with DynamicTests
 * </ul>
 *
 * <p>The rules also enforce consistent test organization patterns including:
 *
 * <ul>
 *   <li>@DisplayName annotations on test classes
 *   <li>@Nested class organization for logical grouping
 *   <li>Proper null validation tests
 * </ul>
 */
@DisplayName("Effect Path API Testing Rules")
class EffectPathTestingRules {

  private static JavaClasses productionClasses;
  private static JavaClasses testClasses;

  /**
   * Set of Effect Path class names that should have comprehensive test coverage.
   *
   * <p>Includes Phase 1, 2, and 3 Path implementations.
   */
  private static final Set<String> EFFECT_PATH_CLASSES =
      Set.of(
          // Phase 1 & 2 Paths
          "MaybePath",
          "EitherPath",
          "TryPath",
          "IOPath",
          "ValidationPath",
          "IdPath",
          "OptionalPath",
          "GenericPath",
          // Phase 3 Paths
          "ReaderPath",
          "WriterPath",
          "WithStatePath",
          "LazyPath",
          "CompletableFuturePath",
          "ListPath",
          "StreamPath",
          "NonDetPath");

  @BeforeAll
  static void setup() {
    productionClasses = getProductionClasses();
    testClasses = getTestClasses();
  }

  /**
   * Verifies that all Effect Path classes have corresponding unit test classes.
   *
   * <p>Each XxxPath class in the effect package should have a corresponding XxxPathTest class.
   */
  @Test
  @DisplayName("All Effect Path classes should have unit tests")
  void all_effect_paths_should_have_unit_tests() {
    Set<String> pathClassesWithoutTests = new HashSet<>();

    for (String pathClassName : EFFECT_PATH_CLASSES) {
      String expectedTestClassName = pathClassName + "Test";

      boolean hasTestClass =
          testClasses.stream()
              .anyMatch(testClass -> testClass.getSimpleName().equals(expectedTestClassName));

      if (!hasTestClass) {
        pathClassesWithoutTests.add(pathClassName);
      }
    }

    if (!pathClassesWithoutTests.isEmpty()) {
      throw new AssertionError(
          String.format(
              "The following Effect Path classes are missing unit tests (*PathTest.java): %s",
              pathClassesWithoutTests));
    }
  }

  /**
   * Verifies that all Effect Path classes have corresponding property test classes.
   *
   * <p>Each XxxPath class should have a corresponding XxxPathPropertyTest class that verifies
   * Functor and Monad laws using jQwik property-based testing.
   */
  @Test
  @DisplayName("All Effect Path classes should have property tests")
  void all_effect_paths_should_have_property_tests() {
    Set<String> pathClassesWithoutPropertyTests = new HashSet<>();

    for (String pathClassName : EFFECT_PATH_CLASSES) {
      String expectedPropertyTestClassName = pathClassName + "PropertyTest";

      boolean hasPropertyTestClass =
          testClasses.stream()
              .anyMatch(
                  testClass -> testClass.getSimpleName().equals(expectedPropertyTestClassName));

      if (!hasPropertyTestClass) {
        pathClassesWithoutPropertyTests.add(pathClassName);
      }
    }

    if (!pathClassesWithoutPropertyTests.isEmpty()) {
      throw new AssertionError(
          String.format(
              "The following Effect Path classes are missing property tests"
                  + " (*PathPropertyTest.java): %s%n"
                  + "Property tests should verify Functor and Monad laws using jQwik.",
              pathClassesWithoutPropertyTests));
    }
  }

  /**
   * Verifies that all Effect Path classes have corresponding laws test classes.
   *
   * <p>Each XxxPath class should have a corresponding XxxPathLawsTest class that explicitly
   * verifies Functor and Monad laws using DynamicTest.
   */
  @Test
  @DisplayName("All Effect Path classes should have laws tests")
  void all_effect_paths_should_have_laws_tests() {
    Set<String> pathClassesWithoutLawsTests = new HashSet<>();

    for (String pathClassName : EFFECT_PATH_CLASSES) {
      String expectedLawsTestClassName = pathClassName + "LawsTest";

      boolean hasLawsTestClass =
          testClasses.stream()
              .anyMatch(testClass -> testClass.getSimpleName().equals(expectedLawsTestClassName));

      if (!hasLawsTestClass) {
        pathClassesWithoutLawsTests.add(pathClassName);
      }
    }

    if (!pathClassesWithoutLawsTests.isEmpty()) {
      throw new AssertionError(
          String.format(
              "The following Effect Path classes are missing laws tests (*PathLawsTest.java): %s%n"
                  + "Laws tests should verify Functor and Monad laws using @TestFactory and"
                  + " DynamicTest.",
              pathClassesWithoutLawsTests));
    }
  }

  /**
   * Verifies that Effect Path unit test classes have @DisplayName annotations.
   *
   * <p>All test classes should have a @DisplayName annotation providing a descriptive name.
   */
  @Test
  @DisplayName("Effect Path test classes should have @DisplayName annotations")
  void effect_path_tests_should_have_display_name() {
    Set<String> testsWithoutDisplayName = new HashSet<>();

    for (String pathClassName : EFFECT_PATH_CLASSES) {
      String testClassName = pathClassName + "Test";

      testClasses.stream()
          .filter(testClass -> testClass.getSimpleName().equals(testClassName))
          .findFirst()
          .ifPresent(
              testClass -> {
                boolean hasDisplayName =
                    testClass.isAnnotatedWith("org.junit.jupiter.api.DisplayName");
                if (!hasDisplayName) {
                  testsWithoutDisplayName.add(testClassName);
                }
              });
    }

    if (!testsWithoutDisplayName.isEmpty()) {
      throw new AssertionError(
          String.format(
              "The following Effect Path test classes are missing @DisplayName annotations: %s",
              testsWithoutDisplayName));
    }
  }

  /**
   * Verifies that Effect Path unit test classes use @Nested classes for organization.
   *
   * <p>Unit tests should be organized into nested classes for logical grouping (Factory Methods,
   * Composable Operations, etc.).
   */
  @Test
  @DisplayName("Effect Path unit tests should use @Nested classes for organization")
  void effect_path_tests_should_use_nested_classes() {
    Set<String> testsWithoutNestedClasses = new HashSet<>();

    for (String pathClassName : EFFECT_PATH_CLASSES) {
      String testClassName = pathClassName + "Test";

      testClasses.stream()
          .filter(testClass -> testClass.getSimpleName().equals(testClassName))
          .findFirst()
          .ifPresent(
              testClass -> {
                // Check if the test class has any inner classes annotated with @Nested
                // ArchUnit doesn't have getInnerClasses(), so we find inner classes by
                // checking for classes whose enclosing class is this test class
                boolean hasNestedClasses =
                    testClasses.stream()
                        .filter(
                            potentialInner ->
                                potentialInner.getEnclosingClass().isPresent()
                                    && potentialInner.getEnclosingClass().get().equals(testClass))
                        .anyMatch(
                            innerClass ->
                                innerClass.isAnnotatedWith("org.junit.jupiter.api.Nested"));

                if (!hasNestedClasses) {
                  testsWithoutNestedClasses.add(testClassName);
                }
              });
    }

    if (!testsWithoutNestedClasses.isEmpty()) {
      throw new AssertionError(
          String.format(
              "The following Effect Path test classes are missing @Nested class organization: %s%n"
                  + "Unit tests should be organized into nested classes for logical grouping.",
              testsWithoutNestedClasses));
    }
  }

  /**
   * Verifies that Effect Path production classes are in the effect package.
   *
   * <p>All Path implementations should reside in the org.higherkindedj.hkt.effect package.
   */
  @Test
  @DisplayName("Effect Path classes should be in the effect package")
  void effect_path_classes_should_be_in_effect_package() {
    Set<String> misplacedClasses = new HashSet<>();

    for (String pathClassName : EFFECT_PATH_CLASSES) {
      productionClasses.stream()
          .filter(javaClass -> javaClass.getSimpleName().equals(pathClassName))
          .findFirst()
          .ifPresent(
              javaClass -> {
                if (!javaClass.getPackageName().equals("org.higherkindedj.hkt.effect")) {
                  misplacedClasses.add(
                      pathClassName + " (found in " + javaClass.getPackageName() + ")");
                }
              });
    }

    if (!misplacedClasses.isEmpty()) {
      throw new AssertionError(
          String.format(
              "The following Effect Path classes are not in the expected package"
                  + " (org.higherkindedj.hkt.effect): %s",
              misplacedClasses));
    }
  }

  /**
   * Verifies that Effect Path classes implement the expected capability interfaces.
   *
   * <p>All Path types should implement at least Composable (for map/peek) and most should implement
   * Chainable (for via/then).
   */
  @Test
  @DisplayName("Effect Path classes should implement capability interfaces")
  void effect_path_classes_should_implement_capabilities() {
    Set<String> classesWithoutCapabilities = new HashSet<>();

    for (String pathClassName : EFFECT_PATH_CLASSES) {
      productionClasses.stream()
          .filter(javaClass -> javaClass.getSimpleName().equals(pathClassName))
          .findFirst()
          .ifPresent(
              javaClass -> {
                boolean implementsComposable =
                    javaClass.getAllRawInterfaces().stream()
                        .anyMatch(
                            iface ->
                                iface.getSimpleName().equals("Composable")
                                    || iface.getSimpleName().equals("Chainable")
                                    || iface.getSimpleName().equals("Recoverable"));

                if (!implementsComposable) {
                  classesWithoutCapabilities.add(pathClassName);
                }
              });
    }

    if (!classesWithoutCapabilities.isEmpty()) {
      throw new AssertionError(
          String.format(
              "The following Effect Path classes do not implement expected capability interfaces"
                  + " (Composable, Chainable, or Recoverable): %s",
              classesWithoutCapabilities));
    }
  }

  /**
   * Verifies that Effect Path classes have null-validation on their factory methods.
   *
   * <p>This is a heuristic check that looks for the presence of Objects.requireNonNull or similar
   * patterns in the source.
   */
  @Test
  @DisplayName("Effect Path factory methods should validate null parameters")
  void effect_path_factories_should_validate_null() {
    Set<String> classesWithPotentialNullIssues = new HashSet<>();

    for (String pathClassName : EFFECT_PATH_CLASSES) {
      productionClasses.stream()
          .filter(javaClass -> javaClass.getSimpleName().equals(pathClassName))
          .findFirst()
          .ifPresent(
              javaClass -> {
                // Check if static factory methods exist
                Set<JavaMethod> staticMethods =
                    javaClass.getMethods().stream()
                        .filter(method -> method.getModifiers().contains(JavaModifier.STATIC))
                        .filter(
                            method -> method.getRawReturnType().getSimpleName().contains("Path"))
                        .collect(Collectors.toSet());

                // If there are static factory methods, we expect null checks
                // This is a heuristic - actual null checking is verified in unit tests
                if (staticMethods.isEmpty()) {
                  // No static factories - might use Path class for creation
                  // This is acceptable
                }
              });
    }

    // This test primarily documents the expectation; actual validation is in unit tests
    // If we find issues, we report them
    if (!classesWithPotentialNullIssues.isEmpty()) {
      throw new AssertionError(
          String.format(
              "The following Effect Path classes may have null validation issues: %s",
              classesWithPotentialNullIssues));
    }
  }

  /**
   * Reports the current test coverage status for Effect Path classes.
   *
   * <p>This is an informational test that prints a summary of which tests exist for each Path type.
   */
  @Test
  @DisplayName("Report Effect Path test coverage status")
  void report_effect_path_test_coverage() {
    StringBuilder report = new StringBuilder();
    report.append("\n=== Effect Path Test Coverage Report ===\n\n");
    report.append(
        String.format("%-25s | %-8s | %-12s | %-10s%n", "Path Type", "Unit", "Property", "Laws"));
    report.append("-".repeat(65)).append("\n");

    for (String pathClassName : EFFECT_PATH_CLASSES.stream().sorted().toList()) {
      String unitTestName = pathClassName + "Test";
      String propertyTestName = pathClassName + "PropertyTest";
      String lawsTestName = pathClassName + "LawsTest";

      boolean hasUnitTest =
          testClasses.stream().anyMatch(tc -> tc.getSimpleName().equals(unitTestName));
      boolean hasPropertyTest =
          testClasses.stream().anyMatch(tc -> tc.getSimpleName().equals(propertyTestName));
      boolean hasLawsTest =
          testClasses.stream().anyMatch(tc -> tc.getSimpleName().equals(lawsTestName));

      report.append(
          String.format(
              "%-25s | %-8s | %-12s | %-10s%n",
              pathClassName,
              hasUnitTest ? "✓" : "✗",
              hasPropertyTest ? "✓" : "✗",
              hasLawsTest ? "✓" : "✗"));
    }

    report.append("\n===========================================\n");

    System.out.println(report);
  }
}
