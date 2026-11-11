// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.freeap;

import static org.higherkindedj.example.book.foundations.OrderOpKindHelper.ORDER_OP;
import static org.higherkindedj.hkt.constant.ConstKindHelper.CONST;
import static org.higherkindedj.hkt.free_ap.FreeApKindHelper.FREE_AP;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.higherkindedj.example.book.foundations.OrderOp;
import org.higherkindedj.example.book.foundations.OrderOpFunctor;
import org.higherkindedj.example.book.foundations.OrderOpInterpreter;
import org.higherkindedj.example.book.foundations.OrderOpKind;
import org.higherkindedj.example.book.foundations.OrderOpOps;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.constant.Const;
import org.higherkindedj.hkt.constant.ConstKind;
import org.higherkindedj.hkt.effect.FreeApPath;
import org.higherkindedj.hkt.effect.FreePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.free.ProgramAnalyser;
import org.higherkindedj.hkt.free.ProgramAnalysis;
import org.higherkindedj.hkt.free_ap.FreeAp;
import org.higherkindedj.hkt.free_ap.FreeApAnalyzer;
import org.higherkindedj.hkt.free_ap.FreeApApplicative;
import org.higherkindedj.hkt.free_ap.FreeApKind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/monads/free_applicative.html">Free Applicative</a>
 * page: a basket priced by independent {@link OrderOp} instructions, held as a {@code FreeApPath}
 * so that the whole program can be read before it runs; an interpreter that prices it with one
 * catalogue call, and one that reports every unknown SKU; and the basket as one step of a {@code
 * Free} program. The page {@code {{#include}}}s the anchored regions, {@code
 * FreeApplicativeBookTest} holds the claims the page makes, and the book's output gate runs {@link
 * #main} against the comments in the regions.
 */
public final class FreeApplicativeBook {

  static final UUID ORDER = UUID.fromString("00000000-0000-0000-0000-000000000042");
  static final BigDecimal LIMIT = new BigDecimal("30.00");
  static final Map<String, BigDecimal> CATALOGUE =
      Map.of("LAMP", new BigDecimal("40.00"), "BULB", new BigDecimal("2.50"));

  // The basket as it was filled: the prices are last week's
  static final List<LineItem> BASKET =
      List.of(
          new LineItem("LAMP", 1, new BigDecimal("38.00")),
          new LineItem("BULB", 4, new BigDecimal("2.40")));

  private FreeApplicativeBook() {}

  // ANCHOR: steps
  static final Functor<OrderOpKind.Witness> ORDER_OPS = OrderOpFunctor.instance();

  // One instruction, held as data: nothing is priced until an interpreter runs it
  static FreeApPath<OrderOpKind.Witness, BigDecimal> priceOf(String sku) {
    return Path.freeApLift(
        ORDER_OP.widen(new OrderOp.PriceOf<>(sku, Function.identity())), ORDER_OPS);
  }

  // ANCHOR_END: steps

  // ANCHOR: program
  // A line at today's price: the price times the quantity
  static FreeApPath<OrderOpKind.Witness, BigDecimal> lineTotal(LineItem line) {
    return priceOf(line.sku()).map(price -> price.multiply(BigDecimal.valueOf(line.quantity())));
  }

  // Every line priced again; no line's price waits on another's
  static FreeApPath<OrderOpKind.Witness, BigDecimal> basketTotal(List<LineItem> lines) {
    return lines.stream()
        .map(FreeApplicativeBook::lineTotal)
        .reduce(
            Path.freeApPure(BigDecimal.ZERO, ORDER_OPS), (a, b) -> a.zipWith(b, BigDecimal::add));
  }

  // ANCHOR_END: program

  // ANCHOR: skus
  // An analysis is an interpreter too: each instruction gives the SKU it would price, in a Const
  static final class SkusToPrice extends OrderOpInterpreter<ConstKind.Witness<List<String>>> {
    @Override
    protected <A> Kind<ConstKind.Witness<List<String>>, A> handlePriceOf(OrderOp.PriceOf<A> op) {
      return CONST.widen(new Const<>(List.of(op.sku())));
    }

    @Override
    protected <A> Kind<ConstKind.Witness<List<String>>, A> handleCancel(OrderOp.Cancel<A> op) {
      return CONST.widen(new Const<>(List.of()));
    }
  }

  // ANCHOR_END: skus

  // ANCHOR: catalogue
  // The catalogue service prices many SKUs in one call
  interface Catalogue {
    Map<String, BigDecimal> pricesFor(List<String> skus);
  }

  // Prices each instruction from prices already fetched, and records any cancellation
  static final class FetchedPrices extends OrderOpInterpreter<IdKind.Witness> {
    private final Map<String, BigDecimal> prices;
    final List<UUID> cancelled = new ArrayList<>();

    FetchedPrices(Map<String, BigDecimal> prices) {
      this.prices = prices;
    }

    @Override
    protected <A> Kind<IdKind.Witness, A> handlePriceOf(OrderOp.PriceOf<A> op) {
      return ID.widen(Id.of(op.k().apply(prices.get(op.sku()))));
    }

    @Override
    protected <A> Kind<IdKind.Witness, A> handleCancel(OrderOp.Cancel<A> op) {
      cancelled.add(op.orderId());
      return ID.widen(Id.of(op.k().apply(Unit.INSTANCE)));
    }
  }

  // Reads the SKUs first, asks the catalogue once, then runs the program
  static BigDecimal priceInOneCall(
      FreeApPath<OrderOpKind.Witness, BigDecimal> program, Catalogue catalogue) {
    List<String> skus =
        FreeApAnalyzer.analyseWith(program.toFreeAp(), new SkusToPrice(), Monoids.list());
    FetchedPrices fetched = new FetchedPrices(catalogue.pricesFor(skus));
    return ID.narrow(program.foldMap(fetched, Instances.monad(id())).runKind()).value();
  }

  // ANCHOR_END: catalogue

  // ANCHOR: checked
  // Gives a price, or a failure naming the SKU the catalogue does not know
  static final class CheckedPrices extends OrderOpInterpreter<ValidatedKind.Witness<List<String>>> {
    @Override
    protected <A> Kind<ValidatedKind.Witness<List<String>>, A> handlePriceOf(
        OrderOp.PriceOf<A> op) {
      BigDecimal price = CATALOGUE.get(op.sku());
      return price == null
          ? VALIDATED.widen(Validated.invalid(List.of("no price for " + op.sku())))
          : VALIDATED.widen(Validated.valid(op.k().apply(price)));
    }

    @Override
    protected <A> Kind<ValidatedKind.Witness<List<String>>, A> handleCancel(OrderOp.Cancel<A> op) {
      return VALIDATED.widen(Validated.valid(op.k().apply(Unit.INSTANCE)));
    }
  }

  // ANCHOR_END: checked

  // ANCHOR: inside_free
  // The basket's prices as one step of a Free program; the next step needs the total
  static FreePath<OrderOpKind.Witness, Boolean> cancelIfTotalOver(
      UUID orderId, List<LineItem> lines, BigDecimal limit) {
    FreePath<OrderOpKind.Witness, BigDecimal> total =
        Path.free(new Free.Ap<>(basketTotal(lines).toFreeAp()), ORDER_OPS);
    return total.via(
        sum ->
            sum.compareTo(limit) > 0
                ? Path.free(OrderOpOps.cancel(orderId, Function.identity()), ORDER_OPS)
                    .map(_ -> true)
                : Path.freePure(false, ORDER_OPS));
  }

  // ANCHOR_END: inside_free

  public static void main(String[] args) {
    // ANCHOR: read
    FreeAp<OrderOpKind.Witness, BigDecimal> program = basketTotal(BASKET).toFreeAp();

    int instructions = FreeApAnalyzer.countOperations(program);
    // 2

    List<String> skus = FreeApAnalyzer.analyseWith(program, new SkusToPrice(), Monoids.list());
    // [LAMP, BULB]
    // ANCHOR_END: read
    System.out.println(instructions);
    System.out.println(skus);

    // ANCHOR: one_call
    // A stand-in catalogue that records each call, and prices from CATALOGUE
    List<List<String>> calls = new ArrayList<>();
    Catalogue catalogue =
        requested -> {
          calls.add(requested);
          return CATALOGUE;
        };

    BigDecimal total = priceInOneCall(basketTotal(BASKET), catalogue);
    // 50.00

    int catalogueCalls = calls.size();
    // 1
    // ANCHOR_END: one_call
    System.out.println(total);
    System.out.println(catalogueCalls);

    // ANCHOR: every_unknown
    List<LineItem> withUnknowns =
        List.of(
            new LineItem("RUG", 1, new BigDecimal("120.00")),
            new LineItem("LAMP", 1, new BigDecimal("38.00")),
            new LineItem("VASE", 2, new BigDecimal("15.00")));

    Validated<List<String>, BigDecimal> checked =
        VALIDATED.narrow(
            basketTotal(withUnknowns)
                .foldMap(new CheckedPrices(), Instances.validated(Semigroups.list()))
                .runKind());

    List<String> problems = checked.getError();
    // [no price for RUG, no price for VASE]
    // ANCHOR_END: every_unknown
    System.out.println(problems);

    // ANCHOR: run_inside_free
    FetchedPrices store = new FetchedPrices(CATALOGUE);

    boolean cancelled =
        ID.narrow(
                cancelIfTotalOver(ORDER, BASKET, LIMIT)
                    .foldMap(store, Instances.monad(id()))
                    .runKind())
            .value();
    // true

    ProgramAnalysis analysis =
        ProgramAnalyser.analyse(cancelIfTotalOver(ORDER, BASKET, LIMIT).toFree());

    // ProgramAnalyser counts the basket's FreeAp as one parallel scope, and does not look inside
    int basketSteps = analysis.parallelScopes();
    // 1
    // ANCHOR_END: run_inside_free
    System.out.println(cancelled);
    System.out.println(basketSteps);

    // ANCHOR: kind
    FreeApApplicative<OrderOpKind.Witness> applicative = FreeApApplicative.instance();

    // A FreeAp is already its own Kind, so widen and narrow are casts
    Kind<FreeApKind.Witness<OrderOpKind.Witness>, BigDecimal> lamp =
        FREE_AP.widen(priceOf("LAMP").toFreeAp());
    Kind<FreeApKind.Witness<OrderOpKind.Witness>, BigDecimal> bulb =
        FREE_AP.widen(priceOf("BULB").toFreeAp());

    Kind<FreeApKind.Witness<OrderOpKind.Witness>, BigDecimal> both =
        applicative.map2(lamp, bulb, BigDecimal::add);

    BigDecimal lampAndBulb =
        ID.narrow(FREE_AP.narrow(both).foldMap(new FetchedPrices(CATALOGUE), Instances.monad(id())))
            .value();
    // 42.50
    // ANCHOR_END: kind
    System.out.println(lampAndBulb);
  }
}
