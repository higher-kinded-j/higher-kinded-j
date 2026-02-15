// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.base;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base class for type class testing with standard fixture setup.
 *
 * <p>Provides a standardised approach to setting up test fixtures for type class implementations.
 * Subclasses must implement abstract methods to provide their specific test values.
 *
 * <h2>Benefits:</h2>
 *
 * <ul>
 *   <li>Consistent fixture naming across all type class tests
 *   <li>Reduced boilerplate in test classes
 *   <li>Clear separation of fixture creation from test logic
 *   <li>Enforces standardised test structure
 * </ul>
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>{@code
 * @DisplayName("MyMonad Complete Test Suite")
 * class MyMonadTest extends TypeClassTestBase<MyMonadKind.Witness<String>, Integer, String> {
 *
 *     private MyMonad<String> monad;
 *
 *     @Override
 *     protected Kind<MyMonadKind.Witness<String>, Integer> createValidKind() {
 *         return HELPER.widen(MyMonad.of(42));
 *     }
 *
 *     @Override
 *     protected Kind<MyMonadKind.Witness<String>, Integer> createValidKind2() {
 *         return HELPER.widen(MyMonad.of(24));
 *     }
 *
 *     @Override
 *     protected Function<Integer, String> createValidMapper() {
 *         return TestFunctions.INT_TO_STRING;
 *     }
 *
 *     @Override
 *     protected BiPredicate<Kind<MyMonadKind.Witness<String>, ?>,
 *                           Kind<MyMonadKind.Witness<String>, ?>> createEqualityChecker() {
 *         return (k1, k2) -> HELPER.narrow(k1).equals(HELPER.narrow(k2));
 *     }
 *
 *     // Additional setup
 *     @BeforeEach
 *     void setUpMonad() {
 *         monad = MyMonad.instance();
 *         // Use the fixtures from base class: validKind, validKind2, validMapper, etc.
 *     }
 *
 *     @Test
 *     void testSomething() {
 *         // Use validKind, validMapper, equalityChecker, etc.
 *         Kind<MyMonadKind.Witness<String>, String> result =
 *             monad.map(validMapper, validKind);
 *         // assertions...
 *     }
 * }
 * }</pre>
 *
 * @param <F> The type class witness type
 * @param <A> The input type for test values
 * @param <B> The output type for mapped values
 */
public abstract class TypeClassTestBase<F extends WitnessArity<TypeArity.Unary>, A, B> {

  // ============================================================================
  // Standard Test Fixtures
  // ============================================================================

  /** Primary test Kind - always required */
  protected Kind<F, A> validKind;

  /** Secondary test Kind - required for operations like map2, ap */
  protected Kind<F, A> validKind2;

  /** Standard mapper function (A -> B) */
  protected Function<A, B> validMapper;

  /** Secondary mapper function (B -> String) for law testing */
  protected Function<B, String> secondMapper;

  /** FlatMap function (A -> Kind<F, B>) for Monad testing */
  protected Function<A, Kind<F, B>> validFlatMapper;

  /** Function Kind for Applicative testing */
  protected Kind<F, Function<A, B>> validFunctionKind;

  /** Combining function for map2 operations */
  protected BiFunction<A, A, B> validCombiningFunction;

  /** Test value for law testing */
  protected A testValue;

  /** Test function for law testing (A -> Kind<F, B>) */
  protected Function<A, Kind<F, B>> testFunction;

  /** Chain function for associativity testing (B -> Kind<F, B>) */
  protected Function<B, Kind<F, B>> chainFunction;

  /** Equality checker for law testing */
  protected BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker;

  // ============================================================================
  // Setup Method
  // ============================================================================

  /**
   * Sets up standard test fixtures by calling abstract factory methods.
   *
   * <p>Subclasses can add additional setup by creating their own {@code @BeforeEach} methods. The
   * base fixtures will be available for use in those methods.
   */
  @BeforeEach
  final void setUpBase() {
    // Required fixtures
    validKind = createValidKind();
    validKind2 = createValidKind2();
    validMapper = createValidMapper();
    equalityChecker = createEqualityChecker();

    // Optional fixtures with sensible defaults
    secondMapper = createSecondMapper();
    validFlatMapper = createValidFlatMapper();
    validFunctionKind = createValidFunctionKind();
    validCombiningFunction = createValidCombiningFunction();
    testValue = createTestValue();
    testFunction = createTestFunction();
    chainFunction = createChainFunction();
  }

  // ============================================================================
  // Required Abstract Methods
  // ============================================================================

  /**
   * Creates the primary test Kind.
   *
   * <p>This should be a valid instance of your type class containing a test value.
   *
   * @return A non-null Kind<F, A> for testing
   */
  protected abstract Kind<F, A> createValidKind();

  /**
   * Creates a secondary test Kind for binary operations.
   *
   * <p>Used in operations like map2, ap that require two Kinds.
   *
   * @return A non-null Kind<F, A> for testing
   */
  protected abstract Kind<F, A> createValidKind2();

  /**
   * Creates the primary mapper function (A -> B).
   *
   * <p>This is used in map operations and should be a simple, deterministic transformation.
   *
   * @return A non-null mapping function
   */
  protected abstract Function<A, B> createValidMapper();

  /**
   * Creates the equality checker for law testing.
   *
   * <p>This determines how to compare two Kinds for equality. Common implementations:
   *
   * <ul>
   *   <li>Reference equality: {@code (k1, k2) -> k1 == k2}
   *   <li>Value equality: {@code (k1, k2) -> narrow(k1).equals(narrow(k2))}
   *   <li>Custom comparison: {@code (k1, k2) -> customCompare(narrow(k1), narrow(k2))}
   * </ul>
   *
   * @return A non-null equality checker
   */
  protected abstract BiPredicate<Kind<F, ?>, Kind<F, ?>> createEqualityChecker();

  // ============================================================================
  // Optional Override Methods (with defaults)
  // ============================================================================

  /**
   * Creates the secondary mapper function (B -> String) for composition testing.
   *
   * <p>Default implementation uses {@code Object::toString}.
   *
   * @return A non-null mapping function
   */
  protected Function<B, String> createSecondMapper() {
    return Object::toString;
  }

  /**
   * Creates the flatMap function (A -> Kind<F, B>) for Monad testing.
   *
   * <p>Default implementation returns null - override if testing Monad operations.
   *
   * @return A flatMap function, or null if not needed
   */
  protected Function<A, Kind<F, B>> createValidFlatMapper() {
    return null;
  }

  /**
   * Creates the function Kind for Applicative testing.
   *
   * <p>Default implementation returns null - override if testing Applicative operations.
   *
   * @return A function Kind, or null if not needed
   */
  protected Kind<F, Function<A, B>> createValidFunctionKind() {
    return null;
  }

  /**
   * Creates the combining function for map2 operations.
   *
   * <p>Default implementation returns null - override if testing map2 operations.
   *
   * @return A combining function, or null if not needed
   */
  protected BiFunction<A, A, B> createValidCombiningFunction() {
    return null;
  }

  /**
   * Creates a test value for law testing.
   *
   * <p>Default implementation returns null - override if testing laws.
   *
   * @return A test value, or null if not needed
   */
  protected A createTestValue() {
    return null;
  }

  /**
   * Creates the test function for law testing.
   *
   * <p>Default implementation returns null - override if testing laws.
   *
   * @return A test function, or null if not needed
   */
  protected Function<A, Kind<F, B>> createTestFunction() {
    return null;
  }

  /**
   * Creates the chain function for associativity testing.
   *
   * <p>Default implementation returns null - override if testing associativity.
   *
   * @return A chain function, or null if not needed
   */
  protected Function<B, Kind<F, B>> createChainFunction() {
    return null;
  }

  // ============================================================================
  // Helper Methods for Subclasses
  // ============================================================================

  /**
   * Validates that all required fixtures have been initialised.
   *
   * <p>Call this in your test methods if you want to ensure fixtures are properly set up.
   *
   * @throws IllegalStateException if required fixtures are null
   */
  protected final void validateRequiredFixtures() {
    if (validKind == null) {
      throw new IllegalStateException("validKind must be initialised");
    }
    if (validKind2 == null) {
      throw new IllegalStateException("validKind2 must be initialised");
    }
    if (validMapper == null) {
      throw new IllegalStateException("validMapper must be initialised");
    }
    if (equalityChecker == null) {
      throw new IllegalStateException("equalityChecker must be initialised");
    }
  }

  /**
   * Validates that Monad-specific fixtures have been initialised.
   *
   * <p>Call this before running Monad tests.
   *
   * @throws IllegalStateException if Monad fixtures are null
   */
  protected final void validateMonadFixtures() {
    validateRequiredFixtures();
    if (validFlatMapper == null) {
      throw new IllegalStateException("validFlatMapper must be initialised for Monad tests");
    }
  }

  /**
   * Validates that Applicative-specific fixtures have been initialised.
   *
   * <p>Call this before running Applicative tests.
   *
   * @throws IllegalStateException if Applicative fixtures are null
   */
  protected final void validateApplicativeFixtures() {
    validateRequiredFixtures();
    if (validFunctionKind == null) {
      throw new IllegalStateException(
          "validFunctionKind must be initialised for Applicative tests");
    }
    if (validCombiningFunction == null) {
      throw new IllegalStateException(
          "validCombiningFunction must be initialised for Applicative tests");
    }
  }

  /**
   * Validates that law testing fixtures have been initialised.
   *
   * <p>Call this before running law tests.
   *
   * @throws IllegalStateException if law testing fixtures are null
   */
  protected final void validateLawTestingFixtures() {
    validateRequiredFixtures();
    if (testValue == null) {
      throw new IllegalStateException("testValue must be initialised for law testing");
    }
    if (testFunction == null) {
      throw new IllegalStateException("testFunction must be initialised for law testing");
    }
  }

  // ============================================================================
  // Documentation
  // ============================================================================

  /**
   * Provides information about which fixtures are available.
   *
   * <p>Useful for debugging and understanding what has been initialised.
   *
   * @return A string describing the fixture state
   */
  protected final String getFixtureStatus() {
    StringBuilder status = new StringBuilder("Fixture Status:\n");
    status.append("  validKind:              ").append(validKind != null ? "✓" : "✗").append("\n");
    status.append("  validKind2:             ").append(validKind2 != null ? "✓" : "✗").append("\n");
    status
        .append("  validMapper:            ")
        .append(validMapper != null ? "✓" : "✗")
        .append("\n");
    status
        .append("  secondMapper:           ")
        .append(secondMapper != null ? "✓" : "✗")
        .append("\n");
    status
        .append("  validFlatMapper:        ")
        .append(validFlatMapper != null ? "✓" : "✗")
        .append("\n");
    status
        .append("  validFunctionKind:      ")
        .append(validFunctionKind != null ? "✓" : "✗")
        .append("\n");
    status
        .append("  validCombiningFunction: ")
        .append(validCombiningFunction != null ? "✓" : "✗")
        .append("\n");
    status.append("  testValue:              ").append(testValue != null ? "✓" : "✗").append("\n");
    status
        .append("  testFunction:           ")
        .append(testFunction != null ? "✓" : "✗")
        .append("\n");
    status
        .append("  chainFunction:          ")
        .append(chainFunction != null ? "✓" : "✗")
        .append("\n");
    status
        .append("  equalityChecker:        ")
        .append(equalityChecker != null ? "✓" : "✗")
        .append("\n");
    return status.toString();
  }
}
