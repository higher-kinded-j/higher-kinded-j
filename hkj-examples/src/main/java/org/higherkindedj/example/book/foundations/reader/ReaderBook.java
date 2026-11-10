// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.reader;

import static org.higherkindedj.hkt.instances.Witnesses.reader;
import static org.higherkindedj.hkt.reader.ReaderKindHelper.READER;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.higherkindedj.example.book.foundations.PricingConfig;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.ReaderPath;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.reader.Reader;
import org.higherkindedj.hkt.reader.ReaderKind;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/monads/reader_monad.html">Reader</a> page: what an
 * order costs, read from a {@code PricingConfig} that no method takes as a parameter, the same
 * reader run against two configurations, a {@code ReaderPath} that changes the configuration for
 * one part of a quote, and the instance over {@code Kind}. The page {@code {{#include}}}s the
 * anchored regions, {@code ReaderBookTest} holds the claims the page makes, and the book's output
 * gate runs {@link #main} against the comments in the regions.
 */
public final class ReaderBook {

  /** The usual prices: VAT at 20%, and delivery at 4.95 below 50.00. */
  static final PricingConfig STANDARD =
      new PricingConfig(new BigDecimal("0.20"), new BigDecimal("4.95"), new BigDecimal("50.00"));

  /** A sale, with free delivery from 30.00. */
  static final PricingConfig SALE =
      new PricingConfig(new BigDecimal("0.20"), new BigDecimal("4.95"), new BigDecimal("30.00"));

  /** An order for one lamp, which costs 40.00 before delivery and VAT. */
  static final Order LAMP_ORDER =
      new Order(
          UUID.fromString("00000000-0000-0000-0000-000000000001"),
          new Customer("Ada", new EmailAddress("ada@example.com")),
          List.of(new LineItem("LAMP", 1, new BigDecimal("40.00"))),
          Instant.parse("2026-10-30T15:00:00Z"),
          Currency.getInstance("GBP"),
          OrderStatus.PAID);

  private ReaderBook() {}

  // ANCHOR: pricing
  // What the goods cost, before delivery and VAT
  static BigDecimal net(Order order) {
    return order.lines().stream()
        .map(line -> line.price().multiply(BigDecimal.valueOf(line.quantity())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  // Delivery is charged below the threshold
  static Reader<PricingConfig, BigDecimal> delivery(BigDecimal net) {
    return Reader.of(
        config ->
            net.compareTo(config.freeDeliveryFrom()) >= 0
                ? BigDecimal.ZERO
                : config.deliveryCharge());
  }

  // VAT on the whole
  static Reader<PricingConfig, BigDecimal> withVat(BigDecimal amount) {
    return Reader.of(
        config ->
            amount
                .multiply(BigDecimal.ONE.add(config.vatRate()))
                .setScale(2, RoundingMode.HALF_UP));
  }

  // No method here takes the configuration: each step is given it when the reader runs
  static Reader<PricingConfig, BigDecimal> toPay(Order order) {
    BigDecimal net = net(order);
    return delivery(net).flatMap(charge -> withVat(net.add(charge)));
  }

  // ANCHOR_END: pricing

  // ANCHOR: quote
  // The configuration a member sees: the same, with delivery free on every order
  static PricingConfig forMembers(PricingConfig config) {
    return new PricingConfig(config.vatRate(), config.deliveryCharge(), BigDecimal.ZERO);
  }

  static ReaderPath<PricingConfig, String> quote(Order order) {
    ReaderPath<PricingConfig, BigDecimal> usual = Path.reader(toPay(order));
    ReaderPath<PricingConfig, BigDecimal> members = usual.local(ReaderBook::forMembers);
    return usual.zipWith(members, (pay, memberPays) -> pay + ", or " + memberPays + " for members");
  }

  // ANCHOR_END: quote

  public static void main(String[] args) {
    // ANCHOR: run
    Reader<PricingConfig, BigDecimal> lampToPay = toPay(LAMP_ORDER);

    BigDecimal usually = lampToPay.run(STANDARD);
    // 53.94
    BigDecimal inTheSale = lampToPay.run(SALE);
    // 48.00
    // ANCHOR_END: run
    System.out.println(usually);
    System.out.println(inTheSale);

    // ANCHOR: quote_outcome
    String quoted = quote(LAMP_ORDER).run(STANDARD);
    // ANCHOR_END: quote_outcome
    System.out.println(quoted);

    // ANCHOR: kind
    Monad<ReaderKind.Witness<PricingConfig>> pricing = Instances.monad(reader());

    Kind<ReaderKind.Witness<PricingConfig>, BigDecimal> lamp = READER.widen(toPay(LAMP_ORDER));
    Kind<ReaderKind.Witness<PricingConfig>, String> label =
        pricing.map(total -> "pay " + total, lamp);

    String ran = READER.runReader(label, STANDARD);
    // ANCHOR_END: kind
    System.out.println(ran);
  }
}
