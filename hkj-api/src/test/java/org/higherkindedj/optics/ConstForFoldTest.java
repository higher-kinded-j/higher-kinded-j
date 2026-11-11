// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ConstForFold")
class ConstForFoldTest {

  private final Monoid<String> stringMonoid = Monoids.string();
  private final Applicative<ConstForFold.Witness<String>> applicative =
      ConstForFold.applicative(stringMonoid);

  @Nested
  @DisplayName("Applicative: ap")
  class ApTests {

    @Test
    @DisplayName("ap should combine accumulated values using monoid, ignoring function")
    void apCombinesValues() {
      Kind<ConstForFold.Witness<String>, Function<Integer, Integer>> ff =
          new ConstForFold<>("hello");
      Kind<ConstForFold.Witness<String>, Integer> fa = new ConstForFold<>("world");

      Kind<ConstForFold.Witness<String>, Integer> result = applicative.ap(ff, fa);

      assertThat(ConstForFold.narrow(result).value()).isEqualTo("helloworld");
    }

    @Test
    @DisplayName("ap with empty left should return right value")
    void apWithEmptyLeft() {
      Kind<ConstForFold.Witness<String>, Function<Integer, Integer>> ff =
          new ConstForFold<>(stringMonoid.empty());
      Kind<ConstForFold.Witness<String>, Integer> fa = new ConstForFold<>("world");

      Kind<ConstForFold.Witness<String>, Integer> result = applicative.ap(ff, fa);

      assertThat(ConstForFold.narrow(result).value()).isEqualTo("world");
    }

    @Test
    @DisplayName("ap with empty right should return left value")
    void apWithEmptyRight() {
      Kind<ConstForFold.Witness<String>, Function<Integer, Integer>> ff =
          new ConstForFold<>("hello");
      Kind<ConstForFold.Witness<String>, Integer> fa = new ConstForFold<>(stringMonoid.empty());

      Kind<ConstForFold.Witness<String>, Integer> result = applicative.ap(ff, fa);

      assertThat(ConstForFold.narrow(result).value()).isEqualTo("hello");
    }

    @Test
    @DisplayName("ap with both empty should return empty")
    void apWithBothEmpty() {
      Kind<ConstForFold.Witness<String>, Function<Integer, Integer>> ff =
          new ConstForFold<>(stringMonoid.empty());
      Kind<ConstForFold.Witness<String>, Integer> fa = new ConstForFold<>(stringMonoid.empty());

      Kind<ConstForFold.Witness<String>, Integer> result = applicative.ap(ff, fa);

      assertThat(ConstForFold.narrow(result).value()).isEqualTo("");
    }
  }

  @Nested
  @DisplayName("Applicative: of")
  class OfTests {

    @Test
    @DisplayName("of should return monoid empty regardless of input")
    void ofReturnsEmpty() {
      Kind<ConstForFold.Witness<String>, Integer> result = applicative.of(42);

      assertThat(ConstForFold.narrow(result).value()).isEqualTo("");
    }
  }

  @Nested
  @DisplayName("Applicative: map")
  class MapTests {

    @Test
    @DisplayName("map should preserve accumulated value and ignore function")
    void mapPreservesValue() {
      Kind<ConstForFold.Witness<String>, Integer> fa = new ConstForFold<>("accumulated");

      Kind<ConstForFold.Witness<String>, String> result = applicative.map(Object::toString, fa);

      assertThat(ConstForFold.narrow(result).value()).isEqualTo("accumulated");
    }
  }

  @Nested
  @DisplayName("Applicative: map2")
  class Map2Tests {

    @Test
    @DisplayName("map2 should combine accumulated values using monoid")
    void map2CombinesValues() {
      Kind<ConstForFold.Witness<String>, Integer> fa = new ConstForFold<>("foo");
      Kind<ConstForFold.Witness<String>, Integer> fb = new ConstForFold<>("bar");

      Kind<ConstForFold.Witness<String>, Integer> result = applicative.map2(fa, fb, Integer::sum);

      assertThat(ConstForFold.narrow(result).value()).isEqualTo("foobar");
    }
  }

  @Nested
  @DisplayName("narrow")
  class NarrowTests {

    @Test
    @DisplayName("narrow should recover the concrete ConstForFold type")
    void narrowRecoversType() {
      ConstForFold<String, Integer> original = new ConstForFold<>("test");
      Kind<ConstForFold.Witness<String>, Integer> kind = original;

      ConstForFold<String, Integer> narrowed = ConstForFold.narrow(kind);

      assertThat(narrowed).isSameAs(original);
      assertThat(narrowed.value()).isEqualTo("test");
    }
  }

  @Nested
  @DisplayName("A value is never null")
  class NeverNull {

    @Test
    @DisplayName("a null value is refused, naming the fold's function and monoid")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void refusesNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> new ConstForFold<String, Integer>(null))
          .withMessage("a fold's function and monoid must not give null");
    }

    @Test
    @DisplayName("of refuses a monoid whose empty value is null")
    @SuppressWarnings("DataFlowIssue") // a monoid that breaks the rule, to show the fold refuses it
    void refusesNullEmpty() {
      Monoid<String> nullEmpty =
          new Monoid<>() {
            @Override
            public String empty() {
              return null;
            }

            @Override
            public String combine(String a, String b) {
              return a + b;
            }
          };

      assertThatNullPointerException().isThrownBy(() -> ConstForFold.applicative(nullEmpty).of(1));
    }
  }
}
