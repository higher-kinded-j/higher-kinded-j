// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.eitherf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.foundations.NoticeOpKindHelper.NOTICE_OP;
import static org.higherkindedj.example.book.foundations.OrderOpKindHelper.ORDER_OP;
import static org.higherkindedj.example.book.foundations.eitherf.EitherFBook.INTO_NOTICES;
import static org.higherkindedj.example.book.foundations.eitherf.EitherFBook.INTO_ORDERS;
import static org.higherkindedj.example.book.foundations.eitherf.EitherFBook.LIMIT;
import static org.higherkindedj.example.book.foundations.eitherf.EitherFBook.ORDER;
import static org.higherkindedj.example.book.foundations.eitherf.EitherFBook.ORDERS_AND_NOTICES;
import static org.higherkindedj.example.book.foundations.eitherf.EitherFBook.cancelAndTell;
import static org.higherkindedj.hkt.eitherf.EitherFKindHelper.EITHERF;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.id;

import java.math.BigDecimal;
import java.util.function.Function;
import org.higherkindedj.example.book.foundations.NoticeOp;
import org.higherkindedj.example.book.foundations.NoticeOpKind;
import org.higherkindedj.example.book.foundations.OrderOp;
import org.higherkindedj.example.book.foundations.OrderOpKind;
import org.higherkindedj.example.book.foundations.OrderOpOps;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.FreePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.eitherf.EitherF;
import org.higherkindedj.hkt.eitherf.EitherFKind;
import org.higherkindedj.hkt.eitherf.Interpreters;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.inject.Inject;
import org.higherkindedj.hkt.inject.InjectInstances;
import org.higherkindedj.hkt.instances.Instances;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the EitherF page makes. */
@DisplayName("EitherF: one program over two instruction sets")
class EitherFBookTest {

  private static boolean run(
      FreePath<EitherFKind.Witness<OrderOpKind.Witness, NoticeOpKind.Witness>, Boolean> program,
      EitherFBook.InMemoryOrders orders,
      EitherFBook.Outbox outbox) {
    return ID.narrow(
            program.foldMap(Interpreters.combine(orders, outbox), Instances.monad(id())).runKind())
        .value();
  }

  private static final Kind<OrderOpKind.Witness, BigDecimal> PRICE_LAMP =
      ORDER_OP.widen(new OrderOp.PriceOf<>("LAMP", Function.identity()));
  private static final Kind<NoticeOpKind.Witness, Unit> SHIPPED =
      NOTICE_OP.widen(new NoticeOp.Send<>(ORDER, "Shipped", Function.identity()));

  @Nested
  @DisplayName("One interpreter for the pair")
  class Combined {

    @Test
    @DisplayName("a line over the limit cancels the order and sends one notice")
    void overTheLimit() {
      EitherFBook.InMemoryOrders orders = new EitherFBook.InMemoryOrders();
      EitherFBook.Outbox outbox = new EitherFBook.Outbox();

      assertThat(run(cancelAndTell(ORDER, "LAMP", LIMIT), orders, outbox)).isTrue();
      assertThat(orders.cancelled).containsExactly(ORDER);
      assertThat(outbox.sent).containsExactly("Cancelled: LAMP costs 40.00");
    }

    @Test
    @DisplayName("a line within the limit cancels nothing and sends nothing")
    void withinTheLimit() {
      EitherFBook.InMemoryOrders orders = new EitherFBook.InMemoryOrders();
      EitherFBook.Outbox outbox = new EitherFBook.Outbox();

      assertThat(run(cancelAndTell(ORDER, "BULB", LIMIT), orders, outbox)).isFalse();
      assertThat(orders.cancelled).isEmpty();
      assertThat(outbox.sent).isEmpty();
    }

    @Test
    @DisplayName("combine hands a Left to the first interpreter and a Right to the second")
    void combineDispatchesBySide() {
      EitherFBook.InMemoryOrders orders = new EitherFBook.InMemoryOrders();
      EitherFBook.Outbox outbox = new EitherFBook.Outbox();
      var both = Interpreters.combine(orders, outbox);

      BigDecimal price = ID.narrow(both.apply(INTO_ORDERS.inject(PRICE_LAMP))).value();
      both.apply(INTO_NOTICES.inject(SHIPPED));

      assertThat(price).isEqualByComparingTo("40.00");
      assertThat(outbox.sent).containsExactly("Shipped");
    }
  }

  @Nested
  @DisplayName("What @ComposeEffects generates")
  class Generated {

    @Test
    @DisplayName("the generated Injects put orders on the left and notices on the right")
    void generatedInjectsMatchTheHandWrittenOnes() {
      var orderSide = EITHERF.narrow(OrderEffectsSupport.injectOrders().inject(PRICE_LAMP));
      var noticeSide = EITHERF.narrow(OrderEffectsSupport.injectNotices().inject(SHIPPED));

      assertThat(orderSide).isInstanceOf(EitherF.Left.class);
      assertThat(noticeSide).isInstanceOf(EitherF.Right.class);
      assertThat(EITHERF.narrow(INTO_ORDERS.inject(PRICE_LAMP))).isEqualTo(orderSide);
      assertThat(EITHERF.narrow(INTO_NOTICES.inject(SHIPPED))).isEqualTo(noticeSide);
    }

    @Test
    @DisplayName("an instruction in the pair folds to the side it is on")
    void foldsToItsSide() {
      String orderSide =
          EITHERF
              .<OrderOpKind.Witness, NoticeOpKind.Witness, BigDecimal>narrow(
                  INTO_ORDERS.inject(PRICE_LAMP))
              .fold(_ -> "an order instruction", _ -> "a notice");

      assertThat(orderSide).isEqualTo("an order instruction");
    }

    @Test
    @DisplayName("the pair's Functor maps through an instruction on either side")
    void functorMapsEitherSide() {
      var mapped =
          ORDERS_AND_NOTICES.map(
              price -> price.add(BigDecimal.ONE), INTO_ORDERS.inject(PRICE_LAMP));
      var orders = new EitherFBook.InMemoryOrders();

      BigDecimal result =
          ID.narrow(Interpreters.combine(orders, new EitherFBook.Outbox()).apply(mapped)).value();

      assertThat(result).isEqualByComparingTo("41.00");
    }
  }

  @Nested
  @DisplayName("Moving a program into the pair")
  class Translate {

    @Test
    @DisplayName("every instruction of a translated OrderOp program goes to the orders side")
    void translatedProgramStaysOnTheLeft() {
      Free<OrderOpKind.Witness, BigDecimal> alone = OrderOpOps.priceOf("BULB", Function.identity());
      Free<EitherFKind.Witness<OrderOpKind.Witness, NoticeOpKind.Witness>, BigDecimal> joined =
          Free.translate(alone, INTO_ORDERS::inject, ORDERS_AND_NOTICES);
      EitherFBook.Outbox outbox = new EitherFBook.Outbox();

      BigDecimal price =
          ID.narrow(
                  joined.foldMap(
                      Interpreters.combine(new EitherFBook.InMemoryOrders(), outbox),
                      Instances.monad(id())))
              .value();

      assertThat(price).isEqualByComparingTo("2.50");
      assertThat(outbox.sent).isEmpty();
    }

    @Test
    @DisplayName("a translated program and a native one in the pair combine into one program")
    void translatedAndNativeCombine() {
      Free<EitherFKind.Witness<OrderOpKind.Witness, NoticeOpKind.Witness>, BigDecimal> joined =
          Free.translate(
              OrderOpOps.priceOf("LAMP", Function.identity()),
              INTO_ORDERS::inject,
              ORDERS_AND_NOTICES);
      EitherFBook.Outbox outbox = new EitherFBook.Outbox();

      boolean told =
          run(
              Path.free(joined, ORDERS_AND_NOTICES)
                  .via(
                      price ->
                          Path.free(
                              EitherFBook.NOTICES.send(
                                  ORDER, "LAMP is " + price, Function.identity()),
                              ORDERS_AND_NOTICES))
                  .map(_ -> true),
              new EitherFBook.InMemoryOrders(),
              outbox);

      assertThat(told).isTrue();
      assertThat(outbox.sent).containsExactly("LAMP is 40.00");
    }
  }

  @Nested
  @DisplayName("Three instruction sets")
  class ThreeSets {

    @Test
    @DisplayName("a third set nests on the right, and injectRightThen reaches inside")
    @SuppressWarnings("migration-nudge") // builds by hand the Inject that injectRightThen gives
    void thirdSetNestsOnTheRight() {
      Inject<
              NoticeOpKind.Witness,
              EitherFKind.Witness<
                  OrderOpKind.Witness, EitherFKind.Witness<NoticeOpKind.Witness, IdKind.Witness>>>
          intoMiddle = InjectInstances.injectRightThen(InjectInstances.injectLeft());

      String path =
          EITHERF
              .narrow(intoMiddle.inject(SHIPPED))
              .fold(
                  _ -> "left",
                  right ->
                      EITHERF
                          .narrow(right)
                          .fold(_ -> "right, then left", _ -> "right, then right"));

      assertThat(path).isEqualTo("right, then left");
    }
  }

  @Nested
  @DisplayName("An instruction is never null")
  class NeverNull {

    @Test
    @DisplayName("EitherF.left and EitherF.right refuse a null instruction")
    void refusesNull() {
      assertThatThrownBy(() -> EitherF.left(null)).isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> EitherF.right(null)).isInstanceOf(NullPointerException.class);
    }
  }
}
