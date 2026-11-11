// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.coyoneda;

import static org.higherkindedj.hkt.coyoneda.CoyonedaKindHelper.COYONEDA;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.coyoneda.Coyoneda;
import org.higherkindedj.hkt.coyoneda.CoyonedaFunctor;
import org.higherkindedj.hkt.coyoneda.CoyonedaKind;
import org.higherkindedj.hkt.effect.FreePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListMonad;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/monads/coyoneda.html">Coyoneda</a> page: a
 * basket's prices mapped three times and walked once, and a warehouse's instruction set that has no
 * {@code Functor}, run as a {@code Free} program through {@code Coyoneda}. The page {@code
 * {{#include}}}s the anchored regions, {@code CoyonedaBookTest} holds the claims the page makes,
 * and the book's output gate runs {@link #main} against the comments in the regions.
 */
public final class CoyonedaBook {

  static final BigDecimal VAT = new BigDecimal("1.20");

  static final List<LineItem> BASKET =
      List.of(
          new LineItem("LAMP", 1, new BigDecimal("40.00")),
          new LineItem("BULB", 4, new BigDecimal("2.50")));

  private CoyonedaBook() {}

  // ANCHOR: stock_op
  // The warehouse's instructions. Each record fixes its own result type and carries no
  // continuation, so there is nothing a map could change: StockOp can have no Functor.
  sealed interface StockOp<A> extends Kind<StockOp.Witness, A> {
    final class Witness implements WitnessArity<TypeArity.Unary> {
      private Witness() {}
    }

    // How many of a SKU the warehouse holds
    record Available(String sku) implements StockOp<Integer> {}

    // Sets a quantity aside for an order, if the warehouse holds that many
    record Reserve(String sku, int quantity) implements StockOp<Boolean> {}
  }

  // ANCHOR_END: stock_op

  // ANCHOR: program
  // Coyoneda gives any instruction set a Functor, so Free can hold StockOp's instructions
  static final CoyonedaFunctor<StockOp.Witness> STOCK = CoyonedaFunctor.instance();

  static <A> FreePath<CoyonedaKind.Witness<StockOp.Witness>, A> step(StockOp<A> op) {
    return Path.freeLift(COYONEDA.lift(op), STOCK);
  }

  // Reserves a line if the warehouse holds enough of its SKU: the second step needs the count
  static FreePath<CoyonedaKind.Witness<StockOp.Witness>, Boolean> reserveLine(LineItem line) {
    return step(new StockOp.Available(line.sku()))
        .via(
            held ->
                held >= line.quantity()
                    ? step(new StockOp.Reserve(line.sku(), line.quantity()))
                    : Path.freePure(false, STOCK));
  }

  // ANCHOR_END: program

  // ANCHOR: warehouse
  // Runs each instruction against stock held in memory
  static final class Warehouse implements Natural<StockOp.Witness, IdKind.Witness> {
    final Map<String, Integer> levels = new HashMap<>(Map.of("LAMP", 3, "BULB", 2));

    @Override
    @SuppressWarnings("unchecked") // each record fixes A; the switch cannot say so
    public <A> Kind<IdKind.Witness, A> apply(Kind<StockOp.Witness, A> fa) {
      Object result =
          switch ((StockOp<A>) fa) {
            case StockOp.Available available -> levels.getOrDefault(available.sku(), 0);
            case StockOp.Reserve reserve -> {
              int held = levels.getOrDefault(reserve.sku(), 0);
              if (held < reserve.quantity()) {
                yield false;
              }
              levels.put(reserve.sku(), held - reserve.quantity());
              yield true;
            }
          };
      return (Kind<IdKind.Witness, A>) ID.widen(Id.of(result));
    }
  }

  // ANCHOR_END: warehouse

  public static void main(String[] args) {
    // ANCHOR: fusion
    // Three maps, held as one: nothing is applied yet
    Coyoneda<ListKind.Witness, BigDecimal> withVat =
        Coyoneda.lift(LIST.widen(BASKET))
            .map(LineItem::price)
            .map(price -> price.multiply(VAT))
            .map(price -> price.setScale(2, RoundingMode.HALF_EVEN));

    // Lowering applies all three to each line, in one walk over the list, with List's Functor
    // (ListMonad.INSTANCE is List's Monad, and so its Functor too)
    List<BigDecimal> prices = LIST.narrow(withVat.lower(ListMonad.INSTANCE));
    // [48.00, 3.00]
    // ANCHOR_END: fusion
    System.out.println(prices);

    // ANCHOR: run
    Warehouse warehouse = new Warehouse();
    IdMonad id = IdMonad.instance();

    // Coyoneda.interpreter runs each instruction with the warehouse, then applies its held maps
    boolean lampReserved =
        ID.narrow(
                reserveLine(BASKET.get(0))
                    .foldMap(Coyoneda.interpreter(warehouse, id), id)
                    .runKind())
            .value();
    // true

    boolean bulbsReserved =
        ID.narrow(
                reserveLine(BASKET.get(1))
                    .foldMap(Coyoneda.interpreter(warehouse, id), id)
                    .runKind())
            .value();
    // false
    // ANCHOR_END: run
    System.out.println(lampReserved);
    System.out.println(bulbsReserved);

    // ANCHOR: one_value
    // One held value runs through an interpreter with foldMap, and needs no Functor for StockOp
    Coyoneda<StockOp.Witness, Boolean> enoughForTwo =
        Coyoneda.lift(new StockOp.Available("LAMP")).map(count -> count >= 2);

    boolean enough = ID.narrow(enoughForTwo.foldMap(new Warehouse(), id)).value();
    // true
    // ANCHOR_END: one_value
    System.out.println(enough);

    // ANCHOR: kind
    CoyonedaFunctor<ListKind.Witness> functor = CoyonedaFunctor.instance();

    // A Coyoneda is already its own Kind, so widen and narrow are casts
    Kind<CoyonedaKind.Witness<ListKind.Witness>, Integer> quantities =
        COYONEDA.widen(Coyoneda.lift(LIST.widen(BASKET)).map(LineItem::quantity));
    Kind<CoyonedaKind.Witness<ListKind.Witness>, Integer> doubled =
        functor.map(quantity -> quantity * 2, quantities);

    List<Integer> lowered = LIST.narrow(COYONEDA.narrow(doubled).lower(ListMonad.INSTANCE));
    // [2, 8]
    // ANCHOR_END: kind
    System.out.println(lowered);
  }
}
