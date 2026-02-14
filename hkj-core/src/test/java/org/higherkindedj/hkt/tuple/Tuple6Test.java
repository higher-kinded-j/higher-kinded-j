// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.tuple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Comprehensive test suite for {@link Tuple6}. */
@DisplayName("Tuple6 Test Suite")
class Tuple6Test {

  @Nested
  @DisplayName("Constructor and Accessors")
  class ConstructorAndAccessors {

    @Test
    @DisplayName("Constructor creates tuple with correct elements")
    void constructorCreatesCorrectTuple() {
      Tuple6<String, Integer, Boolean, Double, Character, Long> tuple =
          new Tuple6<>("Alice", 30, true, 5.5, 'A', 100L);

      assertThat(tuple._1()).isEqualTo("Alice");
      assertThat(tuple._2()).isEqualTo(30);
      assertThat(tuple._3()).isTrue();
      assertThat(tuple._4()).isEqualTo(5.5);
      assertThat(tuple._5()).isEqualTo('A');
      assertThat(tuple._6()).isEqualTo(100L);
    }

    @Test
    @DisplayName("Constructor accepts null elements")
    void constructorAcceptsNullElements() {
      Tuple6<String, Integer, Boolean, Double, Character, Long> tuple =
          new Tuple6<>(null, null, null, null, null, null);

      assertThat(tuple._1()).isNull();
      assertThat(tuple._2()).isNull();
      assertThat(tuple._3()).isNull();
      assertThat(tuple._4()).isNull();
      assertThat(tuple._5()).isNull();
      assertThat(tuple._6()).isNull();
    }

    @Test
    @DisplayName("Tuple.of() factory creates Tuple6")
    void factoryCreatesCorrectTuple() {
      Tuple6<String, Integer, Boolean, Double, Character, Long> tuple =
          Tuple.of("Alice", 30, true, 5.5, 'A', 100L);

      assertThat(tuple._1()).isEqualTo("Alice");
      assertThat(tuple._2()).isEqualTo(30);
      assertThat(tuple._3()).isTrue();
      assertThat(tuple._4()).isEqualTo(5.5);
      assertThat(tuple._5()).isEqualTo('A');
      assertThat(tuple._6()).isEqualTo(100L);
    }
  }

  @Nested
  @DisplayName("map() - Transform All Elements")
  class MapAllElements {

    @Test
    @DisplayName("map() transforms all six elements")
    void mapTransformsAllElements() {
      Tuple6<String, Integer, Boolean, Double, Character, Long> tuple =
          new Tuple6<>("Alice", 30, true, 5.5, 'A', 100L);

      Tuple6<Integer, String, String, Integer, String, String> result =
          tuple.map(
              String::length,
              age -> age + " years",
              active -> active ? "yes" : "no",
              Double::intValue,
              Object::toString,
              l -> l + "L");

      assertThat(result._1()).isEqualTo(5);
      assertThat(result._2()).isEqualTo("30 years");
      assertThat(result._3()).isEqualTo("yes");
      assertThat(result._4()).isEqualTo(5);
      assertThat(result._5()).isEqualTo("A");
      assertThat(result._6()).isEqualTo("100L");
    }

    @Test
    @DisplayName("map() with identity functions returns equivalent tuple")
    void mapWithIdentityReturnsEquivalent() {
      Tuple6<String, Integer, Boolean, Double, Character, Long> original =
          new Tuple6<>("Alice", 30, true, 5.5, 'A', 100L);

      Tuple6<String, Integer, Boolean, Double, Character, Long> result =
          original.map(
              Function.identity(),
              Function.identity(),
              Function.identity(),
              Function.identity(),
              Function.identity(),
              Function.identity());

      assertThat(result).isEqualTo(original);
    }

    @Test
    @DisplayName("map() throws NullPointerException when any mapper is null")
    void mapThrowsWhenMapperNull() {
      Tuple6<String, Integer, Boolean, Double, Character, Long> tuple =
          new Tuple6<>("Alice", 30, true, 5.5, 'A', 100L);

      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  tuple.map(
                      null,
                      Function.identity(),
                      Function.identity(),
                      Function.identity(),
                      Function.identity(),
                      Function.identity()))
          .withMessageContaining("firstMapper");

      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  tuple.map(
                      Function.identity(),
                      null,
                      Function.identity(),
                      Function.identity(),
                      Function.identity(),
                      Function.identity()))
          .withMessageContaining("secondMapper");

      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  tuple.map(
                      Function.identity(),
                      Function.identity(),
                      null,
                      Function.identity(),
                      Function.identity(),
                      Function.identity()))
          .withMessageContaining("thirdMapper");

      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  tuple.map(
                      Function.identity(),
                      Function.identity(),
                      Function.identity(),
                      null,
                      Function.identity(),
                      Function.identity()))
          .withMessageContaining("fourthMapper");

      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  tuple.map(
                      Function.identity(),
                      Function.identity(),
                      Function.identity(),
                      Function.identity(),
                      null,
                      Function.identity()))
          .withMessageContaining("fifthMapper");

      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  tuple.map(
                      Function.identity(),
                      Function.identity(),
                      Function.identity(),
                      Function.identity(),
                      Function.identity(),
                      null))
          .withMessageContaining("sixthMapper");
    }
  }

  @Nested
  @DisplayName("Individual mapN() Methods")
  class IndividualMapMethods {

    private final Tuple6<String, Integer, Boolean, Double, Character, Long> tuple =
        new Tuple6<>("Alice", 30, true, 5.5, 'A', 100L);

    @Test
    @DisplayName("mapFirst() transforms only the first element")
    void mapFirstTransformsOnlyFirst() {
      Tuple6<Integer, Integer, Boolean, Double, Character, Long> result =
          tuple.mapFirst(String::length);

      assertThat(result._1()).isEqualTo(5);
      assertThat(result._2()).isEqualTo(30);
      assertThat(result._3()).isTrue();
      assertThat(result._4()).isEqualTo(5.5);
      assertThat(result._5()).isEqualTo('A');
      assertThat(result._6()).isEqualTo(100L);
    }

    @Test
    @DisplayName("mapSecond() transforms only the second element")
    void mapSecondTransformsOnlySecond() {
      Tuple6<String, String, Boolean, Double, Character, Long> result =
          tuple.mapSecond(age -> age + " years");

      assertThat(result._1()).isEqualTo("Alice");
      assertThat(result._2()).isEqualTo("30 years");
      assertThat(result._3()).isTrue();
      assertThat(result._4()).isEqualTo(5.5);
      assertThat(result._5()).isEqualTo('A');
      assertThat(result._6()).isEqualTo(100L);
    }

    @Test
    @DisplayName("mapThird() transforms only the third element")
    void mapThirdTransformsOnlyThird() {
      Tuple6<String, Integer, String, Double, Character, Long> result =
          tuple.mapThird(b -> b ? "yes" : "no");

      assertThat(result._1()).isEqualTo("Alice");
      assertThat(result._2()).isEqualTo(30);
      assertThat(result._3()).isEqualTo("yes");
      assertThat(result._4()).isEqualTo(5.5);
      assertThat(result._5()).isEqualTo('A');
      assertThat(result._6()).isEqualTo(100L);
    }

    @Test
    @DisplayName("mapFourth() transforms only the fourth element")
    void mapFourthTransformsOnlyFourth() {
      Tuple6<String, Integer, Boolean, Integer, Character, Long> result =
          tuple.mapFourth(Double::intValue);

      assertThat(result._1()).isEqualTo("Alice");
      assertThat(result._2()).isEqualTo(30);
      assertThat(result._3()).isTrue();
      assertThat(result._4()).isEqualTo(5);
      assertThat(result._5()).isEqualTo('A');
      assertThat(result._6()).isEqualTo(100L);
    }

    @Test
    @DisplayName("mapFifth() transforms only the fifth element")
    void mapFifthTransformsOnlyFifth() {
      Tuple6<String, Integer, Boolean, Double, String, Long> result =
          tuple.mapFifth(Object::toString);

      assertThat(result._1()).isEqualTo("Alice");
      assertThat(result._2()).isEqualTo(30);
      assertThat(result._3()).isTrue();
      assertThat(result._4()).isEqualTo(5.5);
      assertThat(result._5()).isEqualTo("A");
      assertThat(result._6()).isEqualTo(100L);
    }

    @Test
    @DisplayName("mapSixth() transforms only the sixth element")
    void mapSixthTransformsOnlySixth() {
      Tuple6<String, Integer, Boolean, Double, Character, String> result =
          tuple.mapSixth(l -> l + "L");

      assertThat(result._1()).isEqualTo("Alice");
      assertThat(result._2()).isEqualTo(30);
      assertThat(result._3()).isTrue();
      assertThat(result._4()).isEqualTo(5.5);
      assertThat(result._5()).isEqualTo('A');
      assertThat(result._6()).isEqualTo("100L");
    }

    @Test
    @DisplayName("Individual map methods with identity return equivalent tuples")
    void mapWithIdentityReturnsEquivalent() {
      assertThat(tuple.mapFirst(Function.identity())).isEqualTo(tuple);
      assertThat(tuple.mapSecond(Function.identity())).isEqualTo(tuple);
      assertThat(tuple.mapThird(Function.identity())).isEqualTo(tuple);
      assertThat(tuple.mapFourth(Function.identity())).isEqualTo(tuple);
      assertThat(tuple.mapFifth(Function.identity())).isEqualTo(tuple);
      assertThat(tuple.mapSixth(Function.identity())).isEqualTo(tuple);
    }

    @Test
    @DisplayName("Individual map methods throw NullPointerException when mapper is null")
    void mapThrowsWhenMapperNull() {
      assertThatNullPointerException().isThrownBy(() -> tuple.mapFirst(null));
      assertThatNullPointerException().isThrownBy(() -> tuple.mapSecond(null));
      assertThatNullPointerException().isThrownBy(() -> tuple.mapThird(null));
      assertThatNullPointerException().isThrownBy(() -> tuple.mapFourth(null));
      assertThatNullPointerException().isThrownBy(() -> tuple.mapFifth(null));
      assertThatNullPointerException().isThrownBy(() -> tuple.mapSixth(null));
    }
  }

  @Nested
  @DisplayName("Record Methods - equals, hashCode, toString")
  class RecordMethods {

    @Test
    @DisplayName("equals() returns true for tuples with same values")
    void equalsReturnsTrueForSameValues() {
      Tuple6<String, Integer, Boolean, Double, Character, Long> tuple1 =
          new Tuple6<>("Alice", 30, true, 5.5, 'A', 100L);
      Tuple6<String, Integer, Boolean, Double, Character, Long> tuple2 =
          new Tuple6<>("Alice", 30, true, 5.5, 'A', 100L);

      assertThat(tuple1).isEqualTo(tuple2);
    }

    @Test
    @DisplayName("equals() returns false for tuples with different values")
    void equalsReturnsFalseForDifferentValues() {
      Tuple6<String, Integer, Boolean, Double, Character, Long> tuple1 =
          new Tuple6<>("Alice", 30, true, 5.5, 'A', 100L);

      assertThat(tuple1).isNotEqualTo(new Tuple6<>("Bob", 30, true, 5.5, 'A', 100L));
      assertThat(tuple1).isNotEqualTo(new Tuple6<>("Alice", 25, true, 5.5, 'A', 100L));
      assertThat(tuple1).isNotEqualTo(new Tuple6<>("Alice", 30, false, 5.5, 'A', 100L));
      assertThat(tuple1).isNotEqualTo(new Tuple6<>("Alice", 30, true, 6.5, 'A', 100L));
      assertThat(tuple1).isNotEqualTo(new Tuple6<>("Alice", 30, true, 5.5, 'B', 100L));
      assertThat(tuple1).isNotEqualTo(new Tuple6<>("Alice", 30, true, 5.5, 'A', 200L));
    }

    @Test
    @DisplayName("hashCode() is consistent with equals()")
    void hashCodeConsistentWithEquals() {
      Tuple6<String, Integer, Boolean, Double, Character, Long> tuple1 =
          new Tuple6<>("Alice", 30, true, 5.5, 'A', 100L);
      Tuple6<String, Integer, Boolean, Double, Character, Long> tuple2 =
          new Tuple6<>("Alice", 30, true, 5.5, 'A', 100L);

      assertThat(tuple1.hashCode()).isEqualTo(tuple2.hashCode());
    }

    @Test
    @DisplayName("toString() contains all element values")
    void toStringContainsAllValues() {
      Tuple6<String, Integer, Boolean, Double, Character, Long> tuple =
          new Tuple6<>("Alice", 30, true, 5.5, 'A', 100L);

      String result = tuple.toString();

      assertThat(result).contains("Alice", "30", "true", "5.5", "A", "100");
    }
  }

  @Nested
  @DisplayName("Immutability")
  class Immutability {

    @Test
    @DisplayName("Mapping operations do not modify original tuple")
    void mappingDoesNotModifyOriginal() {
      Tuple6<String, Integer, Boolean, Double, Character, Long> original =
          new Tuple6<>("Alice", 30, true, 5.5, 'A', 100L);

      original.map(
          String::toUpperCase, n -> n * 2, b -> !b, d -> d * 2, Character::toLowerCase, l -> l * 2);
      original.mapFirst(String::toUpperCase);
      original.mapSecond(n -> n * 2);
      original.mapThird(b -> !b);
      original.mapFourth(d -> d * 2);
      original.mapFifth(Character::toLowerCase);
      original.mapSixth(l -> l * 2);

      assertThat(original._1()).isEqualTo("Alice");
      assertThat(original._2()).isEqualTo(30);
      assertThat(original._3()).isTrue();
      assertThat(original._4()).isEqualTo(5.5);
      assertThat(original._5()).isEqualTo('A');
      assertThat(original._6()).isEqualTo(100L);
    }
  }
}
