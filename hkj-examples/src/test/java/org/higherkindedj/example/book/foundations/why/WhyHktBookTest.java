// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.why;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.foundations.intro.FoundationsIntroBook.order;
import static org.higherkindedj.example.book.foundations.intro.FoundationsIntroBook.totals;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.instances.Instances.functor;
import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Why Higher-Kinded Types? page makes. */
@DisplayName("Why Higher-Kinded Types?: one method for every container")
class WhyHktBookTest {

  private static final Order LAMP_AND_BULBS =
      order(
          "00000000-0000-0000-0000-000000000001",
          List.of(
              new LineItem("LAMP", 1, new BigDecimal("40.00")),
              new LineItem("BULB", 4, new BigDecimal("2.50"))));

  private static final Order REFILL =
      order(
          "00000000-0000-0000-0000-000000000002",
          List.of(new LineItem("BULB", 3, new BigDecimal("2.50"))));

  @Test
  @DisplayName("the generic totals gives what the List method gives")
  void listAgrees() {
    List<Order> orders = List.of(LAMP_AND_BULBS, REFILL);

    List<BigDecimal> generic = LIST.narrow(totals(functor(list()), LIST.widen(orders)));

    assertThat(generic)
        .isEqualTo(WhyHktBook.totalsOfList(orders))
        .containsExactly(new BigDecimal("50.00"), new BigDecimal("7.50"));
  }

  @Test
  @DisplayName("the generic totals gives what the Optional method gives, present or empty")
  void optionalAgrees() {
    for (Optional<Order> order : List.of(Optional.of(LAMP_AND_BULBS), Optional.<Order>empty())) {
      Optional<BigDecimal> generic =
          OPTIONAL.narrow(totals(functor(optional()), OPTIONAL.widen(order)));

      assertThat(generic).isEqualTo(WhyHktBook.totalOfOptional(order));
    }
    assertThat(WhyHktBook.totalOfOptional(Optional.of(LAMP_AND_BULBS)))
        .contains(new BigDecimal("50.00"));
  }

  @Test
  @DisplayName("the generic totals gives what the CompletableFuture method gives")
  void futureAgrees() {
    CompletableFuture<Order> order = CompletableFuture.completedFuture(LAMP_AND_BULBS);

    BigDecimal generic =
        FUTURE.narrow(totals(functor(completableFuture()), FUTURE.widen(order))).join();

    assertThat(generic)
        .isEqualTo(WhyHktBook.totalOfFuture(order).join())
        .isEqualTo(new BigDecimal("50.00"));
  }

  @Test
  @DisplayName("a witness is never instantiated: its only constructor is private")
  void witnessesAreUninhabited() {
    for (Class<?> witness :
        List.of(
            ListKind.Witness.class,
            OptionalKind.Witness.class,
            CompletableFutureKind.Witness.class)) {
      assertThat(witness.getDeclaredConstructors())
          .isNotEmpty()
          .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers()));
    }
  }

  @Test
  @DisplayName("widen then narrow gives back the value it was given")
  void widenThenNarrow() {
    List<Order> orders = List.of(LAMP_AND_BULBS);

    assertThat(LIST.narrow(LIST.widen(orders))).isSameAs(orders);
  }

  @Test
  @DisplayName("any class can claim the List witness, so narrow checks at run time and throws")
  void narrowChecksAtRunTime() {
    record NotAList<A>() implements Kind<ListKind.Witness, A> {}

    assertThatThrownBy(() -> LIST.narrow(new NotAList<Order>()))
        .isInstanceOf(KindUnwrapException.class);
  }
}
