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

public final class LawTestPattern {

  private LawTestPattern() {
    throw new AssertionError("LawTestPattern is a utility class");
  }

  // =============================================================================
  // Functor Laws
  // =============================================================================

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
        .assertMapperNull(() -> functor.map(null, validKind), "f", MAP)
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
        .assertFlatMapperNull(() -> monad.flatMap(null, ofValue), "f", FLAT_MAP)
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
        .assertApplicativeNull(
            () -> traverse.traverse(null, testFunction, validKind), "applicative", TRAVERSE)
        .assertMapperNull(() -> traverse.traverse(applicative, null, validKind), "f", TRAVERSE)
        .assertKindNull(() -> traverse.traverse(applicative, testFunction, null), TRAVERSE)
        .execute();
  }
}
