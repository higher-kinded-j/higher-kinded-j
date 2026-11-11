// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.constant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.higherkindedj.example.book.foundations.constant.ConstBook.BASKET;
import static org.higherkindedj.example.book.foundations.constant.ConstBook.eachLine;
import static org.higherkindedj.example.book.foundations.constant.ConstBook.lineTotal;
import static org.higherkindedj.example.book.foundations.constant.ConstBook.withVat;
import static org.higherkindedj.hkt.constant.ConstKindHelper.CONST;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemLenses;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.constant.Const;
import org.higherkindedj.hkt.constant.ConstApplicative;
import org.higherkindedj.hkt.constant.ConstKind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the Const page makes. */
@DisplayName("Const: a value that every map leaves alone")
class ConstBookTest {

  private static final ConstApplicative<BigDecimal> SUMMING =
      new ConstApplicative<>(Monoids.bigDecimalAddition());

  @Nested
  @DisplayName("The value map leaves alone")
  class Held {

    @Test
    @DisplayName("mapSecond changes only the type, and never calls its function")
    void mapSecondNeverCallsItsFunction() {
      AtomicInteger calls = new AtomicInteger();
      Const<BigDecimal, LineItem> total = new Const<>(new BigDecimal("50.00"));

      Const<BigDecimal, String> relabelled =
          total.mapSecond(
              line -> {
                calls.incrementAndGet();
                return line.sku();
              });

      assertThat(relabelled.value()).isEqualByComparingTo("50.00");
      assertThat(calls).hasValue(0);
    }

    @Test
    @DisplayName("the Applicative's map leaves the value too")
    void applicativeMapLeavesTheValue() {
      Kind<ConstKind.Witness<BigDecimal>, String> mapped =
          SUMMING.map(LineItem::sku, CONST.widen(new Const<>(new BigDecimal("7.00"))));

      assertThat(CONST.narrow(mapped).value()).isEqualByComparingTo("7.00");
    }
  }

  @Nested
  @DisplayName("One traversal, two jobs")
  class OneTraversal {

    @Test
    @DisplayName("run in Id, the traversal rebuilds every line with its price with VAT")
    void idRebuildsTheLines() {
      List<LineItem> repriced =
          LIST.narrow(
              ID.narrow(
                      eachLine(
                          Instances.applicative(id()),
                          line -> ID.widen(Id.of(withVat(line))),
                          BASKET))
                  .value());

      assertThat(repriced)
          .containsExactly(
              new LineItem("LAMP", 1, new BigDecimal("48.00")),
              new LineItem("BULB", 4, new BigDecimal("3.00")));
    }

    @Test
    @DisplayName("run in Const, the same traversal visits each line once and adds the totals")
    void constAddsTheTotals() {
      List<String> visited = new ArrayList<>();

      Kind<ConstKind.Witness<BigDecimal>, Kind<ListKind.Witness, LineItem>> result =
          eachLine(
              SUMMING,
              line -> {
                visited.add(line.sku());
                return CONST.widen(new Const<>(lineTotal(line)));
              },
              BASKET);

      assertThat(CONST.narrow(result).value()).isEqualByComparingTo("50.00");
      assertThat(visited).containsExactly("LAMP", "BULB");
    }

    @Test
    @DisplayName("a list monoid collects what each line gives, in the order of the lines")
    void listMonoidCollectsInOrder() {
      ConstApplicative<List<String>> collecting = new ConstApplicative<>(Monoids.list());

      Kind<ConstKind.Witness<List<String>>, Kind<ListKind.Witness, LineItem>> result =
          eachLine(collecting, line -> CONST.widen(new Const<>(List.of(line.sku()))), BASKET);

      assertThat(CONST.narrow(result).value()).containsExactly("LAMP", "BULB");
    }

    @Test
    @DisplayName("an empty basket gives the monoid's empty value")
    void emptyBasketGivesEmpty() {
      Kind<ConstKind.Witness<BigDecimal>, Kind<ListKind.Witness, LineItem>> result =
          eachLine(SUMMING, line -> CONST.widen(new Const<>(lineTotal(line))), List.of());

      assertThat(CONST.narrow(result).value()).isEqualByComparingTo("0");
    }
  }

  @Nested
  @DisplayName("The Applicative needs a Monoid")
  class NeedsAMonoid {

    @Test
    @DisplayName("of gives the monoid's empty value, whatever it is given")
    void ofGivesEmpty() {
      assertThat(CONST.narrow(SUMMING.of(BASKET.getFirst())).value()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("ap combines the function's value and then the argument's")
    void apCombinesInOrder() {
      ConstApplicative<String> joining = new ConstApplicative<>(Monoids.string());
      Kind<ConstKind.Witness<String>, Function<Integer, Integer>> left =
          CONST.widen(new Const<>("LAMP;"));
      Kind<ConstKind.Witness<String>, Integer> right = CONST.widen(new Const<>("BULB;"));

      assertThat(CONST.narrow(joining.ap(left, right)).value()).isEqualTo("LAMP;BULB;");
    }
  }

  @Nested
  @DisplayName("Const behind folds and getters")
  class Behind {

    @Test
    @DisplayName("a lens run in Const reads its focus and rebuilds nothing")
    void lensInConstReads() {
      Kind<ConstKind.Witness<BigDecimal>, LineItem> read =
          LineItemLenses.price()
              .modifyF(price -> CONST.widen(new Const<>(price)), BASKET.getFirst(), SUMMING);

      assertThat(CONST.narrow(read).value()).isEqualByComparingTo("40.00");
    }
  }

  @Nested
  @DisplayName("A Const always holds a value")
  class AlwaysAValue {

    @Test
    @DisplayName("a null value is refused at construction")
    @SuppressWarnings("DataFlowIssue") // the null is the point of the test
    void refusesNull() {
      assertThatNullPointerException().isThrownBy(() -> new Const<BigDecimal, LineItem>(null));
    }

    @Test
    @DisplayName("a bimap function that returns null is refused")
    void refusesNullFromBimap() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  new Const<BigDecimal, LineItem>(BigDecimal.ONE).bimap(_ -> null, LineItem::sku));
    }

    @Test
    @DisplayName("a traversal's fold fails at the line whose function returns null")
    void traversalFoldFailsAtTheNullLine() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  Traversals.<LineItem>forList()
                      .asFold()
                      .foldMap(
                          Monoids.bigDecimalAddition(),
                          line -> line.sku().equals("BULB") ? null : lineTotal(line),
                          BASKET));
    }

    @Test
    @DisplayName("a mapFirst function that returns null is refused")
    void refusesNullFromMapFirst() {
      assertThatNullPointerException()
          .isThrownBy(() -> new Const<BigDecimal, LineItem>(BigDecimal.ONE).mapFirst(_ -> null));
    }
  }
}
