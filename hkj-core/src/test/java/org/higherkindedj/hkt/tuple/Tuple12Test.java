// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.tuple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Comprehensive test suite for {@link Tuple12}. */
@DisplayName("Tuple12 Test Suite")
class Tuple12Test {

  @Nested
  @DisplayName("Constructor and Accessors")
  class ConstructorAndAccessors {

    @Test
    @DisplayName("Constructor creates tuple with correct elements")
    void constructorCreatesCorrectTuple() {
      Tuple12<
              String,
              Integer,
              Boolean,
              Double,
              Character,
              Long,
              Short,
              Byte,
              Float,
              Integer,
              String,
              Boolean>
          tuple =
              new Tuple12<>(
                  "Alice", 30, true, 5.5, 'A', 100L, (short) 7, (byte) 1, 3.14f, 42, "Bob", false);

      assertThat(tuple._1()).isEqualTo("Alice");
      assertThat(tuple._2()).isEqualTo(30);
      assertThat(tuple._3()).isTrue();
      assertThat(tuple._4()).isEqualTo(5.5);
      assertThat(tuple._5()).isEqualTo('A');
      assertThat(tuple._6()).isEqualTo(100L);
      assertThat(tuple._7()).isEqualTo((short) 7);
      assertThat(tuple._8()).isEqualTo((byte) 1);
      assertThat(tuple._9()).isEqualTo(3.14f);
      assertThat(tuple._10()).isEqualTo(42);
      assertThat(tuple._11()).isEqualTo("Bob");
      assertThat(tuple._12()).isFalse();
    }

    @Test
    @DisplayName("Constructor accepts null elements")
    void constructorAcceptsNullElements() {
      Tuple12<
              String,
              Integer,
              Boolean,
              Double,
              Character,
              Long,
              Short,
              Byte,
              Float,
              Integer,
              String,
              Boolean>
          tuple =
              new Tuple12<>(null, null, null, null, null, null, null, null, null, null, null, null);

      assertThat(tuple._1()).isNull();
      assertThat(tuple._2()).isNull();
      assertThat(tuple._3()).isNull();
      assertThat(tuple._4()).isNull();
      assertThat(tuple._5()).isNull();
      assertThat(tuple._6()).isNull();
      assertThat(tuple._7()).isNull();
      assertThat(tuple._8()).isNull();
      assertThat(tuple._9()).isNull();
      assertThat(tuple._10()).isNull();
      assertThat(tuple._11()).isNull();
      assertThat(tuple._12()).isNull();
    }

    @Test
    @DisplayName("Tuple.of() factory creates Tuple12")
    void factoryCreatesCorrectTuple() {
      Tuple12<
              String,
              Integer,
              Boolean,
              Double,
              Character,
              Long,
              Short,
              Byte,
              Float,
              Integer,
              String,
              Boolean>
          tuple =
              Tuple.of(
                  "Alice", 30, true, 5.5, 'A', 100L, (short) 7, (byte) 1, 3.14f, 42, "Bob", false);

      assertThat(tuple._1()).isEqualTo("Alice");
      assertThat(tuple._12()).isFalse();
    }
  }

  @Nested
  @DisplayName("map() - Transform All Elements")
  class MapAllElements {

    @Test
    @DisplayName("map() transforms all twelve elements")
    void mapTransformsAllElements() {
      Tuple12<
              String,
              Integer,
              Boolean,
              Double,
              Character,
              Long,
              Short,
              Byte,
              Float,
              Integer,
              String,
              Boolean>
          tuple =
              new Tuple12<>(
                  "Alice", 30, true, 5.5, 'A', 100L, (short) 7, (byte) 1, 3.14f, 42, "Bob", false);

      Tuple12<
              Integer,
              String,
              String,
              Integer,
              String,
              String,
              Integer,
              Integer,
              Integer,
              String,
              Integer,
              String>
          result =
              tuple.map(
                  String::length,
                  age -> age + " years",
                  active -> active ? "yes" : "no",
                  Double::intValue,
                  Object::toString,
                  l -> l + "L",
                  Short::intValue,
                  Byte::intValue,
                  Float::intValue,
                  Object::toString,
                  String::length,
                  b -> b ? "true" : "false");

      assertThat(result._1()).isEqualTo(5);
      assertThat(result._2()).isEqualTo("30 years");
      assertThat(result._3()).isEqualTo("yes");
      assertThat(result._4()).isEqualTo(5);
      assertThat(result._5()).isEqualTo("A");
      assertThat(result._6()).isEqualTo("100L");
      assertThat(result._7()).isEqualTo(7);
      assertThat(result._8()).isEqualTo(1);
      assertThat(result._9()).isEqualTo(3);
      assertThat(result._10()).isEqualTo("42");
      assertThat(result._11()).isEqualTo(3);
      assertThat(result._12()).isEqualTo("false");
    }

    @Test
    @DisplayName("map() with identity functions returns equivalent tuple")
    void mapWithIdentityReturnsEquivalent() {
      Tuple12<
              String,
              Integer,
              Boolean,
              Double,
              Character,
              Long,
              Short,
              Byte,
              Float,
              Integer,
              String,
              Boolean>
          original =
              new Tuple12<>(
                  "Alice", 30, true, 5.5, 'A', 100L, (short) 7, (byte) 1, 3.14f, 42, "Bob", false);

      Tuple12<
              String,
              Integer,
              Boolean,
              Double,
              Character,
              Long,
              Short,
              Byte,
              Float,
              Integer,
              String,
              Boolean>
          result =
              original.map(
                  Function.identity(),
                  Function.identity(),
                  Function.identity(),
                  Function.identity(),
                  Function.identity(),
                  Function.identity(),
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
      Tuple12<
              String,
              Integer,
              Boolean,
              Double,
              Character,
              Long,
              Short,
              Byte,
              Float,
              Integer,
              String,
              Boolean>
          tuple =
              new Tuple12<>(
                  "Alice", 30, true, 5.5, 'A', 100L, (short) 7, (byte) 1, 3.14f, 42, "Bob", false);

      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  tuple.map(
                      null,
                      Function.identity(),
                      Function.identity(),
                      Function.identity(),
                      Function.identity(),
                      Function.identity(),
                      Function.identity(),
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
                      Function.identity(),
                      Function.identity(),
                      Function.identity(),
                      Function.identity(),
                      Function.identity(),
                      Function.identity(),
                      Function.identity(),
                      Function.identity(),
                      Function.identity(),
                      Function.identity(),
                      null))
          .withMessageContaining("twelfthMapper");
    }
  }

  @Nested
  @DisplayName("Individual mapN() Methods")
  class IndividualMapMethods {

    private final Tuple12<
            String,
            Integer,
            Boolean,
            Double,
            Character,
            Long,
            Short,
            Byte,
            Float,
            Integer,
            String,
            Boolean>
        tuple =
            new Tuple12<>(
                "Alice", 30, true, 5.5, 'A', 100L, (short) 7, (byte) 1, 3.14f, 42, "Bob", false);

    @Test
    @DisplayName("mapFirst() transforms only the first element")
    void mapFirstTransformsOnlyFirst() {
      var result = tuple.mapFirst(String::length);

      assertThat(result._1()).isEqualTo(5);
      assertThat(result._2()).isEqualTo(30);
      assertThat(result._12()).isFalse();
    }

    @Test
    @DisplayName("mapTwelfth() transforms only the twelfth element")
    void mapTwelfthTransformsOnlyTwelfth() {
      var result = tuple.mapTwelfth(b -> !b);

      assertThat(result._1()).isEqualTo("Alice");
      assertThat(result._12()).isTrue();
    }

    @Test
    @DisplayName("mapNinth() transforms only the ninth element")
    void mapNinthTransformsOnlyNinth() {
      var result = tuple.mapNinth(f -> f * 2);

      assertThat(result._9()).isEqualTo(6.28f);
      assertThat(result._1()).isEqualTo("Alice");
      assertThat(result._12()).isFalse();
    }

    @Test
    @DisplayName("mapTenth() transforms only the tenth element")
    void mapTenthTransformsOnlyTenth() {
      var result = tuple.mapTenth(n -> n + 8);

      assertThat(result._10()).isEqualTo(50);
      assertThat(result._1()).isEqualTo("Alice");
      assertThat(result._12()).isFalse();
    }

    @Test
    @DisplayName("mapEleventh() transforms only the eleventh element")
    void mapEleventhTransformsOnlyEleventh() {
      var result = tuple.mapEleventh(String::toUpperCase);

      assertThat(result._11()).isEqualTo("BOB");
      assertThat(result._1()).isEqualTo("Alice");
      assertThat(result._12()).isFalse();
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
      assertThat(tuple.mapSeventh(Function.identity())).isEqualTo(tuple);
      assertThat(tuple.mapEighth(Function.identity())).isEqualTo(tuple);
      assertThat(tuple.mapNinth(Function.identity())).isEqualTo(tuple);
      assertThat(tuple.mapTenth(Function.identity())).isEqualTo(tuple);
      assertThat(tuple.mapEleventh(Function.identity())).isEqualTo(tuple);
      assertThat(tuple.mapTwelfth(Function.identity())).isEqualTo(tuple);
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
      assertThatNullPointerException().isThrownBy(() -> tuple.mapSeventh(null));
      assertThatNullPointerException().isThrownBy(() -> tuple.mapEighth(null));
      assertThatNullPointerException().isThrownBy(() -> tuple.mapNinth(null));
      assertThatNullPointerException().isThrownBy(() -> tuple.mapTenth(null));
      assertThatNullPointerException().isThrownBy(() -> tuple.mapEleventh(null));
      assertThatNullPointerException().isThrownBy(() -> tuple.mapTwelfth(null));
    }
  }

  @Nested
  @DisplayName("Record Methods - equals, hashCode, toString")
  class RecordMethods {

    @Test
    @DisplayName("equals() returns true for tuples with same values")
    void equalsReturnsTrueForSameValues() {
      var tuple1 =
          new Tuple12<>(
              "Alice", 30, true, 5.5, 'A', 100L, (short) 7, (byte) 1, 3.14f, 42, "Bob", false);
      var tuple2 =
          new Tuple12<>(
              "Alice", 30, true, 5.5, 'A', 100L, (short) 7, (byte) 1, 3.14f, 42, "Bob", false);

      assertThat(tuple1).isEqualTo(tuple2);
    }

    @Test
    @DisplayName("equals() returns false for tuples with different values")
    void equalsReturnsFalseForDifferentValues() {
      var tuple1 =
          new Tuple12<>(
              "Alice", 30, true, 5.5, 'A', 100L, (short) 7, (byte) 1, 3.14f, 42, "Bob", false);

      assertThat(tuple1)
          .isNotEqualTo(
              new Tuple12<>(
                  "Bob", 30, true, 5.5, 'A', 100L, (short) 7, (byte) 1, 3.14f, 42, "Bob", false));
      assertThat(tuple1)
          .isNotEqualTo(
              new Tuple12<>(
                  "Alice", 30, true, 5.5, 'A', 100L, (short) 7, (byte) 1, 3.14f, 42, "Bob", true));
    }

    @Test
    @DisplayName("hashCode() is consistent with equals()")
    void hashCodeConsistentWithEquals() {
      var tuple1 =
          new Tuple12<>(
              "Alice", 30, true, 5.5, 'A', 100L, (short) 7, (byte) 1, 3.14f, 42, "Bob", false);
      var tuple2 =
          new Tuple12<>(
              "Alice", 30, true, 5.5, 'A', 100L, (short) 7, (byte) 1, 3.14f, 42, "Bob", false);

      assertThat(tuple1.hashCode()).isEqualTo(tuple2.hashCode());
    }

    @Test
    @DisplayName("toString() contains all element values")
    void toStringContainsAllValues() {
      var tuple =
          new Tuple12<>(
              "Alice", 30, true, 5.5, 'A', 100L, (short) 7, (byte) 1, 3.14f, 42, "Bob", false);

      assertThat(tuple.toString())
          .contains(
              "Alice", "30", "true", "5.5", "A", "100", "7", "1", "3.14", "42", "Bob", "false");
    }
  }

  @Nested
  @DisplayName("Immutability")
  class Immutability {

    @Test
    @DisplayName("Mapping operations do not modify original tuple")
    void mappingDoesNotModifyOriginal() {
      Tuple12<
              String,
              Integer,
              Boolean,
              Double,
              Character,
              Long,
              Short,
              Byte,
              Float,
              Integer,
              String,
              Boolean>
          original =
              new Tuple12<>(
                  "Alice", 30, true, 5.5, 'A', 100L, (short) 7, (byte) 1, 3.14f, 42, "Bob", false);

      original.map(
          String::toUpperCase,
          n -> n * 2,
          b -> !b,
          d -> d * 2,
          Character::toLowerCase,
          l -> l * 2,
          s -> s * 2,
          b -> b * 2,
          f -> f * 2,
          n -> n * 2,
          String::toUpperCase,
          b -> !b);
      original.mapFirst(String::toUpperCase);
      original.mapTwelfth(b -> !b);

      assertThat(original._1()).isEqualTo("Alice");
      assertThat(original._2()).isEqualTo(30);
      assertThat(original._3()).isTrue();
      assertThat(original._4()).isEqualTo(5.5);
      assertThat(original._5()).isEqualTo('A');
      assertThat(original._6()).isEqualTo(100L);
      assertThat(original._7()).isEqualTo((short) 7);
      assertThat(original._8()).isEqualTo((byte) 1);
      assertThat(original._9()).isEqualTo(3.14f);
      assertThat(original._10()).isEqualTo(42);
      assertThat(original._11()).isEqualTo("Bob");
      assertThat(original._12()).isFalse();
    }
  }
}
