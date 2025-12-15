// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;
import org.higherkindedj.hkt.id.Id;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for IdPath.
 *
 * <p>Tests cover factory methods, Composable/Combinable/Chainable operations, utility methods, and
 * object methods. IdPath is the simplest path type with no error handling.
 */
@DisplayName("IdPath<A> Complete Test Suite")
class IdPathTest {

  private static final String TEST_VALUE = "test";
  private static final Integer TEST_INT = 42;

  @Nested
  @DisplayName("Factory Methods via Path")
  class FactoryMethodsTests {

    @Test
    @DisplayName("Path.id() creates IdPath with value")
    void pathIdCreatesIdPathWithValue() {
      IdPath<String> path = Path.id(TEST_VALUE);

      assertThat(path.run().value()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("Path.id() accepts null value")
    void pathIdAcceptsNullValue() {
      IdPath<String> path = Path.id(null);

      assertThat(path.run().value()).isNull();
    }

    @Test
    @DisplayName("Path.idPath() creates IdPath from Id")
    void pathIdPathCreatesIdPathFromId() {
      Id<String> id = Id.of(TEST_VALUE);

      IdPath<String> path = Path.idPath(id);

      assertThat(path.run().value()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("Path.idPath() validates non-null Id")
    void pathIdPathValidatesNonNullId() {
      assertThatNullPointerException()
          .isThrownBy(() -> Path.idPath(null))
          .withMessageContaining("id must not be null");
    }
  }

  @Nested
  @DisplayName("Run and Getter Methods")
  class RunAndGetterMethodsTests {

    @Test
    @DisplayName("run() returns underlying Id")
    void runReturnsUnderlyingId() {
      IdPath<String> path = Path.id(TEST_VALUE);

      assertThat(path.run()).isInstanceOf(Id.class);
      assertThat(path.run().value()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("get() returns the wrapped value")
    void getReturnsWrappedValue() {
      IdPath<String> path = Path.id(TEST_VALUE);

      assertThat(path.get()).isEqualTo(TEST_VALUE);
    }
  }

  @Nested
  @DisplayName("Composable Operations (map, peek)")
  class ComposableOperationsTests {

    @Test
    @DisplayName("map() transforms value")
    void mapTransformsValue() {
      IdPath<String> path = Path.id(TEST_VALUE);

      IdPath<Integer> result = path.map(String::length);

      assertThat(result.run().value()).isEqualTo(TEST_VALUE.length());
    }

    @Test
    @DisplayName("map() validates null mapper")
    void mapValidatesNullMapper() {
      IdPath<String> path = Path.id(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.map(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("map() chains correctly")
    void mapChainsCorrectly() {
      IdPath<String> path = Path.id("hello");

      IdPath<String> result = path.map(String::toUpperCase).map(s -> s + "!").map(s -> s.repeat(2));

      assertThat(result.run().value()).isEqualTo("HELLO!HELLO!");
    }

    @Test
    @DisplayName("peek() observes value without modifying")
    void peekObservesValueWithoutModifying() {
      IdPath<String> path = Path.id(TEST_VALUE);
      AtomicBoolean called = new AtomicBoolean(false);

      IdPath<String> result = path.peek(v -> called.set(true));

      assertThat(result.run().value()).isEqualTo(TEST_VALUE);
      assertThat(called).isTrue();
    }
  }

  @Nested
  @DisplayName("Chainable Operations (via, flatMap, then)")
  class ChainableOperationsTests {

    @Test
    @DisplayName("via() chains dependent computations")
    void viaChainsComputations() {
      IdPath<String> path = Path.id("hello");

      IdPath<Integer> result = path.via(s -> Path.id(s.length()));

      assertThat(result.run().value()).isEqualTo(5);
    }

    @Test
    @DisplayName("via() validates null mapper")
    void viaValidatesNullMapper() {
      IdPath<String> path = Path.id(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.via(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("via() validates non-null result")
    void viaValidatesNonNullResult() {
      IdPath<String> path = Path.id(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.via(s -> null))
          .withMessageContaining("mapper must not return null");
    }

    @Test
    @DisplayName("via() validates result is IdPath")
    void viaValidatesResultType() {
      IdPath<String> path = Path.id(TEST_VALUE);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.via(s -> Path.right(s.length())))
          .withMessageContaining("via mapper must return IdPath");
    }

    @Test
    @DisplayName("flatMap() is alias for via")
    void flatMapIsAliasForVia() {
      IdPath<String> path = Path.id("hello");

      IdPath<Integer> viaResult = path.via(s -> Path.id(s.length()));
      @SuppressWarnings("unchecked")
      IdPath<Integer> flatMapResult = (IdPath<Integer>) path.flatMap(s -> Path.id(s.length()));

      assertThat(flatMapResult.run().value()).isEqualTo(viaResult.run().value());
    }

    @Test
    @DisplayName("then() sequences computations discarding value")
    void thenSequencesComputationsDiscardingValue() {
      IdPath<String> path = Path.id("hello");
      AtomicBoolean firstExecuted = new AtomicBoolean(false);

      IdPath<Integer> result = path.peek(v -> firstExecuted.set(true)).then(() -> Path.id(42));

      assertThat(result.run().value()).isEqualTo(42);
      assertThat(firstExecuted).isTrue();
    }

    @Test
    @DisplayName("then() throws when supplier returns wrong type")
    void thenThrowsWhenSupplierReturnsWrongType() {
      IdPath<String> path = Path.id(TEST_VALUE);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.then(() -> Path.right(42)))
          .withMessageContaining("then supplier must return IdPath");
    }
  }

  @Nested
  @DisplayName("Combinable Operations (zipWith)")
  class CombinableOperationsTests {

    @Test
    @DisplayName("zipWith() combines two values")
    void zipWithCombinesTwoValues() {
      IdPath<String> first = Path.id("hello");
      IdPath<Integer> second = Path.id(3);

      IdPath<String> result = first.zipWith(second, (s, n) -> s.repeat(n));

      assertThat(result.run().value()).isEqualTo("hellohellohello");
    }

    @Test
    @DisplayName("zipWith() validates null parameters")
    void zipWithValidatesNullParameters() {
      IdPath<String> path = Path.id(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(null, (a, b) -> a + b))
          .withMessageContaining("other must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(Path.id("x"), null))
          .withMessageContaining("combiner must not be null");
    }

    @Test
    @DisplayName("zipWith() throws when given non-IdPath")
    void zipWithThrowsWhenGivenNonIdPath() {
      IdPath<String> path = Path.id(TEST_VALUE);
      EitherPath<String, Integer> eitherPath = Path.right(TEST_INT);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.zipWith(eitherPath, (s, i) -> s + i))
          .withMessageContaining("Cannot zipWith non-IdPath");
    }

    @Test
    @DisplayName("zipWith3() combines three values")
    void zipWith3CombinesThreeValues() {
      IdPath<String> first = Path.id("hello");
      IdPath<String> second = Path.id(" ");
      IdPath<String> third = Path.id("world");

      IdPath<String> result = first.zipWith3(second, third, (a, b, c) -> a + b + c);

      assertThat(result.run().value()).isEqualTo("hello world");
    }
  }

  @Nested
  @DisplayName("Conversion Methods")
  class ConversionMethodsTests {

    @Test
    @DisplayName("toMaybePath() converts to Just for non-null value")
    void toMaybePathConvertsToJust() {
      IdPath<String> path = Path.id(TEST_VALUE);

      MaybePath<String> result = path.toMaybePath();

      assertThat(result.run().isJust()).isTrue();
      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("toMaybePath() converts to Nothing for null value")
    void toMaybePathConvertsToNothing() {
      IdPath<String> path = Path.id(null);

      MaybePath<String> result = path.toMaybePath();

      assertThat(result.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("toEitherPath() converts to Right for non-null value")
    void toEitherPathConvertsToRight() {
      IdPath<String> path = Path.id(TEST_VALUE);

      EitherPath<String, String> result = path.toEitherPath();

      assertThat(result.run().isRight()).isTrue();
      assertThat(result.run().getRight()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("toEitherPath() converts to Right with null for null value")
    void toEitherPathConvertsToRightWithNull() {
      IdPath<String> path = Path.id(null);

      EitherPath<String, String> result = path.toEitherPath();

      // IdPath always succeeds, so toEitherPath() always returns Right
      assertThat(result.run().isRight()).isTrue();
      assertThat(result.run().getRight()).isNull();
    }
  }

  @Nested
  @DisplayName("Object Methods")
  class ObjectMethodsTests {

    @Test
    @DisplayName("equals() works correctly")
    void equalsWorksCorrectly() {
      IdPath<String> path1 = Path.id(TEST_VALUE);
      IdPath<String> path2 = Path.id(TEST_VALUE);
      IdPath<String> path3 = Path.id("different");
      IdPath<String> nullPath1 = Path.id(null);
      IdPath<String> nullPath2 = Path.id(null);

      assertThat(path1).isEqualTo(path2);
      assertThat(path1).isNotEqualTo(path3);
      assertThat(nullPath1).isEqualTo(nullPath2);
    }

    @Test
    @DisplayName("equals() returns true for same instance")
    void equalsReturnsTrueForSameInstance() {
      IdPath<String> path = Path.id(TEST_VALUE);
      assertThat(path.equals(path)).isTrue();
    }

    @Test
    @DisplayName("equals() returns false for non-IdPath")
    void equalsReturnsFalseForNonIdPath() {
      IdPath<String> path = Path.id(TEST_VALUE);
      assertThat(path.equals("not an IdPath")).isFalse();
      assertThat(path.equals(null)).isFalse();
    }

    @Test
    @DisplayName("hashCode() is consistent with equals")
    void hashCodeIsConsistentWithEquals() {
      IdPath<String> path1 = Path.id(TEST_VALUE);
      IdPath<String> path2 = Path.id(TEST_VALUE);

      assertThat(path1.hashCode()).isEqualTo(path2.hashCode());
    }

    @Test
    @DisplayName("toString() provides meaningful representation")
    void toStringProvidesMeaningfulRepresentation() {
      IdPath<String> path = Path.id(TEST_VALUE);

      assertThat(path.toString()).contains("IdPath");
      assertThat(path.toString()).contains(TEST_VALUE);
    }
  }

  @Nested
  @DisplayName("Identity Laws")
  class IdentityLawsTests {

    @Test
    @DisplayName("IdPath always contains exactly one value")
    void idPathAlwaysContainsValue() {
      IdPath<String> path = Path.id(TEST_VALUE);

      // IdPath has no failure case - it always has a value
      assertThat(path.get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("Chaining always succeeds")
    void chainingAlwaysSucceeds() {
      IdPath<Integer> result =
          Path.id("hello").via(s -> Path.id(s.length())).via(n -> Path.id(n * 2));

      assertThat(result.get()).isEqualTo(10);
    }
  }
}
