// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.list;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.foundations.list.ListBook.COURIERS;
import static org.higherkindedj.example.book.foundations.list.ListBook.optionsWithin;
import static org.higherkindedj.example.book.foundations.list.ListBook.servicesOf;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.example.book.foundations.list.ListBook.Service;
import org.higherkindedj.hkt.Alternative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.effect.ListPath;
import org.higherkindedj.hkt.effect.NonDetPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the List page makes about computations with several results. */
@DisplayName("List: every result of every branch")
class ListBookTest {

  @Nested
  @DisplayName("every way to ship an order")
  class Shipping {

    @Test
    @DisplayName("three of the four services cost 10.00 or less")
    void optionsUnderTen() {
      assertThat(optionsWithin(new BigDecimal("10.00")).run())
          .extracting(Service::name)
          .containsExactly("standard", "express", "next day");
    }

    @Test
    @DisplayName("without the filter, every courier's every service comes back, in order")
    void everyBranch() {
      List<Service> all = Path.list(COURIERS).via(courier -> Path.list(servicesOf(courier))).run();

      assertThat(all)
          .extracting(Service::name)
          .containsExactly("standard", "express", "next day", "timed");
    }

    @Test
    @DisplayName("a branch that gives an empty list drops out")
    void emptyBranchDropsOut() {
      List<String> kept =
          Path.list(1, 2, 3, 4)
              .via(n -> n % 2 == 0 ? Path.list("even " + n) : Path.<String>listEmpty())
              .run();

      assertThat(kept).containsExactly("even 2", "even 4");
    }

    @Test
    @DisplayName("a budget below every price leaves no option")
    void noOption() {
      assertThat(optionsWithin(new BigDecimal("1.00")).run()).isEmpty();
    }
  }

  @Nested
  @DisplayName("every combination, or position by position")
  class Combining {

    @Test
    @DisplayName("NonDetPath's zipWith gives every colour with every shade")
    void cartesian() {
      assertThat(
              Path.list("black", "brass")
                  .zipWith(Path.list("linen", "glass"), (c, s) -> c + "/" + s)
                  .run())
          .containsExactly("black/linen", "black/glass", "brass/linen", "brass/glass");
    }

    @Test
    @DisplayName("ListPath's zipWith pairs by position, and the shorter list sets the length")
    void positional() {
      assertThat(
              Path.listPath("LAMP", "BULB", "SHADE")
                  .zipWith(Path.listPath("Monday", "Tuesday"), (sku, day) -> sku + " on " + day)
                  .run())
          .containsExactly("LAMP on Monday", "BULB on Tuesday");
    }

    @Test
    @DisplayName("zipWith3 differs the same way: every combination, or by position")
    void zipWith3() {
      assertThat(
              Path.list(1, 2)
                  .zipWith3(Path.list(10), Path.list(100, 200), (a, b, c) -> a + b + c)
                  .run())
          .containsExactly(111, 211, 112, 212);
      assertThat(
              Path.listPath(1, 2)
                  .zipWith3(Path.listPath(10, 20), Path.listPath(100, 200), (a, b, c) -> a + b + c)
                  .run())
          .containsExactly(111, 222);
    }

    @Test
    @DisplayName("Path.list gives a NonDetPath, Path.listPath a ListPath, and their via agree")
    void factories() {
      NonDetPath<Integer> nonDet = Path.list(1, 2);
      ListPath<Integer> byPosition = Path.listPath(1, 2);

      assertThat(nonDet.via(n -> Path.list(n, n * 10)).run()).containsExactly(1, 10, 2, 20);
      assertThat(byPosition.via(n -> Path.listPath(n, n * 10)).run()).containsExactly(1, 10, 2, 20);
    }
  }

  @Nested
  @DisplayName("a list holds what a Java list holds")
  class Nulls {

    @Test
    @DisplayName("of(null) gives an empty list")
    void ofNull() {
      MonadZero<ListKind.Witness> lists = Instances.monadZero(list());

      assertThat(LIST.narrow(lists.of(null))).isEmpty();
    }

    @Test
    @DisplayName("a null element is kept, and so is a null a map function returns")
    void nullsKept() {
      MonadZero<ListKind.Witness> lists = Instances.monadZero(list());

      assertThat(LIST.narrow(lists.map(s -> s, LIST.widen(Arrays.asList("a", null)))))
          .containsExactly("a", null);
      assertThat(LIST.narrow(lists.map(_ -> (String) null, LIST.widen(List.of(1)))))
          .containsExactly((String) null);
    }

    @Test
    @DisplayName("the Paths refuse null: a null element, or a map or via function that returns one")
    void pathsRefuseNull() {
      assertThatThrownBy(() -> Path.list(Arrays.asList("a", null)))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> Path.list("a").map(_ -> null))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> Path.listPath("a").map(_ -> null))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> Path.list("a").via(_ -> null).run())
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("a flatMap function that returns null throws")
    void flatMapNullThrows() {
      MonadZero<ListKind.Witness> lists = Instances.monadZero(list());

      assertThatThrownBy(() -> lists.flatMap(_ -> null, LIST.widen(List.of(1))))
          .isInstanceOf(KindUnwrapException.class);
    }
  }

  @Nested
  @DisplayName("working with Kind")
  class WorkingWithKind {

    @Test
    @DisplayName("flatMap over the couriers gives their four services")
    void flatMap() {
      MonadZero<ListKind.Witness> lists = Instances.monadZero(list());
      Function<String, Kind<ListKind.Witness, Service>> services =
          courier -> LIST.widen(servicesOf(courier));

      assertThat(LIST.narrow(lists.flatMap(services, LIST.widen(COURIERS)))).hasSize(4);
    }

    @Test
    @DisplayName("ap applies every function to every value")
    void ap() {
      MonadZero<ListKind.Witness> lists = Instances.monadZero(list());
      Kind<ListKind.Witness, Function<Integer, String>> functions =
          LIST.widen(List.of(n -> "a" + n, n -> "b" + n));

      assertThat(LIST.narrow(lists.ap(functions, LIST.widen(List.of(1, 2)))))
          .containsExactly("a1", "a2", "b1", "b2");
    }

    @Test
    @DisplayName("widen wraps the list, and narrow gives back the same list")
    void widenNarrow() {
      List<String> couriers = COURIERS;
      Kind<ListKind.Witness, String> widened = LIST.widen(couriers);

      assertThat((Object) widened).isNotSameAs(couriers);
      assertThat(LIST.narrow(widened)).isSameAs(couriers);
    }

    @Test
    @DisplayName("zero is the empty list, and orElse puts one list after another")
    void zeroAndOrElse() {
      MonadZero<ListKind.Witness> lists = Instances.monadZero(list());
      Alternative<ListKind.Witness> alternative = Instances.alternative(list());

      assertThat(LIST.narrow(lists.<String>zero())).isEmpty();
      assertThat(
              LIST.narrow(alternative.orElse(LIST.widen(List.of(1)), () -> LIST.widen(List.of(2)))))
          .containsExactly(1, 2);
    }
  }
}
