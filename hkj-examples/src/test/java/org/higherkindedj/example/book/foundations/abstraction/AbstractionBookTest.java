// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.abstraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.foundations.OrderOpKindHelper.ORDER_OP;
import static org.higherkindedj.example.book.foundations.abstraction.AbstractionBook.LAMP_AND_BULB;
import static org.higherkindedj.example.book.foundations.abstraction.AbstractionBook.priceOf;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.higherkindedj.example.book.foundations.OrderOp;
import org.higherkindedj.example.book.foundations.OrderOpKind;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.free_ap.FreeAp;
import org.higherkindedj.hkt.free_ap.FreeApAnalyzer;
import org.higherkindedj.hkt.id.IdMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the abstraction page makes about a program held as a FreeAp. */
@DisplayName("Applicative, Selective or Monad?: an applicative program can be read before it runs")
class AbstractionBookTest {

  @Test
  @DisplayName("analysing the program runs no instruction; foldMap runs each one")
  void analysingRunsNothing() {
    List<String> ran = new ArrayList<>();
    FreeAp<OrderOpKind.Witness, BigDecimal> recorded =
        recordedPriceOf("LAMP", ran).map2(recordedPriceOf("BULB", ran), BigDecimal::add);

    assertThat(FreeApAnalyzer.countOperations(recorded)).isEqualTo(2);
    assertThat(FreeApAnalyzer.containsOperation(recorded, op -> true)).isTrue();
    assertThat(ran).isEmpty();

    BigDecimal total =
        ID.narrow(recorded.foldMap(new AbstractionBook.CataloguePrices(), IdMonad.instance()))
            .value();
    assertThat(total).isEqualTo(new BigDecimal("42.50"));
    assertThat(ran).containsExactlyInAnyOrder("LAMP", "BULB");
  }

  private static FreeAp<OrderOpKind.Witness, BigDecimal> recordedPriceOf(
      String sku, List<String> ran) {
    return FreeAp.lift(
        ORDER_OP.widen(
            new OrderOp.PriceOf<>(
                sku,
                price -> {
                  ran.add(sku);
                  return price;
                })));
  }

  @Test
  @DisplayName("the catalogue interpreter refuses a SKU it has no price for")
  void unknownSkuIsRefused() {
    assertThatThrownBy(
            () ->
                priceOf("CHAIR").foldMap(new AbstractionBook.CataloguePrices(), IdMonad.instance()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("no price for CHAIR");
  }

  @Test
  @DisplayName("containsOperation finds a cancellation in a program that has one")
  void findsACancellation() {
    FreeAp<OrderOpKind.Witness, BigDecimal> cancelling =
        priceOf("LAMP")
            .map2(
                FreeAp.lift(
                    ORDER_OP.widen(
                        new OrderOp.Cancel<Unit>(UUID.randomUUID(), unit -> Unit.INSTANCE))),
                (price, unit) -> price);

    assertThat(
            FreeApAnalyzer.containsOperation(
                cancelling, op -> ORDER_OP.narrow(op) instanceof OrderOp.Cancel<?>))
        .isTrue();
    assertThat(
            FreeApAnalyzer.containsOperation(
                LAMP_AND_BULB, op -> ORDER_OP.narrow(op) instanceof OrderOp.Cancel<?>))
        .isFalse();
  }
}
