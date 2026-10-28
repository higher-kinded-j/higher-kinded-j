// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.intro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.math.BigDecimal;
import java.util.List;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Foundations introduction makes about its one generic method. */
@DisplayName("the Foundations introduction: one method over any container")
class FoundationsIntroBookTest {

  private static final Order LAMP_AND_BULBS =
      FoundationsIntroBook.order(
          "00000000-0000-0000-0000-000000000001",
          List.of(
              new LineItem("LAMP", 1, new BigDecimal("40.00")),
              new LineItem("BULB", 4, new BigDecimal("2.50"))));

  private static final Order REFILL =
      FoundationsIntroBook.order(
          "00000000-0000-0000-0000-000000000002",
          List.of(new LineItem("BULB", 3, new BigDecimal("2.50"))));

  @Test
  @DisplayName("the same method totals an order inside a Maybe, an Either and a List")
  void oneMethodThreeContainers() {
    Maybe<BigDecimal> maybeTotal =
        MAYBE.narrow(
            FoundationsIntroBook.totals(
                Instances.functor(maybe()), MAYBE.widen(Maybe.just(LAMP_AND_BULBS))));
    Either<String, BigDecimal> eitherTotal =
        EITHER.narrow(
            FoundationsIntroBook.totals(
                Instances.functor(either()),
                EITHER.widen(Either.<String, Order>right(LAMP_AND_BULBS))));
    List<BigDecimal> listTotals =
        LIST.narrow(
            FoundationsIntroBook.totals(
                Instances.functor(list()), LIST.widen(List.of(LAMP_AND_BULBS, REFILL))));

    assertThat(maybeTotal.get()).isEqualByComparingTo("50.00");
    assertThat(eitherTotal.getRight()).isEqualByComparingTo("50.00");
    assertThat(listTotals).containsExactly(new BigDecimal("50.00"), new BigDecimal("7.50"));
  }

  @Test
  @DisplayName("an empty container stays empty: map never invents a value")
  void emptyStaysEmpty() {
    Maybe<BigDecimal> nothing =
        MAYBE.narrow(
            FoundationsIntroBook.totals(Instances.functor(maybe()), MAYBE.widen(Maybe.nothing())));
    Either<String, BigDecimal> left =
        EITHER.narrow(
            FoundationsIntroBook.totals(
                Instances.functor(either()),
                EITHER.widen(Either.<String, Order>left("not found"))));

    assertThat(nothing.isNothing()).isTrue();
    assertThat(left.isLeft()).isTrue();
    assertThat(left.getLeft()).isEqualTo("not found");
  }
}
