// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.abstraction;

import static org.higherkindedj.example.book.foundations.OrderOpKindHelper.ORDER_OP;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;

import java.math.BigDecimal;
import java.util.Map;
import org.higherkindedj.example.book.foundations.OrderOp;
import org.higherkindedj.example.book.foundations.OrderOpInterpreter;
import org.higherkindedj.example.book.foundations.OrderOpKind;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.free_ap.FreeAp;
import org.higherkindedj.hkt.free_ap.FreeApAnalyzer;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/functional/abstraction_levels.html">Applicative,
 * Selective or Monad?</a> page: a program of independent order instructions, held as a {@link
 * FreeAp} so that it can be inspected before it runs, then run by an interpreter. The page {@code
 * {{#include}}}s the anchored regions, {@code AbstractionBookTest} holds the claims the page makes,
 * and the book's output gate runs {@link #main} against the comments in the regions.
 */
public final class AbstractionBook {

  private AbstractionBook() {}

  // ANCHOR: program
  // One instruction, held as data: nothing is priced until an interpreter runs it
  static FreeAp<OrderOpKind.Witness, BigDecimal> priceOf(String sku) {
    return FreeAp.lift(ORDER_OP.widen(new OrderOp.PriceOf<>(sku, price -> price)));
  }

  // Two prices that do not depend on one another, added together
  static final FreeAp<OrderOpKind.Witness, BigDecimal> LAMP_AND_BULB =
      priceOf("LAMP").map2(priceOf("BULB"), BigDecimal::add);

  // ANCHOR_END: program

  // ANCHOR: interpreter
  // Prices from a catalogue: the interpreter decides what each instruction does
  static class CataloguePrices extends OrderOpInterpreter<IdKind.Witness> {
    private final Map<String, BigDecimal> catalogue =
        Map.of("LAMP", new BigDecimal("40.00"), "BULB", new BigDecimal("2.50"));

    @Override
    protected <A> Kind<IdKind.Witness, A> handlePriceOf(OrderOp.PriceOf<A> op) {
      BigDecimal price = catalogue.get(op.sku());
      if (price == null) {
        throw new IllegalArgumentException("no price for " + op.sku());
      }
      return ID.widen(Id.of(op.k().apply(price)));
    }

    @Override
    protected <A> Kind<IdKind.Witness, A> handleCancel(OrderOp.Cancel<A> op) {
      throw new UnsupportedOperationException("a price list cancels nothing");
    }
  }

  // ANCHOR_END: interpreter

  public static void main(String[] args) {
    // ANCHOR: analyse
    int instructions = FreeApAnalyzer.countOperations(LAMP_AND_BULB);
    // 2

    boolean cancels =
        FreeApAnalyzer.containsOperation(
            LAMP_AND_BULB, op -> ORDER_OP.narrow(op) instanceof OrderOp.Cancel<?>);
    // false
    // ANCHOR_END: analyse
    System.out.println(instructions);
    System.out.println(cancels);

    // ANCHOR: run
    BigDecimal total =
        ID.narrow(LAMP_AND_BULB.foldMap(new CataloguePrices(), IdMonad.instance())).value();
    // 42.50
    // ANCHOR_END: run
    System.out.println(total);
  }
}
