// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.coyoneda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.higherkindedj.example.book.foundations.coyoneda.CoyonedaBook.BASKET;
import static org.higherkindedj.example.book.foundations.coyoneda.CoyonedaBook.VAT;
import static org.higherkindedj.example.book.foundations.coyoneda.CoyonedaBook.reserveLine;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.coyoneda.Coyoneda;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the Coyoneda page makes. */
@DisplayName("Coyoneda: maps held back and applied in one pass")
class CoyonedaBookTest {

  private static final IdMonad ID_MONAD = IdMonad.instance();

  /** List's Functor, counting each walk over a list. */
  private static final class CountingListFunctor implements Functor<ListKind.Witness> {
    int walks;

    @Override
    public <A, B> Kind<ListKind.Witness, B> map(
        Function<? super A, ? extends B> f, Kind<ListKind.Witness, A> fa) {
      walks++;
      return ListMonad.INSTANCE.map(f, fa);
    }
  }

  @Nested
  @DisplayName("Map fusion")
  class Fusion {

    @Test
    @DisplayName("three maps held by a Coyoneda walk the list once when lowered")
    void oneWalk() {
      CountingListFunctor functor = new CountingListFunctor();

      List<BigDecimal> prices =
          LIST.narrow(
              Coyoneda.lift(LIST.widen(BASKET))
                  .map(LineItem::price)
                  .map(price -> price.multiply(VAT))
                  .map(price -> price.setScale(2, RoundingMode.HALF_EVEN))
                  .lower(functor));

      assertThat(prices).containsExactly(new BigDecimal("48.00"), new BigDecimal("3.00"));
      assertThat(functor.walks).isEqualTo(1);
    }

    @Test
    @DisplayName("the same three maps made through List's own map walk the list three times")
    void threeWalksWithout() {
      CountingListFunctor functor = new CountingListFunctor();

      Kind<ListKind.Witness, BigDecimal> prices =
          functor.map(
              price -> price.setScale(2, RoundingMode.HALF_EVEN),
              functor.map(
                  price -> price.multiply(VAT), functor.map(LineItem::price, LIST.widen(BASKET))));

      assertThat(LIST.narrow(prices))
          .containsExactly(new BigDecimal("48.00"), new BigDecimal("3.00"));
      assertThat(functor.walks).isEqualTo(3);
    }

    @Test
    @DisplayName("a chain of a hundred thousand maps lowers without overflowing the stack")
    void longChain() {
      Coyoneda<ListKind.Witness, Integer> counted = Coyoneda.lift(LIST.widen(List.of(0)));
      for (int i = 0; i < 100_000; i++) {
        counted = counted.map(n -> n + 1);
      }

      assertThat(LIST.narrow(counted.lower(ListMonad.INSTANCE))).containsExactly(100_000);
    }

    @Test
    @DisplayName("lift then lower gives back the list")
    void liftThenLower() {
      assertThat(LIST.narrow(Coyoneda.lift(LIST.widen(BASKET)).lower(ListMonad.INSTANCE)))
          .isEqualTo(BASKET);
    }
  }

  @Nested
  @DisplayName("An instruction set with no Functor")
  class NoFunctor {

    @Test
    @DisplayName("a line the warehouse can cover is reserved, and its stock falls")
    void reservesWhatItHolds() {
      CoyonedaBook.Warehouse warehouse = new CoyonedaBook.Warehouse();

      boolean reserved =
          ID.narrow(
                  reserveLine(new LineItem("LAMP", 2, BigDecimal.ONE))
                      .foldMap(Coyoneda.interpreter(warehouse, ID_MONAD), ID_MONAD)
                      .runKind())
              .value();

      assertThat(reserved).isTrue();
      assertThat(warehouse.levels).containsEntry("LAMP", 1);
    }

    @Test
    @DisplayName("a line the warehouse cannot cover is not reserved, and its stock is untouched")
    void refusesWhatItLacks() {
      CoyonedaBook.Warehouse warehouse = new CoyonedaBook.Warehouse();

      boolean reserved =
          ID.narrow(
                  reserveLine(new LineItem("BULB", 4, BigDecimal.ONE))
                      .foldMap(Coyoneda.interpreter(warehouse, ID_MONAD), ID_MONAD)
                      .runKind())
              .value();

      assertThat(reserved).isFalse();
      assertThat(warehouse.levels).containsEntry("BULB", 2);
    }

    @Test
    @DisplayName("one held value runs through foldMap with its maps applied")
    void oneValueThroughFoldMap() {
      boolean enough =
          ID.narrow(
                  Coyoneda.lift(new CoyonedaBook.StockOp.Available("BULB"))
                      .map(count -> count >= 3)
                      .foldMap(new CoyonedaBook.Warehouse(), ID_MONAD))
              .value();

      assertThat(enough).isFalse();
    }
  }

  @Nested
  @DisplayName("Null")
  class Null {

    @Test
    @DisplayName("lift and apply refuse a null value at the call")
    void refusesNullValue() {
      assertThatNullPointerException().isThrownBy(() -> Coyoneda.lift(null));
      assertThatNullPointerException().isThrownBy(() -> Coyoneda.apply(null, Function.identity()));
    }

    @Test
    @DisplayName("a held function's null result meets the target's own rule when lowered")
    void heldNullMeetsTheTargetsRule() {
      Kind<IdKind.Witness, Object> lowered =
          Coyoneda.lift(ID.widen(Id.of("LAMP"))).map(_ -> null).lower(ID_MONAD);

      assertThat(ID.narrow(lowered).value()).isNull();
    }
  }
}
