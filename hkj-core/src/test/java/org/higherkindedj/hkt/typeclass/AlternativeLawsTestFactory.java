// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.typeclass;

import org.higherkindedj.hkt.Alternative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

/**
 * Dynamic test factory for Alternative laws using JUnit 6's @TestFactory.
 *
 * <p>This class tests the algebraic laws that all Alternative implementations must satisfy.
 *
 * <p>Alternative laws tested:
 *
 * <ul>
 *   <li><b>Left Identity:</b> {@code orElse(empty(), () -> fa) == fa}
 *   <li><b>Right Identity:</b> {@code orElse(fa, () -> empty()) == fa}
 *   <li><b>Associativity:</b> {@code orElse(fa, () -> orElse(fb, () -> fc)) == orElse(orElse(fa, ()
 *       -> fb), () -> fc)}
 * </ul>
 *
 * <p>Implementations tested: Maybe, Optional, List
 *
 * <p>Note: Stream is excluded because Java Streams can only be consumed once, making them
 * unsuitable for reuse across multiple test methods. List provides equivalent coverage for
 * concatenation-based Alternative semantics.
 *
 * <p>Note: For List, the Alternative semantics are concatenation, so the identity laws hold but the
 * values may be duplicated in associativity testing.
 *
 * <p>Benefits of @TestFactory approach:
 *
 * <ul>
 *   <li>Tests are generated at runtime based on actual Alternative implementations
 *   <li>Adding new Alternative implementations automatically adds test coverage
 *   <li>Clear, structured test output showing which implementation/law combination passed/failed
 *   <li>Each test runs independently with proper isolation
 * </ul>
 */
@DisplayName("Alternative Laws - Dynamic Test Factory")
class AlternativeLawsTestFactory {

  /**
   * Represents the semantics of an Alternative implementation.
   *
   * <ul>
   *   <li>{@link #CHOICE} - First non-empty value wins (Maybe, Optional)
   *   <li>{@link #CONCATENATION} - Values are combined/concatenated (List)
   * </ul>
   */
  enum AlternativeSemantics {
    /** Choice semantics: orElse returns the first non-empty value. */
    CHOICE,
    /** Concatenation semantics: orElse combines both values. */
    CONCATENATION
  }

  /**
   * Test data record containing all information needed to test an Alternative.
   *
   * @param <F> the functor type constructor
   */
  record AlternativeTestData<F extends WitnessArity<TypeArity.Unary>>(
      String name,
      Alternative<F> alternative,
      AlternativeSemantics semantics,
      Kind<F, Integer> testValue,
      Kind<F, Integer> testValue2,
      EqualityChecker<F> equalityChecker) {

    static <F extends WitnessArity<TypeArity.Unary>> AlternativeTestData<F> of(
        String name,
        Alternative<F> alternative,
        AlternativeSemantics semantics,
        Kind<F, Integer> testValue,
        Kind<F, Integer> testValue2,
        EqualityChecker<F> checker) {
      return new AlternativeTestData<>(
          name, alternative, semantics, testValue, testValue2, checker);
    }
  }

  /** Functional interface for checking equality of Kind values */
  @FunctionalInterface
  interface EqualityChecker<M extends WitnessArity<TypeArity.Unary>> {
    <A> boolean areEqual(Kind<M, A> a, Kind<M, A> b);
  }

  /**
   * Provides test data for all Alternative implementations.
   *
   * <p>This is a centralized source of test data. Adding a new Alternative implementation requires
   * only adding one line here, and all law tests will automatically cover it.
   *
   * <p>Note: Stream is excluded because Java Streams can only be consumed once, making them
   * unsuitable for reuse across multiple test methods. List provides equivalent coverage for
   * concatenation-based Alternative semantics.
   */
  private static Stream<AlternativeTestData<?>> allAlternatives() {
    return Stream.of(
        AlternativeTestData.of(
            "Maybe",
            MaybeMonad.INSTANCE,
            AlternativeSemantics.CHOICE,
            MAYBE.widen(Maybe.just(42)),
            MAYBE.widen(Maybe.just(100)),
            new EqualityChecker<MaybeKind.Witness>() {
              @Override
              public <A> boolean areEqual(
                  Kind<MaybeKind.Witness, A> a, Kind<MaybeKind.Witness, A> b) {
                return MAYBE.narrow(a).equals(MAYBE.narrow(b));
              }
            }),
        AlternativeTestData.of(
            "Optional",
            OptionalMonad.INSTANCE,
            AlternativeSemantics.CHOICE,
            OPTIONAL.widen(Optional.of(42)),
            OPTIONAL.widen(Optional.of(100)),
            new EqualityChecker<OptionalKind.Witness>() {
              @Override
              public <A> boolean areEqual(
                  Kind<OptionalKind.Witness, A> a, Kind<OptionalKind.Witness, A> b) {
                return OPTIONAL.narrow(a).equals(OPTIONAL.narrow(b));
              }
            }),
        AlternativeTestData.of(
            "List",
            ListMonad.INSTANCE,
            AlternativeSemantics.CONCATENATION,
            LIST.widen(List.of(42)),
            LIST.widen(List.of(100)),
            new EqualityChecker<ListKind.Witness>() {
              @Override
              public <A> boolean areEqual(
                  Kind<ListKind.Witness, A> a, Kind<ListKind.Witness, A> b) {
                return LIST.narrow(a).equals(LIST.narrow(b));
              }
            }));
  }

  /**
   * Provides test data for "choice-based" alternatives (Maybe, Optional) where orElse picks the
   * first non-empty value. List uses concatenation semantics instead.
   */
  private static Stream<AlternativeTestData<?>> choiceAlternatives() {
    return allAlternatives().filter(data -> data.semantics() == AlternativeSemantics.CHOICE);
  }

  /**
   * Dynamically generates tests for the left identity law: {@code orElse(empty(), () -> fa) == fa}
   *
   * <p>This law states that combining empty with any value using orElse should return that value.
   */
  @TestFactory
  @DisplayName("Left Identity Law: orElse(empty(), () -> fa) = fa")
  Stream<DynamicTest> leftIdentityLaw() {
    return allAlternatives()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " satisfies left identity law", () -> testLeftIdentityLaw(data)));
  }

  private <F extends WitnessArity<TypeArity.Unary>> void testLeftIdentityLaw(
      AlternativeTestData<F> data) {
    Alternative<F> alt = data.alternative();
    Kind<F, Integer> fa = data.testValue();
    EqualityChecker<F> checker = data.equalityChecker();

    // Left side: orElse(empty(), () -> fa)
    Kind<F, Integer> empty = alt.empty();
    Kind<F, Integer> leftSide = alt.orElse(empty, () -> fa);

    // Right side: fa
    Kind<F, Integer> rightSide = fa;

    assertThat(checker.areEqual(leftSide, rightSide))
        .as("orElse(empty(), () -> fa) should equal fa")
        .isTrue();
  }

  /**
   * Dynamically generates tests for the right identity law: {@code orElse(fa, () -> empty()) == fa}
   *
   * <p>This law states that combining any value with empty using orElse should return that value.
   */
  @TestFactory
  @DisplayName("Right Identity Law: orElse(fa, () -> empty()) = fa")
  Stream<DynamicTest> rightIdentityLaw() {
    return allAlternatives()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " satisfies right identity law",
                    () -> testRightIdentityLaw(data)));
  }

  private <F extends WitnessArity<TypeArity.Unary>> void testRightIdentityLaw(
      AlternativeTestData<F> data) {
    Alternative<F> alt = data.alternative();
    Kind<F, Integer> fa = data.testValue();
    EqualityChecker<F> checker = data.equalityChecker();

    // Left side: orElse(fa, () -> empty())
    Kind<F, Integer> leftSide = alt.orElse(fa, alt::empty);

    // Right side: fa
    Kind<F, Integer> rightSide = fa;

    assertThat(checker.areEqual(leftSide, rightSide))
        .as("orElse(fa, () -> empty()) should equal fa")
        .isTrue();
  }

  /**
   * Dynamically generates tests for the associativity law for choice-based alternatives.
   *
   * <p>{@code orElse(fa, () -> orElse(fb, () -> fc)) == orElse(orElse(fa, () -> fb), () -> fc)}
   *
   * <p>Note: This only tests Maybe and Optional, which have "first non-empty wins" semantics. List
   * and Stream use concatenation, which is associative but produces different results.
   */
  @TestFactory
  @DisplayName(
      "Associativity Law (choice types): orElse(fa, () -> orElse(fb, () -> fc)) = orElse(orElse(fa, () -> fb), () -> fc)")
  Stream<DynamicTest> associativityLawChoice() {
    return choiceAlternatives()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " satisfies associativity law",
                    () -> testAssociativityLaw(data)));
  }

  private <F extends WitnessArity<TypeArity.Unary>> void testAssociativityLaw(
      AlternativeTestData<F> data) {
    Alternative<F> alt = data.alternative();
    EqualityChecker<F> checker = data.equalityChecker();

    // Test with empty values to properly exercise associativity
    Kind<F, Integer> fa = alt.empty();
    Kind<F, Integer> fb = alt.empty();
    Kind<F, Integer> fc = data.testValue();

    // Left side: orElse(fa, () -> orElse(fb, () -> fc))
    Kind<F, Integer> leftSide = alt.orElse(fa, () -> alt.orElse(fb, () -> fc));

    // Right side: orElse(orElse(fa, () -> fb), () -> fc)
    Kind<F, Integer> rightSide = alt.orElse(alt.orElse(fa, () -> fb), () -> fc);

    assertThat(checker.areEqual(leftSide, rightSide)).as("orElse should be associative").isTrue();
  }

  /**
   * Dynamically generates tests verifying that empty is the zero element.
   *
   * <p>This verifies basic empty() behavior across all implementations.
   */
  @TestFactory
  @DisplayName("empty() creates the zero/identity element")
  Stream<DynamicTest> emptyCreatesZero() {
    return allAlternatives()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " empty() creates identity element",
                    () -> testEmptyCreatesZero(data)));
  }

  private <F extends WitnessArity<TypeArity.Unary>> void testEmptyCreatesZero(
      AlternativeTestData<F> data) {
    Alternative<F> alt = data.alternative();
    Kind<F, Integer> fa = data.testValue();
    EqualityChecker<F> checker = data.equalityChecker();

    Kind<F, Integer> empty = alt.empty();

    // Combining empty with a value should give back the value (via orElse)
    Kind<F, Integer> result = alt.orElse(empty, () -> fa);

    assertThat(checker.areEqual(result, fa))
        .as("empty() should be the identity element for orElse")
        .isTrue();
  }

  /**
   * Dynamically generates tests for the guard operation.
   *
   * <p>guard(true) should return of(Unit), guard(false) should return empty()
   *
   * <p>Note: Only tests choice-based alternatives (Maybe, Optional) because the test verifies that
   * orElse(nonEmpty, fallback) returns just the non-empty value. For concatenation-based
   * alternatives (List), orElse combines both values, which is different semantics.
   */
  @TestFactory
  @DisplayName("guard(condition) returns of(Unit) for true, empty() for false")
  Stream<DynamicTest> guardBehavior() {
    return choiceAlternatives()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " guard() works correctly", () -> testGuardBehavior(data)));
  }

  private <F extends WitnessArity<TypeArity.Unary>> void testGuardBehavior(
      AlternativeTestData<F> data) {
    Alternative<F> alt = data.alternative();

    // guard(false) should behave like empty()
    // We verify this by checking that orElse(guard(false), () -> of(42)) equals of(42)
    // just like orElse(empty(), () -> of(42)) equals of(42)
    Kind<F, Integer> fallback = alt.of(42);

    // For guard(false), using orElse should give us the fallback
    // We use map to convert Kind<F, Unit> to Kind<F, Integer>
    Kind<F, Integer> guardFalseMapped = alt.map(_ -> 0, alt.guard(false));
    Kind<F, Integer> afterFalseGuard = alt.orElse(guardFalseMapped, () -> fallback);

    Kind<F, Integer> emptyMapped = alt.map(_ -> 0, alt.<Integer>empty());
    Kind<F, Integer> afterEmpty = alt.orElse(emptyMapped, () -> fallback);

    // Both should produce the same result (the fallback value)
    EqualityChecker<F> checker = data.equalityChecker();
    assertThat(checker.areEqual(afterFalseGuard, afterEmpty))
        .as("guard(false) should behave like empty()")
        .isTrue();

    // Also verify guard(true) produces non-empty result
    Kind<F, Integer> guardTrueMapped = alt.map(_ -> 99, alt.guard(true));
    Kind<F, Integer> afterTrueGuard = alt.orElse(guardTrueMapped, () -> fallback);

    // For choice types, this should give us 99, not 42
    Kind<F, Integer> expected = alt.of(99);
    assertThat(checker.areEqual(afterTrueGuard, expected))
        .as("guard(true) should produce non-empty value")
        .isTrue();
  }

  /**
   * Dynamically generates tests for orElse with non-empty first argument.
   *
   * <p>For choice-based alternatives (Maybe, Optional), the first non-empty value wins.
   */
  @TestFactory
  @DisplayName("orElse returns first non-empty value (choice semantics)")
  Stream<DynamicTest> orElseFirstNonEmpty() {
    return choiceAlternatives()
        .map(
            data ->
                DynamicTest.dynamicTest(
                    data.name() + " orElse returns first non-empty",
                    () -> testOrElseFirstNonEmpty(data)));
  }

  private <F extends WitnessArity<TypeArity.Unary>> void testOrElseFirstNonEmpty(
      AlternativeTestData<F> data) {
    Alternative<F> alt = data.alternative();
    Kind<F, Integer> first = data.testValue();
    Kind<F, Integer> second = data.testValue2();
    EqualityChecker<F> checker = data.equalityChecker();

    // When first is non-empty, result should be first (second not evaluated)
    Kind<F, Integer> result = alt.orElse(first, () -> second);

    assertThat(checker.areEqual(result, first))
        .as("orElse should return first value when it's non-empty")
        .isTrue();
  }
}
