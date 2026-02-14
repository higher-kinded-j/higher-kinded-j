// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.higherkindedj.hkt.effect.capability.Recoverable;
import org.higherkindedj.hkt.maybe.Maybe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for MaybePath.
 *
 * <p>Tests cover factory methods, Composable/Combinable/Chainable/Recoverable operations, utility
 * methods, and object methods.
 */
@DisplayName("MaybePath<A> Complete Test Suite")
class MaybePathTest {

  private static final String TEST_VALUE = "test";
  private static final Integer TEST_INT = 42;

  @Nested
  @DisplayName("Factory Methods via Path")
  class FactoryMethodsTests {

    @Test
    @DisplayName("Path.just() creates MaybePath with value")
    void pathJustCreatesMaybePathWithValue() {
      MaybePath<String> path = Path.just(TEST_VALUE);

      assertThat(path.run().isJust()).isTrue();
      assertThat(path.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("Path.nothing() creates empty MaybePath")
    void pathNothingCreatesEmptyMaybePath() {
      MaybePath<String> path = Path.nothing();

      assertThat(path.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("Path.maybe() creates MaybePath from Maybe")
    void pathMaybeCreatesMaybePathFromMaybe() {
      Maybe<String> just = Maybe.just(TEST_VALUE);
      Maybe<String> nothing = Maybe.nothing();

      MaybePath<String> justPath = Path.maybe(just);
      MaybePath<String> nothingPath = Path.maybe(nothing);

      assertThat(justPath.run().isJust()).isTrue();
      assertThat(justPath.run().get()).isEqualTo(TEST_VALUE);
      assertThat(nothingPath.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("Path.just() validates non-null")
    void pathJustValidatesNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> Path.just(null))
          .withMessageContaining("value cannot be null");
    }

    @Test
    @DisplayName("Path.maybe() with nullable value creates Just for non-null")
    void pathMaybeNullableCreatesJustForNonNull() {
      String value = "hello";
      MaybePath<String> path = Path.maybe(value);

      assertThat(path.run().isJust()).isTrue();
      assertThat(path.run().get()).isEqualTo("hello");
    }

    @Test
    @DisplayName("Path.maybe() with nullable value creates Nothing for null")
    void pathMaybeNullableCreatesNothingForNull() {
      String value = null;
      MaybePath<String> path = Path.maybe(value);

      assertThat(path.run().isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Run and Getter Methods")
  class RunAndGetterMethodsTests {

    @Test
    @DisplayName("run() returns underlying Maybe")
    void runReturnsUnderlyingMaybe() {
      MaybePath<String> justPath = Path.just(TEST_VALUE);
      MaybePath<String> nothingPath = Path.nothing();

      assertThat(justPath.run()).isInstanceOf(Maybe.class);
      assertThat(justPath.run().get()).isEqualTo(TEST_VALUE);
      assertThat(nothingPath.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("getOrElse() returns value for Just")
    void getOrElseReturnsValueForJust() {
      MaybePath<String> path = Path.just(TEST_VALUE);

      assertThat(path.getOrElse("default")).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("getOrElse() returns default for Nothing")
    void getOrElseReturnsDefaultForNothing() {
      MaybePath<String> path = Path.nothing();

      assertThat(path.getOrElse("default")).isEqualTo("default");
    }

    @Test
    @DisplayName("getOrElseGet() returns value for Just without calling supplier")
    void getOrElseGetReturnsValueForJustWithoutCallingSupplier() {
      MaybePath<String> path = Path.just(TEST_VALUE);
      AtomicBoolean called = new AtomicBoolean(false);

      String result =
          path.getOrElseGet(
              () -> {
                called.set(true);
                return "default";
              });

      assertThat(result).isEqualTo(TEST_VALUE);
      assertThat(called).isFalse();
    }

    @Test
    @DisplayName("getOrElseGet() calls supplier for Nothing")
    void getOrElseGetCallsSupplierForNothing() {
      MaybePath<String> path = Path.nothing();
      AtomicBoolean called = new AtomicBoolean(false);

      String result =
          path.getOrElseGet(
              () -> {
                called.set(true);
                return "default";
              });

      assertThat(result).isEqualTo("default");
      assertThat(called).isTrue();
    }
  }

  @Nested
  @DisplayName("Composable Operations (map, peek)")
  class ComposableOperationsTests {

    @Test
    @DisplayName("map() transforms value for Just")
    void mapTransformsValueForJust() {
      MaybePath<String> path = Path.just(TEST_VALUE);

      MaybePath<Integer> result = path.map(String::length);

      assertThat(result.run().isJust()).isTrue();
      assertThat(result.run().get()).isEqualTo(TEST_VALUE.length());
    }

    @Test
    @DisplayName("map() preserves Nothing")
    void mapPreservesNothing() {
      MaybePath<String> path = Path.nothing();

      MaybePath<Integer> result = path.map(String::length);

      assertThat(result.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("map() validates null mapper")
    void mapValidatesNullMapper() {
      MaybePath<String> path = Path.just(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.map(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("map() chains correctly")
    void mapChainsCorrectly() {
      MaybePath<String> path = Path.just("hello");

      MaybePath<String> result =
          path.map(String::toUpperCase).map(s -> s + "!").map(s -> s.repeat(2));

      assertThat(result.run().get()).isEqualTo("HELLO!HELLO!");
    }

    @Test
    @DisplayName("peek() observes value without modifying")
    void peekObservesValueWithoutModifying() {
      MaybePath<String> path = Path.just(TEST_VALUE);
      AtomicBoolean called = new AtomicBoolean(false);

      MaybePath<String> result = path.peek(v -> called.set(true));

      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
      assertThat(called).isTrue();
    }

    @Test
    @DisplayName("peek() does not call consumer for Nothing")
    void peekDoesNotCallConsumerForNothing() {
      MaybePath<String> path = Path.nothing();
      AtomicBoolean called = new AtomicBoolean(false);

      path.peek(v -> called.set(true));

      assertThat(called).isFalse();
    }
  }

  @Nested
  @DisplayName("Chainable Operations (via, flatMap, then)")
  class ChainableOperationsTests {

    @Test
    @DisplayName("via() chains dependent computations for Just")
    void viaChainsComputationsForJust() {
      MaybePath<String> path = Path.just("hello");

      MaybePath<Integer> result = path.via(s -> Path.just(s.length()));

      assertThat(result.run().get()).isEqualTo(5);
    }

    @Test
    @DisplayName("via() preserves Nothing")
    void viaPreservesNothing() {
      MaybePath<String> path = Path.nothing();

      MaybePath<Integer> result = path.via(s -> Path.just(s.length()));

      assertThat(result.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("via() short-circuits on Nothing result")
    void viaShortCircuitsOnNothing() {
      MaybePath<String> path = Path.just("hello");

      MaybePath<Integer> result = path.via(s -> Path.<Integer>nothing()).via(i -> Path.just(i * 2));

      assertThat(result.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("via() validates null mapper")
    void viaValidatesNullMapper() {
      MaybePath<String> path = Path.just(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.via(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("via() validates non-null result")
    void viaValidatesNonNullResult() {
      MaybePath<String> path = Path.just(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.via(s -> null))
          .withMessageContaining("mapper must not return null");
    }

    @Test
    @DisplayName("via() validates result is MaybePath")
    void viaValidatesResultType() {
      MaybePath<String> path = Path.just(TEST_VALUE);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.via(s -> Path.right(s.length())))
          .withMessageContaining("via mapper must return MaybePath");
    }

    @Test
    @DisplayName("flatMap() is alias for via")
    void flatMapIsAliasForVia() {
      MaybePath<String> path = Path.just("hello");

      MaybePath<Integer> viaResult = path.via(s -> Path.just(s.length()));
      // flatMap returns Chainable<B> from interface, but via returns MaybePath<B>
      // Cast to MaybePath to access run() and verify they produce the same result
      @SuppressWarnings("unchecked")
      MaybePath<Integer> flatMapResult =
          (MaybePath<Integer>) path.flatMap(s -> Path.just(s.length()));
      assertThat(flatMapResult.run().get()).isEqualTo(viaResult.run().get());
    }

    @Test
    @DisplayName("then() sequences computations discarding value")
    void thenSequencesComputationsDiscardingValue() {
      MaybePath<String> path = Path.just("hello");
      AtomicBoolean firstExecuted = new AtomicBoolean(false);

      MaybePath<Integer> result = path.peek(v -> firstExecuted.set(true)).then(() -> Path.just(42));

      assertThat(result.run().get()).isEqualTo(42);
      assertThat(firstExecuted).isTrue();
    }

    @Test
    @DisplayName("then() preserves Nothing")
    void thenPreservesNothing() {
      MaybePath<String> path = Path.nothing();

      MaybePath<Integer> result = path.then(() -> Path.just(42));

      assertThat(result.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("then() throws when supplier returns wrong type")
    void thenThrowsWhenSupplierReturnsWrongType() {
      MaybePath<String> path = Path.just(TEST_VALUE);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.then(() -> Path.right(42)))
          .withMessageContaining("then supplier must return MaybePath");
    }
  }

  @Nested
  @DisplayName("Combinable Operations (zipWith)")
  class CombinableOperationsTests {

    @Test
    @DisplayName("zipWith() combines two Just values")
    void zipWithCombinesTwoJustValues() {
      MaybePath<String> first = Path.just("hello");
      MaybePath<Integer> second = Path.just(3);

      MaybePath<String> result = first.zipWith(second, (s, n) -> s.repeat(n));

      assertThat(result.run().get()).isEqualTo("hellohellohello");
    }

    @Test
    @DisplayName("zipWith() returns Nothing if first is Nothing")
    void zipWithReturnsNothingIfFirstIsNothing() {
      MaybePath<String> first = Path.nothing();
      MaybePath<Integer> second = Path.just(3);

      MaybePath<String> result = first.zipWith(second, (s, n) -> s.repeat(n));

      assertThat(result.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("zipWith() returns Nothing if second is Nothing")
    void zipWithReturnsNothingIfSecondIsNothing() {
      MaybePath<String> first = Path.just("hello");
      MaybePath<Integer> second = Path.nothing();

      MaybePath<String> result = first.zipWith(second, (s, n) -> s.repeat(n));

      assertThat(result.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("zipWith() validates null parameters")
    void zipWithValidatesNullParameters() {
      MaybePath<String> path = Path.just(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(null, (a, b) -> a + b))
          .withMessageContaining("other must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(Path.just("x"), null))
          .withMessageContaining("combiner must not be null");
    }

    @Test
    @DisplayName("zipWith3() combines three Just values")
    void zipWith3CombinesThreeJustValues() {
      MaybePath<String> first = Path.just("hello");
      MaybePath<String> second = Path.just(" ");
      MaybePath<String> third = Path.just("world");

      MaybePath<String> result = first.zipWith3(second, third, (a, b, c) -> a + b + c);

      assertThat(result.run().get()).isEqualTo("hello world");
    }

    @Test
    @DisplayName("zipWith() throws when given non-MaybePath")
    void zipWithThrowsWhenGivenNonMaybePath() {
      MaybePath<String> path = Path.just(TEST_VALUE);
      EitherPath<String, Integer> eitherPath = Path.right(TEST_INT);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.zipWith(eitherPath, (s, i) -> s + i))
          .withMessageContaining("Cannot zipWith non-MaybePath");
    }

    @Test
    @DisplayName("zipWith3() returns Nothing if first is Nothing")
    void zipWith3ReturnsNothingIfFirstIsNothing() {
      MaybePath<String> first = Path.nothing();
      MaybePath<String> second = Path.just(" ");
      MaybePath<String> third = Path.just("world");

      MaybePath<String> result = first.zipWith3(second, third, (a, b, c) -> a + b + c);

      assertThat(result.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("zipWith3() returns Nothing if second is Nothing")
    void zipWith3ReturnsNothingIfSecondIsNothing() {
      MaybePath<String> first = Path.just("hello");
      MaybePath<String> second = Path.nothing();
      MaybePath<String> third = Path.just("world");

      MaybePath<String> result = first.zipWith3(second, third, (a, b, c) -> a + b + c);

      assertThat(result.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("zipWith3() returns Nothing if third is Nothing")
    void zipWith3ReturnsNothingIfThirdIsNothing() {
      MaybePath<String> first = Path.just("hello");
      MaybePath<String> second = Path.just(" ");
      MaybePath<String> third = Path.nothing();

      MaybePath<String> result = first.zipWith3(second, third, (a, b, c) -> a + b + c);

      assertThat(result.run().isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Recoverable Operations")
  class RecoverableOperationsTests {

    @Test
    @DisplayName("recover() provides fallback for Nothing")
    void recoverProvidesFallbackForNothing() {
      MaybePath<String> path = Path.nothing();

      MaybePath<String> result = path.recover(unit -> "default");

      assertThat(result.run().get()).isEqualTo("default");
    }

    @Test
    @DisplayName("recover() preserves Just value")
    void recoverPreservesJustValue() {
      MaybePath<String> path = Path.just(TEST_VALUE);

      MaybePath<String> result = path.recover(unit -> "default");

      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("recoverWith() provides fallback path for Nothing")
    void recoverWithProvidesFallbackPathForNothing() {
      MaybePath<String> path = Path.nothing();

      MaybePath<String> result = path.recoverWith(unit -> Path.just("fallback"));

      assertThat(result.run().get()).isEqualTo("fallback");
    }

    @Test
    @DisplayName("recoverWith() preserves Just value")
    void recoverWithPreservesJustValue() {
      MaybePath<String> path = Path.just(TEST_VALUE);

      MaybePath<String> result = path.recoverWith(unit -> Path.just("fallback"));

      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("orElse() provides alternative path for Nothing")
    void orElseAlternativeForNothing() {
      MaybePath<String> path = Path.nothing();

      MaybePath<String> result = path.orElse(() -> Path.just("alternative"));

      assertThat(result.run().get()).isEqualTo("alternative");
    }

    @Test
    @DisplayName("orElse() preserves Just value")
    void orElsePreservesJustValue() {
      MaybePath<String> path = Path.just(TEST_VALUE);
      AtomicBoolean called = new AtomicBoolean(false);

      MaybePath<String> result =
          path.orElse(
              () -> {
                called.set(true);
                return Path.just("alternative");
              });

      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
      assertThat(called).isFalse();
    }

    @Test
    @DisplayName("mapError() is a no-op for MaybePath")
    void mapErrorIsNoOpForMaybePath() {
      MaybePath<String> justPath = Path.just(TEST_VALUE);
      MaybePath<String> nothingPath = Path.nothing();

      // mapError returns Recoverable<E2, A> from interface
      // For MaybePath, it's effectively a no-op and returns the same instance
      Recoverable<String, String> justResult = justPath.mapError(unit -> "error");
      Recoverable<String, String> nothingResult = nothingPath.mapError(unit -> "error");

      assertThat(justResult).isSameAs(justPath);
      assertThat(nothingResult).isSameAs(nothingPath);
    }

    @Test
    @DisplayName("recoverWith() throws when recovery returns wrong type")
    void recoverWithThrowsWhenRecoveryReturnsWrongType() {
      MaybePath<String> path = Path.nothing();

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.recoverWith(unit -> Path.right("fallback")))
          .withMessageContaining("recovery must return MaybePath");
    }

    @Test
    @DisplayName("orElse() throws when alternative returns wrong type")
    void orElseThrowsWhenAlternativeReturnsWrongType() {
      MaybePath<String> path = Path.nothing();

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.orElse(() -> Path.right("alternative")))
          .withMessageContaining("alternative must return MaybePath");
    }
  }

  @Nested
  @DisplayName("Filter Operations")
  class FilterOperationsTests {

    @Test
    @DisplayName("filter() keeps value when predicate is true")
    void filterKeepsValueWhenPredicateIsTrue() {
      MaybePath<Integer> path = Path.just(10);

      MaybePath<Integer> result = path.filter(i -> i > 5);

      assertThat(result.run().get()).isEqualTo(10);
    }

    @Test
    @DisplayName("filter() returns Nothing when predicate is false")
    void filterReturnsNothingWhenPredicateIsFalse() {
      MaybePath<Integer> path = Path.just(10);

      MaybePath<Integer> result = path.filter(i -> i > 15);

      assertThat(result.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("filter() preserves Nothing")
    void filterPreservesNothing() {
      MaybePath<Integer> path = Path.nothing();

      MaybePath<Integer> result = path.filter(i -> i > 5);

      assertThat(result.run().isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Conversion Methods")
  class ConversionMethodsTests {

    @Test
    @DisplayName("toEitherPath() converts Just to Right")
    void toEitherPathConvertsJustToRight() {
      MaybePath<String> path = Path.just(TEST_VALUE);

      EitherPath<String, String> result = path.toEitherPath("error");

      assertThat(result.run().isRight()).isTrue();
      assertThat(result.run().getRight()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("toEitherPath() converts Nothing to Left with error")
    void toEitherPathConvertsNothingToLeftWithError() {
      MaybePath<String> path = Path.nothing();

      EitherPath<String, String> result = path.toEitherPath("error");

      assertThat(result.run().isLeft()).isTrue();
      assertThat(result.run().getLeft()).isEqualTo("error");
    }

    @Test
    @DisplayName("toTryPath() converts Just to Success")
    void toTryPathConvertsJustToSuccess() {
      MaybePath<String> path = Path.just(TEST_VALUE);

      TryPath<String> result = path.toTryPath(() -> new RuntimeException("error"));

      assertThat(result.run().isSuccess()).isTrue();
      assertThat(result.getOrElse(null)).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("toTryPath() converts Nothing to Failure")
    void toTryPathConvertsNothingToFailure() {
      MaybePath<String> path = Path.nothing();
      RuntimeException error = new RuntimeException("error");

      TryPath<String> result = path.toTryPath(() -> error);

      assertThat(result.run().isFailure()).isTrue();
    }
  }

  @Nested
  @DisplayName("Object Methods")
  class ObjectMethodsTests {

    @Test
    @DisplayName("equals() works correctly")
    void equalsWorksCorrectly() {
      MaybePath<String> path1 = Path.just(TEST_VALUE);
      MaybePath<String> path2 = Path.just(TEST_VALUE);
      MaybePath<String> path3 = Path.just("different");
      MaybePath<String> nothing1 = Path.nothing();
      MaybePath<String> nothing2 = Path.nothing();

      assertThat(path1).isEqualTo(path2);
      assertThat(path1).isNotEqualTo(path3);
      assertThat(path1).isNotEqualTo(nothing1);
      assertThat(nothing1).isEqualTo(nothing2);
    }

    @Test
    @DisplayName("equals() returns true for same instance")
    void equalsReturnsTrueForSameInstance() {
      MaybePath<String> path = Path.just(TEST_VALUE);
      assertThat(path.equals(path)).isTrue();
    }

    @Test
    @DisplayName("equals() returns false for non-MaybePath")
    void equalsReturnsFalseForNonMaybePath() {
      MaybePath<String> path = Path.just(TEST_VALUE);
      assertThat(path.equals("not a MaybePath")).isFalse();
      assertThat(path.equals(null)).isFalse();
      assertThat(path.equals(Path.right(TEST_VALUE))).isFalse();
    }

    @Test
    @DisplayName("hashCode() is consistent with equals")
    void hashCodeIsConsistentWithEquals() {
      MaybePath<String> path1 = Path.just(TEST_VALUE);
      MaybePath<String> path2 = Path.just(TEST_VALUE);

      assertThat(path1.hashCode()).isEqualTo(path2.hashCode());
    }

    @Test
    @DisplayName("toString() provides meaningful representation")
    void toStringProvidesMeaningfulRepresentation() {
      MaybePath<String> justPath = Path.just(TEST_VALUE);
      MaybePath<String> nothingPath = Path.nothing();

      assertThat(justPath.toString()).contains("MaybePath");
      assertThat(justPath.toString()).contains("Just");
      assertThat(nothingPath.toString()).contains("MaybePath");
      assertThat(nothingPath.toString()).contains("Nothing");
    }
  }

  @Nested
  @DisplayName("Complex Chaining Patterns")
  class ComplexChainingPatternsTests {

    @Test
    @DisplayName("Railway-oriented programming pattern")
    void railwayOrientedProgrammingPattern() {
      Function<String, MaybePath<Integer>> parseInteger =
          s -> {
            try {
              return Path.just(Integer.parseInt(s));
            } catch (NumberFormatException e) {
              return Path.nothing();
            }
          };

      Function<Integer, MaybePath<Double>> squareRoot =
          i -> i < 0 ? Path.nothing() : Path.just(Math.sqrt(i));

      MaybePath<Double> success = Path.just("16").via(parseInteger).via(squareRoot);

      assertThat(success.run().get()).isEqualTo(4.0);

      MaybePath<Double> parseFailure = Path.just("not-a-number").via(parseInteger).via(squareRoot);

      assertThat(parseFailure.run().isNothing()).isTrue();

      MaybePath<Double> negativeFailure = Path.just("-4").via(parseInteger).via(squareRoot);

      assertThat(negativeFailure.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("Combining multiple sources")
    void combiningMultipleSources() {
      MaybePath<String> firstName = Path.just("John");
      MaybePath<String> lastName = Path.just("Doe");
      MaybePath<Integer> age = Path.just(30);

      MaybePath<String> fullRecord =
          firstName.zipWith3(
              lastName, age, (first, last, a) -> String.format("%s %s, age %d", first, last, a));

      assertThat(fullRecord.run().get()).isEqualTo("John Doe, age 30");
    }

    @Test
    @DisplayName("Recovery with fallback chain")
    void recoveryWithFallbackChain() {
      MaybePath<String> primary = Path.nothing();
      MaybePath<String> secondary = Path.nothing();
      MaybePath<String> tertiary = Path.just("fallback");

      MaybePath<String> result = primary.orElse(() -> secondary).orElse(() -> tertiary);

      assertThat(result.run().get()).isEqualTo("fallback");
    }
  }
}
