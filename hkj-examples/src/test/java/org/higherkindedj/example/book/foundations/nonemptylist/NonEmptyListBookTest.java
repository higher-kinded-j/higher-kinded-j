// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.nonemptylist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.foundations.nonemptylist.NonEmptyListBook.EMPTIED;
import static org.higherkindedj.example.book.foundations.nonemptylist.NonEmptyListBook.PLACED;
import static org.higherkindedj.example.book.foundations.nonemptylist.NonEmptyListBook.lines;
import static org.higherkindedj.example.book.foundations.nonemptylist.NonEmptyListBook.orderTotal;
import static org.higherkindedj.example.book.foundations.nonemptylist.NonEmptyListBook.priciest;
import static org.higherkindedj.hkt.instances.Witnesses.nonEmptyList;
import static org.higherkindedj.hkt.nonemptylist.NonEmptyListKindHelper.NON_EMPTY_LIST;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.nonemptylist.NonEmptyListKind;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the NonEmptyList page makes about a list that is never empty. */
@DisplayName("NonEmptyList: a list that is never empty")
class NonEmptyListBookTest {

  private static final LineItem LAMP = new LineItem("LAMP", 2, new BigDecimal("40.00"));
  private static final LineItem SHADE = new LineItem("SHADE", 1, new BigDecimal("12.50"));

  @Nested
  @DisplayName("the problem")
  class TheProblem {

    @Test
    @DisplayName("a List lets an invalid result hold no errors, and getFirst then throws")
    void aListAllowsNoErrors() {
      Validated<List<String>, LineItem> noReason = Validated.invalid(List.of());

      assertThat(noReason.getError()).isEmpty();
      assertThatThrownBy(() -> noReason.getError().getFirst())
          .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("invalidNel always holds an error, and head reads it")
    void invalidNelAlwaysHoldsAnError() {
      Validated<NonEmptyList<String>, LineItem> refused =
          Validated.invalidNel("order has no lines");

      assertThat(refused.getError().head()).isEqualTo("order has no lines");
    }
  }

  @Nested
  @DisplayName("an order's lines")
  class AnOrdersLines {

    @Test
    @DisplayName("fromList gives Just for an order with lines, and Nothing for one without")
    void fromListGivesJustOrNothing() {
      assertThat(lines(PLACED)).isEqualTo(Maybe.just(NonEmptyList.of(LAMP, SHADE)));
      assertThat(lines(EMPTIED)).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("reduce totals the lines with no starting value")
    void reduceTotalsTheLines() {
      assertThat(orderTotal(NonEmptyList.of(LAMP, SHADE))).isEqualByComparingTo("92.50");
      assertThat(orderTotal(NonEmptyList.single(SHADE))).isEqualByComparingTo("12.50");
    }

    @Test
    @DisplayName("head and max return a line, never throwing")
    void headAndMaxReturnALine() {
      NonEmptyList<LineItem> lines = NonEmptyList.of(SHADE, LAMP);

      assertThat(lines.head()).isEqualTo(SHADE);
      assertThat(priciest(lines)).isEqualTo(LAMP);
    }
  }

  @Nested
  @DisplayName("the methods")
  class TheMethods {

    private final NonEmptyList<Integer> numbers = NonEmptyList.of(3, 1, 2);

    @Test
    @DisplayName("of, single, concat, append and prepend build lists that are never empty")
    void theBuildersNeverGiveAnEmptyList() {
      assertThat(NonEmptyList.single(1).toJavaList()).containsExactly(1);
      assertThat(numbers.concat(NonEmptyList.of(4)).append(5).prepend(0).toJavaList())
          .containsExactly(0, 3, 1, 2, 4, 5);
    }

    @Test
    @DisplayName("fromIterable gives Nothing for an empty iterable")
    void fromIterableGivesNothingWhenEmpty() {
      assertThat(NonEmptyList.fromIterable(new ArrayDeque<Integer>())).isEqualTo(Maybe.nothing());
      assertThat(NonEmptyList.fromIterable(new ArrayDeque<>(List.of(1, 2))))
          .isEqualTo(Maybe.just(NonEmptyList.of(1, 2)));
    }

    @Test
    @DisplayName("head, last, min, max and reduce are total")
    void theReadersAreTotal() {
      assertThat(numbers.head()).isEqualTo(3);
      assertThat(numbers.last()).isEqualTo(2);
      assertThat(numbers.min(Comparator.naturalOrder())).isEqualTo(1);
      assertThat(numbers.max(Comparator.naturalOrder())).isEqualTo(3);
      assertThat(numbers.reduce(Integer::sum)).isEqualTo(6);
      assertThat(numbers.size()).isEqualTo(3);
    }

    @Test
    @DisplayName("map, flatMap, reverse and foldLeft keep at least one element")
    void theTransformationsKeepAnElement() {
      assertThat(numbers.map(n -> n * 10)).isEqualTo(NonEmptyList.of(30, 10, 20));
      assertThat(numbers.flatMap(n -> NonEmptyList.of(n, n))).hasSize(6);
      assertThat(numbers.reverse()).isEqualTo(NonEmptyList.of(2, 1, 3));
      assertThat(numbers.foldLeft("", (acc, n) -> acc + n)).isEqualTo("312");
    }

    @Test
    @DisplayName("tail and toJavaList are Lists that cannot be changed, and tail may be empty")
    void tailCannotBeChanged() {
      assertThat(numbers.tail()).containsExactly(1, 2);
      assertThat(NonEmptyList.single(1).tail()).isEmpty();
      assertThatThrownBy(() -> numbers.tail().add(4))
          .isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(() -> numbers.toJavaList().add(4))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("the instance's ap pairs every function with every value")
    void apPairsEveryFunctionWithEveryValue() {
      Monad<NonEmptyListKind.Witness> monad = Instances.monad(nonEmptyList());
      Kind<NonEmptyListKind.Witness, Function<Integer, Integer>> functions =
          NON_EMPTY_LIST.widen(NonEmptyList.of(n -> n + 1, n -> n * 10));

      assertThat(NON_EMPTY_LIST.narrow(monad.ap(functions, NON_EMPTY_LIST.widen(numbers))))
          .isEqualTo(NonEmptyList.of(4, 2, 3, 30, 10, 20));
    }

    @Test
    @DisplayName(
        "a ValidationPath from invalidNel carries the semigroup, so zipWithAccum needs none")
    void aValidationPathCarriesTheSemigroup() {
      assertThat(
              Path.<String, Integer>invalidNel("a")
                  .zipWithAccum(Path.<String, Integer>invalidNel("b"), Integer::sum)
                  .run())
          .isEqualTo(Validated.invalid(NonEmptyList.of("a", "b")));
    }

    @Test
    @DisplayName("a null element is refused when the list is built")
    void aNullElementIsRefused() {
      assertThatThrownBy(() -> NonEmptyList.single(null)).isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> NonEmptyList.of(1, Arrays.asList(2, null)))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("semigroup concatenates, keeping the order")
    void semigroupConcatenates() {
      assertThat(
              NonEmptyList.<String>semigroup().combine(NonEmptyList.of("a"), NonEmptyList.of("b")))
          .isEqualTo(NonEmptyList.of("a", "b"));
    }
  }

  @Test
  @DisplayName("widening returns the same object")
  void wideningReturnsTheSameObject() {
    NonEmptyList<LineItem> lines = NonEmptyList.of(LAMP, SHADE);

    assertThat(NON_EMPTY_LIST.widen(lines)).isSameAs(lines);
  }
}
