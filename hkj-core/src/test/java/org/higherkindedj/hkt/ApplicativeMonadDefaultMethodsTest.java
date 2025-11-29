// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.Function;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Applicative and Monad Default Methods Tests")
class ApplicativeMonadDefaultMethodsTest {

  private MaybeMonad monad;

  @BeforeEach
  void setUp() {
    monad = MaybeMonad.INSTANCE;
  }

  @Nested
  @DisplayName("Applicative.map2 (curried) Tests")
  class Map2CurriedTests {

    @Test
    @DisplayName("map2 with curried function should combine two Just values")
    void map2CurriedCombinesTwoValues() {
      Kind<MaybeKind.Witness, Integer> a = MAYBE.widen(Maybe.just(10));
      Kind<MaybeKind.Witness, Integer> b = MAYBE.widen(Maybe.just(5));

      // Using curried function: Function<A, Function<B, C>>
      Function<Integer, Function<Integer, Integer>> curriedAdd = x -> y -> x + y;

      Kind<MaybeKind.Witness, Integer> result = monad.map2(a, b, curriedAdd);

      Maybe<Integer> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.orElse(-1)).isEqualTo(15);
    }

    @Test
    @DisplayName("map2 with curried function should return Nothing if first value is Nothing")
    void map2CurriedReturnsNothingOnFirstNothing() {
      Kind<MaybeKind.Witness, Integer> a = MAYBE.widen(Maybe.nothing());
      Kind<MaybeKind.Witness, Integer> b = MAYBE.widen(Maybe.just(5));

      Function<Integer, Function<Integer, Integer>> curriedAdd = x -> y -> x + y;

      Kind<MaybeKind.Witness, Integer> result = monad.map2(a, b, curriedAdd);

      Maybe<Integer> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("map2 with curried function should return Nothing if second value is Nothing")
    void map2CurriedReturnsNothingOnSecondNothing() {
      Kind<MaybeKind.Witness, Integer> a = MAYBE.widen(Maybe.just(10));
      Kind<MaybeKind.Witness, Integer> b = MAYBE.widen(Maybe.nothing());

      Function<Integer, Function<Integer, Integer>> curriedAdd = x -> y -> x + y;

      Kind<MaybeKind.Witness, Integer> result = monad.map2(a, b, curriedAdd);

      Maybe<Integer> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("map2 with curried function should apply complex transformation")
    void map2CurriedAppliesComplexFunction() {
      Kind<MaybeKind.Witness, String> firstName = MAYBE.widen(Maybe.just("John"));
      Kind<MaybeKind.Witness, String> lastName = MAYBE.widen(Maybe.just("Doe"));

      Function<String, Function<String, String>> curriedConcat =
          first -> last -> first + " " + last;

      Kind<MaybeKind.Witness, String> result = monad.map2(firstName, lastName, curriedConcat);

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.orElse("")).isEqualTo("John Doe");
    }
  }

  @Nested
  @DisplayName("Applicative.map4 Tests")
  class Map4Tests {

    @Test
    @DisplayName("map4 should combine four Just values")
    void map4CombinesFourValues() {
      Kind<MaybeKind.Witness, Integer> a = MAYBE.widen(Maybe.just(1));
      Kind<MaybeKind.Witness, Integer> b = MAYBE.widen(Maybe.just(2));
      Kind<MaybeKind.Witness, Integer> c = MAYBE.widen(Maybe.just(3));
      Kind<MaybeKind.Witness, Integer> d = MAYBE.widen(Maybe.just(4));

      Kind<MaybeKind.Witness, Integer> result =
          monad.map4(a, b, c, d, (x, y, z, w) -> x + y + z + w);

      Maybe<Integer> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.orElse(-1)).isEqualTo(10);
    }

    @Test
    @DisplayName("map4 should return Nothing if any value is Nothing")
    void map4ReturnsNothingOnAnyNothing() {
      Kind<MaybeKind.Witness, Integer> a = MAYBE.widen(Maybe.just(1));
      Kind<MaybeKind.Witness, Integer> b = MAYBE.widen(Maybe.nothing());
      Kind<MaybeKind.Witness, Integer> c = MAYBE.widen(Maybe.just(3));
      Kind<MaybeKind.Witness, Integer> d = MAYBE.widen(Maybe.just(4));

      Kind<MaybeKind.Witness, Integer> result =
          monad.map4(a, b, c, d, (x, y, z, w) -> x + y + z + w);

      Maybe<Integer> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("map4 should apply complex function")
    void map4AppliesComplexFunction() {
      Kind<MaybeKind.Witness, String> firstName = MAYBE.widen(Maybe.just("John"));
      Kind<MaybeKind.Witness, String> middleName = MAYBE.widen(Maybe.just("M"));
      Kind<MaybeKind.Witness, String> lastName = MAYBE.widen(Maybe.just("Doe"));
      Kind<MaybeKind.Witness, Integer> age = MAYBE.widen(Maybe.just(30));

      Kind<MaybeKind.Witness, String> result =
          monad.map4(
              firstName,
              middleName,
              lastName,
              age,
              (fn, mn, ln, a) -> fn + " " + mn + ". " + ln + " (" + a + ")");

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.orElse("")).isEqualTo("John M. Doe (30)");
    }
  }

  @Nested
  @DisplayName("Applicative.map5 Tests")
  class Map5Tests {

    @Test
    @DisplayName("map5 should combine five Just values")
    void map5CombinesFiveValues() {
      Kind<MaybeKind.Witness, Integer> a = MAYBE.widen(Maybe.just(1));
      Kind<MaybeKind.Witness, Integer> b = MAYBE.widen(Maybe.just(2));
      Kind<MaybeKind.Witness, Integer> c = MAYBE.widen(Maybe.just(3));
      Kind<MaybeKind.Witness, Integer> d = MAYBE.widen(Maybe.just(4));
      Kind<MaybeKind.Witness, Integer> e = MAYBE.widen(Maybe.just(5));

      Kind<MaybeKind.Witness, Integer> result =
          monad.map5(a, b, c, d, e, (v1, v2, v3, v4, v5) -> v1 + v2 + v3 + v4 + v5);

      Maybe<Integer> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.orElse(-1)).isEqualTo(15);
    }

    @Test
    @DisplayName("map5 should return Nothing if any value is Nothing")
    void map5ReturnsNothingOnAnyNothing() {
      Kind<MaybeKind.Witness, Integer> a = MAYBE.widen(Maybe.just(1));
      Kind<MaybeKind.Witness, Integer> b = MAYBE.widen(Maybe.just(2));
      Kind<MaybeKind.Witness, Integer> c = MAYBE.widen(Maybe.just(3));
      Kind<MaybeKind.Witness, Integer> d = MAYBE.widen(Maybe.nothing());
      Kind<MaybeKind.Witness, Integer> e = MAYBE.widen(Maybe.just(5));

      Kind<MaybeKind.Witness, Integer> result =
          monad.map5(a, b, c, d, e, (v1, v2, v3, v4, v5) -> v1 + v2 + v3 + v4 + v5);

      Maybe<Integer> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("map5 should apply complex function")
    void map5AppliesComplexFunction() {
      Kind<MaybeKind.Witness, String> a = MAYBE.widen(Maybe.just("A"));
      Kind<MaybeKind.Witness, String> b = MAYBE.widen(Maybe.just("B"));
      Kind<MaybeKind.Witness, String> c = MAYBE.widen(Maybe.just("C"));
      Kind<MaybeKind.Witness, String> d = MAYBE.widen(Maybe.just("D"));
      Kind<MaybeKind.Witness, String> e = MAYBE.widen(Maybe.just("E"));

      Kind<MaybeKind.Witness, String> result =
          monad.map5(a, b, c, d, e, (v1, v2, v3, v4, v5) -> v1 + v2 + v3 + v4 + v5);

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.orElse("")).isEqualTo("ABCDE");
    }
  }

  @Nested
  @DisplayName("Monad.flatMapIfOrElse Tests")
  class FlatMapIfOrElseTests {

    @Test
    @DisplayName("flatMapIfOrElse should apply ifTrue when predicate is true")
    void flatMapIfOrElseAppliesIfTrue() {
      Kind<MaybeKind.Witness, Integer> ma = MAYBE.widen(Maybe.just(10));

      Function<Integer, Kind<MaybeKind.Witness, String>> ifTrue =
          i -> MAYBE.widen(Maybe.just("positive: " + i));
      Function<Integer, Kind<MaybeKind.Witness, String>> ifFalse =
          i -> MAYBE.widen(Maybe.just("negative: " + i));

      Kind<MaybeKind.Witness, String> result =
          monad.flatMapIfOrElse(i -> i > 0, ifTrue, ifFalse, ma);

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.orElse("")).isEqualTo("positive: 10");
    }

    @Test
    @DisplayName("flatMapIfOrElse should apply ifFalse when predicate is false")
    void flatMapIfOrElseAppliesIfFalse() {
      Kind<MaybeKind.Witness, Integer> ma = MAYBE.widen(Maybe.just(-5));

      Function<Integer, Kind<MaybeKind.Witness, String>> ifTrue =
          i -> MAYBE.widen(Maybe.just("positive: " + i));
      Function<Integer, Kind<MaybeKind.Witness, String>> ifFalse =
          i -> MAYBE.widen(Maybe.just("negative: " + i));

      Kind<MaybeKind.Witness, String> result =
          monad.flatMapIfOrElse(i -> i > 0, ifTrue, ifFalse, ma);

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.orElse("")).isEqualTo("negative: -5");
    }

    @Test
    @DisplayName("flatMapIfOrElse should return Nothing when ma is Nothing")
    void flatMapIfOrElseWithNothing() {
      Kind<MaybeKind.Witness, Integer> ma = MAYBE.widen(Maybe.nothing());

      Function<Integer, Kind<MaybeKind.Witness, String>> ifTrue =
          i -> MAYBE.widen(Maybe.just("true"));
      Function<Integer, Kind<MaybeKind.Witness, String>> ifFalse =
          i -> MAYBE.widen(Maybe.just("false"));

      Kind<MaybeKind.Witness, String> result =
          monad.flatMapIfOrElse(i -> i > 0, ifTrue, ifFalse, ma);

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("flatMapIfOrElse should handle Nothing returned from ifTrue")
    void flatMapIfOrElseHandlesNothingFromIfTrue() {
      Kind<MaybeKind.Witness, Integer> ma = MAYBE.widen(Maybe.just(10));

      Function<Integer, Kind<MaybeKind.Witness, String>> ifTrue = i -> MAYBE.widen(Maybe.nothing());
      Function<Integer, Kind<MaybeKind.Witness, String>> ifFalse =
          i -> MAYBE.widen(Maybe.just("false"));

      Kind<MaybeKind.Witness, String> result =
          monad.flatMapIfOrElse(i -> i > 0, ifTrue, ifFalse, ma);

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Monad.flatMap4 Tests")
  class FlatMap4Tests {

    @Test
    @DisplayName("flatMap4 should combine four monadic values")
    void flatMap4CombinesFourValues() {
      Kind<MaybeKind.Witness, Integer> ma = MAYBE.widen(Maybe.just(1));
      Kind<MaybeKind.Witness, Integer> mb = MAYBE.widen(Maybe.just(2));
      Kind<MaybeKind.Witness, Integer> mc = MAYBE.widen(Maybe.just(3));
      Kind<MaybeKind.Witness, Integer> md = MAYBE.widen(Maybe.just(4));

      Kind<MaybeKind.Witness, Integer> result =
          monad.flatMap4(ma, mb, mc, md, (a, b, c, d) -> MAYBE.widen(Maybe.just(a * b * c * d)));

      Maybe<Integer> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.orElse(-1)).isEqualTo(24); // 1 * 2 * 3 * 4
    }

    @Test
    @DisplayName("flatMap4 should return Nothing when any input is Nothing")
    void flatMap4ReturnsNothingOnAnyNothing() {
      Kind<MaybeKind.Witness, Integer> ma = MAYBE.widen(Maybe.just(1));
      Kind<MaybeKind.Witness, Integer> mb = MAYBE.widen(Maybe.nothing());
      Kind<MaybeKind.Witness, Integer> mc = MAYBE.widen(Maybe.just(3));
      Kind<MaybeKind.Witness, Integer> md = MAYBE.widen(Maybe.just(4));

      Kind<MaybeKind.Witness, Integer> result =
          monad.flatMap4(ma, mb, mc, md, (a, b, c, d) -> MAYBE.widen(Maybe.just(a * b * c * d)));

      Maybe<Integer> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("flatMap4 should handle Nothing from combiner function")
    void flatMap4HandlesNothingFromCombiner() {
      Kind<MaybeKind.Witness, Integer> ma = MAYBE.widen(Maybe.just(1));
      Kind<MaybeKind.Witness, Integer> mb = MAYBE.widen(Maybe.just(2));
      Kind<MaybeKind.Witness, Integer> mc = MAYBE.widen(Maybe.just(3));
      Kind<MaybeKind.Witness, Integer> md = MAYBE.widen(Maybe.just(4));

      Kind<MaybeKind.Witness, Integer> result =
          monad.flatMap4(ma, mb, mc, md, (a, b, c, d) -> MAYBE.widen(Maybe.nothing()));

      Maybe<Integer> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Monad.flatMap5 Tests")
  class FlatMap5Tests {

    @Test
    @DisplayName("flatMap5 should combine five monadic values")
    void flatMap5CombinesFiveValues() {
      Kind<MaybeKind.Witness, Integer> ma = MAYBE.widen(Maybe.just(1));
      Kind<MaybeKind.Witness, Integer> mb = MAYBE.widen(Maybe.just(2));
      Kind<MaybeKind.Witness, Integer> mc = MAYBE.widen(Maybe.just(3));
      Kind<MaybeKind.Witness, Integer> md = MAYBE.widen(Maybe.just(4));
      Kind<MaybeKind.Witness, Integer> me = MAYBE.widen(Maybe.just(5));

      Kind<MaybeKind.Witness, Integer> result =
          monad.flatMap5(
              ma, mb, mc, md, me, (a, b, c, d, e) -> MAYBE.widen(Maybe.just(a + b + c + d + e)));

      Maybe<Integer> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.orElse(-1)).isEqualTo(15); // 1 + 2 + 3 + 4 + 5
    }

    @Test
    @DisplayName("flatMap5 should return Nothing when any input is Nothing")
    void flatMap5ReturnsNothingOnAnyNothing() {
      Kind<MaybeKind.Witness, Integer> ma = MAYBE.widen(Maybe.just(1));
      Kind<MaybeKind.Witness, Integer> mb = MAYBE.widen(Maybe.just(2));
      Kind<MaybeKind.Witness, Integer> mc = MAYBE.widen(Maybe.nothing());
      Kind<MaybeKind.Witness, Integer> md = MAYBE.widen(Maybe.just(4));
      Kind<MaybeKind.Witness, Integer> me = MAYBE.widen(Maybe.just(5));

      Kind<MaybeKind.Witness, Integer> result =
          monad.flatMap5(
              ma, mb, mc, md, me, (a, b, c, d, e) -> MAYBE.widen(Maybe.just(a + b + c + d + e)));

      Maybe<Integer> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("flatMap5 should handle Nothing from combiner function")
    void flatMap5HandlesNothingFromCombiner() {
      Kind<MaybeKind.Witness, Integer> ma = MAYBE.widen(Maybe.just(1));
      Kind<MaybeKind.Witness, Integer> mb = MAYBE.widen(Maybe.just(2));
      Kind<MaybeKind.Witness, Integer> mc = MAYBE.widen(Maybe.just(3));
      Kind<MaybeKind.Witness, Integer> md = MAYBE.widen(Maybe.just(4));
      Kind<MaybeKind.Witness, Integer> me = MAYBE.widen(Maybe.just(5));

      Kind<MaybeKind.Witness, Integer> result =
          monad.flatMap5(ma, mb, mc, md, me, (a, b, c, d, e) -> MAYBE.widen(Maybe.nothing()));

      Maybe<Integer> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Monad.peek Tests")
  class PeekTests {

    @Test
    @DisplayName("peek should execute action and return original value")
    void peekExecutesAction() {
      StringBuilder sb = new StringBuilder();
      Kind<MaybeKind.Witness, Integer> ma = MAYBE.widen(Maybe.just(42));

      Kind<MaybeKind.Witness, Integer> result = monad.peek(i -> sb.append("Value: ").append(i), ma);

      Maybe<Integer> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.orElse(-1)).isEqualTo(42);
      assertThat(sb.toString()).isEqualTo("Value: 42");
    }

    @Test
    @DisplayName("peek should not execute action for Nothing")
    void peekDoesNotExecuteForNothing() {
      StringBuilder sb = new StringBuilder();
      Kind<MaybeKind.Witness, Integer> ma = MAYBE.widen(Maybe.nothing());

      Kind<MaybeKind.Witness, Integer> result = monad.peek(i -> sb.append("Value: ").append(i), ma);

      Maybe<Integer> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
      assertThat(sb.toString()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Monad.asUnit Tests")
  class AsUnitTests {

    @Test
    @DisplayName("asUnit should convert Just value to Just Unit")
    void asUnitConvertsJustToUnit() {
      Kind<MaybeKind.Witness, Integer> ma = MAYBE.widen(Maybe.just(42));

      Kind<MaybeKind.Witness, Unit> result = monad.asUnit(ma);

      Maybe<Unit> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.orElse(null)).isEqualTo(Unit.INSTANCE);
    }

    @Test
    @DisplayName("asUnit should preserve Nothing")
    void asUnitPreservesNothing() {
      Kind<MaybeKind.Witness, Integer> ma = MAYBE.widen(Maybe.nothing());

      Kind<MaybeKind.Witness, Unit> result = monad.asUnit(ma);

      Maybe<Unit> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Monad.as Tests")
  class AsTests {

    @Test
    @DisplayName("as should replace Just value with constant")
    void asReplacesValue() {
      Kind<MaybeKind.Witness, Integer> ma = MAYBE.widen(Maybe.just(42));

      Kind<MaybeKind.Witness, String> result = monad.as("constant", ma);

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.orElse("")).isEqualTo("constant");
    }

    @Test
    @DisplayName("as should preserve Nothing")
    void asPreservesNothing() {
      Kind<MaybeKind.Witness, Integer> ma = MAYBE.widen(Maybe.nothing());

      Kind<MaybeKind.Witness, String> result = monad.as("constant", ma);

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }
  }
}
