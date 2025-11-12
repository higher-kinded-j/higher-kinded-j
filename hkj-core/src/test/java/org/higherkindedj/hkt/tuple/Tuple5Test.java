// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.tuple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Comprehensive test suite for {@link Tuple5}. */
@DisplayName("Tuple5 Test Suite")
class Tuple5Test {

  @Nested
  @DisplayName("Constructor and Accessors")
  class ConstructorAndAccessors {

    @Test
    @DisplayName("Constructor creates tuple with correct elements")
    void constructorCreatesCorrectTuple() {
      Tuple5<String, Integer, Boolean, Double, Character> tuple =
          new Tuple5<>("Alice", 30, true, 5.5, 'A');

      assertThat(tuple._1()).isEqualTo("Alice");
      assertThat(tuple._2()).isEqualTo(30);
      assertThat(tuple._3()).isTrue();
      assertThat(tuple._4()).isEqualTo(5.5);
      assertThat(tuple._5()).isEqualTo('A');
    }

    @Test
    @DisplayName("Constructor accepts null elements")
    void constructorAcceptsNullElements() {
      Tuple5<String, Integer, Boolean, Double, Character> tuple =
          new Tuple5<>(null, null, null, null, null);

      assertThat(tuple._1()).isNull();
      assertThat(tuple._2()).isNull();
      assertThat(tuple._3()).isNull();
      assertThat(tuple._4()).isNull();
      assertThat(tuple._5()).isNull();
    }
  }

  @Nested
  @DisplayName("map() - Transform All Elements")
  class MapAllElements {

    @Test
    @DisplayName("map() transforms all five elements")
    void mapTransformsAllElements() {
      Tuple5<String, Integer, Boolean, Double, Character> tuple =
          new Tuple5<>("Alice", 30, true, 5.5, 'A');

      Tuple5<Integer, String, String, Integer, String> result =
          tuple.map(
              String::length,
              age -> age + " years",
              active -> active ? "yes" : "no",
              Double::intValue,
              c -> c.toString());

      assertThat(result._1()).isEqualTo(5);
      assertThat(result._2()).isEqualTo("30 years");
      assertThat(result._3()).isEqualTo("yes");
      assertThat(result._4()).isEqualTo(5);
      assertThat(result._5()).isEqualTo("A");
    }

    @Test
    @DisplayName("map() with identity functions returns equivalent tuple")
    void mapWithIdentityReturnsEquivalent() {
      Tuple5<String, Integer, Boolean, Double, Character> original =
          new Tuple5<>("Alice", 30, true, 5.5, 'A');

      Tuple5<String, Integer, Boolean, Double, Character> result =
          original.map(
              Function.identity(),
              Function.identity(),
              Function.identity(),
              Function.identity(),
              Function.identity());

      assertThat(result).isEqualTo(original);
    }

    @Test
    @DisplayName("map() throws NullPointerException when firstMapper is null")
    void mapThrowsWhenFirstMapperNull() {
      Tuple5<String, Integer, Boolean, Double, Character> tuple =
          new Tuple5<>("Alice", 30, true, 5.5, 'A');

      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  tuple.map(
                      null,
                      age -> age + 1,
                      active -> !active,
                      d -> d + 1.0,
                      c -> Character.toLowerCase(c)))
          .withMessageContaining("firstMapper")
          .withMessageContaining("Tuple5.map");
    }

    @Test
    @DisplayName("map() throws NullPointerException when secondMapper is null")
    void mapThrowsWhenSecondMapperNull() {
      Tuple5<String, Integer, Boolean, Double, Character> tuple =
          new Tuple5<>("Alice", 30, true, 5.5, 'A');

      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  tuple.map(
                      String::length,
                      null,
                      active -> !active,
                      d -> d + 1.0,
                      c -> Character.toLowerCase(c)))
          .withMessageContaining("secondMapper")
          .withMessageContaining("Tuple5.map");
    }

    @Test
    @DisplayName("map() throws NullPointerException when thirdMapper is null")
    void mapThrowsWhenThirdMapperNull() {
      Tuple5<String, Integer, Boolean, Double, Character> tuple =
          new Tuple5<>("Alice", 30, true, 5.5, 'A');

      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  tuple.map(
                      String::length,
                      age -> age + 1,
                      null,
                      d -> d + 1.0,
                      c -> Character.toLowerCase(c)))
          .withMessageContaining("thirdMapper")
          .withMessageContaining("Tuple5.map");
    }

    @Test
    @DisplayName("map() throws NullPointerException when fourthMapper is null")
    void mapThrowsWhenFourthMapperNull() {
      Tuple5<String, Integer, Boolean, Double, Character> tuple =
          new Tuple5<>("Alice", 30, true, 5.5, 'A');

      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  tuple.map(
                      String::length,
                      age -> age + 1,
                      active -> !active,
                      null,
                      c -> Character.toLowerCase(c)))
          .withMessageContaining("fourthMapper")
          .withMessageContaining("Tuple5.map");
    }

    @Test
    @DisplayName("map() throws NullPointerException when fifthMapper is null")
    void mapThrowsWhenFifthMapperNull() {
      Tuple5<String, Integer, Boolean, Double, Character> tuple =
          new Tuple5<>("Alice", 30, true, 5.5, 'A');

      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  tuple.map(
                      String::length, age -> age + 1, active -> !active, d -> d + 1.0, null))
          .withMessageContaining("fifthMapper")
          .withMessageContaining("Tuple5.map");
    }
  }

  @Nested
  @DisplayName("mapFirst() - Transform First Element")
  class MapFirstElement {

    @Test
    @DisplayName("mapFirst() transforms only the first element")
    void mapFirstTransformsOnlyFirst() {
      Tuple5<String, Integer, Boolean, Double, Character> tuple =
          new Tuple5<>("Alice", 30, true, 5.5, 'A');

      Tuple5<Integer, Integer, Boolean, Double, Character> result = tuple.mapFirst(String::length);

      assertThat(result._1()).isEqualTo(5);
      assertThat(result._2()).isEqualTo(30);
      assertThat(result._3()).isTrue();
      assertThat(result._4()).isEqualTo(5.5);
      assertThat(result._5()).isEqualTo('A');
    }

    @Test
    @DisplayName("mapFirst() with identity returns equivalent tuple")
    void mapFirstWithIdentityReturnsEquivalent() {
      Tuple5<String, Integer, Boolean, Double, Character> original =
          new Tuple5<>("Alice", 30, true, 5.5, 'A');

      Tuple5<String, Integer, Boolean, Double, Character> result =
          original.mapFirst(Function.identity());

      assertThat(result).isEqualTo(original);
    }

    @Test
    @DisplayName("mapFirst() throws NullPointerException when mapper is null")
    void mapFirstThrowsWhenMapperNull() {
      Tuple5<String, Integer, Boolean, Double, Character> tuple =
          new Tuple5<>("Alice", 30, true, 5.5, 'A');

      assertThatNullPointerException()
          .isThrownBy(() -> tuple.mapFirst(null))
          .withMessageContaining("firstMapper")
          .withMessageContaining("Tuple5.mapFirst");
    }
  }

  @Nested
  @DisplayName("mapSecond() - Transform Second Element")
  class MapSecondElement {

    @Test
    @DisplayName("mapSecond() transforms only the second element")
    void mapSecondTransformsOnlySecond() {
      Tuple5<String, Integer, Boolean, Double, Character> tuple =
          new Tuple5<>("Alice", 30, true, 5.5, 'A');

      Tuple5<String, String, Boolean, Double, Character> result =
          tuple.mapSecond(age -> age + " years");

      assertThat(result._1()).isEqualTo("Alice");
      assertThat(result._2()).isEqualTo("30 years");
      assertThat(result._3()).isTrue();
      assertThat(result._4()).isEqualTo(5.5);
      assertThat(result._5()).isEqualTo('A');
    }

    @Test
    @DisplayName("mapSecond() with identity returns equivalent tuple")
    void mapSecondWithIdentityReturnsEquivalent() {
      Tuple5<String, Integer, Boolean, Double, Character> original =
          new Tuple5<>("Alice", 30, true, 5.5, 'A');

      Tuple5<String, Integer, Boolean, Double, Character> result =
          original.mapSecond(Function.identity());

      assertThat(result).isEqualTo(original);
    }

    @Test
    @DisplayName("mapSecond() throws NullPointerException when mapper is null")
    void mapSecondThrowsWhenMapperNull() {
      Tuple5<String, Integer, Boolean, Double, Character> tuple =
          new Tuple5<>("Alice", 30, true, 5.5, 'A');

      assertThatNullPointerException()
          .isThrownBy(() -> tuple.mapSecond(null))
          .withMessageContaining("secondMapper")
          .withMessageContaining("Tuple5.mapSecond");
    }
  }

  @Nested
  @DisplayName("mapThird() - Transform Third Element")
  class MapThirdElement {

    @Test
    @DisplayName("mapThird() transforms only the third element")
    void mapThirdTransformsOnlyThird() {
      Tuple5<String, Integer, Boolean, Double, Character> tuple =
          new Tuple5<>("Alice", 30, true, 5.5, 'A');

      Tuple5<String, Integer, String, Double, Character> result =
          tuple.mapThird(active -> active ? "yes" : "no");

      assertThat(result._1()).isEqualTo("Alice");
      assertThat(result._2()).isEqualTo(30);
      assertThat(result._3()).isEqualTo("yes");
      assertThat(result._4()).isEqualTo(5.5);
      assertThat(result._5()).isEqualTo('A');
    }

    @Test
    @DisplayName("mapThird() with identity returns equivalent tuple")
    void mapThirdWithIdentityReturnsEquivalent() {
      Tuple5<String, Integer, Boolean, Double, Character> original =
          new Tuple5<>("Alice", 30, true, 5.5, 'A');

      Tuple5<String, Integer, Boolean, Double, Character> result =
          original.mapThird(Function.identity());

      assertThat(result).isEqualTo(original);
    }

    @Test
    @DisplayName("mapThird() throws NullPointerException when mapper is null")
    void mapThirdThrowsWhenMapperNull() {
      Tuple5<String, Integer, Boolean, Double, Character> tuple =
          new Tuple5<>("Alice", 30, true, 5.5, 'A');

      assertThatNullPointerException()
          .isThrownBy(() -> tuple.mapThird(null))
          .withMessageContaining("thirdMapper")
          .withMessageContaining("Tuple5.map");
    }
  }

  @Nested
  @DisplayName("mapFourth() - Transform Fourth Element")
  class MapFourthElement {

    @Test
    @DisplayName("mapFourth() transforms only the fourth element")
    void mapFourthTransformsOnlyFourth() {
      Tuple5<String, Integer, Boolean, Double, Character> tuple =
          new Tuple5<>("Alice", 30, true, 5.5, 'A');

      Tuple5<String, Integer, Boolean, Integer, Character> result =
          tuple.mapFourth(Double::intValue);

      assertThat(result._1()).isEqualTo("Alice");
      assertThat(result._2()).isEqualTo(30);
      assertThat(result._3()).isTrue();
      assertThat(result._4()).isEqualTo(5);
      assertThat(result._5()).isEqualTo('A');
    }

    @Test
    @DisplayName("mapFourth() with identity returns equivalent tuple")
    void mapFourthWithIdentityReturnsEquivalent() {
      Tuple5<String, Integer, Boolean, Double, Character> original =
          new Tuple5<>("Alice", 30, true, 5.5, 'A');

      Tuple5<String, Integer, Boolean, Double, Character> result =
          original.mapFourth(Function.identity());

      assertThat(result).isEqualTo(original);
    }

    @Test
    @DisplayName("mapFourth() throws NullPointerException when mapper is null")
    void mapFourthThrowsWhenMapperNull() {
      Tuple5<String, Integer, Boolean, Double, Character> tuple =
          new Tuple5<>("Alice", 30, true, 5.5, 'A');

      assertThatNullPointerException()
          .isThrownBy(() -> tuple.mapFourth(null))
          .withMessageContaining("fourthMapper")
          .withMessageContaining("Tuple5.map");
    }
  }

  @Nested
  @DisplayName("mapFifth() - Transform Fifth Element")
  class MapFifthElement {

    @Test
    @DisplayName("mapFifth() transforms only the fifth element")
    void mapFifthTransformsOnlyFifth() {
      Tuple5<String, Integer, Boolean, Double, Character> tuple =
          new Tuple5<>("Alice", 30, true, 5.5, 'A');

      Tuple5<String, Integer, Boolean, Double, String> result = tuple.mapFifth(Object::toString);

      assertThat(result._1()).isEqualTo("Alice");
      assertThat(result._2()).isEqualTo(30);
      assertThat(result._3()).isTrue();
      assertThat(result._4()).isEqualTo(5.5);
      assertThat(result._5()).isEqualTo("A");
    }

    @Test
    @DisplayName("mapFifth() with identity returns equivalent tuple")
    void mapFifthWithIdentityReturnsEquivalent() {
      Tuple5<String, Integer, Boolean, Double, Character> original =
          new Tuple5<>("Alice", 30, true, 5.5, 'A');

      Tuple5<String, Integer, Boolean, Double, Character> result =
          original.mapFifth(Function.identity());

      assertThat(result).isEqualTo(original);
    }

    @Test
    @DisplayName("mapFifth() throws NullPointerException when mapper is null")
    void mapFifthThrowsWhenMapperNull() {
      Tuple5<String, Integer, Boolean, Double, Character> tuple =
          new Tuple5<>("Alice", 30, true, 5.5, 'A');

      assertThatNullPointerException()
          .isThrownBy(() -> tuple.mapFifth(null))
          .withMessageContaining("fifthMapper")
          .withMessageContaining("Tuple5.map");
    }
  }

  @Nested
  @DisplayName("Record Methods - equals, hashCode, toString")
  class RecordMethods {

    @Test
    @DisplayName("equals() returns true for tuples with same values")
    void equalsReturnsTrueForSameValues() {
      Tuple5<String, Integer, Boolean, Double, Character> tuple1 =
          new Tuple5<>("Alice", 30, true, 5.5, 'A');
      Tuple5<String, Integer, Boolean, Double, Character> tuple2 =
          new Tuple5<>("Alice", 30, true, 5.5, 'A');

      assertThat(tuple1).isEqualTo(tuple2);
    }

    @Test
    @DisplayName("equals() returns false for tuples with different values")
    void equalsReturnsFalseForDifferentValues() {
      Tuple5<String, Integer, Boolean, Double, Character> tuple1 =
          new Tuple5<>("Alice", 30, true, 5.5, 'A');
      Tuple5<String, Integer, Boolean, Double, Character> tuple2 =
          new Tuple5<>("Bob", 30, true, 5.5, 'A');
      Tuple5<String, Integer, Boolean, Double, Character> tuple3 =
          new Tuple5<>("Alice", 25, true, 5.5, 'A');
      Tuple5<String, Integer, Boolean, Double, Character> tuple4 =
          new Tuple5<>("Alice", 30, false, 5.5, 'A');
      Tuple5<String, Integer, Boolean, Double, Character> tuple5 =
          new Tuple5<>("Alice", 30, true, 6.5, 'A');
      Tuple5<String, Integer, Boolean, Double, Character> tuple6 =
          new Tuple5<>("Alice", 30, true, 5.5, 'B');

      assertThat(tuple1).isNotEqualTo(tuple2);
      assertThat(tuple1).isNotEqualTo(tuple3);
      assertThat(tuple1).isNotEqualTo(tuple4);
      assertThat(tuple1).isNotEqualTo(tuple5);
      assertThat(tuple1).isNotEqualTo(tuple6);
    }

    @Test
    @DisplayName("hashCode() is consistent with equals()")
    void hashCodeConsistentWithEquals() {
      Tuple5<String, Integer, Boolean, Double, Character> tuple1 =
          new Tuple5<>("Alice", 30, true, 5.5, 'A');
      Tuple5<String, Integer, Boolean, Double, Character> tuple2 =
          new Tuple5<>("Alice", 30, true, 5.5, 'A');

      assertThat(tuple1.hashCode()).isEqualTo(tuple2.hashCode());
    }

    @Test
    @DisplayName("toString() contains all element values")
    void toStringContainsAllValues() {
      Tuple5<String, Integer, Boolean, Double, Character> tuple =
          new Tuple5<>("Alice", 30, true, 5.5, 'A');

      String result = tuple.toString();

      assertThat(result).contains("Alice", "30", "true", "5.5", "A");
    }
  }
}
