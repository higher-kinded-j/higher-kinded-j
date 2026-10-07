// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.limiting;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.higherkindedj.optics.util.ListTraversals;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Holds the claims the Limiting Traversals page makes: what each limiting method focuses, and that
 * {@code modify} changes only those elements and keeps the rest in place.
 *
 * <p>Prices are compared with {@code isEqualTo}, so the scale is part of each claim: a discount is
 * rounded back to two decimal places, as a price is written.
 */
@DisplayName("the Limiting Traversals page: each slice, read and written")
class LimitingBookTest {

  private static List<String> names(List<Product> products) {
    return products.stream().map(Product::name).toList();
  }

  private static List<BigDecimal> prices(List<Product> products) {
    return products.stream().map(Product::price).toList();
  }

  private static BigDecimal money(String value) {
    return new BigDecimal(value);
  }

  @Test
  @DisplayName("taking(3) discounts and returns the first three products only")
  void taking() {
    LimitingBook.Slice slice = LimitingBook.taking();

    assertThat(names(slice.focused())).containsExactly("Widget", "Gadget", "Gizmo");
    assertThat(prices(slice.modified()))
        .as("10%% off the first three, rounded to pence; the last two unchanged")
        .containsExactly(
            money("9.00"), money("22.50"), money("13.50"), money("30.00"), money("20.00"));
  }

  @Test
  @DisplayName("dropping(2) skips the first two products")
  void dropping() {
    LimitingBook.Slice slice = LimitingBook.dropping(LimitingBook.taking().source());

    assertThat(names(slice.focused())).containsExactly("Gizmo", "Doohickey", "Thingamajig");
    assertThat(prices(slice.modified()))
        .containsExactly(
            money("10.00"), money("25.00"), money("12.75"), money("25.50"), money("17.00"));
  }

  @Test
  @DisplayName("takingLast(2) focuses the last two products")
  void takingLast() {
    LimitingBook.Slice slice = LimitingBook.takingLast(LimitingBook.taking().source());

    assertThat(names(slice.focused())).containsExactly("Doohickey", "Thingamajig");
    assertThat(prices(slice.modified()))
        .containsExactly(
            money("10.00"), money("25.00"), money("15.00"), money("24.00"), money("16.00"));
  }

  @Test
  @DisplayName("droppingLast(2) focuses everything except the last two")
  void droppingLast() {
    LimitingBook.Slice slice = LimitingBook.droppingLast(LimitingBook.taking().source());

    assertThat(names(slice.focused())).containsExactly("Widget", "Gadget", "Gizmo");
    assertThat(prices(slice.modified()))
        .containsExactly(
            money("9.50"), money("23.75"), money("14.25"), money("30.00"), money("20.00"));
  }

  @Test
  @DisplayName("slicing(1, 4) focuses indices 1, 2 and 3")
  void slicing() {
    LimitingBook.Slice slice = LimitingBook.slicing(LimitingBook.taking().source());

    assertThat(names(slice.focused())).containsExactly("Gadget", "Gizmo", "Doohickey");
    assertThat(prices(slice.modified()))
        .containsExactly(
            money("10.00"), money("22.00"), money("13.20"), money("26.40"), money("20.00"));
  }

  @Test
  @DisplayName("takingWhile stops at the first product that fails, though a later one passes")
  void takingWhile() {
    LimitingBook.Slice slice = LimitingBook.takingWhile();

    assertThat(names(slice.focused())).containsExactly("Widget", "Gadget");
    assertThat(prices(slice.modified()))
        .containsExactly(money("9.00"), money("13.50"), money("25.00"), money("12.00"));
  }

  @Test
  @DisplayName("droppingWhile skips the leading low-stock run, and focuses everything after it")
  void droppingWhile() {
    LimitingBook.Slice slice = LimitingBook.droppingWhile();

    assertThat(names(slice.focused())).containsExactly("Gizmo", "Thing");
    assertThat(slice.modified().stream().map(Product::stock).toList())
        .containsExactly(20, 30, 125, 75);
  }

  @Test
  @DisplayName("droppingWhile on a log skips only the leading [CONFIG] lines")
  void droppingWhileOnALog() {
    assertThat(LimitingBook.runtimeLogs())
        .containsExactly(
            "[CONFIG] Database URL",
            "[CONFIG] Port",
            "INFO: SYSTEM STARTED",
            "ERROR: CONNECTION FAILED")
        .hasToString(
            "[[CONFIG] Database URL, [CONFIG] Port, INFO: SYSTEM STARTED, ERROR: CONNECTION FAILED]");
  }

  @Test
  @DisplayName("element(2) focuses Gizmo alone, and an index past the end focuses nothing")
  void element() {
    LimitingBook.ElementCase element = LimitingBook.element();

    assertThat(names(element.element())).containsExactly("Gizmo");
    assertThat(element.outOfBounds()).isEmpty();
    assertThat(prices(element.updated()))
        .containsExactly(money("10.00"), money("25.00"), money("12.00"));
  }

  @Test
  @DisplayName("an oversized count is clamped, and a negative count or an inverted range is empty")
  void edgeCases() {
    LimitingBook.EdgeCases cases = LimitingBook.edgeCases();

    assertThat(cases.result1()).containsExactly(1, 2, 3);
    assertThat(cases.result2()).isEmpty();
    assertThat(cases.result3()).isEmpty();
    assertThat(cases.result4()).isEmpty();
    assertThat(Traversals.getAll(ListTraversals.dropping(-5), List.of(1, 2, 3)))
        .as("dropping(-5) is dropping(0), which focuses every element")
        .containsExactly(1, 2, 3);
  }
}
