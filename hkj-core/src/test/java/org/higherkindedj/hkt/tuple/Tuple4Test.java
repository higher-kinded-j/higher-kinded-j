// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.tuple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Comprehensive test suite for {@link Tuple4}. */
@DisplayName("Tuple4 Test Suite")
class Tuple4Test {

  @Nested
  @DisplayName("Constructor and Accessors")
  class ConstructorAndAccessors {

    @Test
    @DisplayName("Constructor creates tuple with correct elements")
    void constructorCreatesCorrectTuple() {
      Tuple4<String, Integer, Boolean, Double> tuple = new Tuple4<>("Alice", 30, true, 5.5);

      assertThat(tuple._1()).isEqualTo("Alice");
      assertThat(tuple._2()).isEqualTo(30);
      assertThat(tuple._3()).isTrue();
      assertThat(tuple._4()).isEqualTo(5.5);
    }

    @Test
    @DisplayName("Constructor accepts null elements")
    void constructorAcceptsNullElements() {
      Tuple4<String, Integer, Boolean, Double> tuple = new Tuple4<>(null, null, null, null);

      assertThat(tuple._1()).isNull();
      assertThat(tuple._2()).isNull();
      assertThat(tuple._3()).isNull();
      assertThat(tuple._4()).isNull();
    }
  }

  @Nested
  @DisplayName("map() - Transform All Elements")
  class MapAllElements {

    @Test
    @DisplayName("map() transforms all four elements")
    void mapTransformsAllElements() {
      Tuple4<String, Integer, Boolean, Double> tuple = new Tuple4<>("Alice", 30, true, 5.5);

      Tuple4<Integer, String, String, Integer> result =
          tuple.map(
              String::length,
              age -> age + " years",
              active -> active ? "yes" : "no",
              d -> d.intValue());

      assertThat(result._1()).isEqualTo(5);
      assertThat(result._2()).isEqualTo("30 years");
      assertThat(result._3()).isEqualTo("yes");
      assertThat(result._4()).isEqualTo(5);
    }

    @Test
    @DisplayName("map() with identity functions returns equivalent tuple")
    void mapWithIdentityReturnsEquivalent() {
      Tuple4<String, Integer, Boolean, Double> original = new Tuple4<>("Alice", 30, true, 5.5);

      Tuple4<String, Integer, Boolean, Double> result =
          original.map(
              Function.identity(),
              Function.identity(),
              Function.identity(),
              Function.identity());

      assertThat(result).isEqualTo(original);
    }

    @Test
    @DisplayName("map() throws NullPointerException when firstMapper is null")
    void mapThrowsWhenFirstMapperNull() {
      Tuple4<String, Integer, Boolean, Double> tuple = new Tuple4<>("Alice", 30, true, 5.5);

      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  tuple.map(
                      null, age -> age + 1, active -> !active, d -> d + 1.0))
          .withMessageContaining("firstMapper")
          .withMessageContaining("Tuple4.map");
    }

    @Test
    @DisplayName("map() throws NullPointerException when secondMapper is null")
    void mapThrowsWhenSecondMapperNull() {
      Tuple4<String, Integer, Boolean, Double> tuple = new Tuple4<>("Alice", 30, true, 5.5);

      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  tuple.map(
                      String::length, null, active -> !active, d -> d + 1.0))
          .withMessageContaining("secondMapper")
          .withMessageContaining("Tuple4.map");
    }

    @Test
    @DisplayName("map() throws NullPointerException when thirdMapper is null")
    void mapThrowsWhenThirdMapperNull() {
      Tuple4<String, Integer, Boolean, Double> tuple = new Tuple4<>("Alice", 30, true, 5.5);

      assertThatNullPointerException()
          .isThrownBy(() -> tuple.map(String::length, age -> age + 1, null, d -> d + 1.0))
          .withMessageContaining("thirdMapper")
          .withMessageContaining("Tuple4.map");
    }

    @Test
    @DisplayName("map() throws NullPointerException when fourthMapper is null")
    void mapThrowsWhenFourthMapperNull() {
      Tuple4<String, Integer, Boolean, Double> tuple = new Tuple4<>("Alice", 30, true, 5.5);

      assertThatNullPointerException()
          .isThrownBy(
              () -> tuple.map(String::length, age -> age + 1, active -> !active, null))
          .withMessageContaining("fourthMapper")
          .withMessageContaining("Tuple4.map");
    }
  }

  @Nested
  @DisplayName("mapFirst() - Transform First Element")
  class MapFirstElement {

    @Test
    @DisplayName("mapFirst() transforms only the first element")
    void mapFirstTransformsOnlyFirst() {
      Tuple4<String, Integer, Boolean, Double> tuple = new Tuple4<>("Alice", 30, true, 5.5);

      Tuple4<Integer, Integer, Boolean, Double> result = tuple.mapFirst(String::length);

      assertThat(result._1()).isEqualTo(5);
      assertThat(result._2()).isEqualTo(30);
      assertThat(result._3()).isTrue();
      assertThat(result._4()).isEqualTo(5.5);
    }

    @Test
    @DisplayName("mapFirst() with identity returns equivalent tuple")
    void mapFirstWithIdentityReturnsEquivalent() {
      Tuple4<String, Integer, Boolean, Double> original = new Tuple4<>("Alice", 30, true, 5.5);

      Tuple4<String, Integer, Boolean, Double> result = original.mapFirst(Function.identity());

      assertThat(result).isEqualTo(original);
    }

    @Test
    @DisplayName("mapFirst() throws NullPointerException when mapper is null")
    void mapFirstThrowsWhenMapperNull() {
      Tuple4<String, Integer, Boolean, Double> tuple = new Tuple4<>("Alice", 30, true, 5.5);

      assertThatNullPointerException()
          .isThrownBy(() -> tuple.mapFirst(null))
          .withMessageContaining("firstMapper")
          .withMessageContaining("Tuple4.mapFirst");
    }
  }

  @Nested
  @DisplayName("mapSecond() - Transform Second Element")
  class MapSecondElement {

    @Test
    @DisplayName("mapSecond() transforms only the second element")
    void mapSecondTransformsOnlySecond() {
      Tuple4<String, Integer, Boolean, Double> tuple = new Tuple4<>("Alice", 30, true, 5.5);

      Tuple4<String, String, Boolean, Double> result = tuple.mapSecond(age -> age + " years");

      assertThat(result._1()).isEqualTo("Alice");
      assertThat(result._2()).isEqualTo("30 years");
      assertThat(result._3()).isTrue();
      assertThat(result._4()).isEqualTo(5.5);
    }

    @Test
    @DisplayName("mapSecond() with identity returns equivalent tuple")
    void mapSecondWithIdentityReturnsEquivalent() {
      Tuple4<String, Integer, Boolean, Double> original = new Tuple4<>("Alice", 30, true, 5.5);

      Tuple4<String, Integer, Boolean, Double> result = original.mapSecond(Function.identity());

      assertThat(result).isEqualTo(original);
    }

    @Test
    @DisplayName("mapSecond() throws NullPointerException when mapper is null")
    void mapSecondThrowsWhenMapperNull() {
      Tuple4<String, Integer, Boolean, Double> tuple = new Tuple4<>("Alice", 30, true, 5.5);

      assertThatNullPointerException()
          .isThrownBy(() -> tuple.mapSecond(null))
          .withMessageContaining("secondMapper")
          .withMessageContaining("Tuple4.mapSecond");
    }
  }

  @Nested
  @DisplayName("mapThird() - Transform Third Element")
  class MapThirdElement {

    @Test
    @DisplayName("mapThird() transforms only the third element")
    void mapThirdTransformsOnlyThird() {
      Tuple4<String, Integer, Boolean, Double> tuple = new Tuple4<>("Alice", 30, true, 5.5);

      Tuple4<String, Integer, String, Double> result =
          tuple.mapThird(active -> active ? "yes" : "no");

      assertThat(result._1()).isEqualTo("Alice");
      assertThat(result._2()).isEqualTo(30);
      assertThat(result._3()).isEqualTo("yes");
      assertThat(result._4()).isEqualTo(5.5);
    }

    @Test
    @DisplayName("mapThird() with identity returns equivalent tuple")
    void mapThirdWithIdentityReturnsEquivalent() {
      Tuple4<String, Integer, Boolean, Double> original = new Tuple4<>("Alice", 30, true, 5.5);

      Tuple4<String, Integer, Boolean, Double> result = original.mapThird(Function.identity());

      assertThat(result).isEqualTo(original);
    }

    @Test
    @DisplayName("mapThird() throws NullPointerException when mapper is null")
    void mapThirdThrowsWhenMapperNull() {
      Tuple4<String, Integer, Boolean, Double> tuple = new Tuple4<>("Alice", 30, true, 5.5);

      assertThatNullPointerException()
          .isThrownBy(() -> tuple.mapThird(null))
          .withMessageContaining("thirdMapper")
          .withMessageContaining("Tuple4.map");
    }
  }

  @Nested
  @DisplayName("mapFourth() - Transform Fourth Element")
  class MapFourthElement {

    @Test
    @DisplayName("mapFourth() transforms only the fourth element")
    void mapFourthTransformsOnlyFourth() {
      Tuple4<String, Integer, Boolean, Double> tuple = new Tuple4<>("Alice", 30, true, 5.5);

      Tuple4<String, Integer, Boolean, Integer> result = tuple.mapFourth(Double::intValue);

      assertThat(result._1()).isEqualTo("Alice");
      assertThat(result._2()).isEqualTo(30);
      assertThat(result._3()).isTrue();
      assertThat(result._4()).isEqualTo(5);
    }

    @Test
    @DisplayName("mapFourth() with identity returns equivalent tuple")
    void mapFourthWithIdentityReturnsEquivalent() {
      Tuple4<String, Integer, Boolean, Double> original = new Tuple4<>("Alice", 30, true, 5.5);

      Tuple4<String, Integer, Boolean, Double> result = original.mapFourth(Function.identity());

      assertThat(result).isEqualTo(original);
    }

    @Test
    @DisplayName("mapFourth() throws NullPointerException when mapper is null")
    void mapFourthThrowsWhenMapperNull() {
      Tuple4<String, Integer, Boolean, Double> tuple = new Tuple4<>("Alice", 30, true, 5.5);

      assertThatNullPointerException()
          .isThrownBy(() -> tuple.mapFourth(null))
          .withMessageContaining("fourthMapper")
          .withMessageContaining("Tuple4.map");
    }
  }

  @Nested
  @DisplayName("Record Methods - equals, hashCode, toString")
  class RecordMethods {

    @Test
    @DisplayName("equals() returns true for tuples with same values")
    void equalsReturnsTrueForSameValues() {
      Tuple4<String, Integer, Boolean, Double> tuple1 = new Tuple4<>("Alice", 30, true, 5.5);
      Tuple4<String, Integer, Boolean, Double> tuple2 = new Tuple4<>("Alice", 30, true, 5.5);

      assertThat(tuple1).isEqualTo(tuple2);
    }

    @Test
    @DisplayName("equals() returns false for tuples with different values")
    void equalsReturnsFalseForDifferentValues() {
      Tuple4<String, Integer, Boolean, Double> tuple1 = new Tuple4<>("Alice", 30, true, 5.5);
      Tuple4<String, Integer, Boolean, Double> tuple2 = new Tuple4<>("Bob", 30, true, 5.5);
      Tuple4<String, Integer, Boolean, Double> tuple3 = new Tuple4<>("Alice", 25, true, 5.5);
      Tuple4<String, Integer, Boolean, Double> tuple4 = new Tuple4<>("Alice", 30, false, 5.5);
      Tuple4<String, Integer, Boolean, Double> tuple5 = new Tuple4<>("Alice", 30, true, 6.5);

      assertThat(tuple1).isNotEqualTo(tuple2);
      assertThat(tuple1).isNotEqualTo(tuple3);
      assertThat(tuple1).isNotEqualTo(tuple4);
      assertThat(tuple1).isNotEqualTo(tuple5);
    }

    @Test
    @DisplayName("hashCode() is consistent with equals()")
    void hashCodeConsistentWithEquals() {
      Tuple4<String, Integer, Boolean, Double> tuple1 = new Tuple4<>("Alice", 30, true, 5.5);
      Tuple4<String, Integer, Boolean, Double> tuple2 = new Tuple4<>("Alice", 30, true, 5.5);

      assertThat(tuple1.hashCode()).isEqualTo(tuple2.hashCode());
    }

    @Test
    @DisplayName("toString() contains all element values")
    void toStringContainsAllValues() {
      Tuple4<String, Integer, Boolean, Double> tuple = new Tuple4<>("Alice", 30, true, 5.5);

      String result = tuple.toString();

      assertThat(result).contains("Alice", "30", "true", "5.5");
    }
  }

  @Nested
  @DisplayName("Immutability and Mapper Execution")
  class ImmutabilityAndMapperExecution {

    @Test
    @DisplayName("Mapping operations do not modify original tuple")
    void mappingDoesNotModifyOriginal() {
      Tuple4<String, Integer, Boolean, Double> original = new Tuple4<>("Alice", 30, true, 5.5);

      original.map(String::toUpperCase, n -> n * 2, b -> !b, d -> d * 2);
      original.mapFirst(String::toUpperCase);
      original.mapSecond(n -> n * 2);
      original.mapThird(b -> !b);
      original.mapFourth(d -> d * 2);

      // Original should be unchanged
      assertThat(original._1()).isEqualTo("Alice");
      assertThat(original._2()).isEqualTo(30);
      assertThat(original._3()).isTrue();
      assertThat(original._4()).isEqualTo(5.5);
    }

    @Test
    @DisplayName("Mapper functions are actually executed")
    void mapperFunctionsExecuted() {
      final boolean[] firstMapperCalled = {false};
      final boolean[] secondMapperCalled = {false};
      final boolean[] thirdMapperCalled = {false};
      final boolean[] fourthMapperCalled = {false};

      Tuple4<String, Integer, Boolean, Double> tuple = new Tuple4<>("test", 10, true, 3.14);

      tuple.map(
          s -> {
            firstMapperCalled[0] = true;
            return s.length();
          },
          i -> {
            secondMapperCalled[0] = true;
            return i.toString();
          },
          b -> {
            thirdMapperCalled[0] = true;
            return b.toString();
          },
          d -> {
            fourthMapperCalled[0] = true;
            return d.intValue();
          });

      assertThat(firstMapperCalled[0]).isTrue();
      assertThat(secondMapperCalled[0]).isTrue();
      assertThat(thirdMapperCalled[0]).isTrue();
      assertThat(fourthMapperCalled[0]).isTrue();
    }
  }
}
