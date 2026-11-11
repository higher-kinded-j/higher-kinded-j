// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.free;

import static org.higherkindedj.hkt.free.FreeKindHelper.FREE;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.writer.WriterKindHelper.WRITER;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.higherkindedj.example.book.foundations.AuditEntry;
import org.higherkindedj.example.book.foundations.OrderOp;
import org.higherkindedj.example.book.foundations.OrderOpFunctor;
import org.higherkindedj.example.book.foundations.OrderOpInterpreter;
import org.higherkindedj.example.book.foundations.OrderOpKind;
import org.higherkindedj.example.book.foundations.OrderOpOps;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.FreePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.free.FreeFactory;
import org.higherkindedj.hkt.free.FreeKind;
import org.higherkindedj.hkt.free.FreeMonad;
import org.higherkindedj.hkt.free.ProgramAnalyser;
import org.higherkindedj.hkt.free.ProgramAnalysis;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.writer.Writer;
import org.higherkindedj.hkt.writer.WriterKind;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/monads/free_monad.html">Free</a> page: an order
 * program built from the {@link OrderOp} instructions, which cancels an order whose line costs too
 * much; one interpreter that runs it against an in-memory store and one that audits it into a
 * {@code Writer}; what can be seen of the program before it runs; and the program as a {@code Free}
 * over {@code Kind}. The page {@code {{#include}}}s the anchored regions, {@code FreeBookTest}
 * holds the claims the page makes, and the book's output gate runs {@link #main} against the
 * comments in the regions.
 */
public final class FreeBook {

  static final UUID ORDER = UUID.fromString("00000000-0000-0000-0000-000000000042");
  static final BigDecimal LIMIT = new BigDecimal("30.00");
  static final Map<String, BigDecimal> CATALOGUE =
      Map.of("LAMP", new BigDecimal("40.00"), "BULB", new BigDecimal("2.50"));

  private FreeBook() {}

  // ANCHOR: steps
  static final Functor<OrderOpKind.Witness> ORDER_OPS = OrderOpFunctor.instance();

  // Each step is one instruction, held as data: nothing is priced or cancelled yet.
  // Function.identity() is the continuation k: the step's result is the instruction's own.
  static FreePath<OrderOpKind.Witness, BigDecimal> priceOf(String sku) {
    return Path.free(OrderOpOps.priceOf(sku, Function.identity()), ORDER_OPS);
  }

  static FreePath<OrderOpKind.Witness, Unit> cancel(UUID orderId) {
    return Path.free(OrderOpOps.cancel(orderId, Function.identity()), ORDER_OPS);
  }

  // ANCHOR_END: steps

  // ANCHOR: program
  // What the program decided about a line
  record Decision(String sku, BigDecimal price, boolean cancelled) {}

  // Cancels the order if the line costs more than the limit: the second step needs the price
  static FreePath<OrderOpKind.Witness, Decision> cancelIfOver(
      UUID orderId, String sku, BigDecimal limit) {
    return priceOf(sku)
        .via(
            price ->
                price.compareTo(limit) > 0
                    ? cancel(orderId).map(_ -> new Decision(sku, price, true))
                    : Path.freePure(new Decision(sku, price, false), ORDER_OPS));
  }

  // ANCHOR_END: program

  // ANCHOR: in_memory
  // Runs each instruction against a catalogue and a list of cancelled orders
  static final class InMemoryOrders extends OrderOpInterpreter<IdKind.Witness> {
    final List<UUID> cancelled = new ArrayList<>();

    @Override
    protected <A> Kind<IdKind.Witness, A> handlePriceOf(OrderOp.PriceOf<A> op) {
      return ID.widen(Id.of(op.k().apply(CATALOGUE.get(op.sku()))));
    }

    @Override
    protected <A> Kind<IdKind.Witness, A> handleCancel(OrderOp.Cancel<A> op) {
      cancelled.add(op.orderId());
      return ID.widen(Id.of(op.k().apply(Unit.INSTANCE)));
    }
  }

  // ANCHOR_END: in_memory

  // ANCHOR: audited
  // Keeps an audit entry for each instruction in a Writer's log, and cancels nothing
  static final class AuditedOrders
      extends OrderOpInterpreter<WriterKind.Witness<List<AuditEntry>>> {
    private final UUID orderId;

    AuditedOrders(UUID orderId) {
      this.orderId = orderId;
    }

    @Override
    protected <A> Kind<WriterKind.Witness<List<AuditEntry>>, A> handlePriceOf(
        OrderOp.PriceOf<A> op) {
      BigDecimal price = CATALOGUE.get(op.sku());
      AuditEntry entry = new AuditEntry(orderId, "priced " + op.sku() + " at " + price);
      return WRITER.widen(Writer.of(List.of(entry), op.k().apply(price)));
    }

    @Override
    protected <A> Kind<WriterKind.Witness<List<AuditEntry>>, A> handleCancel(OrderOp.Cancel<A> op) {
      AuditEntry entry = new AuditEntry(op.orderId(), "would cancel");
      return WRITER.widen(Writer.of(List.of(entry), op.k().apply(Unit.INSTANCE)));
    }
  }

  // ANCHOR_END: audited

  public static void main(String[] args) {
    // ANCHOR: run
    InMemoryOrders store = new InMemoryOrders();

    Kind<IdKind.Witness, Decision> ran =
        cancelIfOver(ORDER, "LAMP", LIMIT).foldMap(store, Instances.monad(id())).runKind();

    Decision lamp = ID.narrow(ran).value();
    // Decision[sku=LAMP, price=40.00, cancelled=true]

    boolean cancelled = store.cancelled.contains(ORDER);
    // true
    // ANCHOR_END: run
    System.out.println(lamp);
    System.out.println(cancelled);

    // ANCHOR: audit_run
    Writer<List<AuditEntry>, Decision> audited =
        WRITER.narrow(
            cancelIfOver(ORDER, "LAMP", LIMIT)
                .foldMap(new AuditedOrders(ORDER), Instances.writer(Monoids.list()))
                .runKind());

    List<String> events = audited.log().stream().map(AuditEntry::event).toList();
    // [priced LAMP at 40.00, would cancel]
    // ANCHOR_END: audit_run
    System.out.println(events);

    // ANCHOR: inspect
    ProgramAnalysis analysis = ProgramAnalyser.analyse(cancelIfOver(ORDER, "LAMP", LIMIT).toFree());

    // How many instructions the analysis can see, and whether a function hides any more
    int seen = analysis.suspendCount();
    // 1
    boolean hidden = analysis.hasOpaqueRegions();
    // true
    // ANCHOR_END: inspect
    System.out.println(seen);
    System.out.println(hidden);

    // ANCHOR: kind
    FreeMonad<OrderOpKind.Witness> frees = new FreeMonad<>();

    // A Free is already its own Kind, so widen and narrow are casts
    Kind<FreeKind.Witness<OrderOpKind.Witness>, BigDecimal> lampPrice =
        FREE.widen(OrderOpOps.priceOf("LAMP", Function.identity()));
    Kind<FreeKind.Witness<OrderOpKind.Witness>, BigDecimal> withDelivery =
        frees.map(price -> price.add(new BigDecimal("4.99")), lampPrice);

    BigDecimal charged =
        ID.narrow(FREE.narrow(withDelivery).foldMap(new InMemoryOrders(), Instances.monad(id())))
            .value();
    // 44.99
    // ANCHOR_END: kind
    System.out.println(charged);

    // ANCHOR: factory
    // A factory fixes the instruction type once, so Java can infer it along the chain
    FreeFactory<OrderOpKind.Witness> orders = FreeFactory.of();

    Free<OrderOpKind.Witness, BigDecimal> fee =
        orders.pure(new BigDecimal("40.00")).map(price -> price.multiply(new BigDecimal("0.10")));
    // ANCHOR_END: factory
    System.out.println(ID.narrow(fee.foldMap(new InMemoryOrders(), Instances.monad(id()))).value());
  }
}
