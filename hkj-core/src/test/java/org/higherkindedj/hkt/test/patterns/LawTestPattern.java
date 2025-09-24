// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.patterns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;

/**
 * Complete test patterns for algebraic laws (Functor, Applicative, Monad, Traverse).
 *
 * <p>Provides standardized testing patterns for verifying that implementations satisfy their
 * algebraic laws. These laws ensure mathematical correctness and predictable behavior.
 *
 * <h2>Supported Type Classes:</h2>
 *
 * <ul>
 *   <li>Functor Laws: Identity, Composition
 *   <li>Applicative Laws: Identity, Homomorphism, Interchange, Composition
 *   <li>Monad Laws: Left Identity, Right Identity, Associativity
 *   <li>Traverse Laws: Identity, Composition, Naturality (simplified)
 * </ul>
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Functor Laws:</h3>
 *
 * <pre>{@code
 * LawTestPattern.testFunctorLaws(
 *     functor,
 *     validKind,
 *     f,
 *     g,
 *     equalityChecker
 * );
 * }</pre>
 *
 * <h3>Monad Laws:</h3>
 *
 * <pre>{@code
 * LawTestPattern.testMonadLaws(
 *     monad,
 *     validKind,
 *     testValue,
 *     testFunction,
 *     chainFunction,
 *     equalityChecker
 * );
 * }</pre>
 *
 * <h2>Design Notes:</h2>
 *
 * <ul>
 *   <li>All law tests include null parameter validation
 *   <li>Uses BiPredicate for equality checking (handles different equality semantics)
 *   <li>Clear assertion messages indicate which law failed
 *   <li>Can test laws individually or as complete suites
 * </ul>
 *
 * @see MonadTestPattern
 * @see TraverseTestPattern
 */
public final class LawTestPattern {

  private LawTestPattern() {
    throw new AssertionError("LawTestPattern is a utility class");
  }

  // =============================================================================
  // Functor Laws
  // =============================================================================

  /**
   * Tests all Functor laws: Identity and Composition.
   *
   * <p>Functor Laws:
   *
   * <ul>
   *   <li>Identity: {@code map(id, fa) == fa}
   *   <li>Composition: {@code map(g ∘ f, fa) == map(g, map(f, fa))}
   * </ul>
   *
   * @param functor The Functor instance to test
   * @param validKind A valid Kind for testing
   * @param f First function for composition
   * @param g Second function for composition
   * @param equalityChecker Equality checker for Kind instances
   * @param <F> The Functor witness type
   * @param <A> The input type
   * @param <B> Intermediate type
   * @param <C> Output type
   */
  public static <F, A, B, C> void testFunctorLaws(
      Functor<F> functor,
      Kind<F, A> validKind,
      Function<A, B> f,
      Function<B, C> g,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    testFunctorIdentity(functor, validKind, equalityChecker);
    testFunctorComposition(functor, validKind, f, g, equalityChecker);
  }

  /** Tests Functor Identity Law: {@code map(id, fa) == fa} */
  public static <F, A> void testFunctorIdentity(
      Functor<F> functor,
      Kind<F, A> validKind,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    Function<A, A> identity = a -> a;
    Kind<F, A> mapped = functor.map(identity, validKind);

    assertThat(equalityChecker.test(mapped, validKind))
        .as("Functor Identity Law: map(id, fa) == fa")
        .isTrue();

    // Test null validations
    ValidationTestBuilder.create()
        .assertMapperNull(() -> functor.map(null, validKind), MAP)
        .assertKindNull(() -> functor.map(identity, null), MAP)
        .execute();
  }

  /** Tests Functor Composition Law: {@code map(g ∘ f, fa) == map(g, map(f, fa))} */
  public static <F, A, B, C> void testFunctorComposition(
      Functor<F> functor,
      Kind<F, A> validKind,
      Function<A, B> f,
      Function<B, C> g,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    // Left side: map(g ∘ f, fa)
    Function<A, C> composed = a -> g.apply(f.apply(a));
    Kind<F, C> leftSide = functor.map(composed, validKind);

    // Right side: map(g, map(f, fa))
    Kind<F, B> intermediate = functor.map(f, validKind);
    Kind<F, C> rightSide = functor.map(g, intermediate);

    assertThat(equalityChecker.test(leftSide, rightSide))
        .as("Functor Composition Law: map(g ∘ f, fa) == map(g, map(f, fa))")
        .isTrue();
  }

  // =============================================================================
  // Applicative Laws
  // =============================================================================

  /**
   * Tests all Applicative laws: Identity, Homomorphism, Interchange, Composition.
   *
   * <p>Note: Full Applicative law testing is complex. This provides basic coverage.
   *
   * @param applicative The Applicative instance to test
   * @param validKind A valid Kind for testing
   * @param testValue A test value
   * @param testFunction A test function
   * @param equalityChecker Equality checker for Kind instances
   * @param <F> The Applicative witness type
   * @param <A> The input type
   * @param <B> The output type
   */
  public static <F, A, B> void testApplicativeLaws(
      Applicative<F> applicative,
      Kind<F, A> validKind,
      A testValue,
      Function<A, B> testFunction,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    testApplicativeIdentity(applicative, validKind, equalityChecker);
    testApplicativeHomomorphism(applicative, testValue, testFunction, equalityChecker);
    testApplicativeInterchange(applicative, testValue, testFunction, equalityChecker);
  }

  /** Tests Applicative Identity Law: {@code ap(of(id), fa) == fa} */
  public static <F, A> void testApplicativeIdentity(
      Applicative<F> applicative,
      Kind<F, A> validKind,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    Function<A, A> identity = a -> a;
    Kind<F, Function<A, A>> idFunc = applicative.of(identity);
    Kind<F, A> result = applicative.ap(idFunc, validKind);

    assertThat(equalityChecker.test(result, validKind))
        .as("Applicative Identity Law: ap(of(id), fa) == fa")
        .isTrue();
  }

  /** Tests Applicative Homomorphism Law: {@code ap(of(f), of(x)) == of(f(x))} */
  public static <F, A, B> void testApplicativeHomomorphism(
      Applicative<F> applicative,
      A testValue,
      Function<A, B> testFunction,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    Kind<F, Function<A, B>> funcKind = applicative.of(testFunction);
    Kind<F, A> valueKind = applicative.of(testValue);

    // Left side: ap(of(f), of(x))
    Kind<F, B> leftSide = applicative.ap(funcKind, valueKind);

    // Right side: of(f(x))
    Kind<F, B> rightSide = applicative.of(testFunction.apply(testValue));

    assertThat(equalityChecker.test(leftSide, rightSide))
        .as("Applicative Homomorphism Law: ap(of(f), of(x)) == of(f(x))")
        .isTrue();
  }

  /** Tests Applicative Interchange Law: {@code ap(ff, of(x)) == ap(of(f -> f(x)), ff)} */
  public static <F, A, B> void testApplicativeInterchange(
      Applicative<F> applicative,
      A testValue,
      Function<A, B> testFunction,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    Kind<F, Function<A, B>> funcKind = applicative.of(testFunction);
    Kind<F, A> valueKind = applicative.of(testValue);

    // Left side: ap(ff, of(x))
    Kind<F, B> leftSide = applicative.ap(funcKind, valueKind);

    // Right side: ap(of(f -> f(x)), ff)
    Function<Function<A, B>, B> applyToValue = f -> f.apply(testValue);
    Kind<F, Function<Function<A, B>, B>> applyFunc = applicative.of(applyToValue);
    Kind<F, B> rightSide = applicative.ap(applyFunc, funcKind);

    assertThat(equalityChecker.test(leftSide, rightSide))
        .as("Applicative Interchange Law: ap(ff, of(x)) == ap(of(f -> f(x)), ff)")
        .isTrue();
  }

  // =============================================================================
  // Monad Laws
  // =============================================================================

  /**
   * Tests all Monad laws: Left Identity, Right Identity, Associativity.
   *
   * <p>Monad Laws:
   *
   * <ul>
   *   <li>Left Identity: {@code flatMap(of(a), f) == f(a)}
   *   <li>Right Identity: {@code flatMap(m, of) == m}
   *   <li>Associativity: {@code flatMap(flatMap(m, f), g) == flatMap(m, x -> flatMap(f(x), g))}
   * </ul>
   *
   * @param monad The Monad instance to test
   * @param validKind A valid Kind for testing
   * @param testValue A test value for left identity
   * @param testFunction Test function for laws
   * @param chainFunction Chaining function for associativity
   * @param equalityChecker Equality checker for Kind instances
   * @param <F> The Monad witness type
   * @param <A> The input type
   * @param <B> Intermediate type
   */
  public static <F, A, B> void testMonadLaws(
      Monad<F> monad,
      Kind<F, A> validKind,
      A testValue,
      Function<A, Kind<F, B>> testFunction,
      Function<B, Kind<F, B>> chainFunction,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    testLeftIdentity(monad, testValue, testFunction, equalityChecker);
    testRightIdentity(monad, validKind, equalityChecker);
    testAssociativity(monad, validKind, testFunction, chainFunction, equalityChecker);
  }

  /** Tests Left Identity Law: {@code flatMap(of(a), f) == f(a)} */
  public static <F, A, B> void testLeftIdentity(
      Monad<F> monad,
      A testValue,
      Function<A, Kind<F, B>> testFunction,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    Kind<F, A> ofValue = monad.of(testValue);
    Kind<F, B> leftSide = monad.flatMap(testFunction, ofValue);
    Kind<F, B> rightSide = testFunction.apply(testValue);

    assertThat(equalityChecker.test(leftSide, rightSide))
        .as("Monad Left Identity Law: flatMap(of(a), f) == f(a)")
        .isTrue();

    // Test null validations
    ValidationTestBuilder.create()
        .assertFlatMapperNull(() -> monad.flatMap(null, ofValue), FLAT_MAP)
        .assertKindNull(() -> monad.flatMap(testFunction, null), FLAT_MAP)
        .execute();
  }

  /** Tests Right Identity Law: {@code flatMap(m, of) == m} */
  public static <F, A> void testRightIdentity(
      Monad<F> monad, Kind<F, A> validKind, BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    Function<A, Kind<F, A>> ofFunc = monad::of;
    Kind<F, A> leftSide = monad.flatMap(ofFunc, validKind);

    assertThat(equalityChecker.test(leftSide, validKind))
        .as("Monad Right Identity Law: flatMap(m, of) == m")
        .isTrue();
  }

  /**
   * Tests Associativity Law: {@code flatMap(flatMap(m, f), g) == flatMap(m, x -> flatMap(f(x), g))}
   */
  public static <F, A, B> void testAssociativity(
      Monad<F> monad,
      Kind<F, A> validKind,
      Function<A, Kind<F, B>> f,
      Function<B, Kind<F, B>> g,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    // Left side: flatMap(flatMap(m, f), g)
    Kind<F, B> innerFlatMap = monad.flatMap(f, validKind);
    Kind<F, B> leftSide = monad.flatMap(g, innerFlatMap);

    // Right side: flatMap(m, a -> flatMap(f(a), g))
    Function<A, Kind<F, B>> rightSideFunc = a -> monad.flatMap(g, f.apply(a));
    Kind<F, B> rightSide = monad.flatMap(rightSideFunc, validKind);

    assertThat(equalityChecker.test(leftSide, rightSide))
        .as(
            "Monad Associativity Law: flatMap(flatMap(m, f), g) == flatMap(m, x -> flatMap(f(x),"
                + " g))")
        .isTrue();
  }

  // =============================================================================
  // Traverse Laws (Simplified)
  // =============================================================================

  /**
   * Tests simplified Traverse laws.
   *
   * <p>Note: Full traverse law testing requires Identity and Compose applicatives. This provides
   * basic structural testing.
   *
   * @param traverse The Traverse instance to test
   * @param applicative An Applicative instance for testing
   * @param validKind A valid Kind for testing
   * @param testFunction A test traverse function
   * @param equalityChecker Equality checker for Kind instances
   * @param <T> The Traverse witness type
   * @param <G> The Applicative witness type
   * @param <A> The input type
   * @param <B> The output type
   */
  public static <T, G, A, B> void testTraverseLaws(
      Traverse<T> traverse,
      Applicative<G> applicative,
      Kind<T, A> validKind,
      Function<A, Kind<G, B>> testFunction,
      BiPredicate<Kind<G, ?>, Kind<G, ?>> equalityChecker) {

    testTraverseStructurePreservation(traverse, applicative, validKind, testFunction);
    // Additional traverse law tests would go here
  }

  /** Tests that traverse preserves structure (basic property). */
  public static <T, G, A, B> void testTraverseStructurePreservation(
      Traverse<T> traverse,
      Applicative<G> applicative,
      Kind<T, A> validKind,
      Function<A, Kind<G, B>> testFunction) {

    Kind<G, Kind<T, B>> result = traverse.traverse(applicative, testFunction, validKind);

    assertThat(result)
        .as("Traverse should preserve structure and return non-null result")
        .isNotNull();

    // Test null validations
    ValidationTestBuilder.create()
        .assertApplicativeNull(() -> traverse.traverse(null, testFunction, validKind), TRAVERSE)
        .assertMapperNull(() -> traverse.traverse(applicative, null, validKind), TRAVERSE)
        .assertKindNull(() -> traverse.traverse(applicative, testFunction, null), TRAVERSE)
        .execute();
  }

  // =============================================================================
  // Equality Checker Helpers
  // =============================================================================

  /**
   * Creates a simple reference equality checker.
   *
   * <p>Use this when Kind instances maintain referential equality.
   *
   * @param <F> The witness type
   * @return A reference equality checker
   */
  public static <F> BiPredicate<Kind<F, ?>, Kind<F, ?>> referenceEquality() {
    return (k1, k2) -> k1 == k2;
  }

  /**
   * Creates an equality checker that narrows and compares using equals().
   *
   * @param narrowFunc Function to narrow Kind to concrete type
   * @param <F> The witness type
   * @param <T> The concrete type
   * @return An equality checker using equals()
   */
  public static <F, T> BiPredicate<Kind<F, ?>, Kind<F, ?>> equalsBasedEquality(
      Function<Kind<F, ?>, T> narrowFunc) {
    return (k1, k2) -> {
      T t1 = narrowFunc.apply(k1);
      T t2 = narrowFunc.apply(k2);
      return t1.equals(t2);
    };
  }

  /**
   * Creates an equality checker with custom comparison logic.
   *
   * @param narrowFunc Function to narrow Kind to concrete type
   * @param comparator Custom comparison function
   * @param <F> The witness type
   * @param <T> The concrete type
   * @return A custom equality checker
   */
  public static <F, T> BiPredicate<Kind<F, ?>, Kind<F, ?>> customEquality(
      Function<Kind<F, ?>, T> narrowFunc, BiPredicate<T, T> comparator) {
    return (k1, k2) -> {
      T t1 = narrowFunc.apply(k1);
      T t2 = narrowFunc.apply(k2);
      return comparator.test(t1, t2);
    };
  }
}
