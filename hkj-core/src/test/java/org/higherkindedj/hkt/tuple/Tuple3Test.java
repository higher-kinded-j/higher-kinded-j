// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.tuple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Comprehensive test suite for {@link Tuple3}. */
@DisplayName("Tuple3 Test Suite")
class Tuple3Test {

  @Nested
  @DisplayName("Constructor and Accessors")
  class ConstructorAndAccessors {

    @Test
    @DisplayName("Constructor creates tuple with correct elements")
    void constructorCreatesCorrectTuple() {
      Tuple3<String, Integer, Boolean> tuple = new Tuple3<>("Alice", 30, true);

      assertThat(tuple._1()).isEqualTo("Alice");
      assertThat(tuple._2()).isEqualTo(30);
      assertThat(tuple._3()).isTrue();
    }

    @Test
    @DisplayName("Constructor accepts null elements")
    void constructorAcceptsNullElements() {
      Tuple3<String, Integer, Boolean> tuple = new Tuple3<>(null, null, null);

      assertThat(tuple._1()).isNull();
      assertThat(tuple._2()).isNull();
      assertThat(tuple._3()).isNull();
    }
  }

  @Nested
  @DisplayName("map() - Transform All Elements")
  class MapAllElements {

    @Test
    @DisplayName("map() transforms all three elements")
    void mapTransformsAllElements() {
      Tuple3<String, Integer, Boolean> tuple = new Tuple3<>("Alice", 30, true);

      Tuple3<Integer, String, String> result =
          tuple.map(String::length, age -> age + " years", active -> active ? "yes" : "no");

      assertThat(result._1()).isEqualTo(5);
      assertThat(result._2()).isEqualTo("30 years");
      assertThat(result._3()).isEqualTo("yes");
    }

    @Test
    @DisplayName("map() with identity functions returns equivalent tuple")
    void mapWithIdentityReturnsEquivalent() {
      Tuple3<String, Integer, Boolean> original = new Tuple3<>("Alice", 30, true);

      Tuple3<String, Integer, Boolean> result =
          original.map(Function.identity(), Function.identity(), Function.identity());

      assertThat(result).isEqualTo(original);
    }

    @Test
    @DisplayName("map() throws NullPointerException when firstMapper is null")
    void mapThrowsWhenFirstMapperNull() {
      Tuple3<String, Integer, Boolean> tuple = new Tuple3<>("Alice", 30, true);

      assertThatNullPointerException()
          .isThrownBy(() -> tuple.map(null, age -> age + 1, active -> !active))
          .withMessageContaining("firstMapper")
          .withMessageContaining("Tuple3.map");
    }

    @Test
    @DisplayName("map() throws NullPointerException when secondMapper is null")
    void mapThrowsWhenSecondMapperNull() {
      Tuple3<String, Integer, Boolean> tuple = new Tuple3<>("Alice", 30, true);

      assertThatNullPointerException()
          .isThrownBy(() -> tuple.map(String::length, null, active -> !active))
          .withMessageContaining("secondMapper")
          .withMessageContaining("Tuple3.map");
    }

    @Test
    @DisplayName("map() throws NullPointerException when thirdMapper is null")
    void mapThrowsWhenThirdMapperNull() {
      Tuple3<String, Integer, Boolean> tuple = new Tuple3<>("Alice", 30, true);

      assertThatNullPointerException()
          .isThrownBy(() -> tuple.map(String::length, age -> age + 1, null))
          .withMessageContaining("thirdMapper")
          .withMessageContaining("Tuple3.map");
    }
  }

  @Nested
  @DisplayName("mapFirst() - Transform First Element")
  class MapFirstElement {

    @Test
    @DisplayName("mapFirst() transforms only the first element")
    void mapFirstTransformsOnlyFirst() {
      Tuple3<String, Integer, Boolean> tuple = new Tuple3<>("Alice", 30, true);

      Tuple3<Integer, Integer, Boolean> result = tuple.mapFirst(String::length);

      assertThat(result._1()).isEqualTo(5);
      assertThat(result._2()).isEqualTo(30);
      assertThat(result._3()).isTrue();
    }

    @Test
    @DisplayName("mapFirst() with identity returns equivalent tuple")
    void mapFirstWithIdentityReturnsEquivalent() {
      Tuple3<String, Integer, Boolean> original = new Tuple3<>("Alice", 30, true);

      Tuple3<String, Integer, Boolean> result = original.mapFirst(Function.identity());

      assertThat(result).isEqualTo(original);
    }

    @Test
    @DisplayName("mapFirst() throws NullPointerException when mapper is null")
    void mapFirstThrowsWhenMapperNull() {
      Tuple3<String, Integer, Boolean> tuple = new Tuple3<>("Alice", 30, true);

      assertThatNullPointerException()
          .isThrownBy(() -> tuple.mapFirst(null))
          .withMessageContaining("firstMapper")
          .withMessageContaining("Tuple3.mapFirst");
    }
  }

  @Nested
  @DisplayName("mapSecond() - Transform Second Element")
  class MapSecondElement {

    @Test
    @DisplayName("mapSecond() transforms only the second element")
    void mapSecondTransformsOnlySecond() {
      Tuple3<String, Integer, Boolean> tuple = new Tuple3<>("Alice", 30, true);

      Tuple3<String, String, Boolean> result = tuple.mapSecond(age -> age + " years");

      assertThat(result._1()).isEqualTo("Alice");
      assertThat(result._2()).isEqualTo("30 years");
      assertThat(result._3()).isTrue();
    }

    @Test
    @DisplayName("mapSecond() with identity returns equivalent tuple")
    void mapSecondWithIdentityReturnsEquivalent() {
      Tuple3<String, Integer, Boolean> original = new Tuple3<>("Alice", 30, true);

      Tuple3<String, Integer, Boolean> result = original.mapSecond(Function.identity());

      assertThat(result).isEqualTo(original);
    }

    @Test
    @DisplayName("mapSecond() throws NullPointerException when mapper is null")
    void mapSecondThrowsWhenMapperNull() {
      Tuple3<String, Integer, Boolean> tuple = new Tuple3<>("Alice", 30, true);

      assertThatNullPointerException()
          .isThrownBy(() -> tuple.mapSecond(null))
          .withMessageContaining("secondMapper")
          .withMessageContaining("Tuple3.mapSecond");
    }
  }

  @Nested
  @DisplayName("mapThird() - Transform Third Element")
  class MapThirdElement {

    @Test
    @DisplayName("mapThird() transforms only the third element")
    void mapThirdTransformsOnlyThird() {
      Tuple3<String, Integer, Boolean> tuple = new Tuple3<>("Alice", 30, true);

      Tuple3<String, Integer, String> result = tuple.mapThird(active -> active ? "yes" : "no");

      assertThat(result._1()).isEqualTo("Alice");
      assertThat(result._2()).isEqualTo(30);
      assertThat(result._3()).isEqualTo("yes");
    }

    @Test
    @DisplayName("mapThird() with identity returns equivalent tuple")
    void mapThirdWithIdentityReturnsEquivalent() {
      Tuple3<String, Integer, Boolean> original = new Tuple3<>("Alice", 30, true);

      Tuple3<String, Integer, Boolean> result = original.mapThird(Function.identity());

      assertThat(result).isEqualTo(original);
    }

    @Test
    @DisplayName("mapThird() throws NullPointerException when mapper is null")
    void mapThirdThrowsWhenMapperNull() {
      Tuple3<String, Integer, Boolean> tuple = new Tuple3<>("Alice", 30, true);

      assertThatNullPointerException()
          .isThrownBy(() -> tuple.mapThird(null))
          .withMessageContaining("thirdMapper")
          .withMessageContaining("Tuple3.map");
    }
  }

  @Nested
  @DisplayName("Record Methods - equals, hashCode, toString")
  class RecordMethods {

    @Test
    @DisplayName("equals() returns true for tuples with same values")
    void equalsReturnsTrueForSameValues() {
      Tuple3<String, Integer, Boolean> tuple1 = new Tuple3<>("Alice", 30, true);
      Tuple3<String, Integer, Boolean> tuple2 = new Tuple3<>("Alice", 30, true);

      assertThat(tuple1).isEqualTo(tuple2);
    }

    @Test
    @DisplayName("equals() returns false for tuples with different values")
    void equalsReturnsFalseForDifferentValues() {
      Tuple3<String, Integer, Boolean> tuple1 = new Tuple3<>("Alice", 30, true);
      Tuple3<String, Integer, Boolean> tuple2 = new Tuple3<>("Bob", 30, true);
      Tuple3<String, Integer, Boolean> tuple3 = new Tuple3<>("Alice", 25, true);
      Tuple3<String, Integer, Boolean> tuple4 = new Tuple3<>("Alice", 30, false);

      assertThat(tuple1).isNotEqualTo(tuple2);
      assertThat(tuple1).isNotEqualTo(tuple3);
      assertThat(tuple1).isNotEqualTo(tuple4);
    }

    @Test
    @DisplayName("hashCode() is consistent with equals()")
    void hashCodeConsistentWithEquals() {
      Tuple3<String, Integer, Boolean> tuple1 = new Tuple3<>("Alice", 30, true);
      Tuple3<String, Integer, Boolean> tuple2 = new Tuple3<>("Alice", 30, true);

      assertThat(tuple1.hashCode()).isEqualTo(tuple2.hashCode());
    }

    @Test
    @DisplayName("toString() contains all element values")
    void toStringContainsAllValues() {
      Tuple3<String, Integer, Boolean> tuple = new Tuple3<>("Alice", 30, true);

      String result = tuple.toString();

      assertThat(result).contains("Alice", "30", "true");
    }
  }
}
