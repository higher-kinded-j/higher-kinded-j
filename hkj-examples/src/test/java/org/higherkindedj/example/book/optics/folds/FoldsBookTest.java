// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.folds;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.Fold;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the values the Folds page's query, aggregation and combination examples show. */
@DisplayName("the Folds page: what each query returns")
class FoldsBookTest {

  private static final Purchase PURCHASE = FoldsBook.purchase();

  private static final Fold<Purchase, Product> ITEMS = PurchaseFolds.items();

  private static final Product LAPTOP =
      new Product("Laptop", new BigDecimal("999.99"), "Electronics", true);

  private static final String LAPTOP_TEXT =
      "Product[name=Laptop, price=999.99, category=Electronics, inStock=true]";

  @Test
  @DisplayName("getAll returns every product, in order")
  void getAllReturnsEveryProduct() {
    assertThat(FoldsBook.getAll(PURCHASE))
        .hasToString(
            "["
                + LAPTOP_TEXT
                + ", Product[name=Mouse, price=25.00, category=Electronics, inStock=true]"
                + ", Product[name=Desk, price=350.00, category=Furniture, inStock=false]]");
  }

  @Test
  @DisplayName("preview returns the first product, and Optional.empty for an empty purchase")
  void previewReturnsTheFirstProduct() {
    List<Optional<Product>> previews = FoldsBook.preview(ITEMS, PURCHASE);

    assertThat(previews.get(0)).contains(LAPTOP).hasToString("Optional[" + LAPTOP_TEXT + "]");
    assertThat(previews.get(1)).isEmpty().hasToString("Optional.empty");
  }

  @Test
  @DisplayName("find returns the first product priced over 500")
  void findReturnsTheLaptop() {
    assertThat(FoldsBook.find(ITEMS, PURCHASE)).contains(LAPTOP);
  }

  @Test
  @DisplayName("exists, all, isEmpty and length answer for the three-product purchase")
  void theYesNoAndCountQueries() {
    assertThat(FoldsBook.exists(ITEMS, PURCHASE)).isTrue();
    assertThat(FoldsBook.all(ITEMS, PURCHASE)).isFalse();
    assertThat(FoldsBook.hasItems(ITEMS, PURCHASE)).isTrue();
    assertThat(FoldsBook.length(ITEMS, PURCHASE)).isEqualTo(3);
  }

  @Test
  @DisplayName("the composed fold reads every product name across the history")
  void composedFoldReadsEveryName() {
    assertThat(FoldsBook.allProductNames(PURCHASE))
        .containsExactly("Laptop", "Mouse", "Desk", "Keyboard", "Monitor");
  }

  @Test
  @DisplayName("foldMap with a BigDecimal sum totals the purchase to 1374.99")
  void foldMapTotalsThePrices() {
    assertThat(FoldsBook.totalPrice(PURCHASE))
        .as("BigDecimal equality is scale-sensitive: the page shows the total to two places")
        .isEqualTo(new BigDecimal("1374.99"));
  }

  @Test
  @DisplayName("previewMaybe, findMaybe and getAllMaybe answer Just or Nothing")
  void maybeExtensions() {
    assertThat(FoldsBook.previewMaybeExample(PURCHASE))
        .containsExactly(Maybe.just(LAPTOP), Maybe.nothing());
    assertThat(FoldsBook.previewMaybeExample(PURCHASE).get(0))
        .hasToString("Just(" + LAPTOP_TEXT + ")");
    assertThat(FoldsBook.findMaybeExample(PURCHASE))
        .containsExactly(Maybe.just(LAPTOP), Maybe.nothing());
    assertThat(FoldsBook.getAllMaybeExample(PURCHASE))
        .containsExactly(Maybe.just(PURCHASE.items()), Maybe.nothing());
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
