// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Semigroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for OptionalPath.
 *
 * <p>Tests cover factory methods, Composable/Combinable/Chainable operations, utility methods, and
 * object methods. OptionalPath bridges java.util.Optional with the Path API.
 */
@DisplayName("OptionalPath<A> Complete Test Suite")
class OptionalPathTest {

  private static final String TEST_VALUE = "test";
  private static final Integer TEST_INT = 42;
  private static final Semigroup<List<String>> LIST_SEMIGROUP =
      (a, b) -> {
        var result = new ArrayList<>(a);
        result.addAll(b);
        return result;
      };

  @Nested
  @DisplayName("Factory Methods via Path")
  class FactoryMethodsTests {

    @Test
    @DisplayName("Path.optional() creates OptionalPath from Optional")
    void pathOptionalCreatesOptionalPath() {
      Optional<String> present = Optional.of(TEST_VALUE);
      Optional<String> empty = Optional.empty();

      OptionalPath<String> presentPath = Path.optional(present);
      OptionalPath<String> emptyPath = Path.optional(empty);

      assertThat(presentPath.run()).isPresent();
      assertThat(presentPath.run().get()).isEqualTo(TEST_VALUE);
      assertThat(emptyPath.run()).isEmpty();
    }

    @Test
    @DisplayName("Path.present() creates OptionalPath with value")
    void pathPresentCreatesOptionalPathWithValue() {
      OptionalPath<String> path = Path.present(TEST_VALUE);

      assertThat(path.run()).isPresent();
      assertThat(path.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("Path.absent() creates empty OptionalPath")
    void pathAbsentCreatesEmptyOptionalPath() {
      OptionalPath<String> path = Path.absent();

      assertThat(path.run()).isEmpty();
    }

    @Test
    @DisplayName("Path.optional() validates non-null Optional")
    void pathOptionalValidatesNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> Path.optional(null))
          .withMessageContaining("optional must not be null");
    }

    @Test
    @DisplayName("Path.present() validates non-null value")
    void pathPresentValidatesNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> Path.present(null))
          .withMessageContaining("value must not be null");
    }
  }

  @Nested
  @DisplayName("Run and Getter Methods")
  class RunAndGetterMethodsTests {

    @Test
    @DisplayName("run() returns underlying Optional")
    void runReturnsUnderlyingOptional() {
      OptionalPath<String> presentPath = Path.present(TEST_VALUE);
      OptionalPath<String> emptyPath = Path.absent();

      assertThat(presentPath.run()).isInstanceOf(Optional.class);
      assertThat(presentPath.run().get()).isEqualTo(TEST_VALUE);
      assertThat(emptyPath.run()).isEmpty();
    }

    @Test
    @DisplayName("getOrElse() returns value for present")
    void getOrElseReturnsValueForPresent() {
      OptionalPath<String> path = Path.present(TEST_VALUE);

      assertThat(path.getOrElse("default")).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("getOrElse() returns default for absent")
    void getOrElseReturnsDefaultForAbsent() {
      OptionalPath<String> path = Path.absent();

      assertThat(path.getOrElse("default")).isEqualTo("default");
    }

    @Test
    @DisplayName("getOrElseGet() returns value for present without calling supplier")
    void getOrElseGetReturnsValueForPresentWithoutCallingSupplier() {
      OptionalPath<String> path = Path.present(TEST_VALUE);
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
    @DisplayName("getOrElseGet() calls supplier for absent")
    void getOrElseGetCallsSupplierForAbsent() {
      OptionalPath<String> path = Path.absent();
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

    @Test
    @DisplayName("isPresent() returns true for present")
    void isPresentReturnsTrueForPresent() {
      OptionalPath<String> path = Path.present(TEST_VALUE);
      assertThat(path.isPresent()).isTrue();
    }

    @Test
    @DisplayName("isPresent() returns false for absent")
    void isPresentReturnsFalseForAbsent() {
      OptionalPath<String> path = Path.absent();
      assertThat(path.isPresent()).isFalse();
    }

    @Test
    @DisplayName("isEmpty() returns true for absent")
    void isEmptyReturnsTrueForAbsent() {
      OptionalPath<String> path = Path.absent();
      assertThat(path.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("isEmpty() returns false for present")
    void isEmptyReturnsFalseForPresent() {
      OptionalPath<String> path = Path.present(TEST_VALUE);
      assertThat(path.isEmpty()).isFalse();
    }
  }

  @Nested
  @DisplayName("Composable Operations (map, peek)")
  class ComposableOperationsTests {

    @Test
    @DisplayName("map() transforms value for present")
    void mapTransformsValueForPresent() {
      OptionalPath<String> path = Path.present(TEST_VALUE);

      OptionalPath<Integer> result = path.map(String::length);

      assertThat(result.run()).isPresent();
      assertThat(result.run().get()).isEqualTo(TEST_VALUE.length());
    }

    @Test
    @DisplayName("map() preserves absent")
    void mapPreservesAbsent() {
      OptionalPath<String> path = Path.absent();

      OptionalPath<Integer> result = path.map(String::length);

      assertThat(result.run()).isEmpty();
    }

    @Test
    @DisplayName("map() validates null mapper")
    void mapValidatesNullMapper() {
      OptionalPath<String> path = Path.present(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.map(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("peek() observes value without modifying")
    void peekObservesValueWithoutModifying() {
      OptionalPath<String> path = Path.present(TEST_VALUE);
      AtomicBoolean called = new AtomicBoolean(false);

      OptionalPath<String> result = path.peek(v -> called.set(true));

      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
      assertThat(called).isTrue();
    }

    @Test
    @DisplayName("peek() does not call consumer for absent")
    void peekDoesNotCallConsumerForAbsent() {
      OptionalPath<String> path = Path.absent();
      AtomicBoolean called = new AtomicBoolean(false);

      path.peek(v -> called.set(true));

      assertThat(called).isFalse();
    }
  }

  @Nested
  @DisplayName("Chainable Operations (via, flatMap, then)")
  class ChainableOperationsTests {

    @Test
    @DisplayName("via() chains dependent computations for present")
    void viaChainsComputationsForPresent() {
      OptionalPath<String> path = Path.present("hello");

      OptionalPath<Integer> result = path.via(s -> Path.present(s.length()));

      assertThat(result.run().get()).isEqualTo(5);
    }

    @Test
    @DisplayName("via() preserves absent")
    void viaPreservesAbsent() {
      OptionalPath<String> path = Path.absent();

      OptionalPath<Integer> result = path.via(s -> Path.present(s.length()));

      assertThat(result.run()).isEmpty();
    }

    @Test
    @DisplayName("via() short-circuits on absent result")
    void viaShortCircuitsOnAbsentResult() {
      OptionalPath<String> path = Path.present("hello");

      OptionalPath<Integer> result =
          path.via(s -> Path.<Integer>absent()).via(i -> Path.present(i * 2));

      assertThat(result.run()).isEmpty();
    }

    @Test
    @DisplayName("via() validates null mapper")
    void viaValidatesNullMapper() {
      OptionalPath<String> path = Path.present(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.via(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("via() validates result is OptionalPath")
    void viaValidatesResultType() {
      OptionalPath<String> path = Path.present(TEST_VALUE);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.via(s -> Path.right(s.length())))
          .withMessageContaining("via mapper must return OptionalPath");
    }

    @Test
    @DisplayName("then() sequences computations discarding value")
    void thenSequencesComputationsDiscardingValue() {
      OptionalPath<String> path = Path.present("hello");
      AtomicBoolean firstExecuted = new AtomicBoolean(false);

      OptionalPath<Integer> result =
          path.peek(v -> firstExecuted.set(true)).then(() -> Path.present(42));

      assertThat(result.run().get()).isEqualTo(42);
      assertThat(firstExecuted).isTrue();
    }

    @Test
    @DisplayName("then() preserves absent")
    void thenPreservesAbsent() {
      OptionalPath<String> path = Path.absent();

      OptionalPath<Integer> result = path.then(() -> Path.present(42));

      assertThat(result.run()).isEmpty();
    }

    @Test
    @DisplayName("then() validates result is OptionalPath")
    void thenValidatesResultType() {
      OptionalPath<String> path = Path.present(TEST_VALUE);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.then(() -> Path.right(42)))
          .withMessageContaining("then supplier must return OptionalPath");
    }
  }

  @Nested
  @DisplayName("Combinable Operations (zipWith)")
  class CombinableOperationsTests {

    @Test
    @DisplayName("zipWith() combines two present values")
    void zipWithCombinesTwoPresentValues() {
      OptionalPath<String> first = Path.present("hello");
      OptionalPath<Integer> second = Path.present(3);

      OptionalPath<String> result = first.zipWith(second, (s, n) -> s.repeat(n));

      assertThat(result.run().get()).isEqualTo("hellohellohello");
    }

    @Test
    @DisplayName("zipWith() returns absent if first is absent")
    void zipWithReturnsAbsentIfFirstIsAbsent() {
      OptionalPath<String> first = Path.absent();
      OptionalPath<Integer> second = Path.present(3);

      OptionalPath<String> result = first.zipWith(second, (s, n) -> s.repeat(n));

      assertThat(result.run()).isEmpty();
    }

    @Test
    @DisplayName("zipWith() returns absent if second is absent")
    void zipWithReturnsAbsentIfSecondIsAbsent() {
      OptionalPath<String> first = Path.present("hello");
      OptionalPath<Integer> second = Path.absent();

      OptionalPath<String> result = first.zipWith(second, (s, n) -> s.repeat(n));

      assertThat(result.run()).isEmpty();
    }

    @Test
    @DisplayName("zipWith() throws when given non-OptionalPath")
    void zipWithThrowsWhenGivenNonOptionalPath() {
      OptionalPath<String> path = Path.present(TEST_VALUE);
      EitherPath<String, Integer> eitherPath = Path.right(TEST_INT);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.zipWith(eitherPath, (s, i) -> s + i))
          .withMessageContaining("Cannot zipWith non-OptionalPath");
    }

    @Test
    @DisplayName("zipWith3() combines three present values")
    void zipWith3CombinesThreePresentValues() {
      OptionalPath<String> first = Path.present("hello");
      OptionalPath<String> second = Path.present(" ");
      OptionalPath<String> third = Path.present("world");

      OptionalPath<String> result = first.zipWith3(second, third, (a, b, c) -> a + b + c);

      assertThat(result.run().get()).isEqualTo("hello world");
    }
  }

  @Nested
  @DisplayName("Filter Operations")
  class FilterOperationsTests {

    @Test
    @DisplayName("filter() keeps value when predicate is true")
    void filterKeepsValueWhenPredicateIsTrue() {
      OptionalPath<Integer> path = Path.present(10);

      OptionalPath<Integer> result = path.filter(i -> i > 5);

      assertThat(result.run().get()).isEqualTo(10);
    }

    @Test
    @DisplayName("filter() returns absent when predicate is false")
    void filterReturnsAbsentWhenPredicateIsFalse() {
      OptionalPath<Integer> path = Path.present(10);

      OptionalPath<Integer> result = path.filter(i -> i > 15);

      assertThat(result.run()).isEmpty();
    }

    @Test
    @DisplayName("filter() preserves absent")
    void filterPreservesAbsent() {
      OptionalPath<Integer> path = Path.absent();

      OptionalPath<Integer> result = path.filter(i -> i > 5);

      assertThat(result.run()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Recovery Operations (orElsePath, orElsePathGet)")
  class RecoveryOperationsTests {

    @Test
    @DisplayName("orElsePath() returns this path when present")
    void orElsePathReturnsThisWhenPresent() {
      OptionalPath<String> path = Path.present(TEST_VALUE);

      OptionalPath<String> result = path.orElsePath("alternative");

      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("orElsePath() returns alternative path when absent")
    void orElsePathReturnsAlternativeWhenAbsent() {
      OptionalPath<String> path = Path.absent();

      OptionalPath<String> result = path.orElsePath("alternative");

      assertThat(result.run().get()).isEqualTo("alternative");
    }

    @Test
    @DisplayName("orElsePath() validates null alternative")
    void orElsePathValidatesNullAlternative() {
      OptionalPath<String> path = Path.absent();

      assertThatNullPointerException()
          .isThrownBy(() -> path.orElsePath(null))
          .withMessageContaining("alternative must not be null");
    }

    @Test
    @DisplayName("orElsePathGet() returns this path when present without calling supplier")
    void orElsePathGetReturnsThisWhenPresentWithoutCallingSupplier() {
      OptionalPath<String> path = Path.present(TEST_VALUE);
      AtomicBoolean called = new AtomicBoolean(false);

      OptionalPath<String> result =
          path.orElsePathGet(
              () -> {
                called.set(true);
                return Path.present("alternative");
              });

      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
      assertThat(called).isFalse();
    }

    @Test
    @DisplayName("orElsePathGet() returns alternative path when absent")
    void orElsePathGetReturnsAlternativeWhenAbsent() {
      OptionalPath<String> path = Path.absent();

      OptionalPath<String> result = path.orElsePathGet(() -> Path.present("alternative"));

      assertThat(result.run().get()).isEqualTo("alternative");
    }

    @Test
    @DisplayName("orElsePathGet() validates null supplier")
    void orElsePathGetValidatesNullSupplier() {
      OptionalPath<String> path = Path.absent();

      assertThatNullPointerException()
          .isThrownBy(() -> path.orElsePathGet(null))
          .withMessageContaining("alternative must not be null");
    }

    @Test
    @DisplayName("orElsePathGet() validates supplier result not null")
    void orElsePathGetValidatesSupplierResultNotNull() {
      OptionalPath<String> path = Path.absent();

      assertThatNullPointerException()
          .isThrownBy(() -> path.orElsePathGet(() -> null))
          .withMessageContaining("alternative must not return null");
    }
  }

  @Nested
  @DisplayName("Conversion Methods")
  class ConversionMethodsTests {

    @Test
    @DisplayName("toMaybePath() converts present to Just")
    void toMaybePathConvertsPresentToJust() {
      OptionalPath<String> path = Path.present(TEST_VALUE);

      MaybePath<String> result = path.toMaybePath();

      assertThat(result.run().isJust()).isTrue();
      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("toMaybePath() converts absent to Nothing")
    void toMaybePathConvertsAbsentToNothing() {
      OptionalPath<String> path = Path.absent();

      MaybePath<String> result = path.toMaybePath();

      assertThat(result.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("toEitherPath() converts present to Right")
    void toEitherPathConvertsPresentToRight() {
      OptionalPath<String> path = Path.present(TEST_VALUE);

      EitherPath<String, String> result = path.toEitherPath("error");

      assertThat(result.run().isRight()).isTrue();
      assertThat(result.run().getRight()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("toEitherPath() converts absent to Left")
    void toEitherPathConvertsAbsentToLeft() {
      OptionalPath<String> path = Path.absent();

      EitherPath<String, String> result = path.toEitherPath("error");

      assertThat(result.run().isLeft()).isTrue();
      assertThat(result.run().getLeft()).isEqualTo("error");
    }

    @Test
    @DisplayName("toValidationPath() converts present to Valid")
    void toValidationPathConvertsPresentToValid() {
      OptionalPath<String> path = Path.present(TEST_VALUE);

      ValidationPath<List<String>, String> result =
          path.toValidationPath(List.of("error"), LIST_SEMIGROUP);

      assertThat(result.run().isValid()).isTrue();
      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("toValidationPath() converts absent to Invalid")
    void toValidationPathConvertsAbsentToInvalid() {
      OptionalPath<String> path = Path.absent();

      ValidationPath<List<String>, String> result =
          path.toValidationPath(List.of("error"), LIST_SEMIGROUP);

      assertThat(result.run().isInvalid()).isTrue();
      assertThat(result.run().getError()).containsExactly("error");
    }
  }

  @Nested
  @DisplayName("Object Methods")
  class ObjectMethodsTests {

    @Test
    @DisplayName("equals() works correctly")
    void equalsWorksCorrectly() {
      OptionalPath<String> path1 = Path.present(TEST_VALUE);
      OptionalPath<String> path2 = Path.present(TEST_VALUE);
      OptionalPath<String> path3 = Path.present("different");
      OptionalPath<String> absent1 = Path.absent();
      OptionalPath<String> absent2 = Path.absent();

      assertThat(path1).isEqualTo(path2);
      assertThat(path1).isNotEqualTo(path3);
      assertThat(path1).isNotEqualTo(absent1);
      assertThat(absent1).isEqualTo(absent2);
    }

    @Test
    @DisplayName("equals() returns true for same instance")
    void equalsReturnsTrueForSameInstance() {
      OptionalPath<String> path = Path.present(TEST_VALUE);
      assertThat(path.equals(path)).isTrue();
    }

    @Test
    @DisplayName("equals() returns false for non-OptionalPath")
    void equalsReturnsFalseForNonOptionalPath() {
      OptionalPath<String> path = Path.present(TEST_VALUE);
      assertThat(path.equals("not an OptionalPath")).isFalse();
      assertThat(path.equals(null)).isFalse();
    }

    @Test
    @DisplayName("hashCode() is consistent with equals")
    void hashCodeIsConsistentWithEquals() {
      OptionalPath<String> path1 = Path.present(TEST_VALUE);
      OptionalPath<String> path2 = Path.present(TEST_VALUE);

      assertThat(path1.hashCode()).isEqualTo(path2.hashCode());
    }

    @Test
    @DisplayName("toString() provides meaningful representation")
    void toStringProvidesMeaningfulRepresentation() {
      OptionalPath<String> presentPath = Path.present(TEST_VALUE);
      OptionalPath<String> absentPath = Path.absent();

      assertThat(presentPath.toString()).contains("OptionalPath");
      assertThat(absentPath.toString()).contains("OptionalPath");
      assertThat(absentPath.toString()).contains("empty");
    }
  }

  @Nested
  @DisplayName("Java Optional Interop")
  class JavaOptionalInteropTests {

    @Test
    @DisplayName("Can wrap and unwrap Optional seamlessly")
    void canWrapAndUnwrapOptional() {
      Optional<String> original = Optional.of("test");
      OptionalPath<String> path = Path.optional(original);
      Optional<String> unwrapped = path.run();

      assertThat(unwrapped).isEqualTo(original);
    }

    @Test
    @DisplayName("Works with Optional from stream operations")
    void worksWithOptionalFromStreamOperations() {
      Optional<Integer> streamResult = Stream.of(1, 2, 3).filter(i -> i > 5).findFirst();

      OptionalPath<Integer> path = Path.optional(streamResult);

      assertThat(path.run()).isEmpty();
    }

    @Test
    @DisplayName("Can chain with Java APIs returning Optional")
    void canChainWithJavaApisReturningOptional() {
      OptionalPath<String> result =
          Path.present("123").via(s -> Path.optional(parseIntSafe(s))).map(i -> "Number: " + i);

      assertThat(result.run().get()).isEqualTo("Number: 123");
    }

    private Optional<Integer> parseIntSafe(String s) {
      try {
        return Optional.of(Integer.parseInt(s));
      } catch (NumberFormatException e) {
        return Optional.empty();
      }
    }
  }
}
