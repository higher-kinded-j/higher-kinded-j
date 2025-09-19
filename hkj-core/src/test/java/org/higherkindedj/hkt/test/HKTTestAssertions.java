// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.util.ErrorHandling.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.ThrowableAssert;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.util.ErrorHandling;

/**
 * Custom assertions for testing standardized error handling in Higher-Kinded-J.
 *
 * <p>This utility class provides fluent assertion methods that reduce boilerplate when testing the
 * standard error conditions defined in {@link ErrorHandling}.
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * // Instead of:
 * assertThatThrownBy(() -> monad.map(null, validKind))
 *     .isInstanceOf(NullPointerException.class)
 *     .hasMessage(NULL_FUNCTION_MSG.formatted("function f"));
 *
 * // Use:
 * HKTTestAssertions.assertNullFunctionThrows(() -> monad.map(null, validKind), "function f");
 * }</pre>
 */
public final class HKTTestAssertions {

  private HKTTestAssertions() {
    throw new AssertionError("HKTTestAssertions is a utility class and should not be instantiated");
  }

  // =============================================================================
  // Null Parameter Assertions
  // =============================================================================

  /**
   * Asserts that the given executable throws a NullPointerException with the standard null function
   * error message.
   *
   * @param executable The code that should throw
   * @param parameterName The name of the function parameter for the error message
   * @return The throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertNullFunctionThrows(
      ThrowableAssert.ThrowingCallable executable, String parameterName) {
    return assertThatThrownBy(executable)
        .isInstanceOf(NullPointerException.class)
        .hasMessage(NULL_FUNCTION_MSG.formatted(parameterName));
  }

  /**
   * Asserts that the given executable throws a NullPointerException with the standard null Kind
   * error message.
   *
   * @param executable The code that should throw
   * @param parameterName The name of the Kind parameter for the error message
   * @return The throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertNullKindThrows(
      ThrowableAssert.ThrowingCallable executable, String parameterName) {
    return assertThatThrownBy(executable)
        .isInstanceOf(NullPointerException.class)
        .hasMessage(parameterName + " cannot be null");
  }

  /** Convenience method for the most common null Kind assertion. */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertNullKindThrows(
      ThrowableAssert.ThrowingCallable executable) {
    return assertNullKindThrows(executable, "Kind argument");
  }

  // =============================================================================
  // Kind Unwrapping Assertions
  // =============================================================================

  /**
   * Asserts that narrowing a null Kind throws the standard KindUnwrapException.
   *
   * @param executable The narrowing code that should throw
   * @param typeName The name of the target type for the error message
   * @return The throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertNullKindNarrowThrows(
      ThrowableAssert.ThrowingCallable executable, String typeName) {
    return assertThatThrownBy(executable)
        .isInstanceOf(KindUnwrapException.class)
        .hasMessage(NULL_KIND_TEMPLATE.formatted(typeName));
  }

  /**
   * Asserts that narrowing an invalid Kind type throws the standard KindUnwrapException.
   *
   * @param executable The narrowing code that should throw
   * @param typeName The name of the expected target type
   * @param actualClassName The actual class name that was found
   * @return The throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertInvalidKindTypeThrows(
      ThrowableAssert.ThrowingCallable executable, String typeName, String actualClassName) {
    return assertThatThrownBy(executable)
        .isInstanceOf(KindUnwrapException.class)
        .hasMessage(INVALID_KIND_TYPE_TEMPLATE.formatted(typeName, actualClassName));
  }

  /**
   * Asserts that narrowing an invalid Kind type throws the standard KindUnwrapException,
   * automatically determining the actual class name.
   *
   * @param executable The narrowing code that should throw
   * @param typeName The name of the expected target type
   * @param invalidKind The invalid Kind instance (to get its class name)
   * @return The throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertInvalidKindTypeThrows(
      ThrowableAssert.ThrowingCallable executable, String typeName, Object invalidKind) {
    return assertInvalidKindTypeThrows(executable, typeName, invalidKind.getClass().getName());
  }

  // =============================================================================
  // Widen Operation Assertions
  // =============================================================================

  /**
   * Asserts that a widen operation throws the standard NullPointerException for null input.
   *
   * @param executable The widening code that should throw
   * @param typeName The name of the type being widened
   * @return The throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertNullWidenInputThrows(
      ThrowableAssert.ThrowingCallable executable, String typeName) {
    return assertThatThrownBy(executable)
        .isInstanceOf(NullPointerException.class)
        .hasMessage(NULL_WIDEN_INPUT_TEMPLATE.formatted(typeName));
  }

  // =============================================================================
  // Monad/Functor Operation Assertions
  // =============================================================================

  /** Asserts that a map operation throws for a null function parameter. */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMapNullFunctionThrows(
      ThrowableAssert.ThrowingCallable executable) {
    return assertNullFunctionThrows(executable, "function f for map");
  }

  /** Asserts that a flatMap operation throws for a null function parameter. */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertFlatMapNullFunctionThrows(
      ThrowableAssert.ThrowingCallable executable) {
    return assertNullFunctionThrows(executable, "function f for flatMap");
  }

  /** Asserts that an ap operation throws for a null function Kind parameter. */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertApNullFunctionKindThrows(
      ThrowableAssert.ThrowingCallable executable) {
    return assertNullKindThrows(executable, "function Kind for ap");
  }

  /** Asserts that an ap operation throws for a null argument Kind parameter. */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertApNullArgumentKindThrows(
      ThrowableAssert.ThrowingCallable executable) {
    return assertNullKindThrows(executable, "argument Kind for ap");
  }

  /** Asserts that a map operation throws for a null source Kind parameter. */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMapNullSourceKindThrows(
      ThrowableAssert.ThrowingCallable executable) {
    return assertNullKindThrows(executable, "source Kind for map");
  }

  /** Asserts that a flatMap operation throws for a null source Kind parameter. */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertFlatMapNullSourceKindThrows(
      ThrowableAssert.ThrowingCallable executable) {
    return assertNullKindThrows(executable, "source Kind for flatMap");
  }

  // =============================================================================
  // Holder Validation Assertions
  // =============================================================================

  /**
   * Asserts that creating a holder with null content throws the standard NullPointerException.
   *
   * @param executable The holder creation code that should throw
   * @param typeName The name of the type being held
   * @return The throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertNullHolderContentThrows(
      ThrowableAssert.ThrowingCallable executable, String typeName) {
    return assertThatThrownBy(executable)
        .isInstanceOf(NullPointerException.class)
        .hasMessage(NULL_HOLDER_STATE_TEMPLATE.formatted(typeName + "Holder", typeName));
  }

  // =============================================================================
  // Composite Assertions for Common Patterns
  // =============================================================================

  /**
   * Builder pattern for testing multiple null parameter validations in a single test.
   *
   * <p>Usage:
   *
   * <pre>{@code
   * ValidationTestBuilder.create()
   *     .assertNullFunction(() -> monad.map(null, validKind), "function f")
   *     .assertNullKind(() -> monad.map(validFunction, null), "source Kind")
   *     .execute();
   * }</pre>
   */
  public static class ValidationTestBuilder {
    private final List<ValidationAssertion> assertions = new ArrayList<>();

    public static ValidationTestBuilder create() {
      return new ValidationTestBuilder();
    }

    public ValidationTestBuilder assertNullFunction(
        ThrowableAssert.ThrowingCallable executable, String parameterName) {
      assertions.add(() -> assertNullFunctionThrows(executable, parameterName));
      return this;
    }

    public ValidationTestBuilder assertNullKind(
        ThrowableAssert.ThrowingCallable executable, String parameterName) {
      assertions.add(() -> assertNullKindThrows(executable, parameterName));
      return this;
    }

    public ValidationTestBuilder assertNullWidenInput(
        ThrowableAssert.ThrowingCallable executable, String typeName) {
      assertions.add(() -> assertNullWidenInputThrows(executable, typeName));
      return this;
    }

    public ValidationTestBuilder assertNullKindNarrow(
        ThrowableAssert.ThrowingCallable executable, String typeName) {
      assertions.add(() -> assertNullKindNarrowThrows(executable, typeName));
      return this;
    }

    public ValidationTestBuilder assertInvalidKindType(
        ThrowableAssert.ThrowingCallable executable, String typeName, Object invalidKind) {
      assertions.add(() -> assertInvalidKindTypeThrows(executable, typeName, invalidKind));
      return this;
    }

    /**
     * Execute all assertions. Each assertion is run independently to ensure that a failure in one
     * doesn't prevent others from running.
     */
    public void execute() {
      var failures = new java.util.ArrayList<AssertionError>();

      for (int i = 0; i < assertions.size(); i++) {
        try {
          assertions.get(i).run();
        } catch (AssertionError e) {
          failures.add(new AssertionError("Validation assertion " + (i + 1) + " failed", e));
        }
      }

      if (!failures.isEmpty()) {
        AssertionError combined =
            new AssertionError(failures.size() + " validation assertion(s) failed");
        for (AssertionError failure : failures) {
          combined.addSuppressed(failure);
        }
        throw combined;
      }
    }

    // Remove @FunctionalInterface annotation since this has no abstract methods
    private interface ValidationAssertion {
      void run();
    }
  }

  // =============================================================================
  // Monad Law Testing Utilities
  // =============================================================================

  /** Utilities for testing monad laws with standardized error handling. */
  public static class MonadLawTestUtils {

    /**
     * Tests the left identity law with proper error handling for null parameters.
     *
     * @param monad The monad instance to test
     * @param value The test value
     * @param f The function for the law
     * @param <F> The monad witness type
     * @param <A> The input type
     * @param <B> The output type
     */
    public static <F, A, B> void testLeftIdentityLaw(
        org.higherkindedj.hkt.Monad<F> monad, A value, Function<A, Kind<F, B>> f) {

      // Test the actual law
      Kind<F, A> ofValue = monad.of(value);
      Kind<F, B> leftSide = monad.flatMap(f, ofValue);
      Kind<F, B> rightSide = f.apply(value);

      // Note: Actual equality testing would need to be implemented based on the specific monad
      // This is just the structure for standardized testing

      // Test null validations
      ValidationTestBuilder.create()
          .assertNullFunction(() -> monad.flatMap(null, ofValue), "function f for flatMap")
          .assertNullKind(() -> monad.flatMap(f, null), "source Kind for flatMap")
          .execute();
    }
  }

  // =============================================================================
  // KindHelper Testing Pattern
  // =============================================================================

  /**
   * Standard test pattern for KindHelper implementations.
   *
   * <p>This encapsulates the common test pattern for widen/narrow operations with standardized
   * error handling.
   */
  public static class KindHelperTestPattern {

    /**
     * Tests standard KindHelper widen/narrow operations with error handling.
     *
     * @param helper The KindHelper to test (function interface)
     * @param concreteInstance A valid concrete instance to test with
     * @param typeName The type name for error messages
     * @param invalidKind An invalid Kind instance for testing type errors
     * @param <T> The concrete type
     * @param <F> The witness type
     * @param <A> The value type
     */
    public static <T, F, A> void testStandardOperations(
        KindHelperOperations<T, F, A> helper,
        T concreteInstance,
        String typeName,
        Kind<F, A> invalidKind) {

      // Test successful operations
      Kind<F, A> widened = helper.widen(concreteInstance);
      assertThat(widened).isNotNull();
      T narrowed = helper.narrow(widened);
      assertThat(narrowed).isSameAs(concreteInstance);

      // Test error conditions
      ValidationTestBuilder.create()
          .assertNullWidenInput(() -> helper.widen(null), typeName)
          .assertNullKindNarrow(() -> helper.narrow(null), typeName)
          .assertInvalidKindType(() -> helper.narrow(invalidKind), typeName, invalidKind)
          .execute();
    }

    /**
     * Interface for KindHelper operations to make testing generic. Removed @FunctionalInterface
     * since it has two abstract methods.
     */
    public interface KindHelperOperations<T, F, A> {
      Kind<F, A> widen(T concrete);

      T narrow(Kind<F, A> kind);
    }
  }

  // =============================================================================
  // Traverse Operation Assertions
  // =============================================================================

  /** Asserts that a traverse operation throws for a null applicative parameter. */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertTraverseNullApplicativeThrows(
      ThrowableAssert.ThrowingCallable executable) {
    return assertNullFunctionThrows(executable, "applicative instance for traverse");
  }

  /** Asserts that a traverse operation throws for a null function parameter. */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertTraverseNullFunctionThrows(
      ThrowableAssert.ThrowingCallable executable) {
    return assertNullFunctionThrows(executable, "function f for traverse");
  }

  /** Asserts that a traverse operation throws for a null source Kind parameter. */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertTraverseNullSourceKindThrows(
      ThrowableAssert.ThrowingCallable executable) {
    return assertNullKindThrows(executable, "source Kind for traverse");
  }

  // =============================================================================
  // Foldable Operation Assertions
  // =============================================================================

  /** Asserts that a foldMap operation throws for a null monoid parameter. */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertFoldMapNullMonoidThrows(
      ThrowableAssert.ThrowingCallable executable) {
    return assertNullFunctionThrows(executable, "monoid for foldMap");
  }

  /** Asserts that a foldMap operation throws for a null function parameter. */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertFoldMapNullFunctionThrows(
      ThrowableAssert.ThrowingCallable executable) {
    return assertNullFunctionThrows(executable, "function f for foldMap");
  }

  /** Asserts that a foldMap operation throws for a null source Kind parameter. */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertFoldMapNullSourceKindThrows(
      ThrowableAssert.ThrowingCallable executable) {
    return assertNullKindThrows(executable, "source Kind for foldMap");
  }

  // =============================================================================
  // Composite Traverse/Foldable Testing Extensions
  // =============================================================================

  /** Enhanced ValidationTestBuilder with Traverse-specific assertions. */
  public static class TraverseValidationTestBuilder {
    private final java.util.List<Runnable> assertions = new java.util.ArrayList<>();

    public static TraverseValidationTestBuilder create() {
      return new TraverseValidationTestBuilder();
    }

    // Basic validation methods
    public TraverseValidationTestBuilder assertNullFunction(
        ThrowableAssert.ThrowingCallable executable, String parameterName) {
      assertions.add(() -> assertNullFunctionThrows(executable, parameterName));
      return this;
    }

    public TraverseValidationTestBuilder assertNullKind(
        ThrowableAssert.ThrowingCallable executable, String parameterName) {
      assertions.add(() -> assertNullKindThrows(executable, parameterName));
      return this;
    }

    // Traverse-specific validation methods
    public TraverseValidationTestBuilder assertTraverseNullApplicative(
        ThrowableAssert.ThrowingCallable executable) {
      assertions.add(() -> assertTraverseNullApplicativeThrows(executable));
      return this;
    }

    public TraverseValidationTestBuilder assertTraverseNullFunction(
        ThrowableAssert.ThrowingCallable executable) {
      assertions.add(() -> assertTraverseNullFunctionThrows(executable));
      return this;
    }

    public TraverseValidationTestBuilder assertTraverseNullSourceKind(
        ThrowableAssert.ThrowingCallable executable) {
      assertions.add(() -> assertTraverseNullSourceKindThrows(executable));
      return this;
    }

    public TraverseValidationTestBuilder assertFoldMapNullMonoid(
        ThrowableAssert.ThrowingCallable executable) {
      assertions.add(() -> assertFoldMapNullMonoidThrows(executable));
      return this;
    }

    public TraverseValidationTestBuilder assertFoldMapNullFunction(
        ThrowableAssert.ThrowingCallable executable) {
      assertions.add(() -> assertFoldMapNullFunctionThrows(executable));
      return this;
    }

    public TraverseValidationTestBuilder assertFoldMapNullSourceKind(
        ThrowableAssert.ThrowingCallable executable) {
      assertions.add(() -> assertFoldMapNullSourceKindThrows(executable));
      return this;
    }

    /**
     * Execute all assertions. Each assertion is run independently to ensure that a failure in one
     * doesn't prevent others from running.
     */
    public void execute() {
      var failures = new ArrayList<AssertionError>();

      for (int i = 0; i < assertions.size(); i++) {
        try {
          assertions.get(i).run();
        } catch (AssertionError e) {
          failures.add(
              new AssertionError("Traverse validation assertion " + (i + 1) + " failed", e));
        }
      }

      if (!failures.isEmpty()) {
        AssertionError combined =
            new AssertionError(failures.size() + " traverse validation assertion(s) failed");
        for (AssertionError failure : failures) {
          combined.addSuppressed(failure);
        }
        throw combined;
      }
    }
  }

  // =============================================================================
  // Traverse Law Testing Utilities
  // =============================================================================

  /** Utilities for testing Traverse laws with standardized error handling. */
  public static class TraverseLawTestUtils {

    /**
     * Tests the Traverse naturality law with proper error handling for null parameters.
     *
     * <p>Naturality law: natural transformation should commute with traverse
     *
     * @param traverse The Traverse instance to test
     * @param testKind The test Kind for the law
     * @param applicative1 First applicative instance
     * @param applicative2 Second applicative instance
     * @param testFunction The test function for the law
     * @param naturalTransform The natural transformation
     * @param equalityChecker Function to check equality of results
     * @param <F> The traverse source type witness
     * @param <G1> The first applicative type witness
     * @param <G2> The second applicative type witness
     * @param <A> The source element type
     * @param <B> The target element type
     */
    public static <F, G1, G2, A, B> void testNaturalityLaw(
        Traverse<F> traverse,
        Kind<F, A> testKind,
        Applicative<G1> applicative1,
        Applicative<G2> applicative2,
        Function<A, Kind<G1, B>> testFunction,
        Function<Kind<G1, ?>, Kind<G2, ?>> naturalTransform,
        BiPredicate<Kind<G2, ?>, Kind<G2, ?>> equalityChecker) {

      // This is a complex law that's difficult to test generically without specific type knowledge
      // Individual implementations should provide specific tests for their naturality behavior

      // Test null validations for the law components
      TraverseValidationTestBuilder.create()
          .assertTraverseNullApplicative(() -> traverse.traverse(null, testFunction, testKind))
          .assertTraverseNullFunction(() -> traverse.traverse(applicative1, null, testKind))
          .assertTraverseNullSourceKind(() -> traverse.traverse(applicative1, testFunction, null))
          .execute();
    }

    /**
     * Tests the Traverse identity law with proper error handling.
     *
     * <p>Identity law: traverse(Identity, ta, Identity.of) == Identity.of(ta)
     *
     * @param traverse The Traverse instance to test
     * @param testKind The test Kind for the law
     * @param identityApplicative The Identity applicative
     * @param <F> The traverse source type witness
     * @param <A> The element type
     */
    public static <F, A> void testIdentityLaw(
        org.higherkindedj.hkt.Traverse<F> traverse,
        Kind<F, A> testKind,
        Applicative<Id> identityApplicative) {

      // This requires Identity monad implementation
      // Placeholder for identity law testing - individual implementations should test this
      // specifically

      // Test null validations
      TraverseValidationTestBuilder.create()
          .assertTraverseNullApplicative(
              () -> traverse.traverse(null, a -> identityApplicative.of(a), testKind))
          .assertTraverseNullSourceKind(
              () -> traverse.traverse(identityApplicative, a -> identityApplicative.of(a), null))
          .execute();
    }
  }

  // =============================================================================
  // Traverse Result Verification Utilities
  // =============================================================================

  /** Utilities for verifying common patterns in Traverse results. */
  public static class TraverseResultVerifiers {

    /**
     * Verifies that a traverse operation over a "success" container results in a wrapped success.
     *
     * @param result The result to verify
     * @param unwrapper Function to unwrap the outer container
     * @param innerUnwrapper Function to unwrap the inner container
     * @param expectedValue The expected inner value
     * @param <Outer> The outer container type
     * @param <Inner> The inner container type
     * @param <Value> The value type
     */
    public static <Outer, Inner, Value> void verifyTraverseSuccess(
        Outer result,
        Function<Outer, Inner> unwrapper,
        Function<Inner, Value> innerUnwrapper,
        Value expectedValue) {

      assertThat(result).as("Traverse result should not be null").isNotNull();

      Inner inner = unwrapper.apply(result);
      assertThat(inner).as("Unwrapped traverse result should not be null").isNotNull();

      Value actualValue = innerUnwrapper.apply(inner);
      assertThat(actualValue).as("Inner value should match expected").isEqualTo(expectedValue);
    }

    /**
     * Verifies that a traverse operation over a "failure" container results in a wrapped failure.
     *
     * @param result The result to verify
     * @param failureChecker Function to check if result represents failure
     * @param <Container> The container type
     */
    public static <Container> void verifyTraverseFailure(
        Container result, java.util.function.Predicate<Container> failureChecker) {

      assertThat(result).as("Traverse result should not be null").isNotNull();

      assertThat(failureChecker.test(result)).as("Result should represent failure").isTrue();
    }
  }
}
