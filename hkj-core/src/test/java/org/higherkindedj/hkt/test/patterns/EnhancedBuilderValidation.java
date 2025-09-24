// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.patterns;

// This file shows the enhanced validation methods that should be added to the
// existing builder classes in TypeClassTestPattern.java

import java.util.List;

/**
 * Enhanced validation methods for builder classes.
 *
 * <p>These methods should be added to the existing builder classes: - FunctorTestBuilder -
 * MonadTestBuilder (if created) - MonadErrorTestBuilder
 *
 * <h2>Key Improvements:</h2>
 *
 * <ul>
 *   <li>Detailed error messages listing all missing parameters
 *   <li>Clear guidance on how to fix the issue
 *   <li>Conditional validation based on what tests are being run
 * </ul>
 */
public final class EnhancedBuilderValidation {

  private EnhancedBuilderValidation() {
    throw new AssertionError("Utility class");
  }

  // ============================================================================
  // Example: Enhanced FunctorTestBuilder validation
  // ============================================================================

  /**
   * Enhanced validation for FunctorTestBuilder.
   *
   * <p>Add this method to FunctorTestBuilder class to replace the existing validateConfiguration()
   * method.
   */
  public static void exampleFunctorValidation() {
    // This is示例 code showing the pattern - add to actual builder class

    /*
    private void validateConfiguration() {
      List<String> missing = new ArrayList<>();
      List<String> hints = new ArrayList<>();

      // Check required fixtures
      if (validKind == null) {
        missing.add("validKind");
        hints.add("Use .withKind(yourKindInstance) to set the test Kind");
      }

      if (validMapper == null) {
        missing.add("validMapper");
        hints.add("Use .withMapper(yourMapperFunction) to set the mapping function");
      }

      // Check conditional requirements
      if (includeLaws) {
        if (equalityChecker == null) {
          missing.add("equalityChecker");
          hints.add("Use .withEqualityChecker(checker) - required for law testing");
        }
        if (secondMapper == null) {
          missing.add("secondMapper");
          hints.add("Use .withSecondMapper(mapper) - required for composition law");
        }
      }

      // Throw detailed exception if validation fails
      if (!missing.isEmpty()) {
        StringBuilder message = new StringBuilder();
        message.append("Cannot execute test - missing required configuration:\n\n");

        message.append("Missing parameters:\n");
        for (String param : missing) {
          message.append("  ✗ ").append(param).append("\n");
        }

        message.append("\nHow to fix:\n");
        for (String hint : hints) {
          message.append("  → ").append(hint).append("\n");
        }

        message.append("\nExample usage:\n");
        message.append("  TypeClassTestPattern.functorTest(functor, MyFunctor.class)\n");
        message.append("      .withKind(validKind)\n");
        message.append("      .withMapper(validMapper)\n");
        if (includeLaws) {
          message.append("      .withEqualityChecker(equalityChecker)\n");
          message.append("      .withSecondMapper(secondMapper)\n");
        }
        message.append("      .test();\n");

        throw new IllegalStateException(message.toString());
      }
    }
    */
  }

  // ============================================================================
  // Example: Enhanced MonadErrorTestBuilder validation
  // ============================================================================

  /**
   * Enhanced validation for MonadErrorTestBuilder.
   *
   * <p>Add this method to MonadErrorTestBuilder class to replace the existing
   * validateConfiguration() method.
   */
  public static void exampleMonadErrorValidation() {
    // This is example code showing the pattern - add to actual builder class

    /*
    private void validateConfiguration() {
      List<String> missing = new ArrayList<>();
      List<String> hints = new ArrayList<>();

      // Always required
      if (validKind == null) {
        missing.add("validKind");
        hints.add("Use .withKind(yourKindInstance)");
      }

      // Required for operations and validations
      if (includeOperations || includeValidations) {
        if (validMapper == null) {
          missing.add("validMapper");
          hints.add("Use .withMapper(yourMapperFunction)");
        }
        if (validFlatMapper == null) {
          missing.add("validFlatMapper");
          hints.add("Use .withFlatMapper(yourFlatMapFunction)");
        }
        if (validFunctionKind == null) {
          missing.add("validFunctionKind");
          hints.add("Use .withFunctionKind(yourFunctionKind)");
        }
        if (validHandler == null) {
          missing.add("validHandler");
          hints.add("Use .withHandler(yourErrorHandler)");
        }
        if (validFallback == null) {
          missing.add("validFallback");
          hints.add("Use .withFallback(yourFallbackKind)");
        }
      }

      // Required for laws
      if (includeLaws) {
        if (testValue == null) {
          missing.add("testValue");
          hints.add("Use .withTestValue(yourTestValue) - required for law testing");
        }
        if (testFunction == null) {
          missing.add("testFunction");
          hints.add("Use .withTestFunction(yourTestFunction) - required for law testing");
        }
        if (chainFunction == null) {
          missing.add("chainFunction");
          hints.add("Use .withChainFunction(yourChainFunction) - required for law testing");
        }
        if (equalityChecker == null) {
          missing.add("equalityChecker");
          hints.add("Use .withEqualityChecker(checker) - required for law testing");
        }
      }

      // Throw detailed exception if validation fails
      if (!missing.isEmpty()) {
        StringBuilder message = new StringBuilder();
        message.append("Cannot execute MonadError test - missing required configuration:\n\n");

        // Show what's being tested
        message.append("Test configuration:\n");
        message.append("  Operations:           ").append(includeOperations ? "✓" : "✗").append("\n");
        message.append("  Validations:          ").append(includeValidations ? "✓" : "✗").append("\n");
        message.append("  Exception Propagation:").append(includeExceptionPropagation ? "✓" : "✗").append("\n");
        message.append("  Laws:                 ").append(includeLaws ? "✓" : "✗").append("\n");
        message.append("\n");

        message.append("Missing parameters (").append(missing.size()).append("):\n");
        for (String param : missing) {
          message.append("  ✗ ").append(param).append("\n");
        }

        message.append("\nHow to fix:\n");
        for (String hint : hints) {
          message.append("  → ").append(hint).append("\n");
        }

        message.append("\nExample usage:\n");
        message.append("  TypeClassTestPattern.monadErrorTest(monadError, MyMonadError.class)\n");
        message.append("      .withKind(validKind)\n");
        message.append("      .withMapper(validMapper)\n");
        message.append("      .withFlatMapper(validFlatMapper)\n");
        message.append("      .withFunctionKind(validFunctionKind)\n");
        message.append("      .withHandler(validHandler)\n");
        message.append("      .withFallback(validFallback)\n");
        if (includeLaws) {
          message.append("      .withTestValue(testValue)\n");
          message.append("      .withTestFunction(testFunction)\n");
          message.append("      .withChainFunction(chainFunction)\n");
          message.append("      .withEqualityChecker(equalityChecker)\n");
        }
        message.append("      .test();\n");

        message.append("\nOr skip optional test components:\n");
        message.append("  .skipValidations()          // Skip null parameter validation tests\n");
        message.append("  .skipExceptionPropagation() // Skip exception propagation tests\n");
        message.append("  .skipLaws()                 // Skip algebraic law tests\n");

        throw new IllegalStateException(message.toString());
      }
    }
    */
  }

  // ============================================================================
  // Validation Utility Methods
  // ============================================================================

  /**
   * Creates a detailed validation error message.
   *
   * <p>Use this helper in your builder's validateConfiguration() method.
   *
   * @param builderName Name of the builder (e.g., "MonadErrorTestBuilder")
   * @param missing List of missing parameter names
   * @param hints List of hints for fixing the issues
   * @param includeOperations Whether operations are included
   * @param includeValidations Whether validations are included
   * @param includeExceptionPropagation Whether exception propagation is included
   * @param includeLaws Whether laws are included
   * @return Detailed error message
   */
  public static String createValidationErrorMessage(
      String builderName,
      List<String> missing,
      List<String> hints,
      boolean includeOperations,
      boolean includeValidations,
      boolean includeExceptionPropagation,
      boolean includeLaws) {

    StringBuilder message = new StringBuilder();
    message
        .append("Cannot execute ")
        .append(builderName)
        .append(" test - missing configuration:\n\n");

    // Show what's being tested
    message.append("Test configuration:\n");
    message.append("  Operations:           ").append(includeOperations ? "✓" : "✗").append("\n");
    message.append("  Validations:          ").append(includeValidations ? "✓" : "✗").append("\n");
    message
        .append("  Exception Propagation:")
        .append(includeExceptionPropagation ? "✓" : "✗")
        .append("\n");
    message.append("  Laws:                 ").append(includeLaws ? "✓" : "✗").append("\n");
    message.append("\n");

    message.append("Missing parameters (").append(missing.size()).append("):\n");
    for (String param : missing) {
      message.append("  ✗ ").append(param).append("\n");
    }

    message.append("\nHow to fix:\n");
    for (String hint : hints) {
      message.append("  → ").append(hint).append("\n");
    }

    message.append("\nTip: You can skip optional test components:\n");
    message.append("  .skipValidations()          // Skip null parameter validation tests\n");
    message.append("  .skipExceptionPropagation() // Skip exception propagation tests\n");
    message.append("  .skipLaws()                 // Skip algebraic law tests\n");

    return message.toString();
  }

  /**
   * Adds a missing parameter with its hint to the lists.
   *
   * @param missing List to add parameter name to
   * @param hints List to add hint to
   * @param paramName Name of the missing parameter
   * @param hint How to fix the issue
   */
  public static void addMissingParameter(
      List<String> missing, List<String> hints, String paramName, String hint) {
    missing.add(paramName);
    hints.add(hint);
  }
}
