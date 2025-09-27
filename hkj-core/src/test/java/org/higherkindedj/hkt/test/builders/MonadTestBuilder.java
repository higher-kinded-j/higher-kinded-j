// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.builders;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.test.patterns.MonadTestPattern;

/**
 * Fluent builder for comprehensive Monad testing.
 *
 * <p>Provides a fluent API for building and executing complete Monad test suites. This builder
 * offers more flexibility than {@link MonadTestPattern} by allowing selective test execution and
 * custom configurations.
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Complete Test Suite:</h3>
 *
 * <pre>{@code
 * MonadTestBuilder.forMonad(monad)
 *     .withValidKind(validKind)
 *     .withTestValue(42)
 *     .withMapper(validMapper)
 *     .withFlatMapper(validFlatMapper)
 *     .withFunctionKind(validFunctionKind)
 *     .withTestFunction(testFunction)
 *     .withChainFunction(chainFunction)
 *     .withEqualityChecker(equalityChecker)
 *     .testAll();
 * }</pre>
 *
 * <h3>Selective Testing:</h3>
 *
 * <pre>{@code
 * MonadTestBuilder.forMonad(monad)
 *     .withValidKind(validKind)
 *     .withMapper(validMapper)
 *     .testBasicOperations()
 *     .testNullValidations()
 *     .execute();
 * }</pre>
 *
 * <h3>Custom Configuration:</h3>
 *
 * <pre>{@code
 * MonadTestBuilder.forMonad(monad)
 *     .withValidKind(validKind)
 *     .skipExceptionTests()  // Skip exception propagation tests
 *     .skipLawTests()        // Skip monad law tests
 *     .testAll();
 * }</pre>
 *
 * <h2>Design Notes:</h2>
 *
 * <ul>
 *   <li>More flexible than MonadTestPattern for custom scenarios
 *   <li>Fluent API for readable test construction
 *   <li>Selective test execution capabilities
 *   <li>Automatic test data validation before execution
 * </ul>
 *
 * @param <F> The Monad witness type
 * @see MonadTestPattern
 * @see ValidationTestBuilder
 */
public final class MonadTestBuilder<F> {

  private final Monad<F> monad;
  private final List<TestStep> testSteps = new ArrayList<>();

  // Test data
  private Kind<F, Integer> validKind;
  private Integer testValue;
  private Function<Integer, String> mapper;
  private Function<Integer, Kind<F, String>> flatMapper;
  private Kind<F, Function<Integer, String>> functionKind;
  private Function<Integer, Kind<F, String>> testFunction;
  private Function<String, Kind<F, String>> chainFunction;
  private BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker;

  // Test configuration flags
  private boolean skipExceptionTests = false;
  private boolean skipLawTests = false;
  private boolean skipBasicTests = false;
  private boolean skipNullTests = false;

  private MonadTestBuilder(Monad<F> monad) {
    this.monad = monad;
  }

  /**
   * Creates a new MonadTestBuilder for the given Monad.
   *
   * @param monad The Monad instance to test
   * @param <F> The Monad witness type
   * @return A new builder instance
   */
  public static <F> MonadTestBuilder<F> forMonad(Monad<F> monad) {
    if (monad == null) {
      throw new IllegalArgumentException("Monad cannot be null");
    }
    return new MonadTestBuilder<>(monad);
  }

  // =============================================================================
  // Configuration Methods
  // =============================================================================

  /**
   * Sets the valid Kind for testing.
   *
   * @param validKind A valid Kind instance
   * @return This builder for chaining
   */
  public MonadTestBuilder<F> withValidKind(Kind<F, Integer> validKind) {
    this.validKind = validKind;
    return this;
  }

  /**
   * Sets the test value for monad law testing.
   *
   * @param testValue The test value
   * @return This builder for chaining
   */
  public MonadTestBuilder<F> withTestValue(Integer testValue) {
    this.testValue = testValue;
    return this;
  }

  /**
   * Sets the mapper function for map operation testing.
   *
   * @param mapper The mapping function
   * @return This builder for chaining
   */
  public MonadTestBuilder<F> withMapper(Function<Integer, String> mapper) {
    this.mapper = mapper;
    return this;
  }

  /**
   * Sets the flatMapper function for flatMap operation testing.
   *
   * @param flatMapper The flatMapping function
   * @return This builder for chaining
   */
  public MonadTestBuilder<F> withFlatMapper(Function<Integer, Kind<F, String>> flatMapper) {
    this.flatMapper = flatMapper;
    return this;
  }

  /**
   * Sets the function Kind for ap operation testing.
   *
   * @param functionKind The function Kind
   * @return This builder for chaining
   */
  public MonadTestBuilder<F> withFunctionKind(Kind<F, Function<Integer, String>> functionKind) {
    this.functionKind = functionKind;
    return this;
  }

  /**
   * Sets the test function for monad law testing.
   *
   * @param testFunction The test function
   * @return This builder for chaining
   */
  public MonadTestBuilder<F> withTestFunction(Function<Integer, Kind<F, String>> testFunction) {
    this.testFunction = testFunction;
    return this;
  }

  /**
   * Sets the chain function for associativity law testing.
   *
   * @param chainFunction The chaining function
   * @return This builder for chaining
   */
  public MonadTestBuilder<F> withChainFunction(Function<String, Kind<F, String>> chainFunction) {
    this.chainFunction = chainFunction;
    return this;
  }

  /**
   * Sets the equality checker for monad law testing.
   *
   * @param equalityChecker The equality predicate
   * @return This builder for chaining
   */
  public MonadTestBuilder<F> withEqualityChecker(
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {
    this.equalityChecker = equalityChecker;
    return this;
  }

  // =============================================================================
  // Test Selection Methods
  // =============================================================================

  /**
   * Adds basic operation tests to the suite.
   *
   * <p>Tests that map, flatMap, and ap operations work correctly.
   *
   * @return This builder for chaining
   */
  public MonadTestBuilder<F> testBasicOperations() {
    testSteps.add(
        () -> {
          validateBasicTestData();
          MonadTestPattern.testBasicOperations(monad, validKind, mapper, flatMapper, functionKind);
        });
    return this;
  }

  /**
   * Adds null validation tests to the suite.
   *
   * <p>Tests that all operations properly reject null parameters.
   *
   * @return This builder for chaining
   */
  public MonadTestBuilder<F> testNullValidations() {
    testSteps.add(
        () -> {
          validateBasicTestData();
          MonadTestPattern.testNullValidations(monad, validKind, mapper, flatMapper, functionKind);
        });
    return this;
  }

  /**
   * Adds exception propagation tests to the suite.
   *
   * <p>Tests that exceptions thrown by functions are properly propagated.
   *
   * @return This builder for chaining
   */
  public MonadTestBuilder<F> testExceptionPropagation() {
    testSteps.add(
        () -> {
          validateKind();
          MonadTestPattern.testExceptionPropagation(monad, validKind);
        });
    return this;
  }

  /**
   * Adds monad law tests to the suite.
   *
   * <p>Tests left identity, right identity, and associativity laws.
   *
   * @return This builder for chaining
   */
  public MonadTestBuilder<F> testMonadLaws() {
    testSteps.add(
        () -> {
          validateLawTestData();
          MonadTestPattern.testMonadLaws(
              monad, validKind, testValue, testFunction, chainFunction, equalityChecker);
        });
    return this;
  }

  /**
   * Adds left identity law test to the suite.
   *
   * @return This builder for chaining
   */
  public MonadTestBuilder<F> testLeftIdentity() {
    testSteps.add(
        () -> {
          validateLawTestData();
          MonadTestPattern.testLeftIdentity(monad, testValue, testFunction, equalityChecker);
        });
    return this;
  }

  /**
   * Adds right identity law test to the suite.
   *
   * @return This builder for chaining
   */
  public MonadTestBuilder<F> testRightIdentity() {
    testSteps.add(
        () -> {
          validateKindAndEqualityChecker();
          MonadTestPattern.testRightIdentity(monad, validKind, equalityChecker);
        });
    return this;
  }

  /**
   * Adds associativity law test to the suite.
   *
   * @return This builder for chaining
   */
  public MonadTestBuilder<F> testAssociativity() {
    testSteps.add(
        () -> {
          validateLawTestData();
          MonadTestPattern.testAssociativity(
              monad, validKind, testFunction, chainFunction, equalityChecker);
        });
    return this;
  }

  // =============================================================================
  // Configuration Flags
  // =============================================================================

  /**
   * Skips exception propagation tests.
   *
   * @return This builder for chaining
   */
  public MonadTestBuilder<F> skipExceptionTests() {
    this.skipExceptionTests = true;
    return this;
  }

  /**
   * Skips monad law tests.
   *
   * @return This builder for chaining
   */
  public MonadTestBuilder<F> skipLawTests() {
    this.skipLawTests = true;
    return this;
  }

  /**
   * Skips basic operation tests.
   *
   * @return This builder for chaining
   */
  public MonadTestBuilder<F> skipBasicTests() {
    this.skipBasicTests = true;
    return this;
  }

  /**
   * Skips null validation tests.
   *
   * @return This builder for chaining
   */
  public MonadTestBuilder<F> skipNullTests() {
    this.skipNullTests = true;
    return this;
  }

  // =============================================================================
  // Execution Methods
  // =============================================================================

  /**
   * Runs all configured tests.
   *
   * <p>Executes all test steps added via testXxx() methods.
   */
  public void execute() {
    if (testSteps.isEmpty()) {
      throw new IllegalStateException(
          "No tests configured. Use testXxx() methods to add tests before calling execute().");
    }

    List<AssertionError> failures = new ArrayList<>();

    for (int i = 0; i < testSteps.size(); i++) {
      try {
        testSteps.get(i).run();
      } catch (AssertionError e) {
        failures.add(new AssertionError("Test step " + (i + 1) + " failed", e));
      }
    }

    if (!failures.isEmpty()) {
      AssertionError combined = new AssertionError(failures.size() + " monad test step(s) failed");
      for (AssertionError failure : failures) {
        combined.addSuppressed(failure);
      }
      throw combined;
    }
  }

  /**
   * Runs all standard monad tests.
   *
   * <p>This is equivalent to calling all testXxx() methods and then execute(). Tests are added
   * based on configuration flags (skipXxx).
   */
  public void testAll() {
    if (!skipBasicTests) {
      testBasicOperations();
    }
    if (!skipNullTests) {
      testNullValidations();
    }
    if (!skipExceptionTests) {
      testExceptionPropagation();
    }
    if (!skipLawTests) {
      testMonadLaws();
    }
    execute();
  }

  // =============================================================================
  // Validation Helpers
  // =============================================================================

  private void validateKind() {
    if (validKind == null) {
      throw new IllegalStateException("validKind must be set before running tests");
    }
  }

  private void validateBasicTestData() {
    validateKind();
    if (mapper == null) {
      throw new IllegalStateException("mapper must be set before running basic operation tests");
    }
    if (flatMapper == null) {
      throw new IllegalStateException(
          "flatMapper must be set before running basic operation tests");
    }
    if (functionKind == null) {
      throw new IllegalStateException(
          "functionKind must be set before running basic operation tests");
    }
  }

  private void validateLawTestData() {
    validateKind();
    if (testValue == null) {
      throw new IllegalStateException("testValue must be set before running law tests");
    }
    if (testFunction == null) {
      throw new IllegalStateException("testFunction must be set before running law tests");
    }
    if (chainFunction == null) {
      throw new IllegalStateException("chainFunction must be set before running law tests");
    }
    if (equalityChecker == null) {
      throw new IllegalStateException("equalityChecker must be set before running law tests");
    }
  }

  private void validateKindAndEqualityChecker() {
    validateKind();
    if (equalityChecker == null) {
      throw new IllegalStateException("equalityChecker must be set before running law tests");
    }
  }

  // =============================================================================
  // Helper Interface
  // =============================================================================

  @FunctionalInterface
  private interface TestStep {
    void run();
  }
}
