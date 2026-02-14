// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.typeclass;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Monoids utility class")
class MonoidsTest {

  @Nested
  @DisplayName("List monoid")
  class ListMonoidTests {

    @Test
    @DisplayName("should have empty list as empty value")
    void shouldHaveCorrectEmpty() {
      Monoid<List<String>> listMonoid = Monoids.list();

      assertThat(listMonoid.empty()).isEmpty();
    }

    @Test
    @DisplayName("should combine two lists by concatenation")
    void shouldCombineByConcatenation() {
      Monoid<List<String>> listMonoid = Monoids.list();

      List<String> result = listMonoid.combine(List.of("a", "b"), List.of("c", "d"));

      assertThat(result).containsExactly("a", "b", "c", "d");
    }

    @Test
    @DisplayName("should handle empty lists")
    void shouldHandleEmptyLists() {
      Monoid<List<String>> listMonoid = Monoids.list();

      List<String> result = listMonoid.combine(List.of(), List.of("a"));

      assertThat(result).containsExactly("a");
    }

    @Test
    @DisplayName("should satisfy identity laws")
    void shouldSatisfyIdentityLaws() {
      Monoid<List<Integer>> listMonoid = Monoids.list();
      List<Integer> value = List.of(1, 2, 3);

      assertThat(listMonoid.combine(listMonoid.empty(), value)).isEqualTo(value);
      assertThat(listMonoid.combine(value, listMonoid.empty())).isEqualTo(value);
    }
  }

  @Nested
  @DisplayName("Set monoid")
  class SetMonoidTests {

    @Test
    @DisplayName("should have empty set as empty value")
    void shouldHaveCorrectEmpty() {
      Monoid<Set<String>> setMonoid = Monoids.set();

      assertThat(setMonoid.empty()).isEmpty();
    }

    @Test
    @DisplayName("should combine two sets by union")
    void shouldCombineByUnion() {
      Monoid<Set<String>> setMonoid = Monoids.set();

      Set<String> result = setMonoid.combine(Set.of("a", "b"), Set.of("c", "d"));

      assertThat(result).containsExactlyInAnyOrder("a", "b", "c", "d");
    }

    @Test
    @DisplayName("should handle overlapping sets")
    void shouldHandleOverlappingSets() {
      Monoid<Set<String>> setMonoid = Monoids.set();

      Set<String> result = setMonoid.combine(Set.of("a", "b"), Set.of("b", "c"));

      assertThat(result).containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    @DisplayName("should satisfy identity laws")
    void shouldSatisfyIdentityLaws() {
      Monoid<Set<Integer>> setMonoid = Monoids.set();
      Set<Integer> value = new HashSet<>(Set.of(1, 2, 3));

      assertThat(setMonoid.combine(setMonoid.empty(), value)).isEqualTo(value);
      assertThat(setMonoid.combine(value, setMonoid.empty())).isEqualTo(value);
    }
  }

  @Nested
  @DisplayName("String monoid")
  class StringMonoidTests {

    @Test
    @DisplayName("should have empty string as empty value")
    void shouldHaveCorrectEmpty() {
      Monoid<String> stringMonoid = Monoids.string();

      assertThat(stringMonoid.empty()).isEmpty();
    }

    @Test
    @DisplayName("should combine two strings by concatenation")
    void shouldCombineByConcatenation() {
      Monoid<String> stringMonoid = Monoids.string();

      String result = stringMonoid.combine("Hello", " World");

      assertThat(result).isEqualTo("Hello World");
    }

    @Test
    @DisplayName("should satisfy identity laws")
    void shouldSatisfyIdentityLaws() {
      Monoid<String> stringMonoid = Monoids.string();
      String value = "test";

      assertThat(stringMonoid.combine(stringMonoid.empty(), value)).isEqualTo(value);
      assertThat(stringMonoid.combine(value, stringMonoid.empty())).isEqualTo(value);
    }
  }

  @Nested
  @DisplayName("Integer monoids")
  class IntegerMonoidTests {

    @Nested
    @DisplayName("integerAddition")
    class IntegerAdditionTests {

      @Test
      @DisplayName("should have 0 as empty value")
      void shouldHaveCorrectEmpty() {
        Monoid<Integer> intAddition = Monoids.integerAddition();

        assertThat(intAddition.empty()).isEqualTo(0);
      }

      @Test
      @DisplayName("should combine two integers by addition")
      void shouldCombineByAddition() {
        Monoid<Integer> intAddition = Monoids.integerAddition();

        Integer result = intAddition.combine(10, 20);

        assertThat(result).isEqualTo(30);
      }

      @Test
      @DisplayName("should satisfy identity laws")
      void shouldSatisfyIdentityLaws() {
        Monoid<Integer> intAddition = Monoids.integerAddition();
        Integer value = 42;

        assertThat(intAddition.combine(intAddition.empty(), value)).isEqualTo(value);
        assertThat(intAddition.combine(value, intAddition.empty())).isEqualTo(value);
      }
    }

    @Nested
    @DisplayName("integerMultiplication")
    class IntegerMultiplicationTests {

      @Test
      @DisplayName("should have 1 as empty value")
      void shouldHaveCorrectEmpty() {
        Monoid<Integer> intMultiplication = Monoids.integerMultiplication();

        assertThat(intMultiplication.empty()).isEqualTo(1);
      }

      @Test
      @DisplayName("should combine two integers by multiplication")
      void shouldCombineByMultiplication() {
        Monoid<Integer> intMultiplication = Monoids.integerMultiplication();

        Integer result = intMultiplication.combine(10, 20);

        assertThat(result).isEqualTo(200);
      }

      @Test
      @DisplayName("should satisfy identity laws")
      void shouldSatisfyIdentityLaws() {
        Monoid<Integer> intMultiplication = Monoids.integerMultiplication();
        Integer value = 42;

        assertThat(intMultiplication.combine(intMultiplication.empty(), value)).isEqualTo(value);
        assertThat(intMultiplication.combine(value, intMultiplication.empty())).isEqualTo(value);
      }
    }
  }

  @Nested
  @DisplayName("Boolean monoids")
  class BooleanMonoidTests {

    @Nested
    @DisplayName("booleanAnd")
    class BooleanAndTests {

      @Test
      @DisplayName("should have true as empty value")
      void shouldHaveCorrectEmpty() {
        Monoid<Boolean> boolAnd = Monoids.booleanAnd();

        assertThat(boolAnd.empty()).isTrue();
      }

      @Test
      @DisplayName("should combine by logical AND")
      void shouldCombineByAnd() {
        Monoid<Boolean> boolAnd = Monoids.booleanAnd();

        assertThat(boolAnd.combine(true, true)).isTrue();
        assertThat(boolAnd.combine(true, false)).isFalse();
        assertThat(boolAnd.combine(false, true)).isFalse();
        assertThat(boolAnd.combine(false, false)).isFalse();
      }

      @Test
      @DisplayName("should satisfy identity laws")
      void shouldSatisfyIdentityLaws() {
        Monoid<Boolean> boolAnd = Monoids.booleanAnd();

        assertThat(boolAnd.combine(boolAnd.empty(), true)).isTrue();
        assertThat(boolAnd.combine(true, boolAnd.empty())).isTrue();
        assertThat(boolAnd.combine(boolAnd.empty(), false)).isFalse();
        assertThat(boolAnd.combine(false, boolAnd.empty())).isFalse();
      }
    }

    @Nested
    @DisplayName("booleanOr")
    class BooleanOrTests {

      @Test
      @DisplayName("should have false as empty value")
      void shouldHaveCorrectEmpty() {
        Monoid<Boolean> boolOr = Monoids.booleanOr();

        assertThat(boolOr.empty()).isFalse();
      }

      @Test
      @DisplayName("should combine by logical OR")
      void shouldCombineByOr() {
        Monoid<Boolean> boolOr = Monoids.booleanOr();

        assertThat(boolOr.combine(true, true)).isTrue();
        assertThat(boolOr.combine(true, false)).isTrue();
        assertThat(boolOr.combine(false, true)).isTrue();
        assertThat(boolOr.combine(false, false)).isFalse();
      }

      @Test
      @DisplayName("should satisfy identity laws")
      void shouldSatisfyIdentityLaws() {
        Monoid<Boolean> boolOr = Monoids.booleanOr();

        assertThat(boolOr.combine(boolOr.empty(), true)).isTrue();
        assertThat(boolOr.combine(true, boolOr.empty())).isTrue();
        assertThat(boolOr.combine(boolOr.empty(), false)).isFalse();
        assertThat(boolOr.combine(false, boolOr.empty())).isFalse();
      }
    }
  }

  @Nested
  @DisplayName("Long monoids")
  class LongMonoidTests {

    @Nested
    @DisplayName("longAddition")
    class LongAdditionTests {

      @Test
      @DisplayName("should have 0L as empty value")
      void shouldHaveCorrectEmpty() {
        Monoid<Long> longAddition = Monoids.longAddition();

        assertThat(longAddition.empty()).isEqualTo(0L);
      }

      @Test
      @DisplayName("should combine two longs by addition")
      void shouldCombineByAddition() {
        Monoid<Long> longAddition = Monoids.longAddition();

        Long result = longAddition.combine(10L, 20L);

        assertThat(result).isEqualTo(30L);
      }

      @Test
      @DisplayName("should handle negative numbers")
      void shouldHandleNegativeNumbers() {
        Monoid<Long> longAddition = Monoids.longAddition();

        Long result = longAddition.combine(-5L, 10L);

        assertThat(result).isEqualTo(5L);
      }

      @Test
      @DisplayName("should handle large numbers")
      void shouldHandleLargeNumbers() {
        Monoid<Long> longAddition = Monoids.longAddition();

        Long result = longAddition.combine(Long.MAX_VALUE - 10, 5L);

        assertThat(result).isEqualTo(Long.MAX_VALUE - 5);
      }

      @Test
      @DisplayName("should satisfy left identity law")
      void shouldSatisfyLeftIdentity() {
        Monoid<Long> longAddition = Monoids.longAddition();
        Long value = 42L;

        Long result = longAddition.combine(longAddition.empty(), value);

        assertThat(result).isEqualTo(value);
      }

      @Test
      @DisplayName("should satisfy right identity law")
      void shouldSatisfyRightIdentity() {
        Monoid<Long> longAddition = Monoids.longAddition();
        Long value = 42L;

        Long result = longAddition.combine(value, longAddition.empty());

        assertThat(result).isEqualTo(value);
      }

      @Test
      @DisplayName("should satisfy associativity law")
      void shouldSatisfyAssociativity() {
        Monoid<Long> longAddition = Monoids.longAddition();
        Long a = 100L;
        Long b = 200L;
        Long c = 300L;

        Long leftAssoc = longAddition.combine(longAddition.combine(a, b), c);
        Long rightAssoc = longAddition.combine(a, longAddition.combine(b, c));

        assertThat(leftAssoc).isEqualTo(rightAssoc).isEqualTo(600L);
      }
    }

    @Nested
    @DisplayName("longMultiplication")
    class LongMultiplicationTests {

      @Test
      @DisplayName("should have 1L as empty value")
      void shouldHaveCorrectEmpty() {
        Monoid<Long> longMultiplication = Monoids.longMultiplication();

        assertThat(longMultiplication.empty()).isEqualTo(1L);
      }

      @Test
      @DisplayName("should combine two longs by multiplication")
      void shouldCombineByMultiplication() {
        Monoid<Long> longMultiplication = Monoids.longMultiplication();

        Long result = longMultiplication.combine(10L, 20L);

        assertThat(result).isEqualTo(200L);
      }

      @Test
      @DisplayName("should handle negative numbers")
      void shouldHandleNegativeNumbers() {
        Monoid<Long> longMultiplication = Monoids.longMultiplication();

        Long result = longMultiplication.combine(-5L, 10L);

        assertThat(result).isEqualTo(-50L);
      }

      @Test
      @DisplayName("should handle zero")
      void shouldHandleZero() {
        Monoid<Long> longMultiplication = Monoids.longMultiplication();

        Long result = longMultiplication.combine(0L, 10L);

        assertThat(result).isEqualTo(0L);
      }

      @Test
      @DisplayName("should satisfy left identity law")
      void shouldSatisfyLeftIdentity() {
        Monoid<Long> longMultiplication = Monoids.longMultiplication();
        Long value = 42L;

        Long result = longMultiplication.combine(longMultiplication.empty(), value);

        assertThat(result).isEqualTo(value);
      }

      @Test
      @DisplayName("should satisfy right identity law")
      void shouldSatisfyRightIdentity() {
        Monoid<Long> longMultiplication = Monoids.longMultiplication();
        Long value = 42L;

        Long result = longMultiplication.combine(value, longMultiplication.empty());

        assertThat(result).isEqualTo(value);
      }

      @Test
      @DisplayName("should satisfy associativity law")
      void shouldSatisfyAssociativity() {
        Monoid<Long> longMultiplication = Monoids.longMultiplication();
        Long a = 2L;
        Long b = 3L;
        Long c = 4L;

        Long leftAssoc = longMultiplication.combine(longMultiplication.combine(a, b), c);
        Long rightAssoc = longMultiplication.combine(a, longMultiplication.combine(b, c));

        assertThat(leftAssoc).isEqualTo(rightAssoc).isEqualTo(24L);
      }
    }
  }

  @Nested
  @DisplayName("Double monoids")
  class DoubleMonoidTests {

    @Nested
    @DisplayName("doubleAddition")
    class DoubleAdditionTests {

      @Test
      @DisplayName("should have 0.0 as empty value")
      void shouldHaveCorrectEmpty() {
        Monoid<Double> doubleAddition = Monoids.doubleAddition();

        assertThat(doubleAddition.empty()).isEqualTo(0.0);
      }

      @Test
      @DisplayName("should combine two doubles by addition")
      void shouldCombineByAddition() {
        Monoid<Double> doubleAddition = Monoids.doubleAddition();

        Double result = doubleAddition.combine(10.5, 20.3);

        assertThat(result).isEqualTo(30.8);
      }

      @Test
      @DisplayName("should handle negative numbers")
      void shouldHandleNegativeNumbers() {
        Monoid<Double> doubleAddition = Monoids.doubleAddition();

        Double result = doubleAddition.combine(-5.5, 10.5);

        assertThat(result).isEqualTo(5.0);
      }

      @Test
      @DisplayName("should handle very small numbers")
      void shouldHandleSmallNumbers() {
        Monoid<Double> doubleAddition = Monoids.doubleAddition();

        Double result = doubleAddition.combine(0.1, 0.2);

        assertThat(result).isCloseTo(0.3, Assertions.within(0.0001));
      }

      @Test
      @DisplayName("should satisfy left identity law")
      void shouldSatisfyLeftIdentity() {
        Monoid<Double> doubleAddition = Monoids.doubleAddition();
        Double value = 42.5;

        Double result = doubleAddition.combine(doubleAddition.empty(), value);

        assertThat(result).isEqualTo(value);
      }

      @Test
      @DisplayName("should satisfy right identity law")
      void shouldSatisfyRightIdentity() {
        Monoid<Double> doubleAddition = Monoids.doubleAddition();
        Double value = 42.5;

        Double result = doubleAddition.combine(value, doubleAddition.empty());

        assertThat(result).isEqualTo(value);
      }

      @Test
      @DisplayName("should satisfy associativity law")
      void shouldSatisfyAssociativity() {
        Monoid<Double> doubleAddition = Monoids.doubleAddition();
        Double a = 1.5;
        Double b = 2.5;
        Double c = 3.5;

        Double leftAssoc = doubleAddition.combine(doubleAddition.combine(a, b), c);
        Double rightAssoc = doubleAddition.combine(a, doubleAddition.combine(b, c));

        assertThat(leftAssoc).isEqualTo(rightAssoc).isEqualTo(7.5);
      }
    }

    @Nested
    @DisplayName("doubleMultiplication")
    class DoubleMultiplicationTests {

      @Test
      @DisplayName("should have 1.0 as empty value")
      void shouldHaveCorrectEmpty() {
        Monoid<Double> doubleMultiplication = Monoids.doubleMultiplication();

        assertThat(doubleMultiplication.empty()).isEqualTo(1.0);
      }

      @Test
      @DisplayName("should combine two doubles by multiplication")
      void shouldCombineByMultiplication() {
        Monoid<Double> doubleMultiplication = Monoids.doubleMultiplication();

        Double result = doubleMultiplication.combine(2.5, 4.0);

        assertThat(result).isEqualTo(10.0);
      }

      @Test
      @DisplayName("should handle negative numbers")
      void shouldHandleNegativeNumbers() {
        Monoid<Double> doubleMultiplication = Monoids.doubleMultiplication();

        Double result = doubleMultiplication.combine(-2.5, 4.0);

        assertThat(result).isEqualTo(-10.0);
      }

      @Test
      @DisplayName("should handle zero")
      void shouldHandleZero() {
        Monoid<Double> doubleMultiplication = Monoids.doubleMultiplication();

        Double result = doubleMultiplication.combine(0.0, 10.0);

        assertThat(result).isEqualTo(0.0);
      }

      @Test
      @DisplayName("should handle fractional multiplication")
      void shouldHandleFractionalMultiplication() {
        Monoid<Double> doubleMultiplication = Monoids.doubleMultiplication();

        Double result = doubleMultiplication.combine(0.5, 0.25);

        assertThat(result).isEqualTo(0.125);
      }

      @Test
      @DisplayName("should satisfy left identity law")
      void shouldSatisfyLeftIdentity() {
        Monoid<Double> doubleMultiplication = Monoids.doubleMultiplication();
        Double value = 42.5;

        Double result = doubleMultiplication.combine(doubleMultiplication.empty(), value);

        assertThat(result).isEqualTo(value);
      }

      @Test
      @DisplayName("should satisfy right identity law")
      void shouldSatisfyRightIdentity() {
        Monoid<Double> doubleMultiplication = Monoids.doubleMultiplication();
        Double value = 42.5;

        Double result = doubleMultiplication.combine(value, doubleMultiplication.empty());

        assertThat(result).isEqualTo(value);
      }

      @Test
      @DisplayName("should satisfy associativity law")
      void shouldSatisfyAssociativity() {
        Monoid<Double> doubleMultiplication = Monoids.doubleMultiplication();
        Double a = 2.0;
        Double b = 3.0;
        Double c = 4.0;

        Double leftAssoc = doubleMultiplication.combine(doubleMultiplication.combine(a, b), c);
        Double rightAssoc = doubleMultiplication.combine(a, doubleMultiplication.combine(b, c));

        assertThat(leftAssoc).isEqualTo(rightAssoc).isEqualTo(24.0);
      }
    }
  }

  @Nested
  @DisplayName("Optional monoids")
  class OptionalMonoidTests {

    @Nested
    @DisplayName("firstOptional")
    class FirstOptionalTests {

      @Test
      @DisplayName("should have empty Optional as empty value")
      void shouldHaveCorrectEmpty() {
        Monoid<Optional<String>> firstOptional = Monoids.firstOptional();

        assertThat(firstOptional.empty()).isEmpty();
      }

      @Test
      @DisplayName("should return first when first is present")
      void shouldReturnFirstWhenPresent() {
        Monoid<Optional<String>> firstOptional = Monoids.firstOptional();

        Optional<String> result =
            firstOptional.combine(Optional.of("first"), Optional.of("second"));

        assertThat(result).contains("first");
      }

      @Test
      @DisplayName("should return second when first is empty")
      void shouldReturnSecondWhenFirstEmpty() {
        Monoid<Optional<String>> firstOptional = Monoids.firstOptional();

        Optional<String> result = firstOptional.combine(Optional.empty(), Optional.of("second"));

        assertThat(result).contains("second");
      }

      @Test
      @DisplayName("should return empty when both are empty")
      void shouldReturnEmptyWhenBothEmpty() {
        Monoid<Optional<String>> firstOptional = Monoids.firstOptional();

        Optional<String> result = firstOptional.combine(Optional.empty(), Optional.empty());

        assertThat(result).isEmpty();
      }

      @Test
      @DisplayName("should satisfy left identity law")
      void shouldSatisfyLeftIdentity() {
        Monoid<Optional<String>> firstOptional = Monoids.firstOptional();
        Optional<String> value = Optional.of("test");

        Optional<String> result = firstOptional.combine(firstOptional.empty(), value);

        assertThat(result).isEqualTo(value);
      }

      @Test
      @DisplayName("should satisfy right identity law")
      void shouldSatisfyRightIdentity() {
        Monoid<Optional<String>> firstOptional = Monoids.firstOptional();
        Optional<String> value = Optional.of("test");

        Optional<String> result = firstOptional.combine(value, firstOptional.empty());

        assertThat(result).isEqualTo(value);
      }
    }

    @Nested
    @DisplayName("lastOptional")
    class LastOptionalTests {

      @Test
      @DisplayName("should have empty Optional as empty value")
      void shouldHaveCorrectEmpty() {
        Monoid<Optional<String>> lastOptional = Monoids.lastOptional();

        assertThat(lastOptional.empty()).isEmpty();
      }

      @Test
      @DisplayName("should return second when second is present")
      void shouldReturnSecondWhenPresent() {
        Monoid<Optional<String>> lastOptional = Monoids.lastOptional();

        Optional<String> result = lastOptional.combine(Optional.of("first"), Optional.of("second"));

        assertThat(result).contains("second");
      }

      @Test
      @DisplayName("should return first when second is empty")
      void shouldReturnFirstWhenSecondEmpty() {
        Monoid<Optional<String>> lastOptional = Monoids.lastOptional();

        Optional<String> result = lastOptional.combine(Optional.of("first"), Optional.empty());

        assertThat(result).contains("first");
      }

      @Test
      @DisplayName("should return empty when both are empty")
      void shouldReturnEmptyWhenBothEmpty() {
        Monoid<Optional<String>> lastOptional = Monoids.lastOptional();

        Optional<String> result = lastOptional.combine(Optional.empty(), Optional.empty());

        assertThat(result).isEmpty();
      }

      @Test
      @DisplayName("should satisfy left identity law")
      void shouldSatisfyLeftIdentity() {
        Monoid<Optional<String>> lastOptional = Monoids.lastOptional();
        Optional<String> value = Optional.of("test");

        Optional<String> result = lastOptional.combine(lastOptional.empty(), value);

        assertThat(result).isEqualTo(value);
      }

      @Test
      @DisplayName("should satisfy right identity law")
      void shouldSatisfyRightIdentity() {
        Monoid<Optional<String>> lastOptional = Monoids.lastOptional();
        Optional<String> value = Optional.of("test");

        Optional<String> result = lastOptional.combine(value, lastOptional.empty());

        assertThat(result).isEqualTo(value);
      }
    }

    @Nested
    @DisplayName("maximum")
    class MaximumTests {

      @Test
      @DisplayName("should have empty Optional as empty value")
      void shouldHaveCorrectEmpty() {
        Monoid<Optional<Integer>> maximum = Monoids.maximum();

        assertThat(maximum.empty()).isEmpty();
      }

      @Test
      @DisplayName("should return maximum of two values")
      void shouldReturnMaximum() {
        Monoid<Optional<Integer>> maximum = Monoids.maximum();

        Optional<Integer> result = maximum.combine(Optional.of(5), Optional.of(10));

        assertThat(result).contains(10);
      }

      @Test
      @DisplayName("should return first when values are equal")
      void shouldReturnFirstWhenEqual() {
        Monoid<Optional<Integer>> maximum = Monoids.maximum();

        Optional<Integer> result = maximum.combine(Optional.of(10), Optional.of(10));

        assertThat(result).contains(10);
      }

      @Test
      @DisplayName("should return second when first is empty")
      void shouldReturnSecondWhenFirstEmpty() {
        Monoid<Optional<Integer>> maximum = Monoids.maximum();

        Optional<Integer> result = maximum.combine(Optional.empty(), Optional.of(10));

        assertThat(result).contains(10);
      }

      @Test
      @DisplayName("should return first when second is empty")
      void shouldReturnFirstWhenSecondEmpty() {
        Monoid<Optional<Integer>> maximum = Monoids.maximum();

        Optional<Integer> result = maximum.combine(Optional.of(5), Optional.empty());

        assertThat(result).contains(5);
      }

      @Test
      @DisplayName("should return empty when both are empty")
      void shouldReturnEmptyWhenBothEmpty() {
        Monoid<Optional<Integer>> maximum = Monoids.maximum();

        Optional<Integer> result = maximum.combine(Optional.empty(), Optional.empty());

        assertThat(result).isEmpty();
      }

      @Test
      @DisplayName("should work with custom comparator")
      void shouldWorkWithCustomComparator() {
        Monoid<Optional<String>> maximum = Monoids.maximum(Comparator.comparing(String::length));

        Optional<String> result = maximum.combine(Optional.of("short"), Optional.of("longer"));

        assertThat(result).contains("longer");
      }

      @Test
      @DisplayName("should satisfy left identity law")
      void shouldSatisfyLeftIdentity() {
        Monoid<Optional<Integer>> maximum = Monoids.maximum();
        Optional<Integer> value = Optional.of(42);

        Optional<Integer> result = maximum.combine(maximum.empty(), value);

        assertThat(result).isEqualTo(value);
      }

      @Test
      @DisplayName("should satisfy right identity law")
      void shouldSatisfyRightIdentity() {
        Monoid<Optional<Integer>> maximum = Monoids.maximum();
        Optional<Integer> value = Optional.of(42);

        Optional<Integer> result = maximum.combine(value, maximum.empty());

        assertThat(result).isEqualTo(value);
      }
    }

    @Nested
    @DisplayName("minimum")
    class MinimumTests {

      @Test
      @DisplayName("should have empty Optional as empty value")
      void shouldHaveCorrectEmpty() {
        Monoid<Optional<Integer>> minimum = Monoids.minimum();

        assertThat(minimum.empty()).isEmpty();
      }

      @Test
      @DisplayName("should return minimum of two values")
      void shouldReturnMinimum() {
        Monoid<Optional<Integer>> minimum = Monoids.minimum();

        Optional<Integer> result = minimum.combine(Optional.of(5), Optional.of(10));

        assertThat(result).contains(5);
      }

      @Test
      @DisplayName("should return first when values are equal")
      void shouldReturnFirstWhenEqual() {
        Monoid<Optional<Integer>> minimum = Monoids.minimum();

        Optional<Integer> result = minimum.combine(Optional.of(10), Optional.of(10));

        assertThat(result).contains(10);
      }

      @Test
      @DisplayName("should return second when first is empty")
      void shouldReturnSecondWhenFirstEmpty() {
        Monoid<Optional<Integer>> minimum = Monoids.minimum();

        Optional<Integer> result = minimum.combine(Optional.empty(), Optional.of(10));

        assertThat(result).contains(10);
      }

      @Test
      @DisplayName("should return first when second is empty")
      void shouldReturnFirstWhenSecondEmpty() {
        Monoid<Optional<Integer>> minimum = Monoids.minimum();

        Optional<Integer> result = minimum.combine(Optional.of(5), Optional.empty());

        assertThat(result).contains(5);
      }

      @Test
      @DisplayName("should return empty when both are empty")
      void shouldReturnEmptyWhenBothEmpty() {
        Monoid<Optional<Integer>> minimum = Monoids.minimum();

        Optional<Integer> result = minimum.combine(Optional.empty(), Optional.empty());

        assertThat(result).isEmpty();
      }

      @Test
      @DisplayName("should work with custom comparator")
      void shouldWorkWithCustomComparator() {
        Monoid<Optional<String>> minimum = Monoids.minimum(Comparator.comparing(String::length));

        Optional<String> result = minimum.combine(Optional.of("short"), Optional.of("longer"));

        assertThat(result).contains("short");
      }

      @Test
      @DisplayName("should satisfy left identity law")
      void shouldSatisfyLeftIdentity() {
        Monoid<Optional<Integer>> minimum = Monoids.minimum();
        Optional<Integer> value = Optional.of(42);

        Optional<Integer> result = minimum.combine(minimum.empty(), value);

        assertThat(result).isEqualTo(value);
      }

      @Test
      @DisplayName("should satisfy right identity law")
      void shouldSatisfyRightIdentity() {
        Monoid<Optional<Integer>> minimum = Monoids.minimum();
        Optional<Integer> value = Optional.of(42);

        Optional<Integer> result = minimum.combine(value, minimum.empty());

        assertThat(result).isEqualTo(value);
      }
    }
  }

  @Nested
  @DisplayName("Parameterized Monoid Law Tests")
  class ParameterizedMonoidLawTests {

    // Parameterized test data for monoids with their test values
    private static Stream<Arguments> monoidIdentityLawProvider() {
      return Stream.of(
          Arguments.of("longAddition", Monoids.longAddition(), 42L),
          Arguments.of("longMultiplication", Monoids.longMultiplication(), 42L),
          Arguments.of("doubleAddition", Monoids.doubleAddition(), 42.5),
          Arguments.of("doubleMultiplication", Monoids.doubleMultiplication(), 42.5),
          Arguments.of("firstOptional", Monoids.firstOptional(), Optional.of("test")),
          Arguments.of("lastOptional", Monoids.lastOptional(), Optional.of("test")),
          Arguments.of("maximum", Monoids.maximum(), Optional.of(42)),
          Arguments.of("minimum", Monoids.minimum(), Optional.of(42)));
    }

    @ParameterizedTest(name = "{0} satisfies left identity law")
    @MethodSource("monoidIdentityLawProvider")
    @DisplayName("All monoids satisfy left identity law: combine(empty, x) == x")
    <T> void shouldSatisfyLeftIdentityLaw(String monoidName, Monoid<T> monoid, T testValue) {
      T result = monoid.combine(monoid.empty(), testValue);

      assertThat(result).isEqualTo(testValue);
    }

    @ParameterizedTest(name = "{0} satisfies right identity law")
    @MethodSource("monoidIdentityLawProvider")
    @DisplayName("All monoids satisfy right identity law: combine(x, empty) == x")
    <T> void shouldSatisfyRightIdentityLaw(String monoidName, Monoid<T> monoid, T testValue) {
      T result = monoid.combine(testValue, monoid.empty());

      assertThat(result).isEqualTo(testValue);
    }

    // Parameterized test data for monoids that have associativity law
    private static Stream<Arguments> monoidAssociativityLawProvider() {
      return Stream.of(
          Arguments.of("longAddition", Monoids.longAddition(), 100L, 200L, 300L, 600L),
          Arguments.of("longMultiplication", Monoids.longMultiplication(), 2L, 3L, 4L, 24L),
          Arguments.of("doubleAddition", Monoids.doubleAddition(), 1.5, 2.5, 3.5, 7.5),
          Arguments.of(
              "doubleMultiplication", Monoids.doubleMultiplication(), 2.0, 3.0, 4.0, 24.0));
    }

    @ParameterizedTest(name = "{0} satisfies associativity law")
    @MethodSource("monoidAssociativityLawProvider")
    @DisplayName(
        "All monoids satisfy associativity law: combine(combine(a, b), c) == combine(a, combine(b,"
            + " c))")
    <T> void shouldSatisfyAssociativityLaw(
        String monoidName, Monoid<T> monoid, T a, T b, T c, T expected) {
      T leftAssoc = monoid.combine(monoid.combine(a, b), c);
      T rightAssoc = monoid.combine(a, monoid.combine(b, c));

      assertThat(leftAssoc).isEqualTo(rightAssoc).isEqualTo(expected);
    }
  }
}
