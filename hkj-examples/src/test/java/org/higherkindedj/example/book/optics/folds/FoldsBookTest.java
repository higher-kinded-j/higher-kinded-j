// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.folds;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.Fold;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the values the Folds page's query, aggregation and combination examples show. */
@DisplayName("the Folds page: what each query returns")
class FoldsBookTest {

  private static final Order ORDER = FoldsBook.order();

  private static final Fold<Order, LineItem> LINES = Fold.of(Order::lines);

  private static final LineItem LAPTOP = new LineItem("LAPTOP", 1, new BigDecimal("999.99"));

  private static final String LAPTOP_TEXT = "LineItem[sku=LAPTOP, quantity=1, price=999.99]";

  @Test
  @DisplayName("getAll returns every line, in order")
  void getAllReturnsEveryLine() {
    assertThat(FoldsBook.getAll(ORDER))
        .hasToString(
            "["
                + LAPTOP_TEXT
                + ", LineItem[sku=MOUSE, quantity=2, price=12.50]"
                + ", LineItem[sku=DESK, quantity=1, price=350.00]]");
  }

  @Test
  @DisplayName("preview returns the first line, and Optional.empty for an order with no lines")
  void previewReturnsTheFirstLine() {
    List<Optional<LineItem>> previews = FoldsBook.preview(LINES, ORDER);

    assertThat(previews.get(0)).contains(LAPTOP).hasToString("Optional[" + LAPTOP_TEXT + "]");
    assertThat(previews.get(1)).isEmpty().hasToString("Optional.empty");
  }

  @Test
  @DisplayName("find returns the first line priced over 500")
  void findReturnsTheLaptop() {
    assertThat(FoldsBook.find(LINES, ORDER))
        .contains(LAPTOP)
        .hasToString("Optional[" + LAPTOP_TEXT + "]");
  }

  @Test
  @DisplayName("exists, all, isEmpty and length answer for the three-line order")
  void theYesNoAndCountQueries() {
    assertThat(FoldsBook.exists(LINES, ORDER)).isTrue();
    assertThat(FoldsBook.all(LINES, ORDER)).isFalse();
    assertThat(FoldsBook.hasLines(LINES, ORDER)).isTrue();
    assertThat(FoldsBook.length(LINES, ORDER)).isEqualTo(3);
  }

  @Test
  @DisplayName("the composed fold reads every SKU across the history")
  void composedFoldReadsEverySku() {
    assertThat(FoldsBook.allSkus(ORDER))
        .containsExactly("LAPTOP", "MOUSE", "DESK", "KEYBOARD", "MONITOR");
  }

  @Test
  @DisplayName("foldMap with a BigDecimal sum of line totals totals the order to 1374.99")
  void foldMapTotalsTheLines() {
    assertThat(FoldsBook.orderTotal(ORDER))
        .as("BigDecimal equality is scale-sensitive: the page shows the total to two places")
        .isEqualTo(new BigDecimal("1374.99"));
  }

  @Test
  @DisplayName("previewMaybe, findMaybe and getAllMaybe answer Just or Nothing")
  void maybeExtensions() {
    assertThat(FoldsBook.previewMaybeExample(ORDER))
        .containsExactly(Maybe.just(LAPTOP), Maybe.nothing());
    assertThat(FoldsBook.previewMaybeExample(ORDER).get(0))
        .hasToString("Just(" + LAPTOP_TEXT + ")");
    assertThat(FoldsBook.findMaybeExample(ORDER))
        .containsExactly(Maybe.just(LAPTOP), Maybe.nothing());
    assertThat(FoldsBook.getAllMaybeExample(ORDER))
        .containsExactly(Maybe.just(ORDER.lines()), Maybe.nothing());
  }

  @Test
  @DisplayName("Fold.empty focuses on nothing")
  void emptyFoldFocusesOnNothing() {
    Employee alice = new Employee("Alice", "alice@example.com");
    Team team = new Team("Core", alice, List.of(new Employee("Bob", "bob@example.com")));

    assertThat(FoldsBook.emptyFold(team)).containsExactly(List.of(), 0, true);
  }

  @Test
  @DisplayName("plus keeps the first fold's values before the second's")
  void plusKeepsTheOrder() {
    Employee alice = new Employee("Alice", "alice@example.com");

    assertThat(FoldsBook.ordering(FoldsBook.NAME_LENS, FoldsBook.EMAIL_LENS, alice))
        .containsExactly(
            List.of("Alice", "alice@example.com"), List.of("alice@example.com", "Alice"));
  }
}
