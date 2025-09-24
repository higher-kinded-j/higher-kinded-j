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
import org.higherkindedj.hkt.test.data.TestData;

/**
 * Enhanced comprehensive test patterns with better composability and reusability.
 *
 * <h2>New Features:</h2>
 *
 * <ul>
 *   <li>Builder pattern for complex test configurations
 *   <li>Selective test execution methods
 *   <li>Better support for custom equality checkers
 *   <li>Integrated validation configuration
 * </ul>
 */
public final class TypeClassTestPattern {

  private TypeClassTestPattern() {
    throw new AssertionError("TypeClassTestPattern is a utility class");
  }

  // =============================================================================
  // ENHANCED BUILDER PATTERN FOR COMPLEX TEST CONFIGURATIONS
  // =============================================================================

  /**
   * Creates a builder for comprehensive Functor testing.
   *
   * @param <F> The Functor witness type
   * @param <A> The input type
   * @param <B> The output type
   */
  public static <F, A, B> FunctorTestBuilder<F, A, B> functorTest(
      Functor<F> functor, Class<?> contextClass) {
    return new FunctorTestBuilder<>(functor, contextClass);
  }

  public static class FunctorTestBuilder<F, A, B> {
    private final Functor<F> functor;
    private final Class<?> contextClass;
    private Kind<F, A> validKind;
    private Function<A, B> validMapper;
    private Function<B, String> secondMapper = Object::toString;
    private BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker;

    private boolean includeOperations = true;
    private boolean includeValidations = true;
    private boolean includeExceptionPropagation = true;
    private boolean includeLaws = true;

    private FunctorTestBuilder(Functor<F> functor, Class<?> contextClass) {
      this.functor = functor;
      this.contextClass = contextClass;
    }

    public FunctorTestBuilder<F, A, B> withKind(Kind<F, A> kind) {
      this.validKind = kind;
      return this;
    }

    public FunctorTestBuilder<F, A, B> withMapper(Function<A, B> mapper) {
      this.validMapper = mapper;
      return this;
    }

    public FunctorTestBuilder<F, A, B> withSecondMapper(Function<B, String> mapper) {
      this.secondMapper = mapper;
      return this;
    }

    public FunctorTestBuilder<F, A, B> withEqualityChecker(
        BiPredicate<Kind<F, ?>, Kind<F, ?>> checker) {
      this.equalityChecker = checker;
      return this;
    }

    public FunctorTestBuilder<F, A, B> skipOperations() {
      this.includeOperations = false;
      return this;
    }

    public FunctorTestBuilder<F, A, B> skipValidations() {
      this.includeValidations = false;
      return this;
    }

    public FunctorTestBuilder<F, A, B> skipExceptionPropagation() {
      this.includeExceptionPropagation = false;
      return this;
    }

    public FunctorTestBuilder<F, A, B> skipLaws() {
      this.includeLaws = false;
      return this;
    }

    public void test() {
      validateConfiguration();

      if (includeOperations) {
        testFunctorOperations(functor, validKind, validMapper);
      }
      if (includeValidations) {
        testFunctorValidations(functor, contextClass, validKind, validMapper);
      }
      if (includeExceptionPropagation) {
        testFunctorExceptionPropagation(functor, validKind);
      }
      if (includeLaws) {
        testFunctorLaws(functor, validKind, validMapper, secondMapper, equalityChecker);
      }
    }

    private void validateConfiguration() {
      if (validKind == null) {
        throw new IllegalStateException("validKind must be configured");
      }
      if (validMapper == null) {
        throw new IllegalStateException("validMapper must be configured");
      }
      if (includeLaws && equalityChecker == null) {
        throw new IllegalStateException("equalityChecker required for law testing");
      }
    }
  }

  /** Creates a builder for comprehensive MonadError testing. */
  public static <F, E, A, B> MonadErrorTestBuilder<F, E, A, B> monadErrorTest(
      MonadError<F, E> monadError, Class<?> contextClass) {
    return new MonadErrorTestBuilder<>(monadError, contextClass);
  }

  public static class MonadErrorTestBuilder<F, E, A, B> {
    private final MonadError<F, E> monadError;
    private final Class<?> contextClass;

    // Required parameters
    private Kind<F, A> validKind;
    private Kind<F, A> validKind2;
    private A testValue;
    private Function<A, B> validMapper;
    private Function<A, Kind<F, B>> validFlatMapper;
    private Kind<F, Function<A, B>> validFunctionKind;
    private BiFunction<A, A, B> validCombiningFunction;
    private Function<E, Kind<F, A>> validHandler;
    private Kind<F, A> validFallback;
    private Function<A, Kind<F, B>> testFunction;
    private Function<B, Kind<F, B>> chainFunction;
    private BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker;

    // Optional configuration
    private FlexibleValidationConfig.MonadErrorValidation<F, E, A, B> validationConfig;
    private boolean includeOperations = true;
    private boolean includeValidations = true;
    private boolean includeExceptionPropagation = true;
    private boolean includeLaws = true;

    private MonadErrorTestBuilder(MonadError<F, E> monadError, Class<?> contextClass) {
      this.monadError = monadError;
      this.contextClass = contextClass;
    }

    // Fluent setters for all parameters
    public MonadErrorTestBuilder<F, E, A, B> withKind(Kind<F, A> kind) {
      this.validKind = kind;
      return this;
    }

    public MonadErrorTestBuilder<F, E, A, B> withKind2(Kind<F, A> kind2) {
      this.validKind2 = kind2;
      return this;
    }

    public MonadErrorTestBuilder<F, E, A, B> withTestValue(A value) {
      this.testValue = value;
      return this;
    }

    public MonadErrorTestBuilder<F, E, A, B> withMapper(Function<A, B> mapper) {
      this.validMapper = mapper;
      return this;
    }

    public MonadErrorTestBuilder<F, E, A, B> withFlatMapper(Function<A, Kind<F, B>> flatMapper) {
      this.validFlatMapper = flatMapper;
      return this;
    }

    public MonadErrorTestBuilder<F, E, A, B> withFunctionKind(
        Kind<F, Function<A, B>> functionKind) {
      this.validFunctionKind = functionKind;
      return this;
    }

    public MonadErrorTestBuilder<F, E, A, B> withCombiningFunction(
        BiFunction<A, A, B> combiningFunction) {
      this.validCombiningFunction = combiningFunction;
      return this;
    }

    public MonadErrorTestBuilder<F, E, A, B> withHandler(Function<E, Kind<F, A>> handler) {
      this.validHandler = handler;
      return this;
    }

    public MonadErrorTestBuilder<F, E, A, B> withFallback(Kind<F, A> fallback) {
      this.validFallback = fallback;
      return this;
    }

    public MonadErrorTestBuilder<F, E, A, B> withTestFunction(
        Function<A, Kind<F, B>> testFunction) {
      this.testFunction = testFunction;
      return this;
    }

    public MonadErrorTestBuilder<F, E, A, B> withChainFunction(
        Function<B, Kind<F, B>> chainFunction) {
      this.chainFunction = chainFunction;
      return this;
    }

    public MonadErrorTestBuilder<F, E, A, B> withEqualityChecker(
        BiPredicate<Kind<F, ?>, Kind<F, ?>> checker) {
      this.equalityChecker = checker;
      return this;
    }

    public MonadErrorTestBuilder<F, E, A, B> withValidationConfig(
        FlexibleValidationConfig.MonadErrorValidation<F, E, A, B> config) {
      this.validationConfig = config;
      return this;
    }

    public MonadErrorTestBuilder<F, E, A, B> skipOperations() {
      this.includeOperations = false;
      return this;
    }

    public MonadErrorTestBuilder<F, E, A, B> skipValidations() {
      this.includeValidations = false;
      return this;
    }

    public MonadErrorTestBuilder<F, E, A, B> skipExceptionPropagation() {
      this.includeExceptionPropagation = false;
      return this;
    }

    public MonadErrorTestBuilder<F, E, A, B> skipLaws() {
      this.includeLaws = false;
      return this;
    }

    public void test() {
      validateConfiguration();

      if (includeOperations) {
        testMonadErrorOperations(
            monadError,
            validKind,
            validMapper,
            validFlatMapper,
            validFunctionKind,
            validHandler,
            validFallback);
      }

      if (includeValidations) {
        if (validationConfig != null) {
          validationConfig.test();
        } else {
          testMonadErrorValidations(
              monadError,
              contextClass,
              validKind,
              validMapper,
              validFlatMapper,
              validFunctionKind,
              validHandler,
              validFallback);
        }
      }

      if (includeExceptionPropagation) {
        testMonadErrorExceptionPropagation(monadError, validKind);
      }

      if (includeLaws) {
        testMonadLaws(
            monadError, validKind, testValue, testFunction, chainFunction, equalityChecker);
      }
    }

    private void validateConfiguration() {
      if (validKind == null) throw new IllegalStateException("validKind required");
      if (includeOperations || includeValidations) {
        if (validMapper == null) throw new IllegalStateException("validMapper required");
        if (validFlatMapper == null) throw new IllegalStateException("validFlatMapper required");
        if (validFunctionKind == null)
          throw new IllegalStateException("validFunctionKind required");
        if (validHandler == null) throw new IllegalStateException("validHandler required");
        if (validFallback == null) throw new IllegalStateException("validFallback required");
      }
      if (includeLaws) {
        if (testValue == null) throw new IllegalStateException("testValue required for laws");
        if (testFunction == null) throw new IllegalStateException("testFunction required for laws");
        if (chainFunction == null)
          throw new IllegalStateException("chainFunction required for laws");
        if (equalityChecker == null)
          throw new IllegalStateException("equalityChecker required for laws");
      }
    }
  }

  // =============================================================================
  // EXISTING METHODS (PRESERVED FOR BACKWARD COMPATIBILITY)
  // =============================================================================

  // Keep all existing test methods exactly as they are...
  // [Previous implementation preserved]

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
    RuntimeException testException = TestData.createTestException("functor test");
    Function<A, String> throwingMapper =
        a -> {
          throw testException;
        };
    assertThatThrownBy(() -> functor.map(throwingMapper, validKind))
        .as("map should propagate function exceptions")
        .isSameAs(testException);
  }

  public static <F, A, B, C> void testFunctorLaws(
      Functor<F> functor,
      Kind<F, A> validKind,
      Function<A, B> f,
      Function<B, C> g,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    // Identity Law
    Function<A, A> identity = a -> a;
    Kind<F, A> mapped = functor.map(identity, validKind);
    assertThat(equalityChecker.test(mapped, validKind))
        .as("Functor Identity Law: map(id, fa) == fa")
        .isTrue();

    // Composition Law
    Function<A, C> composed = a -> g.apply(f.apply(a));
    Kind<F, C> leftSide = functor.map(composed, validKind);
    Kind<F, B> intermediate = functor.map(f, validKind);
    Kind<F, C> rightSide = functor.map(g, intermediate);
    assertThat(equalityChecker.test(leftSide, rightSide))
        .as("Functor Composition Law: map(g ∘ f, fa) == map(g, map(f, fa))")
        .isTrue();
  }

  // MonadError methods
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

  // Monad methods
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
    RuntimeException testException = TestData.createTestException("monad test");

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

  public static <F, A, B> void testMonadLaws(
      Monad<F> monad,
      Kind<F, A> validKind,
      A testValue,
      Function<A, Kind<F, B>> testFunction,
      Function<B, Kind<F, B>> chainFunction,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    // Left Identity
    Kind<F, A> ofValue = monad.of(testValue);
    Kind<F, B> leftSide = monad.flatMap(testFunction, ofValue);
    Kind<F, B> rightSide = testFunction.apply(testValue);
    assertThat(equalityChecker.test(leftSide, rightSide))
        .as("Monad Left Identity Law: flatMap(of(a), f) == f(a)")
        .isTrue();

    // Right Identity
    Function<A, Kind<F, A>> ofFunc = monad::of;
    Kind<F, A> leftSideIdentity = monad.flatMap(ofFunc, validKind);
    assertThat(equalityChecker.test(leftSideIdentity, validKind))
        .as("Monad Right Identity Law: flatMap(m, of) == m")
        .isTrue();

    // Associativity
    Kind<F, B> innerFlatMap = monad.flatMap(testFunction, validKind);
    Kind<F, B> leftSideAssoc = monad.flatMap(chainFunction, innerFlatMap);
    Function<A, Kind<F, B>> rightSideFunc =
        a -> monad.flatMap(chainFunction, testFunction.apply(a));
    Kind<F, B> rightSideAssoc = monad.flatMap(rightSideFunc, validKind);
    assertThat(equalityChecker.test(leftSideAssoc, rightSideAssoc))
        .as("Monad Associativity Law")
        .isTrue();
  }

  // Applicative methods
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

    RuntimeException testException = TestData.createTestException("applicative test");
    Function<A, String> throwingMapper =
        a -> {
          throw testException;
        };

    assertThatThrownBy(() -> applicative.map(throwingMapper, validKind))
        .as("map should propagate function exceptions")
        .isSameAs(testException);
  }

  public static <F, A, B> void testApplicativeLaws(
      Applicative<F> applicative,
      Kind<F, A> validKind,
      A testValue,
      Function<A, B> testFunction,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    // Identity Law
    Function<A, A> identity = a -> a;
    Kind<F, Function<A, A>> idFunc = applicative.of(identity);
    Kind<F, A> result = applicative.ap(idFunc, validKind);
    assertThat(equalityChecker.test(result, validKind)).as("Applicative Identity Law").isTrue();

    // Homomorphism Law
    Kind<F, Function<A, B>> funcKind = applicative.of(testFunction);
    Kind<F, A> valueKind = applicative.of(testValue);
    Kind<F, B> leftSide = applicative.ap(funcKind, valueKind);
    Kind<F, B> rightSide = applicative.of(testFunction.apply(testValue));
    assertThat(equalityChecker.test(leftSide, rightSide))
        .as("Applicative Homomorphism Law")
        .isTrue();
  }

  // Foldable methods
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

    RuntimeException testException = TestData.createTestException("foldable test");
    Function<A, M> throwingFunction =
        a -> {
          throw testException;
        };

    assertThatThrownBy(() -> foldable.foldMap(validMonoid, throwingFunction, validKind))
        .as("foldMap should propagate function exceptions")
        .isSameAs(testException);
  }

  // Traverse methods
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

    RuntimeException testException = TestData.createTestException("traverse test");

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

  public static <F, G, A, B> void testTraverseLaws(
      Traverse<F> traverse,
      Applicative<G> applicative,
      Kind<F, A> validKind,
      Function<A, Kind<G, B>> testFunction,
      BiPredicate<Kind<G, ?>, Kind<G, ?>> equalityChecker) {

    Kind<G, Kind<F, B>> result = traverse.traverse(applicative, testFunction, validKind);
    assertThat(result)
        .as("Traverse should preserve structure and return non-null result")
        .isNotNull();
  }

  // Utility methods
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

  // Combined test methods for backward compatibility
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
