// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.comingfrom;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.higherkindedj.example.book.optics.cast.Address;
import org.higherkindedj.example.book.optics.cast.ConsignmentState;
import org.higherkindedj.example.book.optics.cast.ConsignmentStatePrisms;
import org.higherkindedj.example.book.optics.cast.CustomerProfile;
import org.higherkindedj.example.book.optics.cast.CustomerProfileFocus;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.EmailAddressFocus;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.each.EachInstances;
import org.higherkindedj.optics.fluent.OpticOps;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.util.ListTraversals;
import org.higherkindedj.optics.util.Prisms;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the Coming from Lombok, Streams and Switch page makes. */
@DisplayName("Coming from Lombok, Streams and Switch: each Java habit beside its optic")
class FromJavaBookTest {

  private static final LineItem LAMP = new LineItem("LAMP", 1, new BigDecimal("40.00"));

  private static final LineItem BULB = new LineItem("BULB", 4, new BigDecimal("2.50"));

  /** Ada's order of a lamp and four bulbs, her email as a client might send it. */
  private static final Order ORDER =
      new Order(
          UUID.fromString("00000000-0000-0000-0000-000000000001"),
          new Customer("Ada", new EmailAddress("  Ada@Example.COM ")),
          List.of(LAMP, BULB),
          Instant.parse("2026-10-01T09:00:00Z"),
          Currency.getInstance("GBP"),
          OrderStatus.NEW);

  private static final Address HOME = new Address("1 Long Street", "London", "n1 1aa");

  private static Consignment consignment(ConsignmentState state) {
    return new Consignment(ORDER.id(), HOME, state);
  }

  @Nested
  @DisplayName("From Lombok and withers")
  class FromLombok {

    @Test
    @DisplayName("the wither cascade and the path make the same change, three records deep")
    void cascadeMatchesThePath() {
      Order byWithers = FromJavaBook.normaliseEmailByWithers(ORDER);
      Order byFocus = FromJavaBook.normaliseEmailByFocus(ORDER);

      assertThat(byFocus).isEqualTo(byWithers);
      assertThat(byFocus.customer().email().value()).isEqualTo("ada@example.com");
      assertThat(byFocus.lines()).isSameAs(ORDER.lines());
    }

    @Test
    @DisplayName("one level deep, the path and the lens set the field a wither would")
    void oneLevelDeep() {
      assertThat(OrderFocus.status().set(OrderStatus.PAID, ORDER).status())
          .isEqualTo(OrderStatus.PAID);
      assertThat(OrderLenses.status().set(OrderStatus.PAID, ORDER))
          .isEqualTo(OrderFocus.status().set(OrderStatus.PAID, ORDER));
    }
  }

  @Nested
  @DisplayName("From streams")
  class FromStreams {

    private final TraversalPath<Order, Integer> quantities =
        OrderFocus.lines().via(LineItemFocus.quantity());

    @Test
    @DisplayName("the conditional map and the filtered path discount only the bulk line")
    void filteredPathMatchesTheStream() {
      Order byStream = FromJavaBook.discountBulkByStream(ORDER);
      Order byFocus = FromJavaBook.discountBulkByFocus(ORDER);

      assertThat(byFocus).isEqualTo(byStream);
      assertThat(byFocus.lines())
          .extracting(LineItem::price)
          .containsExactly(new BigDecimal("40.00"), new BigDecimal("2.250"));
    }

    @Test
    @DisplayName("a stream's filter drops the lines it rejects; a filtered path keeps them")
    void filterDropsWhereThePathKeeps() {
      List<LineItem> filtered = ORDER.lines().stream().filter(l -> l.quantity() >= 4).toList();
      assertThat(filtered).containsExactly(BULB);

      Order byFocus = FromJavaBook.discountBulkByFocus(ORDER);
      assertThat(byFocus.lines()).hasSize(2);
      assertThat(byFocus.lines().getFirst()).isEqualTo(LAMP);
    }

    @Test
    @DisplayName("getAll reads what stream().map(f).toList() reads")
    void getAllReadsLikeAStream() {
      assertThat(quantities.getAll(ORDER))
          .isEqualTo(ORDER.lines().stream().map(LineItem::quantity).toList());
    }

    @Test
    @DisplayName("foldMap, exists and count answer what reduce, anyMatch and count answer")
    void foldsAnswerLikeAStream() {
      assertThat(quantities.foldMap(Monoids.integerAddition(), q -> q, ORDER))
          .isEqualTo(ORDER.lines().stream().map(LineItem::quantity).reduce(0, Integer::sum))
          .isEqualTo(5);
      assertThat(quantities.exists(q -> q >= 4, ORDER))
          .isEqualTo(ORDER.lines().stream().anyMatch(l -> l.quantity() >= 4))
          .isTrue();
      assertThat(quantities.count(ORDER)).isEqualTo((int) ORDER.lines().stream().count());
    }

    @Test
    @DisplayName("exists stops at the first match, as a loop would")
    void existsStopsAtTheFirstMatch() {
      AtomicInteger visits = new AtomicInteger();
      Lens<LineItem, Integer> countingQuantity =
          Lens.of(
              line -> {
                visits.incrementAndGet();
                return line.quantity();
              },
              (line, quantity) -> new LineItem(line.sku(), quantity, line.price()));

      boolean any = OrderFocus.lines().via(countingQuantity).exists(q -> q >= 1, ORDER);

      assertThat(any).isTrue();
      assertThat(visits).hasValue(1);
    }

    @Test
    @DisplayName("Collectors.toMap loses a LinkedHashMap's order; the map's path keeps it")
    void mapValuesKeepTheSourceOrder() {
      Map<String, BigDecimal> prices = new LinkedHashMap<>();
      prices.put("LAMP", new BigDecimal("40.00"));
      prices.put("BULB", new BigDecimal("2.50"));
      prices.put("SOFA", new BigDecimal("400.00"));
      Catalogue catalogue = new Catalogue("Autumn", prices);

      Map<String, BigDecimal> byStream =
          catalogue.prices().entrySet().stream()
              .collect(
                  Collectors.toMap(
                      Map.Entry::getKey, e -> e.getValue().multiply(new BigDecimal("0.9"))));
      Catalogue byFocus =
          CatalogueFocus.prices()
              .each(EachInstances.mapValuesEach())
              .modifyAll(price -> price.multiply(new BigDecimal("0.9")), catalogue);

      assertThat(byFocus.prices()).isEqualTo(byStream);
      assertThat(byFocus.prices().keySet()).containsExactly("LAMP", "BULB", "SOFA");
    }

    @Test
    @DisplayName(
        "a limited traversal changes the first line and keeps the rest, where limit drops it")
    void takingKeepsTheRest() {
      Order byFocus =
          FocusPath.of(OrderLenses.lines())
              .via(ListTraversals.<LineItem>taking(1))
              .via(LineItemFocus.price())
              .modifyAll(price -> price.multiply(new BigDecimal("0.9")), ORDER);

      assertThat(byFocus.lines())
          .extracting(LineItem::price)
          .containsExactly(new BigDecimal("36.000"), new BigDecimal("2.50"));
      assertThat(ORDER.lines().stream().limit(1).toList()).containsExactly(LAMP);
    }

    @Test
    @DisplayName("modifyAllValidated reports every bad value, and writes nothing then")
    void validatesEveryElement() {
      TraversalPath<Order, BigDecimal> prices = OrderFocus.lines().via(LineItemFocus.price());
      Order bad =
          new Order(
              ORDER.id(),
              ORDER.customer(),
              List.of(
                  new LineItem("LAMP", 1, new BigDecimal("-1.00")),
                  BULB,
                  new LineItem("SOFA", 1, new BigDecimal("-3.00"))),
              ORDER.placedAt(),
              ORDER.currency(),
              ORDER.status());

      Validated<List<String>, Order> checked =
          OpticOps.modifyAllValidated(bad, prices.toTraversal(), FromJavaBookTest::checkPrice);

      assertThat(checked)
          .isEqualTo(Validated.invalid(List.of("negative: -1.00", "negative: -3.00")));
      assertThat(
              OpticOps.modifyAllValidated(
                  ORDER, prices.toTraversal(), FromJavaBookTest::checkPrice))
          .isEqualTo(Validated.valid(ORDER));
    }
  }

  @Nested
  @DisplayName("From switch and instanceof")
  class FromSwitch {

    @Test
    @DisplayName("the sealed switch and the prism path tidy a returned consignment alike")
    void switchMatchesThePrism() {
      Consignment returned = consignment(new ConsignmentState.Returned("  damaged "));

      Consignment bySwitch = FromJavaBook.tidyReasonBySwitch(returned);
      Consignment byPrism = FromJavaBook.tidyReasonByPrism(returned);

      assertThat(byPrism).isEqualTo(bySwitch);
      assertThat(byPrism.state()).isEqualTo(new ConsignmentState.Returned("damaged"));
    }

    @Test
    @DisplayName("both pass any other state through untouched")
    void otherStatesPassThrough() {
      Consignment pending = consignment(new ConsignmentState.Pending());

      assertThat(FromJavaBook.tidyReasonBySwitch(pending)).isSameAs(pending);
      assertThat(FromJavaBook.tidyReasonByPrism(pending)).isSameAs(pending);
    }

    @Test
    @DisplayName(
        "moving a state is a check through the state's prism, then a write through its path")
    void movingAStateIsACheckThenAWrite() {
      Instant at = Instant.parse("2026-10-02T08:00:00Z");
      Consignment pending = consignment(new ConsignmentState.Pending());
      Consignment returned = consignment(new ConsignmentState.Returned("damaged"));
      AffinePath<Consignment, ConsignmentState.Pending> isPending =
          ConsignmentFocus.state().via(ConsignmentStatePrisms.pending());

      assertThat(isPending.matches(pending)).isTrue();
      assertThat(isPending.matches(returned)).isFalse();
      assertThat(ConsignmentFocus.state().set(new ConsignmentState.Dispatched(at), pending).state())
          .isEqualTo(new ConsignmentState.Dispatched(at));
    }

    @Test
    @DisplayName("a prism is the instanceof test, and build widens a variant or wraps a value")
    void prismIsThePatternAndTheConstructor() {
      ConsignmentState.Returned damaged = new ConsignmentState.Returned("damaged");
      ConsignmentState pending = new ConsignmentState.Pending();

      assertThat(ConsignmentStatePrisms.returned().getOptional(damaged)).contains(damaged);
      assertThat(ConsignmentStatePrisms.returned().getOptional(pending)).isEmpty();
      assertThat(ConsignmentStatePrisms.returned().matches(pending)).isFalse();
      assertThat(ConsignmentStatePrisms.returned().build(damaged)).isSameAs(damaged);
      assertThat(Prisms.<String>some().build("x")).isEqualTo(Optional.of("x"));
    }

    @Test
    @DisplayName("an Optional field's path reads like map, and modify leaves an empty one alone")
    void optionalFieldReadsLikeMap() {
      AffinePath<CustomerProfile, String> altEmail =
          CustomerProfileFocus.altEmail().via(EmailAddressFocus.value());
      CustomerProfile with =
          new CustomerProfile(
              "Ada", Optional.empty(), Optional.of(new EmailAddress(" ada@example.org ")));
      CustomerProfile without = new CustomerProfile("Ada", Optional.empty(), Optional.empty());

      assertThat(altEmail.getOptional(with)).isEqualTo(with.altEmail().map(EmailAddress::value));
      assertThat(altEmail.modify(String::strip, with).altEmail())
          .contains(new EmailAddress("ada@example.org"));
      assertThat(altEmail.modify(String::strip, without)).isEqualTo(without);
    }
  }

  private static Validated<String, BigDecimal> checkPrice(BigDecimal price) {
    return price.signum() < 0 ? Validated.invalid("negative: " + price) : Validated.valid(price);
  }
}
