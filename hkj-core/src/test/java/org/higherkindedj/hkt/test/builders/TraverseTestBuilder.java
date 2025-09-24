// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.test.patterns.TraverseTestPattern;

/**
 * Fluent builder for comprehensive Traverse testing.
 *
 * <p>Provides a fluent API for building and executing complete Traverse test suites. This builder
 * offers more flexibility than {@link TraverseTestPattern} by allowing selective test execution and
 * custom configurations.
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Complete Test Suite:</h3>
 *
 * <pre>{@code
 * TraverseTestBuilder.forTraverse(traverse)
 *     .withValidKind(validKind)
 *     .withMapper(validMapper)
 *     .withApplicative(validApplicative)
 *     .withTraverseFunction(traverseFunction)
 *     .withMonoid(validMonoid)
 *     .withFoldMapFunction(foldMapFunction)
 *     .testAll();
 * }</pre>
 *
 * <h3>Selective Testing:</h3>
 *
 * <pre>{@code
 * TraverseTestBuilder.forTraverse(traverse)
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
 * TraverseTestBuilder.forTraverse(traverse)
 *     .withValidKind(validKind)
 *     .skipExceptionTests()
 *     .testAll();
 * }</pre>
 *
 * @param <F> The Traverse witness type
 * @see TraverseTestPattern
 * @see ValidationTestBuilder
 */
public final class TraverseTestBuilder<F> {

  private final Traverse<F> traverse;
  private final List<TestStep> testSteps = new ArrayList<>();

  // Test data - using raw types with @SuppressWarnings for flexibility
  @SuppressWarnings("rawtypes")
  private Kind validKind;

  @SuppressWarnings("rawtypes")
  private Function mapper;

  @SuppressWarnings("rawtypes")
  private Applicative applicative;

  @SuppressWarnings("rawtypes")
  private Function traverseFunction;

  @SuppressWarnings("rawtypes")
  private Monoid monoid;

  @SuppressWarnings("rawtypes")
  private Function foldMapFunction;

  // Configuration flags
  private boolean skipExceptionTests = false;
  private boolean skipBasicTests = false;
  private boolean skipNullTests = false;

  private TraverseTestBuilder(Traverse<F> traverse) {
    this.traverse = traverse;
  }

  /**
   * Creates a new TraverseTestBuilder for the given Traverse.
   *
   * @param traverse The Traverse instance to test
   * @param <F> The Traverse witness type
   * @return A new builder instance
   */
  public static <F> TraverseTestBuilder<F> forTraverse(Traverse<F> traverse) {
    if (traverse == null) {
      throw new IllegalArgumentException("Traverse cannot be null");
    }
    return new TraverseTestBuilder<>(traverse);
  }

  // =============================================================================
  // Configuration Methods
  // =============================================================================

  /**
   * Sets the valid Kind for testing.
   *
   * @param validKind A valid Kind instance
   * @param <A> The value type
   * @return This builder for chaining
   */
  @SuppressWarnings("unchecked")
  public <A> TraverseTestBuilder<F> withValidKind(Kind<F, A> validKind) {
    this.validKind = validKind;
    return this;
  }

  /**
   * Sets the mapper function for map operation testing.
   *
   * @param mapper The mapping function
   * @param <A> The input type
   * @param <B> The output type
   * @return This builder for chaining
   */
  @SuppressWarnings("unchecked")
  public <A, B> TraverseTestBuilder<F> withMapper(Function<A, B> mapper) {
    this.mapper = mapper;
    return this;
  }

  /**
   * Sets the Applicative instance for traverse testing.
   *
   * @param applicative The Applicative instance
   * @param <G> The Applicative witness type
   * @return This builder for chaining
   */
  @SuppressWarnings("unchecked")
  public <G> TraverseTestBuilder<F> withApplicative(Applicative<G> applicative) {
    this.applicative = applicative;
    return this;
  }

  /**
   * Sets the traverse function for traverse operation testing.
   *
   * @param traverseFunction The traverse function
   * @param <A> The input type
   * @param <G> The Applicative witness type
   * @param <B> The output type
   * @return This builder for chaining
   */
  @SuppressWarnings("unchecked")
  public <A, G, B> TraverseTestBuilder<F> withTraverseFunction(
      Function<A, Kind<G, B>> traverseFunction) {
    this.traverseFunction = traverseFunction;
    return this;
  }

  /**
   * Sets the Monoid for foldMap testing.
   *
   * @param monoid The Monoid instance
   * @param <M> The Monoid type
   * @return This builder for chaining
   */
  @SuppressWarnings("unchecked")
  public <M> TraverseTestBuilder<F> withMonoid(Monoid<M> monoid) {
    this.monoid = monoid;
    return this;
  }

  /**
   * Sets the foldMap function for foldMap operation testing.
   *
   * @param foldMapFunction The foldMap function
   * @param <A> The input type
   * @param <M> The Monoid type
   * @return This builder for chaining
   */
  @SuppressWarnings("unchecked")
  public <A, M> TraverseTestBuilder<F> withFoldMapFunction(Function<A, M> foldMapFunction) {
    this.foldMapFunction = foldMapFunction;
    return this;
  }

  // =============================================================================
  // Test Selection Methods
  // =============================================================================

  /**
   * Adds basic operation tests to the suite.
   *
   * @return This builder for chaining
   */
  public TraverseTestBuilder<F> testBasicOperations() {
    testSteps.add(
        () -> {
          validateBasicTestData();
          invokeBasicOperationsTest();
        });
    return this;
  }

  /**
   * Adds null validation tests to the suite.
   *
   * @return This builder for chaining
   */
  public TraverseTestBuilder<F> testNullValidations() {
    testSteps.add(
        () -> {
          validateBasicTestData();
          invokeNullValidationsTest();
        });
    return this;
  }

  /**
   * Adds exception propagation tests to the suite.
   *
   * @return This builder for chaining
   */
  public TraverseTestBuilder<F> testExceptionPropagation() {
    testSteps.add(
        () -> {
          validateExceptionTestData();
          invokeExceptionPropagationTest();
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
  public TraverseTestBuilder<F> skipExceptionTests() {
    this.skipExceptionTests = true;
    return this;
  }

  /**
   * Skips basic operation tests.
   *
   * @return This builder for chaining
   */
  public TraverseTestBuilder<F> skipBasicTests() {
    this.skipBasicTests = true;
    return this;
  }

  /**
   * Skips null validation tests.
   *
   * @return This builder for chaining
   */
  public TraverseTestBuilder<F> skipNullTests() {
    this.skipNullTests = true;
    return this;
  }

  // =============================================================================
  // Execution Methods
  // =============================================================================

  /** Runs all configured tests. */
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
      AssertionError combined =
          new AssertionError(failures.size() + " traverse test step(s) failed");
      for (AssertionError failure : failures) {
        combined.addSuppressed(failure);
      }
      throw combined;
    }
  }

  /** Runs all standard traverse tests. */
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
    execute();
  }

  // =============================================================================
  // Validation and Invocation Helpers
  // =============================================================================

  private void validateBasicTestData() {
    if (validKind == null) {
      throw new IllegalStateException("validKind must be set");
    }
    if (mapper == null) {
      throw new IllegalStateException("mapper must be set");
    }
    if (applicative == null) {
      throw new IllegalStateException("applicative must be set");
    }
    if (traverseFunction == null) {
      throw new IllegalStateException("traverseFunction must be set");
    }
    if (monoid == null) {
      throw new IllegalStateException("monoid must be set");
    }
    if (foldMapFunction == null) {
      throw new IllegalStateException("foldMapFunction must be set");
    }
  }

  private void validateExceptionTestData() {
    if (validKind == null) {
      throw new IllegalStateException("validKind must be set");
    }
    if (applicative == null) {
      throw new IllegalStateException("applicative must be set");
    }
    if (monoid == null) {
      throw new IllegalStateException("monoid must be set");
    }
  }

  @SuppressWarnings("unchecked")
  private void invokeBasicOperationsTest() {
    TraverseTestPattern.testBasicOperations(
        traverse, validKind, mapper, applicative, traverseFunction, monoid, foldMapFunction);
  }

  @SuppressWarnings("unchecked")
  private void invokeNullValidationsTest() {
    TraverseTestPattern.testNullValidations(
        traverse, validKind, mapper, applicative, traverseFunction, monoid, foldMapFunction);
  }

  @SuppressWarnings("unchecked")
  private void invokeExceptionPropagationTest() {
    TraverseTestPattern.testExceptionPropagation(traverse, validKind, applicative, monoid);
  }

  @FunctionalInterface
  private interface TestStep {
    void run();
  }
}
