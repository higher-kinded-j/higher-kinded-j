// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.fluent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.ADA;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.BULB;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.LAMP;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.ORDER;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.line;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.CustomerFocus;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemFocus;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderFocus;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.fluent.OpticOps;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Updates That Can Fail page makes about its examples. */
@DisplayName("the Updates That Can Fail page")
class FluentBookTest {

  private static final Order BAD_PRICES =
      order(List.of(line("LAMP", "-10.00"), line("BULB", "25.00"), line("SOFA", "15000.00")));
  private static final Order GOOD_PRICES = order(List.of(line("LAMP", "25.00")));

  @Test
  @DisplayName("every bad price is reported, in order, and a good order comes back unchanged")
  void everyError() {
    FluentBook.EveryError bad = FluentBook.everyError(BAD_PRICES);

    assertThat(bad.checked())
        .isEqualTo(
            Validated.invalid(
                List.of("Price cannot be negative: -10.00", "Price exceeds maximum: 15000.00")));
    assertThat(bad.report())
        .isEqualTo(
            "2 invalid prices: Price cannot be negative: -10.00; Price exceeds maximum: 15000.00");
    assertThat(FluentBook.everyError(GOOD_PRICES).checked())
        .isEqualTo(Validated.valid(GOOD_PRICES));
  }

  @Test
  @DisplayName("the hand-written loop finds the same two errors as modifyAllValidated")
  void byHand() {
    assertThat(FluentBook.priceErrorsByHand(BAD_PRICES))
        .containsExactly("Price cannot be negative: -10.00", "Price exceeds maximum: 15000.00");
    assertThat(FluentBook.priceErrorsByHand(GOOD_PRICES)).isEmpty();
  }

  @Test
  @DisplayName("modifyEither accepts a good email and rejects a bad one with its reason")
  void failFast() {
    assertThat(FluentBook.failFast(ADA).message()).isEqualTo("accepted: ada@example.com");
    assertThat(
            FluentBook.failFast(new Customer("Bob", new EmailAddress("bob.example.com"))).result())
        .isEqualTo(Either.left("Invalid email: bob.example.com"));
  }

  @Test
  @DisplayName("modifyMaybe trims a good name, and gives Nothing for one of the wrong length")
  void noDetail() {
    Customer padded = new Customer("  Ada  ", ADA.email());
    Customer tooShort = new Customer("A", ADA.email());

    assertThat(FluentBook.noDetail(padded).normalised()).isEqualTo(Maybe.just(ADA));
    assertThat(FluentBook.noDetail(tooShort).normalised()).isEqualTo(Maybe.nothing());
    assertThat(FluentBook.noDetail(tooShort).safe()).isEqualTo(tooShort);
  }

  @Test
  @DisplayName("modifyAllEither keeps the first error, though it validates every price")
  void firstError() {
    List<BigDecimal> seen = new ArrayList<>();
    Traversal<Order, BigDecimal> prices =
        OrderFocus.lines().via(LineItemFocus.price()).toTraversal();

    Either<String, Order> firstFailure =
        OpticOps.modifyAllEither(
            BAD_PRICES,
            prices,
            price -> {
              seen.add(price);
              return FluentBook.checkPriceEither(price);
            });

    assertThat(FluentBook.firstError(BAD_PRICES))
        .isEqualTo(Either.left("Price cannot be negative: -10.00"));
    assertThat(firstFailure).isEqualTo(Either.left("Price cannot be negative: -10.00"));
    assertThat(seen)
        .containsExactly(
            new BigDecimal("-10.00"), new BigDecimal("25.00"), new BigDecimal("15000.00"));
  }

  @Test
  @DisplayName("registration checks the email, then the name, and stops at the first failure")
  void sequential() {
    assertThat(FluentBook.register(ADA)).isEqualTo(Either.right(ADA));
    assertThat(FluentBook.register(new Customer("A", ADA.email())))
        .isEqualTo(Either.left("Name must be at least 2 characters"));
    assertThat(FluentBook.register(new Customer("A", new EmailAddress("a.example.com"))))
        .isEqualTo(Either.left("Invalid email: a.example.com"));
  }

  @Test
  @DisplayName("the builders give the same results as the static methods")
  void builders() {
    FluentBook.Builders builders = FluentBook.builders(ADA, BAD_PRICES);

    assertThat(builders.email()).isEqualTo(FluentBook.failFast(ADA).result());
    assertThat(builders.prices()).isEqualTo(FluentBook.everyError(BAD_PRICES).checked());
  }

  @Test
  @DisplayName("modifyF on a path fetches every current price inside a CompletableFuture")
  void modifyF() {
    assertThat(FluentBook.repriced(ORDER).join().lines())
        .extracting(LineItem::price)
        .containsExactly(new BigDecimal("41.00"), new BigDecimal("3.50"));
  }

  @Test
  @DisplayName("OpticOps reads, writes and queries through generated optics")
  void readsAndWrites() {
    FluentBook.ReadsAndWrites result = FluentBook.readsAndWrites(ADA, ORDER);

    assertThat(result.name()).isEqualTo("Ada");
    assertThat(result.quantities()).containsExactly(1, 4);
    assertThat(result.firstQuantity()).contains(1);
    assertThat(result.paid().status()).isEqualTo(OrderStatus.PAID);
    assertThat(result.doubled().lines()).extracting(LineItem::quantity).containsExactly(2, 8);
    assertThat(result.anyBulk()).isTrue();
    assertThat(result.allOrdered()).isTrue();
    assertThat(result.lineCount()).isEqualTo(2);
    assertThat(result.noLines()).isFalse();
    assertThat(result.overTen()).contains(LAMP);
  }

  @Test
  @DisplayName("the static and builder styles compute the same values")
  void styles() {
    FluentBook.Styles styles = FluentBook.styles(LAMP);

    assertThat(styles.quantity()).isEqualTo(styles.sameQuantity()).isEqualTo(1);
    assertThat(styles.more()).isEqualTo(styles.alsoMore());
    assertThat(styles.more().quantity()).isEqualTo(2);
  }

  @Test
  @DisplayName("the four builders read, set, modify and query every quantity")
  void builderVerbs() {
    FluentBook.Verbs verbs = FluentBook.builderVerbs(ORDER);

    assertThat(verbs.all()).containsExactly(1, 4);
    assertThat(verbs.reset().lines()).extracting(LineItem::quantity).containsExactly(1, 1);
    assertThat(verbs.bumped().lines()).extracting(LineItem::quantity).containsExactly(2, 5);
    assertThat(verbs.any()).isTrue();
  }

  @Test
  @DisplayName("a new order is stamped, and a paid one comes back as it was")
  void conditional() {
    Instant now = Instant.parse("2026-10-07T12:00:00Z");
    Order paid =
        new Order(
            ORDER.id(), ADA, ORDER.lines(), ORDER.placedAt(), ORDER.currency(), OrderStatus.PAID);

    assertThat(FluentBook.stampIfNew(ORDER, now).placedAt()).isEqualTo(now);
    assertThat(FluentBook.stampIfNew(paid, now)).isEqualTo(paid);
  }

  @Test
  @DisplayName("a filtered traversal discounts only the bulk lines")
  void filtered() {
    FluentBook.BulkDiscount result = FluentBook.discountBulkLines(ORDER);

    assertThat(result.discounted().lines())
        .extracting(LineItem::price)
        .containsExactly(new BigDecimal("40.00"), new BigDecimal("2.250"));
    assertThat(result.bulkLines()).extracting(LineItem::sku).containsExactly("BULB");
  }

  @Test
  @DisplayName("a fold sums the quantities, and a stream picks the dear lines' SKUs")
  void aggregateAndStream() {
    assertThat(FluentBook.totalQuantity(ORDER)).isEqualTo(5);
    assertThat(FluentBook.dearSkus(ORDER)).containsExactly("LAMP");
  }

  @Test
  @DisplayName("an optic composed once before the loop reads every order")
  void composeOnce() {
    Order other = order(List.of(BULB));

    assertThat(FluentBook.composeOnce(List.of(ORDER, other)))
        .containsExactly(List.of(1, 4), List.of(4));
  }

  @Test
  @DisplayName("checkpoint: every bad price, or only the first, for the same order")
  void checkpointEveryOrFirst() {
    Order twoBad =
        order(List.of(line("LAMP", "-1.00"), line("BULB", "5.00"), line("SOFA", "-2.00")));

    assertThat(FluentBook.everyError(twoBad).checked())
        .isEqualTo(
            Validated.invalid(
                List.of("Price cannot be negative: -1.00", "Price cannot be negative: -2.00")));
    assertThat(FluentBook.firstError(twoBad))
        .isEqualTo(Either.left("Price cannot be negative: -1.00"));
  }

  @Test
  @DisplayName("checkpoint: a chain checking the name first stops at the name")
  void checkpointChainStops() {
    Customer b = new Customer("B", new EmailAddress("b.example.com"));

    Either<String, Customer> nameFirst =
        OpticOps.modifyEither(
                b,
                CustomerFocus.name().toLens(),
                name ->
                    name.length() >= 2
                        ? Either.<String, String>right(name)
                        : Either.left("Name must be at least 2 characters"))
            .flatMap(
                checked ->
                    OpticOps.modifyEither(
                        checked, CustomerFocus.email().value().toLens(), FluentBook::checkEmail));

    assertThat(nameFirst).isEqualTo(Either.left("Name must be at least 2 characters"));
  }
}
