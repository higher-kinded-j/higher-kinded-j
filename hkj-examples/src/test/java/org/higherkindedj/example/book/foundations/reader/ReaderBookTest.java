// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.foundations.reader.ReaderBook.LAMP_ORDER;
import static org.higherkindedj.example.book.foundations.reader.ReaderBook.SALE;
import static org.higherkindedj.example.book.foundations.reader.ReaderBook.STANDARD;
import static org.higherkindedj.example.book.foundations.reader.ReaderBook.delivery;
import static org.higherkindedj.example.book.foundations.reader.ReaderBook.forMembers;
import static org.higherkindedj.example.book.foundations.reader.ReaderBook.net;
import static org.higherkindedj.example.book.foundations.reader.ReaderBook.quote;
import static org.higherkindedj.example.book.foundations.reader.ReaderBook.toPay;
import static org.higherkindedj.hkt.instances.Witnesses.reader;
import static org.higherkindedj.hkt.reader.ReaderKindHelper.READER;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.higherkindedj.example.book.foundations.PricingConfig;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.ReaderPath;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.reader.Reader;
import org.higherkindedj.hkt.reader.ReaderKind;
import org.higherkindedj.hkt.reader.ReaderSelective;
import org.higherkindedj.optics.Getter;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.FocusPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the Reader page makes about reading, running and adapting an environment. */
@DisplayName("Reader: a computation that reads an environment it is given when it runs")
class ReaderBookTest {

  @Nested
  @DisplayName("pricing from one configuration")
  class Pricing {

    @Test
    @DisplayName("the lamp order costs 53.94 usually and 48.00 in the sale")
    void oneReaderTwoConfigurations() {
      Reader<PricingConfig, BigDecimal> lampToPay = toPay(LAMP_ORDER);

      assertThat(lampToPay.run(STANDARD)).isEqualByComparingTo("53.94");
      assertThat(lampToPay.run(SALE)).isEqualByComparingTo("48.00");
      assertThat(lampToPay.run(SALE).toPlainString()).isEqualTo("48.00");
    }

    @Test
    @DisplayName("delivery is charged below the threshold and free from it")
    void deliveryFollowsTheThreshold() {
      assertThat(net(LAMP_ORDER)).isEqualByComparingTo("40.00");
      assertThat(delivery(new BigDecimal("49.99")).run(STANDARD)).isEqualByComparingTo("4.95");
      assertThat(delivery(new BigDecimal("50.00")).run(STANDARD)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("building a reader reads nothing, and each run calls its function again")
    void eachRunCallsTheFunctionAgain() {
      List<PricingConfig> seen = new ArrayList<>();
      Reader<PricingConfig, BigDecimal> recorded =
          Reader.<PricingConfig, PricingConfig>of(
                  config -> {
                    seen.add(config);
                    return config;
                  })
              .flatMap(config -> toPay(LAMP_ORDER));

      assertThat(seen).isEmpty();
      recorded.run(STANDARD);
      recorded.run(SALE);
      assertThat(seen).containsExactly(STANDARD, SALE);
    }

    @Test
    @DisplayName("every step of one run is given the same configuration")
    void everyStepSeesTheSameConfiguration() {
      List<PricingConfig> seen = new ArrayList<>();
      Reader<PricingConfig, BigDecimal> first =
          Reader.of(
              config -> {
                seen.add(config);
                return config.deliveryCharge();
              });
      Reader<PricingConfig, BigDecimal> chained =
          first.flatMap(
              charge ->
                  Reader.of(
                      config -> {
                        seen.add(config);
                        return charge.add(config.vatRate());
                      }));

      chained.run(STANDARD);

      assertThat(seen).hasSize(2).allSatisfy(config -> assertThat(config).isSameAs(STANDARD));
    }

    @Test
    @DisplayName("ask gives the configuration itself, and constant a value whatever it holds")
    void askGivesTheEnvironment() {
      assertThat(Reader.<PricingConfig>ask().run(STANDARD)).isSameAs(STANDARD);
      assertThat(Reader.<PricingConfig, String>constant("fixed").run(STANDARD)).isEqualTo("fixed");
    }
  }

  @Nested
  @DisplayName("a ReaderPath")
  class OnAReaderPath {

    @Test
    @DisplayName("local changes the configuration for the members' part of the quote only")
    void localChangesOnePart() {
      assertThat(quote(LAMP_ORDER).run(STANDARD)).isEqualTo("53.94, or 48.00 for members");
    }

    @Test
    @DisplayName("an order over the threshold costs members the same")
    void overTheThresholdBothPartsAgree() {
      Order bigger =
          new Order(
              LAMP_ORDER.id(),
              LAMP_ORDER.customer(),
              List.of(new LineItem("LAMP", 2, new BigDecimal("40.00"))),
              LAMP_ORDER.placedAt(),
              LAMP_ORDER.currency(),
              LAMP_ORDER.status());

      assertThat(quote(bigger).run(STANDARD)).isEqualTo("96.00, or 96.00 for members");
    }

    @Test
    @DisplayName("a member pays no delivery whatever the order costs, down to nothing")
    void membersPayNoDeliveryOnAnyOrder() {
      PricingConfig members = forMembers(STANDARD);

      assertThat(delivery(BigDecimal.ZERO).run(members)).isEqualByComparingTo("0");
      assertThat(delivery(new BigDecimal("0.01")).run(members)).isEqualByComparingTo("0");
      assertThat(delivery(new BigDecimal("0.01")).run(STANDARD)).isEqualByComparingTo("4.95");
    }

    @Test
    @DisplayName(
        "local, and magnify from a Getter or a FocusPath, adapt a reader to a larger environment")
    void adaptToALargerEnvironment() {
      record Shop(String name, PricingConfig pricing) {}
      Shop shop = new Shop("lamps", STANDARD);
      ReaderPath<PricingConfig, BigDecimal> lamp = Path.reader(toPay(LAMP_ORDER));

      ReaderPath<Shop, BigDecimal> viaLocal = lamp.local(Shop::pricing);
      ReaderPath<Shop, BigDecimal> viaGetter = lamp.magnify(Getter.of(Shop::pricing));
      ReaderPath<Shop, BigDecimal> viaFocusPath =
          lamp.magnify(
              FocusPath.of(
                  Lens.<Shop, PricingConfig>of(
                      Shop::pricing, (whole, pricing) -> new Shop(whole.name(), pricing))));

      assertThat(viaLocal.run(shop)).isEqualByComparingTo("53.94");
      assertThat(viaGetter.run(shop)).isEqualByComparingTo("53.94");
      assertThat(viaFocusPath.run(shop)).isEqualByComparingTo("53.94");
    }

    @Test
    @DisplayName("via chains a step that reads the configuration, and toReader gives the Reader")
    void viaAndToReader() {
      ReaderPath<PricingConfig, String> threshold =
          Path.reader(toPay(LAMP_ORDER))
              .via(pay -> Path.asks(config -> pay + " below " + config.freeDeliveryFrom()));

      assertThat(threshold.run(STANDARD)).isEqualTo("53.94 below 50.00");
      assertThat(threshold.toReader().run(STANDARD)).isEqualTo("53.94 below 50.00");
    }
  }

  @Nested
  @DisplayName("values, never null")
  class NeverNull {

    @Test
    @DisplayName("constant(null) throws at the call")
    void constantNullThrowsAtTheCall() {
      assertThatThrownBy(() -> Reader.constant(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("a function given to of or map that returns null fails the reader when it runs")
    void aNullResultFailsTheRun() {
      Reader<PricingConfig, BigDecimal> nothing = Reader.of(config -> null);
      Reader<PricingConfig, BigDecimal> mappedToNothing =
          toPay(LAMP_ORDER).map(total -> (BigDecimal) null);

      assertThatThrownBy(() -> nothing.run(STANDARD)).isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> mappedToNothing.run(STANDARD))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("f must not return null");
    }
  }

  @Nested
  @DisplayName("working with Kind")
  class WorkingWithKind {

    @Test
    @DisplayName("the instance maps a widened reader, and runReader runs it")
    void theInstanceMaps() {
      Monad<ReaderKind.Witness<PricingConfig>> pricing = Instances.monad(reader());

      String ran =
          READER.runReader(
              pricing.map(total -> "pay " + total, READER.widen(toPay(LAMP_ORDER))), STANDARD);

      assertThat(ran).isEqualTo("pay 53.94");
    }

    @Test
    @DisplayName("widen returns the same object, since Reader implements ReaderKind")
    void widenAllocatesNothing() {
      Reader<PricingConfig, BigDecimal> lamp = toPay(LAMP_ORDER);

      assertThat(READER.widen(lamp)).isSameAs(lamp);
      assertThat(READER.narrow(READER.widen(lamp))).isSameAs(lamp);
    }

    @Test
    @DisplayName("the Selective instance is an object of its own")
    void theSelectiveInstanceIsSeparate() {
      Monad<ReaderKind.Witness<PricingConfig>> monad = Instances.monad(reader());
      Selective<ReaderKind.Witness<PricingConfig>> selective = ReaderSelective.instance();

      assertThat(monad).isNotInstanceOf(Selective.class);
      assertThat((Object) selective).isNotSameAs(monad);
    }
  }
}
