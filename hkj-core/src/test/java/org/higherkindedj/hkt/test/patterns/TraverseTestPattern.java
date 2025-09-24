// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.patterns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.test.data.TestData;

/**
 * Complete test pattern for Traverse implementations.
 *
 * <p>Provides a comprehensive test suite that can be run with a single method call, including:
 *
 * <ul>
 *   <li>Map operation validation
 *   <li>Traverse operation validation
 *   <li>SequenceA operation validation
 *   <li>FoldMap operation validation
 *   <li>Null parameter validation
 *   <li>Exception propagation testing
 * </ul>
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>{@code
 * @Test
 * void completeTraverseTest() {
 *     TraverseTestPattern.runComplete(
 *         traverse,
 *         validKind,
 *         validMapper,
 *         validApplicative,
 *         validTraverseFunction,
 *         validMonoid,
 *         validFoldMapFunction
 *     );
 * }
 * }</pre>
 *
 * <h2>Design Notes:</h2>
 *
 * <ul>
 *   <li>Aligns with production validation framework
 *   <li>Can test operations individually or as complete suite
 *   <li>Includes both Traverse and Foldable operations
 *   <li>Clear assertion messages for debugging
 * </ul>
 *
 * @see Traverse
 * @see Foldable
 * @see LawTestPattern
 */
public final class TraverseTestPattern {

  private TraverseTestPattern() {
    throw new AssertionError("TraverseTestPattern is a utility class");
  }

  /**
   * Runs complete Traverse test suite.
   *
   * @param traverse The Traverse instance to test
   * @param validKind A valid Kind for testing
   * @param validMapper A valid mapping function
   * @param validApplicative A valid Applicative instance
   * @param validTraverseFunction A valid traverse function
   * @param validMonoid A valid Monoid instance
   * @param validFoldMapFunction A valid foldMap function
   * @param <F> The Traverse type witness
   * @param <G> The Applicative type witness
   * @param <A> The source element type
   * @param <B> The target element type
   * @param <M> The Monoid type
   */
  public static <F, G, A, B, M> void runComplete(
      Traverse<F> traverse,
      Kind<F, A> validKind,
      Function<A, B> validMapper,
      Applicative<G> validApplicative,
      Function<A, Kind<G, B>> validTraverseFunction,
      Monoid<M> validMonoid,
      Function<A, M> validFoldMapFunction) {

    testBasicOperations(
        traverse,
        validKind,
        validMapper,
        validApplicative,
        validTraverseFunction,
        validMonoid,
        validFoldMapFunction);
    testNullValidations(
        traverse,
        validKind,
        validMapper,
        validApplicative,
        validTraverseFunction,
        validMonoid,
        validFoldMapFunction);
    testExceptionPropagation(traverse, validKind, validApplicative, validMonoid);
  }

  /**
   * Tests basic Traverse operations work correctly.
   *
   * @param traverse The Traverse instance to test
   * @param validInput A valid input Kind
   * @param validMapper A valid mapping function
   * @param validApplicative A valid Applicative instance
   * @param validTraverseFunction A valid traverse function
   * @param validMonoid A valid Monoid instance
   * @param validFoldMapFunction A valid foldMap function
   * @param <F> The Traverse type witness
   * @param <G> The Applicative type witness
   * @param <A> The source element type
   * @param <B> The target element type
   * @param <M> The Monoid type
   */
  public static <F, G, A, B, M> void testBasicOperations(
      Traverse<F> traverse,
      Kind<F, A> validInput,
      Function<A, B> validMapper,
      Applicative<G> validApplicative,
      Function<A, Kind<G, B>> validTraverseFunction,
      Monoid<M> validMonoid,
      Function<A, M> validFoldMapFunction) {

    // Test map (from Functor)
    assertThat(traverse.map(validMapper, validInput))
        .as("map should return non-null result")
        .isNotNull();

    // Test traverse
    assertThat(traverse.traverse(validApplicative, validTraverseFunction, validInput))
        .as("traverse should return non-null result")
        .isNotNull();

    // Test foldMap (from Foldable)
    assertThat(traverse.foldMap(validMonoid, validFoldMapFunction, validInput))
        .as("foldMap should return non-null result")
        .isNotNull();
  }

  /**
   * Tests all null parameter validations.
   *
   * @param traverse The Traverse instance to test
   * @param validKind A valid Kind for testing
   * @param validMapper A valid mapping function
   * @param validApplicative A valid Applicative instance
   * @param validTraverseFunction A valid traverse function
   * @param validMonoid A valid Monoid instance
   * @param validFoldMapFunction A valid foldMap function
   * @param <F> The Traverse type witness
   * @param <G> The Applicative type witness
   * @param <A> The source element type
   * @param <B> The target element type
   * @param <M> The Monoid type
   */
  public static <F, G, A, B, M> void testNullValidations(
      Traverse<F> traverse,
      Kind<F, A> validKind,
      Function<A, B> validMapper,
      Applicative<G> validApplicative,
      Function<A, Kind<G, B>> validTraverseFunction,
      Monoid<M> validMonoid,
      Function<A, M> validFoldMapFunction) {

    ValidationTestBuilder.create()
        // Map validations
        .assertMapperNull(() -> traverse.map(null, validKind), "map")
        .assertKindNull(() -> traverse.map(validMapper, null), "map")
        // Traverse validations
        .assertApplicativeNull(
            () -> traverse.traverse(null, validTraverseFunction, validKind), "traverse")
        .assertMapperNull(() -> traverse.traverse(validApplicative, null, validKind), "traverse")
        .assertKindNull(
            () -> traverse.traverse(validApplicative, validTraverseFunction, null), "traverse")
        // FoldMap validations
        .assertMonoidNull(() -> traverse.foldMap(null, validFoldMapFunction, validKind), "foldMap")
        .assertMapperNull(() -> traverse.foldMap(validMonoid, null, validKind), "foldMap")
        .assertKindNull(() -> traverse.foldMap(validMonoid, validFoldMapFunction, null), "foldMap")
        .execute();
  }

  /**
   * Tests exception propagation through Traverse operations.
   *
   * @param traverse The Traverse instance to test
   * @param validInput A valid input Kind
   * @param validApplicative A valid Applicative instance
   * @param validMonoid A valid Monoid instance
   * @param <F> The Traverse type witness
   * @param <G> The Applicative type witness
   * @param <A> The source element type
   * @param <M> The Monoid type
   */
  public static <F, G, A, M> void testExceptionPropagation(
      Traverse<F> traverse,
      Kind<F, A> validInput,
      Applicative<G> validApplicative,
      Monoid<M> validMonoid) {

    RuntimeException testException = TestData.createTestException("traverse test");

    // Test map exception propagation
    Function<A, M> throwingMapper =
        a -> {
          throw testException;
        };
    assertThatThrownBy(() -> traverse.map(throwingMapper, validInput))
        .as("map should propagate function exceptions")
        .isSameAs(testException);

    // Test traverse exception propagation
    Function<A, Kind<G, M>> throwingTraverseFunction =
        a -> {
          throw testException;
        };
    assertThatThrownBy(
            () -> traverse.traverse(validApplicative, throwingTraverseFunction, validInput))
        .as("traverse should propagate function exceptions")
        .isSameAs(testException);

    // Test foldMap exception propagation
    Function<A, M> throwingFoldMapFunction =
        a -> {
          throw testException;
        };
    assertThatThrownBy(() -> traverse.foldMap(validMonoid, throwingFoldMapFunction, validInput))
        .as("foldMap should propagate function exceptions")
        .isSameAs(testException);
  }

  /**
   * Tests sequenceA operation (if implemented).
   *
   * <p>Note: Not all Traverse implementations may provide easy sequenceA testing. This is a helper
   * for implementations that do.
   *
   * @param traverse The Traverse instance to test
   * @param applicative The Applicative instance
   * @param validInput A Kind containing applicative values
   * @param <F> The Traverse type witness
   * @param <G> The Applicative type witness
   * @param <A> The element type
   */
  public static <F, G, A> void testSequenceA(
      Traverse<F> traverse, Applicative<G> applicative, Kind<F, Kind<G, A>> validInput) {

    Kind<G, Kind<F, A>> result = traverse.sequenceA(applicative, validInput);

    assertThat(result).as("sequenceA should return non-null result").isNotNull();

    // Test null validations
    ValidationTestBuilder.create()
        .assertApplicativeNull(() -> traverse.sequenceA(null, validInput), "sequenceA")
        .assertKindNull(() -> traverse.sequenceA(applicative, null), "sequenceA")
        .execute();
  }

  /**
   * Tests Foldable-only operations (for Foldable instances).
   *
   * @param foldable The Foldable instance to test
   * @param validKind A valid Kind for testing
   * @param validMonoid A valid Monoid instance
   * @param validFoldMapFunction A valid foldMap function
   * @param <F> The Foldable type witness
   * @param <A> The element type
   * @param <M> The Monoid type
   */
  public static <F, A, M> void testFoldableOperations(
      Foldable<F> foldable,
      Kind<F, A> validKind,
      Monoid<M> validMonoid,
      Function<A, M> validFoldMapFunction) {

    // Test basic operation
    assertThat(foldable.foldMap(validMonoid, validFoldMapFunction, validKind))
        .as("foldMap should return non-null result")
        .isNotNull();

    // Test null validations
    ValidationTestBuilder.create()
        .assertMonoidNull(() -> foldable.foldMap(null, validFoldMapFunction, validKind), "foldMap")
        .assertMapperNull(() -> foldable.foldMap(validMonoid, null, validKind), "foldMap")
        .assertKindNull(() -> foldable.foldMap(validMonoid, validFoldMapFunction, null), "foldMap")
        .execute();
  }
}
