// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.patterns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.*;
import org.higherkindedj.hkt.test.assertions.TypeClassAssertions;

/**
 * Enhanced comprehensive test patterns with better composability and reusability.
 *
 * <p>This class now delegates law testing to {@link LawTestPattern} for better separation of
 * concerns and maintainability.
 *
 * <h2>Delegation Strategy:</h2>
 *
 * <ul>
 *   <li>Law testing methods delegate to granular law methods in LawTestPattern
 *   <li>Validation testing remains in this class using TypeClassAssertions
 *   <li>No validation tests are performed during law testing (separated concerns)
 * </ul>
 */
public final class TypeClassTestPattern {

  private TypeClassTestPattern() {
    throw new AssertionError("TypeClassTestPattern is a utility class");
  }

  // =============================================================================
  // FUNCTOR TESTING
  // =============================================================================

  public static <F, A, B> void testFunctorOperations(
      Functor<F> functor, Kind<F, A> validKind, Function<A, B> validMapper) {
    assertThat(functor.map(validMapper, validKind))
        .as("map should return non-null result")
        .isNotNull();
  }

  public static <F, A, B> void testFunctorValidations(
      Functor<F> functor, Class<?> contextClass, Kind<F, A> validKind, Function<A, B> validMapper) {
    TypeClassAssertions.assertAllFunctorOperations(functor, contextClass, validKind, validMapper);
  }

  public static <F, A> void testFunctorExceptionPropagation(
      Functor<F> functor, Kind<F, A> validKind) {
    RuntimeException testException = createTestException("functor test");
    Function<A, String> throwingMapper =
        a -> {
          throw testException;
        };
    assertThatThrownBy(() -> functor.map(throwingMapper, validKind))
        .as("map should propagate function exceptions")
        .isSameAs(testException);
  }

  /**
   * Tests Functor laws by delegating to granular law methods in LawTestPattern.
   *
   * <p>This method tests the Identity and Composition laws without performing validation tests.
   */
  public static <F, A, B, C> void testFunctorLaws(
      Functor<F> functor,
      Kind<F, A> validKind,
      Function<A, B> f,
      Function<B, C> g,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    // Delegate to LawTestPattern - tests only algebraic laws
    LawTestPattern.testFunctorIdentityLaw(functor, validKind, equalityChecker);
    LawTestPattern.testFunctorCompositionLaw(functor, validKind, f, g, equalityChecker);
  }

  // =============================================================================
  // APPLICATIVE TESTING
  // =============================================================================

  public static <F, A, B> void testApplicativeOperations(
      Applicative<F> applicative,
      Kind<F, A> validKind,
      Kind<F, A> validKind2,
      Function<A, B> validMapper,
      Kind<F, Function<A, B>> validFunctionKind,
      BiFunction<A, A, B> validCombiningFunction) {

    assertThat(applicative.map(validMapper, validKind))
        .as("map should return non-null result")
        .isNotNull();

    assertThat(applicative.ap(validFunctionKind, validKind))
        .as("ap should return non-null result")
        .isNotNull();

    assertThat(applicative.map2(validKind, validKind2, validCombiningFunction))
        .as("map2 should return non-null result")
        .isNotNull();
  }

  public static <F, A, B> void testApplicativeValidations(
      Applicative<F> applicative,
      Class<?> contextClass,
      Kind<F, A> validKind,
      Kind<F, A> validKind2,
      Function<A, B> validMapper,
      Kind<F, Function<A, B>> validFunctionKind,
      BiFunction<A, A, B> validCombiningFunction) {

    TypeClassAssertions.assertAllApplicativeOperations(
        applicative,
        contextClass,
        validKind,
        validKind2,
        validMapper,
        validFunctionKind,
        validCombiningFunction);
  }

  public static <F, A> void testApplicativeExceptionPropagation(
      Applicative<F> applicative, Kind<F, A> validKind) {

    RuntimeException testException = createTestException("applicative test");
    Function<A, String> throwingMapper =
        a -> {
          throw testException;
        };

    assertThatThrownBy(() -> applicative.map(throwingMapper, validKind))
        .as("map should propagate function exceptions")
        .isSameAs(testException);
  }

  /**
   * Tests Applicative laws by delegating to granular law methods in LawTestPattern.
   *
   * <p>This method tests Identity, Homomorphism, and Interchange laws without performing validation
   * tests.
   *
   * <p><strong>Note:</strong> This now includes the Interchange law which was previously missing.
   */
  public static <F, A, B> void testApplicativeLaws(
      Applicative<F> applicative,
      Kind<F, A> validKind,
      A testValue,
      Function<A, B> testFunction,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    // Delegate to LawTestPattern - tests all three laws
    LawTestPattern.testApplicativeIdentityLaw(applicative, validKind, equalityChecker);
    LawTestPattern.testApplicativeHomomorphismLaw(
        applicative, testValue, testFunction, equalityChecker);
    LawTestPattern.testApplicativeInterchangeLaw(
        applicative, testValue, testFunction, equalityChecker);
  }

  // =============================================================================
  // MONAD TESTING
  // =============================================================================

  public static <F, A, B> void testMonadOperations(
      Monad<F> monad,
      Kind<F, A> validKind,
      Function<A, B> validMapper,
      Function<A, Kind<F, B>> validFlatMapper,
      Kind<F, Function<A, B>> validFunctionKind) {

    assertThat(monad.map(validMapper, validKind))
        .as("map should return non-null result")
        .isNotNull();

    assertThat(monad.flatMap(validFlatMapper, validKind))
        .as("flatMap should return non-null result")
        .isNotNull();

    assertThat(monad.ap(validFunctionKind, validKind))
        .as("ap should return non-null result")
        .isNotNull();
  }

  public static <F, A, B> void testMonadValidations(
      Monad<F> monad,
      Class<?> contextClass,
      Kind<F, A> validKind,
      Function<A, B> validMapper,
      Function<A, Kind<F, B>> validFlatMapper,
      Kind<F, Function<A, B>> validFunctionKind) {

    TypeClassAssertions.assertAllMonadOperations(
        monad, contextClass, validKind, validMapper, validFlatMapper, validFunctionKind);
  }

  public static <F, A> void testMonadExceptionPropagation(Monad<F> monad, Kind<F, A> validKind) {
    RuntimeException testException = createTestException("monad test");

    Function<A, String> throwingMapper =
        a -> {
          throw testException;
        };
    assertThatThrownBy(() -> monad.map(throwingMapper, validKind))
        .as("map should propagate function exceptions")
        .isSameAs(testException);

    Function<A, Kind<F, String>> throwingFlatMapper =
        a -> {
          throw testException;
        };
    assertThatThrownBy(() -> monad.flatMap(throwingFlatMapper, validKind))
        .as("flatMap should propagate function exceptions")
        .isSameAs(testException);
  }

  /**
   * Tests Monad laws by delegating to granular law methods in LawTestPattern.
   *
   * <p>This method tests Left Identity, Right Identity, and Associativity laws without performing
   * validation tests.
   */
  public static <F, A, B> void testMonadLaws(
      Monad<F> monad,
      Kind<F, A> validKind,
      A testValue,
      Function<A, Kind<F, B>> testFunction,
      Function<B, Kind<F, B>> chainFunction,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    // Delegate to LawTestPattern - tests all three monad laws
    LawTestPattern.testLeftIdentityLaw(monad, testValue, testFunction, equalityChecker);
    LawTestPattern.testRightIdentityLaw(monad, validKind, equalityChecker);
    LawTestPattern.testAssociativityLaw(
        monad, validKind, testFunction, chainFunction, equalityChecker);
  }

  // =============================================================================
  // MONAD ERROR TESTING
  // =============================================================================

  public static <F, E, A, B> void testMonadErrorOperations(
      MonadError<F, E> monadError,
      Kind<F, A> validKind,
      Function<A, B> validMapper,
      Function<A, Kind<F, B>> validFlatMapper,
      Kind<F, Function<A, B>> validFunctionKind,
      Function<E, Kind<F, A>> validHandler,
      Kind<F, A> validFallback) {

    testMonadOperations(monadError, validKind, validMapper, validFlatMapper, validFunctionKind);

    assertThat(monadError.handleErrorWith(validKind, validHandler))
        .as("handleErrorWith should return non-null result")
        .isNotNull();

    assertThat(monadError.recoverWith(validKind, validFallback))
        .as("recoverWith should return non-null result")
        .isNotNull();
  }

  public static <F, E, A, B> void testMonadErrorValidations(
      MonadError<F, E> monadError,
      Class<?> contextClass,
      Kind<F, A> validKind,
      Function<A, B> validMapper,
      Function<A, Kind<F, B>> validFlatMapper,
      Kind<F, Function<A, B>> validFunctionKind,
      Function<E, Kind<F, A>> validHandler,
      Kind<F, A> validFallback) {

    TypeClassAssertions.assertAllMonadErrorOperations(
        monadError,
        contextClass,
        validKind,
        validMapper,
        validFlatMapper,
        validFunctionKind,
        validHandler,
        validFallback);
  }

  public static <F, E, A> void testMonadErrorExceptionPropagation(
      MonadError<F, E> monadError, Kind<F, A> validKind) {

    testMonadExceptionPropagation(monadError, validKind);
  }

  // =============================================================================
  // SELECTIVE TESTING
  // =============================================================================

  public static <F, A, B, C> void testSelectiveOperations(
      Selective<F> selective,
      Kind<F, Choice<A, B>> validChoiceKind,
      Kind<F, Function<A, B>> validFunctionKind,
      Kind<F, Function<A, C>> validLeftHandler,
      Kind<F, Function<B, C>> validRightHandler,
      Kind<F, Boolean> validCondition,
      Kind<F, A> validEffect,
      Kind<F, A> validThenBranch,
      Kind<F, A> validElseBranch) {

    assertThat(selective.select(validChoiceKind, validFunctionKind))
        .as("select should return non-null result")
        .isNotNull();

    assertThat(selective.branch(validChoiceKind, validLeftHandler, validRightHandler))
        .as("branch should return non-null result")
        .isNotNull();

    assertThat(selective.whenS(validCondition, validEffect))
        .as("whenS should return non-null result")
        .isNotNull();

    assertThat(selective.ifS(validCondition, validThenBranch, validElseBranch))
        .as("ifS should return non-null result")
        .isNotNull();
  }

  public static <F, A, B, C> void testSelectiveValidations(
      Selective<F> selective,
      Class<?> contextClass,
      Kind<F, Choice<A, B>> validChoiceKind,
      Kind<F, Function<A, B>> validFunctionKind,
      Kind<F, Function<A, C>> validLeftHandler,
      Kind<F, Function<B, C>> validRightHandler,
      Kind<F, Boolean> validCondition,
      Kind<F, A> validEffect,
      Kind<F, A> validThenBranch,
      Kind<F, A> validElseBranch) {

    TypeClassAssertions.assertAllSelectiveOperations(
        selective,
        contextClass,
        validChoiceKind,
        validFunctionKind,
        validLeftHandler,
        validRightHandler,
        validCondition,
        validEffect,
        validThenBranch,
        validElseBranch);
  }

  public static <F, A, B> void testSelectiveExceptionPropagation(
      Selective<F> selective,
      Kind<F, Choice<A, B>> validChoiceKind,
      Kind<F, Function<A, B>> validFunctionKind) {

    RuntimeException testException = createTestException("selective test");

    // Test exception propagation in select
    Function<A, B> throwingFunc =
        a -> {
          throw testException;
        };
    Kind<F, Function<A, B>> throwingFuncKind = selective.of(throwingFunc);

    // Create a Left choice that will trigger function application
    Choice<A, B> leftChoice = new Selective.SimpleChoice<>(true, null, null);
    Kind<F, Choice<A, B>> leftChoiceKind = selective.of(leftChoice);

    assertThatThrownBy(() -> selective.select(leftChoiceKind, throwingFuncKind))
        .as("select should propagate function exceptions")
        .isSameAs(testException);
  }

  /**
   * Tests Selective laws by delegating to granular law methods in LawTestPattern.
   *
   * <p>This method tests Identity and Distributivity laws without performing validation tests.
   */
  public static <F, A, B> void testSelectiveLaws(
      Selective<F> selective,
      Kind<F, Choice<A, B>> choiceKind,
      B testValue,
      Function<A, B> testFunction,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    // Delegate to LawTestPattern - tests selective laws
    LawTestPattern.testSelectiveIdentityLaw(selective, testValue, equalityChecker);
    LawTestPattern.testSelectiveDistributivityLaw(
        selective, choiceKind, testFunction, equalityChecker);
  }

  // =============================================================================
  // COMBINED TEST METHOD FOR SELECTIVE
  // =============================================================================

  public static <F, A, B, C> void testCompleteSelective(
      Selective<F> selective,
      Class<?> contextClass,
      Kind<F, Choice<A, B>> validChoiceKind,
      Kind<F, Function<A, B>> validFunctionKind,
      Kind<F, Function<A, C>> validLeftHandler,
      Kind<F, Function<B, C>> validRightHandler,
      Kind<F, Boolean> validCondition,
      Kind<F, A> validEffect,
      Kind<F, A> validThenBranch,
      Kind<F, A> validElseBranch,
      B testValue,
      Function<A, B> testFunction,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    testSelectiveOperations(
        selective,
        validChoiceKind,
        validFunctionKind,
        validLeftHandler,
        validRightHandler,
        validCondition,
        validEffect,
        validThenBranch,
        validElseBranch);

    testSelectiveValidations(
        selective,
        contextClass,
        validChoiceKind,
        validFunctionKind,
        validLeftHandler,
        validRightHandler,
        validCondition,
        validEffect,
        validThenBranch,
        validElseBranch);

    testSelectiveExceptionPropagation(selective, validChoiceKind, validFunctionKind);

    testSelectiveLaws(selective, validChoiceKind, testValue, testFunction, equalityChecker);
  }

  // =============================================================================
  // FOLDABLE TESTING
  // =============================================================================

  public static <F, A, M> void testFoldableOperations(
      Foldable<F> foldable,
      Kind<F, A> validKind,
      Monoid<M> validMonoid,
      Function<A, M> validFoldMapFunction) {

    assertThat(foldable.foldMap(validMonoid, validFoldMapFunction, validKind))
        .as("foldMap should return non-null result")
        .isNotNull();
  }

  public static <F, A, M> void testFoldableValidations(
      Foldable<F> foldable,
      Class<?> contextClass,
      Kind<F, A> validKind,
      Monoid<M> validMonoid,
      Function<A, M> validFoldMapFunction) {

    TypeClassAssertions.assertAllFoldableOperations(
        foldable, contextClass, validKind, validMonoid, validFoldMapFunction);
  }

  public static <F, A, M> void testFoldableExceptionPropagation(
      Foldable<F> foldable, Kind<F, A> validKind, Monoid<M> validMonoid) {

    RuntimeException testException = createTestException("foldable test");
    Function<A, M> throwingFunction =
        a -> {
          throw testException;
        };

    assertThatThrownBy(() -> foldable.foldMap(validMonoid, throwingFunction, validKind))
        .as("foldMap should propagate function exceptions")
        .isSameAs(testException);
  }

  // =============================================================================
  // TRAVERSE TESTING
  // =============================================================================

  public static <F, G, A, B, M> void testTraverseOperations(
      Traverse<F> traverse,
      Kind<F, A> validKind,
      Function<A, B> validMapper,
      Applicative<G> validApplicative,
      Function<A, Kind<G, B>> validTraverseFunction,
      Monoid<M> validMonoid,
      Function<A, M> validFoldMapFunction) {

    assertThat(traverse.map(validMapper, validKind))
        .as("map should return non-null result")
        .isNotNull();

    assertThat(traverse.foldMap(validMonoid, validFoldMapFunction, validKind))
        .as("foldMap should return non-null result")
        .isNotNull();

    assertThat(traverse.traverse(validApplicative, validTraverseFunction, validKind))
        .as("traverse should return non-null result")
        .isNotNull();
  }

  public static <F, G, A, B, M> void testTraverseValidations(
      Traverse<F> traverse,
      Class<?> contextClass,
      Kind<F, A> validKind,
      Function<A, B> validMapper,
      Applicative<G> validApplicative,
      Function<A, Kind<G, B>> validTraverseFunction,
      Monoid<M> validMonoid,
      Function<A, M> validFoldMapFunction) {

    TypeClassAssertions.assertAllTraverseOperations(
        traverse,
        contextClass,
        validKind,
        validMapper,
        validApplicative,
        validTraverseFunction,
        validMonoid,
        validFoldMapFunction);
  }

  public static <F, G, A, M> void testTraverseExceptionPropagation(
      Traverse<F> traverse,
      Kind<F, A> validKind,
      Applicative<G> validApplicative,
      Monoid<M> validMonoid) {

    RuntimeException testException = createTestException("traverse test");

    Function<A, String> throwingMapper =
        a -> {
          throw testException;
        };
    assertThatThrownBy(() -> traverse.map(throwingMapper, validKind))
        .as("map should propagate function exceptions")
        .isSameAs(testException);

    Function<A, M> throwingFoldMapFunction =
        a -> {
          throw testException;
        };
    assertThatThrownBy(() -> traverse.foldMap(validMonoid, throwingFoldMapFunction, validKind))
        .as("foldMap should propagate function exceptions")
        .isSameAs(testException);

    Function<A, Kind<G, String>> throwingTraverseFunction =
        a -> {
          throw testException;
        };
    assertThatThrownBy(
            () -> traverse.traverse(validApplicative, throwingTraverseFunction, validKind))
        .as("traverse should propagate function exceptions")
        .isSameAs(testException);
  }

  /**
   * Tests Traverse laws by delegating to LawTestPattern.
   *
   * <p>This method tests structure preservation without performing validation tests.
   */
  public static <F, G, A, B> void testTraverseLaws(
      Traverse<F> traverse,
      Applicative<G> applicative,
      Kind<F, A> validKind,
      Function<A, Kind<G, B>> testFunction,
      BiPredicate<Kind<G, ?>, Kind<G, ?>> equalityChecker) {

    // Delegate to LawTestPattern - tests only structure preservation law
    LawTestPattern.testTraverseStructurePreservationLaw(
        traverse, applicative, validKind, testFunction);
  }

  // =============================================================================
  // UTILITY METHODS
  // =============================================================================

  public static <F> BiPredicate<Kind<F, ?>, Kind<F, ?>> referenceEquality() {
    return (k1, k2) -> k1 == k2;
  }

  public static <F, T> BiPredicate<Kind<F, ?>, Kind<F, ?>> equalsEquality(
      Function<Kind<F, ?>, T> narrower) {
    return (k1, k2) -> {
      T t1 = narrower.apply(k1);
      T t2 = narrower.apply(k2);
      return t1.equals(t2);
    };
  }

  private static RuntimeException createTestException(String message) {
    return new RuntimeException("Test exception: " + message);
  }

  // =============================================================================
  // COMBINED TEST METHODS FOR BACKWARD COMPATIBILITY
  // =============================================================================

  public static <F, A, B> void testCompleteFunctor(
      Functor<F> functor,
      Class<?> contextClass,
      Kind<F, A> validKind,
      Function<A, B> validMapper,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    testFunctorOperations(functor, validKind, validMapper);
    testFunctorValidations(functor, contextClass, validKind, validMapper);
    testFunctorExceptionPropagation(functor, validKind);
    Function<B, String> secondMapper = Object::toString;
    testFunctorLaws(functor, validKind, validMapper, secondMapper, equalityChecker);
  }

  public static <F, E, A, B> void testCompleteMonadError(
      MonadError<F, E> monadError,
      Class<?> contextClass,
      Kind<F, A> validKind,
      A testValue,
      Function<A, B> validMapper,
      Function<A, Kind<F, B>> validFlatMapper,
      Kind<F, Function<A, B>> validFunctionKind,
      Function<E, Kind<F, A>> validHandler,
      Kind<F, A> validFallback,
      Function<A, Kind<F, B>> testFunction,
      Function<B, Kind<F, B>> chainFunction,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    testMonadErrorOperations(
        monadError,
        validKind,
        validMapper,
        validFlatMapper,
        validFunctionKind,
        validHandler,
        validFallback);
    testMonadErrorValidations(
        monadError,
        contextClass,
        validKind,
        validMapper,
        validFlatMapper,
        validFunctionKind,
        validHandler,
        validFallback);
    testMonadErrorExceptionPropagation(monadError, validKind);
    testMonadLaws(monadError, validKind, testValue, testFunction, chainFunction, equalityChecker);
  }

  public static <F, A, B> void testCompleteMonad(
      Monad<F> monad,
      Class<?> contextClass,
      Kind<F, A> validKind,
      A testValue,
      Function<A, B> validMapper,
      Function<A, Kind<F, B>> validFlatMapper,
      Kind<F, Function<A, B>> validFunctionKind,
      Function<A, Kind<F, B>> testFunction,
      Function<B, Kind<F, B>> chainFunction,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    testMonadOperations(monad, validKind, validMapper, validFlatMapper, validFunctionKind);
    testMonadValidations(
        monad, contextClass, validKind, validMapper, validFlatMapper, validFunctionKind);
    testMonadExceptionPropagation(monad, validKind);
    testMonadLaws(monad, validKind, testValue, testFunction, chainFunction, equalityChecker);
  }

  public static <F, A, B> void testCompleteApplicative(
      Applicative<F> applicative,
      Class<?> contextClass,
      Kind<F, A> validKind,
      Kind<F, A> validKind2,
      Function<A, B> validMapper,
      Kind<F, Function<A, B>> validFunctionKind,
      BiFunction<A, A, B> validCombiningFunction,
      A testValue,
      Function<A, B> testFunction,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    testApplicativeOperations(
        applicative, validKind, validKind2, validMapper, validFunctionKind, validCombiningFunction);
    testApplicativeValidations(
        applicative,
        contextClass,
        validKind,
        validKind2,
        validMapper,
        validFunctionKind,
        validCombiningFunction);
    testApplicativeExceptionPropagation(applicative, validKind);
    testApplicativeLaws(applicative, validKind, testValue, testFunction, equalityChecker);
  }

  public static <F, A, M> void testCompleteFoldable(
      Foldable<F> foldable,
      Class<?> contextClass,
      Kind<F, A> validKind,
      Monoid<M> validMonoid,
      Function<A, M> validFoldMapFunction) {

    testFoldableOperations(foldable, validKind, validMonoid, validFoldMapFunction);
    testFoldableValidations(foldable, contextClass, validKind, validMonoid, validFoldMapFunction);
    testFoldableExceptionPropagation(foldable, validKind, validMonoid);
  }

  public static <F, G, A, B, M> void testCompleteTraverse(
      Traverse<F> traverse,
      Class<?> contextClass,
      Kind<F, A> validKind,
      Function<A, B> validMapper,
      Applicative<G> validApplicative,
      Function<A, Kind<G, B>> validTraverseFunction,
      Monoid<M> validMonoid,
      Function<A, M> validFoldMapFunction,
      BiPredicate<Kind<G, ?>, Kind<G, ?>> equalityChecker) {

    testTraverseOperations(
        traverse,
        validKind,
        validMapper,
        validApplicative,
        validTraverseFunction,
        validMonoid,
        validFoldMapFunction);
    testTraverseValidations(
        traverse,
        contextClass,
        validKind,
        validMapper,
        validApplicative,
        validTraverseFunction,
        validMonoid,
        validFoldMapFunction);
    testTraverseExceptionPropagation(traverse, validKind, validApplicative, validMonoid);
    testTraverseLaws(traverse, validApplicative, validKind, validTraverseFunction, equalityChecker);
  }
}
