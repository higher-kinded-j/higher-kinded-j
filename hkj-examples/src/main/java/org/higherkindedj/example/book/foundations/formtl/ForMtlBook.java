// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.formtl;

import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.reader_t.ReaderTKindHelper.READER_T;
import static org.higherkindedj.hkt.state_t.StateTKindHelper.STATE_T;
import static org.higherkindedj.hkt.writer_t.WriterTKindHelper.WRITER_T;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.higherkindedj.example.book.foundations.AuditEntry;
import org.higherkindedj.example.book.foundations.Basket;
import org.higherkindedj.example.book.foundations.PricingConfig;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadReader;
import org.higherkindedj.hkt.MonadState;
import org.higherkindedj.hkt.MonadWriter;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Pair;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.reader_t.ReaderTMonadReader;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.state_t.StateTMonadState;
import org.higherkindedj.hkt.writer_t.WriterTMonad;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/functional/for_mtl.html">For with MTL
 * Capabilities</a> page: one method each for reading pricing settings, adding to a basket and
 * writing an audit log, each written for any container with the capability, then run over {@code
 * ReaderT}, {@code StateT} and {@code WriterT} on {@code Id}. The page {@code {{#include}}}s the
 * anchored regions, {@code ForMtlBookTest} holds the claims the page makes, and the book's output
 * gate runs {@link #main} against the comments in the regions.
 */
public final class ForMtlBook {

  private ForMtlBook() {}

  static final LineItem LAMP = new LineItem("LAMP", 1, new BigDecimal("40.00"));
  static final LineItem BULBS = new LineItem("BULB", 4, new BigDecimal("2.50"));

  static final Order ORDER =
      new Order(
          UUID.fromString("00000000-0000-0000-0000-000000000001"),
          new Customer("Ada", new EmailAddress("ada@example.com")),
          List.of(LAMP, BULBS),
          Instant.parse("2026-10-01T09:00:00Z"),
          Currency.getInstance("GBP"),
          OrderStatus.PAID);

  // ANCHOR: reader
  // What the customer pays: delivery is added below the threshold, then VAT on the whole
  public static <F extends WitnessArity<TypeArity.Unary>> Kind<F, BigDecimal> toPay(
      MonadReader<F, PricingConfig> pricing, BigDecimal net) {
    return For.from(pricing, pricing.ask())
        .let(
            config ->
                net.compareTo(config.freeDeliveryFrom()) >= 0
                    ? BigDecimal.ZERO
                    : config.deliveryCharge())
        .yield(
            (config, delivery) ->
                net.add(delivery)
                    .multiply(BigDecimal.ONE.add(config.vatRate()))
                    .setScale(2, RoundingMode.HALF_UP));
  }

  // ANCHOR_END: reader

  // ANCHOR: state
  // Each step sees the basket the step before it left
  public static <F extends WitnessArity<TypeArity.Unary>> Kind<F, BigDecimal> addBoth(
      MonadState<F, Basket> basket, LineItem first, LineItem second) {
    return For.from(basket, basket.modify(chosen -> chosen.add(first)))
        .from(_ -> basket.modify(chosen -> chosen.add(second)))
        .from(_ -> basket.gets(Basket::total))
        .yield((_, _, total) -> total);
  }

  // ANCHOR_END: state

  // ANCHOR: writer
  // Each tell adds to the log, and the log's Monoid joins the entries
  public static <F extends WitnessArity<TypeArity.Unary>> Kind<F, OrderStatus> ship(
      MonadWriter<F, List<AuditEntry>> audit, Order order) {
    return For.from(audit, audit.tell(List.of(new AuditEntry(order.id(), "packed"))))
        .from(_ -> audit.tell(List.of(new AuditEntry(order.id(), "shipped"))))
        .yield((_, _) -> OrderStatus.SHIPPED);
  }

  // ANCHOR_END: writer

  public static void main(String[] args) {
    // ANCHOR: run_reader
    Monad<IdKind.Witness> idMonad = Instances.monad(id());
    PricingConfig config =
        new PricingConfig(new BigDecimal("0.20"), new BigDecimal("4.95"), new BigDecimal("50.00"));
    ReaderTMonadReader<IdKind.Witness, PricingConfig> pricing = new ReaderTMonadReader<>(idMonad);

    BigDecimal small =
        ID.unwrap(READER_T.narrow(toPay(pricing, new BigDecimal("10.00"))).run().apply(config));
    // 17.94

    BigDecimal large =
        ID.unwrap(READER_T.narrow(toPay(pricing, new BigDecimal("60.00"))).run().apply(config));
    // 72.00
    // ANCHOR_END: run_reader

    // ANCHOR: run_state
    StateTMonadState<Basket, IdKind.Witness> basket = new StateTMonadState<>(idMonad);

    StateTuple<Basket, BigDecimal> after =
        ID.unwrap(STATE_T.narrow(addBoth(basket, LAMP, BULBS)).runStateT(Basket.EMPTY));
    BigDecimal total = after.value();
    // 50.00
    int lines = after.state().lines().size();
    // 2
    // ANCHOR_END: run_state

    // ANCHOR: run_writer
    WriterTMonad<IdKind.Witness, List<AuditEntry>> audit =
        new WriterTMonad<>(idMonad, Monoids.list());

    Pair<OrderStatus, List<AuditEntry>> shipped =
        ID.unwrap(WRITER_T.narrow(ship(audit, ORDER)).run());
    List<String> events = shipped.second().stream().map(AuditEntry::event).toList();
    // [packed, shipped]
    // ANCHOR_END: run_writer

    System.out.println(small);
    System.out.println(large);
    System.out.println(total);
    System.out.println(lines);
    System.out.println(events);
  }
}
