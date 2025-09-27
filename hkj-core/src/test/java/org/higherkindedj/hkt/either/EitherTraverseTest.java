// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.test.assertions.TraverseAssertions.*;
import static org.higherkindedj.hkt.test.data.TestExceptions.*;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.hkt.test.patterns.TraverseTestPattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherTraverse Core Traverse Operations Tests")
class EitherTraverseTest {

  private static final String ERROR_VALUE = "TestError";
  private static final Integer SUCCESS_VALUE = 42;

  private Traverse<EitherKind.Witness<String>> traverse;
  private Applicative<MaybeKind.Witness> maybeApplicative;
  private Kind<EitherKind.Witness<String>, Integer> rightKind;
  private Kind<EitherKind.Witness<String>, Integer> leftKind;

  @BeforeEach
  void setUp() {
    traverse = EitherTraverse.instance();
    maybeApplicative = MaybeMonad.INSTANCE;
    rightKind = EITHER.widen(Either.right(SUCCESS_VALUE));
    leftKind = EITHER.widen(Either.left(ERROR_VALUE));
  }

  @Nested
  @DisplayName("Complete Traverse Test Suite")
  class CompleteTraverseTestSuite {

    @Test
    @DisplayName("Run complete traverse test pattern")
    void runCompleteTraverseTestPattern() {
      Function<Integer, String> validMapper = i -> "Mapped:" + i;
      Function<Integer, Kind<MaybeKind.Witness, String>> validTraverseFunction =
          i -> MAYBE.widen(Maybe.just("Traversed:" + i));
      Monoid<String> stringMonoid = Monoids.string();
      Function<Integer, String> validFoldMapFunction = i -> "Fold:" + i;

      TraverseTestPattern.runComplete(
          traverse,
          rightKind,
          validMapper,
          maybeApplicative,
          validTraverseFunction,
          stringMonoid,
          validFoldMapFunction);
    }
  }

  @Nested
  @DisplayName("Traverse Operation Tests")
  class TraverseOperationTests {

    @Test
    @DisplayName("traverse() on Right with successful function")
    void traverseRightSuccessful() {
      Function<Integer, Kind<MaybeKind.Witness, String>> traverseFunc =
          i -> MAYBE.widen(Maybe.just("Traversed:" + i));

      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> result =
          traverse.traverse(maybeApplicative, traverseFunc, rightKind);

      Maybe<Kind<EitherKind.Witness<String>, String>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();

      Either<String, String> either = EITHER.narrow(maybe.get());
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).isEqualTo("Traversed:" + SUCCESS_VALUE);
    }

    @Test
    @DisplayName("traverse() on Right with failing function")
    void traverseRightFailing() {
      Function<Integer, Kind<MaybeKind.Witness, String>> failingFunc =
          i -> MAYBE.widen(Maybe.nothing());

      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> result =
          traverse.traverse(maybeApplicative, failingFunc, rightKind);

      Maybe<Kind<EitherKind.Witness<String>, String>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("traverse() on Left preserves Left in applicative context")
    void traverseLeftPreservesError() {
      Function<Integer, Kind<MaybeKind.Witness, String>> traverseFunc =
          i -> MAYBE.widen(Maybe.just("Traversed:" + i));

      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> result =
          traverse.traverse(maybeApplicative, traverseFunc, leftKind);

      Maybe<Kind<EitherKind.Witness<String>, String>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();

      Either<String, String> either = EITHER.narrow(maybe.get());
      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isEqualTo(ERROR_VALUE);
    }

    @Test
    @DisplayName("traverse() with conditional function")
    void traverseWithConditionalFunction() {
      Function<Integer, Kind<MaybeKind.Witness, String>> conditionalFunc =
          i -> i > 50 ? MAYBE.widen(Maybe.just(i.toString())) : MAYBE.widen(Maybe.nothing());

      // Should fail because 42 <= 50
      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> failResult =
          traverse.traverse(maybeApplicative, conditionalFunc, rightKind);
      assertThat(MAYBE.narrow(failResult).isNothing()).isTrue();

      // Should succeed with value > 50
      Kind<EitherKind.Witness<String>, Integer> bigRight = EITHER.widen(Either.right(100));
      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> successResult =
          traverse.traverse(maybeApplicative, conditionalFunc, bigRight);

      Maybe<Kind<EitherKind.Witness<String>, String>> maybe = MAYBE.narrow(successResult);
      assertThat(maybe.isJust()).isTrue();
      assertThat(EITHER.<String, String>narrow(maybe.get()).getRight()).isEqualTo("100");
    }

    @Test
    @DisplayName("traverse() null validations")
    void traverseNullValidations() {
      Function<Integer, Kind<MaybeKind.Witness, String>> validFunc =
          i -> MAYBE.widen(Maybe.just(i.toString()));

      assertTraverseApplicativeNull(() -> traverse.traverse(null, validFunc, rightKind));
      assertTraverseFunctionNull(() -> traverse.traverse(maybeApplicative, null, rightKind));
      assertTraverseKindNull(() -> traverse.traverse(maybeApplicative, validFunc, null));
    }
  }

  @Nested
  @DisplayName("SequenceA Operation Tests")
  class SequenceAOperationTests {

    @Test
    @DisplayName("sequenceA() turns Right<Just<A>> into Just<Right<A>>")
    void sequenceRightJustToJustRight() {
      Kind<MaybeKind.Witness, Integer> maybeKind = MAYBE.widen(Maybe.just(SUCCESS_VALUE));
      Kind<EitherKind.Witness<String>, Kind<MaybeKind.Witness, Integer>> input =
          EITHER.widen(Either.right(maybeKind));

      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, Integer>> result =
          traverse.sequenceA(maybeApplicative, input);

      Maybe<Kind<EitherKind.Witness<String>, Integer>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();

      Either<String, Integer> either = EITHER.narrow(maybe.get());
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).isEqualTo(SUCCESS_VALUE);
    }

    @Test
    @DisplayName("sequenceA() turns Right<Nothing> into Nothing")
    void sequenceRightNothingToNothing() {
      Kind<MaybeKind.Witness, Integer> nothingKind = MAYBE.widen(Maybe.nothing());
      Kind<EitherKind.Witness<String>, Kind<MaybeKind.Witness, Integer>> input =
          EITHER.widen(Either.right(nothingKind));

      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, Integer>> result =
          traverse.sequenceA(maybeApplicative, input);

      assertThat(MAYBE.narrow(result).isNothing()).isTrue();
    }

    @Test
    @DisplayName("sequenceA() preserves Left values")
    void sequenceLeftPreservesError() {
      Kind<EitherKind.Witness<String>, Kind<MaybeKind.Witness, Integer>> leftInput =
          EITHER.widen(Either.left(ERROR_VALUE));

      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, Integer>> result =
          traverse.sequenceA(maybeApplicative, leftInput);

      Maybe<Kind<EitherKind.Witness<String>, Integer>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();

      Either<String, Integer> either = EITHER.narrow(maybe.get());
      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isEqualTo(ERROR_VALUE);
    }
  }

  @Nested
  @DisplayName("Exception Handling Tests")
  class ExceptionHandlingTests {

    @Test
    @DisplayName("traverse() propagates function exceptions on Right")
    void traversePropagatesFunctionExceptions() {
      RuntimeException testException = runtime("traverse test");
      Function<Integer, Kind<MaybeKind.Witness, String>> throwingFunc =
          i -> {
            throw testException;
          };

      assertThatThrownBy(() -> traverse.traverse(maybeApplicative, throwingFunc, rightKind))
          .isSameAs(testException);
    }

    @Test
    @DisplayName("traverse() on Left doesn't call throwing function")
    void traverseOnLeftDoesntCallThrowingFunction() {
      RuntimeException testException = runtime("should not throw");
      Function<Integer, Kind<MaybeKind.Witness, String>> throwingFunc =
          i -> {
            throw testException;
          };

      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> result =
          traverse.traverse(maybeApplicative, throwingFunc, leftKind);

      Maybe<Kind<EitherKind.Witness<String>, String>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(EITHER.<String, String>narrow(maybe.get()).getLeft()).isEqualTo(ERROR_VALUE);
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("traverse() with null values in Right")
    void traverseWithNullValuesInRight() {
      Kind<EitherKind.Witness<String>, Integer> rightNull = EITHER.widen(Either.right(null));

      Function<Integer, Kind<MaybeKind.Witness, String>> nullSafeTraverse =
          i -> MAYBE.widen(Maybe.just(i == null ? "null" : i.toString()));

      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> result =
          traverse.traverse(maybeApplicative, nullSafeTraverse, rightNull);

      Maybe<Kind<EitherKind.Witness<String>, String>> maybe = MAYBE.narrow(result);
      assertThat(EITHER.<String, String>narrow(maybe.get()).getRight()).isEqualTo("null");
    }

    @Test
    @DisplayName("traverse() with null error in Left")
    void traverseWithNullErrorInLeft() {
      Kind<EitherKind.Witness<String>, Integer> leftNull = EITHER.widen(Either.left(null));

      Function<Integer, Kind<MaybeKind.Witness, String>> traverseFunc =
          i -> MAYBE.widen(Maybe.just("value"));

      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> result =
          traverse.traverse(maybeApplicative, traverseFunc, leftNull);

      Maybe<Kind<EitherKind.Witness<String>, String>> maybe = MAYBE.narrow(result);
      assertThat(EITHER.<String, String>narrow(maybe.get()).getLeft()).isNull();
    }

    @Test
    @DisplayName("traverse() with complex nested structures")
    void traverseWithComplexStructures() {
      Kind<EitherKind.Witness<String>, List<Integer>> listRight =
          EITHER.widen(Either.right(List.of(1, 2, 3)));

      Function<List<Integer>, Kind<MaybeKind.Witness, String>> listTraverse =
          list -> MAYBE.widen(Maybe.just("Size:" + list.size()));

      Traverse<EitherKind.Witness<String>> listTraverser = EitherTraverse.instance();

      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> result =
          listTraverser.traverse(maybeApplicative, listTraverse, listRight);

      Maybe<Kind<EitherKind.Witness<String>, String>> maybe = MAYBE.narrow(result);
      assertThat(EITHER.<String, String>narrow(maybe.get()).getRight()).isEqualTo("Size:3");
    }
  }

  @Nested
  @DisplayName("Type Safety Tests")
  class TypeSafetyTests {

    @Test
    @DisplayName("traverse() with different error types")
    void traverseWithDifferentErrorTypes() {
      record ComplexError(String code, int severity) {}

      Traverse<EitherKind.Witness<ComplexError>> complexTraverse = EitherTraverse.instance();

      Kind<EitherKind.Witness<ComplexError>, Integer> rightValue = EITHER.widen(Either.right(100));

      Function<Integer, Kind<MaybeKind.Witness, String>> traverseFunc =
          i -> MAYBE.widen(Maybe.just("value:" + i));

      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<ComplexError>, String>> result =
          complexTraverse.traverse(maybeApplicative, traverseFunc, rightValue);

      Maybe<Kind<EitherKind.Witness<ComplexError>, String>> maybe = MAYBE.narrow(result);
      assertThat(EITHER.<ComplexError, String>narrow(maybe.get()).getRight())
          .isEqualTo("value:100");
    }

    @Test
    @DisplayName("traverse() maintains type safety through transformations")
    void traverseMaintainsTypeSafety() {
      Kind<EitherKind.Witness<String>, Double> doubleRight = EITHER.widen(Either.right(3.14159));

      Function<Double, Kind<MaybeKind.Witness, String>> formatFunc =
          d -> MAYBE.widen(Maybe.just(String.format("%.2f", d)));

      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> result =
          traverse.traverse(maybeApplicative, formatFunc, doubleRight);

      Maybe<Kind<EitherKind.Witness<String>, String>> maybe = MAYBE.narrow(result);
      assertThat(EITHER.<String, String>narrow(maybe.get()).getRight()).isEqualTo("3.14");
    }
  }

  @Nested
  @DisplayName("Performance Tests")
  class PerformanceTests {

    @Test
    @DisplayName("traverse() with large data structures")
    void traverseWithLargeDataStructures() {
      List<Integer> largeList = IntStream.range(0, 1000).boxed().collect(Collectors.toList());

      Kind<EitherKind.Witness<String>, List<Integer>> largeRight =
          EITHER.widen(Either.right(largeList));

      Traverse<EitherKind.Witness<String>> listTraverser = EitherTraverse.instance();

      Function<List<Integer>, Kind<MaybeKind.Witness, String>> largeTraverse =
          list -> MAYBE.widen(Maybe.just("Processed " + list.size() + " items"));

      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> result =
          listTraverser.traverse(maybeApplicative, largeTraverse, largeRight);

      Maybe<Kind<EitherKind.Witness<String>, String>> maybe = MAYBE.narrow(result);
      assertThat(EITHER.<String, String>narrow(maybe.get()).getRight())
          .isEqualTo("Processed 1000 items");
    }

    @Test
    @DisplayName("traverse() efficient with Left values")
    void traverseEfficientWithLeftValues() {
      // Left values should not traverse, so even expensive functions are safe
      Function<Integer, Kind<MaybeKind.Witness, String>> expensiveFunc =
          i -> {
            // Simulate expensive computation
            return MAYBE.widen(Maybe.just("expensive:" + i));
          };

      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> result =
          traverse.traverse(maybeApplicative, expensiveFunc, leftKind);

      // Should complete quickly without calling expensive function
      Maybe<Kind<EitherKind.Witness<String>, String>> maybe = MAYBE.narrow(result);
      assertThat(EITHER.<String, String>narrow(maybe.get()).getLeft()).isEqualTo(ERROR_VALUE);
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("traverse() integrates with map")
    void traverseIntegratesWithMap() {
      Kind<EitherKind.Witness<String>, Integer> start = rightKind;

      Function<Integer, String> mapper = i -> "mapped:" + i;
      Kind<EitherKind.Witness<String>, String> mapped = traverse.map(mapper, start);

      Function<String, Kind<MaybeKind.Witness, String>> traverseFunc =
          s -> MAYBE.widen(Maybe.just(s.toUpperCase()));

      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> result =
          traverse.traverse(maybeApplicative, traverseFunc, mapped);

      Maybe<Kind<EitherKind.Witness<String>, String>> maybe = MAYBE.narrow(result);
      assertThat(EITHER.<String, String>narrow(maybe.get()).getRight())
          .isEqualTo("MAPPED:" + SUCCESS_VALUE);
    }

    @Test
    @DisplayName("traverse() integrates with foldMap")
    void traverseIntegratesWithFoldMap() {
      Monoid<String> stringMonoid = Monoids.string();

      // First fold, then traverse the result
      String folded = traverse.foldMap(stringMonoid, i -> "fold:" + i, rightKind);

      Kind<EitherKind.Witness<String>, String> foldedKind = EITHER.widen(Either.right(folded));

      Function<String, Kind<MaybeKind.Witness, String>> traverseFunc =
          s -> MAYBE.widen(Maybe.just("traversed:" + s));

      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> result =
          traverse.traverse(maybeApplicative, traverseFunc, foldedKind);

      Maybe<Kind<EitherKind.Witness<String>, String>> maybe = MAYBE.narrow(result);
      assertThat(EITHER.<String, String>narrow(maybe.get()).getRight())
          .isEqualTo("traversed:fold:" + SUCCESS_VALUE);
    }

    @Test
    @DisplayName("traverse() composition with sequenceA")
    void traverseCompositionWithSequenceA() {
      // Create nested structure
      Kind<MaybeKind.Witness, Integer> maybeValue = MAYBE.widen(Maybe.just(100));
      Kind<EitherKind.Witness<String>, Kind<MaybeKind.Witness, Integer>> nested =
          EITHER.widen(Either.right(maybeValue));

      // Sequence to swap nesting
      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, Integer>> sequenced =
          traverse.sequenceA(maybeApplicative, nested);

      // Verify structure
      Maybe<Kind<EitherKind.Witness<String>, Integer>> maybe = MAYBE.narrow(sequenced);
      assertThat(maybe.isJust()).isTrue();
      assertThat(EITHER.<String, Integer>narrow(maybe.get()).getRight()).isEqualTo(100);
    }
  }

  @Nested
  @DisplayName("Traverse Laws Tests")
  class TraverseLawsTests {

    @Test
    @DisplayName("Traverse preserves structure")
    void traversePreservesStructure() {
      Function<Integer, Kind<MaybeKind.Witness, String>> identityLike =
          i -> MAYBE.widen(Maybe.just(i.toString()));

      // Right case
      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> rightResult =
          traverse.traverse(maybeApplicative, identityLike, rightKind);
      assertThat(MAYBE.narrow(rightResult).isJust()).isTrue();

      // Left case
      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> leftResult =
          traverse.traverse(maybeApplicative, identityLike, leftKind);
      assertThat(MAYBE.narrow(leftResult).isJust()).isTrue();
      assertThat(EITHER.<String, String>narrow(MAYBE.narrow(leftResult).get()).isLeft()).isTrue();
    }

    @Test
    @DisplayName("Traverse naturality property")
    void traverseNaturality() {
      // For Either, naturality means that traverse with pure values
      // gives same structure as pure of the mapped structure
      Function<Integer, Kind<MaybeKind.Witness, String>> pureTraverse =
          i -> maybeApplicative.of("pure:" + i);

      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> traverseResult =
          traverse.traverse(maybeApplicative, pureTraverse, rightKind);

      // Map then pure
      Kind<EitherKind.Witness<String>, String> mapped = traverse.map(i -> "pure:" + i, rightKind);
      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> pureResult =
          maybeApplicative.of(mapped);

      // Both should give Just(Right("pure:42"))
      Maybe<Kind<EitherKind.Witness<String>, String>> traverseMaybe = MAYBE.narrow(traverseResult);
      Maybe<Kind<EitherKind.Witness<String>, String>> pureMaybe = MAYBE.narrow(pureResult);

      assertThat(traverseMaybe.isJust()).isTrue();
      assertThat(pureMaybe.isJust()).isTrue();
      assertThat(EITHER.<String, String>narrow(traverseMaybe.get()))
          .isEqualTo(EITHER.<String, String>narrow(pureMaybe.get()));
    }
  }
}
